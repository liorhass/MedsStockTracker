package com.liorhass.android.medsstocktracker.medicinelist

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.Medicine
import com.liorhass.android.medsstocktracker.databinding.FragmentMedicineListItemBinding
import com.liorhass.android.medsstocktracker.model.ImageTypes
import com.liorhass.android.medsstocktracker.model.calculateStatusImage
import com.liorhass.android.medsstocktracker.model.expectedRunOutDateAndTime

class MedicineListAdapter(val viewModel: MedicineListViewModel, val context: Context) :
    Adapter<MedicineListAdapter.ViewHolder>() {

    // Cache images so we don't have to create the Drawable every time
    private var imageStatusOk: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_check_circle_outline_green_48dp)
    private var imageStatusWarn: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_information_outline_orange_48dp)
    private var imageStatusAlert: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_alert_outline_red_48dp)

    private var data: List<Medicine> = listOf() // Injected by calling our setMedicines() function
    var selectionTracker: SelectionTracker<Long>? = null  // Build by the fragment and injected here

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentMedicineListItemBinding.inflate(inflater, parent, false)
        binding.viewModel = viewModel
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medicine = data[position]
        holder.bind(medicine,selectionTracker?.isSelected(medicine.id) ?: false)
    }

    override fun getItemCount(): Int = data.size
    override fun getItemId(position: Int): Long = data[position].id

    inner class ViewHolder(private val binding: FragmentMedicineListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(medicine: Medicine, isActivated: Boolean) {
            binding.medicine = medicine
            binding.statusImage.setImageDrawable(
                when (medicine.calculateStatusImage(binding.statusImage.context)) {
                    ImageTypes.STATUS_OK -> imageStatusOk
                    ImageTypes.STATUS_WARN -> imageStatusWarn
                    else -> imageStatusAlert
                }
            )
            binding.itemSelectedCheckMark.visibility = if (isActivated) View.VISIBLE else View.GONE
            binding.root.isActivated = isActivated
        }

        // Required for supporting selections
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = adapterPosition
                override fun getSelectionKey(): Long? = itemId
    //                override fun inSelectionHotspot(e: MotionEvent): Boolean = false // No selection-hotspot in our items
    //                override fun inDragRegion(e: MotionEvent): Boolean = true // No mouse driven band-selection in our items
            }
    }

    fun setMedicines(medicinesList: List<Medicine>) {
        data = sortMedicineList(medicinesList)
        notifyDataSetChanged() // Notify the RecyclerView that the data has changed todo: replace with diffing
    }

    fun reSortData() {
        data = sortMedicineList(data)
    }

    private fun sortMedicineList(origList: List<Medicine>): List<Medicine> {
        val sortBy =
            PreferenceManager.getDefaultSharedPreferences(context).getInt(
                context.getString(R.string.pref_medicine_sort_key),
                MedicineListFragment.SORT_BY_URGENCY
            )
        return if (sortBy == MedicineListFragment.SORT_BY_URGENCY) {
            origList.sortedBy {it.expectedRunOutDateAndTime}
        } else {
            origList.sortedBy {it.name}
        }
    }
}