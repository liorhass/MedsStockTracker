package com.liorhass.android.medsstocktracker.eventlist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.selection.Selection
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import kotlinx.coroutines.*
import timber.log.Timber

class EventListViewModel(private val loggedEventsDao: LoggedEventsDao) : ViewModel() {

    // So we can cancel coroutines started by this ViewModel.
    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    // No worries here because Room always runs on background thread when returning observables
    var loggedEvents = loggedEventsDao.getAllLoggedEvents()

    // Trigger display of a confirmation dialog before deletion of events
    private val _confirmDeletion = MutableLiveData<OneTimeEvent<Boolean>>()
    val confirmDeletion : LiveData<OneTimeEvent<Boolean>>
        get() = _confirmDeletion


    fun onEventDeletionClicked() {
        // User clicked on "delete" button. Ask the fragment to show a confirmation notification
        // asking "are you sure?", and call our deleteEvents() method if the user confirms.
        _confirmDeletion.value = OneTimeEvent(true)
    }

    fun deleteEvents(eventsToDelete: Selection<Long>) {
        val iterator: Iterator<Long> = eventsToDelete.iterator()
        while(iterator.hasNext()) {
            val eventId = iterator.next()
            Timber.v("deleteEvents(): going to delete ID=${eventId}")
            uiScope.launch {
                withContext(Dispatchers.IO) {
                    loggedEventsDao.deleteLoggedEvent(eventId)
                }
            }
        }
    }


}
