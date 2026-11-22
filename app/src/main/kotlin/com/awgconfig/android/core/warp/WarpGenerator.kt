package com.awgconfig.android.core.warp

import com.awgconfig.android.core.awg.AwgObfuscation
import com.awgconfig.android.core.awg.AwgObfuscationGenerator
import com.awgconfig.android.core.awg.AwgProfiles
import com.awgconfig.android.core.awg.ConfigBuilder
import com.awgconfig.android.core.cps.CpsChain
import com.awgconfig.android.core.cps.CpsGenerators
import com.awgconfig.android.core.cps.ExtraPackets
import com.awgconfig.android.core.crypto.KeyPair
import com.awgconfig.android.core.model.AwgProfileId
import com.awgconfig.android.core.model.BatchOutcome
import com.awgconfig.android.core.model.DeviceProfile
import com.awgconfig.android.core.model.EndpointMode
import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.core.model.RouteMode
import com.awgconfig.android.core.model.WarpGenParams
import com.awgconfig.android.core.routes.RouteCatalog
import com.awgconfig.android.core.vpnlink.VpnLinkBuilder
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Оркестратор генерации WARP-конфигов — порт generateWarpConfig /
 * generateMultipleWarpConfigs донора. Регистрирует устройства WARP с самого
 * устройства, собирает конфиг по правилам выбранного профиля.
 */
class WarpGenerator(private val routesStream: () -> InputStream) {

    private val seq = AtomicLong(0)

