package io.leaflet.reader.ui

import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * R2 DEF-008 regression: Compose-observed state must only be written on the
 * main thread.
 *
 * `ReaderViewModel` exposes `ui` / `search` as `StateFlow`s that the screen
 * reads through `collectAsState()`; a write from a background dispatcher is a
 * SNAPSHOT write. Compose then runs its apply-notification machinery on the
 * writing thread, which (a) drives recomposition on a thread without a
 * `Looper` — `IllegalStateException: The current thread must have a looper!`
 * inside `AndroidPrefetchScheduler`, reproduced in this session's joint device
 * run — and (b) mutates the SlotTable concurrently with the main thread's
 * composition/dispose, which is the mechanism behind the reported
 * `SlotWriter.removeGroup` → `ArrayIndexOutOfBoundsException` during
 * `WrappedComposition.dispose`.
 *
 * The check is mechanism-level and deterministic: a global snapshot apply
 * observer records the thread of every apply while the reader opens, searches,
 * re-opens (freshness recheck) and refreshes. Any apply on a non-main thread
 * fails the test; in production the same write is what corrupts the runtime.
 */
@RunWith(AndroidJUnit4::class)
class ReaderThreadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun composeStateIsOnlyWrittenOnTheMainThread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val md =
            """
            # Thread Proof

            Alpha paragraph one with a page-search-needle.

            Alpha paragraph two.
            """.trimIndent()
        val store = FakePageStore(mapOf("thread.md" to md.toByteArray()), mapOf("thread.md" to "thread.md"))
        val vm = pageViewModel(context, store)
        val offMainApplies = CopyOnWriteArrayList<String>()
        val registration =
            Snapshot.registerApplyObserver { applied, _ ->
                if (applied.isNotEmpty() && Looper.myLooper() != Looper.getMainLooper()) {
                    offMainApplies.add("${Thread.currentThread().name}: ${applied.size} state(s)")
                }
            }
        try {
            compose.setContent { ReaderScreenShim(documentId = "thread.md", vm = vm) }
            compose.waitUntil(timeoutMillis = 15_000) {
                vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
            }
            // Search runs a debounced scan on IO and writes `_search`.
            compose.runOnIdle { vm.onSearchQuery("page-search-needle") }
            compose.waitUntil(timeoutMillis = 15_000) {
                vm.search.value.query == "page-search-needle" && !vm.search.value.searching
            }
            // Same-document re-open (freshness recheck) and an explicit refresh
            // both run IO work and then publish state.
            compose.runOnIdle { vm.open(io.leaflet.model.DocumentId("thread.md")) }
            compose.waitUntil(timeoutMillis = 15_000) {
                vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
            }
            compose.runOnIdle { vm.refresh(io.leaflet.model.DocumentId("thread.md")) }
            compose.waitUntil(timeoutMillis = 15_000) {
                vm.ui.value is io.leaflet.reader.ui.reader.ReaderUiState.Content
            }
            compose.waitForIdle()
            // Position persistence also runs off the main thread: make sure the
            // exit flush path is exercised too.
            compose.runOnIdle { vm.onVisibleBlock(1) }
            compose.waitForIdle()
        } finally {
            registration.dispose()
        }
        assertTrue(
            "Compose state was applied off the main thread: $offMainApplies",
            offMainApplies.isEmpty(),
        )
    }
}
