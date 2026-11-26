package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.calendar.CalendarDate
import dev.hermeskotlin.core.calendar.CalendarLayoutEngine
import dev.hermeskotlin.core.calendar.calendarAddMonths
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.calendar.calendarToday
import dev.hermeskotlin.core.calendar.formatClock24
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Chip
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.onAccent
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.cal_done_action
import dev.hermeskotlin.ui.resources.cal_next
import dev.hermeskotlin.ui.resources.cal_pick_hour
import dev.hermeskotlin.ui.resources.cal_pick_minute
import dev.hermeskotlin.ui.resources.cal_previous
import dev.hermeskotlin.ui.resources.cal_today
import org.jetbrains.compose.resources.stringResource

/** Readable 12h or 24h clock text for picker buttons and detail readouts. */
internal fun formatReadableClock(hour: Int, minute: Int, use24Hour: Boolean): String {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    if (use24Hour) return formatClock24(h, m)
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} $ampm"
}

/** Keeps an event's end time strictly after [newStart] when the user moves the start time forward. */
internal fun shiftEndTimeIfNeeded(oldStart: String, newStart: String, curEnd: String): String {
    val (oh, om) = parseTimeHHMM(oldStart) ?: return curEnd
    val (nh, nm) = parseTimeHHMM(newStart) ?: return curEnd
    val (eh, em) = parseTimeHHMM(curEnd) ?: return curEnd
    val oldStartMin = oh * 60 + om
    val newStartMin = nh * 60 + nm
    val curEndMin = eh * 60 + em
    if (curEndMin > newStartMin) return curEnd
    val duration = (curEndMin - oldStartMin).coerceIn(15, 12 * 60)
    val nextEnd = (newStartMin + duration).coerceAtMost(23 * 60 + 55)
    return formatClock24(nextEnd / 60, nextEnd % 60)
}

