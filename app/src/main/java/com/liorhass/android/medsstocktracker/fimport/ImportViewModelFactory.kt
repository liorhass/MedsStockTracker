package com.liorhass.android.medsstocktracker.fimport

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.database.MedicinesDao

// Boilerplate code of a ViewModel-factory - provides the DAO and app context to the ViewModel
class ImportViewModelFactory(
    private val medicinesDao: MedicinesDao,
    private val loggedEventsDao: LoggedEventsDao,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("unchecked_cast")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ImportViewModel::class.java)) {
            return ImportViewModel(medicinesDao, loggedEventsDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
