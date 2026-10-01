package com.berealrecop.app.data

import android.net.Uri

data class BeRealItem(
    val id: String,
    val uri: Uri? = null,
    val filePath: String?,
    val fileName: String,
    val dayNumber: Int,       // E.g. 1, 2, ..., 31
    val monthName: String,    // E.g. "septembre"
    val year: Int,            // E.g. 2026
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0,
    val isIncluded: Boolean = true
) : Comparable<BeRealItem> {
    val monthIndex: Int
        get() = BeRealParser.monthToNumber(monthName)

    override fun compareTo(other: BeRealItem): Int {
        // Sort chronologically: year -> monthIndex / month -> day -> hour -> minute -> second
        if (year != other.year) return year.compareTo(other.year)
        val monthCmp = monthIndex.compareTo(other.monthIndex)
        if (monthCmp != 0) return monthCmp
        if (dayNumber != other.dayNumber) return dayNumber.compareTo(other.dayNumber)
        if (hour != other.hour) return hour.compareTo(other.hour)
        if (minute != other.minute) return minute.compareTo(other.minute)
        return second.compareTo(other.second)
    }
}
