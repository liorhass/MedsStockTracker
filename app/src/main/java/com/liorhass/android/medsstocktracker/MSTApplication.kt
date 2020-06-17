package com.liorhass.android.medsstocktracker

import android.app.Application
import com.liorhass.android.medsstocktracker.util.setUserSelectedTheme
import timber.log.Timber
import timber.log.Timber.DebugTree

class MSTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        setUserSelectedTheme(this)
    }
}