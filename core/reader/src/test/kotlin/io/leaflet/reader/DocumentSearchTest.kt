package io.leaflet.reader

import io.leaflet.markdown.BlockKind
import io.leaflet.markdown.BlockPositionMap
import io.leaflet.markdown.MatchLocation
import io.leaflet.markdown.ParsedDocument
import io.leaflet.markdown.PositionMap
import io.leaflet.model.DocumentId
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-document literal search tests (T11, REQ-0008 FR-1/FR-2/FR-4/AC-2/AC-7).
 *
 * Covers: Chinese/Japanese/emoji/repeat/no-match counts, case-sensitivity
 * (frozen default), no-cross-block rule, code+table visible text, empty/blank
 * queries, syntax markers never matching, stale-generation guard, and
 * wrap-around stepping.
 */
class DocumentSearchTest {
    private fun docOf(vararg visible: String): ParsedDocument {
        val blocks = visible.mapIndexed { i, text -> io.leaflet.markdown.RenderBlock(i, BlockKind.PARAGRAPH, text) }
        val ops = BlockPositionMap(visible.toList())
        return ParsedDocument(
            documentId = DocumentId("d"),
            title = "D",
            toc = emptyList(),
            blocks = blocks,
            positionMap = PositionMap(blocks.size),
            blockTexts = visible.toList(),
            positionOps = ops,
        )
    }

    @Test
    fun chineseFiveMatchesAcrossBlocks() {
        val doc = docOf("量子计算机第一段量子", "无关段落", "量子计算机第二段量子", "量子尾段")
        val hits = DocumentSearch.search(doc, "量子")
        assertEquals(5, hits.size)
        assertTrue(hits.all { it.blockIndex in 0..3 })
        // Document order: block then offset.
        assertEquals(hits.sortedWith(compareBy({ it.blockIndex }, { it.startInBlock })), hits)
    }

    @Test
    fun japaneseAndEmojiCountCorrectly() {
        val doc = docOf("古池や蛙飛び込む水の音", "古池や二度目", "📚书📚架📚")
        assertEquals(2, DocumentSearch.search(doc, "古池や").size)
        assertEquals(3, DocumentSearch.search(doc, "📚").size)
        assertEquals(0, DocumentSearch.search(doc, "不存在").size)
    }

    @Test
    fun repeatedMatchesInOneBlockAllCounted() {
        val doc = docOf("aaa")
        val hits = DocumentSearch.search(doc, "a")
        assertEquals(3, hits.size)
        // Overlapping literal scan advances one char at a time (matches
        // BlockPositionMap): "aa" in "aaa" hits twice.
        assertEquals(2, DocumentSearch.search(doc, "aa").size)
    }

    @Test
    fun emptyAndBlankQueriesNeverSearch() {
        val doc = docOf("正文")
        assertTrue(DocumentSearch.search(doc, "").isEmpty())
        assertTrue(DocumentSearch.search(doc, "   ").isEmpty())
    }

    @Test
    fun caseSensitiveByDefault() {
        val doc = docOf("ErrorCode errorcode ERRORCODE")
        assertEquals(1, DocumentSearch.search(doc, "ErrorCode").size)
        assertEquals(1, DocumentSearch.search(doc, "errorcode").size)
        assertEquals(1, DocumentSearch.search(doc, "ERRORCODE").size)
        assertEquals(0, DocumentSearch.search(doc, "errorCode").size)
    }

    @Test
    fun noCrossBlockMatches() {
        // Query spanning the block boundary (tail of 0 + head of 1) matches
        // nowhere, even though the concatenated text contains it.
        val doc = docOf("前半匹配", "后半匹配")
        assertEquals(0, DocumentSearch.search(doc, "匹配后半").size)
        assertEquals(listOf(MatchLocation(0, 2, 4)), DocumentSearch.search(doc, "匹配").take(1))
    }

    @Test
    fun emptyDocumentSearchesWithoutCrash() {
        val doc = docOf()
        assertTrue(DocumentSearch.search(doc, "x").isEmpty())
    }

    @Test
    fun staleGuardDropsExpiredResults() {
        assertTrue(DocumentSearch.isStale(3, 4))
        assertFalse(DocumentSearch.isStale(4, 4))
    }

    @Test
    fun stepWrapsAroundBothDirections() {
        assertEquals(0, DocumentSearch.stepIndex(4, 5, true))
        assertEquals(4, DocumentSearch.stepIndex(0, 5, false))
        assertEquals(2, DocumentSearch.stepIndex(1, 5, true))
        assertEquals(1, DocumentSearch.stepIndex(2, 5, false))
    }

    @Test
    fun currentFollowsIndexSafely() {
        val hits = listOf(MatchLocation(0, 0, 1), MatchLocation(1, 0, 1))
        val state = SearchUiState(query = "a", matches = hits, currentIndex = 1)
        assertEquals(MatchLocation(1, 0, 1), state.current)
        assertEquals(2, state.total)
        assertNull(SearchUiState().current)
    }

    // -- R2-06 regressions: cap, cancellation, shared scan -----------------

    @Test
    fun denseRepeatStopsAtCapWithFlag() =
        runTest {
            // 20k-char single block, 1-char query: uncapped this is 20k rows;
            // the production path returns exactly the first window + capped.
            val doc = docOf("a".repeat(20_000))
            val result = DocumentSearch.searchCapped(doc, "a")
            assertEquals(DocumentSearch.MAX_SEARCH_MATCHES, result.matches.size)
            assertTrue(result.capped)
            assertEquals(DocumentSearch.MAX_SEARCH_MATCHES, DocumentSearch.search(doc, "a").size)
        }

    @Test
    fun sparseQueryBelowCapIsComplete() =
        runTest {
            val doc = docOf("量子计算机第一段量子", "无关段落", "量子计算机第二段量子")
            val result = DocumentSearch.searchCapped(doc, "量子")
            assertEquals(4, result.matches.size)
            assertFalse(result.capped)
        }

    @Test
    fun cancelledScanThrowsNotPartialResults() =
        runTest {
            // Deterministic: cancel lands before the child first runs, so the
            // yield/ensureActive at block 0 throws before any result exists.
            // A non-cooperative loop (no suspension point) would run to
            // completion despite the cancel — `result` staying null plus
            // `isCancelled` discriminates the two.
            val doc = docOf(*Array(200_000) { "block $it filler text here" })
            var result: SearchResult? = null
            val job =
                backgroundScope.launch {
                    result = DocumentSearch.searchCapped(doc, "zzz-never-present")
                }
            job.cancel()
            runCatching { job.join() }
            assertNull(result)
            assertTrue(job.isCancelled)
        }
}
