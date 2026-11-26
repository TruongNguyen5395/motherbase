package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Sparkles
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.calendar.AgendaDaySection
import dev.hermeskotlin.core.calendar.CalendarDate
import dev.hermeskotlin.core.calendar.CalendarItem
import dev.hermeskotlin.core.calendar.CalendarLayoutEngine
import dev.hermeskotlin.core.calendar.MonthWeekRow
import dev.hermeskotlin.core.calendar.PlacedSpanBar
import dev.hermeskotlin.core.calendar.PlacedTimedBlock
import dev.hermeskotlin.core.calendar.TimeGridLayout
import dev.hermeskotlin.core.calendar.formatClockCompact
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.VerticalScrollbar
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.onAccent
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.cal_all_day
import dev.hermeskotlin.ui.resources.cal_more_count
import dev.hermeskotlin.ui.resources.cal_next
import dev.hermeskotlin.ui.resources.cal_no_events
import dev.hermeskotlin.ui.resources.cal_previous
import org.jetbrains.compose.resources.stringResource

private val LIVE_RED = Color(0xFFEA4335)
private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 56.dp
private val SPAN_LANE_HEIGHT = 22.dp

@Composable
internal fun MiniMonthCalendar(
    anchorDate: CalendarDate,
    todayDate: CalendarDate,
    onSelectDate: (CalendarDate) -> Unit,
    onStepMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(anchorDate.year, anchorDate.month) {
        CalendarLayoutEngine.monthGridWeeks(anchorDate.year, anchorDate.month, weekStartsSunday = true)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${monthFullName(anchorDate.month)} ${anchorDate.year}",
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(Lucide.ArrowLeft, contentDescription = stringResource(Res.string.cal_previous), onClick = { onStepMonth(-1) })
                IconButton(Lucide.ArrowRight, contentDescription = stringResource(Res.string.cal_next), onClick = { onStepMonth(1) })
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(7, 1, 2, 3, 4, 5, 6).forEach { dow ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        dowSingleLetter(dow),
                        style = Theme[typography][caption].copy(fontSize = 10.sp),
                        color = Theme[colors][textTertiary],
                    )
                }
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val isToday = day.epochDay == todayDate.epochDay
                    val isSelected = day.epochDay == anchorDate.epochDay
                    val inMonth = day.month == anchorDate.month
                    val bg = when {
                        isToday -> Theme[colors][accent]
                        isSelected -> Theme[colors][accent].copy(alpha = 0.22f)
                        else -> Color.Transparent
                    }
                    val fg = when {
                        isToday -> Theme[colors][onAccent]
                        inMonth -> Theme[colors][textColor]
                        else -> Theme[colors][textTertiary]
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onSelectDate(day) }
                            .handCursor(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(22.dp).background(bg, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.day.toString(),
                                style = Theme[typography][caption].copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
internal fun CalendarTimeGridView(
    layout: TimeGridLayout,
    todayDate: CalendarDate,
    currentMinuteOfDay: Int,
    use24Hour: Boolean,
    timezoneLabel: String,
    onSelectDay: (CalendarDate) -> Unit,
    onEmptySlotClick: (CalendarDate, Int) -> Unit,
    onItemClick: (CalendarItem) -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val initialScrollPx = remember(density) { with(density) { (HOUR_HEIGHT * 7).roundToPx() } }
    val scrollState = rememberScrollState(initial = initialScrollPx)
    val lineColor = Theme[colors][stroke]
    val days = layout.days

    Column(modifier.fillMaxSize().background(Theme[colors][surface])) {
        // Day column headers (matches Google Calendar weekday + big date badge)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Theme[colors][surface])
                .padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.width(GUTTER_WIDTH).padding(horizontal = 4.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    timezoneLabel,
                    style = Theme[typography][caption].copy(fontSize = 10.sp),
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            days.forEach { day ->
                val isToday = day.epochDay == todayDate.epochDay
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onSelectDay(day) }
                        .handCursor()
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        dowShortName(day.dayOfWeek),
                        style = Theme[typography][caption].copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isToday) Theme[colors][accent] else Theme[colors][textTertiary],
                    )
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(if (isToday) Theme[colors][accent] else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.day.toString(),
                            style = Theme[typography][bodySmall].copy(
                                fontSize = 15.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = if (isToday) Theme[colors][onAccent] else Theme[colors][textColor],
                        )
                    }
                }
            }
        }

        // All-day & multi-day span strip (including Dispatch task deadlines)
        if (layout.allDayBars.isNotEmpty() && days.isNotEmpty()) {
            val stripHeight = (layout.allDayLaneCount.coerceAtLeast(1) * SPAN_LANE_HEIGHT.value + 6f).dp
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Spacer(Modifier.width(GUTTER_WIDTH))
                BoxWithConstraints(Modifier.weight(1f).height(stripHeight)) {
                    val colWidth = maxWidth / days.size
                    layout.allDayBars.forEach { bar ->
                        SpanBarChip(
                            bar = bar,
                            colWidth = colWidth,
                            laneHeight = SPAN_LANE_HEIGHT,
                            onItemClick = onItemClick,
                            onToggleTaskDone = onToggleTaskDone,
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor))

        // Scrollable 24-hour grid + timed blocks + live red time indicator
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val totalGridHeight = HOUR_HEIGHT * 24
            val hourPx = with(density) { HOUR_HEIGHT.toPx() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .height(totalGridHeight),
            ) {
                // Time gutter
                Column(Modifier.width(GUTTER_WIDTH).fillMaxHeight()) {
                    for (hour in 0 until 24) {
                        Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT), contentAlignment = Alignment.TopEnd) {
                            if (hour > 0) {
                                Text(
                                    formatGutterHour(hour, use24Hour),
                                    style = Theme[typography][caption].copy(fontSize = 10.sp),
                                    color = Theme[colors][textTertiary],
                                    modifier = Modifier.padding(end = 6.dp).offset(y = (-6).dp),
                                )
                            }
                        }
                    }
                }

                // Day columns
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            for (hour in 1 until 24) {
                                val y = hour * hourPx
                                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            }
                        },
                ) {
                    days.forEach { day ->
                        val blocks = layout.timedByDay[day.epochDay].orEmpty()
                        val isToday = day.epochDay == todayDate.epochDay
                        BoxWithConstraints(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .drawBehind {
                                    drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
                                }
                                .pointerInput(day.epochDay) {
                                    detectTapGestures { offset ->
                                        val minsRaw = ((offset.y / hourPx) * 60f).toInt().coerceIn(0, 23 * 60 + 30)
                                        val snapped = (minsRaw / 30) * 30
                                        onEmptySlotClick(day, snapped)
                                    }
                                },
                        ) {
                            val colW = maxWidth
                            blocks.forEach { placed ->
                                TimedBlockCard(
                                    placed = placed,
                                    colWidth = colW,
                                    hourHeight = HOUR_HEIGHT,
                                    use24Hour = use24Hour,
                                    onClick = { onItemClick(placed.item) },
                                )
                            }
                            if (isToday) {
                                val nowY = (currentMinuteOfDay / 60f) * HOUR_HEIGHT.value
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .offset(y = (nowY - 4f).dp)
                                        .height(8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Box(Modifier.fillMaxWidth().height(2.dp).background(LIVE_RED))
                                    Box(Modifier.offset(x = (-4).dp).size(8.dp).background(LIVE_RED, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun SpanBarChip(
    bar: PlacedSpanBar,
    colWidth: Dp,
    laneHeight: Dp,
    onItemClick: (CalendarItem) -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
) {
    val spanCols = (bar.endCol - bar.startCol + 1).coerceAtLeast(1)
    val x = colWidth * bar.startCol
    val y = laneHeight * bar.lane
    val w = (colWidth * spanCols) - 2.dp
    val item = bar.item
    val baseColor = parseCalendarHexColor(item.colorHex, Theme[colors][accent])
    val shape = RoundedCornerShape(
        topStart = if (bar.continuesBefore) 0.dp else 5.dp,
        bottomStart = if (bar.continuesBefore) 0.dp else 5.dp,
        topEnd = if (bar.continuesAfter) 0.dp else 5.dp,
        bottomEnd = if (bar.continuesAfter) 0.dp else 5.dp,
    )
    val bg = when {
        item.isGhost -> baseColor.copy(alpha = 0.16f)
        item.isTaskDeadline && item.isCompleted -> Theme[colors][surface2]
        else -> baseColor
    }
    val fg = when {
        item.isGhost -> baseColor
        item.isTaskDeadline && item.isCompleted -> Theme[colors][textTertiary]
        else -> contrastOnFill(baseColor)
    }

    Row(
        Modifier
            .offset(x = x + 1.dp, y = y)
            .width(w.coerceAtLeast(16.dp))
            .height(laneHeight - 2.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (item.isGhost) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = baseColor,
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)),
                        )
                    }
                } else Modifier,
            )
            .clickable(role = Role.Button) { onItemClick(item) }
            .handCursor()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item is CalendarItem.TaskDeadlineItem) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Checkbox) { onToggleTaskDone(item.task.id, !item.isCompleted) },
                contentAlignment = Alignment.Center,
            ) {
                UnstyledIcon(
                    if (item.isCompleted) Lucide.CircleCheck else Lucide.Circle,
                    contentDescription = item.title,
                    tint = fg,
                    modifier = Modifier.size(12.dp),
                )
            }
        } else if (item.isGhost) {
            UnstyledIcon(Lucide.Sparkles, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        }
        Text(
            item.title,
            style = Theme[typography][caption].copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimedBlockCard(
    placed: PlacedTimedBlock,
    colWidth: Dp,
    hourHeight: Dp,
    use24Hour: Boolean,
    onClick: () -> Unit,
) {
    val item = placed.item
    val colCount = placed.columnCount.coerceAtLeast(1)
    val subW = colWidth / colCount
    val x = subW * placed.columnIndex
    val top = (placed.startMinute / 60f) * hourHeight.value
    val durationMins = (placed.endMinute - placed.startMinute).coerceAtLeast(24)
    val blockH = ((durationMins / 60f) * hourHeight.value).dp

    val baseColor = parseCalendarHexColor(item.colorHex, Theme[colors][accent])
    val isDeclined = item is CalendarItem.EventItem && item.event.responseStatus == "declined"
    val bg = when {
        item.isGhost -> baseColor.copy(alpha = 0.16f)
        isDeclined -> baseColor.copy(alpha = 0.12f)
        else -> baseColor
    }
    val fg = when {
        item.isGhost -> baseColor
        isDeclined -> Theme[colors][textSecondary]
        else -> contrastOnFill(baseColor)
    }
    val shape = RoundedCornerShape(6.dp)
    val timeLabel = "${formatClockCompact(placed.startMinute, use24Hour)} – ${formatClockCompact(placed.endMinute, use24Hour)}"
    val location = (item as? CalendarItem.EventItem)?.event?.location

    Column(
        Modifier
            .offset(x = x + 1.dp, y = top.dp + 0.5.dp)
            .width((subW - 3.dp).coerceAtLeast(18.dp))
            .height((blockH - 1.dp).coerceAtLeast(20.dp))
            .alpha(if (isDeclined) 0.7f else 1f)
            .clip(shape)
            .background(bg)
            .then(
                if (item.isGhost) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = baseColor,
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)),
                        )
                    }
                } else {
                    Modifier.border(0.5.dp, Color.White.copy(alpha = 0.25f), shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            item.title,
            style = Theme[typography][caption].copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (item.isCompleted || isDeclined) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = fg,
            maxLines = if (blockH > 46.dp) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (blockH >= 36.dp) {
            Text(
                timeLabel,
                style = Theme[typography][caption].copy(fontSize = 10.5.sp),
                color = fg.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (blockH >= 60.dp && !location.isNullOrBlank()) {
            Text(
                location,
                style = Theme[typography][caption].copy(fontSize = 10.sp),
                color = fg.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CalendarScheduleAgendaView(
    sections: List<AgendaDaySection>,
    todayDate: CalendarDate,
    currentMinuteOfDay: Int,
    use24Hour: Boolean,
    onItemClick: (CalendarItem) -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    if (sections.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.cal_no_events),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textTertiary],
            )
        }
        return
    }
    Box(modifier.fillMaxSize().background(Theme[colors][surface])) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(vertical = 4.dp),
        ) {
            sections.forEachIndexed { idx, section ->
                if (idx > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))
                }
                val isToday = section.date.epochDay == todayDate.epochDay
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Date column (`26 NOV, THU`)
                    Row(
                        Modifier.width(108.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(if (isToday) Theme[colors][accent] else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                section.date.day.toString(),
                                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                                color = if (isToday) Theme[colors][onAccent] else Theme[colors][textColor],
                            )
                        }
                        Text(
                            formatScheduleDateSublabel(section.date),
                            style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
                            color = if (isToday) Theme[colors][accent] else Theme[colors][textTertiary],
                        )
                    }

                    // Events & tasks column
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        section.allDayItems.forEach { item ->
                            AgendaRow(
                                item = item,
                                timeText = stringResource(Res.string.cal_all_day),
                                dimmed = false,
                                onClick = { onItemClick(item) },
                                onToggleTaskDone = onToggleTaskDone,
                            )
                        }
                        var redLineDrawn = false
                        section.timedItems.forEach { timed ->
                            if (isToday && !redLineDrawn && timed.startMinute >= currentMinuteOfDay) {
                                redLineDrawn = true
                                LiveTimeAgendaDivider()
                            }
                            val past = isToday && timed.endMinute < currentMinuteOfDay
                            val timeLabel = "${formatClockCompact(timed.startMinute, use24Hour)} – ${formatClockCompact(timed.endMinute, use24Hour)}"
                            AgendaRow(
                                item = timed.item,
                                timeText = timeLabel,
                                dimmed = past,
                                onClick = { onItemClick(timed.item) },
                                onToggleTaskDone = onToggleTaskDone,
                            )
                        }
                        if (isToday && !redLineDrawn) {
                            LiveTimeAgendaDivider()
                        }
                    }
                }
            }
        }
        VerticalScrollbar(scroll, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 2.dp))
    }
}

