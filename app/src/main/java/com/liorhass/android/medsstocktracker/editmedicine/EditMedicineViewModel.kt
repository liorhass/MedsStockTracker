package com.liorhass.android.medsstocktracker.editmedicine

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.library.baseAdapters.BR
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.liorhass.android.medsstocktracker.MSTApplication
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.*
import com.liorhass.android.medsstocktracker.model.DoseFormatter
import com.liorhass.android.medsstocktracker.model.estimateCurrentStock
import com.liorhass.android.medsstocktracker.util.*
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.absoluteValue


class EditMedicineViewModel(private val medicineId: Long,
                            private val medicinesDao: MedicinesDao,
                            private val loggedEventsDao: LoggedEventsDao,
                            private val mstApplication: MSTApplication) :
    AndroidViewModel(mstApplication) {

    val formFields = FormFields()
    private val _saveButtonEnabled = MutableLiveData(false)
    val saveButtonEnabled: LiveData<Boolean>
        get() = _saveButtonEnabled
    private val _addPrevButtonIsVisible = MutableLiveData(false)
    val addPrevButtonIsVisible: LiveData<Boolean>
        get() = _addPrevButtonIsVisible
    private val _addPrevButtonText = MutableLiveData("")
    val addPrevButtonText: LiveData<String>
        get() = _addPrevButtonText
    private val _addPrevPrevButtonIsVisible = MutableLiveData(false)
    val addPrevPrevButtonIsVisible: LiveData<Boolean>
        get() = _addPrevPrevButtonIsVisible
    private val _addPrevPrevButtonText = MutableLiveData("")
    val addPrevPrevButtonText: LiveData<String>
        get() = _addPrevPrevButtonText

    // These observables are observed by the fragment. We use them to flag an input error in one of
    // the input fields.
    private val _resetInputErrors = MutableLiveData<OneTimeEvent<Boolean>>()
    val resetInputErrors: LiveData<OneTimeEvent<Boolean>>
        get() = _resetInputErrors
    private val _medicineNameInputError = MutableLiveData<OneTimeEvent<ErrorCodes>>()
    val medicineNameInputError: LiveData<OneTimeEvent<ErrorCodes>>
        get() = _medicineNameInputError
    private val _dailyDoseInputError = MutableLiveData<OneTimeEvent<Boolean>>()
    val dailyDoseInputError: LiveData<OneTimeEvent<Boolean>>
        get() = _dailyDoseInputError
    private val _currentStockInputError = MutableLiveData<OneTimeEvent<Boolean>>()
    val currentStockInputError: LiveData<OneTimeEvent<Boolean>>
        get() = _currentStockInputError
    enum class ErrorCodes {
        NO_NAME,
        NAME_ALREADY_EXIST
    }

    // This observable is observed by the fragment. We use it to tell it to show a dialog
    class DialogInfo (val type: DialogTypes, val title: String = "", val message: String = "", val dismissButtonText: String = "")
    private val _showDialog = MutableLiveData<OneTimeEvent<DialogInfo>>()
    val showDialog: LiveData<OneTimeEvent<DialogInfo>>
        get() = _showDialog
    enum class DialogTypes {
        HELP_DIALOG,
        ADD_TO_STOCK
    }

    // This observable is observed by the fragment. We use it to tell it to close the dialog
    private val _closeDialog = MutableLiveData<OneTimeEvent<Boolean>>()
    val closeDialog: LiveData<OneTimeEvent<Boolean>>
        get() = _closeDialog

    // This observable is observed by the fragment. We use it to tell it to navigate to another fragment
    private val _navigateTo = MutableLiveData<OneTimeEvent<NavigationEventWithNoArguments>>() // https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
    val navigationTrigger : LiveData<OneTimeEvent<NavigationEventWithNoArguments>>
        get() = _navigateTo

//    val numberFormat = NumberFormat.getInstance() // Current locale formatter

    private var job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private var medicine: Medicine = Medicine()
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
                }
                _saveButtonEnabled.value = false  // Disable the Save button until the user inputs something
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
        formFields.name = medicine.name
        formFields.dailyDose = DoseFormatter.formatDose(medicine.dailyDose)
        formFields.currentStock = DoseFormatter.formatDose(medicine.estimateCurrentStock())
        formFields.notes = medicine.notes
        if (medicine.prevIncrement > 0) {
            _addPrevButtonText.value = medicine.prevIncrement.toString()
            _addPrevButtonIsVisible.value = true
            if (medicine.prevPrevIncrement > 0) {
                _addPrevPrevButtonText.value = medicine.prevPrevIncrement.toString()
                _addPrevPrevButtonIsVisible.value = true
            }
        }
    }

    fun onSave(medicineName: String, dailyDoseStr: String, currentStockStr: String, notes: String) {
        Timber.v("save(): medicineName='${medicineName}'  dailyDose='${dailyDoseStr}'  currentStock='${currentStockStr}'  notes='${notes}'")

        // If previously the user had errors markings on some of the form fields, and now they re-submit
        // the form, we need to clear the error markings before we re-evaluate the form. Otherwise
        // the old error markings remain on the screen.
        _resetInputErrors.value = OneTimeEvent(true)

        uiScope.launch {
            if (verifyFieldsAndUpdateMedicine(medicineName, dailyDoseStr, currentStockStr, notes)) {
                // Trigger navigation back to MedicineListFragment
                _navigateTo.value = OneTimeEvent(
                    NavigationEventWithNoArguments(
                        NavigationDestinations.NAVIGATION_DESTINATION_MEDICINE_LIST_FRAGMENT
                    )
                )
            }
        }
    }

    /**
     * @return true if should navigate back to the medicine list fragment, false if should stay on
     * this fragment (because there was an error in an input field, and we're waiting for the user
     * to correct it).
     */
    private suspend fun verifyFieldsAndUpdateMedicine(rawMedicineName: String, dailyDoseStr: String, currentStockStr: String, rawNotes: String): Boolean {
        var inputError = false
        var dailyDose = 0.0
        var currentStock = 0.0
        val medicineName = rawMedicineName.trim()
        val notes = rawNotes.trim()

        if (medicineName.isEmpty()) {
            _medicineNameInputError.value = OneTimeEvent(ErrorCodes.NO_NAME)
            inputError = true
        }
        if (medicine.id == Medicine.ID_OF_UNINITIALIZED_MEDICINE && isMedicineNameAlreadyInDatabase(medicineName)) {
            // Trying to create a new medicine with a name that already exists
            _medicineNameInputError.value = OneTimeEvent(ErrorCodes.NAME_ALREADY_EXIST)
            inputError = true
        }
        try {
            dailyDose = dailyDoseStr.localeAgnosticToDouble()
//            dailyDose = numberFormat.parse(dailyDoseStr)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            Timber.d("daily dose format error. dailyDose='$dailyDose'")
            _dailyDoseInputError.value = OneTimeEvent(true)
            inputError = true
        }
        try {
            currentStock = currentStockStr.localeAgnosticToDouble()
//            currentStock = numberFormat.parse(currentStockStr)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            Timber.d("current stock format error. currentStock='$currentStock'")
            _currentStockInputError.value = OneTimeEvent(true)
            inputError = true
        }
        if (! inputError) {
            updateMedicine(medicineName, dailyDose, currentStock, notes)
            return true
        }
        return false
    }

    private suspend fun updateMedicine(medicineName: String, dailyDose: Double, currentStock: Double, notes: String) {
        Timber.d("updateMedicine(): medicineName=$medicineName dailyDose=$dailyDose currentStock=$currentStock notes=$notes")
        // Reset the time-of-last-alert, so we'll generate a "normal" (non-critical) alert
        medicine.timeOfLastAlert = 0L //todo: is this should be here or only when creating new medicine?

        if (medicine.id == Medicine.ID_OF_UNINITIALIZED_MEDICINE) {
            // We're creating a new medicine
            medicine.name = medicineName
            medicine.dailyDose = dailyDose
            medicine.nAvailableOriginally = currentStock
            medicine.nAvailableUpdateDateAndTime = System.currentTimeMillis()
            medicine.notes = notes
            updateMedicineInDatabase(medicine)

            logEvent(LoggedEvent.TYPE_CREATE_MEDICINE,
                medicineName,
                mstApplication.getString(R.string.logged_event_new_med, medicine.name,
                medicine.dailyDose, medicine.nAvailableOriginally, medicine.notes))
        } else {
            // We're updating an existing medicine
            val logMsg = StringBuilder()
            var somethingChanged = false
            if (medicine.name != medicineName) {
                // "Name changed from " + medicine.name + " to " + medicineName + ". "
                logMsg.append(mstApplication.getString(R.string.logged_event_name_changed, medicine.name, medicineName))
                medicine.name = medicineName
                somethingChanged = true
            }
            if ((medicine.dailyDose - dailyDose).absoluteValue > 0.01) {
                // "Daily-Dose changed from " + medicine.dailyDose + " to " + dailyDose + ". "
                logMsg.append(mstApplication.getString(R.string.logged_event_daily_dose_changed, medicine.dailyDose, dailyDose))
                medicine.dailyDose = dailyDose
                somethingChanged = true
            }
            if ((medicine.estimateCurrentStock() - currentStock).absoluteValue > 0.01) {
                updatePrevIncrement(currentStock)
                // "Available-Dose changed from " + medicine.nAvailableOriginally + " to " + currentStock + "."
                logMsg.append(mstApplication.getString(R.string.logged_event_current_stock_changed, medicine.nAvailableOriginally, currentStock))
                medicine.nAvailableOriginally = currentStock
                medicine.nAvailableUpdateDateAndTime = System.currentTimeMillis()
                somethingChanged = true
            }
            if (medicine.notes != notes) {
                logMsg.append(mstApplication.getString(R.string.logged_event_notes_changed, medicine.notes, notes))
                medicine.notes = notes
                somethingChanged = true
            }
            if (somethingChanged) {
                updateMedicineInDatabase(medicine)

                logEvent(LoggedEvent.TYPE_MANUALLY_SET_MEDICINE_INFO, medicineName, logMsg.toString())
            }
        }
    }

    private suspend fun updateMedicineInDatabase(updatedMedicine: Medicine) {
        withContext(Dispatchers.IO) {
            if (updatedMedicine.id == Medicine.ID_OF_UNINITIALIZED_MEDICINE) {
                medicinesDao.insertMedicine(updatedMedicine)
            } else {
                Timber.d("Updating medicine: name=${updatedMedicine.name}  dailyDose=${updatedMedicine.dailyDose}")
                medicinesDao.updateMedicine(updatedMedicine)
            }
        }
    }

    private suspend fun logEvent(eventType: Int, medicineName: String, msg: String) {
        withContext(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            val loggedEvent = LoggedEvent(dateAndTime = currentTime, medicineName = medicineName,
                type = eventType, text = msg)
            loggedEventsDao.insertLoggedEvent(loggedEvent)
        }
    }

    fun onCancel() {
        Timber.v("Cancel the edit-med")
        // Trigger navigation back to MedicineListFragment
        _navigateTo.value = OneTimeEvent(
            NavigationEventWithNoArguments(
                NavigationDestinations.NAVIGATION_DESTINATION_MEDICINE_LIST_FRAGMENT
            )
        )
    }

    fun onAddButtonClick() {
        Timber.v("onAddButtonClick()")
        // Should open the dialog that let the user enter how many pills to add to the current stock
        _showDialog.value = OneTimeEvent(DialogInfo(DialogTypes.ADD_TO_STOCK))
    }

    fun onHelpMedicineName() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                DialogTypes.HELP_DIALOG,
                mstApplication.getString(R.string.help_dialog_medicine_name_title),
                mstApplication.getString(R.string.help_dialog_medicine_name_msg),
                mstApplication.getString(R.string.help_dialog_btn)
            )
        )
    }
    fun onHelpDailyDose() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                DialogTypes.HELP_DIALOG,
                mstApplication.getString(R.string.help_dialog_daily_dose_title),
                mstApplication.getString(R.string.help_dialog_daily_dose_msg),
                mstApplication.getString(R.string.help_dialog_btn)
            )
        )
    }
    fun onHelpCurrentStock() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                DialogTypes.HELP_DIALOG,
                mstApplication.getString(R.string.help_dialog_current_stock_title),
                mstApplication.getString(R.string.help_dialog_current_stock_msg),
                mstApplication.getString(R.string.help_dialog_btn)
            )
        )
    }
    fun onHelpNotes() {
        _showDialog.value = OneTimeEvent(
            DialogInfo(
                DialogTypes.HELP_DIALOG,
                mstApplication.getString(R.string.help_dialog_notes_title),
                mstApplication.getString(R.string.help_dialog_notes_msg),
                mstApplication.getString(R.string.help_dialog_btn)
            )
        )
    }

    private suspend fun isMedicineNameAlreadyInDatabase(medicineName: String): Boolean {
        return withContext(Dispatchers.IO) {
            // Read all medicines from the database
            val medicines = AppDatabase.getInstance(mstApplication).medicineDao.getAllMedicines()
            for (medicine in medicines) {
                if (medicineName.equals(medicine.name, true)) {
                    return@withContext true
                }
            }
            false
        }
    }

    // Called when the ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        job.cancel()
    }

    private fun onFormFieldChanged() {
        if (_saveButtonEnabled.value != true) {
            _saveButtonEnabled.value = true
        }
    }

    // An observable class that holds the form's fields (by two-way-binding). See https://developer.android.com/topic/libraries/data-binding/observability#observable_objects
    inner class FormFields : BaseObservable() {
        @get:Bindable
        var name: String = ""
            set(value) {
                if (field != value) {
                    Timber.v("name(): value='$value'  field='$field'")
                    field = value
                    onFormFieldChanged()
                    notifyPropertyChanged(BR.name) // Notify observers of a new value.
                }
            }

        @get:Bindable
        var dailyDose: String = ""
            set(value) {
                if (field != value) {
                    Timber.v("dailyDose: value='$value'  field='$field'")
                    field = value
                    onFormFieldChanged()
                    notifyPropertyChanged(BR.dailyDose) // Notify observers of a new value.
                }
            }

        @get:Bindable
        var currentStock: String = ""
            set(value) {
                if (field != value) {
                    Timber.v("currentStock: value='$value'  field='$field'")
                    field = value
                    onFormFieldChanged()
                    notifyPropertyChanged(BR.currentStock) // Notify observers of a new value.
                }
            }

        @get:Bindable
        var notes: String = ""
            set(value) {
                if (field != value) {
                    Timber.v("notes: value='$value'  field='$field'")
                    field = value
                    onFormFieldChanged()
                    notifyPropertyChanged(BR.notes) // Notify observers of a new value.
                }
            }
    }

    fun onAddToCurrentStock(quantityToAddStr: String) {
        Timber.d("onAddToCurrentStock(): quantityToAddStr='$quantityToAddStr'")
        var quantityToAdd = 0.0
        try {
            quantityToAdd = quantityToAddStr.localeAgnosticToDouble()
//            quantityToAdd = numberFormat.parse(quantityToAddStr)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            Timber.d("onAddToCurrentStock() format error. quantityToAddStr='$quantityToAddStr'")
            // Silently ignore these errors for now. todo: handle this error nicer
        }
        if (! quantityToAdd.equalsAlmost(0.0)) {
            addToCurrentStockFormField(quantityToAdd)
        }
        // Close the dialog
        _closeDialog.value = OneTimeEvent(true)
    }

    fun onCancelAddToCurrentStock() {
        // Close the dialog
        _closeDialog.value = OneTimeEvent(true)
    }

    fun onAddPrevButtonClick() {
        addToCurrentStockFormField(medicine.prevIncrement.toDouble())
    }

    fun onAddPrevPrevButtonClick() {
        addToCurrentStockFormField(medicine.prevPrevIncrement.toDouble())
    }

    private fun addToCurrentStockFormField(quantityToAdd: Double) {
        var prevStock: Double = if (formFields.currentStock.isBlank()) {
            // We allow empty field. Treat it as if it was 0.0. Especially useful for when creating new medicines
            0.0
        } else {
            try {
                formFields.currentStock.localeAgnosticToDouble()
//                numberFormat.parse(formFields.currentStock)?.toDouble() ?: 0.0
            } catch (e: Exception) {
                Timber.e(e, "addToCurrentStockFormField(): Can't convert form field to Double. Field: '${formFields.currentStock}'")
                return
            }
        }
        prevStock += quantityToAdd
        formFields.currentStock = DoseFormatter.formatDose(prevStock)
    }

    private fun updatePrevIncrement(currentStock: Double) {
        val quantityAdded = (0.5 + currentStock - medicine.estimateCurrentStock()).toInt()
        if (quantityAdded > 0  &&  quantityAdded != medicine.prevIncrement) {
            medicine.prevPrevIncrement = medicine.prevIncrement
            medicine.prevIncrement = quantityAdded
        }
    }

    fun getScreenTitle(): String {
        return when (medicineId) {
            Medicine.ID_OF_UNINITIALIZED_MEDICINE -> mstApplication.getString(R.string.fragment_title_new_medicine)
            else -> mstApplication.getString(R.string.fragment_title_edit_medicine)
        }
    }
}
