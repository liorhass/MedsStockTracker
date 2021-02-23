package com.liorhass.android.medsstocktracker.eventlist

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.LoggedEvent
import com.liorhass.android.medsstocktracker.databinding.FragmentEventListItemBinding

class EventListAdapter(val viewModel: EventListViewModel, val context: Context) :
    Adapter<EventListAdapter.ViewHolder>() {

    private var data: List<LoggedEvent> = listOf() // Injected by calling our setLoggedEvents() function
    var selectionTracker: SelectionTracker<Long>? = null  // Build by the fragment and injected here

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentEventListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medicine = data[position]
        holder.bind(medicine,selectionTracker?.isSelected(medicine.id) ?: false)
    }

    override fun getItemCount(): Int = data.size
    override fun getItemId(position: Int): Long = data[position].id

    inner class ViewHolder(private val binding: FragmentEventListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: LoggedEvent, isActivated: Boolean) {
            binding.loggedEvent = event
            binding.root.isActivated = isActivated
        }

        // Required for supporting selections
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = adapterPosition
                override fun getSelectionKey(): Long = itemId
    //                override fun inSelectionHotspot(e: MotionEvent): Boolean = false // No selection-hotspot in our items
    //                override fun inDragRegion(e: MotionEvent): Boolean = true // No mouse driven band-selection in our items
            }
    }

    fun setLoggedEvents(eventsList: List<LoggedEvent>) {
        data = sortEventList(eventsList)
        notifyDataSetChanged() // Notify the RecyclerView that the data has changed todo: replace with diffing
    }

    fun reSortData() {
        data = sortEventList(data)
    }

    private fun sortEventList(origList: List<LoggedEvent>): List<LoggedEvent> {
        val sortBy =
            PreferenceManager.getDefaultSharedPreferences(context).getInt(
                context.getString(R.string.pref_events_sort_key),
                EventListFragment.SORT_DESCENDING
            )
        return if (sortBy == EventListFragment.SORT_DESCENDING) {
            origList.sortedByDescending {it.dateAndTime}
        } else {
            origList.sortedBy {it.dateAndTime}
        }
    }
}