    suspend fun generate(params: WarpGenParams): BatchOutcome {
        val n = params.count.coerceIn(1, 3) // лимит донора: 1–3 конфига за генерацию
        val warnings = mutableListOf<String>()

        // 1. Маршруты — до регистрации (как донор: пустой список = 502 до траты регистрации)
        val resolved: RouteCatalog.ResolvedRoutes? = if (
            params.routeMode == RouteMode.SPLIT && params.presets.isNotEmpty()
        ) {
            RouteCatalog.resolve(
                presetKeys = params.presets,
                stream = routesStream,
                includeIpv6 = params.includeIpv6,
                endpointOverride = null,
                noLimit = params.noRouteLimit,
            ).also { warnings.addAll(it.warnings) }
        } else null
        val routed = resolved?.let { RouteCatalog.routeMatcher(it.cidrs) }

        // 2. CPS I1 — до регистрации
        val customI1 = params.customCps["i1"]?.takeIf { it.isNotBlank() }
        val i1: String = customI1 ?: CpsGenerators.generateI1(params.cpsProtocol).first
        validateI1(i1, warnings)

        // I2–I5: только вместе с I1 (I9 донора), только не-legacy
        val supportsIFields = params.profile != AwgProfileId.LEGACY && i1.isNotBlank()
        val extraCps: Map<String, String>? = if (supportsIFields && (params.cps5 || params.customCps.any { it.key in listOf("i2", "i3", "i4", "i5") })) {
            val auto = if (params.cps5) ExtraPackets.generate() else emptyMap()
            linkedMapOf(
                "I2" to (params.customCps["i2"]?.takeIf { it.isNotBlank() } ?: auto["I2"] ?: ""),
                "I3" to (params.customCps["i3"]?.takeIf { it.isNotBlank() } ?: auto["I3"] ?: ""),
                "I4" to (params.customCps["i4"]?.takeIf { it.isNotBlank() } ?: auto["I4"] ?: ""),
                "I5" to (params.customCps["i5"]?.takeIf { it.isNotBlank() } ?: auto["I5"] ?: ""),
            )
        } else null

        val configs = mutableListOf<GeneratedConfig>()
        val usedCidrs = mutableListOf<String>()
        var lastError: Exception? = null

        for (index in 0 until n) {
            try {
                val keyPair = KeyPair()
                val registration = WarpRegistrationApi.registerDevice(
                    keyPair.publicKey.toBase64()
                )
                val peerPub = registration.peerPublicKey
                    ?: "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

                // 3. Endpoint
                val port = if (EndpointSelector.isPortAllowed(params.port)) params.port else EndpointSelector.DEFAULT_PORT
                val selected: EndpointSelector.Selected = when (params.endpointMode) {
                    EndpointMode.HOSTNAME -> EndpointSelector.hostname(port)
                    EndpointMode.MANUAL -> params.manualEndpointIp?.let {
                        EndpointSelector.manual(it, port)
                    } ?: EndpointSelector.hostname(port)
                    EndpointMode.SMART -> EndpointSelector.selectSmart(port, usedCidrs, routed).also {
                        if (it.cidr24 != null && it.cidr24 != "unknown") usedCidrs.add(it.cidr24!!)
                    }
                }

                // 4. Обфускация + мобильный/роутерный профиль (mobile-first, router-second)
                val obf: AwgObfuscation = when (params.profile) {
                    AwgProfileId.LEGACY -> AwgObfuscationGenerator.warpSafe(DeviceProfile.UNIVERSAL)
                    else -> AwgObfuscationGenerator.warpSafe(params.device)
                }
                val mobileJunk: Triple<Int, Int, Int>? =
                    if (params.device == DeviceProfile.MOBILE) ConfigBuilder.mobileJunk() else null
                val legacyJunk = when {
                    params.profile != AwgProfileId.LEGACY -> null
                    params.device == DeviceProfile.ROUTER -> AwgObfuscationGenerator.applyRouterCaps(
                        AwgObfuscation(
                            jc = AwgProfiles.LEGACY_JC, jmin = AwgProfiles.LEGACY_JMIN, jmax = AwgProfiles.LEGACY_JMAX,
                            s1 = 0, s2 = 0, s3 = 0, s4 = 0,
                            h1 = "1", h2 = "2", h3 = "3", h4 = "4",
                        )
                    ).let { Triple(it.jc, it.jmin, it.jmax) }
                    params.device == DeviceProfile.MOBILE -> mobileJunk
                    else -> null
                }

                // 5. IPv4-only логика (мобильный профиль или отсутствие IPv6 у регистрации)
                val ipv6Missing = registration.clientIpv6 == null
                val ipv4Only = params.device == DeviceProfile.MOBILE || ipv6Missing
                val clientV6 = if (ipv4Only) null else registration.clientIpv6
                val routeCidrs = when {
                    resolved == null -> null
                    ipv4Only -> resolved.cidrs.filterNot { it.contains(":") }
                    else -> resolved.cidrs
                }

                // 6. Сборка текста конфига
                val iface = when (params.profile) {
                    AwgProfileId.LEGACY -> ConfigBuilder.interfaceLegacy(
                        privateKey = keyPair.privateKey.toBase64(),
                        clientIpv4 = registration.clientIpv4,
                        clientIpv6 = clientV6,
                        dnsLine = params.dns.line,
                        i1 = i1,
                        plainAddress = true,
                        junk = legacyJunk ?: mobileJunk ?: Triple(AwgProfiles.LEGACY_JC, AwgProfiles.LEGACY_JMIN, AwgProfiles.LEGACY_JMAX),
                    )
                    AwgProfileId.AWG2 -> ConfigBuilder.interfaceAwg2WarpSafe(
                        privateKey = keyPair.privateKey.toBase64(),
                        clientIpv4 = registration.clientIpv4,
                        clientIpv6 = clientV6,
                        obf = obf,
                        dnsLine = params.dns.line,
                        plainAddress = true,
                        i1 = i1,
                        extraCps = extraCps,
                    )
                    AwgProfileId.AWG3, AwgProfileId.AWG31 -> ConfigBuilder.interfaceAwg3(
                        privateKey = keyPair.privateKey.toBase64(),
                        clientIpv4 = registration.clientIpv4,
                        clientIpv6 = clientV6,
                        obf = obf,
                        dnsLine = params.dns.line,
                        plainAddress = true,
                        i1 = i1,
                        extraCps = extraCps,
                        contentPaddingAddition = AwgProfiles.AWG3_DEFAULT_CONTENT_PADDING,
                        disableCookies = null,
                        isAwg31 = params.profile == AwgProfileId.AWG31,
                        timingRanges = null,
                    )
                }
                val allowed = routeCidrs ?: if (ipv4Only) {
                    listOf("0.0.0.0/0")
                } else if (params.includeIpv6) {
                    listOf("0.0.0.0/0", "::/0")
                } else {
                    listOf("0.0.0.0/0")
                }
                val keepalive = params.persistentKeepalive ?: when (params.profile.isAwg3) {
                    true -> AwgProfiles.AWG3_DEFAULT_PERSISTENT_KEEPALIVE
                    else -> "25"
                }
                val peer = ConfigBuilder.peerBlock(
                    publicKey = peerPub,
                    allowedIps = allowed,
                    endpoint = selected.endpoint,
                    keepalive = keepalive,
                )
                val text = ConfigBuilder.fullConfig(iface, peer)

                // 7. vpn://-ссылка (недоступна для AWG 3.0)
                val vpnLink = if (params.wantVpnLink) {
                    VpnLinkBuilder.build(
                        confText = text,
                        hostName = selected.ip,
                        dns1 = params.dns.line.split(",").map { it.trim() }.getOrElse(0) { "1.1.1.1" },
                        dns2 = params.dns.line.split(",").map { it.trim() }.getOrElse(1) { "1.0.0.1" },
                        profileKey = params.profile.key,
                    )
                } else null

                if (ipv6Missing && params.device != DeviceProfile.MOBILE) {
                    warnings.add("Регистрация WARP не вернула IPv6-адрес: конфиг #${index + 1} — только IPv4.")
                }

                configs.add(
                    GeneratedConfig(
                        id = "warp-${System.currentTimeMillis()}-${seq.incrementAndGet()}",
                        protocolKind = ProtocolKind.WARP,
                        title = "WARP ${params.profile.displayVersion}" + if (n > 1) " #${index + 1}" else "",
                        text = text,
                        vpnLink = vpnLink,
                        details = buildMap {
                            put("profile", params.profile.displayVersion)
                            put("endpoint", selected.endpoint)
                            put("endpointSource", selected.source)
                            put("dns", params.dns.line)
                            put("routes", if (routeCidrs != null) "${routeCidrs.size} CIDR" else "полный туннель")
                            put("device", params.device.key)
                            if (i1.isNotBlank()) put("cps", params.cpsProtocol.key)
                            put("keepalive", keepalive)
                        },
                        warnings = warnings.toList(),
                        createdAt = System.currentTimeMillis(),
                        tunnelCapable = true,
                    )
                )
            } catch (e: Exception) {
                lastError = e
                if (configs.isNotEmpty()) break // уже есть хотя бы один — остальные как предупреждение
                throw e
            }
        }

        if (configs.size < n) {
            warnings.add("Удалось сгенерировать только ${configs.size} из $n конфигураций.")
        }
        if (params.profile == AwgProfileId.AWG3 && params.wantVpnLink) {
            warnings.add("vpn:// недоступен для AWG 3.0: историческая protocol_version не подтверждена; используйте экспорт .conf.")
        }
        return BatchOutcome(configs, warnings.distinct())
    }

    private fun validateI1(value: String, warnings: MutableList<String>) {
        val parsed = CpsChain.parse(value, compat = "modern")
        if (!parsed.ok) {
            parsed.errors.firstOrNull()?.let { warnings.add("I1: ${CpsChain.describeError(it)}") }
        }
    }
}
