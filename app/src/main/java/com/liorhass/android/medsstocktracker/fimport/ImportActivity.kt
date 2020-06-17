package com.liorhass.android.medsstocktracker.fimport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.liorhass.android.medsstocktracker.MainActivity
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.AppDatabase
import com.liorhass.android.medsstocktracker.databinding.ActivityImportBinding
import com.liorhass.android.medsstocktracker.util.AlertDialogFragment
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import timber.log.Timber

class ImportActivity : AppCompatActivity() {

    private lateinit var viewModel: ImportViewModel
    private lateinit var binding: ActivityImportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityImportBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)

        // Get a reference to our ViewModel using our ViewModelFactory
        val medicinesDao = AppDatabase.getInstance(application).medicineDao
        val loggedEventsDao = AppDatabase.getInstance(application).loggedEventDao
        val vewModelFactory = ImportViewModelFactory(medicinesDao, loggedEventsDao, application)
        viewModel = ViewModelProvider(this, vewModelFactory).get(ImportViewModel::class.java)
        viewModel.navigateBack.observe(this, Observer {
            navigateBack(it)
        })
        viewModel.importSummary.observe(this, Observer {
            showImportSummary(it)
        })

        binding.viewModel = viewModel

        Timber.d("onCreate():  Action=${intent.action}")
        var uri: Uri? = null
        try {
            uri = intent?.data
        } catch (e: Exception) {
            Timber.e("Exception when trying to get URI from Intent: ${e.localizedMessage}")
        }
        if (uri != null) {
            // Do the actual import work
            viewModel.setUri(uri)
        } else {
            Timber.e("No URI in intent")
        }
    }

    private fun navigateBack(oneTimeEvent: OneTimeEvent<Boolean>) {
        if (oneTimeEvent.getContentIfNotHandled() != null) {
            onBackPressed()
        }
    }

    private fun showImportSummary(oneTimeEvent: OneTimeEvent<Int>) {
        val nMedicinesImported: Int? = oneTimeEvent.getContentIfNotHandled()
        if (nMedicinesImported != null) {
            summarizeImport(nMedicinesImported)
        }
    }

    private fun summarizeImport(nMedicinesImported: Int) {
        AlertDialogFragment.newInstance(
            getString(R.string.import_complete_dialog_title),
            getString(R.string.import_complete_dialog_msg, nMedicinesImported),
            getString(R.string.import_complete_dialog_button_text),
            "",
            object : AlertDialogFragment.Companion.AlertDialogListener {
                override fun positiveButtonClicked() {
                    val intent = Intent(this@ImportActivity, MainActivity::class.java)
                    startActivity(intent)
                }

                override fun negativeButtonClicked() {/* should not happen since there's no button */}
            }
        ).show(supportFragmentManager, AlertDialogFragment::class.java.name)
    }
}