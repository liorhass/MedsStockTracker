package com.liorhass.android.medsstocktracker.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface LoggedEventsDao {

    @Insert
    fun insertLoggedEvent(loggedEvent: LoggedEvent)

    @Update
    fun updateLoggedEvent(loggedEvent: LoggedEvent)

    @Query("SELECT * FROM eventLogs WHERE _id = :key")
    fun getLoggedEvent(key: Long): LoggedEvent?

    @Query("SELECT * FROM eventLogs ORDER BY _id DESC")
    fun getAllLoggedEvents(): LiveData<List<LoggedEvent>>

    @Query("DELETE FROM eventLogs WHERE _id = :key")
    fun deleteLoggedEvent(key: Long)
}