package io.leaflet.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.model.DocumentId
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3-07 acceptance on the REAL reader route: large documents through the
 * production read → parse → page path, end-marker reachability, 1,000+ heading
 * TOCs, and in-document search (REQ-0002/0006/0008/0011).
 *
 * Samples are generated in-process and fed through [FakePageStore], so the
 * numbers are the app's read+parse+first-content cost with in-memory bytes;
 * provider/SAF read latency is NOT measured here (see the R3-07 delivery).
 * Peak memory samples this process's `totalPss` while the document loads.
 *
 * The 5 MB / 10 MB methods are separate from the small sizes so the fast batch
 * can run without waiting for the large ones.
 */
@RunWith(AndroidJUnit4::class)
class ReaderLargeDocumentPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val kilobyte = 1024
    private val megabyte = 1024 * 1024

    // -- samples --------------------------------------------------------------

    private fun manySmallBlocks(
        docId: String,
        target: Int,
    ): String =
        buildString(target + 512) {
            append("SAMPLE-$docId start.\n\n")
            var i = 0
            while (length < target) {
                append("Paragraph ${i++} alpha beta gamma delta epsilon zeta eta theta.\n\n")
            }
            append("END-MARKER-MANY\n")
        }

    private fun giantParagraph(
        docId: String,
        target: Int,
    ): String =
        buildString(target + 512) {
            append("SAMPLE-$docId start.\n\n")
            while (length < target) append("alpha beta gamma delta ")
            append("\n\nEND-MARKER-PARA\n")
        }

    private fun giantCodeBlock(
        docId: String,
        target: Int,
    ): String =
        buildString(target + 512) {
            append("SAMPLE-$docId start.\n\n```kotlin\n")
            while (length < target) append("val value = computeSomething(alpha, beta, gamma)\n")
            append("```\n\nEND-MARKER-CODE\n")
        }

    private fun headingsDocument(count: Int): String =
        buildString(count * 48) {
            append("SAMPLE-toc start.\n\n")
            for (i in 1..count) {
                append("## Section $i\n\nParagraph $i body alpha beta gamma.\n\n")
            }
            append("END-MARKER-TOC\n")
        }

    private fun searchDocument(
        docId: String,
        occurrences: Int,
    ): String =
        buildString(occurrences * 24) {
            append("SAMPLE-$docId start.\n\n")
            for (i in 1..occurrences) append("Line $i carries the needle token.\n\n")
        }

    // -- harness --------------------------------------------------------------

    private fun boot(): Pair<FakePageStore, ReaderViewModel> {
        val store = FakePageStore(mapOf("boot.md" to "boot paragraph\n".toByteArray()), mapOf("boot.md" to "boot.md"))
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreenShim(documentId = "boot.md", vm = vm, settingsViewModel = pageSettingsViewModel())
        }
        compose.waitUntil(timeoutMillis = 20_000) { PageViews.viewTexts(compose).any { it.contains("boot paragraph") } }
        return store to vm
    }

    /** Waits for [condition], failing with the [step] name instead of a bare timeout. */
    private fun awaitStep(
        step: String,
        timeoutMs: Long,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            compose.waitForIdle()
        }
        assertTrue("R3-07 step timed out after ${timeoutMs}ms: $step", false)
    }

    /** Peak `totalPss` (KB) of this process while a load runs. */
    private class PssSampler {
        @Volatile private var running = true
        private var peak = 0
        private var thread: Thread? = null

        fun start() {
            thread =
                Thread {
                    while (running) {
                        val info = android.os.Debug.MemoryInfo()
                        android.os.Debug.getMemoryInfo(info)
                        if (info.totalPss > peak) peak = info.totalPss
                        Thread.sleep(50)
                    }
                }.also {
                    it.isDaemon = true
                    it.start()
                }
        }

        fun stop(): Int {
            running = false
            thread?.join(2_000)
            return peak
        }
    }

    private fun measure(
        store: FakePageStore,
        vm: ReaderViewModel,
        docId: String,
        sample: String,
        marker: String,
        timeoutMs: Long,
        withSampler: Boolean = true,
    ): String {
        store.setBytes(docId, sample.toByteArray())
        val sampler = PssSampler()
        if (withSampler) sampler.start()
        val t0 = System.currentTimeMillis()
        compose.runOnIdle { vm.open(DocumentId(docId)) }
        awaitStep("$docId open -> Content", timeoutMs) { vm.ui.value is ReaderUiState.Content }
        val toContent = System.currentTimeMillis() - t0
        val document = (vm.ui.value as ReaderUiState.Content).document
        try {
            awaitStep("$docId Content -> first readable text", timeoutMs) {
                PageViews.viewTexts(compose).any { it.contains("SAMPLE-$docId") }
            }
        } catch (e: AssertionError) {
            val texts = PageViews.viewTexts(compose)
            throw AssertionError(
                "${e.message}; degraded=${document.degraded} blocks=${document.blocks.size} " +
                    "value=${vm.ui.value::class.simpleName} " +
                    "parseHasSample=${document.blocks.any { it.text.contains("SAMPLE-$docId") }} " +
                    "viewTexts=${texts.size} first=${texts.take(3)} " +
                    "composeTexts=${PageViews.composeTexts(compose).take(6)} " +
                    "scroller=${PageViews.scrollerBounds(compose, horizontal = false)} " +
                    "block0='${document.blocks.firstOrNull()?.text?.take(60)}'",
                e,
            )
        }
        val toFirstText = System.currentTimeMillis() - t0
        val parsedToEnd = document.blocks.any { it.text.contains(marker) }
        val blocks = document.blocks.size

        // Reach the end marker ON THE PAGE (a real search jump proves the whole
        // file — not just the first screen — is live and reachable).
        compose.runOnIdle { vm.onSearchQuery(marker) }
        awaitStep("$docId search jump -> end marker on the page", timeoutMs) {
            PageViews.viewTexts(compose).any { it.contains(marker) }
        }
        val toEndMarker = System.currentTimeMillis() - t0
        val peak = sampler.stop()

        assertTrue("$docId must parse to its end marker", parsedToEnd)
        return "$docId: blocks=$blocks openToContent=${toContent}ms firstText=${toFirstText}ms " +
            "endMarker=${toEndMarker}ms peakPss=${peak}KB"
    }

    // -- fast batch -----------------------------------------------------------

    /** Many small blocks at 100 KB and 1 MB. */
    @Test
    fun manySmallBlocksOpenAt100kAnd1m() {
        val (store, vm) = boot()
        val results =
            listOf("100KB" to 100 * kilobyte, "1MB" to megabyte).map { (label, size) ->
                measure(store, vm, "many-$label.md", manySmallBlocks("many-$label.md", size), "END-MARKER-MANY", 180_000)
            }
        println("R3-07 many-small | " + results.joinToString(" | "))
    }

    /** One giant paragraph at 100 KB and 1 MB (block-splitting path). */
    @Test
    fun giantParagraphOpensAt100kAnd1m() {
        val (store, vm) = boot()
        val results =
            listOf("100KB" to 100 * kilobyte, "1MB" to megabyte).map { (label, size) ->
                measure(store, vm, "para-$label.md", giantParagraph("para-$label.md", size), "END-MARKER-PARA", 180_000)
            }
        println("R3-07 giant-paragraph | " + results.joinToString(" | "))
    }

    /** One giant fenced code block at 100 KB. */
    @Test
    fun giantCodeBlockOpensAt100k() {
        val (store, vm) = boot()
        val result =
            measure(store, vm, "code-100KB.md", giantCodeBlock("code-100KB.md", 100 * kilobyte), "END-MARKER-CODE", 180_000)
        println("R3-07 giant-code | $result")
    }

    /** A 1,200-heading TOC parses and a late heading is reachable. */
    @Test
    fun thousandHeadingTocIsParsedAndLateHeadingsAreReachable() {
        val (store, vm) = boot()
        store.setBytes("toc.md", headingsDocument(1_200).toByteArray())
        compose.runOnIdle { vm.open(DocumentId("toc.md")) }
        awaitStep("toc open -> Content", 180_000) { vm.ui.value is ReaderUiState.Content }
        awaitStep("toc first text", 180_000) { PageViews.viewTexts(compose).any { it.contains("SAMPLE-toc") } }

        val document = (vm.ui.value as ReaderUiState.Content).document
        assertTrue("the TOC must hold 1,000+ headings (was ${document.toc.size})", document.toc.size >= 1_200)
        assertEquals("the last heading is present exactly once", 1, document.toc.count { it.text == "Section 1200" })

        compose.runOnIdle { vm.onSearchQuery("Section 1200") }
        awaitStep("toc late heading on the page", 180_000) {
            PageViews.viewTexts(compose).any { it.contains("Paragraph 1200 body") }
        }
    }

    /** Dense matches hit the cap notice; the latest query always wins. */
    @Test
    fun denseSearchShowsTheCapNoticeAndRapidInputKeepsTheLatestQuery() {
        val (store, vm) = boot()
        store.setBytes("dense.md", searchDocument("dense", 6_000).toByteArray())
        compose.runOnIdle { vm.open(DocumentId("dense.md")) }
        awaitStep("dense open", 180_000) { PageViews.viewTexts(compose).any { it.contains("SAMPLE-dense") } }

        val rect = PageViews.scrollerBounds(compose, horizontal = false)!!
        compose.onRoot().performTouchInput { click(Offset(rect.center.x, rect.center.y)) }
        compose.waitForIdle()
        compose.onNodeWithText("Search").performClick()
        compose.waitForIdle()

        val field = compose.onAllNodes(hasSetTextAction()).onFirst()
        field.performTextInput("needle")
        awaitStep("capped notice", 120_000) { PageViews.composeTexts(compose).any { it.contains("结果过多") } }

        field.performTextInput("zzzz")
        awaitStep("no-match notice for the latest query", 120_000) {
            PageViews.composeTexts(compose).any { it.contains("无匹配") }
        }
        val search = vm.search.value
        assertTrue("the latest query owns the search state (was '${search.query}')", search.query.endsWith("zzzz"))
        assertEquals("no stale matches for the latest query", 0, search.total)
    }

    /** Switching documents clears the search state (REQ-0008 FR-5). */
    @Test
    fun searchResetsAcrossDocumentSwitch() {
        val (store, vm) = boot()
        store.setBytes("a.md", searchDocument("a", 40).toByteArray())
        store.setBytes("b.md", "SAMPLE-b start.\n\nPlain body with no matches.\n".toByteArray())
        compose.runOnIdle { vm.open(DocumentId("a.md")) }
        awaitStep("doc a open", 120_000) { PageViews.viewTexts(compose).any { it.contains("SAMPLE-a") } }

        compose.runOnIdle { vm.onSearchQuery("needle") }
        awaitStep("matches for doc a", 120_000) { vm.search.value.total > 0 }

        compose.runOnIdle { vm.open(DocumentId("b.md")) }
        awaitStep("doc b open", 120_000) { PageViews.viewTexts(compose).any { it.contains("SAMPLE-b") } }

        val search = vm.search.value
        assertEquals("query cleared on document switch", "", search.query)
        assertEquals("matches cleared on document switch", 0, search.total)
    }

    // -- slow batch (run separately) -----------------------------------------

    /** Many small blocks at 5 MB. */
    @Test
    fun manySmallBlocksOpenAt5m() {
        val (store, vm) = boot()
        val result = measure(store, vm, "many-5MB.md", manySmallBlocks("many-5MB.md", 5 * megabyte), "END-MARKER-MANY", 420_000)
        println("R3-07 many-small-5MB | $result")
    }

    /** One giant paragraph at 5 MB. */
    @Test
    fun giantParagraphOpensAt5m() {
        val (store, vm) = boot()
        val result = measure(store, vm, "para-5MB.md", giantParagraph("para-5MB.md", 5 * megabyte), "END-MARKER-PARA", 420_000)
        println("R3-07 giant-paragraph-5MB | $result")
    }

    /** One giant paragraph at 10 MB: the largest shape in the acceptance list. */
    @Test
    fun giantParagraphOpensAt10m() {
        val (store, vm) = boot()
        val result = measure(store, vm, "para-10MB.md", giantParagraph("para-10MB.md", 10 * megabyte), "END-MARKER-PARA", 600_000)
        println("R3-07 giant-paragraph-10MB | $result")
    }

    /** One giant fenced code block at the larger sizes. */
    @Test
    fun giantCodeBlockOpensAt1m() {
        val (store, vm) = boot()
        val result =
            measure(store, vm, "code-1MB.md", giantCodeBlock("code-1MB.md", megabyte), "END-MARKER-CODE", 420_000)
        println("R3-07 giant-code-1MB | $result")
    }

    @Test
    fun giantCodeBlockOpensAt5m() {
        val (store, vm) = boot()
        val result =
            measure(store, vm, "code-5MB.md", giantCodeBlock("code-5MB.md", 5 * megabyte), "END-MARKER-CODE", 480_000)
        println("R3-07 giant-code-5MB | $result")
    }

    @Test
    fun giantCodeBlockOpensAt10m() {
        val (store, vm) = boot()
        val result =
            measure(store, vm, "code-10MB.md", giantCodeBlock("code-10MB.md", 10 * megabyte), "END-MARKER-CODE", 600_000)
        println("R3-07 giant-code-10MB | $result")
    }

    /**
     * Above the documented 16 MiB read cap the document is truncated with an
     * explicit notice, not silently: the state is degraded, the notice string
     * is on the page, the truncated tail marker is absent, and the readable
     * head still renders.
     */
    @Test
    fun oversizedDocumentShowsTheExplicitReadCapNotice() {
        val (store, vm) = boot()
        val docId = "cap-17MB.md"
        store.setBytes(docId, giantParagraph(docId, 17 * megabyte).toByteArray())
        compose.runOnIdle { vm.open(DocumentId(docId)) }
        awaitStep("cap open -> Content", 300_000) { vm.ui.value is ReaderUiState.Content }
        awaitStep("cap first readable text", 300_000) {
            PageViews.viewTexts(compose).any { it.contains("SAMPLE-$docId") }
        }

        val document = (vm.ui.value as ReaderUiState.Content).document
        assertTrue("a truncated document must be marked degraded", document.degraded)
        assertTrue(
            "the truncation notice must be visible (degradedNotice=${document.degradedNotice})",
            PageViews.composeTexts(compose).any { it.contains("文件过大") },
        )
        assertTrue(
            "the tail marker beyond the cap must not be silently present",
            document.blocks.none { it.text.contains("END-MARKER-PARA") },
        )
        assertTrue("the readable head is still shown", document.blocks.isNotEmpty())
        println(
            "R3-07 read-cap-17MB | blocks=${document.blocks.size} degraded=${document.degraded} " +
                "notice=${document.degradedNotice}",
        )
    }
}
