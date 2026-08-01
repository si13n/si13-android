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
    fun `none and high priorities have distinct presentations`() {
        assertEquals(R.drawable.bg_task_property_chip, priorityPresentation(TaskPriority.NONE).backgroundRes)
        assertEquals(R.drawable.bg_task_property_chip_high, priorityPresentation(TaskPriority.HIGH).backgroundRes)
    }

    @Test
    fun `legacy stored priorities map to high`() {
        assertEquals(TaskPriority.HIGH, TaskPriority.fromStorageValue("low"))
        assertEquals(TaskPriority.HIGH, TaskPriority.fromStorageValue("medium"))
    }

    @Test
    fun `all filter groups active tasks by due date`() {
        val tasks = listOf(
            task("overdue", dueDate = "2026-07-27"),
            task("today", dueDate = "2026-07-28"),
            task("upcoming", dueDate = "2026-07-29"),
            task("unscheduled"),
            task("done", completed = true)
        )

        val sections = TaskSectioner.sections(tasks, HomeTaskFilter.ALL, today)

        assertEquals(
            listOf(
                TaskSectionKind.OVERDUE,
                TaskSectionKind.TODAY,
                TaskSectionKind.UPCOMING,
                TaskSectionKind.NO_DUE_DATE
            ),
            sections.map(TaskSection::kind)
        )
        assertEquals(4, sections.sumOf { it.tasks.size })
    }

    @Test
    fun `completed filter excludes active tasks`() {
        val tasks = listOf(task("active"), task("done", completed = true))

        val sections = TaskSectioner.sections(tasks, HomeTaskFilter.COMPLETED, today)

        assertEquals(listOf("done"), sections.single().tasks.map(Task::id))
    }

    @Test
    fun `home progress messages match Figma thresholds`() {
        assertEquals(R.string.progress_get_started, HomeProgressPresentation.messageRes(0, 0))
        assertEquals(R.string.progress_get_started, HomeProgressPresentation.messageRes(0, 5))
        assertEquals(R.string.progress_good_start, HomeProgressPresentation.messageRes(1, 5))
        assertEquals(R.string.progress_keep_going, HomeProgressPresentation.messageRes(2, 5))
        assertEquals(R.string.progress_almost_there, HomeProgressPresentation.messageRes(4, 5))
        assertEquals(R.string.progress_almost_there, HomeProgressPresentation.messageRes(5, 5))
    }

    private fun task(
        id: String,
        dueDate: String? = null,
        completed: Boolean = false
    ) = Task(id, id, completed, 1L, 1L, dueDate = dueDate)
}
