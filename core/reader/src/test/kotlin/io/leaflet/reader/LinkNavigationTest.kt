package io.leaflet.reader

import io.leaflet.markdown.BlockKind
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
 * T10 reader-side JVM tests (REQ-0009 FR-4–FR-6): fragment jumps
 * (duplicate-first-wins, encoded/case variants, missing-anchor null) and
 * viewport-sampled decode math (bounds, budget, cancellation-safe purity).
 *
 * Storage/bitmap work stays behind injectable seams (`DocumentSource`,
 * `:app` loader) — these tests pin the decision tables only.
 */
class LinkNavigationTest {
    private fun doc(): ParsedDocument {
        // Mirrors `duplicate-headings.md`: three "Overview" + two "概述".
        val toc =
            listOf(
                TocEntry(2, "Overview", "overview"),
                TocEntry(2, "Details", "details"),
                TocEntry(2, "Overview", "overview-1"),
                TocEntry(2, "概述", "概述"),
                TocEntry(2, "概述", "概述-1"),
                TocEntry(2, "Overview", "overview-2"),
            )
        val blocks = ArrayList<RenderBlock>()
        val texts = ArrayList<String>()
        var i = 0

        fun heading(text: String) {
            blocks.add(RenderBlock(i, BlockKind.HEADING, "## $text"))
            texts.add(text)
            i++
        }

        fun body(text: String) {
            blocks.add(RenderBlock(i, BlockKind.PARAGRAPH, text))
            texts.add(text)
            i++
        }
        heading("Overview")
        body("first")
        heading("Details")
        body("details")
        heading("Overview")
        body("second")
        heading("概述")
        body("zh-1")
        heading("概述")
        body("zh-2")
        heading("Overview")
        body("third")
        return ParsedDocument(
            documentId = DocumentId("t"),
            title = "t",
            toc = toc,
            blocks = blocks,
            positionMap = PositionMap(blocks.size),
            blockTexts = texts.toList(),
            positionOps = FakeOps(mapOf("overview" to 0, "details" to 2, "overview-1" to 4, "概述" to 6, "概述-1" to 8, "overview-2" to 10)),
        )
    }

    @Test
    fun bareFragmentLandsOnFirstDuplicate() {
        val d = doc()
        assertEquals(0, FragmentMatch.resolve(d, "overview"))
        assertEquals(4, FragmentMatch.resolve(d, "overview-1"))
        assertEquals(10, FragmentMatch.resolve(d, "overview-2"))
    }

    @Test
    fun encodedCaseAndDashVariantsNormalize() {
        val d = doc()
        assertEquals(0, FragmentMatch.resolve(d, "Overview"))
        assertEquals(0, FragmentMatch.resolve(d, "%6Fverview"))
        assertEquals(6, FragmentMatch.resolve(d, "概述"))
        assertEquals(6, FragmentMatch.resolve(d, "%E6%A6%82%E8%BF%B0"))
    }

    @Test
    fun missingAnchorReturnsNullNeverZero() {
        val d = doc()
        assertNull(FragmentMatch.resolve(d, "no-such-heading"))
        assertNull(FragmentMatch.resolve(d, ""))
        assertNull(FragmentMatch.resolve(d, "   "))
        // Must not fall back to block 0 pretending success (REQ-0009 FR-5).
        assertFalse(FragmentMatch.resolve(d, "no-such-heading") == 0)
    }
}

private class FakeOps(private val anchors: Map<String, Int>) : io.leaflet.markdown.PositionMapOps {
    override fun blockCount(): Int = anchors.size

    override fun visibleTextOf(blockIndex: Int): String = ""

    override fun locateAnchor(anchor: String): io.leaflet.markdown.BlockLocation? =
        anchors[anchor]?.let { io.leaflet.markdown.BlockLocation(it, 0) }

    override fun locateQuery(query: String): List<io.leaflet.markdown.MatchLocation> = emptyList()
}

class ImageSampleTest {
    @Test
    fun powerOfTwoWidthSampling() {
        assertEquals(1, ImageSample.inSampleSizeForWidth(800, 600, 800))
        assertEquals(2, ImageSample.inSampleSizeForWidth(1600, 1200, 800))
        assertEquals(4, ImageSample.inSampleSizeForWidth(3200, 2400, 800))
        assertEquals(1, ImageSample.inSampleSizeForWidth(0, 600, 800))
        assertEquals(1, ImageSample.inSampleSizeForWidth(800, 600, 0))
    }

    @Test
    fun budgetForcesDownsampleForHugeImages() {
        // 8000×8000 ARGB = 256MB raw: viewport sampling alone is not
        // enough; the ceiling forces a larger sample (never OOM).
        val sample = ImageSample.sampleFor(8000, 8000, 1080)
        assertTrue(ImageSample.fitsBudget(8000, 8000, sample))
        assertTrue(sample >= 8)
        // Ordinary photo fits at viewport sampling.
        val photo = ImageSample.sampleFor(1600, 1200, 800)
        assertEquals(2, photo)
        assertTrue(ImageSample.fitsBudget(1600, 1200, photo))
    }

    @Test
    fun decodedBytesUseCeilingDivision() {
        assertEquals(4L * 400 * 300, ImageSample.decodedBytes(800, 600, 2))
        assertEquals(4L * 401 * 301, ImageSample.decodedBytes(801, 601, 2))
        assertTrue(ImageSample.decodedBytes(100, 100, 1) <= ImageSample.MAX_DECODE_BYTES)
    }

    @Test
    fun ceilingIsDocumentedPlaceholder() {
        // Guards the REQ-0011 baseline conversation: if someone changes the
        // ceiling, this test forces the KDoc + REQ-0009 OQ update alongside.
        assertEquals(12L * 1024L * 1024L, ImageSample.MAX_DECODE_BYTES)
        assertEquals(4, ImageSample.BYTES_PER_PIXEL)
    }
}
