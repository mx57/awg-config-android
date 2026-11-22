package com.awgconfig.android.core.vpnlink

import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.Deflater

/**
 * vpn://-ссылка AmneziaVPN (one-tap import) — порт src/server/vpnLinkBuilder.js донора:
 * JSON → qCompress (4-байтный BE-префикс длины + zlib) → base64url без padding.
 */
object VpnLinkBuilder {

    private val AWG_KEYS = listOf(
        "Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4",
        "I1", "I2", "I3", "I4", "I5", "HeaderProtectionKey", "ContentPaddingAddition",
        "RekeyAfterTime", "RekeyTimeout", "RejectAfterTime", "KeepaliveTimeout",
        "MaxHandshakeAttempts", "RandomTrailers", "DisableCookies",
    )

    private fun qCompress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(4096)
        val compressed = mutableListOf<Byte>()
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            for (i in 0 until n) compressed.add(buffer[i])
        }
        deflater.end()
        val prefix = byteArrayOf(
            ((data.size ushr 24) and 0xff).toByte(),
            ((data.size ushr 16) and 0xff).toByte(),
            ((data.size ushr 8) and 0xff).toByte(),
            (data.size and 0xff).toByte(),
        )
        return prefix + compressed.toByteArray()
    }

    private fun toBase64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun confMap(conf: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in conf.split("\n")) {
            val t = line.trim()
            if (t.startsWith("[") && t.endsWith("]")) continue
            val i = t.indexOf('=')
            if (i > 0) map[t.substring(0, i).trim()] = t.substring(i + 1).trim()
        }
        return map
    }

    private fun extractMtu(conf: String): String =
        Regex("""^\s*MTU\s*=\s*(\d+)\s*$""", RegexOption.MULTILINE).find(conf)?.groupValues?.get(1) ?: "1280"

    private fun extractPort(conf: String): String =
        Regex("""^\s*Endpoint\s*=\s*.*:(\d+)\s*$""", RegexOption.MULTILINE).find(conf)?.groupValues?.get(1) ?: "4500"

    private fun extractEndpointHost(conf: String): String? {
        val m = Regex("""^\s*Endpoint\s*=\s*(?:\[([^]]+)]|([^:\s]+)):\d+\s*$""", RegexOption.MULTILINE).find(conf) ?: return null
        val g1 = m.groupValues[1]
        val g2 = m.groupValues[2]
        return when {
            g1.isNotEmpty() -> g1
            g2.isNotEmpty() -> g2
            else -> null
        }
    }

    private fun buildLastConfig(confText: String, fallbackHost: String): JSONObject {
        val map = confMap(confText)
        val last = JSONObject()
        last.put("config", confText)
        last.put("hostName", extractEndpointHost(confText) ?: fallbackHost)
        last.put("port", extractPort(confText).toInt())
        map["PrivateKey"]?.let { last.put("client_priv_key", it) }
        map["Address"]?.let { last.put("client_ip", it) }
        (map["PresharedKey"] ?: map["PreSharedKey"])?.let { last.put("psk_key", it) }
        map["PublicKey"]?.let { last.put("server_pub_key", it) }
        last.put("mtu", extractMtu(confText))
        map["PersistentKeepalive"]?.let { last.put("persistent_keep_alive", it) }
        last.put("allowed_ips", JSONArray(map["AllowedIPs"]?.split(Regex("\\s*,\\s*"))?.filter { it.isNotEmpty() } ?: emptyList<String>()))
        for (key in AWG_KEYS) map[key]?.let { last.put(key, it) }
        return last
    }

    /** protocol_version: 1.5 / 2 / 3.1 (awg3 → null, vpn:// недоступен). */
    fun protocolVersion(profileKey: String): String? = when (profileKey) {
        "awg2" -> "2"
        "awg31" -> "3.1"
        "awg3" -> null
        else -> "1.5"
    }

    /**
     * @param confText полный текст .conf
     * @param hostName хост endpoint'а для верхнего уровня
     * @param dns1/dns2 DNS для верхнего уровня
     * @param profileKey legacy / awg2 / awg3 / awg31
     */
    fun build(confText: String, hostName: String, dns1: String, dns2: String, profileKey: String): String? {
        val protocolVersion = protocolVersion(profileKey) ?: return null
        val fallbackHost = hostName.ifEmpty { "engage.cloudflareclient.com" }
        val lastConfig = buildLastConfig(confText, fallbackHost)

        val awg = JSONObject().apply {
            put("last_config", lastConfig.toString())
            put("isThirdPartyConfig", true)
            put("port", extractPort(confText))
            put("protocol_version", protocolVersion)
            put("transport_proto", "udp")
        }
        val container = JSONObject().apply {
            put("container", "amnezia-awg")
            put("awg", awg)
        }
        val outer = JSONObject().apply {
            put("containers", JSONArray().put(container))
            put("defaultContainer", "amnezia-awg")
            put("description", "WARP ($profileKey) via AWG Config Generator")
            put("dns1", dns1.ifEmpty { "1.1.1.1" })
            put("dns2", dns2.ifEmpty { "1.0.0.1" })
            put("hostName", lastConfig.getString("hostName"))
        }
        val compressed = qCompress(outer.toString().toByteArray(Charsets.UTF_8))
        return "vpn://" + toBase64Url(compressed)
    }
}
