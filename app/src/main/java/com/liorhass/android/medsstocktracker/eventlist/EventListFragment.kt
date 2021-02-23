package com.liorhass.android.medsstocktracker.eventlist

import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.AppDatabase
import com.liorhass.android.medsstocktracker.databinding.FragmentEventListBinding
import com.liorhass.android.medsstocktracker.util.AlertDialogFragment
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import timber.log.Timber

/**
 * A fragment that displays the list of events that happened in the app
 */
class EventListFragment : Fragment() {

    private lateinit var binding: FragmentEventListBinding
    private lateinit var viewModel: EventListViewModel
    private var actionMode: ActionMode? = null
    private lateinit var adapter: EventListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Get a reference to our ViewModel using our ViewModelFactory
        val application = requireNotNull(this.activity).application
        val loggedEventsDao = AppDatabase.getInstance(application).loggedEventDao
        val vewModelFactory = EventListViewModelFactory(loggedEventsDao)
        viewModel = ViewModelProvider(this, vewModelFactory).get(EventListViewModel::class.java)
        viewModel.confirmDeletion.observe(viewLifecycleOwner, {
            confirmEventDeletion(it)
        })

        binding = FragmentEventListBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        // Set the adapter of our RecyclerView
        adapter = EventListAdapter(viewModel, requireNotNull(context))
        binding.eventListRecyclerView.adapter = adapter
        // Observe changes in the database, and reload the adapter with the updated data
        viewModel.loggedEvents.observe(viewLifecycleOwner, {
            adapter.setLoggedEvents(it)
        })

        buildSelectionTracker(adapter)

        // Tell Android that we have a menu. It will call our onCreateOptionsMenu()
        setHasOptionsMenu(true)

        return binding.root
    }

    // In our onCreate() we called setHasOptionsMenu(true). This then calls us here.
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_event_list, menu)
        Timber.v("onCreateOptionsMenu()")
    }

    private var selectionTracker : SelectionTracker<Long>? = null
    private fun buildSelectionTracker(adapter: EventListAdapter) {
        selectionTracker = SelectionTracker.Builder(
            "event_list_selection",
            binding.eventListRecyclerView,
            LoggedEventListItemKeyProvider(binding.eventListRecyclerView),
            LoggedEventListItemDetailsLookup(binding.eventListRecyclerView),
            StorageStrategy.createLongStorage()
        )
//            .withSelectionPredicate(SelectionPredicates.createSelectAnything())
//            .withOnItemActivatedListener { item, e ->
//                Timber.v("ItemActivated key=${item.selectionKey}  event=${MotionEvent.actionToString(e.action)}")
//                return@withOnItemActivatedListener true
//            }
            .build()

        selectionTracker?.addObserver(
            object : SelectionTracker.SelectionObserver<Long>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    if (selectionTracker?.hasSelection() == true && actionMode == null) {
                        // First item selected - replace toolbar with our CAB
                        actionMode = (activity as AppCompatActivity).startSupportActionMode(
                            EventListActionModeController(
                                selectionTracker as SelectionTracker<Long>,
                                viewModel
                            )
                        )
                        actionMode?.title = getString(R.string.fragment_title_events)
                        val nItemsSelected = selectionTracker?.selection?.size()
                        actionMode?.subtitle = resources.getQuantityString(R.plurals.items_selected,
                            nItemsSelected ?: 1, nItemsSelected)
                    } else if (selectionTracker?.hasSelection() != true && actionMode != null) {
                        // Last item was unselected - remove CAB
                        actionMode?.finish()
                        actionMode = null
                    } else {
                        val nItemsSelected = selectionTracker?.selection?.size()
                        actionMode?.subtitle = resources.getQuantityString(R.plurals.items_selected,
                            nItemsSelected ?: 1, nItemsSelected)
                    }
                }
                override fun onItemStateChanged(key: Long, selected: Boolean) {
                    Timber.v("onItemStateChanged(key=${key}), selected=${selected}")
                }
                override fun onSelectionRefresh() {
                    Timber.v("onSelectionRefresh()")
                }
                override fun onSelectionRestored() {
                    Timber.v("onSelectionRestored()")
                }
            }
        )

        adapter.selectionTracker = selectionTracker
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuSortOrder -> {
                // The user clicked on the "reverse-sort-order" button
                val sharedPreferences: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(activity)
                val previousSortBy =
                    sharedPreferences.getInt(getString(R.string.pref_events_sort_key), SORT_DESCENDING)
                val newSortBy = if (previousSortBy == SORT_DESCENDING) SORT_ASCENDING else SORT_DESCENDING
                sharedPreferences.edit().putInt(getString(R.string.pref_events_sort_key), newSortBy).apply()
                // Tell our recyclerView to redraw. (This is done by re-assigning its adapter)
                adapter.reSortData()
                binding.eventListRecyclerView.adapter = adapter
            }
        }
        return true
    }

    // Delete button clicked. Show a confirmation dialog, and if the user confirms, delete the selected events.
    private fun confirmEventDeletion(oneTimeEvent: OneTimeEvent<Boolean>) {
        if (oneTimeEvent.getContentIfNotHandled() != null) {
            val nEventsToDelete = selectionTracker!!.selection.size()
            AlertDialogFragment.newInstance(
                getString(R.string.delete_event_confirmation_dialog_title),
                resources.getQuantityString(
                    R.plurals.delete_event_confirmation_dialog_msg,
                    nEventsToDelete, nEventsToDelete
                ),
                getString(R.string.delete),
                getString(R.string.cancel),
                object : AlertDialogFragment.Companion.AlertDialogListener {
                    override fun positiveButtonClicked() {
                        // Handle positive button
                        Timber.v("positive button")
                        viewModel.deleteEvents(selectionTracker!!.selection)
                        selectionTracker!!.clearSelection()
                    }

                    override fun negativeButtonClicked() {
                        // Handle negative button
                        Timber.v("negative button")
                    }
                }
            ).show(requireActivity().supportFragmentManager, AlertDialogFragment::class.java.name)
        }
    }

    companion object {
        const val SORT_ASCENDING = 1
        const val SORT_DESCENDING = 2
    }
}
