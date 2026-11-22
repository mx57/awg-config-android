package com.awgconfig.android.tunnel

import android.content.Context
import com.awgconfig.android.core.model.GeneratedConfig
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Встроенный VPN-туннель: WireGuard GoBackend из com.wireguard.android:tunnel.
 * Поднимает туннель по сгенерированному конфигу и туннелирует весь трафик устройства.
 *
 * AWG-поля обфускации (Jc/S/H/I…) — клиентская маскировка, стандартный WireGuard-бэкенд
 * их не читает; WARP-пир — стандартный WireGuard, поэтому WARP-конфиги подключаются.
 * Для собственных AWG-серверов с обфускацией протокола конфиг нужно импортировать
 * в AmneziaVPN/AmneziaWG (кнопка экспорта в приложении).
 */
class VpnTunnelController(private val context: Context) {

    enum class State { DOWN, CONNECTING, UP, ERROR }

    data class TunnelState(
        val state: State = State.DOWN,
        val title: String? = null,
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val error: String? = null,
        val version: String? = null,
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

    /**
     * Поднимает туннель по конфигу. AWG-поля вырезаются: остаётся стандартный WireGuard.
     */
    suspend fun connect(config: GeneratedConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val wgConf = toWireGuardConf(config.text)
            val parsed = Config.parse(ByteArrayInputStream(wgConf.toByteArray(Charsets.UTF_8)))
            _state.value = _state.value.copy(state = State.CONNECTING, title = config.title, error = null)
            backend.setState(tunnel, Tunnel.State.UP, parsed)
            _state.value = _state.value.copy(state = State.UP, title = config.title)
            refreshStats()
            Result.success(Unit)
        } catch (e: BackendException) {
            val message = when (e.reason) {
                BackendException.Reason.VPN_NOT_AUTHORIZED ->
                    "Нет разрешения VPN: разрешите подключение в системном диалоге"
                else -> e.reason?.name ?: "BackendException"
            }
            _state.value = _state.value.copy(state = State.ERROR, error = message)
            Result.failure(e)
        } catch (e: Exception) {
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

    companion object {
        private val INTERFACE_KEEP = setOf("PrivateKey", "Address", "DNS", "MTU", "ListenPort")
        private val PEER_KEEP = setOf("PublicKey", "PresharedKey", "PreSharedKey", "AllowedIPs", "Endpoint", "PersistentKeepalive")

        /** Убирает AWG-поля: остаётся конфиг, который поймёт стандартный WireGuard-бэкенд. */
        fun toWireGuardConf(awgConf: String): String {
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
                    "interface" -> key in INTERFACE_KEEP
                    "peer" -> key in PEER_KEEP
                    else -> false
                }
                if (keep) out.add(line)
            }
            return out.joinToString("\n")
        }
    }
}
