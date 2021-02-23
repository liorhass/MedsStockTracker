package com.liorhass.android.medsstocktracker.fimport

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.AppDatabase
import com.liorhass.android.medsstocktracker.databinding.DialogFragmentImportBinding
import com.liorhass.android.medsstocktracker.util.AlertDialogFragment
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import timber.log.Timber

class ImportFragment : Fragment() {

    private lateinit var viewModel: ImportViewModel
    private lateinit var binding: DialogFragmentImportBinding

    private val args: ImportFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogFragmentImportBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this

        // Get a reference to our ViewModel using our ViewModelFactory
        val medicinesDao = AppDatabase.getInstance(requireActivity().application).medicineDao
        val loggedEventsDao = AppDatabase.getInstance(requireActivity().application).loggedEventDao
        val vewModelFactory = ImportViewModelFactory(medicinesDao, loggedEventsDao, requireActivity().application)
        viewModel = ViewModelProvider(this, vewModelFactory).get(ImportViewModel::class.java)
        viewModel.navigateBack.observe(viewLifecycleOwner, {
            navigateBack(it)
        })
        viewModel.importSummary.observe(viewLifecycleOwner, {
            showImportSummary(it)
        })

        binding.viewModel = viewModel

        Timber.d("onCreateView(): uri=${args.importSourceUri}")
        viewModel.setUri(args.importSourceUri)

        return binding.root
    }

    private fun showImportSummary(oneTimeEvent: OneTimeEvent<Int>) {
        val nMedicinesImported: Int? = oneTimeEvent.getContentIfNotHandled()
        if (nMedicinesImported != null) {
            summarizePerformedImport(nMedicinesImported)
        }
    }

    private fun summarizePerformedImport(nMedicinesImported: Int) {
        AlertDialogFragment.newInstance(
            getString(R.string.import_complete_dialog_title),
            getString(R.string.import_complete_dialog_msg, nMedicinesImported),
            getString(R.string.import_complete_dialog_button_text),
            "",
            object : AlertDialogFragment.Companion.AlertDialogListener {
                override fun positiveButtonClicked() {
                    findNavController().navigate(ImportFragmentDirections.actionImportDialogFragmentToMedicineListFragment())
                }

                override fun negativeButtonClicked() {/* should not happen since there's no button */}
            }
        ).show(requireActivity().supportFragmentManager, AlertDialogFragment::class.java.name)
    }

    private fun navigateBack(oneTimeEvent: OneTimeEvent<Boolean>) {
        if (oneTimeEvent.getContentIfNotHandled() != null) {
            findNavController().navigate(ImportFragmentDirections.actionImportDialogFragmentToMedicineListFragment())
        }
    }
}