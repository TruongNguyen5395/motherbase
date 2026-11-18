package dev.hermeskotlin.ui.tasks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.tasks.DirectusConfigStatus
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.components.ProvideAppWords
import dev.hermeskotlin.ui.preview.PageSamples
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TasksPageUiTest {

    @Test
    fun priorityStripToggleAndBentoProjectGroupsRender() = runDesktopComposeUiTest(width = 1080, height = 720) {
        var priorityStripEnabled by mutableStateOf(true)
        var state by mutableStateOf(PageSamples.tasks(selectedTaskId = null).copy(showPriorityStrip = true))

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state.copy(showPriorityStrip = priorityStripEnabled),
                            actions = TasksBoardActions(
                                onTogglePriorityStrip = { priorityStripEnabled = it },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("NEEDS DECISION (1)").assertIsDisplayed()
        onAllNodesWithText("Backend API").let { nodes ->
            assertEquals(2, nodes.fetchSemanticsNodes().size)
            nodes[0].assertIsDisplayed()
            nodes[1].assertIsDisplayed()
        }
        onAllNodesWithText("Prune aged backup snapshots")[0].assertIsDisplayed()
        onAllNodesWithText("Homelab")[0].assertIsDisplayed()

        onNodeWithText("Priority strip").performClick()
        waitForIdle()
        assertEquals(false, priorityStripEnabled)
        onAllNodesWithText("NEEDS DECISION (1)").fetchSemanticsNodes().let { nodes ->
            assertEquals(0, nodes.size)
        }
    }

    @Test
    fun clickingTaskOpensRightRailWithDualChannelsLiveLogAndTweakableChip() = runDesktopComposeUiTest(width = 1120, height = 760) {
        var state by mutableStateOf(PageSamples.tasks(selectedTaskId = null))
        var approvedOption: ReplyInteraction.Option? = null
        var approvedParams: Map<String, String> = emptyMap()
        var approvedTweaks: List<TweakDeltaInput> = emptyList()

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSelectTask = { id -> state = state.copy(selectedTaskId = id) },
                                onSelectChannel = { idx -> state = state.copy(activeChannelIdx = idx) },
                                onApprovePlan = { _, opt, params, tweaks ->
                                    approvedOption = opt
                                    approvedParams = params
                                    approvedTweaks = tweaks
                                },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Audit gateway rate-limit middleware").performClick()
        waitForIdle()
        onNodeWithText("LIVE CHAT LOG").assertIsDisplayed()

        onAllNodesWithText("Prune aged backup snapshots")[0].performClick()
        waitForIdle()

        // Both linked Room and Session chips appear inside the Right-Rail inspector.
        onNodeWithText("Room (@ops, @reviewer)").assertIsDisplayed()
        onNodeWithText("Session (@ops)").assertIsDisplayed()
        onNodeWithText("Scanned backup volumes; 18 snapshots eligible for pruning.").assertIsDisplayed()

        // Tweak retention chip (7 days -> 14 days -> 30 days) then trigger action button.
        onNodeWithTag("interaction_param_retention").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("interaction_choice_option_0").performScrollTo().performClick()
        waitForIdle()

        assertEquals("Run maintenance", approvedOption?.label)
        assertEquals("30 days", approvedParams["retention"])
        assertEquals(
            listOf(TweakDeltaInput(paramKey = "retention", paramLabel = "Keep", agentProposed = "14 days", userSelected = "30 days")),
            approvedTweaks,
        )
    }

    @Test
    fun narrowPhoneWidth360DpShowsPriorityAndProjectGroups() = runDesktopComposeUiTest(width = 360, height = 800) {
        val state = PageSamples.tasks(selectedTaskId = null)
        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Tasks").assertIsDisplayed()
        onNodeWithText("Priority strip").assertIsDisplayed()
        onAllNodesWithText("Backend API")[0].performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unconfiguredBoardRendersDirectusSetupForm() = runDesktopComposeUiTest(width = 412, height = 820) {
        val state = TasksUiState(config = DirectusConfigStatus(configured = false))
        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Connect Directus").assertIsDisplayed()
        onNodeWithText("Directus URL").assertIsDisplayed()
        onNodeWithText("API token").assertIsDisplayed()
        onNodeWithText("Test and save").assertIsDisplayed()
    }

    @Test
    fun sidebarToggleAndTabletWideDockingWorkTogether() = runDesktopComposeUiTest(width = 960, height = 800) {
        var state by mutableStateOf(PageSamples.tasks(selectedTaskId = null))
        var sidebarToggles = 0
        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSelectTask = { id -> state = state.copy(selectedTaskId = id) },
                            ),
                            onBack = {},
                            onToggleSidebar = { sidebarToggles++ },
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Show or hide the sidebar").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(1, sidebarToggles)

        onNodeWithText("Audit gateway rate-limit middleware").performClick()
        waitForIdle()
        onNodeWithText("LIVE CHAT LOG").assertIsDisplayed()
        onNodeWithText("Prepare plan").performScrollTo().assertIsDisplayed()
    }
}
