package dev.hermeskotlin.core.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private fun resolveZone(zoneId: String?): ZoneId =
    zoneId?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        runCatching { ZoneId.of(raw) }.getOrNull()
    } ?: ZoneId.systemDefault()

private fun LocalDate.toCalendarDate(): CalendarDate = CalendarDate(
    year = year,
    month = monthValue,
    day = dayOfMonth,
    epochDay = toEpochDay(),
    dayOfWeek = dayOfWeek.value,
)

private fun ZonedDateTime.toZonedMoment(): CalendarZonedMoment = CalendarZonedMoment(
    date = toLocalDate().toCalendarDate(),
    hour = hour,
    minute = minute,
    epochMs = toInstant().toEpochMilli(),
)

actual fun calendarToday(zoneId: String?): CalendarDate =
    LocalDate.now(resolveZone(zoneId)).toCalendarDate()

actual fun calendarNowZoned(zoneId: String?): CalendarZonedMoment =
    ZonedDateTime.now(resolveZone(zoneId)).toZonedMoment()

actual fun calendarDateFromIso(isoDate: String): CalendarDate? {
    val clean = isoDate.trim().take(10)
    if (clean.length < 10) return null
    return runCatching { LocalDate.parse(clean).toCalendarDate() }.getOrNull()
}

actual fun calendarDateFromEpochDay(epochDay: Long): CalendarDate =
    LocalDate.ofEpochDay(epochDay).toCalendarDate()

actual fun calendarAddMonths(year: Int, month: Int, deltaMonths: Int): Pair<Int, Int> {
    val ym = YearMonth.of(year, month).plusMonths(deltaMonths.toLong())
    return ym.year to ym.monthValue
}

actual fun calendarDaysInMonth(year: Int, month: Int): Int =
    YearMonth.of(year, month).lengthOfMonth()

actual fun calendarZonedFromIso(isoTimestamp: String, zoneId: String?): CalendarZonedMoment? {
    val clean = isoTimestamp.trim()
    if (clean.isEmpty()) return null
    val zone = resolveZone(zoneId)
    val instant = runCatching { Instant.parse(clean) }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(clean).toInstant() }.getOrNull()
    if (instant != null) {
        return instant.atZone(zone).toZonedMoment()
    }
    val localDate = calendarDateFromIso(clean) ?: return null
    return ZonedDateTime.of(
        LocalDate.of(localDate.year, localDate.month, localDate.day),
        LocalTime.MIDNIGHT,
        zone,
    ).toZonedMoment()
}

actual fun calendarZonedFromEpochMs(epochMs: Long, zoneId: String?): CalendarZonedMoment =
    Instant.ofEpochMilli(epochMs).atZone(resolveZone(zoneId)).toZonedMoment()

actual fun calendarToUtcIso(date: CalendarDate, hour: Int, minute: Int, zoneId: String?): String {
    val zone = resolveZone(zoneId)
    val zdt = ZonedDateTime.of(
        LocalDate.of(date.year, date.month, date.day),
        LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
        zone,
    )
    return DateTimeFormatter.ISO_INSTANT.format(zdt.toInstant())
}
