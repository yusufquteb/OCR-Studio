package com.ocrstudio.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                WorkerConstants.NOTIFICATION_CHANNEL_ID,
                "OCR processing",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WorkerConstants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        contentText: String,
        progressPercent: Int,
        channelId: String = WorkerConstants.NOTIFICATION_CHANNEL_ID
    ): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent < 0)
            .build()
    }
}
