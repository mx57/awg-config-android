package com.awgconfig.android.core

import com.awgconfig.android.core.vpnlink.VpnLinkBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.InflaterInputStream

/** vpn://-ссылка: qCompress + base64url, формат JSON как у донора. */
class VpnLinkBuilderTest {

    private val conf = """
        [Interface]
        PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
        Address = 172.16.0.2
        DNS = 1.1.1.1, 1.0.0.1
        Jc = 12
        Jmin = 233
        Jmax = 811
        S1 = 0
        S2 = 0
        H1 = 1
        MTU = 1280
        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        AllowedIPs = 0.0.0.0/0
        Endpoint = 162.159.192.1:4500
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `protocol version mapping`() {
        assertEquals("1.5", VpnLinkBuilder.protocolVersion("legacy"))
        assertEquals("2", VpnLinkBuilder.protocolVersion("awg2"))
        assertEquals("3.1", VpnLinkBuilder.protocolVersion("awg31"))
        assertNull(VpnLinkBuilder.protocolVersion("awg3"))
    }

    @Test
    fun `vpn link round trip`() {
        val link = VpnLinkBuilder.build(conf, "162.159.192.1", "1.1.1.1", "1.0.0.1", "awg2")
        assertNotNull(link)
        assertTrue(link!!.startsWith("vpn://"))

        val base64 = link.removePrefix("vpn://")
        assertTrue(base64.none { it == '=' }) // без padding
        val compressed = Base64.getUrlDecoder().decode(base64)

        // qCompress: 4-байтный BE-префикс длины + zlib
        val declaredLength = ((compressed[0].toInt() and 0xff) shl 24) or
            ((compressed[1].toInt() and 0xff) shl 16) or
            ((compressed[2].toInt() and 0xff) shl 8) or
            (compressed[3].toInt() and 0xff)
        val payload = compressed.copyOfRange(4, compressed.size)
        val jsonBytes = InflaterInputStream(payload.inputStream()).readBytes()
        assertEquals(declaredLength, jsonBytes.size)

        val json = JSONObject(String(jsonBytes))
        assertEquals("amnezia-awg", json.getString("defaultContainer"))
        val container = json.getJSONArray("containers").getJSONObject(0)
        assertEquals("amnezia-awg", container.getString("container"))
        val awg = container.getJSONObject("awg")
        assertEquals("2", awg.getString("protocol_version"))
        assertEquals("udp", awg.getString("transport_proto"))
        assertEquals(true, awg.getBoolean("isThirdPartyConfig"))
        assertEquals("4500", awg.getString("port"))

        val last = JSONObject(awg.getString("last_config"))
        assertEquals(conf, last.getString("config"))
        assertEquals("162.159.192.1", last.getString("hostName"))
        assertEquals(4500, last.getInt("port"))
        assertEquals("12", last.getString("Jc"))
        assertTrue(last.getJSONArray("allowed_ips").length() > 0)
    }

    @Test
    fun `awg3 has no vpn link`() {
        assertNull(VpnLinkBuilder.build(conf, "h", "1.1.1.1", "1.0.0.1", "awg3"))
    }
}
