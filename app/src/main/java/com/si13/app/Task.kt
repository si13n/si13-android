package com.si13.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class Task(
    val id: String,
    val text: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val listName: String = DEFAULT_TASK_LIST,
    val note: String = "",
    val reminderAt: Long? = null,
    val repeatRule: TaskRepeatRule = TaskRepeatRule.NONE,
    val repeatInterval: Int = 1,
    val repeatUnit: RepeatUnit = RepeatUnit.WEEK,
    val repeatWeekdays: List<Int> = emptyList(),
    val repeatEndAt: Long? = null,
    val repeatOccurrences: Int? = null,
    val listId: String? = null,
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val attachments: List<TaskAttachment> = emptyList(),
    val locationReminder: LocationReminder? = null,
    val assigneeIds: List<String> = emptyList(),
    val completedAt: Long? = null
)

data class TaskDraft(
    val text: String,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val listName: String = DEFAULT_TASK_LIST,
    val note: String = "",
    val reminderAt: Long? = null,
    val repeatRule: TaskRepeatRule = TaskRepeatRule.NONE,
    val repeatInterval: Int = 1,
    val repeatUnit: RepeatUnit = RepeatUnit.WEEK,
    val repeatWeekdays: List<Int> = emptyList(),
    val repeatEndAt: Long? = null,
    val repeatOccurrences: Int? = null,
    val listId: String? = null,
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val attachments: List<TaskAttachment> = emptyList(),
    val locationReminder: LocationReminder? = null,
    val assigneeIds: List<String> = emptyList()
)

data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean = false
)

data class TaskAttachment(
    val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
)

data class LocationReminder(
    val label: String,
    val trigger: LocationTrigger = LocationTrigger.ARRIVE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float = 150f
)

enum class LocationTrigger(val storageValue: String) {
    ARRIVE("arrive"),
    LEAVE("leave");

    companion object {
        fun fromStorageValue(value: String?): LocationTrigger =
            entries.firstOrNull { it.storageValue == value } ?: ARRIVE
    }
}

enum class TaskRepeatRule(val storageValue: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
    CUSTOM("custom");

    fun nextDate(
        current: LocalDate,
        interval: Int = 1,
        unit: RepeatUnit = RepeatUnit.WEEK,
        weekdays: List<Int> = emptyList()
    ): LocalDate? = when (this) {
        NONE -> null
        DAILY -> current.plusDays(1)
        WEEKDAYS -> generateSequence(current.plusDays(1)) { it.plusDays(1) }
            .first { it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        WEEKLY -> current.plusWeeks(1)
        MONTHLY -> {
            val target = YearMonth.from(current).plusMonths(1)
            target.atDay(current.dayOfMonth.coerceAtMost(target.lengthOfMonth()))
        }
        YEARLY -> current.plusYears(1)
        CUSTOM -> customNextDate(current, interval.coerceAtLeast(1), unit, weekdays)
    }

    private fun customNextDate(
        current: LocalDate,
        interval: Int,
        unit: RepeatUnit,
        weekdays: List<Int>
    ): LocalDate {
        if (unit == RepeatUnit.WEEK && weekdays.isNotEmpty()) {
            val normalized = weekdays.filter { it in 1..7 }.toSet()
            return generateSequence(current.plusDays(1)) { it.plusDays(1) }
                .first { candidate ->
                    val weeks = java.time.temporal.ChronoUnit.WEEKS.between(
                        current.with(DayOfWeek.MONDAY),
                        candidate.with(DayOfWeek.MONDAY)
                    )
                    candidate.dayOfWeek.value in normalized && weeks % interval == 0L
                }
        }
        return when (unit) {
            RepeatUnit.DAY -> current.plusDays(interval.toLong())
            RepeatUnit.WEEK -> current.plusWeeks(interval.toLong())
            RepeatUnit.MONTH -> {
                val target = YearMonth.from(current).plusMonths(interval.toLong())
                target.atDay(current.dayOfMonth.coerceAtMost(target.lengthOfMonth()))
            }
            RepeatUnit.YEAR -> current.plusYears(interval.toLong())
        }
    }

    companion object {
        fun fromStorageValue(value: String?): TaskRepeatRule =
            entries.firstOrNull { it.storageValue == value } ?: NONE
    }
}

enum class RepeatUnit(val storageValue: String) {
    DAY("day"), WEEK("week"), MONTH("month"), YEAR("year");

    companion object {
        fun fromStorageValue(value: String?): RepeatUnit =
            entries.firstOrNull { it.storageValue == value } ?: WEEK
    }
}

const val DEFAULT_TASK_LIST = "Personal"

val BUILT_IN_TASK_LISTS = listOf("Personal", "Work", "Shared", "Shopping")

enum class TaskPriority(val rank: Int, val storageValue: String) {
    NONE(0, "none"), HIGH(1, "high");

    fun next(): TaskPriority = if (this == NONE) HIGH else NONE

    companion object {
        fun fromStorageValue(value: String?): TaskPriority = when (value) {
            "high", "low", "medium" -> HIGH
            else -> NONE
        }

        fun next(priority: TaskPriority): TaskPriority = priority.next()
    }
}
