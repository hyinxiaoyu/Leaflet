package io.leaflet.reader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.model.DocumentId
import io.leaflet.reader.ui.reader.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R2 DEF-012 / DEF-013 regressions (state consistency around document switches
 * and same-document rechecks), reported by the 2026-09-20 code review.
 */
@RunWith(AndroidJUnit4::class)
class ReaderStateConsistencyTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun body(
        prefix: String,
        blocks: Int,
    ): String =
        buildString {
            append("# $prefix Heading\n\n")
            for (i in 1..blocks) append("$prefix block $i text\n\n")
        }

    /**
     * DEF-012: switching A→B and backgrounding while B still loads must never
     * persist B's row with A's address/anchor/fingerprint.
     */
    @Test
    fun switchingDocumentsDuringLoadNeverWritesTheOldAddressUnderTheNewId() {
        val a = body("Alpha", 6)
        val b = body("Bravo", 4)
        val store =
            FakePageStore(
                mapOf("a.md" to a.toByteArray(), "b.md" to b.toByteArray()),
                mapOf("a.md" to "a.md", "b.md" to "b.md"),
            )
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "a.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 20_000) { vm.ui.value is ReaderUiState.Content }

        // A is read up to block 5 and saved under A.
        compose.runOnIdle { vm.jumpToBlock(5) }
        compose.waitUntil(timeoutMillis = 20_000) { store.savedBlocksFor("a.md").lastOrNull() == 5 }
        val aFingerprint = kotlinx.coroutines.runBlocking { store.loadPosition(DocumentId("a.md")) }?.fingerprint
        assertTrue("A has its own stored row", aFingerprint != null)

        // B loads slowly, so the switch is still in flight (state = Loading).
        store.slowOpenFor = "b.md"
        store.slowOpenMs = 5_000
        compose.runOnIdle { vm.open(DocumentId("b.md")) }
        compose.waitUntil(timeoutMillis = 5_000) { vm.ui.value is ReaderUiState.Loading }

        // The exit/pause flush now fires (screen lifecycle calls it).
        val flush = vm.flushPosition()
        kotlinx.coroutines.runBlocking { flush.join() }

        assertTrue(
            "B must not be written while it loads: ${store.savedBlocksFor("b.md")}",
            store.savedBlocksFor("b.md").isEmpty(),
        )
        assertEquals("A keeps its own address", 5, store.savedBlocksFor("a.md").lastOrNull())
        assertEquals(
            "A keeps its own fingerprint",
            aFingerprint,
            kotlinx.coroutines.runBlocking { store.loadPosition(DocumentId("a.md")) }?.fingerprint,
        )
    }

    /**
     * DEF-013: a slow same-document recheck must not remount the reader (which
     * disposes the list state and re-runs the open-time restore scroll).
     */
    @Test
    fun sameDocumentRecheckDoesNotRemountTheReader() {
        val text = body("Charlie", 12)
        val store = FakePageStore(mapOf("c.md" to text.toByteArray()), mapOf("c.md" to "c.md"))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "c.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 20_000) { vm.ui.value is ReaderUiState.Content }
        assertTrue("reader entries are composed", PageViews.allTextViews(compose).isNotEmpty())

        // Read on: the throttled save path advances the stored address.
        Thread.sleep(2_200)
        compose.runOnIdle { vm.onVisibleBlock(6) }
        compose.waitUntil(timeoutMillis = 20_000) { store.savedBlocksFor("c.md").lastOrNull() == 6 }
        compose.waitForIdle()

        // Snapshot the LIVE view instance and the visible window.
        val beforeView = PageViews.allTextViews(compose).first()
        val beforeTexts = PageViews.viewTexts(compose)

        // Slow recheck of the SAME document (describe delayed, so the check is
        // observably in flight).
        store.describeDelayMs = 800
        val describesBefore = store.describeCount
        compose.runOnIdle { vm.open(DocumentId("c.md")) }
        compose.waitUntil(timeoutMillis = 20_000) { store.describeCount > describesBefore }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { vm.ui.value is ReaderUiState.Content }

        assertSame(
            "the reader entry was not remounted (list state preserved)",
            beforeView,
            PageViews.allTextViews(compose).first(),
        )
        assertEquals("the visible window did not move", beforeTexts, PageViews.viewTexts(compose))
    }
}
