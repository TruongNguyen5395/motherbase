package dev.hermeskotlin.core.calendar

/** Unified item rendered on calendar views (synced events, Dispatch task deadlines, or staged Ghost Blocks). */
sealed interface CalendarItem {
    val itemId: String
    val title: String
    val colorHex: String
    val allDay: Boolean
    val isGhost: Boolean
    val isCompleted: Boolean
    val isTaskDeadline: Boolean

    data class EventItem(
        val event: CalendarEvent,
    ) : CalendarItem {
        override val itemId: String get() = "ev:${event.id}"
        override val title: String get() = event.title
        override val colorHex: String get() = event.colorHex
        override val allDay: Boolean get() = event.allDay
        override val isGhost: Boolean get() = false
        override val isCompleted: Boolean get() = event.title.startsWith("[Done]")
        override val isTaskDeadline: Boolean get() = false
    }

    data class TaskDeadlineItem(
        val task: DispatchCalendarTask,
        val dueDateIso: String,
        override val colorHex: String = "#0284C7",
    ) : CalendarItem {
        override val itemId: String get() = "due:${task.id}"
        override val title: String get() = task.title
        override val allDay: Boolean get() = true
        override val isGhost: Boolean get() = false
        override val isCompleted: Boolean get() = task.status == "done"
        override val isTaskDeadline: Boolean get() = true
    }

    data class GhostOperationItem(
        val proposalId: String,
        val operation: ScheduleOperation,
    ) : CalendarItem {
        override val itemId: String get() = "ghost:${proposalId}:${operation.opId}"
        override val title: String get() = operation.title
        override val colorHex: String get() = operation.colorHex
        override val allDay: Boolean get() = operation.allDay
        override val isGhost: Boolean get() = true
        override val isCompleted: Boolean get() = false
        override val isTaskDeadline: Boolean get() = false
    }
}

/** Positioned timed slice inside a single day column, with connected-cluster column packing. */
data class PlacedTimedBlock(
    val item: CalendarItem,
    val dayEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val columnIndex: Int,
    val columnCount: Int,
)

