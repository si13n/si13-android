package com.si13.forgetty

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSorterTest {
    private val tasks = listOf(
        task("b", "Bravo", createdAt = 20L),
        task("a", "alpha", createdAt = 10L),
        task("p", "Priority", createdAt = 5L, priority = TaskPriority.HIGH)
    )

    @Test
    fun newestFirstUsesCreatedDateDescending() {
        assertEquals(
            listOf("b", "a", "p"),
            TaskSorter.sort(tasks, TaskSortMode.NEWEST_FIRST).map(Task::id)
        )
    }

    @Test
    fun oldestFirstUsesCreatedDateAscending() {
        assertEquals(
            listOf("p", "a", "b"),
            TaskSorter.sort(tasks, TaskSortMode.OLDEST_FIRST).map(Task::id)
        )
    }

    @Test
    fun priorityFirstKeepsPriorityAheadThenUsesNewestDate() {
        assertEquals(
            listOf("p", "b", "a"),
            TaskSorter.sort(tasks, TaskSortMode.PRIORITY_FIRST).map(Task::id)
        )
    }

    @Test
    fun alphabeticalUsesTaskTitle() {
        assertEquals(
            listOf("a", "b", "p"),
            TaskSorter.sort(tasks, TaskSortMode.ALPHABETICAL).map(Task::id)
        )
    }

    private fun task(
        id: String,
        text: String,
        createdAt: Long,
        priority: TaskPriority = TaskPriority.NONE
    ) = Task(
        id = id,
        text = text,
        completed = false,
        createdAt = createdAt,
        updatedAt = createdAt,
        priority = priority
    )
}
