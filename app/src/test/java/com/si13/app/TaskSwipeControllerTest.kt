package com.si13.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSwipeControllerTest {
    @Test
    fun firstDeleteRequestUsesStableIdExactlyOnce() {
        val deletedTaskIds = mutableListOf<String>()
        val controller = TaskSwipeController { _, _ -> }
        controller.open("task-1")

        assertTrue(controller.requestDelete("task-1", deletedTaskIds::add))
        assertFalse(controller.requestDelete("task-1", deletedTaskIds::add))

        assertEquals(listOf("task-1"), deletedTaskIds)
        assertNull(controller.openTaskId)
    }

    @Test
    fun closingOpenActionDoesNotDelete() {
        val deletedTaskIds = mutableListOf<String>()
        val controller = TaskSwipeController { _, _ -> }
        controller.open("task-1")

        assertTrue(controller.close())

        assertTrue(deletedTaskIds.isEmpty())
        assertNull(controller.openTaskId)
    }

    @Test
    fun openingSecondTaskClosesFirstTask() {
        val transitions = mutableListOf<Pair<String?, String?>>()
        val controller = TaskSwipeController { previous, current ->
            transitions += previous to current
        }

        controller.open("task-a")
        controller.open("task-b")

        assertEquals("task-b", controller.openTaskId)
        assertEquals(listOf(null to "task-a", "task-a" to "task-b"), transitions)
    }

    @Test
    fun outsideScrollAndNavigationCloseThroughTheSameStateAction() {
        val controller = TaskSwipeController { _, _ -> }

        listOf("outside", "scroll", "navigation").forEach { taskId ->
            controller.open(taskId)
            assertTrue(controller.close())
            assertNull(controller.openTaskId)
        }
    }
}
