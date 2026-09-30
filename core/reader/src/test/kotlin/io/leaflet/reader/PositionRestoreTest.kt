package io.leaflet.reader

import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.BlockPositionMap
import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.PositionMap
import io.leaflet.markdown.RenderBlock
import io.leaflet.markdown.TocEntry
import io.leaflet.model.DocumentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restore-priority matrix (T07, REQ-0003 FR-3/AC-1/AC-3):
 * fresh fingerprint → exact block/offset; stale → anchor → progress → top.
 * No pixel values anywhere — block indexes only. JVM-only, no storage.
 */
class PositionRestoreTest {
    private fun doc(vararg headings: String): ParsedDocument {
        val blocks = ArrayList<RenderBlock>()
        val visible = ArrayList<String>()
        var i = 0
        blocks.add(RenderBlock(i, BlockKind.PARAGRAPH, "intro"))
        visible.add("intro")
        i++
        for (h in headings) {
            blocks.add(RenderBlock(i, BlockKind.HEADING, "# $h"))
            visible.add(h)
            i++
            blocks.add(RenderBlock(i, BlockKind.PARAGRAPH, "body of $h"))
            visible.add("body of $h")
            i++
        }
        val toc =
            headings.mapIndexed { index, h ->
                TocEntry(1, h, if (index == 0) h.lowercase() else "${h.lowercase()}-$index")
            }
        val ops = BlockPositionMap(visible).withAnchors(toc, blocks)
        return ParsedDocument(
            documentId = DocumentId("d"),
            title = "D",
            toc = toc,
            blocks = blocks,
            positionMap = PositionMap(blocks.size),
            blockTexts = visible.toList(),
            positionOps = ops,
        )
    }

    private fun pos(
        block: Int,
        offset: Int = 0,
        progress: Float = 0f,
        anchor: String? = null,
        version: Int = 1,
    ) = io.leaflet.model.ReadingPosition(
        documentId = DocumentId("d"),
        version = version,
        blockIndex = block,
        blockOffset = offset,
        progress = progress,
        anchor = anchor,
    )

