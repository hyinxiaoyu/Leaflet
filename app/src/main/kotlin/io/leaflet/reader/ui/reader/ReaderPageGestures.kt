package io.leaflet.reader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import io.leaflet.reader.PageGesture
import io.leaflet.reader.ReadingMode
import io.leaflet.reader.TurnIntent

/** Observe after child handlers, and never move content while a finger is down. */
internal suspend fun PointerInputScope.detectReaderPageGestures(
    mode: ReadingMode,
    swipeThresholdPx: Float,
    onIntent: (TurnIntent) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val gesture =
            PageGesture(
                mode = mode,
                widthPx = size.width,
                startX = down.position.x,
                touchSlopPx = viewConfiguration.touchSlop,
                swipeThresholdPx = maxOf(swipeThresholdPx, viewConfiguration.touchSlop * 2),
                longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
            )
        var childConsumed = down.isConsumed
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            childConsumed = childConsumed || event.changes.any { it.isConsumed }
            val displacement = change.position - down.position
            val intent =
                gesture.update(
                    dx = displacement.x,
                    dy = displacement.y,
                    elapsedMillis = change.uptimeMillis - down.uptimeMillis,
                    released = !change.pressed,
                    childConsumed = childConsumed,
                    singlePointer = event.changes.size == 1,
                    inBounds =
                        change.position.x >= 0 && change.position.x < size.width &&
                            change.position.y >= 0 && change.position.y < size.height,
                )
            if (gesture.horizontalDrag && !childConsumed) change.consume()
            if (!change.pressed) {
                if (intent != TurnIntent.NONE) {
                    change.consume()
                    onIntent(intent)
                }
                break
            }
        }
    }
}
