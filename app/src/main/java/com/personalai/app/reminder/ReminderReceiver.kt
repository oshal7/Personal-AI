package com.personalai.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalai.app.notification.ReminderNotifier

/** Receives the alarm broadcast scheduled by [AlarmScheduler] and posts the reminder notification. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (taskId == -1L || title.isNullOrBlank()) return
        ReminderNotifier(context).notify(taskId, title)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
    }
}
