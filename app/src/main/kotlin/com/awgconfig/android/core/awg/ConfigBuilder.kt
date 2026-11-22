package com.awgconfig.android.core.awg

import com.awgconfig.android.core.model.AwgProfileId
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.DeviceProfile

/**
 * Сборка текста .conf — порт api/warp.js: buildInterfaceLegacy / buildInterfaceAwg2(WarpSafe) /
 * buildAwg3Interface / buildFullConfig донора.
 */
object ConfigBuilder {

    private fun addressLine(v4: String, v6: String?, plain: Boolean): String = when {
        v6 == null -> if (plain) "Address = $v4" else "Address = $v4/32"
        plain -> "Address = $v4, $v6"
        else -> "Address = $v4/32, $v6/128"
    }

    /** Legacy 1.5: поле за полем как у донора (S1/S2=0, junk 120/23/911, H=1..4). */
    fun interfaceLegacy(
        privateKey: String, clientIpv4: String, clientIpv6: String?,
        dnsLine: String, i1: String, plainAddress: Boolean,
        junk: Triple<Int, Int, Int> = Triple(AwgProfiles.LEGACY_JC, AwgProfiles.LEGACY_JMIN, AwgProfiles.LEGACY_JMAX),
    ): String {
        val lines = mutableListOf(
            "[Interface]",
            "PrivateKey = $privateKey",
            addressLine(clientIpv4, clientIpv6, plainAddress),
            "DNS = $dnsLine",
            "MTU = ${AwgProfiles.WARP_SAFE_MTU}",
            "S1 = 0",
            "S2 = 0",
            "Jc = ${junk.first}",
            "Jmin = ${junk.second}",
            "Jmax = ${junk.third}",
            "H1 = 1",
            "H2 = 2",
            "H3 = 3",
            "H4 = 4",
        )
        if (i1.isNotBlank()) lines.add("I1 = $i1")
        return lines.joinToString("\n")
    }

    /** AWG 2.0 (свой сервер): случайные H-диапазоны, S по док-границам, MTU = 1280 - S4. */
    fun interfaceAwg2(
        privateKey: String, clientIpv4: String, clientIpv6: String?,
        obf: AwgObfuscation, dnsLine: String, plainAddress: Boolean,
        i1: String, extraCps: Map<String, String>?,
    ): String {
        val mtu = AwgObfuscationGenerator.interfaceMtu(obf.s4, stockPeer = false)
        val lines = mutableListOf(
            "[Interface]",
            "PrivateKey = $privateKey",
            addressLine(clientIpv4, clientIpv6, plainAddress),
            "DNS = $dnsLine",
            "MTU = $mtu",
            "Jc = ${obf.jc}",
            "Jmin = ${obf.jmin}",
            "Jmax = ${obf.jmax}",
            "S1 = ${obf.s1}",
            "S2 = ${obf.s2}",
            "S3 = ${obf.s3}",
            "S4 = ${obf.s4}",
            "H1 = ${obf.h1}",
            "H2 = ${obf.h2}",
            "H3 = ${obf.h3}",
            "H4 = ${obf.h4}",
        )
        if (i1.isNotBlank()) {
            lines.add("I1 = $i1")
            extraCps?.let { appendExtraCps(lines, it) }
        }
        return lines.joinToString("\n")
    }

    /** AWG 2.0 WARP-safe: H=1..4, S=0, MTU=1280. */
    fun interfaceAwg2WarpSafe(
        privateKey: String, clientIpv4: String, clientIpv6: String?,
        obf: AwgObfuscation, dnsLine: String, plainAddress: Boolean,
        i1: String, extraCps: Map<String, String>?,
    ): String {
        val lines = mutableListOf(
            "[Interface]",
            "PrivateKey = $privateKey",
            addressLine(clientIpv4, clientIpv6, plainAddress),
            "DNS = $dnsLine",
            "MTU = ${AwgProfiles.WARP_SAFE_MTU}",
            "Jc = ${obf.jc}",
            "Jmin = ${obf.jmin}",
            "Jmax = ${obf.jmax}",
            "S1 = 0", "S2 = 0", "S3 = 0", "S4 = 0",
            "H1 = 1", "H2 = 2", "H3 = 3", "H4 = 4",
        )
        if (i1.isNotBlank()) {
            lines.add("I1 = $i1")
            extraCps?.let { appendExtraCps(lines, it) }
        }
        return lines.joinToString("\n")
    }

