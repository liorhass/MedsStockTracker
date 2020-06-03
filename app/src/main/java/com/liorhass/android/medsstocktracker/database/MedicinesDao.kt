package com.liorhass.android.medsstocktracker.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MedicinesDao {

    @Insert
    fun insertMedicine(medicine: Medicine)

    @Update
    fun updateMedicine(medicine: Medicine)

    @Query("SELECT * FROM medicines WHERE _id = :key")
    fun getMedicine(key: Long): Medicine?

    // to sort by a parameter: https://stackoverflow.com/a/55298054/1071117
    @Query("SELECT * FROM medicines ORDER BY _id DESC")
    fun getAllMedicinesLD(): LiveData<List<Medicine>>

    @Query("SELECT * FROM medicines ORDER BY _id DESC")
    fun getAllMedicines(): List<Medicine>

    @Query("DELETE FROM medicines WHERE _id = :key")
    fun deleteMedicine(key: Long)
}