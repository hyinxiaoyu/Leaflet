package io.leaflet.markdown

import io.leaflet.model.DocumentId

/** One table-of-contents entry: heading level, text, deduplicated anchor. */
data class TocEntry(
    val level: Int,
    val text: String,
    val anchor: String,
)

/**
 * One renderable content block. The Reader MUST NOT assume one file maps to
 * one TextView; large single blocks get a plain-text fallback (T06).
 *
 * T09 typography (REQ-0007): `source` is the exact per-block source slice
 * (fence markers included for code/table) used for Markwon Spanned rendering
 * and verbatim code copy; `text` remains the legacy plain-text binding. New
 * call sites MUST prefer [source] and treat empty-text blocks as degraded —
 * [text] stays only for the integer-indexed legacy position tests.
 */
data class RenderBlock(
    val index: Int,
    val kind: BlockKind,
    val text: String,
    /** Exact source slice (T09 Spanned/copy seam; empty only for legacy test rows). */
    val source: String = text,
    /** Fence info line for CODE blocks (`kotlin`, `` for none/unknown); else empty. */
    val language: String = "",
    /** In-text document links extracted from this block (T10, REQ-0009; empty when none). */
    val links: List<BlockLink> = emptyList(),
    /** In-text image destinations extracted from this block (T10, REQ-0009; empty when none). */
    val images: List<BlockImage> = emptyList(),
    /**
     * Literal-text block (R2 DEF-001): the text was never Markdown, so no
     * entry may re-parse it. Set for every TXT chunk and for PLAIN_FALLBACK
     * degradation chunks (oversized blocks, encoding-damaged content). When
     * true, [text] IS the displayed string — `#`, `**`, `[label](t)` stay
     * visible characters, never syntax.
     */
    val literal: Boolean = false,
    /**
     * Verbatim code body as DATA (R2 DEF-004 second round): the parser stores
     * the node's literal here for [BlockKind.CODE] blocks, so the copy path
     * never has to guess the fence boundaries by scanning [source]. Scanning
     * truncated a body that itself contains a fence line (a four-backtick or
     * `~~~` fence around a literal ``` line). Null for non-code blocks and for
     * legacy hand-built test rows (which fall back to the scan).
     */
    val codeBody: String? = null,
) {
    /** Display label for the code header (FR-5): empty when no language was declared. */
    val languageLabel: String get() = language

    /**
     * Verbatim code body for the copy button (FR-6): fence markers stripped,
     * trailing newline trimmed, interior bytes untouched.
     *
     * Prefers [codeBody] (exact, fence-agnostic). The legacy scan runs only
     * when no body was recorded — and it is intentionally conservative: it
     * stops at the first fence-looking line, which is why the data path
     * exists.
     */
    fun codeForCopy(): String {
        if (kind != BlockKind.CODE) return ""
        codeBody?.let { return it.trimEnd('\n') }
        val lines = source.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("```") }
        val endFromStart =
            if (start >= 0) {
                lines.subList(start + 1, lines.size)
                    .indexOfFirst { it.trimStart().startsWith("```") }
            } else {
                -1
            }
        val body =
            if (start >= 0 && endFromStart >= 0) {
                lines.subList(start + 1, start + 1 + endFromStart).joinToString("\n")
            } else if (start >= 0) {
                lines.subList(start + 1, lines.size).joinToString("\n")
            } else {
                // Indented code or legacy rows without a source slice.
                source.ifEmpty { text }
            }
        return body.trimEnd('\n')
    }
}

/**
 * Table render mode (REQ-0007 FR-8/FR-9, frozen T09): only NORMAL ships.
 *
 * - NORMAL: wide tables sit in a horizontal-scroll container; every column
 *   is reachable on narrow screens. This is the sole rendered path.
 * - WRAP (column-width adaptive wrapping) and CARD (wide-table → vertical
 *   field cards) are M2+ follow-ups. They are recorded here so future work
 *   has a named seam, but they MUST NOT gain UI switches until implemented
 *   (FR-9). No code path may branch on them today.
 */
enum class TableRenderMode {
    NORMAL,

    /** M2+ follow-up: column-width adaptive wrapping (not implemented). */
    WRAP,

    /** M2+ follow-up: wide tables as vertical field cards (not implemented). */
    CARD,
}

/**
 * Typography style parameters (REQ-0007 FR-1–FR-4/FR-7/FR-10, frozen T09).
 *
 * All values are persistence-layer steps flowing one way: DataStore
 * (`SettingsStore`, `:app`) → Reader entry binding. The Reader never reads
 * storage directly. Steps are clamped by [ReaderStyle.coerce]; unknown font
 * families fall back to `"system"`.
 */
