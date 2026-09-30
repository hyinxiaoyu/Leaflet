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
 * T08 paging/TOC/arbitration/mode matrix (REQ-0005/REQ-0006, JVM-only):
 * turn-distance formula, overlap band, head/tail clamp, occlusion + rotation
 * accounting, duplicate-heading jumps, arbitration priority, mode keep-position.
 */
class PageTurnTest {
    @Test
    fun turnDistanceIsViewportMinusFivePercentByDefault() {
        // 1000px viewport, default 5% overlap → 950px per turn.
        assertEquals(950, PageTurn.turnDistancePx(1000, PageTurn.OVERLAP_DEFAULT))
        assertEquals(50, PageTurn.overlapPx(1000, PageTurn.OVERLAP_DEFAULT))
    }

    @Test
    fun overlapRatioStaysWithinThreeToEightPercent() {
        for (fraction in listOf(0.03f, 0.05f, 0.08f)) {
            val viewport = 1000
            val ratio = PageTurn.overlapPx(viewport, fraction).toFloat() / viewport
            assertTrue("ratio $ratio out of band", ratio in 0.03f..0.08f)
        }
        // Out-of-band inputs coerce, never escape the band.
        assertEquals(0.03f, PageTurn.coerceOverlap(0.0f), 0.0f)
        assertEquals(0.08f, PageTurn.coerceOverlap(1.0f), 0.0f)
        assertEquals(0.05f, PageTurn.coerceOverlap(0.05f), 0.0f)
    }

    @Test
    fun fiveConsecutiveTurnsAdvanceByExactSteps() {
        // AC-1 shape: 5 turns of 950px each, error budget <= 2px per turn.
        val distance = PageTurn.turnDistancePx(1000, 0.05f)
        var offset = 0
        repeat(5) { offset = PageTurn.nextOffset(offset, distance, Int.MAX_VALUE) }
        assertEquals(5 * distance, offset)
        assertTrue(kotlin.math.abs(distance - 950) <= 2)
    }

    @Test
    fun headAndTailClamp() {
        val distance = PageTurn.turnDistancePx(1000, 0.05f)
        // Head: backward turn at top is a no-op.
        assertEquals(0, PageTurn.prevOffset(0, distance))
        assertTrue(PageTurn.isAtTop(0))
        // Tail: forward turn past maxOffset clamps, never overshoots.
        val max = 2000
        assertEquals(max, PageTurn.nextOffset(1900, distance, max))
        assertTrue(PageTurn.isAtBottom(max, max))
        assertFalse(PageTurn.isAtBottom(0, max))
        // Rapid 10x turns from top stop exactly at the tail.
        var offset = 0
        repeat(10) { offset = PageTurn.nextOffset(offset, distance, max) }
        assertEquals(max, offset)
    }

    @Test
    fun occlusionAndRotationRecalculate() {
        // Menu chrome eats 200px of a 1000px host → 800px visible.
        val visible = PageTurn.visibleHeightPx(totalHeightPx = 1000, bottomObscuredPx = 200)
        assertEquals(800, visible)
        assertEquals(760, PageTurn.turnDistancePx(visible, 0.05f))
        // Rotation to a shorter viewport recomputes from the NEW height.
        val rotated = PageTurn.visibleHeightPx(totalHeightPx = 600)
        assertEquals(600, rotated)
        // Pathological occlusion never inverts the viewport.
        assertTrue(PageTurn.visibleHeightPx(1000, topObscuredPx = 900, bottomObscuredPx = 900) >= 1)
    }

    @Test
    fun singleScreenDocumentNeverTurns() {
        // Edge case: whole doc fits one screen → maxOffset 0, turns are no-ops.
        assertEquals(0, PageTurn.nextOffset(0, 950, 0))
        assertTrue(PageTurn.isAtBottom(0, 0))
        assertTrue(PageTurn.isAtTop(0))
    }
}

class TapZonesTest {
    @Test
    fun thirdsMapToPrevMenuNext() {
        assertEquals(TapZone.PREV, TapZones.zoneFor(0f, 900))
        assertEquals(TapZone.PREV, TapZones.zoneFor(299f, 900))
        assertEquals(TapZone.MENU, TapZones.zoneFor(300f, 900))
        assertEquals(TapZone.MENU, TapZones.zoneFor(450f, 900))
        assertEquals(TapZone.NEXT, TapZones.zoneFor(600f, 900))
        assertEquals(TapZone.NEXT, TapZones.zoneFor(899f, 900))
    }

    @Test
    fun degenerateWidthNeverTurns() {
        assertEquals(TapZone.MENU, TapZones.zoneFor(10f, 0))
        assertEquals(TapZone.MENU, TapZones.zoneFor(10f, -5))
    }

    /**
     * UX-06 first-run onboarding: shown only for a definite unseen flag that
     * has not been dismissed in this composition. An unread flag (`null`) must
     * NOT show it, so the overlay cannot flash before DataStore answers.
     */
    @Test
    fun firstRunHintShowsOnlyWhenUnseenAndNotYetDismissed() {
        assertTrue("a fresh install sees the hint", TapZones.shouldShowHint(seen = false, dismissed = false))
        assertFalse("a read-but-unseen flag does not flash the hint", TapZones.shouldShowHint(seen = null, dismissed = false))
        assertFalse("an already-seen install never sees it again", TapZones.shouldShowHint(seen = true, dismissed = false))
        assertFalse(
            "a dismissal in this session hides it before the write lands",
            TapZones.shouldShowHint(seen = false, dismissed = true),
        )
        assertFalse("dismissed stays hidden once stored", TapZones.shouldShowHint(seen = true, dismissed = true))
    }
}

