package com.si13.forgetty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreTaskMapperTest {
    @Test
    fun oldDocumentWithoutExpandedFieldsUsesSafeDefaults() {
        val task = FirestoreTaskMapper.fromMap(
            "old-id",
            mapOf("text" to "Legacy", "completed" to false, "createdAt" to 12L, "updatedAt" to 13L)
        )!!

        assertEquals(DEFAULT_TASK_LIST, task.listName)
        assertEquals(TaskPriority.NONE, task.priority)
        assertEquals(TaskRepeatRule.NONE, task.repeatRule)
        assertEquals("", task.note)
        assertEquals(emptyList<String>(), task.tags)
        assertNull(task.reminderAt)
        assertNull(task.locationReminder)
    }

    @Test
    fun expandedDocumentRoundTripsThroughNativeFirestoreValues() {
        val source = Task(
            id = "id", text = "Task", completed = false, createdAt = 1, updatedAt = 2,
            priority = TaskPriority.HIGH, dueDate = "2026-08-02", dueTimeMinutes = 540,
            note = "Notes", repeatRule = TaskRepeatRule.WEEKLY, tags = listOf("QA"),
            subtasks = listOf(Subtask("s", "Step")),
            attachments = listOf(TaskAttachment("a", "file.txt", "content://file")),
            locationReminder = LocationReminder("Office")
        )

        assertEquals(source, FirestoreTaskMapper.fromMap(source.id, FirestoreTaskMapper.toMap(source)))
    }
}
