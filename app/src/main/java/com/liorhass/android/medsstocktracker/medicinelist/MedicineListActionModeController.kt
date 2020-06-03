package com.liorhass.android.medsstocktracker.medicinelist

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.selection.SelectionTracker
import com.liorhass.android.medsstocktracker.R
import timber.log.Timber


class MedicineListActionModeController(
    private val selectionTracker: SelectionTracker<Long>,
    private val viewModel: MedicineListViewModel
) : ActionMode.Callback {

    override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
        actionMode?.menuInflater?.inflate(R.menu.fragment_medicine_list_cab, menu)
        return true
    }

    override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(actionMode: ActionMode?, menuItem: MenuItem?): Boolean {
        Timber.v("onActionItemClicked(): menuItem=${menuItem.toString()}")
        when (menuItem!!.itemId) {
            // Delete button clicked.
            R.id.medicine_list_cab_menu_delete -> {
                viewModel.onMedicineDeletionClicked()
            }
        }
        return true
    }

    override fun onDestroyActionMode(actionMode: ActionMode?) {
        selectionTracker.clearSelection()
    }
}