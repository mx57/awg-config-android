package com.awgconfig.android.tunnel

import android.content.Context
import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.ProtocolKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.amnezia.awg.backend.BackendException
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Statistics
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Единое ядро туннелирования приложения.
 *
 * Движок — AmneziaWG GoBackend (org.amnezia.awg из amneziawg-android, Apache-2.0):
 * полноценный AmneziaWG (Jc/Jmin/Jmax/S1-4/H1-4/I1-I5) поверх libwg-go.so.
 * Поддерживает всё, что генерирует приложение:
 *  - WARP-конфиги (AWG-поля вырезаются: пир WARP — стандартный WireGuard);
 *  - конфиги собственных AmneziaWG-серверов (передаются как есть — обфускация работает);
 *  - MASQUE — экспортный протокол, встроенного QUIC-транспорта нет (только проверка).
 *
 * Здесь же реальный тест туннеля: подключение, ожидание handshake по uapi,
 * замер выходного IP через HTTPS и отключение.
 */
class TunnelCore(private val context: Context) {

    enum class State { DOWN, CONNECTING, UP, ERROR }

    data class TunnelState(
        val state: State = State.DOWN,
        val title: String? = null,
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val error: String? = null,
        val version: String? = null,
        /** Текст текущего подключённого конфига. */
        val configText: String? = null,
    )

    /** Результат реального теста туннеля. */
    data class RealTestResult(
        val tunnelUp: Boolean,
        val handshakeOk: Boolean,
        val httpOk: Boolean,
        val exitIp: String? = null,
        val warpOn: Boolean? = null,
        val fullTunnel: Boolean = true,
        val error: String? = null,
    )

    private val backend by lazy { GoBackend(context) }

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state

    private val tunnel = object : Tunnel {
        override fun getName(): String = "awg0"
        override fun onStateChange(newState: Tunnel.State) {
            _state.value = _state.value.copy(
                state = when (newState) {
                    Tunnel.State.UP -> State.UP
                    else -> State.DOWN
                }
            )
        }
    }

    init {
        runCatching { _state.value = _state.value.copy(version = backend.version) }
    }

    /** Проверено ли разрешение VPN (VpnService.prepare == null). */
    fun isPrepared(): Boolean = android.net.VpnService.prepare(context) == null

    /** Поднимает туннель по конфигу. Ловит любой Throwable (в т.ч. Error —
     * UnsatisfiedLinkError от движка не должен убивать приложение): ошибка
     * попадает в state.error и видна на экране результата/туннеля. */
    suspend fun connect(config: GeneratedConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prepared = prepareConf(config.text, config.protocolKind)
            val parsed = Config.parse(ByteArrayInputStream(prepared.toByteArray(Charsets.UTF_8)))
            _state.value = _state.value.copy(state = State.CONNECTING, title = config.title, error = null, configText = config.text)
            backend.setState(tunnel, Tunnel.State.UP, parsed)
            _state.value = _state.value.copy(state = State.UP, title = config.title)
            refreshStats()
            Result.success(Unit)
        } catch (e: BackendException) {
            _state.value = _state.value.copy(state = State.ERROR, error = backendMessage(e))
            Result.failure(e)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            _state.value = _state.value.copy(state = State.ERROR, error = e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        }
        _state.value = _state.value.copy(state = State.DOWN)
    }

    suspend fun refreshStats() {
        withContext(Dispatchers.IO) {
            runCatching {
                val stats: Statistics = backend.getStatistics(tunnel)
                _state.value = _state.value.copy(rxBytes = stats.totalRx(), txBytes = stats.totalTx())
            }
        }
    }

    /**
     * Реальный тест: поднимает туннель, дожидается handshake (uapi last_handshake),
     * делает HTTPS-запрос cdn-cgi/trace СКВОЗЬ туннель, отключается.
     * Требует выданного разрешения VPN (isPrepared).
     */
    suspend fun realTest(config: GeneratedConfig): RealTestResult = withContext(Dispatchers.IO) {
        val fullTunnel = hasDefaultRoute(config.text)
        // гасим активный туннель, чтобы тест шёл по чистому конфигу
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        _state.value = _state.value.copy(state = State.CONNECTING, title = config.title, error = null)
        try {
            val prepared = prepareConf(config.text, config.protocolKind)
            val parsed = Config.parse(ByteArrayInputStream(prepared.toByteArray(Charsets.UTF_8)))
            backend.setState(tunnel, Tunnel.State.UP, parsed)

            // HTTPS-сквозь-туннель в отдельном потоке (трафик порождает handshake),
            // uapi опрашиваем здесь
            val start = System.currentTimeMillis()
            val traceRef = AtomicReference<Pair<String, Boolean>?>(null)
            val worker = Thread {
                try { traceRef.set(fetchTrace()) } catch (_: Throwable) { /* нет сети/таймаут */ }
            }
            worker.isDaemon = true
            worker.start()

            var handshakeOk = false
            while (System.currentTimeMillis() - start < HANDSHAKE_WAIT_MS) {
                if (backend.getLastHandshake(tunnel) > 0) { handshakeOk = true; break }
                delay(300)
            }
            worker.join(HANDSHAKE_WAIT_MS)
            if (!handshakeOk && backend.getLastHandshake(tunnel) > 0) handshakeOk = true
            val trace = traceRef.get()

            RealTestResult(
                tunnelUp = true,
                handshakeOk = handshakeOk,
                httpOk = trace != null,
                exitIp = trace?.first,
                warpOn = trace?.second,
                fullTunnel = fullTunnel,
            )
        } catch (e: BackendException) {
            val message = backendMessage(e)
            _state.value = _state.value.copy(state = State.ERROR, error = message)
            RealTestResult(false, false, false, fullTunnel = fullTunnel, error = message)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            val message = e.message ?: e.javaClass.simpleName
            _state.value = _state.value.copy(state = State.ERROR, error = message)
            RealTestResult(false, false, false, fullTunnel = fullTunnel, error = message)
        } finally {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            _state.value = _state.value.copy(state = State.DOWN)
        }
    }

