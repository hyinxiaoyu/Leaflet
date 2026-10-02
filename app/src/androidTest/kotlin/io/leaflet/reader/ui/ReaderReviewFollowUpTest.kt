package io.leaflet.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.TapZone
import io.leaflet.reader.TapZones
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Follow-up code review 2026-10-01: the two UI findings that need the composed
 * page — tail stability when the reading menu toggles (CR-20261001-03) and the
 * bounded fallback for a pathological wide table (CR-20261001-01).
 *
 * Lives in its own class because it composes the REAL `ReaderScreen` route the
 * same way `ReaderInteractionTest` does; the helpers reused here
 * ([PageHarness], [PageViews], `ReaderScreenShim`) are the shared page-test
 * harness. Coordinates are injected as real touch input and read back from the
 * composed tree. Emulator evidence only: hardware refresh/ghosting is R3-09.
 */
@RunWith(AndroidJUnit4::class)
class ReaderReviewFollowUpTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun numberedDocument(paragraphs: Int): String =
        buildString {
            append("# Follow-up Proof\n\n")
            for (i in 1..paragraphs) {
                append("Paragraph %02d alpha beta gamma delta epsilon zeta eta.\n\n".format(i))
            }
        }

    private fun openPage(
        body: String,
        settings: SettingsViewModel,
        docId: String,
        expect: String,
    ): ReaderViewModel {
        val store = FakePageStore(mapOf(docId to body.toByteArray()), mapOf(docId to docId))
        val vm = pageViewModel(context, store)
        compose.setContent {
            ReaderScreenShim(documentId = docId, vm = vm, settingsViewModel = settings)
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            PageViews.viewTexts(compose).any { it.contains(expect) } ||
                PageViews.composeTexts(compose).any { it.contains(expect) }
        }
        return vm
    }

    private fun usePageTurnMode(settings: SettingsViewModel) {
        compose.runOnIdle { settings.setReadingMode(ReadingMode.PAGE_TURN.stored) }
        val deadline = System.currentTimeMillis() + 5_000
        var stored = ""
        while (System.currentTimeMillis() < deadline) {
            stored = flowValue(settings.readingMode)
            if (stored == ReadingMode.PAGE_TURN.stored) break
            Thread.sleep(20)
        }
        assertEquals("the production settings path must reach PAGE_TURN", ReadingMode.PAGE_TURN.stored, stored)
        compose.waitForIdle()
    }

    /** The reader list's own rect: the tap-zone detector sits on exactly this. */
    private fun listRect() = PageViews.scrollerBounds(compose, horizontal = false) ?: error("no vertical scroller on the page")

    private fun tapZone(zone: TapZone) {
        val rect = listRect()
        val point =
            when (zone) {
                TapZone.NEXT -> Offset(rect.right - 8f, rect.center.y)
                TapZone.PREV -> Offset(rect.left + 8f, rect.center.y)
                TapZone.MENU -> Offset(rect.center.x, rect.center.y)
            }
        assertEquals(
            "$zone tap must land in its own zone",
            zone,
            TapZones.zoneFor(point.x - rect.left, rect.width.toInt()),
        )
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    /** Asserts the composed body lines kept their root-relative positions. */
    private fun assertSameBodyPositions(
        step: String,
        before: List<Pair<String, Float>>,
    ) {
        val after = PageViews.textTopsByPrefix(compose, "")
        assertEquals("$step: the composed body lines changed", before.map { it.first }, after.map { it.first })
        before.zip(after).forEach { (b, a) ->
            assertEquals("$step: '${b.first}' moved", b.second, a.second, 0.5f)
        }
    }

    /**
     * CR-20261001-03: closing the reading menu at the document tail must not move
     * the body. A tail reserve that shrinks with the menu lowers the maximum
     * scroll offset, so the list clamps backwards and the text jumps — the defect
     * the follow-up review found in the chrome-aware reserve.
     */
    @Test
    fun togglingTheMenuAtTheTailDoesNotMoveTheBody() {
        val settings = pageSettingsViewModel()
        openPage(numberedDocument(40), settings, docId = "tail-stability.md", expect = "Paragraph 01")
        usePageTurnMode(settings)

        // Turn to the tail with the menu OPEN, where the reserve used to be largest.
        tapZone(TapZone.MENU)
        var turns = 0
        while (turns < 60 && PageViews.viewTexts(compose).none { it.startsWith("Paragraph 40") }) {
            tapZone(TapZone.NEXT)
            turns++
        }
        assertTrue(
            "the last paragraph must be composed to exercise the tail",
            PageViews.viewTexts(compose).any { it.startsWith("Paragraph 40") },
        )
        val withMenuOpen = PageViews.textTopsByPrefix(compose, "")
        assertTrue("the tail must expose body lines", withMenuOpen.isNotEmpty())

        tapZone(TapZone.MENU)
        assertSameBodyPositions("menu closed at the tail", withMenuOpen)

        tapZone(TapZone.MENU)
        assertSameBodyPositions("menu reopened at the tail", withMenuOpen)
    }

    /**
     * CR-20261001-01: a table whose cell cannot be represented as a fixed
     * constraint must not crash the reader. The grid is replaced by the block's
     * own scrollable source text (pipes visible), so every character stays
     * reachable through horizontal scrolling.
     */
    @Test
    fun pathologicalWideTableFallsBackToScrollableTextWithoutCrashing() {
        val settings = pageSettingsViewModel()
        val cell = "測".repeat(10_000)
        val body = "| Left | Right |\n| --- | --- |\n| $cell | b |\n\nParagraph 01 after the table.\n"
        val vm = openPage(body, settings, docId = "wide-table.md", expect = "Paragraph 01")

        compose.runOnIdle {
            check(vm.ui.value is ReaderUiState.Content) { "the wide table must not fail the document" }
        }
        assertTrue(
            "the pathological table must render as its scrollable source text",
            PageViews.composeTexts(compose).any { it.contains("| Left |") && it.length > 10_000 },
        )
        compose.onNodeWithText("Paragraph 01 after the table.", substring = true).assertIsDisplayed()
    }
}
