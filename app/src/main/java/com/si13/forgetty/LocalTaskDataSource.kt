package com.si13.forgetty

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalTaskDataSource(
    private val taskDao: TaskDao
) : TaskDataSource {
    override fun observeTasks(): Flow<List<Task>> {
        return taskDao.observeTasks().map { tasks -> tasks.map { it.toTask() } }
    }

    override suspend fun getTasks(): List<Task> {
        return taskDao.getTasks().map { it.toTask() }
    }

    override suspend fun hasTasks(): Boolean {
        return taskDao.countTasks() > 0
    }

    override suspend fun upsert(task: Task) {
        taskDao.upsert(task.toEntity())
    }

    override suspend fun upsertAll(tasks: List<Task>) {
        taskDao.upsertAll(tasks.map { it.toEntity() })
    }

    override suspend fun delete(taskId: String) {
        taskDao.delete(taskId)
    }

    override suspend fun deleteAll() {
        taskDao.deleteAll()
    }
}
