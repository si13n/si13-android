package com.si13.app

data class Task(
    val id: String,
    val text: String,
    val completed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val priority: TaskPriority = TaskPriority.NONE,
    val dueDate: String? = null
)

enum class TaskPriority(val rank: Int, val storageValue: String) {
    NONE(0, "none"), HIGH(1, "high");

    fun next(): TaskPriority {
        return when (this) {
            NONE -> HIGH
            HIGH -> NONE
        }
    }

    companion object {
        fun fromStorageValue(value: String?): TaskPriority {
            return when (value) {
                "high", "low", "medium" -> HIGH
                else -> NONE
            }
        }

        fun next(priority: TaskPriority): TaskPriority = priority.next()
    }
}
