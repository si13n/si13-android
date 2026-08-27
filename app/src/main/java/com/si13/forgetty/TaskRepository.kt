package com.si13.forgetty

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val localTaskDataSource: TaskDataSource,
    private val remoteTaskDataSourceFactory: (String) -> TaskDataSource,
    private val currentUserIdProvider: () -> String?,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val mutationObserver: TaskMutationObserver? = null
) {
    fun observeTasks(): Flow<List<Task>> {
        return activeDataSource().observeTasks().map { tasks -> tasks.sortedForDisplay() }
    }

    suspend fun addTask(
        text: String,
        priority: TaskPriority = TaskPriority.NONE,
        dueDate: String? = null,
        listName: String = DEFAULT_TASK_LIST
    ): Task = addTask(TaskDraft(text = text, priority = priority, dueDate = dueDate, listName = listName))

    suspend fun addTask(draft: TaskDraft): Task {
        val trimmedText = validateTitle(draft.text)
        val now = nowProvider()
        val task = Task(
            id = UUID.randomUUID().toString(),
            text = trimmedText,
            completed = false,
            createdAt = now,
            updatedAt = now,
            priority = draft.priority,
            dueDate = draft.dueDate,
            dueTimeMinutes = draft.dueTimeMinutes,
            listName = draft.listName.ifBlank { DEFAULT_TASK_LIST },
            note = draft.note,
            reminderAt = draft.reminderAt,
            repeatRule = draft.repeatRule,
            repeatInterval = draft.repeatInterval.coerceAtLeast(1),
            repeatUnit = draft.repeatUnit,
            repeatWeekdays = draft.repeatWeekdays,
            repeatEndAt = draft.repeatEndAt,
            repeatOccurrences = draft.repeatOccurrences,
            listId = draft.listId,
            tags = draft.tags.distinct(),
            subtasks = draft.subtasks,
            attachments = draft.attachments,
            locationReminder = draft.locationReminder,
            assigneeIds = draft.assigneeIds.distinct()
        )
        activeDataSource().upsert(task)
        mutationObserver?.onTaskChanged(task)
        return task
    }

    suspend fun setTaskCompleted(task: Task, completed: Boolean) {
        val now = nowProvider()
        val dataSource = activeDataSource()
        val updated = task.copy(completed = completed, completedAt = now.takeIf { completed }, updatedAt = now)
        dataSource.upsert(updated)
        mutationObserver?.onTaskChanged(updated)
        if (completed && !task.completed) {
            nextOccurrence(task, now)?.let { next -> dataSource.upsert(next); mutationObserver?.onTaskChanged(next) }
        }
    }

    suspend fun setTaskPriority(task: Task, priority: TaskPriority) {
        val updated = task.copy(
                priority = priority,
                updatedAt = nowProvider()
            )
        activeDataSource().upsert(updated)
        mutationObserver?.onTaskChanged(updated)
    }

    suspend fun toggleTaskPriority(task: Task) {
        val dataSource = activeDataSource()
        val currentTask = dataSource.getTasks()
            .firstOrNull { currentTask -> currentTask.id == task.id }
        val currentPriority = currentTask?.priority ?: task.priority

        val updated = task.copy(
                priority = TaskPriority.next(currentPriority),
                updatedAt = nowProvider()
            )
        dataSource.upsert(updated)
        mutationObserver?.onTaskChanged(updated)
    }

    suspend fun updateTask(
        task: Task,
        text: String = task.text,
        priority: TaskPriority = task.priority,
        dueDate: String? = task.dueDate
    ) {
        val updated = task.copy(
                text = validateTitle(text),
                priority = priority,
                dueDate = dueDate,
                updatedAt = nowProvider()
            )
        activeDataSource().upsert(updated)
        mutationObserver?.onTaskChanged(updated)
    }

    suspend fun duplicateTask(task: Task): Task {
        val now = nowProvider()
        return task.copy(
            id = UUID.randomUUID().toString(),
            text = validateTitle(task.text),
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            subtasks = task.subtasks.map { it.copy(id = UUID.randomUUID().toString(), completed = false) }
        ).also { activeDataSource().upsert(it); mutationObserver?.onTaskChanged(it) }
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

    suspend fun deleteTask(taskId: String) {
        activeDataSource().delete(taskId)
        mutationObserver?.onTaskDeleted(taskId)
    }

    suspend fun restoreTask(task: Task) {
        activeDataSource().upsert(task)
        mutationObserver?.onTaskChanged(task)
    }

    suspend fun deleteAllTasks() {
        val dataSource = activeDataSource()
        val ids = dataSource.getTasks().map(Task::id)
        dataSource.deleteAll()
        ids.forEach { mutationObserver?.onTaskDeleted(it) }
    }

    suspend fun deleteCompletedTasks() {
        val dataSource = activeDataSource()
        dataSource.getTasks().filter(Task::completed).forEach {
            dataSource.delete(it.id)
            mutationObserver?.onTaskDeleted(it.id)
        }
    }

    suspend fun renameList(oldName: String, newName: String) {
        val cleanName = newName.trim().ifBlank { DEFAULT_TASK_LIST }
        val dataSource = activeDataSource()
        dataSource.getTasks().filter { it.listName == oldName }.forEach { task ->
            val updated = task.copy(listName = cleanName, updatedAt = nowProvider())
            dataSource.upsert(updated)
            mutationObserver?.onTaskChanged(updated)
        }
    }

    suspend fun moveTasksFromList(oldName: String, fallbackName: String = DEFAULT_TASK_LIST) {
        renameList(oldName, fallbackName)
    }

    suspend fun getTasks(): List<Task> = activeDataSource().getTasks()

    internal suspend fun insertSeedTasks(tasks: List<Task>) {
        activeDataSource().upsertAll(tasks)
    }

    private fun validateTitle(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Task text cannot be empty." }
        require(trimmed.length <= MAX_TASK_LENGTH) {
            "Task text cannot exceed $MAX_TASK_LENGTH characters."
        }
        return trimmed
    }

    private fun nextOccurrence(task: Task, now: Long): Task? {
        val currentDate = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        if (task.repeatRule == TaskRepeatRule.NONE || task.repeatOccurrences == 1) return null
        val nextDate = task.repeatRule.nextDate(
            currentDate,
            task.repeatInterval,
            task.repeatUnit,
            task.repeatWeekdays
        ) ?: return null
        val nextStart = nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (task.repeatEndAt != null && nextStart > task.repeatEndAt) return null
        val dayShift = ChronoUnit.DAYS.between(currentDate, nextDate)
        val nextReminder = task.reminderAt?.let { reminder ->
            Instant.ofEpochMilli(reminder).atZone(ZoneId.systemDefault()).plusDays(dayShift)
                .toInstant().toEpochMilli()
        }
        return task.copy(
            id = UUID.randomUUID().toString(),
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            dueDate = nextDate.toString(),
            reminderAt = nextReminder,
            repeatOccurrences = task.repeatOccurrences?.minus(1),
            subtasks = task.subtasks.map { it.copy(completed = false) }
        )
    }

    private fun List<Task>.sortedForDisplay(): List<Task> {
        return sortedWith(
            compareBy<Task> { it.completed }
                .thenByDescending { it.priority.rank }
                .thenByDescending { it.createdAt }
        )
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
        const val MAX_TASK_LENGTH = 100

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
                },
                nowProvider = System::currentTimeMillis,
                mutationObserver = AndroidTaskMutationObserver(appContext)
            )
        }
    }
}

sealed class TaskImportResult {
    data class Imported(val count: Int) : TaskImportResult()
    object NoLocalTasks : TaskImportResult()
    data class Failure(val exception: Exception) : TaskImportResult()
}