class GestureArbitrationTest {
    @Test
    fun contentGesturesAlwaysWinOverZones() {
        for (zone in TapZone.entries) {
            assertEquals(TurnIntent.NONE, GestureArbitration.decide(ContentGesture.LINK_TAP, zone))
            assertEquals(TurnIntent.NONE, GestureArbitration.decide(ContentGesture.BUTTON_TAP, zone))
            assertEquals(TurnIntent.NONE, GestureArbitration.decide(ContentGesture.CODE_COPY_TAP, zone))
            assertEquals(
                TurnIntent.NONE,
                GestureArbitration.decide(ContentGesture.HORIZONTAL_SCROLL, zone),
            )
            assertEquals(TurnIntent.NONE, GestureArbitration.decide(ContentGesture.LONG_PRESS, zone))
        }
    }

    @Test
    fun plainTapMapsZonesToTurnOrMenu() {
        assertEquals(TurnIntent.PREV_PAGE, GestureArbitration.decide(ContentGesture.PLAIN_TAP, TapZone.PREV))
        assertEquals(TurnIntent.NEXT_PAGE, GestureArbitration.decide(ContentGesture.PLAIN_TAP, TapZone.NEXT))
        assertEquals(TurnIntent.TOGGLE_MENU, GestureArbitration.decide(ContentGesture.PLAIN_TAP, TapZone.MENU))
    }
}

class TocNavigationTest {
    private fun docWithDuplicateHeadings(): ParsedDocument {
        // Anchors follow the T06 suffix rule (`总结`, `总结-1`, `总结-2`):
        // duplicate heading texts get `base`, `base-1`, ... in first-seen order.
        val headings = listOf("引言", "总结", "方法", "总结", "附录", "总结")
        val seen = HashMap<String, Int>()
        val anchors =
            headings.map { h ->
                val n = seen.getOrDefault(h, 0)
                seen[h] = n + 1
                if (n == 0) h else "$h-$n"
            }
        val blocks = ArrayList<RenderBlock>()
        val visible = ArrayList<String>()
        var i = 0
        headings.forEach { h ->
            blocks.add(RenderBlock(i, BlockKind.HEADING, "# $h"))
            visible.add(h)
            i++
            blocks.add(RenderBlock(i, BlockKind.PARAGRAPH, "body $i"))
            visible.add("body $i")
            i++
        }
        val toc = headings.mapIndexed { index, h -> TocEntry(1, h, anchors[index]) }
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

    @Test
    fun duplicateAnchorsAreDistinctAndStable() {
        val doc = docWithDuplicateHeadings()
        val summaries = doc.toc.filter { it.text == "总结" }.map { it.anchor }
        assertEquals(3, summaries.size)
        assertEquals(3, summaries.toSet().size)
        // Same content re-parsed yields the same anchor sequence.
        val again = docWithDuplicateHeadings().toc.filter { it.text == "总结" }.map { it.anchor }
        assertEquals(summaries, again)
    }

    @Test
    fun eachDuplicateJumpsToItsOwnBlock() {
        val doc = docWithDuplicateHeadings()
        val landed = doc.toc.filter { it.text == "总结" }.map { TocNavigation.resolve(doc, it.anchor) }
        assertEquals(3, landed.toSet().size)
        assertTrue(landed.all { it != null && it in doc.blocks.indices })
        // Each landing is a heading block holding the entry text.
        landed.forEach { index ->
            val block = doc.blocks[index!!]
            assertEquals(BlockKind.HEADING, block.kind)
            assertTrue(block.text.contains("总结"))
        }
        // Ordered: later TOC entries land on later blocks.
        assertTrue(landed[0]!! < landed[1]!! && landed[1]!! < landed[2]!!)
    }

    @Test
    fun unknownAnchorResolvesNull() {
        val doc = docWithDuplicateHeadings()
        assertNull(TocNavigation.resolve(doc, "not-a-heading"))
        assertNull(TocNavigation.resolve(doc, ""))
    }

    @Test
    fun emptyDocumentHasNoChapter() {
        val empty =
            ParsedDocument(
                documentId = DocumentId("e"),
                title = "E",
                toc = emptyList(),
                blocks = emptyList(),
                positionMap = PositionMap(0),
            )
        assertNull(TocNavigation.resolve(empty, "anything"))
        assertNull(TocNavigation.currentChapter(empty, 0))
    }

    @Test
    fun currentChapterTracksNearestHeadingAbove() {
        val doc = docWithDuplicateHeadings()
        // block 0 = 引言 heading, block 2 = 总结#1 heading, block 3 = its body.
        assertEquals("引言", TocNavigation.currentChapter(doc, 0))
        assertEquals("引言", TocNavigation.currentChapter(doc, 1))
        assertEquals("总结", TocNavigation.currentChapter(doc, 3))
    }
}

class ReadingModeTest {
    @Test
    fun storedValuesRoundTrip() {
        assertEquals(ReadingMode.CONTINUOUS_SCROLL, ReadingMode.of("CONTINUOUS_SCROLL"))
        assertEquals(ReadingMode.PAGE_TURN, ReadingMode.of("PAGE_TURN"))
        assertEquals(ReadingMode.CONTINUOUS_SCROLL, ReadingMode.of("garbage"))
    }

    @Test
    fun einkDefaultsToPageTurn() {
        assertEquals(ReadingMode.PAGE_TURN, ReadingMode.defaultForEink(true))
        assertEquals(ReadingMode.CONTINUOUS_SCROLL, ReadingMode.defaultForEink(false))
    }

    @Test
    fun modeSwitchKeepsPosition() {
        // AC-5 shape: switching modes keeps the same block address.
        assertEquals(7, keepBlock(7, 20))
        assertEquals(19, keepBlock(99, 20))
        assertEquals(0, keepBlock(0, 0))
    }
}
