package dev.hermeskotlin.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarLayoutEngineTest {

    @Test
    fun packsOverlappingClustersSeparatelyFromIsolatedEvents() {
        val d = calendarDateFromIso("2026-11-26")!!
        val e1 = CalendarEvent(
            id = "e1",
            calendarId = "c1",
            title = "Morning Standup",
            startAt = "2026-11-26T02:00:00Z", // 09:00 ICT
            endAt = "2026-11-26T03:00:00Z",   // 10:00 ICT
        )
        val e2 = CalendarEvent(
            id = "e2",
            calendarId = "c1",
            title = "Flight VJ386",
            startAt = "2026-11-26T10:05:00Z", // 17:05 ICT
            endAt = "2026-11-26T11:15:00Z",   // 18:15 ICT
        )
        val e3 = CalendarEvent(
            id = "e3",
            calendarId = "c2",
            title = "Flight to Quy Nhon",
            startAt = "2026-11-26T10:05:00Z", // 17:05 ICT
            endAt = "2026-11-26T11:15:00Z",   // 18:15 ICT
        )

        val items = CalendarLayoutEngine.collectItems(events = listOf(e1, e2, e3))
        val grid = CalendarLayoutEngine.buildTimeGrid(
            days = listOf(d),
            items = items,
            zoneId = "Asia/Ho_Chi_Minh",
        )
        val blocks = grid.timedByDay[d.epochDay].orEmpty()
        assertEquals(3, blocks.size)

        val standup = blocks.first { it.item.title == "Morning Standup" }
        assertEquals(1, standup.columnCount)
        assertEquals(0, standup.columnIndex)

        val flights = blocks.filter { it.item.title.startsWith("Flight") }
        assertEquals(2, flights.size)
        assertTrue(flights.all { it.columnCount == 2 })
        assertEquals(setOf(0, 1), flights.map { it.columnIndex }.toSet())
    }

    @Test
    fun placesMultiDaySpanBarsAcrossMonthRowsWithContinuationFlags() {
        val weeks = CalendarLayoutEngine.monthGridWeeks(2026, 11, weekStartsSunday = true)
        // Nov 2026 starts Sunday Nov 1; Nov 28 is Saturday (end of week 4), Nov 29 is Sunday (start of week 5)
        val spanEvent = CalendarEvent(
            id = "trip",
            calendarId = "c1",
            title = "Offsite",
            allDay = true,
            startDate = "2026-11-27", // Friday
            endDate = "2026-12-01",   // exclusive -> covers Fri 27, Sat 28, Sun 29, Mon 30
        )
        val items = CalendarLayoutEngine.collectItems(events = listOf(spanEvent))
        val rows = CalendarLayoutEngine.buildMonthRows(weeks, items, zoneId = "Asia/Ho_Chi_Minh")

        val weekWithNov27 = rows.first { row -> row.days.any { it.iso == "2026-11-27" } }
        val bar1 = weekWithNov27.spanBars.single()
        assertEquals(5, bar1.startCol) // Friday
        assertEquals(6, bar1.endCol)   // Saturday
        assertEquals(false, bar1.continuesBefore)
        assertEquals(true, bar1.continuesAfter)

        val weekWithNov29 = rows.first { row -> row.days.any { it.iso == "2026-11-29" } }
        val bar2 = weekWithNov29.spanBars.single()
        assertEquals(0, bar2.startCol) // Sunday
        assertEquals(1, bar2.endCol)   // Monday Nov 30
        assertEquals(true, bar2.continuesBefore)
        assertEquals(false, bar2.continuesAfter)
    }

    @Test
    fun hidesWeekendsWhenDisabledInWeekView() {
        val anchor = calendarDateFromIso("2026-11-26")!!
        val workWeek = CalendarLayoutEngine.visibleGridDays(anchor, dayCount = 7, showWeekends = false)
        assertEquals(5, workWeek.size)
        assertTrue(workWeek.all { it.dayOfWeek in 1..5 })
    }
}
