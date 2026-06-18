package com.personalai.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalai.app.PersonalAiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager alarms don't survive a reboot, so re-arm every still-pending reminder. */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as PersonalAiApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                app.taskRepository.pendingReminders()
                    .filter { task -> (task.reminderAtMillis ?: 0L) > now }
                    .forEach { task -> app.alarmScheduler.schedule(task.id, task.title, task.reminderAtMillis!!) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
