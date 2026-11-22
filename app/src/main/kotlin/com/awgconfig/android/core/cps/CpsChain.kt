package com.awgconfig.android.core.cps

/**
 * CPS-цепочки AmneziaWG для полей I1–I5: разбор, проверка совместимости,
 * канонический вид — порт public/static/cps-chain.js донора.
 *
 * Теги: <b 0x…> фикс. байты; <r N> N случайных байт при каждой отправке;
 * <rd N> N случайных цифр ASCII; <rc N> N случайных латинских букв; <t> unix time.
 * Ограничения amneziawg-go: N в 1..1000; один <t> на поле; <rd>/<rc> только v0.2.15+.
 */
object CpsChain {

    val FIELDS = listOf("I1", "I2", "I3", "I4", "I5")
    val COMPAT_LEVELS = listOf("all", "modern")
    const val MAX_N = 1000
    /** Не выходит одной UDP-датаграммой без фрагментации при MTU 1500 (1500-20-8). */
    const val MAX_PACKET_BYTES = 1472
    /** Выше — фрагментация в сетях с MTU 1280 (1280-28). */
    const val MTU_1280_BYTES = 1252
    const val MAX_TEXT_LENGTH = 4096
    /** Ширина <t> в amneziawg-go v0.2.16+ (в v0.2.13–v0.2.15 — 8 байт). */
    const val TIMESTAMP_BYTES = 4

    val TAGS = mapOf(
        "b" to TagInfo(runtime = false, modernOnly = false),
        "r" to TagInfo(runtime = true, modernOnly = false),
        "rd" to TagInfo(runtime = true, modernOnly = true),
        "rc" to TagInfo(runtime = true, modernOnly = true),
        "t" to TagInfo(runtime = true, modernOnly = false),
    )
    private val NOT_APPLICABLE = listOf("d", "ds", "dz", "c", "wt", "wr")

    data class TagInfo(val runtime: Boolean, val modernOnly: Boolean)

    sealed class Segment {
        data class FixedBytes(val hex: String) : Segment()
        data class RandomBytes(val n: Int) : Segment()
        data class RandomDigits(val n: Int) : Segment()
        data class RandomChars(val n: Int) : Segment()
        data object Timestamp : Segment()
    }

    data class ParseError(val code: String, val at: Int = -1, val tag: String? = null)

    data class ParseResult(
        val ok: Boolean,
        val errors: List<ParseError>,
        val segments: List<Segment>,
        val bytes: Int,
        val fixedBytes: Int,
        val needsModern: Boolean,
        val canonical: String,
    )

    fun segmentBytes(seg: Segment): Int = when (seg) {
        is Segment.FixedBytes -> seg.hex.length / 2
        is Segment.Timestamp -> TIMESTAMP_BYTES
        is Segment.RandomBytes -> seg.n
        is Segment.RandomDigits -> seg.n
        is Segment.RandomChars -> seg.n
    }

    fun segmentText(seg: Segment): String = when (seg) {
        is Segment.FixedBytes -> "<b 0x${seg.hex}>"
        is Segment.Timestamp -> "<t>"
        is Segment.RandomBytes -> "<r ${seg.n}>"
        is Segment.RandomDigits -> "<rd ${seg.n}>"
        is Segment.RandomChars -> "<rc ${seg.n}>"
    }

    fun serialize(segments: List<Segment>): String = segments.joinToString("") { segmentText(it) }

