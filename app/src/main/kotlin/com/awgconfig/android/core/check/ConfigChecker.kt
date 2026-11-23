package com.awgconfig.android.core.check

import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.core.model.TestReport
import com.awgconfig.android.tunnel.TunnelCore
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.BadConfigException.Reason
import org.amnezia.awg.config.Config
import org.amnezia.awg.config.ParseException
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI

/**
 * Проверка валидности конфигов.
 *
 *  - Структурная (реальная): полный разбор конфига движком AmneziaWG
 *    (org.amnezia.awg.config.Config.parse) — проверяются ключи, форматы ключей
 *    X25519, адреса, AllowedIPs, endpoint; для MASQUE — разбор masque:// URI.
 *  - Endpoint (реальная): DNS-резолв хоста endpoint с замером времени.
 *  - Тест через туннель (REAL) — в TunnelCore.realTest().
 */
object ConfigChecker {

    /** Быстрая проверка: структура + DNS endpoint. */
    fun quick(config: GeneratedConfig): TestReport {
        val structural = structural(config.text, config.protocolKind)
        val endpoint = extractEndpoint(config.text, config.protocolKind)
        var dnsOk: Boolean? = null
        var latency: Long? = null
        var endpointError: String? = null
        if (structural.ok && endpoint != null) {
            val host = endpoint.substringBeforeLast(":").removePrefix("[").removeSuffix("]")
            try {
                val start = System.currentTimeMillis()
                val addresses = InetAddress.getAllByName(host)
                latency = System.currentTimeMillis() - start
                dnsOk = addresses.isNotEmpty()
                if (!dnsOk) endpointError = "host не разрешается"
            } catch (e: Exception) {
                dnsOk = false
                endpointError = e.message ?: e.javaClass.simpleName
            }
        }
        return TestReport(
            configId = config.id,
            level = com.awgconfig.android.core.model.TestLevel.QUICK,
            structuralOk = structural.ok,
            structuralErrors = structural.errors,
            endpoint = endpoint,
            dnsOk = dnsOk,
            endpointLatencyMs = latency,
            endpointError = endpointError,
        )
    }

    /** Полный разбор конфига движком: список проблем (пустой = ок). */
    fun structural(text: String, kind: ProtocolKind): Result {
        if (kind == ProtocolKind.MASQUE) return structuralMasque(text)
        // WARP-конфиг проверяется в том виде, в каком уйдёт в движок
        val prepared = TunnelCore.prepareConf(text, kind)
        try {
            Config.parse(ByteArrayInputStream(prepared.toByteArray(Charsets.UTF_8)))
            return Result(true, emptyList())
        } catch (e: BadConfigException) {
            val location = listOfNotNull(
                e.section?.name?.lowercase(),
                e.location?.name?.lowercase()?.replace('_', ' '),
            ).joinToString(" · ")
            val reasonText = when (e.reason) {
                Reason.INVALID_KEY -> "некорректный ключ"
                Reason.INVALID_NUMBER -> "некорректное число"
                Reason.INVALID_VALUE -> "некорректное значение"
                Reason.MISSING_ATTRIBUTE -> "отсутствует обязательное поле"
                Reason.MISSING_SECTION -> "отсутствует секция"
                Reason.SYNTAX_ERROR -> "синтаксическая ошибка"
                Reason.UNKNOWN_ATTRIBUTE -> "неизвестное поле"
                Reason.UNKNOWN_SECTION -> "неизвестная секция"
                null -> "ошибка разбора"
                else -> "ошибка разбора"
            }
            val text = e.text?.toString()
            return Result(
                false,
                listOf(
                    buildString {
                        if (location.isNotBlank()) append("[$location] ")
                        append(reasonText)
                        if (!text.isNullOrBlank()) append(": $text")
                    },
                ),
            )
        } catch (e: ParseException) {
            return Result(false, listOf(e.message ?: "синтаксическая ошибка"))
        } catch (e: Exception) {
            return Result(false, listOf(e.message ?: e.javaClass.simpleName))
        }
    }

    private fun structuralMasque(text: String): Result {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return Result(false, listOf("пустой конфиг"))
        val uri = try {
            URI(firstLine.trim())
        } catch (e: Exception) {
            return Result(false, listOf("masque:// URI не разбирается: ${e.message ?: e.javaClass.simpleName}"))
        }
        val errors = mutableListOf<String>()
        if (uri.scheme != "masque") errors.add("схема должна быть masque://, а не ${uri.scheme ?: "(нет)"}")
        if (uri.host.isNullOrBlank()) errors.add("отсутствует хост")
        return Result(errors.isEmpty(), errors)
    }

    /** Endpoint из конфига: host:port (для MASQUE — авторитет URI). */
    fun extractEndpoint(text: String, kind: ProtocolKind): String? {
        if (kind == ProtocolKind.MASQUE) {
            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
            return try {
                val uri = URI(firstLine.trim())
                listOfNotNull(uri.host, uri.port.takeIf { it > 0 }).joinToString(":")
            } catch (_: Exception) {
                null
            }
        }
        return text.lineSequence()
            .firstOrNull { it.trim().startsWith("Endpoint") && it.contains("=") }
            ?.substringAfter("=")?.trim()
    }

    data class Result(val ok: Boolean, val errors: List<String>)
}
