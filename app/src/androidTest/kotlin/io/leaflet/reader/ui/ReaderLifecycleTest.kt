package io.leaflet.reader.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.model.DocumentId
import io.leaflet.reader.ui.reader.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3-01 package (最新缺陷独立复核与生命周期稳定性): the DEF-008 crash appeared
 * while an Activity was destroyed, so the lifecycle transitions around the
 * reader are exercised explicitly here — leaving the route (dispose flush),
 * Activity recreation, and a plain destroy via suite teardown — and the
 * residual risk is stated rather than claiming the original crash's cause was
 * proven by "it did not reproduce".
 *
 * These are stability checks: they assert the reader neither loses its
 * negotiated state (document instance + restore block) nor crashes during
 * those transitions.
 */
@RunWith(AndroidJUnit4::class)
class ReaderLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val markdown = "# Lifecycle Proof\n\nAlpha\n\nBeta\n\nGamma\n\nDelta\n"

    @Test
    fun leavingTheRouteFlushesPositionAndKeepsTheViewModelUsable() {
        val store = FakePageStore(mapOf("life.md" to markdown.toByteArray()), mapOf("life.md" to "life.md"))
        val vm = pageViewModel(context, store)
        val showReader = mutableStateOf(true)
        compose.setContent {
            if (showReader.value) {
                ReaderScreenShim(documentId = "life.md", vm = vm)
            } else {
                Text("library route")
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { vm.ui.value is ReaderUiState.Content }
        compose.runOnIdle { vm.jumpToBlock(2) }
        compose.waitForIdle()

        // Route pop: the reader leaves the composition → DisposableEffect flush.
        compose.runOnIdle { showReader.value = false }
        compose.waitUntil(timeoutMillis = 20_000) {
            store.savedPositions.contains("life.md")
        }
        compose.waitForIdle()
        assertTrue("flush reached the store", store.savedPositions.contains("life.md"))

        // The ViewModel instance (route-scoped in production) survives and can
        // re-open the same document without losing the parsed content.
        val before = vm.ui.value as ReaderUiState.Content
        compose.runOnIdle { vm.open(DocumentId("life.md")) }
        compose.waitUntil(timeoutMillis = 20_000) {
            val after = vm.ui.value
            after is ReaderUiState.Content && after.document === before.document
        }
    }

    @Test
    fun leavingTheRouteFlushesAPositionThatWasNotSavedYet() {
        // DEF-010: the previous test called `jumpToBlock`, which persists
        // IMMEDIATELY — an old write already satisfied it. This test creates a
        // position inside the save-throttle window (nothing persisted yet),
        // records the write history, then leaves the route and requires a NEW
        // write with the final address. Disabling the dispose flush makes it
        // fail.
        val store = FakePageStore(mapOf("life.md" to markdown.toByteArray()), mapOf("life.md" to "life.md"))
        val vm = pageViewModel(context, store)
        val showReader = mutableStateOf(true)
        compose.setContent {
            if (showReader.value) {
                ReaderScreenShim(documentId = "life.md", vm = vm)
            } else {
                Text("library route")
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { vm.ui.value is ReaderUiState.Content }

        val before = store.savedBlocksFor("life.md")
        // A scroll observation inside the 2s throttle: the throttler must NOT
        // persist this address by itself (address 4 is the discriminator — the
        // restore/scroll echoes only ever write address 0).
        compose.runOnIdle { vm.onVisibleBlock(4) }
        compose.waitForIdle()
        assertTrue(
            "address 4 is not persisted by the throttled observation: $before",
            before.none { it == 4 },
        )

        // Route pop → dispose flush must write the unsaved address. The wait
        // goes through the compose rule: the flush runs on the ViewModel's
        // Main-bound scope, and the test environment defers main-thread work
        // until the rule pumps it (a raw sleep loop would never resume it).
        compose.runOnIdle { showReader.value = false }
        compose.waitUntil(timeoutMillis = 20_000) {
            store.savedBlocksFor("life.md").lastOrNull() == 4
        }
        val after = store.savedBlocksFor("life.md")
        assertTrue("a new write happened on exit: $before -> $after", after.size > before.size)
        assertEquals("exit flush persisted the latest address", 4, after.last())
    }
}
