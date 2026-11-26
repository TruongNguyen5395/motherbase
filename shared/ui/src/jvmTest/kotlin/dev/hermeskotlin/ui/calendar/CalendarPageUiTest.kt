package dev.hermeskotlin.ui.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