    /** Прямой (без туннеля) выходной IP для сравнения. null — сеть недоступна. */
    suspend fun directIp(): String? = withContext(Dispatchers.IO) {
        fetchTrace()?.first
    }

    private fun backendMessage(e: BackendException): String = when (e.reason) {
        BackendException.Reason.VPN_NOT_AUTHORIZED ->
            "Нет разрешения VPN: разрешите подключение в системном диалоге"
        BackendException.Reason.DNS_RESOLUTION_FAILURE ->
            "Не удалось разрешить DNS endpoint сервера"
        BackendException.Reason.UNABLE_TO_START_VPN ->
            "Не удалось запустить VPN-сервис"
        BackendException.Reason.TUN_CREATION_ERROR ->
            "Не удалось создать туннель (отказ системы)"
        BackendException.Reason.GO_ACTIVATION_ERROR_CODE ->
            "Ошибка активации движка AmneziaWG"
        else -> "${e.reason}: ${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        const val HANDSHAKE_WAIT_MS = 12_000L
        const val TRACE_URL_PRIMARY = "https://www.cloudflare.com/cdn-cgi/trace"
        const val TRACE_URL_FALLBACK = "https://1.1.1.1/cdn-cgi/trace"

        /** Поля стандартного WireGuard для [Interface] и [Peer]. */
        val WG_INTERFACE = setOf("PrivateKey", "Address", "DNS", "MTU", "ListenPort")
        val WG_PEER = setOf("PublicKey", "PresharedKey", "PreSharedKey", "AllowedIPs", "Endpoint", "PersistentKeepalive")

        /** cdn-cgi/trace → (ip, warp=on). */
        fun fetchTrace(): Pair<String, Boolean>? {
            for (url in listOf(TRACE_URL_PRIMARY, TRACE_URL_FALLBACK)) {
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.instanceFollowRedirects = false
                    if (conn.responseCode !in 200..299) { conn.disconnect(); continue }
                    val body = conn.inputStream.use { it.readBytes() }.decodeToString()
                    conn.disconnect()
                    val ip = body.lineSequence().firstOrNull { it.startsWith("ip=") }?.removePrefix("ip=")?.trim()
                    val warp = body.lineSequence().firstOrNull { it.startsWith("warp=") }?.removePrefix("warp=")?.trim()
                    if (ip != null) return ip to (warp == "on" || warp == "plus")
                } catch (_: Exception) {
                    // пробуем следующий URL
                }
            }
            return null
        }

        fun hasDefaultRoute(conf: String): Boolean = conf.lineSequence().any {
            it.trim().startsWith("AllowedIPs") && (it.contains("0.0.0.0/0") || it.contains("::/0"))
        }

        /** Убирает AWG-поля: остаётся чистый WireGuard-конфиг. */
        fun stripAwgFields(awgConf: String): String {
            var section = ""
            val out = mutableListOf<String>()
            for (raw in awgConf.split("\n")) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.trim('[', ']').lowercase()
                    out.add(line)
                    continue
                }
                val key = line.substringBefore("=").trim()
                val keep = when (section) {
                    "interface" -> key in WG_INTERFACE
                    "peer" -> key in WG_PEER
                    else -> false
                }
                if (keep) out.add(line)
            }
            return out.joinToString("\n")
        }

        /**
         * Готовит текст конфига к движку:
         *  - WARP: AWG/CPS-поля вырезаются (пир WARP — стандартный WireGuard,
         *    обфускация Jc/S/H ломала бы handshake);
         *  - собственные AmneziaWG-серверы: конфиг передаётся как есть — движок
         *    AmneziaWG понимает Jc/Jmin/Jmax/S1-4/H1-4/I1-5 нативно;
         *  - MASQUE: не подключается встроенным движком (нет QUIC-транспорта).
         */
        fun prepareConf(text: String, kind: ProtocolKind): String = when (kind) {
            ProtocolKind.WARP -> stripAwgFields(text)
            else -> text
        }
    }
}