@Composable
private fun LiveTimeAgendaDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(LIVE_RED, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(LIVE_RED))
    }
}

@Composable
private fun AgendaRow(
    item: CalendarItem,
    timeText: String,
    dimmed: Boolean,
    onClick: () -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
) {
    val baseColor = parseCalendarHexColor(item.colorHex, Theme[colors][accent])
    val location = (item as? CalendarItem.EventItem)?.event?.location
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.48f else 1f)
            .clip(shape)
            .then(
                if (item.isGhost) {
                    Modifier.background(baseColor.copy(alpha = 0.08f)).drawBehind {
                        drawRoundRect(
                            color = baseColor,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)),
                        )
                    }
                } else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item is CalendarItem.TaskDeadlineItem) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Checkbox) { onToggleTaskDone(item.task.id, !item.isCompleted) },
                contentAlignment = Alignment.Center,
            ) {
                UnstyledIcon(
                    if (item.isCompleted) Lucide.CircleCheck else Lucide.Circle,
                    contentDescription = item.title,
                    tint = baseColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(Modifier.size(10.dp).background(baseColor, CircleShape))
        }

        Text(
            timeText,
            style = Theme[typography][caption],
            color = Theme[colors][textSecondary],
            maxLines = 1,
            modifier = Modifier.width(104.dp),
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = Theme[typography][bodySmall].copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = Theme[colors][textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!location.isNullOrBlank()) {
                    Text(
                        location,
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CalendarMonthGridView(
    monthRows: List<MonthWeekRow>,
    anchorDate: CalendarDate,
    todayDate: CalendarDate,
    use24Hour: Boolean,
    onSelectDate: (CalendarDate) -> Unit,
    onItemClick: (CalendarItem) -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = Theme[colors][stroke]
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize().background(Theme[colors][surface])) {
        val compact = maxWidth < 600.dp
        Column(Modifier.fillMaxSize()) {
            // Day-of-week header row
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                listOf(7, 1, 2, 3, 4, 5, 6).forEach { dow ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            dowShortName(dow),
                            style = Theme[typography][caption].copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = Theme[colors][textTertiary],
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    monthRows.forEach { week ->
                        val maxLane = (week.spanBars.maxOfOrNull { it.lane } ?: -1) + 1
                        val cellHeight = if (compact) 68.dp else 118.dp
                        BoxWithConstraints(
                            Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .drawBehind {
                                    drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
                                },
                        ) {
                            val colW = maxWidth / 7
                            Row(Modifier.fillMaxSize()) {
                                week.days.forEachIndexed { colIdx, day ->
                                    val isToday = day.epochDay == todayDate.epochDay
                                    val isSelected = day.epochDay == anchorDate.epochDay
                                    val inMonth = day.month == anchorDate.month
                                    val timedList = week.timedByCol[colIdx].orEmpty()
                                    val overflow = week.overflowCounts[colIdx] ?: 0
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .drawBehind {
                                                if (colIdx > 0) drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
                                            }
                                            .background(if (isSelected && !isToday) Theme[colors][accent].copy(alpha = 0.08f) else Color.Transparent)
                                            .clickable(role = Role.Button) { onSelectDate(day) }
                                            .handCursor()
                                            .padding(top = 3.dp, start = 4.dp, end = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        val label = if (day.day == 1) "${monthShortName(day.month)} 1" else day.day.toString()
                                        Box(
                                            Modifier
                                                .size(width = if (day.day == 1) 42.dp else 22.dp, height = 22.dp)
                                                .background(if (isToday) Theme[colors][accent] else Color.Transparent, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                label,
                                                style = Theme[typography][caption].copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                                ),
                                                color = when {
                                                    isToday -> Theme[colors][onAccent]
                                                    inMonth -> Theme[colors][textColor]
                                                    else -> Theme[colors][textTertiary]
                                                },
                                            )
                                        }
                                        Spacer(Modifier.height((maxLane * 19).coerceAtMost(38).dp))
                                        if (compact) {
                                            val dotCount = timedList.size + week.spanBars.count { colIdx in it.startCol..it.endCol }
                                            if (dotCount > 0) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                                                    timedList.take(3).forEach { t ->
                                                        Box(
                                                            Modifier
                                                                .size(5.dp)
                                                                .background(parseCalendarHexColor(t.item.colorHex, Theme[colors][accent]), CircleShape),
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            timedList.forEach { timed ->
                                                val dotColor = parseCalendarHexColor(timed.item.colorHex, Theme[colors][accent])
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable(role = Role.Button) { onItemClick(timed.item) }
                                                        .padding(horizontal = 2.dp, vertical = 1.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Box(Modifier.size(6.dp).background(dotColor, CircleShape))
                                                    Text(
                                                        "${formatClockCompact(timed.startMinute, use24Hour)} ${timed.item.title}",
                                                        style = Theme[typography][caption].copy(fontSize = 10.5.sp),
                                                        color = Theme[colors][textColor],
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            if (overflow > 0) {
                                                Text(
                                                    stringResource(Res.string.cal_more_count, overflow),
                                                    style = Theme[typography][caption].copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                                    color = Theme[colors][textSecondary],
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (!compact && week.spanBars.isNotEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(top = 26.dp)) {
                                    week.spanBars.filter { it.lane < 2 }.forEach { bar ->
                                        SpanBarChip(
                                            bar = bar,
                                            colWidth = colW,
                                            laneHeight = 19.dp,
                                            onItemClick = onItemClick,
                                            onToggleTaskDone = onToggleTaskDone,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // On narrow phones (< 600dp), show the selected day's items neatly below the month grid.
                    if (compact) {
                        val selectedItems = remember(monthRows, anchorDate) {
                            val items = mutableListOf<Pair<CalendarItem, String?>>()
                            monthRows.forEach { week ->
                                val idx = week.days.indexOfFirst { it.epochDay == anchorDate.epochDay }
                                if (idx >= 0) {
                                    week.spanBars.filter { idx in it.startCol..it.endCol }.forEach {
                                        items += it.item to null
                                    }
                                    week.timedByCol[idx]?.forEach { t ->
                                        items += t.item to formatClockCompact(t.startMinute, use24Hour)
                                    }
                                }
                            }
                            items
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "${anchorDate.iso} · ${dowShortName(anchorDate.dayOfWeek)}",
                                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                                color = Theme[colors][textColor],
                            )
                            if (selectedItems.isEmpty()) {
                                Text(stringResource(Res.string.cal_no_events), style = Theme[typography][caption], color = Theme[colors][textTertiary])
                            } else {
                                selectedItems.forEach { (item, timeLabel) ->
                                    AgendaRow(
                                        item = item,
                                        timeText = timeLabel ?: stringResource(Res.string.cal_all_day),
                                        dimmed = false,
                                        onClick = { onItemClick(item) },
                                        onToggleTaskDone = onToggleTaskDone,
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(scroll, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 2.dp))
            }
        }
    }
}

private fun formatGutterHour(hour: Int, use24Hour: Boolean): String {
    if (use24Hour) return "${hour.toString().padStart(2, '0')}:00"
    return when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }
}
