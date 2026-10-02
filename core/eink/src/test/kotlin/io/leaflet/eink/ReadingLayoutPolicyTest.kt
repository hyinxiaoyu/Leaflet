package io.leaflet.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the reading-layout presets (REQ-0007) and the progress cadence
 * (REQ-0004 FR-4) against silent drift. JVM only: these are policy numbers, not
 * device measurements, and say nothing about physical legibility or ghosting.
 */
class ReadingLayoutPolicyTest {
    @Test
    fun standardPresetKeepsTheShippedNumbers() {
        val policy = StandardDisplayProfile().readingLayout

        assertEquals(16f, policy.baseFontSizeSp, 0f)
        assertEquals(listOf(8, 16, 24), policy.pageMarginDp)
        // 22 / 16 × {1.00, 1.25, 1.50}: the line heights Standard has always used.
        assertEquals(22f / 16f, policy.lineHeightRatioForStep(0), 0f)
        assertEquals(22f / 16f * 1.25f, policy.lineHeightRatioForStep(1), 1e-6f)
        assertEquals(22f / 16f * 1.5f, policy.lineHeightRatioForStep(2), 1e-6f)
        assertNull(policy.maxMeasureEm)
        assertFalse(policy.exactLineHeight)
        assertFalse(policy.justifyText)
        assertFalse(policy.hyphenate)
        assertEquals(4, policy.blockPaddingDp)
    }

    @Test
    fun einkPresetTunesTypographyForThePanel() {
        val eink = EinkDisplayProfile().readingLayout
        val standard = StandardDisplayProfile().readingLayout

        assertTrue("E-Ink body type starts larger", eink.baseFontSizeSp > standard.baseFontSizeSp)
        for (step in 0 until ReadingLayoutPolicy.STEP_COUNT) {
            val tighter = eink.lineHeightRatioForStep(step) < standard.lineHeightRatioForStep(step)
            val wider = eink.pageMarginDpForStep(step) > standard.pageMarginDpForStep(step)
            assertTrue("E-Ink line height at step $step stays tighter", tighter)
            assertTrue("E-Ink margin at step $step stays wider", wider)
        }
        assertTrue(eink.lineHeightRatios.zipWithNext().all { (a, b) -> a < b })
        assertTrue(eink.pageMarginDp.zipWithNext().all { (a, b) -> a < b })
        assertEquals(36f, eink.maxMeasureEm!!, 0f)
        assertTrue("E-Ink ratios are real baseline distances", eink.exactLineHeight)
        assertTrue(eink.justifyText)
        assertTrue("justification needs hyphenation to avoid rivers", eink.hyphenate)
    }

    @Test
    fun presetsAreWiredThroughTheSingleProfileSeam() {
        assertEquals(ReadingLayoutPolicy.EINK, profileFor(DisplayModeId.EINK).readingLayout)
        assertEquals(ReadingLayoutPolicy.STANDARD, profileFor(DisplayModeId.STANDARD).readingLayout)
        assertEquals(ReadingLayoutPolicy.STANDARD, profileForName("bogus").readingLayout)
    }

    @Test
    fun stepLookupsClampInsteadOfThrowing() {
        val policy = ReadingLayoutPolicy.EINK

        assertEquals(policy.pageMarginDp.first(), policy.pageMarginDpForStep(-4))
        assertEquals(policy.pageMarginDp.last(), policy.pageMarginDpForStep(99))
        assertEquals(policy.lineHeightRatios.first(), policy.lineHeightRatioForStep(-1), 0f)
        assertEquals(policy.lineHeightRatios.last(), policy.lineHeightRatioForStep(3), 0f)
    }

    @Test
    fun malformedPresetsAreRejected() {
        val ok = ReadingLayoutPolicy.STANDARD

        assertRejected { ok.copy(baseFontSizeSp = 0f) }
        assertRejected { ok.copy(lineHeightRatios = listOf(1.4f, 1.6f)) }
        assertRejected { ok.copy(lineHeightRatios = listOf(0.9f, 1.4f, 1.6f)) }
        assertRejected { ok.copy(pageMarginDp = listOf(8, 16)) }
        assertRejected { ok.copy(pageMarginDp = listOf(-1, 16, 24)) }
        assertRejected { ok.copy(maxMeasureEm = 10f) }
        assertRejected { ok.copy(blockPaddingDp = -1) }
    }

    @Test
    fun progressIsThrottledOnEinkOnly() {
        assertEquals(0L, RefreshPolicy.SYSTEM_DEFAULT.progressMinIntervalMs)
        assertEquals(1_000L, RefreshPolicy.CONSERVATIVE_FULL_REDRAW.progressMinIntervalMs)
        assertEquals(1_000L, EinkDisplayProfile().refreshPolicy.progressMinIntervalMs)
        assertEquals(0L, StandardDisplayProfile().refreshPolicy.progressMinIntervalMs)
    }

    @Test
    fun progressDelayIsLeadingEdgeThenCoalesced() {
        // First value, an elapsed interval, and a disabled throttle all show immediately.
        assertEquals(0L, progressUpdateDelayMs(nowMs = 5_000, lastShownMs = null, minIntervalMs = 1_000))
        assertEquals(0L, progressUpdateDelayMs(nowMs = 6_000, lastShownMs = 5_000, minIntervalMs = 1_000))
        assertEquals(0L, progressUpdateDelayMs(nowMs = 5_001, lastShownMs = 5_000, minIntervalMs = 0))
        // Inside the interval the update waits for the remainder only.
        assertEquals(600L, progressUpdateDelayMs(nowMs = 5_400, lastShownMs = 5_000, minIntervalMs = 1_000))
        // A clock that jumped backwards never waits longer than one interval.
        assertEquals(1_000L, progressUpdateDelayMs(nowMs = 1_000, lastShownMs = 5_000, minIntervalMs = 1_000))
    }

    private fun assertRejected(build: () -> ReadingLayoutPolicy) {
        try {
            build()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected the preset to be rejected")
    }
}
