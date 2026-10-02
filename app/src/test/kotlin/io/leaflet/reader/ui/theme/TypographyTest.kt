package io.leaflet.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chrome-typography presets (JVM only). These pin policy numbers, not what a panel shows:
 * stroke legibility on real E-Ink stays unverified until the device matrix is run.
 */
class TypographyTest {
    @Test
    fun standardKeepsTheMaterialScale() {
        assertEquals(Typography(), leafletTypography(highContrast = false))
    }

    @Test
    fun highContrastLiftsSmallStylesAndKeepsTheirStrokes() {
        val t = leafletTypography(highContrast = true)

        for (small in listOf(t.bodySmall, t.labelSmall, t.labelMedium)) {
            assertTrue("small chrome text starts at 14 sp", small.fontSize.value >= 14f)
        }
        assertTrue(t.bodyMedium.fontSize.value >= 16f)
        assertTrue(t.bodyLarge.fontSize.value >= 18f)
        // Thin regular strokes are the first thing a dithered panel drops.
        assertTrue((t.bodySmall.fontWeight?.weight ?: 0) >= FontWeight.Medium.weight)
        assertTrue((t.labelSmall.fontWeight?.weight ?: 0) >= FontWeight.Medium.weight)
    }

    @Test
    fun everyHighContrastStyleKeepsLineHeightAboveItsSize() {
        val t = leafletTypography(highContrast = true)

        for (style in listOf(t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall)) {
            assertTrue(style.lineHeight.value > style.fontSize.value)
        }
    }
}
