package com.si13.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskEntityMappingTest {
    @Test
    fun oldEntityDefaultsMapToBackwardCompatibleTask() {
        val task = TaskEntity("old", "Existing task", false, 10, 10).toTask()

        assertEquals(TaskPriority.NONE, task.priority)
        assertEquals(DEFAULT_TASK_LIST, task.listName)
        assertEquals("", task.note)
        assertEquals(TaskRepeatRule.NONE, task.repeatRule)
        assertEquals(emptyList<String>(), task.tags)
        assertEquals(emptyList<Subtask>(), task.subtasks)
        assertNull(task.reminderAt)
    }

    @Test
    fun expandedTaskRoundTripsThroughRoomEntity() {
        val task = Task(
            id = "expanded",
            text = "Prepare QA release",
            completed = true,
            createdAt = 10,
            updatedAt = 20,
            priority = TaskPriority.HIGH,
            dueDate = "2026-08-02",
            listName = "Work",
            note = "Include known issues",
            reminderAt = 1234,
            repeatRule = TaskRepeatRule.CUSTOM,
            repeatInterval = 2,
            repeatUnit = RepeatUnit.WEEK,
            repeatWeekdays = listOf(1, 5),
            tags = listOf("QA", "Important"),
            subtasks = listOf(Subtask("s,1", "Check release | notes", true)),
            attachments = listOf(TaskAttachment("a1", "notes, final.pdf", "content://file/1", "application/pdf", 42)),
            locationReminder = LocationReminder("Office, Warsaw", LocationTrigger.ARRIVE, 52.2, 21.0),
            assigneeIds = listOf("alex", "maria"),
            completedAt = 30
        )

        assertEquals(task, task.toEntity().toTask())
    }
}