    @Test
    fun freshFingerprintRestoresExactBlockAndOffset() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 4, offset = 2),
                savedFingerprint = "10:5",
                liveFingerprint = "10:5",
                blockCount = 7,
                locateAnchor = { null },
            )
        assertEquals(RestoreKind.EXACT, r.kind)
        assertEquals(4, r.blockIndex)
        assertEquals(2, r.blockOffset)
        assertFalse(r.showStaleNotice)
    }

    @Test
    fun staleFingerprintFallsBackToAnchor() {
        val parsed = doc("Alpha", "Beta")
        // Helper slugs are plain lowercase ("alpha", "beta-1"); "beta" alone
        // is not a real anchor (verified against the JVM on 2026-09-18).
        val r =
            PositionRestore.restore(
                saved = pos(block = 99, progress = 0.9f, anchor = "beta-1"),
                savedFingerprint = "10:5",
                liveFingerprint = "11:6",
                blockCount = parsed.blocks.size,
                locateAnchor = { locateAnchorInDoc(parsed, it) },
            )
        assertEquals(RestoreKind.ANCHOR, r.kind)
        assertEquals(parsed.blocks.indexOfFirst { it.text.contains("Beta") }, r.blockIndex)
        assertTrue(r.showStaleNotice)
    }

    @Test
    fun anchorMissFallsBackToProgress() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 99, progress = 0.5f, anchor = "gone-heading"),
                savedFingerprint = "10:5",
                liveFingerprint = "11:6",
                blockCount = 10,
                locateAnchor = { null },
            )
        assertEquals(RestoreKind.PROGRESS, r.kind)
        assertEquals(5, r.blockIndex)
        assertTrue(r.showStaleNotice)
    }

    @Test
    fun noAnchorNoProgressFallsBackToTop() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 6),
                savedFingerprint = "10:5",
                liveFingerprint = "11:6",
                blockCount = 10,
                locateAnchor = { null },
            )
        assertEquals(RestoreKind.TOP, r.kind)
        assertEquals(0, r.blockIndex)
        assertTrue(r.showStaleNotice)
    }

    @Test
    fun savedAtTopLandingOnTopStaysSilent() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 0),
                savedFingerprint = "10:5",
                liveFingerprint = "11:6",
                blockCount = 10,
                locateAnchor = { null },
            )
        assertEquals(RestoreKind.TOP, r.kind)
        assertFalse(r.showStaleNotice)
    }

    @Test
    fun freshButOutOfRangeBlockFallsBack() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 50, progress = 0.25f, anchor = null),
                savedFingerprint = "10:5",
                liveFingerprint = "10:5",
                blockCount = 8,
                locateAnchor = { null },
            )
        // Same fingerprint but the block is gone (truncation): never land past the end.
        assertTrue(r.blockIndex in 0 until 8)
        assertTrue(r.kind == RestoreKind.PROGRESS || r.kind == RestoreKind.TOP)
        assertTrue(r.showStaleNotice)
    }

    @Test
    fun unknownSavedFingerprintIsStale() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 3, progress = 0.5f),
                savedFingerprint = null,
                liveFingerprint = "10:5",
                blockCount = 10,
                locateAnchor = { null },
            )
        assertTrue(r.kind == RestoreKind.PROGRESS || r.kind == RestoreKind.TOP)
        assertTrue(r.showStaleNotice)
    }

    @Test
    fun unknownVersionFallsBackToTopSilently() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 5, progress = 0.8f, anchor = "x", version = 99),
                savedFingerprint = "10:5",
                liveFingerprint = "10:5",
                blockCount = 10,
                locateAnchor = { it?.let { 2 } },
            )
        assertEquals(RestoreKind.TOP, r.kind)
        assertEquals(0, r.blockIndex)
        assertFalse(r.showStaleNotice)
    }

    @Test
    fun nullSavedFallsBackToTopSilently() {
        val r =
            PositionRestore.restore(
                saved = null,
                savedFingerprint = null,
                liveFingerprint = "10:5",
                blockCount = 10,
                locateAnchor = { null },
            )
        assertEquals(RestoreKind.TOP, r.kind)
        assertFalse(r.showStaleNotice)
    }

    @Test
    fun emptyDocumentAlwaysTop() {
        val r =
            PositionRestore.restore(
                saved = pos(block = 3, progress = 0.9f, anchor = "x"),
                savedFingerprint = "10:5",
                liveFingerprint = "10:5",
                blockCount = 0,
                locateAnchor = { 0 },
            )
        assertEquals(RestoreKind.TOP, r.kind)
        assertFalse(r.showStaleNotice)
    }

    @Test
    fun progressForBlockHandlesConstantFiles() {
        assertEquals(0f, PositionRestore.progressForBlock(0, 0), 0f)
        assertEquals(0f, PositionRestore.progressForBlock(0, 1), 0f)
        assertEquals(0f, PositionRestore.progressForBlock(0, 5), 0f)
        assertEquals(1f, PositionRestore.progressForBlock(4, 5), 0f)
        assertEquals(0.5f, PositionRestore.progressForBlock(2, 5), 0f)
    }

    @Test
    fun anchorForBlockFindsNearestPrecedingHeading() {
        val parsed = doc("Alpha", "Beta")
        assertEquals("Alpha", anchorForBlock(parsed, 2))
        assertEquals("Beta", anchorForBlock(parsed, 4))
        assertNull(anchorForBlock(parsed, 0))
    }

    @Test
    fun locateAnchorMatchesTocTextFallback() {
        val parsed = doc("Reading Notes")
        // Helper slugs are plain lowercase with spaces kept ("reading notes"),
        // not hyphenated (verified against the JVM on 2026-09-18); the raw
        // heading-text path covers pre-slug records.
        assertEquals(1, locateAnchorInDoc(parsed, "reading notes"))
        // … then raw heading-text fallback (pre-slug records).
        assertEquals(1, locateAnchorInDoc(parsed, "Reading Notes"))
        assertNull(locateAnchorInDoc(parsed, "no-such-heading"))
    }

    // -- R2-02: single anchor map — duplicates bind by TOC order -----------

    @Test
    fun duplicateAnchorsResolveToDistinctBlocksByOrder() {
        val parsed = doc("总结", "总结", "总结")
        val anchors = parsed.toc.map { it.anchor }
        assertEquals(3, anchors.toSet().size)
        val blocks = anchors.map { locateAnchorInDoc(parsed, it) }
        assertEquals(listOf(1, 3, 5), blocks)
        // Same map through the production TocNavigation entry point.
        assertEquals(blocks, anchors.map { TocNavigation.resolve(parsed, it) })
    }

    @Test
    fun thousandHeadingMapStaysLinear() {
        // 2000 headings: every anchor resolves to its own block; total time
        // must stay far below the old O(H²) re-scan (correctness + budget;
        // trend recorded by hand, no CI gate).
        val titles = (1..2000).map { "Section $it" }
        val parsed = doc(*titles.toTypedArray())
        val start = System.nanoTime()
        var sum = 0
        for (entry in parsed.toc) {
            sum += TocNavigation.resolve(parsed, entry.anchor) ?: -1
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(parsed.blocks.filter { it.kind == BlockKind.HEADING }.size, 2000)
        assertTrue("sum=$sum ms=$ms", sum > 0 && ms < 5_000)
    }
}