data class ReaderStyle(
    /** Font-size step, `FONT_SIZE_MIN..FONT_SIZE_MAX` (default `FONT_SIZE_DEFAULT`). */
    val fontSizeStep: Int = FONT_SIZE_DEFAULT,
    /** System font family (`"system"` = platform default; no bundled fonts). */
    val fontFamily: String = FONT_FAMILY_DEFAULT,
    /** Line-spacing step, `LINE_SPACING_MIN..LINE_SPACING_MAX`. */
    val lineSpacingStep: Int = LINE_SPACING_DEFAULT,
    /** Page-margin step, `PAGE_MARGIN_MIN..PAGE_MARGIN_MAX`. */
    val pageMarginStep: Int = PAGE_MARGIN_DEFAULT,
    /** Code-block soft-wrap (E-Ink default ON per REQ-0007 FR-7). */
    val codeSoftWrap: Boolean = CODE_SOFT_WRAP_DEFAULT,
) {
    companion object {
        const val FONT_SIZE_MIN = 0
        const val FONT_SIZE_MAX = 4
        const val FONT_SIZE_DEFAULT = 2
        const val LINE_SPACING_MIN = 0
        const val LINE_SPACING_MAX = 2
        const val LINE_SPACING_DEFAULT = 1
        const val PAGE_MARGIN_MIN = 0
        const val PAGE_MARGIN_MAX = 2
        const val PAGE_MARGIN_DEFAULT = 1
        const val FONT_FAMILY_DEFAULT = "system"
        const val CODE_SOFT_WRAP_DEFAULT = true

        /**
         * Whitelisted system font families (REQ-0007 FR-2, T09 spike outcome).
         * Fixed candidate list chosen over family-query enumeration: the query
         * API surface differs across OEM builds, while these three resolve on
         * every Android device ([android.graphics.Typeface] constants) with
         * monospace reserved for code blocks, never body text.
         */
        val KNOWN_FONT_FAMILIES: Set<String> = setOf("system", "sans-serif", "serif")

        /** `true` when [family] is a selectable body-text family. */
        fun isKnownFontFamily(family: String): Boolean = family in KNOWN_FONT_FAMILIES

        /** Body-text scale for a font-size step (step 2 = 1.15× baseline). */
        fun fontScaleForStep(step: Int): Float = 0.85f + 0.15f * step.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)

        /** Line-spacing multiplier for a line-spacing step (1.00 / 1.25 / 1.50). */
        fun lineSpacingMultiplierForStep(step: Int): Float = 1.0f + 0.25f * step.coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX)

        /** Horizontal page-margin dp for a margin step. */
        fun pageMarginDpForStep(step: Int): Int =
            when (step.coerceIn(PAGE_MARGIN_MIN, PAGE_MARGIN_MAX)) {
                0 -> 8
                1 -> 16
                else -> 24
            }
    }

    /** Clamp steps to their bands and unknown families to `"system"`. */
    fun coerce(): ReaderStyle =
        copy(
            fontSizeStep = fontSizeStep.coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX),
            fontFamily =
                if (fontFamily.isBlank() || fontFamily !in KNOWN_FONT_FAMILIES) {
                    FONT_FAMILY_DEFAULT
                } else {
                    fontFamily
                },
            lineSpacingStep = lineSpacingStep.coerceIn(LINE_SPACING_MIN, LINE_SPACING_MAX),
            pageMarginStep = pageMarginStep.coerceIn(PAGE_MARGIN_MIN, PAGE_MARGIN_MAX),
        )
}

enum class BlockKind {
    HEADING,
    PARAGRAPH,
    LIST,
    QUOTE,
    CODE,
    TABLE,
    SEPARATOR,

    /** Oversized single block served as bounded plain text (see DegradedNotice). */
    PLAIN_FALLBACK,
}

/** Block <-> visible-text <-> source-offset mapping for search/jump (T06/T11). */
data class PositionMap(
    val blockCount: Int,
)

/** Block address for TOC jumps and position restore. */
data class BlockLocation(val blockIndex: Int, val blockOffset: Int)

/** One literal visible-text match for in-document search. */
data class MatchLocation(val blockIndex: Int, val startInBlock: Int, val endInBlock: Int)

/**
 * Position-map operations (reader.md §4). The T01 [PositionMap] data shape is
 * kept for compatibility; this interface carries the behavior T06/T11 bind.
 */
interface PositionMapOps {
    fun blockCount(): Int

    fun visibleTextOf(blockIndex: Int): String

    fun locateAnchor(anchor: String): BlockLocation?

    /**
     * Literal per-block visible-text matches in document order (R2-06):
     * blank queries yield nothing; the scan stops at
     * [SearchLimits.MAX_SEARCH_MATCHES] via [collectBlockMatches], so a
     * dense repeat returns the first window, never an OOM-sized list.
     * Callers needing the truncation flag use `:core:reader`
     * `DocumentSearch.searchCapped`.
     */
    fun locateQuery(query: String): List<MatchLocation>
}

/**
 * Parsed document surface (frozen T01 contract §6).
 *
 * Title rule (frozen): frontmatter title → first H1 → file name (no extension).
 * `degraded` marks oversized-block or encoding-damage fallback; `degradedNotice`
 * carries the banner text (null when clean). Empty files parse to zero blocks
 * with `degraded=false` — emptiness is a state, not an error.
 */
data class ParsedDocument(
    val documentId: DocumentId,
    val title: String,
    val toc: List<TocEntry>,
    val blocks: List<RenderBlock>,
    val positionMap: PositionMap,
    val degraded: Boolean = false,
    val degradedNotice: String? = null,
    val blockTexts: List<String> = emptyList(),
    val positionOps: PositionMapOps? = null,
    /**
     * Whole-document literal flag (R2 DEF-001): true for the TXT path, whose
     * blocks are never Markdown. Entries MUST render [blockTexts] as literal
     * text instead of re-parsing Markdown. Markdown documents are false; an
     * individual Markdown block can still be [RenderBlock.literal] (fallback).
     */
    val literalText: Boolean = false,
)

/** Minimal parser entry point. TODO(T06): cancellable background parsing. */
interface DocumentParser {
    suspend fun parse(
        documentId: DocumentId,
        fileName: String,
        content: String,
    ): ParsedDocument
}
