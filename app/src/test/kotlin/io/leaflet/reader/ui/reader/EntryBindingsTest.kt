package io.leaflet.reader.ui.reader

import androidx.compose.ui.text.font.FontWeight
import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.RenderBlock
import io.leaflet.reader.BlockHighlight
import io.leaflet.reader.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2-03 production-mapping tests (REQ-0002/0007/0009): [EntryBindings] is
 * the exact mapping the Reader entries bind — same data in, same text +
 * spans out. JVM-runnable (compose-ui text types are pure Kotlin); the
 * composables only apply this mapping (verified by build + device
 * walkthrough in R2-07).
 */
class EntryBindingsTest {
    private fun para(
        text: String,
        source: String = text,
    ) = RenderBlock(0, BlockKind.PARAGRAPH, text, source)

    @Test
    fun styledParagraphFindsBoldItalicStrikeAndCode() {
        val block =
            para(
                "Hello world it strike code end",
                "Hello **world** *it* ~~strike~~ `code` end",
            )
        val kinds = EntryBindings.inlineStyles(block).map { it.op }.toSet()
        assertTrue(kinds.contains(InlineOp.BOLD))
        assertTrue(kinds.contains(InlineOp.ITALIC))
        assertTrue(kinds.contains(InlineOp.STRIKE))
        assertTrue(kinds.contains(InlineOp.CODE))
        val bold = EntryBindings.inlineStyles(block).first { it.op == InlineOp.BOLD }
        assertEquals(6, bold.start)
        assertEquals(11, bold.end)
    }

    // -- R2 DEF-003: highlight coordinates follow the RENDERED string --------

    @Test
    fun headingHighlightLandsOnTheRenderedWord() {
        // `# Alpha` (the source slice) renders as "Alpha". Visible text match
        // "Alpha" = 0..4; the rendered match must be 0..4 too, while the old
        // source-slice measurement gives 2..6 — the reported DEF-003 shift.
        val block = RenderBlock(0, BlockKind.HEADING, "Alpha", "# Alpha")
        val document = doc("Alpha", block)
        val search =
            SearchUiState(
                query = "Alpha",
                matches = listOf(io.leaflet.markdown.MatchLocation(0, 0, 5)),
            )
        val mapped =
            EntryBindings.highlightFor(
                document = document,
                blockIndex = 0,
                displayed = "Alpha",
                search = search,
            )
        assertEquals(0..4, mapped.current)
        val shifted =
            EntryBindings.highlightFor(
                document = document,
                blockIndex = 0,
                displayed = "# Alpha",
                search = search,
            )
        assertEquals(2..6, shifted.current)
    }

    @Test
    fun boldedWordHighlightMapsPastTheMarkers() {
        // Source `a **needle** b` renders as "a needle b"; the visible match
        // at 2..8 must map onto the rendered 2..8 (markers consumed).
        val block = RenderBlock(0, BlockKind.PARAGRAPH, "a needle b", "a **needle** b")
        val document = doc("a needle b", block)
        val search =
            SearchUiState(
                query = "needle",
                matches = listOf(io.leaflet.markdown.MatchLocation(0, 2, 8)),
            )
        val mapped =
            EntryBindings.highlightFor(
                document = document,
                blockIndex = 0,
                displayed = "a needle b",
                search = search,
            )
        assertEquals(2..7, mapped.current)
    }

    @Test
    fun duplicatedWordMapsToItsFirstOccurrence() {
        // One visible match whose word occurs twice in the rendered string:
        // the match keeps its own (first) position, so the highlight never
        // lands on a guessed occurrence; no stale second span lingers.
        val block = RenderBlock(0, BlockKind.PARAGRAPH, "needle again needle", "needle again needle")
        val document = doc("needle again needle", block)
        val search =
            SearchUiState(
                query = "needle",
                // A single hit (the search only saw the first occurrence).
                matches = listOf(io.leaflet.markdown.MatchLocation(0, 0, 6)),
            )
        val mapped =
            EntryBindings.highlightFor(
                document = document,
                blockIndex = 0,
                displayed = "needle again needle",
                search = search,
            )
        assertEquals(0..5, mapped.current)
        assertFalse(mapped.blockFallback)
        assertTrue(mapped.others.isEmpty())
    }

    @Test
    fun literalBlockDisplaysItsTextVerbatim() {
        // R2 DEF-001: TXT / fallback blocks carry markers as characters, so
        // their displayed string is the text itself — never stripped.
        val block = RenderBlock(0, BlockKind.PARAGRAPH, "# Not a heading **literal**", literal = true)
        assertEquals("# Not a heading **literal**", EntryBindings.displayedText(block))
    }

