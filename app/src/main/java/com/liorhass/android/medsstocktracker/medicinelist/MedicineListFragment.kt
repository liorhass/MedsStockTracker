// Using SelectionTracker with a RecyclerView:
// https://androidkt.com/recyclerview-selection-28-0-0/
//    https://github.com/Thumar/recyclerview-selection/blob/678ef933be92f65be1537affa3d6887d6a1f11f2/app/src/main/java/com/androidkt/recyclerviewselection/adapter/ItemListAdapter.java
//    https://github.com/Thumar/recyclerview-selection/blob/678ef933be92f65be1537affa3d6887d6a1f11f2/app/src/main/java/com/androidkt/recyclerviewselection/MainActivity.java
//    https://github.com/Thumar/recyclerview-selection/blob/678ef933be92f65be1537affa3d6887d6a1f11f2/app/src/main/java/com/androidkt/recyclerviewselection/ActionModeController.java
// https://proandroiddev.com/a-guide-to-recyclerview-selection-3ed9f2381504
//    https://github.com/marcosholgado/multiselection/blob/master/app/src/main/java/com/marcosholgado/multiselection/MainAdapter.kt
// https://medium.com/@Ashok_Varma/new-androidx-api-selectiontracker-df25bf807e79
//    https://github.com/Ashok-Varma/AndroidX_Exp/blob/master/app/src/main/java/com/ashokvarma/androidx/recyclerview/selection/PokemonRecyclerAdapter.java
//
//
package com.liorhass.android.medsstocktracker.medicinelist

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import com.google.android.material.snackbar.Snackbar
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.AppDatabase
import com.liorhass.android.medsstocktracker.database.Medicine
import com.liorhass.android.medsstocktracker.databinding.FragmentMedicineListBinding
import com.liorhass.android.medsstocktracker.util.AlertDialogFragment
import com.liorhass.android.medsstocktracker.util.NavigationDestinations
import com.liorhass.android.medsstocktracker.util.NavigationEventWithLongArgument
import com.liorhass.android.medsstocktracker.util.OneTimeEvent
import timber.log.Timber


/**
 * A fragment that displays the list of medicines the user defined.
 */
class MedicineListFragment : Fragment() {

    private lateinit var binding: FragmentMedicineListBinding
    private lateinit var viewModel: MedicineListViewModel
    private var actionMode: ActionMode? = null
    private lateinit var adapter: MedicineListAdapter
    private var selectionTracker : SelectionTracker<Long>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Get a reference to our ViewModel using our ViewModelFactory
        val application = requireNotNull(this.activity).application
        val medicinesDao = AppDatabase.getInstance(application).medicineDao
        val loggedEventsDao = AppDatabase.getInstance(application).loggedEventDao
        val vewModelFactory = MedicineListViewModelFactory(medicinesDao, loggedEventsDao, application)
        viewModel = ViewModelProvider(this, vewModelFactory).get(MedicineListViewModel::class.java)
        viewModel.navigationTrigger.observe(viewLifecycleOwner, Observer {
            navigateToDestination(it)
        })
        viewModel.confirmDeletion.observe(viewLifecycleOwner, Observer {
            confirmMedicineDeletion(it)
        })
        viewModel.launchShare.observe(viewLifecycleOwner, Observer {
            confirmSharing(it)
        })

        binding = FragmentMedicineListBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        // Our Floating Action Button
        binding.fab.setOnClickListener { fab ->
            actionMode?.finish()
            actionMode = null
            val action = MedicineListFragmentDirections.actionMedicineListFragmentToEditMedicineFragment(
                Medicine.ID_OF_UNINITIALIZED_MEDICINE)
            fab.findNavController().navigate(action)
        }

        // Set the adapter of our RecyclerView
        adapter = MedicineListAdapter(viewModel, requireNotNull(context))
        binding.medicineListRecyclerView.adapter = adapter
        // Observe changes in the database, and reload the adapter with the updated data
        viewModel.medicines.observe(viewLifecycleOwner, Observer {
            adapter.setMedicines(it)
        })

        buildSelectionTracker(adapter)

        // Tell Android that we have a menu. It will call our onCreateOptionsMenu()
        setHasOptionsMenu(true)

