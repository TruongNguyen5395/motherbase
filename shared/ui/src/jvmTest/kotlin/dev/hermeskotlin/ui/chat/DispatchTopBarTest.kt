package dev.hermeskotlin.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DispatchTopBarTest {
    @Test
    fun topBarRendersBackArrowWithoutDispatchLabelAndTaskPanelToggle() = runDesktopComposeUiTest(width = 760, height = 160) {
        var backClicks = 0
        var taskClicks = 0
        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TopBar(
                            title = "Project delivery checklist",
                            titleFace = null,
                            status = null,
                            onOpenSidebar = {},
                            onNewChat = {},
                            onOpenMenu = null,
                            onBackToDispatch = { backClicks++ },
                            taskPanelOpen = false,
                            taskHasPendingPlan = true,
                            onToggleTaskPanel = { taskClicks++ },
                        )
                    }
                }
            }
        }
        waitForIdle()

        assertEquals(0, onAllNodesWithText("Dispatch").fetchSemanticsNodes().size)
        onNodeWithTag("topbar_back_to_dispatch").assertIsDisplayed().performClick()
        onNodeWithTag("topbar_task_button").assertIsDisplayed().performClick()
        waitForIdle()

        assertTrue(backClicks == 1)
        assertTrue(taskClicks == 1)
    }
}
