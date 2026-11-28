package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.PanelLeft
import com.composables.icons.lucide.PanelRight
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.calendar.CalendarDate
import dev.hermeskotlin.core.calendar.CalendarEvent
import dev.hermeskotlin.core.calendar.CalendarInfo
import dev.hermeskotlin.core.calendar.CalendarItem
import dev.hermeskotlin.core.calendar.CalendarLayoutEngine
import dev.hermeskotlin.core.calendar.DispatchCalendarTask
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.UpdateProposalRequest
import dev.hermeskotlin.core.calendar.calendarAddMonths
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClockCompact
import dev.hermeskotlin.core.calendar.plusDays
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.media.MediaApi
import dev.hermeskotlin.core.models.ModelOption
import dev.hermeskotlin.core.models.ReasoningEffort
import dev.hermeskotlin.core.pet.PetApi
import dev.hermeskotlin.core.pet.PetSprite
import dev.hermeskotlin.core.pet.PetState
import dev.hermeskotlin.core.pet.petStateOf
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.settings.AppSettings
import dev.hermeskotlin.core.settings.SettingsStore
import dev.hermeskotlin.core.voice.AudioApi
import dev.hermeskotlin.core.voice.DeviceDictation
import dev.hermeskotlin.core.voice.SpeechPlayer
import dev.hermeskotlin.core.voice.VoiceRecorder
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.background
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.BottomSheet
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Chip
import dev.hermeskotlin.designsystem.components.DropdownMenu
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.LocalOpenModals
import dev.hermeskotlin.designsystem.components.MenuChoice
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.VerticalScrollbar
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.components.scrollbar
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.dangerSoft
import dev.hermeskotlin.designsystem.sidebar
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.bots.BotAvatar
import dev.hermeskotlin.ui.chat.AttachSheet
import dev.hermeskotlin.ui.chat.AttachmentPicker
import dev.hermeskotlin.ui.chat.InputRequestPanel
import dev.hermeskotlin.ui.chat.LocalModelPillBounds
import dev.hermeskotlin.ui.chat.ModelConfirmDialog
import dev.hermeskotlin.ui.chat.ModelSheet
import dev.hermeskotlin.ui.chat.ScheduleProposalContent
import dev.hermeskotlin.ui.chat.rememberAttachmentPicker
import dev.hermeskotlin.ui.clearOfWindowControls
import dev.hermeskotlin.ui.components.EmptyState
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.pet.PetAvatar
import dev.hermeskotlin.ui.pet.PetController
import dev.hermeskotlin.ui.pet.PetSheet
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.app_try_again
import dev.hermeskotlin.ui.resources.cal_agent_ask
import dev.hermeskotlin.ui.resources.cal_agent_open_fullscreen
import dev.hermeskotlin.ui.resources.cal_agent_panel_empty
import dev.hermeskotlin.ui.resources.cal_agent_reset_session
import dev.hermeskotlin.ui.resources.cal_agent_settings
import dev.hermeskotlin.ui.resources.cal_agent_title
import dev.hermeskotlin.ui.resources.cal_ask_agent_plan
import dev.hermeskotlin.ui.resources.cal_back
import dev.hermeskotlin.ui.resources.cal_block_slot
import dev.hermeskotlin.ui.resources.cal_connect_account
import dev.hermeskotlin.ui.resources.cal_connect_title
import dev.hermeskotlin.ui.resources.cal_create
import dev.hermeskotlin.ui.resources.cal_dispatch_panel_toggle
import dev.hermeskotlin.ui.resources.cal_dispatch_tasks
import dev.hermeskotlin.ui.resources.cal_estimated_minutes
import dev.hermeskotlin.ui.resources.cal_my_calendars
import dev.hermeskotlin.ui.resources.cal_next
import dev.hermeskotlin.ui.resources.cal_open_session
import dev.hermeskotlin.ui.resources.cal_other_calendars
import dev.hermeskotlin.ui.resources.cal_plan_my_day
import dev.hermeskotlin.ui.resources.cal_plugin_missing
import dev.hermeskotlin.ui.resources.cal_previous
import dev.hermeskotlin.ui.resources.cal_proposal_apply
import dev.hermeskotlin.ui.resources.cal_proposal_delete
import dev.hermeskotlin.ui.resources.cal_proposal_heading
import dev.hermeskotlin.ui.resources.cal_proposal_hide
import dev.hermeskotlin.ui.resources.cal_proposal_show
import dev.hermeskotlin.ui.resources.cal_proposal_staged_summary
import dev.hermeskotlin.ui.resources.cal_read_only
import dev.hermeskotlin.ui.resources.cal_scheduled_tasks
import dev.hermeskotlin.ui.resources.cal_set_task_target
import dev.hermeskotlin.ui.resources.cal_settings_title
import dev.hermeskotlin.ui.resources.cal_show_completed_tasks
import dev.hermeskotlin.ui.resources.cal_show_declined
import dev.hermeskotlin.ui.resources.cal_show_weekends
import dev.hermeskotlin.ui.resources.cal_sync_now
import dev.hermeskotlin.ui.resources.cal_task_due
import dev.hermeskotlin.ui.resources.cal_task_target_badge
import dev.hermeskotlin.ui.resources.cal_title
import dev.hermeskotlin.ui.resources.cal_today
import dev.hermeskotlin.ui.resources.cal_toggle_calendars
import dev.hermeskotlin.ui.resources.cal_unscheduled_tasks
import dev.hermeskotlin.ui.resources.cal_view_day
import dev.hermeskotlin.ui.resources.cal_view_four_days
import dev.hermeskotlin.ui.resources.cal_view_month
import dev.hermeskotlin.ui.resources.cal_view_schedule
import dev.hermeskotlin.ui.resources.cal_view_settings
import dev.hermeskotlin.ui.resources.cal_view_three_days
import dev.hermeskotlin.ui.resources.cal_view_week
import dev.hermeskotlin.ui.resources.chat_notice_dictation_mic
import dev.hermeskotlin.ui.resources.tasks_close_inspector
import dev.hermeskotlin.ui.tasks.AgentFloatingTrigger
import dev.hermeskotlin.ui.tasks.ProvideChannelMedia
import dev.hermeskotlin.ui.tasks.TaskChannelLog
import dev.hermeskotlin.ui.tasks.TaskChatComposer
import dev.hermeskotlin.ui.voice.DictationState
import dev.hermeskotlin.ui.voice.VoiceController
import dev.hermeskotlin.ui.voice.rememberMicrophonePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal enum class CalendarRightTab {
    TASKS,
    AGENT,
}

