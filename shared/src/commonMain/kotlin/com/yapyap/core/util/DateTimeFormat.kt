package com.yapyap.core.util

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** True for server ISO timestamps; false for optimistic placeholders. */
fun isRealTimestamp(value: String): Boolean =
    value != "pending" && value.contains("T")

/** Local calendar date key (`yyyy-MM-dd`) for grouping chat day separators. */
fun isoToLocalDateKey(isoTimestamp: String): String {
    if (!isRealTimestamp(isoTimestamp)) return todayDateKey()
    val instant = parseIsoInstant(isoTimestamp) ?: return isoTimestamp.substringBefore("T")
    return instant.toLocalDate(systemTimeZone()).toDateKey()
}

/** Time under each chat bubble, e.g. `3:45 PM`. */
fun formatMessageTime(isoTimestamp: String): String {
    if (!isRealTimestamp(isoTimestamp)) return ""
    val instant = parseIsoInstant(isoTimestamp) ?: return legacyTimeFallback(isoTimestamp)
    return formatLocalTime(instant)
}

/** Conversation list row timestamp — time today, `Yesterday`, or `Jun 21`. */
fun formatConversationTimestamp(isoTimestamp: String): String {
    if (!isRealTimestamp(isoTimestamp)) return ""
    val instant = parseIsoInstant(isoTimestamp) ?: return isoTimestamp.substringBefore("T")
    val messageDate = instant.toLocalDate(systemTimeZone())
    val today = todayLocalDate()
    return when (messageDate) {
        today -> formatLocalTime(instant)
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        else -> if (messageDate.year == today.year) {
            formatShortDate(messageDate)
        } else {
            formatFullDate(messageDate)
        }
    }
}

/** Current instant as ISO-8601 UTC for optimistic pending rows. */
fun currentIsoTimestamp(): String =
    Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()).toString()

internal fun parseIsoInstant(iso: String): Instant? = runCatching {
    val normalized = when {
        iso.endsWith("Z") || iso.contains("+") -> iso
        else -> "${iso}Z"
    }
    Instant.parse(normalized)
}.getOrNull()

internal fun systemTimeZone(): TimeZone = TimeZone.currentSystemDefault()

internal fun Instant.toLocalDate(timeZone: TimeZone): LocalDate =
    toLocalDateTime(timeZone).date

internal fun todayLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
        .toLocalDate(systemTimeZone())

internal fun LocalDate.toDateKey(): String {
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    return "$year-$month-$day"
}

internal fun formatLocalTime(instant: Instant): String {
    val local = instant.toLocalDateTime(systemTimeZone())
    val hour24 = local.hour
    val minute = local.minute.toString().padStart(2, '0')
    val (hour12, suffix) = when {
        hour24 == 0 -> 12 to "AM"
        hour24 < 12 -> hour24 to "AM"
        hour24 == 12 -> 12 to "PM"
        else -> (hour24 - 12) to "PM"
    }
    return "$hour12:$minute $suffix"
}

internal fun formatShortDate(date: LocalDate): String {
    val monthName = SHORT_MONTH_NAMES.getOrElse(date.monthNumber - 1) { date.monthNumber.toString() }
    return "$monthName ${date.dayOfMonth}"
}

internal fun formatFullDate(date: LocalDate): String {
    val monthName = SHORT_MONTH_NAMES.getOrElse(date.monthNumber - 1) { date.monthNumber.toString() }
    return "$monthName ${date.dayOfMonth}, ${date.year}"
}

private fun legacyTimeFallback(iso: String): String {
    val timePart = iso.substringAfter("T", "")
    return timePart.take(5)
}

private val SHORT_MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
