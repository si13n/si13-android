package com.si13.forgetty

import kotlinx.coroutines.flow.Flow

interface TaskDataSource {
    fun observeTasks(): Flow<List<Task>>

    suspend fun getTasks(): List<Task>

    suspend fun hasTasks(): Boolean

    suspend fun upsert(task: Task)

    suspend fun upsertAll(tasks: List<Task>)

    suspend fun delete(taskId: String)

    suspend fun deleteAll()
}
