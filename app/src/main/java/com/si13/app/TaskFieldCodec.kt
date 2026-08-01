package com.si13.app

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object TaskFieldCodec {
    private const val ITEM_SEPARATOR = "|"
    private const val FIELD_SEPARATOR = ","

    fun encodeStrings(values: List<String>): String = values.joinToString(ITEM_SEPARATOR, transform = ::encode)

    fun decodeStrings(value: String?): List<String> = value.orEmpty()
        .takeIf(String::isNotBlank)
        ?.split(ITEM_SEPARATOR)
        ?.map(::decode)
        .orEmpty()

    fun encodeSubtasks(values: List<Subtask>): String = values.joinToString(ITEM_SEPARATOR) {
        listOf(encode(it.id), encode(it.title), it.completed.toString()).joinToString(FIELD_SEPARATOR)
    }

    fun decodeSubtasks(value: String?): List<Subtask> = records(value).mapNotNull { fields ->
        if (fields.size < 3) null else Subtask(decode(fields[0]), decode(fields[1]), fields[2].toBoolean())
    }

    fun encodeAttachments(values: List<TaskAttachment>): String = values.joinToString(ITEM_SEPARATOR) {
        listOf(
            encode(it.id), encode(it.displayName), encode(it.uri), encode(it.mimeType.orEmpty()),
            it.sizeBytes?.toString().orEmpty()
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decodeAttachments(value: String?): List<TaskAttachment> = records(value).mapNotNull { fields ->
        if (fields.size < 5) null else TaskAttachment(
            id = decode(fields[0]),
            displayName = decode(fields[1]),
            uri = decode(fields[2]),
            mimeType = decode(fields[3]).ifBlank { null },
            sizeBytes = fields[4].toLongOrNull()
        )
    }

    fun encodeLocation(value: LocationReminder?): String? = value?.let {
        listOf(
            encode(it.label), it.trigger.storageValue, it.latitude?.toString().orEmpty(),
            it.longitude?.toString().orEmpty(), it.radiusMeters.toString()
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decodeLocation(value: String?): LocationReminder? {
        val fields = value?.split(FIELD_SEPARATOR).orEmpty()
        if (fields.size < 5) return null
        return LocationReminder(
            label = decode(fields[0]),
            trigger = LocationTrigger.fromStorageValue(fields[1]),
            latitude = fields[2].toDoubleOrNull(),
            longitude = fields[3].toDoubleOrNull(),
            radiusMeters = fields[4].toFloatOrNull() ?: 150f
        )
    }

    private fun records(value: String?): List<List<String>> = value.orEmpty()
        .takeIf(String::isNotBlank)
        ?.split(ITEM_SEPARATOR)
        ?.map { it.split(FIELD_SEPARATOR) }
        .orEmpty()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
