package com.liorhass.android.medsstocktracker.util

import com.liorhass.android.medsstocktracker.database.LoggedEvent
import com.liorhass.android.medsstocktracker.database.Medicine

class SharedData (
    val version: Int = 1,
    val time: Long = System.currentTimeMillis(),
    val medicines: List<Medicine>? = null,
    val loggedEvents: List<LoggedEvent>? = null
) {
    object Constants {
        const val AUTHORITY: String = "com.liorhass.android.medsstocktracker.fileprovider" // Must be in sync with AndroidManifest.xml
        const val DIR_NAME: String = "shares" // Must be in sync with xml/filepaths.xml
        const val FILE_NAME: String = "mst.mstdbx"
    }
}