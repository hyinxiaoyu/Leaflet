package io.leaflet.markdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.leaflet.model.DocumentId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T06 large-file instrumented pass (REQ-0002 AC-6): 10MB "many small blocks"
 * and "single giant block" shapes open cancellably with no OOM/ANR/crash,
 * giant blocks degrade with notice, cancellation releases, content stays
 * readable (no silent truncation).
 *
 * Fixture-free by design: shapes are synthesized in memory (same shapes the
 * T03 generator produces — see `testdata/README.md` "huge.md"), so no storage
 * permission, no `adb push`, and no testdata in the APK. Runs on-device
 * because only ART exercises the real 10MB parse memory path; the JVM suite
 * owns the syntax/title/encoding matrices instead.
 */
@RunWith(AndroidJUnit4::class)
class LargeFileInstrumentedTest {
    /**
     * R2 DEF-006: the REAL Markwon-backed parser on device. The old suite used
     * `createForJvmTests()`, which cannot prove the production Markwon/UI path
     * for large files.
     */
    private val parser =
        LeafletDocumentParser.create(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
        )

    private fun manyBlocks(sizeBytes: Int): String {
        val sb = StringBuilder(sizeBytes + 1024)
        var i = 0
        while (sb.length < sizeBytes) {
            sb.append("## Section ").append(i).append("\n\n")
                .append("Body line with needle long-doc-needle-").append(i % 97)
                .append(" and filler text to reach the target tier.\n\n")
            i++
        }
        return sb.toString()
    }

    @Test(timeout = 600_000)
    fun manySmallBlocks10MOpensReadable() {
        // 10MB ≈ 50s+ CPU on emulator: plain @Test (no runTest) so the
        // 60s virtual budget never applies — real JUnit timeout (10min)
        // guards instead. Readability only (no cancellation here; the
        // cancel path is covered at 2MB inside runTest above).
        val text = manyBlocks(10 * 1024 * 1024)
        assertTrue(text.length >= 10 * 1024 * 1024)
        val doc =
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) {
                parser.parse(
                    DocumentId("many10"),
                    DecodedText(text, EncodingState.CLEAN),
                    "large-many-10m.md",
                    true,
                )
            }
        assertTrue(doc.blocks.size > 1000)
        assertTrue(doc.blocks.all { it.text.length <= BlockLimits.MAX_BLOCK_CHARS })
        assertTrue(doc.blockTexts.joinToString("").contains("long-doc-needle-42"))
    }

    @Test
    fun singleGiantPara10MDegradesBounded() =
        runTest {
            val text = "word ".repeat(10 * 1024 * 1024 / 5)
            val doc =
                parser.parse(
                    DocumentId("para10"),
                    DecodedText(text, EncodingState.CLEAN),
                    "large-single-para-10m.md",
                    true,
                )
            assertTrue(doc.degraded)
            assertTrue(doc.degradedNotice!!.contains("纯文本显示"))
            assertTrue(doc.blocks.all { it.text.length <= BlockLimits.MAX_BLOCK_CHARS })
            // No silent truncation: fallback covers the source length.
            val covered = doc.blocks.sumOf { it.text.length }
            assertTrue(covered >= text.length - doc.blocks.size)
        }

    @Test
    fun singleGiantCode10MDegradesBounded() =
        runTest {
            val body = "x".repeat(10 * 1024 * 1024 - 16)
            val doc =
                parser.parse(
                    DocumentId("code10"),
                    DecodedText("```text\n$body\n```\n", EncodingState.CLEAN),
                    "large-single-code-10m.md",
                    true,
                )
            assertTrue(doc.degraded)
            assertTrue(doc.blocks.all { it.text.length <= BlockLimits.MAX_BLOCK_CHARS })
        }

    @Test
    fun parseCancellationDropsCleanly() =
        runTest {
            // 2MB fixture (~40k blocks ≈ 80 yields at the 512-block cadence):
            // exercises the same cooperative-cancel path as a 10MB parse but
            // finishes inside runTest's 60s virtual budget (10MB ≈ 50s+ CPU on
            // emulator, see T13 gate evidence — full-length cancel coverage stays
            // with the JVM suite and the cancellable production path).
            // The parse runs on Dispatchers.Default (real thread) so the test
            // thread stays free to deliver the cancel.
            val decoded = DecodedText(manyBlocks(2 * 1024 * 1024), EncodingState.CLEAN)
            var cancelled = false
            val job =
                launch(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        parser.parse(DocumentId("cancel"), decoded, "cancel.md", true)
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                }
            delay(10)
            job.cancelAndJoin()
            assertTrue(cancelled)
        }

    @Test
    fun staleParseNeverOverwrites() =
        runTest {
            // Slow A is cancelled before it completes; B's result stands alone.
            val decodedA = DecodedText(manyBlocks(1024 * 1024), EncodingState.CLEAN)
            val jobA =
                launch {
                    parser.parse(DocumentId("a"), decodedA, "a.md", true)
                }
            delay(5)
            jobA.cancelAndJoin()
            val docB =
                parser.parse(
                    DocumentId("b"),
                    DecodedText("# Doc B\n", EncodingState.CLEAN),
                    "b.md",
                    true,
                )
            assertEquals("Doc B", docB.title)
        }
}
