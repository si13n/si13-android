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
                        document.data?.let { FirestoreTaskMapper.fromMap(document.id, it) }
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
            .mapNotNull { document -> document.data?.let { FirestoreTaskMapper.fromMap(document.id, it) } }
    }

    override suspend fun hasTasks(): Boolean {
        return !tasksCollection.limit(1).get().await().isEmpty
    }

    override suspend fun upsert(task: Task) {
        tasksCollection.document(task.id).set(FirestoreTaskMapper.toMap(task)).await()
    }

    override suspend fun upsertAll(tasks: List<Task>) {
        val batch = firestore.batch()
        tasks.forEach { task ->
            batch.set(tasksCollection.document(task.id), FirestoreTaskMapper.toMap(task))
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
            "updatedAt" to updatedAt,
            "priority" to priority.storageValue,
            "listName" to listName,
            "note" to note,
            "repeatRule" to repeatRule.storageValue,
            "repeatInterval" to repeatInterval,
            "repeatUnit" to repeatUnit.storageValue,
            "repeatWeekdays" to repeatWeekdays,
            "tags" to tags,
            "subtasks" to subtasks.map { subtask ->
                mapOf("id" to subtask.id, "title" to subtask.title, "completed" to subtask.completed)
            },
            "attachments" to attachments.map { attachment ->
                buildMap<String, Any> {
                    put("id", attachment.id)
                    put("displayName", attachment.displayName)
                    put("uri", attachment.uri)
                    attachment.mimeType?.let { put("mimeType", it) }
                    attachment.sizeBytes?.let { put("sizeBytes", it) }
                }
            },
            "assigneeIds" to assigneeIds
        )
        dueDate?.let { values["dueDate"] = it }
        dueTimeMinutes?.let { values["dueTimeMinutes"] = it }
        reminderAt?.let { values["reminderAt"] = it }
        repeatEndAt?.let { values["repeatEndAt"] = it }
        repeatOccurrences?.let { values["repeatOccurrences"] = it }
        listId?.let { values["listId"] = it }
        completedAt?.let { values["completedAt"] = it }
        locationReminder?.let { location ->
            values["locationReminder"] = buildMap<String, Any> {
                put("label", location.label)
                put("trigger", location.trigger.storageValue)
                put("radiusMeters", location.radiusMeters.toDouble())
                location.latitude?.let { put("latitude", it) }
                location.longitude?.let { put("longitude", it) }
            }
        }
        return values
    }

    private fun Map<String, Any>.toTask(documentId: String): Task? {
        val text = this["text"] as? String ?: return null
        val completed = this["completed"] as? Boolean ?: false
        val createdAt = this["createdAt"] as? Number ?: System.currentTimeMillis()
        val updatedAt = this["updatedAt"] as? Number ?: createdAt

        return Task(
            id = documentId,
            text = text,
            completed = completed,
            createdAt = createdAt.toLong(),
            updatedAt = updatedAt.toLong(),
            priority = TaskPriority.fromStorageValue(this["priority"] as? String),
            dueDate = this["dueDate"] as? String,
            dueTimeMinutes = (this["dueTimeMinutes"] as? Number)?.toInt(),
            listName = (this["listName"] as? String).orEmpty().ifBlank { DEFAULT_TASK_LIST },
            note = this["note"] as? String ?: "",
            reminderAt = (this["reminderAt"] as? Number)?.toLong(),
            repeatRule = TaskRepeatRule.fromStorageValue(this["repeatRule"] as? String),
            repeatInterval = ((this["repeatInterval"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            repeatUnit = RepeatUnit.fromStorageValue(this["repeatUnit"] as? String),
            repeatWeekdays = (this["repeatWeekdays"] as? List<*>)
                .orEmpty().mapNotNull { (it as? Number)?.toInt() },
            repeatEndAt = (this["repeatEndAt"] as? Number)?.toLong(),
            repeatOccurrences = (this["repeatOccurrences"] as? Number)?.toInt(),
            listId = this["listId"] as? String,
            tags = (this["tags"] as? List<*>).orEmpty().mapNotNull { it as? String },
            subtasks = (this["subtasks"] as? List<*>).orEmpty().mapNotNull(::subtaskFromFirestore),
            attachments = (this["attachments"] as? List<*>).orEmpty().mapNotNull(::attachmentFromFirestore),
            locationReminder = locationFromFirestore(this["locationReminder"]),
            assigneeIds = (this["assigneeIds"] as? List<*>).orEmpty().mapNotNull { it as? String },
            completedAt = (this["completedAt"] as? Number)?.toLong()
        )
    }

    private fun subtaskFromFirestore(value: Any?): Subtask? {
        val map = value as? Map<*, *> ?: return null
        val id = map["id"] as? String ?: return null
        val title = map["title"] as? String ?: return null
        return Subtask(id, title, map["completed"] as? Boolean ?: false)
    }

    private fun attachmentFromFirestore(value: Any?): TaskAttachment? {
        val map = value as? Map<*, *> ?: return null
        val id = map["id"] as? String ?: return null
        val displayName = map["displayName"] as? String ?: return null
        val uri = map["uri"] as? String ?: return null
        return TaskAttachment(
            id = id,
            displayName = displayName,
            uri = uri,
            mimeType = map["mimeType"] as? String,
            sizeBytes = (map["sizeBytes"] as? Number)?.toLong()
        )
    }

    private fun locationFromFirestore(value: Any?): LocationReminder? {
        val map = value as? Map<*, *> ?: return null
        val label = map["label"] as? String ?: return null
        return LocationReminder(
            label = label,
            trigger = LocationTrigger.fromStorageValue(map["trigger"] as? String),
            latitude = (map["latitude"] as? Number)?.toDouble(),
            longitude = (map["longitude"] as? Number)?.toDouble(),
            radiusMeters = (map["radiusMeters"] as? Number)?.toFloat() ?: 150f
        )
    }
}

/** Pure mapper kept separate so old Firestore documents can be regression-tested without a network. */
internal object FirestoreTaskMapper {
    fun toMap(task: Task): Map<String, Any> = with(task) {
        buildMap {
            put("text", text); put("completed", completed); put("createdAt", createdAt); put("updatedAt", updatedAt)
            put("priority", priority.storageValue); put("listName", listName); put("note", note)
            put("repeatRule", repeatRule.storageValue); put("repeatInterval", repeatInterval); put("repeatUnit", repeatUnit.storageValue)
            put("repeatWeekdays", repeatWeekdays); put("tags", tags); put("assigneeIds", assigneeIds)
            put("subtasks", subtasks.map { mapOf("id" to it.id, "title" to it.title, "completed" to it.completed) })
            put("attachments", attachments.map { item -> buildMap<String, Any> {
                put("id", item.id); put("displayName", item.displayName); put("uri", item.uri)
                item.mimeType?.let { put("mimeType", it) }; item.sizeBytes?.let { put("sizeBytes", it) }
            } })
            dueDate?.let { put("dueDate", it) }; dueTimeMinutes?.let { put("dueTimeMinutes", it) }
            reminderAt?.let { put("reminderAt", it) }; repeatEndAt?.let { put("repeatEndAt", it) }
            repeatOccurrences?.let { put("repeatOccurrences", it) }; listId?.let { put("listId", it) }
            completedAt?.let { put("completedAt", it) }
            locationReminder?.let { location -> put("locationReminder", buildMap<String, Any> {
                put("label", location.label); put("trigger", location.trigger.storageValue); put("radiusMeters", location.radiusMeters.toDouble())
                location.latitude?.let { put("latitude", it) }; location.longitude?.let { put("longitude", it) }
            }) }
        }
    }

    fun fromMap(documentId: String, values: Map<String, Any>): Task? {
        val text = values["text"] as? String ?: return null
        val createdAt = (values["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (values["updatedAt"] as? Number)?.toLong() ?: createdAt
        return Task(
            id = documentId, text = text, completed = values["completed"] as? Boolean ?: false,
            createdAt = createdAt, updatedAt = updatedAt,
            priority = TaskPriority.fromStorageValue(values["priority"] as? String),
            dueDate = values["dueDate"] as? String,
            dueTimeMinutes = (values["dueTimeMinutes"] as? Number)?.toInt(),
            listName = (values["listName"] as? String).orEmpty().ifBlank { DEFAULT_TASK_LIST },
            note = values["note"] as? String ?: "",
            reminderAt = (values["reminderAt"] as? Number)?.toLong(),
            repeatRule = TaskRepeatRule.fromStorageValue(values["repeatRule"] as? String),
            repeatInterval = ((values["repeatInterval"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            repeatUnit = RepeatUnit.fromStorageValue(values["repeatUnit"] as? String),
            repeatWeekdays = (values["repeatWeekdays"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() },
            repeatEndAt = (values["repeatEndAt"] as? Number)?.toLong(),
            repeatOccurrences = (values["repeatOccurrences"] as? Number)?.toInt(),
            listId = values["listId"] as? String,
            tags = (values["tags"] as? List<*>).orEmpty().mapNotNull { it as? String },
            subtasks = (values["subtasks"] as? List<*>).orEmpty().mapNotNull(::subtask),
            attachments = (values["attachments"] as? List<*>).orEmpty().mapNotNull(::attachment),
            locationReminder = location(values["locationReminder"]),
            assigneeIds = (values["assigneeIds"] as? List<*>).orEmpty().mapNotNull { it as? String },
            completedAt = (values["completedAt"] as? Number)?.toLong()
        )
    }

    private fun subtask(value: Any?): Subtask? = (value as? Map<*, *>)?.let { map ->
        Subtask(map["id"] as? String ?: return null, map["title"] as? String ?: return null, map["completed"] as? Boolean ?: false)
    }
    private fun attachment(value: Any?): TaskAttachment? = (value as? Map<*, *>)?.let { map ->
        TaskAttachment(map["id"] as? String ?: return null, map["displayName"] as? String ?: return null,
            map["uri"] as? String ?: return null, map["mimeType"] as? String, (map["sizeBytes"] as? Number)?.toLong())
    }
    private fun location(value: Any?): LocationReminder? = (value as? Map<*, *>)?.let { map ->
        LocationReminder(map["label"] as? String ?: return null, LocationTrigger.fromStorageValue(map["trigger"] as? String),
            (map["latitude"] as? Number)?.toDouble(), (map["longitude"] as? Number)?.toDouble(), (map["radiusMeters"] as? Number)?.toFloat() ?: 150f)
    }
}
