package com.awgconfig.android.core.model

/** Профиль генерации AmneziaWG — как у донора awgconfig.com. */
enum class AwgProfileId(val key: String, val displayVersion: String) {
    LEGACY("legacy", "1.5"),
    AWG2("awg2", "2.0"),
    AWG3("awg3", "3.0"),
    AWG31("awg31", "3.1");

    val isAwg3: Boolean get() = this == AWG3 || this == AWG31

    companion object {
        fun fromKey(raw: String?): AwgProfileId = when (raw?.trim()?.lowercase()) {
            "awg2", "2", "v2" -> AWG2
            "awg3", "awg3.0", "3", "3.0", "v3" -> AWG3
            "awg31", "awg3.1", "3.1", "31", "v3.1" -> AWG31
            else -> LEGACY
        }
    }
}

/** CPS-протокол пакета I1 (имитация трафика перед handshake). */
enum class CpsProtocol(val key: String, val stable: Boolean, val autoEligible: Boolean) {
    AUTO("auto", true, true),
    STATIC("static", true, true),
    SIP("sip", true, true),
    STUN("stun", true, true),
    QUIC("quic", false, false),
    DNS("dns", false, false),
    DTLS("dtls", false, false);

    companion object {
        fun fromKey(raw: String?): CpsProtocol =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: AUTO
    }
}

/** Режим выбора endpoint WARP. */
enum class EndpointMode(val key: String) {
    HOSTNAME("hostname"),   // engage.cloudflareclient.com
    SMART("smart"),         // TCP-пре-чек по встроенному списку IP
    MANUAL("manual");      // фиксированный IP из списка

    companion object {
        fun fromKey(raw: String?): EndpointMode =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: HOSTNAME
    }
}

/** Режим маршрутизации: полный туннель или выборочный (пресеты). */
enum class RouteMode(val key: String) { FULL("full"), SPLIT("split") }

/** Профиль устройства — как у донора (универсальный / мобильный / роутер). */
enum class DeviceProfile(val key: String) {
    UNIVERSAL("universal"), MOBILE("mobile"), ROUTER("router");

    companion object {
        fun fromKey(raw: String?): DeviceProfile =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: UNIVERSAL
    }
}

/** DNS-пресеты донора. */
enum class DnsPreset(val id: String, val line: String) {
    CLOUDFLARE("cloudflare", "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001"),
    GOOGLE("google", "8.8.8.8, 2001:4860:4860::8888, 8.8.4.4, 2001:4860:4860::8844"),
    YANDEX("yandex", "77.88.8.8, 2a02:6b8::feed:0ff, 77.88.8.1, 2a02:6b8:0:1::feed:0ff"),
    YANDEX_SAFE("yandex_safe", "77.88.8.88, 2a02:6b8::feed:bad, 77.88.8.2, 2a02:6b8:0:1::feed:bad"),
    ADGUARD("adguard", "94.140.14.14, 2a10:50c0::ad1:ff, 94.140.15.15, 2a10:50c0::ad2:ff"),
    QUAD9("quad9", "9.9.9.9, 2620:fe::fe, 149.112.112.112, 2620:fe::9"),
    COMSS_ONE("comss_one", "83.220.169.155, 212.109.195.93"),
    MALW_LINK("malw_link", "80.253.249.40, 193.23.209.189, 2a12:bec4:1460:5b7::2, 2a01:ecc0:680:120::2");

    companion object {
        fun fromId(raw: String?): DnsPreset =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: CLOUDFLARE
    }
}

/** Параметры генерации WARP-конфига. */
data class WarpGenParams(
    val profile: AwgProfileId = AwgProfileId.AWG2,
    val endpointMode: EndpointMode = EndpointMode.HOSTNAME,
    val manualEndpointIp: String? = null,
    val port: Int = 4500,
    val dns: DnsPreset = DnsPreset.CLOUDFLARE,
    val routeMode: RouteMode = RouteMode.FULL,
    val presets: Set<String> = emptySet(),
    val includeIpv6: Boolean = false,
    val noRouteLimit: Boolean = false,
    val device: DeviceProfile = DeviceProfile.UNIVERSAL,
    val cpsProtocol: CpsProtocol = CpsProtocol.AUTO,
    val cps5: Boolean = false,
    val customCps: Map<String, String> = emptyMap(),
    val count: Int = 1,
    val wantVpnLink: Boolean = true,
    val persistentKeepalive: String? = null,
)

