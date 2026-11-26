package dev.hermeskotlin.ui.calendar

import androidx.compose.ui.graphics.Color
import dev.hermeskotlin.core.calendar.CalendarDate
import dev.hermeskotlin.core.calendar.plusDays

internal fun parseCalendarHexColor(hex: String?, fallback: Color): Color {
    val clean = hex?.trim()?.removePrefix("#") ?: return fallback
    return when (clean.length) {
        6 -> clean.toLongOrNull(16)?.let { Color(0xFF000000L or it) } ?: fallback
        8 -> clean.toLongOrNull(16)?.let { Color(it) } ?: fallback
        else -> fallback
    }
}

internal fun contrastOnFill(bg: Color): Color {
    val lum = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (lum > 0.62f) Color(0xFF111827) else Color.White
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val MONTH_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val DOW_SHORT = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DOW_SINGLE = listOf("M", "T", "W", "T", "F", "S", "S")

internal fun monthFullName(month: Int): String = MONTH_NAMES.getOrElse(month - 1) { "" }
internal fun monthShortName(month: Int): String = MONTH_SHORT.getOrElse(month - 1) { "" }
internal fun dowShortName(dayOfWeek: Int): String = DOW_SHORT.getOrElse(dayOfWeek - 1) { "" }
internal fun dowSingleLetter(dayOfWeek: Int): String = DOW_SINGLE.getOrElse(dayOfWeek - 1) { "" }

internal fun formatScheduleDateSublabel(date: CalendarDate): String =
    "${monthShortName(date.month).uppercase()}, ${dowShortName(date.dayOfWeek)}"

internal fun formatCalendarRangeTitle(
    mode: CalendarViewMode,
    anchor: CalendarDate,
    visibleDays: List<CalendarDate> = emptyList(),
): String {
    if (mode == CalendarViewMode.SCHEDULE) {
        val end = anchor.plusDays(44)
        return if (anchor.year == end.year && anchor.month == end.month) {
            "${monthFullName(anchor.month)} ${anchor.year}"
        } else {
            "${monthShortName(anchor.month)} ${anchor.year} – ${monthShortName(end.month)} ${end.year}"
        }
    }
    if (mode == CalendarViewMode.MONTH || visibleDays.isEmpty()) {
        return "${monthFullName(anchor.month)} ${anchor.year}"
    }
    val first = visibleDays.first()
    val last = visibleDays.last()
    return when {
        first.year == last.year && first.month == last.month -> "${monthFullName(first.month)} ${first.year}"
        first.year == last.year -> "${monthShortName(first.month)} – ${monthShortName(last.month)} ${last.year}"
        else -> "${monthShortName(first.month)} ${first.year} – ${monthShortName(last.month)} ${last.year}"
    }
}
