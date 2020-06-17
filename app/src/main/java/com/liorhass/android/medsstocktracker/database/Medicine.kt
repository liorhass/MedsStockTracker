package com.liorhass.android.medsstocktracker.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = ID_OF_UNINITIALIZED_MEDICINE,

    @ColumnInfo(name = "med_name")
    var name: String = "",

    @ColumnInfo(name = "n_available_originally")
    var nAvailableOriginally: Double = 0.0,

    /**
     * The system time of when was the nAvailableOriginally manually updated for the last time
     */
    @ColumnInfo(name = "n_available_update_date_and_time")
    var nAvailableUpdateDateAndTime: Long = -1,

    @ColumnInfo(name = "daily_dose")
    var dailyDose: Double = 0.0,

    @ColumnInfo(name = "time_of_last_alert")
    var timeOfLastAlert: Long = -1,

    @ColumnInfo(name = "notes")
    var notes: String = "",

    @ColumnInfo(name = "prev_increment")
    var prev_increment: Int = 0,

    @ColumnInfo(name = "prev_prev_increment")
    var prev_prev_increment: Int = 0
)
{
    companion object {
        const val ID_OF_UNINITIALIZED_MEDICINE = 0L
    }
}
