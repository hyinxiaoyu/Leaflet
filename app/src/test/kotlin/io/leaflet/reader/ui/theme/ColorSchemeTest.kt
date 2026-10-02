package io.leaflet.reader.ui.theme

import androidx.compose.ui.graphics.Color
import io.leaflet.model.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSchemeTest {
    @Test
    fun einkKeepsEverySurfaceAndAccentGrayscaleEvenInSystemDarkMode() {
        val scheme = leafletColorScheme(DisplayMode.EINK, darkTheme = true)
        val colors =
            listOf(
                scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer, scheme.inversePrimary,
                scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer,
                scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer,
                scheme.background, scheme.onBackground, scheme.surface, scheme.onSurface, scheme.surfaceVariant, scheme.onSurfaceVariant,
                scheme.surfaceTint, scheme.inverseSurface, scheme.inverseOnSurface, scheme.error, scheme.onError,
                scheme.errorContainer, scheme.onErrorContainer, scheme.outline, scheme.outlineVariant, scheme.scrim,
                scheme.surfaceBright, scheme.surfaceDim, scheme.surfaceContainer, scheme.surfaceContainerLow,
                scheme.surfaceContainerLowest, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
            )
        for (color in colors) {
            assertEquals(color.red, color.green, 0.0001f)
            assertEquals(color.green, color.blue, 0.0001f)
            assertEquals(1f, color.alpha, 0.0001f)
        }
        assertEquals(Color.White, scheme.background)
        assertEquals(Color.Black, scheme.onSurface)
    }
}
