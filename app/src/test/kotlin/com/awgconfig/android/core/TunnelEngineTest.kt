package com.awgconfig.android.core

import com.awgconfig.android.core.check.ConfigChecker
import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.tunnel.TunnelCore
import org.amnezia.awg.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Проверки вокруг встроенного движка AmneziaWG:
 * подготовка конфига (WARP-стриппинг vs полный AWG), реальный разбор
 * конфига парсером org.amnezia.awg и ConfigChecker.
 */
class TunnelEngineTest {

    private val peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    /** Настоящая пара ключей — парсер валидирует формат X25519 по-настоящему. */
    private val realPrivateKey: String =
        com.awgconfig.android.core.crypto.KeyPair().privateKey.toBase64()

    /** Полный AWG-конфиг собственного сервера со всеми полями обфускации. */
    private val awgConf = """
        [Interface]
        PrivateKey = $realPrivateKey
        Address = 10.8.1.2/32
        DNS = 1.1.1.1
        MTU = 1280
        Jc = 12
        Jmin = 233
        Jmax = 811
        S1 = 0
        S2 = 0
        S3 = 0
        S4 = 0
        H1 = 1
        H2 = 2
        H3 = 3
        H4 = 4
        I1 = <b 0x00>
        ContentPaddingAddition = 10-100
        [Peer]
        PublicKey = $peerPublicKey
        AllowedIPs = 0.0.0.0/0
        Endpoint = engage.cloudflareclient.com:4500
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `awg server config passes as-is and engine parser accepts all obfuscation fields`() {
        val prepared = TunnelCore.prepareConf(awgConf, ProtocolKind.AWG_SERVER)
        assertEquals(awgConf.trim(), prepared.trim())
        // реальный разбор парсером движка AmneziaWG
        val config = Config.parse(ByteArrayInputStream(prepared.toByteArray(Charsets.UTF_8)))
        assertEquals(1, config.getPeers().size)
        assertTrue(config.getInterface().getAddresses().isNotEmpty())
    }

    @Test
    fun `warp config is stripped to plain wireguard and still parses`() {
        val prepared = TunnelCore.prepareConf(awgConf, ProtocolKind.WARP)
        assertFalse(prepared.contains("Jc"))
        assertFalse(prepared.contains("S1"))
        assertFalse(prepared.contains("H1"))
        assertFalse(prepared.contains("I1"))
        assertFalse(prepared.contains("ContentPaddingAddition"))
        assertTrue(prepared.contains("PrivateKey = " + realPrivateKey.take(8)))
        assertTrue(prepared.contains("Endpoint = engage.cloudflareclient.com:4500"))
        val config = Config.parse(ByteArrayInputStream(prepared.toByteArray(Charsets.UTF_8)))
        assertEquals(1, config.getPeers().size)
    }

    @Test
    fun `structural check reports bad key format with human text`() {
        val broken = awgConf.replace(
            "PrivateKey = $realPrivateKey",
            "PrivateKey = not-a-key",
        )
        val result = ConfigChecker.structural(broken, ProtocolKind.AWG_SERVER)
        assertFalse(result.ok)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `structural check passes for valid warp config`() {
        val prepared = TunnelCore.prepareConf(awgConf, ProtocolKind.WARP)
        val result = ConfigChecker.structural(prepared, ProtocolKind.WARP)
        assertTrue(result.ok)
    }

    @Test
    fun `masque structural check validates uri`() {
        val good = ConfigChecker.structural(
            "masque://masque.cloudflare.com:443/mas?token=abc",
            ProtocolKind.MASQUE,
        )
        assertTrue(good.ok)
        val bad = ConfigChecker.structural(
            "notmasque:///",
            ProtocolKind.MASQUE,
        )
        assertFalse(bad.ok)
    }

    @Test
    fun `extract endpoint finds host and port`() {
        val ep = ConfigChecker.extractEndpoint(awgConf, ProtocolKind.AWG_SERVER)
        assertEquals("engage.cloudflareclient.com:4500", ep)
    }

    @Test
    fun `quick report verdict invalid on broken structure`() {
        val broken = GeneratedConfig(
            id = "test-1",
            protocolKind = ProtocolKind.AWG_SERVER,
            title = "t",
            text = "[Interface]\nGarbage = 1\n",
        )
        val report = ConfigChecker.quick(broken)
        assertFalse(report.structuralOk)
        assertEquals(com.awgconfig.android.core.model.TestVerdict.INVALID, report.verdict)
        assertNotNull(report.structuralErrors.firstOrNull())
    }
}
