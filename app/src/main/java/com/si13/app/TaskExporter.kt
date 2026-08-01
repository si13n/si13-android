package com.si13.app

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

object TaskExporter {
    fun createJson(context: Context, tasks: List<Task>): android.net.Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "forgetty-tasks.json")
        file.writeText(buildString {
            append("{\"version\":1,\"tasks\":[")
            tasks.forEachIndexed { index, task ->
                if (index > 0) append(',')
                append("{\"id\":\"").append(task.id.json()).append("\",")
                append("\"title\":\"").append(task.text.json()).append("\",")
                append("\"completed\":").append(task.completed).append(',')
                append("\"priority\":\"").append(task.priority.storageValue).append("\",")
                append("\"dueDate\":").append(task.dueDate?.let { "\"${it.json()}\"" } ?: "null").append(',')
                append("\"list\":\"").append(task.listName.json()).append("\",")
                append("\"notes\":\"").append(task.note.json()).append("\",")
                append("\"tags\":[").append(task.tags.joinToString { "\"${it.json()}\"" }).append("]}")
            }
            append("]}")
        })
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun String.json(): String = replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r")
}
