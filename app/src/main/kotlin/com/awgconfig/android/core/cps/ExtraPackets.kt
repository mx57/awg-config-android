package com.awgconfig.android.core.cps

import com.awgconfig.android.core.util.Rng

/**
 * I2–I5 (опция cps5): маскировочные пакеты после I1 перед junk-трейном и handshake,
 * отправляются при каждой попытке инициации. Цепочки с рантайм-тегами — порт
 * src/server/cpsExtraPackets.js донора.
 */
object ExtraPackets {

    private fun tagB(len: Int) = "<b 0x${Rng.hex(len)}>"
    private fun tagR(min: Int, max: Int) = "<r ${Rng.intIn(min, max)}>"
    private fun tagT() = "<t>"

    private val TEMPLATES = listOf(
        // I2
        { "${tagB(Rng.intIn(4, 9))}${tagT()}${tagR(48, 120)}" },
        // I3
        { "${tagR(8, 24)}${tagB(Rng.intIn(2, 5))}${tagR(40, 96)}" },
        // I4
        { "${tagT()}${tagR(24, 64)}${tagB(Rng.intIn(3, 7))}${tagR(16, 48)}" },
        // I5
        { "${tagB(Rng.intIn(6, 13))}${tagR(64, 140)}" },
    )

    /** I2–I5: разная форма у каждого поля; фиксированные <b> — меньшая часть пакета. */
    fun generate(): Map<String, String> = linkedMapOf(
        "I2" to TEMPLATES[0](),
        "I3" to TEMPLATES[1](),
        "I4" to TEMPLATES[2](),
        "I5" to TEMPLATES[3](),
    )
}
