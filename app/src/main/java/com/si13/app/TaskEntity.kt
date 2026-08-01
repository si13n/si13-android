package com.si13.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val text: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val priority: String? = null,
    val dueDate: String? = null,
    val dueTimeMinutes: Int? = null,
    val listName: String = DEFAULT_TASK_LIST,
    val note: String = "",
    val reminderAt: Long? = null,
    val repeatRule: String = TaskRepeatRule.NONE.storageValue,
    val repeatInterval: Int = 1,
    val repeatUnit: String = RepeatUnit.WEEK.storageValue,
    val repeatWeekdays: String = "",
    val repeatEndAt: Long? = null,
    val repeatOccurrences: Int? = null,
    val listId: String? = null,
    val tags: String = "",
    val subtasks: String = "",
    val attachments: String = "",
    val locationReminder: String? = null,
    val assigneeIds: String = "",
    val completedAt: Long? = null
) {
    fun toTask(): Task = Task(
        id = id,
        text = text,
        completed = completed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        priority = TaskPriority.fromStorageValue(priority),
        dueDate = dueDate,
        dueTimeMinutes = dueTimeMinutes,
        listName = listName.ifBlank { DEFAULT_TASK_LIST },
        note = note,
        reminderAt = reminderAt,
        repeatRule = TaskRepeatRule.fromStorageValue(repeatRule),
        repeatInterval = repeatInterval.coerceAtLeast(1),
        repeatUnit = RepeatUnit.fromStorageValue(repeatUnit),
        repeatWeekdays = TaskFieldCodec.decodeStrings(repeatWeekdays).mapNotNull(String::toIntOrNull),
        repeatEndAt = repeatEndAt,
        repeatOccurrences = repeatOccurrences,
        listId = listId,
        tags = TaskFieldCodec.decodeStrings(tags),
        subtasks = TaskFieldCodec.decodeSubtasks(subtasks),
        attachments = TaskFieldCodec.decodeAttachments(attachments),
        locationReminder = TaskFieldCodec.decodeLocation(locationReminder),
        assigneeIds = TaskFieldCodec.decodeStrings(assigneeIds),
        completedAt = completedAt
    )
}

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    text = text,
    completed = completed,
    createdAt = createdAt,
    updatedAt = updatedAt,
    // Version 4 stored the absence of priority as SQL NULL. Keep that representation so
    // migrated rows and newly written rows remain byte-for-byte compatible.
    priority = priority.storageValue.takeUnless { priority == TaskPriority.NONE },
    dueDate = dueDate,
    dueTimeMinutes = dueTimeMinutes,
    listName = listName.ifBlank { DEFAULT_TASK_LIST },
    note = note,
    reminderAt = reminderAt,
    repeatRule = repeatRule.storageValue,
    repeatInterval = repeatInterval,
    repeatUnit = repeatUnit.storageValue,
    repeatWeekdays = TaskFieldCodec.encodeStrings(repeatWeekdays.map(Int::toString)),
    repeatEndAt = repeatEndAt,
    repeatOccurrences = repeatOccurrences,
    listId = listId,
    tags = TaskFieldCodec.encodeStrings(tags),
    subtasks = TaskFieldCodec.encodeSubtasks(subtasks),
    attachments = TaskFieldCodec.encodeAttachments(attachments),
    locationReminder = TaskFieldCodec.encodeLocation(locationReminder),
    assigneeIds = TaskFieldCodec.encodeStrings(assigneeIds),
    completedAt = completedAt
)
