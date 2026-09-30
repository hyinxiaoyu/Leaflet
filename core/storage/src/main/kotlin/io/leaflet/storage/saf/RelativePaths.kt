package io.leaflet.storage.saf

/**
 * Pure in-tree relative-path resolution (REQ-0009 FR-1–FR-3, overview
 * contract 3).
 *
 * Rules, applied in order:
 *
 * 1. Blank refs, same-document fragments (`#anchor`) and query-only refs
 *    resolve to nothing here (null) — same-document anchors are T08/T10
 *    Reader business, not document resolution.
 * 2. Any URI scheme (`http:`, `foo:`, …) returns null — remote targets are
 *    never auto-fetched (offline default) and unknown schemes never execute.
 * 3. Absolute refs (`/…`) and backslash separators return null: addressing
 *    is strictly relative to the referring document's directory, and `\` is
 *    a legal filename character, never a separator.
 * 4. `.` / `..` / duplicate-slash segments are normalized; `..` that would
 *    escape the library root returns null (authorization-tree confinement).
 * 5. Percent-encoded segments are decoded (UTF-8) so `空格/%E5%AE%89.md`
 *    round-trips. A segment that decodes to `.`, `..`, `/` or NUL is
 *    rejected (encoded navigation or separator smuggling returns null).
 *
 * No content URI is ever built by string concatenation here — the result is
 * a library-relative path for a metadata lookup; stream access always goes
 * through the stored content URI via SAF (ADR-0002).
 */
object RelativePaths {
    fun resolveSibling(
        baseRelativePath: String,
        ref: String,
    ): String? {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) return null
        val beforeFragment = trimmed.substringBefore('#')
        if (beforeFragment.isEmpty()) return null
        val pathPart = beforeFragment.substringBefore('?').trim()
        if (pathPart.isEmpty()) return null
        if (hasScheme(pathPart)) return null
        if (pathPart.startsWith('/')) return null
        if ('\\' in pathPart) return null

        val baseDir = baseRelativePath.substringBeforeLast('/', "")
        val combined = if (baseDir.isEmpty()) pathPart else "$baseDir/$pathPart"
        return normalize(combined)
    }

    fun normalize(path: String): String? {
        val out = ArrayDeque<String>()
        for (raw in path.split('/')) {
            if (raw.isEmpty() || raw == ".") continue
            if (raw == "..") {
                if (out.isEmpty()) return null
                out.removeLast()
                continue
            }
            val decoded = decodeSegment(raw) ?: return null
            out.addLast(decoded)
        }
        if (out.isEmpty()) return null
        return out.joinToString("/")
    }

    private fun hasScheme(ref: String): Boolean {
        val colon = ref.indexOf(':')
        if (colon <= 0) return false
        val scheme = ref.substring(0, colon)
        if (!scheme[0].isLetter()) return false
        return scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }

    private fun decodeSegment(raw: String): String? {
        if ('%' !in raw) {
            if ('\u0000' in raw) return null
            return raw
        }
        val bytes = ArrayList<Byte>(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%') {
                if (i + 2 >= raw.length) return null
                val hex = raw.substring(i + 1, i + 3)
                val value = hex.toIntOrNull(16) ?: return null
                bytes.add(value.toByte())
                i += 3
            } else {
                if (c == '\u0000') return null
                // Non-ASCII source chars ride through as UTF-8 bytes.
                for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.add(b)
                i += 1
            }
        }
        val decoded =
            try {
                bytes.toByteArray().toString(Charsets.UTF_8)
            } catch (e: Exception) {
                return null
            }
        if (decoded.isEmpty() || decoded == "." || decoded == "..") return null
        if ('/' in decoded || '\u0000' in decoded) return null
        return decoded
    }
}
