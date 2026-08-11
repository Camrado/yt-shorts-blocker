package com.example.ytshortsblocker.data

/** Shorts time for one calendar day. [date] is ISO, e.g. "2026-08-11". */
data class DayUsage(
    val date: String,
    val seconds: Int,
) {
    val minutes: Int get() = seconds / 60
}
