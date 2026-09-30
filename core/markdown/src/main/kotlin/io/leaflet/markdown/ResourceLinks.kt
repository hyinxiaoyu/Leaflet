package io.leaflet.markdown

/**
 * In-text document-link destination (T10, REQ-0009 FR-1–FR-5/FR-10).
 *
 * [path] is the raw path part as authored (percent-encoded, fragment split
 * off into [fragment]); it is normalized against the referring document's
 * directory at resolution time, never at parse time.
 */
data class BlockLink(
    val label: String,
    val destination: String,
    val kind: DocLinkKind,
    val path: String = "",
    val fragment: String? = null,
)

/** In-text image destination (T10, REQ-0009 FR-6–FR-9). */
data class BlockImage(
    val alt: String,
    val destination: String,
    val kind: ImageKind,
    val path: String = "",
)

/** Classification of a Markdown document link destination. */
enum class DocLinkKind {
    /** Blank destination (`[x]()`): no row, no action. */
    EMPTY,

    /** Same-document anchor (`#section`): scroll in place. */
    SAME_DOC_ANCHOR,

    /** In-library MD/TXT target: resolve via the authorized tree. */
    LIBRARY_DOC,

    /** Non-document target (`.pdf`, `.png` link, …): explicit prompt, never rendered as a doc. */
    UNSUPPORTED_MEDIA,

    /** OS-handoff schemes: acted on only for a user tap, never prefetched. */
    EXTERNAL,

    /** Unknown scheme: never executed, never crashes. */
    UNSUPPORTED_SCHEME,

    /** Absolute path / backslash / otherwise unaddressable: unreachable prompt. */
    UNREACHABLE,
}

/** Classification of a Markdown image destination. */
enum class ImageKind {
    /** Blank source: skipped, never a broken box. */
    EMPTY,

    /** Tree-relative still: resolved + viewport-sampled through SAF. */
    LOCAL,

    /** `http(s)` / protocol-relative: static placeholder, never fetched. */
    REMOTE,

    /** SVG and friends: static placeholder (no decoder, no WebView). */
    UNSUPPORTED_FORMAT,

    /** Unknown scheme (incl. `data:`): never executed. */
    UNSUPPORTED_SCHEME,

    /** Absolute path / backslash: unreachable placeholder. */
    UNREACHABLE,
}

/**
 * Pure link/image destination rules (T10, REQ-0009 FR-1–FR-4/FR-9/FR-10).
 *
 * JVM-testable with zero Android/storage imports. The AUTHORITY for path
 * normalization stays in `:core:storage` (`RelativePaths`); [resolveDocPath]
 * and [resolveImagePath] mirror those exact rules (in-tree `..` allowed,
 * tree escape rejected, encoded navigation rejected, content URIs never
 * concatenated) so image loading and unit tests share one behavior.
 */
object ResourceLinks {
    /** Schemes handed to the OS only on an explicit user tap (FR-10; no confirm, per REQ-0009 UX). */
    val EXTERNAL_SCHEMES: Set<String> = setOf("http", "https", "mailto", "tel")

    /** Link targets carrying these extensions open in-reader; other extensions are media. */
    val DOC_EXTENSIONS: Set<String> = setOf("md", "markdown", "txt")

    /** Still formats decoded via the platform sampler; SVG is deliberately excluded. */
    val DECODABLE_IMAGE_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun classifyDocLink(
        raw: String,
        label: String = "",
    ): DocLinkRef {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return DocLinkRef(label, raw, DocLinkKind.EMPTY)
        if (trimmed.startsWith("#")) {
            return DocLinkRef(label, raw, DocLinkKind.SAME_DOC_ANCHOR, fragment = trimmed.substring(1))
        }
        val scheme = schemeOf(trimmed)
        if (scheme != null) {
            return if (scheme in EXTERNAL_SCHEMES) {
                DocLinkRef(label, raw, DocLinkKind.EXTERNAL)
            } else {
                DocLinkRef(label, raw, DocLinkKind.UNSUPPORTED_SCHEME)
            }
        }
        if (trimmed.startsWith("/") || '\\' in trimmed) {
            return DocLinkRef(label, raw, DocLinkKind.UNREACHABLE)
        }
        val beforeFragment = trimmed.substringBefore('#')
        val fragment = if ('#' in trimmed) trimmed.substringAfter('#') else null
        val pathPart = beforeFragment.substringBefore('?').trim()
        if (pathPart.isEmpty()) return DocLinkRef(label, raw, DocLinkKind.EMPTY, fragment = fragment)
        if ('\\' in pathPart) return DocLinkRef(label, raw, DocLinkKind.UNREACHABLE, pathPart, fragment)
        // Escape-vs-climb needs the referring document's directory (resolved
        // later via resolveDocPath); classification stays permissive here so
        // in-tree `..` (e.g. `../sibling.md`) is not mislabeled. The
        // resolution layer rejects escapes as unreachable.
        val ext = extensionOf(pathPart)
        if (ext.isNotEmpty() && ext !in DOC_EXTENSIONS) {
            return DocLinkRef(label, raw, DocLinkKind.UNSUPPORTED_MEDIA, pathPart, fragment)
        }
        return DocLinkRef(label, raw, DocLinkKind.LIBRARY_DOC, pathPart, fragment)
    }

