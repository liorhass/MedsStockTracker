package com.liorhass.android.medsstocktracker.medicinelist

import android.app.Application
import android.net.Uri
import android.text.Spanned
import androidx.core.content.FileProvider.getUriForFile
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.recyclerview.selection.Selection
import com.google.gson.Gson
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.LoggedEvent
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.database.MedicinesDao
import com.liorhass.android.medsstocktracker.util.NavigationDestinations
import com.liorhass.android.medsstocktracker.util.NavigationEventWithLongArgument
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import com.liorhass.android.medsstocktracker.util.SharedData
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File

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

    // Trigger a launch of a share action
    private val _launchShare = MutableLiveData<OneTimeEvent<Uri>>()
    val launchShare : LiveData<OneTimeEvent<Uri>>
        get() = _launchShare

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

    fun doShare() {
        Timber.v("doShare()")
        uiScope.launch {
            var uri: Uri? = null
            withContext(Dispatchers.IO) {
                uri = dumpDbToJsonFile()
            }
            // Launching the sharing action needs access to the activity that is doing the
            // launching. Since we don't want to have a reference to the activity in the
            // viewModel, we'll have to pass the actual job of creating the Intent and
            // launching it to our fragment.
            if (uri != null) {
                _launchShare.value = OneTimeEvent(uri!!)
            }
        }
    }

    /**
     * Dump the database to a file in Json format. The file must be located in a specific directory
     * in order to be able to be shared later. This location is specified in xml/filepaths.xml and
     * pointed to in the manifest file (FileProvider section). For edtails see:
     * https://developer.android.com/training/secure-file-sharing/setup-sharing#DefineMetaData
     * @return Uri of the Json file on success, null otherwise.
     */
    private fun dumpDbToJsonFile(): Uri? {
        val sharedData = SharedData(medicines = medicines.value)
        val jsonStr = Gson().toJson(sharedData)
        Timber.d("JSON: $jsonStr")

        return try {
            val path = File(application.filesDir, SharedData.Constants.DIR_NAME)
            if (!path.exists()) {
                path.mkdirs()
            }

            val file = File(path, SharedData.Constants.FILE_NAME)
            file.writeText(jsonStr)
            Timber.d("dumpDbToJsonFile(): Wrote DB to $path/${SharedData.Constants.FILE_NAME}")
            getUriForFile(application, SharedData.Constants.AUTHORITY, file)
        } catch (e: Exception) {
            Timber.e("doShare(): Exception: ${e.localizedMessage}")
            null
        }
    }

    // Called when the ViewModel is destroyed. Cancel all ongoing coroutines
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
        Timber.v("onCleard()")
    }
}
