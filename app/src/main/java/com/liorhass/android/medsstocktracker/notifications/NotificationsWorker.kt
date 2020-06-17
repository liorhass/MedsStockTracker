// https://www.reddit.com/r/androiddev/comments/gcwkre/android_notification_as_deep_as_possible/?$deep_link=true
// https://itnext.io/android-notification-channel-as-deep-as-possible-1a5b08538c87
// https://itnext.io/android-notification-styling-cc6b0bb86021
//
package com.liorhass.android.medsstocktracker.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liorhass.android.medsstocktracker.MainActivity
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.AppDatabase
import com.liorhass.android.medsstocktracker.database.Medicine
import com.liorhass.android.medsstocktracker.model.calculateExpectedRunOutDateAndTime
import com.liorhass.android.medsstocktracker.model.calculateExpectedRunOutOfDateString
import com.liorhass.android.medsstocktracker.model.getDaysLeft
import com.liorhass.android.medsstocktracker.settings.SettingsFragment
import com.liorhass.android.medsstocktracker.util.parseHour
import com.liorhass.android.medsstocktracker.util.parseMinute
import timber.log.Timber
import java.util.*

class NotificationsWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val PREF_DAY_OF_LAST_CHECK = "pdolc"
    }

    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    override suspend fun doWork(): Result {
        Timber.d("===  doWork()  sec=${System.currentTimeMillis()/1000} ===")

        if (isNowTimeForNotifications()) {
            // Read all medicines from the database
            val medicines = AppDatabase.getInstance(context).medicineDao.getAllMedicines()

            generateAllNotifications(getMedicinesWithNotifications(medicines))
        }

        return Result.success()
    }

    /**
     * Check if we should generate notifications. This should be done only once a day, and only
     * after a certain time configured by the user (default to 06:00).
     */
    private fun isNowTimeForNotifications(): Boolean {
        val dayOfLastCheck = sharedPreferences.getLong(PREF_DAY_OF_LAST_CHECK, 0)
        val now = System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(now) // the amount of time in milliseconds to add to UTC to get local time.
        val dayNow = (now + offset) / (1000 * 3600 * 24)
        if (dayNow != dayOfLastCheck) {
            val defaultEarliestTime = "07:00"
            // We haven't generated notifications today yet. Check if the time has arrived
            val earliestTimeStr =
                sharedPreferences.getString(applicationContext.getString(
                    R.string.pref_notifications_time_key), defaultEarliestTime)
            val earliestTimeMsec: Int =
                1000 * (3600 * parseHour(earliestTimeStr ?: defaultEarliestTime) +
                        60 * parseMinute(earliestTimeStr ?: defaultEarliestTime))
            return if ((now + offset) % (1000 * 3600 * 24) > earliestTimeMsec) {
                       sharedPreferences.edit().putLong(PREF_DAY_OF_LAST_CHECK, dayNow).apply()
                       true
                   } else {
                       // It's too early in the day
                       false
                   }
        } else {
            return false
        }
    }

    private fun getMedicinesWithNotifications(medicines: List<Medicine>?): MedicinesWithNotifications {
        val sortedMedicines = medicines?.sortedBy { it.calculateExpectedRunOutDateAndTime() }
        val daysLeftCriticalStr = sharedPreferences.getString(
            applicationContext.getString(R.string.pref_meds_stock_critical_threshold_key),
            applicationContext.getString(R.string.pref_meds_stock_critical_threshold_default_value))
        val daysLeftWarningStr = sharedPreferences.getString(
            applicationContext.getString(R.string.pref_meds_stock_warning_threshold_key),
            applicationContext.getString(R.string.pref_meds_stock_warning_threshold_default_value))
        val daysLeftCritical = daysLeftCriticalStr?.toIntOrNull() ?: SettingsFragment.DEFAULT_CRITICAL_THRESHOLD_IN_DAYS
        val daysLeftWarning  = daysLeftWarningStr?.toIntOrNull() ?: SettingsFragment.DEFAULT_WARNING_THRESHOLD_IN_DAYS // Don't frown. These 5 and 14 are a fallback of a fallback. Will never be used. Seriously.

        val medicinesWithCritical = mutableListOf<Medicine>()
        val medicinesWithWarnings = mutableListOf<Medicine>()
        sortedMedicines?.forEach { medicine ->
            Timber.d("Medicine: ${medicine.name}  expect-to-run-out-at: ${medicine.calculateExpectedRunOutOfDateString(context)}")
            if (medicine.getDaysLeft() <= daysLeftCritical) {
                medicinesWithCritical.add(medicine)
            } else if (medicine.getDaysLeft() <= daysLeftWarning) {
                medicinesWithWarnings.add(medicine)
            }
        }
        return MedicinesWithNotifications(medicinesWithCritical, medicinesWithWarnings)
    }

    class MedicinesWithNotifications(val criticalList: List<Medicine>, val warningList: List<Medicine>) {
        val isEmpty: Boolean
            get() {
                return criticalList.isEmpty() and warningList.isEmpty()
            }
    }

    private fun generateAllNotifications(medicines: MedicinesWithNotifications) {
        if (medicines.isEmpty) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android v8 and up we generate 2 different notifications: one for critically low
            // stocks, and one for "normal" (a.k.a. "warning") low stock. Each has it's own channel,
            // and the user can set different settings (such as ringtone) to each of them via the
            // system's notification settings screen. We don't provide the ringtone, vibrate and led
            // settings because the system ignores them anyway.
            Timber.d("generateAllNotifications(): critical-len:${medicines.criticalList.size}  waring-len:${medicines.warningList.size}")
            generateNotificationsForChannel(
                medicines.criticalList,
                CRITICAL_CHANNEL_ID,
                context.getString(R.string.notification_title_for_critical),
                context.getString(R.string.notification_critical_meds_list_title),
                null,
                null,
                false,
                notificationManager,
                1
            )
            generateNotificationsForChannel(
                medicines.warningList,
                WARNING_CHANNEL_ID,
                context.getString(R.string.notification_title_for_warning),
                context.getString(R.string.notification_warn_meds_list_title),
                null,
                null,
                false,
                notificationManager,
                2
            )
        } else {
            // Ringtone
            val ringtoneUriStr = sharedPreferences.getString(applicationContext.getString(R.string.pref_notifications_ringtone_key), "")
            val ringtoneUri = if (!TextUtils.isEmpty(ringtoneUriStr)) Uri.parse(ringtoneUriStr) else null
            // Vibration
            val vibrate = sharedPreferences.getBoolean(applicationContext.getString(R.string.pref_notifications_vibrate_key), true)
            val vibratePattern = if (vibrate) longArrayOf(0, 200, 500, 200) else null
            // LED
            val led = sharedPreferences.getBoolean(applicationContext.getString(R.string.pref_notifications_led_key), true)
            generateNotificationsForChannel(
                medicines.criticalList + medicines.warningList,
                WARNING_CHANNEL_ID,
                context.getString(R.string.notification_warn_meds_list_title),
                context.getString(R.string.notification_title_for_warning),
                ringtoneUri,
                vibratePattern,
                led,
                notificationManager,
                2
            )
        }
    }

    private fun generateNotificationsForChannel(
        medicines: List<Medicine>,
        channelId: String,
        notificationTittle: String,
        notificationMedsListTittle: String,
        ringtoneUri: Uri?,
        vibratePattern: LongArray?,
        led: Boolean,
        notificationManager: NotificationManagerCompat,
        notificationId: Int
    ) {
        if (medicines.isEmpty()) {
            return
        }

        val notificationBuilder =
                NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_status_bar)
//            .setLargeIcon(BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_launcher))
            .setContentTitle(notificationTittle)
//            .setContentText(textContent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Ringtone
        if (ringtoneUri != null) {
            notificationBuilder.setSound(ringtoneUri)
        }
        // Vibration
        if (vibratePattern != null) {
            notificationBuilder.setVibrate(vibratePattern)
        }
        // LED
        if (led) {
            notificationBuilder.setLights(Color.RED, 300, 3000)
        }

        val inboxStyle: NotificationCompat.InboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle(notificationMedsListTittle) // Sets a title for the Inbox in expanded layout
        // Put medicines in the expanded layout
        Timber.v("vvvv")
        for (medicine in medicines) {
            Timber.d(applicationContext.getString(R.string.low_stock_notification, medicine.name, medicine.getDaysLeft()))
            inboxStyle.addLine(applicationContext.getString(R.string.low_stock_notification,
                            medicine.name, medicine.getDaysLeft()))
        }
        Timber.v("^^^^")
        notificationBuilder.setStyle(inboxStyle) // Set the notification layout

        // Create an explicit intent for our MainActivity
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, 0)
        notificationBuilder.setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}

