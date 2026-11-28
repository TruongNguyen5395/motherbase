package dev.hermeskotlin.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.PanelLeft
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.UpdateProposalRequest
import dev.hermeskotlin.core.chat.ChatState
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.media.MediaApi
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
import dev.hermeskotlin.core.tasks.DispatchCheckinSchedule
import dev.hermeskotlin.core.tasks.DispatchCoordinatorConfig
import dev.hermeskotlin.core.tasks.DispatchCoordinatorUpdate
import dev.hermeskotlin.core.tasks.EisenhowerQuadrant
import dev.hermeskotlin.core.tasks.MatrixLevel
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
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.MarkdownText
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
import dev.hermeskotlin.designsystem.successSoft
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
import dev.hermeskotlin.ui.LocalAppSettings
import dev.hermeskotlin.ui.PlatformBackHandler
import dev.hermeskotlin.ui.bots.BotAvatar
import dev.hermeskotlin.ui.calendar.ScheduleProposalConflictDialog
import dev.hermeskotlin.ui.calendar.ScheduleTaskBlockDialog
import dev.hermeskotlin.ui.chat.AttachButton
import dev.hermeskotlin.ui.chat.AttachSheet
import dev.hermeskotlin.ui.chat.AttachmentPicker
import dev.hermeskotlin.ui.chat.ComposerKey
import dev.hermeskotlin.ui.chat.ComposerTextInput
import dev.hermeskotlin.ui.chat.ComposerTray
import dev.hermeskotlin.ui.chat.DictationButton
import dev.hermeskotlin.ui.chat.DockShadow
import dev.hermeskotlin.ui.chat.InputRequestPanel
import dev.hermeskotlin.ui.chat.LocalModelPillBounds
import dev.hermeskotlin.ui.chat.ModelConfirmDialog
import dev.hermeskotlin.ui.chat.ModelPickerState
import dev.hermeskotlin.ui.chat.ModelPill
import dev.hermeskotlin.ui.chat.ModelSelection
import dev.hermeskotlin.ui.chat.ModelSheet
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
import dev.hermeskotlin.ui.pet.PetAvatar
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
import kotlin.math.roundToInt

internal data class TasksBoardActions(
    val onRefresh: () -> Unit = {},
    val onSaveConfig: (url: String, token: String) -> Unit = { _, _ -> },
    val onSaveConfigAndCoordinator: (url: String, token: String, coordinator: DispatchCoordinatorUpdate?) -> Unit = { u, t, _ -> onSaveConfig(u, t) },
    val onSaveCoordinatorConfig: (DispatchCoordinatorUpdate) -> Unit = {},
    val onTogglePriorityStrip: (Boolean) -> Unit = {},
    val onSetBoardViewMode: (TasksBoardViewMode) -> Unit = {},
    val onToggleIncludeDone: () -> Unit = {},
    val onToggleUnreadOnly: () -> Unit = {},
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
    val onUpdateTaskMatrix: (taskId: String, impact: MatrixLevel, urgency: MatrixLevel) -> Unit = { _, _, _ -> },
    val onCreateSessionChannel: (DirectusTask, profile: String?) -> Unit = { _, _ -> },
    val onCreateRoomChannel: (DirectusTask, members: List<Bot>) -> Unit = { _, _ -> },
    val onReplaceWithSession: (DirectusTask, oldChannel: TaskChannel, profile: String?) -> Unit = { _, _, _ -> },
    val onReplaceWithRoom: (DirectusTask, oldChannel: TaskChannel, members: List<Bot>) -> Unit = { _, _, _ -> },
    val onUnlinkChannel: (DirectusTask, channel: TaskChannel) -> Unit = { _, _ -> },
    val onSwitchToAssignedAgent: (DirectusTask) -> Unit = {},
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
    val onOpenScheduleBlock: (DirectusTask, DispatchTimeBlock?) -> Unit = { _, _ -> },
    val onDeleteTimeBlock: (String) -> Unit = {},
    val onOpenCalendar: (() -> Unit)? = null,
    val onApplyProposal: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    val onDiscardProposal: (String) -> Unit = {},
    val onRevertProposal: (String) -> Unit = {},
    val onUpdateProposal: (String, UpdateProposalRequest) -> Unit = { _, _ -> },
    val onRestageProposal: (String) -> Unit = {},
    val onDeleteProposal: (String) -> Unit = {},
)