@Composable
internal fun CalendarPage(
    gateway: SavedGateway,
    visible: Boolean,
    initialOpenAgent: Boolean = false,
    onConsumedOpenAgent: () -> Unit = {},
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onOpenSession: (sessionId: String, title: String, profile: String?) -> Unit = { _, _, _ -> },
    onOpenRoom: (Room) -> Unit = {},
    onToggleSidebar: (() -> Unit)? = null,
) {
    val viewModel: CalendarViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        if (!visible) voiceController.stopAll()
        onDispose {}
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

    val openSession by rememberUpdatedState(onOpenSession)
    val openRoom by rememberUpdatedState(onOpenRoom)

    LaunchedEffect(gateway, visible) {
        viewModel.bind(gateway, visible)
    }
    LaunchedEffect(gateway, state.agentProfile) {
        petController.bind(state.agentProfile)
    }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (isActive) {
            delay(30_000L)
            viewModel.tickClock()
        }
    }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.consumeSessionExpired()
            onSessionExpired()
        }
    }
    LaunchedEffect(state.feedback) {
        if (state.feedback != null) {
            delay(4_000L)
            viewModel.clearFeedback()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarNavEvent.OpenSession -> openSession(event.sessionId, event.title, event.profile)
                is CalendarNavEvent.OpenRoom -> openRoom(event.room)
            }
        }
    }

    val onToggleDictation: (TextFieldState) -> Unit = { fieldState ->
        microphone.withMicrophone(onDenied = { viewModel.reportAttachmentError(dictationNeedsMic) }) {
            if (voiceController.dictation.value.recording) {
                voiceController.finishDictation()
            } else {
                val engine = (settingsStore.settings.value ?: AppSettings()).dictationEngine
                val profile = state.agentProfile.ifBlank { null }
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

    ProvideChannelMedia(
        gateway = gateway.gatewayUrl,
        activeChannel = state.agentChannel,
        mediaApi = mediaApi,
    ) {
        CalendarPageContent(
            state = state,
            initialOpenAgent = initialOpenAgent,
            onConsumedOpenAgent = onConsumedOpenAgent,
            attachmentPicker = attachmentPicker,
            petSprite = petSprite,
            dictation = dictation,
            onToggleDictation = onToggleDictation,
            onOpenPetPicker = { targetProfile ->
                val target = targetProfile.trim().ifEmpty { Bot.DEFAULT }
                petController.bind(target)
                petSheetOpen = true
            },
            onBack = onBack,
            onToggleSidebar = onToggleSidebar,
            onRefresh = viewModel::refresh,
            onSyncNow = { viewModel.syncNow() },
            onSetViewMode = viewModel::setViewMode,
            onStepPeriod = viewModel::stepPeriod,
            onGoToday = viewModel::goToday,
            onSelectDate = viewModel::selectDate,
            onOpenDay = viewModel::openDay,
            onToggleWeekends = viewModel::toggleShowWeekends,
            onToggleDeclined = viewModel::toggleShowDeclined,
            onToggleCompletedTasks = viewModel::toggleShowCompletedTasks,
            onToggleCalendarVisible = viewModel::toggleCalendarVisible,
            onSetTaskTargetCalendar = viewModel::setTaskTargetCalendar,
            onSelectEvent = viewModel::selectEvent,
            onSelectTask = viewModel::selectTask,
            onOpenTaskSession = viewModel::openTaskSession,
            onOpenCreateEvent = viewModel::openCreateEvent,
            onOpenEditEvent = viewModel::openEditEvent,
            onCloseEventEditor = viewModel::closeEventEditor,
            onSaveEvent = viewModel::saveEvent,
            onDeleteEvent = viewModel::deleteEvent,
            onToggleTaskDone = viewModel::toggleTaskDone,
            onOpenScheduleTask = viewModel::openScheduleTask,
            onCloseScheduleTask = viewModel::closeScheduleTaskDialog,
            onRefreshSlots = viewModel::findFreeSlots,
            onSaveTaskBlock = viewModel::saveTaskBlock,
            onDeleteTaskBlock = viewModel::deleteTaskBlock,
            onApplyProposal = viewModel::applyProposal,
            onDiscardProposal = viewModel::discardProposal,
            onRevertProposal = { id -> viewModel.revertProposal(id) },
            onUpdateProposal = viewModel::updateProposal,
            onRestageProposal = viewModel::restageProposal,
            onDeleteProposal = viewModel::deleteProposal,
            onDismissConflict = viewModel::dismissProposalConflict,
            onForceApplyConflict = viewModel::confirmForceApplyProposal,
            onOpenConnectDialog = viewModel::openConnectDialog,
            onCloseConnectDialog = viewModel::closeConnectDialog,
            onImportHermesGoogle = viewModel::importHermesGoogle,
            onStartGoogleOAuth = viewModel::startGoogleOAuth,
            onStartLarkOAuth = viewModel::startLarkOAuth,
            onStartMicrosoftOAuth = viewModel::startMicrosoftOAuth,
            onCompleteOAuth = viewModel::completeOAuth,
            onConnectCalDav = viewModel::connectCalDav,
            onConnectIcs = viewModel::connectIcs,
            onOpenSettings = viewModel::openSettingsDialog,
            onCloseSettings = viewModel::closeSettingsDialog,
            onSaveSettings = viewModel::saveSettings,
            onDisconnectAccount = viewModel::disconnectAccount,
            onAgentPanelOpened = viewModel::onAgentPanelOpened,
            onAskAgent = viewModel::askAgent,
            onQuickPlanDay = viewModel::quickPlanDay,
            onRefreshAgentChat = viewModel::refreshAgentChat,
            onResetAgentSession = viewModel::resetAgentSession,
            onOpenAgentFullscreen = viewModel::openAgentFullscreen,
            onAnswerAgentRequest = viewModel::answerAgentRequest,
            onRemoveAttachment = viewModel::removeAttachment,
            onRefreshModels = viewModel::loadModels,
            onSelectModel = viewModel::selectModel,
            onDismissModelConfirm = viewModel::dismissModelConfirm,
            onSelectEffort = { viewModel.setReasoningEffort(it.wire) },
            onSetFast = viewModel::setFast,
            onUpdateSettings = settingsStore::update,
            onClearFeedback = viewModel::clearFeedback,
        )
    }

    PetSheet(
        visible = petSheetOpen,
        controller = petController,
        onDismiss = {
            petSheetOpen = false
            petController.bind(state.agentProfile)
        },
        onAdopted = { petSheetOpen = false },
    )
}

@Composable
internal fun CalendarPageContent(
    state: CalendarUiState,
    initialOpenAgent: Boolean = false,
    onConsumedOpenAgent: () -> Unit = {},
    attachmentPicker: AttachmentPicker? = null,
    petSprite: PetSprite? = null,
    dictation: DictationState = DictationState(),
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
    onOpenPetPicker: ((String) -> Unit)? = null,
    onBack: () -> Unit = {},
    onToggleSidebar: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSetViewMode: (CalendarViewMode) -> Unit = {},
    onStepPeriod: (Int) -> Unit = {},
    onGoToday: () -> Unit = {},
    onSelectDate: (CalendarDate) -> Unit = {},
    onOpenDay: (CalendarDate) -> Unit = { d ->
        onSelectDate(d)
        onSetViewMode(CalendarViewMode.DAY)
    },
    onToggleWeekends: () -> Unit = {},
    onToggleDeclined: () -> Unit = {},
    onToggleCompletedTasks: () -> Unit = {},
    onToggleCalendarVisible: (CalendarInfo) -> Unit = {},
    onSetTaskTargetCalendar: (CalendarInfo) -> Unit = {},
    onSelectEvent: (CalendarEvent?) -> Unit = {},
    onSelectTask: (DispatchCalendarTask?) -> Unit = {},
    onOpenTaskSession: (String) -> Unit = {},
    onOpenCreateEvent: (CalendarDate?, Int?, Boolean) -> Unit = { _, _, _ -> },
    onOpenEditEvent: (CalendarEvent) -> Unit = {},
    onCloseEventEditor: () -> Unit = {},
    onSaveEvent: (EventEditorDraft) -> Unit = {},
    onDeleteEvent: (String, Boolean) -> Unit = { _, _ -> },
    onToggleTaskDone: (String, Boolean) -> Unit = { _, _ -> },
    onOpenScheduleTask: (DispatchCalendarTask, DispatchTimeBlock?) -> Unit = { _, _ -> },
    onCloseScheduleTask: () -> Unit = {},
    onRefreshSlots: (Int) -> Unit = {},
    onSaveTaskBlock: (TaskBlockDraft) -> Unit = {},
    onDeleteTaskBlock: (String) -> Unit = {},
    onApplyProposal: (String, List<String>, Boolean) -> Unit = { _, _, _ -> },
    onDiscardProposal: (String) -> Unit = {},
    onRevertProposal: (String) -> Unit = {},
    onUpdateProposal: (String, UpdateProposalRequest) -> Unit = { _, _ -> },
    onRestageProposal: (String) -> Unit = {},
    onDeleteProposal: (String) -> Unit = {},
    onDismissConflict: () -> Unit = {},
    onForceApplyConflict: () -> Unit = {},
    onOpenConnectDialog: () -> Unit = {},
    onCloseConnectDialog: () -> Unit = {},
    onImportHermesGoogle: (String?) -> Unit = {},
    onStartGoogleOAuth: (String?, String?, String?, Boolean, (String) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onStartLarkOAuth: (String, String, String, String?, Boolean, (String) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onStartMicrosoftOAuth: (String, String?, String, String?, Boolean, (String) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onCompleteOAuth: (String) -> Unit = {},
    onConnectCalDav: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onConnectIcs: (String, String, String?) -> Unit = { _, _, _ -> },
    onOpenSettings: () -> Unit = {},
    onCloseSettings: () -> Unit = {},
    onSaveSettings: (String, String, String, String, String?) -> Unit = { _, _, _, _, _ -> },
    onDisconnectAccount: (String) -> Unit = {},
    onAgentPanelOpened: () -> Unit = {},
    onAskAgent: (String) -> Unit = {},
    onQuickPlanDay: () -> Unit = {},
    onRefreshAgentChat: () -> Unit = {},
    onResetAgentSession: () -> Unit = {},
    onOpenAgentFullscreen: () -> Unit = {},
    onAnswerAgentRequest: (InputRequest, JsonObject) -> Unit = { _, _ -> },
    onRemoveAttachment: (String) -> Unit = {},
    onRefreshModels: (Boolean) -> Unit = {},
    onSelectModel: (ModelOption, Boolean) -> Unit = { _, _ -> },
    onDismissModelConfirm: () -> Unit = {},
    onSelectEffort: (ReasoningEffort) -> Unit = {},
    onSetFast: (Boolean) -> Unit = {},
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit = {},
    onClearFeedback: () -> Unit = {},
) {
    var leftRailDockedOpen by rememberSaveable { mutableStateOf(true) }
    var rightRailDockedOpen by rememberSaveable { mutableStateOf(true) }
    var leftSheetOpen by remember { mutableStateOf(false) }
    var rightSheetOpen by remember { mutableStateOf(false) }
    var rightTab by rememberSaveable { mutableStateOf(CalendarRightTab.TASKS) }
    var modelsOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    val modelPillBounds = remember { mutableStateOf<Rect?>(null) }
    var miniMonthAnchor by remember(state.anchorDate.year, state.anchorDate.month) { mutableStateOf(state.anchorDate) }

    val modals = LocalOpenModals.current

    val effectiveProposals = remember(
        state.proposals,
        state.updatedProposals,
        state.deletedProposalIds,
        state.proposalStatuses,
    ) {
        state.proposals
            .asSequence()
            .filterNot { it.id in state.deletedProposalIds }
            .map { base ->
                val latest = state.updatedProposals[base.id] ?: base
                val override = state.proposalStatuses[base.id]
                if (override != null && override != latest.status) latest.copy(status = override) else latest
            }
            .toList()
    }
    val stagedCount = remember(effectiveProposals) {
        effectiveProposals.count { it.status == ScheduleProposal.STATUS_STAGED }
    }
    val agentPetState = remember(
        state.agentError,
        effectiveProposals,
        state.agentInputRequests,
        state.agentTurnRunning,
        state.agentTurnStatus,
    ) {
        petStateOf(
            failed = state.agentError != null,
            justFinished = stagedCount > 0,
            awaitingInput = state.agentInputRequests.isNotEmpty(),
            toolRunning = state.agentTurnRunning &&
                !state.agentTurnStatus.isNullOrBlank() && state.agentTurnStatus != "Thinking…",
            reasoning = state.agentTurnRunning &&
                (state.agentTurnStatus.isNullOrBlank() || state.agentTurnStatus == "Thinking…"),
            busy = state.agentTurnRunning,
        )
    }

    val visibleCalIds = remember(state.calendars) {
        state.calendars.filter { it.visible }.map { it.id }.toSet()
    }
    val calIdsKnown = remember(state.calendars) { state.calendars.map { it.id }.toSet() }
    val filteredEvents = remember(state.events, visibleCalIds, calIdsKnown) {
        state.events.filter { ev ->
            ev.calendarId !in calIdsKnown || ev.calendarId in visibleCalIds
        }
    }
    val projectColors = remember(state.dispatchOverview.projects) {
        state.dispatchOverview.projects.mapNotNull { p -> p.color?.let { c -> p.id to c } }.toMap()
    }
    val items = remember(
        filteredEvents,
        state.dispatchOverview.tasks,
        effectiveProposals,
        state.showDeclined,
        state.showCompletedTasks,
        projectColors,
    ) {
        CalendarLayoutEngine.collectItems(
            events = filteredEvents,
            tasks = state.dispatchOverview.tasks,
            stagedProposals = effectiveProposals,
            showDeclined = state.showDeclined,
            showCompletedTasks = state.showCompletedTasks,
            projectColors = projectColors,
        )
    }

    val tz = state.settings.timezone.ifBlank { null }
    val visibleDays = remember(state.viewMode, state.anchorDate, state.showWeekends) {
        val count = state.viewMode.gridDayCount
        if (count > 0) CalendarLayoutEngine.visibleGridDays(state.anchorDate, count, weekStartsSunday = true, showWeekends = state.showWeekends)
        else emptyList()
    }
    val timeGridLayout = remember(visibleDays, items, tz) {
        if (visibleDays.isNotEmpty()) CalendarLayoutEngine.buildTimeGrid(visibleDays, items, tz) else null
    }
    val monthRows = remember(state.viewMode, state.anchorDate, items, tz) {
        if (state.viewMode == CalendarViewMode.MONTH) {
            val weeks = CalendarLayoutEngine.monthGridWeeks(state.anchorDate.year, state.anchorDate.month, weekStartsSunday = true)
            CalendarLayoutEngine.buildMonthRows(weeks, items, tz, maxVisiblePerDay = 3)
        } else emptyList()
    }
    val agendaSections = remember(state.viewMode, state.anchorDate, items, tz) {
        if (state.viewMode == CalendarViewMode.SCHEDULE) {
            CalendarLayoutEngine.buildAgendaSections(
                startDate = state.anchorDate,
                endDate = state.anchorDate.plusDays(44),
                items = items,
                zoneId = tz,
                includeEmptyDays = false,
            )
        } else emptyList()
    }
    val rangeTitle = remember(state.viewMode, state.anchorDate, visibleDays) {
        formatCalendarRangeTitle(state.viewMode, state.anchorDate, visibleDays)
    }

    CompositionLocalProvider(LocalModelPillBounds provides modelPillBounds) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Theme[colors][background])
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onKeyEvent { event ->
                if (!isDesktop || modals.any || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.T -> { onGoToday(); true }
                    Key.D -> { onSetViewMode(CalendarViewMode.DAY); true }
                    Key.X -> { onSetViewMode(CalendarViewMode.FOUR_DAYS); true }
                    Key.W -> { onSetViewMode(CalendarViewMode.WEEK); true }
                    Key.M -> { onSetViewMode(CalendarViewMode.MONTH); true }
                    Key.A -> { onSetViewMode(CalendarViewMode.SCHEDULE); true }
                    Key.C -> { onOpenCreateEvent(state.anchorDate, 540, false); true }
                    Key.DirectionLeft -> { onStepPeriod(-1); true }
                    Key.DirectionRight -> { onStepPeriod(1); true }
                    else -> false
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wideLeft = maxWidth >= 900.dp
            val wideRight = maxWidth >= 1120.dp
            val compactToolbar = maxWidth < 640.dp
            val rootMaxHeight = maxHeight
            val agentCurrentlyVisible = rightTab == CalendarRightTab.AGENT &&
                if (wideRight) rightRailDockedOpen else rightSheetOpen

            LaunchedEffect(initialOpenAgent, wideRight) {
                if (initialOpenAgent) {
                    rightTab = CalendarRightTab.AGENT
                    if (wideRight) rightRailDockedOpen = true else rightSheetOpen = true
                    onConsumedOpenAgent()
                }
            }
            LaunchedEffect(agentCurrentlyVisible) {
                if (agentCurrentlyVisible) onAgentPanelOpened()
            }

            val handleItemClick: (CalendarItem) -> Unit = { item ->
                when (item) {
                    is CalendarItem.EventItem -> onSelectEvent(item.event)
                    is CalendarItem.TaskDeadlineItem -> onSelectTask(item.task)
                    is CalendarItem.GhostOperationItem -> {
                        rightTab = CalendarRightTab.AGENT
                        if (wideRight) rightRailDockedOpen = true else rightSheetOpen = true
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                CalendarTopBar(
                    rangeTitle = rangeTitle,
                    viewMode = state.viewMode,
                    showWeekends = state.showWeekends,
                    showDeclined = state.showDeclined,
                    showCompletedTasks = state.showCompletedTasks,
                    syncing = state.syncing,
                    compact = compactToolbar,
                    stagedCount = stagedCount,
                    onToggleSidebar = onToggleSidebar,
                    onBack = onBack,
                    onToggleCalendarsPanel = {
                        if (wideLeft) leftRailDockedOpen = !leftRailDockedOpen else leftSheetOpen = true
                    },
                    onGoToday = onGoToday,
                    onStepPeriod = onStepPeriod,
                    onSyncNow = onSyncNow,
                    onOpenCreate = { onOpenCreateEvent(state.anchorDate, 540, false) },
                    onSetViewMode = onSetViewMode,
                    onToggleWeekends = onToggleWeekends,
                    onToggleDeclined = onToggleDeclined,
                    onToggleCompletedTasks = onToggleCompletedTasks,
                    onOpenSettings = onOpenSettings,
                    onToggleRightPanel = {
                        if (wideRight) rightRailDockedOpen = !rightRailDockedOpen else rightSheetOpen = !rightSheetOpen
                    },
                )

                Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

                if (state.pluginMissing) {
                    EmptyState(
                        icon = Lucide.Calendar,
                        title = stringResource(Res.string.cal_title),
                        body = stringResource(Res.string.cal_plugin_missing),
                        action = {
                            Button(stringResource(Res.string.app_try_again), onClick = onRefresh, variant = ButtonVariant.Outline)
                        },
                    )
                    return@BoxWithConstraints
                }

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (wideLeft && leftRailDockedOpen) {
                        CalendarLeftRail(
                            accountsCount = state.accounts.size,
                            calendars = state.calendars,
                            miniMonthAnchor = miniMonthAnchor,
                            todayDate = state.todayDate,
                            onCreateClick = { onOpenCreateEvent(state.anchorDate, 540, false) },
                            onSelectMiniDate = { d -> onSelectDate(d) },
                            onStepMiniMonth = { delta ->
                                val (y, m) = calendarAddMonths(miniMonthAnchor.year, miniMonthAnchor.month, delta)
                                calendarDateFromIso("${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-01")
                                    ?.let { miniMonthAnchor = it }
                            },
                            onToggleCalendar = onToggleCalendarVisible,
                            onSetTaskTarget = onSetTaskTargetCalendar,
                            onOpenConnect = onOpenConnectDialog,
                            modifier = Modifier.width(248.dp).fillMaxHeight(),
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Theme[colors][stroke]))
                    }

                    // Center Calendar Canvas
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize()) {
                            if (state.accounts.isEmpty() && !state.loading) {
                                ConnectBannerRow(
                                    onConnectClick = onOpenConnectDialog,
                                    onPlanClick = {
                                        rightTab = CalendarRightTab.AGENT
                                        if (wideRight) rightRailDockedOpen = true else rightSheetOpen = true
                                    },
                                )
                            }
                            if (stagedCount > 0) {
                                StagedProposalBanner(
                                    count = stagedCount,
                                    onClick = {
                                        rightTab = CalendarRightTab.AGENT
                                        if (wideRight) rightRailDockedOpen = true else rightSheetOpen = true
                                    },
                                )
                            }
                            if (state.loading && items.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Spinner()
                                }
                            } else {
                                when (state.viewMode) {
                                    CalendarViewMode.DAY, CalendarViewMode.THREE_DAYS, CalendarViewMode.FOUR_DAYS, CalendarViewMode.WEEK -> {
                                        val grid = timeGridLayout
                                        if (grid != null) {
                                            CalendarTimeGridView(
                                                layout = grid,
                                                todayDate = state.todayDate,
                                                currentMinuteOfDay = state.currentMinuteOfDay,
                                                use24Hour = state.use24Hour,
                                                timezoneLabel = tz?.substringAfterLast('/') ?: "Local",
                                                onSelectDay = onSelectDate,
                                                onEmptySlotClick = { d, min -> onOpenCreateEvent(d, min, false) },
                                                onItemClick = handleItemClick,
                                                onToggleTaskDone = onToggleTaskDone,
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                            )
                                        }
                                    }
                                    CalendarViewMode.SCHEDULE -> CalendarScheduleAgendaView(
                                        sections = agendaSections,
                                        todayDate = state.todayDate,
                                        currentMinuteOfDay = state.currentMinuteOfDay,
                                        use24Hour = state.use24Hour,
                                        onItemClick = handleItemClick,
                                        onToggleTaskDone = onToggleTaskDone,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                    CalendarViewMode.MONTH -> CalendarMonthGridView(
                                        monthRows = monthRows,
                                        anchorDate = state.anchorDate,
                                        todayDate = state.todayDate,
                                        use24Hour = state.use24Hour,
                                        onSelectDate = onOpenDay,
                                        onItemClick = handleItemClick,
                                        onToggleTaskDone = onToggleTaskDone,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        val bannerError = state.error ?: state.feedback
                        if (bannerError != null) {
                            val isError = state.error != null
                            Row(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        start = 12.dp,
                                        end = if (!agentCurrentlyVisible) 80.dp else 12.dp,
                                        bottom = 12.dp,
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isError) Theme[colors][dangerSoft] else Theme[colors][surface])
                                    .border(
                                        1.dp,
                                        if (isError) Theme[colors][danger].copy(alpha = 0.4f) else Theme[colors][strokeStrong],
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    bannerError,
                                    style = Theme[typography][caption],
                                    color = if (isError) Theme[colors][danger] else Theme[colors][textSecondary],
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                IconButton(Lucide.X, contentDescription = stringResource(Res.string.cal_back), onClick = onClearFeedback)
                            }
                        }

                        if (!agentCurrentlyVisible && !state.pluginMissing) {
                            AgentFloatingTrigger(
                                bot = state.agentBot,
                                avatarBytes = state.botAvatars[state.agentBot.name],
                                petSprite = petSprite,
                                petState = agentPetState,
                                badgeActive = stagedCount > 0 || state.agentInputRequests.isNotEmpty(),
                                busy = state.agentTurnRunning,
                                contentDescription = stringResource(Res.string.cal_agent_title),
                                testTag = "calendar_agent_button",
                                onClick = {
                                    rightTab = CalendarRightTab.AGENT
                                    if (wideRight) rightRailDockedOpen = true else rightSheetOpen = true
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(end = 16.dp, bottom = if (isDesktop) 16.dp else 20.dp),
                            )
                        }
                    }

                    if (wideRight && rightRailDockedOpen) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Theme[colors][stroke]))
                        CalendarRightPanel(
                            state = state,
                            effectiveProposals = effectiveProposals,
                            petSprite = petSprite,
                            petState = agentPetState,
                            attachmentPicker = attachmentPicker,
                            dictation = dictation,
                            onToggleDictation = onToggleDictation,
                            selectedTab = rightTab,
                            onSelectTab = { rightTab = it },
                            onClosePanel = { rightRailDockedOpen = false },
                            onToggleTaskDone = onToggleTaskDone,
                            onSelectTask = onSelectTask,
                            onOpenTaskSession = onOpenTaskSession,
                            onScheduleTask = onOpenScheduleTask,
                            onDeleteBlock = onDeleteTaskBlock,
                            onQuickPlanDay = onQuickPlanDay,
                            onRefreshAgentChat = onRefreshAgentChat,
                            onResetAgentSession = onResetAgentSession,
                            onOpenAgentFullscreen = onOpenAgentFullscreen,
                            onOpenSettings = onOpenSettings,
                            onAskAgent = onAskAgent,
                            onRemoveAttachment = onRemoveAttachment,
                            onAttach = { attachOpen = true },
                            onOpenModels = {
                                modelsOpen = true
                                onRefreshModels(false)
                            },
                            onApplyProposal = onApplyProposal,
                            onDiscardProposal = onDiscardProposal,
                            onRevertProposal = onRevertProposal,
                            onUpdateProposal = onUpdateProposal,
                            onRestageProposal = onRestageProposal,
                            onDeleteProposal = onDeleteProposal,
                            onAnswerRequest = onAnswerAgentRequest,
                            defaultProposalsExpanded = true,
                            modifier = Modifier.width(360.dp).fillMaxHeight(),
                        )
                    }
                }
            }

            if (!wideLeft) {
                BottomSheet(visible = leftSheetOpen, onDismiss = { leftSheetOpen = false }) {
                    CalendarLeftRail(
                        accountsCount = state.accounts.size,
                        calendars = state.calendars,
                        miniMonthAnchor = miniMonthAnchor,
                        todayDate = state.todayDate,
                        onCreateClick = {
                            leftSheetOpen = false
                            onOpenCreateEvent(state.anchorDate, 540, false)
                        },
                        onSelectMiniDate = { d -> onSelectDate(d) },
                        onStepMiniMonth = { delta ->
                            val (y, m) = calendarAddMonths(miniMonthAnchor.year, miniMonthAnchor.month, delta)
                            calendarDateFromIso("${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-01")
                                ?.let { miniMonthAnchor = it }
                        },
                        onToggleCalendar = onToggleCalendarVisible,
                        onSetTaskTarget = onSetTaskTargetCalendar,
                        onOpenConnect = {
                            leftSheetOpen = false
                            onOpenConnectDialog()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    )
                }
            }

            if (!wideRight) {
                val sheetHeight = (rootMaxHeight * 0.82f).coerceIn(320.dp.coerceAtMost(rootMaxHeight * 0.85f), 860.dp)
                BottomSheet(visible = rightSheetOpen, onDismiss = { rightSheetOpen = false }) {
                    CalendarRightPanel(
                        state = state,
                        effectiveProposals = effectiveProposals,
                        petSprite = petSprite,
                        petState = agentPetState,
                        attachmentPicker = attachmentPicker,
                        dictation = dictation,
                        onToggleDictation = onToggleDictation,
                        selectedTab = rightTab,
                        onSelectTab = { rightTab = it },
                        onClosePanel = { rightSheetOpen = false },
                        onToggleTaskDone = onToggleTaskDone,
                        onSelectTask = { t ->
                            rightSheetOpen = false
                            onSelectTask(t)
                        },
                        onOpenTaskSession = { tid ->
                            rightSheetOpen = false
                            onOpenTaskSession(tid)
                        },
                        onScheduleTask = { t, b ->
                            rightSheetOpen = false
                            onOpenScheduleTask(t, b)
                        },
                        onDeleteBlock = onDeleteTaskBlock,
                        onQuickPlanDay = onQuickPlanDay,
                        onRefreshAgentChat = onRefreshAgentChat,
                        onResetAgentSession = onResetAgentSession,
                        onOpenAgentFullscreen = onOpenAgentFullscreen,
                        onOpenSettings = onOpenSettings,
                        onAskAgent = onAskAgent,
                        onRemoveAttachment = onRemoveAttachment,
                        onAttach = { attachOpen = true },
                        onOpenModels = {
                            modelsOpen = true
                            onRefreshModels(false)
                        },
                        onApplyProposal = onApplyProposal,
                        onDiscardProposal = onDiscardProposal,
                        onRevertProposal = onRevertProposal,
                        onUpdateProposal = onUpdateProposal,
                        onRestageProposal = onRestageProposal,
                        onDeleteProposal = onDeleteProposal,
                        onAnswerRequest = onAnswerAgentRequest,
                        defaultProposalsExpanded = false,
                        modifier = Modifier.fillMaxWidth().height(sheetHeight).testTag("calendar_right_sheet_content"),
                    )
                }
            }
        }
    }

    ConnectCalendarDialog(
        visible = state.connectDialogOpen,
        health = state.health,
        oauthPending = state.oauthPending,
        oauthBusy = state.oauthBusy,
        oauthError = state.oauthError,
        onDismiss = onCloseConnectDialog,
        onImportHermesGoogle = onImportHermesGoogle,
        onStartGoogleOAuth = onStartGoogleOAuth,
        onStartLarkOAuth = onStartLarkOAuth,
        onStartMicrosoftOAuth = onStartMicrosoftOAuth,
        onCompleteOAuth = onCompleteOAuth,
        onConnectCalDav = onConnectCalDav,
        onConnectIcs = onConnectIcs,
    )

    TaskDetailDialog(
        task = state.selectedTask,
        projects = state.dispatchOverview.projects,
        timezone = state.settings.timezone.ifBlank { null },
        use24Hour = state.use24Hour,
        mutating = state.mutating,
        openingSession = state.openingTaskSession,
        onDismiss = { onSelectTask(null) },
        onOpenSession = onOpenTaskSession,
        onScheduleBlock = onOpenScheduleTask,
        onDeleteBlock = onDeleteTaskBlock,
        onToggleDone = onToggleTaskDone,
    )

    EventDetailDialog(
        event = state.selectedEvent,
        use24Hour = state.use24Hour,
        mutating = state.mutating,
        openingSession = state.openingTaskSession,
        onDismiss = { onSelectEvent(null) },
        onEdit = onOpenEditEvent,
        onDelete = onDeleteEvent,
        onUnscheduleBlock = onDeleteTaskBlock,
        onOpenTaskSession = onOpenTaskSession,
        onRescheduleBlock = { ev ->
            val tid = ev.taskId ?: return@EventDetailDialog
            val task = state.dispatchOverview.tasks.firstOrNull { it.id == tid }
                ?: DispatchCalendarTask(
                    id = tid,
                    title = ev.title.removePrefix("[Task] ").trim().ifBlank { ev.title },
                )
            val blk = ev.timeBlockId?.let { bid ->
                task.timeBlocks.firstOrNull { it.id == bid }
                    ?: if (ev.startAt != null && ev.endAt != null) {
                        DispatchTimeBlock(
                            id = bid,
                            taskId = tid,
                            label = ev.title,
                            startAt = ev.startAt.orEmpty(),
                            endAt = ev.endAt.orEmpty(),
                            timezone = ev.timezone,
                        )
                    } else null
            }
            onOpenScheduleTask(task, blk)
        },
    )

    EventEditorDialog(
        draft = state.editingEventDraft,
        calendars = state.calendars,
        use24Hour = state.use24Hour,
        mutating = state.mutating,
        onDismiss = onCloseEventEditor,
        onSave = onSaveEvent,
        onDelete = { occId -> onDeleteEvent(occId, false) },
    )

    ScheduleTaskBlockDialog(
        draft = state.schedulingTask,
        freeSlots = state.freeSlots,
        findingSlots = state.findingFreeSlots,
        use24Hour = state.use24Hour,
        mutating = state.mutating,
        onDismiss = onCloseScheduleTask,
        onRefreshSlots = onRefreshSlots,
        onSave = onSaveTaskBlock,
        onDeleteBlock = onDeleteTaskBlock,
        onOpenSession = onOpenTaskSession,
    )

    CalendarSettingsDialog(
        visible = state.settingsDialogOpen,
        settings = state.settings,
        accounts = state.accounts,
        use24Hour = state.use24Hour,
        bots = state.bots,
        botAvatars = state.botAvatars,
        petSprite = petSprite,
        petState = agentPetState,
        resettingAgent = state.resettingAgent,
        mutating = state.mutating,
        onDismiss = onCloseSettings,
        onSave = onSaveSettings,
        onDisconnectAccount = onDisconnectAccount,
        onOpenPetPicker = onOpenPetPicker,
        onResetAgentSession = onResetAgentSession,
    )

    ScheduleProposalConflictDialog(
        conflict = state.pendingConflict,
        busy = state.pendingConflict?.proposalId in state.busyProposalIds,
        onDismiss = onDismissConflict,
        onForceApply = onForceApplyConflict,
    )

    if (attachmentPicker != null) {
        AttachSheet(
            visible = attachOpen,
            onDismiss = { attachOpen = false },
            picker = attachmentPicker,
            subtitle = null,
        )
    }

    ModelSheet(
        visible = modelsOpen,
        onDismiss = { modelsOpen = false },
        state = state.agentChatState,
        picker = state.modelPicker,
        onRefresh = { onRefreshModels(true) },
        onSelectModel = { model ->
            onSelectModel(model, false)
            modelsOpen = false
        },
        onSelectEffort = onSelectEffort,
        onFast = onSetFast,
        onSettings = onUpdateSettings,
        anchor = { modelPillBounds.value.takeIf { isDesktop } },
    )
    ModelConfirmDialog(
        pending = state.modelPicker.confirm,
        onConfirm = { model -> onSelectModel(model, true) },
        onDismiss = onDismissModelConfirm,
    )
    }
}

@Composable
private fun CalendarTopBar(
    rangeTitle: String,
    viewMode: CalendarViewMode,
    showWeekends: Boolean,
    showDeclined: Boolean,
    showCompletedTasks: Boolean,
    syncing: Boolean,
    compact: Boolean,
    stagedCount: Int,
    onToggleSidebar: (() -> Unit)?,
    onBack: () -> Unit,
    onToggleCalendarsPanel: () -> Unit,
    onGoToday: () -> Unit,
    onStepPeriod: (Int) -> Unit,
    onSyncNow: () -> Unit,
    onOpenCreate: () -> Unit,
    onSetViewMode: (CalendarViewMode) -> Unit,
    onToggleWeekends: () -> Unit,
    onToggleDeclined: () -> Unit,
    onToggleCompletedTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleRightPanel: () -> Unit,
) {
    var viewMenuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface])
            .clearOfWindowControls(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggleSidebar != null) {
                IconButton(Lucide.PanelLeft, contentDescription = stringResource(Res.string.cal_back), onClick = onToggleSidebar)
            } else {
                IconButton(Lucide.ArrowLeft, contentDescription = stringResource(Res.string.cal_back), onClick = onBack)
            }

            if (!compact) {
                IconButton(Lucide.CalendarDays, contentDescription = stringResource(Res.string.cal_toggle_calendars), onClick = onToggleCalendarsPanel)
                Button(
                    text = stringResource(Res.string.cal_today),
                    onClick = onGoToday,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                )
                IconButton(Lucide.ArrowLeft, contentDescription = stringResource(Res.string.cal_previous), onClick = { onStepPeriod(-1) })
                IconButton(Lucide.ArrowRight, contentDescription = stringResource(Res.string.cal_next), onClick = { onStepPeriod(1) })
            }

            Text(
                rangeTitle,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold, fontSize = if (compact) 14.sp else 16.sp),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )

            if (compact) {
                IconButton(Lucide.Plus, contentDescription = stringResource(Res.string.cal_create), onClick = onOpenCreate)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    if (syncing) {
                        Spinner(Modifier.padding(10.dp).size(18.dp))
                    } else {
                        IconButton(Lucide.RefreshCw, contentDescription = stringResource(Res.string.cal_sync_now), onClick = onSyncNow)
                    }
                }
            }

            val currentViewLabel = when (viewMode) {
                CalendarViewMode.DAY -> stringResource(Res.string.cal_view_day)
                CalendarViewMode.THREE_DAYS -> stringResource(Res.string.cal_view_three_days)
                CalendarViewMode.FOUR_DAYS -> stringResource(Res.string.cal_view_four_days)
                CalendarViewMode.WEEK -> stringResource(Res.string.cal_view_week)
                CalendarViewMode.MONTH -> stringResource(Res.string.cal_view_month)
                CalendarViewMode.SCHEDULE -> stringResource(Res.string.cal_view_schedule)
            }

            DropdownMenu(
                expanded = viewMenuOpen,
                onExpandedChange = { viewMenuOpen = it },
                items = {
                    MenuChoice(stringResource(Res.string.cal_view_day), Lucide.Calendar, viewMode == CalendarViewMode.DAY, onClick = {
                        viewMenuOpen = false; onSetViewMode(CalendarViewMode.DAY)
                    })
                    MenuChoice(stringResource(Res.string.cal_view_three_days), Lucide.CalendarDays, viewMode == CalendarViewMode.THREE_DAYS, onClick = {
                        viewMenuOpen = false; onSetViewMode(CalendarViewMode.THREE_DAYS)
                    })
                    MenuChoice(stringResource(Res.string.cal_view_four_days), Lucide.CalendarDays, viewMode == CalendarViewMode.FOUR_DAYS, onClick = {
                        viewMenuOpen = false; onSetViewMode(CalendarViewMode.FOUR_DAYS)
                    })
                    MenuChoice(stringResource(Res.string.cal_view_week), Lucide.CalendarDays, viewMode == CalendarViewMode.WEEK, onClick = {
                        viewMenuOpen = false; onSetViewMode(CalendarViewMode.WEEK)
                    })
                    MenuChoice(stringResource(Res.string.cal_view_month), Lucide.Calendar, viewMode == CalendarViewMode.MONTH, onClick = {
                        viewMenuOpen = false; onSetViewMode(CalendarViewMode.MONTH)
                    })
                    MenuChoice(stringResource(Res.string.cal_view_schedule), Lucide.ListTodo, viewMode == CalendarViewMode.SCHEDULE, onClick = {
                        viewMenuOpen = false; onSetViewMode(CalendarViewMode.SCHEDULE)
                    })
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Theme[colors][stroke]))
                    MenuChoice(stringResource(Res.string.cal_show_weekends), Lucide.Check, showWeekends, onClick = {
                        viewMenuOpen = false; onToggleWeekends()
                    })
                    MenuChoice(stringResource(Res.string.cal_show_declined), Lucide.Check, showDeclined, onClick = {
                        viewMenuOpen = false; onToggleDeclined()
                    })
                    MenuChoice(stringResource(Res.string.cal_show_completed_tasks), Lucide.Check, showCompletedTasks, onClick = {
                        viewMenuOpen = false; onToggleCompletedTasks()
                    })
                    if (compact) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Theme[colors][stroke]))
                        MenuChoice(stringResource(Res.string.cal_today), Lucide.Clock, false, onClick = {
                            viewMenuOpen = false; onGoToday()
                        })
                        MenuChoice(stringResource(Res.string.cal_sync_now), Lucide.RefreshCw, false, onClick = {
                            viewMenuOpen = false; onSyncNow()
                        })
                        MenuChoice(stringResource(Res.string.cal_settings_title), Lucide.Settings2, false, onClick = {
                            viewMenuOpen = false; onOpenSettings()
                        })
                    }
                },
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Theme[colors][surface2])
                        .border(1.dp, Theme[colors][strokeStrong], CircleShape)
                        .clickable(role = Role.Button) { viewMenuOpen = true }
                        .handCursor()
                        .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (compact) currentViewLabel.take(3) else currentViewLabel,
                        style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textColor],
                        maxLines = 1,
                    )
                    UnstyledIcon(Lucide.ChevronDown, contentDescription = stringResource(Res.string.cal_view_settings), tint = Theme[colors][textSecondary], modifier = Modifier.size(14.dp))
                }
            }

            if (!compact) {
                IconButton(Lucide.Settings2, contentDescription = stringResource(Res.string.cal_settings_title), onClick = onOpenSettings)
            }

            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(Lucide.PanelRight, contentDescription = stringResource(Res.string.cal_dispatch_panel_toggle), onClick = onToggleRightPanel)
                if (stagedCount > 0) {
                    Box(
                        Modifier
                            .padding(6.dp)
                            .size(8.dp)
                            .background(Theme[colors][accent], CircleShape),
                    )
                }
            }
        }

        if (compact) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(Lucide.CalendarDays, contentDescription = stringResource(Res.string.cal_toggle_calendars), onClick = onToggleCalendarsPanel)
                Button(
                    text = stringResource(Res.string.cal_today),
                    onClick = onGoToday,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                )
                IconButton(Lucide.ArrowLeft, contentDescription = stringResource(Res.string.cal_previous), onClick = { onStepPeriod(-1) })
                IconButton(Lucide.ArrowRight, contentDescription = stringResource(Res.string.cal_next), onClick = { onStepPeriod(1) })
                Spacer(Modifier.weight(1f))
                IconButton(Lucide.Settings2, contentDescription = stringResource(Res.string.cal_settings_title), onClick = onOpenSettings)
                Box(contentAlignment = Alignment.Center) {
                    if (syncing) {
                        Spinner(Modifier.padding(10.dp).size(18.dp))
                    } else {
                        IconButton(Lucide.RefreshCw, contentDescription = stringResource(Res.string.cal_sync_now), onClick = onSyncNow)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLeftRail(
    accountsCount: Int,
    calendars: List<CalendarInfo>,
    miniMonthAnchor: CalendarDate,
    todayDate: CalendarDate,
    onCreateClick: () -> Unit,
    onSelectMiniDate: (CalendarDate) -> Unit,
    onStepMiniMonth: (Int) -> Unit,
    onToggleCalendar: (CalendarInfo) -> Unit,
    onSetTaskTarget: (CalendarInfo) -> Unit,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val myCals = remember(calendars) { calendars.filter { it.writable } }
    val otherCals = remember(calendars) { calendars.filterNot { it.writable } }

    Box(modifier.background(Theme[colors][sidebar])) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                text = stringResource(Res.string.cal_create),
                onClick = onCreateClick,
                variant = ButtonVariant.Primary,
                leadingIcon = Lucide.Plus,
                modifier = Modifier.fillMaxWidth(),
            )

            MiniMonthCalendar(
                anchorDate = miniMonthAnchor,
                todayDate = todayDate,
                onSelectDate = onSelectMiniDate,
                onStepMonth = onStepMiniMonth,
            )

            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

            if (myCals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.cal_my_calendars),
                        style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textSecondary],
                    )
                    myCals.forEach { cal ->
                        CalendarLayerRow(cal, onToggle = { onToggleCalendar(cal) }, onSetTaskTarget = { onSetTaskTarget(cal) })
                    }
                }
            }

            if (otherCals.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.cal_other_calendars),
                        style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textSecondary],
                    )
                    otherCals.forEach { cal ->
                        CalendarLayerRow(cal, onToggle = { onToggleCalendar(cal) }, onSetTaskTarget = null)
                    }
                }
            }

            Button(
                text = if (accountsCount == 0) stringResource(Res.string.cal_connect_account) else "+ ${stringResource(Res.string.cal_connect_account)}",
                onClick = onOpenConnect,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                leadingIcon = if (accountsCount == 0) Lucide.Plus else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VerticalScrollbar(scroll, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 2.dp))
    }
}

