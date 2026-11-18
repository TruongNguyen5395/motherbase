package dev.hermeskotlin.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.background
import dev.hermeskotlin.designsystem.body
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.code
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Chip
import dev.hermeskotlin.designsystem.components.Dialog
import dev.hermeskotlin.designsystem.components.DropdownMenu
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.MenuChoice
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.Switch
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.components.verticalScrollWithBar
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.eyebrow
import dev.hermeskotlin.designsystem.heading
import dev.hermeskotlin.designsystem.label as labelStyle
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusFull
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.radiusSmall
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.success
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.surface3
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.designsystem.well
import dev.hermeskotlin.ui.PlatformBackHandler
import dev.hermeskotlin.ui.chat.DEFAULT_RAIL_WIDTH
import dev.hermeskotlin.ui.chat.ParameterizedActionContent
import dev.hermeskotlin.ui.chat.RightRailLayout
import dev.hermeskotlin.ui.chat.rememberRightRailState
import dev.hermeskotlin.ui.components.EmptyState
import dev.hermeskotlin.ui.components.ListSkeleton
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.resources.*
import dev.hermeskotlin.ui.sessions.SubpageHeader
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal data class TasksBoardActions(
    val onRefresh: () -> Unit = {},
    val onSaveConfig: (url: String, token: String) -> Unit = { _, _ -> },
    val onTogglePriorityStrip: (Boolean) -> Unit = {},
    val onToggleIncludeDone: () -> Unit = {},
    val onSelectTask: (String?) -> Unit = {},
    val onSelectChannel: (Int) -> Unit = {},
    val onRefreshChannel: () -> Unit = {},
    val onCreateProject: (name: String, hermesProjectId: String?, workspaceCwd: String?, defaultBots: List<String>) -> Unit = { _, _, _, _ -> },
    val onCreateTask: (title: String, description: String?, projectId: String?, priority: String, assignedBots: List<String>) -> Unit = { _, _, _, _, _ -> },
    val onAssignProject: (taskId: String, projectId: String?) -> Unit = { _, _ -> },
    val onUpdateStatus: (taskId: String, status: String) -> Unit = { _, _ -> },
    val onCreateSessionChannel: (DirectusTask, profile: String?) -> Unit = { _, _ -> },
    val onCreateRoomChannel: (DirectusTask, members: List<Bot>) -> Unit = { _, _ -> },
    val onSendToChannel: (DirectusTask, text: String) -> Unit = { _, _ -> },
    val onPreparePlan: (DirectusTask) -> Unit = {},
    val onApprovePlan: (DirectusTask, ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit = { _, _, _, _ -> },
    val onOpenFullscreen: (DirectusTask, TaskChannel) -> Unit = { _, _ -> },
    val onDismissFeedback: () -> Unit = {},
)

private val BENTO_TWO_COL_THRESHOLD = 620.dp
private val PRIORITY_OPTIONS = listOf("low", "medium", "high", "urgent")
private val STATUS_OPTIONS = listOf(
    DirectusTask.STATUS_TODO,
    DirectusTask.STATUS_IN_PROGRESS,
    DirectusTask.STATUS_BLOCKED,
    DirectusTask.STATUS_DONE,
)

/**
 * Directus Tasks Command Board: Priority strip toggle + explicit Project Bento boxes + Right-Rail detail
 * inspector with dual Room/Session channels, live chat log, and parameterized action cards.
 */
@Composable
internal fun TasksPage(
    gateway: SavedGateway,
    visible: Boolean,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onOpenSession: (sessionId: String, title: String, profile: String?) -> Unit = { _, _, _ -> },
    onOpenRoom: (Room) -> Unit = {},
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(gateway) { viewModel.bind(gateway) }
    LaunchedEffect(visible) { if (visible && state.tasks.isEmpty() && !state.loading) viewModel.refresh() }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.consumeSessionExpired()
            onSessionExpired()
        }
    }
    LaunchedEffect(state.actionFeedback) {
        if (state.actionFeedback != null) {
            delay(4_000)
            viewModel.clearFeedback()
        }
    }

    val openSession by rememberUpdatedState(onOpenSession)
    val openRoom by rememberUpdatedState(onOpenRoom)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TasksNavEvent.OpenSession -> openSession(event.sessionId, event.title, event.profile)
                is TasksNavEvent.OpenRoom -> openRoom(event.room)
            }
        }
    }

    val actions = remember(viewModel) {
        TasksBoardActions(
            onRefresh = viewModel::refresh,
            onSaveConfig = viewModel::saveConfig,
            onTogglePriorityStrip = viewModel::togglePriorityStrip,
            onToggleIncludeDone = viewModel::toggleIncludeDone,
            onSelectTask = viewModel::selectTask,
            onSelectChannel = viewModel::selectChannel,
            onRefreshChannel = viewModel::refreshActiveChannel,
            onCreateProject = viewModel::createProject,
            onCreateTask = viewModel::createTask,
            onAssignProject = viewModel::assignProject,
            onUpdateStatus = viewModel::updateTaskStatus,
            onCreateSessionChannel = viewModel::openOrCreateSessionChannel,
            onCreateRoomChannel = viewModel::openOrCreateRoomChannel,
            onSendToChannel = viewModel::sendToActiveChannel,
            onPreparePlan = viewModel::preparePlan,
            onApprovePlan = viewModel::approveStagedPlan,
            onOpenFullscreen = viewModel::openChannelFullscreen,
            onDismissFeedback = viewModel::clearFeedback,
        )
    }

    TasksBoardContent(state = state, actions = actions, onBack = onBack)
}

