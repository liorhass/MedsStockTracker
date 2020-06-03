package com.liorhass.android.medsstocktracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.liorhass.android.medsstocktracker.R
import timber.log.Timber
import java.util.concurrent.TimeUnit


private const val NOTIFICATIONS_WORK_UNIQUE_NAME = "medsStockTrackerNotificationWorker"
fun scheduleOrCancelNotificationsWork(context: Context) {
    if (areNotificationsEnabled(context)) {
        // Notifications are enabled
        Timber.d("Notifications are enabled - Scheduling periodic worker")

        val periodicWorkRequest =
            PeriodicWorkRequestBuilder<NotificationsWorker>(20, TimeUnit.MINUTES)
                .build()

        // Enqueue a periodic work request, but only if none is already active.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOTIFICATIONS_WORK_UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest)

    } else {
        // Notifications are disabled
        Timber.d("Canceling notifications job")
        WorkManager.getInstance(context).cancelUniqueWork(NOTIFICATIONS_WORK_UNIQUE_NAME)
    }
}

fun areNotificationsEnabled(context: Context?): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (context == null) {
            false
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    } else {
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context?.getString(R.string.pref_enable_notifications_key), true)
    }
}

/**
 * Create our notification channels and register them with the system
 * This should be called only once after the installation of the app (the first time it is run)
 */
fun createNotificationChannels(context: Context) {
    // https://developer.android.com/training/notify-user/build-notification#Priority

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = NotificationManagerCompat.from(context)

        //
        // Critical channel - for medicines with critically low stock
        //
        val criticalChannel = NotificationChannel(
            CRITICAL_CHANNEL_ID,
            context.getString(R.string.critical_notifications_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.critical_notifications_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 500, 200)
            enableLights(true)
            lightColor = Color.RED
        }
        // Register the channel with the system
        notificationManager.createNotificationChannel(criticalChannel)

        //
        // Warning channel - for medicines with "normal" low stock
        //
        val warningChannel = NotificationChannel(
            WARNING_CHANNEL_ID,
            context.getString(R.string.warning_notifications_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.warning_notifications_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 500, 200)
            enableLights(true)
            lightColor = Color.RED
        }
        // Register the channel with the system
        notificationManager.createNotificationChannel(warningChannel)
    }
}
const val CRITICAL_CHANNEL_ID = "cc_id"
const val WARNING_CHANNEL_ID = "nc_id"

