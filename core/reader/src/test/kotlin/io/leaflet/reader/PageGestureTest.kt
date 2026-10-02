package io.leaflet.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGestureTest {
    private fun gesture(
        x: Float = 800f,
        mode: ReadingMode = ReadingMode.PAGE_TURN,
    ) = PageGesture(mode, 900, x, 8f, 48f, 500)

    @Test
    fun tapZonesIncludeMarginsAndContinuousOnlyOpensMenu() {
        assertEquals(TurnIntent.PREV_PAGE, gesture(1f).update(0f, 0f, 50, true))
        assertEquals(TurnIntent.NEXT_PAGE, gesture(899f).update(0f, 0f, 50, true))
        assertEquals(TurnIntent.TOGGLE_MENU, gesture(450f).update(0f, 0f, 50, true))
        assertEquals(TurnIntent.NONE, gesture(mode = ReadingMode.CONTINUOUS_SCROLL).update(0f, 0f, 50, true))
        assertEquals(
            TurnIntent.TOGGLE_MENU,
            gesture(450f, ReadingMode.CONTINUOUS_SCROLL).update(0f, 0f, 50, true),
        )
    }

    @Test
    fun leftSwipeTurnsOnceOnReleaseAndRightSwipeGoesBack() {
        val left = gesture()
        assertEquals(TurnIntent.NONE, left.update(-100f, 3f, 100, false))
        assertEquals(TurnIntent.NONE, left.update(-200f, 3f, 200, false))
        assertEquals(TurnIntent.NEXT_PAGE, left.update(-200f, 3f, 250, true))
        assertEquals(TurnIntent.NONE, left.update(-200f, 3f, 260, true))
        assertEquals(TurnIntent.PREV_PAGE, gesture().update(100f, 3f, 250, true))
    }

    @Test
    fun shortDragDoesNotBecomeATapOrTurn() {
        assertEquals(TurnIntent.NONE, gesture().update(-47f, 0f, 100, true))
        assertEquals(TurnIntent.NEXT_PAGE, gesture().update(-48f, 0f, 100, true))
    }

    @Test
    fun verticalIntentStaysCancelledEvenIfItLaterMovesSideways() {
        val gesture = gesture()
        assertEquals(TurnIntent.NONE, gesture.update(2f, 20f, 50, false))
        assertEquals(TurnIntent.NONE, gesture.update(-200f, 20f, 100, true))
        assertEquals(TurnIntent.NONE, gesture().update(-100f, 100f, 100, true))
    }

    @Test
    fun horizontalStartWithVerticalFinishDoesNotTurn() {
        val gesture = gesture()
        gesture.update(-20f, 2f, 50, false)
        assertEquals(TurnIntent.NONE, gesture.update(-60f, 100f, 150, true))
    }

    @Test
    fun childConsumptionCancelsTapsAndSwipesPermanently() {
        assertEquals(TurnIntent.NONE, gesture().update(0f, 0f, 50, true, childConsumed = true))
        val gesture = gesture()
        gesture.update(-20f, 0f, 50, false, childConsumed = true)
        assertEquals(TurnIntent.NONE, gesture.update(-100f, 0f, 100, true))
    }

    @Test
    fun longPressNeverTurnsEvenAfterMoving() {
        assertEquals(TurnIntent.NONE, gesture().update(0f, 0f, 500, true))
        val gesture = gesture()
        gesture.update(0f, 0f, 500, false)
        assertEquals(TurnIntent.NONE, gesture.update(-100f, 0f, 550, true))
        val slowSwipe = gesture()
        slowSwipe.update(-20f, 0f, 100, false)
        assertEquals("an established drag is not a long press", TurnIntent.NEXT_PAGE, slowSwipe.update(-100f, 0f, 800, true))
    }

    @Test
    fun extraPointerPermanentlyCancels() {
        val gesture = gesture()
        gesture.update(-20f, 0f, 50, false, singlePointer = false)
        assertEquals(TurnIntent.NONE, gesture.update(-100f, 0f, 100, true))
    }

    @Test
    fun continuousHorizontalSwipeNeverTurns() {
        assertEquals(
            TurnIntent.NONE,
            gesture(mode = ReadingMode.CONTINUOUS_SCROLL).update(-100f, 0f, 100, true),
        )
    }

    @Test
    fun reversedDragEndingNearStartDoesNotOpenMenu() {
        val gesture = gesture(450f)
        gesture.update(-100f, 0f, 50, false)
        assertEquals(TurnIntent.NONE, gesture.update(0f, 0f, 100, true))
    }

    @Test
    fun releaseOutsideNeverTurnsOrOpensMenu() {
        assertEquals(TurnIntent.NONE, gesture().update(0f, 0f, 100, true, inBounds = false))
        assertEquals(TurnIntent.NONE, gesture(450f).update(0f, 0f, 100, true, inBounds = false))
    }

    @Test
    fun leavingAndReenteringPermanentlyCancelsTheSwipe() {
        val gesture = gesture()
        gesture.update(-100f, 0f, 100, false)
        gesture.update(-200f, 0f, 200, false, inBounds = false)
        assertEquals(TurnIntent.NONE, gesture.update(-100f, 0f, 300, true, inBounds = true))
    }

    @Test
    fun leavingAndReenteringCannotFallBackToATap() {
        val gesture = gesture(450f)
        gesture.update(0f, 0f, 100, false, inBounds = false)
        assertEquals(TurnIntent.NONE, gesture.update(0f, 0f, 200, true))
    }
}
