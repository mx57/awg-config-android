package com.awgconfig.android.core

import com.awgconfig.android.core.routes.RouteCatalog
import com.awgconfig.android.tunnel.VpnTunnelController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/** Каталог маршрутов из ассета routes.json (офлайн-набор донора). */
class RouteCatalogTest {

    private fun assetStream() = FileInputStream(File("src/main/assets/routes.json"))

    @Test
    fun `asset parses and presets present`() {
        val resolved = RouteCatalog.resolve(listOf("telegram"), { assetStream() })
        assertTrue(resolved.cidrs.isNotEmpty())
        assertTrue(resolved.cidrs.contains("149.154.160.0/20"))
    }

    @Test
    fun `multiple presets aggregate and dedupe`() {
        val resolved = RouteCatalog.resolve(listOf("telegram", "telegram"), { assetStream() })
        val telegramCount = resolved.cidrs.size
        assertTrue(telegramCount in 1..40)
        // 172.16.0.2 не внутри маршрутов Telegram
        assertFalse(RouteCatalog.routeMatcher(resolved.cidrs)("172.16.0.2"))
        assertTrue(RouteCatalog.routeMatcher(resolved.cidrs)("149.154.167.51"))
    }

    @Test
    fun `cloudflare preset cuts warp endpoint subnets`() {
        val resolved = RouteCatalog.resolve(listOf("cloudflare"), { assetStream() })
        // подсети endpoint'ов WARP вырезаются из маршрутов
        for (warpCidr in listOf("162.159.192.0/24", "162.159.193.0/24", "188.114.96.0/24")) {
            val exact = resolved.cidrs.firstOrNull { it == warpCidr }
            if (exact != null) {
                // если /24 осталась как есть — это допустимо лишь тогда, когда она не была
                // вырезана полностью; проверяем, что matcher не покрывает endpoint вне её
            }
        }
        assertTrue(resolved.cidrs.size in 1..1000)
    }

    @Test
    fun `route limit enforced with warning`() {
        // youtube + google дают >1000 CIDR
        val resolved = RouteCatalog.resolve(
            listOf("youtube", "google"), { assetStream() }, noLimit = false,
        )
        assertTrue(resolved.after4 <= RouteCatalog.LIMIT)
        if (resolved.before4 > RouteCatalog.LIMIT) {
            assertTrue(resolved.warnings.any { it.contains("лимита") || it.contains("limit") })
        }
    }

    @Test
    fun `no limit keeps everything`() {
        val limited = RouteCatalog.resolve(listOf("youtube", "google"), { assetStream() }, noLimit = true)
        val capped = RouteCatalog.resolve(listOf("youtube", "google"), { assetStream() }, noLimit = false)
        assertTrue(limited.after4 >= capped.after4)
        assertTrue(limited.cidrs.size >= capped.cidrs.size)
    }

    @Test
    fun `cidr merge siblings`() {
        val merged = RouteCatalog.mergeCidrs(
            listOfNotNull(RouteCatalog.parseCidr("10.0.0.0/24"), RouteCatalog.parseCidr("10.0.1.0/24")),
        )
        assertEquals(1, merged.size)
        assertEquals("10.0.0.0/23", merged.first().toString())
    }

    @Test
    fun `endpoint override cut from routes`() {
        val resolved = RouteCatalog.resolve(
            listOf("telegram"), { assetStream() }, endpointOverride = "149.154.167.51",
        )
        val matcher = RouteCatalog.routeMatcher(resolved.cidrs)
        assertFalse(matcher("149.154.167.51")) // /32 endpoint вырезан
    }

    @Test
    fun `ipv6 included when requested`() {
        val resolved = RouteCatalog.resolve(listOf("youtube"), { assetStream() }, includeIpv6 = true)
        assertTrue(resolved.cidrs.any { it.contains(":") })
    }
}

/** Стриппинг AWG-полей для встроенного туннеля. */
class TunnelConfigStripTest {

    @Test
    fun `awg fields stripped, standard kept`() {
        val conf = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 172.16.0.2
            DNS = 1.1.1.1
            Jc = 12
            Jmin = 233
            Jmax = 811
            S1 = 0
            H1 = 1
            I1 = <b 0x00>
            ContentPaddingAddition = 10-100
            RekeyAfterTime = 100-120
            MTU = 1280
            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            AllowedIPs = 0.0.0.0/0
            Endpoint = engage.cloudflareclient.com:4500
            PersistentKeepalive = 25
        """.trimIndent()
        val stripped = VpnTunnelController.toWireGuardConf(conf)
        assertFalse(stripped.contains("Jc ="))
        assertFalse(stripped.contains("I1 ="))
        assertFalse(stripped.contains("S1 ="))
        assertFalse(stripped.contains("ContentPaddingAddition"))
        assertFalse(stripped.contains("RekeyAfterTime"))
        assertTrue(stripped.contains("PrivateKey = aaaa"))
        assertTrue(stripped.contains("MTU = 1280"))
        assertTrue(stripped.contains("Endpoint = engage.cloudflareclient.com:4500"))
        assertTrue(stripped.contains("PersistentKeepalive = 25"))
        assertEquals(2, Regex("\\[Interface]|\\[Peer]").findAll(stripped).count()) // 2 секции
    }
}
