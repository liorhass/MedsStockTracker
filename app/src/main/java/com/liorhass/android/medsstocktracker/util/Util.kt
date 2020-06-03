package com.liorhass.android.medsstocktracker.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.preference.PreferenceManager
import com.liorhass.android.medsstocktracker.R
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

fun hideSoftKeyboard(activity: Activity) {
    val inputManager =
        activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val windowToken =
        if (null == activity.currentFocus) null else activity.currentFocus!!.windowToken
    inputManager.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
}

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * See: https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
 */
open class OneTimeEvent<out T>(private val content: T) {
    private var hasBeenHandled = false
    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            Timber.v("Event#getContentIfNotHandled(): null")
            null
        } else {
            Timber.v("Event#getContentIfNotHandled(): content")
            hasBeenHandled = true
            content
        }
    }
}
data class NavigationEventWithNoArguments(val destination: Int)
data class NavigationEventWithLongArgument(val destination: Int, val arg: Long)
object NavigationDestinations {
    const val NAVIGATION_DESTINATION_MEDICINE_LIST_FRAGMENT = 1
    const val NAVIGATION_DESTINATION_MEDICINE_DETAILS_FRAGMENT = 2
}


/**
 * Return the hour of a time string
 * @param value Time in the format "16:45"
 * @return hour (16 in the example above)
 */
fun parseHour(value: String): Int {
    return try {
        val time = value.split(":").toTypedArray()
        time[0].toInt()
    } catch (e: Exception) {
        0
    }
}

/**
 * Return the minute of a time string
 * @param value Time in the format "16:45"
 * @return minute (45 in the example above)
 */
fun parseMinute(value: String): Int {
    return try {
        val time = value.split(":").toTypedArray()
        time[1].toInt()
    } catch (e: Exception) {
        0
    }
}

@SuppressLint("SimpleDateFormat")
private val dateFormatForDayAndMonth = SimpleDateFormat("EEE, d MMM") // Fri, 7 Apr   //todo: should be replaced w/ locale aware formatter
fun humanFriendlyDate(dateAndTime: Long, context: Context): String {
    val daysFromNow = CalendarCalculator.diffInDays(System.currentTimeMillis(), dateAndTime)
    val msg = when {
        daysFromNow == 0 -> context.getString(R.string.today)
        daysFromNow == 1 -> context.getString(R.string.tomorrow)
        daysFromNow == -1 -> context.getString(R.string.yesterday)
        daysFromNow < -1 -> context.getString(R.string.days_ago, -daysFromNow)
        else -> context.getString(R.string.in_days, daysFromNow)
    }
    return "${dateFormatForDayAndMonth.format(Date(dateAndTime))} ($msg)"
}

// We use this object only as an optimization to save the instantiation of the Calendars and Dates
// on every call to diffInDays()
object CalendarCalculator {
    private val calBefore: Calendar = Calendar.getInstance()
    private val dateBefore: Date = Date()
    private val calAfter: Calendar = Calendar.getInstance()
    private val dateAfter: Date = Date()

    /**
     * Get two milliseconds times, and calculates the difference in days between them (how many
     * midnight crossings are between them). If they are both on the same calendar day, the result
     * is 0. If they are one minute apart, but on two sides of midnight, the result is 1 (or -1).
     */
    fun diffInDays(timeBefore: Long, timeAfter: Long): Int {
        dateBefore.time = timeBefore
        dateAfter.time = timeAfter
        calBefore.time = dateBefore
        calAfter.time = dateAfter

        return 365 * (calAfter.get(Calendar.YEAR) - calBefore.get(Calendar.YEAR)) +
                (calAfter.get(Calendar.DAY_OF_YEAR) - calBefore.get(Calendar.DAY_OF_YEAR))
    }
}

fun setUserSelectedTheme(context: Context) {
    // Get the desired mode as set by the user in our sharedPreferences
    val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    val theme = sharedPreferences.getString(context.getString(R.string.pref_theme_key),
        context.getString(R.string.pref_theme_auto_value)) // default is "Auto"
    Timber.d("setTheme() Setting theme to $theme")

    val mode = when(theme) {
        // On Android 10+ "auto" means follow the system's theme settings. On older systems it means
        // to follow battery saving mode (dark theme when battery becomes low)
        context.getString(R.string.pref_theme_auto_value) ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                {MODE_NIGHT_FOLLOW_SYSTEM} else {MODE_NIGHT_AUTO_BATTERY}
        context.getString(R.string.pref_theme_light_value) -> MODE_NIGHT_NO
        else -> MODE_NIGHT_YES
    }
    setDefaultNightMode(mode)
}


