package com.ocrstudio.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val EXTRA_JOB_ID = "job_id"

    /** Deep-links a tap on the processing notification back into JobProgressScreen for [jobId].
     *  Built from the launcher intent rather than referencing MainActivity directly, since this
     *  module (:worker) doesn't depend on :app. */
    fun jobPendingIntent(context: Context, jobId: String): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launchIntent.action = android.content.Intent.ACTION_VIEW
        launchIntent.putExtra(EXTRA_JOB_ID, jobId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, jobId.hashCode(), launchIntent, flags)
    }

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
        channelId: String = WorkerConstants.NOTIFICATION_CHANNEL_ID,
        contentIntent: PendingIntent? = null
    ): android.app.Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), progressPercent < 0)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
    }
}
