package io.leaflet.reader

import io.leaflet.eink.PageTurnBehavior

/**
 * Viewport page-turn geometry (T08, REQ-0005 FR-1/FR-2/FR-7).
 *
 * Pure JVM-testable heading: the actual scroll is executed by the host
 * (`LazyListState.scroll { scrollBy(...) }` / `scrollToItem` — instant, never
 * smooth/animated per the E-Ink rule). The distance formula reuses
 * [PageTurnBehavior] (single source); this object adds the visible-height
 * accounting (Insets/menu occlusion) and the head/tail clamp so rapid
 * repeated turns can never run past the document ends.
 *
 * All heights are pixels; overlap is a fraction coerced to 3%..8%
 * (default 5%, shared with `SettingsStore.pageOverlap`).
 */
object PageTurn {
    /** Default overlap fraction (5% of the viewport). */
    const val OVERLAP_DEFAULT: Float = 0.05f

    /** Minimum overlap fraction (3%). */
    const val OVERLAP_MIN: Float = 0.03f

    /** Maximum overlap fraction (8%). */
    const val OVERLAP_MAX: Float = 0.08f

    /** Coerce an overlap fraction into the allowed 3%..8% band. */
    fun coerceOverlap(fraction: Float): Float = fraction.coerceIn(OVERLAP_MIN, OVERLAP_MAX)

    /**
     * Pixels kept as context across one turn (`5% x viewport` by default).
     * Always >= 0; a zero/negative viewport yields 0 (no turn).
     */
    fun overlapPx(
        viewportHeightPx: Int,
        overlapFraction: Float,
    ): Int {
        if (viewportHeightPx <= 0) return 0
        return (viewportHeightPx * coerceOverlap(overlapFraction)).toInt().coerceAtLeast(0)
    }

    /**
     * Pixels to move for one forward/backward turn: `viewport - overlap`.
     * Delegates to [PageTurnBehavior] so the formula has a single source.
     */
    fun turnDistancePx(
        viewportHeightPx: Int,
        overlapFraction: Float,
    ): Int {
        if (viewportHeightPx <= 0) return 0
        return PageTurnBehavior(coerceOverlap(overlapFraction)).turnDistancePx(viewportHeightPx)
    }

    /**
     * Reader visible height: total host height minus top/bottom occlusion
     * (system Insets, expanded menu, static progress chrome). Occlusion is
     * clamped so the result never goes below [minHeightPx].
     */
    fun visibleHeightPx(
        totalHeightPx: Int,
        topObscuredPx: Int = 0,
        bottomObscuredPx: Int = 0,
        minHeightPx: Int = 1,
    ): Int {
        val visible = totalHeightPx - topObscuredPx.coerceAtLeast(0) - bottomObscuredPx.coerceAtLeast(0)
        return visible.coerceAtLeast(minHeightPx)
    }

    /** Clamp a pixel offset into `[0, maxOffsetPx]`. Negative max clamps to 0. */
    fun clampOffset(
        offsetPx: Int,
        maxOffsetPx: Int,
    ): Int {
        if (maxOffsetPx <= 0) return 0
        return offsetPx.coerceIn(0, maxOffsetPx)
    }

    /** Offset after one forward turn, clamped to the tail (never overshoots). */
    fun nextOffset(
        currentOffsetPx: Int,
        distancePx: Int,
        maxOffsetPx: Int,
    ): Int = clampOffset(currentOffsetPx + distancePx.coerceAtLeast(0), maxOffsetPx)

    /** Offset after one backward turn, clamped to the head (never negative). */
    fun prevOffset(
        currentOffsetPx: Int,
        distancePx: Int,
    ): Int = (currentOffsetPx - distancePx.coerceAtLeast(0)).coerceAtLeast(0)

    /** True when already at the head: a backward turn is a no-op. */
    fun isAtTop(offsetPx: Int): Boolean = offsetPx <= 0

    /** True when already at the tail: a forward turn is a no-op. */
    fun isAtBottom(
        offsetPx: Int,
        maxOffsetPx: Int,
    ): Boolean = offsetPx >= maxOffsetPx.coerceAtLeast(0)
}
