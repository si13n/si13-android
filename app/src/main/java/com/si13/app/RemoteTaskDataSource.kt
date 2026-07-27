package com.si13.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RemoteTaskDataSource(
    private val firestore: FirebaseFirestore,
    private val userId: String
) : TaskDataSource {
    // Firestore rules are scoped to this path so each user only reaches their own task documents.
    private val tasksCollection = firestore
        .collection("users")
        .document(userId)
        .collection("tasks")

    override fun observeTasks(): Flow<List<Task>> {
        return callbackFlow {
            val listener = tasksCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    trySend(snapshot?.documents.orEmpty().mapNotNull { document ->
                        document.data?.toTask(document.id)
                    })
                }

            awaitClose { listener.remove() }
        }
    }

    override suspend fun getTasks(): List<Task> {
        return tasksCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { document -> document.data?.toTask(document.id) }
    }

    override suspend fun hasTasks(): Boolean {
        return !tasksCollection.limit(1).get().await().isEmpty
    }

    override suspend fun upsert(task: Task) {
        tasksCollection.document(task.id).set(task.toFirestoreMap()).await()
    }

    override suspend fun upsertAll(tasks: List<Task>) {
        val batch = firestore.batch()
        tasks.forEach { task ->
            batch.set(tasksCollection.document(task.id), task.toFirestoreMap())
        }
        batch.commit().await()
    }

    override suspend fun delete(taskId: String) {
        tasksCollection.document(taskId).delete().await()
    }

    override suspend fun deleteAll() {
        val documents = tasksCollection.get().await().documents
        if (documents.isEmpty()) {
            return
        }

        val batch = firestore.batch()
        documents.forEach { document -> batch.delete(document.reference) }
        batch.commit().await()
    }

    private fun Task.toFirestoreMap(): Map<String, Any> {
        val values = mutableMapOf<String, Any>(
            "text" to text,
            "completed" to completed,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
        priority?.let { values["priority"] = it.storageValue }
        return values
    }

    private fun Map<String, Any>.toTask(documentId: String): Task? {
        val text = this["text"] as? String ?: return null
        val completed = this["completed"] as? Boolean ?: false
        val createdAt = this["createdAt"] as? Number ?: return null
        val updatedAt = this["updatedAt"] as? Number ?: createdAt

        return Task(
            id = documentId,
            text = text,
            completed = completed,
            createdAt = createdAt.toLong(),
            updatedAt = updatedAt.toLong(),
            priority = TaskPriority.fromStorageValue(this["priority"] as? String)
        )
    }
}
