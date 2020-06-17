package com.liorhass.android.medsstocktracker.fimport

import android.app.Application
import android.net.Uri
import android.text.Spanned
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.liorhass.android.medsstocktracker.MSTApplication
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.LoggedEvent
import com.liorhass.android.medsstocktracker.database.LoggedEventsDao
import com.liorhass.android.medsstocktracker.database.Medicine
import com.liorhass.android.medsstocktracker.database.MedicinesDao
import com.liorhass.android.medsstocktracker.model.getLastUpdatedStr
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import com.liorhass.android.medsstocktracker.util.SharedData
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.BufferedReader

class ImportViewModel(
    private val medicinesDao: MedicinesDao,
    private val loggedEventsDao: LoggedEventsDao,
    application: Application) : AndroidViewModel(application) {

    // So we can cancel coroutines started by this ViewModel.
    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private val _message = MutableLiveData<Spanned>()
    val message : LiveData<Spanned>
        get() = _message
    private val _importButtonEnabled = MutableLiveData(false)
    val importButtonEnabled : LiveData<Boolean>
        get() = _importButtonEnabled

    // Trigger activity termination as a result of user clicking the cancel button
    private val _navigateBack = MutableLiveData<OneTimeEvent<Boolean>>()
    val navigateBack : LiveData<OneTimeEvent<Boolean>>
        get() = _navigateBack

    // Display a dialog with the import summary (after a successful import)
    private val _importSummary = MutableLiveData<OneTimeEvent<Int>>()
    val importSummary : LiveData<OneTimeEvent<Int>>
        get() = _importSummary

    private var importedSharedData: SharedData? = null

    fun setUri(uri: Uri) {
        Timber.d("setUri(): Uri=$uri")

        uiScope.launch {
            var conflictingMedicines: List<Medicine>? = null
            var importResult = ImportResult.OK
            withContext(Dispatchers.IO) {
                val jsonStr = readJsonFromUri(uri)
                if (jsonStr == null) {
                    importResult = ImportResult.ERR_CANNOT_READ_FROM_URI
                    return@withContext
                }

                importedSharedData = parseJsonString(jsonStr)
                if (importedSharedData == null) {
                    importResult = ImportResult.ERR_IMPORTED_DATA_PARSING_ERROR
                    return@withContext
                }

                if (importedSharedData?.medicines?.isEmpty() != false) {
                    importResult = ImportResult.ERR_NO_MEDICINES_IN_IMPORTED_DATA
                    return@withContext
                }

                conflictingMedicines = findConflictingData(importedSharedData)
                if (conflictingMedicines?.isNotEmpty() == true) {
                    importResult = ImportResult.ERR_CONFLICTING_MEDICINES
                    return@withContext
                }
            }
            // Back at the UI thread
            displayImportResults(importResult, conflictingMedicines)
        }
    }

    private fun displayImportResults(importResult: ImportResult, conflictingMedicines: List<Medicine>?) {
        when(importResult) {
            ImportResult.OK -> {
                _message.value = buildImportMedicineListMsg(importedSharedData?.medicines)
                _importButtonEnabled.value = true
                Timber.d("displayImportResults() OK")
            }
            ImportResult.ERR_CANNOT_READ_FROM_URI -> {
                _message.value = buildReadFromUriErrorMsg()
                Timber.d("displayImportResults() ERR_CANNOT_READ_FROM_URI")
            }
            ImportResult.ERR_IMPORTED_DATA_PARSING_ERROR -> {
                _message.value = buildDataParsingErrorMsg()
                Timber.d("displayImportResults() ERR_IMPORTED_DATA_PARSING_ERROR")
            }
            ImportResult.ERR_NO_MEDICINES_IN_IMPORTED_DATA -> {
                _message.value = buildNoMedicinesInImportedDataMsg()
                Timber.d("displayImportResults() ERR_NO_MEDICINES_IN_IMPORTED_DATA")
            }
            ImportResult.ERR_CONFLICTING_MEDICINES -> {
                _message.value = buildConflictingMedicinesMsg(conflictingMedicines)
                Timber.d("displayImportResults() ERR_CONFLICTING_MEDICINES")
            }
        }
    }

    private fun readJsonFromUri(uri: Uri): String? {
        var bufferedReader: BufferedReader? = null
        var result: String? = null
        try {
            bufferedReader = getApplication<MSTApplication>().contentResolver.openInputStream(uri)?.bufferedReader()
            result = bufferedReader?.readText()
        } catch (e: Exception) {
            Timber.e("readFromUri(): Exception when reading from Uri: $uri  ${e.localizedMessage}")
        } finally {
            bufferedReader?.close()
        }
        if (result == null) {
            Timber.e("readFromUri(): Couldn't read from Uri: $uri")
        }
        Timber.v("readJsonFromUri(): JSON:  $result")
        return result
    }

    private fun parseJsonString(jsonStr: String): SharedData? {
        var importedData: SharedData? = null
        try {
            importedData = Gson().fromJson(jsonStr, SharedData::class.java)
        } catch (e: Exception) {
            Timber.e("parseJsonString(): Exception when parsing JSON: $jsonStr  ${e.localizedMessage}")
        }
        return importedData
    }

    /**
     * For each medicine in the argument importedData, see if there is a medicine in the
     * database with the same name.
     * @return null if there are no conflicts. Otherwise, a list of conflicting medicines.
     */
    private fun findConflictingData(importedData: SharedData?): List<Medicine>? {
        val medicines = medicinesDao.getAllMedicines()
        val importedMedicines = importedData?.medicines ?: return null
        val conflictingMedicines = mutableListOf<Medicine>()
        for (importedMedicine in importedMedicines) {
            for (medicine in medicines) {
                if (importedMedicine.name == medicine.name) {
                    conflictingMedicines.add(medicine)
                }
            }
        }
        return if (conflictingMedicines.isEmpty()) null else conflictingMedicines
    }

    private fun buildImportMedicineListMsg(importedMedicines: List<Medicine>?): Spanned {
        val sb = StringBuilder()
        sb.append(getApplication<MSTApplication>().getString(R.string.import_msg_head))
        if (importedMedicines != null) {
            for (medicine in importedMedicines) {
                sb.append(String.format(getApplication<MSTApplication>().getString(R.string.import_msg_list_item), medicine.name))
            }
        }
        sb.append(getApplication<MSTApplication>().getString(R.string.import_msg_tail))
        return HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun buildReadFromUriErrorMsg(): Spanned {
        return HtmlCompat.fromHtml(getApplication<MSTApplication>().getString(R.string.import_uri_read_error), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun buildDataParsingErrorMsg(): Spanned {
        return HtmlCompat.fromHtml(getApplication<MSTApplication>().getString(R.string.import_data_format_error), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun buildNoMedicinesInImportedDataMsg(): Spanned {
        return HtmlCompat.fromHtml(getApplication<MSTApplication>().getString(R.string.import_no_medicines_in_imported_data_msg), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun buildConflictingMedicinesMsg(conflictingMedicines: List<Medicine>?): Spanned {
        val sb = StringBuilder()
        sb.append(getApplication<MSTApplication>().getString(R.string.import_conflict_head))
        if (conflictingMedicines != null) {
            for (medicine in conflictingMedicines) {
                sb.append(String.format(getApplication<MSTApplication>().getString(R.string.import_conflict_list_item), medicine.name))
            }
        }
        sb.append(getApplication<MSTApplication>().getString(R.string.import_conflict_tail))
        return HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    fun onCancel() {
        Timber.v("Cancel the import")
        // When the user click "Cancel", we just ask the activity to navigate "back". Since we're at
        // the top of the back-stack, this is like terminating the app.
        _navigateBack.value = OneTimeEvent(true)
    }

    fun onImport() {
        Timber.v("Do the import")

        uiScope.launch {
            importMedicinesToDatabase()
        }
    }

    private suspend fun importMedicinesToDatabase() {
        val importedMedicines: List<Medicine> = importedSharedData?.medicines ?: return
        withContext(Dispatchers.IO) {
            for (medicine in importedMedicines) {
                Timber.d("importMedicinesToDatabase(): inserting: ${medicine.name}")

                medicine.id = Medicine.ID_OF_UNINITIALIZED_MEDICINE  // Imported medicines are received with their old IDs which are of course rubbish
                medicinesDao.insertMedicine(medicine)

                logEvent(medicine.name,
                    getApplication<MSTApplication>().getString(R.string.logged_event_imported_med,
                        medicine.name, medicine.dailyDose, medicine.nAvailableOriginally,
                        medicine.getLastUpdatedStr(getApplication())))
            }
        }
        _importSummary.value = OneTimeEvent(importedMedicines.size)
    }

    private fun logEvent(medicineName: String, msg: String) {
        val currentTime = System.currentTimeMillis()
        val loggedEvent = LoggedEvent(dateAndTime = currentTime, medicineName = medicineName,
            type = LoggedEvent.TYPE_IMPORT_MEDICINE, text = msg)
        loggedEventsDao.insertLoggedEvent(loggedEvent)
    }

    private enum class ImportResult {
        OK,
        ERR_CANNOT_READ_FROM_URI,
        ERR_IMPORTED_DATA_PARSING_ERROR,
        ERR_NO_MEDICINES_IN_IMPORTED_DATA,
        ERR_CONFLICTING_MEDICINES
    }
}