    /** AWG 3.0/3.1: тайминги, ContentPaddingAddition, DisableCookies, MTU=1280. */
    fun interfaceAwg3(
        privateKey: String, clientIpv4: String, clientIpv6: String?,
        obf: AwgObfuscation, dnsLine: String, plainAddress: Boolean,
        i1: String, extraCps: Map<String, String>?,
        contentPaddingAddition: String?, disableCookies: String?,
        isAwg31: Boolean, timingRanges: Map<String, String>?,
    ): String {
        val timings = AwgProfiles.AWG3_TIMINGS.toMutableMap()
        timingRanges?.forEach { (k, v) -> if (v.isNotBlank()) timings[k] = v }
        val lines = mutableListOf(
            "[Interface]",
            "PrivateKey = $privateKey",
            addressLine(clientIpv4, clientIpv6, plainAddress),
            "DNS = $dnsLine",
            "Jc = ${obf.jc}",
            "Jmin = ${obf.jmin}",
            "Jmax = ${obf.jmax}",
            "S1 = 0", "S2 = 0", "S3 = 0", "S4 = 0",
            "H1 = 1", "H2 = 2", "H3 = 3", "H4 = 4",
        )
        if (i1.isNotBlank()) {
            lines.add("I1 = $i1")
            extraCps?.let { appendExtraCps(lines, it) }
        }
        if (!contentPaddingAddition.isNullOrBlank()) lines.add("ContentPaddingAddition = $contentPaddingAddition")
        timings.forEach { (k, v) -> lines.add("$k = $v") }
        // RandomTrailers выключен и не пишется: AmneziaWG Apple до 3.1.3 отвергает ключ.
        if (isAwg31 && !disableCookies.isNullOrBlank() && disableCredentialsEnabled(disableCookies)) {
            lines.add("DisableCookies = $disableCookies")
        }
        lines.add("MTU = ${AwgProfiles.WARP_SAFE_MTU}")
        return lines.joinToString("\n")
    }

    private fun disableCredentialsEnabled(v: String) = v.lowercase() == "on"

    private fun appendExtraCps(lines: MutableList<String>, extraCps: Map<String, String>) {
        for (field in listOf("I2", "I3", "I4", "I5")) {
            extraCps[field]?.takeIf { it.isNotBlank() }?.let { lines.add("$field = $it") }
        }
    }

    fun peerBlock(
        publicKey: String,
        allowedIps: List<String>,
        endpoint: String,
        presharedKey: String? = null,
        keepalive: String? = null,
    ): String {
        val sb = StringBuilder("[Peer]\n")
        sb.append("PublicKey = $publicKey\n")
        presharedKey?.takeIf { it.isNotBlank() }?.let { sb.append("PresharedKey = $it\n") }
        sb.append("AllowedIPs = ${allowedIps.joinToString(", ")}\n")
        sb.append("Endpoint = $endpoint")
        keepalive?.takeIf { it.isNotBlank() && it.lowercase() != "off" && it != "0" }
            ?.let { sb.append("\nPersistentKeepalive = $it") }
        return sb.toString()
    }

    fun fullConfig(interfaceText: String, peerText: String): String =
        interfaceText + "\n\n" + peerText

    // ── Конфиг для собственного AWG-сервера ─────────────────────────────────────

    fun ownServerConfig(params: AwgServerParams, privateKey: String, i1: String, extraCps: Map<String, String>?): String {
        val obf = if (params.warpSafe) {
            AwgObfuscationGenerator.warpSafe(params.device, params.jc, params.jmin, params.jmax)
        } else {
            AwgObfuscationGenerator.ownServer(params.jc, params.jmin, params.jmax, params.useRandomHeaders, params.device)
        }
        val iface = when (params.profile) {
            AwgProfileId.LEGACY -> interfaceLegacy(
                privateKey, params.clientIpv4, params.clientIpv6, params.dns.line,
                i1, plainAddress = false,
                junk = Triple(obf.jc, obf.jmin, obf.jmax),
            )
            AwgProfileId.AWG2 -> if (params.warpSafe) {
                interfaceAwg2WarpSafe(privateKey, params.clientIpv4, params.clientIpv6, obf, params.dns.line, false, i1, extraCps)
            } else {
                interfaceAwg2(privateKey, params.clientIpv4, params.clientIpv6, obf, params.dns.line, false, i1, extraCps)
            }
            AwgProfileId.AWG3 -> interfaceAwg3(
                privateKey, params.clientIpv4, params.clientIpv6, obf, params.dns.line, false,
                i1, extraCps, AwgProfiles.AWG3_DEFAULT_CONTENT_PADDING, null, false, null,
            )
            AwgProfileId.AWG31 -> interfaceAwg3(
                privateKey, params.clientIpv4, params.clientIpv6, obf, params.dns.line, false,
                i1, extraCps, AwgProfiles.AWG3_DEFAULT_CONTENT_PADDING, null, true, null,
            )
        }
        val allowed = params.allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val peer = peerBlock(
            publicKey = params.serverPublicKey,
            allowedIps = allowed.ifEmpty { listOf("0.0.0.0/0") },
            endpoint = "${params.host}:${params.port}",
            presharedKey = params.psk,
            keepalive = params.keepalive.takeIf { it > 0 }?.toString(),
        )
        return fullConfig(iface, peer)
    }

    /** Mobile-режим: IPv4-only + junk 3/64/128 (как у донора для legacy). */
    fun mobileJunk(): Triple<Int, Int, Int> =
        Triple(AwgProfiles.MOBILE_JC, AwgProfiles.MOBILE_JMIN, AwgProfiles.MOBILE_JMAX)
}
