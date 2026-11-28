package dev.hermeskotlin.ui.sessions

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotMeta
import dev.hermeskotlin.core.bots.BotSession
import dev.hermeskotlin.core.chat.Waiting
import dev.hermeskotlin.core.projects.ProjectIndex
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.rooms.RoomMember
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DispatchRoomEntry
import dev.hermeskotlin.core.tasks.DispatchSessionEntry
import dev.hermeskotlin.core.threads.ChatThread
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.bots.BotsUiState
import dev.hermeskotlin.ui.bots.NeedsYou
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UnifiedAgentSidebarTest {

    private fun pinnedBot(name: String, displayName: String, activity: Double = 10.0): Bot = Bot(
        name = name,
        displayName = displayName,
        canonicalSession = BotSession(id = "canon-$name", lastActive = activity),
        uiMeta = buildJsonObject {
            put(BotMeta.KEY, buildJsonObject { put("pinned", true) })
        },
    )

    @Test
    fun coordinatorAgentOrdersFirstAheadOfPinnedAndActiveBots() {
        val pinned = pinnedBot("coder", "Coder", activity = 500.0)
        val coordinator = Bot(
            name = "hermes-coord",
            displayName = "Hermes",
            canonicalSession = BotSession(id = "canon-coord", lastActive = 100.0),
        )
        val regular = Bot(
            name = "research",
            displayName = "Researcher",
            canonicalSession = BotSession(id = "canon-research", lastActive = 300.0),
        )

        val ordered = orderAgentsForSidebar(listOf(pinned, regular, coordinator), coordinatorProfile = "hermes-coord")

        assertEquals(listOf("hermes-coord", "coder", "research"), ordered.map { it.name })
    }

    @Test
    fun activeOverviewCollectsAndDeduplicatesCrossAgentAttentionAndRunningWork() {
        val coord = Bot(name = "default", isDefault = true, displayName = "Hermes", canonicalSession = BotSession(id = "c-def"))
        val coder = Bot(
            name = "coder",
            displayName = "Coder",
            canonicalSession = BotSession(id = "c-coder"),
            workerSession = BotSession(id = "w-coder", lastActive = 995.0, title = "Worker"),
        )
        val researcher = Bot(
            name = "research",
            displayName = "Researcher",
            canonicalSession = BotSession(id = "c-res"),
        )
        val dispatchSessions = mapOf(
            "d-wait" to DispatchSessionEntry("d-wait", "t-1", "Approve migration", profile = "research"),
            "d-run" to DispatchSessionEntry("d-run", "t-2", "Refactor sidebar", profile = "coder"),
        )
        val room = Room(
            roomId = "room-1",
            name = "Review Room",
            members = listOf(RoomMember("m1", profile = "default", displayName = "Hermes")),
        )
        val dispatchRooms = mapOf("room-1" to DispatchRoomEntry("room-1", "t-2", "Refactor sidebar"))

        val items = buildActiveAttentionItems(
            bots = listOf(coord, coder, researcher),
            needsYou = listOf(NeedsYou.Answer(coord, Waiting.Question)),
            dispatchSessions = dispatchSessions,
            dispatchRooms = dispatchRooms,
            rooms = listOf(room),
            desktopRooms = emptyList(),
            unreadRooms = setOf("room-1"),
            liveWaiting = mapOf("d-wait" to Waiting.Approval, "c-def" to Waiting.Question),
            liveRunning = mapOf("d-run" to true),
            agentBranches = emptyMap(),
            nowSeconds = 1000.0,
            launchProfile = "default",
        )

        // Waiting Dispatch -> NeedsYou Bot -> Running Dispatch -> Working Bot -> Unread Dispatch Room
        assertEquals(
            listOf(
                "dispatch:d-wait",
                "needs:default",
                "dispatch:d-run",
                "working:coder",
                "room:room-1",
            ),
            items.map { it.key },
        )
    }

    @Test
    fun sliceAgentSessionsExcludesCanonicalAndCapsRecentWhileKeepingSelected() {
        val bot = Bot(
            name = "coder",
            canonicalSession = BotSession(id = "canon-1", resolvedId = "canon-live"),
        )
        val sessions = listOf(
            SessionSummary(id = "canon-live", title = "Bot Chat", lastActive = 1000.0),
            SessionSummary(id = "disp-1", title = "Task 1", lastActive = 990.0),
        ) + (1..8).map { i ->
            SessionSummary(id = "rec-$i", title = "Recent $i", lastActive = 900.0 - i)
        }
        val slice = sliceAgentSessions(
            bot = bot,
            threads = emptyList(),
            listed = sessions,
            allRows = sessions,
            project = null,
            projectIndex = ProjectIndex(emptyList()),
            dispatchSessions = mapOf("disp-1" to DispatchSessionEntry("disp-1", "t-1", "Implement feature", profile = "coder")),
            calendarAgent = null,
            statuses = emptyMap(),
            drafts = emptySet(),
            selectedId = "rec-8",
            attentionFilter = AttentionFilter.All,
            threadsCollapsed = false,
            dispatchCollapsed = false,
            pinnedCollapsed = false,
            recentExpanded = false,
        )

        assertEquals(listOf("disp-1"), slice.shownDispatch.map { it.session.id })
        assertFalse(slice.shownRecent.any { it.id == "canon-live" })
        assertEquals(8, slice.totalRecentCount)
        assertTrue(slice.recentCapped)
        assertEquals(DEFAULT_AGENT_RECENT_CAP, slice.shownRecent.size)
        assertTrue(slice.shownRecent.any { it.id == "rec-8" })
    }

    @Test
    fun unifiedSidebarRendersActiveOverviewExpandedAgentSectionsAndRoomsAt304dp() = runDesktopComposeUiTest(width = 304, height = 720) {
        val coord = Bot(name = "default", isDefault = true, displayName = "Hermes", canonicalSession = BotSession(id = "canon-def", preview = "Ready"))
        val coder = Bot(name = "coder", displayName = "Coder", canonicalSession = BotSession(id = "canon-coder", preview = "Working on UI"))
        val dispatch = mapOf(
            "d-1" to DispatchSessionEntry("d-1", "task-1", "Deploy sidebar", DirectusTask.STATUS_IN_PROGRESS, profile = "coder"),
        )
        val coderSessions = listOf(
            SessionSummary(id = "d-1", title = "Deploy sidebar", lastActive = 100.0),
            SessionSummary(id = "th-s1", title = "CI Pipeline", pinned = true, lastActive = 95.0),
        ) + (1..6).map { idx ->
            SessionSummary(id = "r-$idx", title = "Recent chat $idx", lastActive = 80.0 - idx)
        }
        val threads = listOf(ChatThread(id = "th-1", name = "CI Pipeline", sessionId = "th-s1"))
        val rooms = listOf(
            Room(
                roomId = "room-1",
                name = "Architecture Room",
                members = listOf(
                    RoomMember("m1", profile = "default", displayName = "Hermes"),
                    RoomMember("m2", profile = "coder", displayName = "Coder"),
                ),
            ),
        )

        var toggledBot: String? = null
        var newChatBot: String? = null
        var openedDispatchTask: String? = null
        var openedConfigFor: String? = null

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        UnifiedAgentSidebar(
                            botsState = BotsUiState(bots = listOf(coord, coder), loading = false),
                            avatars = emptyMap(),
                            coordinatorProfile = "default",
                            launchProfile = "default",
                            expandedAgents = setOf("coder"),
                            boundAgentName = "coder",
                            boundUiState = SessionsUiState(sessions = coderSessions, loading = false),
                            boundThreads = threads,
                            boundStatuses = mapOf("d-1" to RowStatus(running = true)),
                            agentBranches = emptyMap(),
                            liveWaiting = emptyMap(),
                            liveRunning = mapOf("d-1" to true),
                            drafts = emptySet(),
                            dispatchSessions = dispatch,
                            dispatchRooms = mapOf("room-1" to DispatchRoomEntry("room-1", "task-1", "Deploy sidebar")),
                            calendarAgent = null,
                            attentionFilter = AttentionFilter.All,
                            selectedId = "d-1",
                            selectedRunning = true,
                            nowSeconds = 100.0,
                            showActiveOverview = true,
                            projectsAsDropdown = false,
                            threadsCollapsed = false,
                            dispatchCollapsed = false,
                            pinnedCollapsed = false,
                            reorderingThreads = false,
                            troubles = emptyMap(),
                            failingRoutines = emptyMap(),
                            needsYou = emptyList(),
                            rooms = rooms,
                            roomsAvailable = true,
                            unreadRooms = emptySet(),
                            roomsNotice = null,
                            screenStates = emptyMap(),
                            showScreenAction = false,
                            botActions = PreviewBotActions,
                            pageCallbacks = AgentPageCallbacks(
                                onOpenArchived = {},
                                onOpenConfig = { openedConfigFor = it.name },
                                onOpenCapabilities = {},
                                onOpenPlugins = {},
                                onOpenInsights = {},
                            ),
                            onToggleAgent = { toggledBot = it.name },
                            onNewChatInAgent = { newChatBot = it.name },
                            onOpenSession = { _, _ -> },
                            onSessionActions = { _, _ -> },
                            sessionMenu = null,
                            swipeRight = { null },
                            swipeLeft = { null },
                            onOpenThread = { _, _ -> },
                            onThreadActions = { _, _ -> },
                            onReorderedThreads = {},
                            onToggleThreads = {},
                            onToggleDispatch = {},
                            onTogglePinned = {},
                            onSelectProject = {},
                            onNewProject = null,
                            onProjectOptions = { _, _ -> },
                            onSelectAttentionFilter = {},
                            onLoadMore = {},
                            onRefreshBound = {},
                            onOpenActiveDispatch = { openedDispatchTask = it.taskId },
                            onOpenActiveBranchSession = { _, _ -> },
                            onOpenRoom = {},
                            onOpenDesktopRoom = {},
                            onNewRoom = {},
                            onRenameRoom = { _, _ -> },
                            onDeleteRoom = {},
                            onDismissRoomsNotice = {},
                            onRetryBots = {},
                            onDismissBotsNotice = {},
                            top = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        // Cross-agent overview + Coordinator badge + expanded Coder sections
        onNodeWithText("ACTIVE & NEEDS YOU").assertIsDisplayed()
        onNodeWithText("Coordinator").assertIsDisplayed()
        onNodeWithText("Direct chat").assertIsDisplayed()
        onNodeWithText("DISPATCH · 1").assertIsDisplayed()
        onNodeWithText("THREADS · 1").assertIsDisplayed()
        onNodeWithText("RECENT").assertIsDisplayed()
        onNodeWithText("Show all (6)").assertIsDisplayed()
        onNodeWithText("Recent chat 6").assertDoesNotExist()

        // Clicking Show all reveals capped recent row; Rooms sits below
        val list = onNodeWithTag("unified_agent_sidebar_list")
        onNodeWithText("Show all (6)").performClick()
        waitForIdle()
        list.performScrollToKey("agent:coder:recent:r-6")
        onNodeWithText("Recent chat 6").assertIsDisplayed()
        list.performScrollToKey("room:room-1")
        onNodeWithText("Architecture Room").assertIsDisplayed()

        // New chat button on Hermes header + expand toggle + agent menu -> Config
        list.performScrollToKey("agent:default")
        onNodeWithContentDescription("New chat with Hermes").performClick()
        assertEquals("default", newChatBot)

        onNodeWithTag("agent_row_default").performClick()
        assertEquals("default", toggledBot)

        list.performScrollToKey("agent:coder")
        onNodeWithContentDescription("Coder actions").performClick()
        waitForIdle()
        onNodeWithText("Config").performClick()
        assertEquals("coder", openedConfigFor)

        // Active overview click jumps to Dispatch task
        list.performScrollToKey("active:dispatch:d-1")
        onNodeWithTag("active_item_dispatch:d-1").performClick()
        assertEquals("task-1", openedDispatchTask)
    }
}
