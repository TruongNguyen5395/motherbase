package dev.hermeskotlin.ui.sessions

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.calendar.CalendarAgentConfig
import dev.hermeskotlin.core.calendar.CalendarAgentStore
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectIndex
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.tasks.DispatchSessionEntry
import dev.hermeskotlin.core.threads.ChatThread
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CalendarSidebarUiTest {

    @Test
    fun threadSectionsIsolatesDedicatedCalendarAgentSessionFromDispatchAndChats() {
        val threadSession = SessionSummary(id = "sess-th-1", title = "Daily Ops", pinned = true)
        val threads = listOf(ChatThread(id = "th-1", name = "Daily Ops", sessionId = "sess-th-1"))
        val dispatchSession = SessionSummary(id = "sess-d1", title = "Dispatch task")
        val dispatchMap = mapOf(
            "sess-d1" to DispatchSessionEntry(sessionId = "sess-d1", taskId = "task-1", taskTitle = "Write release notes"),
        )
        val calendarLineageMatch = SessionSummary(
            id = "sess-cal-compressed",
            title = CalendarAgentStore.AGENT_SESSION_TITLE,
            lineageIds = listOf("cal-agent-old", "sess-cal-compressed"),
        )
        val regularChat = SessionSummary(id = "sess-regular", title = "Regular chat")

        val sections = threadSections(
            threads = threads,
            listed = listOf(threadSession, dispatchSession, calendarLineageMatch, regularChat),
            rows = listOf(threadSession, dispatchSession, calendarLineageMatch, regularChat),
            project = null,
            index = ProjectIndex(emptyList<Project>()),
            dispatchSessions = dispatchMap,
            calendarAgent = CalendarAgentConfig(profile = "planner", sessionId = "cal-agent-old"),
        )

        assertEquals(1, sections.threads.size)
        assertEquals(1, sections.dispatch.size)
        assertEquals("sess-cal-compressed", sections.calendar?.id)
        assertEquals(listOf("sess-regular"), sections.others.map { it.id })
    }

    @Test
    fun calendarSectionRendersBelowDispatchAndAbovePinnedWithFallbackTitle() = runDesktopComposeUiTest(width = 320, height = 720) {
        val threads = listOf(
            ThreadRow(
                thread = ChatThread(id = "th-1", name = "Daily Ops", sessionId = "sess-th-1"),
                session = SessionSummary(id = "sess-th-1", title = "Daily Ops", pinned = true, lastActive = 100.0),
            ),
        )
        val dispatch = listOf(
            DispatchRow(
                entry = DispatchSessionEntry(sessionId = "sess-d1", taskId = "task-1", taskTitle = "Deploy storage service"),
                session = SessionSummary(id = "sess-d1", title = "Task start", lastActive = 95.0),
            ),
        )
        val calendarSession = SessionSummary(id = "sess-cal-1", title = null, lastActive = 92.0)
        val others = listOf(
            SessionSummary(id = "sess-pin", title = "Pinned normal chat", pinned = true, lastActive = 90.0),
            SessionSummary(id = "sess-plain", title = "Regular normal chat", lastActive = 80.0),
        )

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        SessionList(
                            sessions = others,
                            selectedId = null,
                            onOpen = {},
                            onActions = {},
                            sectioned = true,
                            projectIndex = ProjectIndex(emptyList<Project>()),
                            threads = threads,
                            onToggleThreads = {},
                            dispatch = dispatch,
                            calendar = calendarSession,
                            onToggleDispatch = {},
                            onTogglePinned = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        val threadsBounds = onNodeWithText("THREADS · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dispatchBounds = onNodeWithText("DISPATCH · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val calendarBounds = onNodeWithText("CALENDAR").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pinnedBounds = onNodeWithText("PINNED · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue(threadsBounds.top < dispatchBounds.top, "Expected DISPATCH below THREADS")
        assertTrue(dispatchBounds.top < calendarBounds.top, "Expected CALENDAR below DISPATCH")
        assertTrue(calendarBounds.top < pinnedBounds.top, "Expected CALENDAR above PINNED")

        onNodeWithText(CalendarAgentStore.AGENT_SESSION_TITLE).assertIsDisplayed()
        onNodeWithText("Deploy storage service").assertIsDisplayed()
        onNodeWithText("Pinned normal chat").assertIsDisplayed()
        onNodeWithText("Regular normal chat").assertIsDisplayed()
    }
}