    @Test
    fun displayedTextStripsMarkersForMarkdownBlocks() {
        val block = para("See the note", "See **the** note")
        assertEquals("See the note", EntryBindings.displayedText(block))
    }

    private fun doc(
        visible: String,
        block: RenderBlock,
    ) = io.leaflet.markdown.ParsedDocument(
        documentId = io.leaflet.model.DocumentId("t"),
        title = "t",
        toc = emptyList(),
        blocks = listOf(block),
        positionMap = io.leaflet.markdown.PositionMap(1),
        blockTexts = listOf(visible),
    )

    @Test
    fun unclosedMarkersStayStyleFree() {
        val block = para("a **oops and *meh end", "a **oops and *meh end")
        assertTrue(EntryBindings.inlineStyles(block).isEmpty())
    }

    @Test
    fun taskListKeepsMarkerAndListKind() {
        val block = RenderBlock(0, BlockKind.LIST, "- [ ] Buy oat milk", "- [ ] Buy oat milk")
        assertEquals(BlockKind.LIST, block.kind)
        assertTrue(EntryBindings.displayedText(block).contains("[ ]"))
    }

    @Test
    fun tableGridExposesEveryCellWithoutDelimiter() {
        val source = "| Name | Age |\n| --- | --- |\n| Ada | 36 |\n| Grace | 85 |"
        val block = RenderBlock(0, BlockKind.TABLE, source, source)
        val rows = EntryBindings.tableCells(block)
        assertEquals(3, rows.size)
        assertEquals(listOf("Name", "Age"), rows[0])
        assertEquals(listOf("Ada", "36"), rows[1])
        assertEquals(listOf("Grace", "85"), rows[2])
        assertTrue(rows.flatten().none { it.contains("---") })
    }

    @Test
    fun tableCellSearchHighlightMapsInsideCell() {
        // R2-06 (REQ-0008 AC-3): the block-level highlight (visible-text
        // coordinates over the whole table slice) re-locates inside the
        // stripped cell — the bar jumps to the table AND the needle lights
        // inside the right cell, never a borrowed neighbor offset.
        val blockVisible = "| Ada | 36 |"
        // "Ada" at visible 2..4.
        val blockHit = BlockHighlight(others = listOf(2..4), current = 2..4, blockFallback = false)
        val inHit = EntryBindings.cellSearchHighlight("Ada", blockHit, blockVisible)
        assertEquals(0..2, inHit?.current)
        assertTrue(inHit?.others?.isEmpty() == true)
        // A cell holding none of the needle substrings gets no spans.
        assertNull(EntryBindings.cellSearchHighlight("36", blockHit, blockVisible))
        // Ambiguous current (needle twice in one cell) keeps others but no
        // current — the whole-block fallback carries the position.
        val dup = EntryBindings.cellSearchHighlight("Ada Ada", blockHit, blockVisible)
        assertNull(dup?.current)
        assertTrue(dup?.others?.isNotEmpty() == true)
        // Out-of-range block coordinates degrade to null, never a crash.
        val stale = BlockHighlight(others = listOf(90..95), current = null, blockFallback = false)
        assertNull(EntryBindings.cellSearchHighlight("Ada", stale, blockVisible))
    }

    // -- R2 DEF-003 (table sub-item): cell identity, not substring luck ----

    @Test
    fun identicalCellsOnlyTheSelectedOneIsCurrent() {
        val base = "x\tx"
        val spans = EntryBindings.tableCellSpans(base)
        assertEquals(listOf(listOf(0..0, 2..2)), spans)
        val highlight = BlockHighlight(others = listOf(0..0), current = 2..2, blockFallback = false)

        val firstCell = EntryBindings.cellSearchHighlight("x", highlight, base, spans[0][0])
        assertEquals("first cell keeps a plain match", listOf(0..0), firstCell?.others)
        assertNull("first cell must NOT be the current match", firstCell?.current)

        val secondCell = EntryBindings.cellSearchHighlight("x", highlight, base, spans[0][1])
        assertEquals("second cell is the current match", 0..0, secondCell?.current)
        assertTrue(secondCell?.others?.isEmpty() == true)
    }

