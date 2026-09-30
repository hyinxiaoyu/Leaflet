package io.leaflet.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.RenderBlock
import io.leaflet.reader.BlockHighlight
import io.leaflet.reader.SearchHighlight

/**
 * Pure presentation mapping for reader entries (R2-03, extracted from
 * ReaderScreen for page-testability without composition).
 *
 * No Android views, no Markwon, no I/O here: callers feed a [RenderBlock]
 * (+ optional search state) and get the exact text + style ops the entry
 * composables bind. The mapping is the contract the app page tests assert;
 * the composables only apply it.
 */
object EntryBindings {
    /**
     * Marker-free string a plain (non-Spanned) entry renders for [block]
     * (R2 DEF-003): the visible text a reader actually sees, so search
     * coordinates computed on `blockTexts` need no second coordinate system.
     * Code bodies keep their fence-stripped copy text.
     */
    fun displayedText(block: RenderBlock): String =
        when {
            block.kind == BlockKind.CODE -> block.codeForCopy().ifBlank { " " }
            // R2 DEF-001: literal blocks show their text as-is — the markers
            // are visible characters, not syntax to strip.
            block.literal -> block.text.ifBlank { " " }
            else -> InlineStyles.strip(block.text).ifBlank { " " }
        }

    /**
     * Highlight mapping for one bound block (T11 coordinates, R2 DEF-003).
     *
     * [displayed] is THE string the entry actually renders — the Compose
     * entry passes its stripped text, the Spanned entry passes the text
     * Markwon produced. Match ranges live in visible-text coordinates
     * (`blockTexts`), and [SearchHighlight.highlightForBlock] re-locates each
     * match substring inside [displayed]; passing a mismatched string was
     * what shifted highlights off the matched word.
     */
    fun highlightFor(
        document: ParsedDocument,
        blockIndex: Int,
        displayed: String,
        search: io.leaflet.reader.SearchUiState,
    ): BlockHighlight {
        if (search.matches.isEmpty() || search.query.isBlank()) return BlockHighlight.EMPTY
        val visible = document.blockTexts.getOrElse(blockIndex) { "" }
        if (visible.isEmpty()) return BlockHighlight.EMPTY
        val blockHits = search.matches.filter { it.blockIndex == blockIndex }
        if (blockHits.isEmpty()) return BlockHighlight.EMPTY
        val ranges = blockHits.map { SearchHighlight.rangeOf(it) }
        val current =
            search.current?.takeIf { it.blockIndex == blockIndex }?.let { SearchHighlight.rangeOf(it) }
        return SearchHighlight.highlightForBlock(displayed, visible, ranges, current)
            ?: BlockHighlight.EMPTY
    }

    /**
     * Cell-local search highlight (R2-06, REQ-0008 AC-3; R2 DEF-003 second
     * round): maps the block-level [BlockHighlight] — expressed in the
     * block's OWN visible string ([blockVisible]) — onto ONE stripped cell.
     *
     * Identity first: when [cellSpan] is known, only the ranges that actually
     * INTERSECT this cell's span can light it up, and they are converted to
     * cell-local coordinates by arithmetic. Two cells holding the same text
     * used to both become "the current match" because the old code searched
     * every cell for the current match's substring — it had no idea which cell
     * the match belonged to.
     *
     * [cellSpan] is this cell's `[start,end)` range inside [blockVisible]
     * (see [tableCellSpans]). Null (grid/span mismatch, legacy callers) keeps
     * the old substring-search behaviour as a documented fallback — it can
     * only place spans that exist in this cell, never across cells.
     *
     * @param strippedCell the exact string the cell composable renders
     *   (inline markers already consumed).
     */
    fun cellSearchHighlight(
        strippedCell: String,
        highlight: BlockHighlight,
        blockVisible: String,
        cellSpan: IntRange? = null,
    ): BlockHighlight? {
        if (strippedCell.isEmpty() || blockVisible.isEmpty()) return null
        if (highlight.others.isEmpty() && highlight.current == null) return null
        return if (cellSpan != null) {
            positionalCellHighlight(strippedCell, highlight, blockVisible, cellSpan)
        } else {
            legacyCellHighlight(strippedCell, highlight, blockVisible)
        }
    }

    /**
     * Row-major spans of every cell inside the block's visible string
     * (R2 DEF-003): the walker joins cells with `\t` and rows with `\n`, so
     * splitting that string yields the exact source of every cell range. Empty
     * cells are preserved (an empty segment is still a cell).
     */
    fun tableCellSpans(blockVisible: String): List<List<IntRange>> {
        if (blockVisible.isEmpty()) return emptyList()
        val rows = ArrayList<List<IntRange>>()
        var rowStart = 0
        for (rowText in blockVisible.split("\n")) {
            val spans = ArrayList<IntRange>()
            var cellStart = rowStart
            for (cell in rowText.split("\t")) {
                val end = cellStart + cell.length
                spans.add(cellStart until end)
                cellStart = end + 1
            }
            rows.add(spans)
            rowStart += rowText.length + 1
        }
        return rows
    }