/** Stateless Command Board UI shared between live [TasksPage], Compose previews, and UI tests. */
@Composable
internal fun TasksBoardContent(
    state: TasksUiState,
    actions: TasksBoardActions,
    onBack: () -> Unit,
) {
    val railState = rememberRightRailState()
    var railTrigger by remember { mutableIntStateOf(0) }
    var setupDialogOpen by remember { mutableStateOf(false) }
    var newProjectOpen by remember { mutableStateOf(false) }
    var newTaskOpen by remember { mutableStateOf(false) }
    var pickSessionFor by remember { mutableStateOf<DirectusTask?>(null) }
    var pickRoomFor by remember { mutableStateOf<DirectusTask?>(null) }

    LaunchedEffect(state.selectedTaskId, railTrigger) {
        if (state.selectedTaskId != null) railState.open() else railState.close()
    }

    val selectAndOpen: (String?) -> Unit = { id ->
        if (id != null) railTrigger++
        actions.onSelectTask(id)
    }

    PlatformBackHandler(enabled = state.selectedTaskId != null && railState.isOpen) {
        actions.onSelectTask(null)
    }

    val selected = state.selectedTask
    val configured = state.config?.configured == true

    RightRailLayout(
        state = railState,
        railWidth = DEFAULT_RAIL_WIDTH,
        swipeEnabled = !isDesktop && selected != null,
        rail = {
            if (selected != null) {
                TaskDetailInspector(
                    task = selected,
                    projects = state.projects,
                    busy = selected.id in state.busyTaskIds,
                    activeChannelIdx = state.activeChannelIdx,
                    activeChannel = state.activeChannel,
                    channelLoading = state.channelLoading,
                    channelMessages = state.channelMessages,
                    channelError = state.channelError,
                    sendingToChannel = state.sendingToChannel,
                    onClose = { actions.onSelectTask(null) },
                    onAssignProject = { pid -> actions.onAssignProject(selected.id, pid) },
                    onUpdateStatus = { status -> actions.onUpdateStatus(selected.id, status) },
                    onSelectChannel = actions.onSelectChannel,
                    onRefreshChannel = actions.onRefreshChannel,
                    onOpenPickSession = { pickSessionFor = selected },
                    onOpenPickRoom = { pickRoomFor = selected },
                    onOpenFullscreen = { ch -> actions.onOpenFullscreen(selected, ch) },
                    onPreparePlan = { actions.onPreparePlan(selected) },
                    onApprovePlan = { opt, selectedParams, tweaks ->
                        actions.onApprovePlan(selected, opt, selectedParams, tweaks)
                    },
                    onSendToChannel = { text -> actions.onSendToChannel(selected, text) },
                )
            }
        },
    ) { contentModifier ->
        Column(contentModifier.fillMaxSize()) {
            SubpageHeader(
                title = stringResource(Res.string.tasks_title),
                subtitle = stringResource(Res.string.tasks_subtitle),
                onBack = onBack,
                actions = {
                    if (configured) {
                        IconButton(
                            Lucide.Plus,
                            contentDescription = stringResource(Res.string.tasks_new_task),
                            onClick = { newTaskOpen = true },
                        )
                        IconButton(
                            Lucide.FolderPlus,
                            contentDescription = stringResource(Res.string.tasks_new_project),
                            onClick = { newProjectOpen = true },
                        )
                    }
                    IconButton(
                        Lucide.RefreshCw,
                        contentDescription = stringResource(Res.string.app_try_again),
                        onClick = actions.onRefresh,
                    )
                    IconButton(
                        Lucide.Settings2,
                        contentDescription = stringResource(Res.string.tasks_setup_directus),
                        onClick = { setupDialogOpen = true },
                    )
                },
            )

            if (configured) {
                BoardControlsBar(
                    showPriorityStrip = state.showPriorityStrip,
                    includeDone = state.includeDone,
                    onTogglePriorityStrip = actions.onTogglePriorityStrip,
                    onToggleIncludeDone = actions.onToggleIncludeDone,
                )
            }

            state.actionFeedback?.let { msg ->
                FeedbackBanner(message = msg, onDismiss = actions.onDismissFeedback)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.tasks.isEmpty() && !configured -> ListSkeleton()
                    state.pluginMissing -> EmptyState(
                        icon = Lucide.TriangleAlert,
                        title = stringResource(Res.string.tasks_plugin_missing),
                        body = state.error ?: stringResource(Res.string.tasks_setup_hint),
                        error = true,
                        action = {
                            Button(
                                text = stringResource(Res.string.app_try_again),
                                onClick = actions.onRefresh,
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.RefreshCw,
                            )
                        },
                    )
                    state.config != null && !configured -> DirectusSetupPanel(
                        config = state.config,
                        saving = state.savingConfig,
                        error = state.configError,
                        onSave = actions.onSaveConfig,
                    )
                    state.error != null && state.tasks.isEmpty() -> EmptyState(
                        icon = Lucide.TriangleAlert,
                        title = stringResource(Res.string.tasks_error_load),
                        body = state.error,
                        error = true,
                        action = {
                            Button(
                                text = stringResource(Res.string.app_try_again),
                                onClick = actions.onRefresh,
                                variant = ButtonVariant.Secondary,
                                leadingIcon = Lucide.RefreshCw,
                            )
                        },
                    )
                    else -> BoardScrollGrid(
                        state = state,
                        onSelectTask = selectAndOpen,
                        onNewTask = { newTaskOpen = true },
                    )
                }
            }
        }
    }

    if (setupDialogOpen) {
        DirectusSetupDialog(
            config = state.config,
            saving = state.savingConfig,
            error = state.configError,
            onDismiss = { setupDialogOpen = false },
            onSave = { url, token ->
                actions.onSaveConfig(url, token)
                setupDialogOpen = false
            },
        )
    }

    if (newProjectOpen) {
        NewProjectDialog(
            hermesProjects = state.hermesProjects,
            bots = state.bots,
            onDismiss = { newProjectOpen = false },
            onCreate = { name, hermesId, cwd, defaultBots ->
                actions.onCreateProject(name, hermesId, cwd, defaultBots)
                newProjectOpen = false
            },
        )
    }

    if (newTaskOpen) {
        NewTaskDialog(
            projects = state.projects,
            bots = state.bots,
            onDismiss = { newTaskOpen = false },
            onCreate = { title, desc, projectId, priority, assigned ->
                actions.onCreateTask(title, desc, projectId, priority, assigned)
                newTaskOpen = false
            },
        )
    }

    pickSessionFor?.let { task ->
        PickSessionBotDialog(
            bots = state.bots,
            onDismiss = { pickSessionFor = null },
            onPick = { profile ->
                actions.onCreateSessionChannel(task, profile)
                pickSessionFor = null
            },
        )
    }

    pickRoomFor?.let { task ->
        PickRoomBotsDialog(
            bots = state.bots,
            onDismiss = { pickRoomFor = null },
            onCreate = { members ->
                actions.onCreateRoomChannel(task, members)
                pickRoomFor = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardControlsBar(
    showPriorityStrip: Boolean,
    includeDone: Boolean,
    onTogglePriorityStrip: (Boolean) -> Unit,
    onToggleIncludeDone: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], RoundedCornerShape(Theme[radii][radiusMedium]))
                .toggleable(value = showPriorityStrip, role = Role.Switch, onValueChange = onTogglePriorityStrip)
                .handCursor()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_priority_toggle),
                style = Theme[typography][labelStyle],
                color = Theme[colors][textColor],
            )
            Switch(checked = showPriorityStrip)
        }

        Chip(
            text = stringResource(if (includeDone) Res.string.tasks_hide_done else Res.string.tasks_show_done),
            selected = includeDone,
            onClick = onToggleIncludeDone,
        )
    }
}

