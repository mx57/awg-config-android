package com.awgconfig.android.core.awg

import com.awgconfig.android.core.model.AwgProfileId

/**
 * Профили и WARP-safe правила AmneziaWG — порт src/server/awg/{profiles,warpSafety,evidence}.js донора.
 */
object AwgProfiles {

    /** WARP-safe wire-формат: пир Cloudflare — стандартный WireGuard. */
    val WARP_SAFE_S1 = "0"
    val WARP_SAFE_S2 = "0"
    val WARP_SAFE_S3 = "0"
    val WARP_SAFE_S4 = "0"
    val WARP_SAFE_H1 = "1"
    val WARP_SAFE_H2 = "2"
    val WARP_SAFE_H3 = "3"
    val WARP_SAFE_H4 = "4"

    /** MTU для WARP-safe конфигов (wgcf/официальный клиент WARP). */
    const val WARP_SAFE_MTU = 1280

    /** Junk-строка legacy-шаблона донора. */
    const val LEGACY_JC = 120
    const val LEGACY_JMIN = 23
    const val LEGACY_JMAX = 911

    /** AWG 2.0: диапазоны по документации (amneziawg-go). */
    const val AWG2_H_MIN = 5
    const val AWG2_H_MAX = 0x7fffffff
    const val AWG2_MIN_H_BAND = 65536
    const val AWG2_S123_MAX = 64
    const val AWG2_S4_MAX = 32
    const val AWG2_JC_MAX = 25
    const val AWG2_JMIN_MIN = 64
    const val AWG2_JMAX_MAX = 1024

    /** Мобильный профиль: низкий Jc, узкий junk, IPv4-only. */
    const val MOBILE_JC = 3
    const val MOBILE_JMIN = 64
    const val MOBILE_JMAX = 128

    /** Режим роутера: жёсткие ограничения для MikroTik/GL.iNet/Keenetic/OpenWrt. */
    const val ROUTER_JC_MAX = 2
    const val ROUTER_JMIN_MIN = 40
    const val ROUTER_JMIN_MAX = 64
    const val ROUTER_JMAX_MAX = 128
    const val ROUTER_JUNK_SPREAD = 64

    /** Тайминги AWG 3.x (значения по умолчанию из evidence.js донора). */
    val AWG3_TIMINGS = linkedMapOf(
        "RekeyAfterTime" to "100-120",
        "RekeyTimeout" to "3-7",
        "RejectAfterTime" to "150-180",
        "KeepaliveTimeout" to "5-15",
        "MaxHandshakeAttempts" to "15-20",
    )

    const val AWG3_DEFAULT_PERSISTENT_KEEPALIVE = "25-35"
    const val AWG3_DEFAULT_CONTENT_PADDING = "10-100"

    /** Заголовки H1–H4 фиксированы для WARP (типы сообщений WireGuard 1–4). */
    fun isWarpSafe(profile: AwgProfileId): Boolean = true // все профили донора WARP-safe

    fun displayVersion(profile: AwgProfileId): String = profile.displayVersion

    fun protocolVersionForVpnLink(profile: AwgProfileId): String? = when (profile) {
        AwgProfileId.LEGACY -> "1.5"
        AwgProfileId.AWG2 -> "2"
        AwgProfileId.AWG31 -> "3.1"
        AwgProfileId.AWG3 -> null // протокол-версия не подтверждена — vpn:// недоступен
    }
}
