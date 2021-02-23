package com.liorhass.android.medsstocktracker.editmedicine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.liorhass.android.medsstocktracker.MSTApplication
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.database.MedicinesDao

// Boilerplate code of a ViewModel-factory - provides the DAO and app context to the ViewModel
class EditMedicineViewModelFactory(
    private val medicineId: Long,
    private val medicinesDao: MedicinesDao,
    private val loggedEventsDao: LoggedEventsDao,
    private val application: MSTApplication) :
        ViewModelProvider.Factory {
    @Suppress("unchecked_cast")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditMedicineViewModel::class.java)) {
            return EditMedicineViewModel(medicineId, medicinesDao, loggedEventsDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
