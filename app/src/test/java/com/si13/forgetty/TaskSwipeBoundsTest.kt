package com.si13.forgetty

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSwipeBoundsTest {
    @Test
    fun largeLeftSwipeStopsAtDeleteActionWidth() {
        assertEquals(-72f, TaskSwipeBounds.swipeOffset(0f, -500f, 72f), 0f)
    }

    @Test
    fun rightSwipeCannotRevealPastClosedPosition() {
        assertEquals(0f, TaskSwipeBounds.swipeOffset(0f, 500f, 72f), 0f)
    }

    @Test
    fun rightSwipeClosesAnOpenRevealProgressively() {
        assertEquals(-32f, TaskSwipeBounds.swipeOffset(-72f, 40f, 72f), 0f)
        assertEquals(0f, TaskSwipeBounds.swipeOffset(-72f, 100f, 72f), 0f)
    }

    @Test
    fun overlayTranslationStaysWithinMeasuredActionWidth() {
        assertEquals(72f, TaskSwipeBounds.overlayTranslation(500f, 72f), 0f)
        assertEquals(40f, TaskSwipeBounds.overlayTranslation(-32f, 72f), 0f)
        assertEquals(0f, TaskSwipeBounds.overlayTranslation(-500f, 72f), 0f)
    }

    @Test
    fun swipeAtOpenThresholdSettlesAtExactActionWidth() {
        assertEquals(-72f, TaskSwipeBounds.settleTarget(-27.36f, 72f), 0.01f)
        assertEquals(-72f, TaskSwipeBounds.settleTarget(-60f, 72f), 0f)
    }

    @Test
    fun swipeBelowOpenThresholdSettlesClosed() {
        assertEquals(0f, TaskSwipeBounds.settleTarget(-27f, 72f), 0f)
        assertEquals(0f, TaskSwipeBounds.settleTarget(0f, 72f), 0f)
    }
}