@Composable
private fun FeedbackBanner(message: String, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(Theme[colors][surface2])
            .border(1.dp, Theme[colors][strokeStrong], shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = Theme[typography][bodySmall],
            color = Theme[colors][textColor],
            modifier = Modifier.weight(1f),
        )
        IconButton(Lucide.X, contentDescription = stringResource(Res.string.tasks_close_inspector), onClick = onDismiss)
    }
}

@Composable
private fun DirectusSetupPanel(
    config: dev.hermeskotlin.core.tasks.DirectusConfigStatus?,
    saving: Boolean,
    error: String?,
    onSave: (url: String, token: String) -> Unit,
) {
    val urlState = rememberTextFieldState(config?.url.orEmpty())
    val tokenState = rememberTextFieldState("")
    Box(
        Modifier.fillMaxSize().verticalScrollWithBar().padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val shape = RoundedCornerShape(Theme[radii][radiusLarge])
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.tasks_setup_directus),
                style = Theme[typography][heading],
                color = Theme[colors][textColor],
            )
            Text(
                stringResource(Res.string.tasks_setup_hint),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
            TextField(
                state = urlState,
                label = stringResource(Res.string.tasks_directus_url),
                placeholder = stringResource(Res.string.tasks_directus_url_hint),
            )
            TextField(
                state = tokenState,
                label = stringResource(Res.string.tasks_directus_token),
                placeholder = config?.tokenPreview?.takeIf { it.isNotBlank() }?.let {
                    stringResource(Res.string.tasks_directus_token_keep, it)
                },
                password = true,
            )
            if (error != null) {
                Text(error, style = Theme[typography][bodySmall], color = Theme[colors][danger])
            }
            val urlText = urlState.text.toString().trim()
            val canSave = !saving && urlText.isNotBlank() && (tokenState.text.toString().trim().isNotBlank() || config?.tokenSet == true)
            Button(
                text = stringResource(if (saving) Res.string.tasks_testing else Res.string.tasks_save_connect),
                onClick = { onSave(urlText, tokenState.text.toString()) },
                enabled = canSave,
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoardScrollGrid(
    state: TasksUiState,
    onSelectTask: (String?) -> Unit,
    onNewTask: () -> Unit,
) {
    val activeProjects = remember(state.projects) { state.projects.filterNot { it.archived } }
    val projectById = remember(activeProjects) { activeProjects.associateBy { it.id } }
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)

    val projectBuckets = remember(activeProjects, state.tasks) {
        val grouped = state.tasks.groupBy { task -> task.projectId?.takeIf { it in projectById } }
        buildList<Pair<DirectusProject?, List<DirectusTask>>> {
            activeProjects.forEach { project ->
                add(project to grouped[project.id].orEmpty())
            }
            val unassigned = grouped[null].orEmpty()
            if (unassigned.isNotEmpty() || activeProjects.isEmpty()) {
                add(null to unassigned)
            }
        }
    }

    if (state.tasks.isEmpty() && activeProjects.isEmpty()) {
        EmptyState(
            icon = Lucide.ListTodo,
            title = stringResource(Res.string.tasks_empty_board),
            body = stringResource(Res.string.tasks_empty_board_detail),
            action = {
                Button(
                    text = stringResource(Res.string.tasks_new_task),
                    onClick = onNewTask,
                    leadingIcon = Lucide.Plus,
                )
            },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= BENTO_TWO_COL_THRESHOLD) 2 else 1
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.showPriorityStrip) {
                item(key = "priority-strip") {
                    PriorityStripSection(
                        tasks = state.priorityTasks,
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        onSelectTask = onSelectTask,
                    )
                }
            }

            if (columns == 2) {
                val rows = projectBuckets.chunked(2)
                items(rows.size, key = { idx -> rows[idx].joinToString("-") { it.first?.id ?: "unassigned" } }) { idx ->
                    val pair = rows[idx]
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { (project, bucketTasks) ->
                            ProjectBentoCard(
                                project = project,
                                projectLabel = project?.name ?: unassignedLabel,
                                tasks = bucketTasks,
                                selectedTaskId = state.selectedTaskId,
                                busyTaskIds = state.busyTaskIds,
                                onSelectTask = onSelectTask,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(projectBuckets.size, key = { idx -> projectBuckets[idx].first?.id ?: "unassigned" }) { idx ->
                    val (project, bucketTasks) = projectBuckets[idx]
                    ProjectBentoCard(
                        project = project,
                        projectLabel = project?.name ?: unassignedLabel,
                        tasks = bucketTasks,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        onSelectTask = onSelectTask,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriorityStripSection(
    tasks: List<DirectusTask>,
    projectById: Map<String, DirectusProject>,
    unassignedLabel: String,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][warningSoft].copy(alpha = 0.55f))
            .border(1.dp, Theme[colors][warning].copy(alpha = 0.4f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.Sparkles, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(16.dp))
            Text(
                stringResource(Res.string.tasks_priority_section, tasks.size).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textColor],
            )
        }
        if (tasks.isEmpty()) {
            Text(
                stringResource(Res.string.tasks_empty_priority),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tasks.forEach { task ->
                    val projectName = task.projectId?.let { projectById[it]?.name } ?: unassignedLabel
                    TaskPill(
                        task = task,
                        projectName = projectName,
                        selected = task.id == selectedTaskId,
                        busy = task.id in busyTaskIds,
                        onClick = { onSelectTask(task.id) },
                        modifier = Modifier.widthIn(min = 240.dp, max = 380.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectBentoCard(
    project: DirectusProject?,
    projectLabel: String,
    tasks: List<DirectusTask>,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val dotColor = project?.color?.let(::parseHexColorOrNull) ?: Theme[colors][accent]
    Column(
        modifier
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke], shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Text(
                projectLabel,
                style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.tasks_project_count, tasks.size),
                style = Theme[typography][code].copy(fontSize = 11.sp),
                color = Theme[colors][textTertiary],
            )
        }

        if (!project?.workspaceCwd.isNullOrBlank()) {
            Text(
                project?.workspaceCwd.orEmpty(),
                style = Theme[typography][code].copy(fontSize = 11.sp),
                color = Theme[colors][textMuted],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        tasks.forEach { task ->
            TaskPill(
                task = task,
                projectName = projectLabel,
                selected = task.id == selectedTaskId,
                busy = task.id in busyTaskIds,
                onClick = { onSelectTask(task.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskPill(
    task: DirectusTask,
    projectName: String,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val fill = if (selected) Theme[colors][accentSoft] else Theme[colors][surface2]
    val borderColor = when {
        selected -> Theme[colors][accent]
        task.agentState == TaskContext.STATE_NEEDS_YOU -> Theme[colors][warning].copy(alpha = 0.6f)
        else -> Theme[colors][stroke]
    }
    val dotColor = stateDotColor(task)

    Column(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                Spinner(size = 12.dp)
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            }
            Text(
                task.title,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            MiniBadge(
                text = projectName,
                backgroundColor = Theme[colors][surface3],
                contentColor = Theme[colors][textSecondary],
            )
            MiniBadge(
                text = taskStatusLabel(task.status),
                backgroundColor = Theme[colors][surface],
                contentColor = Theme[colors][textTertiary],
            )
            if (task.agentState != TaskContext.STATE_IDLE) {
                MiniBadge(
                    text = agentStateLabel(task.agentState),
                    backgroundColor = if (task.agentState == TaskContext.STATE_NEEDS_YOU) Theme[colors][warningSoft] else Theme[colors][accentSoft],
                    contentColor = if (task.agentState == TaskContext.STATE_NEEDS_YOU) Theme[colors][warning] else Theme[colors][accentText],
                )
            }
            task.assignedBots.take(3).forEach { bot ->
                MiniBadge(
                    text = "@$bot",
                    backgroundColor = Theme[colors][well],
                    contentColor = Theme[colors][textTertiary],
                )
            }
        }
    }
}

@Composable
private fun MiniBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
) {
    Text(
        text,
        style = Theme[typography][caption].copy(fontSize = 11.sp),
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(Theme[radii][radiusFull]))
            .background(backgroundColor)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun TaskDetailInspector(
    task: DirectusTask,
    projects: List<DirectusProject>,
    busy: Boolean,
    activeChannelIdx: Int,
    activeChannel: TaskChannel?,
    channelLoading: Boolean,
    channelMessages: List<ChannelChatLine>,
    channelError: String?,
    sendingToChannel: Boolean,
    onClose: () -> Unit,
    onAssignProject: (String?) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onRefreshChannel: () -> Unit,
    onOpenPickSession: () -> Unit,
    onOpenPickRoom: () -> Unit,
    onOpenFullscreen: (TaskChannel) -> Unit,
    onPreparePlan: () -> Unit,
    onApprovePlan: (ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit,
    onSendToChannel: (String) -> Unit,
) {
    val steerState = rememberTextFieldState("")
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)
    val currentProjectLabel = projects.firstOrNull { it.id == task.projectId }?.name ?: unassignedLabel

    Column(
        Modifier
            .fillMaxSize()
            .background(Theme[colors][background]),
    ) {
        // Pinned inspector header.
        Row(
            Modifier
                .fillMaxWidth()
                .border(0.dp, Color.Transparent)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                task.title,
                style = Theme[typography][heading],
                color = Theme[colors][textColor],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                Lucide.X,
                contentDescription = stringResource(Res.string.tasks_close_inspector),
                onClick = onClose,
            )
        }

        // Scrollable detail body (explicit Project/Status selectors, Linked Channels, Live Chat Log, Action Proposal).
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollWithBar()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!task.description.isNullOrBlank()) {
                Text(
                    task.description.orEmpty(),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textSecondary],
                )
            }

            TaskMetadataPickers(
                currentProjectId = task.projectId,
                currentProjectLabel = currentProjectLabel,
                projects = projects,
                unassignedLabel = unassignedLabel,
                currentStatus = task.status,
                onAssignProject = onAssignProject,
                onUpdateStatus = onUpdateStatus,
            )

            TaskChannelsSection(
                channels = task.channels,
                activeIdx = activeChannelIdx,
                activeChannel = activeChannel,
                busy = busy,
                onSelectChannel = onSelectChannel,
                onOpenPickSession = onOpenPickSession,
                onOpenPickRoom = onOpenPickRoom,
                onOpenFullscreen = onOpenFullscreen,
            )

            TaskChannelLog(
                activeChannel = activeChannel,
                loading = channelLoading,
                messages = channelMessages,
                error = channelError,
                onRefresh = onRefreshChannel,
            )

            TaskStagedPlanSection(
                task = task,
                busy = busy,
                onPreparePlan = onPreparePlan,
                onApprovePlan = onApprovePlan,
            )
        }

        // Pinned quick steer / @mention bar.
        QuickSteerBar(
            state = steerState,
            sending = sendingToChannel,
            onSend = {
                val txt = steerState.text.toString().trim()
                if (txt.isNotEmpty()) {
                    onSendToChannel(txt)
                    steerState.clearText()
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMetadataPickers(
    currentProjectId: String?,
    currentProjectLabel: String,
    projects: List<DirectusProject>,
    unassignedLabel: String,
    currentStatus: String,
    onAssignProject: (String?) -> Unit,
    onUpdateStatus: (String) -> Unit,
) {
    var projectMenuOpen by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownMenu(
            expanded = projectMenuOpen,
            onExpandedChange = { projectMenuOpen = it },
            items = {
                MenuChoice(
                    text = unassignedLabel,
                    icon = Lucide.FolderX,
                    selected = currentProjectId == null,
                    onClick = {
                        projectMenuOpen = false
                        onAssignProject(null)
                    },
                )
                projects.filterNot { it.archived }.forEach { proj ->
                    MenuChoice(
                        text = proj.name,
                        icon = Lucide.Folder,
                        selected = proj.id == currentProjectId,
                        onClick = {
                            projectMenuOpen = false
                            onAssignProject(proj.id)
                        },
                    )
                }
            },
        ) {
            PickerPill(
                icon = Lucide.Folder,
                label = currentProjectLabel,
                onClick = { projectMenuOpen = true },
            )
        }

        DropdownMenu(
            expanded = statusMenuOpen,
            onExpandedChange = { statusMenuOpen = it },
            items = {
                STATUS_OPTIONS.forEach { st ->
                    MenuChoice(
                        text = taskStatusLabel(st),
                        icon = Lucide.ListChecks,
                        selected = st == currentStatus,
                        onClick = {
                            statusMenuOpen = false
                            onUpdateStatus(st)
                        },
                    )
                }
            },
        ) {
            PickerPill(
                icon = Lucide.CircleDot,
                label = taskStatusLabel(currentStatus),
                onClick = { statusMenuOpen = true },
            )
        }
    }
}

@Composable
private fun PickerPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    Row(
        Modifier
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][strokeStrong], shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(icon, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(14.dp))
        Text(label, style = Theme[typography][bodySmall], color = Theme[colors][textColor], maxLines = 1, overflow = TextOverflow.Ellipsis)
        UnstyledIcon(Lucide.ChevronDown, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(14.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskChannelsSection(
    channels: List<TaskChannel>,
    activeIdx: Int,
    activeChannel: TaskChannel?,
    busy: Boolean,
    onSelectChannel: (Int) -> Unit,
    onOpenPickSession: () -> Unit,
    onOpenPickRoom: () -> Unit,
    onOpenFullscreen: (TaskChannel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_channel_heading).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textTertiary],
            )
            if (activeChannel != null) {
                Button(
                    text = stringResource(Res.string.tasks_open_fullscreen),
                    onClick = { onOpenFullscreen(activeChannel) },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Small,
                    leadingIcon = Lucide.ExternalLink,
                )
            }
        }

        if (channels.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                channels.forEachIndexed { idx, ch ->
                    val baseLabel = ch.label.ifBlank { if (ch.isRoom) "Room" else "Session" }
                    val displayLabel = if (ch.isRoom && !baseLabel.startsWith("Room", ignoreCase = true)) {
                        "Room · $baseLabel"
                    } else {
                        baseLabel
                    }
                    Chip(
                        text = displayLabel,
                        selected = idx == activeIdx,
                        onClick = { onSelectChannel(idx) },
                    )
                }
            }
        } else {
            Text(
                stringResource(Res.string.tasks_no_channel),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
        }

        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                text = stringResource(Res.string.tasks_add_session),
                onClick = onOpenPickSession,
                enabled = !busy,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                leadingIcon = Lucide.MessageSquare,
            )
            Button(
                text = stringResource(Res.string.tasks_add_room),
                onClick = onOpenPickRoom,
                enabled = !busy,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                leadingIcon = Lucide.Users,
            )
        }
    }
}

@Composable
private fun TaskChannelLog(
    activeChannel: TaskChannel?,
    loading: Boolean,
    messages: List<ChannelChatLine>,
    error: String?,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val scrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_chat_log).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textTertiary],
            )
            if (activeChannel != null) {
                IconButton(
                    Lucide.RefreshCw,
                    contentDescription = stringResource(Res.string.tasks_chat_log),
                    onClick = onRefresh,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 220.dp)
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], shape)
                .verticalScroll(scrollState)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                activeChannel == null -> Text(
                    stringResource(Res.string.tasks_no_channel),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textMuted],
                )
                loading && messages.isEmpty() -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner(size = 14.dp)
                    Text(stringResource(Res.string.tasks_chat_log), style = Theme[typography][bodySmall], color = Theme[colors][textTertiary])
                }
                error != null && messages.isEmpty() -> Text(
                    error,
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][danger],
                )
                messages.isEmpty() -> Text(
                    stringResource(Res.string.tasks_chat_empty),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textTertiary],
                )
                else -> messages.forEach { line ->
                    val bubbleShape = RoundedCornerShape(Theme[radii][radiusSmall])
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(bubbleShape)
                            .background(if (line.fromUser) Theme[colors][accentSoft] else Theme[colors][surface2])
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!line.fromUser && !line.speaker.isNullOrBlank()) {
                            Text(
                                "@${line.speaker}",
                                style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                                color = Theme[colors][accentText],
                            )
                        }
                        Text(
                            line.text,
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][textColor],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskStagedPlanSection(
    task: DirectusTask,
    busy: Boolean,
    onPreparePlan: () -> Unit,
    onApprovePlan: (ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val plan = task.stagedPlan
    val approveFallback = stringResource(Res.string.tasks_approve_action)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke], shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_plan_heading).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textTertiary],
            )
            Button(
                text = stringResource(if (plan == null) Res.string.tasks_prepare_plan else Res.string.tasks_refresh_plan),
                onClick = onPreparePlan,
                enabled = !busy,
                loading = busy,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                leadingIcon = Lucide.Sparkles,
            )
        }

        if (plan == null) {
            Text(
                stringResource(Res.string.tasks_no_plan),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
        } else {
            val params = remember(plan.params) {
                plan.params.map { ReplyInteraction.Param(key = it.key, label = it.label, options = it.options, defaultValue = it.default) }
            }
            val options = remember(plan.actions, approveFallback) {
                if (plan.actions.isNotEmpty()) {
                    plan.actions.map { act ->
                        ReplyInteraction.Option(
                            label = act.label,
                            description = act.description,
                            value = act.instruction.ifBlank { act.label },
                        )
                    }
                } else {
                    listOf(ReplyInteraction.Option(label = approveFallback, value = approveFallback))
                }
            }
            if (!plan.summary.isBlank() && plan.question != null && plan.question != plan.summary) {
                Text(plan.summary, style = Theme[typography][bodySmall], color = Theme[colors][textSecondary])
            }
            ParameterizedActionContent(
                cardKey = "task-${task.id}-${plan.hashCode()}",
                question = plan.question ?: plan.summary.takeIf { it.isNotBlank() },
                params = params,
                options = options,
                sendEnabled = !busy,
                onAction = onApprovePlan,
            )
        }
    }
}

