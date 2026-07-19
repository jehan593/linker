package com.linker.app.util

import java.text.DateFormat
import java.util.Date

fun formatSavedAt(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
