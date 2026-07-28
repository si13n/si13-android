package com.si13.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class PriorityPresentation(
    val colorRes: Int,
    val backgroundRes: Int
)

internal fun priorityPresentation(priority: TaskPriority): PriorityPresentation = when (priority) {
    TaskPriority.NONE -> PriorityPresentation(R.color.text_secondary, R.drawable.bg_task_property_chip)
    TaskPriority.HIGH -> PriorityPresentation(R.color.home_priority_high, R.drawable.bg_task_property_chip_high)
}

internal object TaskDatePresentation {
    fun formatDate(date: LocalDate, today: LocalDate, locale: Locale): String {
        val pattern = if (date.year == today.year) "d MMM" else "d MMM uuuu"
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    fun isOverdue(date: LocalDate, today: LocalDate): Boolean = date.isBefore(today)
}
