package io.leaflet.reader.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.leaflet.eink.DisplayModeId
import io.leaflet.eink.DisplayProfile
import io.leaflet.eink.EinkDisplayProfile
import io.leaflet.eink.MotionPolicy
import io.leaflet.eink.profileFor
import io.leaflet.model.DisplayMode

/**
 * The resolved [MotionPolicy] for the display mode this theme was built with.
 *
 * Screens read this instead of re-deriving the mode → profile mapping, so every
 * animation/transition decision (REQ-0004 FR-2: "all animation, transition,
 * crossfade ... decisions go through MotionPolicy") has one source. The default
 * is the most conservative policy (animations off), so a screen composed outside
 * [LeafletTheme] — a preview or a test — never animates.
 */
val LocalMotionPolicy =
    staticCompositionLocalOf {
        MotionPolicy(animationsAllowed = false)
    }

/**
 * The resolved [DisplayProfile] for the display mode this theme was built with, for screens
 * that need a visual-policy decision other than motion (high contrast, for example) without
 * re-deriving the mode → profile mapping. Like [LocalMotionPolicy], the default is the most
 * conservative preset, so a screen composed outside [LeafletTheme] never shows a scrim,
 * translucency or animation.
 */
val LocalDisplayProfile =
    staticCompositionLocalOf<DisplayProfile> {
        EinkDisplayProfile()
    }

/**
 * T12 display-strategy theme (REQ-0004 FR-1/FR-2/FR-4, REQ-0010 FR-1/FR-9).
 *
 * Single place where the runtime [DisplayMode] becomes a [DisplayProfile]
 * (via `:core:eink` [profileFor]) and a [MotionPolicy]. All screens below
 * read motion/ripple/overscroll decisions from these two objects — never
 * from a local `if (mode == ...)` motion branch.
 *
 * EINK: solid white/black, high contrast, no translucent surfaces; ripple
 * indication OFF (LocalIndication provides null) and the system overscroll
 * glow disabled (transparent glow). STANDARD keeps platform defaults.
 *
 * No crossfade, shared-element, or animated transition exists on any path
 * (navigation uses instant swaps; REQ-0004 §Standard).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeafletTheme(
    displayMode: DisplayMode,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val modeId =
        when (displayMode) {
            DisplayMode.STANDARD -> DisplayModeId.STANDARD
            DisplayMode.EINK -> DisplayModeId.EINK
        }
    val profile: DisplayProfile = remember(modeId) { profileFor(modeId) }
    val motion: MotionPolicy = remember(profile) { MotionPolicy.from(profile) }
    val scheme = leafletColorScheme(displayMode, darkTheme)
    // MotionPolicy is the single gate (FR-2): the EINK branch provides the
    // static configuration; the STANDARD branch leaves platform defaults
    // untouched so behavior there is byte-identical to stock Compose.
    val defaultOverscroll: OverscrollConfiguration? = LocalOverscrollConfiguration.current
    val einkOverscroll: OverscrollConfiguration =
        remember(defaultOverscroll) {
            val padding = defaultOverscroll?.drawPadding
            if (padding != null) {
                OverscrollConfiguration(
                    glowColor = Color.Transparent,
                    drawPadding = padding,
                )
            } else {
                OverscrollConfiguration(glowColor = Color.Transparent)
            }
        }
    val typography = remember(profile) { leafletTypography(profile.highContrastEnabled) }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // The resolved policy is published for screens that own a transition or
        // animation (NavHost, for example) instead of re-deriving the mode.
        CompositionLocalProvider(LocalMotionPolicy provides motion, LocalDisplayProfile provides profile) {
            if (motion.animationsAllowed) {
                content()
            } else {
                // LocalIndication is non-null typed in foundation 1.7.5: cast to
                // the nullable form so EINK provides null (clickables then skip
                // the indication node — verified in ClickableKt bytecode).
                @Suppress("UNCHECKED_CAST")
                val noIndication = LocalIndication as androidx.compose.runtime.ProvidableCompositionLocal<Indication?>
                CompositionLocalProvider(
                    noIndication provides null,
                    LocalOverscrollConfiguration provides einkOverscroll,
                    content = content,
                )
            }
        }
    }
}

/** Complete grayscale tokens keep every Material surface/control legible on E-Ink. */
internal fun leafletColorScheme(
    displayMode: DisplayMode,
    darkTheme: Boolean,
): androidx.compose.material3.ColorScheme =
    when {
        displayMode == DisplayMode.EINK ->
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEEEEEE),
                onPrimaryContainer = Color.Black,
                inversePrimary = Color.White,
                secondary = Color(0xFF444444),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFEEEEEE),
                onSecondaryContainer = Color.Black,
                tertiary = Color(0xFF444444),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFEEEEEE),
                onTertiaryContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFF2F2F2),
                onSurfaceVariant = Color.Black,
                surfaceTint = Color.Black,
                inverseSurface = Color.Black,
                inverseOnSurface = Color.White,
                error = Color.Black,
                onError = Color.White,
                errorContainer = Color(0xFFEEEEEE),
                onErrorContainer = Color.Black,
                outline = Color.Black,
                // Dividers and card borders are 1 dp lines: a mid-gray that thin dithers away on
                // an E-Ink panel, so the variant outline stays a dark gray.
                outlineVariant = Color(0xFF666666),
                scrim = Color.Black,
                surfaceBright = Color.White,
                surfaceDim = Color(0xFFEEEEEE),
                surfaceContainer = Color.White,
                surfaceContainerLow = Color.White,
                surfaceContainerLowest = Color.White,
                surfaceContainerHigh = Color(0xFFF2F2F2),
                surfaceContainerHighest = Color(0xFFEEEEEE),
            )
        darkTheme -> darkColorScheme()
        else ->
            lightColorScheme(
                primary = Color(0xFF315C46),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE5EFE7),
                onPrimaryContainer = Color(0xFF183624),
                secondary = Color(0xFF526458),
                background = Color(0xFFF8F9F5),
                surface = Color(0xFFFCFDF9),
                onSurface = Color(0xFF202720),
                outline = Color(0xFF6E7A70),
                outlineVariant = Color(0xFFD0D8CE),
            )
    }
