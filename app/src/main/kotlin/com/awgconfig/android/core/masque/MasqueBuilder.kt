package com.awgconfig.android.core.masque

import com.awgconfig.android.core.model.MasqueParams
import com.awgconfig.android.core.util.Rng
import org.json.JSONObject

/**
 * Генератор MASQUE-конфигов (дополнение к донору): masque://-URI для клиентов
 * с поддержкой MASQUE (sing-box, Hiddify и др.) + фрагмент JSON outbound для sing-box.
 *
 * Cloudflare MASQUE — HTTP/3 CONNECT-UDP прокси; ссылка вида
 * masque://<token>@<host>:<port>/?congestion_control=bbr&udp_relay=on&sni=<sni>#<label>
 */
object MasqueBuilder {

    fun randomToken(length: Int = 32): String {
        val alphabet = "0123456789abcdef"
        return buildString(length) { repeat(length) { append(alphabet[Rng.nextInt(alphabet.length)]) } }
    }

    data class MasqueResult(
        val uri: String,
        val singBoxJson: String?,
        val host: String,
        val port: Int,
        val sni: String,
        val token: String,
    )

    fun build(params: MasqueParams): MasqueResult {
        val token = params.token.ifBlank { randomToken() }
        val host = params.host.trim()
        val sni = params.sni.ifBlank { host }.trim()
        val query = "congestion_control=bbr&udp_relay=on&sni=$sni"
        val uri = "masque://$token@$host:${params.port}/?$query#${params.label.replace(" ", "%20")}"

        val singBoxJson = if (params.includeSingBox) {
            JSONObject().apply {
                put("type", "h3")
                put("tag", "masque-out")
                put("server", host)
                put("server_port", params.port)
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", sni)
                    put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome"))
                })
                put("masque", JSONObject().apply {
                    put("enabled", true)
                    put("path", "/")
                })
            }.toString(2)
        } else null

        return MasqueResult(uri, singBoxJson, host, params.port, sni, token)
    }
}