@Composable
private fun QuickSteerBar(
    state: TextFieldState,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(0.dp))
            .background(Theme[colors][surface])
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            state = state,
            placeholder = stringResource(Res.string.tasks_quick_steer_hint),
            singleLine = true,
            enabled = !sending,
            onKeyboardAction = { onSend() },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            Lucide.Send,
            contentDescription = stringResource(Res.string.tasks_send),
            onClick = onSend,
            enabled = !sending && state.text.toString().isNotBlank(),
            tint = Theme[colors][onAccent],
            containerColor = Theme[colors][accent],
        )
    }
}

@Composable
private fun DirectusSetupDialog(
    config: dev.hermeskotlin.core.tasks.DirectusConfigStatus?,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (url: String, token: String) -> Unit,
) {
    val urlState = rememberTextFieldState(config?.url.orEmpty())
    val tokenState = rememberTextFieldState("")
    val urlText = urlState.text.toString().trim()
    val canSave = !saving && urlText.isNotBlank() && (tokenState.text.toString().trim().isNotBlank() || config?.tokenSet == true)
    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_setup_directus),
        message = stringResource(Res.string.tasks_setup_hint),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
            Button(
                stringResource(if (saving) Res.string.tasks_testing else Res.string.tasks_save_connect),
                onClick = { onSave(urlText, tokenState.text.toString()) },
                enabled = canSave,
                loading = saving,
            )
        },
    ) {
        TextField(
            state = urlState,
            label = stringResource(Res.string.tasks_directus_url),
            placeholder = stringResource(Res.string.tasks_directus_url_hint),
        )
        TextField(
            state = tokenState,
            label = stringResource(Res.string.tasks_directus_token),
            placeholder = config?.tokenPreview?.takeIf { it.isNotBlank() }?.let {
                stringResource(Res.string.tasks_directus_token_keep, it)
            },
            password = true,
        )
        if (error != null) {
            Text(error, style = Theme[typography][bodySmall], color = Theme[colors][danger])
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewProjectDialog(
    hermesProjects: List<Project>,
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onCreate: (name: String, hermesProjectId: String?, workspaceCwd: String?, defaultBots: List<String>) -> Unit,
) {
    val nameState = rememberTextFieldState("")
    val folderState = rememberTextFieldState("")
    var pickedHermesId by remember { mutableStateOf<String?>(null) }
    val selectedBots = remember { mutableStateListOf<String>() }
    val selectableHermes = remember(hermesProjects) { hermesProjects.filterNot { it.isNoProject || it.isAuto } }
    val visibleBots = remember(bots) { bots.filterNot { it.meta.hidden } }

    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_new_project),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
            Button(
                stringResource(Res.string.rooms_create),
                onClick = {
                    onCreate(
                        nameState.text.toString().trim(),
                        pickedHermesId,
                        folderState.text.toString().trim().ifEmpty { null },
                        selectedBots.toList(),
                    )
                },
                enabled = nameState.text.toString().isNotBlank(),
            )
        },
    ) {
        TextField(
            state = nameState,
            label = stringResource(Res.string.tasks_project_name),
            autofocus = true,
        )
        TextField(
            state = folderState,
            label = stringResource(Res.string.tasks_project_folder),
        )
        if (selectableHermes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selectableHermes.forEach { proj ->
                    Chip(
                        text = proj.label,
                        selected = pickedHermesId == proj.id,
                        onClick = {
                            if (pickedHermesId == proj.id) {
                                pickedHermesId = null
                            } else {
                                pickedHermesId = proj.id
                                if (folderState.text.toString().isBlank() && !proj.path.isNullOrBlank()) {
                                    folderState.setTextAndPlaceCursorAtEnd(proj.path.orEmpty())
                                }
                            }
                        },
                    )
                }
            }
        }
        if (visibleBots.isNotEmpty()) {
            Text(stringResource(Res.string.tasks_project_bots), style = Theme[typography][labelStyle], color = Theme[colors][textSecondary])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleBots.forEach { bot ->
                    val on = bot.name in selectedBots
                    Chip(
                        text = "@${bot.name}",
                        selected = on,
                        onClick = { if (on) selectedBots.remove(bot.name) else selectedBots.add(bot.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTaskDialog(
    projects: List<DirectusProject>,
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, projectId: String?, priority: String, assignedBots: List<String>) -> Unit,
) {
    val titleState = rememberTextFieldState("")
    val descState = rememberTextFieldState("")
    var pickedProjectId by remember { mutableStateOf<String?>(projects.firstOrNull { !it.archived }?.id) }
    var pickedPriority by remember { mutableStateOf("medium") }
    val selectedBots = remember { mutableStateListOf<String>() }
    val visibleBots = remember(bots) { bots.filterNot { it.meta.hidden } }

    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_new_task),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
            Button(
                stringResource(Res.string.rooms_create),
                onClick = {
                    onCreate(
                        titleState.text.toString().trim(),
                        descState.text.toString().trim().ifEmpty { null },
                        pickedProjectId,
                        pickedPriority,
                        selectedBots.toList(),
                    )
                },
                enabled = titleState.text.toString().isNotBlank(),
            )
        },
    ) {
        TextField(
            state = titleState,
            label = stringResource(Res.string.tasks_task_title),
            autofocus = true,
        )
        TextField(
            state = descState,
            label = stringResource(Res.string.tasks_task_description),
            singleLine = false,
            maxLines = 4,
        )
        Text(stringResource(Res.string.tasks_task_project), style = Theme[typography][labelStyle], color = Theme[colors][textSecondary])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(
                text = stringResource(Res.string.tasks_unassigned_project),
                selected = pickedProjectId == null,
                onClick = { pickedProjectId = null },
            )
            projects.filterNot { it.archived }.forEach { proj ->
                Chip(
                    text = proj.name,
                    selected = pickedProjectId == proj.id,
                    onClick = { pickedProjectId = proj.id },
                )
            }
        }
        Text(stringResource(Res.string.tasks_task_priority), style = Theme[typography][labelStyle], color = Theme[colors][textSecondary])
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PRIORITY_OPTIONS.forEach { p ->
                Chip(text = p, selected = pickedPriority == p, onClick = { pickedPriority = p })
            }
        }
        if (visibleBots.isNotEmpty()) {
            Text(stringResource(Res.string.tasks_project_bots), style = Theme[typography][labelStyle], color = Theme[colors][textSecondary])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleBots.forEach { bot ->
                    val on = bot.name in selectedBots
                    Chip(
                        text = "@${bot.name}",
                        selected = on,
                        onClick = { if (on) selectedBots.remove(bot.name) else selectedBots.add(bot.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickSessionBotDialog(
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onPick: (profile: String?) -> Unit,
) {
    val visibleBots = remember(bots) { bots.filterNot { it.meta.hidden } }
    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_pick_bot_title),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BotPickRow(
                label = stringResource(Res.string.tasks_default_bot),
                sub = null,
                onClick = { onPick(null) },
            )
            visibleBots.forEach { bot ->
                BotPickRow(
                    label = bot.label,
                    sub = "@${bot.name}",
                    onClick = { onPick(bot.name) },
                )
            }
        }
    }
}

@Composable
private fun BotPickRow(label: String, sub: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface2])
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(Lucide.Bot, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(16.dp))
        Text(label, style = Theme[typography][body], color = Theme[colors][textColor], modifier = Modifier.weight(1f))
        if (sub != null) {
            Text(sub, style = Theme[typography][code].copy(fontSize = 12.sp), color = Theme[colors][textTertiary])
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickRoomBotsDialog(
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onCreate: (List<Bot>) -> Unit,
) {
    val visibleBots = remember(bots) { bots.filterNot { it.meta.hidden } }
    val selected = remember { mutableStateListOf<Bot>() }
    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_pick_bots_title),
        message = stringResource(Res.string.tasks_room_needs_two),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
            Button(
                stringResource(Res.string.tasks_add_room),
                onClick = { onCreate(selected.toList()) },
                enabled = selected.size >= 2,
            )
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleBots.forEach { bot ->
                val on = selected.any { it.name == bot.name }
                Chip(
                    text = "${bot.label} (@${bot.name})",
                    selected = on,
                    onClick = {
                        if (on) selected.removeAll { it.name == bot.name } else selected.add(bot)
                    },
                )
            }
        }
    }
}

