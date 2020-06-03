package com.liorhass.android.medsstocktracker.medicinelist

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.database.MedicinesDao

// Boilerplate code of a ViewModel-factory - provides the DAO and app context to the ViewModel
class MedicineListViewModelFactory(
    private val medicinesDao: MedicinesDao,
    private val loggedEventsDao: LoggedEventsDao,
    private val application: Application) :
        ViewModelProvider.Factory {

    @Suppress("unchecked_cast")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicineListViewModel::class.java)) {
            return MedicineListViewModel(medicinesDao, loggedEventsDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
