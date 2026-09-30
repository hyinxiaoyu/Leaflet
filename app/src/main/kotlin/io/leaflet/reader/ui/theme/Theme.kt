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
import androidx.compose.ui.graphics.Color
import io.leaflet.eink.DisplayModeId
import io.leaflet.eink.DisplayProfile
import io.leaflet.eink.MotionPolicy
import io.leaflet.eink.profileFor
import io.leaflet.model.DisplayMode

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
    val scheme =
        when {
            displayMode == DisplayMode.EINK ->
                lightColorScheme(
                    primary = Color.Black,
                    onPrimary = Color.White,
                    background = Color.White,
                    onBackground = Color.Black,
                    surface = Color.White,
                    onSurface = Color.Black,
                )
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
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
    MaterialTheme(colorScheme = scheme) {
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