@Composable
private fun stateDotColor(task: DirectusTask): Color = when (task.agentState) {
    TaskContext.STATE_NEEDS_YOU -> Theme[colors][warning]
    TaskContext.STATE_RUNNING, TaskContext.STATE_QUEUED -> Theme[colors][accent]
    else -> if (task.status == DirectusTask.STATUS_DONE) Theme[colors][success] else Theme[colors][textTertiary]
}

private fun taskStatusLabel(status: String): String = when (status) {
    DirectusTask.STATUS_IN_PROGRESS -> "In progress"
    DirectusTask.STATUS_BLOCKED -> "Blocked"
    DirectusTask.STATUS_DONE -> "Done"
    else -> "Todo"
}

@Composable
private fun agentStateLabel(agentState: String): String = when (agentState) {
    TaskContext.STATE_NEEDS_YOU -> stringResource(Res.string.tasks_state_needs_you)
    TaskContext.STATE_RUNNING -> stringResource(Res.string.tasks_state_running)
    TaskContext.STATE_QUEUED -> stringResource(Res.string.tasks_state_queued)
    else -> stringResource(Res.string.tasks_state_idle)
}

private fun parseHexColorOrNull(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return null
    return when (clean.length) {
        6 -> Color((0xFF000000L or value).toInt())
        8 -> Color(value.toInt())
        else -> null
    }
}
