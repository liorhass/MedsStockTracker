package com.liorhass.android.medsstocktracker.editmedicine

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.LoggedEvent
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import com.liorhass.android.medsstocktracker.util.NavigationDestinations
import com.liorhass.android.medsstocktracker.util.NavigationEventWithNoArguments
import com.liorhass.android.medsstocktracker.database.Medicine
import com.liorhass.android.medsstocktracker.database.MedicinesDao
import com.liorhass.android.medsstocktracker.model.DoseFormatter
import com.liorhass.android.medsstocktracker.model.estimateCurrentStock
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.absoluteValue

class EditMedicineViewModel(private val medicineId: Long,
                            private val medicinesDao: MedicinesDao,
                            private val loggedEventsDao: LoggedEventsDao,
                            private val application: Application) :
    ViewModel() {

    // Must be initialized to an empty Medicine because we use data binding to draw our screen, and
    // the drawing happens before we initialize medicine from the DB in our init{} block. It's
    // simpler that way than to have to check for null in the data binding in the XML code.
    class MedicineStr {
        var name: String = ""
        var dailyDose: String = ""
        var currentStock: String = ""
    }
    private val _medicineStr = MutableLiveData(MedicineStr())
    val medicineStr: LiveData<MedicineStr>
        get() = _medicineStr

/*//todo:2brm
    private val _saveButtonEnabled = MutableLiveData<Boolean>(false)
    val saveButtonEnabled: LiveData<Boolean>
        get() = _saveButtonEnabled
*/

    // These observables are observed by the fragment. We use them to flag an input error in one of
    // the input fields.
    private val _resetInputErrors = MutableLiveData<OneTimeEvent<Boolean>>()
    val resetInputErrors: LiveData<OneTimeEvent<Boolean>>
        get() = _resetInputErrors
    private val _medicineNameInputError = MutableLiveData<OneTimeEvent<Boolean>>()
    val medicineNameInputError: LiveData<OneTimeEvent<Boolean>>
        get() = _medicineNameInputError
    private val _dailyDoseInputError = MutableLiveData<OneTimeEvent<Boolean>>()
    val dailyDoseInputError: LiveData<OneTimeEvent<Boolean>>
        get() = _dailyDoseInputError
    private val _currentStockInputError = MutableLiveData<OneTimeEvent<Boolean>>()
    val currentStockInputError: LiveData<OneTimeEvent<Boolean>>
        get() = _currentStockInputError

    // This observable is observed by the fragment. We use it to tell it to show a dialog
    class DialogInfo (val title: String, val message: String, val dismissButtonText: String)
    private val _showDialog = MutableLiveData<OneTimeEvent<DialogInfo>>()
    val showDialog: LiveData<OneTimeEvent<DialogInfo>>
        get() = _showDialog

    private var medicine: Medicine = Medicine()

    private var job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private val _navigateTo = MutableLiveData<OneTimeEvent<NavigationEventWithNoArguments>>() // https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
    val navigationTrigger : LiveData<OneTimeEvent<NavigationEventWithNoArguments>>
        get() = _navigateTo

    init {
        initMedicine()
    }

    private fun initMedicine() {
        if (medicineId != Medicine.ID_OF_UNINITIALIZED_MEDICINE) {
            uiScope.launch {
                val med = getMedicineFromDatabase(medicineId) // Read the medicine from DB
                if (med == null) {
                    // Something horrible happened. DB doesn't have a medicine w/ the requested ID. But we look to the other side, and continue as if nothing happen ;)
                    Timber.wtf("Medicine with id=${medicineId} is not in DB!")
                    medicine = Medicine()
                } else {
                    medicine = med
                    Timber.v("initializeMedicine(): name=${medicine.name}")
                    populateFormFields()  // Update the UI fields

/* todo 2brm
                    // When we populate the form's text fields, it generate a call to their onTextChanged()
                    // callback. Since this callback is where we enable the "save" button, it turns out that
                    // the button is always enabled. To prevent that, we disable it here immediately after
                    // the form load, and then enable it normally in the onTextChanged() callback of
                    // any of the text fields.
                    _saveButtonEnabled.value = false
                    Timber.d("initMedicine() _saveButtonEnabled.value = false  ===========")
*/
                }
            }
        }
    }

    private suspend fun getMedicineFromDatabase(medicineId: Long): Medicine? {
        return withContext(Dispatchers.IO) {
            val med = medicinesDao.getMedicine(medicineId)
            med
        }
    }

    private fun populateFormFields() {
        val medicineStr = MedicineStr()
        medicineStr.name         = medicine.name
        medicineStr.dailyDose    = DoseFormatter.formatDose(medicine.dailyDose)
        medicineStr.currentStock = DoseFormatter.formatDose(medicine.estimateCurrentStock())
        _medicineStr.value = medicineStr // Setting the LiveData's value informs all observers of the new data
    }

    fun onSave(medicineName: String, dailyDoseStr: String, currentStockStr: String) {
        Timber.v("save(): medicineName='${medicineName}'  dailyDose='${dailyDoseStr}'  currentStock='${currentStockStr}'")

        // If the user had errors, and re-submits the form, we need to clear the error markings
        // before we re-evaluate the form. Otherwise the old error markings remain on the screen.
        _resetInputErrors.value = OneTimeEvent(true)

        if (updateMedicine(medicineName, dailyDoseStr, currentStockStr)) {
            // Trigger navigation back to MedicineListFragment
            _navigateTo.value = OneTimeEvent(
                NavigationEventWithNoArguments(
                    NavigationDestinations.NAVIGATION_DESTINATION_MEDICINE_LIST_FRAGMENT
                )
            )
        }
    }

    /**
     * @return true if should navigate back to the medicine list fragment, false if should stay on
     * this fragment (because there was an error in an input field, and we're waiting for the user
     * to correct it).
     */
    private fun updateMedicine(medicineName: String, dailyDoseStr: String, currentStockStr: String): Boolean {
        var inputError = false
        var dailyDose = 0.0
        var currentStock = 0.0

        if (medicineName.isBlank()) {
            _medicineNameInputError.value = OneTimeEvent(true)
            inputError = true
        }
        try {
            dailyDose = dailyDoseStr.toDouble()
        } catch (e: Exception) {
            Timber.d("daily dose format error")
            _dailyDoseInputError.value = OneTimeEvent(true)
            inputError = true
        }
        try {
            currentStock = currentStockStr.toDouble()
        } catch (e: Exception) {
            Timber.d("current stock format error")
            _currentStockInputError.value = OneTimeEvent(true)
            inputError = true
        }
        if (! inputError) {
            updateMedicine(medicineName, dailyDose, currentStock)
            return true
        }
        return false
    }

    private fun updateMedicine(medicineName: String, dailyDose: Double, currentStock: Double) {
        Timber.d("updateMedicine(): medicineName=$medicineName dailyDose=$dailyDose currentStock=$currentStock")
        // Reset the time-of-last-alert, so we'll generate a "normal" (non-critical) alert
        medicine.timeOfLastAlert = 0L //todo: is this should be here or only when creating new medicine?

        if (medicine.id == Medicine.ID_OF_UNINITIALIZED_MEDICINE) {
            // We're creating a new medicine
            medicine.name = medicineName
            medicine.dailyDose = dailyDose
            medicine.nAvailableOriginally = currentStock
            medicine.nAvailableUpdateDateAndTime = System.currentTimeMillis()
            updateMedicineInDatabase(medicine)

            logEvent(LoggedEvent.TYPE_CREATE_MEDICINE,
                medicineName,
                application.getString(R.string.logged_event_new_med, medicine.name,
                medicine.dailyDose, medicine.nAvailableOriginally))
        } else {
            // We're updating an existing medicine
            val logMsg = StringBuilder()
            var somethingChanged = false
            if (medicine.name != medicineName) {
                // "Name changed from " + medicine.name + " to " + medicineName + ". "
                logMsg.append(application.getString(R.string.logged_event_name_changed, medicine.name, medicineName))
                medicine.name = medicineName
                somethingChanged = true
            }
            if ((medicine.dailyDose - dailyDose).absoluteValue > 0.01) {
                // "Daily-Dose changed from " + medicine.dailyDose + " to " + dailyDose + ". "
                logMsg.append(application.getString(R.string.logged_event_daily_dose_changed, medicine.dailyDose, dailyDose))
                medicine.dailyDose = dailyDose
                somethingChanged = true
            }
            if ((medicine.nAvailableOriginally - currentStock).absoluteValue > 0.01) {
                // "Available-Dose changed from " + medicine.nAvailableOriginally + " to " + currentStock + "."
                logMsg.append(application.getString(R.string.logged_event_current_stock_changed, medicine.nAvailableOriginally, currentStock))
                medicine.nAvailableOriginally = currentStock
                medicine.nAvailableUpdateDateAndTime = System.currentTimeMillis()
                somethingChanged = true
            }
            if (somethingChanged) {
                updateMedicineInDatabase(medicine)

                logEvent(LoggedEvent.TYPE_MANUALLY_SET_MEDICINE_INFO, medicineName, logMsg.toString())
            }
        }
    }

    private fun updateMedicineInDatabase(updatedMedicine: Medicine) {
        uiScope.launch {
            withContext(Dispatchers.IO) {
                if (updatedMedicine.id == Medicine.ID_OF_UNINITIALIZED_MEDICINE) {
                    medicinesDao.insertMedicine(updatedMedicine)
                } else {
                    Timber.d("Updating medicine: name=${updatedMedicine.name}  dailyDose=${updatedMedicine.dailyDose}")
                    medicinesDao.updateMedicine(updatedMedicine)
                }
            }
        }
    }

    private fun logEvent(eventType: Int, medicineName: String, msg: String) {
        uiScope.launch {
            withContext(Dispatchers.IO) {
                val currentTime = System.currentTimeMillis()
                val loggedEvent = LoggedEvent(dateAndTime = currentTime, medicineName = medicineName,
                    type = eventType, text = msg)
                loggedEventsDao.insertLoggedEvent(loggedEvent)
            }
        }
    }

    fun onCancel() {
        Timber.v("Cancel the edit-med")
        _navigateTo.value = OneTimeEvent(
            NavigationEventWithNoArguments(
                NavigationDestinations.NAVIGATION_DESTINATION_MEDICINE_LIST_FRAGMENT
            )
        ) // Trigger navigation back to MedicineListFragment
    }

    fun onHelpMedicineName() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                application.getString(R.string.help_dialog_medicine_name_title),
                application.getString(R.string.help_dialog_medicine_name_msg),
                application.getString(R.string.help_dialog_btn)
            )
        )
    }
    fun onHelpDailyDose() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                application.getString(R.string.help_dialog_daily_dose_title),
                application.getString(R.string.help_dialog_daily_dose_msg),
                application.getString(R.string.help_dialog_btn)
            )
        )
    }
    fun onHelpCurrentStock() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                application.getString(R.string.help_dialog_current_stock_title),
                application.getString(R.string.help_dialog_current_stock_msg),
                application.getString(R.string.help_dialog_btn)
            )
        )
    }

/*//todo:2brm
    fun onTextFieldChanged() {
        Timber.d("onTextFieldChanged():")
        _saveButtonEnabled.value = true
        Timber.d("onTextFieldChanged() _saveButtonEnabled.value = true  ===========")
    }
*/

    // Called when the ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }
}

