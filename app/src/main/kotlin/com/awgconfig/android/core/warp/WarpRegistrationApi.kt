package com.awgconfig.android.core.warp

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Регистрация устройства Cloudflare WARP через официальный API — точный порт
 * src/server/warpRegistration.js донора (3.1.7 байт-в-байт):
 * POST reg → PATCH reg/{id} (warp_enabled=true) → GET reg/{id} (обновлённый конфиг).
 */
object WarpRegistrationApi {

    private const val API_HOST = "api.cloudflareclient.com"
    private const val API_PREFIX = "/v0i1909051800"
    private const val REQUEST_TIMEOUT_MS = 20000
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val USER_AGENT = "okhttp/3.12.1"

    private const val RETRY_MAX_ATTEMPTS = 3
    private const val RETRY_BASE_DELAY_MS = 450L
    private const val RETRY_MAX_DELAY_MS = 4000L

    /** Класс отказа регистрации — как registrationClass донора. */
    enum class RefusalClass { RATE_LIMITED, UNAVAILABLE, REJECTED, MALFORMED }

    class RegistrationException(
        val refusalClass: RefusalClass,
        message: String,
        val upstreamStatus: Int? = null,
        val retryAfterS: Long? = null,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    data class RegistrationResult(
        val peerPublicKey: String?,
        val clientIpv4: String,
        val clientIpv6: String?,
        val tokenId: String,
    )

    fun refusalText(cls: RefusalClass): String = when (cls) {
        RefusalClass.RATE_LIMITED ->
            "Cloudflare временно ограничил создание устройств WARP. Повторите через минуту; если ошибка держится, это сбой на стороне Cloudflare."
        RefusalClass.UNAVAILABLE ->
            "Сервис регистрации Cloudflare WARP сейчас не отвечает. Повторите позже."
        RefusalClass.REJECTED ->
            "Cloudflare не принял запрос регистрации WARP. Это сбой на стороне сервиса, а не ваших настроек."
        RefusalClass.MALFORMED ->
            "Cloudflare вернул некорректный ответ регистрации."
    }

    private fun isValidIPv4(s: String?): Boolean {
        if (s == null) return false
        val parts = s.split(".")
        if (parts.size != 4) return false
        return parts.all { p ->
            p.length in 1..3 && p.all { it.isDigit() } && p.toInt() in 0..255
        }
    }

    private fun isValidIPv6(s: String?): Boolean =
        s != null && Regex("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$|^::\$").matches(s)

    private class HttpResponse(val status: Int, val body: String, val retryAfterHeader: String?)

    /** Открывалка соединений. Внедряется из AwgApp: при активном VPN привязывает
     * сокеты регистрации к базовой (не-VPN) сети, чтобы регистрация не зависела
     * от состояния туннеля. В JVM-тестах остаётся null — обычный openConnection. */
    internal var connectionOpener: ((URL) -> java.net.URLConnection)? = null

    private fun requestOnce(method: String, path: String, body: JSONObject?, token: String?): HttpResponse {
        val url = URL("https://$API_HOST$API_PREFIX/$path")
        val conn = (connectionOpener?.invoke(url) ?: url.openConnection()) as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = REQUEST_TIMEOUT_MS.toInt()
            conn.readTimeout = REQUEST_TIMEOUT_MS.toInt()
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            val payload = body?.toString()?.toByteArray(Charsets.UTF_8)
            if (payload != null) {
                conn.setFixedLengthStreamingMode(payload.size)
                conn.doOutput = true
                conn.outputStream.use { it.write(payload) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val bodyBytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (bodyBytes.size > MAX_RESPONSE_BYTES) {
                throw RegistrationException(RefusalClass.MALFORMED, "Ответ Cloudflare API слишком большой.")
            }
            HttpResponse(status, String(bodyBytes, Charsets.UTF_8), conn.getHeaderField("Retry-After"))
        } catch (e: IOException) {
            throw RegistrationException(
                RefusalClass.UNAVAILABLE,
                "Ошибка запроса к Cloudflare API: ${e.message ?: e.javaClass.simpleName}",
                retryAfterS = 60,
                cause = e,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun statusException(res: HttpResponse, message: String): RegistrationException {
        if (res.status == 429) {
            val ra = res.retryAfterHeader?.trim()?.toLongOrNull()
            return RegistrationException(
                RefusalClass.RATE_LIMITED, message,
                upstreamStatus = res.status,
                retryAfterS = if (ra != null && ra in 1..3600) ra else 60,
            )
        }
        if (res.status == 408 || (res.status in 500..599 && res.status != 501)) {
            return RegistrationException(RefusalClass.UNAVAILABLE, message, upstreamStatus = res.status, retryAfterS = 60)
        }
        return RegistrationException(RefusalClass.REJECTED, message, upstreamStatus = res.status)
    }

    private fun isTransient(err: RegistrationException): Boolean {
        val code = err.upstreamStatus
        if (code == 429 || code == 502 || code == 503 || code == 504) return true
        return err.refusalClass == RefusalClass.UNAVAILABLE && err.cause is IOException
    }

    private fun request(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        var lastError: RegistrationException? = null
        for (attempt in 0 until RETRY_MAX_ATTEMPTS) {
            try {
                val res = requestOnce(method, path, body, token)
                if (res.status in 200..299) {
                    return try {
                        if (res.body.isBlank()) JSONObject() else JSONObject(res.body)
                    } catch (e: Exception) {
                        throw RegistrationException(
                            RefusalClass.MALFORMED, "Не удалось обработать ответ сервера.",
                            upstreamStatus = res.status, cause = e,
                        )
                    }
                }
                val apiMessage = try { JSONObject(res.body).optString("message") } catch (_: Exception) { null }
                throw statusException(res, apiMessage ?: "Ошибка с кодом ${res.status}")
            } catch (err: RegistrationException) {
                lastError = err
                if (!isTransient(err) || attempt == RETRY_MAX_ATTEMPTS - 1) throw err
                val jitter = Rng.nextInt(320)
                val delay = minOf(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS shl attempt) + jitter
                try {
                    sleepHook(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw err
                }
            }
        }
        throw lastError ?: RegistrationException(RefusalClass.MALFORMED, "Неизвестная ошибка регистрации")
    }

    private object Rng {
        private val rnd = java.security.SecureRandom()
        fun nextInt(bound: Int) = rnd.nextInt(bound)
    }

    /** Регистрирует новое устройство для publicKey (base64 X25519). */
    fun registerDevice(publicKey: String): RegistrationResult {
        val regBody = JSONObject().apply {
            put("install_id", "")
            put("tos", java.time.Instant.now().toString())
            put("key", publicKey)
            put("fcm_token", "")
            put("type", "ios")
            put("locale", "en_US")
        }
        val regResponse = request("POST", "reg", regBody, null)
        val result = regResponse.optJSONObject("result")
        val id = result?.optString("id")
        val token = result?.optString("token")
        if (id.isNullOrEmpty() || token.isNullOrEmpty()) {
            throw RegistrationException(RefusalClass.MALFORMED, "В ответе регистрации нет id или token")
        }
        request("PATCH", "reg/$id", JSONObject().put("warp_enabled", true), token)

        var config = result.optJSONObject("config")
        if (config?.optJSONArray("peers") == null || config.optJSONArray("peers").length() == 0) {
            throw RegistrationException(RefusalClass.MALFORMED, "Недостающие данные для конфигурации WARP")
        }
        // Обновлённый конфиг после включения warp (как mergeConfigAfterWarp донора)
        try {
            val refreshed = request("GET", "reg/$id", null, token).optJSONObject("result")?.optJSONObject("config")
            if (refreshed?.optJSONArray("peers") != null && refreshed.optJSONArray("peers").length() > 0) {
                config = refreshed
            }
        } catch (_: Exception) {
            // не фатально: используем конфиг из POST
        }

        val addresses = config?.optJSONObject("interface")?.optJSONObject("addresses")
        val v4 = addresses?.optString("v4")
        val v6 = addresses?.optString("v6")
        if (!isValidIPv4(v4)) {
            throw RegistrationException(RefusalClass.MALFORMED, "Cloudflare вернул некорректный IPv4: $v4")
        }
        val peerKey = config.optJSONArray("peers").optJSONObject(0)?.optString("public_key")
        return RegistrationResult(
            peerPublicKey = peerKey?.takeIf { it.isNotBlank() },
            clientIpv4 = v4!!,
            clientIpv6 = v6?.takeIf { isValidIPv6(it) },
            tokenId = id,
        )
    }

    /** Тестовый хук для JVM-тестов. */
    internal var sleepHook: (Long) -> Unit = { Thread.sleep(it) }
}
