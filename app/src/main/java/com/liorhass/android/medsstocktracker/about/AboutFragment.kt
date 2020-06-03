package com.liorhass.android.medsstocktracker.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.liorhass.android.medsstocktracker.R
import com.liorhass.android.medsstocktracker.database.AppDatabase

class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val topView: View = inflater.inflate(R.layout.fragment_about, container, false)
        val textView = topView.findViewById<View>(R.id.about_version_text) as TextView
        textView.text = HtmlCompat.fromHtml(
            String.format(getString(R.string.about_message), AppDatabase.DATABASE_VERSION),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        return topView
    }
}