    /** Identity-preserving mapping: intersect the block ranges with [cellSpan]. */
    private fun positionalCellHighlight(
        strippedCell: String,
        highlight: BlockHighlight,
        blockVisible: String,
        cellSpan: IntRange,
    ): BlockHighlight? {
        val from = cellSpan.first.coerceIn(0, blockVisible.length)
        val to = (cellSpan.last + 1).coerceIn(0, blockVisible.length)
        if (from >= to) return null
        val cellText = blockVisible.substring(from, to)

        /** Block range → this cell's local range, or null when it is elsewhere. */
        fun localOf(range: IntRange): IntRange? {
            val start = maxOf(range.first, from)
            val end = minOf(range.last + 1, to)
            if (start >= end) return null
            return (start - from) until (end - from)
        }

        /**
         * Cell-local (cellText) range → the rendered string. The two agree for
         * every supported cell shape; when they differ (e.g. the slice and the
         * renderer disagree on whitespace) the substring is located INSIDE this
         * cell only — never in a sibling.
         */
        fun intoRendered(local: IntRange): IntRange? {
            if (cellText == strippedCell) return local.takeIf { it.first >= 0 && it.last < strippedCell.length }
            val needle = cellText.substring(local.first.coerceIn(0, cellText.length), (local.last + 1).coerceIn(0, cellText.length))
            if (needle.isEmpty()) return null
            val hit = strippedCell.indexOf(needle)
            if (hit < 0) return null
            return hit until (hit + needle.length)
        }

        val others = highlight.others.mapNotNull(::localOf).mapNotNull(::intoRendered).distinct()
        val current = highlight.current?.let(::localOf)?.let(::intoRendered)
        if (others.isEmpty() && current == null) return null
        return BlockHighlight(
            others = others.filterNot { it == current },
            current = current,
            // A cell-local miss never means "bold the whole block" — the
            // block-level fallback already drives that decision.
            blockFallback = false,
        )
    }

    /** Pre-DEF-003 behaviour: search this cell for the match substrings. */
    private fun legacyCellHighlight(
        strippedCell: String,
        highlight: BlockHighlight,
        blockVisible: String,
    ): BlockHighlight? {
        fun locateAll(needle: String): List<IntRange> {
            if (needle.isEmpty()) return emptyList()
            val out = ArrayList<IntRange>()
            var from = 0
            while (from < strippedCell.length) {
                val hit = strippedCell.indexOf(needle, from)
                if (hit < 0) break
                out.add(hit until (hit + needle.length))
                from = hit + 1
            }
            return out
        }

        fun substringOf(range: IntRange): String? {
            val from = range.first.coerceIn(0, blockVisible.length)
            val to = (range.last + 1).coerceIn(0, blockVisible.length)
            if (from >= to) return null
            return blockVisible.substring(from, to)
        }
        val others = highlight.others.mapNotNull(::substringOf).flatMap(::locateAll).distinct()
        val curMapped = highlight.current?.let(::substringOf)?.let { locateAll(it).singleOrNull() }
        if (others.isEmpty() && curMapped == null) return null
        return BlockHighlight(
            others = if (curMapped != null) others.filterNot { it == curMapped } else others,
            current = curMapped,
            blockFallback = false,
        )
    }

    /**
     * Static (animation-free, E-Ink safe) search highlighting: other matches
     * get a flat background span, the current match an inverted bold span.
     * Plain passthrough when [highlight] is [BlockHighlight.EMPTY].
     */
    fun annotated(
        displayed: String,
        highlight: BlockHighlight,
    ): AnnotatedString {
        if (displayed.isEmpty()) return AnnotatedString("")
        if (highlight.others.isEmpty() && highlight.current == null) {
            return AnnotatedString(displayed)
        }
        val otherBg = Color(0xFFFFFF00)
        val currentBg = Color(0xFF000000)
        val currentFg = Color(0xFFFFFFFF)
        return buildAnnotatedString {
            append(displayed)
            for (range in highlight.others) {
                val from = range.first.coerceIn(0, displayed.length)
                val to = (range.last + 1).coerceIn(0, displayed.length)
                if (from < to) addStyle(SpanStyle(background = otherBg), from, to)
            }
            highlight.current?.let { range ->
                val from = range.first.coerceIn(0, displayed.length)
                val to = (range.last + 1).coerceIn(0, displayed.length)
                if (from < to) {
                    addStyle(
                        SpanStyle(
                            background = currentBg,
                            color = currentFg,
                            fontWeight = FontWeight.Bold,
                        ),
                        from,
                        to,
                    )
                }
            }
        }
    }

    /**
     * Cell grid for TABLE blocks (R2-03 native table): splits the
     * pipe-delimited source slice into rows × cells, dropping the `---`
     * delimiter row. Empty when the slice is not a grid (fallback path
     * renders source text, never a crash).
     */
    fun tableCells(block: RenderBlock): List<List<String>> {
        if (block.kind != BlockKind.TABLE) return emptyList()
        val lines = block.source.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()

        fun cellsOf(line: String): List<String> {
            var t = line.trim()
            if (t.startsWith("|")) t = t.drop(1)
            if (t.endsWith("|")) t = t.dropLast(1)
            return t.split("|").map { it.trim().replace("\\|", "|") }
        }

        fun isDelimiter(line: String): Boolean {
            val cells = cellsOf(line)
            if (cells.isEmpty()) return false
            return cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' || it.isWhitespace() } }
        }

        val rows = ArrayList<List<String>>()
        for (line in lines) {
            if (!line.contains("|")) continue
            if (isDelimiter(line)) continue
            rows.add(cellsOf(line))
        }
        return rows
    }

    /**
     * Inline-style ops for one block's displayed text (R2-03 rich-text
     * contract): bold/italic/strikethrough/code ranges derived from the
     * block SOURCE slice (markers live in source, never in visible text).
     * The entry composables apply these natively; code bodies are excluded
     * (monospace wins, markers are literal there).
     */
    fun inlineStyles(block: RenderBlock): List<InlineStyle> {
        if (block.kind == BlockKind.CODE || block.kind == BlockKind.PLAIN_FALLBACK) return emptyList()
        return InlineStyles.of(block.source.ifEmpty { block.text })
    }
}

/** One inline style range over displayed text: [start,end) + op. */
data class InlineStyle(
    val start: Int,
    val end: Int,
    val op: InlineOp,
)

enum class InlineOp {
    BOLD,
    ITALIC,
    STRIKE,
    CODE,
}
