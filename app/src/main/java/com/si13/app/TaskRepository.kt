package com.si13.app

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val localTaskDataSource: TaskDataSource,
    private val remoteTaskDataSourceFactory: (String) -> TaskDataSource,
    private val currentUserIdProvider: () -> String?
) {
    fun observeTasks(): Flow<List<Task>> {
        return activeDataSource().observeTasks()
    }

    suspend fun addTask(text: String) {
        val trimmedText = text.trim()
        require(trimmedText.isNotEmpty()) { "Task text cannot be empty." }
        require(trimmedText.length <= MAX_TASK_LENGTH) { "Task text cannot exceed $MAX_TASK_LENGTH characters." }

        val now = System.currentTimeMillis()
        activeDataSource().upsert(
            Task(
                id = UUID.randomUUID().toString(),
                text = trimmedText,
                completed = false,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun setTaskCompleted(task: Task, completed: Boolean) {
        activeDataSource().upsert(
            task.copy(
                completed = completed,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun hasLocalTasks(): Boolean {
        return localTaskDataSource.hasTasks()
    }

    suspend fun importLocalTasksToRemote(): TaskImportResult {
        val userId = currentUserIdProvider()
            ?: return TaskImportResult.Failure(IllegalStateException("No authenticated user."))
        val localTasks = localTaskDataSource.getTasks()
        if (localTasks.isEmpty()) {
            return TaskImportResult.NoLocalTasks
        }

        return try {
            // Keep guest tasks local until the full batch write succeeds, so failed imports can retry.
            remoteTaskDataSourceFactory(userId).upsertAll(localTasks)
            localTaskDataSource.deleteAll()
            TaskImportResult.Imported(localTasks.size)
        } catch (exception: Exception) {
            TaskImportResult.Failure(exception)
        }
    }

    suspend fun discardLocalTasks() {
        localTaskDataSource.deleteAll()
    }

    private fun activeDataSource(): TaskDataSource {
        val userId = currentUserIdProvider()
        return if (userId == null) {
            localTaskDataSource
        } else {
            remoteTaskDataSourceFactory(userId)
        }
    }

    companion object {
        const val MAX_TASK_LENGTH = 200

        fun create(context: Context): TaskRepository {
            val appContext = context.applicationContext
            return TaskRepository(
                localTaskDataSource = LocalTaskDataSource(TaskDatabase.getInstance(appContext).taskDao()),
                remoteTaskDataSourceFactory = { userId ->
                    RemoteTaskDataSource(FirebaseFirestore.getInstance(), userId)
                },
                currentUserIdProvider = {
                    FirebaseAuth.getInstance().currentUser?.uid
                        ?: AuthRepository(appContext).getCurrentUser()?.uid
                }
            )
        }
    }
}

sealed class TaskImportResult {
    data class Imported(val count: Int) : TaskImportResult()
    object NoLocalTasks : TaskImportResult()
    data class Failure(val exception: Exception) : TaskImportResult()
}
