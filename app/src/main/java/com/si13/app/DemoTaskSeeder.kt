package com.si13.app

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Creates a broad, deterministic-enough dataset for exercising the task UI in debug builds. */
object DemoTaskSeeder {
    suspend fun seedIfEmpty(repository: TaskRepository): Boolean {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val lists = listOf("Personal", "Work", "Shared", "Shopping")
        val tags = listOf("planning", "QA", "health", "travel", "finance", "home", "learning")
        val tasks = (0 until 100).map { index ->
            val completed = index % 9 == 0 || index % 17 == 0
            val dueDate = when (index % 8) {
                0 -> today.minusDays((index % 5 + 1).toLong())
                1, 2 -> today
                3 -> today.plusDays(1)
                4 -> today.plusDays(2)
                5 -> today.plusDays(7)
                else -> null
            }
            val createdAt = now - (index + 1L) * 86_400_000L
            val completedAt = if (completed) now - (index % 7).toLong() * 86_400_000L else null
            Task(
                id = "demo-${UUID.randomUUID()}",
                text = demoTitle(index),
                completed = completed,
                createdAt = createdAt,
                updatedAt = completedAt ?: createdAt,
                priority = if (index % 5 == 0) TaskPriority.HIGH else TaskPriority.NONE,
                dueDate = dueDate?.toString(),
                dueTimeMinutes = if (index % 4 == 0) 9 * 60 + (index % 6) * 15 else null,
                listName = lists[index % lists.size],
                note = if (index % 3 == 0) "Demo note for task ${index + 1}." else "",
                reminderAt = if (index % 6 == 0 && dueDate != null) dueDate.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null,
                repeatRule = when (index % 12) { 0 -> TaskRepeatRule.DAILY; 1 -> TaskRepeatRule.WEEKLY; 2 -> TaskRepeatRule.MONTHLY; else -> TaskRepeatRule.NONE },
                repeatInterval = if (index % 12 == 1) 2 else 1,
                tags = buildList { add(tags[index % tags.size]); if (index % 10 == 0) add(tags[(index + 2) % tags.size]) },
                subtasks = if (index % 7 == 0) listOf(Subtask("demo-sub-$index-a", "First step"), Subtask("demo-sub-$index-b", "Final check", completed)) else emptyList(),
                completedAt = completedAt
            )
        }
        repository.insertSeedTasks(tasks)
        return true
    }

    private fun demoTitle(index: Int): String = when (index % 10) {
        0 -> "Review project roadmap #${index + 1}"
        1 -> "Buy groceries and household supplies"
        2 -> "Prepare weekly status report"
        3 -> "Call a friend about weekend plans"
        4 -> "Read chapter ${index + 1} of the book"
        5 -> "Schedule dentist appointment"
        6 -> "Test release candidate on Android"
        7 -> "Plan next week's priorities"
        8 -> "Organize files and clean inbox"
        else -> "Take a walk and stretch"
    }
}
