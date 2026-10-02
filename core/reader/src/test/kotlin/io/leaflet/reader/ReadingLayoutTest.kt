package io.leaflet.reader

import io.leaflet.eink.EinkDisplayProfile
import io.leaflet.eink.ReadingLayoutPolicy
import io.leaflet.eink.StandardDisplayProfile
import io.leaflet.markdown.ReaderStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-0007 typography resolution (JVM only). The Standard cases pin the numbers the
 * Reader computed inline before the policy existed; the E-Ink cases pin the policy
 * relationships. None of this measures a device.
 */
class ReadingLayoutTest {
    private val standard = StandardDisplayProfile().readingLayout
    private val eink = EinkDisplayProfile().readingLayout

    @Test
    fun standardResolutionEqualsTheLegacyInlineFormulas() {
        for (size in ReaderStyle.FONT_SIZE_MIN..ReaderStyle.FONT_SIZE_MAX) {
            for (spacing in ReaderStyle.LINE_SPACING_MIN..ReaderStyle.LINE_SPACING_MAX) {
                for (margin in ReaderStyle.PAGE_MARGIN_MIN..ReaderStyle.PAGE_MARGIN_MAX) {
                    val layout = ReadingLayoutResolver.resolve(ReaderStyle(size, "system", spacing, margin), standard)
                    val scale = ReaderStyle.fontScaleForStep(size)
                    val multiplier = ReaderStyle.lineSpacingMultiplierForStep(spacing)
                    assertEquals(16f * scale, layout.fontSizeSp, 1e-4f)
                    assertEquals(22f * scale * multiplier, layout.lineHeightSp, 1e-3f)
                    assertEquals(ReaderStyle.pageMarginDpForStep(margin), layout.pageMarginDp)
                }
            }
        }
        val any = ReadingLayoutResolver.resolve(ReaderStyle(), standard)
        assertNull(any.maxMeasureSp)
        assertEquals("Standard keeps the legacy additive line spacing", false, any.exactLineHeight)
        assertEquals(false, any.justify)
        assertEquals(false, any.hyphenate)
        assertEquals(4, any.blockPaddingDp)
    }

    @Test
    fun einkKeepsFiveDistinctSizesAndThreeDistinctSpacingsAndMargins() {
        val sizes = (0..4).map { ReadingLayoutResolver.resolve(ReaderStyle(fontSizeStep = it), eink).fontSizeSp }
        val spacings = (0..2).map { ReadingLayoutResolver.resolve(ReaderStyle(lineSpacingStep = it), eink).lineHeightSp }
        val margins = (0..2).map { ReadingLayoutResolver.resolve(ReaderStyle(pageMarginStep = it), eink).pageMarginDp }

        assertEquals(5, sizes.toSet().size)
        assertEquals(3, spacings.toSet().size)
        assertEquals(3, margins.toSet().size)
        assertTrue(sizes.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun lineHeightNeverFallsBelowTheFontSize() {
        for (policy in listOf(standard, eink)) {
            for (size in 0..4) {
                for (spacing in 0..2) {
                    val layout = ReadingLayoutResolver.resolve(ReaderStyle(fontSizeStep = size, lineSpacingStep = spacing), policy)
                    assertTrue(layout.lineHeightSp >= layout.fontSizeSp)
                }
            }
        }
    }

    @Test
    fun einkMeasureScalesWithTheFontSize() {
        val small = ReadingLayoutResolver.resolve(ReaderStyle(fontSizeStep = 0), eink)
        val large = ReadingLayoutResolver.resolve(ReaderStyle(fontSizeStep = 4), eink)

        assertNotNull(small.maxMeasureSp)
        assertEquals(36f * small.fontSizeSp, small.maxMeasureSp!!, 1e-3f)
        assertTrue("a larger font gets a proportionally wider measure", large.maxMeasureSp!! > small.maxMeasureSp!!)
        // The measure is in em, so characters per line stays constant across steps.
        assertEquals(small.maxMeasureSp!! / small.fontSizeSp, large.maxMeasureSp!! / large.fontSizeSp, 1e-3f)
    }

    @Test
    fun outOfBandStepsClampInsteadOfThrowing() {
        val layout = ReadingLayoutResolver.resolve(ReaderStyle(fontSizeStep = 99, lineSpacingStep = -3, pageMarginStep = 42), eink)

        assertEquals(eink.baseFontSizeSp * ReaderStyle.fontScaleForStep(ReaderStyle.FONT_SIZE_MAX), layout.fontSizeSp, 1e-4f)
        assertEquals(eink.pageMarginDp.last(), layout.pageMarginDp)
        assertEquals(layout.fontSizeSp * eink.lineHeightRatios.first(), layout.lineHeightSp, 1e-4f)
    }

    @Test
    fun resolverCarriesWrappingFlagsFromThePolicyOnly() {
        val layout = ReadingLayoutResolver.resolve(ReaderStyle(), ReadingLayoutPolicy.EINK)

        assertEquals(true, layout.justify)
        assertEquals(true, layout.hyphenate)
        assertEquals(true, layout.exactLineHeight)
        assertEquals(ReadingLayoutPolicy.EINK.blockPaddingDp, layout.blockPaddingDp)
    }
}
