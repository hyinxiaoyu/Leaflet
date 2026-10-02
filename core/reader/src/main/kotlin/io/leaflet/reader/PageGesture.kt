package io.leaflet.reader

import kotlin.math.abs

/** One release-triggered reader gesture; child consumption always cancels it. */
class PageGesture(
    private val mode: ReadingMode,
    private val widthPx: Int,
    private val startX: Float,
    private val touchSlopPx: Float,
    private val swipeThresholdPx: Float,
    private val longPressTimeoutMillis: Long,
) {
    private var cancelled = false
    private var finished = false
    var horizontalDrag: Boolean = false
        private set

    fun update(
        dx: Float,
        dy: Float,
        elapsedMillis: Long,
        released: Boolean,
        childConsumed: Boolean = false,
        singlePointer: Boolean = true,
        inBounds: Boolean = true,
    ): TurnIntent {
        if (finished) return TurnIntent.NONE
        // A drag that already established horizontal intent may be slow on an
        // E-Ink device. Only holding before that intent counts as a long press.
        if (!inBounds || childConsumed || !singlePointer || (!horizontalDrag && elapsedMillis >= longPressTimeoutMillis)) cancelled = true
        if (!cancelled && !horizontalDrag && dx * dx + dy * dy > touchSlopPx * touchSlopPx) {
            if (mode == ReadingMode.PAGE_TURN && abs(dx) > abs(dy)) {
                horizontalDrag = true
            } else {
                cancelled = true
            }
        }
        if (!released) return TurnIntent.NONE
        finished = true
        if (cancelled) return TurnIntent.NONE
        if (horizontalDrag) {
            if (abs(dx) < swipeThresholdPx || abs(dx) <= abs(dy)) return TurnIntent.NONE
            return if (dx < 0) TurnIntent.NEXT_PAGE else TurnIntent.PREV_PAGE
        }
        val intent = GestureArbitration.decide(ContentGesture.PLAIN_TAP, TapZones.zoneFor(startX, widthPx))
        return if (mode == ReadingMode.PAGE_TURN || intent == TurnIntent.TOGGLE_MENU) intent else TurnIntent.NONE
    }
}
