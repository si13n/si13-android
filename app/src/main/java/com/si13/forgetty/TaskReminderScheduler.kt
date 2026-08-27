package com.si13.forgetty

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface TaskMutationObserver {
    fun onTaskChanged(task: Task)
    fun onTaskDeleted(taskId: String)
}

class AndroidTaskMutationObserver(private val context: Context) : TaskMutationObserver {
    override fun onTaskChanged(task: Task) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = reminderPendingIntent(context, task.id)
        alarm.cancel(pending)
        val trigger = task.reminderAt
        if (!task.completed && trigger != null && trigger > System.currentTimeMillis()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
        ForgettyWidgetUpdater.updateAll(context)
    }

    override fun onTaskDeleted(taskId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(reminderPendingIntent(context, taskId))
        NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        ForgettyWidgetUpdater.updateAll(context)
    }

    private fun reminderPendingIntent(context: Context, taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            Intent(context, TaskReminderReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val task = TaskRepository.create(context).getTasks().firstOrNull { it.id == taskId } ?: return@runCatching
                if (task.completed) return@runCatching
                createReminderChannel(context)
                val open = PendingIntent.getActivity(
                    context, task.id.hashCode(),
                    Intent(context, MainActivity::class.java).putExtra(EXTRA_TASK_ID, task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val complete = PendingIntent.getBroadcast(
                    context, task.id.hashCode(),
                    Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_COMPLETE).putExtra(EXTRA_TASK_ID, task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val snooze = PendingIntent.getBroadcast(
                    context, task.id.hashCode() xor 31,
                    Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_TASK_ID, task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle(task.text)
                    .setContentText(task.listName + task.dueDate?.let { " · $it" }.orEmpty())
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .addAction(0, context.getString(R.string.complete), complete)
                    .addAction(0, context.getString(R.string.remind_later), snooze)
                    .build()
                if (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
                }
            }
            pending.finish()
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val repository = TaskRepository.create(context)
                val task = repository.getTasks().firstOrNull { it.id == taskId } ?: return@runCatching
                when (intent.action) {
                    ACTION_COMPLETE -> repository.setTaskCompleted(task, true)
                    ACTION_SNOOZE -> repository.updateTask(task.copy(reminderAt = System.currentTimeMillis() + 10 * 60_000L))
                }
                NotificationManagerCompat.from(context).cancel(taskId.hashCode())
            }
            pending.finish()
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { TaskRepository.create(context).getTasks().forEach(AndroidTaskMutationObserver(context)::onTaskChanged) }
            pending.finish()
        }
    }
}

private fun createReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(REMINDER_CHANNEL, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
    )
}

const val EXTRA_TASK_ID = "forgetty_task_id"
const val ACTION_COMPLETE = "com.si13.forgetty.COMPLETE_TASK"
const val ACTION_SNOOZE = "com.si13.forgetty.SNOOZE_TASK"
private const val REMINDER_CHANNEL = "forgetty_task_reminders"
