package dev.hermeskotlin.ui.sessions

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectIndex
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.tasks.DispatchSessionEntry
import dev.hermeskotlin.core.threads.ChatThread
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DispatchSidebarUiTest {

    @Test
    fun dispatchSectionRendersDirectlyBelowThreadsAndAbovePinned() = runDesktopComposeUiTest(width = 320, height = 720) {
        val threads = listOf(
            ThreadRow(
                thread = ChatThread(id = "th-1", name = "Daily Ops", sessionId = "sess-th-1"),
                session = SessionSummary(id = "sess-th-1", title = "Daily Ops", pinned = true, lastActive = 100.0),
            ),
        )
        val dispatch = listOf(
            DispatchRow(
                entry = DispatchSessionEntry(sessionId = "sess-d1", taskId = "task-1", taskTitle = "Deploy storage service"),
                session = SessionSummary(id = "sess-d1", title = "[Task: Deploy storage service] start", lastActive = 95.0),
            ),
        )
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
        val pinnedBounds = onNodeWithText("PINNED · 1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        assertTrue(threadsBounds.top < dispatchBounds.top, "Expected DISPATCH below THREADS ($threadsBounds vs $dispatchBounds)")
        assertTrue(dispatchBounds.top < pinnedBounds.top, "Expected DISPATCH above PINNED ($dispatchBounds vs $pinnedBounds)")

        onNodeWithText("Daily Ops").assertIsDisplayed()
        onNodeWithText("Deploy storage service").assertIsDisplayed()
        onNodeWithText("Pinned normal chat").assertIsDisplayed()
        onNodeWithText("Regular normal chat").assertIsDisplayed()
    }

    @Test
    fun collapsedDispatchSectionFoldsIdleRowsButKeepsRunningAndSelected() = runDesktopComposeUiTest(width = 320, height = 720) {
        val dispatch = listOf(
            DispatchRow(
                entry = DispatchSessionEntry("sess-idle", "t-1", "Idle dispatch task"),
                session = SessionSummary("sess-idle", title = "Idle dispatch task", lastActive = 100.0),
            ),
            DispatchRow(
                entry = DispatchSessionEntry("sess-run", "t-2", "Running dispatch task"),
                session = SessionSummary("sess-run", title = "Running dispatch task", lastActive = 99.0),
            ),
            DispatchRow(
                entry = DispatchSessionEntry("sess-sel", "t-3", "Selected dispatch task"),
                session = SessionSummary("sess-sel", title = "Selected dispatch task", lastActive = 98.0),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        SessionList(
                            sessions = emptyList(),
                            selectedId = "sess-sel",
                            onOpen = {},
                            onActions = {},
                            statuses = mapOf("sess-run" to RowStatus(running = true)),
                            sectioned = true,
                            dispatch = dispatch,
                            dispatchCollapsed = true,
                            onToggleDispatch = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("DISPATCH · 3").assertIsDisplayed()
        onNodeWithText("Running dispatch task").assertIsDisplayed()
        onNodeWithText("Selected dispatch task").assertIsDisplayed()
        onNodeWithText("Idle dispatch task").assertDoesNotExist()
    }
}
