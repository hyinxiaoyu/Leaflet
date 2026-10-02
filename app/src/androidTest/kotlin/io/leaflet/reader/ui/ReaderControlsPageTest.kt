package io.leaflet.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.reader.R
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.SettingsViewModel
import io.leaflet.reader.ui.reader.ReaderActionMenu
import io.leaflet.reader.ui.reader.ReaderControlTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderControlsPageTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun openPage(
        body: String,
        settings: SettingsViewModel = pageSettingsViewModel(),
    ) {
        val store = FakePageStore(mapOf("controls.md" to body.toByteArray()))
        val vm = pageViewModel(context, store)
        compose.setContent { ReaderScreenShim("controls.md", vm, settings) }
        compose.waitUntil(20_000) { PageViews.viewTexts(compose).any { it.contains("Paragraph 01") } }
        compose.waitForIdle()
    }

    private fun longDocument() = (1..60).joinToString("\n\n") { "Paragraph %02d alpha beta gamma delta epsilon zeta eta.".format(it) }

    private fun anchor(): Pair<String, Float> = PageViews.textTopsByPrefix(compose, "Paragraph ").maxBy { it.second }

    private fun topOf(text: String) = PageViews.textTopsByPrefix(compose, "Paragraph ").first { it.first == text }.second

    @Test
    fun footerControlsTurnOnceAndMenuDoesNotMoveBody() {
        openPage(longDocument())
        compose.onNodeWithTag(ReaderControlTags.PREVIOUS).assertIsNotEnabled()
        compose.onNodeWithTag(ReaderControlTags.NEXT).assertIsEnabled()
        val (text, before) = anchor()
        compose.onNodeWithTag(ReaderControlTags.MENU).performClick()
        assertEquals("opening menu leaves text in place", before, topOf(text), 2f)
        compose.onNodeWithTag(ReaderControlTags.MENU).performClick()
        assertEquals("closing menu leaves text in place", before, topOf(text), 2f)
        compose.onNodeWithTag(ReaderControlTags.NEXT).performClick()
        assertTrue("visible next turns", topOf(text) < before)
        compose.onNodeWithTag(ReaderControlTags.PREVIOUS).assertIsEnabled().performClick()
        assertEquals("visible previous returns to the same position", before, topOf(text), 2f)
    }

    @Test
    fun singleScreenHasDisabledPageControls() {
        openPage("Paragraph 01 short document.")
        compose.onNodeWithTag(ReaderControlTags.PREVIOUS).assertIsNotEnabled()
        compose.onNodeWithTag(ReaderControlTags.NEXT).assertIsNotEnabled()
        compose.onNodeWithTag(ReaderControlTags.MENU).assertIsEnabled()
    }

    @Test
    fun helpCanReopenAndMarginTouchesCannotOperateBehindIt() {
        val settings = pageSettingsViewModel(tapZonesHintSeen = true)
        openPage(longDocument(), settings)
        val (text, before) = anchor()
        val area = compose.onNodeWithTag("reader.gestures").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(ReaderControlTags.MENU).performClick()
        compose.onNodeWithTag(ReaderControlTags.HELP).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.reader_hint_title)).assertExists()
        compose.onRoot().performTouchInput { click(Offset(area.right - 1f, area.center.y)) }
        assertEquals("tutorial margin cannot turn underlying content", before, topOf(text), 2f)
        compose.onRoot().performTouchInput { click(Offset(area.center.x, area.bottom - 1f)) }
        compose.onNodeWithTag(ReaderControlTags.MENU).assertTextContains(context.getString(R.string.reader_open_menu))
        compose.onNodeWithText(context.getString(R.string.reader_hint_got_it)).performClick()
        assertEquals("tutorial dismissal preserves position", before, topOf(text), 2f)
        assertTrue("help does not reset first-run preference", flowValue(settings.tapZonesHintSeen))
        compose.onNodeWithTag(ReaderControlTags.MENU).performClick()
        compose.onNodeWithTag(ReaderControlTags.HELP).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.reader_hint_title)).assertExists()
    }

    @Test
    fun continuousModeKeepsMenuWithoutPageButtons() {
        val settings = pageSettingsViewModel()
        settings.setReadingMode(ReadingMode.CONTINUOUS_SCROLL.stored)
        compose.waitUntil(5_000) { flowValue(settings.readingMode) == ReadingMode.CONTINUOUS_SCROLL.stored }
        openPage(longDocument(), settings)
        compose.onNodeWithTag(ReaderControlTags.PREVIOUS).assertDoesNotExist()
        compose.onNodeWithTag(ReaderControlTags.NEXT).assertDoesNotExist()
        compose.onNodeWithTag(ReaderControlTags.MENU).assertIsEnabled().performClick()
        compose.onNodeWithTag(ReaderControlTags.ACTIONS).assertExists()
    }

    @Test
    fun boundedMenuAtNarrowWidthAndDoubleFontScaleKeepsEveryActionReachable() {
        val invoked = mutableListOf<String>()
        val density = context.resources.displayMetrics.density
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                Box(Modifier.width(280.dp)) {
                    ReaderActionMenu(
                        mode = ReadingMode.PAGE_TURN,
                        maxHeight = 120.dp,
                        canChangeMode = true,
                        onToc = { invoked += "toc" },
                        onSearch = { invoked += "search" },
                        onRefresh = { invoked += "refresh" },
                        onMode = { invoked += "mode" },
                        onHelp = { invoked += "help" },
                        onBack = { invoked += "back" },
                    )
                }
            }
        }
        val bounds = compose.onNodeWithTag(ReaderControlTags.ACTIONS).fetchSemanticsNode().boundsInRoot
        assertTrue("menu obeys its height cap", bounds.height <= 120 * density + 1)
        for (label in listOf(
            R.string.reader_toc,
            R.string.reader_search,
            R.string.reader_refresh,
            R.string.reader_mode_continuous,
            R.string.reader_controls_help,
            R.string.go_back,
        )) {
            compose.onNodeWithText(context.getString(label)).performScrollTo().performClick()
        }
        compose.runOnIdle { assertEquals(listOf("toc", "search", "refresh", "mode", "help", "back"), invoked) }
    }
}