    @Test
    fun threeIdenticalCellsOnlyTheSelectedOneIsCurrent() {
        val base = "x\tx\tx"
        val spans = EntryBindings.tableCellSpans(base)
        val highlight = BlockHighlight(others = listOf(0..0, 2..2), current = 4..4, blockFallback = false)
        val cells = (0..2).map { i -> EntryBindings.cellSearchHighlight("x", highlight, base, spans[0][i]) }
        assertEquals(listOf(0..0), cells[0]?.others)
        assertNull(cells[0]?.current)
        assertEquals(listOf(0..0), cells[1]?.others)
        assertNull(cells[1]?.current)
        assertTrue(cells[2]?.others?.isEmpty() == true)
        assertEquals(0..0, cells[2]?.current)
        // exactly one cell claims the current match
        assertEquals(1, cells.count { it?.current != null })
    }

    @Test
    fun identicalCellsAcrossRowsKeepTheirOwnCell() {
        val base = "x\tx\nx\tx"
        val spans = EntryBindings.tableCellSpans(base)
        assertEquals(listOf(listOf(0..0, 2..2), listOf(4..4, 6..6)), spans)
        val highlight = BlockHighlight(others = listOf(0..0, 2..2, 4..4), current = 6..6, blockFallback = false)
        val cells =
            (0..1).flatMap { r -> (0..1).map { c -> EntryBindings.cellSearchHighlight("x", highlight, base, spans[r][c]) } }
        // row 1 cell 2 is the current one; everything else is a plain match
        assertNull(cells[0]?.current)
        assertNull(cells[1]?.current)
        assertNull(cells[2]?.current)
        assertEquals(0..0, cells[3]?.current)
        assertEquals(1, cells.count { it?.current != null })
        assertEquals(listOf(0..0), cells[2]?.others)
    }

    @Test
    fun cellWithInlineMarkersMapsToTheRenderedText() {
        // The rendered cell drops the markers; the match range is expressed in
        // the block's visible text (markers already consumed).
        val base = "b\tb"
        val spans = EntryBindings.tableCellSpans(base)
        val stripped = InlineStyles.strip("**b**")
        assertEquals("b", stripped)
        val highlight = BlockHighlight(others = listOf(0..0), current = 2..2, blockFallback = false)
        val first = EntryBindings.cellSearchHighlight(stripped, highlight, base, spans[0][0])
        assertEquals(listOf(0..0), first?.others)
        assertNull(first?.current)
        val second = EntryBindings.cellSearchHighlight(stripped, highlight, base, spans[0][1])
        assertEquals(0..0, second?.current)
    }

    @Test
    fun spanOutsideTheBlockDegradesWithoutSpanOrCrash() {
        val highlight = BlockHighlight(others = listOf(0..0), current = null, blockFallback = false)
        assertNull(EntryBindings.cellSearchHighlight("x", highlight, "x", 10..12))
    }

    @Test
    fun spansMatchTheGridTheEntryRenders() {
        // The positional path is only taken when the visible grid lines up with
        // the rendered grid; `tableCellSpans` is the source of that grid.
        val block = RenderBlock(0, BlockKind.TABLE, "| A | B |\n| --- | --- |\n| C | D |", "| A | B |\n| --- | --- |\n| C | D |")
        assertEquals(2, EntryBindings.tableCells(block).size)
        assertEquals(2, EntryBindings.tableCellSpans("A\tB\nC\tD").size)
        assertEquals(2, EntryBindings.tableCellSpans("A\tB\nC\tD")[0].size)
        assertEquals(listOf(listOf(0..0, 2..2), listOf(4..4, 6..6)), EntryBindings.tableCellSpans("A\tB\nC\tD"))
    }

    @Test
    fun tableCellTextComposesWithSearchHighlight() {
        // Cell stripped text keeps its inline spans AND composes with the
        // cell-local search spans.
        val stripped = "Ada **Lovelace**"
        val display = InlineStyles.strip(stripped)
        assertEquals("Ada Lovelace", display)
        val styles = InlineStyles.of(stripped)
        assertTrue(styles.isNotEmpty())
        // Cell-local "Lovelace" hit at stripped 4..11.
        val cellHit = BlockHighlight(others = listOf(4..11), current = 4..11, blockFallback = false)
        val annotated = EntryBindings.annotated(display, cellHit)
        val spans = annotated.spanStyles
        assertTrue(spans.any { it.start == 4 && it.end == 12 })
        assertTrue(spans.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun annotatedAppliesSearchSpans() {
        val annotated =
            EntryBindings.annotated(
                "Hello world",
                BlockHighlight(others = listOf(0..4), current = 6..10, blockFallback = false),
            )
        val spans = annotated.spanStyles
        assertTrue(spans.isNotEmpty())
        // Current-match span is inverted bold.
        assertTrue(spans.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun emptyHighlightPassesThrough() {
        val out = EntryBindings.annotated("plain", BlockHighlight.EMPTY)
        assertEquals("plain", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }
}
