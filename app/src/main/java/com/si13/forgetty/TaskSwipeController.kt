package com.si13.forgetty

internal class TaskSwipeController(
    private val onOpenTaskChanged: (previousTaskId: String?, openTaskId: String?) -> Unit
) {
    var openTaskId: String? = null
        private set

    private val deletingTaskIds = mutableSetOf<String>()

    fun open(taskId: String?) {
        if (openTaskId == taskId) return
        val previousTaskId = openTaskId
        openTaskId = taskId
        onOpenTaskChanged(previousTaskId, taskId)
    }

    fun close(): Boolean {
        if (openTaskId == null) return false
        open(null)
        return true
    }

    fun requestDelete(taskId: String, delete: (String) -> Unit): Boolean {
        if (!deletingTaskIds.add(taskId)) return false
        close()
        delete(taskId)
        return true
    }

    fun allowDeleteRetry(taskId: String) {
        deletingTaskIds.remove(taskId)
    }

    fun retainTasks(taskIds: Set<String>) {
        deletingTaskIds.retainAll(taskIds)
        if (openTaskId !in taskIds) {
            openTaskId = null
        }
    }
}
