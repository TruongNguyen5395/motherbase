package dev.hermeskotlin.ui.tasks

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.PanelLeft
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.chat.ChatState
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.models.ModelOption
import dev.hermeskotlin.core.models.ReasoningEffort
import dev.hermeskotlin.core.pet.PetApi
import dev.hermeskotlin.core.pet.PetSprite
import dev.hermeskotlin.core.pet.PetState
import dev.hermeskotlin.core.pet.petStateOf
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.settings.AppSettings
import dev.hermeskotlin.core.settings.SettingsStore
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DispatchCoordinatorConfig
import dev.hermeskotlin.core.tasks.DispatchCoordinatorUpdate
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.core.voice.AudioApi
import dev.hermeskotlin.core.voice.DeviceDictation
import dev.hermeskotlin.core.voice.SpeechPlayer
import dev.hermeskotlin.core.voice.VoiceRecorder
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
import dev.hermeskotlin.designsystem.components.MarkdownText
import dev.hermeskotlin.designsystem.components.MenuChoice
import dev.hermeskotlin.designsystem.components.MinTouchTarget
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.Switch
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.glow
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.components.scrollbar
import dev.hermeskotlin.designsystem.components.verticalScrollWithBar
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.dangerSoft
import dev.hermeskotlin.designsystem.eyebrow
import dev.hermeskotlin.designsystem.heading
import dev.hermeskotlin.designsystem.label as labelStyle
import dev.hermeskotlin.designsystem.onUserBubble
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusFull
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.radiusSmall
import dev.hermeskotlin.designsystem.radiusXLarge
import dev.hermeskotlin.designsystem.sidebar as sidebarColor
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
import dev.hermeskotlin.designsystem.userBubble
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.designsystem.well
import dev.hermeskotlin.ui.PlatformBackHandler
import dev.hermeskotlin.ui.bots.BotAvatar
import dev.hermeskotlin.ui.chat.AttachButton
import dev.hermeskotlin.ui.chat.AttachSheet
import dev.hermeskotlin.ui.chat.AttachmentPicker
import dev.hermeskotlin.ui.chat.ComposerKey
import dev.hermeskotlin.ui.chat.ComposerTextInput
import dev.hermeskotlin.ui.chat.ComposerTray
import dev.hermeskotlin.ui.chat.DEFAULT_RAIL_WIDTH
import dev.hermeskotlin.ui.chat.DictationButton
import dev.hermeskotlin.ui.chat.DockShadow
import dev.hermeskotlin.ui.chat.InputRequestPanel
import dev.hermeskotlin.ui.chat.LocalModelPillBounds
import dev.hermeskotlin.ui.chat.ModelConfirmDialog
import dev.hermeskotlin.ui.chat.ModelPickerState
import dev.hermeskotlin.ui.chat.ModelPill
import dev.hermeskotlin.ui.chat.ModelSelection
import dev.hermeskotlin.ui.chat.ModelSheet
import dev.hermeskotlin.ui.chat.ParameterizedActionContent
import dev.hermeskotlin.ui.chat.ReplyInteractionCard
import dev.hermeskotlin.ui.chat.RightRailLayout
import dev.hermeskotlin.ui.chat.SendButton
import dev.hermeskotlin.ui.chat.SendIcon
import dev.hermeskotlin.ui.chat.SentAttachments
import dev.hermeskotlin.ui.chat.Snug
import dev.hermeskotlin.ui.chat.bubbleGlow
import dev.hermeskotlin.ui.chat.composerKey
import dev.hermeskotlin.ui.chat.fileDrop
import dev.hermeskotlin.ui.chat.isPasteKey
import dev.hermeskotlin.ui.chat.rememberAttachmentPicker
import dev.hermeskotlin.ui.chat.rememberRightRailState
import dev.hermeskotlin.ui.chat.userBubbleShape
import dev.hermeskotlin.ui.clearOfWindowControls
import dev.hermeskotlin.ui.components.EmptyState
import dev.hermeskotlin.ui.components.ListSkeleton
import dev.hermeskotlin.ui.enterSends
import dev.hermeskotlin.ui.hasVoice
import dev.hermeskotlin.ui.imeVisible
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.pet.PetController
import dev.hermeskotlin.ui.pet.PetSheet
import dev.hermeskotlin.ui.pet.PetView
import dev.hermeskotlin.ui.resources.*
import dev.hermeskotlin.ui.sessions.SubpageHeader
import dev.hermeskotlin.ui.voice.DictationState
import dev.hermeskotlin.ui.voice.VoiceController
import dev.hermeskotlin.ui.voice.rememberMicrophonePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal data class TasksBoardActions(
    val onRefresh: () -> Unit = {},
    val onSaveConfig: (url: String, token: String) -> Unit = { _, _ -> },
    val onSaveConfigAndCoordinator: (url: String, token: String, coordinator: DispatchCoordinatorUpdate?) -> Unit = { u, t, _ -> onSaveConfig(u, t) },
    val onSaveCoordinatorConfig: (DispatchCoordinatorUpdate) -> Unit = {},
    val onTogglePriorityStrip: (Boolean) -> Unit = {},
    val onToggleIncludeDone: () -> Unit = {},
    val onFocusProject: (String?) -> Unit = {},
    val onSetFocusGrouping: (TaskFocusGrouping) -> Unit = {},
    val onToggleProjectExpanded: (String) -> Unit = {},
    val onToggleWorkstreamCollapsed: (String) -> Unit = {},
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
    val onOpenCoordinator: () -> Unit = {},
    val onCloseCoordinator: () -> Unit = {},
    val onToggleCoordinator: () -> Unit = {},
    val onSendToCoordinator: (String) -> Unit = {},
    val onQuickCoordinatorAction: (CoordinatorQuickAction) -> Unit = {},
    val onResetCoordinatorSession: () -> Unit = {},
    val onOpenCoordinatorFullscreen: () -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
    val onRefreshModels: (Boolean) -> Unit = {},
    val onSelectModel: (ModelOption, Boolean) -> Unit = { _, _ -> },
    val onDismissModelConfirm: () -> Unit = {},
    val onSelectEffort: (ReasoningEffort) -> Unit = {},
    val onSetFast: (Boolean) -> Unit = {},
    val onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit = {},
    val onAnswerRequest: (InputRequest, JsonObject) -> Unit = { _, _ -> },
    val onPreparePlan: (DirectusTask) -> Unit = {},
    val onApprovePlan: (DirectusTask, ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit = { _, _, _, _ -> },
    val onOpenFullscreen: (DirectusTask, TaskChannel) -> Unit = { _, _ -> },
    val onDismissFeedback: () -> Unit = {},
)

private val BENTO_TWO_COL_THRESHOLD = 620.dp
private val KANBAN_THREE_COL_THRESHOLD = 840.dp
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
    onToggleSidebar: (() -> Unit)? = null,
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val settingsStore = koinInject<SettingsStore>()
    val petApi = koinInject<PetApi>()
    val audioApi = koinInject<AudioApi>()
    val voiceRecorder = koinInject<VoiceRecorder>()
    val speechPlayer = koinInject<SpeechPlayer>()
    val deviceDictation = koinInject<DeviceDictation>()
    val appScope = koinInject<CoroutineScope>()
    val scope = rememberCoroutineScope()

    val petController = remember(petApi, scope) { PetController(petApi, scope) }
    val petSprite by petController.sprite.collectAsStateWithLifecycle()
    var petSheetOpen by remember { mutableStateOf(false) }

    val voiceController = remember(audioApi, voiceRecorder, speechPlayer, scope, appScope, deviceDictation) {
        VoiceController(
            audio = audioApi,
            recorder = voiceRecorder,
            player = speechPlayer,
            scope = scope,
            appScope = appScope,
            deviceDictation = deviceDictation,
        )
    }
    DisposableEffect(voiceController) {
        onDispose { voiceController.stopAll() }
    }
    DisposableEffect(visible) {
        viewModel.setVisible(visible)
        if (!visible) voiceController.stopAll()
        onDispose { viewModel.setVisible(false) }
    }

    val dictation by voiceController.dictation.collectAsStateWithLifecycle()
    val microphone = rememberMicrophonePermission()
    val dictationNeedsMic = stringResource(Res.string.chat_notice_dictation_mic)
    LaunchedEffect(dictation.error) {
        val err = dictation.error
        if (!err.isNullOrBlank()) {
            viewModel.reportAttachmentError(err)
            voiceController.dismissDictationError()
        }
    }

    val attachmentPicker = rememberAttachmentPicker(
        onPicked = viewModel::addAttachments,
        onError = viewModel::reportAttachmentError,
    )
    LaunchedEffect(gateway) { viewModel.bind(gateway) }
    LaunchedEffect(gateway, state.coordinatorConfig.profile) {
        petController.bind(state.coordinatorConfig.profile.ifBlank { Bot.DEFAULT })
    }
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

    val onToggleDictation: (TextFieldState) -> Unit = { fieldState ->
        microphone.withMicrophone(onDenied = { viewModel.reportAttachmentError(dictationNeedsMic) }) {
            if (voiceController.dictation.value.recording) {
                voiceController.finishDictation()
            } else {
                val engine = (settingsStore.settings.value ?: AppSettings()).dictationEngine
                val profile = state.activeChannel?.profile
                    ?: if (state.coordinatorOpen) state.coordinatorConfig.profile else state.selectedTask?.assignedBots?.firstOrNull()
                val typed = fieldState.text.toString()
                var shown = typed
                fun withSpoken(base: String, spoken: String) = if (base.isBlank()) spoken else "${base.trimEnd()} $spoken"
                voiceController.startDictation(
                    gateway = gateway.gatewayUrl,
                    profile = profile,
                    engine = engine,
                    onPartial = { partial ->
                        if (fieldState.text.toString() == shown) {
                            shown = withSpoken(typed, partial)
                            fieldState.setTextAndPlaceCursorAtEnd(shown)
                        }
                    },
                    onCancelled = {
                        if (shown != typed && fieldState.text.toString() == shown) {
                            fieldState.setTextAndPlaceCursorAtEnd(typed)
                        }
                    },
                ) { text ->
                    val current = fieldState.text.toString()
                    fieldState.setTextAndPlaceCursorAtEnd(withSpoken(if (current == shown) typed else current, text))
                }
            }
        }
    }

    val actions = remember(viewModel, settingsStore) {
        TasksBoardActions(
            onRefresh = viewModel::refresh,
            onSaveConfig = { url, token -> viewModel.saveConfig(url, token) },
            onSaveConfigAndCoordinator = viewModel::saveConfig,
            onSaveCoordinatorConfig = viewModel::saveCoordinatorConfig,
            onTogglePriorityStrip = viewModel::togglePriorityStrip,
            onToggleIncludeDone = viewModel::toggleIncludeDone,
            onFocusProject = viewModel::focusProject,
            onSetFocusGrouping = viewModel::setFocusGrouping,
            onToggleProjectExpanded = viewModel::toggleProjectExpanded,
            onToggleWorkstreamCollapsed = viewModel::toggleWorkstreamCollapsed,
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
            onOpenCoordinator = { viewModel.openCoordinator(autoCheckinPrompt = false) },
            onCloseCoordinator = viewModel::closeCoordinator,
            onToggleCoordinator = viewModel::toggleCoordinator,
            onSendToCoordinator = { txt -> viewModel.sendToCoordinator(txt) },
            onQuickCoordinatorAction = { act -> viewModel.triggerQuickCoordinatorAction(act) },
            onResetCoordinatorSession = viewModel::resetCoordinatorSession,
            onOpenCoordinatorFullscreen = viewModel::openCoordinatorFullscreen,
            onRemoveAttachment = viewModel::removeAttachment,
            onRefreshModels = viewModel::loadModels,
            onSelectModel = viewModel::selectModel,
            onDismissModelConfirm = viewModel::dismissModelConfirm,
            onSelectEffort = { viewModel.setReasoningEffort(it.wire) },
            onSetFast = viewModel::setFast,
            onUpdateSettings = settingsStore::update,
            onAnswerRequest = viewModel::answerChannelRequest,
            onPreparePlan = viewModel::preparePlan,
            onApprovePlan = viewModel::approveStagedPlan,
            onOpenFullscreen = viewModel::openChannelFullscreen,
            onDismissFeedback = viewModel::clearFeedback,
        )
    }

    TasksBoardContent(
        state = state,
        actions = actions,
        onBack = onBack,
        onToggleSidebar = onToggleSidebar,
        attachmentPicker = attachmentPicker,
        petSprite = petSprite,
        onOpenPetPicker = { petSheetOpen = true },
        dictation = dictation,
        onToggleDictation = onToggleDictation,
    )

    PetSheet(
        visible = petSheetOpen,
        controller = petController,
        onDismiss = { petSheetOpen = false },
        onAdopted = { petSheetOpen = false },
    )
}

