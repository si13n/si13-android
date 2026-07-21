package com.si13.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val text: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toTask(): Task {
        return Task(
            id = id,
            text = text,
            completed = completed,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        text = text,
        completed = completed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