@Composable
internal fun CalendarDatePickerTrigger(
    label: String,
    valueIso: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val parsed = remember(valueIso) { calendarDateFromIso(valueIso) }
    val display = remember(parsed, valueIso) {
        if (parsed != null) {
            val rawDow = dowShortName(parsed.dayOfWeek)
            val dow = if (rawDow.length > 1) rawDow.take(1) + rawDow.drop(1).lowercase() else rawDow
            "$dow, ${monthShortName(parsed.month)} ${parsed.day}, ${parsed.year}"
        } else {
            valueIso.ifBlank { "—" }
        }
    }
    PickerTriggerBox(
        label = label,
        displayValue = display,
        icon = Lucide.Calendar,
        expanded = expanded,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
internal fun CalendarTimePickerTrigger(
    label: String,
    valueHHMM: String,
    use24Hour: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val parsed = remember(valueHHMM) { parseTimeHHMM(valueHHMM) ?: (9 to 0) }
    val display = remember(parsed, use24Hour) {
        formatReadableClock(parsed.first, parsed.second, use24Hour)
    }
    PickerTriggerBox(
        label = label,
        displayValue = display,
        icon = Lucide.Clock,
        expanded = expanded,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun PickerTriggerBox(
    label: String,
    displayValue: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (expanded) Theme[colors][accent] else Theme[colors][strokeStrong]
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = Theme[typography][caption],
            color = Theme[colors][textSecondary],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Theme[colors][surface])
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .handCursor()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(
                icon,
                contentDescription = null,
                tint = if (expanded) Theme[colors][accentText] else Theme[colors][textSecondary],
                modifier = Modifier.size(15.dp),
            )
            Text(
                displayValue,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            UnstyledIcon(
                if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun CalendarDatePickerPanel(
    selectedIso: String,
    todayDate: CalendarDate = remember { calendarToday() },
    minDateIso: String? = null,
    onSelectDate: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedDate = remember(selectedIso) { calendarDateFromIso(selectedIso) }
    val minDate = remember(minDateIso) { minDateIso?.let(::calendarDateFromIso) }
    var displayMonth by remember(selectedIso) {
        mutableStateOf(selectedDate ?: todayDate)
    }
    val weeks = remember(displayMonth.year, displayMonth.month) {
        CalendarLayoutEngine.monthGridWeeks(displayMonth.year, displayMonth.month, weekStartsSunday = true)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Theme[colors][surface2], RoundedCornerShape(10.dp))
            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                Lucide.ArrowLeft,
                contentDescription = stringResource(Res.string.cal_previous),
                onClick = {
                    val (y, m) = calendarAddMonths(displayMonth.year, displayMonth.month, -1)
                    calendarDateFromIso("${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-01")
                        ?.let { displayMonth = it }
                },
            )
            Text(
                "${monthShortName(displayMonth.month)} ${displayMonth.year}",
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                Lucide.ArrowRight,
                contentDescription = stringResource(Res.string.cal_next),
                onClick = {
                    val (y, m) = calendarAddMonths(displayMonth.year, displayMonth.month, 1)
                    calendarDateFromIso("${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-01")
                        ?.let { displayMonth = it }
                },
            )
            val todayAllowed = minDate == null || todayDate.epochDay >= minDate.epochDay
            if (todayAllowed) {
                Button(
                    text = stringResource(Res.string.cal_today),
                    onClick = {
                        displayMonth = todayDate
                        onSelectDate(todayDate.iso)
                    },
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                )
            }
            Button(
                text = stringResource(Res.string.cal_done_action),
                onClick = onDone,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            listOf(7, 1, 2, 3, 4, 5, 6).forEach { dow ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        dowSingleLetter(dow),
                        style = Theme[typography][caption].copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = Theme[colors][textTertiary],
                    )
                }
            }
        }

        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val enabled = minDate == null || day.epochDay >= minDate.epochDay
                    val isSelected = selectedDate?.epochDay == day.epochDay
                    val isToday = day.epochDay == todayDate.epochDay
                    val inMonth = day.month == displayMonth.month
                    val bg = if (isSelected) Theme[colors][accent] else Color.Transparent
                    val fg = when {
                        isSelected -> Theme[colors][onAccent]
                        !enabled -> Theme[colors][textTertiary]
                        inMonth -> Theme[colors][textColor]
                        else -> Theme[colors][textTertiary]
                    }
                    val ringMod = if (isToday && !isSelected) {
                        Modifier.border(1.dp, Theme[colors][accent], CircleShape)
                    } else {
                        Modifier
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(CircleShape)
                            .alpha(if (enabled) 1f else 0.35f)
                            .clickable(enabled = enabled, role = Role.Button) { onSelectDate(day.iso) }
                            .handCursor(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(bg, CircleShape)
                                .then(ringMod),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.day.toString(),
                                style = Theme[typography][caption].copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = fg,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalendarTimePickerPanel(
    valueHHMM: String,
    use24Hour: Boolean,
    onSelectTime: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (curHour, curMin) = parseTimeHHMM(valueHHMM) ?: (9 to 0)
    val isPm = curHour >= 12
    val curHour12 = when {
        curHour == 0 -> 12
        curHour > 12 -> curHour - 12
        else -> curHour
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Theme[colors][surface2], RoundedCornerShape(10.dp))
            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatReadableClock(curHour, curMin, use24Hour),
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][accentText],
            )
            Spacer(Modifier.weight(1f))
            if (!use24Hour) {
                Chip(
                    text = "AM",
                    selected = !isPm,
                    onClick = {
                        if (isPm) onSelectTime(formatClock24((curHour - 12).coerceIn(0, 11), curMin))
                    },
                )
                Chip(
                    text = "PM",
                    selected = isPm,
                    onClick = {
                        if (!isPm) onSelectTime(formatClock24((curHour + 12).coerceIn(12, 23), curMin))
                    },
                )
            }
            Button(
                text = stringResource(Res.string.cal_done_action),
                onClick = onDone,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            )
        }

        Text(
            stringResource(Res.string.cal_pick_hour),
            style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][textSecondary],
        )

        val hourRows = remember(use24Hour) {
            if (use24Hour) (0..23).chunked(6)
            else listOf(listOf(12, 1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 10, 11))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            hourRows.forEach { rowHours ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowHours.forEach { hVal ->
                        val selected = if (use24Hour) hVal == curHour else hVal == curHour12
                        val targetHour = if (use24Hour) {
                            hVal
                        } else {
                            when {
                                !isPm && hVal == 12 -> 0
                                !isPm -> hVal
                                isPm && hVal == 12 -> 12
                                else -> hVal + 12
                            }
                        }
                        val label = if (use24Hour) hVal.toString().padStart(2, '0') else hVal.toString()
                        TimeGridCell(
                            text = label,
                            selected = selected,
                            onClick = { onSelectTime(formatClock24(targetHour, curMin)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Text(
            stringResource(Res.string.cal_pick_minute),
            style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][textSecondary],
        )

        val minuteRows = remember { (0..55 step 5).chunked(6) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            minuteRows.forEach { rowMins ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowMins.forEach { mVal ->
                        TimeGridCell(
                            text = ":${mVal.toString().padStart(2, '0')}",
                            selected = mVal == curMin,
                            onClick = { onSelectTime(formatClock24(curHour, mVal)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeGridCell(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) Theme[colors][accent] else Theme[colors][surface]
    val fg = if (selected) Theme[colors][onAccent] else Theme[colors][textColor]
    val border = if (selected) Theme[colors][accent] else Theme[colors][stroke]
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Theme[typography][caption].copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = fg,
            maxLines = 1,
        )
    }
}