/** Параметры генерации конфига для собственного AWG-сервера. */
data class AwgServerParams(
    val profile: AwgProfileId = AwgProfileId.AWG2,
    val host: String = "",
    val port: Int = 51820,
    val serverPublicKey: String = "",
    val clientIpv4: String = "10.8.1.2",
    val clientIpv6: String? = null,
    val subnetBits: Int = 32,
    val dns: DnsPreset = DnsPreset.CLOUDFLARE,
    val allowedIps: String = "0.0.0.0/0",
    val psk: String? = null,
    val keepalive: Int = 25,
    val warpSafe: Boolean = false,
    val jc: Int? = null, val jmin: Int? = null, val jmax: Int? = null,
    val useRandomHeaders: Boolean = false,
    val device: DeviceProfile = DeviceProfile.UNIVERSAL,
)

/** Параметры MASQUE-конфига. */
data class MasqueParams(
    val host: String = "masque.cloudflare.com",
    val port: Int = 443,
    val sni: String = "masque.cloudflare.com",
    val token: String = "",
    val label: String = "Cloudflare MASQUE",
    val includeSingBox: Boolean = false,
)

/** Готовый конфиг + мета для UI/истории/туннеля. */
data class GeneratedConfig(
    val id: String,
    val protocolKind: ProtocolKind,
    val title: String,
    val text: String,
    val vpnLink: String? = null,
    val details: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Можно ли поднять встроенный туннель по этому конфигу. */
    val tunnelCapable: Boolean = true,
)

enum class ProtocolKind(val key: String) {
    WARP("warp"), AWG_SERVER("awg_server"), MASQUE("masque"), KEYPAIR("keypair")
}

/** Запись истории генераций (локально, до 100). */
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val protocolKind: ProtocolKind,
    val title: String,
    val text: String,
    val vpnLink: String? = null,
    val details: Map<String, String> = emptyMap(),
    val tunnelCapable: Boolean = true,
) {
    fun toGeneratedConfig() = GeneratedConfig(
        id = id, protocolKind = protocolKind, title = title, text = text,
        vpnLink = vpnLink, details = details, createdAt = createdAt,
        tunnelCapable = tunnelCapable
    )
}

/** Результат пакетной генерации. */
data class BatchOutcome(
    val configs: List<GeneratedConfig>,
    val warnings: List<String> = emptyList(),
)

// ── Проверка валидности конфигов ─────────────────────────────────────────────

/** Уровень проверки: QUICK — разбор + DNS endpoint; REAL — полный тест через туннель. */
enum class TestLevel { QUICK, REAL }

/** Итоговый вердикт проверки. */
enum class TestVerdict { VALID, PARTIAL, INVALID }

/** Отчёт проверки конфига (QUICK или REAL). */
data class TestReport(
    val configId: String,
    val level: TestLevel,
    val at: Long = System.currentTimeMillis(),
    // структурная проверка
    val structuralOk: Boolean = false,
    val structuralErrors: List<String> = emptyList(),
    // endpoint
    val endpoint: String? = null,
    val dnsOk: Boolean? = null,
    val endpointLatencyMs: Long? = null,
    val endpointError: String? = null,
    // реальный туннель (только REAL и туннелируемые протоколы)
    val tunnelAttempted: Boolean = false,
    val tunnelUp: Boolean? = null,
    val handshakeOk: Boolean? = null,
    val httpOk: Boolean? = null,
    val fullTunnel: Boolean? = null,
    val exitIp: String? = null,
    val directIp: String? = null,
    val warpOn: Boolean? = null,
    val tunnelError: String? = null,
) {
    val verdict: TestVerdict
        get() = when {
            !structuralOk -> TestVerdict.INVALID
            level == TestLevel.QUICK -> when {
                dnsOk == true -> TestVerdict.VALID
                dnsOk == null -> TestVerdict.PARTIAL
                else -> TestVerdict.PARTIAL
            }
            !tunnelAttempted -> when (dnsOk) {
                true -> TestVerdict.VALID
                else -> TestVerdict.PARTIAL
            }
            handshakeOk != true -> TestVerdict.INVALID
            httpOk == true -> when {
                // полный туннель: IP должен смениться
                fullTunnel == true && directIp != null && exitIp != null && directIp == exitIp -> TestVerdict.PARTIAL
                else -> TestVerdict.VALID
            }
            else -> TestVerdict.PARTIAL // handshake есть, но HTTP сквозь туннель не прошёл
        }
}

/** Прогресс пакетной проверки. */
data class BatchTestProgress(
    val total: Int,
    val done: Int = 0,
    val running: Boolean = true,
    val real: Boolean = false,
    val finishedAt: Long? = null,
)
