package com.si13.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTaskDataSourceTest {

    @Test
    fun storesAndReadsTasks() = runTest {
        val dataSource = LocalTaskDataSource(FakeTaskDao())
        val task = Task(
            id = "task-1",
            text = "Buy milk",
            completed = false,
            createdAt = 1L,
            updatedAt = 1L
        )

        dataSource.upsert(task)

        assertEquals(listOf(task), dataSource.getTasks())
        assertEquals(listOf(task), dataSource.observeTasks().first())
        assertTrue(dataSource.hasTasks())
    }

    @Test
    fun deleteAllClearsTasks() = runTest {
        val dataSource = LocalTaskDataSource(FakeTaskDao())
        dataSource.upsert(
            Task(
                id = "task-1",
                text = "Buy milk",
                completed = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        dataSource.deleteAll()

        assertTrue(dataSource.getTasks().isEmpty())
        assertFalse(dataSource.hasTasks())
    }
}

private class FakeTaskDao : TaskDao {
    private val tasks = LinkedHashMap<String, TaskEntity>()
    private val taskFlow = MutableStateFlow<List<TaskEntity>>(emptyList())

    override fun observeTasks() = taskFlow

    override suspend fun getTasks(): List<TaskEntity> {
        return taskFlow.value
    }

    override suspend fun countTasks(): Int {
        return tasks.size
    }

    override suspend fun upsert(task: TaskEntity) {
        tasks[task.id] = task
        publish()
    }

    override suspend fun upsertAll(tasks: List<TaskEntity>) {
        tasks.forEach { task -> this.tasks[task.id] = task }
        publish()
    }

    override suspend fun deleteAll() {
        tasks.clear()
        publish()
    }

    private fun publish() {
        taskFlow.value = tasks.values.sortedByDescending { it.createdAt }
    }
}
