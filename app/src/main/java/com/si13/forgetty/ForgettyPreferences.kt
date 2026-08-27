package com.si13.forgetty

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek

enum class HomeDisplayMode { LIST, CALENDAR }

data class NotificationPreferences(
    val taskReminders: Boolean,
    val overdueReminders: Boolean,
    val dailySummary: Boolean,
    val sharedListUpdates: Boolean
)

class ForgettyPreferences private constructor(
    private val preferences: SharedPreferences
) {
    var selectedList: String
        get() = preferences.getString(KEY_SELECTED_LIST, ALL_TASKS).orEmpty().ifBlank { ALL_TASKS }
        set(value) = edit(KEY_SELECTED_LIST, value)

    var homeFilter: String
        get() = preferences.getString(KEY_HOME_FILTER, "all") ?: "all"
        set(value) = edit(KEY_HOME_FILTER, value)

    var sortMode: String
        get() = preferences.getString(KEY_SORT_MODE, "due_date") ?: "due_date"
        set(value) = edit(KEY_SORT_MODE, value)

    var displayMode: HomeDisplayMode
        get() = runCatching {
            HomeDisplayMode.valueOf(preferences.getString(KEY_DISPLAY_MODE, null) ?: "LIST")
        }.getOrDefault(HomeDisplayMode.LIST)
        set(value) = edit(KEY_DISPLAY_MODE, value.name)

    var showCompleted: Boolean
        get() = preferences.getBoolean(KEY_SHOW_COMPLETED, false)
        set(value) = edit(KEY_SHOW_COMPLETED, value)

    var confirmBeforeDeleting: Boolean
        get() = preferences.getBoolean(KEY_CONFIRM_DELETE, true)
        set(value) = edit(KEY_CONFIRM_DELETE, value)

    var defaultList: String
        get() = preferences.getString(KEY_DEFAULT_LIST, DEFAULT_TASK_LIST).orEmpty()
            .ifBlank { DEFAULT_TASK_LIST }
        set(value) = edit(KEY_DEFAULT_LIST, value)

    var defaultFilter: String
        get() = preferences.getString(KEY_DEFAULT_FILTER, "all") ?: "all"
        set(value) = edit(KEY_DEFAULT_FILTER, value)

    var defaultReminderMinutes: Int
        get() = preferences.getInt(KEY_DEFAULT_REMINDER, 9 * 60)
        set(value) = edit(KEY_DEFAULT_REMINDER, value.coerceIn(0, 1439))

    var startOfWeek: DayOfWeek
        get() = runCatching {
            DayOfWeek.valueOf(preferences.getString(KEY_START_OF_WEEK, null) ?: "MONDAY")
        }.getOrDefault(DayOfWeek.MONDAY)
        set(value) = edit(KEY_START_OF_WEEK, value.name)

    val notificationPreferences: NotificationPreferences
        get() = NotificationPreferences(
            taskReminders = preferences.getBoolean(KEY_TASK_REMINDERS, true),
            overdueReminders = preferences.getBoolean(KEY_OVERDUE_REMINDERS, true),
            dailySummary = preferences.getBoolean(KEY_DAILY_SUMMARY, false),
            sharedListUpdates = preferences.getBoolean(KEY_SHARED_UPDATES, true)
        )

    fun setTaskReminders(value: Boolean) = edit(KEY_TASK_REMINDERS, value)
    fun setOverdueReminders(value: Boolean) = edit(KEY_OVERDUE_REMINDERS, value)
    fun setDailySummary(value: Boolean) = edit(KEY_DAILY_SUMMARY, value)
    fun setSharedListUpdates(value: Boolean) = edit(KEY_SHARED_UPDATES, value)
    fun clear() = preferences.edit().clear().apply()

    private fun edit(key: String, value: String) = preferences.edit().putString(key, value).apply()
    private fun edit(key: String, value: Boolean) = preferences.edit().putBoolean(key, value).apply()
    private fun edit(key: String, value: Int) = preferences.edit().putInt(key, value).apply()

    companion object {
        const val ALL_TASKS = "All tasks"
        private const val NAME = "forgetty_preferences"
        private const val KEY_SELECTED_LIST = "selected_list"
        private const val KEY_HOME_FILTER = "home_filter"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_SHOW_COMPLETED = "show_completed"
        private const val KEY_CONFIRM_DELETE = "confirm_before_delete"
        private const val KEY_DEFAULT_LIST = "default_list"
        private const val KEY_DEFAULT_FILTER = "default_filter"
        private const val KEY_DEFAULT_REMINDER = "default_reminder_minutes"
        private const val KEY_START_OF_WEEK = "start_of_week"
        private const val KEY_TASK_REMINDERS = "task_reminders"
        private const val KEY_OVERDUE_REMINDERS = "overdue_reminders"
        private const val KEY_DAILY_SUMMARY = "daily_summary"
        private const val KEY_SHARED_UPDATES = "shared_list_updates"

        fun create(context: Context): ForgettyPreferences = ForgettyPreferences(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        )
    }
}
