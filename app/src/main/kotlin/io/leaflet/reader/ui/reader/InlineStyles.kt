package io.leaflet.reader.ui.reader

/**
 * Delimiter-pair inline scanner over marker-carrying strings (R2-03).
 *
 * Two entry points sharing one scan discipline:
 * - [of]: style ranges in DISPLAYED coordinates (markers excluded).
 * - [strip]: the displayed string itself (markers dropped, link labels
 *   kept, destinations dropped, unclosed markers literal).
 * Indices align by construction: both walks consume the same shapes, so
 * `of(s)` ranges index `strip(s)` directly.
 *
 * Supported subset (MVP): `**bold**`, `*italic*` (single, not part of
 * `**`), `~~strike~~`, `` `code` ``, `[label](dest)` (label kept, spans
 * inside the label recurse). Unclosed/overlapping markers stay literal
 * (graceful degradation, REQ-0002). Code bodies never reach here as blocks
 * (excluded by [EntryBindings.inlineStyles]); inline code spans inside
 * prose and table cells are styled monospace instead.
 */
internal object InlineStyles {
    /**
     * Displayed text for a marker-carrying string: drops consumed markers,
     * keeps link labels (destination dropped), leaves unclosed markers
     * literal.
     */
    fun strip(source: String): String {
        val out = StringBuilder(source.length)
        var si = 0
        val n = source.length
        while (si < n) {
            when {
                source.startsWith("**", si) -> {
                    val close = source.indexOf("**", si + 2)
                    if (close > si + 2 && !source.substring(si + 2, close).contains('\n')) {
                        out.append(source, si + 2, close)
                        si = close + 2
                    } else {
                        out.append("**")
                        si += 2
                    }
                }
                source.startsWith("~~", si) -> {
                    val close = source.indexOf("~~", si + 2)
                    if (close > si + 2 && !source.substring(si + 2, close).contains('\n')) {
                        out.append(source, si + 2, close)
                        si = close + 2
                    } else {
                        out.append("~~")
                        si += 2
                    }
                }
                source[si] == '*' && !source.startsWith("**", si) -> {
                    val close = findSingleStarClose(source, si + 1)
                    if (close > si + 1) {
                        out.append(source, si + 1, close)
                        si = close + 1
                    } else {
                        out.append('*')
                        si += 1
                    }
                }
                source[si] == '`' -> {
                    val close = source.indexOf('`', si + 1)
                    if (close > si + 1 && !source.substring(si + 1, close).contains('\n')) {
                        out.append(source, si + 1, close)
                        si = close + 1
                    } else {
                        out.append('`')
                        si += 1
                    }
                }
                source[si] == '[' -> {
                    // [label](dest): keep the label, drop the destination.
                    // A bare '[' without a link shape stays literal.
                    val labelClose = source.indexOf(']', si + 1)
                    if (labelClose > si + 1 && labelClose + 1 < n && source[labelClose + 1] == '(') {
                        val destClose = source.indexOf(')', labelClose + 2)
                        if (destClose > labelClose + 1 && !source.substring(si, destClose).contains('\n')) {
                            out.append(source, si + 1, labelClose)
                            si = destClose + 1
                        } else {
                            out.append('[')
                            si += 1
                        }
                    } else {
                        out.append('[')
                        si += 1
                    }
                }
                else -> {
                    out.append(source[si])
                    si += 1
                }
            }
        }
        return out.toString()
    }

    fun of(source: String): List<InlineStyle> {
        val out = ArrayList<InlineStyle>()
        var si = 0
        var di = 0
        val n = source.length
        while (si < n) {
            when {
                source.startsWith("**", si) -> {
                    val close = source.indexOf("**", si + 2)
                    if (close > si + 2 && !source.substring(si + 2, close).contains('\n')) {
                        val len = close - (si + 2)
                        out.add(InlineStyle(di, di + len, InlineOp.BOLD))
                        di += len
                        si = close + 2
                    } else {
                        di += 2
                        si += 2
                    }
                }
                source.startsWith("~~", si) -> {
                    val close = source.indexOf("~~", si + 2)
                    if (close > si + 2 && !source.substring(si + 2, close).contains('\n')) {
                        val len = close - (si + 2)
                        out.add(InlineStyle(di, di + len, InlineOp.STRIKE))
                        di += len
                        si = close + 2
                    } else {
                        di += 2
                        si += 2
                    }
                }
                source[si] == '*' && !source.startsWith("**", si) -> {
                    val close = findSingleStarClose(source, si + 1)
                    if (close > si + 1) {
                        val len = close - (si + 1)
                        out.add(InlineStyle(di, di + len, InlineOp.ITALIC))
                        di += len
                        si = close + 1
                    } else {
                        di += 1
                        si += 1
                    }
                }
                source[si] == '`' -> {
                    val close = source.indexOf('`', si + 1)
                    if (close > si + 1 && !source.substring(si + 1, close).contains('\n')) {
                        val len = close - (si + 1)
                        out.add(InlineStyle(di, di + len, InlineOp.CODE))
                        di += len
                        si = close + 1
                    } else {
                        di += 1
                        si += 1
                    }
                }
                source[si] == '[' -> {
                    // [label](dest): label occupies display space; spans
                    // inside the label recurse with shifted ranges.
                    val labelClose = source.indexOf(']', si + 1)
                    if (labelClose > si + 1 && labelClose + 1 < n && source[labelClose + 1] == '(') {
                        val destClose = source.indexOf(')', labelClose + 2)
                        if (destClose > labelClose + 1 && !source.substring(si, destClose).contains('\n')) {
                            val inner = source.substring(si + 1, labelClose)
                            for (s in of(inner)) {
                                out.add(InlineStyle(di + s.start, di + s.end, s.op))
                            }
                            di += inner.length
                            si = destClose + 1
                        } else {
                            di += 1
                            si += 1
                        }
                    } else {
                        di += 1
                        si += 1
                    }
                }
                else -> {
                    di += 1
                    si += 1
                }
            }
        }
        return out
    }

    private fun findSingleStarClose(
        source: String,
        from: Int,
    ): Int {
        var i = from
        while (i < source.length) {
            if (source[i] == '*' && !source.startsWith("**", i) &&
                (i == 0 || !source.startsWith("**", i - 1))
            ) {
                if (!source.substring(from, i).contains('\n')) return i
                return -1
            }
            i++
        }
        return -1
    }
}
