package io.leaflet.eink

import androidx.annotation.ColorInt

/**
 * Runtime display strategy (init §3, REQ-0004 FR-1): how motion, scrolling,
 * contrast and refresh behave. Orthogonal to the `full`/`eink` build flavor.
 *
 * UI and Reader MUST read these fields (via [MotionPolicy]) instead of
 * scattering `if (isEink)` branches.
 */
interface DisplayProfile {
    val animationsEnabled: Boolean
    val smoothScrollingEnabled: Boolean
    val rippleEnabled: Boolean
    val highContrastEnabled: Boolean

    @get:ColorInt val defaultBackground: Int

    @get:ColorInt val defaultForeground: Int
    val scrollBehavior: ScrollBehavior
    val pageTurnBehavior: PageTurnBehavior
    val refreshPolicy: RefreshPolicy
    val readerDecorations: ReaderDecorations

    /** Typography and page-layout numbers for the shared Reader (REQ-0007); added after REQ-0004 FR-1's original ten. */
    val readingLayout: ReadingLayoutPolicy
}

/** Runtime mode identifier, mirroring `:core:model` DisplayMode without a model dependency. */
enum class DisplayModeId { STANDARD, EINK }

/**
 * Standard Android preset: Material 3, light ripple, normal scrolling.
 * Still no complex transitions/shared-element/crossfade in M0/M1 (REQ-0004 §Standard).
 */
data class StandardDisplayProfile(
    override val animationsEnabled: Boolean = true,
    override val smoothScrollingEnabled: Boolean = true,
    override val rippleEnabled: Boolean = true,
    override val highContrastEnabled: Boolean = false,
    @ColorInt override val defaultBackground: Int = 0xFFFFFFFF.toInt(),
    @ColorInt override val defaultForeground: Int = 0xFF1A1C1E.toInt(),
    override val scrollBehavior: ScrollBehavior = ScrollBehavior.CONTINUOUS_SCROLL,
    override val pageTurnBehavior: PageTurnBehavior = PageTurnBehavior(),
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.SYSTEM_DEFAULT,
    override val readerDecorations: ReaderDecorations =
        ReaderDecorations(minimalStaticChrome = false, instantMenuVisibility = false),
    override val readingLayout: ReadingLayoutPolicy = ReadingLayoutPolicy.STANDARD,
) : DisplayProfile

/**
 * E-Ink preset (REQ-0004 FR-4 forced off-list): animations, smooth scroll,
 * overscroll effects, ripple, crossfade, transparency/shadow/gradient/blur and
 * infinite loading animations are all OFF. Solid colors, high contrast, static
 * text + percentage loading states, throttled progress updates. Reading layout:
 * larger type, tighter line spacing, wider margins, a bounded measure, justified
 * and hyphenated text ([ReadingLayoutPolicy.EINK]).
 */
data class EinkDisplayProfile(
    override val animationsEnabled: Boolean = false,
    override val smoothScrollingEnabled: Boolean = false,
    override val rippleEnabled: Boolean = false,
    override val highContrastEnabled: Boolean = true,
    @ColorInt override val defaultBackground: Int = 0xFFFFFFFF.toInt(),
    @ColorInt override val defaultForeground: Int = 0xFF000000.toInt(),
    override val scrollBehavior: ScrollBehavior = ScrollBehavior.PAGE_TURN,
    override val pageTurnBehavior: PageTurnBehavior = PageTurnBehavior(),
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.CONSERVATIVE_FULL_REDRAW,
    override val readerDecorations: ReaderDecorations =
        ReaderDecorations(minimalStaticChrome = true, instantMenuVisibility = true),
    override val readingLayout: ReadingLayoutPolicy = ReadingLayoutPolicy.EINK,
) : DisplayProfile

/**
 * Single construction seam for a runtime mode (T12): every caller that needs
 * a [DisplayProfile] from a mode goes through here, so the presets cannot
 * drift apart across Theme/Reader/Settings wiring.
 */
fun profileFor(mode: DisplayModeId): DisplayProfile =
    when (mode) {
        DisplayModeId.STANDARD -> StandardDisplayProfile()
        DisplayModeId.EINK -> EinkDisplayProfile()
    }

/** Name-based seam ("STANDARD"/"EINK"); unknown names fall back to Standard. */
fun profileForName(modeName: String): DisplayProfile =
    profileFor(
        when (modeName) {
            DisplayModeId.EINK.name -> DisplayModeId.EINK
            else -> DisplayModeId.STANDARD
        },
    )
