package com.awgconfig.android.core.warp

import com.awgconfig.android.core.util.Rng
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Выбор endpoint WARP — порт FALLBACK_ENDPOINTS/selectBestEndpointIp донора:
 * hostname engage.cloudflareclient.com, фиксированные IP из 5 /24, TCP-пре-чек 800 мс.
 */
object EndpointSelector {

    const val ENGAGE_HOST = "engage.cloudflareclient.com"
    const val DEFAULT_PORT = 4500

    /** Allowlist портов, которые WARP принимает надёжно. */
    val PORT_ALLOWLIST = listOf(2408, 500, 4500, 1701, 880, 8854)

    data class FallbackEndpoint(val ip: String, val cidr24: String)

    /** Кураторский список WARP endpoint'ов (5 /24 подсетей Cloudflare). */
    val FALLBACK_ENDPOINTS = listOf(
        FallbackEndpoint("162.159.192.1", "162.159.192.0/24"),
        FallbackEndpoint("162.159.192.8", "162.159.192.0/24"),
        FallbackEndpoint("162.159.193.1", "162.159.193.0/24"),
        FallbackEndpoint("162.159.193.8", "162.159.193.0/24"),
        FallbackEndpoint("162.159.195.1", "162.159.195.0/24"),
        FallbackEndpoint("162.159.195.8", "162.159.195.0/24"),
        FallbackEndpoint("188.114.96.1", "188.114.96.0/24"),
        FallbackEndpoint("188.114.96.8", "188.114.96.0/24"),
        FallbackEndpoint("188.114.97.1", "188.114.97.0/24"),
        FallbackEndpoint("188.114.97.66", "188.114.97.0/24"),
        FallbackEndpoint("188.114.99.1", "188.114.99.0/24"),
    )

    data class Selected(
        val endpoint: String,
        val ip: String,
        val port: Int,
        val cidr24: String?,
        val source: String, // hostname | tcp_check | fallback | manual
    )

    private fun checkTcpLatency(ip: String, port: Int, timeoutMs: Int): Long? = try {
        val socket = Socket()
        try {
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    } catch (_: Exception) {
        null
    }

    /**
     * «Умный» выбор: TCP-пре-чек встроенных IP (800 мс), пропуская подсети excludeCidrs
     * и адреса, покрытые маршрутами split (routed). Без кандидатов — случайный fallback.
     */
    fun selectSmart(
        port: Int,
        excludeCidrs: List<String> = emptyList(),
        routed: ((String) -> Boolean)? = null,
    ): Selected {
        val outsideRoutes = { e: FallbackEndpoint -> routed == null || !routed(e.ip) }
        val candidates = FALLBACK_ENDPOINTS.filter {
            outsideRoutes(it) && it.cidr24 !in excludeCidrs
        }
        if (candidates.isEmpty()) {
            val pick = FALLBACK_ENDPOINTS[Rng.nextInt(FALLBACK_ENDPOINTS.size)]
            return Selected("${pick.ip}:$port", pick.ip, port, pick.cidr24, "fallback")
        }
        var best: Pair<FallbackEndpoint, Long>? = null
        for (candidate in candidates) {
            val latency = checkTcpLatency(candidate.ip, port, 750) ?: continue
            if (best == null || latency < best.second) best = candidate to latency
        }
        return if (best != null) {
            Selected("${best.first.ip}:$port", best.first.ip, port, best.first.cidr24, "tcp_check")
        } else {
            val pick = candidates[Rng.nextInt(candidates.size)]
            Selected("${pick.ip}:$port", pick.ip, port, pick.cidr24, "fallback")
        }
    }

    fun hostname(port: Int): Selected =
        Selected("$ENGAGE_HOST:$port", ENGAGE_HOST, port, null, "hostname")

    fun manual(ip: String, port: Int): Selected =
        Selected("$ip:$port", ip, port, null, "manual")

    fun isPortAllowed(port: Int): Boolean = port in PORT_ALLOWLIST
}
