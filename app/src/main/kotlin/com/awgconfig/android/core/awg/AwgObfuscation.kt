package com.awgconfig.android.core.awg

import com.awgconfig.android.core.model.DeviceProfile
import com.awgconfig.android.core.util.Rng

/**
 * Генерация параметров обфускации AmneziaWG — порт generateAwg2MagicHeaderRanges /
 * pickAwg2PaddingDocCompliant / pickAwg2JunkDocCompliant / applyRouterModeCaps / applyMobileModeOverrides.
 */
data class AwgObfuscation(
    val jc: Int,
    val jmin: Int,
    val jmax: Int,
    val s1: Int, val s2: Int, val s3: Int, val s4: Int,
    val h1: String, val h2: String, val h3: String, val h4: String,
)

object AwgObfuscationGenerator {

    /**
     * Случайные неперекрывающиеся диапазоны H1..H4 внутри [5, 2^31-1] с полосами >= 65536.
     * Для собственных AWG-серверов (random headers); для WARP H фиксированы 1–4.
     */
    fun randomMagicHeaderRanges(): Array<String> {
        val c1Min = AwgProfiles.AWG2_H_MIN + AwgProfiles.AWG2_MIN_H_BAND - 1
        val c1Max = AwgProfiles.AWG2_H_MAX - 3 * AwgProfiles.AWG2_MIN_H_BAND
        val c1 = Rng.intIn(c1Min, c1Max)
        val c2 = Rng.intIn(c1 + AwgProfiles.AWG2_MIN_H_BAND, AwgProfiles.AWG2_H_MAX - 2 * AwgProfiles.AWG2_MIN_H_BAND)
        val c3 = Rng.intIn(c2 + AwgProfiles.AWG2_MIN_H_BAND, AwgProfiles.AWG2_H_MAX - AwgProfiles.AWG2_MIN_H_BAND)
        return arrayOf(
            "${AwgProfiles.AWG2_H_MIN}-$c1",
            "${c1 + 1}-$c2",
            "${c2 + 1}-$c3",
            "${c3 + 1}-${AwgProfiles.AWG2_H_MAX}",
        )
    }

    /**
     * S1+56 != S2, S1+56 != S3, S2+92 != S3 — мешает DPI выводить границы пакетов.
     */
    fun pickPaddingDocCompliant(): IntArray {
        var s1: Int
        var s2: Int
        var s3: Int
        run {
            var s1L = 0; var s2L = 0; var s3L = 0
            for (k in 0 until 128) {
                s1L = Rng.intIn(0, AwgProfiles.AWG2_S123_MAX)
                s2L = Rng.intIn(0, AwgProfiles.AWG2_S123_MAX)
                s3L = Rng.intIn(0, AwgProfiles.AWG2_S123_MAX)
                if (s1L + 56 != s2L && s1L + 56 != s3L && s2L + 92 != s3L) break
            }
            if (s1L + 56 == s2L || s1L + 56 == s3L || s2L + 92 == s3L) { s1L = 10; s2L = 20; s3L = 30 }
            s1 = s1L; s2 = s2L; s3 = s3L
        }
        return intArrayOf(s1, s2, s3, Rng.intIn(0, AwgProfiles.AWG2_S4_MAX))
    }

    /** Junk-трейн в границах AWG 2.0: Jc 1..25, Jmin 64..800, Jmax >= Jmin+64 .. 1024. */
    fun pickJunkDocCompliant(jcOverride: Int? = null, jminOverride: Int? = null, jmaxOverride: Int? = null): Triple<Int, Int, Int> {
        val jc = jcOverride ?: Rng.intIn(1, AwgProfiles.AWG2_JC_MAX)
        val jmin = jminOverride ?: Rng.intIn(AwgProfiles.AWG2_JMIN_MIN, 800)
        val jmax = jmaxOverride ?: Rng.intIn(
            maxOf(jmin + 64, AwgProfiles.AWG2_JMIN_MIN + 1),
            AwgProfiles.AWG2_JMAX_MAX
        )
        return Triple(jc, jmin, jmax)
    }

    /** WARP-safe обфускация: H=1..4, S=0, junk в границах 2.0. */
    fun warpSafe(profile: DeviceProfile = DeviceProfile.UNIVERSAL, customJc: Int? = null, customJmin: Int? = null, customJmax: Int? = null): AwgObfuscation {
        var obf = AwgObfuscation(
            jc = 0, jmin = 0, jmax = 0,
            s1 = 0, s2 = 0, s3 = 0, s4 = 0,
            h1 = AwgProfiles.WARP_SAFE_H1, h2 = AwgProfiles.WARP_SAFE_H2,
            h3 = AwgProfiles.WARP_SAFE_H3, h4 = AwgProfiles.WARP_SAFE_H4,
        )
        val (jc, jmin, jmax) = pickJunkDocCompliant(customJc, customJmin, customJmax)
        obf = obf.copy(jc = jc, jmin = jmin, jmax = jmax)
        if (profile == DeviceProfile.MOBILE) obf = applyMobile(obf)
        if (profile == DeviceProfile.ROUTER) obf = applyRouterCaps(obf)
        return obf
    }

    /** Обфускация для собственного сервера: случайные H-диапазоны и doc-compliant S. */
    fun ownServer(customJc: Int? = null, customJmin: Int? = null, customJmax: Int? = null, randomHeaders: Boolean = true, profile: DeviceProfile = DeviceProfile.UNIVERSAL): AwgObfuscation {
        val headers = if (randomHeaders) randomMagicHeaderRanges() else arrayOf("1", "2", "3", "4")
        val pad = pickPaddingDocCompliant()
        var obf = AwgObfuscation(
            jc = 0, jmin = 0, jmax = 0,
            s1 = pad[0], s2 = pad[1], s3 = pad[2], s4 = pad[3],
            h1 = headers[0], h2 = headers[1], h3 = headers[2], h4 = headers[3],
        )
        val (jc, jmin, jmax) = pickJunkDocCompliant(customJc, customJmin, customJmax)
        obf = obf.copy(jc = jc, jmin = jmin, jmax = jmax)
        if (profile == DeviceProfile.MOBILE) obf = applyMobile(obf)
        if (profile == DeviceProfile.ROUTER) obf = applyRouterCaps(obf)
        return obf
    }

    fun applyMobile(obf: AwgObfuscation): AwgObfuscation = obf.copy(
        jc = AwgProfiles.MOBILE_JC,
        jmin = AwgProfiles.MOBILE_JMIN,
        jmax = AwgProfiles.MOBILE_JMAX,
    )

    fun applyRouterCaps(obf: AwgObfuscation): AwgObfuscation {
        val jmin = maxOf(AwgProfiles.ROUTER_JMIN_MIN, minOf(obf.jmin, AwgProfiles.ROUTER_JMIN_MAX))
        return obf.copy(
            jc = minOf(obf.jc, AwgProfiles.ROUTER_JC_MAX),
            jmin = jmin,
            jmax = minOf(maxOf(obf.jmax, jmin + AwgProfiles.ROUTER_JUNK_SPREAD), AwgProfiles.ROUTER_JMAX_MAX),
        )
    }

    /** MTU интерфейса: для стокового WireGuard-пира — 1280; иначе 1280 - S4 (floor 1280). */
    fun interfaceMtu(s4: Int, stockPeer: Boolean): Int =
        if (stockPeer) AwgProfiles.WARP_SAFE_MTU
        else maxOf(AwgProfiles.WARP_SAFE_MTU, 1280 - maxOf(0, s4))
}
