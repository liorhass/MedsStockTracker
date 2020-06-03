// https://proandroiddev.com/a-guide-to-recyclerview-selection-3ed9f2381504
// https://medium.com/@Ashok_Varma/new-androidx-api-selectiontracker-df25bf807e79
// https://androidkt.com/recyclerview-selection-28-0-0/
package com.liorhass.android.medsstocktracker.medicinelist

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.widget.RecyclerView

// Provide the SelectionTracker item's position given a key, and a key given position
class MedicineListItemKeyProvider(private val recyclerView: RecyclerView) :
    ItemKeyProvider<Long>(SCOPE_MAPPED) {

    override fun getKey(position: Int): Long? {
        return recyclerView.adapter?.getItemId(position)
    }

    override fun getPosition(key: Long): Int {
        val viewHolder = recyclerView.findViewHolderForItemId(key)
        return viewHolder?.layoutPosition ?: RecyclerView.NO_POSITION
    }
}

// Provides the selection library access to RecyclerView items' details given a MotionEvent
class MedicineListItemDetailsLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<Long>() {
    override fun getItemDetails(event: MotionEvent): ItemDetails<Long>? {
        val view = recyclerView.findChildViewUnder(event.x, event.y)
        if (view != null) {
            val viewHolder = recyclerView.getChildViewHolder(view)
            if (viewHolder is MedicineListAdapter.ViewHolder) {
                return viewHolder.getItemDetails()
            }
        }
        return null
    }
}