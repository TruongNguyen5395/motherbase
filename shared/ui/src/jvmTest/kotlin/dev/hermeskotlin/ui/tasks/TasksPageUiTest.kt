package dev.hermeskotlin.ui.tasks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.chat.ChatState
import dev.hermeskotlin.core.chat.ClarifyQuestion
import dev.hermeskotlin.core.chat.GeneratedImage
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.media.MediaApi
import dev.hermeskotlin.core.models.ModelCatalog
import dev.hermeskotlin.core.models.ModelOption
import dev.hermeskotlin.core.models.ModelProvider
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.settings.AppSettings
import dev.hermeskotlin.core.tasks.DirectusConfigStatus
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DispatchCheckinSchedule
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.PlanAction
import dev.hermeskotlin.core.tasks.StagedPlan
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TaskReplyStatus
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.LocalAppSettings
import dev.hermeskotlin.ui.LocalWindowControls
import dev.hermeskotlin.ui.WindowControls
import dev.hermeskotlin.ui.chat.ModelPickerState
import dev.hermeskotlin.ui.components.ProvideAppWords
import dev.hermeskotlin.ui.preview.PageSamples
import dev.hermeskotlin.ui.voice.DictationState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        onNodeWithText("NEEDS ATTENTION (1)").assertIsDisplayed()
        onNodeWithText("High impact & urgent (1)").assertIsDisplayed()
        onAllNodesWithText("Backend API").let { nodes ->
            assertEquals(2, nodes.fetchSemanticsNodes().size)
            nodes[0].assertIsDisplayed()
            nodes[1].assertIsDisplayed()
        }
        onAllNodesWithText("Prune aged backup snapshots")[0].assertIsDisplayed()
        onAllNodesWithText("Homelab")[0].assertIsDisplayed()

        onNodeWithText("Needs attention").performClick()
        waitForIdle()
        assertEquals(false, priorityStripEnabled)
        onAllNodesWithText("NEEDS ATTENTION (1)").fetchSemanticsNodes().let { nodes ->
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

        // Details and Action Proposal stay collapsed on initial task open so LIVE CHAT LOG is unobstructed.
        onNodeWithTag("task_plan_sheet").assertDoesNotExist()
        onNodeWithTag("task_inspector_details_toggle").performClick()
        waitForIdle()

        // Both linked Room and Session chips appear inside the expanded Details drawer.
        onNodeWithText("Room (@ops, @reviewer)").assertIsDisplayed()
        onNodeWithText("Session (@ops)").assertIsDisplayed()

        // Clicking Action proposal opens the staged plan sheet.
        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
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
        onNodeWithText("Dispatch").assertIsDisplayed()
        onNodeWithText("Needs attention").assertIsDisplayed()
        onAllNodesWithText("Backend API").let { nodes ->
            val count = nodes.fetchSemanticsNodes().size
            assertEquals(2, count)
            nodes[count - 1].performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun largeProjectCapsAtFiveTasksExpandsAndSwitchesToFocusWorkstreams() = runDesktopComposeUiTest(width = 1080, height = 760) {
        val today = epochDayFromCivilIso("2026-11-18")!!
        val alpha = DirectusProject(id = "alpha", name = "Release Rollout", sort = 1)
        val beta = DirectusProject(id = "beta", name = "Core Infra", sort = 2)
        val root = DirectusTask(id = "wg-pack", title = "Packaging stream", status = DirectusTask.STATUS_IN_PROGRESS, context = TaskContext(taskId = "wg-pack", projectId = "alpha"))
        val alphaTasks = listOf(root) + (1..6).map { idx ->
            DirectusTask(
                id = "a-$idx",
                title = "Packaging step $idx",
                status = if (idx == 1) DirectusTask.STATUS_BLOCKED else DirectusTask.STATUS_TODO,
                parentId = "wg-pack",
                dueDate = if (idx == 2) "2026-11-16" else null,
                context = TaskContext(taskId = "a-$idx", projectId = "alpha"),
            )
        }
        val betaTask = DirectusTask(id = "b-1", title = "Calibrate checks", context = TaskContext(taskId = "b-1", projectId = "beta"))

        var state by mutableStateOf(
            TasksUiState(
                config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
                projects = listOf(alpha, beta),
                tasks = alphaTasks + betaTask,
                todayEpochDay = today,
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onFocusProject = { pid -> state = state.copy(focusedProjectId = pid) },
                                onSetFocusGrouping = { g -> state = state.copy(focusGrouping = g) },
                                onToggleProjectExpanded = { k ->
                                    val next = if (k in state.expandedProjectIds) state.expandedProjectIds - k else state.expandedProjectIds + k
                                    state = state.copy(expandedProjectIds = next)
                                },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("NEEDS ATTENTION (2)").assertIsDisplayed()
        onNodeWithText("High impact & urgent (2)").assertIsDisplayed()
        onNodeWithText("+2 more").performScrollTo().assertIsDisplayed()
        onNodeWithText("+2 more").performClick()
        waitForIdle()
        onNodeWithText("Show fewer").performScrollTo().assertIsDisplayed()

        onNodeWithText("Focus").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("By workstream").assertIsDisplayed()
        onNodeWithText("By status").performClick()
        waitForIdle()
        onNodeWithText("Blocked / Overdue").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyAttentionRendersCompactSingleLineBar() = runDesktopComposeUiTest(width = 960, height = 640) {
        val proj = DirectusProject(id = "p1", name = "Alpha", sort = 1)
        val cleanTask = DirectusTask(id = "t1", title = "Routine check", context = TaskContext(taskId = "t1", projectId = "p1"))
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(proj),
            tasks = listOf(cleanTask),
            todayEpochDay = epochDayFromCivilIso("2026-11-18")!!,
        )

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(state = state, actions = TasksBoardActions(), onBack = {})
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Needs attention (0) · No high-impact or urgent tasks").assertIsDisplayed()
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
        onNodeWithText("Prepare plan").assertIsDisplayed()
    }

    @Test
    fun liveChatPanelRendersUserBubbleStreamingMarkdownToolStatusClarifyAndSendsFollowUp() = runDesktopComposeUiTest(width = 1080, height = 760) {
        val session = TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-nc", label = "Session (@Hermes)")
        val task = DirectusTask(
            id = "t-nc",
            title = "Deploy object storage stack",
            status = DirectusTask.STATUS_IN_PROGRESS,
            priority = "high",
            context = TaskContext(
                taskId = "t-nc",
                projectId = "p1",
                agentState = TaskContext.STATE_RUNNING,
                channels = listOf(session),
            ),
        )
        val clarify = InputRequest.Clarify(
            id = "srq-nc-1",
            questions = listOf(
                ClarifyQuestion(
                    qid = null,
                    question = "Confirm storage domain?",
                    choices = listOf("cloud.example.com", "nc.example.com"),
                ),
            ),
        )
        var sentMessage: String? = null
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(DirectusProject(id = "p1", name = "Infra", sort = 1)),
            tasks = listOf(task),
            selectedTaskId = task.id,
            activeChannelIdx = 0,
            channelMessages = listOf(
                ChannelChatLine(key = "u-1", speaker = null, text = "Check storage deploy", fromUser = true),
                ChannelChatLine(key = "a-1", speaker = "Hermes", text = "Checking **nginx** workers…", fromUser = false, streaming = true),
            ),
            channelTurnRunning = true,
            channelTurnStatus = "directus_task_board",
            channelInputRequests = listOf(clarify),
            gatewayConnected = true,
            todayEpochDay = epochDayFromCivilIso("2026-11-18")!!,
        )

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSendToChannel = { _, text -> sentMessage = text },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("LIVE CHAT LOG").assertIsDisplayed()
        onNodeWithText("Check storage deploy").assertIsDisplayed()
        waitUntil(timeoutMillis = 3_000) {
            onAllNodesWithText("Checking nginx workers…", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Checking nginx workers…", substring = true).assertIsDisplayed()
        onNodeWithText("directus_task_board").assertIsDisplayed()
        onNodeWithText("Confirm storage domain?").assertIsDisplayed()
        onNodeWithText("cloud.example.com").assertIsDisplayed()

        onNodeWithTag("task_chat_composer_input").performTextInput("Restart php-fpm")
        waitForIdle()
        onNodeWithContentDescription("Send").performClick()
        waitForIdle()
        assertEquals("Restart php-fpm", sentMessage)
    }

    @Test
    fun markdownDescriptionAndWindowsCaptionControlsAvoidance() = runDesktopComposeUiTest(width = 1080, height = 760) {
        val task = DirectusTask(
            id = "t-md",
            title = "Summarize chat service",
            description = "**Target goal:** /srv/docs/README.md",
            status = DirectusTask.STATUS_TODO,
            dueDate = "2026-11-14",
            context = TaskContext(taskId = "t-md", projectId = "p1", agentState = TaskContext.STATE_IDLE),
        )
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(DirectusProject(id = "p1", name = "Operations", sort = 1)),
            tasks = listOf(task),
            selectedTaskId = task.id,
            todayEpochDay = epochDayFromCivilIso("2026-11-18")!!,
        )

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(
                    LocalDesktopLayout provides true,
                    LocalWindowControls provides WindowControls(start = 0, end = 140, height = 40),
                ) {
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

        onNodeWithText("Dispatch").assertIsDisplayed()
        onNodeWithTag("task_inspector_details_toggle").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 3_000) {
            onAllNodesWithText("Target goal: /srv/docs/README.md", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Target goal: /srv/docs/README.md", substring = true).assertIsDisplayed()

        val closeBounds = onNodeWithContentDescription("Close details").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            closeBounds.right <= 1080f - 140f + 1f,
            "Close details button (${closeBounds.right}) overlaps right window controls region (${1080f - 140f})",
        )
    }

    @Test
    fun composerSupportsMultilineGrowthAttachmentsAndSessionVsRoomModelPicker() = runDesktopComposeUiTest(width = 1080, height = 760) {
        val session = TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-ops", label = "Session (@ops)", profile = "ops")
        val room = TaskChannel(type = TaskChannel.TYPE_ROOM, id = "room-ops", label = "Room (@ops, @dev)")
        val task = DirectusTask(
            id = "t-comp",
            title = "Review cluster telemetry",
            status = DirectusTask.STATUS_IN_PROGRESS,
            context = TaskContext(
                taskId = "t-comp",
                projectId = "p1",
                channels = listOf(session, room),
            ),
        )
        val catalog = ModelCatalog(
            providers = listOf(
                ModelProvider(
                    slug = "anthropic",
                    name = "Anthropic",
                    models = listOf(ModelOption(id = "claude-opus-5", provider = "anthropic", reasoning = true)),
                ),
            ),
            currentModel = "claude-opus-5",
            currentProvider = "anthropic",
        )
        val attachment = OutgoingAttachment("att-1", "brief.pdf", "application/pdf", byteArrayOf(1, 2, 3))
        var removedId: String? = null
        var sentTexts = mutableListOf<String>()
        var state by mutableStateOf(
            TasksUiState(
                config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
                projects = listOf(DirectusProject(id = "p1", name = "Infra", sort = 1)),
                tasks = listOf(task),
                selectedTaskId = task.id,
                activeChannelIdx = 0,
                channelAttachments = listOf(attachment),
                channelChatState = ChatState(model = "claude-opus-5", provider = "anthropic"),
                modelPicker = ModelPickerState(catalog = catalog),
                gatewayConnected = true,
                todayEpochDay = epochDayFromCivilIso("2026-11-18")!!,
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Light) {
                CompositionLocalProvider(LocalDesktopLayout provides false) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSelectChannel = { idx -> state = state.copy(activeChannelIdx = idx) },
                                onRemoveAttachment = { id ->
                                    removedId = id
                                    state = state.copy(channelAttachments = state.channelAttachments.filterNot { it.id == id })
                                },
                                onSendToChannel = { _, txt -> sentTexts += txt },
                            ),
                            onBack = {},
                            dictation = DictationState(recording = true),
                            onToggleDictation = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        // + attachment button, queued attachment chip, model pill, dictation mic, and Send button are all displayed together.
        onNodeWithContentDescription("Add photos or files").assertIsDisplayed()
        onNodeWithText("brief.pdf").assertIsDisplayed()
        onNodeWithContentDescription("Change model", substring = true).assertIsDisplayed()
        onNodeWithContentDescription("Finish dictating").assertIsDisplayed()
        onNodeWithContentDescription("Send").assertIsDisplayed()

        // Attachment-only message enables Send and dispatches even when text box is empty.
        onNodeWithContentDescription("Send").performClick()
        waitForIdle()
        assertEquals(listOf(""), sentTexts)

        // Removing the attachment calls onRemoveAttachment.
        onNodeWithContentDescription("Remove brief.pdf").performClick()
        waitForIdle()
        assertEquals("att-1", removedId)

        // Multiline text grows the composer input height cleanly.
        val singleHeight = onNodeWithTag("task_chat_composer_input").fetchSemanticsNode().boundsInRoot.height
        onNodeWithTag("task_chat_composer_input").performTextInput("First line of notes\nSecond line of notes\nThird line of notes")
        waitForIdle()
        val multiHeight = onNodeWithTag("task_chat_composer_input").fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            multiHeight > singleHeight + 20f,
            "Expected multiline composer to expand (single=$singleHeight, multi=$multiHeight)",
        )

        // Switching to a multi-bot Room channel hides the session-only model pill while keeping +.
        onNodeWithTag("task_inspector_details_toggle").performClick()
        waitForIdle()
        onNodeWithText("Room (@ops, @dev)").performClick()
        waitForIdle()
        onNodeWithContentDescription("Change model", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Add photos or files").assertIsDisplayed()
    }

    @Test
    fun slideUpPlanDrawerStartsCollapsedOnTaskOpenAndAutoOpensOnlyWhenNewlyPrepared() = runDesktopComposeUiTest(width = 1080, height = 760) {
        val pendingTask = DirectusTask(
            id = "t-pending",
            title = "Pending proposal task",
            status = DirectusTask.STATUS_TODO,
            context = TaskContext(
                taskId = "t-pending",
                projectId = "p1",
                agentState = TaskContext.STATE_NEEDS_YOU,
                stagedPlan = StagedPlan(
                    summary = "Ready for review before execution.",
                    actions = listOf(PlanAction(label = "Execute plan", instruction = "Run step 1")),
                    generatedAt = "2026-11-19T10:00:00Z",
                ),
            ),
        )
        val approvedTask = DirectusTask(
            id = "t-approved",
            title = "Already approved task",
            status = DirectusTask.STATUS_IN_PROGRESS,
            context = TaskContext(
                taskId = "t-approved",
                projectId = "p1",
                agentState = TaskContext.STATE_RUNNING,
                stagedPlan = StagedPlan(
                    summary = "Previously approved rollout plan.",
                    actions = listOf(PlanAction(label = "Re-run rollout", instruction = "Re-run")),
                    generatedAt = "2026-11-19T09:00:00Z",
                    approvedAt = "2026-11-19T09:15:00Z",
                ),
            ),
        )
        val noPlanTask = DirectusTask(
            id = "t-empty",
            title = "Unplanned task",
            status = DirectusTask.STATUS_TODO,
            context = TaskContext(taskId = "t-empty", projectId = "p1", agentState = TaskContext.STATE_IDLE),
        )
        var preparedFor: String? = null
        var state by mutableStateOf(
            TasksUiState(
                config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
                projects = listOf(DirectusProject(id = "p1", name = "Ops", sort = 1)),
                tasks = listOf(pendingTask, approvedTask, noPlanTask),
                selectedTaskId = null,
                gatewayConnected = true,
                todayEpochDay = epochDayFromCivilIso("2026-11-18")!!,
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSelectTask = { id -> state = state.copy(selectedTaskId = id) },
                                onPreparePlan = { t -> preparedFor = t.id },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        // 1. Opening a task with an unapproved plan keeps the sheet collapsed until tapped, and never shows a "Plan ready" chip.
        onAllNodesWithText("Pending proposal task")[0].performClick()
        waitForIdle()
        kotlin.test.assertEquals(0, onAllNodesWithText("Plan ready").fetchSemanticsNodes().size)
        onNodeWithTag("task_plan_sheet").assertDoesNotExist()
        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertIsDisplayed()
        onNodeWithText("Ready for review before execution.").assertIsDisplayed()

        // Tapping the docked handle collapses the sheet, and tapping again re-opens it.
        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertDoesNotExist()
        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertIsDisplayed()

        // 2. Clicking a task with an already-approved plan also keeps the sheet collapsed until tapped.
        onAllNodesWithText("Already approved task")[0].performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertDoesNotExist()
        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertIsDisplayed()
        onNodeWithText("Previously approved rollout plan.").assertIsDisplayed()

        // 3. Triggering Prepare plan on an empty task auto-opens the sheet once the new plan arrives.
        onAllNodesWithText("Unplanned task")[0].performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertDoesNotExist()
        onNodeWithText("Prepare plan").assertIsDisplayed()
        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
        assertEquals("t-empty", preparedFor)

        val generatedPlan = StagedPlan(
            summary = "Freshly staged plan from agent.",
            actions = listOf(PlanAction(label = "Start work", instruction = "Begin step 1")),
            generatedAt = "2026-11-26T08:05:00Z",
        )
        state = state.copy(
            tasks = state.tasks.map { t ->
                if (t.id == "t-empty") t.copy(context = t.context?.copy(agentState = TaskContext.STATE_NEEDS_YOU, stagedPlan = generatedPlan))
                else t
            },
        )
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertIsDisplayed()
        onNodeWithText("Freshly staged plan from agent.").assertIsDisplayed()
    }

    @Test
    fun coordinatorFloatingTriggerOpensRailTriggersTextOnlyQuickChipsResetsSessionAndEditsSettings() = runDesktopComposeUiTest(width = 1120, height = 780) {
        val clickedActions = mutableListOf<CoordinatorQuickAction>()
        var resetCount = 0
        var savedCoordinatorProfile: String? = null
        var savedSchedules: List<DispatchCheckinSchedule> = emptyList()
        var state by mutableStateOf(
            PageSamples.tasks(selectedTaskId = null).copy(
                checkinDueSlot = "2026-11-20@16:00",
                gatewayConnected = true,
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onOpenCoordinator = { state = state.copy(coordinatorOpen = true, selectedTaskId = null) },
                                onCloseCoordinator = { state = state.copy(coordinatorOpen = false) },
                                onToggleCoordinator = { state = state.copy(coordinatorOpen = !state.coordinatorOpen, selectedTaskId = null) },
                                onQuickCoordinatorAction = { act -> clickedActions += act },
                                onResetCoordinatorSession = { resetCount += 1 },
                                onSaveConfigAndCoordinator = { _, _, coord ->
                                    savedCoordinatorProfile = coord?.profile
                                    savedSchedules = coord?.checkinSchedules.orEmpty()
                                },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        // Icon-only floating trigger button has accessible description without visible label or subtitle text.
        onNodeWithTag("dispatch_coordinator_button").assertIsDisplayed()
        onNodeWithContentDescription("Ask Hermes").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Ask Hermes").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("Check-in ready").fetchSemanticsNodes().isEmpty())

        // Clicking the floating trigger opens the Coordinator right rail without name/description header clutter.
        onNodeWithTag("dispatch_coordinator_button").performClick()
        waitForIdle()
        onNodeWithTag("coordinator_reset_session_button").assertIsDisplayed()
        onNodeWithText("Check-in ready").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Dispatch Coordinator · @default").fetchSemanticsNodes().isEmpty())

        // Text-only quick action chips are displayed and trigger their respective CoordinatorQuickAction.
        onNodeWithText("Check in now").assertIsDisplayed().performClick()
        onNodeWithText("Review blocked & overdue").assertIsDisplayed().performClick()
        onNodeWithText("Suggest next priorities").assertIsDisplayed().performClick()
        waitForIdle()
        assertEquals(
            listOf(
                CoordinatorQuickAction.CHECK_IN,
                CoordinatorQuickAction.BLOCKERS,
                CoordinatorQuickAction.PRIORITIES,
            ),
            clickedActions,
        )

        // Reset session button triggers onResetCoordinatorSession.
        onNodeWithTag("coordinator_reset_session_button").performClick()
        waitForIdle()
        assertEquals(1, resetCount)

        // Opening settings from the coordinator rail opens Dispatch's Setting with section descriptions and check-in rules.
        onNodeWithTag("coordinator_settings_button").performClick()
        waitForIdle()
        onNodeWithText("Dispatch's Setting").assertIsDisplayed()
        onNodeWithText("Coordinator agent").assertIsDisplayed()
        onNodeWithText("Standing instructions (default prompt)").assertIsDisplayed()
        onNodeWithText("Scheduled check-ins").assertIsDisplayed()

        // Add a custom check-in schedule rule with time, window, and standing instruction.
        onNodeWithTag("tasks_checkin_add_button").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Check-in time").assertIsDisplayed()
        onNodeWithText("20:00").performClick()
        onNodeWithText("± 45 min").performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("checkin_rule_instruction_field")))
            .performTextInput("Verify sprint capacity")
        waitForIdle()
        onNodeWithText("Apply").performClick()
        waitForIdle()

        onNodeWithText("20:00").performScrollTo().assertIsDisplayed()
        onNodeWithText("Verify sprint capacity").performScrollTo().assertIsDisplayed()

        onNodeWithText("Save settings").performClick()
        waitForIdle()
        assertEquals("default", savedCoordinatorProfile)
        assertTrue(
            savedSchedules.any {
                it.time == "20:00" && it.windowMinutes == 45 && it.instruction == "Verify sprint capacity" && it.enabled
            },
        )
    }

    @Test
    fun attentionIndicatorsFilterUnreadAndMatrixViewsRenderOnDesktopAndPhone() = runDesktopComposeUiTest(width = 1120, height = 760) {
        val today = epochDayFromCivilIso("2026-11-18")!!
        val proj = DirectusProject(id = "p1", name = "Core", sort = 1)
        val unreadTask = DirectusTask(
            id = "t-reply",
            title = "Review agent summary",
            priority = "low",
            context = TaskContext(taskId = "t-reply", projectId = "p1", impact = "low", urgency = "low"),
            replyStatus = TaskReplyStatus(hasUnreadReply = true, awaitingReply = true, lastReplyAt = 2000.0),
        )
        val doNowTask = DirectusTask(
            id = "t-crit",
            title = "Fix billing outage",
            priority = "urgent",
            context = TaskContext(taskId = "t-crit", projectId = "p1", impact = "high", urgency = "high"),
        )

        var updatedMatrix by mutableStateOf<Triple<String, MatrixLevel, MatrixLevel>?>(null)
        var state by mutableStateOf(
            TasksUiState(
                config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
                projects = listOf(proj),
                tasks = listOf(unreadTask, doNowTask),
                todayEpochDay = today,
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSetBoardViewMode = { mode -> state = state.copy(boardViewMode = mode) },
                                onToggleUnreadOnly = { state = state.copy(unreadOnly = !state.unreadOnly) },
                                onSelectTask = { id -> state = state.copy(selectedTaskId = id) },
                                onUpdateTaskMatrix = { id, imp, urg ->
                                    updatedMatrix = Triple(id, imp, urg)
                                    state = state.copy(
                                        tasks = state.tasks.map { t ->
                                            if (t.id == id) {
                                                t.copy(context = (t.context ?: TaskContext(taskId = id)).copy(impact = imp.wire, urgency = urg.wire))
                                            } else {
                                                t
                                            }
                                        },
                                    )
                                },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        // NEEDS ATTENTION surfaces priority tasks only; no "Agent replies", "Awaiting reply", or "Unread" text badges.
        onNodeWithText("NEEDS ATTENTION (1)").assertIsDisplayed()
        onNodeWithText("High impact & urgent (1)").assertIsDisplayed()
        kotlin.test.assertEquals(0, onAllNodesWithText("Agent replies (1)").fetchSemanticsNodes().size)
        kotlin.test.assertEquals(0, onAllNodesWithText("Awaiting reply").fetchSemanticsNodes().size)
        onNodeWithTag("task_indicator_t-reply", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("project_unread_p1", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("task_pill_ack_t-reply").assertDoesNotExist()

        // Toolbar "Unread" filter chip narrows the board to unread tasks only, then restores all tasks on second click.
        onNodeWithTag("board_filter_unread").performClick()
        waitForIdle()
        onNodeWithText("Review agent summary").assertIsDisplayed()
        kotlin.test.assertEquals(0, onAllNodesWithText("Fix billing outage").fetchSemanticsNodes().size)

        onNodeWithTag("board_filter_unread").performClick()
        waitForIdle()
        onAllNodesWithText("Fix billing outage")[0].assertIsDisplayed()

        // Switch board to 2D Matrix view and verify all 4 Cartesian quadrants render.
        onNodeWithText("Matrix").performClick()
        waitForIdle()
        onNodeWithTag("matrix_quadrant_do_now").assertIsDisplayed()
        onNodeWithTag("matrix_quadrant_schedule").assertIsDisplayed()
        onNodeWithTag("matrix_quadrant_delegate").assertIsDisplayed()
        onNodeWithTag("matrix_quadrant_later").assertIsDisplayed()

        // Open task detail, expand Details, and adjust position via the 3×3 interactive 2D pad.
        onAllNodesWithText("Review agent summary")[0].performClick()
        waitForIdle()
        onNodeWithTag("task_inspector_details_toggle").performClick()
        waitForIdle()
        onNodeWithTag("task_matrix_picker_pill").performClick()
        waitForIdle()
        onNodeWithTag("matrix_pad_cell_high_high").performClick()
        onNodeWithTag("matrix_pad_apply").performClick()
        waitForIdle()
        assertEquals(Triple("t-reply", MatrixLevel.HIGH, MatrixLevel.HIGH), updatedMatrix)
    }

    @Test
    fun dispatchSessionSidePanelEditsMetadataAndOpensActionProposalWithoutPlanReadyChip() = runDesktopComposeUiTest(width = 520, height = 760) {
        val pendingTask = DirectusTask(
            id = "t-side",
            title = "Ship landing page checklist",
            description = "Finalize design handoff checklist.",
            status = DirectusTask.STATUS_TODO,
            context = TaskContext(
                taskId = "t-side",
                projectId = "p1",
                impact = "high",
                urgency = "medium",
                agentState = TaskContext.STATE_NEEDS_YOU,
                assignedBots = listOf("ops"),
                channels = listOf(TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-side", label = "Session (@ops)", profile = "ops")),
                stagedPlan = StagedPlan(
                    summary = "Review checklist before shipping.",
                    actions = listOf(PlanAction(label = "Ship checklist", instruction = "Verify and ship")),
                    generatedAt = "2026-11-27T10:00:00Z",
                ),
            ),
        )
        var approved = false
        var updatedMatrix: Pair<MatrixLevel, MatrixLevel>? = null
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(DirectusProject(id = "p1", name = "Delivery", sort = 1)),
            tasks = listOf(pendingTask),
            selectedTaskId = "t-side",
            todayEpochDay = epochDayFromCivilIso("2026-11-27")!!,
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        DispatchTaskSidePanel(
                            state = state,
                            activeChannelId = "sess-side",
                            clearWindowControls = false,
                            onClose = {},
                            onAssignProject = {},
                            onUpdateStatus = {},
                            onUpdateMatrix = { imp, urg -> updatedMatrix = imp to urg },
                            onSelectChannel = {},
                            onCreateSessionChannel = { _, _ -> },
                            onCreateRoomChannel = { _, _ -> },
                            onReplaceWithSession = { _, _, _ -> },
                            onReplaceWithRoom = { _, _, _ -> },
                            onUnlinkChannel = { _, _ -> },
                            onSwitchToAssignedAgent = {},
                            onPreparePlan = {},
                            onApprovePlan = { _, _, _, _ -> approved = true },
                            onOpenScheduleBlock = { _, _ -> },
                            onDeleteTimeBlock = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag("dispatch_task_panel").assertIsDisplayed()
        onNodeWithText("Finalize design handoff checklist.").assertIsDisplayed()
        kotlin.test.assertEquals(0, onAllNodesWithText("Plan ready").fetchSemanticsNodes().size)

        onNodeWithTag("task_matrix_picker_pill").performClick()
        waitForIdle()
        onNodeWithTag("matrix_pad_cell_high_high").performClick()
        onNodeWithTag("matrix_pad_apply").performClick()
        waitForIdle()
        assertEquals(MatrixLevel.HIGH to MatrixLevel.HIGH, updatedMatrix)

        onNodeWithTag("task_plan_dock_button").performClick()
        waitForIdle()
        onNodeWithTag("task_plan_sheet").assertIsDisplayed()
        onNodeWithText("Ship checklist").performClick()
        waitForIdle()
        assertTrue(approved)
    }

    @Test
    fun narrowPhoneMatrixViewRendersInteractiveMinimapWithoutTruncation() = runDesktopComposeUiTest(width = 360, height = 800) {
        val today = epochDayFromCivilIso("2026-11-18")!!
        val proj = DirectusProject(id = "p1", name = "Mobile", sort = 1)
        val laterTask = DirectusTask(
            id = "t-later",
            title = "Clean up old feature flags",
            priority = "low",
            context = TaskContext(taskId = "t-later", projectId = "p1", impact = "low", urgency = "low"),
        )
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(proj),
            tasks = listOf(laterTask),
            todayEpochDay = today,
            boardViewMode = TasksBoardViewMode.MATRIX,
            showPriorityStrip = false,
        )

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

        onNodeWithTag("matrix_minimap_do_now").assertIsDisplayed()
        onNodeWithTag("matrix_minimap_later").performClick()
        waitForIdle()
        onNodeWithText("Clean up old feature flags").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun desktopInspectorRailIsResizableViaEdgeHandleAndPersistsWidth() = runDesktopComposeUiTest(width = 1280, height = 800) {
        var appSettings by mutableStateOf(AppSettings())
        val state = PageSamples.tasks(selectedTaskId = "t-2")

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(
                    LocalDesktopLayout provides true,
                    LocalAppSettings provides appSettings,
                ) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onUpdateSettings = { transform -> appSettings = transform(appSettings) },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag("right_rail_resize_handle").assertExists()
        onNodeWithTag("right_rail_resize_handle").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - 180f)
        }
        waitForIdle()
        assertTrue(
            appSettings.dispatchRailWidth > 460,
            "Expected dispatchRailWidth to widen after dragging left, got ${appSettings.dispatchRailWidth}",
        )

        onNodeWithTag("right_rail_resize_handle").performMouseInput { doubleClick(center) }
        waitForIdle()
        assertEquals(460, appSettings.dispatchRailWidth)
    }

    @Test
    fun runningSpinnerAndReviewIndicatorStayVisibleIndependentlyAcrossBoardAndMatrix() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val today = epochDayFromCivilIso("2026-11-18")!!
        val proj = DirectusProject(id = "p1", name = "Ops", sort = 1)
        val runningAndUnread = DirectusTask(
            id = "t-run-rev",
            title = "Active task with unread review",
            status = DirectusTask.STATUS_IN_PROGRESS,
            priority = "medium",
            context = TaskContext(
                taskId = "t-run-rev",
                projectId = "p1",
                agentState = TaskContext.STATE_RUNNING,
                impact = "high",
                urgency = "low",
                channels = listOf(TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-run", label = "Session (@Ops)")),
            ),
            replyStatus = TaskReplyStatus(hasUnreadReply = true, lastReplyAt = 2_000.0),
        )
        var viewMode by mutableStateOf(TasksBoardViewMode.PROJECTS)

        setContent {
            val state = TasksUiState(
                config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
                projects = listOf(proj),
                tasks = listOf(runningAndUnread),
                selectedTaskId = "t-run-rev",
                todayEpochDay = today,
                boardViewMode = viewMode,
                showPriorityStrip = false,
            )
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        TasksBoardContent(
                            state = state,
                            actions = TasksBoardActions(
                                onSetBoardViewMode = { viewMode = it },
                            ),
                            onBack = {},
                        )
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag("task_running_spinner_t-run-rev", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("task_indicator_t-run-rev", useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithText("Running")[0].assertIsDisplayed()

        onNodeWithText("Matrix").performClick()
        waitForIdle()
        onNodeWithTag("matrix_running_spinner_t-run-rev", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("matrix_indicator_t-run-rev", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun dispatchSidebarExtractsMediaPdfCardAndOpensPreviewViewer() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val today = epochDayFromCivilIso("2026-11-18")!!
        val proj = DirectusProject(id = "p1", name = "Ops", sort = 1)
        val channel = TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-ops", label = "Session (@ops)", profile = "ops")
        val task = DirectusTask(
            id = "t-media",
            title = "Share weekly summary",
            status = DirectusTask.STATUS_IN_PROGRESS,
            priority = "medium",
            context = TaskContext(
                taskId = "t-media",
                projectId = "p1",
                impact = "high",
                urgency = "medium",
                channels = listOf(channel),
            ),
        )
        val requests = mutableListOf<String>()
        val mediaApi = MediaApi(createHttpClient(MockEngine { request ->
            requests += "${request.url.encodedPath}|${request.url.parameters["path"]}|${request.url.parameters["profile"]}|${request.url.parameters["session_id"]}"
            respond("""{"detail":"Missing"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(proj),
            tasks = listOf(task),
            selectedTaskId = "t-media",
            todayEpochDay = today,
            showPriorityStrip = false,
            channelMessages = listOf(
                ChannelChatLine(
                    key = "a-pdf",
                    speaker = "ops",
                    text = "Here is the PDF:\nMEDIA:/opt/data/shared/outputs/weekly/weekly_report.pdf",
                    fromUser = false,
                    profile = "ops",
                ),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        ProvideChannelMedia(
                            gateway = GatewayUrl.parse("https://hermes.example"),
                            activeChannel = channel,
                            mediaApi = mediaApi,
                        ) {
                            TasksBoardContent(
                                state = state,
                                actions = TasksBoardActions(),
                                onBack = {},
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        assertTrue(onAllNodesWithText("MEDIA:", substring = true).fetchSemanticsNodes().isEmpty())
        onNodeWithText("Here is the PDF:").assertIsDisplayed()
        onNodeWithText("weekly_report.pdf").assertIsDisplayed()
        onNodeWithText("PDF").assertIsDisplayed()

        onNodeWithContentDescription("Preview").assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 3_000) {
            requests.any { it == "/api/fs/download|/opt/data/shared/outputs/weekly/weekly_report.pdf|ops|sess-ops" }
        }
        onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
        waitForIdle()
    }

    @Test
    fun dispatchSidebarRoomMessagesUseLineProfileAndStripGeneratedEchoes() = runDesktopComposeUiTest(width = 1280, height = 800) {
        val today = epochDayFromCivilIso("2026-11-18")!!
        val proj = DirectusProject(id = "p1", name = "Ops", sort = 1)
        val roomChannel = TaskChannel(type = TaskChannel.TYPE_ROOM, id = "task-room", label = "Room")
        val task = DirectusTask(
            id = "t-room",
            title = "Review room outputs",
            status = DirectusTask.STATUS_IN_PROGRESS,
            priority = "medium",
            context = TaskContext(
                taskId = "t-room",
                projectId = "p1",
                impact = "high",
                urgency = "medium",
                channels = listOf(roomChannel),
            ),
        )
        val requests = mutableListOf<String>()
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val mediaApi = MediaApi(createHttpClient(MockEngine { request ->
            val key = "${request.url.encodedPath}|${request.url.parameters["path"]}|${request.url.parameters["profile"]}|${request.url.parameters["session_id"]}"
            requests += key
            when (request.url.encodedPath) {
                "/api/fs/read-text" -> respond(
                    """{"binary":false,"byteSize":12,"language":"markdown","text":"# Room Notes","truncated":false}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                else -> respond("Not found", HttpStatusCode.NotFound)
            }
        }))
        val state = TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true),
            projects = listOf(proj),
            tasks = listOf(task),
            selectedTaskId = "t-room",
            todayEpochDay = today,
            showPriorityStrip = false,
            channelMessages = listOf(
                ChannelChatLine(
                    key = "r-1",
                    speaker = "bot-a",
                    text = "See summary:\nMEDIA:/opt/data/summary.md",
                    fromUser = false,
                    profile = "bot-a",
                ),
                ChannelChatLine(
                    key = "r-2",
                    speaker = "bot-b",
                    text = "Saved chart at /sandbox/chart.png.",
                    fromUser = false,
                    profile = "bot-b",
                    generatedImages = listOf(
                        GeneratedImage("/opt/data/chart.png", listOf("/opt/data/chart.png", "/sandbox/chart.png")),
                    ),
                ),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        ProvideChannelMedia(
                            gateway = GatewayUrl.parse("https://hermes.example"),
                            activeChannel = roomChannel,
                            mediaApi = mediaApi,
                        ) {
                            TasksBoardContent(
                                state = state,
                                actions = TasksBoardActions(),
                                onBack = {},
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        assertTrue(onAllNodesWithText("/sandbox/chart.png", substring = true).fetchSemanticsNodes().isEmpty())
        waitUntil(timeoutMillis = 3_000) {
            requests.any { it == "/api/fs/download|/opt/data/chart.png|bot-b|null" }
        }

        onNodeWithText("summary.md").assertIsDisplayed()
        onNodeWithContentDescription("Preview").assertIsDisplayed().performClick()
        waitUntil(timeoutMillis = 3_000) {
            requests.any { it == "/api/fs/read-text|/opt/data/summary.md|bot-a|null" } &&
                onAllNodesWithText("Room Notes").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Room Notes").assertIsDisplayed()
    }
}