@Composable
private fun CalendarLayerRow(
    cal: CalendarInfo,
    onToggle: () -> Unit,
    onSetTaskTarget: (() -> Unit)?,
) {
    val fill = parseCalendarHexColor(cal.colorHex, Theme[colors][accent])
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .handCursor()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (cal.visible) fill else Theme[colors][surface])
                .border(1.5.dp, fill, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (cal.visible) {
                UnstyledIcon(Lucide.Check, contentDescription = null, tint = contrastOnFill(fill), modifier = Modifier.size(11.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                cal.name,
                style = Theme[typography][bodySmall],
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (cal.isTaskTarget) {
                Text(
                    stringResource(Res.string.cal_task_target_badge),
                    style = Theme[typography][caption].copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = Theme[colors][accentText],
                )
            } else if (!cal.writable) {
                Text(
                    stringResource(Res.string.cal_read_only),
                    style = Theme[typography][caption].copy(fontSize = 10.sp),
                    color = Theme[colors][textTertiary],
                )
            }
        }
        if (onSetTaskTarget != null && !cal.isTaskTarget && cal.writable) {
            IconButton(
                Lucide.Target,
                contentDescription = stringResource(Res.string.cal_set_task_target),
                onClick = onSetTaskTarget,
                iconSize = 14.dp,
            )
        }
    }
}

@Composable
private fun ConnectBannerRow(
    onConnectClick: () -> Unit,
    onPlanClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][accentSoft])
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(Lucide.Calendar, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(16.dp))
        Text(
            stringResource(Res.string.cal_connect_title),
            style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][textColor],
            modifier = Modifier.weight(1f),
        )
        Button(stringResource(Res.string.cal_connect_account), onClick = onConnectClick, variant = ButtonVariant.Primary, size = ButtonSize.Small)
        Button(stringResource(Res.string.cal_plan_my_day), onClick = onPlanClick, variant = ButtonVariant.Outline, size = ButtonSize.Small)
    }
}

