package com.yapyap.core.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

fun todayDateKey(): String = todayLocalDate().toDateKey()

fun yesterdayDateKey(): String = todayLocalDate().minus(1, DateTimeUnit.DAY).toDateKey()

fun formatDateLabel(
    dateKey: String,
    todayKey: String,
    yesterdayKey: String,
    todayLabel: String,
    yesterdayLabel: String,
): String {
    return when (dateKey) {
        todayKey -> todayLabel
        yesterdayKey -> yesterdayLabel
        else -> dateKey.toFriendlyDate()
    }
}

private fun String.toFriendlyDate(): String {
    val parts = split("-")
    if (parts.size != 3) return this
    val year = parts[0]
    val month = parts[1].toIntOrNull() ?: return this
    val day = parts[2].toIntOrNull() ?: return this
    val monthName = FRIENDLY_MONTH_NAMES.getOrElse(month - 1) { parts[1] }
    return "$monthName $day, $year"
}

private val FRIENDLY_MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
