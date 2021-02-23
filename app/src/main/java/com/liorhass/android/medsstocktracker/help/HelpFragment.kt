package com.liorhass.android.medsstocktracker.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.liorhass.android.medsstocktracker.R

class HelpFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val topView: View = inflater.inflate(R.layout.fragment_help, container, false)
        val webView = topView.findViewById<View>(R.id.helpWebView) as WebView
        webView.loadUrl(getString(R.string.help_file_url))
        return topView
    }
}