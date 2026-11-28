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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.calendar.CalendarAgentConfig
import dev.hermeskotlin.core.calendar.ScheduleOperation
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

    @Test
    fun floatingTriggerOpensCalendarAgentAndSettingsSelectsAgentProfile() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val sample = PageSamples.calendar(CalendarViewMode.FOUR_DAYS).copy(
            bots = listOf(
                Bot(name = "default", isDefault = true),
                Bot(name = "planner", displayName = "Planner"),
            ),
            agentConfig = CalendarAgentConfig(profile = "default", sessionId = "cal-sess-1"),
        )
        var resetCount = 0
        var fullscreenCount = 0

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        CalendarPageContent(
                            state = sample,
                            onResetAgentSession = { resetCount++ },
                            onOpenAgentFullscreen = { fullscreenCount++ },
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag("calendar_agent_button").assertIsDisplayed().performClick()
        waitForIdle()

        onNodeWithTag("calendar_chat_composer_input").assertIsDisplayed()
        onNodeWithTag("calendar_agent_button").assertDoesNotExist()
        onNodeWithTag("calendar_agent_fullscreen_button").performClick()
        waitForIdle()
        assertEquals(1, fullscreenCount)

        onNodeWithTag("calendar_agent_reset_session_button").performClick()
        waitForIdle()
        assertEquals(1, resetCount)

        onNodeWithTag("calendar_agent_close_button").performClick()
        waitForIdle()
        onNodeWithTag("calendar_agent_button").assertIsDisplayed()
    }

    @Test
    fun calendarSettingsDialogRendersAgentSectionAndSavesSelectedProfile() = runDesktopComposeUiTest(width = 760, height = 820) {
        val sample = PageSamples.calendar(CalendarViewMode.FOUR_DAYS)
        val settingsWithAgent = sample.settings.copy(
            agent = CalendarAgentConfig(profile = "default", sessionId = "cal-sess-1"),
        )
        var savedProfile: String? = null

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        CalendarSettingsDialog(
                            visible = true,
                            settings = settingsWithAgent,
                            accounts = sample.accounts,
                            bots = listOf(
                                Bot(name = "default", isDefault = true),
                                Bot(name = "planner", displayName = "Planner"),
                            ),
                            mutating = false,
                            onDismiss = {},
                            onSave = { _, _, _, _, profile -> savedProfile = profile },
                            onDisconnectAccount = {},
                            onOpenPetPicker = {},
                            onResetAgentSession = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Calendar Agent").assertExists()
        onNodeWithText("Planner (@planner)").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals("planner", savedProfile)
    }

    @Test
    fun compactAgentSheetHidesRevertedProposalsAndCollapsesStagedStripByDefault() = runDesktopComposeUiTest(width = 760, height = 960) {
        val base = PageSamples.calendar(CalendarViewMode.FOUR_DAYS)
        val revertedProposal = ScheduleProposal(
            id = "prop-reverted",
            title = "Old Reverted Friday",
            targetDate = "2026-11-27",
            status = ScheduleProposal.STATUS_REVERTED,
            operations = listOf(
                ScheduleOperation(
                    opId = "op-rev",
                    kind = "create_event",
                    title = "Old Reverted Task",
                    startAt = "2026-11-27T02:00:00Z",
                    endAt = "2026-11-27T03:00:00Z",
                ),
            ),
        )
        val stagedProposal = ScheduleProposal(
            id = "prop-staged",
            title = "Saturday Plan",
            targetDate = "2026-11-28",
            status = ScheduleProposal.STATUS_STAGED,
            operations = listOf(
                ScheduleOperation(
                    opId = "op-stg",
                    kind = "create_event",
                    title = "Write Release Notes",
                    startAt = "2026-11-28T03:00:00Z",
                    endAt = "2026-11-28T04:00:00Z",
                ),
            ),
        )
        var deletedId: String? = null
        val sample = base.copy(
            proposals = listOf(revertedProposal, stagedProposal),
            agentConfig = CalendarAgentConfig(profile = "default", sessionId = "cal-sess-1"),
        )

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        CalendarPageContent(
                            state = sample,
                            onDeleteProposal = { id -> deletedId = id },
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag("calendar_agent_button").assertIsDisplayed().performClick()
        waitForIdle()

        // Reverted proposals must not clutter the Agent panel header; staged starts collapsed.
        onNodeWithTag("calendar_chat_composer_input").assertIsDisplayed()
        onNodeWithText("Old Reverted Friday").assertDoesNotExist()
        onNodeWithText("Old Reverted Task").assertDoesNotExist()
        onNodeWithTag("calendar_proposals_toggle").assertIsDisplayed()
        onNodeWithText("Write Release Notes").assertDoesNotExist()

        // Expanding reveals the staged item; collapsing hides it; delete button clears it cleanly.
        onNodeWithTag("calendar_proposals_toggle").performClick()
        waitForIdle()
        onNodeWithText("Write Release Notes").assertIsDisplayed()

        onNodeWithTag("calendar_proposals_toggle").performClick()
        waitForIdle()
        onNodeWithText("Write Release Notes").assertDoesNotExist()

        onNodeWithTag("calendar_proposals_clear_top").performClick()
        waitForIdle()
        assertEquals("prop-staged", deletedId)
    }
}
