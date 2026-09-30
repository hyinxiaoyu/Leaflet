package io.leaflet.eink

/** Continuous reading vs. discrete page turns (REQ-0005 behavior, REQ-0010 entry). */
enum class ScrollBehavior {
    CONTINUOUS_SCROLL,
    PAGE_TURN,
}

/**
 * Viewport page-turn geometry: turn by `viewportHeight - overlap`.
 * Overlap defaults to 5% of the viewport; DataStore-tunable within 3%–8% (T09).
 */
data class PageTurnBehavior(
    /** Visible fraction kept as context across a turn, 0.03..0.08. */
    val overlapFraction: Float = 0.05f,
) {
    init {
        require(overlapFraction in 0.03f..0.08f) {
            "overlapFraction must be within 3%..8%, was $overlapFraction"
        }
    }

    /** Pixels to scroll for one forward/backward turn given the visible height. */
    fun turnDistancePx(viewportHeightPx: Int): Int = (viewportHeightPx * (1f - overlapFraction)).toInt().coerceAtLeast(0)
}

/** System-level redraw rhythm hints. Generic only — never hardware waveforms. */
enum class RefreshPolicy {
    /** Normal Android invalidation. */
    SYSTEM_DEFAULT,

    /** Few full redraws, coarse progress updates; E-Ink friendly. */
    CONSERVATIVE_FULL_REDRAW,
}

/**
 * Reader chrome policy: maximize reading area, static menus, no animated chrome.
 */
data class ReaderDecorations(
    /** Show only a thin static progress indicator (no animated bars/clocks). */
    val minimalStaticChrome: Boolean,
    /** Menus appear/disappear instantly on tap, no transition. */
    val instantMenuVisibility: Boolean,
)
