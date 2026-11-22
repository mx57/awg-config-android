package com.awgconfig.android.core.util

import java.security.SecureRandom

/** Единый источник случайности: как и донор, использует CSPRNG для всех значений. */
object Rng {
    private val random = SecureRandom()

    /** Целое в [0, bound) — как crypto.randomInt(0, bound). */
    fun nextInt(bound: Int): Int = random.nextInt(bound)

    /** Целое в [min, max] включительно. */
    fun intIn(min: Int, max: Int): Int = if (max <= min) min else min + random.nextInt(max - min + 1)

    fun bytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun hex(n: Int): String = bytes(n).joinToString("") { "%02x".format(it) }

    /** Для Collections.shuffled(...) — SecureRandom наследует java.util.Random. */
    fun asRandom(): java.util.Random = random
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun ByteArray.toBase64(): String = java.util.Base64.getEncoder().encodeToString(this)
fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