    fun classifyImage(
        raw: String,
        alt: String = "",
    ): ImageRef {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ImageRef(alt, raw, ImageKind.EMPTY)
        // Protocol-relative URLs are remote (REQ-0009 scope: `//` → static placeholder).
        if (trimmed.startsWith("//")) return ImageRef(alt, raw, ImageKind.REMOTE)
        val scheme = schemeOf(trimmed)
        if (scheme != null) {
            return if (scheme == "http" || scheme == "https") {
                ImageRef(alt, raw, ImageKind.REMOTE)
            } else {
                ImageRef(alt, raw, ImageKind.UNSUPPORTED_SCHEME)
            }
        }
        if (trimmed.startsWith("/") || '\\' in trimmed) return ImageRef(alt, raw, ImageKind.UNREACHABLE)
        val pathPart = trimmed.substringBefore('#').substringBefore('?').trim()
        if (pathPart.isEmpty()) return ImageRef(alt, raw, ImageKind.EMPTY)
        if ('\\' in pathPart) return ImageRef(alt, raw, ImageKind.UNREACHABLE)
        val ext = extensionOf(pathPart)
        if (ext == "svg" || ext == "svgz") {
            return ImageRef(alt, raw, ImageKind.UNSUPPORTED_FORMAT, pathPart)
        }
        return ImageRef(alt, raw, ImageKind.LOCAL, pathPart)
    }

    /**
     * Fragment normalization, frozen with the TOC rule (REQ-0009 Open
     * Questions): percent-decode (UTF-8), then the same [Headings.slugify] the
     * TOC anchors use. Already-slugged fragments (`overview-1`) are
     * idempotent; `#Overview` and `#%E6%A6%82%E8%BF%B0` land on the same
     * anchor as the literal heading text.
     */
    fun normalizeFragment(raw: String): String {
        val trimmed = raw.trim()
        val decoded = percentDecode(trimmed) ?: trimmed
        return Headings.slugify(decoded)
    }

    /**
     * Consistency mirror of `:core:storage` `RelativePaths.resolveSibling`
     * (fragment/query already split by the caller). In-tree `..` allowed,
     * tree escape rejected, encoded `.`/`..`/`/`/NUL rejected, and no content
     * URI is ever built here — the result feeds a metadata/tree lookup.
     */
    fun resolveDocPath(
        baseRelativePath: String,
        pathPart: String,
    ): String? {
        val trimmed = pathPart.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("/") || '\\' in trimmed) return null
        if (schemeOf(trimmed) != null) return null
        val baseDir = baseRelativePath.substringBeforeLast('/', "")
        val combined = if (baseDir.isEmpty()) trimmed else "$baseDir/$trimmed"
        return normalizeSegments(combined)
    }

    /** Same mirror for image refs (identical rules; separate entry for call-site clarity). */
    fun resolveImagePath(
        baseRelativePath: String,
        pathPart: String,
    ): String? = resolveDocPath(baseRelativePath, pathPart)

    internal fun schemeOf(ref: String): String? {
        val colon = ref.indexOf(':')
        if (colon <= 0) return null
        val scheme = ref.substring(0, colon)
        if (!scheme[0].isLetter()) return null
        if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
        return scheme.lowercase()
    }

    private fun extensionOf(pathPart: String): String {
        val fileName = pathPart.substringAfterLast('/')
        if ('.' !in fileName) return ""
        return fileName.substringAfterLast('.').lowercase()
    }

    private fun normalizeSegments(path: String): String? {
        val out = ArrayDeque<String>()
        for (raw in path.split('/')) {
            if (raw.isEmpty() || raw == ".") continue
            if (raw == "..") {
                if (out.isEmpty()) return null
                out.removeLast()
                continue
            }
            out.addLast(decodeSegment(raw) ?: return null)
        }
        if (out.isEmpty()) return null
        return out.joinToString("/")
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
                for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.add(b)
                i += 1
            }
        }
        val decoded =
            try {
                bytes.toByteArray().toString(Charsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
        if (decoded.isEmpty() || decoded == "." || decoded == "..") return null
        if ('/' in decoded || '\u0000' in decoded) return null
        return decoded
    }

    private fun percentDecode(raw: String): String? {
        if ('%' !in raw) return raw
        val bytes = ArrayList<Byte>(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%') {
                if (i + 2 >= raw.length) return null
                val value = raw.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                bytes.add(value.toByte())
                i += 3
            } else {
                if (c == '\u0000') return null
                for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.add(b)
                i += 1
            }
        }
        return try {
            bytes.toByteArray().toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

/** Classified document-link destination (see [ResourceLinks.classifyDocLink]). */
data class DocLinkRef(
    val label: String,
    val raw: String,
    val kind: DocLinkKind,
    val pathPart: String = "",
    val fragment: String? = null,
)

/** Classified image destination (see [ResourceLinks.classifyImage]). */
data class ImageRef(
    val alt: String,
    val raw: String,
    val kind: ImageKind,
    val pathPart: String = "",
)