        return binding.root
    }

    private fun buildSelectionTracker(adapter: MedicineListAdapter) {
        selectionTracker = SelectionTracker.Builder(
            "medicine_list_selection",
            binding.medicineListRecyclerView,
            MedicineListItemKeyProvider(binding.medicineListRecyclerView),
            MedicineListItemDetailsLookup(binding.medicineListRecyclerView),
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
                            MedicineListActionModeController(
                                selectionTracker as SelectionTracker<Long>,
                                viewModel
                            )
                        )
                        actionMode?.title = getString(R.string.fragment_title_medicines)
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

    // In our onCreateView() we called setHasOptionsMenu(true). This then calls us here.
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_medicines_list, menu)
    }

    // Called every time (just before) the menu is opened
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val checkedItemId: Int
        val sharedPreferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(activity)
        val sortBy =
            sharedPreferences.getInt(getString(R.string.pref_medicine_sort_key), SORT_BY_URGENCY)
        checkedItemId = if (sortBy == SORT_BY_URGENCY) {
            R.id.menuSortByUrgency
        } else {
            R.id.menuSortAlphabetically
        }
        menu.findItem(checkedItemId).isChecked = true
    }

    @SuppressLint("ApplySharedPref")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuSortByUrgency -> {
                doSortCriteriaSelection(SORT_BY_URGENCY)
            }
            R.id.menuSortAlphabetically -> {
                doSortCriteriaSelection(SORT_ALPHABETICALLY)
            }
            R.id.menuShare -> {
                viewModel.doShare()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun doSortCriteriaSelection(sortCriteria: Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val previousSortBy = sharedPreferences.getInt(getString(R.string.pref_medicine_sort_key), SORT_BY_URGENCY)
        if (previousSortBy != sortCriteria) {
            sharedPreferences.edit().putInt(getString(R.string.pref_medicine_sort_key), sortCriteria).apply()
            // Tell our recyclerView to redraw. (This is done by re-assigning its adapter)
            adapter.reSortData()
            binding.medicineListRecyclerView.adapter = adapter
            requireActivity().invalidateOptionsMenu() // So our onPrepareOptionsMenu() will get called next time the menu is opened
        }
    }

    private fun navigateToDestination(navigationEvent: OneTimeEvent<NavigationEventWithLongArgument>) {
        navigationEvent.getContentIfNotHandled()?.let { event -> // Only proceed if the event has never been handled
            when(event.destination) {
                NavigationDestinations.NAVIGATION_DESTINATION_MEDICINE_DETAILS_FRAGMENT -> {
                    if (selectionTracker?.selection?.size() ?: 0 > 0) {
                        // Just a hack to bypass a SelectionTracker bug where a long-press on a selected
                        // item behaves like a click on an item when there is no selection at all (i.e. to
                        // navigation to the edit-medicine screen) while maintaining the selection and
                        // the active ActionMode. Bizarre.
                        return
                    }
                    val medicineId = event.arg
                    Timber.v("Navigating to EditMedicineFragment medicineId=$medicineId")
                    val action =
                        MedicineListFragmentDirections.actionMedicineListFragmentToEditMedicineFragment(
                            medicineId
                        )
                    findNavController().navigate(action)
                }
                else -> Timber.wtf("navigateToDestination(): Unknown destination: ${event.destination}")
            }
        }
    }

    // Delete button clicked. Show a confirmation dialog, and if the user confirms, delete the selected medicines.
    private fun confirmMedicineDeletion(oneTimeEvent: OneTimeEvent<Boolean>) {
        if (oneTimeEvent.getContentIfNotHandled() != null) {
            val nMedicinesToDelete = selectionTracker!!.selection.size()
            AlertDialogFragment.newInstance(
                getString(R.string.delete_medicine_confirmation_dialog_title),
                resources.getQuantityString(
                    R.plurals.delete_medicine_confirmation_dialog_msg,
                    nMedicinesToDelete, nMedicinesToDelete
                ),
                getString(R.string.delete),
                getString(R.string.cancel),
                object : AlertDialogFragment.Companion.AlertDialogListener {
                    override fun positiveButtonClicked() {
                        // Handle positive button
                        Timber.v("positive button")
                        viewModel.deleteMedicines(selectionTracker!!.selection)
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

    private fun confirmSharing(oneTimeEvent: OneTimeEvent<Uri>) {
        val uri: Uri? = oneTimeEvent.getContentIfNotHandled()
        if (uri != null) {
            AlertDialogFragment.newInstance(
                getString(R.string.share_confirmation_dialog_title),
                getString(R.string.share_confirmation_dialog_msg),
                getString(R.string.btn_continue),
                getString(R.string.cancel),
                object : AlertDialogFragment.Companion.AlertDialogListener {
                    override fun positiveButtonClicked() {
                        // Handle positive button
                        launchShareChooser(uri)
                    }
                    override fun negativeButtonClicked() {}
                }
            ).show(requireActivity().supportFragmentManager, AlertDialogFragment::class.java.name)
        }
    }

    private fun launchShareChooser(uri: Uri) {
        Timber.v("launchShare(): Uri=$uri")
        // See: https://medium.com/androiddevelopers/sharing-content-between-android-apps-2e6db9d1368b  and  https://stackoverflow.com/a/52843942/1071117
        val shareIntent = ShareCompat.IntentBuilder.from(requireActivity())
            .setStream(uri)
            .setType(context?.contentResolver?.getType(uri))
            .setSubject(getString(R.string.share_subject_string))
            .intent
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(shareIntent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(
                binding.medicineListCoordinatorLayout,
                getString(R.string.no_app_support_share),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        const val SORT_BY_URGENCY = 1
        const val SORT_ALPHABETICALLY = 2
    }
}
