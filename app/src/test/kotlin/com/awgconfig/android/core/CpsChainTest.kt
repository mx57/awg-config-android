package com.awgconfig.android.core

import com.awgconfig.android.core.cps.CpsChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Парсер CPS-цепочек I1–I5 — порт cps-chain.js. */
class CpsChainTest {

    @Test
    fun `valid chain canonicalized`() {
        val result = CpsChain.parse("<b 0x0A0B><r 10><t>", "modern")
        assertTrue(result.ok)
        assertEquals(3, result.segments.size)
        assertEquals("<b 0x0a0b><r 10><t>", result.canonical)
        assertEquals(2 + 10 + CpsChain.TIMESTAMP_BYTES, result.bytes)
    }

    @Test
    fun `text outside tags rejected`() {
        val result = CpsChain.parse("junk<b 0x0A>")
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.code == "outside_text" })
    }

    @Test
    fun `odd hex rejected`() {
        val result = CpsChain.parse("<b 0x0A0>")
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.code == "b_hex" })
    }

    @Test
    fun `n range enforced`() {
        assertFalse(CpsChain.parse("<r 0>").ok)
        assertFalse(CpsChain.parse("<r 1001>").ok)
        assertTrue(CpsChain.parse("<r 1000>").ok)
    }

    @Test
    fun `two timestamps rejected`() {
        assertFalse(CpsChain.parse("<t><t>").ok)
    }

    @Test
    fun `modern tags require modern compat`() {
        val all = CpsChain.parse("<rd 5>", "all")
        assertFalse(all.ok)
        assertTrue(all.errors.any { it.code == "modern_only" })
        val modern = CpsChain.parse("<rd 5>", "modern")
        assertTrue(modern.ok)
    }

    @Test
    fun `too large packet rejected`() {
        assertFalse(CpsChain.parse("<r 1473>").ok)
    }

    @Test
    fun `gap detection`() {
        val gap = CpsChain.findGap(
            mapOf("I1" to true, "I2" to true, "I3" to false, "I4" to true, "I5" to true)
        )
        assertNotNull(gap)
        assertEquals("I4" to "I3", gap)
        assertNull(CpsChain.findGap(mapOf("I1" to true, "I2" to true)))
    }

    @Test
    fun `extract from conf`() {
        val conf = """
            [Interface]
            I1 = <b 0x01>
            I2 = <r 5>
            [Peer]
            PublicKey = X
        """.trimIndent()
        val fields = CpsChain.extractFromConf(conf)
        assertEquals(2, fields.size)
        assertEquals("<b 0x01>", fields["I1"])
        assertEquals("<r 5>", fields["I2"])
    }
}
