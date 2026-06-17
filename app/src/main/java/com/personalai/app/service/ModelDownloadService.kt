package com.personalai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.personalai.app.PersonalAiApplication
import com.personalai.app.R
import com.personalai.app.data.repository.DownloadProgress
import com.personalai.app.domain.model.ModelRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs the (multi-GB, multi-minute) model download as a foreground service so it keeps going
 * when the screen turns off or the user switches to another app — both of which would cancel a
 * coroutine scoped to the ViewModel/Activity instead. `stopWithTask="false"` in the manifest also
 * keeps it alive if the app is swiped away from Recents.
 */
class ModelDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelRepository = (application as PersonalAiApplication).modelRepository
        val model = ModelRegistry.default

        startForeground(NOTIFICATION_ID, buildProgressNotification(percent = null))

        serviceScope.launch {
            modelRepository.downloadState.collect { progress ->
                when (progress) {
                    null -> Unit
                    is DownloadProgress.InProgress -> {
                        val percent = if (progress.totalBytes > 0) {
                            (progress.bytesRead * 100 / progress.totalBytes).toInt().coerceIn(0, 100)
                        } else null
                        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(percent))
                    }
                    is DownloadProgress.Complete -> {
                        postFinalNotification(getString(R.string.model_download_notification_complete))
                        stopSelf()
                    }
                    is DownloadProgress.Failed -> {
                        postFinalNotification(getString(R.string.model_download_error, progress.message))
                        stopSelf()
                    }
                }
            }
        }

        modelRepository.ensureBackgroundDownload(model, serviceScope)

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.model_download_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(percent: Int?): Notification {
        val text = if (percent != null) {
            getString(R.string.model_download_progress, percent)
        } else {
            getString(R.string.model_download_notification_starting)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.model_download_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    private fun postFinalNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.model_download_notification_title))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        stopForeground(STOP_FOREGROUND_DETACH)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
