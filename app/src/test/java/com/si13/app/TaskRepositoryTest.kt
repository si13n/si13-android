package com.si13.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryTest {

    @Test
    fun guestUserWritesToLocalStorage() = runTest {
        val local = FakeTaskDataSource()
        val remote = FakeTaskDataSource()
        val repository = TaskRepository(
            localTaskDataSource = local,
            remoteTaskDataSourceFactory = { remote },
            currentUserIdProvider = { null }
        )

        repository.addTask("Guest task")

        assertEquals("Guest task", local.getTasks().single().text)
        assertTrue(remote.getTasks().isEmpty())
    }

    @Test
    fun authenticatedUserWritesToRemoteStorage() = runTest {
        val local = FakeTaskDataSource()
        val remote = FakeTaskDataSource()
        val repository = TaskRepository(
            localTaskDataSource = local,
            remoteTaskDataSourceFactory = { remote },
            currentUserIdProvider = { "user-1" }
        )

        repository.addTask("Remote task")

        assertTrue(local.getTasks().isEmpty())
        assertEquals("Remote task", remote.getTasks().single().text)
    }

    @Test
    fun successfulImportUploadsLocalTasksAndClearsLocalStorage() = runTest {
        val local = FakeTaskDataSource()
        val remote = FakeTaskDataSource()
        val task = Task("task-1", "Guest task", false, 1L, 1L)
        local.upsert(task)
        val repository = TaskRepository(
            localTaskDataSource = local,
            remoteTaskDataSourceFactory = { remote },
            currentUserIdProvider = { "user-1" }
        )

        val result = repository.importLocalTasksToRemote()

        assertEquals(TaskImportResult.Imported(1), result)
        assertEquals(listOf(task), remote.getTasks())
        assertTrue(local.getTasks().isEmpty())
    }

    @Test
    fun failedImportKeepsLocalTasks() = runTest {
        val local = FakeTaskDataSource()
        val remote = FakeTaskDataSource(failUpsertAll = true)
        val task = Task("task-1", "Guest task", false, 1L, 1L)
        local.upsert(task)
        val repository = TaskRepository(
            localTaskDataSource = local,
            remoteTaskDataSourceFactory = { remote },
            currentUserIdProvider = { "user-1" }
        )

        val result = repository.importLocalTasksToRemote()

        assertTrue(result is TaskImportResult.Failure)
        assertEquals(listOf(task), local.getTasks())
    }

    @Test
    fun discardDeletesOnlyLocalTasks() = runTest {
        val local = FakeTaskDataSource()
        val remote = FakeTaskDataSource()
        val localTask = Task("local-task", "Local task", false, 1L, 1L)
        val remoteTask = Task("remote-task", "Remote task", false, 2L, 2L)
        local.upsert(localTask)
        remote.upsert(remoteTask)
        val repository = TaskRepository(
            localTaskDataSource = local,
            remoteTaskDataSourceFactory = { remote },
            currentUserIdProvider = { "user-1" }
        )

        repository.discardLocalTasks()

        assertTrue(local.getTasks().isEmpty())
        assertEquals(listOf(remoteTask), remote.getTasks())
    }

    @Test
    fun rejectsEmptyAndTooLongTasks() = runTest {
        val repository = TaskRepository(
            localTaskDataSource = FakeTaskDataSource(),
            remoteTaskDataSourceFactory = { FakeTaskDataSource() },
            currentUserIdProvider = { null }
        )

        assertFailsWithIllegalArgument { repository.addTask("   ") }
        assertFailsWithIllegalArgument { repository.addTask("x".repeat(TaskRepository.MAX_TASK_LENGTH + 1)) }
    }
}

private class FakeTaskDataSource(
    private val failUpsertAll: Boolean = false
) : TaskDataSource {
    private val tasks = LinkedHashMap<String, Task>()
    private val taskFlow = MutableStateFlow<List<Task>>(emptyList())

    override fun observeTasks() = taskFlow

    override suspend fun getTasks(): List<Task> {
        return taskFlow.first()
    }

    override suspend fun hasTasks(): Boolean {
        return tasks.isNotEmpty()
    }

    override suspend fun upsert(task: Task) {
        tasks[task.id] = task
        publish()
    }

    override suspend fun upsertAll(tasks: List<Task>) {
        if (failUpsertAll) {
            throw IllegalStateException("Upload failed")
        }
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

private suspend fun assertFailsWithIllegalArgument(block: suspend () -> Unit) {
    try {
        block()
    } catch (exception: IllegalArgumentException) {
        return
    }

    throw AssertionError("Expected IllegalArgumentException")
}
