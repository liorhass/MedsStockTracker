package com.liorhass.android.medsstocktracker.medicinelist

import android.app.Application
import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.lifecycle.*
import androidx.recyclerview.selection.Selection
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import com.liorhass.android.medsstocktracker.util.NavigationDestinations
import com.liorhass.android.medsstocktracker.util.NavigationEventWithLongArgument
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.LoggedEvent
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.database.MedicinesDao
import kotlinx.coroutines.*
import timber.log.Timber

class MedicineListViewModel(private val medicinesDao: MedicinesDao,
                            private val loggedEventDao: LoggedEventsDao,
                            private val application: Application) :
        ViewModel() {

    // So we can cancel coroutines started by this ViewModel.
    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    var medicines = medicinesDao.getAllMedicinesLD()

    // Trigger a navigation to the MedicineDetails screen
    private val _navigateTo = MutableLiveData<OneTimeEvent<NavigationEventWithLongArgument>>() // https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
    val navigationTrigger : LiveData<OneTimeEvent<NavigationEventWithLongArgument>>
        get() = _navigateTo

    // Trigger display of a confirmation dialog before deletion of medicines
    private val _confirmDeletion = MutableLiveData<OneTimeEvent<Boolean>>()
    val confirmDeletion : LiveData<OneTimeEvent<Boolean>>
        get() = _confirmDeletion

    fun onNavigateToMedicineDetails(medicineId: Long) {
        Timber.v("onNavigateToMedicineDetails() id=$medicineId")
        // Trigger navigation to MedicineDetailsFragment
        _navigateTo.value = OneTimeEvent(
            NavigationEventWithLongArgument(
                NavigationDestinations.NAVIGATION_DESTINATION_MEDICINE_DETAILS_FRAGMENT,
                medicineId
            )
        )
    }

    fun onMedicineDeletionClicked() {
        // User clicked on "delete" button. Ask the fragment to show a confirmation notification
        // asking "are you sure?", and call our deleteMedicines() method if the user confirms.
        _confirmDeletion.value = OneTimeEvent(true)
    }

    fun deleteMedicines(medicinesToDelete: Selection<Long>) {
        val iterator: Iterator<Long> = medicinesToDelete.iterator()
        while(iterator.hasNext()) {
            val medicineId = iterator.next()
            Timber.v("deleteMedicines(): going to delete ID=${medicineId}")
            uiScope.launch {
                withContext(Dispatchers.IO) {
                    val medicine= medicinesDao.getMedicine(medicineId)
                    val loggedEvent = LoggedEvent(dateAndTime = System.currentTimeMillis(),
                              type = LoggedEvent.TYPE_DELETE_MEDICINE,
                              medicineName = medicine?.name ?: "",
                              text = application.getString(R.string.logged_event_medicine_deleted))
                    loggedEventDao.insertLoggedEvent(loggedEvent)
                    medicinesDao.deleteMedicine(medicineId)
                }
            }
        }
    }

    // If there are no medicines in the database we show an appropriate message. Done via
    // data binding in fragment_medicine_list.xml
    val medicineListIsEmptyMsg: Spanned = HtmlCompat.fromHtml(
        application.getString(R.string.empty_medicine_list_message),
        HtmlCompat.FROM_HTML_MODE_LEGACY
    )
    val isMedicineListEmpty = Transformations.map(medicines) {
        it?.isEmpty()
    }

    // Called when the ViewModel is destroyed. Cancel all ongoing coroutines
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
        Timber.v("onCleard()")
    }
}
