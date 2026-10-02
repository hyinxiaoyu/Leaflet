package io.leaflet.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.model.DocumentId
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.ui.reader.ReaderUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual line measurement and restoration, rather than only stored block indices. */
@RunWith(AndroidJUnit4::class)
class ReaderLogicalPositionPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val body = (0..1799).joinToString(" ") { "word$it" }

    private fun turn() {
        val bounds = PageViews.scrollerBounds(compose, horizontal = false) ?: error("reader list is absent")
        compose.onRoot().performTouchInput { click(Offset(bounds.right - 8f, bounds.center.y)) }
        compose.waitForIdle()
    }

    private fun visibleNativeCharacter(): Int {
        val root = PageViews.rootView(compose)
        val top = PageViews.scrollerBounds(compose, horizontal = false)?.top ?: error("reader list is absent")
        val views = PageViews.allTextViews(compose)
        return PageViews.onMain {
            val view = views.first { it.text.toString().startsWith("word0 ") }
            val rootLocation = IntArray(2)
            val textLocation = IntArray(2)
            root.getLocationInWindow(rootLocation)
            view.getLocationInWindow(textLocation)
            val y = (rootLocation[1] + top - textLocation[1] - view.totalPaddingTop).toInt().coerceAtLeast(0)
            val layout = view.layout ?: error("text layout is absent")
            layout.getLineStart(layout.getLineForVertical(y))
        }
    }

    @Test
    fun aLongParagraphRestoresItsVisibleLineAfterReopen() {
        val id = "long.md"
        val store = FakePageStore(mapOf(id to body.toByteArray()))
        val settings = pageSettingsViewModel()
        settings.setReadingMode(ReadingMode.PAGE_TURN.stored)
        var current by mutableStateOf(pageViewModel(context, store))
        compose.setContent { key(current) { ReaderScreenShim(id, current, settings) } }
        compose.waitUntil(20_000) { current.ui.value is ReaderUiState.Content }
        compose.waitUntil(5_000) { flowValue(settings.readingMode) == ReadingMode.PAGE_TURN.stored }
        compose.waitForIdle()
        turn()
        val before = visibleNativeCharacter()
        assertTrue("the first page turn moved inside the paragraph", before > 0)
        runBlocking { current.flushPosition().join() }
        val saved = runBlocking { store.loadPosition(DocumentId(id)) } ?: error("no position saved")
        assertEquals(0, saved.position.blockIndex)
        assertEquals("the measured visible line is persisted", before, saved.position.blockOffset)

        val reopened = pageViewModel(context, store)
        compose.runOnIdle { current = reopened }
        compose.waitUntil(20_000) { reopened.ui.value is ReaderUiState.Content }
        compose.waitForIdle()
        assertEquals("reopening keeps the actual visible line", before, visibleNativeCharacter())
        runBlocking { reopened.flushPosition().join() }
        assertEquals(saved.position.blockOffset, runBlocking { store.loadPosition(DocumentId(id)) }?.position?.blockOffset)
    }

    @Test
    fun aPageTurnInsideOneTxtBlockRecordsItsCharacterOffset() {
        val id = "long.txt"
        val store = FakePageStore(mapOf(id to body.toByteArray()))
        val settings = pageSettingsViewModel()
        settings.setReadingMode(ReadingMode.PAGE_TURN.stored)
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim(id, vm, settings) }
        compose.waitUntil(20_000) { vm.ui.value is ReaderUiState.Content }
        compose.waitUntil(5_000) { flowValue(settings.readingMode) == ReadingMode.PAGE_TURN.stored }
        compose.waitForIdle()
        turn()
        runBlocking { vm.flushPosition().join() }
        val position = runBlocking { store.loadPosition(DocumentId(id)) }?.position ?: error("no position saved")
        assertEquals(0, position.blockIndex)
        assertTrue("Compose text also has a nonzero in-block address", position.blockOffset > 0)
    }
}
