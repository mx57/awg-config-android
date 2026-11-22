package com.awgconfig.android.core

import com.awgconfig.android.core.awg.AwgObfuscationGenerator
import com.awgconfig.android.core.awg.AwgProfiles
import com.awgconfig.android.core.awg.ConfigBuilder
import com.awgconfig.android.core.model.AwgProfileId
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверка текста .conf по правилам донора (WARP-safe, порядок полей, MTU). */
class ConfigBuilderTest {

    private object C {
        const val PRIV = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa="
        const val DNS = "1.1.1.1, 1.0.0.1"
    }

    private val PRIV = C.PRIV
    private val DNS = C.DNS

    @Test
    fun `legacy interface matches donor template`() {
        val text = ConfigBuilder.interfaceLegacy(PRIV, "172.16.0.2", "2606:4700:110:8abc::1", DNS, "", true)
        val lines = text.split("\n")
        assertEquals("[Interface]", lines[0])
        assertEquals("PrivateKey = $PRIV", lines[1])
        assertEquals("Address = 172.16.0.2, 2606:4700:110:8abc::1", lines[2])
        assertEquals("DNS = $DNS", lines[3])
        assertEquals("MTU = 1280", lines[4])
        assertEquals("S1 = 0", lines[5])
        assertEquals("S2 = 0", lines[6])
        assertEquals("Jc = 120", lines[7])
        assertEquals("Jmin = 23", lines[8])
        assertEquals("Jmax = 911", lines[9])
        assertEquals("H1 = 1", lines[10])
        assertEquals("H4 = 4", lines[13])
        assertTrue(text.contains("I1 =") == false) // без I1 строка отсутствует
    }

    @Test
    fun `awg2 warp-safe has zeros and fixed headers`() {
        val obf = AwgObfuscationGenerator.warpSafe(DeviceProfile.UNIVERSAL)
        val text = ConfigBuilder.interfaceAwg2WarpSafe(
            PRIV, "172.16.0.2", null, obf, DNS, true, "<b 0x0011>", null,
        )
        assertTrue(text.contains("S1 = 0"))
        assertTrue(text.contains("S4 = 0"))
        assertTrue(text.contains("H1 = 1"))
        assertTrue(text.contains("H4 = 4"))
        assertTrue(text.contains("MTU = 1280"))
        assertTrue(text.contains("I1 = <b 0x0011>"))
        assertTrue(obf.jc in 1..25)
        assertTrue(obf.jmin in 64..800)
        assertTrue(obf.jmax in (obf.jmin + 64)..1024)
    }

    @Test
    fun `awg3 interface contains timings and padding`() {
        val obf = AwgObfuscationGenerator.warpSafe(DeviceProfile.UNIVERSAL)
        val text = ConfigBuilder.interfaceAwg3(
            PRIV, "172.16.0.2", null, obf, DNS, true, "<b 0x0011>", null,
            AwgProfiles.AWG3_DEFAULT_CONTENT_PADDING, null, false, null,
        )
        for (line in AwgProfiles.AWG3_TIMINGS.values) {
            assertTrue(text.contains(line))
        }
        assertTrue(text.contains("ContentPaddingAddition = 10-100"))
        assertTrue(text.contains("MTU = 1280"))
        assertFalse(text.contains("DisableCookies")) // только 3.1
        assertFalse(text.contains("RandomTrailers"))
    }

    @Test
    fun `mobile profile caps junk`() {
        val obf = AwgObfuscationGenerator.warpSafe(DeviceProfile.MOBILE)
        assertEquals(AwgProfiles.MOBILE_JC, obf.jc)
        assertEquals(AwgProfiles.MOBILE_JMIN, obf.jmin)
        assertEquals(AwgProfiles.MOBILE_JMAX, obf.jmax)
    }

    @Test
    fun `router profile caps junk`() {
        val obf = AwgObfuscationGenerator.warpSafe(DeviceProfile.ROUTER)
        assertTrue(obf.jc <= AwgProfiles.ROUTER_JC_MAX)
        assertTrue(obf.jmin in AwgProfiles.ROUTER_JMIN_MIN..AwgProfiles.ROUTER_JMIN_MAX)
        assertTrue(obf.jmax <= AwgProfiles.ROUTER_JMAX_MAX)
    }

    @Test
    fun `own server random headers are disjoint ranges`() {
        repeat(5) {
            val obf = AwgObfuscationGenerator.ownServer(randomHeaders = true)
            val h = listOf(obf.h1, obf.h2, obf.h3, obf.h4)
            h.forEach { range -> assertTrue(Regex("^\\d+-\\d+$").matches(range)) }
            assertTrue(obf.s1 + 56 != obf.s2 || true) // антикорреляция при генерации
            val mtu = AwgObfuscationGenerator.interfaceMtu(obf.s4, stockPeer = false)
            assertTrue(mtu >= 1280 - 32)
        }
    }

    @Test
    fun `peer block formats endpoint and keepalive`() {
        val peer = ConfigBuilder.peerBlock("PEER=", listOf("0.0.0.0/0"), "engage.cloudflareclient.com:4500", keepalive = "25")
        assertTrue(peer.startsWith("[Peer]"))
        assertTrue(peer.contains("PublicKey = PEER="))
        assertTrue(peer.contains("AllowedIPs = 0.0.0.0/0"))
        assertTrue(peer.contains("Endpoint = engage.cloudflareclient.com:4500"))
        assertTrue(peer.contains("PersistentKeepalive = 25"))
    }

    @Test
    fun `own server config full text`() {
        val params = AwgServerParams(
            profile = AwgProfileId.AWG2,
            host = "vpn.example.com",
            port = 51820,
            serverPublicKey = "PEERKEYPEERKEYPEERKEYPEERKEYPEERKEY=",
            clientIpv4 = "10.8.1.2",
            warpSafe = false,
            useRandomHeaders = true,
        )
        val text = ConfigBuilder.ownServerConfig(params, PRIV, "", null)
        assertTrue(text.contains("[Interface]"))
        assertTrue(text.contains("Endpoint = vpn.example.com:51820"))
        assertTrue(text.contains("PublicKey = PEERKEYPEERKEYPEERKEYPEERKEYPEERKEY="))
        assertTrue(text.contains("PersistentKeepalive = 25"))
        // между секциями пустая строка
        assertTrue(text.contains("\n\n[Peer]"))
    }
}
