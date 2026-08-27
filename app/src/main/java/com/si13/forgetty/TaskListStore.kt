package com.si13.forgetty

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class TaskListDefinition(
    val id: String,
    val name: String,
    val color: String,
    val shared: Boolean = false,
    val protected: Boolean = false,
    val ownerId: String? = null,
    val members: List<SharedListMember> = emptyList()
)

data class SharedListMember(
    val userId: String,
    val displayName: String,
    val role: SharedListRole
)

enum class SharedListRole { OWNER, EDITOR, VIEWER }

class TaskListStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getLists(): List<TaskListDefinition> = preferences.getString(KEY_LISTS, null)?.let { encoded ->
        if (encoded.isBlank()) emptyList() else encoded.split(RECORD_SEPARATOR).mapNotNull(::decode)
    } ?: DEFAULT_LISTS

    fun create(name: String, color: String): TaskListDefinition {
        val cleanName = uniqueName(name)
        val list = TaskListDefinition(UUID.randomUUID().toString(), cleanName, color)
        save(getLists() + list)
        return list
    }

    fun rename(id: String, name: String): Pair<String, TaskListDefinition>? {
        val current = getLists()
        val old = current.firstOrNull { it.id == id } ?: return null
        val change = update(id, name, old.color) ?: return null
        return change.oldName to change.updated
    }

    fun update(id: String, name: String, color: String): TaskListChange? {
        val current = getLists()
        val old = current.firstOrNull { it.id == id } ?: return null
        val updated = old.copy(name = uniqueName(name, excludingId = id), color = color)
        save(current.map { if (it.id == id) updated else it })
        return TaskListChange(old.name, updated)
    }

    fun changeColor(id: String, color: String) {
        save(getLists().map { if (it.id == id) it.copy(color = color) else it })
    }

    fun delete(id: String): TaskListDefinition? {
        val current = getLists()
        val target = current.firstOrNull { it.id == id } ?: return null
        save(current.filterNot { it.id == id })
        return target
    }

    private fun uniqueName(raw: String, excludingId: String? = null): String {
        val base = raw.trim().take(40).ifBlank { DEFAULT_TASK_LIST }
        val names = getLists().filterNot { it.id == excludingId }.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in names) return base
        var suffix = 2
        while (true) {
            val suffixText = " $suffix"
            val candidate = base.take(MAX_LIST_NAME_LENGTH - suffixText.length) + suffixText
            if (candidate.lowercase() !in names) return candidate
            suffix++
        }
    }

    private fun save(values: List<TaskListDefinition>) {
        preferences.edit().putString(KEY_LISTS, values.joinToString(RECORD_SEPARATOR, transform = ::encode)).apply()
    }

    private fun encode(value: TaskListDefinition): String = listOf(
        esc(value.id), esc(value.name), esc(value.color), value.shared.toString(),
        value.protected.toString(), esc(value.ownerId.orEmpty())
    ).joinToString(FIELD_SEPARATOR)

    private fun decode(value: String): TaskListDefinition? {
        val fields = value.split(FIELD_SEPARATOR)
        if (fields.size < 6) return null
        return TaskListDefinition(
            id = unesc(fields[0]),
            name = unesc(fields[1]),
            color = unesc(fields[2]),
            shared = fields[3].toBoolean(),
            protected = fields[4].toBoolean(),
            ownerId = unesc(fields[5]).ifBlank { null }
        )
    }

    private fun esc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun unesc(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val NAME = "forgetty_task_lists"
        private const val KEY_LISTS = "lists"
        private const val RECORD_SEPARATOR = "|"
        private const val FIELD_SEPARATOR = ","
        private const val MAX_LIST_NAME_LENGTH = 40
        val COLORS = listOf("#5268D8", "#477F8D", "#9B577B", "#347A62", "#956B24", "#6B44A8", "#C45252", "#3D7A5E")
        val DEFAULT_LISTS = listOf(
            TaskListDefinition("personal", "Personal", "#477F8D"),
            TaskListDefinition("work", "Work", "#5268D8"),
            TaskListDefinition("shared", "Shared", "#9B577B", shared = true),
            TaskListDefinition("shopping", "Shopping", "#347A62")
        )

        fun create(context: Context) = TaskListStore(context)
    }
}

data class TaskListChange(val oldName: String, val updated: TaskListDefinition)
