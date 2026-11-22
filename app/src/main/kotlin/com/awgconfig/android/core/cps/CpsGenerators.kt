package com.awgconfig.android.core.cps

import com.awgconfig.android.core.model.CpsProtocol
import com.awgconfig.android.core.util.Rng
import com.awgconfig.android.core.util.hexToBytes
import com.awgconfig.android.core.util.toHex
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Генераторы бинарных CPS-пейлоадов — порт cpsGenerator.js + cps/{stun,dtls,dns,quic}.js донора.
 * STUN — binding request с CRC32-fingerprint; DTLS — ClientHello; QUIC — RFC 9001 Initial;
 * DNS — ответ A; SIP/Static — из CpsPayloads.
 */
object CpsGenerators {

    // ── CRC32 (для STUN fingerprint) ─────────────────────────────────────────────

    private val CRC32_TABLE = IntArray(256).also { table ->
        for (index in 0 until 256) {
            var value = index
            for (bit in 0 until 8) {
                value = if (value and 1 != 0) POLY_REV.toInt() xor (value ushr 1) else value ushr 1
            }
            table[index] = value
        }
    }

    private const val POLY_REV = 0xedb88320L

    private fun crc32(data: ByteArray): Int {
        var checksum = -0x1
        for (b in data) checksum = CRC32_TABLE[(checksum xor b.toInt()) and 0xff] xor (checksum ushr 8)
        return (checksum xor -0x1)
    }

    // ── Общие буферы ─────────────────────────────────────────────────────────────