/** Multi-day or all-day horizontal bar spanning [startCol..endCol] within a calendar row (All-day strip or Month week row). */
data class PlacedSpanBar(
    val item: CalendarItem,
    val startCol: Int,
    val endCol: Int,
    val lane: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/** One day entry in Schedule (Agenda) view. */
data class AgendaDaySection(
    val date: CalendarDate,
    val allDayItems: List<CalendarItem>,
    val timedItems: List<PlacedTimedItem>,
)

data class PlacedTimedItem(
    val item: CalendarItem,
    val startMinute: Int,
    val endMinute: Int,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

/** Layout result for a multi-day time grid (`Day`, `3-Day`, `4-Day`, `Week`). */
data class TimeGridLayout(
    val days: List<CalendarDate>,
    val allDayBars: List<PlacedSpanBar>,
    val allDayLaneCount: Int,
    val timedByDay: Map<Long, List<PlacedTimedBlock>>,
)

/** One 7-day week row in Month grid view. */
data class MonthWeekRow(
    val days: List<CalendarDate>,
    val spanBars: List<PlacedSpanBar>,
    val timedByCol: Map<Int, List<PlacedTimedItem>>,
    val overflowCounts: Map<Int, Int>,
)

/**
 * Pure calendar layout algorithms:
 * - Overlap column packing (connected collision clusters -> `columnIndex` / `columnCount`)
 * - Multi-day horizontal span lane packing (for All-day header strip and Month 7-column weeks)
 * - Schedule agenda grouping
 */
object CalendarLayoutEngine {

    /**
     * Generates the visible list of dates for a `Day` (1), `3-Day` (3), `4-Day` (4), or `Week` (7 or 5) grid.
     * For Week view, Sunday-first matches the Google Calendar reference screenshots; when [showWeekends] is false,
     * Saturday and Sunday are excluded.
     */
    fun visibleGridDays(
        anchor: CalendarDate,
        dayCount: Int,
        weekStartsSunday: Boolean = true,
        showWeekends: Boolean = true,
    ): List<CalendarDate> {
        if (dayCount == 7) {
            val offset = if (weekStartsSunday) (anchor.dayOfWeek % 7) else (anchor.dayOfWeek - 1)
            val weekStart = anchor.plusDays(-offset)
            val all = (0 until 7).map { weekStart.plusDays(it) }
            return if (showWeekends) all else all.filter { it.dayOfWeek in 1..5 }
        }
        return (0 until dayCount.coerceAtLeast(1)).map { anchor.plusDays(it) }
    }

    /** Generates the 5 or 6 week rows (each 7 [CalendarDate]s) covering [year]/[month], starting Sunday. */
    fun monthGridWeeks(year: Int, month: Int, weekStartsSunday: Boolean = true): List<List<CalendarDate>> {
        val firstOfMonth = calendarDateFromIso(
            "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01",
        ) ?: return emptyList()
        val daysInMonth = calendarDaysInMonth(year, month)
        val leadingOffset = if (weekStartsSunday) (firstOfMonth.dayOfWeek % 7) else (firstOfMonth.dayOfWeek - 1)
        val gridStart = firstOfMonth.plusDays(-leadingOffset)
        val totalCells = leadingOffset + daysInMonth
        val weekCount = ((totalCells + 6) / 7).coerceIn(5, 6)
        return (0 until weekCount).map { w ->
            (0 until 7).map { d -> gridStart.plusDays(w * 7 + d) }
        }
    }

    /** Combines external/synced events, Dispatch task deadlines, and staged Ghost operations into [CalendarItem]s. */
    fun collectItems(
        events: List<CalendarEvent>,
        tasks: List<DispatchCalendarTask> = emptyList(),
        stagedProposals: List<ScheduleProposal> = emptyList(),
        showDeclined: Boolean = false,
        showCompletedTasks: Boolean = true,
        projectColors: Map<String, String> = emptyMap(),
    ): List<CalendarItem> {
        val out = ArrayList<CalendarItem>(events.size + tasks.size + 8)
        for (ev in events) {
            if (ev.status == "cancelled") continue
            if (!showDeclined && ev.responseStatus == "declined") continue
            if (!showCompletedTasks && ev.isDispatchBlock && ev.title.startsWith("[Done]")) continue
            out += CalendarItem.EventItem(ev)
        }
        for (task in tasks) {
            val due = task.dueDate?.trim()?.take(10)?.takeIf { it.length == 10 } ?: continue
            if (!showCompletedTasks && task.status == "done") continue
            val color = task.projectId?.let { projectColors[it] } ?: "#0284C7"
            out += CalendarItem.TaskDeadlineItem(task = task, dueDateIso = due, colorHex = color)
        }
        for (prop in stagedProposals) {
            if (prop.status != ScheduleProposal.STATUS_STAGED) continue
            for (op in prop.operations) {
                if (!op.selected || op.kind == "unschedule_block") continue
                out += CalendarItem.GhostOperationItem(proposalId = prop.id, operation = op)
            }
        }
        return out
    }

    /** Lays out a Day / 3-Day / 4-Day / Week time grid with All-day span bars and packed timed blocks. */
    fun buildTimeGrid(
        days: List<CalendarDate>,
        items: List<CalendarItem>,
        zoneId: String?,
    ): TimeGridLayout {
        if (days.isEmpty()) return TimeGridLayout(days, emptyList(), 0, emptyMap())
        val firstDay = days.first().epochDay
        val lastDay = days.last().epochDay
        val dayIndexByEpoch = days.withIndex().associate { (idx, d) -> d.epochDay to idx }

        val allDayEntries = mutableListOf<Triple<CalendarItem, Long, Long>>()
        val timedByDayRaw = HashMap<Long, MutableList<RawSlice>>()

        for (item in items) {
            if (item.allDay) {
                val (sEpoch, eEpochExclusive) = resolveAllDayEpochBounds(item) ?: continue
                if (eEpochExclusive <= firstDay || sEpoch > lastDay) continue
                allDayEntries += Triple(item, sEpoch, eEpochExclusive - 1L)
                continue
            }
            val (sMoment, eMoment) = resolveTimedMoments(item, zoneId) ?: continue
            if (eMoment.epochMs <= sMoment.epochMs) continue
            var curDay = sMoment.date.epochDay
            val endDayInclusive = if (eMoment.minuteOfDay == 0 && eMoment.date.epochDay > sMoment.date.epochDay) {
                eMoment.date.epochDay - 1L
            } else {
                eMoment.date.epochDay
            }
            while (curDay <= endDayInclusive) {
                if (curDay in dayIndexByEpoch) {
                    val startMin = if (curDay == sMoment.date.epochDay) sMoment.minuteOfDay else 0
                    val endMin = when {
                        curDay < endDayInclusive -> 1440
                        eMoment.date.epochDay > curDay -> 1440
                        else -> eMoment.minuteOfDay.coerceAtLeast(startMin + 15).coerceAtMost(1440)
                    }
                    if (endMin > startMin) {
                        timedByDayRaw.getOrPut(curDay) { mutableListOf() } += RawSlice(item, startMin, endMin)
                    }
                }
                curDay++
            }
        }

        val spanBars = packSpanBars(allDayEntries, days)
        val laneCount = (spanBars.maxOfOrNull { it.lane } ?: -1) + 1
        val packedByDay = days.associate { day ->
            day.epochDay to packDayColumns(day.epochDay, timedByDayRaw[day.epochDay].orEmpty())
        }
        return TimeGridLayout(
            days = days,
            allDayBars = spanBars,
            allDayLaneCount = laneCount,
            timedByDay = packedByDay,
        )
    }

    /** Lays out a Month view by week rows, placing multi-day bars first and counting per-cell overflow. */
    fun buildMonthRows(
        weeks: List<List<CalendarDate>>,
        items: List<CalendarItem>,
        zoneId: String?,
        maxVisiblePerDay: Int = 3,
    ): List<MonthWeekRow> {
        val allDayEntries = mutableListOf<Triple<CalendarItem, Long, Long>>()
        val timedByDay = HashMap<Long, MutableList<PlacedTimedItem>>()

        for (item in items) {
            if (item.allDay) {
                val (sEpoch, eEpochExcl) = resolveAllDayEpochBounds(item) ?: continue
                allDayEntries += Triple(item, sEpoch, eEpochExcl - 1L)
            } else {
                val (sMoment, eMoment) = resolveTimedMoments(item, zoneId) ?: continue
                timedByDay.getOrPut(sMoment.date.epochDay) { mutableListOf() } += PlacedTimedItem(
                    item = item,
                    startMinute = sMoment.minuteOfDay,
                    endMinute = eMoment.minuteOfDay,
                    startEpochMs = sMoment.epochMs,
                    endEpochMs = eMoment.epochMs,
                )
            }
        }

        return weeks.map { weekDays ->
            val first = weekDays.first().epochDay
            val last = weekDays.last().epochDay
            val weekAllDay = allDayEntries.filter { (_, s, e) -> e >= first && s <= last }
            val allBars = packSpanBars(weekAllDay, weekDays)

            val visibleBars = mutableListOf<PlacedSpanBar>()
            val overflow = HashMap<Int, Int>()
            val occupiedByBars = HashMap<Int, Int>()

            for (bar in allBars) {
                if (bar.lane < maxVisiblePerDay) {
                    visibleBars += bar
                    for (c in bar.startCol..bar.endCol) {
                        occupiedByBars[c] = (occupiedByBars[c] ?: 0) + 1
                    }
                } else {
                    for (c in bar.startCol..bar.endCol) {
                        overflow[c] = (overflow[c] ?: 0) + 1
                    }
                }
            }

            val timedCols = HashMap<Int, List<PlacedTimedItem>>()
            weekDays.forEachIndexed { col, day ->
                val dayTimed = timedByDay[day.epochDay].orEmpty().sortedBy { it.startMinute }
                val usedLanes = occupiedByBars[col] ?: 0
                val remaining = (maxVisiblePerDay - usedLanes).coerceAtLeast(0)
                if (dayTimed.size > remaining) {
                    timedCols[col] = dayTimed.take(remaining)
                    overflow[col] = (overflow[col] ?: 0) + (dayTimed.size - remaining)
                } else if (dayTimed.isNotEmpty()) {
                    timedCols[col] = dayTimed
                }
            }

            MonthWeekRow(
                days = weekDays,
                spanBars = visibleBars,
                timedByCol = timedCols,
                overflowCounts = overflow,
            )
        }
    }

    /** Builds chronological agenda sections for [Schedule] view across [startDate..endDate]. */
    fun buildAgendaSections(
        startDate: CalendarDate,
        endDate: CalendarDate,
        items: List<CalendarItem>,
        zoneId: String?,
        includeEmptyDays: Boolean = false,
    ): List<AgendaDaySection> {
        val allDayByEpoch = HashMap<Long, MutableList<CalendarItem>>()
        val timedByEpoch = HashMap<Long, MutableList<PlacedTimedItem>>()

        for (item in items) {
            if (item.allDay) {
                val (sEpoch, eEpochExcl) = resolveAllDayEpochBounds(item) ?: continue
                var d = maxOf(sEpoch, startDate.epochDay)
                val endIncl = minOf(eEpochExcl - 1L, endDate.epochDay)
                while (d <= endIncl) {
                    allDayByEpoch.getOrPut(d) { mutableListOf() } += item
                    d++
                }
            } else {
                val (sMoment, eMoment) = resolveTimedMoments(item, zoneId) ?: continue
                val d = sMoment.date.epochDay
                if (d in startDate.epochDay..endDate.epochDay) {
                    timedByEpoch.getOrPut(d) { mutableListOf() } += PlacedTimedItem(
                        item = item,
                        startMinute = sMoment.minuteOfDay,
                        endMinute = eMoment.minuteOfDay,
                        startEpochMs = sMoment.epochMs,
                        endEpochMs = eMoment.epochMs,
                    )
                }
            }
        }

        val sections = mutableListOf<AgendaDaySection>()
        var cur = startDate
        while (cur.epochDay <= endDate.epochDay) {
            val allDay = allDayByEpoch[cur.epochDay].orEmpty()
            val timed = timedByEpoch[cur.epochDay].orEmpty().sortedBy { it.startEpochMs }
            if (includeEmptyDays || allDay.isNotEmpty() || timed.isNotEmpty()) {
                sections += AgendaDaySection(date = cur, allDayItems = allDay, timedItems = timed)
            }
            cur = cur.plusDays(1)
        }
        return sections
    }

    /**
     * Connected-cluster column packing: overlapping events form a cluster sharing the same `columnCount`,
     * while independent non-overlapping events earlier/later in the day still take full width (`columnCount = 1`).
     */
    fun packDayColumns(dayEpochDay: Long, slices: List<RawSlice>): List<PlacedTimedBlock> {
        if (slices.isEmpty()) return emptyList()
        val sorted = slices.sortedWith(compareBy<RawSlice>({ it.startMinute }, { -(it.endMinute - it.startMinute) }))

        val clusters = mutableListOf<MutableList<RawSlice>>()
        var currentCluster = mutableListOf<RawSlice>()
        var clusterEnd = -1
        for (slice in sorted) {
            if (currentCluster.isEmpty() || slice.startMinute < clusterEnd) {
                currentCluster += slice
                clusterEnd = maxOf(clusterEnd, slice.endMinute)
            } else {
                clusters += currentCluster
                currentCluster = mutableListOf(slice)
                clusterEnd = slice.endMinute
            }
        }
        if (currentCluster.isNotEmpty()) clusters += currentCluster

        val placed = ArrayList<PlacedTimedBlock>(sorted.size)
        for (cluster in clusters) {
            val colEndMinutes = mutableListOf<Int>()
            val assigned = mutableListOf<Pair<RawSlice, Int>>()
            for (slice in cluster) {
                val freeCol = colEndMinutes.indexOfFirst { it <= slice.startMinute }
                val col = if (freeCol >= 0) {
                    colEndMinutes[freeCol] = slice.endMinute
                    freeCol
                } else {
                    colEndMinutes += slice.endMinute
                    colEndMinutes.lastIndex
                }
                assigned += slice to col
            }
            val totalCols = colEndMinutes.size.coerceAtLeast(1)
            for ((slice, col) in assigned) {
                placed += PlacedTimedBlock(
                    item = slice.item,
                    dayEpochDay = dayEpochDay,
                    startMinute = slice.startMinute,
                    endMinute = slice.endMinute,
                    columnIndex = col,
                    columnCount = totalCols,
                )
            }
        }
        return placed
    }

    private fun packSpanBars(
        entries: List<Triple<CalendarItem, Long, Long>>,
        rowDays: List<CalendarDate>,
    ): List<PlacedSpanBar> {
        if (entries.isEmpty() || rowDays.isEmpty()) return emptyList()
        val rowStart = rowDays.first().epochDay
        val rowEnd = rowDays.last().epochDay
        val colByEpoch = rowDays.withIndex().associate { (i, d) -> d.epochDay to i }

        data class Segment(
            val item: CalendarItem,
            val startCol: Int,
            val endCol: Int,
            val contBefore: Boolean,
            val contAfter: Boolean,
        )

        val segments = entries.mapNotNull { (item, startEpoch, endEpochIncl) ->
            if (endEpochIncl < rowStart || startEpoch > rowEnd) return@mapNotNull null
            val clampedStart = maxOf(startEpoch, rowStart)
            val clampedEnd = minOf(endEpochIncl, rowEnd)
            val sCol = colByEpoch[clampedStart] ?: return@mapNotNull null
            val eCol = colByEpoch[clampedEnd] ?: return@mapNotNull null
            Segment(
                item = item,
                startCol = sCol,
                endCol = eCol,
                contBefore = startEpoch < rowStart,
                contAfter = endEpochIncl > rowEnd,
            )
        }.sortedWith(
            compareBy<Segment>(
                { it.startCol },
                { -(it.endCol - it.startCol) },
                { if (it.item.isTaskDeadline) 1 else 0 },
                { it.item.title },
            ),
        )

        val lanes = mutableListOf<MutableList<IntRange>>()
        val out = ArrayList<PlacedSpanBar>(segments.size)
        for (seg in segments) {
            val range = seg.startCol..seg.endCol
            var pickedLane = -1
            for ((idx, laneRanges) in lanes.withIndex()) {
                if (laneRanges.none { r -> r.first <= range.last && range.first <= r.last }) {
                    pickedLane = idx
                    laneRanges += range
                    break
                }
            }
            if (pickedLane < 0) {
                pickedLane = lanes.size
                lanes += mutableListOf(range)
            }
            out += PlacedSpanBar(
                item = seg.item,
                startCol = seg.startCol,
                endCol = seg.endCol,
                lane = pickedLane,
                continuesBefore = seg.contBefore,
                continuesAfter = seg.contAfter,
            )
        }
        return out
    }

    private fun resolveAllDayEpochBounds(item: CalendarItem): Pair<Long, Long>? = when (item) {
        is CalendarItem.EventItem -> {
            val s = item.event.startDate?.let(::calendarDateFromIso)?.epochDay ?: return null
            val e = item.event.endDate?.let(::calendarDateFromIso)?.epochDay ?: (s + 1L)
            s to maxOf(e, s + 1L)
        }
        is CalendarItem.TaskDeadlineItem -> {
            val s = calendarDateFromIso(item.dueDateIso)?.epochDay ?: return null
            s to (s + 1L)
        }
        is CalendarItem.GhostOperationItem -> {
            val s = item.operation.startDate?.let(::calendarDateFromIso)?.epochDay ?: return null
            val e = item.operation.endDate?.let(::calendarDateFromIso)?.epochDay ?: (s + 1L)
            s to maxOf(e, s + 1L)
        }
    }

    private fun resolveTimedMoments(item: CalendarItem, zoneId: String?): Pair<CalendarZonedMoment, CalendarZonedMoment>? = when (item) {
        is CalendarItem.EventItem -> {
            val ev = item.event
            val s = ev.startEpochMs?.let { calendarZonedFromEpochMs(it, zoneId) }
                ?: ev.startAt?.let { calendarZonedFromIso(it, zoneId) }
                ?: return null
            val e = ev.endEpochMs?.let { calendarZonedFromEpochMs(it, zoneId) }
                ?: ev.endAt?.let { calendarZonedFromIso(it, zoneId) }
                ?: calendarZonedFromEpochMs(s.epochMs + 30 * 60_000L, zoneId)
            s to e
        }
        is CalendarItem.GhostOperationItem -> {
            val s = item.operation.startAt?.let { calendarZonedFromIso(it, zoneId) } ?: return null
            val e = item.operation.endAt?.let { calendarZonedFromIso(it, zoneId) }
                ?: calendarZonedFromEpochMs(s.epochMs + 30 * 60_000L, zoneId)
            s to e
        }
        is CalendarItem.TaskDeadlineItem -> null
    }

    data class RawSlice(
        val item: CalendarItem,
        val startMinute: Int,
        val endMinute: Int,
    )
}
