package com.awgconfig.android

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.awgconfig.android.core.batch.BatchGenerator
import com.awgconfig.android.core.masque.MasqueBuilder
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.BatchOutcome
import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.MasqueParams
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.core.model.WarpGenParams
import com.awgconfig.android.core.warp.WarpRegistrationApi
import com.awgconfig.android.data.HistoryStore
import com.awgconfig.android.data.SettingsStore
import com.awgconfig.android.i18n.Strings
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

/** Ошибка соединения/регистрации для человеческих текстов. */
fun errorText(e: Exception, lang: String): String = when (e) {
    is WarpRegistrationApi.RegistrationException -> when (lang) {
        "en" -> when (e.refusalClass) {
            WarpRegistrationApi.RefusalClass.RATE_LIMITED ->
                "Cloudflare is rate-limiting WARP device creation. Wait a minute and retry."
            else -> "Cloudflare WARP registration service failed: ${e.refusalClass.name.lowercase()}"
        }
        else -> WarpRegistrationApi.refusalText(e.refusalClass)
    }
    else -> e.message ?: e.javaClass.simpleName
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                _genState.value = GenState.Failed(e.message ?: "error", null)
            }
        }
    }

    fun generateMasque(params: MasqueParams) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            val outcome = BatchGenerator.masqueBatch(params, 1)
            _genState.value = GenState.Done(outcome)
            _lastOutcome.value = outcome
            _resultIndex.value = 0
            outcome.configs.forEach { remember(it) }
        }
    }

    fun generateKeypair() {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            val outcome = BatchGenerator.keypairBatch(1)
            _genState.value = GenState.Done(outcome)
            _lastOutcome.value = outcome
            _resultIndex.value = 0
            outcome.configs.forEach { remember(it) }
        }
    }

    // ── Пакетная генерация ────────────────────────────────────────────────────
    fun batchWarp(params: WarpGenParams) = generateWarp(params.copy(count = params.count.coerceIn(1, 3)))

    fun batchAwg(params: AwgServerParams, count: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            val outcome = BatchGenerator.awgServerBatch(params, count)
            _genState.value = GenState.Done(outcome)
            _lastOutcome.value = outcome
            _resultIndex.value = 0
            outcome.configs.forEach { remember(it) }
        }
    }

    fun batchMasque(params: MasqueParams, count: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            val outcome = BatchGenerator.masqueBatch(params, count)
            _genState.value = GenState.Done(outcome)
            _lastOutcome.value = outcome
            _resultIndex.value = 0
            outcome.configs.forEach { remember(it) }
        }
    }

    fun batchKeypair(count: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            _genState.value = GenState.Working(2)
            val outcome = BatchGenerator.keypairBatch(count)
            _genState.value = GenState.Done(outcome)
            _lastOutcome.value = outcome
            _resultIndex.value = 0
            outcome.configs.forEach { remember(it) }
        }
    }

    /** Открыть конфиг из истории как результат. */
    fun openFromHistory(config: GeneratedConfig) {
        _lastOutcome.value = BatchOutcome(listOf(config))
        _resultIndex.value = 0
        _genState.value = GenState.Done(BatchOutcome(listOf(config)))
    }

    private fun remember(config: GeneratedConfig) {
        if (config.protocolKind == ProtocolKind.KEYPAIR) return // пары ключей не пишем в историю
        history.add(
            com.awgconfig.android.core.model.HistoryEntry(
                createdAt = config.createdAt,
                protocolKind = config.protocolKind,
                title = config.title,
                text = config.text,
                vpnLink = config.vpnLink,
                details = config.details,
                tunnelCapable = config.tunnelCapable,
            )
        )
    }

    // ── Туннель ────────────────────────────────────────────────────────────────
    /** Конфиг, для которого пользователь запросил подключение (ждёт prepare). */
    private val _pendingConnect = MutableStateFlow<GeneratedConfig?>(null)
    val pendingConnect: StateFlow<GeneratedConfig?> = _pendingConnect

    /** Требуется системный диалог VPN: экран запускает prepare и зовёт approveConnect(). */
    fun requestConnect(config: GeneratedConfig) {
        _pendingConnect.value = config
    }

    fun consumePendingConnect(): GeneratedConfig? {
        val value = _pendingConnect.value
        _pendingConnect.value = null
        return value
    }

    fun connectTunnelApproved(config: GeneratedConfig) {
        viewModelScope.launch {
            settings.lastTunnelConfig = config.text
            settings.lastTunnelTitle = config.title
            tunnel.connect(config)
        }
    }

    fun disconnectTunnel() {
        viewModelScope.launch { tunnel.disconnect() }
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

    fun saveBatchZip(outcome: BatchOutcome, baseName: String): String? {
        val bytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                outcome.configs.forEachIndexed { i, config ->
                    zip.putNextEntry(ZipEntry("${baseName}_${i + 1}.conf"))
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
