package dev.hermeskotlin.ui.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.components.ProvideAppWords
import dev.hermeskotlin.ui.preview.PageSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CalendarPageUiTest {

    @Test
    fun desktopFourDayGridRendersEventsProposalsAndTaskScheduleTrigger() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val sample = PageSamples.calendar(CalendarViewMode.FOUR_DAYS)
        var scheduledTaskTitle: String? = null
        var appliedId: String? = null

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        CalendarPageContent(
                            state = sample,
                            onOpenScheduleTask = { task, _ -> scheduledTaskTitle = task.title },
                            onApplyProposal = { id, _, _ -> appliedId = id },
                        )
                    }
                }
            }
        }
        waitForIdle()

        onAllNodesWithText("November 2026")[0].assertIsDisplayed()
        onNodeWithText("Work Primary").assertIsDisplayed()
        onNodeWithText("MotherBase").assertIsDisplayed()
        onNodeWithText("Company Offsite").assertIsDisplayed()
        onNodeWithText("Engineering Standup").assertExists()
        assertTrue(onAllNodesWithText("Review Gateway Schema PR").fetchSemanticsNodes().size >= 2)

        // Trigger scheduling a Dispatch task directly from the right rail
        onNodeWithText("Schedule in free slot").performClick()
        waitForIdle()
        assertEquals("Review Gateway Schema PR", scheduledTaskTitle)

        // Open Agent tab from staged proposal banner and apply proposal
        onNodeWithText("Schedule proposal (1)").performClick()
        waitForIdle()
        onNodeWithText("Afternoon Focus Block").assertExists()
        onNodeWithText("Apply (1)").performClick()
        waitForIdle()
        assertEquals("prop-1", appliedId)
    }

    @Test
    fun scheduleAgendaAndMonthViewsRenderChronologically() = runDesktopComposeUiTest(width = 1100, height = 760) {
        val scheduleSample = PageSamples.calendar(CalendarViewMode.SCHEDULE)
        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        CalendarPageContent(state = scheduleSample)
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Engineering Standup").assertExists()
        assertEquals(2, onAllNodesWithText("Company Offsite").fetchSemanticsNodes().size)
    }

    @Test
    fun monthViewAndNarrowPhoneLayoutRenderWithoutOverflow() = runDesktopComposeUiTest(width = 360, height = 760) {
        val monthSample = PageSamples.calendar(CalendarViewMode.MONTH)
        var createClicked = false
        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        CalendarPageContent(
                            state = monthSample,
                            onOpenCreateEvent = { _, _, _ -> createClicked = true },
                        )
                    }
                }
            }
        }
        waitForIdle()

        onAllNodesWithText("November 2026")[0].assertIsDisplayed()
        onNodeWithText("Today").assertIsDisplayed()
        onAllNodesWithText("26")[0].assertIsDisplayed()

        // Narrow toolbar uses `+` icon button cleanly without overflowing
        onNodeWithText("Create").assertDoesNotExist()
        onNodeWithContentDescription("Create").performClick()
        waitForIdle()
        assertTrue(createClicked)
    }

    @Test
    fun taskDetailAndEventDetailExposeOpenSessionWithoutForcedTextEdit() = runDesktopComposeUiTest(width = 960, height = 760) {
        val sample = PageSamples.calendar(CalendarViewMode.FOUR_DAYS)
        val unscheduledTask = sample.dispatchOverview.tasks.last()
        var openedTaskId: String? = null
        var scheduledFromDetail = false

        // 1. Clicking a task opens TaskDetailDialog with "Open session" instead of a name/time edit form
        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TaskDetailDialog(
                            task = unscheduledTask,
                            projects = sample.dispatchOverview.projects,
                            timezone = sample.settings.timezone,
                            use24Hour = false,
                            mutating = false,
                            openingSession = false,
                            onDismiss = {},
                            onOpenSession = { tid -> openedTaskId = tid },
                            onScheduleBlock = { _, _ -> scheduledFromDetail = true },
                            onDeleteBlock = {},
                            onToggleDone = { _, _ -> },
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Event title").assertDoesNotExist()
        onNodeWithText("Open session").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals("task-2", openedTaskId)

        onNodeWithText("Schedule in free slot").assertIsDisplayed().performClick()
        waitForIdle()
        assertTrue(scheduledFromDetail)

        // 2. Clicking a scheduled time-block event shows "Open session" alongside Change time / Unschedule
        val blockEvent = sample.events.first { it.taskId == "task-1" }
        var blockOpenedTaskId: String? = null
        var rescheduledEventTitle: String? = null
        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        EventDetailDialog(
                            event = blockEvent,
                            use24Hour = false,
                            mutating = false,
                            onDismiss = {},
                            onEdit = {},
                            onDelete = { _, _ -> },
                            onUnscheduleBlock = {},
                            onOpenTaskSession = { tid -> blockOpenedTaskId = tid },
                            onRescheduleBlock = { ev -> rescheduledEventTitle = ev.title },
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Change time").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(blockEvent.title, rescheduledEventTitle)

        onNodeWithText("Open session").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals("task-1", blockOpenedTaskId)
    }

    @Test
    fun eventEditorUsesVisualDateAndTimePickersInsteadOfTextFields() = runDesktopComposeUiTest(width = 720, height = 820) {
        val sample = PageSamples.calendar(CalendarViewMode.FOUR_DAYS)
        var savedDraft: EventEditorDraft? = null

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        EventEditorDialog(
                            draft = EventEditorDraft(
                                calendarId = "cal-primary",
                                title = "Architecture sync",
                                dateIso = "2026-11-26",
                                endDateIso = "2026-11-26",
                                startTime = "09:00",
                                endTime = "10:00",
                            ),
                            calendars = sample.calendars,
                            use24Hour = false,
                            mutating = false,
                            onDismiss = {},
                            onSave = { savedDraft = it },
                            onDelete = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Date (YYYY-MM-DD)").assertDoesNotExist()
        onNodeWithText("Start (HH:MM)").assertDoesNotExist()

        // Pick date from calendar grid
        onNodeWithText("Thu, Nov 26, 2026").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("28").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Sat, Nov 28, 2026").assertIsDisplayed()

        // Pick start time via visual hour/minute selector (2:30 PM -> shifts end to 3:30 PM)
        onNodeWithText("9:00 AM").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("PM").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("2").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(":30").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Done").performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("2:30 PM").assertIsDisplayed()
        onNodeWithText("3:30 PM").assertIsDisplayed()

        onNodeWithText("Save event").performClick()
        waitForIdle()
        assertEquals("2026-11-28", savedDraft?.dateIso)
        assertEquals("14:30", savedDraft?.startTime)
        assertEquals("15:30", savedDraft?.endTime)
    }

    @Test
    fun monthDayTapOpensSingleDayViewWhileTimeGridHeadersStayInCurrentMode() = runDesktopComposeUiTest(width = 800, height = 760) {
        var selectOnlyCalled = 0
        var openDayCalled = 0

        setContent {
            var uiState by remember { mutableStateOf(PageSamples.calendar(CalendarViewMode.MONTH)) }
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        CalendarPageContent(
                            state = uiState,
                            onSelectDate = { d ->
                                selectOnlyCalled++
                                uiState = uiState.copy(anchorDate = d)
                            },
                            onOpenDay = { d ->
                                openDayCalled++
                                uiState = uiState.copy(anchorDate = d, viewMode = CalendarViewMode.DAY)
                            },
                            onSetViewMode = { mode -> uiState = uiState.copy(viewMode = mode) },
                        )
                    }
                }
            }
        }
        waitForIdle()

        // Month mode -> clicking day 27 jumps straight to 1-Day view for Nov 27
        onNodeWithText("Month").assertIsDisplayed()
        onNodeWithText("27").performClick()
        waitForIdle()
        assertEquals(1, openDayCalled)
        assertEquals(0, selectOnlyCalled)
        onNodeWithText("Day").assertIsDisplayed()
        onNodeWithText("FRI").assertIsDisplayed()

        // Switch to 4-Day mode -> clicking day header uses selectDate and preserves 4-Day mode
        onNodeWithText("Day").performClick()
        waitForIdle()
        onNodeWithText("4 days").performClick()
        waitForIdle()
        onNodeWithText("28").performClick()
        waitForIdle()
        assertEquals(1, openDayCalled)
        assertEquals(1, selectOnlyCalled)
        onNodeWithText("4 days").assertIsDisplayed()
    }
}
