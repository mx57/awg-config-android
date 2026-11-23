package com.awgconfig.android.core.batch

import com.awgconfig.android.core.awg.ConfigBuilder
import com.awgconfig.android.core.cps.CpsGenerators
import com.awgconfig.android.core.crypto.KeyPair
import com.awgconfig.android.core.masque.MasqueBuilder
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.BatchOutcome
import com.awgconfig.android.core.model.GeneratedConfig
import com.awgconfig.android.core.model.MasqueParams
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.core.util.Rng
import java.util.concurrent.atomic.AtomicLong

/**
 * Пакетная генерация без внешнего API (свой AWG-сервер / MASQUE / keypair):
 * до 100 конфигов с уникальными ключами и инкрементными клиентскими IP.
 */
object BatchGenerator {

    private const val MAX_BATCH = 100
    private val seq = AtomicLong(0)

    private fun nextId(prefix: String) = "$prefix-${System.currentTimeMillis()}-${seq.incrementAndGet()}"

    fun incrementIpv4(base: String, step: Int): String? {
        val parts = base.split(".")
        if (parts.size != 4) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it !in 0..255 }) return null
        var value = (nums[0] shl 24) or (nums[1] shl 16) or (nums[2] shl 8) or nums[3]
        value += step
        if (value < 0 || value > 0xffffffffL.toLong()) return null
        val v = value.toLong() and 0xffffffffL
        return "${(v ushr 24) and 0xff}.${(v ushr 16) and 0xff}.${(v ushr 8) and 0xff}.${v and 0xff}"
    }

    /** Пакет конфигов для собственного AWG-сервера: уникальные ключи, инкрементные IP. */
    fun awgServerBatch(params: AwgServerParams, count: Int): BatchOutcome {
        val n = count.coerceIn(1, MAX_BATCH)
        val configs = mutableListOf<GeneratedConfig>()
        val warnings = mutableListOf<String>()
        for (i in 0 until n) {
            val ip = incrementIpv4(params.clientIpv4, i)
                ?: params.clientIpv4.also { warnings.add("Не удалось инкрементировать IP: использован базовый $it") }
            val i1 = if (params.profile.isAwg3 || params.profile == com.awgconfig.android.core.model.AwgProfileId.AWG2) {
                CpsGenerators.generateI1(com.awgconfig.android.core.model.CpsProtocol.AUTO).first
            } else ""
            val keyPair = KeyPair()
            val text = ConfigBuilder.ownServerConfig(params, keyPair.privateKey.toBase64(), i1, null)
            configs.add(
                GeneratedConfig(
                    id = nextId("awg"),
                    protocolKind = ProtocolKind.AWG_SERVER,
                    title = "AWG ${params.profile.displayVersion} ${params.host}" + if (n > 1) " #${i + 1}" else "",
                    text = text,
                    details = mapOf(
                        "endpoint" to "${params.host}:${params.port}",
                        "clientIp" to ip,
                        "dns" to params.dns.line,
                        "profile" to params.profile.displayVersion,
                    ),
                    warnings = emptyList(),
                    tunnelCapable = true, // встроенный движок AmneziaWG подключает любые AWG-конфиги, с обфускацией
                )
            )
        }
        return BatchOutcome(configs, warnings.distinct())
    }

    /** Пакет MASQUE-URI со случайными токенами. */
    fun masqueBatch(params: MasqueParams, count: Int): BatchOutcome {
        val n = count.coerceIn(1, MAX_BATCH)
        val configs = mutableListOf<GeneratedConfig>()
        for (i in 0 until n) {
            val result = MasqueBuilder.build(params.copy(label = params.label + if (n > 1) " #${i + 1}" else ""))
            val text = buildString {
                appendLine("# MASQUE ${result.host}:${result.port} SNI=${result.sni}")
                appendLine(result.uri)
                result.singBoxJson?.let { appendLine(); appendLine("# sing-box outbound:"); appendLine(it) }
            }
            configs.add(
                GeneratedConfig(
                    id = nextId("masque"),
                    protocolKind = ProtocolKind.MASQUE,
                    title = "MASQUE ${result.host}" + if (n > 1) " #${i + 1}" else "",
                    text = text.trim(),
                    details = mapOf(
                        "endpoint" to "${result.host}:${result.port}",
                        "sni" to result.sni,
                        "token" to "${result.token.length} симв.",
                    ),
                    tunnelCapable = false,
                )
            )
        }
        return BatchOutcome(configs)
    }

    /** Пакет пар ключей X25519. */
    fun keypairBatch(count: Int): BatchOutcome {
        val n = count.coerceIn(1, MAX_BATCH)
        val configs = mutableListOf<GeneratedConfig>()
        for (i in 0 until n) {
            val keyPair = KeyPair()
            val priv = keyPair.privateKey.toBase64()
            val pub = keyPair.publicKey.toBase64()
            val text = buildString {
                appendLine("# X25519 keypair #${i + 1} (${Rng.hex(4)})")
                appendLine("PrivateKey = $priv")
                appendLine("PublicKey = $pub")
            }
            configs.add(
                GeneratedConfig(
                    id = nextId("keypair"),
                    protocolKind = ProtocolKind.KEYPAIR,
                    title = "X25519 #${i + 1}",
                    text = text.trim(),
                    details = mapOf("publicKey" to pub, "privateKey" to priv),
                    tunnelCapable = false,
                )
            )
        }
        return BatchOutcome(configs)
    }
}