private val DEFAULT_DISPATCH_RAIL_WIDTH = 460.dp
private val BENTO_TWO_COL_THRESHOLD = 620.dp
private val KANBAN_THREE_COL_THRESHOLD = 840.dp
private val PRIORITY_OPTIONS = listOf("low", "medium", "high", "urgent")

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
    onOpenCalendar: (() -> Unit)? = null,
    onToggleSidebar: (() -> Unit)? = null,
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val settingsStore = koinInject<SettingsStore>()
    val mediaApi = koinInject<MediaApi>()
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
                val activeCh = state.activeChannel
                val profile = when {
                    state.coordinatorOpen -> state.coordinatorConfig.profile.ifBlank { null }
                    activeCh != null -> activeCh.profile
                    else -> state.selectedTask?.assignedBots?.firstOrNull()
                }
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
            onSetBoardViewMode = viewModel::setBoardViewMode,
            onToggleIncludeDone = viewModel::toggleIncludeDone,
            onToggleUnreadOnly = viewModel::toggleUnreadOnly,
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
            onUpdateTaskMatrix = viewModel::updateTaskMatrix,
            onCreateSessionChannel = viewModel::openOrCreateSessionChannel,
            onCreateRoomChannel = viewModel::openOrCreateRoomChannel,
            onReplaceWithSession = viewModel::replaceWithSessionChannel,
            onReplaceWithRoom = viewModel::replaceWithRoomChannel,
            onUnlinkChannel = viewModel::unlinkChannel,
            onSwitchToAssignedAgent = viewModel::switchToAssignedAgent,
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
            onOpenScheduleBlock = viewModel::openScheduleTaskBlock,
            onDeleteTimeBlock = viewModel::deleteTaskTimeBlock,
            onOpenCalendar = onOpenCalendar,
            onApplyProposal = viewModel::applyScheduleProposal,
            onDiscardProposal = viewModel::discardScheduleProposal,
            onRevertProposal = viewModel::revertScheduleProposal,
            onUpdateProposal = viewModel::updateScheduleProposal,
            onRestageProposal = viewModel::restageScheduleProposal,
            onDeleteProposal = viewModel::deleteScheduleProposal,
        )
    }

    ProvideChannelMedia(
        gateway = gateway.gatewayUrl,
        activeChannel = state.activeChannel,
        mediaApi = mediaApi,
    ) {
        TasksBoardContent(
            state = state,
            actions = actions,
            onBack = onBack,
            onToggleSidebar = onToggleSidebar,
            attachmentPicker = attachmentPicker,
            petSprite = petSprite,
            onOpenPetPicker = { targetProfile ->
                val target = targetProfile.trim().ifEmpty { Bot.DEFAULT }
                petController.bind(target)
                petSheetOpen = true
            },
            dictation = dictation,
            onToggleDictation = onToggleDictation,
        )
    }

    PetSheet(
        visible = petSheetOpen,
        controller = petController,
        onDismiss = {
            petSheetOpen = false
            petController.bind(state.coordinatorConfig.profile.ifBlank { Bot.DEFAULT })
        },
        onAdopted = { petSheetOpen = false },
    )

    ScheduleTaskBlockDialog(
        draft = state.schedulingTaskDraft,
        freeSlots = state.freeSlots,
        findingSlots = state.findingFreeSlots,
        mutating = state.calendarMutating,
        onDismiss = viewModel::closeScheduleTaskBlock,
        onRefreshSlots = viewModel::refreshCalendarFreeSlots,
        onSave = viewModel::saveTaskTimeBlock,
        onDeleteBlock = viewModel::deleteTaskTimeBlock,
    )

    ScheduleProposalConflictDialog(
        conflict = state.pendingProposalConflict,
        busy = state.pendingProposalConflict?.proposalId in state.busyProposalIds,
        onDismiss = viewModel::dismissProposalConflict,
        onForceApply = viewModel::confirmForceApplyProposal,
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
    onOpenPetPicker: ((String) -> Unit)? = null,
    dictation: DictationState = DictationState(),
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
) {
    val railState = rememberRightRailState()
    var railTrigger by remember { mutableIntStateOf(0) }
    var setupDialogOpen by remember { mutableStateOf(false) }
    var newProjectOpen by remember { mutableStateOf(false) }
    var newTaskOpen by remember { mutableStateOf(false) }
    var pickSessionFor by remember { mutableStateOf<PickChannelTarget?>(null) }
    var pickRoomFor by remember { mutableStateOf<PickChannelTarget?>(null) }
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

    val persistedRailWidth = LocalAppSettings.current.dispatchRailWidth.dp
    var localRailWidth by remember(persistedRailWidth) { mutableStateOf(persistedRailWidth) }
    val resizeHandler = remember(actions) {
        { width: Dp ->
            localRailWidth = width
            actions.onUpdateSettings { it.copy(dispatchRailWidth = width.value.roundToInt()) }
        }
    }

    CompositionLocalProvider(LocalModelPillBounds provides modelPillBounds) {
    RightRailLayout(
        state = railState,
        railWidth = localRailWidth,
        defaultRailWidth = DEFAULT_DISPATCH_RAIL_WIDTH,
        onRailWidthChange = resizeHandler.takeIf { isDesktop },
        swipeEnabled = !isDesktop && railOpenTarget,
        dockOnWide = true,
        onDismiss = {
            if (state.coordinatorOpen) actions.onCloseCoordinator()
            else if (state.selectedTaskId != null) actions.onSelectTask(null)
        },
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
                    onOpenSettings = { setupDialogOpen = true },
                    onQuickAction = actions.onQuickCoordinatorAction,
                    onSendToCoordinator = actions.onSendToCoordinator,
                    onRemoveAttachment = actions.onRemoveAttachment,
                    onAttach = {
                        if (isDesktop) attachmentPicker?.pickFiles() else attachOpen = true
                    },
                    onOpenModels = { modelsOpen = true },
                    onAnswerRequest = actions.onAnswerRequest,
                    appliedProposalStatuses = state.proposalStatuses,
                    updatedProposals = state.updatedProposals,
                    deletedProposalIds = state.deletedProposalIds,
                    onApplyProposal = actions.onApplyProposal,
                    onDiscardProposal = actions.onDiscardProposal,
                    onRevertProposal = actions.onRevertProposal,
                    onUpdateProposal = actions.onUpdateProposal,
                    onRestageProposal = actions.onRestageProposal,
                    onDeleteProposal = actions.onDeleteProposal,
                )
            } else if (selected != null) {
                TaskDetailInspector(
                    task = selected,
                    projects = state.projects,
                    bots = state.bots,
                    botAvatars = state.botAvatars,
                    todayEpochDay = state.todayEpochDay,
                    coordinatorBotName = state.coordinatorBot.name,
                    coordinatorPetSprite = petSprite,
                    coordinatorPetState = coordinatorPetState,
                    busy = selected.id in state.busyTaskIds,
                    running = selected.id in state.runningTaskIds,
                    timeBlocks = state.taskTimeBlocks[selected.id].orEmpty(),
                    calendarPluginAvailable = state.calendarPluginAvailable,
                    calendarTimezone = state.calendarTimezone,
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
                    onUpdateMatrix = { imp, urg -> actions.onUpdateTaskMatrix(selected.id, imp, urg) },
                    onSelectChannel = actions.onSelectChannel,
                    onRefreshChannel = actions.onRefreshChannel,
                    onOpenPickSession = { pickSessionFor = PickChannelTarget(selected) },
                    onOpenPickRoom = { pickRoomFor = PickChannelTarget(selected) },
                    onReplaceWithSession = { ch -> pickSessionFor = PickChannelTarget(selected, replaceChannel = ch) },
                    onReplaceWithRoom = { ch -> pickRoomFor = PickChannelTarget(selected, replaceChannel = ch) },
                    onUnlinkChannel = { ch -> actions.onUnlinkChannel(selected, ch) },
                    onSwitchToAssignedAgent = { actions.onSwitchToAssignedAgent(selected) },
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
                    appliedProposalStatuses = state.proposalStatuses,
                    updatedProposals = state.updatedProposals,
                    deletedProposalIds = state.deletedProposalIds,
                    onApplyProposal = actions.onApplyProposal,
                    onDiscardProposal = actions.onDiscardProposal,
                    onRevertProposal = actions.onRevertProposal,
                    onUpdateProposal = actions.onUpdateProposal,
                    onRestageProposal = actions.onRestageProposal,
                    onDeleteProposal = actions.onDeleteProposal,
                    onOpenScheduleBlock = { blk -> actions.onOpenScheduleBlock(selected, blk) },
                    onDeleteTimeBlock = actions.onDeleteTimeBlock,
                    onOpenCalendar = actions.onOpenCalendar,
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
                        contentDescription = stringResource(Res.string.tasks_dispatch_settings_title),
                        onClick = { setupDialogOpen = true },
                    )
                },
            )

            if (configured) {
                BoardControlsBar(
                    state = state,
                    onTogglePriorityStrip = actions.onTogglePriorityStrip,
                    onSetBoardViewMode = actions.onSetBoardViewMode,
                    onToggleIncludeDone = actions.onToggleIncludeDone,
                    onToggleUnreadOnly = actions.onToggleUnreadOnly,
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
                        onUpdateTaskMatrix = actions.onUpdateTaskMatrix,
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
                            .navigationBarsPadding()
                            .padding(end = 16.dp, bottom = if (isDesktop) 16.dp else 20.dp),
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
            petSprite = petSprite,
            petState = coordinatorPetState,
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

    pickSessionFor?.let { target ->
        PickSessionBotDialog(
            bots = state.bots,
            onDismiss = { pickSessionFor = null },
            onPick = { profile ->
                if (target.replaceChannel != null) {
                    actions.onReplaceWithSession(target.task, target.replaceChannel, profile)
                } else {
                    actions.onCreateSessionChannel(target.task, profile)
                }
                pickSessionFor = null
            },
        )
    }

    pickRoomFor?.let { target ->
        PickRoomBotsDialog(
            bots = state.bots,
            confirmLabel = if (target.replaceChannel != null) {
                stringResource(Res.string.tasks_replace_room_confirm)
            } else {
                stringResource(Res.string.tasks_add_room)
            },
            onDismiss = { pickRoomFor = null },
            onCreate = { members ->
                if (target.replaceChannel != null) {
                    actions.onReplaceWithRoom(target.task, target.replaceChannel, members)
                } else {
                    actions.onCreateRoomChannel(target.task, members)
                }
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
    onSetBoardViewMode: (TasksBoardViewMode) -> Unit,
    onToggleIncludeDone: () -> Unit,
    onToggleUnreadOnly: () -> Unit,
    onFocusProject: (String?) -> Unit,
    onSetFocusGrouping: (TaskFocusGrouping) -> Unit,
) {
    val buckets = remember(state.projects, state.tasks, state.todayEpochDay, state.replyBadges) {
        buildProjectBuckets(state.projects, state.tasks, state.todayEpochDay, state.replyBadges)
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

            if (state.focusedProjectId == null) {
                Chip(
                    text = stringResource(Res.string.tasks_view_projects),
                    selected = state.boardViewMode == TasksBoardViewMode.PROJECTS,
                    onClick = { onSetBoardViewMode(TasksBoardViewMode.PROJECTS) },
                )
                Chip(
                    text = stringResource(Res.string.tasks_view_matrix),
                    selected = state.boardViewMode == TasksBoardViewMode.MATRIX,
                    onClick = { onSetBoardViewMode(TasksBoardViewMode.MATRIX) },
                )
            } else {
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
                Chip(
                    text = stringResource(Res.string.tasks_focus_group_matrix),
                    selected = state.focusGrouping == TaskFocusGrouping.MATRIX,
                    onClick = { onSetFocusGrouping(TaskFocusGrouping.MATRIX) },
                )
            }

            Chip(
                text = stringResource(Res.string.tasks_filter_unread),
                count = state.unreadCount.takeIf { it > 0 },
                countColor = Theme[colors][success],
                selected = state.unreadOnly,
                onClick = onToggleUnreadOnly,
                modifier = Modifier.testTag("board_filter_unread"),
            )

            Chip(
                text = stringResource(if (state.includeDone) Res.string.tasks_hide_done else Res.string.tasks_show_done),
                selected = state.includeDone,
                onClick = onToggleIncludeDone,
            )
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
    onUpdateTaskMatrix: (taskId: String, impact: MatrixLevel, urgency: MatrixLevel) -> Unit,
) {
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)
    val standaloneLabel = stringResource(Res.string.tasks_standalone_group)
    val replyBadges = state.replyBadges
    val runningTaskIds = state.runningTaskIds
    val visibleTasks = remember(state.tasks, state.unreadOnly, replyBadges) {
        if (state.unreadOnly) {
            state.tasks.filter { (replyBadges[it.id] ?: TaskReplyBadge.NONE) != TaskReplyBadge.NONE }
        } else {
            state.tasks
        }
    }
    val allBuckets = remember(state.projects, visibleTasks, state.todayEpochDay, replyBadges, state.unreadOnly) {
        val raw = buildProjectBuckets(state.projects, visibleTasks, state.todayEpochDay, replyBadges)
        if (state.unreadOnly) raw.filter { it.tasks.isNotEmpty() } else raw
    }
    val projectById = remember(state.projects) {
        state.projects.associateBy { it.id }
    }
    val focusedBucket = remember(state.projects, visibleTasks, state.focusedProjectId, state.todayEpochDay, replyBadges) {
        state.focusedProjectId?.let { fid ->
            buildProjectBuckets(state.projects, visibleTasks, state.todayEpochDay, replyBadges).firstOrNull { it.key == fid }
        }
    }
    val stripSplit = remember(focusedBucket, visibleTasks, state.todayEpochDay, replyBadges) {
        if (focusedBucket != null) {
            buildAttentionSplit(focusedBucket.tasks, state.todayEpochDay, replyBadges)
        } else {
            state.attentionSplit
        }
    }

    if (state.tasks.isEmpty() && allBuckets.all { it.project == null }) {
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

    if (state.unreadOnly && visibleTasks.isEmpty()) {
        EmptyState(
            icon = Lucide.Check,
            title = stringResource(Res.string.tasks_unread_empty),
            body = stringResource(Res.string.tasks_unread_empty_body),
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
            if (state.showPriorityStrip && !state.unreadOnly) {
                item(key = "attention-strip") {
                    AttentionStripSection(
                        split = stripSplit,
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        todayEpochDay = state.todayEpochDay,
                        replyBadges = replyBadges,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        runningTaskIds = runningTaskIds,
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
                } else if (state.focusGrouping == TaskFocusGrouping.MATRIX) {
                    item(key = "focus-matrix-${focusedBucket.key}") {
                        TasksEisenhowerMatrixBoard(
                            tasks = focusedBucket.tasks,
                            projectById = projectById,
                            unassignedLabel = unassignedLabel,
                            showProjectBadge = false,
                            todayEpochDay = state.todayEpochDay,
                            replyBadges = replyBadges,
                            wideLayout = columns == 2,
                            selectedTaskId = state.selectedTaskId,
                            busyTaskIds = state.busyTaskIds,
                            runningTaskIds = runningTaskIds,
                            onSelectTask = onSelectTask,
                            onUpdateTaskMatrix = onUpdateTaskMatrix,
                        )
                    }
                } else if (state.focusGrouping == TaskFocusGrouping.WORKSTREAM) {
                    val workstreams = buildWorkstreamGroups(
                        tasks = focusedBucket.tasks,
                        todayEpochDay = state.todayEpochDay,
                        standaloneTitle = standaloneLabel,
                        replyBadges = replyBadges,
                    )
                    items(workstreams.size, key = { idx -> "wg-${workstreams[idx].key}" }) { idx ->
                        val group = workstreams[idx]
                        WorkstreamSectionCard(
                            group = group,
                            projectLabel = focusedBucket.project?.name ?: unassignedLabel,
                            todayEpochDay = state.todayEpochDay,
                            replyBadges = replyBadges,
                            twoColumns = columns == 2,
                            collapsed = group.key in state.collapsedWorkstreamKeys,
                            selectedTaskId = state.selectedTaskId,
                            busyTaskIds = state.busyTaskIds,
                            runningTaskIds = runningTaskIds,
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
                            replyBadges = replyBadges,
                            includeDone = state.includeDone,
                            wideThreeCols = kanbanWide,
                            selectedTaskId = state.selectedTaskId,
                            busyTaskIds = state.busyTaskIds,
                            runningTaskIds = runningTaskIds,
                            onSelectTask = onSelectTask,
                        )
                    }
                }
            } else if (state.boardViewMode == TasksBoardViewMode.MATRIX) {
                item(key = "board-matrix") {
                    TasksEisenhowerMatrixBoard(
                        tasks = visibleTasks,
                        projectById = projectById,
                        unassignedLabel = unassignedLabel,
                        showProjectBadge = true,
                        todayEpochDay = state.todayEpochDay,
                        replyBadges = replyBadges,
                        wideLayout = columns == 2,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        runningTaskIds = runningTaskIds,
                        onSelectTask = onSelectTask,
                        onUpdateTaskMatrix = onUpdateTaskMatrix,
                    )
                }
            } else if (columns == 2) {
                val (leftBuckets, rightBuckets) = distributeBentoMasonry(
                    buckets = allBuckets,
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
                items(allBuckets.size, key = { idx -> allBuckets[idx].key }) { idx ->
                    val bucket = allBuckets[idx]
                    ProjectBentoCard(
                        bucket = bucket,
                        projectLabel = bucket.project?.name ?: unassignedLabel,
                        todayEpochDay = state.todayEpochDay,
                        replyBadges = replyBadges,
                        expanded = bucket.key in state.expandedProjectIds,
                        selectedTaskId = state.selectedTaskId,
                        busyTaskIds = state.busyTaskIds,
                        runningTaskIds = runningTaskIds,
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
    replyBadges: Map<String, TaskReplyBadge>,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    runningTaskIds: Set<String> = emptySet(),
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
                stringResource(Res.string.tasks_attention_empty_v2),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textSecondary],
            )
        }
        return
    }

    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val stripeColor = Theme[colors][danger]
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
            UnstyledIcon(
                Lucide.Target,
                contentDescription = null,
                tint = stripeColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(Res.string.tasks_priority_section, split.totalCount).uppercase(),
                style = Theme[typography][eyebrow],
                color = Theme[colors][textColor],
            )
        }

        if (split.highPriority.isNotEmpty()) {
            AttentionSubgroupColumn(
                heading = stringResource(Res.string.tasks_attention_high_priority, split.highPriority.size + split.extraHighPriorityCount),
                headingColor = Theme[colors][danger],
                tasks = split.highPriority,
                extraCount = split.extraHighPriorityCount,
                projectById = projectById,
                unassignedLabel = unassignedLabel,
                todayEpochDay = todayEpochDay,
                replyBadges = replyBadges,
                selectedTaskId = selectedTaskId,
                busyTaskIds = busyTaskIds,
                runningTaskIds = runningTaskIds,
                onSelectTask = onSelectTask,
                modifier = Modifier.fillMaxWidth(),
            )
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
    replyBadges: Map<String, TaskReplyBadge>,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    runningTaskIds: Set<String> = emptySet(),
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
            val badge = replyBadges[task.id] ?: TaskReplyBadge.NONE
            TaskPill(
                task = task,
                projectName = projectName,
                showProjectBadge = true,
                todayEpochDay = todayEpochDay,
                replyBadge = badge,
                selected = task.id == selectedTaskId,
                busy = task.id in busyTaskIds,
                running = task.id in runningTaskIds,
                onClick = { onSelectTask(task.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (extraCount > 0) {
            Text(
                stringResource(Res.string.tasks_attention_more_priority, extraCount),
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
    val replyBadges = state.replyBadges
    val runningTaskIds = state.runningTaskIds
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buckets.forEach { bucket ->
            ProjectBentoCard(
                bucket = bucket,
                projectLabel = bucket.project?.name ?: unassignedLabel,
                todayEpochDay = state.todayEpochDay,
                replyBadges = replyBadges,
                expanded = bucket.key in state.expandedProjectIds,
                selectedTaskId = state.selectedTaskId,
                busyTaskIds = state.busyTaskIds,
                runningTaskIds = runningTaskIds,
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
    val folderTint = Theme[colors][textTertiary]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.Folder, contentDescription = null, tint = folderTint, modifier = Modifier.size(16.dp))
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
    replyBadges: Map<String, TaskReplyBadge>,
    twoColumns: Boolean,
    collapsed: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    runningTaskIds: Set<String> = emptySet(),
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
            if (group.unreadCount > 0) {
                UnreadCountIndicator(group.unreadCount, Modifier.testTag("workstream_unread_${group.key}"))
            }
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
                                val badge = replyBadges[task.id] ?: TaskReplyBadge.NONE
                                TaskPill(
                                    task = task,
                                    projectName = projectLabel,
                                    showProjectBadge = false,
                                    todayEpochDay = todayEpochDay,
                                    replyBadge = badge,
                                    selected = task.id == selectedTaskId,
                                    busy = task.id in busyTaskIds,
                                    running = task.id in runningTaskIds,
                                    onClick = { onSelectTask(task.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    group.tasks.forEach { task ->
                        val badge = replyBadges[task.id] ?: TaskReplyBadge.NONE
                        TaskPill(
                            task = task,
                            projectName = projectLabel,
                            showProjectBadge = false,
                            todayEpochDay = todayEpochDay,
                            replyBadge = badge,
                            selected = task.id == selectedTaskId,
                            busy = task.id in busyTaskIds,
                            running = task.id in runningTaskIds,
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
    replyBadges: Map<String, TaskReplyBadge>,
    includeDone: Boolean,
    wideThreeCols: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    runningTaskIds: Set<String> = emptySet(),
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
                    replyBadges = replyBadges,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    runningTaskIds = runningTaskIds,
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
                    replyBadges = replyBadges,
                    selectedTaskId = selectedTaskId,
                    busyTaskIds = busyTaskIds,
                    runningTaskIds = runningTaskIds,
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
    replyBadges: Map<String, TaskReplyBadge>,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    runningTaskIds: Set<String> = emptySet(),
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
            val badge = replyBadges[task.id] ?: TaskReplyBadge.NONE
            TaskPill(
                task = task,
                projectName = projectLabel,
                showProjectBadge = false,
                todayEpochDay = todayEpochDay,
                replyBadge = badge,
                selected = task.id == selectedTaskId,
                busy = task.id in busyTaskIds,
                running = task.id in runningTaskIds,
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
    replyBadges: Map<String, TaskReplyBadge>,
    expanded: Boolean,
    selectedTaskId: String?,
    busyTaskIds: Set<String>,
    runningTaskIds: Set<String> = emptySet(),
    onSelectTask: (String?) -> Unit,
    onToggleExpand: () -> Unit,
    onFocusProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = bucket.project
    val tasks = bucket.tasks
    val hiddenCount = (tasks.size - DEFAULT_PROJECT_PREVIEW_LIMIT).coerceAtLeast(0)
    val shown = if (expanded || hiddenCount == 0) tasks else tasks.take(DEFAULT_PROJECT_PREVIEW_LIMIT)
    val alertCount = tasks.count { isHighPriorityAttention(it, todayEpochDay) }
    val unreadCount = tasks.count { (replyBadges[it.id] ?: TaskReplyBadge.NONE) != TaskReplyBadge.NONE }
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val folderTint = Theme[colors][textTertiary]

    Column(
        modifier
            .clip(shape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke], shape),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Theme[colors][surface2])
                .clickable(role = Role.Button, onClick = onFocusProject)
                .handCursor()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UnstyledIcon(Lucide.Folder, contentDescription = null, tint = folderTint, modifier = Modifier.size(15.dp))
                Text(
                    projectLabel,
                    style = Theme[typography][body].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unreadCount > 0) {
                    UnreadCountIndicator(unreadCount, Modifier.testTag("project_unread_${bucket.key}"))
                }
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
                    modifier = Modifier.padding(start = 23.dp),
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][strokeStrong]))

        if (tasks.isEmpty()) {
            Text(
                stringResource(Res.string.tasks_project_empty),
                style = Theme[typography][bodySmall],
                color = Theme[colors][textTertiary],
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        } else {
            shown.forEachIndexed { idx, task ->
                if (idx > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))
                }
                val badge = replyBadges[task.id] ?: TaskReplyBadge.NONE
                TaskPill(
                    task = task,
                    projectName = projectLabel,
                    showProjectBadge = false,
                    todayEpochDay = todayEpochDay,
                    replyBadge = badge,
                    selected = task.id == selectedTaskId,
                    busy = task.id in busyTaskIds,
                    running = task.id in runningTaskIds,
                    onClick = { onSelectTask(task.id) },
                    modifier = Modifier.fillMaxWidth(),
                    flat = true,
                )
            }
        }

        if (hiddenCount > 0) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
    flat: Boolean = false,
    replyBadge: TaskReplyBadge = TaskReplyBadge.NONE,
    running: Boolean = task.agentState == TaskContext.STATE_RUNNING,
) {
    val overdueDays = taskOverdueDays(task, todayEpochDay)
    val dueEpochDay = parseDueEpochDay(task.dueDate)
    val matrixPos = resolveTaskMatrix(task, todayEpochDay)
    val isDoNow = task.status != DirectusTask.STATUS_DONE && matrixPos.quadrant == EisenhowerQuadrant.DO_NOW
    val isWaitingInput = replyBadge == TaskReplyBadge.WAITING_INPUT
    val isUnread = replyBadge == TaskReplyBadge.UNREAD || isWaitingInput
    val isRisk = task.status == DirectusTask.STATUS_BLOCKED || overdueDays > 0 || isDoNow
    val surfaceColor = Theme[colors][surface]
    val fill = when {
        selected -> Theme[colors][accentSoft].compositeOver(surfaceColor)
        isWaitingInput -> Theme[colors][dangerSoft].compositeOver(surfaceColor)
        isRisk -> Theme[colors][dangerSoft].compositeOver(surfaceColor)
        isUnread -> Theme[colors][successSoft].compositeOver(surfaceColor)
        !flat -> Theme[colors][surface2]
        else -> Color.Transparent
    }
    val edgeStripeColor = when {
        selected && flat -> Theme[colors][accent]
        selected -> null
        isWaitingInput -> Theme[colors][danger]
        isUnread -> Theme[colors][success]
        flat && isRisk -> Theme[colors][danger]
        else -> null
    }
    val containerModifier = if (flat) {
        modifier
            .background(fill)
            .leftEdgeAccentStripe(edgeStripeColor)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 9.dp)
    } else {
        val shape = RoundedCornerShape(Theme[radii][radiusMedium])
        val borderColor = when {
            selected -> Theme[colors][accent]
            isWaitingInput -> Theme[colors][danger]
            isRisk -> Theme[colors][danger].copy(alpha = 0.5f)
            isUnread -> Theme[colors][success].copy(alpha = 0.6f)
            else -> Theme[colors][stroke]
        }
        val borderWidth = if (selected) 1.5.dp else 1.dp
        modifier
            .clip(shape)
            .background(fill)
            .leftEdgeAccentStripe(edgeStripeColor)
            .border(borderWidth, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    }
    val dotColor = when {
        isDoNow -> Theme[colors][danger]
        else -> stateDotColor(task, overdueDays)
    }
    val neutralBadgeBg = if (flat) Theme[colors][surface2] else surfaceColor
    val showMatrixBadge = task.status != DirectusTask.STATUS_DONE &&
        (isDoNow || task.impact != null || task.urgency != null)
    val showRunningBadge = running || task.agentState == TaskContext.STATE_QUEUED
    val hasMetaBadges = showProjectBadge ||
        showMatrixBadge ||
        showRunningBadge ||
        task.status != DirectusTask.STATUS_TODO ||
        overdueDays > 0 ||
        (dueEpochDay != null && task.status != DirectusTask.STATUS_DONE) ||
        task.assignedBots.isNotEmpty()

    Column(
        containerModifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy || running) {
                Spinner(
                    Modifier.size(14.dp).testTag("task_running_spinner_${task.id}"),
                    color = Theme[colors][accent],
                )
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            }
            Text(
                task.title,
                style = Theme[typography][bodySmall].copy(
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = Theme[colors][textColor],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isWaitingInput || isUnread) {
                UnreadReviewIndicator(
                    waitingInput = isWaitingInput,
                    modifier = Modifier.testTag("task_indicator_${task.id}"),
                )
            }
        }

        if (hasMetaBadges) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMatrixBadge) {
                    MiniBadge(
                        text = "${quadrantTitle(matrixPos.quadrant)} · ${matrixLevelShortCode(matrixPos.impact)}/${matrixLevelShortCode(matrixPos.effectiveUrgency)}",
                        backgroundColor = quadrantSoftColor(matrixPos.quadrant),
                        contentColor = quadrantAccentColor(matrixPos.quadrant),
                    )
                }
                if (showProjectBadge) {
                    MiniBadge(
                        text = projectName,
                        backgroundColor = Theme[colors][surface3],
                        contentColor = Theme[colors][textSecondary],
                    )
                }
                if (showRunningBadge) {
                    RunningPillBadge(
                        agentState = task.agentState,
                        isRunning = running,
                    )
                }
                if (task.status != DirectusTask.STATUS_TODO) {
                    val isBlocked = task.status == DirectusTask.STATUS_BLOCKED
                    MiniBadge(
                        text = taskStatusLabel(task.status),
                        backgroundColor = if (isBlocked) Theme[colors][dangerSoft] else neutralBadgeBg,
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
                            backgroundColor = neutralBadgeBg,
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
    AgentFloatingTrigger(
        bot = bot,
        avatarBytes = avatarBytes,
        petSprite = petSprite,
        petState = petState,
        badgeActive = checkinDue,
        busy = busy,
        contentDescription = stringResource(Res.string.tasks_coordinator_ask, bot.label),
        testTag = "dispatch_coordinator_button",
        onClick = onClick,
        modifier = modifier,
    )
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
    onOpenSettings: () -> Unit,
    onQuickAction: (CoordinatorQuickAction) -> Unit,
    onSendToCoordinator: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
    appliedProposalStatuses: Map<String, String> = emptyMap(),
    updatedProposals: Map<String, ScheduleProposal> = emptyMap(),
    deletedProposalIds: Set<String> = emptySet(),
    onApplyProposal: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    onDiscardProposal: (String) -> Unit = {},
    onRevertProposal: (String) -> Unit = {},
    onUpdateProposal: (String, UpdateProposalRequest) -> Unit = { _, _ -> },
    onRestageProposal: (String) -> Unit = {},
    onDeleteProposal: (String) -> Unit = {},
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.semantics { contentDescription = "${coordinatorBot.label} · @${coordinatorBot.name}" }) {
                if (petSprite != null) {
                    PetAvatar(
                        sprite = petSprite,
                        state = petState,
                        size = 30.dp,
                    )
                } else {
                    BotAvatar(
                        bot = coordinatorBot,
                        picture = botAvatars[coordinatorBot.name],
                        size = 30.dp,
                    )
                }
            }
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (checkinDue) {
                    MiniBadge(
                        text = stringResource(Res.string.tasks_coordinator_checkin_ready),
                        backgroundColor = Theme[colors][warningSoft],
                        contentColor = Theme[colors][warning],
                    )
                }
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
                contentDescription = stringResource(Res.string.tasks_dispatch_settings_title),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("coordinator_settings_button"),
            )
            IconButton(
                Lucide.X,
                contentDescription = stringResource(Res.string.tasks_close_inspector),
                onClick = onClose,
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        TaskChannelLog(
            activeChannel = activeChannel,
            loading = channelLoading,
            messages = channelMessages,
            bots = bots,
            botAvatars = botAvatars,
            coordinatorBotName = coordinatorBot.name,
            petSprite = petSprite,
            petState = petState,
            emptyText = stringResource(Res.string.tasks_coordinator_empty),
            error = channelError,
            turnRunning = turnBusy,
            turnStatus = channelTurnStatus,
            onRefresh = onRefreshChannel,
            onOpenFullscreen = if (activeChannel != null) onOpenFullscreen else null,
            onSelectInteraction = onSendToCoordinator,
            onFillInteraction = { text -> steerState.setTextAndPlaceCursorAtEnd(text) },
            appliedProposalStatuses = appliedProposalStatuses,
            updatedProposals = updatedProposals,
            deletedProposalIds = deletedProposalIds,
            onApplyProposal = onApplyProposal,
            onDiscardProposal = onDiscardProposal,
            onRevertProposal = onRevertProposal,
            onUpdateProposal = onUpdateProposal,
            onRestageProposal = onRestageProposal,
            onDeleteProposal = onDeleteProposal,
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
    todayEpochDay: Long,
    coordinatorBotName: String? = null,
    coordinatorPetSprite: PetSprite? = null,
    coordinatorPetState: PetState = PetState.Idle,
    busy: Boolean,
    running: Boolean = false,
    timeBlocks: List<DispatchTimeBlock> = emptyList(),
    calendarPluginAvailable: Boolean = false,
    calendarTimezone: String = "Asia/Ho_Chi_Minh",
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
    onUpdateMatrix: (MatrixLevel, MatrixLevel) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onRefreshChannel: () -> Unit,
    onOpenPickSession: () -> Unit,
    onOpenPickRoom: () -> Unit,
    onReplaceWithSession: (TaskChannel) -> Unit = {},
    onReplaceWithRoom: (TaskChannel) -> Unit = {},
    onUnlinkChannel: (TaskChannel) -> Unit = {},
    onSwitchToAssignedAgent: () -> Unit = {},
    onOpenFullscreen: (TaskChannel) -> Unit,
    onPreparePlan: () -> Unit,
    onApprovePlan: (ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit,
    onSendToChannel: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
    appliedProposalStatuses: Map<String, String> = emptyMap(),
    updatedProposals: Map<String, ScheduleProposal> = emptyMap(),
    deletedProposalIds: Set<String> = emptySet(),
    onApplyProposal: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    onDiscardProposal: (String) -> Unit = {},
    onRevertProposal: (String) -> Unit = {},
    onUpdateProposal: (String, UpdateProposalRequest) -> Unit = { _, _ -> },
    onRestageProposal: (String) -> Unit = {},
    onDeleteProposal: (String) -> Unit = {},
    onOpenScheduleBlock: (DispatchTimeBlock?) -> Unit = {},
    onDeleteTimeBlock: (String) -> Unit = {},
    onOpenCalendar: (() -> Unit)? = null,
) {
    val steerState = rememberTextFieldState("")
    LaunchedEffect(task.id) { steerState.clearText() }
    var detailsExpanded by remember(task.id) { mutableStateOf(false) }
    var descExpanded by remember(task.id) { mutableStateOf(false) }
    var matrixPadOpen by remember(task.id) { mutableStateOf(false) }
    val matrixPos = remember(task, todayEpochDay) { resolveTaskMatrix(task, todayEpochDay) }
    val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)
    val currentProjectLabel = projects.firstOrNull { it.id == task.projectId }?.name ?: unassignedLabel
    val plan = task.stagedPlan
    val metaScroll = rememberScrollState()
    var planSheetOpen by remember(task.id) { mutableStateOf(false) }
    var openPlanOnArrival by remember(task.id) { mutableStateOf(false) }
    var lastSeenPlanStamp by remember(task.id) { mutableStateOf(plan?.generatedAt to plan?.summary) }

    // Keep the proposal sheet collapsed when entering a task so the chat log stays visible;
    // auto-open only when a newly prepared/refreshed plan arrives after an explicit trigger.
    LaunchedEffect(task.id, plan?.generatedAt, plan?.summary) {
        val currentStamp = plan?.generatedAt to plan?.summary
        if (plan != null && openPlanOnArrival && currentStamp != lastSeenPlanStamp && channelInputRequests.isEmpty()) {
            planSheetOpen = true
            openPlanOnArrival = false
        }
        lastSeenPlanStamp = currentStamp
    }
    LaunchedEffect(channelInputRequests.isNotEmpty()) {
        if (channelInputRequests.isNotEmpty()) planSheetOpen = false
    }
    val triggerPreparePlan: () -> Unit = {
        openPlanOnArrival = true
        onPreparePlan()
    }

    Column(Modifier.fillMaxSize()) {
        // Pinned inspector header on sidebarColor ground to match left SessionsSidebar.
        Row(
            Modifier
                .fillMaxWidth()
                .clearOfWindowControls()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
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

        // Single-line compact metadata bar + collapsible Details drawer so live chat gets full height by default.
        val matrixCode = "${matrixLevelShortCode(matrixPos.impact)}/${matrixLevelShortCode(matrixPos.effectiveUrgency)}"
        val summaryText = "$currentProjectLabel · ${taskStatusLabel(task.status)} · ${quadrantTitle(matrixPos.quadrant)} $matrixCode"
        val toggleShape = RoundedCornerShape(Theme[radii][radiusSmall])
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (running) {
                    Spinner(Modifier.size(12.dp), color = Theme[colors][accent])
                } else {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(quadrantAccentColor(matrixPos.quadrant)),
                    )
                }
                Text(
                    summaryText,
                    style = Theme[typography][caption],
                    color = Theme[colors][textSecondary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (running || task.agentState == TaskContext.STATE_QUEUED) {
                    RunningPillBadge(agentState = task.agentState, isRunning = running)
                }
            }
            Row(
                Modifier
                    .testTag("task_inspector_details_toggle")
                    .clip(toggleShape)
                    .background(if (detailsExpanded) Theme[colors][accentSoft] else Theme[colors][surface])
                    .border(
                        1.dp,
                        if (detailsExpanded) Theme[colors][accent].copy(alpha = 0.45f) else Theme[colors][strokeStrong],
                        toggleShape,
                    )
                    .clickable(role = Role.Button) { detailsExpanded = !detailsExpanded }
                    .handCursor()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.tasks_inspector_details),
                    style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
                    color = if (detailsExpanded) Theme[colors][accentText] else Theme[colors][textColor],
                    maxLines = 1,
                )
                UnstyledIcon(
                    if (detailsExpanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = null,
                    tint = if (detailsExpanded) Theme[colors][accentText] else Theme[colors][textTertiary],
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        TaskHandlerMismatchBanner(
            task = task,
            activeChannel = activeChannel,
            bots = bots,
            busy = busy,
            onSwitchToAssignedAgent = onSwitchToAssignedAgent,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        )

        AnimatedVisibility(
            visible = detailsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .scrollbar(metaScroll)
                    .verticalScroll(metaScroll)
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!task.description.isNullOrBlank()) {
                    TaskDescriptionCard(
                        description = task.description.orEmpty(),
                        expanded = descExpanded,
                        onToggleExpand = { descExpanded = !descExpanded },
                    )
                }

                TaskMetadataPickers(
                    currentProjectId = task.projectId,
                    currentProjectLabel = currentProjectLabel,
                    projects = projects,
                    unassignedLabel = unassignedLabel,
                    currentStatus = task.status,
                    matrixPosition = matrixPos,
                    onAssignProject = onAssignProject,
                    onUpdateStatus = onUpdateStatus,
                    onOpenMatrixPad = { matrixPadOpen = true },
                )

                TaskChannelsSection(
                    channels = task.channels,
                    bots = bots,
                    assignedBots = task.assignedBots,
                    activeIdx = activeChannelIdx,
                    busy = busy,
                    onSelectChannel = onSelectChannel,
                    onOpenPickSession = onOpenPickSession,
                    onOpenPickRoom = onOpenPickRoom,
                    onReplaceWithSession = onReplaceWithSession,
                    onReplaceWithRoom = onReplaceWithRoom,
                    onUnlinkChannel = onUnlinkChannel,
                )

                if (calendarPluginAvailable || timeBlocks.isNotEmpty() || task.estimatedMinutes != null) {
                    TaskTimeBlocksSection(
                        task = task,
                        timeBlocks = timeBlocks,
                        timezone = calendarTimezone,
                        onScheduleBlock = onOpenScheduleBlock,
                        onDeleteBlock = onDeleteTimeBlock,
                        onOpenCalendar = onOpenCalendar,
                    )
                }
            }
        }

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
                coordinatorBotName = coordinatorBotName,
                petSprite = coordinatorPetSprite,
                petState = coordinatorPetState,
                error = channelError,
                turnRunning = sendingToChannel || channelTurnRunning,
                turnStatus = channelTurnStatus,
                channelHeaderChip = if (!detailsExpanded) {
                    {
                        TaskChannelHandlerBadge(
                            channels = task.channels,
                            activeIdx = activeChannelIdx,
                            activeChannel = activeChannel,
                            bots = bots,
                            botAvatars = botAvatars,
                            assignedBots = task.assignedBots,
                            busy = busy,
                            onSelectChannel = onSelectChannel,
                            onOpenPickSession = onOpenPickSession,
                            onOpenPickRoom = onOpenPickRoom,
                            onReplaceWithSession = onReplaceWithSession,
                            onReplaceWithRoom = onReplaceWithRoom,
                            onUnlinkChannel = onUnlinkChannel,
                        )
                    }
                } else null,
                onRefresh = onRefreshChannel,
                onOpenFullscreen = activeChannel?.let { ch -> { onOpenFullscreen(ch) } },
                onSelectInteraction = onSendToChannel,
                onFillInteraction = { text -> steerState.setTextAndPlaceCursorAtEnd(text) },
                appliedProposalStatuses = appliedProposalStatuses,
                updatedProposals = updatedProposals,
                deletedProposalIds = deletedProposalIds,
                onApplyProposal = onApplyProposal,
                onDiscardProposal = onDiscardProposal,
                onRevertProposal = onRevertProposal,
                onUpdateProposal = onUpdateProposal,
                onRestageProposal = onRestageProposal,
                onDeleteProposal = onDeleteProposal,
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
                        onCollapse = {
                            planSheetOpen = false
                            openPlanOnArrival = false
                        },
                        onPreparePlan = triggerPreparePlan,
                        onApprovePlan = { opt, selectedParams, tweaks ->
                            planSheetOpen = false
                            openPlanOnArrival = false
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
                    triggerPreparePlan()
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

    if (matrixPadOpen) {
        TaskMatrixPadDialog(
            task = task,
            todayEpochDay = todayEpochDay,
            onDismiss = { matrixPadOpen = false },
            onApply = onUpdateMatrix,
        )
    }
}

private fun formatCheckinRange(time: String, windowMinutes: Int): String {
    val slotMin = DispatchCoordinatorConfig.parseSlotMinute(time) ?: return time
    val win = windowMinutes.coerceIn(5, 180)
    val startMin = (slotMin - win).coerceAtLeast(0)
    val endMin = (slotMin + win).coerceAtMost(1439)
    return "${DispatchCoordinatorConfig.formatSlotMinute(startMin)} – ${DispatchCoordinatorConfig.formatSlotMinute(endMin)}"
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface], shape)
            .border(1.dp, Theme[colors][stroke], shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
            )
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = Theme[typography][caption],
                    color = Theme[colors][textTertiary],
                )
            }
        }
        Switch(checked = checked)
    }
}

private data class EditingCheckinState(
    val index: Int?,
    val initial: DispatchCheckinSchedule,
)

@Composable
private fun DirectusSetupDialog(
    config: dev.hermeskotlin.core.tasks.DirectusConfigStatus?,
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    petSprite: PetSprite?,
    petState: PetState,
    saving: Boolean,
    error: String?,
    onOpenPetPicker: ((String) -> Unit)? = null,
    onResetCoordinatorSession: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (url: String, token: String, coordinator: DispatchCoordinatorUpdate?) -> Unit,
) {
    val urlState = rememberTextFieldState(config?.url.orEmpty())
    val tokenState = rememberTextFieldState("")
    val coord = config?.coordinator ?: DispatchCoordinatorConfig()
    var selectedProfile by remember(coord.profile) { mutableStateOf(coord.profile.ifBlank { Bot.DEFAULT }) }
    val promptState = rememberTextFieldState(coord.defaultPrompt)
    var checkinEnabled by remember(coord.checkinEnabled) { mutableStateOf(coord.checkinEnabled) }
    var autoOpenOnLaunch by remember(coord.autoOpenOnLaunch) { mutableStateOf(coord.autoOpenOnLaunch) }
    val schedules = remember(coord.checkinSchedules, coord.checkinTimes, coord.windowMinutes) {
        mutableStateListOf<DispatchCheckinSchedule>().apply {
            addAll(coord.effectiveCheckinSchedules())
        }
    }
    var editingRule by remember { mutableStateOf<EditingCheckinState?>(null) }

    val urlText = urlState.text.toString().trim()
    val canSave = !saving && urlText.isNotBlank() && (tokenState.text.toString().trim().isNotBlank() || config?.tokenSet == true)
    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        maxWidth = 720.dp,
        title = stringResource(Res.string.tasks_dispatch_settings_title),
        message = stringResource(Res.string.tasks_dispatch_settings_subtitle),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
            Button(
                stringResource(if (saving) Res.string.tasks_testing else Res.string.tasks_save_settings),
                onClick = {
                    val normalizedSchedules = schedules
                        .mapIndexed { idx, rule ->
                            val mins = DispatchCoordinatorConfig.parseSlotMinute(rule.time)
                            val cleanTime = mins?.let { DispatchCoordinatorConfig.formatSlotMinute(it) } ?: rule.time.trim()
                            rule.copy(
                                id = rule.id.ifBlank { "$cleanTime-$idx" },
                                time = cleanTime,
                                windowMinutes = rule.windowMinutes.coerceIn(5, 180),
                                instruction = rule.instruction.trim(),
                            )
                        }
                        .filter { DispatchCoordinatorConfig.parseSlotMinute(it.time) != null }
                        .sortedBy { DispatchCoordinatorConfig.parseSlotMinute(it.time) ?: Int.MAX_VALUE }
                    val enabledTimes = normalizedSchedules.filter { it.enabled }.map { it.time }.distinct()
                    val fallbackWindow = normalizedSchedules.firstOrNull()?.windowMinutes ?: coord.windowMinutes
                    onSave(
                        urlText,
                        tokenState.text.toString(),
                        DispatchCoordinatorUpdate(
                            profile = selectedProfile,
                            defaultPrompt = promptState.text.toString().trim(),
                            checkinEnabled = checkinEnabled,
                            checkinTimes = enabledTimes,
                            checkinSchedules = normalizedSchedules,
                            windowMinutes = fallbackWindow,
                            graceMinutes = fallbackWindow,
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
                .heightIn(max = 560.dp)
                .verticalScrollWithBar(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSectionCard(
                title = stringResource(Res.string.tasks_directus_section_title),
                description = stringResource(Res.string.tasks_directus_section_desc),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val tokenHint = config?.tokenPreview?.takeIf { it.isNotBlank() }?.let {
                        stringResource(Res.string.tasks_directus_token_keep, it)
                    }
                    if (maxWidth >= 520.dp) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextField(
                                state = urlState,
                                modifier = Modifier.weight(1f),
                                label = stringResource(Res.string.tasks_directus_url),
                                placeholder = stringResource(Res.string.tasks_directus_url_hint),
                            )
                            TextField(
                                state = tokenState,
                                modifier = Modifier.weight(1f),
                                label = stringResource(Res.string.tasks_directus_token),
                                placeholder = tokenHint,
                                password = true,
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextField(
                                state = urlState,
                                label = stringResource(Res.string.tasks_directus_url),
                                placeholder = stringResource(Res.string.tasks_directus_url_hint),
                            )
                            TextField(
                                state = tokenState,
                                label = stringResource(Res.string.tasks_directus_token),
                                placeholder = tokenHint,
                                password = true,
                            )
                        }
                    }
                }
            }

            CoordinatorSettingsSection(
                bots = bots,
                botAvatars = botAvatars,
                petSprite = petSprite,
                petState = petState,
                selectedProfile = selectedProfile,
                onSelectProfile = { selectedProfile = it },
                promptState = promptState,
                checkinEnabled = checkinEnabled,
                onToggleCheckin = { checkinEnabled = it },
                autoOpenOnLaunch = autoOpenOnLaunch,
                onToggleAutoOpen = { autoOpenOnLaunch = it },
                schedules = schedules,
                onToggleSchedule = { idx, enabled ->
                    if (idx in schedules.indices) schedules[idx] = schedules[idx].copy(enabled = enabled)
                },
                onAddSchedule = {
                    val usedTimes = schedules.map { it.time }.toSet()
                    val defaultTime = listOf("09:00", "13:30", "16:00", "20:00").firstOrNull { it !in usedTimes } ?: "09:00"
                    editingRule = EditingCheckinState(
                        index = null,
                        initial = DispatchCheckinSchedule(
                            id = "",
                            time = defaultTime,
                            windowMinutes = coord.windowMinutes.coerceIn(5, 180),
                            instruction = "",
                            enabled = true,
                        ),
                    )
                },
                onEditSchedule = { idx ->
                    schedules.getOrNull(idx)?.let { rule ->
                        editingRule = EditingCheckinState(index = idx, initial = rule)
                    }
                },
                onDeleteSchedule = { idx ->
                    if (idx in schedules.indices) schedules.removeAt(idx)
                },
                onOpenPetPicker = onOpenPetPicker,
                onResetCoordinatorSession = onResetCoordinatorSession,
            )

            if (error != null) {
                Text(error, style = Theme[typography][bodySmall], color = Theme[colors][danger])
            }
        }
    }

    editingRule?.let { current ->
        CheckinRuleEditorDialog(
            initial = current.initial,
            isNew = current.index == null,
            onDismiss = { editingRule = null },
            onDelete = current.index?.let { idx ->
                {
                    if (idx in schedules.indices) schedules.removeAt(idx)
                    editingRule = null
                }
            },
            onSave = { updated ->
                val targetIndex = current.index
                if (targetIndex != null && targetIndex in schedules.indices) {
                    schedules[targetIndex] = updated
                } else {
                    schedules.add(updated)
                }
                schedules.sortBy { DispatchCoordinatorConfig.parseSlotMinute(it.time) ?: Int.MAX_VALUE }
                editingRule = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoordinatorSettingsSection(
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    petSprite: PetSprite?,
    petState: PetState,
    selectedProfile: String,
    onSelectProfile: (String) -> Unit,
    promptState: TextFieldState,
    checkinEnabled: Boolean,
    onToggleCheckin: (Boolean) -> Unit,
    autoOpenOnLaunch: Boolean,
    onToggleAutoOpen: (Boolean) -> Unit,
    schedules: List<DispatchCheckinSchedule>,
    onToggleSchedule: (Int, Boolean) -> Unit,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Int) -> Unit,
    onDeleteSchedule: (Int) -> Unit,
    onOpenPetPicker: ((String) -> Unit)?,
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

    SettingsSectionCard(
        title = stringResource(Res.string.tasks_coordinator_settings_heading),
        description = stringResource(Res.string.tasks_coordinator_settings_desc),
        leading = {
            if (petSprite != null) {
                PetAvatar(
                    sprite = petSprite,
                    state = petState,
                    size = 34.dp,
                )
            } else {
                BotAvatar(
                    bot = activeBot,
                    picture = botAvatars[activeBot.name],
                    size = 34.dp,
                )
            }
        },
        trailing = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onOpenPetPicker != null) {
                    Button(
                        stringResource(Res.string.tasks_coordinator_choose_pet),
                        onClick = { onOpenPetPicker(selectedProfile) },
                        variant = ButtonVariant.Outline,
                        size = ButtonSize.Small,
                    )
                }
                if (onResetCoordinatorSession != null) {
                    Button(
                        stringResource(Res.string.tasks_coordinator_reset_session),
                        onClick = onResetCoordinatorSession,
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Small,
                    )
                }
            }
        },
    ) {
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
            supportingText = stringResource(Res.string.tasks_coordinator_default_prompt_desc),
            singleLine = false,
            maxLines = 4,
        )
    }

    SettingsSectionCard(
        title = stringResource(Res.string.tasks_coordinator_checkin_heading),
        description = stringResource(Res.string.tasks_coordinator_checkin_desc),
    ) {
        SettingsSwitchRow(
            label = stringResource(Res.string.tasks_coordinator_checkin_toggle),
            checked = checkinEnabled,
            onCheckedChange = onToggleCheckin,
        )

        if (checkinEnabled) {
            SettingsSwitchRow(
                label = stringResource(Res.string.tasks_coordinator_auto_open_toggle),
                description = stringResource(Res.string.tasks_coordinator_auto_open_desc),
                checked = autoOpenOnLaunch,
                onCheckedChange = onToggleAutoOpen,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(Res.string.tasks_checkin_rules_heading),
                        style = Theme[typography][labelStyle],
                        color = Theme[colors][textSecondary],
                    )
                    Text(
                        stringResource(Res.string.tasks_checkin_rules_desc),
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                    )
                }
                Button(
                    stringResource(Res.string.tasks_checkin_add_button),
                    onClick = onAddSchedule,
                    leadingIcon = Lucide.Plus,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Small,
                    modifier = Modifier.testTag("tasks_checkin_add_button"),
                )
            }

            if (schedules.isEmpty()) {
                val emptyShape = RoundedCornerShape(Theme[radii][radiusMedium])
                Text(
                    stringResource(Res.string.tasks_checkin_rules_empty),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textTertiary],
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .background(Theme[colors][surface], emptyShape)
                        .border(1.dp, Theme[colors][stroke], emptyShape)
                        .padding(12.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    schedules.forEachIndexed { idx, rule ->
                        val itemShape = RoundedCornerShape(Theme[radii][radiusMedium])
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(Theme[colors][surface], itemShape)
                                .border(1.dp, Theme[colors][stroke], itemShape)
                                .clickable(role = Role.Button) { onEditSchedule(idx) }
                                .handCursor()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    UnstyledIcon(
                                        Lucide.Clock,
                                        contentDescription = null,
                                        tint = Theme[colors][accentText],
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        rule.time,
                                        style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                                        color = if (rule.enabled) Theme[colors][textColor] else Theme[colors][textTertiary],
                                    )
                                    MiniBadge(
                                        text = stringResource(Res.string.tasks_checkin_rule_meta, rule.windowMinutes),
                                        backgroundColor = Theme[colors][surface3],
                                        contentColor = Theme[colors][textSecondary],
                                    )
                                    Text(
                                        formatCheckinRange(rule.time, rule.windowMinutes),
                                        style = Theme[typography][caption],
                                        color = Theme[colors][textTertiary],
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    rule.instruction.ifBlank { stringResource(Res.string.tasks_checkin_rule_default_inst) },
                                    style = Theme[typography][bodySmall],
                                    color = if (rule.instruction.isBlank()) Theme[colors][textTertiary] else Theme[colors][textSecondary],
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(Theme[radii][radiusFull]))
                                    .toggleable(
                                        value = rule.enabled,
                                        role = Role.Switch,
                                        onValueChange = { onToggleSchedule(idx, it) },
                                    )
                                    .handCursor(),
                            ) {
                                Switch(checked = rule.enabled)
                            }
                            IconButton(
                                Lucide.Pencil,
                                contentDescription = stringResource(Res.string.tasks_checkin_edit_title),
                                onClick = { onEditSchedule(idx) },
                            )
                            IconButton(
                                Lucide.Trash2,
                                contentDescription = stringResource(Res.string.tasks_checkin_delete_rule),
                                onClick = { onDeleteSchedule(idx) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckinRuleEditorDialog(
    initial: DispatchCheckinSchedule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (DispatchCheckinSchedule) -> Unit,
) {
    val timeState = rememberTextFieldState(initial.time)
    val windowState = rememberTextFieldState(initial.windowMinutes.toString())
    val instructionState = rememberTextFieldState(initial.instruction)
    val parsedMinute = DispatchCoordinatorConfig.parseSlotMinute(timeState.text.toString())
    val normalizedTime = parsedMinute?.let { DispatchCoordinatorConfig.formatSlotMinute(it) } ?: initial.time
    val parsedWindow = (windowState.text.toString().trim().toIntOrNull() ?: initial.windowMinutes).coerceIn(5, 180)
    val canApply = parsedMinute != null

    fun shiftSlotMinute(delta: Int) {
        val base = parsedMinute ?: DispatchCoordinatorConfig.parseSlotMinute(initial.time) ?: (9 * 60)
        val next = ((base + delta) % 1440 + 1440) % 1440
        timeState.setTextAndPlaceCursorAtEnd(DispatchCoordinatorConfig.formatSlotMinute(next))
    }

    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
        title = stringResource(Res.string.tasks_checkin_edit_title),
        actions = {
            if (!isNew && onDelete != null) {
                Button(
                    stringResource(Res.string.tasks_checkin_delete_rule),
                    onClick = onDelete,
                    variant = ButtonVariant.Ghost,
                )
            }
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
            Button(
                stringResource(Res.string.tasks_checkin_save_rule),
                onClick = {
                    val slotMin = parsedMinute ?: return@Button
                    val cleanTime = DispatchCoordinatorConfig.formatSlotMinute(slotMin)
                    onSave(
                        initial.copy(
                            id = initial.id.ifBlank { cleanTime },
                            time = cleanTime,
                            windowMinutes = parsedWindow,
                            instruction = instructionState.text.toString().trim(),
                        ),
                    )
                },
                enabled = canApply,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScrollWithBar(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TextField(
                    state = timeState,
                    modifier = Modifier.weight(1f),
                    label = stringResource(Res.string.tasks_checkin_time_label),
                    placeholder = "09:00",
                    supportingText = stringResource(Res.string.tasks_checkin_time_desc),
                )
                Column(
                    Modifier.padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            Lucide.Minus,
                            contentDescription = "-15m",
                            onClick = { shiftSlotMinute(-15) },
                        )
                        IconButton(
                            Lucide.Plus,
                            contentDescription = "+15m",
                            onClick = { shiftSlotMinute(15) },
                        )
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("08:30", "09:00", "13:30", "16:00", "20:00").forEach { preset ->
                    Chip(
                        text = preset,
                        selected = normalizedTime == preset && canApply,
                        onClick = { timeState.setTextAndPlaceCursorAtEnd(preset) },
                    )
                }
            }

            TextField(
                state = windowState,
                label = stringResource(Res.string.tasks_checkin_window_label),
                placeholder = "30",
                supportingText = stringResource(Res.string.tasks_checkin_window_desc, normalizedTime),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Chip(
                            text = stringResource(Res.string.tasks_checkin_rule_meta, mins),
                            selected = parsedWindow == mins,
                            onClick = { windowState.setTextAndPlaceCursorAtEnd(mins.toString()) },
                        )
                    }
                }
                if (canApply) {
                    MiniBadge(
                        text = formatCheckinRange(normalizedTime, parsedWindow),
                        backgroundColor = Theme[colors][accentSoft],
                        contentColor = Theme[colors][accentText],
                    )
                }
            }

            TextField(
                state = instructionState,
                modifier = Modifier.testTag("checkin_rule_instruction_field"),
                label = stringResource(Res.string.tasks_checkin_instruction_label),
                placeholder = stringResource(Res.string.tasks_checkin_instruction_hint),
                supportingText = stringResource(Res.string.tasks_checkin_instruction_desc),
                singleLine = false,
                maxLines = 4,
            )
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
