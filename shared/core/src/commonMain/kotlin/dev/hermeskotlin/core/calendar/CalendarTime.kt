package dev.hermeskotlin.core.calendar

/**
 * Civil date in ISO-8601 coordinates. [month] is 1..12, [day] is 1..31,
 * and [dayOfWeek] is 1 (Monday) .. 7 (Sunday).
 */
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val epochDay: Long,
    val dayOfWeek: Int,
) : Comparable<CalendarDate> {
    val iso: String
        get() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    override fun compareTo(other: CalendarDate): Int = epochDay.compareTo(other.epochDay)
}

/** Instant projected onto a target timezone for calendar grid placement. */
data class CalendarZonedMoment(
    val date: CalendarDate,
    val hour: Int,
    val minute: Int,
    val epochMs: Long,
) {
    val minuteOfDay: Int get() = hour * 60 + minute
}

expect fun calendarToday(zoneId: String? = null): CalendarDate

expect fun calendarNowZoned(zoneId: String? = null): CalendarZonedMoment

expect fun calendarDateFromIso(isoDate: String): CalendarDate?

expect fun calendarDateFromEpochDay(epochDay: Long): CalendarDate

expect fun calendarAddMonths(year: Int, month: Int, deltaMonths: Int): Pair<Int, Int>

expect fun calendarDaysInMonth(year: Int, month: Int): Int

expect fun calendarZonedFromIso(isoTimestamp: String, zoneId: String? = null): CalendarZonedMoment?

expect fun calendarZonedFromEpochMs(epochMs: Long, zoneId: String? = null): CalendarZonedMoment

expect fun calendarToUtcIso(date: CalendarDate, hour: Int, minute: Int, zoneId: String? = null): String

fun CalendarDate.plusDays(days: Int): CalendarDate = calendarDateFromEpochDay(epochDay + days)

/** Formats `[hour]:[minute]` as `HH:MM`. */
fun formatClock24(hour: Int, minute: Int): String =
    "${hour.coerceIn(0, 23).toString().padStart(2, '0')}:${minute.coerceIn(0, 59).toString().padStart(2, '0')}"

/** Formats `[hour]:[minute]` compactly in 12h or 24h style matching Google Calendar cards. */
fun formatClockCompact(minuteOfDay: Int, use24Hour: Boolean = false): String {
    val clamped = minuteOfDay.coerceIn(0, 1439)
    val h = clamped / 60
    val m = clamped % 60
    if (use24Hour) return formatClock24(h, m)
    val suffix = if (h < 12) "am" else "pm"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return if (m == 0) "$h12$suffix" else "$h12:${m.toString().padStart(2, '0')}$suffix"
}
