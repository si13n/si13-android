package com.si13.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSwipeBoundsTest {
    @Test
    fun largeLeftSwipeStopsAtDeleteActionWidth() {
        assertEquals(-72f, TaskSwipeBounds.translation(0f, -500f, 72f), 0f)
    }

    @Test
    fun rightSwipeNeverMovesForegroundPastClosedPosition() {
        assertEquals(0f, TaskSwipeBounds.translation(0f, 500f, 72f), 0f)
    }

    @Test
    fun rightSwipeClosesAnOpenForegroundProgressively() {
        assertEquals(-32f, TaskSwipeBounds.translation(-72f, 40f, 72f), 0f)
        assertEquals(0f, TaskSwipeBounds.translation(-72f, 100f, 72f), 0f)
    }
}
