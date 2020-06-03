package com.liorhass.android.medsstocktracker.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.notifications.areNotificationsEnabled
import com.liorhass.android.medsstocktracker.notifications.scheduleOrCancelNotificationsWork
import com.liorhass.android.medsstocktracker.util.setUserSelectedTheme
import com.takisoft.preferencex.EditTextPreference
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.TimePickerPreference
import timber.log.Timber

// See https://github.com/Gericop/Android-Support-Preference-V7-Fix

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val DEFAULT_WARNING_THRESHOLD_IN_DAYS = 14
        const val DEFAULT_CRITICAL_THRESHOLD_IN_DAYS = 5
    }
    private var v8NotificationsEnabled: Boolean? = null

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v8NotificationsEnabled = areNotificationsEnabled(context)
        }

        populateSummaryLines()

        // Call onSharedPreferenceChanged() on every change made to settings.
        PreferenceManager.getDefaultSharedPreferences(activity).
                registerOnSharedPreferenceChangeListener(this)

        // Notifications settings (ringtone, vibrate, etc), were moved as of Android v8 to the
        // system settings. Google in their infinite wisdom and constant strive to help developers,
        // require that we maintain different settings screens and code for both old and new
        // systems. Because why not?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationsPreference =
                findPreference<Preference>(getString(R.string.pref_notifications_key))
            notificationsPreference?.isVisible = true
            notificationsPreference?.setOnPreferenceClickListener { _ ->
                openSystemNotificationSettings()
                true
            }
            val timePicker = findPreference<TimePickerPreference>(
                                    getString(R.string.pref_notifications_time_key2))
            timePicker?.isVisible = true
            timePicker?.isEnabled = v8NotificationsEnabled == true
        } else {
            findPreference<Preference>(getString(R.string.pref_notifications_category_key))?.
                isVisible = true
        }

/*
        // Do something when the "Debug preference" is clicked.
        val dbgPreference = findPreference<Preference>("debug")
        dbgPreference?.setOnPreferenceClickListener {it ->
            Timber.d("$$$$$$$$$$$$$$$$$$$$$$$$$$")
            Timber.d("Canceling notifications job")
            WorkManager.getInstance(it.context).cancelUniqueWork("medsStockTrackerNotificationWorker")
            scheduleOrCancelNotificationsWork(requireActivity())
            true
        }
*/
    }

    private fun populateSummaryLines() {
        val theme: ListPreference? = findPreference(getString(R.string.pref_theme_key))
        theme?.summaryProvider =
            Preference.SummaryProvider<ListPreference> {
                it.entry
            }

        val stockCriticalThreshold: EditTextPreference? =
            findPreference(getString(R.string.pref_meds_stock_critical_threshold_key))
        stockCriticalThreshold?.summaryProvider =
            Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                if (TextUtils.isEmpty(text)) {
                    getString(R.string.pref_not_set)
                } else {
                    val days= text.toIntOrNull() ?: DEFAULT_CRITICAL_THRESHOLD_IN_DAYS
                    resources.getQuantityString(
                        R.plurals.pref_meds_stock_threshold_critical_summary, days, days)
                }
            }

        val stockWarningThreshold: EditTextPreference? =
            findPreference(getString(R.string.pref_meds_stock_warning_threshold_key))
        stockWarningThreshold?.summaryProvider =
            Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                if (TextUtils.isEmpty(text)) {
                    getString(R.string.pref_not_set)
                } else {
                    val days= text.toIntOrNull() ?: DEFAULT_WARNING_THRESHOLD_IN_DAYS
                    resources.getQuantityString(
                        R.plurals.pref_meds_stock_threshold_warning_summary, days, days)
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationsPreference: Preference? =
                findPreference(getString(R.string.pref_notifications_key))
            if (v8NotificationsEnabled == true) {
                notificationsPreference?.summary = getString(R.string.pref_notifications_enabled_summary)
            } else {
                notificationsPreference?.summary = getString(R.string.pref_notifications_disabled_summary)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(activity).
                unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Timber.d("onSharedPreferenceChanged() key=$key")
        if (key?.equals(getString(R.string.pref_theme_key)) == true) {
            setUserSelectedTheme(requireContext())
        } else {
            // Changes to these settings require that we re-evaluate if we need to set a notification
            scheduleOrCancelNotificationsWork(requireActivity())
        }
    }

    private fun openSystemNotificationSettings() {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context?.packageName)
        } else {
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("app_package", context?.packageName)
            intent.putExtra("app_uid", context?.applicationInfo?.uid)
        }
        context?.startActivity(intent)
    }
}