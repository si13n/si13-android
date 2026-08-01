package com.si13.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class WidgetKind { COMPACT, TODAY, PROGRESS }

abstract class ForgettyWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            updateWidgets(context, manager, ids, kind)
            pending.finish()
        }
    }
}

class CompactForgettyWidget : ForgettyWidgetProvider(WidgetKind.COMPACT)
class TodayForgettyWidget : ForgettyWidgetProvider(WidgetKind.TODAY)
class ProgressForgettyWidget : ForgettyWidgetProvider(WidgetKind.PROGRESS)

object ForgettyWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            CompactForgettyWidget::class.java to WidgetKind.COMPACT,
            TodayForgettyWidget::class.java to WidgetKind.TODAY,
            ProgressForgettyWidget::class.java to WidgetKind.PROGRESS
        ).forEach { (provider, kind) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                updateWidgets(context, manager, ids, kind)
            }
        }
    }
}

private suspend fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, kind: WidgetKind) {
    val tasks = runCatching { TaskRepository.create(context).getTasks() }.getOrDefault(emptyList())
    val active = tasks.filterNot(Task::completed)
    val today = active.filter { it.dueDate == LocalDate.now().toString() }
    val completed = tasks.count(Task::completed)
    ids.forEach { id ->
        val views = RemoteViews(context.packageName, R.layout.widget_forgetty).apply {
            val title = when (kind) {
                WidgetKind.COMPACT -> context.getString(R.string.widget_tasks_remaining, active.size)
                WidgetKind.TODAY -> context.getString(R.string.widget_today_count, today.size)
                WidgetKind.PROGRESS -> context.getString(R.string.home_progress_summary, completed, tasks.size)
            }
            val featured = when (kind) {
                WidgetKind.TODAY -> today.firstOrNull()
                WidgetKind.COMPACT -> active.firstOrNull()
                WidgetKind.PROGRESS -> null
            }
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_task, featured?.text ?: context.getString(if (active.isEmpty()) R.string.progress_all_done else R.string.open_forgetty))
            setProgressBar(R.id.widget_progress, tasks.size.coerceAtLeast(1), completed, false)
            setViewVisibility(R.id.widget_complete, if (featured == null) android.view.View.GONE else android.view.View.VISIBLE)
            setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(
                context, id xor 17, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            featured?.let { task ->
                setOnClickPendingIntent(R.id.widget_complete, PendingIntent.getBroadcast(
                    context, task.id.hashCode(), Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_COMPLETE).putExtra(EXTRA_TASK_ID, task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }
        }
        manager.updateAppWidget(id, views)
    }
}
