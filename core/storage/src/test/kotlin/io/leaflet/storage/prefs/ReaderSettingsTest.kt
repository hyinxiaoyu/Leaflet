package io.leaflet.storage.prefs

import io.leaflet.model.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reader-settings defaults and coercion (REQ-0007 FR-1–FR-4, page overlap
 * 3%–8%, REQ-0005/REQ-0010 entries): corrupt/out-of-range values fall back
 * to frozen defaults, never crash.
 */
class ReaderSettingsTest {
    @Test
    fun defaultsAreFrozen() {
        assertEquals(2, ReaderSettings.FONT_SIZE_DEFAULT)
        assertEquals(1, ReaderSettings.LINE_SPACING_DEFAULT)
        assertEquals(1, ReaderSettings.PAGE_MARGIN_DEFAULT)
        assertEquals("system", ReaderSettings.FONT_FAMILY_DEFAULT)
        assertEquals(true, ReaderSettings.CODE_SOFT_WRAP_DEFAULT)
        assertEquals(0.05f, ReaderSettings.PAGE_OVERLAP_DEFAULT, 0.0f)
        assertEquals("PAGE_TURN", ReaderSettings.READING_MODE_DEFAULT)
    }

    @Test
    fun rangesHaveRequiredSteps() {
        // REQ-0007 FR-1: at least 5 font steps; FR-3/FR-4: at least 3 each.
        assertTrue(ReaderSettings.FONT_SIZE_MAX - ReaderSettings.FONT_SIZE_MIN + 1 >= 5)
        assertTrue(ReaderSettings.LINE_SPACING_MAX - ReaderSettings.LINE_SPACING_MIN + 1 >= 3)
        assertTrue(ReaderSettings.PAGE_MARGIN_MAX - ReaderSettings.PAGE_MARGIN_MIN + 1 >= 3)
    }

    @Test
    fun outOfRangeCoercesToBounds() {
        assertEquals(0, ReaderSettings.coerceFontSize(-99))
        assertEquals(4, ReaderSettings.coerceFontSize(99))
        assertEquals(0, ReaderSettings.coerceLineSpacing(-1))
        assertEquals(2, ReaderSettings.coercePageMargin(7))
        assertEquals(0.03f, ReaderSettings.coercePageOverlap(0.0f), 0.0f)
        assertEquals(0.08f, ReaderSettings.coercePageOverlap(1.0f), 0.0f)
        assertEquals(0.05f, ReaderSettings.coercePageOverlap(0.05f), 0.0f)
    }

    @Test
    fun blankFontFallsBackToSystem() {
        assertEquals("system", ReaderSettings.sanitizeFontFamily(""))
        assertEquals("system", ReaderSettings.sanitizeFontFamily("   "))
        assertEquals("serif", ReaderSettings.sanitizeFontFamily("serif"))
    }

    @Test
    fun unknownReadingModeFallsBackToPageTurns() {
        assertEquals("PAGE_TURN", ReaderSettings.sanitizeReadingMode("PAGED???"))
        assertEquals("PAGE_TURN", ReaderSettings.sanitizeReadingMode("PAGE_TURN"))
    }

    /**
     * CR-20260930-05 / REQ-0005 FR-4: with no stored choice, E-Ink installs
     * and Standard installs both start in page turns. An explicit stored mode is never
     * rewritten by a display-mode switch.
     */
    @Test
    fun bothDisplayModesDefaultToPageTurns() {
        assertEquals("PAGE_TURN", ReaderSettings.defaultReadingModeFor(DisplayMode.EINK))
        assertEquals("PAGE_TURN", ReaderSettings.defaultReadingModeFor(DisplayMode.STANDARD))
        assertEquals("PAGE_TURN", ReaderSettings.READING_MODE_DEFAULT)
    }
}
