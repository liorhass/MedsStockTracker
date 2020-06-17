package com.liorhass.android.medsstocktracker.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eventLogs")
data class LoggedEvent (
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    var id: Long = 0L,

    @ColumnInfo(name = "date_and_time")
    var dateAndTime: Long = -1,

    @ColumnInfo(name = "type")
    var type: Int = -1,

    @ColumnInfo(name = "med_name")
    val medicineName: String = "",

    @ColumnInfo(name = "text")
    val text: String = ""

) {
    companion object {
        const val TYPE_MANUALLY_SET_MEDICINE_INFO: Int = 1
//        const val TYPE_INCREMENT_MEDICINE_STOCK: Int = 2
        const val TYPE_CREATE_MEDICINE: Int = 3
        const val TYPE_DELETE_MEDICINE: Int = 4
        const val TYPE_IMPORT_MEDICINE: Int = 5
    }
}

