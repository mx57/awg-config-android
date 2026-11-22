package com.awgconfig.android.core

import com.awgconfig.android.core.cps.CpsGenerators
import com.awgconfig.android.core.cps.CpsPayloads
import com.awgconfig.android.core.cps.CpsChain
import com.awgconfig.android.core.crypto.Key
import com.awgconfig.android.core.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Криптография и CPS-генераторы. */
class CoreGeneratorsTest {

    @Test
    fun `keypair generates valid x25519 keys`() {
        repeat(8) {
            val kp = KeyPair()
            val priv = kp.privateKey.toBase64()
            val pub = kp.publicKey.toBase64()
            assertEquals(44, priv.length)
            assertEquals(44, pub.length)
            assertTrue(priv.endsWith("="))
        }
    }

    @Test
    fun `deterministic derivation from private key`() {
        val privB64 = KeyPair().privateKey.toBase64()
        val priv = Key.fromBase64(privB64)
        val pub1 = KeyPair(priv).publicKey
        val pub2 = KeyPair(priv).publicKey
        assertEquals(pub1.toBase64(), pub2.toBase64())
    }

    @Test
    fun `cps static payload picks valid chain`() {
        repeat(4) {
            val chain = CpsPayloads.pickRandom()
            val parsed = CpsChain.parse(chain, "modern")
            assertTrue(parsed.ok)
            assertTrue(parsed.bytes > 500) // реальные пейлоады ~700+ байт
        }
    }

    @Test
    fun `stun payload has plausible size`() {
        val chain = CpsGenerators.generateI1(com.awgconfig.android.core.model.CpsProtocol.STUN).first
        val parsed = CpsChain.parse(chain, "modern")
        assertTrue(parsed.ok)
        assertTrue(parsed.bytes in 108..260)
    }

    @Test
    fun `quic initial is 1200 bytes`() {
        val chain = CpsGenerators.generateI1(com.awgconfig.android.core.model.CpsProtocol.QUIC).first
        val parsed = CpsChain.parse(chain, "modern")
        assertTrue(parsed.ok)
        assertEquals(1200, parsed.bytes)
    }

    @Test
    fun `dtls and dns payloads parse`() {
        for (protocol in listOf(
            com.awgconfig.android.core.model.CpsProtocol.DTLS,
            com.awgconfig.android.core.model.CpsProtocol.DNS,
        )) {
            val chain = CpsGenerators.generateI1(protocol).first
            val parsed = CpsChain.parse(chain, "modern")
            assertTrue(parsed.ok)
            assertTrue(parsed.bytes in 10..1472)
        }
    }

    @Test
    fun `sip pair parses`() {
        val (i1, i2) = CpsPayloads.sipPair()
        assertTrue(CpsChain.parse(i1, "modern").ok)
        assertTrue(CpsChain.parse(i2, "modern").ok)
        assertTrue(i1.contains("494e5649")) // INVITE в hex
    }

    @Test
    fun `auto generates stable protocol`() {
        val (chain, resolved) = CpsGenerators.generateI1(com.awgconfig.android.core.model.CpsProtocol.AUTO)
        assertTrue(
            resolved in listOf(
                com.awgconfig.android.core.model.CpsProtocol.STATIC,
                com.awgconfig.android.core.model.CpsProtocol.SIP,
                com.awgconfig.android.core.model.CpsProtocol.STUN,
            )
        )
        assertTrue(CpsChain.parse(chain, "modern").ok)
    }
}
