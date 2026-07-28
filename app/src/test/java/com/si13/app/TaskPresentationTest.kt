package com.si13.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TaskPresentationTest {
    private val today = LocalDate.of(2026, 7, 28)

    @Test
    fun `home due date labels distinguish today tomorrow and years`() {
        assertEquals("30 Jul", TaskDatePresentation.formatDate(LocalDate.of(2026, 7, 30), today, Locale.UK))
        assertEquals("30 Jul 2027", TaskDatePresentation.formatDate(LocalDate.of(2027, 7, 30), today, Locale.UK))
    }

    @Test
    fun `overdue presentation applies only to active tasks`() {
        val overdue = LocalDate.of(2026, 7, 27)
        assertTrue(TaskDatePresentation.isOverdue(overdue, today))
        assertFalse(TaskDatePresentation.isOverdue(today, today))
    }

    @Test
    fun `every priority has a distinct presentation`() {
        assertEquals(R.drawable.bg_task_property_chip, priorityPresentation(TaskPriority.NONE).backgroundRes)
        assertEquals(R.drawable.bg_task_property_chip_low, priorityPresentation(TaskPriority.LOW).backgroundRes)
        assertEquals(R.drawable.bg_task_property_chip_medium, priorityPresentation(TaskPriority.MEDIUM).backgroundRes)
        assertEquals(R.drawable.bg_task_property_chip_high, priorityPresentation(TaskPriority.HIGH).backgroundRes)
    }
}
