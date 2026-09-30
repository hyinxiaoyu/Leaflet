package io.leaflet.reader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.reader.ui.reader.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3-01 / DEF-010: the real destroy+create transition of the host Activity,
 * kept in its OWN class whose name sorts last.
 *
 * Why the name: `activity.recreate()` leaves the `createComposeRule` host with
 * no composition attached (the rule composes into the first instance only), so
 * any test that runs AFTER it in the same process fails with "No compose
 * hierarchies found in the app" — observed as a cross-test contamination that
 * broke `ReaderStorageBoundaryTest` on the E-Ink flavor. Isolating it last
 * keeps the recreate coverage without poisoning the suite; the flush
 * discrimination itself lives in [ReaderLifecycleTest] and does not destroy
 * the harness.
 */
@RunWith(AndroidJUnit4::class)
class ZzActivityRecreationTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val markdown = "# Lifecycle Proof\n\nAlpha\n\nBeta\n\nGamma\n\nDelta\n"

    @Test
    fun activityRecreationKeepsTheDocumentAndRestoreAddress() {
        // A REAL destroy+create cycle of the host Activity (the transition that
        // preceded the reported crash), with the reader composed.
        val store = FakePageStore(mapOf("life.md" to markdown.toByteArray()), mapOf("life.md" to "life.md"))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(documentId = "life.md", vm = vm) }
        compose.waitUntil(timeoutMillis = 20_000) { vm.ui.value is ReaderUiState.Content }

        // Advance the reading address first, then capture it: the Content's
        // restore address tracks the latest saved position (DEF-013), and this
        // test asserts that the CURRENT address survives destroy+create.
        compose.runOnIdle { vm.jumpToBlock(2) }
        compose.waitForIdle()
        val before = vm.ui.value as ReaderUiState.Content
        assertEquals("address advanced before recreation", 2, before.restoreBlock)
        val activity = PageViews.activityOf(compose)
        PageViews.onMain { activity.recreate() }

        // The recreated instance has no composition behind `createComposeRule`
        // (the rule composes into the FIRST activity), so this test asserts the
        // state contract that survives a real destroy+create instead of asking
        // Compose for a tree that the harness does not rebuild: the ViewModel
        // keeps the parsed document and its restore address, and the destroyed
        // composition flushed the position on dispose.
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline && !store.savedPositions.contains("life.md")) {
            Thread.sleep(100)
        }
        assertTrue("dispose flush ran during recreation", store.savedPositions.contains("life.md"))
        val after = vm.ui.value as ReaderUiState.Content
        assertTrue("same document instance survives recreation", after.document === before.document)
        assertEquals("restore address survives recreation", before.restoreBlock, after.restoreBlock)
    }
}
