package com.awgconfig.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.awgconfig.android.core.batch.BatchGenerator
import com.awgconfig.android.core.check.ConfigChecker
import com.awgconfig.android.core.masque.MasqueBuilder
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.BatchOutcome
import com.awgconfig.android.core.model.BatchTestProgress
import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.HistoryEntry
import com.awgconfig.android.core.model.MasqueParams
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.core.model.TestLevel
import com.awgconfig.android.core.model.TestReport
import com.awgconfig.android.core.model.WarpGenParams
import com.awgconfig.android.core.warp.WarpRegistrationApi
import com.awgconfig.android.data.HistoryStore
import com.awgconfig.android.data.SettingsStore
import com.awgconfig.android.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Состояние генерации для UI. */
sealed interface GenState {
    data object Idle : GenState
    data class Working(val step: Int) : GenState
    data class Failed(val message: String, val hint: String? = null) : GenState
    data class Done(val outcome: BatchOutcome) : GenState
}

/** Действие туннеля, ожидающее системного диалога VPN-разрешения.
 * Один и тот же prepare-флоу для всех вариантов: подключение с экрана
 * результата, реальная проверка конфига, пакетная реальная проверка,
 * повторное подключение последнего конфига с экрана туннеля. */
sealed interface TunnelRequest {
    val config: GeneratedConfig

    data class Connect(override val config: GeneratedConfig) : TunnelRequest
    data class RealTest(override val config: GeneratedConfig) : TunnelRequest
    data class BatchRealTest(val configs: List<GeneratedConfig>) : TunnelRequest {
        override val config: GeneratedConfig get() = configs.first()
    }
}

/** Ошибка соединения/регистрации для человеческих текстов: человеческая
 * формулировка + техническая причина (HTTP-код апстрима, сообщение). Ловит
 * любой Throwable — uncaught Error не должен оставлять вечный спиннер. */
fun errorText(t: Throwable, lang: String): String {
    val human = when (t) {
        is WarpRegistrationApi.RegistrationException -> when (lang) {
            "en" -> when (t.refusalClass) {
                WarpRegistrationApi.RefusalClass.RATE_LIMITED ->
                    "Cloudflare is rate-limiting WARP device creation. Wait a minute and retry."
                else -> "Cloudflare WARP registration service failed: ${t.refusalClass.name.lowercase()}"
            }
            else -> WarpRegistrationApi.refusalText(t.refusalClass)
        }
        else -> t.message ?: t.javaClass.simpleName
    }
    val technical = listOfNotNull(
        (t as? WarpRegistrationApi.RegistrationException)?.upstreamStatus?.let { "HTTP $it" },
        t.cause?.let { it.message ?: it.javaClass.simpleName },
    ).joinToString(": ")
    return if (technical.isBlank()) human else "$human ($technical)"
}

class AppViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val awgApp = app as AwgApp
    val settings: SettingsStore = awgApp.settings
    val history: HistoryStore = awgApp.history
    val tunnel = awgApp.tunnel

    // ── Язык ───────────────────────────────────────────────────────────────────
    private val _lang = MutableStateFlow(settings.language)
    val lang: StateFlow<String> = _lang
    val strings: Map<String, String>
        get() = if (_lang.value == "en") Strings.en else Strings.ru

    fun setLanguage(value: String) {
        settings.language = value
        _lang.value = value
    }

    // ── Генерация ─────────────────────────────────────────────────────────────
    private val _genState = MutableStateFlow<GenState>(GenState.Idle)
    val genState: StateFlow<GenState> = _genState

    /** Конфиги последней генерации + выбранный индекс для экрана результата. */
    private val _lastOutcome = MutableStateFlow<BatchOutcome?>(null)
    val lastOutcome: StateFlow<BatchOutcome?> = _lastOutcome
    private val _resultIndex = MutableStateFlow(0)
    val resultIndex: StateFlow<Int> = _resultIndex

    fun selectResult(index: Int) { _resultIndex.value = index }

    fun resetGen() { _genState.value = GenState.Idle }

    /** WARP: регистрация с устройства + сборка конфига.
     * Обязательно вне main-потока: регистрация делает реальные HTTP-запросы к
     * api.cloudflareclient.com, а сетевой I/O на Dispatchers.Main бросает
     * NetworkOnMainThreadException до отправки любого байта. */
    fun generateWarp(params: WarpGenParams) {
        viewModelScope.launch(Dispatchers.IO) {
            _genState.value = GenState.Working(0)
            try {
                val outcome = awgApp.warpGenerator.generate(params)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(
                    errorText(e, _lang.value),
                    if (_lang.value == "en") "Try later or switch endpoint/port in options." else "Попробуйте позже или смените endpoint и порт в настройках.",
                )
            }
        }
    }

    fun generateAwgServer(params: AwgServerParams) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            try {
                val outcome = BatchGenerator.awgServerBatch(params, 1)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(errorText(e, _lang.value), null)
            }
        }
    }

    fun generateMasque(params: MasqueParams) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            try {
                val outcome = BatchGenerator.masqueBatch(params, 1)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(errorText(e, _lang.value), null)
            }
        }
    }

    fun generateKeypair() {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            try {
                val outcome = BatchGenerator.keypairBatch(1)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(errorText(e, _lang.value), null)
            }
        }
    }

    // ── Пакетная генерация ────────────────────────────────────────────────────
    fun batchWarp(params: WarpGenParams) = generateWarp(params.copy(count = params.count.coerceIn(1, 3)))

    fun batchAwg(params: AwgServerParams, count: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            try {
                val outcome = BatchGenerator.awgServerBatch(params, count)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(errorText(e, _lang.value), null)
            }
        }
    }

    fun batchMasque(params: MasqueParams, count: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            try {
                val outcome = BatchGenerator.masqueBatch(params, count)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(errorText(e, _lang.value), null)
            }
        }
    }

    fun batchKeypair(count: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            try {
                val outcome = BatchGenerator.keypairBatch(count)
                _genState.value = GenState.Done(outcome)
                _lastOutcome.value = outcome
                _resultIndex.value = 0
                outcome.configs.forEach { remember(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _genState.value = GenState.Failed(errorText(e, _lang.value), null)
            }
        }
    }

    /** Открыть конфиг из истории как результат. */
    fun openFromHistory(entry: HistoryEntry) {
        val config = entry.toGeneratedConfig()
        _lastOutcome.value = BatchOutcome(listOf(config))
        _resultIndex.value = 0
        _genState.value = GenState.Done(BatchOutcome(listOf(config)))
        _currentTest.value = _testReports.value[config.id]
    }

    /** Счётчик изменений истории — экран «Мои конфиги» перечитывает список по нему. */
    private val _historyTick = MutableStateFlow(0)
    val historyTick: StateFlow<Int> = _historyTick

    fun clearHistory() {
        history.clear()
        _historyTick.value++
    }

    private fun remember(config: GeneratedConfig) {
        if (config.protocolKind == ProtocolKind.KEYPAIR) return // пары ключей не пишем в историю
        history.add(
            HistoryEntry(
                id = config.id,
                createdAt = config.createdAt,
                protocolKind = config.protocolKind,
                title = config.title,
                text = config.text,
                vpnLink = config.vpnLink,
                details = config.details,
                tunnelCapable = config.tunnelCapable,
            )
        )
        _historyTick.value++
    }

    // ── Туннель ────────────────────────────────────────────────────────────────
    /** Очередь действий туннеля: заполняется кнопками подключений/проверок,
     * AppRoot показывает системный диалог VPN и после разрешения выполняет. */
    private val _tunnelRequest = MutableStateFlow<TunnelRequest?>(null)
    val tunnelRequest: StateFlow<TunnelRequest?> = _tunnelRequest

    /** Пользователь нажал «Подключиться»: запросить системный диалог VPN. */
    fun requestConnect(config: GeneratedConfig) {
        _tunnelRequest.value = TunnelRequest.Connect(config)
    }

    /** Реальная проверка: если разрешение VPN ещё не выдано — сначала
     * системный диалог, затем тест. Иначе — тест сразу. */
    fun requestRealTest(config: GeneratedConfig) {
        if (config.tunnelCapable && !tunnel.isPrepared()) {
            _tunnelRequest.value = TunnelRequest.RealTest(config)
        } else {
            testReal(config)
        }
    }

    /** Пакетная реальная проверка: тоже prepare-aware. Не-туннелируемые
     * (MASQUE) проверяются только структурно/DNS — они остаются в списке. */
    fun requestBatchRealTest(configs: List<GeneratedConfig>) {
        if (configs.isEmpty()) return
        if (!tunnel.isPrepared()) {
            _tunnelRequest.value = TunnelRequest.BatchRealTest(configs)
        } else {
            batchTest(configs, real = true)
        }
    }

    /** Подключить последний сохранённый конфиг (кнопка на экране туннеля). */
    fun requestConnectLast() {
        val text = settings.lastTunnelConfig ?: return
        val title = settings.lastTunnelTitle ?: "—"
        val kind = settings.lastTunnelKind
            ?.let { k -> ProtocolKind.entries.firstOrNull { it.key == k } }
            ?: detectProtocol(text) ?: return
        requestConnect(
            GeneratedConfig(
                id = "last",
                protocolKind = kind,
                title = title,
                text = text,
                tunnelCapable = kind == ProtocolKind.WARP || kind == ProtocolKind.AWG_SERVER,
            )
        )
    }

    /** Эвристика протокола для конфигов, сохранённых без kind (v1.0.x). */
    private fun detectProtocol(text: String): ProtocolKind? = when {
        text.contains("cloudflareclient", ignoreCase = true) -> ProtocolKind.WARP
        text.contains("masque://", ignoreCase = true) -> ProtocolKind.MASQUE
        text.contains("[Interface]") -> ProtocolKind.AWG_SERVER
        else -> null
    }

    fun consumeTunnelRequest(): TunnelRequest? {
        val value = _tunnelRequest.value
        _tunnelRequest.value = null
        return value
    }

    /** Выполнить действие туннеля после выданного разрешения VPN. */
    fun executeTunnelRequest(request: TunnelRequest) {
        when (request) {
            is TunnelRequest.Connect -> connectTunnelApproved(request.config)
            is TunnelRequest.RealTest -> testReal(request.config)
            is TunnelRequest.BatchRealTest -> batchTest(request.configs, real = true)
        }
    }

    fun connectTunnelApproved(config: GeneratedConfig) {
        viewModelScope.launch {
            settings.lastTunnelConfig = config.text
            settings.lastTunnelTitle = config.title
            settings.lastTunnelKind = config.protocolKind.key
            val result = tunnel.connect(config)
            result.exceptionOrNull()?.let {
                android.util.Log.w("AwgConfig", "Tunnel connect failed: ${it.message}")
            }
        }
    }

    fun disconnectTunnel() {
        viewModelScope.launch { tunnel.disconnect() }
    }

    // ── Проверка валидности конфигов ────────────────────────────────────────
    /** Отчёты по всем проверенным конфигам (id → отчёт). */
    private val _testReports = MutableStateFlow<Map<String, TestReport>>(emptyMap())
    val testReports: StateFlow<Map<String, TestReport>> = _testReports

    /** Отчёт для экрана результата (текущий конфиг). */
    private val _currentTest = MutableStateFlow<TestReport?>(null)
    val currentTest: StateFlow<TestReport?> = _currentTest

    /** Прогресс пакетной проверки. */
    private val _batchTest = MutableStateFlow<BatchTestProgress?>(null)
    val batchTest: StateFlow<BatchTestProgress?> = _batchTest

    val isBatchTesting: Boolean get() = _batchTest.value?.running == true

    fun resetCurrentTest() { _currentTest.value = null }

    /** Быстрая проверка: разбор движком + DNS endpoint. */
    fun testQuick(config: GeneratedConfig) {
        _currentTest.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val report = ConfigChecker.quick(config)
            _currentTest.value = report
            _testReports.value = _testReports.value + (config.id to report)
        }
    }

    /** Реальная проверка: быстрая + туннель с handshake и выходным IP. */
    fun testReal(config: GeneratedConfig) {
        _currentTest.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val report = runRealTest(config)
            _currentTest.value = report
            _testReports.value = _testReports.value + (config.id to report)
        }
    }

    /** Пакетная проверка списка конфигов (последовательно). */
    fun batchTest(configs: List<GeneratedConfig>, real: Boolean) {
        if (configs.isEmpty()) return
        if (isBatchTesting) return
        viewModelScope.launch(Dispatchers.IO) {
            _batchTest.value = BatchTestProgress(configs.size, 0, running = true, real = real)
            configs.forEachIndexed { i, config ->
                val report = if (real) runRealTest(config) else ConfigChecker.quick(config)
                _testReports.value = _testReports.value + (config.id to report)
                _batchTest.value = _batchTest.value?.copy(done = i + 1)
            }
            _batchTest.value = _batchTest.value?.copy(running = false, finishedAt = System.currentTimeMillis())
        }
    }

    fun clearBatchTest() { _batchTest.value = null }

    private suspend fun runRealTest(config: GeneratedConfig): TestReport {
        val quick = ConfigChecker.quick(config)
        if (!quick.structuralOk) return quick.copy(level = TestLevel.REAL)
        if (!config.tunnelCapable) {
            // MASQUE: QUIC-транспорта нет — только структурная проверка и DNS
            return quick.copy(level = TestLevel.REAL)
        }
        val directIp = runCatching { tunnel.directIp() }.getOrNull()
        val real = tunnel.realTest(config)
        return quick.copy(
            level = TestLevel.REAL,
            tunnelAttempted = true,
            tunnelUp = real.tunnelUp,
            handshakeOk = real.handshakeOk,
            httpOk = real.httpOk,
            fullTunnel = real.fullTunnel,
            exitIp = real.exitIp,
            directIp = directIp,
            warpOn = real.warpOn,
            tunnelError = real.error,
        )
    }

    /** Удалить запись истории. */
    fun deleteHistoryEntry(id: String) {
        history.removeById(id)
        _testReports.value = _testReports.value - id
        _historyTick.value++
    }

    // ── Экспорт ────────────────────────────────────────────────────────────────

    /** Сохраняет файл в «Загрузки» (MediaStore, без разрешений на API 29+). */
    fun saveToDownloads(fileName: String, mime: String, bytes: ByteArray): String? {
        val context: Context = getApplication()
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            fileName
        } catch (_: Exception) {
            null
        }
    }

    fun saveConfigFile(config: GeneratedConfig): String? {
        val name = configFileName(config)
        return saveToDownloads(name, "text/plain", config.text.toByteArray(Charsets.UTF_8))
    }

    fun saveBatchZip(outcome: BatchOutcome, baseName: String): String? =
        saveConfigsZip(outcome.configs, baseName)

    /** ZIP из произвольного списка конфигов (для «Мои конфиги»). */
    fun saveConfigsZip(configs: List<GeneratedConfig>, baseName: String): String? {
        if (configs.isEmpty()) return null
        val bytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                configs.forEachIndexed { i, config ->
                    val ext = when (config.protocolKind) {
                        ProtocolKind.MASQUE, ProtocolKind.KEYPAIR -> "txt"
                        else -> "conf"
                    }
                    val safe = config.title.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(24)
                    zip.putNextEntry(ZipEntry("${baseName}_${i + 1}_${safe}.${ext}"))
                    zip.write(config.text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            bos
        }.toByteArray()
        return saveToDownloads("$baseName.zip", "application/zip", bytes)
    }

    /** Intent «поделиться» текстом конфига/ссылки. */
    fun shareIntent(title: String, text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun configFileName(config: GeneratedConfig): String = when (config.protocolKind) {
        ProtocolKind.WARP -> "AWGWarp_${config.details["profile"]?.replace(".", "") ?: "20"}_${config.id.takeLast(6)}.conf"
        ProtocolKind.AWG_SERVER -> "AWG_${config.id.takeLast(8)}.conf"
        ProtocolKind.MASQUE -> "MASQUE_${config.id.takeLast(8)}.txt"
        ProtocolKind.KEYPAIR -> "KEYS_${config.id.takeLast(8)}.txt"
    }
}
