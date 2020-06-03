package com.liorhass.android.medsstocktracker.model

import com.liorhass.android.medsstocktracker.database.LoggedEvent
import java.text.SimpleDateFormat
import java.util.*

private val dateFormatForDayAndMonth = SimpleDateFormat("EEE, d MMM  HH:mm") // Fri, 7 Apr  14:33 //todo: should be replaced w/ locale aware formatter

fun LoggedEvent.getDateAndTime(): String = dateFormatForDayAndMonth.format(Date(dateAndTime))
