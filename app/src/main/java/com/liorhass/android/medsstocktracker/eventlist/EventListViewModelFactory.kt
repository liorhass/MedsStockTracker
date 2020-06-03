package com.liorhass.android.medsstocktracker.eventlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao

// Boilerplate code of a ViewModel-factory - provides the DAO and app context to the ViewModel
class EventListViewModelFactory(
    private val dataSource: LoggedEventsDao
) : ViewModelProvider.Factory {

    @Suppress("unchecked_cast")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventListViewModel::class.java)) {
            return EventListViewModel(dataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
