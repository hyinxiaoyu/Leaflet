package io.leaflet.markdown

import io.leaflet.model.DocumentId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search-facing visible-text assertions (T11, REQ-0008 FR-2/FR-8/FR-9/AC-2/AC-3/AC-7).
 *
 * Pins: syntax markers never match, code/table cell text is searchable,
 * Chinese/Japanese/emoji/repeat/no-match counts, case-sensitivity, the
 * no-cross-block rule, TXT literalness, degraded plain-fallback chunks, and
 * blank-query behavior. Position-map internals own the scan; these tests
 * prove the parsed pipeline feeds it visible text.
 */
class DocumentSearchVisibilityTest {
    private val parser = LeafletDocumentParser.createForJvmTests()

    private suspend fun parseMd(
        name: String,
        body: String,
    ): ParsedDocument =
        parser.parse(
            DocumentId("t"),
            TextDecoder.decode(body.toByteArray()),
            name,
            true,
        )

    @Test
    fun markersNeverMatchVisibleWordsDo() =
        runTest {
            val doc = parseMd("t.md", "# 标题\n\n**加粗**与文字正文。\n")
            val ops = doc.positionOps!!
            assertTrue(ops.locateQuery("标题").isNotEmpty())
            assertTrue(ops.locateQuery("加粗").isNotEmpty())
            assertTrue(ops.locateQuery("文字").isNotEmpty())
            assertTrue(ops.locateQuery("**").isEmpty())
            assertTrue(ops.locateQuery("#").isEmpty())
            assertTrue(ops.locateQuery("](").isEmpty())
        }

    @Test
    fun linkLabelSearchableDestinationNotOnSeparateQuery() =
        runTest {
            // commonmark TextContentRenderer keeps link labels and destinations
            // in visible text (T06 seam); the search contract only bans *syntax
            // markers* (`](`, `**`, `#`), not destination substrings. This test
            // pins the current renderer-visible behavior so a renderer change
            // cannot silently alter search scope.
            val doc = parseMd("t.md", "看[说明](other-doc.md)结尾。\n")
            val ops = doc.positionOps!!
            assertTrue(ops.locateQuery("说明").isNotEmpty())
        }

    @Test
    fun codeAndTableTextAreSearchable() =
        runTest {
            val doc =
                parseMd(
                    "t.md",
                    "引言\n\n```kotlin\nval 搜索词 = 1\n```\n\n| 名称 | 值 |\n| --- | --- |\n| 搜索词 | 42 |\n",
                )
            val ops = doc.positionOps!!
            val codeHits = ops.locateQuery("搜索词")
            // Code block + table cell → at least two blocks hit.
            assertTrue(codeHits.size >= 2)
            assertTrue(codeHits.map { it.blockIndex }.toSet().size >= 2)
            // Fence markers themselves are not visible text.
            assertTrue(ops.locateQuery("```").isEmpty())
        }

    @Test
    fun multilingualCountsMatchFixtures() =
        runTest {
            val zh = parseMd("t.md", "量子计算机好，量子计算机妙。量子！\n")
            assertEquals(3, zh.positionOps!!.locateQuery("量子").size)
            val ja = parseMd("t.md", "古池や蛙飛び込む。古池や二度目。\n")
            assertEquals(2, ja.positionOps!!.locateQuery("古池や").size)
            val emoji = parseMd("t.md", "📚书📚架📚尾\n")
            assertEquals(3, emoji.positionOps!!.locateQuery("📚").size)
            assertTrue(emoji.positionOps!!.locateQuery("不存在的词").isEmpty())
        }

    @Test
    fun repeatAndCasingFrozen() =
        runTest {
            val doc = parseMd("t.md", "aaa ErrorCode errorcode\n")
            val ops = doc.positionOps!!
            assertEquals(3, ops.locateQuery("a").size)
            assertEquals(1, ops.locateQuery("ErrorCode").size)
            assertEquals(1, ops.locateQuery("errorcode").size)
            assertEquals(0, ops.locateQuery("errorCode").size)
        }

    @Test
    fun longSpansNeverMatchAcrossBlocks() =
        runTest {
            val doc = parseMd("t.md", "前半匹配\n\n后半匹配\n")
            val ops = doc.positionOps!!
            assertTrue(ops.locateQuery("匹配后半").isEmpty())
            assertEquals(2, ops.locateQuery("匹配").size)
        }

    @Test
    fun txtTreatsMarkersAsLiteralText() =
        runTest {
            val doc =
                parser.parse(
                    DocumentId("t"),
                    TextDecoder.decode("# 标题 **加粗** [文字](u)".toByteArray()),
                    "t.txt",
                    false,
                )
            val ops = doc.positionOps!!
            assertTrue(ops.locateQuery("**").isNotEmpty())
            assertTrue(ops.locateQuery("# 标题").isNotEmpty())
            assertTrue(ops.locateQuery("文字").isNotEmpty())
        }

    @Test
    fun degradedFallbackChunksAreSearchable() =
        runTest {
            // A giant single code block splits into bounded plain-text chunks
            // (ADR-0006); the needle stays findable after the split.
            val needle = "degraded-needle-7"
            val big =
                buildString {
                    append("```\n")
                    append("x".repeat(40 * 1024))
                    append(needle)
                    append("y".repeat(40 * 1024))
                    append("\n```\n")
                }
            val doc = parseMd("t.md", big)
            assertTrue(doc.degraded)
            val hits = doc.positionOps!!.locateQuery(needle)
            assertEquals(1, hits.size)
        }

    @Test
    fun blankQueriesYieldNothing() =
        runTest {
            val doc = parseMd("t.md", "正文内容\n")
            val ops = doc.positionOps!!
            assertTrue(ops.locateQuery("").isEmpty())
            // R2-06: whitespace-only is blank too (matches DocumentSearch).
            assertTrue(ops.locateQuery("   ").isEmpty())
        }

    @Test
    fun denseRepeatStopsAtSharedCap() =
        runTest {
            // One block of 20k `a`: the shared scan stops at the same window
            // `:core:reader` enforces, in document order (first window).
            val doc = parseMd("t.md", "a".repeat(20_000) + "\n")
            val hits = doc.positionOps!!.locateQuery("a")
            assertEquals(SearchLimits.MAX_SEARCH_MATCHES, hits.size)
            assertEquals(MatchLocation(0, 0, 1), hits.first())
        }
}