@Composable
private fun StagedProposalBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][accentSoft])
            .clickable(role = Role.Button, onClick = onClick)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(Lucide.Sparkles, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(14.dp))
        Text(
            "${stringResource(Res.string.cal_proposal_heading)} ($count)",
            style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
            color = Theme[colors][accentText],
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(Res.string.cal_proposal_apply), style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][accent])
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarRightPanel(
    state: CalendarUiState,
    effectiveProposals: List<ScheduleProposal>,
    petSprite: PetSprite?,
    petState: PetState,
    attachmentPicker: AttachmentPicker?,
    dictation: DictationState,
    onToggleDictation: ((TextFieldState) -> Unit)?,
    selectedTab: CalendarRightTab,
    onSelectTab: (CalendarRightTab) -> Unit,
    onClosePanel: () -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
    onSelectTask: (DispatchCalendarTask) -> Unit,
    onOpenTaskSession: (String) -> Unit,
    onScheduleTask: (DispatchCalendarTask, DispatchTimeBlock?) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onQuickPlanDay: () -> Unit,
    onRefreshAgentChat: () -> Unit,
    onResetAgentSession: () -> Unit,
    onOpenAgentFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onAskAgent: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onApplyProposal: (String, List<String>, Boolean) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onRevertProposal: (String) -> Unit,
    onUpdateProposal: (String, UpdateProposalRequest) -> Unit,
    onRestageProposal: (String) -> Unit,
    onDeleteProposal: (String) -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
    defaultProposalsExpanded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Theme[colors][sidebar])) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(
                text = stringResource(Res.string.cal_dispatch_tasks),
                selected = selectedTab == CalendarRightTab.TASKS,
                onClick = { onSelectTab(CalendarRightTab.TASKS) },
                count = state.dispatchOverview.tasks.count { it.status != "done" },
            )
            Chip(
                text = stringResource(Res.string.cal_agent_title),
                selected = selectedTab == CalendarRightTab.AGENT,
                onClick = { onSelectTab(CalendarRightTab.AGENT) },
                count = effectiveProposals.count { it.status == ScheduleProposal.STATUS_STAGED }.takeIf { it > 0 },
            )
            Spacer(Modifier.weight(1f))
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        if (selectedTab == CalendarRightTab.TASKS) {
            DispatchTasksBacklogTab(
                state = state,
                onToggleTaskDone = onToggleTaskDone,
                onSelectTask = onSelectTask,
                onOpenTaskSession = onOpenTaskSession,
                onScheduleTask = onScheduleTask,
                onDeleteBlock = onDeleteBlock,
                onPlanWithAgent = {
                    onSelectTab(CalendarRightTab.AGENT)
                    onQuickPlanDay()
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            CalendarAgentPanel(
                state = state,
                effectiveProposals = effectiveProposals,
                petSprite = petSprite,
                petState = petState,
                attachmentPicker = attachmentPicker,
                dictation = dictation,
                onToggleDictation = onToggleDictation,
                onClose = onClosePanel,
                onQuickPlanDay = onQuickPlanDay,
                onRefreshAgentChat = onRefreshAgentChat,
                onResetAgentSession = onResetAgentSession,
                onOpenAgentFullscreen = onOpenAgentFullscreen,
                onOpenSettings = onOpenSettings,
                onAskAgent = onAskAgent,
                onRemoveAttachment = onRemoveAttachment,
                onAttach = onAttach,
                onOpenModels = onOpenModels,
                onApplyProposal = onApplyProposal,
                onDiscardProposal = onDiscardProposal,
                onRevertProposal = onRevertProposal,
                onUpdateProposal = onUpdateProposal,
                onRestageProposal = onRestageProposal,
                onDeleteProposal = onDeleteProposal,
                onAnswerRequest = onAnswerRequest,
                defaultProposalsExpanded = defaultProposalsExpanded,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DispatchTasksBacklogTab(
    state: CalendarUiState,
    onToggleTaskDone: (String, Boolean) -> Unit,
    onSelectTask: (DispatchCalendarTask) -> Unit,
    onOpenTaskSession: (String) -> Unit,
    onScheduleTask: (DispatchCalendarTask, DispatchTimeBlock?) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onPlanWithAgent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val tz = state.settings.timezone.ifBlank { null }
    val unscheduled = remember(state.dispatchOverview.tasks) {
        state.dispatchOverview.tasks.filter { it.status != "done" && it.timeBlocks.isEmpty() }
    }
    val scheduled = remember(state.dispatchOverview.tasks) {
        state.dispatchOverview.tasks.filter { it.status != "done" && it.timeBlocks.isNotEmpty() }
    }

    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                text = stringResource(Res.string.cal_ask_agent_plan, state.agentBot.label),
                onClick = onPlanWithAgent,
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                leadingIcon = Lucide.Sparkles,
                enabled = state.gatewayConnected && !state.agentTurnRunning,
                modifier = Modifier.fillMaxWidth(),
            )

            if (unscheduled.isNotEmpty()) {
                Text(
                    stringResource(Res.string.cal_unscheduled_tasks),
                    style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textSecondary],
                )
                unscheduled.forEach { task ->
                    DispatchBacklogTaskCard(
                        task = task,
                        tz = tz,
                        use24Hour = state.use24Hour,
                        onToggleDone = { onToggleTaskDone(task.id, task.status != "done") },
                        onSelectTask = { onSelectTask(task) },
                        onOpenSession = { onOpenTaskSession(task.id) },
                        onSchedule = { onScheduleTask(task, null) },
                        onEditBlock = { blk -> onScheduleTask(task, blk) },
                        onDeleteBlock = onDeleteBlock,
                    )
                }
            }

            if (scheduled.isNotEmpty()) {
                Text(
                    stringResource(Res.string.cal_scheduled_tasks),
                    style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textSecondary],
                )
                scheduled.forEach { task ->
                    DispatchBacklogTaskCard(
                        task = task,
                        tz = tz,
                        use24Hour = state.use24Hour,
                        onToggleDone = { onToggleTaskDone(task.id, task.status != "done") },
                        onSelectTask = { onSelectTask(task) },
                        onOpenSession = { onOpenTaskSession(task.id) },
                        onSchedule = { onScheduleTask(task, task.timeBlocks.firstOrNull()) },
                        onEditBlock = { blk -> onScheduleTask(task, blk) },
                        onDeleteBlock = onDeleteBlock,
                    )
                }
            }
        }
        VerticalScrollbar(scroll, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DispatchBacklogTaskCard(
    task: DispatchCalendarTask,
    tz: String?,
    use24Hour: Boolean,
    onToggleDone: () -> Unit,
    onSelectTask: () -> Unit,
    onOpenSession: () -> Unit,
    onSchedule: () -> Unit,
    onEditBlock: (DispatchTimeBlock) -> Unit,
    onDeleteBlock: (String) -> Unit,
) {
    val isDone = task.status == "done"
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface], RoundedCornerShape(10.dp))
            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Checkbox, onClick = onToggleDone)
                    .handCursor(),
                contentAlignment = Alignment.Center,
            ) {
                UnstyledIcon(
                    if (isDone) Lucide.CircleCheck else Lucide.Circle,
                    contentDescription = task.title,
                    tint = if (isDone) Theme[colors][accent] else Theme[colors][textSecondary],
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onSelectTask)
                    .handCursor(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    task.title,
                    style = Theme[typography][bodySmall].copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = Theme[colors][textColor],
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.dueDate?.take(10)?.let { due ->
                        Text(stringResource(Res.string.cal_task_due, due), style = Theme[typography][caption], color = Theme[colors][textTertiary])
                    }
                    task.estimatedMinutes?.let { est ->
                        Text(stringResource(Res.string.cal_estimated_minutes, est), style = Theme[typography][caption], color = Theme[colors][accentText])
                    }
                }
            }
            IconButton(
                icon = Lucide.MessageSquare,
                contentDescription = stringResource(Res.string.cal_open_session),
                onClick = onOpenSession,
            )
        }
        task.timeBlocks.forEach { blk ->
            val s = calendarZonedFromIso(blk.startAt, tz)
            val e = calendarZonedFromIso(blk.endAt, tz)
            val label = if (s != null && e != null) {
                "${s.date.iso.drop(5)} ${formatClockCompact(s.minuteOfDay, use24Hour)}–${formatClockCompact(e.minuteOfDay, use24Hour)}"
            } else blk.startAt
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Theme[colors][surface2], RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { onEditBlock(blk) }
                    .handCursor()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = Theme[typography][caption], color = Theme[colors][textSecondary])
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onDeleteBlock(blk.id) }
                        .padding(2.dp),
                ) {
                    UnstyledIcon(Lucide.X, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(12.dp))
                }
            }
        }
        if (task.timeBlocks.isEmpty() && !isDone) {
            Button(
                text = stringResource(Res.string.cal_block_slot),
                onClick = onSchedule,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                leadingIcon = Lucide.CalendarClock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CalendarAgentPanel(
    state: CalendarUiState,
    effectiveProposals: List<ScheduleProposal>,
    petSprite: PetSprite?,
    petState: PetState,
    attachmentPicker: AttachmentPicker?,
    dictation: DictationState,
    onToggleDictation: ((TextFieldState) -> Unit)?,
    onClose: () -> Unit,
    onQuickPlanDay: () -> Unit,
    onRefreshAgentChat: () -> Unit,
    onResetAgentSession: () -> Unit,
    onOpenAgentFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onAskAgent: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onApplyProposal: (String, List<String>, Boolean) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onRevertProposal: (String) -> Unit,
    onUpdateProposal: (String, UpdateProposalRequest) -> Unit,
    onRestageProposal: (String) -> Unit,
    onDeleteProposal: (String) -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
    defaultProposalsExpanded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val promptState = rememberTextFieldState("")
    val quickScroll = rememberScrollState()
    val proposalsScroll = rememberScrollState()
    val agentBot = state.agentBot
    val stagedProposals = remember(effectiveProposals) {
        effectiveProposals.filter { it.status == ScheduleProposal.STATUS_STAGED }.take(4)
    }
    var proposalsExpanded by remember(defaultProposalsExpanded) { mutableStateOf(defaultProposalsExpanded) }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.semantics { contentDescription = "${agentBot.label} · @${agentBot.name}" }) {
                if (petSprite != null) {
                    PetAvatar(
                        sprite = petSprite,
                        state = petState,
                        size = 28.dp,
                    )
                } else {
                    BotAvatar(
                        bot = agentBot,
                        picture = state.botAvatars[agentBot.name],
                        size = 28.dp,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    agentBot.label,
                    style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "@${agentBot.name}",
                    style = Theme[typography][caption],
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.agentSessionId != null) {
                IconButton(
                    Lucide.RefreshCw,
                    contentDescription = stringResource(Res.string.cal_sync_now),
                    onClick = onRefreshAgentChat,
                    modifier = Modifier.testTag("calendar_agent_refresh_button"),
                )
            }
            if (state.resettingAgent) {
                Spinner(Modifier.size(16.dp).padding(horizontal = 4.dp))
            } else {
                IconButton(
                    Lucide.RotateCcw,
                    contentDescription = stringResource(Res.string.cal_agent_reset_session),
                    onClick = onResetAgentSession,
                    modifier = Modifier.testTag("calendar_agent_reset_session_button"),
                )
            }
            if (state.gatewayConnected || state.agentChannel != null) {
                IconButton(
                    Lucide.ExternalLink,
                    contentDescription = stringResource(Res.string.cal_agent_open_fullscreen),
                    onClick = onOpenAgentFullscreen,
                    modifier = Modifier.testTag("calendar_agent_fullscreen_button"),
                )
            }
            IconButton(
                Lucide.Settings2,
                contentDescription = stringResource(Res.string.cal_agent_settings),
                onClick = onOpenSettings,
                modifier = Modifier.testTag("calendar_agent_settings_button"),
            )
            IconButton(
                Lucide.X,
                contentDescription = stringResource(Res.string.tasks_close_inspector),
                onClick = onClose,
                modifier = Modifier.testTag("calendar_agent_close_button"),
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        if (stagedProposals.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Theme[colors][surface2])
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button) { proposalsExpanded = !proposalsExpanded }
                        .handCursor()
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                        .testTag("calendar_proposals_toggle"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UnstyledIcon(
                        Lucide.Calendar,
                        contentDescription = null,
                        tint = Theme[colors][accentText],
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(Res.string.cal_proposal_staged_summary, stagedProposals.size),
                        style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                        color = Theme[colors][textColor],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (stagedProposals.size == 1) {
                    val onlyId = stagedProposals.first().id
                    IconButton(
                        Lucide.Trash2,
                        contentDescription = stringResource(Res.string.cal_proposal_delete),
                        onClick = { onDeleteProposal(onlyId) },
                        enabled = onlyId !in state.busyProposalIds,
                        modifier = Modifier.testTag("calendar_proposals_clear_top"),
                    )
                }
                IconButton(
                    if (proposalsExpanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = stringResource(
                        if (proposalsExpanded) Res.string.cal_proposal_hide else Res.string.cal_proposal_show,
                    ),
                    onClick = { proposalsExpanded = !proposalsExpanded },
                )
            }
            if (proposalsExpanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (defaultProposalsExpanded) 280.dp else 220.dp)
                        .verticalScroll(proposalsScroll)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    stagedProposals.forEach { prop ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Theme[colors][surface], RoundedCornerShape(10.dp))
                                .border(1.dp, Theme[colors][strokeStrong], RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ScheduleProposalContent(
                                cardKey = "cal-rail-${prop.id}",
                                proposal = prop,
                                overrideStatus = state.proposalStatuses[prop.id],
                                enabled = prop.id !in state.busyProposalIds,
                                use24Hour = state.use24Hour,
                                isDeleted = prop.id in state.deletedProposalIds,
                                onApply = { opIds, force -> onApplyProposal(prop.id, opIds, force) },
                                onDiscard = { onDiscardProposal(prop.id) },
                                onRevert = { onRevertProposal(prop.id) },
                                onUpdate = { req -> onUpdateProposal(prop.id, req) },
                                onRestage = { onRestageProposal(prop.id) },
                                onDelete = { onDeleteProposal(prop.id) },
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))
        }

        TaskChannelLog(
            activeChannel = state.agentChannel,
            loading = state.agentLoading,
            messages = state.agentMessages,
            bots = state.bots,
            botAvatars = state.botAvatars,
            coordinatorBotName = agentBot.name,
            petSprite = petSprite,
            petState = petState,
            emptyText = stringResource(Res.string.cal_agent_panel_empty),
            error = state.agentError,
            turnRunning = state.agentTurnRunning,
            turnStatus = state.agentTurnStatus,
            showHeader = false,
            onRefresh = onRefreshAgentChat,
            onOpenFullscreen = if (state.agentChannel != null) onOpenAgentFullscreen else null,
            onSelectInteraction = onAskAgent,
            onFillInteraction = { text -> promptState.setTextAndPlaceCursorAtEnd(text) },
            appliedProposalStatuses = state.proposalStatuses,
            updatedProposals = state.updatedProposals,
            deletedProposalIds = state.deletedProposalIds,
            onApplyProposal = onApplyProposal,
            onDiscardProposal = onDiscardProposal,
            onRevertProposal = onRevertProposal,
            onUpdateProposal = onUpdateProposal,
            onRestageProposal = onRestageProposal,
            onDeleteProposal = onDeleteProposal,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (state.agentError != null && state.agentMessages.isNotEmpty() && !state.agentTurnRunning) {
            Text(
                state.agentError,
                style = Theme[typography][caption],
                color = Theme[colors][danger],
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        if (state.agentInputRequests.isNotEmpty()) {
            val requestScroll = rememberScrollState()
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .scrollbar(requestScroll)
                    .verticalScroll(requestScroll),
            ) {
                InputRequestPanel(
                    requests = state.agentInputRequests,
                    connected = state.gatewayConnected,
                    onAnswer = onAnswerRequest,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(quickScroll)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(
                text = stringResource(Res.string.cal_plan_my_day),
                selected = false,
                onClick = { if (state.gatewayConnected && !state.agentTurnRunning) onQuickPlanDay() },
            )
        }

        TaskChatComposer(
            state = promptState,
            connected = state.gatewayConnected,
            attachments = state.agentAttachments,
            channelChatState = state.agentChatState,
            modelPicker = state.modelPicker,
            showModelPicker = true,
            attachmentPicker = attachmentPicker,
            dictation = dictation,
            onToggleDictation = onToggleDictation,
            placeholderText = stringResource(Res.string.cal_agent_ask, agentBot.label),
            inputTestTag = "calendar_chat_composer_input",
            onRemoveAttachment = onRemoveAttachment,
            onAttach = onAttach,
            onOpenModels = onOpenModels,
            onSend = {
                val txt = promptState.text.toString().trim()
                if (txt.isNotEmpty() || state.agentAttachments.isNotEmpty()) {
                    onAskAgent(txt)
                    promptState.clearText()
                }
            },
        )
    }
}
