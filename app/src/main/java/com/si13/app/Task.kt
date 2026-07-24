package com.si13.app

data class Task(
    val id: String,
    val text: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val priority: TaskPriority? = null
)

enum class TaskPriority(val rank: Int, val storageValue: String) {
    HIGH(1, "high");

    fun next(): TaskPriority? {
        return when (this) {
            HIGH -> null
        }
    }

    companion object {
        fun fromStorageValue(value: String?): TaskPriority? {
            return values().firstOrNull { it.storageValue == value }
        }

        fun next(priority: TaskPriority?): TaskPriority? {
            return priority?.next() ?: HIGH
        }
    }
}
