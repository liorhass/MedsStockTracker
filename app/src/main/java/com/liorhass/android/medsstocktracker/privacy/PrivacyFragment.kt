package com.liorhass.android.medsstocktracker.privacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.liorhass.android.medsstocktracker.R

class PrivacyFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val topView: View = inflater.inflate(R.layout.fragment_privacy, container, false)
        val webView = topView.findViewById<View>(R.id.privacyWebView) as WebView
        webView.loadUrl(getString(R.string.privacy_file_url))
        return topView
    }
}