    fun parse(text: String?, compat: String = "all"): ParseResult {
        val level = if (compat in COMPAT_LEVELS) compat else "all"
        val source = text ?: ""
        val errors = mutableListOf<ParseError>()
        val segments = mutableListOf<Segment>()

        if (source.length > MAX_TEXT_LENGTH) errors.add(ParseError("too_long_text"))

        var i = 0
        while (i < source.length) {
            val open = source.indexOf('<', i)
            val between = source.subSequence(i, if (open == -1) source.length else open)
            if (between.toString().trim().isNotEmpty()) errors.add(ParseError("outside_text", i))
            if (open == -1) break
            val close = source.indexOf('>', open + 1)
            val nextOpen = source.indexOf('<', open + 1)
            if (close == -1 || (nextOpen != -1 && nextOpen < close)) {
                errors.add(ParseError("unclosed", open))
                i = if (nextOpen == -1) source.length else nextOpen
                continue
            }
            val inner = source.substring(open + 1, close).trim()
            i = close + 1
            if (inner.isEmpty()) { errors.add(ParseError("empty_tag", open)); continue }
            val parts = inner.split(Regex("\\s+"))
            val name = parts[0]
            val param = parts.drop(1).joinToString(" ")
            if (!name.matches(Regex("^[a-z]+$")) && TAGS.containsKey(name.lowercase())) {
                errors.add(ParseError("tag_case", open, name)); continue
            }
            if (name in NOT_APPLICABLE) { errors.add(ParseError("not_applicable", open, name)); continue }
            if (!TAGS.containsKey(name)) { errors.add(ParseError("unknown_tag", open, name)); continue }

            when (name) {
                "b" -> {
                    val m = Regex("^0x([0-9a-fA-F]+)$").find(param)
                    if (m == null || m.groupValues[1].length % 2 != 0) {
                        errors.add(ParseError("b_hex", open, "b")); continue
                    }
                    segments.add(Segment.FixedBytes(m.groupValues[1].lowercase()))
                }
                "t" -> {
                    if (param.isNotEmpty()) { errors.add(ParseError("t_param", open, "t")); continue }
                    segments.add(Segment.Timestamp)
                }
                else -> {
                    val n = param.toIntOrNull()
                    if (n == null || n < 1 || n > MAX_N) {
                        errors.add(ParseError("n_range", open, name)); continue
                    }
                    segments.add(
                        when (name) {
                            "rd" -> Segment.RandomDigits(n)
                            "rc" -> Segment.RandomChars(n)
                            else -> Segment.RandomBytes(n)
                        }
                    )
                }
            }
        }

        if (segments.count { it is Segment.Timestamp } > 1) errors.add(ParseError("t_twice", tag = "t"))
        val needsModern = segments.any { seg ->
            val key = when (seg) {
                is Segment.FixedBytes -> "b"
                is Segment.Timestamp -> "t"
                is Segment.RandomBytes -> "r"
                is Segment.RandomDigits -> "rd"
                is Segment.RandomChars -> "rc"
            }
            TAGS[key]?.modernOnly == true
        }
        if (needsModern && level == "all") errors.add(ParseError("modern_only"))
        val bytes = segments.sumOf { segmentBytes(it) }
        if (bytes > MAX_PACKET_BYTES) errors.add(ParseError("too_large"))
        val fixedBytes = segments.filterIsInstance<Segment.FixedBytes>().sumOf { segmentBytes(it) }

        return ParseResult(
            ok = errors.isEmpty(),
            errors = errors,
            segments = segments,
            bytes = bytes,
            fixedBytes = fixedBytes,
            needsModern = needsModern,
            canonical = serialize(segments),
        )
    }

    /** Описание ошибки по-русски (как MESSAGES_RU донора). */
    fun describeError(error: ParseError): String {
        val base = when (error.code) {
            "too_long_text" -> "цепочка длиннее 4096 символов"
            "outside_text" -> "текст вне тегов: всё содержимое должно быть внутри <…>"
            "unclosed" -> "тег не закрыт символом >"
            "empty_tag" -> "пустой тег <>"
            "tag_case" -> "имя тега пишется строчными буквами"
            "unknown_tag" -> "неизвестный тег"
            "not_applicable" -> "тег не работает в полях I1–I5"
            "modern_only" -> "теги <rd> и <rc> понимают только клиенты на amneziawg-go 0.2.15 и новее"
            "b_hex" -> "<b> ждёт байты в виде 0x и чётного числа hex-цифр"
            "n_range" -> "длина должна быть целым числом от 1 до 1000"
            "t_param" -> "<t> пишется без параметра"
            "t_twice" -> "в одном поле может быть только один <t>"
            "too_large" -> "пакет больше 1472 байт"
            else -> error.code
        }
        return if (error.tag != null && error.code in listOf("unknown_tag", "not_applicable", "tag_case")) {
            "$base: <${error.tag}>"
        } else base
    }

    /**
     * Пропуск в I1–I5: amneziawg-go v0.2.13–v0.2.15 требуют номера пакетов подряд с 1.
     */
    fun findGap(present: Map<String, Boolean>): Pair<String, String>? {
        for (idx in 1 until FIELDS.size) {
            if (present.getOrDefault(FIELDS[idx], false) && !present.getOrDefault(FIELDS[idx - 1], false)) {
                return FIELDS[idx] to FIELDS[idx - 1]
            }
        }
        return null
    }

    fun describeGap(gap: Pair<String, String>): String =
        "поле ${gap.first} задано, а ${gap.second} — нет: клиенты на amneziawg-go до 0.2.16 отвергают пропуски в I1–I5"

    /** Поля I1–I5 из текста .conf (секция [Interface]). */
    fun extractFromConf(confText: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var section = ""
        for (raw in confText.split("\r\n", "\n")) {
            val line = raw.trim()
            val header = Regex("^\\[(\\w+)]$").find(line)
            if (header != null) { section = header.groupValues[1].lowercase(); continue }
            val kv = Regex("^(I[1-5])\\s*=\\s*(.+)$").find(line)
            if (kv != null && (section == "interface" || section == "")) {
                out[kv.groupValues[1]] = kv.groupValues[2].trim()
            }
        }
        return out
    }

    fun isCompat(level: String): Boolean = level in COMPAT_LEVELS
}