/** Stateless Command Board UI shared between live [TasksPage], Compose previews, and UI tests. */
@Composable
internal fun TasksBoardContent(
    state: TasksUiState,
    actions: TasksBoardActions,
    onBack: () -> Unit,
    onToggleSidebar: (() -> Unit)? = null,
    attachmentPicker: AttachmentPicker? = null,
    petSprite: PetSprite? = null,
    onOpenPetPicker: (() -> Unit)? = null,
    dictation: DictationState = DictationState(),
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
) {
    val railState = rememberRightRailState()
    var railTrigger by remember { mutableIntStateOf(0) }
    var setupDialogOpen by remember { mutableStateOf(false) }
    var newProjectOpen by remember { mutableStateOf(false) }
    var newTaskOpen by remember { mutableStateOf(false) }
    var pickSessionFor by remember { mutableStateOf<DirectusTask?>(null) }
    var pickRoomFor by remember { mutableStateOf<DirectusTask?>(null) }
    var modelsOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    val modelPillBounds = remember { mutableStateOf<Rect?>(null) }

    val selected = state.selectedTask
    val railOpenTarget = state.selectedTaskId != null || state.coordinatorOpen
    val configured = state.config?.configured == true

    LaunchedEffect(state.selectedTaskId, state.coordinatorOpen, railState.docked, railTrigger) {
        if (railOpenTarget) railState.open() else railState.close()
    }

    val selectAndOpen: (String?) -> Unit = { id ->
        if (id != null) railTrigger++
        actions.onSelectTask(id)
    }
    val toggleCoordinatorRail: () -> Unit = {
        railTrigger++
        actions.onToggleCoordinator()
    }

    val coordinatorPetState = remember(
        state.channelError,
        state.checkinDueSlot,
        state.channelInputRequests,
        state.priorityTasks.size,
        state.sendingToChannel,
        state.channelTurnRunning,
        state.channelTurnStatus,
    ) {
        petStateOf(
            failed = state.channelError != null,
            justFinished = state.checkinDueSlot != null,
            awaitingInput = state.channelInputRequests.isNotEmpty() || state.priorityTasks.isNotEmpty(),
            toolRunning = (state.sendingToChannel || state.channelTurnRunning) &&
                !state.channelTurnStatus.isNullOrBlank() && state.channelTurnStatus != "Thinking…",
            reasoning = (state.sendingToChannel || state.channelTurnRunning) &&
                (state.channelTurnStatus.isNullOrBlank() || state.channelTurnStatus == "Thinking…"),
            busy = state.sendingToChannel || state.channelTurnRunning,
        )
    }

    CompositionLocalProvider(LocalModelPillBounds provides modelPillBounds) {
    RightRailLayout(
        state = railState,
        railWidth = DEFAULT_RAIL_WIDTH,
        swipeEnabled = !isDesktop && railOpenTarget,
        dockOnWide = true,
        rail = {
            if (state.coordinatorOpen) {
                CoordinatorInspector(
                    coordinatorBot = state.coordinatorBot,
                    bots = state.bots,
                    botAvatars = state.botAvatars,
                    petSprite = petSprite,
                    petState = coordinatorPetState,
                    activeChannel = state.activeChannel,
                    channelLoading = state.channelLoading,
                    channelMessages = state.channelMessages,
                    channelAttachments = state.channelAttachments,
                    channelChatState = state.channelChatState,
                    modelPicker = state.modelPicker,
                    channelError = state.channelError,
                    sendingToChannel = state.sendingToChannel,
                    channelTurnRunning = state.channelTurnRunning,
                    channelTurnStatus = state.channelTurnStatus,
                    channelInputRequests = state.channelInputRequests,
                    checkinDue = state.checkinDueSlot != null,
                    resettingSession = state.resettingCoordinator,
                    gatewayConnected = state.gatewayConnected,
                    attachmentPicker = attachmentPicker,
                    dictation = dictation,
                    onToggleDictation = onToggleDictation,
                    onClose = actions.onCloseCoordinator,
                    onRefreshChannel = actions.onRefreshChannel,
                    onResetSession = actions.onResetCoordinatorSession,
                    onOpenFullscreen = actions.onOpenCoordinatorFullscreen,
                    onOpenPetPicker = onOpenPetPicker,
                    onOpenSettings = { setupDialogOpen = true },
                    onQuickAction = actions.onQuickCoordinatorAction,
                    onSendToCoordinator = actions.onSendToCoordinator,
                    onRemoveAttachment = actions.onRemoveAttachment,
                    onAttach = {
                        if (isDesktop) attachmentPicker?.pickFiles() else attachOpen = true
                    },
                    onOpenModels = { modelsOpen = true },
                    onAnswerRequest = actions.onAnswerRequest,
                )
            } else if (selected != null) {
                TaskDetailInspector(
                    task = selected,
                    projects = state.projects,
                    bots = state.bots,
                    botAvatars = state.botAvatars,
                    busy = selected.id in state.busyTaskIds,
                    activeChannelIdx = state.activeChannelIdx,
                    activeChannel = state.activeChannel,
                    channelLoading = state.channelLoading,
                    channelMessages = state.channelMessages,
                    channelAttachments = state.channelAttachments,
                    channelChatState = state.channelChatState,
                    modelPicker = state.modelPicker,
                    channelError = state.channelError,
                    sendingToChannel = state.sendingToChannel,
                    channelTurnRunning = state.channelTurnRunning,
                    channelTurnStatus = state.channelTurnStatus,
                    channelInputRequests = state.channelInputRequests,
                    gatewayConnected = state.gatewayConnected,
                    attachmentPicker = attachmentPicker,
                    dictation = dictation,
                    onToggleDictation = onToggleDictation,
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
                    onRemoveAttachment = actions.onRemoveAttachment,
                    onAttach = {
                        if (isDesktop) attachmentPicker?.pickFiles() else attachOpen = true
                    },
                    onOpenModels = { modelsOpen = true },
                    onAnswerRequest = actions.onAnswerRequest,
                )
            }
        },
    ) { contentModifier ->
        PlatformBackHandler(enabled = railOpenTarget && railState.isOpen) {
            if (state.coordinatorOpen) actions.onCloseCoordinator() else actions.onSelectTask(null)
        }
        Column(contentModifier.fillMaxSize()) {
            SubpageHeader(
                title = stringResource(Res.string.tasks_title),
                subtitle = stringResource(Res.string.tasks_subtitle),
                onBack = onBack,
                actions = {
                    if (onToggleSidebar != null) {
                        IconButton(
                            Lucide.PanelLeft,
                            contentDescription = stringResource(Res.string.app_keys_sidebar),
                            onClick = onToggleSidebar,
                        )
                    }
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
                    state = state,
                    onTogglePriorityStrip = actions.onTogglePriorityStrip,
                    onToggleIncludeDone = actions.onToggleIncludeDone,
                    onFocusProject = actions.onFocusProject,
                    onSetFocusGrouping = actions.onSetFocusGrouping,
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
                        bots = state.bots,
                        botAvatars = state.botAvatars,
                        saving = state.savingConfig,
                        error = state.configError,
                        onSave = actions.onSaveConfigAndCoordinator,
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
                        onFocusProject = actions.onFocusProject,
                        onToggleProjectExpanded = actions.onToggleProjectExpanded,
                        onToggleWorkstreamCollapsed = actions.onToggleWorkstreamCollapsed,
                    )
                }

                if (configured && !state.coordinatorOpen) {
                    CoordinatorFloatingTrigger(
                        bot = state.coordinatorBot,
                        avatarBytes = state.botAvatars[state.coordinatorBot.name],
                        petSprite = petSprite,
                        petState = coordinatorPetState,
                        checkinDue = state.checkinDueSlot != null,
                        busy = state.sendingToChannel || state.channelTurnRunning,
                        onClick = toggleCoordinatorRail,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    if (setupDialogOpen) {
        DirectusSetupDialog(
            config = state.config,
            bots = state.bots,
            botAvatars = state.botAvatars,
            saving = state.savingConfig,
            error = state.configError,
            onOpenPetPicker = onOpenPetPicker,
            onResetCoordinatorSession = actions.onResetCoordinatorSession,
            onDismiss = { setupDialogOpen = false },
            onSave = { url, token, coordUpdate ->
                actions.onSaveConfigAndCoordinator(url, token, coordUpdate)
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

    if (attachmentPicker != null) {
        val isRoom = state.activeChannel?.isRoom == true
        AttachSheet(
            visible = attachOpen,
            onDismiss = { attachOpen = false },
            picker = attachmentPicker,
            subtitle = if (isRoom) {
                stringResource(Res.string.chat_attach_limits, OutgoingAttachment.MAX_COUNT, OutgoingAttachment.MAX_INLINE_BYTES / 1_000_000)
            } else {
                null
            },
        )
    }

    ModelSheet(
        visible = modelsOpen && state.activeChannel?.isRoom != true,
        onDismiss = { modelsOpen = false },
        state = state.channelChatState,
        picker = state.modelPicker,
        onRefresh = { actions.onRefreshModels(true) },
        onSelectModel = { model ->
            actions.onSelectModel(model, false)
            modelsOpen = false
        },
        onSelectEffort = actions.onSelectEffort,
        onFast = actions.onSetFast,
        onSettings = actions.onUpdateSettings,
        anchor = { modelPillBounds.value.takeIf { isDesktop } },
    )
    ModelConfirmDialog(
        pending = state.modelPicker.confirm,
        onConfirm = { model -> actions.onSelectModel(model, true) },
        onDismiss = actions.onDismissModelConfirm,
    )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardControlsBar(
    state: TasksUiState,
    onTogglePriorityStrip: (Boolean) -> Unit,
    onToggleIncludeDone: () -> Unit,
    onFocusProject: (String?) -> Unit,
    onSetFocusGrouping: (TaskFocusGrouping) -> Unit,
) {
    val buckets = remember(state.projects, state.tasks, state.todayEpochDay) {
        buildProjectBuckets(state.projects, state.tasks, state.todayEpochDay)
    }
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)

    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                    .background(Theme[colors][surface])
                    .border(1.dp, Theme[colors][stroke], RoundedCornerShape(Theme[radii][radiusMedium]))
                    .toggleable(value = state.showPriorityStrip, role = Role.Switch, onValueChange = onTogglePriorityStrip)
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
                Switch(checked = state.showPriorityStrip)
            }

            Chip(
                text = stringResource(if (state.includeDone) Res.string.tasks_hide_done else Res.string.tasks_show_done),
                selected = state.includeDone,
                onClick = onToggleIncludeDone,
            )

            if (state.focusedProjectId != null) {
                Chip(
                    text = stringResource(Res.string.tasks_focus_group_workstream),
                    selected = state.focusGrouping == TaskFocusGrouping.WORKSTREAM,
                    onClick = { onSetFocusGrouping(TaskFocusGrouping.WORKSTREAM) },
                )
                Chip(
                    text = stringResource(Res.string.tasks_focus_group_status),
                    selected = state.focusGrouping == TaskFocusGrouping.STATUS,
                    onClick = { onSetFocusGrouping(TaskFocusGrouping.STATUS) },
                )
            }
        }

        if (buckets.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "scope-all") {
                    Chip(
                        text = stringResource(Res.string.tasks_all_projects, state.tasks.size),
                        selected = state.focusedProjectId == null,
                        onClick = { onFocusProject(null) },
                    )
                }
                items(buckets.size, key = { idx -> "scope-${buckets[idx].key}" }) { idx ->
                    val bucket = buckets[idx]
                    val label = bucket.project?.name ?: unassignedLabel
                    val active = state.focusedProjectId == bucket.key
                    Chip(
                        text = label,
                        count = bucket.tasks.size,
                        selected = active,
                        onClick = { onFocusProject(if (active) null else bucket.key) },
                    )
                }
            }
        }
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
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    saving: Boolean,
    error: String?,
    onSave: (url: String, token: String, coordinator: DispatchCoordinatorUpdate?) -> Unit,
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
                onClick = { onSave(urlText, tokenState.text.toString(), null) },
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
    onFocusProject: (String?) -> Unit,
    onToggleProjectExpanded: (String) -> Unit,
    onToggleWorkstreamCollapsed: (String) -> Unit,
) {
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)
    val standaloneLabel = stringResource(Res.string.tasks_standalone_group)
    val buckets = remember(state.projects, state.tasks, state.todayEpochDay) {
        buildProjectBuckets(state.projects, state.tasks, state.todayEpochDay)
    }
    val projectById = remember(buckets) {
        buckets.mapNotNull { it.project }.associateBy { it.id }
    }
    val focusedBucket = remember(buckets, state.focusedProjectId) {
        state.focusedProjectId?.let { fid -> buckets.firstOrNull { it.key == fid } }
    }
    val stripSplit = remember(focusedBucket, state.tasks, state.todayEpochDay) {
        if (focusedBucket != null) {
            buildAttentionSplit(focusedBucket.tasks, state.todayEpochDay)
        } else {
            state.attentionSplit
        }
    }

    if (state.tasks.isEmpty() && buckets.all { it.project == null }) {
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
        val kanbanWide = maxWidth >= KANBAN_THREE_COL_THRESHOLD
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.showPriorityStrip) {
                item(key = "attention-strip") {
                    AttentionStripSection(
                        split = stripSplit,
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        todayEpochDay = state.todayEpochDay,
                        twoColumns = columns == 2,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        onSelectTask = onSelectTask,
                    )
                }
            }

            if (focusedBucket != null) {
                item(key = "focus-header-${focusedBucket.key}") {
                    FocusedProjectHeader(
                        project = focusedBucket.project,
                        projectLabel = focusedBucket.project?.name ?: unassignedLabel,
                        taskCount = focusedBucket.tasks.size,
                        onClearFocus = { onFocusProject(null) },
                    )
                }

                if (focusedBucket.tasks.isEmpty()) {
                    item(key = "focus-empty-${focusedBucket.key}") {
                        Text(
                            stringResource(Res.string.tasks_project_empty),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][textSecondary],
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else if (state.focusGrouping == TaskFocusGrouping.WORKSTREAM) {
                    val workstreams = buildWorkstreamGroups(
                        tasks = focusedBucket.tasks,
                        todayEpochDay = state.todayEpochDay,
                        standaloneTitle = standaloneLabel,
                    )
                    items(workstreams.size, key = { idx -> "wg-${workstreams[idx].key}" }) { idx ->
                        val group = workstreams[idx]
                        WorkstreamSectionCard(
                            group = group,
                            projectLabel = focusedBucket.project?.name ?: unassignedLabel,
                            todayEpochDay = state.todayEpochDay,
                            twoColumns = columns == 2,
                            collapsed = group.key in state.collapsedWorkstreamKeys,
                            selectedTaskId = state.selectedTaskId,
                            busyTaskIds = state.busyTaskIds,
                            onToggleCollapse = { onToggleWorkstreamCollapsed(group.key) },
                            onSelectTask = onSelectTask,
                        )
                    }
                } else {
                    item(key = "focus-kanban-${focusedBucket.key}") {
                        StatusKanbanBoard(
                            tasks = focusedBucket.tasks,
                            projectLabel = focusedBucket.project?.name ?: unassignedLabel,
                            todayEpochDay = state.todayEpochDay,
                            includeDone = state.includeDone,
                            wideThreeCols = kanbanWide,
                            selectedTaskId = state.selectedTaskId,
                            busyTaskIds = state.busyTaskIds,
                            onSelectTask = onSelectTask,
                        )
                    }
                }
            } else if (columns == 2) {
                val (leftBuckets, rightBuckets) = distributeBentoMasonry(
                    buckets = buckets,
                    expandedProjectIds = state.expandedProjectIds,
                    previewLimit = DEFAULT_PROJECT_PREVIEW_LIMIT,
                )
                item(key = "bento-masonry") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BentoColumnStack(
                            buckets = leftBuckets,
                            state = state,
                            unassignedLabel = unassignedLabel,
                            onSelectTask = onSelectTask,
                            onToggleProjectExpanded = onToggleProjectExpanded,
                            onFocusProject = onFocusProject,
                            modifier = Modifier.weight(1f),
                        )
                        BentoColumnStack(
                            buckets = rightBuckets,
                            state = state,
                            unassignedLabel = unassignedLabel,
                            onSelectTask = onSelectTask,
                            onToggleProjectExpanded = onToggleProjectExpanded,
                            onFocusProject = onFocusProject,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                items(buckets.size, key = { idx -> buckets[idx].key }) { idx ->
                    val bucket = buckets[idx]
                    ProjectBentoCard(
                        bucket = bucket,
                        projectLabel = bucket.project?.name ?: unassignedLabel,
                        todayEpochDay = state.todayEpochDay,
                        expanded = bucket.key in state.expandedProjectIds,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        onSelectTask = onSelectTask,
                        onToggleExpand = { onToggleProjectExpanded(bucket.key) },
                        onFocusProject = { onFocusProject(bucket.key) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionStripSection(
    split: AttentionSplit,
    projectById: Map<String, DirectusProject>,
    unassignedLabel: String,
    todayEpochDay: Long,
    twoColumns: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
) {
    if (split.isEmpty) {
        val compactShape = RoundedCornerShape(Theme[radii][radiusMedium])
        Row(
            Modifier
                .fillMaxWidth()
                .clip(compactShape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], compactShape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Theme[colors][success]))
            Text(
                stringResource(Res.string.tasks_attention_empty_compact),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
        }
        return
    }

    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val stripeColor = if (split.plansReady.isNotEmpty()) Theme[colors][warning] else Theme[colors][danger]
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke], shape)
            .drawWithContent {
                drawContent()
                drawRect(
                    color = stripeColor,
                    topLeft = Offset.Zero,
                    size = Size(4.dp.toPx(), size.height),
                )
            }
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.Sparkles, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(16.dp))
            Text(
                stringResource(Res.string.tasks_priority_section, split.totalCount).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textColor],
            )
        }

        if (twoColumns && split.plansReady.isNotEmpty() && split.riskAlerts.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AttentionSubgroupColumn(
                    heading = stringResource(Res.string.tasks_attention_plans, split.plansReady.size),
                    headingColor = Theme[colors][warning],
                    tasks = split.plansReady,
                    extraCount = 0,
                    projectById = projectById,
                    unassignedLabel = unassignedLabel,
                    todayEpochDay = todayEpochDay,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.weight(1f),
                )
                AttentionSubgroupColumn(
                    heading = stringResource(Res.string.tasks_attention_risks, split.riskAlerts.size + split.extraRiskCount),
                    headingColor = Theme[colors][danger],
                    tasks = split.riskAlerts,
                    extraCount = split.extraRiskCount,
                    projectById = projectById,
                    unassignedLabel = unassignedLabel,
                    todayEpochDay = todayEpochDay,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            if (split.plansReady.isNotEmpty()) {
                AttentionSubgroupColumn(
                    heading = stringResource(Res.string.tasks_attention_plans, split.plansReady.size),
                    headingColor = Theme[colors][warning],
                    tasks = split.plansReady,
                    extraCount = 0,
                    projectById = projectById,
                    unassignedLabel = unassignedLabel,
                    todayEpochDay = todayEpochDay,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (split.riskAlerts.isNotEmpty()) {
                AttentionSubgroupColumn(
                    heading = stringResource(Res.string.tasks_attention_risks, split.riskAlerts.size + split.extraRiskCount),
                    headingColor = Theme[colors][danger],
                    tasks = split.riskAlerts,
                    extraCount = split.extraRiskCount,
                    projectById = projectById,
                    unassignedLabel = unassignedLabel,
                    todayEpochDay = todayEpochDay,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AttentionSubgroupColumn(
    heading: String,
    headingColor: Color,
    tasks: List<DirectusTask>,
    extraCount: Int,
    projectById: Map<String, DirectusProject>,
    unassignedLabel: String,
    todayEpochDay: Long,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            heading,
            style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
            color = headingColor,
        )
        tasks.forEach { task ->
            val projectName = task.projectId?.let { projectById[it]?.name } ?: unassignedLabel
            TaskPill(
                task = task,
                projectName = projectName,
                showProjectBadge = true,
                todayEpochDay = todayEpochDay,
                selected = task.id == selectedTaskId,
                busy = task.id in busyTaskIds,
                onClick = { onSelectTask(task.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (extraCount > 0) {
            Text(
                stringResource(Res.string.tasks_attention_more, extraCount),
                style = Theme[typography][caption],
                color = Theme[colors][textSecondary],
            )
        }
    }
}

@Composable
private fun BentoColumnStack(
    buckets: List<ProjectBucket>,
    state: TasksUiState,
    unassignedLabel: String,
    onSelectTask: (String?) -> Unit,
    onToggleProjectExpanded: (String) -> Unit,
    onFocusProject: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buckets.forEach { bucket ->
            ProjectBentoCard(
                bucket = bucket,
                projectLabel = bucket.project?.name ?: unassignedLabel,
                todayEpochDay = state.todayEpochDay,
                expanded = bucket.key in state.expandedProjectIds,
                selectedTaskId = state.selectedTaskId,
                busyTaskIds = state.busyTaskIds,
                onSelectTask = onSelectTask,
                onToggleExpand = { onToggleProjectExpanded(bucket.key) },
                onFocusProject = { onFocusProject(bucket.key) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FocusedProjectHeader(
    project: DirectusProject?,
    projectLabel: String,
    taskCount: Int,
    onClearFocus: () -> Unit,
) {
    val dotColor = project?.color?.let(::parseHexColorOrNull) ?: Theme[colors][accent]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Text(
                projectLabel,
                style = Theme[typography][heading],
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(Res.string.tasks_project_count, taskCount),
                style = Theme[typography][code].copy(fontSize = 11.sp),
                color = Theme[colors][textTertiary],
            )
            IconButton(
                Lucide.X,
                contentDescription = stringResource(Res.string.tasks_all_projects, taskCount),
                onClick = onClearFocus,
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
    }
}

@Composable
private fun WorkstreamSectionCard(
    group: WorkstreamGroup,
    projectLabel: String,
    todayEpochDay: Long,
    twoColumns: Boolean,
    collapsed: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onToggleCollapse: () -> Unit,
    onSelectTask: (String?) -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke], shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleCollapse)
                .handCursor()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(
                if (collapsed) Lucide.ChevronRight else Lucide.ChevronDown,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(16.dp),
            )
            Text(
                group.title,
                style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (group.alertCount > 0) {
                MiniBadge(
                    text = stringResource(Res.string.tasks_attention_count, group.alertCount),
                    backgroundColor = Theme[colors][dangerSoft],
                    contentColor = Theme[colors][danger],
                )
            }
            Text(
                stringResource(Res.string.tasks_project_count, group.tasks.size),
                style = Theme[typography][code].copy(fontSize = 11.sp),
                color = Theme[colors][textTertiary],
            )
        }

        if (!collapsed) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (twoColumns && group.tasks.size > 1) {
                    group.tasks.chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { task ->
                                TaskPill(
                                    task = task,
                                    projectName = projectLabel,
                                    showProjectBadge = false,
                                    todayEpochDay = todayEpochDay,
                                    selected = task.id == selectedTaskId,
                                    busy = task.id in busyTaskIds,
                                    onClick = { onSelectTask(task.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    group.tasks.forEach { task ->
                        TaskPill(
                            task = task,
                            projectName = projectLabel,
                            showProjectBadge = false,
                            todayEpochDay = todayEpochDay,
                            selected = task.id == selectedTaskId,
                            busy = task.id in busyTaskIds,
                            onClick = { onSelectTask(task.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private data class KanbanBucket(
    val title: String,
    val tasks: List<DirectusTask>,
    val accentColor: Color,
)

@Composable
private fun StatusKanbanBoard(
    tasks: List<DirectusTask>,
    projectLabel: String,
    todayEpochDay: Long,
    includeDone: Boolean,
    wideThreeCols: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
) {
    val blockedOrOverdue = tasks.filter { it.status != DirectusTask.STATUS_DONE && isAttentionRisk(it, todayEpochDay) }
    val inProgress = tasks.filter { it.status == DirectusTask.STATUS_IN_PROGRESS && !isAttentionRisk(it, todayEpochDay) }
    val todo = tasks.filter { it.status == DirectusTask.STATUS_TODO && !isAttentionRisk(it, todayEpochDay) }
    val done = if (includeDone) tasks.filter { it.status == DirectusTask.STATUS_DONE } else emptyList()

    val columns = buildList {
        add(KanbanBucket(stringResource(Res.string.tasks_status_blocked_or_overdue), blockedOrOverdue, Theme[colors][danger]))
        add(KanbanBucket(stringResource(Res.string.tasks_status_in_progress), inProgress, Theme[colors][accent]))
        add(KanbanBucket(stringResource(Res.string.tasks_status_todo), todo, Theme[colors][textTertiary]))
        if (includeDone) {
            add(KanbanBucket(stringResource(Res.string.tasks_status_done), done, Theme[colors][success]))
        }
    }

    if (wideThreeCols) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            columns.forEach { col ->
                KanbanColumnCard(
                    column = col,
                    projectLabel = projectLabel,
                    todayEpochDay = todayEpochDay,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            columns.forEach { col ->
                KanbanColumnCard(
                    column = col,
                    projectLabel = projectLabel,
                    todayEpochDay = todayEpochDay,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun KanbanColumnCard(
    column: KanbanBucket,
    projectLabel: String,
    todayEpochDay: Long,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
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
            Box(Modifier.size(8.dp).clip(CircleShape).background(column.accentColor))
            Text(
                column.title,
                style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                modifier = Modifier.weight(1f),
            )
            Text(
                column.tasks.size.toString(),
                style = Theme[typography][code].copy(fontSize = 11.sp),
                color = Theme[colors][textTertiary],
            )
        }
        column.tasks.forEach { task ->
            TaskPill(
                task = task,
                projectName = projectLabel,
                showProjectBadge = false,
                todayEpochDay = todayEpochDay,
                selected = task.id == selectedTaskId,
                busy = task.id in busyTaskIds,
                onClick = { onSelectTask(task.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProjectBentoCard(
    bucket: ProjectBucket,
    projectLabel: String,
    todayEpochDay: Long,
    expanded: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    onSelectTask: (String?) -> Unit,
    onToggleExpand: () -> Unit,
    onFocusProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = bucket.project
    val tasks = bucket.tasks
    val hiddenCount = (tasks.size - DEFAULT_PROJECT_PREVIEW_LIMIT).coerceAtLeast(0)
    val shown = if (expanded || hiddenCount == 0) tasks else tasks.take(DEFAULT_PROJECT_PREVIEW_LIMIT)
    val alertCount = tasks.count { it.agentState == TaskContext.STATE_NEEDS_YOU || isAttentionRisk(it, todayEpochDay) }
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
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
                .clickable(role = Role.Button, onClick = onFocusProject)
                .handCursor()
                .padding(vertical = 2.dp),
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
            if (alertCount > 0) {
                MiniBadge(
                    text = stringResource(Res.string.tasks_attention_count, alertCount),
                    backgroundColor = Theme[colors][dangerSoft],
                    contentColor = Theme[colors][danger],
                )
            }
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

        if (tasks.isEmpty()) {
            Text(
                stringResource(Res.string.tasks_project_empty),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textTertiary],
            )
        } else {
            shown.forEach { task ->
                TaskPill(
                    task = task,
                    projectName = projectLabel,
                    showProjectBadge = false,
                    todayEpochDay = todayEpochDay,
                    selected = task.id == selectedTaskId,
                    busy = task.id in busyTaskIds,
                    onClick = { onSelectTask(task.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (hiddenCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) stringResource(Res.string.tasks_show_fewer_tasks) else stringResource(Res.string.tasks_show_more_tasks, hiddenCount),
                    style = Theme[typography][labelStyle],
                    color = Theme[colors][textSecondary],
                    modifier = Modifier
                        .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
                        .clickable(role = Role.Button, onClick = onToggleExpand)
                        .handCursor()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Text(
                    stringResource(Res.string.tasks_open_focus),
                    style = Theme[typography][labelStyle],
                    color = Theme[colors][accentText],
                    modifier = Modifier
                        .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
                        .clickable(role = Role.Button, onClick = onFocusProject)
                        .handCursor()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskPill(
    task: DirectusTask,
    projectName: String,
    showProjectBadge: Boolean,
    todayEpochDay: Long,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overdueDays = taskOverdueDays(task, todayEpochDay)
    val dueEpochDay = parseDueEpochDay(task.dueDate)
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val fill = if (selected) Theme[colors][accentSoft].compositeOver(Theme[colors][surface]) else Theme[colors][surface2]
    val borderColor = when {
        selected -> Theme[colors][accent]
        task.agentState == TaskContext.STATE_NEEDS_YOU -> Theme[colors][warning].copy(alpha = 0.6f)
        task.status == DirectusTask.STATUS_BLOCKED || overdueDays > 0 -> Theme[colors][danger].copy(alpha = 0.45f)
        else -> Theme[colors][stroke]
    }
    val dotColor = stateDotColor(task, overdueDays)
    val hasMetaBadges = showProjectBadge ||
        task.agentState != TaskContext.STATE_IDLE ||
        task.status != DirectusTask.STATUS_TODO ||
        overdueDays > 0 ||
        (dueEpochDay != null && task.status != DirectusTask.STATUS_DONE) ||
        task.assignedBots.isNotEmpty()

    Column(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                Spinner(Modifier.size(12.dp))
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

        if (hasMetaBadges) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (showProjectBadge) {
                    MiniBadge(
                        text = projectName,
                        backgroundColor = Theme[colors][surface3],
                        contentColor = Theme[colors][textSecondary],
                    )
                }
                if (task.agentState != TaskContext.STATE_IDLE) {
                    MiniBadge(
                        text = agentStateLabel(task.agentState),
                        backgroundColor = if (task.agentState == TaskContext.STATE_NEEDS_YOU) Theme[colors][warningSoft] else Theme[colors][accentSoft],
                        contentColor = if (task.agentState == TaskContext.STATE_NEEDS_YOU) Theme[colors][warning] else Theme[colors][accentText],
                    )
                }
                if (task.status != DirectusTask.STATUS_TODO) {
                    val isBlocked = task.status == DirectusTask.STATUS_BLOCKED
                    MiniBadge(
                        text = taskStatusLabel(task.status),
                        backgroundColor = if (isBlocked) Theme[colors][dangerSoft] else Theme[colors][surface],
                        contentColor = if (isBlocked) Theme[colors][danger] else Theme[colors][textTertiary],
                    )
                }
                if (overdueDays > 0) {
                    MiniBadge(
                        text = stringResource(Res.string.tasks_overdue_days, overdueDays),
                        backgroundColor = Theme[colors][dangerSoft],
                        contentColor = Theme[colors][danger],
                    )
                } else if (dueEpochDay != null && task.status != DirectusTask.STATUS_DONE) {
                    if (dueEpochDay == todayEpochDay) {
                        MiniBadge(
                            text = stringResource(Res.string.tasks_due_today),
                            backgroundColor = Theme[colors][warningSoft],
                            contentColor = Theme[colors][warning],
                        )
                    } else {
                        MiniBadge(
                            text = stringResource(Res.string.tasks_due_date_short, formatShortDueLabel(dueEpochDay)),
                            backgroundColor = Theme[colors][surface],
                            contentColor = Theme[colors][textTertiary],
                        )
                    }
                }
                task.assignedBots.take(2).forEach { bot ->
                    MiniBadge(
                        text = "@$bot",
                        backgroundColor = Theme[colors][well],
                        contentColor = Theme[colors][textTertiary],
                    )
                }
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
private fun CoordinatorFloatingTrigger(
    bot: Bot,
    avatarBytes: ByteArray?,
    petSprite: PetSprite?,
    petState: PetState,
    checkinDue: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusFull])
    val borderColor = when {
        checkinDue -> Theme[colors][warning]
        busy -> Theme[colors][accent]
        else -> Theme[colors][strokeStrong]
    }
    Row(
        modifier
            .testTag("dispatch_coordinator_button")
            .dropShadow(shape, DockShadow)
            .clip(shape)
            .background(Theme[colors][surface], shape)
            .border(if (checkinDue) 1.5.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (petSprite != null) {
            PetView(
                sprite = petSprite,
                state = petState,
                onClick = onClick,
            )
        } else {
            BotAvatar(
                bot = bot,
                picture = avatarBytes,
                size = 32.dp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.tasks_coordinator_ask, bot.label),
                    style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (busy) {
                    Spinner(Modifier.size(12.dp))
                } else if (checkinDue) {
                    MiniBadge(
                        text = stringResource(Res.string.tasks_coordinator_checkin_ready),
                        backgroundColor = Theme[colors][warningSoft],
                        contentColor = Theme[colors][warning],
                    )
                }
            }
            Text(
                stringResource(Res.string.tasks_coordinator_title),
                style = Theme[typography][caption],
                color = Theme[colors][textTertiary],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CoordinatorInspector(
    coordinatorBot: Bot,
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    petSprite: PetSprite?,
    petState: PetState,
    activeChannel: TaskChannel?,
    channelLoading: Boolean,
    channelMessages: List<ChannelChatLine>,
    channelAttachments: List<OutgoingAttachment>,
    channelChatState: ChatState,
    modelPicker: ModelPickerState,
    channelError: String?,
    sendingToChannel: Boolean,
    channelTurnRunning: Boolean,
    channelTurnStatus: String?,
    channelInputRequests: List<InputRequest>,
    checkinDue: Boolean,
    resettingSession: Boolean,
    gatewayConnected: Boolean,
    attachmentPicker: AttachmentPicker?,
    dictation: DictationState,
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
    onClose: () -> Unit,
    onRefreshChannel: () -> Unit,
    onResetSession: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onOpenPetPicker: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onQuickAction: (CoordinatorQuickAction) -> Unit,
    onSendToCoordinator: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
) {
    val steerState = rememberTextFieldState("")
    val quickScroll = rememberScrollState()
    val turnBusy = sendingToChannel || channelTurnRunning

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clearOfWindowControls()
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotAvatar(
                bot = coordinatorBot,
                picture = botAvatars[coordinatorBot.name],
                size = 30.dp,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        coordinatorBot.label,
                        style = Theme[typography][heading],
                        color = Theme[colors][textColor],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (checkinDue) {
                        MiniBadge(
                            text = stringResource(Res.string.tasks_coordinator_checkin_ready),
                            backgroundColor = Theme[colors][warningSoft],
                            contentColor = Theme[colors][warning],
                        )
                    }
                }
                Text(
                    "${stringResource(Res.string.tasks_coordinator_title)} · @${coordinatorBot.name}",
                    style = Theme[typography][caption],
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onOpenPetPicker != null) {
                IconButton(
                    Lucide.Sparkles,
                    contentDescription = stringResource(Res.string.tasks_coordinator_choose_pet),
                    onClick = onOpenPetPicker,
                )
            }
            if (resettingSession) {
                Spinner(Modifier.size(16.dp).padding(horizontal = 4.dp))
            } else {
                IconButton(
                    Lucide.RotateCcw,
                    contentDescription = stringResource(Res.string.tasks_coordinator_reset_session),
                    onClick = onResetSession,
                    modifier = Modifier.testTag("coordinator_reset_session_button"),
                )
            }
            if (activeChannel != null) {
                IconButton(
                    Lucide.ExternalLink,
                    contentDescription = stringResource(Res.string.tasks_open_fullscreen),
                    onClick = onOpenFullscreen,
                )
            }
            IconButton(
                Lucide.Settings2,
                contentDescription = stringResource(Res.string.tasks_coordinator_settings_heading),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("coordinator_settings_button"),
            )
            IconButton(
                Lucide.X,
                contentDescription = stringResource(Res.string.tasks_close_inspector),
                onClick = onClose,
            )
        }

        if (petSprite != null) {
            val petShape = RoundedCornerShape(Theme[radii][radiusMedium])
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(petShape)
                    .background(Theme[colors][surface])
                    .border(1.dp, Theme[colors][stroke], petShape)
                    .clickable(enabled = onOpenPetPicker != null, role = Role.Button) { onOpenPetPicker?.invoke() }
                    .handCursor(onOpenPetPicker != null)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PetView(
                    sprite = petSprite,
                    state = petState,
                    onClick = { onOpenPetPicker?.invoke() },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(Res.string.tasks_coordinator_empty),
                        style = Theme[typography][bodySmall],
                        color = Theme[colors][textSecondary],
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        TaskChannelLog(
            activeChannel = activeChannel,
            loading = channelLoading,
            messages = channelMessages,
            bots = bots,
            botAvatars = botAvatars,
            emptyText = stringResource(Res.string.tasks_coordinator_empty),
            error = channelError,
            turnRunning = turnBusy,
            turnStatus = channelTurnStatus,
            onRefresh = onRefreshChannel,
            onOpenFullscreen = if (activeChannel != null) onOpenFullscreen else null,
            onSelectInteraction = onSendToCoordinator,
            onFillInteraction = { text -> steerState.setTextAndPlaceCursorAtEnd(text) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (channelInputRequests.isNotEmpty()) {
            val requestScroll = rememberScrollState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .scrollbar(requestScroll)
                    .verticalScroll(requestScroll),
            ) {
                InputRequestPanel(
                    requests = channelInputRequests,
                    connected = gatewayConnected,
                    onAnswer = onAnswerRequest,
                )
            }
        }

        // Text-only quick action chips (explicitly no icons/emojis).
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(quickScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(
                text = stringResource(Res.string.tasks_chip_checkin),
                selected = checkinDue,
                onClick = { if (!turnBusy) onQuickAction(CoordinatorQuickAction.CHECK_IN) },
            )
            Chip(
                text = stringResource(Res.string.tasks_chip_blockers),
                selected = false,
                onClick = { if (!turnBusy) onQuickAction(CoordinatorQuickAction.BLOCKERS) },
            )
            Chip(
                text = stringResource(Res.string.tasks_chip_priorities),
                selected = false,
                onClick = { if (!turnBusy) onQuickAction(CoordinatorQuickAction.PRIORITIES) },
            )
        }

        TaskChatComposer(
            state = steerState,
            connected = gatewayConnected,
            attachments = channelAttachments,
            channelChatState = channelChatState,
            modelPicker = modelPicker,
            showModelPicker = true,
            attachmentPicker = attachmentPicker,
            dictation = dictation,
            onToggleDictation = onToggleDictation,
            placeholderText = stringResource(Res.string.tasks_coordinator_hint),
            onRemoveAttachment = onRemoveAttachment,
            onAttach = onAttach,
            onOpenModels = onOpenModels,
            onSend = {
                val txt = steerState.text.toString().trim()
                if (txt.isNotEmpty() || channelAttachments.isNotEmpty()) {
                    onSendToCoordinator(txt)
                    steerState.clearText()
                }
            },
        )
    }
}

@Composable
private fun TaskDetailInspector(
    task: DirectusTask,
    projects: List<DirectusProject>,
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    busy: Boolean,
    activeChannelIdx: Int,
    activeChannel: TaskChannel?,
    channelLoading: Boolean,
    channelMessages: List<ChannelChatLine>,
    channelAttachments: List<OutgoingAttachment>,
    channelChatState: ChatState,
    modelPicker: ModelPickerState,
    channelError: String?,
    sendingToChannel: Boolean,
    channelTurnRunning: Boolean,
    channelTurnStatus: String?,
    channelInputRequests: List<InputRequest>,
    gatewayConnected: Boolean,
    attachmentPicker: AttachmentPicker?,
    dictation: DictationState,
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
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
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
) {
    val steerState = rememberTextFieldState("")
    LaunchedEffect(task.id) { steerState.clearText() }
    var descExpanded by remember(task.id) { mutableStateOf(false) }
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)
    val currentProjectLabel = projects.firstOrNull { it.id == task.projectId }?.name ?: unassignedLabel
    val plan = task.stagedPlan
    val metaScroll = rememberScrollState()
    var planSheetOpen by remember(task.id) { mutableStateOf(false) }

    // Auto-slide the plan sheet up from the bottom when opening a task with a pending
    // unapproved plan, or when a newly prepared plan arrives.
    LaunchedEffect(task.id, plan?.generatedAt, plan?.approvedAt, plan?.summary) {
        val shouldAutoOpen = plan != null && plan.approvedAt == null && channelInputRequests.isEmpty()
        planSheetOpen = shouldAutoOpen
    }
    LaunchedEffect(channelInputRequests.isNotEmpty()) {
        if (channelInputRequests.isNotEmpty()) planSheetOpen = false
    }

    Column(Modifier.fillMaxSize()) {
        // Pinned inspector header on sidebarColor ground to match left SessionsSidebar.
        Row(
            Modifier
                .fillMaxWidth()
                .clearOfWindowControls()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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

        // Compact metadata & linked channels (staged plan lives in the slide-up drawer docked above the composer).
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 170.dp)
                .scrollbar(metaScroll)
                .verticalScroll(metaScroll)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!task.description.isNullOrBlank()) {
                val descShape = RoundedCornerShape(Theme[radii][radiusMedium])
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(descShape)
                        .background(Theme[colors][surface])
                        .border(1.dp, Theme[colors][stroke], descShape)
                        .clickable { descExpanded = !descExpanded }
                        .handCursor()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    MarkdownText(
                        text = task.description.orEmpty(),
                        textStyle = Theme[typography][bodySmall].copy(color = Theme[colors][textSecondary]),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (descExpanded) Modifier else Modifier.heightIn(max = 72.dp).clipToBounds()),
                    )
                }
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
                busy = busy,
                onSelectChannel = onSelectChannel,
                onOpenPickSession = onOpenPickSession,
                onOpenPickRoom = onOpenPickRoom,
            )
        }

        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        // Live chat stream fills remaining inspector height; staged plan slides up over its bottom edge.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sheetMaxHeight = (maxHeight * 0.72f).coerceIn(180.dp, 340.dp)
            TaskChannelLog(
                activeChannel = activeChannel,
                loading = channelLoading,
                messages = channelMessages,
                bots = bots,
                botAvatars = botAvatars,
                error = channelError,
                turnRunning = sendingToChannel || channelTurnRunning,
                turnStatus = channelTurnStatus,
                onRefresh = onRefreshChannel,
                onOpenFullscreen = activeChannel?.let { ch -> { onOpenFullscreen(ch) } },
                onSelectInteraction = onSendToChannel,
                onFillInteraction = { text -> steerState.setTextAndPlaceCursorAtEnd(text) },
                modifier = Modifier.fillMaxSize(),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = plan != null && planSheetOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (plan != null) {
                    TaskStagedPlanSheet(
                        task = task,
                        busy = busy,
                        maxSheetHeight = sheetMaxHeight,
                        onCollapse = { planSheetOpen = false },
                        onPreparePlan = onPreparePlan,
                        onApprovePlan = { opt, selectedParams, tweaks ->
                            planSheetOpen = false
                            onApprovePlan(opt, selectedParams, tweaks)
                        },
                    )
                }
            }
        }

        if (channelInputRequests.isNotEmpty()) {
            val requestScroll = rememberScrollState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .scrollbar(requestScroll)
                    .verticalScroll(requestScroll),
            ) {
                InputRequestPanel(
                    requests = channelInputRequests,
                    connected = gatewayConnected,
                    onAnswer = onAnswerRequest,
                )
            }
        }

        // Docked plan bar directly above the composer: triggers plan prep or toggles the slide-up drawer.
        TaskPlanDockBar(
            hasPlan = plan != null,
            pendingApproval = plan != null && plan.approvedAt == null,
            expanded = plan != null && planSheetOpen,
            busy = busy,
            onClick = {
                if (plan == null) {
                    onPreparePlan()
                } else {
                    planSheetOpen = !planSheetOpen
                }
            },
        )

        // Two-row floating composer matching normal chat (+ attachments, model picker, voice dictation, multiline input, send).
        TaskChatComposer(
            state = steerState,
            connected = gatewayConnected,
            attachments = channelAttachments,
            channelChatState = channelChatState,
            modelPicker = modelPicker,
            showModelPicker = activeChannel?.isRoom != true,
            attachmentPicker = attachmentPicker,
            dictation = dictation,
            onToggleDictation = onToggleDictation,
            onRemoveAttachment = onRemoveAttachment,
            onAttach = onAttach,
            onOpenModels = onOpenModels,
            onSend = {
                val txt = steerState.text.toString().trim()
                if (txt.isNotEmpty() || channelAttachments.isNotEmpty()) {
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(icon, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(13.dp))
        Text(
            label,
            style = Theme[typography][bodySmall],
            color = Theme[colors][textColor],
            singleLine = true,
            overflow = TextOverflow.Ellipsis,
        )
        UnstyledIcon(Lucide.ChevronDown, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    Row(
        Modifier
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][strokeStrong], shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .handCursor(enabled)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            Spinner(Modifier.size(13.dp))
        } else {
            UnstyledIcon(icon, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(13.dp))
        }
        Text(
            label,
            style = Theme[typography][bodySmall],
            color = if (enabled) Theme[colors][textColor] else Theme[colors][textMuted],
            singleLine = true,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskChannelsSection(
    channels: List<TaskChannel>,
    activeIdx: Int,
    busy: Boolean,
    onSelectChannel: (Int) -> Unit,
    onOpenPickSession: () -> Unit,
    onOpenPickRoom: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
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
        ActionPill(
            icon = Lucide.MessageSquare,
            label = stringResource(Res.string.tasks_add_session),
            loading = false,
            enabled = !busy,
            onClick = onOpenPickSession,
        )
        ActionPill(
            icon = Lucide.Users,
            label = stringResource(Res.string.tasks_add_room),
            loading = false,
            enabled = !busy,
            onClick = onOpenPickRoom,
        )
    }
}

@Composable
private fun TaskChannelLog(
    activeChannel: TaskChannel?,
    loading: Boolean,
    messages: List<ChannelChatLine>,
    bots: List<Bot> = emptyList(),
    botAvatars: Map<String, ByteArray> = emptyMap(),
    emptyText: String? = null,
    error: String?,
    turnRunning: Boolean,
    turnStatus: String?,
    onRefresh: () -> Unit,
    onOpenFullscreen: (() -> Unit)?,
    onSelectInteraction: ((String) -> Unit)? = null,
    onFillInteraction: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lastTextLen = messages.lastOrNull()?.text?.length ?: 0
    val botByName = remember(bots) { bots.associateBy { it.name } }
    LaunchedEffect(activeChannel?.id, messages.size, lastTextLen, turnRunning) {
        if (messages.isNotEmpty() || turnRunning) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_chat_log).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textTertiary],
                modifier = Modifier.weight(1f),
            )
            if (activeChannel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        Lucide.RefreshCw,
                        contentDescription = stringResource(Res.string.tasks_chat_log),
                        onClick = onRefresh,
                    )
                    if (onOpenFullscreen != null) {
                        IconButton(
                            Lucide.ExternalLink,
                            contentDescription = stringResource(Res.string.tasks_open_fullscreen),
                            onClick = onOpenFullscreen,
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .scrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    activeChannel == null && emptyText == null && messages.isEmpty() && !turnRunning -> {
                        val hintShape = RoundedCornerShape(Theme[radii][radiusLarge])
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(hintShape)
                                .background(Theme[colors][surface])
                                .border(1.dp, Theme[colors][stroke], hintShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UnstyledIcon(Lucide.MessageSquare, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(15.dp))
                            Text(
                                stringResource(Res.string.tasks_no_channel),
                                style = Theme[typography][bodySmall],
                                color = Theme[colors][textSecondary],
                            )
                        }
                    }
                    loading && messages.isEmpty() && !turnRunning -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(Modifier.size(14.dp))
                        Text(stringResource(Res.string.tasks_chat_log), style = Theme[typography][bodySmall], color = Theme[colors][textTertiary])
                    }
                    error != null && messages.isEmpty() && !turnRunning -> {
                        val errShape = RoundedCornerShape(Theme[radii][radiusLarge])
                        Text(
                            error,
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][danger],
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(errShape)
                                .background(Theme[colors][surface])
                                .border(1.dp, Theme[colors][danger], errShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    messages.isEmpty() && !turnRunning -> Text(
                        emptyText ?: stringResource(Res.string.tasks_chat_empty),
                        style = Theme[typography][bodySmall],
                        color = Theme[colors][textTertiary],
                    )
                    else -> Unit
                }

                messages.forEach { line ->
                    if (line.fromUser) {
                        val bubbleShape = userBubbleShape()
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (line.attachments.isNotEmpty()) {
                                Box(Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End)) {
                                    SentAttachments(line.attachments)
                                }
                            }
                            if (line.text.isNotBlank()) {
                                Box(Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End)) {
                                    Text(
                                        line.text,
                                        style = Theme[typography][bodySmall],
                                        color = Theme[colors][onUserBubble],
                                        modifier = Modifier
                                            .glow(bubbleShape, bubbleGlow(Theme[colors][userBubble]))
                                            .clip(bubbleShape)
                                            .background(Theme[colors][userBubble], bubbleShape)
                                            .padding(horizontal = 13.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        val speakerName = line.speaker?.trim()?.takeIf { it.isNotEmpty() }
                        val speakerBot = speakerName?.let { name ->
                            botByName[name] ?: Bot(name = name, isDefault = name.equals(Bot.DEFAULT, ignoreCase = true))
                        }
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (speakerBot != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BotAvatar(
                                        bot = speakerBot,
                                        picture = botAvatars[speakerBot.name],
                                        size = 20.dp,
                                    )
                                    Text(
                                        "@${speakerBot.name}",
                                        style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                                        color = Theme[colors][accentText],
                                    )
                                }
                            }
                            if (line.text.isNotBlank()) {
                                MarkdownText(
                                    text = line.text,
                                    streaming = line.streaming,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            val interaction = line.interaction
                            if (interaction != null) {
                                ReplyInteractionCard(
                                    replyKey = line.key,
                                    interaction = interaction,
                                    sendEnabled = !turnRunning,
                                    onSend = { text -> onSelectInteraction?.invoke(text) },
                                    onFill = { text -> onFillInteraction?.invoke(text) },
                                )
                            }
                        }
                    }
                }

                if (turnRunning) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(Modifier.size(14.dp))
                        Text(
                            turnStatus ?: stringResource(Res.string.chat_thinking),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][accentText],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Theme[colors][sidebarColor]))),
            )
        }
    }
}

@Composable
private fun TaskStagedPlanSheet(
    task: DirectusTask,
    busy: Boolean,
    maxSheetHeight: Dp,
    onCollapse: () -> Unit,
    onPreparePlan: () -> Unit,
    onApprovePlan: (ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val plan = task.stagedPlan ?: return
    val approveFallback = stringResource(Res.string.tasks_approve_action)
    val sheetScroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .testTag("task_plan_sheet")
            .dropShadow(shape, DockShadow)
            .clip(shape)
            .background(Theme[colors][surface], shape)
            .border(1.dp, Theme[colors][strokeStrong], shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UnstyledIcon(
                    Lucide.Sparkles,
                    contentDescription = null,
                    tint = Theme[colors][accentText],
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(Res.string.tasks_plan_heading).uppercase(),
                    style = Theme[typography][eyebrow],
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plan.approvedAt == null) {
                    MiniBadge(
                        text = stringResource(Res.string.tasks_state_needs_you),
                        backgroundColor = Theme[colors][warningSoft],
                        contentColor = Theme[colors][warning],
                    )
                }
            }
            Button(
                text = stringResource(Res.string.tasks_refresh_plan),
                onClick = onPreparePlan,
                enabled = !busy,
                loading = busy,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                leadingIcon = Lucide.Sparkles,
            )
            IconButton(
                Lucide.ChevronDown,
                contentDescription = stringResource(Res.string.chat_collapse),
                onClick = onCollapse,
                modifier = Modifier.testTag("task_plan_sheet_collapse"),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .scrollbar(sheetScroll)
                .verticalScroll(sheetScroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
private fun TaskPlanDockBar(
    hasPlan: Boolean,
    pendingApproval: Boolean,
    expanded: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val enabled = hasPlan || !busy
    val borderColor = if (pendingApproval) Theme[colors][accent].copy(alpha = 0.55f) else Theme[colors][strokeStrong]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
            .testTag("task_plan_dock_button")
            .clip(shape)
            .background(if (pendingApproval) Theme[colors][accentSoft] else Theme[colors][surface], shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .handCursor(enabled)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy && !hasPlan) {
            Spinner(Modifier.size(14.dp))
        } else {
            UnstyledIcon(
                Lucide.Sparkles,
                contentDescription = null,
                tint = Theme[colors][accentText],
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            stringResource(if (hasPlan) Res.string.tasks_plan_heading else Res.string.tasks_prepare_plan),
            style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
            color = if (enabled) Theme[colors][textColor] else Theme[colors][textMuted],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pendingApproval) {
            MiniBadge(
                text = stringResource(Res.string.tasks_state_needs_you),
                backgroundColor = Theme[colors][warningSoft],
                contentColor = Theme[colors][warning],
            )
        }
        if (hasPlan) {
            UnstyledIcon(
                if (expanded) Lucide.ChevronDown else Lucide.ChevronUp,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun TaskChatComposer(
    state: TextFieldState,
    connected: Boolean,
    attachments: List<OutgoingAttachment>,
    channelChatState: ChatState,
    modelPicker: ModelPickerState,
    showModelPicker: Boolean,
    attachmentPicker: AttachmentPicker?,
    dictation: DictationState = DictationState(),
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
    placeholderText: String? = null,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onSend: () -> Unit,
) {
    val keyboardUp = imeVisible
    var focused by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf(false) }
    val canSend = (state.text.toString().isNotBlank() || attachments.isNotEmpty()) && connected
    val shape = RoundedCornerShape(Theme[radii][radiusXLarge])
    val borderColor = when {
        dropping -> Theme[colors][accent]
        focused -> Theme[colors][textTertiary]
        else -> Theme[colors][strokeStrong]
    }
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = if (keyboardUp) 8.dp else 12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (attachmentPicker != null) {
                        Modifier.fileDrop(onHover = { dropping = it }) { attachmentPicker.attachFiles(it) }
                    } else {
                        Modifier
                    },
                )
                .dropShadow(shape, DockShadow)
                .clip(shape)
                .background(Theme[colors][surface], shape)
                .border(if (dropping) 2.dp else 1.dp, borderColor, shape)
                .padding(
                    start = 4.dp,
                    end = 6.dp,
                    top = if (attachments.isEmpty()) 10.dp else 8.dp,
                    bottom = 6.dp,
                ),
        ) {
            if (attachments.isNotEmpty()) {
                ComposerTray(attachments, onRemove = onRemoveAttachment)
            }
            ComposerTextInput(
                state = state,
                maxHeightInLines = if (isDesktop) 10 else 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .padding(horizontal = 10.dp)
                    .testTag("task_chat_composer_input")
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (!enterSends) {
                            Modifier
                        } else {
                            Modifier.onPreviewKeyEvent { event ->
                                when {
                                    isPasteKey(event) -> attachmentPicker?.pasteAttachments() == true
                                    composerKey(event) == ComposerKey.Send && canSend -> {
                                        onSend()
                                        true
                                    }
                                    else -> false
                                }
                            }
                        },
                    ),
                placeholder = {
                    Text(
                        placeholderText ?: stringResource(Res.string.tasks_quick_steer_hint),
                        style = Theme[typography][body],
                        color = Theme[colors][textTertiary],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachButton(
                    onClick = onAttach,
                    enabled = connected && attachments.size < OutgoingAttachment.MAX_COUNT,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (showModelPicker) {
                        val hasModel = ModelSelection.of(channelChatState, modelPicker.catalog).model != null
                        if (hasModel) {
                            ModelPill(
                                state = channelChatState,
                                picker = modelPicker,
                                onClick = onOpenModels,
                            )
                        } else {
                            FallbackModelPill(
                                loading = modelPicker.loading,
                                enabled = connected,
                                onClick = onOpenModels,
                            )
                        }
                    }
                }
                Snug {
                    if (onToggleDictation != null && (hasVoice || dictation.active)) {
                        DictationButton(
                            state = dictation,
                            enabled = connected,
                            onClick = { onToggleDictation(state) },
                        )
                    }
                    SendButton(
                        icon = SendIcon.Send,
                        onClick = onSend,
                        enabled = canSend,
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackModelPill(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bounds = LocalModelPillBounds.current
    val discHeight = if (isDesktop) 30.dp else 36.dp
    val changeModel = stringResource(Res.string.chat_change_model)
    Box(
        Modifier
            .then(if (bounds != null) Modifier.onGloballyPositioned { bounds.value = it.boundsInWindow() } else Modifier)
            .heightIn(min = MinTouchTarget)
            .testTag("task_model_picker_fallback")
            .handCursor(enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = changeModel },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .heightIn(min = discHeight)
                .clip(CircleShape)
                .background(Theme[colors][surface3], CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (loading) {
                Spinner(Modifier.size(13.dp))
            } else {
                UnstyledIcon(
                    Lucide.Sparkles,
                    contentDescription = null,
                    tint = if (enabled) Theme[colors][accentText] else Theme[colors][textMuted],
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                stringResource(Res.string.chat_model_title),
                style = Theme[typography][caption],
                color = if (enabled) Theme[colors][textSecondary] else Theme[colors][textMuted],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UnstyledIcon(
                Lucide.ChevronDown,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun DirectusSetupDialog(
    config: dev.hermeskotlin.core.tasks.DirectusConfigStatus?,
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    saving: Boolean,
    error: String?,
    onOpenPetPicker: (() -> Unit)? = null,
    onResetCoordinatorSession: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (url: String, token: String, coordinator: DispatchCoordinatorUpdate?) -> Unit,
) {
    val urlState = rememberTextFieldState(config?.url.orEmpty())
    val tokenState = rememberTextFieldState("")
    val coord = config?.coordinator ?: DispatchCoordinatorConfig()
    var selectedProfile by remember(coord.profile) { mutableStateOf(coord.profile.ifBlank { "default" }) }
    val promptState = rememberTextFieldState(coord.defaultPrompt)
    var checkinEnabled by remember(coord.checkinEnabled) { mutableStateOf(coord.checkinEnabled) }
    var autoOpenOnLaunch by remember(coord.autoOpenOnLaunch) { mutableStateOf(coord.autoOpenOnLaunch) }
    val checkinTimesState = rememberTextFieldState(coord.checkinTimes.joinToString(", "))
    val windowState = rememberTextFieldState(coord.windowMinutes.toString())

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
                onClick = {
                    val parsedTimes = checkinTimesState.text.toString()
                        .split(',', ';')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    val parsedWindow = windowState.text.toString().trim().toIntOrNull() ?: coord.windowMinutes
                    onSave(
                        urlText,
                        tokenState.text.toString(),
                        DispatchCoordinatorUpdate(
                            profile = selectedProfile,
                            defaultPrompt = promptState.text.toString().trim(),
                            checkinEnabled = checkinEnabled,
                            checkinTimes = parsedTimes,
                            windowMinutes = parsedWindow,
                            autoOpenOnLaunch = autoOpenOnLaunch,
                        ),
                    )
                },
                enabled = canSave,
                loading = saving,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScrollWithBar(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            CoordinatorSettingsSection(
                bots = bots,
                botAvatars = botAvatars,
                selectedProfile = selectedProfile,
                onSelectProfile = { selectedProfile = it },
                promptState = promptState,
                checkinEnabled = checkinEnabled,
                onToggleCheckin = { checkinEnabled = it },
                autoOpenOnLaunch = autoOpenOnLaunch,
                onToggleAutoOpen = { autoOpenOnLaunch = it },
                checkinTimesState = checkinTimesState,
                windowState = windowState,
                onOpenPetPicker = onOpenPetPicker,
                onResetCoordinatorSession = onResetCoordinatorSession,
            )
            if (error != null) {
                Text(error, style = Theme[typography][bodySmall], color = Theme[colors][danger])
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoordinatorSettingsSection(
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    promptState: TextFieldState,
    checkinEnabled: Boolean,
    onToggleCheckin: (Boolean) -> Unit,
    autoOpenOnLaunch: Boolean,
    onToggleAutoOpen: (Boolean) -> Unit,
    checkinTimesState: TextFieldState,
    windowState: TextFieldState,
    onOpenPetPicker: (() -> Unit)?,
    onResetCoordinatorSession: (() -> Unit)?,
) {
    val visibleBots = remember(bots) {
        val list = bots.filterNot { it.meta.hidden }
        if (list.any { it.name == Bot.DEFAULT }) {
            list
        } else {
            listOf(Bot(name = Bot.DEFAULT, isDefault = true)) + list
        }
    }
    val activeBot = remember(visibleBots, selectedProfile) {
        visibleBots.firstOrNull { it.name == selectedProfile }
            ?: Bot(name = selectedProfile, isDefault = selectedProfile.equals(Bot.DEFAULT, ignoreCase = true))
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotAvatar(
            bot = activeBot,
            picture = botAvatars[activeBot.name],
            size = 28.dp,
        )
        Text(
            stringResource(Res.string.tasks_coordinator_settings_heading),
            style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
            color = Theme[colors][textColor],
            modifier = Modifier.weight(1f),
        )
        if (onOpenPetPicker != null) {
            Chip(
                text = stringResource(Res.string.tasks_coordinator_choose_pet),
                selected = false,
                onClick = onOpenPetPicker,
            )
        }
        if (onResetCoordinatorSession != null) {
            Chip(
                text = stringResource(Res.string.tasks_coordinator_reset_session),
                selected = false,
                onClick = onResetCoordinatorSession,
            )
        }
    }

    Text(
        stringResource(Res.string.tasks_coordinator_agent_label),
        style = Theme[typography][labelStyle],
        color = Theme[colors][textSecondary],
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibleBots.forEach { bot ->
            Chip(
                text = "${bot.label} (@${bot.name})",
                selected = selectedProfile == bot.name,
                onClick = { onSelectProfile(bot.name) },
            )
        }
    }

    TextField(
        state = promptState,
        label = stringResource(Res.string.tasks_coordinator_default_prompt),
        placeholder = stringResource(Res.string.tasks_coordinator_default_prompt_hint),
        singleLine = false,
        maxLines = 4,
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
            .background(Theme[colors][surface2])
            .toggleable(value = checkinEnabled, role = Role.Switch, onValueChange = onToggleCheckin)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.tasks_coordinator_checkin_toggle),
            style = Theme[typography][bodySmall],
            color = Theme[colors][textColor],
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checkinEnabled)
    }

    if (checkinEnabled) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                .background(Theme[colors][surface2])
                .toggleable(value = autoOpenOnLaunch, role = Role.Switch, onValueChange = onToggleAutoOpen)
                .handCursor()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.tasks_coordinator_auto_open_toggle),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textColor],
                modifier = Modifier.weight(1f),
            )
            Switch(checked = autoOpenOnLaunch)
        }

        TextField(
            state = checkinTimesState,
            label = stringResource(Res.string.tasks_coordinator_checkin_times),
            placeholder = "09:00, 16:00",
        )
        TextField(
            state = windowState,
            label = stringResource(Res.string.tasks_coordinator_checkin_window),
        )
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
private fun stateDotColor(task: DirectusTask, overdueDays: Int): Color = when {
    task.agentState == TaskContext.STATE_NEEDS_YOU -> Theme[colors][warning]
    task.status == DirectusTask.STATUS_BLOCKED || overdueDays > 0 -> Theme[colors][danger]
    task.agentState == TaskContext.STATE_RUNNING || task.agentState == TaskContext.STATE_QUEUED || task.status == DirectusTask.STATUS_IN_PROGRESS -> Theme[colors][accent]
    task.status == DirectusTask.STATUS_DONE -> Theme[colors][success]
    else -> Theme[colors][textTertiary]
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
