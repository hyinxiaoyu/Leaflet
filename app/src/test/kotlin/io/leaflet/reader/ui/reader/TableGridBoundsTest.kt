package io.leaflet.reader.ui.reader

import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.markdown.TextDecoder
import io.leaflet.model.DocumentId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CR-20261001-01: a table cell is measured with fixed constraints, and Compose
 * cannot represent an arbitrary dimension. A JVM probe against the pinned
 * `ui-unit` 1.7.5 factory shows `Constraints.fixed(262142, …)` accepted and
 * `Constraints.fixed(262143, …)` throwing
 * `IllegalArgumentException: Can't represent a width of 262143 … in Constraints`
 * (the review's retained `ConstraintsProbe.java` reproduces it).
 *
 * These cases pin the pre-measurement gate that keeps the reader from handing
 * such a width to the framework — a rejected cell falls back to the block's own
 * scrollable text, where every character stays reachable.
 */
class TableGridBoundsTest {
    @Test
    fun theGateKeepsAMarginBelowTheMeasuredFrameworkLimit() {
        assertTrue(
            "the gate must stay below the measured 262142 limit",
            MAX_CONSTRAINT_DIMENSION_PX <= 262_142,
        )
        assertTrue(MAX_CONSTRAINT_DIMENSION_PX > 0)
    }

    @Test
    fun ordinaryCellsAreRepresentable() {
        // 40 CJK characters at 16 sp on a 3x display (≈48 px per em).
        assertTrue(isTableCellWidthRepresentable(characters = 40, emPx = 48f, paddingPx = 48f))
        // A wide header row is still far inside the bound.
        assertTrue(isTableCellWidthRepresentable(characters = 400, emPx = 48f, paddingPx = 48f))
    }

    @Test
    fun aHugeSingleLineCellIsRejectedSoTheScrollableFallbackRuns() {
        // The review's sample: one cell holding 10,000 CJK characters.
        assertFalse(isTableCellWidthRepresentable(characters = 10_000, emPx = 48f, paddingPx = 48f))
        // The same cell on a low-density display is still rejected: the gate
        // scales with density, so exposure never depends on the device.
        assertFalse(isTableCellWidthRepresentable(characters = 10_000, emPx = 16f, paddingPx = 32f))
    }

    @Test
    fun theGateBoundaryIsExact() {
        val emPx = 48f
        val paddingPx = 48f
        val maxCharacters = ((MAX_CONSTRAINT_DIMENSION_PX - paddingPx) / (2f * emPx)).toInt()
        assertTrue(isTableCellWidthRepresentable(maxCharacters, emPx, paddingPx))
        assertFalse(isTableCellWidthRepresentable(maxCharacters + 1, emPx, paddingPx))
    }

    /**
     * The review's sample end to end on the JVM: the PRODUCTION parser admits the
     * table as a non-degraded `TABLE` block whose widest cell holds 10,000 CJK
     * characters, and the gate rejects that cell — so the reader renders the
     * block's scrollable text instead of handing an unrepresentable width to
     * layout.
     */
    @Test
    fun theReviewsWideTableIsAdmittedByTheParserAndRejectedByTheGate() =
        runTest {
            val cell = "測".repeat(10_000)
            val body = "| Left | Right |\n| --- | --- |\n| $cell | b |\n"
            val parser = LeafletDocumentParser.createForJvmTests()
            val document =
                parser.parse(
                    DocumentId("wide.md"),
                    TextDecoder.decode(body.toByteArray()),
                    "wide.md",
                    true,
                )
            assertFalse("the parser admits this table without degrading", document.degraded)
            val table = document.blocks.single { it.kind == BlockKind.TABLE }
            assertTrue(
                "the block keeps the whole table (was ${table.text.length} chars)",
                table.text.length > 10_000,
            )
            val widestCell = EntryBindings.tableCells(table).maxOf { row -> row.maxOf { it.length } }
            assertEquals("the widest cell holds the 10,000-character content", 10_000, widestCell)
            assertFalse(
                "a 10,000-character single-line cell must be rejected by the gate",
                isTableCellWidthRepresentable(widestCell, emPx = 48f, paddingPx = 48f),
            )
        }
}
