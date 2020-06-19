package com.liorhass.android.medsstocktracker.util

import android.app.Dialog

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liorhass.android.medsstocktracker.R

/**
 * Show an alert dialog with a positive and a negative buttons.
 * Usage example:
 *
 *   val alertDialogFragment = AlertDialogFragment.newInstance(
 *       "My-Dialog-Title",
 *       "My-Dialog-Message",
 *       object : AlertDialogFragment.Companion.AlertDialogListener {
 *           override fun positiveButtonClicked() {
 *               // Handle positive button
 *               Timber.v("positive button")
 *               viewModel.doSomething()
 *           }
 *           override fun negativeButtonClicked() {
 *               // Handle negative button
 *               Timber.v("negative button")
 *           }
 *       }
 *   ).show(activity.supportFragmentManager, AlertDialogFragment::class.java.name)
 *
 */
class AlertDialogFragment : DialogFragment() {
    companion object {
        fun newInstance(
            dialogTitle: String,
            dialogMsg: String,
            positiveButtonLabel: String,
            negativeButtonLabel: String,
            onClickListener: AlertDialogListener? = null
        ) = AlertDialogFragment().apply {
            this.arguments = Bundle().apply {
                this.putString(DIALOG_TITLE, dialogTitle)
                this.putString(DIALOG_MSG, dialogMsg)
                this.putString(POSITIVE_BUTTON_LABEL, positiveButtonLabel)
                this.putString(NEGATIVE_BUTTON_LABEL, negativeButtonLabel)
                this.putParcelable(CLICK_LISTENER, onClickListener)
            }
        }

        interface AlertDialogListener : Parcelable {
            fun positiveButtonClicked()
            fun negativeButtonClicked()
            override fun describeContents(): Int = 0
            override fun writeToParcel(dest: Parcel, flags: Int) {}
        }

        const val DIALOG_TITLE = "title"
        const val DIALOG_MSG = "msg"
        const val POSITIVE_BUTTON_LABEL = "pbl"
        const val NEGATIVE_BUTTON_LABEL = "nbl"
        const val CLICK_LISTENER = "c_l"
    }

    private var onClickListener: AlertDialogListener? = null

// If we wanted a customized layout for this dialog, we would have used this:
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
//        return inflater.inflate(R.layout.my_dialog, container, false)
//    }
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//          ...
//    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title: String? = arguments?.getString(DIALOG_TITLE)
        val msg: String? = arguments?.getString(DIALOG_MSG)
        val positiveButtonLabel: String? = arguments?.getString(POSITIVE_BUTTON_LABEL)
        val negativeButtonLabel: String? = arguments?.getString(NEGATIVE_BUTTON_LABEL)
        onClickListener = arguments?.getParcelable(CLICK_LISTENER)

        val alertDialogBuilder = MaterialAlertDialogBuilder(this.requireActivity(), R.style.ThemeOverlay_MedsStockTracker_MaterialAlertDialog)
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setMessage(msg)
        alertDialogBuilder.setPositiveButton(positiveButtonLabel)
        { dialog, _ ->
            // on positive button
            onClickListener?.positiveButtonClicked()
            dialog.dismiss()
        }
        alertDialogBuilder.setNegativeButton(negativeButtonLabel)
        { dialog, _ ->
            onClickListener?.negativeButtonClicked()
            dialog.dismiss()
        }
        return alertDialogBuilder.create()
    }
}

