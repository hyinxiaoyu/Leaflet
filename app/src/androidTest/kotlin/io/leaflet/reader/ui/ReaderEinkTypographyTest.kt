package io.leaflet.reader.ui

import android.graphics.Color
import android.text.Layout
import android.widget.TextView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.leaflet.markdown.ReaderStyle
import io.leaflet.model.DisplayMode
import io.leaflet.reader.ui.reader.ReaderScreen
import io.leaflet.reader.ui.theme.LeafletTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E-Ink reading typography on the REAL reader route (REQ-0007; `ReadingLayoutPolicy.EINK`).
 *
 * NOT RUN on the authoring host (no JDK/Android SDK/emulator). These assertions read the
 * production Markwon `TextView`s, so they prove the policy reaches the view — they say
 * nothing about legibility, contrast, ghosting or refresh on a physical E-Ink panel, which
 * stay unverified until the device matrix is run.
 */
@RunWith(AndroidJUnit4::class)
class ReaderEinkTypographyTest {
    @get:Rule
    val compose = createComposeRule()

    private val markdown =
        """
        # Typography Proof

        Alpha paragraph with enough ordinary words to wrap across more than one line on a narrow panel.
        """.trimIndent()

    private fun openBody(mode: DisplayMode): TextView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FakePageStore(mapOf("typo.md" to markdown.toByteArray()), mapOf("typo.md" to "typo.md"))
        val vm = pageViewModel(context, store)
        compose.setContent {
            LeafletTheme(displayMode = mode, darkTheme = false) {
                ReaderScreen(viewModel = vm, documentId = "typo.md", onBack = {}, defaultDisplayMode = mode)
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) { PageViews.hasVisibleText(compose, "Alpha paragraph") }
        compose.waitForIdle()
        return PageViews.allTextViews(compose).first { it.text.contains("Alpha paragraph") }
    }

    private fun scaledPx(
        baseSp: Float,
        view: TextView,
    ): Float = baseSp * ReaderStyle.fontScaleForStep(ReaderStyle.FONT_SIZE_DEFAULT) * view.resources.displayMetrics.scaledDensity

    @Test
    fun einkBodyIsLargerJustifiedHyphenatedAndPureBlack() {
        val body = openBody(DisplayMode.EINK)

        assertEquals("E-Ink base size is 18 sp × step scale", scaledPx(18f, body), body.textSize, 1f)
        assertEquals(Layout.JUSTIFICATION_MODE_INTER_WORD, body.justificationMode)
        assertEquals(Layout.HYPHENATION_FREQUENCY_NORMAL, body.hyphenationFrequency)
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, body.breakStrategy)
        assertEquals("body text is the theme's pure black, not the platform's translucent black", Color.BLACK, body.currentTextColor)
    }

    @Test
    fun einkLineHeightIsTheRealBaselineDistance() {
        val body = openBody(DisplayMode.EINK)

        // Default line-spacing step → ratio 1.6; the view's own line height is exact, so it must
        // match within rounding rather than carry the font's natural height on top.
        val expected = 1.6f * body.textSize
        assertTrue("line height ${body.lineHeight} vs $expected", kotlin.math.abs(body.lineHeight - expected) <= 2f)
    }

    @Test
    fun standardBodyKeepsTheShippedSizeAndThePlatformWrappingDefaults() {
        val body = openBody(DisplayMode.STANDARD)
        // A plain TextView holds the platform's own defaults; views must be built on the main thread.
        val platform = PageViews.onMain { TextView(body.context) }

        assertEquals("Standard base size is unchanged at 16 sp × step scale", scaledPx(16f, body), body.textSize, 1f)
        assertEquals(platform.justificationMode, body.justificationMode)
        assertEquals(platform.hyphenationFrequency, body.hyphenationFrequency)
        assertEquals(platform.breakStrategy, body.breakStrategy)
    }
}
