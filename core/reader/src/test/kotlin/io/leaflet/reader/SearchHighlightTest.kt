package io.leaflet.reader

import io.leaflet.markdown.MatchLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visible→rendered highlight mapping tests (T11, REQ-0008 FR-3).
 *
 * The search coordinates live in visible text; block entries render a
 * different string for some kinds (Markwon-rendered prose, code body without
 * fences). These tests pin: direct reuse when the strings agree, marker
 * shifting for headings, fence stripping for code, first-occurrence mapping
 * for duplicated words (R2 DEF-003 — never a guessed offset), whole-block
 * fallback when a match cannot be located at all, and safe degradation on
 * out-of-range input.
 */
class SearchHighlightTest {
    @Test
    fun sameStringReusesRangesDirectly() {
        // "量子计算机好" is 6 chars (0..5): 5..7 is out of bounds (stale
        // coordinates are dropped, never styled). Use in-bounds ranges.
        val inBounds = listOf(0..2, 3..5)
        val h = SearchHighlight.highlightForBlock("量子计算机好", "量子计算机好", inBounds, 3..5)!!
        assertEquals(inBounds, h.others + listOfNotNull(h.current))
        assertEquals(3..5, h.current)
        assertFalse(h.blockFallback)
    }

    @Test
    fun headingSourceShiftsVisibleOffsets() {
        // Visible "标题" renders as "# 标题": the match must shift by 2.
        val h = SearchHighlight.highlightForBlock("# 标题", "标题", listOf(0..1), 0..1)!!
        assertEquals(2..3, h.current)
        assertTrue(h.others.isEmpty())
        assertFalse(h.blockFallback)
    }

    @Test
    fun codeBodyWithoutFencesMapsDirectly() {
        // Visible text of a fenced block is the fence interior; the entry
        // renders `codeForCopy()` (same interior), so offsets reuse.
        val body = "val needle = 1"
        val h = SearchHighlight.highlightForBlock(body, body, listOf(4..9), 4..9)!!
        assertEquals(4..9, h.current)
        assertFalse(h.blockFallback)
    }

    // -- R2 DEF-003 (second round): match IDENTITY survives the mapping -----

    @Test
    fun duplicateMatchesKeepTheirOwnOrdinal() {
        // The reported repro: the second match is the current one. Mapping by
        // "first occurrence" put the current span on the FIRST x.
        val h = SearchHighlight.highlightForBlock("x x!", "x x", listOf(0..0, 2..2), 2..2)!!
        assertEquals(2..2, h.current)
        assertEquals(listOf(0..0), h.others)
        assertFalse(h.blockFallback)
    }

    @Test
    fun steppingMovesTheCurrentSpanToTheOtherOccurrence() {
        val first = SearchHighlight.highlightForBlock("x x!", "x x", listOf(0..0, 2..2), 0..0)!!
        assertEquals(0..0, first.current)
        assertEquals(listOf(2..2), first.others)
        val second = SearchHighlight.highlightForBlock("x x!", "x x", listOf(0..0, 2..2), 2..2)!!
        assertEquals(2..2, second.current)
        assertEquals(listOf(0..0), second.others)
    }

    @Test
    fun markerShiftWithDuplicatesKeepsOrder() {
        // Heading: displayed carries `# ` and holds the word twice. The 2nd
        // visible match must land on the 2nd rendered occurrence.
        val displayed = "# dup dup"
        val visible = "dup dup"
        val h = SearchHighlight.highlightForBlock(displayed, visible, listOf(0..2, 4..6), 4..6)!!
        assertEquals(6..8, h.current)
        assertEquals(listOf(2..4), h.others)
        assertEquals("dup", displayed.substring(6, 9))
    }

    @Test
    fun listBulletsKeepBothMatchesOnTheirOwnWords() {
        // A list entry renders `- alpha` lines while the visible text drops the
        // markers: both matches must map onto their own word, never the first.
        val displayed = "- alpha beta\n- alpha gamma"
        val visible = "alpha beta\nalpha gamma"
        // visible: "alpha beta\nalpha gamma" → matches at 0..4 and 11..15
        val h = SearchHighlight.highlightForBlock(displayed, visible, listOf(0..4, 11..15), 11..15)!!
        // displayed: "- alpha beta\n- alpha gamma" → words at 2..6 and 15..19
        assertEquals(15..19, h.current)
        assertEquals(listOf(2..6), h.others)
        assertEquals("alpha", displayed.substring(15, 20))
        assertEquals("alpha", displayed.substring(2, 7))
    }

    @Test
    fun unmappableOrdinalIsDroppedNotGuessed() {
        // displayed has fewer occurrences than visible: the extra match has no
        // place to go and is dropped (never clamped onto a wrong word).
        val h = SearchHighlight.highlightForBlock("only dup", "dup dup dup", listOf(0..2, 4..6, 8..10), 8..10)
        assertNull(h?.current)
        assertTrue(h == null || h.blockFallback)
    }

    @Test
    fun identicalStringsReuseEveryRangeExactly() {
        // "aa" occurs twice in "aaa x aaa": identical strings map one-to-one,
        // so each range keeps its own coordinates (overlaps included).
        val displayed = "aaa x aaa"
        val ranges = listOf(0..1, 1..2, 6..7, 7..8)
        val h = SearchHighlight.highlightForBlock(displayed, displayed, ranges, 0..1)!!
        assertEquals(0..1, h.current)
        assertFalse(h.blockFallback)
        assertEquals(listOf(1..2, 6..7, 7..8), h.others)
    }

    @Test
    fun duplicatedWordMapsToItsFirstRenderedOccurrence() {
        // Visible "x" (one match) inside a rendered "x x": the match maps to
        // the FIRST occurrence — one search match becomes exactly one current
        // span, never a guessed offset (R2 DEF-003).
        val h = SearchHighlight.highlightForBlock("x x", "x", listOf(0..0), 0..0)!!
        assertEquals(0..0, h.current)
        assertFalse(h.blockFallback)
        assertTrue(h.others.isEmpty())
    }

    @Test
    fun unlocatableCurrentFallsBackToWholeBlock() {
        // Mismatched (stale) coordinates whose text is absent from the
        // rendered string: the current match stays unmapped so the caller
        // bolds the block instead of styling the wrong characters.
        val h = SearchHighlight.highlightForBlock("renamed", "old title", listOf(0..2), 0..2)
        // Nothing intersects the rendered string → no highlight at all.
        assertNull(h)
    }

    @Test
    fun emptyRangesYieldNull() {
        assertNull(SearchHighlight.highlightForBlock("text", "text", emptyList(), null))
    }

    @Test
    fun outOfRangeInputsDegradeSafely() {
        val h = SearchHighlight.highlightForBlock("ab", "ab", listOf(9..12), 9..12)
        // Ranges unknown to the block: no current, fallback tells the
        // caller to bold the block rather than crash.
        assertTrue(h == null || h.blockFallback || h.current == null)
    }

    @Test
    fun rangeOfConvertsExclusiveEnd() {
        assertEquals(3..6, SearchHighlight.rangeOf(MatchLocation(2, 3, 7)))
    }
}
