package com.liorhass.android.medsstocktracker.model

import android.content.Context
import androidx.preference.PreferenceManager
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.Medicine
import com.liorhass.android.medsstocktracker.util.CalendarCalculator
import com.liorhass.android.medsstocktracker.util.humanFriendlyDate
import timber.log.Timber
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.floor

private const val MIN_DAILY_DOSE = 0.00001 // Protect us from daily-dose that is set to 0.0

object DoseFormatter {
    private val formatter: NumberFormat = NumberFormat.getInstance()
    init {
        if (formatter is DecimalFormat) {
            formatter.applyPattern("#.##") //todo: should be replaced w/ locale aware formatter
        }
    }
    fun formatDose(dose: Double): String {
        Timber.d("formatDose(): dose=$dose")
        return formatter.format(dose)
    }
}

fun Medicine.getDaysLeft(): Int {
    var daysLeft = Int.MAX_VALUE // For when daily-dose is 0
    if (this.dailyDose > MIN_DAILY_DOSE) {
        daysLeft = floor(this.estimateCurrentStock() / this.dailyDose).toInt()
    }
    return daysLeft
}

/**
 * Estimate the number of currently available medicines.
 *
 * It is assumed that the whole daily dose is consumed at the beginning of each day. So if the
 * stock is updated at 10:30 in the morning to be 7 pills, and the daily dose is 2 pills, the
 * estimated stock will be 7 on the same day until midnight, and 5 afterwards until the next
 * midnight, etc.
 */
fun Medicine.estimateCurrentStock(): Double {
    val nMidnightCrossingsSinceLastUpdate = CalendarCalculator.diffInDays(
            nAvailableUpdateDateAndTime, System.currentTimeMillis())
    return kotlin.math.max(
        0.0,
        this.nAvailableOriginally - nMidnightCrossingsSinceLastUpdate * this.dailyDose
    )
}

/**
 * @return When the current stock will run out
 */
fun Medicine.calculateExpectedRunOutDateAndTime(): Long {
    if (this.dailyDose < MIN_DAILY_DOSE) {
        // If daily-dose is 0 we don't care what the current-stock is. We will never run out.
        return Long.MAX_VALUE
    }
    val nDaysStockShouldLast: Long = (this.nAvailableOriginally / this.dailyDose).toLong()
    return nAvailableUpdateDateAndTime + nDaysStockShouldLast * 24 * 60 * 60 * 1000
}

val Medicine.expectedRunOutDateAndTime: Long
    get() = this.calculateExpectedRunOutDateAndTime()

fun Medicine.getDailyUsageStr(): String = DoseFormatter.formatDose(this.dailyDose)
fun Medicine.getLastUpdatedStr(context: Context): String = humanFriendlyDate(nAvailableUpdateDateAndTime, context)

/**
 *  return something like "17 pills;  4 days"
 */
fun Medicine.calculateCurrentStockConciseString(context: Context): String? {
    val daysLeft: Int = this.getDaysLeft()
    return if (daysLeft > 1000) {
        // If there are more than 1000 days left, we don't show the days
        String.format(
            context.getString(R.string.remaining_stock_concise_msg_no_days),
            DoseFormatter.formatDose(this.estimateCurrentStock())
        )
    } else {
        String.format(
            context.getString(R.string.remaining_stock_concise_msg),
            DoseFormatter.formatDose(this.estimateCurrentStock()), daysLeft
        )
    }
}

fun Medicine.calculateExpectedRunOutOfDateString(context: Context): String {
    val expectedRunOutDateAndTime: Long = this.expectedRunOutDateAndTime
    return if (expectedRunOutDateAndTime == Long.MAX_VALUE) {
        context.getString(R.string.never)
    } else {
        humanFriendlyDate(expectedRunOutDateAndTime, context)
    }
}

//todo: should cache the boundaries from SharedPreferences in a member variable. Make sure to
//      refresh them whenever the settings change
fun Medicine.calculateStatusImage(context: Context): Int {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val daysLeft: Int = this.getDaysLeft()

    val daysLeftCriticalThreshold: String? = sharedPreferences.getString(
            context.getString(R.string.pref_meds_stock_critical_threshold_key),
            context.getString(R.string.pref_meds_stock_critical_threshold_default_value)
        )
    return if (daysLeft < daysLeftCriticalThreshold?.toIntOrNull() ?: 1) {
        ImageTypes.STATUS_ALERT
    } else {
        val daysLeftWarningThreshold: String? = sharedPreferences.getString(
                context.getString(R.string.pref_meds_stock_warning_threshold_key),
                context.getString(R.string.pref_meds_stock_warning_threshold_default_value)
            )
        if (daysLeft < daysLeftWarningThreshold?.toIntOrNull() ?: 1) {
            ImageTypes.STATUS_WARN
        } else {
            ImageTypes.STATUS_OK
        }
    }
}

object ImageTypes {
    const val STATUS_OK = 1
    const val STATUS_WARN = 2
    const val STATUS_ALERT = 3
}


