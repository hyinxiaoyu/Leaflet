package io.leaflet.markdown

/**
 * Decoded file text plus the encoding path taken (T06, REQ-0002 FR-5/FR-6).
 *
 * - UTF-8 (BOM stripped) is the only happy path.
 * - Anything with undecodable bytes surfaces as [HadEncodingDamage]: valid
 *   lines stay readable (U+FFFD per bad byte run), the UI shows an explicit
 *   notice, and the message NEVER claims a detected encoding name.
 */
data class DecodedText(
    val text: String,
    val encodingState: EncodingState,
)

enum class EncodingState {
    CLEAN,

    /** UTF-8 after stripping a leading BOM. */
    BOM_STRIPPED,

    /**
     * Input had truncated multibyte sequences or illegal bytes; decoded with
     * replacement characters. Reader shows the "encoding unrecognized" notice.
     */
    HAD_ENCODING_DAMAGE,
}

object TextDecoder {
    private const val REPLACEMENT = '�'

    fun decode(bytes: ByteArray): DecodedText {
        var start = 0
        var bomStripped = false
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            start = 3
            bomStripped = true
        }
        val out = StringBuilder(bytes.size.coerceAtLeast(0))
        var damaged = false
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> {
                    out.append(b.toChar())
                    i++
                }
                b in 0xC2..0xDF -> {
                    if (i + 1 < bytes.size && isContinuation(bytes[i + 1])) {
                        out.append(
                            (((b and 0x1F) shl 6) or (bytes[i + 1].toInt() and 0x3F)).toChar(),
                        )
                        i += 2
                    } else {
                        out.append(REPLACEMENT)
                        damaged = true
                        i++
                    }
                }
                // 0xE0/0xED/0xF0/0xF4 need range-checked 2nd byte (overlongs,
                // surrogates, >U+10FFFF); other 3-byte leads need any 2
                // continuations. Unpaired surrogates never reach toChar().
                b in 0xE0..0xEF -> {
                    val ok =
                        i + 2 < bytes.size && isContinuation(bytes[i + 1]) &&
                            isContinuation(bytes[i + 2]) &&
                            when (b) {
                                0xE0 -> (bytes[i + 1].toInt() and 0xFF) in 0xA0..0xBF
                                0xED -> (bytes[i + 1].toInt() and 0xFF) in 0x80..0x9F
                                else -> true
                            }
                    if (ok) {
                        out.append(
                            (
                                ((b and 0x0F) shl 12) or
                                    ((bytes[i + 1].toInt() and 0x3F) shl 6) or
                                    (bytes[i + 2].toInt() and 0x3F)
                            ).toChar(),
                        )
                        i += 3
                    } else {
                        out.append(REPLACEMENT)
                        damaged = true
                        i++
                    }
                }
                b in 0xF0..0xF4 -> {
                    val b2 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
                    val ok =
                        i + 3 < bytes.size && isContinuation(bytes[i + 1]) &&
                            isContinuation(bytes[i + 2]) && isContinuation(bytes[i + 3]) &&
                            when (b) {
                                0xF0 -> b2 in 0x90..0xBF
                                0xF4 -> b2 in 0x80..0x8F
                                else -> true
                            }
                    if (ok) {
                        val cp =
                            ((b and 0x07) shl 18) or
                                ((bytes[i + 1].toInt() and 0x3F) shl 12) or
                                ((bytes[i + 2].toInt() and 0x3F) shl 6) or
                                (bytes[i + 3].toInt() and 0x3F)
                        // 0xF4+range-checked 2nd byte already caps at U+10FFFF.
                        out.append(Character.toChars(cp))
                        i += 4
                    } else {
                        out.append(REPLACEMENT)
                        damaged = true
                        i++
                    }
                }
                else -> {
                    out.append(REPLACEMENT)
                    damaged = true
                    i++
                }
            }
        }
        val state =
            when {
                damaged -> EncodingState.HAD_ENCODING_DAMAGE
                bomStripped -> EncodingState.BOM_STRIPPED
                else -> EncodingState.CLEAN
            }
        return DecodedText(out.toString(), state)
    }

    private fun isContinuation(b: Byte): Boolean = (b.toInt() and 0xC0) == 0x80
}

/**
 * YAML frontmatter title extraction, deliberately narrow subset (REQ-0002 FR-4).
 *
 * Recognized: a leading `---` line, then `title:` scalar lines with optional
 * single/double quotes, then a closing `---` line. Anything else (unclosed
 * fence, non-scalar title, missing key) returns null and NEVER fails the body.
 */
internal object Frontmatter {
    data class Result(val title: String?, val bodyStartLine: Int)

    fun extract(text: String): Result {
        val lines = text.lineSequence().toList()
        if (lines.isEmpty() || lines[0].trim() != "---") return Result(null, 0)
        var closeAt = -1
        for (i in 1 until lines.size.coerceAtMost(64)) {
            if (lines[i].trim() == "---" || lines[i].trim() == "...") {
                closeAt = i
                break
            }
        }
        if (closeAt < 0) return Result(null, 0)
        var title: String? = null
        for (i in 1 until closeAt) {
            val line = lines[i]
            val key = line.substringBefore(':').trim()
            if (!key.equals("title", ignoreCase = true)) continue
            val raw = line.substringAfter(':').trim()
            if (raw.isEmpty() || raw.startsWith("[") || raw.startsWith("{") ||
                raw.startsWith("|") || raw.startsWith(">")
            ) {
                continue
            }
            title =
                raw.removeSurrounding("\"").removeSurrounding("'").trim()
                    .takeIf { it.isNotEmpty() }
        }
        return Result(title, closeAt + 1)
    }
}

/**
 * Heading + TOC extraction over an accepted commonmark 0.13.0 AST.
 * Duplicate heading texts get unique anchors (`overview`, `overview-1`, ...).
 */
internal object Headings {
    fun slugify(text: String): String {
        val slug =
            text.lowercase()
                .replace(Regex("[\\s_]+"), "-")
                .replace(Regex("[^\\p{L}\\p{N}-]"), "")
                .trim('-')
        return slug.ifEmpty { "section" }
    }

    fun uniqueAnchors(texts: List<String>): List<String> {
        val counts = mutableMapOf<String, Int>()
        return texts.map { text ->
            val base = slugify(text)
            val seen = counts.getOrDefault(base, 0)
            counts[base] = seen + 1
            if (seen == 0) base else "$base-$seen"
        }
    }
}

/**
 * Bounded single-block policy (ADR-0006; thresholds from T03/T13 measurement,
 * NOT invented here).
 *
 * A block whose source exceeds [MAX_BLOCK_CHARS] is split into bounded plain-text
 * chunks ([PLAIN_FALLBACK] kind); one notice-worthy event per document.
 */
internal object BlockLimits {
    /**
     * Initial bound, pending T03/T13 measurement (REQ-0002 Open Questions:
     * "single huge block split threshold ... implementer sets an initial value
     * and records it"). 32 KiB chars keeps a single TextView bind well inside
     * safe measure/layout budgets while staying far above ordinary blocks.
     */
    const val MAX_BLOCK_CHARS: Int = 32 * 1024
}