    private fun u8(v: Int) = byteArrayOf((v and 0xff).toByte())
    private fun u16(v: Int) = byteArrayOf(((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte())
    private fun u24(v: Int) = byteArrayOf(((v ushr 16) and 0xff).toByte(), ((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte())
    private fun u32(v: Long) = byteArrayOf(
        ((v ushr 24) and 0xff).toByte(), ((v ushr 16) and 0xff).toByte(),
        ((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte(),
    )
    private fun concat(vararg parts: ByteArray): ByteArray {
        var total = 0
        parts.forEach { total += it.size }
        val out = ByteArray(total)
        var off = 0
        parts.forEach { System.arraycopy(it, 0, out, off, it.size); off += it.size }
        return out
    }

    // ── STUN ─────────────────────────────────────────────────────────────────────

    private val STUN_SOFTWARE_VALUES = listOf("libwebrtc", "Cisco-libsrtp", "pjnath", "twilio-srtp", "cloudflare-stun")

    private fun stunAttribute(type: Int, value: ByteArray): ByteArray {
        val padding = ByteArray((4 - value.size % 4) % 4)
        return concat(u16(type), u16(value.size), value, padding)
    }

    private val stunUsernameTemplates = listOf(
        { "${Rng.hex(4)}:${Rng.hex(4)}" },
        { "${Rng.hex(3).uppercase()}/${Rng.hex(2)}" },
        { "wa-${Rng.intIn(100000000, 999999999)}${Rng.nextInt(10)}:${Rng.hex(4)}" },
        { "${Rng.intIn(1700000000, 1900000000)}:${Rng.bytes(8).toBase64NoPad().take(12)}" },
    )

    private fun ByteArray.toBase64NoPad(): String =
        java.util.Base64.getEncoder().encodeToString(this).replace(Regex("[+/=]"), "")

    private fun generateStunPayload(): ByteArray {
        val username = stunAttribute(0x0006, stunUsernameTemplates[Rng.nextInt(stunUsernameTemplates.size)]().toByteArray(Charsets.UTF_8))
        val software = stunAttribute(0x8022, STUN_SOFTWARE_VALUES[Rng.nextInt(STUN_SOFTWARE_VALUES.size)].toByteArray(Charsets.UTF_8))
        val priority = stunAttribute(0x0024, u32(Rng.intIn(1000000, 2130706431).toLong()))
        val controlling = stunAttribute(0x802a, Rng.bytes(8))
        val candidate = stunAttribute(0x0025, ByteArray(0))
        val baseAttributes = concat(software, username, priority, controlling, candidate)
        val targetLength = 100 + Rng.intIn(0, 120)
        val paddingNeeded = maxOf(0, targetLength - 20 - baseAttributes.size - 8)
        val padding = if (paddingNeeded > 0) {
            stunAttribute(0x0026, Rng.bytes(paddingNeeded - paddingNeeded % 4))
        } else ByteArray(0)
        val attributes = concat(baseAttributes, padding)
        val messageLength = attributes.size + 8
        val partial = concat(u16(0x0001), u16(messageLength), u32(0x2112a442L), Rng.bytes(12), attributes)
        val fingerprint = stunAttribute(0x8028, u32((crc32(partial).toLong() xor 0x5354554eL)))
        return concat(partial, fingerprint)
    }

    // ── DTLS ─────────────────────────────────────────────────────────────────────

    private fun tlsExtension(type: Int, value: ByteArray) = concat(u16(type), u16(value.size), value)

    private fun generateDtlsPayload(): ByteArray {
        val host = "cloudflare.com".toByteArray(Charsets.US_ASCII)
        val serverName = concat(u8(0), u16(host.size), host)
        val extensions = concat(
            tlsExtension(0x0000, concat(u16(serverName.size), serverName)),
            tlsExtension(0x000a, concat(u16(4), u16(0x001d), u16(0x0017))),
            tlsExtension(0x000b, byteArrayOf(1, 0)),
            tlsExtension(0x000e, concat(u16(2), u16(0x0007), u8(0))),
            tlsExtension(0x000d, concat(u16(6), u16(0x0403), u16(0x0804), u16(0x0401))),
            tlsExtension(0x0017, ByteArray(0)),
        )
        val ciphers = concat(u16(0xc02b), u16(0xc02f), u16(0xc00a), u16(0xc014))
        val body = concat(
            u16(0xfefd), Rng.bytes(32), u8(0), u8(0),
            u16(ciphers.size), ciphers, u8(1), u8(0), u16(extensions.size), extensions,
        )
        val length = byteArrayOf(
            ((body.size ushr 16) and 0xff).toByte(),
            ((body.size ushr 8) and 0xff).toByte(),
            (body.size and 0xff).toByte(),
        )
        val handshake = concat(u8(1), length, u16(0), ByteArray(3), length, body)
        return concat(u8(0x16), u16(0xfefd), ByteArray(8), u16(handshake.size), handshake)
    }

    // ── DNS ─────────────────────────────────────────────────────────────────────

    private val DNS_NAMES = listOf("cloudflare.com", "google.com", "icloud.com")

    private fun encodeName(name: String): ByteArray = concat(
        *name.split(".").map { label -> concat(u8(label.length), label.toByteArray(Charsets.US_ASCII)) }.toTypedArray(),
        u8(0),
    )

    private fun generateDnsTemplate(): String {
        val name = DNS_NAMES[Rng.nextInt(DNS_NAMES.size)]
        val question = concat(encodeName(name), u16(1), u16(1))
        val answer = concat(u16(0xc00c), u16(1), u16(1), u32(300), u16(4), byteArrayOf(1, 1, 1, 1))
        val body = concat(u16(0x8180), u16(1), u16(1), u16(0), u16(0), question, answer)
        return "<r 2><b 0x${body.toHex()}>"
    }

    // ── QUIC (RFC 9000/9001 Initial) ──────────────────────────────────────────────

    private val QUIC_V1_INITIAL_SALT = "38762cf7f55934b34d179ae6a4c80cadccbb7f0a".hexToBytes()

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }.doFinal(ikm)

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val blocks = mutableListOf<ByteArray>()
        var previous = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < length) {
            previous = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
                .doFinal(concat(previous, info, u8(counter)))
            blocks.add(previous)
            generated += previous.size
            counter++
        }
        return concat(*blocks.toTypedArray()).copyOf(length)
    }

    private fun hkdfExpandLabel(secret: ByteArray, label: String, length: Int): ByteArray {
        val fullLabel = "tls13 $label".toByteArray(Charsets.US_ASCII)
        val info = concat(u16(length), u8(fullLabel.size), fullLabel, u8(0))
        return hkdfExpand(secret, info, length)
    }

    private fun encodeQuicVarInt(value: Long): ByteArray = when {
        value < 64 -> u8(value.toInt())
        value < 16384 -> {
            val v = (value or 0x4000).toInt()
            byteArrayOf(((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte())
        }
        value < (1L shl 30) -> {
            val v = (value or 0x80000000L).toInt()
            byteArrayOf(((v ushr 24) and 0xff).toByte(), ((v ushr 16) and 0xff).toByte(), ((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte())
        }
        else -> {
            val v = value or (-0x4000000000000000L)
            byteArrayOf(
                ((v ushr 56) and 0xff).toByte(), ((v ushr 48) and 0xff).toByte(),
                ((v ushr 40) and 0xff).toByte(), ((v ushr 32) and 0xff).toByte(),
                ((v ushr 24) and 0xff).toByte(), ((v ushr 16) and 0xff).toByte(),
                ((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte(),
            )
        }
    }

    private fun quicInitialSecrets(dcid: ByteArray): Array<ByteArray> {
        val initialSecret = hkdfExtract(QUIC_V1_INITIAL_SALT, dcid)
        val clientSecret = hkdfExpandLabel(initialSecret, "client in", 32)
        return arrayOf(
            initialSecret, clientSecret,
            hkdfExpandLabel(clientSecret, "quic key", 16),
            hkdfExpandLabel(clientSecret, "quic iv", 12),
            hkdfExpandLabel(clientSecret, "quic hp", 16),
        )
    }

    private fun quicTransportParameter(id: Long, value: Long): ByteArray {
        val encoded = encodeQuicVarInt(value)
        return concat(encodeQuicVarInt(id), encodeQuicVarInt(encoded.size.toLong()), encoded)
    }

    private fun buildQuicClientHello(serverName: String): ByteArray {
        val host = serverName.toByteArray(Charsets.US_ASCII)
        val serverNameEntry = concat(u8(0), u16(host.size), host)
        val extensions = concat(
            tlsExtension(0x0000, concat(u16(serverNameEntry.size), serverNameEntry)),
            tlsExtension(0x002b, concat(byteArrayOf(2), u16(0x0304))),
            tlsExtension(0x000a, concat(u16(2), u16(0x001d))),
            tlsExtension(0x000d, concat(u16(6), u16(0x0804), u16(0x0403), u16(0x0807))),
            tlsExtension(0x0033, concat(u16(36), u16(0x001d), u16(32), Rng.bytes(32))),
            tlsExtension(0x0039, concat(
                quicTransportParameter(0x01, 30000),
                quicTransportParameter(0x04, 1048576),
            )),
        )
        val body = concat(
            u16(0x0303), Rng.bytes(32), u8(0),
            u16(2), u16(0x1301), byteArrayOf(1, 0),
            u16(extensions.size), extensions,
        )
        return concat(u8(1), u24(body.size), body)
    }

    private fun packetNonce(iv: ByteArray, packetNumber: Long): ByteArray {
        val nonce = iv.copyOf()
        val encoded = ByteArray(8)
        for (i in 7 downTo 0) encoded[7 - i] = ((packetNumber ushr (8 * i)) and 0xff).toByte()
        for (i in encoded.indices) {
            val idx = nonce.size - encoded.size + i
            nonce[idx] = (nonce[idx].toInt() xor encoded[i].toInt()).toByte()
        }
        return nonce
    }

    private fun headerProtectionMask(hp: ByteArray, sample: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(hp, "AES"))
        return cipher.doFinal(sample).copyOf(5)
    }

    /** QUIC Initial-пакет ~1200 байт с корректной header protection. */
    private fun buildQuicInitial(): ByteArray {
        val dcid = Rng.bytes(8)
        val scid = Rng.bytes(8)
        val packetNumber = 0L
        val packetNumberLength = 2
        val targetLength = 1200
        val clientHello = buildQuicClientHello("cloudflare.com")
        val cryptoFrame = concat(u8(0x06), encodeQuicVarInt(0), encodeQuicVarInt(clientHello.size.toLong()), clientHello)
        val prefix = concat(
            u8(0xc0 or (packetNumberLength - 1)), u32(1),
            u8(dcid.size), dcid, u8(scid.size), scid, encodeQuicVarInt(0),
        )
        var lengthField = encodeQuicVarInt((packetNumberLength + cryptoFrame.size + 16).toLong())
        var plaintextLength = 0
        repeat(3) {
            plaintextLength = targetLength - prefix.size - lengthField.size - packetNumberLength - 16
            lengthField = encodeQuicVarInt((packetNumberLength + plaintextLength + 16).toLong())
        }
        require(plaintextLength >= cryptoFrame.size) { "QUIC targetLength too small for ClientHello" }
        val plaintext = concat(cryptoFrame, ByteArray(plaintextLength - cryptoFrame.size))
        val packetNumberBytes = byteArrayOf(
            ((packetNumber ushr 8) and 0xff).toByte(), (packetNumber and 0xff).toByte(),
        )
        val header = concat(prefix, lengthField, packetNumberBytes)
        val secrets = quicInitialSecrets(dcid)
        val key = secrets[2]
        val iv = secrets[3]
        val hp = secrets[4]
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, packetNonce(iv, packetNumber)))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        val packet = concat(header, ciphertext)
        val packetNumberOffset = prefix.size + lengthField.size
        val sample = packet.copyOfRange(packetNumberOffset + 4, packetNumberOffset + 20)
        val mask = headerProtectionMask(hp, sample)
        packet[0] = (packet[0].toInt() xor (mask[0].toInt() and 0x0f)).toByte()
        for (i in 0 until packetNumberLength) {
            packet[packetNumberOffset + i] = (packet[packetNumberOffset + i].toInt() xor mask[i + 1].toInt()).toByte()
        }
        return packet
    }

    // ── Диспетчер ────────────────────────────────────────────────────────────────

    private fun fixedBytesTemplate(value: ByteArray): String {
        val sb = StringBuilder()
        var offset = 0
        while (offset < value.size) {
            val chunk = value.copyOfRange(offset, minOf(offset + 1000, value.size))
            sb.append("<b 0x${chunk.toHex()}>")
            offset += 1000
        }
        return sb.toString()
    }

    /** Генерация I1-цепочки для выбранного протокола. */
    fun generateI1(protocol: CpsProtocol): Pair<String, CpsProtocol> {
        if (protocol != CpsProtocol.AUTO) return generateResolved(protocol, protocol)
        // auto: перетасованные стабильные протоколы (static/sip/stun)
        val stable = listOf(CpsProtocol.STATIC, CpsProtocol.SIP, CpsProtocol.STUN).shuffled(Rng.asRandom())
        for (candidate in stable) {
            try {
                return generateResolved(CpsProtocol.AUTO, candidate)
            } catch (_: IllegalStateException) {
                // каждому стабильному генератору — одна ограниченная попытка
            }
        }
        throw IllegalStateException("Все стабильные CPS-генераторы не справились")
    }

    private fun generateResolved(requested: CpsProtocol, resolved: CpsProtocol): Pair<String, CpsProtocol> {
        val value = when (resolved) {
            CpsProtocol.STATIC -> CpsPayloads.pickRandom()
            CpsProtocol.SIP -> CpsPayloads.sipPair().first
            CpsProtocol.STUN -> fixedBytesTemplate(generateStunPayload())
            CpsProtocol.QUIC -> fixedBytesTemplate(buildQuicInitial())
            CpsProtocol.DNS -> generateDnsTemplate()
            CpsProtocol.DTLS -> fixedBytesTemplate(generateDtlsPayload())
            CpsProtocol.AUTO -> CpsPayloads.pickRandom()
        }
        return value to resolved
    }
}
