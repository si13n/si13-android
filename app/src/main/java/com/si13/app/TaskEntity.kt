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
    val listName: String = DEFAULT_TASK_LIST
) {
    fun toTask(): Task {
        return Task(
            id = id,
            text = text,
            completed = completed,
            createdAt = createdAt,
            updatedAt = updatedAt,
            priority = TaskPriority.fromStorageValue(priority),
            dueDate = dueDate,
            listName = listName
        )
    }
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        text = text,
        completed = completed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        priority = priority.storageValue,
        dueDate = dueDate,
        listName = listName
    )
}
