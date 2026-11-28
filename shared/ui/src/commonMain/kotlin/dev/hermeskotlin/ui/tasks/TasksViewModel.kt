package dev.hermeskotlin.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.calendar.CalendarApi
import dev.hermeskotlin.core.calendar.CreateTimeBlockRequest
import dev.hermeskotlin.core.calendar.DispatchCalendarTask
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.FreeSlot
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.UpdateProposalRequest
import dev.hermeskotlin.core.calendar.UpdateTimeBlockRequest
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.calendar.calendarNowZoned
import dev.hermeskotlin.core.calendar.calendarToUtcIso
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClock24
import dev.hermeskotlin.core.calendar.plusDays
import dev.hermeskotlin.core.chat.AttachmentKind
import dev.hermeskotlin.core.chat.AttentionTracker
import dev.hermeskotlin.core.chat.ChatState
import dev.hermeskotlin.core.chat.IMAGE_ONLY_FALLBACK_PROMPT
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.chat.ShownAttachment
import dev.hermeskotlin.core.chat.attachToSession
import dev.hermeskotlin.core.chat.detachSessionImages
import dev.hermeskotlin.core.chat.splitAttachmentRefs
import dev.hermeskotlin.core.chat.formatActionCardReply
import dev.hermeskotlin.core.chat.formatActionCardSummary
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.models.ModelOption
import dev.hermeskotlin.core.models.ModelsApi
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.errorMessage
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectsApi
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.rooms.RoomLine
import dev.hermeskotlin.core.rooms.RoomMemberInput
import dev.hermeskotlin.core.rooms.RoomSeenStore
import dev.hermeskotlin.core.rooms.RoomsApi
import dev.hermeskotlin.core.rooms.roomLines
import dev.hermeskotlin.core.rpc.GatewayEvent
import dev.hermeskotlin.core.rpc.JsonRpcClient
import dev.hermeskotlin.core.rpc.RpcException
import dev.hermeskotlin.core.rpc.ServerRequest
import dev.hermeskotlin.core.sessions.SeenStore
import dev.hermeskotlin.core.sessions.SessionMessage
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.tasks.ApproveTaskRequest
import dev.hermeskotlin.core.tasks.CreateProjectRequest
import dev.hermeskotlin.core.tasks.CreateTaskRequest
import dev.hermeskotlin.core.tasks.DirectusConfigStatus
import dev.hermeskotlin.core.tasks.DirectusConfigUpdate
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import dev.hermeskotlin.core.tasks.DispatchCoordinatorConfig
import dev.hermeskotlin.core.tasks.DispatchCoordinatorUpdate
import dev.hermeskotlin.core.tasks.DispatchJumpTarget
import dev.hermeskotlin.core.tasks.DispatchSessionsStore
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TaskReplyStatus
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.core.tasks.UpdateTaskRequest
import dev.hermeskotlin.core.tasks.UpsertContextRequest
import dev.hermeskotlin.core.tasks.matrixSyncedPriority
import dev.hermeskotlin.ui.calendar.PendingProposalConflict
import dev.hermeskotlin.ui.calendar.ScheduleProposalCoordinator
import dev.hermeskotlin.ui.calendar.TaskBlockDraft
import dev.hermeskotlin.ui.calendar.errorTextOrNull
import dev.hermeskotlin.ui.calendar.parseTimeHHMM
import dev.hermeskotlin.ui.chat.ModelPickerState
import dev.hermeskotlin.ui.chat.PendingSwitch
import dev.hermeskotlin.ui.components.localMoment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface TasksNavEvent {
    data class OpenSession(val sessionId: String, val title: String, val profile: String?) : TasksNavEvent
    data class OpenRoom(val room: Room) : TasksNavEvent
}

enum class CoordinatorQuickAction {
    CHECK_IN,
    BLOCKERS,
    PRIORITIES,
}

data class ChannelChatLine(
    val key: String,
    val speaker: String?,
    val text: String,
    val fromUser: Boolean,
    val profile: String? = null,
    val streaming: Boolean = false,
    val attachments: List<ShownAttachment> = emptyList(),
    val interaction: ReplyInteraction? = null,
)

data class TasksUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val pluginMissing: Boolean = false,
    val sessionExpired: Boolean = false,
    val config: DirectusConfigStatus? = null,
    val savingConfig: Boolean = false,
    val configError: String? = null,
    val projects: List<DirectusProject> = emptyList(),
    val tasks: List<DirectusTask> = emptyList(),
    val hermesProjects: List<Project> = emptyList(),
    val bots: List<Bot> = emptyList(),
    val botAvatars: Map<String, ByteArray> = emptyMap(),
    val includeDone: Boolean = false,
    val unreadOnly: Boolean = false,
    val showPriorityStrip: Boolean = true,
    val boardViewMode: TasksBoardViewMode = TasksBoardViewMode.PROJECTS,
    val focusedProjectId: String? = null,
    val focusGrouping: TaskFocusGrouping = TaskFocusGrouping.WORKSTREAM,
    val expandedProjectIds: Set<String> = emptySet(),
    val collapsedWorkstreamKeys: Set<String> = emptySet(),
    val todayEpochDay: Long = currentLocalEpochDay(),
    val seenRoomSeqs: Map<String, Int> = emptyMap(),
    val waitingSessionIds: Set<String> = emptySet(),
    val localReadAtByTaskId: Map<String, Double> = emptyMap(),
    val localUnreadTaskIds: Set<String> = emptySet(),
    val selectedTaskId: String? = null,
    val coordinatorOpen: Boolean = false,
    val resettingCoordinator: Boolean = false,
    val checkinDueSlot: String? = null,
    val activeChannelIdx: Int = 0,
    val channelLoading: Boolean = false,
    val channelMessages: List<ChannelChatLine> = emptyList(),
    val channelAttachments: List<OutgoingAttachment> = emptyList(),
    val channelChatState: ChatState = ChatState(),
    val modelPicker: ModelPickerState = ModelPickerState(),
    val channelError: String? = null,
    val busyTaskIds: Set<String> = emptySet(),
    val sendingToChannel: Boolean = false,
    val channelTurnRunning: Boolean = false,
    val channelTurnStatus: String? = null,
    val channelInputRequests: List<InputRequest> = emptyList(),
    val gatewayConnected: Boolean = true,
    val actionFeedback: String? = null,
    val taskTimeBlocks: Map<String, List<DispatchTimeBlock>> = emptyMap(),
    val calendarPluginAvailable: Boolean = false,
    val calendarTimezone: String = "Asia/Ho_Chi_Minh",
    val freeSlots: List<FreeSlot> = emptyList(),
    val findingFreeSlots: Boolean = false,
    val schedulingTaskDraft: TaskBlockDraft? = null,
    val calendarMutating: Boolean = false,
    val proposalStatuses: Map<String, String> = emptyMap(),
    val updatedProposals: Map<String, ScheduleProposal> = emptyMap(),
    val deletedProposalIds: Set<String> = emptySet(),
    val busyProposalIds: Set<String> = emptySet(),
    val pendingProposalConflict: PendingProposalConflict? = null,
) {
    val selectedTask: DirectusTask?
        get() = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }

    val coordinatorConfig: DispatchCoordinatorConfig
        get() = config?.coordinator ?: DispatchCoordinatorConfig()

    val coordinatorBot: Bot
        get() {
            val target = coordinatorConfig.profile.trim().removePrefix("@").ifEmpty { Bot.DEFAULT }
            return bots.firstOrNull { it.name.equals(target, ignoreCase = true) }
                ?: bots.firstOrNull { it.isDefault }
                ?: Bot(name = target, isDefault = target.equals(Bot.DEFAULT, ignoreCase = true))
        }

    val coordinatorChannel: TaskChannel?
        get() = coordinatorConfig.sessionId.trim().takeIf { it.isNotEmpty() }?.let { sid ->
            TaskChannel(
                type = TaskChannel.TYPE_SESSION,
                id = sid,
                label = DispatchSessionsStore.COORDINATOR_SESSION_TITLE,
                profile = coordinatorConfig.profile.ifBlank { Bot.DEFAULT },
                managed = true,
            )
        }

    val activeChannel: TaskChannel?
        get() = when {
            coordinatorOpen -> coordinatorChannel
            else -> selectedTask?.channels?.getOrNull(activeChannelIdx) ?: selectedTask?.channels?.firstOrNull()
        }

    val priorityTasks: List<DirectusTask>
        get() = tasks.filter { isHighPriorityAttention(it, todayEpochDay) }

    val replyBadges: Map<String, TaskReplyBadge>
        get() = tasks.associate { task ->
            task.id to resolveTaskReplyBadge(
                task = task,
                seenRoomSeqs = seenRoomSeqs,
                waitingSessionIds = waitingSessionIds,
                localReadAtByTaskId = localReadAtByTaskId,
                localUnreadTaskIds = localUnreadTaskIds,
            )
        }

    val unreadCount: Int
        get() = replyBadges.values.count { it != TaskReplyBadge.NONE }

    internal val attentionSplit: AttentionSplit
        get() = buildAttentionSplit(tasks, todayEpochDay, replyBadges)
}

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class TasksViewModel(
    private val directusApi: DirectusTasksApi,
    private val sessionsApi: SessionsApi,
    private val roomsApi: RoomsApi,
    private val projectsApi: ProjectsApi,
    private val botsApi: BotsApi,
    private val connection: GatewayConnection,
    private val dispatchStore: DispatchSessionsStore? = null,
    private val modelsApi: ModelsApi? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val seenStore: SeenStore? = null,
    private val roomSeenStore: RoomSeenStore? = null,
    private val attentionTracker: AttentionTracker? = null,
    private val calendarApi: CalendarApi? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _events = Channel<TasksNavEvent>(Channel.BUFFERED)
    val events: Flow<TasksNavEvent> = _events.receiveAsFlow()

    private var boundGateway: SavedGateway? = null
    private var activeRuntimeSessionId: String? = null
    private var activeRuntimeChannelId: String? = null
    private val runningTaskSessions = LinkedHashMap<String, String>()
    private var externalContextOnly: Boolean = false
    private var externalViewingTaskId: String? = null
    private var externalViewingChannelId: String? = null
    private var lastStreamEventAtMs: Long = 0L
    private var turnPollJob: Job? = null
    private var modelsLoadJob: Job? = null
    private var roomSeenJob: Job? = null
    private var refreshJob: Job? = null
    private var fetchingBoard: Boolean = false
    private var pageVisible: Boolean = false
    private var hasObservedVisibility: Boolean = false
    private var lastAutoTriggeredSlot: String? = null
    private var dismissedCheckinSlot: String? = null
    private var pendingExternalOpen: DispatchJumpTarget? = null

    private val proposalCoordinator = ScheduleProposalCoordinator(
        calendarApi = calendarApi,
        scope = viewModelScope,
        onError = { msg -> _state.update { it.copy(actionFeedback = msg) } },
        onSessionExpired = { _state.update { it.copy(sessionExpired = true) } },
        onProposalChanged = {
            boundGateway?.let { gw -> refreshTaskTimeBlocksSilently(gw) }
        },
    )

    init {
        viewModelScope.launch {
            connection.state.collect { connState ->
                val connected = connState is ConnectionState.Connected
                if (!connected) {
                    activeRuntimeSessionId = null
                    activeRuntimeChannelId = null
                }
                _state.update { it.copy(gatewayConnected = connected) }
                if (connected && !externalContextOnly && (_state.value.selectedTask != null || _state.value.coordinatorOpen) && _state.value.activeChannel?.isRoom != true && _state.value.modelPicker.catalog == null) {
                    loadModels(withPrices = false)
                }
                if (connected) {
                    maybeRunAutoCheckin()
                }
            }
        }
        viewModelScope.launch {
            connection.events.collect { event -> handleGatewayEvent(event) }
        }
        viewModelScope.launch {
            connection.serverRequests.collect { request -> handleServerRequest(request) }
        }
        if (attentionTracker != null) {
            viewModelScope.launch {
                attentionTracker.waiting.collect { waitingMap ->
                    _state.update { it.copy(waitingSessionIds = waitingMap.keys) }
                }
            }
        }
        viewModelScope.launch {
            proposalCoordinator.statuses.collect { map -> _state.update { it.copy(proposalStatuses = map) } }
        }
        viewModelScope.launch {
            proposalCoordinator.updatedProposals.collect { map -> _state.update { it.copy(updatedProposals = map) } }
        }
        viewModelScope.launch {
            proposalCoordinator.deletedProposalIds.collect { ids -> _state.update { it.copy(deletedProposalIds = ids) } }
        }
        viewModelScope.launch {
            proposalCoordinator.busyProposalIds.collect { ids -> _state.update { it.copy(busyProposalIds = ids) } }
        }
        viewModelScope.launch {
            proposalCoordinator.pendingConflict.collect { c -> _state.update { it.copy(pendingProposalConflict = c) } }
        }
        if (dispatchStore != null) {
            viewModelScope.launch {
                dispatchStore.pendingJump.collect { jump ->
                    if (jump != null && boundGateway?.gatewayUrl == jump.gateway) {
                        handleExternalJump(jump)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        roomSeenJob?.cancel()
    }

    fun bind(gateway: SavedGateway) {
        val pendingForGateway = dispatchStore?.pendingJump?.value?.takeIf { it.gateway == gateway.gatewayUrl }
        if (boundGateway == gateway && _state.value.config != null) {
            if (pendingForGateway != null) handleExternalJump(pendingForGateway)
            return
        }
        refreshJob?.cancel()
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        roomSeenJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        runningTaskSessions.clear()
        externalContextOnly = false
        externalViewingTaskId = null
        externalViewingChannelId = null
        lastAutoTriggeredSlot = null
        dismissedCheckinSlot = null
        pendingExternalOpen = null
        fetchingBoard = false
        boundGateway = gateway
        proposalCoordinator.clear()
        _state.update {
            TasksUiState(
                loading = true,
                showPriorityStrip = it.showPriorityStrip,
                boardViewMode = it.boardViewMode,
                includeDone = it.includeDone,
                focusGrouping = it.focusGrouping,
                todayEpochDay = currentLocalEpochDay(),
                waitingSessionIds = it.waitingSessionIds,
                gatewayConnected = connection.state.value is ConnectionState.Connected,
            )
        }
        if (roomSeenStore != null) {
            roomSeenJob = viewModelScope.launch {
                roomSeenStore.seen(gateway.gatewayUrl).collect { seenMap ->
                    if (boundGateway == gateway) {
                        _state.update { it.copy(seenRoomSeqs = seenMap) }
                    }
                }
            }
        }
        if (pendingForGateway != null) {
            handleExternalJump(pendingForGateway)
        } else {
            refresh()
        }
    }

    fun setVisible(visible: Boolean) {
        hasObservedVisibility = true
        val becameVisible = visible && !pageVisible
        pageVisible = visible
        if (becameVisible) {
            if (externalContextOnly) {
                externalContextOnly = false
                externalViewingTaskId = null
                externalViewingChannelId = null
            }
            refreshCheckinEligibility()
            maybeRunAutoCheckin()
            val gw = boundGateway
            if (gw != null && !_state.value.loading && !fetchingBoard && refreshJob?.isActive != true && _state.value.tasks.isNotEmpty()) {
                viewModelScope.launch {
                    refreshBoardSilently(gw)
                    val sel = _state.value.selectedTask
                    if (sel != null && _state.value.channelMessages.isEmpty() && !_state.value.coordinatorOpen) {
                        loadChannelLog(gw, sel, _state.value.activeChannelIdx)
                    }
                }
            }
        }
    }

    fun refresh() {
        val gateway = boundGateway ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, pluginMissing = false) }
            when (val cfgResult = directusApi.getConfig(gateway.gatewayUrl)) {
                is ApiResult.SessionExpired -> {
                    if (boundGateway == gateway) _state.update { it.copy(loading = false, sessionExpired = true) }
                    return@launch
                }
                is ApiResult.Failed -> {
                    if (boundGateway == gateway) {
                        val missing = cfgResult.status == 404
                        _state.update {
                            it.copy(
                                loading = false,
                                pluginMissing = missing,
                                error = if (missing) null else cfgResult.message,
                            )
                        }
                    }
                    return@launch
                }
                is ApiResult.Unavailable -> {
                    if (boundGateway == gateway) _state.update { it.copy(loading = false, error = cfgResult.message) }
                    return@launch
                }
                is ApiResult.InvalidCredentials, ApiResult.RateLimited -> {
                    if (boundGateway == gateway) _state.update { it.copy(loading = false, sessionExpired = true) }
                    return@launch
                }
                is ApiResult.Success -> {
                    if (boundGateway != gateway) return@launch
                    val cfg = cfgResult.value
                    _state.update { it.copy(config = cfg) }
                    dispatchStore?.syncCoordinator(gateway.gatewayUrl, cfg.coordinator)
                    if (!cfg.configured) {
                        _state.update { it.copy(loading = false, projects = emptyList(), tasks = emptyList()) }
                        dispatchStore?.clearGateway(gateway.gatewayUrl)
                        return@launch
                    }
                }
            }

            loadBoardAndMetadata(gateway)
        }
    }

    private suspend fun loadBoardAndMetadata(gateway: SavedGateway) {
        val includeDone = _state.value.includeDone
        fetchingBoard = true
        val boardResult = try {
            directusApi.fetchBoard(gateway.gatewayUrl, includeDone = includeDone)
        } finally {
            fetchingBoard = false
        }
        if (boundGateway != gateway) return
        when (boardResult) {
            is ApiResult.SessionExpired -> _state.update { it.copy(loading = false, sessionExpired = true) }
            is ApiResult.Failed -> _state.update { it.copy(loading = false, error = boardResult.message) }
            is ApiResult.Unavailable -> _state.update { it.copy(loading = false, error = boardResult.message) }
            is ApiResult.InvalidCredentials, ApiResult.RateLimited -> _state.update { it.copy(loading = false, sessionExpired = true) }
            is ApiResult.Success -> {
                val board = boardResult.value
                val pending = pendingExternalOpen?.takeIf { it.gateway == gateway.gatewayUrl }
                val pendingTask = pending?.let { p -> board.tasks.firstOrNull { it.id == p.taskId } }
                if (pending != null && pendingTask == null) {
                    if (!includeDone) {
                        _state.update { it.copy(includeDone = true) }
                        loadBoardAndMetadata(gateway)
                        return
                    } else {
                        pendingExternalOpen = null
                    }
                } else if (pendingTask != null) {
                    pendingExternalOpen = null
                }

                val today = currentLocalEpochDay()
                _state.update { cur ->
                    val mergedConfig = board.coordinator?.let { coord ->
                        cur.config?.copy(coordinator = coord) ?: DirectusConfigStatus(configured = board.configured, coordinator = coord)
                    } ?: cur.config
                    val keptSelected = when {
                        pendingTask != null -> pendingTask.id
                        else -> cur.selectedTaskId?.takeIf { sid -> board.tasks.any { it.id == sid } }
                    }
                    val selectedObj = keptSelected?.let { sid -> board.tasks.firstOrNull { it.id == sid } }
                    val safeChannelIdx = when {
                        pendingTask != null -> preferredChannelIndex(pendingTask, pending.channelId)
                        selectedObj != null && cur.activeChannelIdx < selectedObj.channels.size -> cur.activeChannelIdx
                        else -> 0
                    }
                    val keptFocused = cur.focusedProjectId?.takeIf { fid ->
                        fid == UNASSIGNED_PROJECT_BUCKET_ID || board.projects.any { it.id == fid && !it.archived }
                    }
                    val keptLocalRead = keptLocalReadAt(cur.localReadAtByTaskId, board.tasks).let { base ->
                        if (externalContextOnly && selectedObj != null) {
                            base + (selectedObj.id to readWatermarkFor(selectedObj))
                        } else {
                            base
                        }
                    }
                    cur.copy(
                        loading = false,
                        error = null,
                        config = mergedConfig,
                        projects = board.projects,
                        tasks = board.tasks,
                        todayEpochDay = today,
                        focusedProjectId = keptFocused,
                        selectedTaskId = if (cur.coordinatorOpen && pendingTask == null) null else keptSelected,
                        coordinatorOpen = if (pendingTask != null) false else cur.coordinatorOpen,
                        activeChannelIdx = safeChannelIdx,
                        localReadAtByTaskId = keptLocalRead,
                        localUnreadTaskIds = emptySet(),
                    )
                }
                _state.value.config?.coordinator?.let { coord ->
                    dispatchStore?.syncCoordinator(gateway.gatewayUrl, coord)
                }
                dispatchStore?.syncBoard(gateway.gatewayUrl, board.tasks, includeDone = includeDone)
                refreshCheckinEligibility()
                maybeRunAutoCheckin()
                if (_state.value.coordinatorOpen && !_state.value.channelTurnRunning) {
                    if (!externalContextOnly) loadCoordinatorLog(gateway)
                } else {
                    _state.value.selectedTask?.let { task ->
                        if (externalContextOnly) {
                            task.channels.getOrNull(_state.value.activeChannelIdx)?.let { ch ->
                                markChannelSeen(gateway, ch, task.replyStatus?.latestRoomSeq)
                            }
                        } else {
                            loadChannelLog(gateway, task, _state.value.activeChannelIdx)
                        }
                    }
                }
            }
        }

        // Metadata (bots & Hermes projects) enhances dialogs and chips, so failures never block the board.
        val bots = runCatching { botsApi.roster().bots }.getOrNull()
        val hermesProjects = runCatching { projectsApi.projects(null) }.getOrNull()
        if (boundGateway == gateway) {
            _state.update {
                it.copy(
                    bots = bots ?: it.bots,
                    hermesProjects = hermesProjects ?: it.hermesProjects,
                )
            }
            if (bots != null) {
                loadBotAvatars(gateway, bots)
            }
        }
        refreshTaskTimeBlocksSilently(gateway)
    }

    private suspend fun loadBotAvatars(gateway: SavedGateway, roster: List<Bot>) {
        val current = _state.value.botAvatars
        val toLoad = roster.filter { it.hasAvatar && it.name !in current }
        if (toLoad.isEmpty()) return
        val fetched = LinkedHashMap<String, ByteArray>()
        for (bot in toLoad) {
            if (boundGateway != gateway) return
            val bytes = runCatching { botsApi.avatar(bot.name) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                fetched[bot.name] = bytes
            }
        }
        if (fetched.isNotEmpty() && boundGateway == gateway) {
            _state.update { it.copy(botAvatars = it.botAvatars + fetched) }
        }
    }

    fun saveConfig(
        url: String,
        token: String,
        coordinator: DispatchCoordinatorUpdate? = null,
    ) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(savingConfig = true, configError = null) }
            val resolvedCoord = prepareCoordinatorProfileSwitch(gateway, coordinator)
            val cleanUrl = url.trim()
            val result = if (cleanUrl.isEmpty() && resolvedCoord != null && _state.value.config?.configured != true) {
                directusApi.saveCoordinatorConfig(gateway.gatewayUrl, resolvedCoord)
            } else {
                directusApi.saveConfig(
                    gateway.gatewayUrl,
                    DirectusConfigUpdate(
                        url = cleanUrl,
                        token = token.trim().ifEmpty { null },
                        coordinator = resolvedCoord,
                    ),
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    if (boundGateway != gateway) return@launch
                    _state.update { it.copy(savingConfig = false, config = result.value, configError = null) }
                    dispatchStore?.syncCoordinator(gateway.gatewayUrl, result.value.coordinator)
                    refreshCheckinEligibility()
                    refresh()
                }
                is ApiResult.Failed -> if (boundGateway == gateway) _state.update { it.copy(savingConfig = false, configError = result.message) }
                is ApiResult.Unavailable -> if (boundGateway == gateway) _state.update { it.copy(savingConfig = false, configError = result.message) }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited ->
                    if (boundGateway == gateway) _state.update { it.copy(savingConfig = false, sessionExpired = true) }
            }
        }
    }

    fun saveCoordinatorConfig(update: DispatchCoordinatorUpdate) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            val resolved = prepareCoordinatorProfileSwitch(gateway, update) ?: update
            when (val res = directusApi.saveCoordinatorConfig(gateway.gatewayUrl, resolved)) {
                is ApiResult.Success -> {
                    if (boundGateway != gateway) return@launch
                    _state.update { it.copy(config = res.value) }
                    dispatchStore?.syncCoordinator(gateway.gatewayUrl, res.value.coordinator)
                    refreshCheckinEligibility()
                }
                is ApiResult.Failed -> if (boundGateway == gateway) _state.update { it.copy(actionFeedback = res.message) }
                is ApiResult.Unavailable -> if (boundGateway == gateway) _state.update { it.copy(actionFeedback = res.message) }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited ->
                    if (boundGateway == gateway) _state.update { it.copy(sessionExpired = true) }
            }
        }
    }

    private suspend fun prepareCoordinatorProfileSwitch(
        gateway: SavedGateway,
        update: DispatchCoordinatorUpdate?,
    ): DispatchCoordinatorUpdate? {
        if (update == null) return null
        val cur = _state.value.coordinatorConfig
        val nextProfile = update.profile?.trim()?.removePrefix("@")?.ifEmpty { Bot.DEFAULT }
        if (nextProfile == null || nextProfile.equals(cur.profile, ignoreCase = true)) return update
        val oldSid = cur.sessionId.trim()
        if (oldSid.isNotEmpty() && update.sessionId == null) {
            runCatching { sessionsApi.setArchived(gateway.gatewayUrl, oldSid, archived = true, profile = cur.profile) }
            dispatchStore?.notifyLifecycleChange(gateway.gatewayUrl, archivedIds = setOf(oldSid))
            if (_state.value.coordinatorOpen) {
                activeRuntimeSessionId = null
                activeRuntimeChannelId = null
                _state.update { it.copy(channelMessages = emptyList(), channelChatState = ChatState()) }
            }
            return update.copy(sessionId = "")
        }
        return update
    }

    fun togglePriorityStrip(enabled: Boolean) {
        _state.update { it.copy(showPriorityStrip = enabled) }
    }

    fun setBoardViewMode(mode: TasksBoardViewMode) {
        _state.update { it.copy(boardViewMode = mode) }
    }

    fun toggleIncludeDone() {
        _state.update { it.copy(includeDone = !it.includeDone) }
        refresh()
    }

    fun toggleUnreadOnly() {
        _state.update { it.copy(unreadOnly = !it.unreadOnly) }
    }

    fun focusProject(projectId: String?) {
        _state.update { it.copy(focusedProjectId = projectId, collapsedWorkstreamKeys = emptySet()) }
    }

    fun setFocusGrouping(grouping: TaskFocusGrouping) {
        _state.update { it.copy(focusGrouping = grouping) }
    }

    fun toggleProjectExpanded(projectKey: String) {
        _state.update { cur ->
            val next = if (projectKey in cur.expandedProjectIds) cur.expandedProjectIds - projectKey else cur.expandedProjectIds + projectKey
            cur.copy(expandedProjectIds = next)
        }
    }

    fun toggleWorkstreamCollapsed(groupKey: String) {
        _state.update { cur ->
            val next = if (groupKey in cur.collapsedWorkstreamKeys) cur.collapsedWorkstreamKeys - groupKey else cur.collapsedWorkstreamKeys + groupKey
            cur.copy(collapsedWorkstreamKeys = next)
        }
    }

    fun selectTask(taskId: String?, preferredChannelId: String? = null) {
        externalContextOnly = false
        externalViewingTaskId = null
        externalViewingChannelId = null
        pendingExternalOpen = null
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        val targetTask = taskId?.let { id -> _state.value.tasks.firstOrNull { it.id == id } }
        val preferredChannelIdx = targetTask?.let { preferredChannelIndex(it, preferredChannelId) } ?: 0
        _state.update {
            it.copy(
                selectedTaskId = taskId,
                coordinatorOpen = false,
                activeChannelIdx = preferredChannelIdx,
                channelMessages = emptyList(),
                channelAttachments = emptyList(),
                channelChatState = ChatState(),
                modelPicker = ModelPickerState(),
                channelError = null,
                channelTurnRunning = false,
                channelTurnStatus = null,
                channelInputRequests = emptyList(),
            )
        }
        val gateway = boundGateway ?: return
        val task = _state.value.selectedTask ?: return
        viewModelScope.launch { loadChannelLog(gateway, task, preferredChannelIdx) }
        if (task.channels.isEmpty()) loadModels(withPrices = false)
    }

    /** Synchronizes task metadata and Action Proposal for an active fullscreen chat/room without streaming logs in [TasksViewModel]. */
    fun syncTaskContextOnly(gateway: SavedGateway, taskId: String, channelId: String) {
        if (taskId == DispatchSessionsStore.COORDINATOR_TASK_ID) return
        if (boundGateway != gateway || _state.value.config == null) {
            bind(gateway)
        }
        externalContextOnly = true
        externalViewingTaskId = taskId
        externalViewingChannelId = channelId
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        val loaded = _state.value.tasks.firstOrNull { it.id == taskId }
        if (loaded != null) {
            val idx = preferredChannelIndex(loaded, channelId)
            val watermark = readWatermarkFor(loaded)
            _state.update { cur ->
                cur.copy(
                    selectedTaskId = loaded.id,
                    coordinatorOpen = false,
                    activeChannelIdx = idx,
                    channelMessages = emptyList(),
                    channelAttachments = emptyList(),
                    channelChatState = ChatState(),
                    modelPicker = ModelPickerState(),
                    channelError = null,
                    channelTurnRunning = false,
                    channelTurnStatus = null,
                    channelInputRequests = emptyList(),
                    localReadAtByTaskId = cur.localReadAtByTaskId + (loaded.id to watermark),
                    localUnreadTaskIds = cur.localUnreadTaskIds - loaded.id,
                )
            }
            loaded.channels.getOrNull(idx)?.let { ch ->
                markChannelSeen(gateway, ch, loaded.replyStatus?.latestRoomSeq)
            }
            viewModelScope.launch { refreshTaskTimeBlocksSilently(gateway) }
            return
        }
        val knownDone = dispatchStore?.let { store ->
            store.byGateway.value[gateway.gatewayUrl]?.values?.any {
                it.taskId == taskId && it.taskStatus == DirectusTask.STATUS_DONE
            } == true || store.roomsByGateway.value[gateway.gatewayUrl]?.values?.any {
                it.taskId == taskId && it.taskStatus == DirectusTask.STATUS_DONE
            } == true
        } == true
        pendingExternalOpen = DispatchJumpTarget(gateway.gatewayUrl, taskId, channelId)
        val turnedOnDone = knownDone && !_state.value.includeDone
        _state.update { cur ->
            cur.copy(
                coordinatorOpen = false,
                includeDone = cur.includeDone || knownDone,
                localUnreadTaskIds = cur.localUnreadTaskIds - taskId,
            )
        }
        if (turnedOnDone || (!fetchingBoard && refreshJob?.isActive != true)) {
            refresh()
        }
    }

    fun clearExternalTaskContext(taskId: String) {
        if (externalViewingTaskId == taskId) {
            externalContextOnly = false
            externalViewingTaskId = null
            externalViewingChannelId = null
        }
    }

    /** Opens Dispatch directly on [taskId] (or Coordinator), enabling `includeDone` automatically if needed. */
    fun openExternalTask(taskId: String, channelId: String? = null) {
        val gateway = boundGateway ?: return
        externalContextOnly = false
        externalViewingTaskId = null
        externalViewingChannelId = null
        if (taskId == DispatchSessionsStore.COORDINATOR_TASK_ID) {
            pendingExternalOpen = null
            openCoordinator()
            if (_state.value.config == null && refreshJob?.isActive != true) {
                refresh()
            }
            return
        }
        val loaded = _state.value.tasks.firstOrNull { it.id == taskId }
        if (loaded != null) {
            selectTask(loaded.id, preferredChannelId = channelId)
            return
        }
        val knownDone = dispatchStore?.let { store ->
            store.byGateway.value[gateway.gatewayUrl]?.values?.any {
                it.taskId == taskId && it.taskStatus == DirectusTask.STATUS_DONE
            } == true || store.roomsByGateway.value[gateway.gatewayUrl]?.values?.any {
                it.taskId == taskId && it.taskStatus == DirectusTask.STATUS_DONE
            } == true
        } == true
        pendingExternalOpen = DispatchJumpTarget(gateway.gatewayUrl, taskId, channelId)
        val turnedOnDone = knownDone && !_state.value.includeDone
        _state.update { cur ->
            cur.copy(
                coordinatorOpen = false,
                includeDone = cur.includeDone || knownDone,
            )
        }
        if (turnedOnDone || (!fetchingBoard && refreshJob?.isActive != true) || (!fetchingBoard && !_state.value.loading)) {
            refresh()
        }
    }

    private fun handleExternalJump(jump: DispatchJumpTarget) {
        dispatchStore?.consumeJump(jump)
        openExternalTask(jump.taskId, jump.channelId)
    }

    private fun preferredChannelIndex(task: DirectusTask, explicitChannelId: String?): Int {
        if (explicitChannelId != null) {
            task.channels.indexOfFirst { it.id == explicitChannelId }.takeIf { it >= 0 }?.let { return it }
        }
        val replyChannelId = task.replyStatus?.lastReplyChannelId
        if (replyChannelId != null) {
            task.channels.indexOfFirst { it.id == replyChannelId }.takeIf { it >= 0 }?.let { return it }
        }
        return 0
    }

    fun openCoordinator(autoCheckinPrompt: Boolean = false) {
        externalContextOnly = false
        externalViewingTaskId = null
        externalViewingChannelId = null
        val alreadyOpen = _state.value.coordinatorOpen
        if (!alreadyOpen) {
            turnPollJob?.cancel()
            modelsLoadJob?.cancel()
            activeRuntimeSessionId = null
            activeRuntimeChannelId = null
            _state.update {
                it.copy(
                    selectedTaskId = null,
                    coordinatorOpen = true,
                    activeChannelIdx = 0,
                    channelMessages = emptyList(),
                    channelAttachments = emptyList(),
                    channelChatState = ChatState(),
                    modelPicker = ModelPickerState(),
                    channelError = null,
                    channelTurnRunning = false,
                    channelTurnStatus = null,
                    channelInputRequests = emptyList(),
                )
            }
        }
        val gateway = boundGateway ?: return
        val channel = _state.value.coordinatorChannel
        if (channel != null && !alreadyOpen) {
            viewModelScope.launch { loadCoordinatorLog(gateway) }
        } else if (channel == null) {
            loadModels(withPrices = false)
        }
        val dueSlot = _state.value.checkinDueSlot
        if (autoCheckinPrompt && dueSlot != null) {
            triggerQuickCoordinatorAction(CoordinatorQuickAction.CHECK_IN, explicitSlot = dueSlot)
        }
    }

    fun closeCoordinator() {
        if (!_state.value.coordinatorOpen) return
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        dismissedCheckinSlot = _state.value.checkinDueSlot ?: dismissedCheckinSlot
        _state.update {
            it.copy(
                coordinatorOpen = false,
                channelMessages = emptyList(),
                channelAttachments = emptyList(),
                channelChatState = ChatState(),
                modelPicker = ModelPickerState(),
                channelError = null,
                channelTurnRunning = false,
                channelTurnStatus = null,
                channelInputRequests = emptyList(),
            )
        }
    }

    fun toggleCoordinator() {
        if (_state.value.coordinatorOpen) {
            closeCoordinator()
        } else {
            openCoordinator(autoCheckinPrompt = _state.value.checkinDueSlot != null)
        }
    }

    fun sendToCoordinator(text: String, checkinSlotToAck: String? = null) {
        val gateway = boundGateway ?: return
        val clean = text.trim()
        val attachments = _state.value.channelAttachments
        if (clean.isEmpty() && attachments.isEmpty()) return
        val visibleText = clean.ifEmpty {
            if (attachments.any { it.kind == AttachmentKind.Image }) IMAGE_ONLY_FALLBACK_PROMPT else ""
        }
        val optimisticKey = "user-${Uuid.random()}"
        val optimisticLine = ChannelChatLine(
            key = optimisticKey,
            speaker = null,
            text = visibleText,
            fromUser = true,
            attachments = attachments.map { it.toShown() },
        )
        _state.update { cur ->
            cur.copy(
                selectedTaskId = null,
                coordinatorOpen = true,
                sendingToChannel = true,
                channelError = null,
                channelAttachments = emptyList(),
                channelMessages = cur.channelMessages + optimisticLine,
                channelTurnRunning = true,
                channelTurnStatus = null,
                checkinDueSlot = if (checkinSlotToAck != null) null else cur.checkinDueSlot,
            )
        }
        viewModelScope.launch {
            try {
                val nowSec = nowMs() / 1000.0
                val existing = _state.value.coordinatorChannel
                val channel = existing ?: createCoordinatorSession(gateway, checkinSlotToAck, nowSec)
                if (channel == null) {
                    _state.update { cur ->
                        cur.copy(
                            channelAttachments = attachments + cur.channelAttachments,
                            channelMessages = cur.channelMessages.filterNot { it.key == optimisticKey },
                            channelTurnRunning = false,
                            channelTurnStatus = null,
                        )
                    }
                    return@launch
                }
                if (existing != null) {
                    ackCoordinatorInteraction(gateway, checkinSlotToAck, nowSec)
                }
                lastStreamEventAtMs = nowMs()
                dispatchMessage(channel, visibleText, attachments)
                startTurnFallbackPoll(gateway, channel = channel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { cur ->
                    cur.copy(
                        channelAttachments = attachments + cur.channelAttachments,
                        channelMessages = cur.channelMessages.filterNot { it.key == optimisticKey },
                        channelTurnRunning = false,
                        channelTurnStatus = null,
                        channelError = e.message ?: "Send failed",
                        actionFeedback = e.message ?: "Send failed",
                    )
                }
            } finally {
                _state.update { it.copy(sendingToChannel = false) }
            }
        }
    }

    fun triggerQuickCoordinatorAction(
        action: CoordinatorQuickAction,
        explicitSlot: String? = null,
    ) {
        val nowMillis = nowMs()
        val moment = localMoment(nowMillis)
        val dateIso = formatIsoDate(moment.year, moment.month, moment.day)
        val minuteOfDay = moment.hour * 60 + moment.minute
        val coord = _state.value.coordinatorConfig
        val activeMatch = coord.activeOrUpcomingCheckin(dateIso, minuteOfDay)
        val slotToAck = when (action) {
            CoordinatorQuickAction.CHECK_IN -> explicitSlot
                ?: _state.value.checkinDueSlot
                ?: activeMatch?.slotKey
                ?: coord.activeOrUpcomingCheckinSlotId(dateIso, minuteOfDay)
            else -> _state.value.checkinDueSlot
        }
        val scheduleInstruction = if (action == CoordinatorQuickAction.CHECK_IN) {
            val targetTime = slotToAck?.substringAfter('@', "")?.takeIf { it.isNotBlank() }
            val matchedSchedule = coord.effectiveCheckinSchedules()
                .firstOrNull { it.time == targetTime }
                ?: activeMatch?.schedule
            matchedSchedule?.instruction?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val prompt = when (action) {
            CoordinatorQuickAction.CHECK_IN -> buildString {
                append("Run a Dispatch check-in with me now. ")
                append("Review the live board tasks and projects against my standing guidelines, ")
                append("highlight any plans waiting for my approval or blocked/overdue items, ")
                append("and suggest the most effective next actions.")
                if (scheduleInstruction != null) {
                    append("\n\nFocus for this check-in window: ")
                    append(scheduleInstruction)
                }
            }
            CoordinatorQuickAction.BLOCKERS -> buildString {
                append("Review all blocked and overdue tasks across projects on the Dispatch board, ")
                append("identify the root bottlenecks, and propose concrete unblocking steps or plan options.")
            }
            CoordinatorQuickAction.PRIORITIES -> buildString {
                append("Based on the active tasks, due dates, and my standing guidelines, ")
                append("suggest the top 3 priorities I should focus on or delegate right now.")
            }
        }
        sendToCoordinator(prompt, checkinSlotToAck = slotToAck)
    }

    fun resetCoordinatorSession() {
        val gateway = boundGateway ?: return
        if (_state.value.resettingCoordinator) return
        turnPollJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        _state.update {
            it.copy(
                resettingCoordinator = true,
                channelMessages = emptyList(),
                channelAttachments = emptyList(),
                channelChatState = ChatState(),
                channelError = null,
                channelTurnRunning = false,
                channelTurnStatus = null,
                channelInputRequests = emptyList(),
            )
        }
        viewModelScope.launch {
            try {
                val cur = _state.value.coordinatorConfig
                val oldSid = cur.sessionId.trim()
                if (oldSid.isNotEmpty()) {
                    runCatching { sessionsApi.setArchived(gateway.gatewayUrl, oldSid, archived = true, profile = cur.profile) }
                    dispatchStore?.notifyLifecycleChange(gateway.gatewayUrl, archivedIds = setOf(oldSid))
                }
                val newChannel = createCoordinatorSession(gateway, checkinSlotToAck = null, nowSec = nowMs() / 1000.0)
                if (newChannel == null) {
                    val saved = directusApi.saveCoordinatorConfig(
                        gateway.gatewayUrl,
                        DispatchCoordinatorUpdate(sessionId = ""),
                    )
                    if (saved is ApiResult.Success && boundGateway == gateway) {
                        _state.update { it.copy(config = saved.value) }
                        dispatchStore?.syncCoordinator(gateway.gatewayUrl, saved.value.coordinator)
                    }
                }
                if (boundGateway == gateway) {
                    _state.update { it.copy(actionFeedback = "Started a fresh coordinator session.") }
                }
            } finally {
                if (boundGateway == gateway) {
                    _state.update { it.copy(resettingCoordinator = false) }
                }
            }
        }
    }

    fun openCoordinatorFullscreen() {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            val channel = _state.value.coordinatorChannel
                ?: createCoordinatorSession(gateway, checkinSlotToAck = null, nowSec = nowMs() / 1000.0)
                ?: return@launch
            _events.send(
                TasksNavEvent.OpenSession(
                    sessionId = channel.id,
                    title = DispatchSessionsStore.COORDINATOR_SESSION_TITLE,
                    profile = channel.profile,
                ),
            )
        }
    }

    private fun refreshCheckinEligibility() {
        val cfg = _state.value.config ?: return
        if (!cfg.configured) {
            _state.update { it.copy(checkinDueSlot = null) }
            return
        }
        val nowMillis = nowMs()
        val moment = localMoment(nowMillis)
        val dateIso = formatIsoDate(moment.year, moment.month, moment.day)
        val minuteOfDay = moment.hour * 60 + moment.minute
        val due = cfg.coordinator.resolveDueCheckinSlot(
            localDateIso = dateIso,
            localMinuteOfDay = minuteOfDay,
            nowEpochSeconds = nowMillis / 1000L,
        )
        _state.update { it.copy(checkinDueSlot = due) }
    }

    private fun maybeRunAutoCheckin() {
        if (!pageVisible) return
        if (connection.state.value !is ConnectionState.Connected) return
        val cfg = _state.value.config ?: return
        if (!cfg.configured || _state.value.loading) return
        val dueSlot = _state.value.checkinDueSlot ?: return
        if (lastAutoTriggeredSlot == dueSlot || dismissedCheckinSlot == dueSlot) return
        if (_state.value.channelTurnRunning || _state.value.sendingToChannel) return
        lastAutoTriggeredSlot = dueSlot
        openCoordinator(autoCheckinPrompt = true)
    }

    private suspend fun createCoordinatorSession(
        gateway: SavedGateway,
        checkinSlotToAck: String?,
        nowSec: Double,
    ): TaskChannel? {
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: return null
        val coord = _state.value.coordinatorConfig
        val profile = coord.profile.trim().removePrefix("@").ifEmpty { Bot.DEFAULT }
        val picks = _state.value.channelChatState
        val result = runCatching {
            client.request(
                "session.create",
                buildJsonObject {
                    put("profile", profile)
                    put("title", DispatchSessionsStore.COORDINATOR_SESSION_TITLE)
                    put("source", "desktop")
                    put("cols", 80)
                    if (picks.model != null && picks.provider != null) {
                        put("model", picks.model)
                        put("provider", picks.provider)
                    }
                    picks.reasoningEffort?.let { put("reasoning_effort", it) }
                    picks.fast?.let { put("fast", it) }
                },
            ) as? JsonObject
        }.getOrNull() ?: return null
        val runtimeId = (result["session_id"] as? JsonPrimitive)?.contentOrNull
        val storedId = (result["stored_session_id"] as? JsonPrimitive)?.contentOrNull ?: runtimeId ?: return null
        val info = result["info"] as? JsonObject
        activeRuntimeSessionId = runtimeId ?: storedId
        activeRuntimeChannelId = storedId

        val updatedCoord = coord.copy(
            profile = profile,
            sessionId = storedId,
            lastCheckinSlot = checkinSlotToAck ?: coord.lastCheckinSlot,
            lastInteractionAt = nowSec,
        )
        _state.update { cur ->
            val nextCfg = (cur.config ?: DirectusConfigStatus(configured = true)).copy(coordinator = updatedCoord)
            cur.copy(
                config = nextCfg,
                checkinDueSlot = if (checkinSlotToAck != null) null else cur.checkinDueSlot,
                channelChatState = if (info != null) cur.channelChatState.withChannelInfo(info) else cur.channelChatState,
            )
        }
        dispatchStore?.syncCoordinator(gateway.gatewayUrl, updatedCoord)
        dispatchStore?.notifyLifecycleChange(gateway.gatewayUrl, unarchivedIds = setOf(storedId))

        val saveRes = directusApi.saveCoordinatorConfig(
            gateway.gatewayUrl,
            DispatchCoordinatorUpdate(
                profile = profile,
                sessionId = storedId,
                lastCheckinSlot = checkinSlotToAck,
                lastInteractionAt = nowSec,
            ),
        )
        if (saveRes is ApiResult.Success && boundGateway == gateway) {
            _state.update { it.copy(config = saveRes.value) }
            dispatchStore?.syncCoordinator(gateway.gatewayUrl, saveRes.value.coordinator)
        }
        return TaskChannel(
            type = TaskChannel.TYPE_SESSION,
            id = storedId,
            label = DispatchSessionsStore.COORDINATOR_SESSION_TITLE,
            profile = profile,
            managed = true,
        )
    }

    private suspend fun ackCoordinatorInteraction(
        gateway: SavedGateway,
        checkinSlotToAck: String?,
        nowSec: Double,
    ) {
        val coord = _state.value.coordinatorConfig
        val updatedCoord = coord.copy(
            lastCheckinSlot = checkinSlotToAck ?: coord.lastCheckinSlot,
            lastInteractionAt = nowSec,
        )
        _state.update { cur ->
            val nextCfg = cur.config?.copy(coordinator = updatedCoord)
            cur.copy(
                config = nextCfg,
                checkinDueSlot = if (checkinSlotToAck != null) null else cur.checkinDueSlot,
            )
        }
        dispatchStore?.syncCoordinator(gateway.gatewayUrl, updatedCoord)
        val res = directusApi.saveCoordinatorConfig(
            gateway.gatewayUrl,
            DispatchCoordinatorUpdate(
                lastCheckinSlot = checkinSlotToAck,
                lastInteractionAt = nowSec,
            ),
        )
        if (res is ApiResult.Success && boundGateway == gateway) {
            _state.update { it.copy(config = res.value) }
            dispatchStore?.syncCoordinator(gateway.gatewayUrl, res.value.coordinator)
        }
    }

    fun selectChannel(index: Int) {
        val task = _state.value.selectedTask ?: return
        if (index !in task.channels.indices) return
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        _state.update {
            it.copy(
                activeChannelIdx = index,
                channelMessages = emptyList(),
                channelChatState = ChatState(),
                modelPicker = ModelPickerState(),
                channelError = null,
                channelTurnRunning = false,
                channelTurnStatus = null,
                channelInputRequests = emptyList(),
            )
        }
        val gateway = boundGateway ?: return
        val channel = task.channels[index]
        if (externalContextOnly) {
            if (channel.id != externalViewingChannelId) {
                viewModelScope.launch { emitOpenChannelNav(task, channel) }
            }
            return
        }
        viewModelScope.launch { loadChannelLog(gateway, task, index) }
    }

    fun addAttachments(picked: List<OutgoingAttachment>) {
        if (picked.isEmpty()) return
        val isRoom = _state.value.activeChannel?.isRoom == true
        val maxBytes: Long = if (isRoom) OutgoingAttachment.MAX_INLINE_BYTES.toLong() else OutgoingAttachment.MAX_BYTES.toLong()
        val maxLabel = if (isRoom) "${OutgoingAttachment.MAX_INLINE_BYTES / 1_000_000} MB" else OutgoingAttachment.MAX_SIZE
        val room = (OutgoingAttachment.MAX_COUNT - _state.value.channelAttachments.size).coerceAtLeast(0)
        val fitting = picked.filter { it.size <= maxBytes }
        val rejectedSize = picked.size - fitting.size
        val accepted = fitting.take(room)
        val rejectedCount = fitting.size - accepted.size
        _state.update { cur ->
            val notice = when {
                rejectedSize > 0 -> "Skipped file larger than $maxLabel."
                rejectedCount > 0 -> "Up to ${OutgoingAttachment.MAX_COUNT} attachments per message."
                else -> cur.actionFeedback
            }
            cur.copy(
                channelAttachments = cur.channelAttachments + accepted,
                actionFeedback = notice,
            )
        }
    }

    fun removeAttachment(id: String) {
        _state.update { cur -> cur.copy(channelAttachments = cur.channelAttachments.filterNot { it.id == id }) }
    }

    fun reportAttachmentError(message: String) {
        if (message.isNotBlank()) _state.update { it.copy(actionFeedback = message) }
    }

    fun loadModels(withPrices: Boolean = false) {
        val api = modelsApi ?: return
        if (_state.value.activeChannel?.isRoom == true) return
        modelsLoadJob?.cancel()
        _state.update { it.copy(modelPicker = it.modelPicker.copy(loading = true, error = null)) }
        modelsLoadJob = viewModelScope.launch {
            try {
                val activeCh = _state.value.activeChannel
                val profile = when {
                    _state.value.coordinatorOpen -> _state.value.coordinatorConfig.profile.ifBlank { null }
                    activeCh != null -> activeCh.profile
                    else -> _state.value.selectedTask?.assignedBots?.firstOrNull()
                }
                val runtimeId = activeRuntimeSessionId
                val catalog = api.options(runtimeId, profile)
                _state.update { it.copy(modelPicker = it.modelPicker.copy(catalog = catalog, loading = false)) }
                if (!withPrices) return@launch
                val prices = try {
                    api.missingPrices(catalog, runtimeId, profile)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                prices?.let { found ->
                    _state.update { cur ->
                        cur.copy(modelPicker = cur.modelPicker.copy(catalog = cur.modelPicker.catalog?.withPrices(found)))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(modelPicker = it.modelPicker.copy(loading = false, error = e.message.orEmpty())) }
            }
        }
    }

    fun selectModel(model: ModelOption, confirmed: Boolean = false) {
        val channel = _state.value.activeChannel
        if (channel?.isRoom == true) return
        _state.update { it.copy(modelPicker = it.modelPicker.copy(confirm = null)) }
        val previous = _state.value.channelChatState
        _state.update { it.copy(channelChatState = previous.copy(model = model.id, provider = model.provider)) }
        if (channel == null) return
        viewModelScope.launch {
            try {
                val result = setSessionConfig(
                    channel = channel,
                    key = "model",
                    value = JsonPrimitive("${model.id} --provider ${model.provider} --session"),
                    confirmExpensive = confirmed,
                )
                if (result.bool("confirm_required") == true) {
                    val message = result.str("confirm_message") ?: "This model costs more than usual. Switch anyway?"
                    _state.update {
                        it.copy(
                            channelChatState = previous,
                            modelPicker = it.modelPicker.copy(confirm = PendingSwitch(model, message)),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        channelChatState = previous,
                        actionFeedback = e.message ?: "Couldn't switch the model.",
                    )
                }
            }
        }
    }

    fun dismissModelConfirm() {
        _state.update { it.copy(modelPicker = it.modelPicker.copy(confirm = null)) }
    }

    fun setReasoningEffort(wire: String) {
        val channel = _state.value.activeChannel
        if (channel?.isRoom == true) return
        val previous = _state.value.channelChatState
        _state.update {
            it.copy(
                channelChatState = previous.copy(
                    reasoningEffort = wire,
                    reasoningRevision = previous.reasoningRevision + 1,
                ),
            )
        }
        if (channel == null) return
        viewModelScope.launch {
            try {
                setSessionConfig(channel, "reasoning", JsonPrimitive(wire))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        channelChatState = previous,
                        actionFeedback = e.message ?: "Couldn't change reasoning level.",
                    )
                }
            }
        }
    }

    fun setFast(on: Boolean) {
        val channel = _state.value.activeChannel
        if (channel?.isRoom == true) return
        val previous = _state.value.channelChatState
        _state.update { it.copy(channelChatState = previous.copy(fast = on)) }
        if (channel == null) return
        viewModelScope.launch {
            try {
                setSessionConfig(channel, "fast", JsonPrimitive(if (on) "fast" else "normal"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        channelChatState = previous,
                        actionFeedback = e.message ?: "Couldn't change fast mode.",
                    )
                }
            }
        }
    }

    fun refreshActiveChannel() {
        val gateway = boundGateway ?: return
        if (_state.value.coordinatorOpen) {
            viewModelScope.launch { loadCoordinatorLog(gateway, attachSession = true) }
            return
        }
        val task = _state.value.selectedTask ?: return
        val idx = _state.value.activeChannelIdx
        viewModelScope.launch { loadChannelLog(gateway, task, idx) }
    }

    private suspend fun loadCoordinatorLog(
        gateway: SavedGateway,
        attachSession: Boolean = true,
    ) {
        val channel = _state.value.coordinatorChannel ?: run {
            _state.update { it.copy(channelLoading = false, channelMessages = emptyList(), channelError = null) }
            return
        }
        if (_state.value.channelMessages.isEmpty()) {
            _state.update { it.copy(channelLoading = true, channelError = null) }
        }
        val linesResult = fetchSessionLines(gateway, channel)
        if (boundGateway != gateway || !_state.value.coordinatorOpen || _state.value.coordinatorChannel?.id != channel.id) return
        _state.update { cur ->
            val fetched = linesResult.first
            val hasStreaming = cur.channelMessages.any { it.streaming }
            val merged = when {
                linesResult.second != null && cur.channelMessages.isNotEmpty() -> cur.channelMessages
                cur.channelTurnRunning && hasStreaming -> cur.channelMessages
                cur.channelTurnRunning && fetched.size < cur.channelMessages.size -> cur.channelMessages
                else -> fetched
            }
            cur.copy(
                channelLoading = false,
                channelMessages = merged,
                channelError = if (merged.isEmpty()) linesResult.second else null,
            )
        }
        if (attachSession) {
            attachSessionChannel(channel)
        }
    }

    private suspend fun fetchSessionLines(
        gateway: SavedGateway,
        channel: TaskChannel,
    ): Pair<List<ChannelChatLine>, String?> =
        when (val res = sessionsApi.messages(gateway.gatewayUrl, channel.id, limit = 40, profile = channel.profile)) {
            is ApiResult.Success -> parseSessionChatLines(res.value.messages, channel.profile) to null
            is ApiResult.Failed -> emptyList<ChannelChatLine>() to res.message
            is ApiResult.Unavailable -> emptyList<ChannelChatLine>() to res.message
            is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited -> {
                if (boundGateway == gateway) _state.update { it.copy(sessionExpired = true) }
                emptyList<ChannelChatLine>() to null
            }
        }

    private suspend fun loadChannelLog(
        gateway: SavedGateway,
        task: DirectusTask,
        channelIdx: Int,
        attachSession: Boolean = true,
    ) {
        val channel = task.channels.getOrNull(channelIdx) ?: run {
            _state.update { it.copy(channelLoading = false, channelMessages = emptyList(), channelError = null) }
            return
        }
        if (_state.value.channelMessages.isEmpty()) {
            _state.update { it.copy(channelLoading = true, channelError = null) }
        }
        var latestRoomSeqLoaded: Int? = null
        val linesResult: Pair<List<ChannelChatLine>, String?> = if (channel.isRoom) {
            try {
                val roomState = roomsApi.state(channel.id)
                val page = roomsApi.log(channel.id, sinceSeq = 0, limit = 50)
                latestRoomSeqLoaded = maxOf(
                    roomState.room.latestSeq ?: 0,
                    page.events.maxOfOrNull { it.seq } ?: 0,
                ).takeIf { it > 0 }
                val parsed = roomLines(page.events, roomState.room.members)
                    .filterIsInstance<RoomLine.Message>()
                    .takeLast(30)
                    .map { msg ->
                        val shownAttachments = msg.attachments.map { file ->
                            ShownAttachment(
                                key = file.attachmentId,
                                name = file.name,
                                kind = when (file.kind) {
                                    "image" -> AttachmentKind.Image
                                    "pdf" -> AttachmentKind.Pdf
                                    else -> AttachmentKind.File
                                },
                            )
                        }
                        ChannelChatLine(
                            key = "room-${msg.seq}-${msg.eventId}",
                            speaker = if (msg.fromUser) null else msg.speaker ?: msg.profile,
                            text = msg.text,
                            fromUser = msg.fromUser,
                            profile = msg.profile,
                            attachments = shownAttachments,
                        )
                    }
                parsed to null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList<ChannelChatLine>() to (e.message ?: "Could not read room log")
            }
        } else {
            fetchSessionLines(gateway, channel)
        }
        if (boundGateway != gateway || _state.value.selectedTaskId != task.id || _state.value.activeChannelIdx != channelIdx) return
        _state.update { cur ->
            val fetched = linesResult.first
            val hasStreaming = cur.channelMessages.any { it.streaming }
            val merged = when {
                linesResult.second != null && cur.channelMessages.isNotEmpty() -> cur.channelMessages
                cur.channelTurnRunning && hasStreaming -> cur.channelMessages
                cur.channelTurnRunning && fetched.size < cur.channelMessages.size -> cur.channelMessages
                else -> fetched
            }
            val watermark = readWatermarkFor(task)
            cur.copy(
                channelLoading = false,
                channelMessages = merged,
                channelError = if (merged.isEmpty()) linesResult.second else null,
                localReadAtByTaskId = cur.localReadAtByTaskId + (task.id to watermark),
                localUnreadTaskIds = cur.localUnreadTaskIds - task.id,
            )
        }
        markChannelSeen(gateway, channel, latestRoomSeqLoaded ?: task.replyStatus?.latestRoomSeq)
        if (attachSession && !channel.isRoom) {
            attachSessionChannel(channel)
        }
    }

    fun createProject(name: String, hermesProjectId: String?, workspaceCwd: String?, defaultBots: List<String>) {
        val gateway = boundGateway ?: return
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            when (val res = directusApi.createProject(
                gateway.gatewayUrl,
                CreateProjectRequest(
                    name = cleanName,
                    hermesProjectId = hermesProjectId?.trim()?.ifEmpty { null },
                    workspaceCwd = workspaceCwd?.trim()?.ifEmpty { null },
                    defaultBots = defaultBots,
                    sort = _state.value.projects.size + 1,
                ),
            )) {
                is ApiResult.Success -> {
                    if (boundGateway != gateway) return@launch
                    _state.update { it.copy(projects = it.projects + res.value) }
                }
                is ApiResult.Failed -> _state.update { it.copy(actionFeedback = res.message) }
                is ApiResult.Unavailable -> _state.update { it.copy(actionFeedback = res.message) }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited ->
                    _state.update { it.copy(sessionExpired = true) }
            }
        }
    }

    fun createTask(title: String, description: String?, projectId: String?, priority: String, assignedBots: List<String>) {
        val gateway = boundGateway ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            when (val res = directusApi.createTask(
                gateway.gatewayUrl,
                CreateTaskRequest(
                    title = cleanTitle,
                    description = description?.trim()?.ifEmpty { null },
                    priority = priority,
                    projectId = projectId,
                    assignedBots = assignedBots,
                ),
            )) {
                is ApiResult.Success -> {
                    if (boundGateway != gateway) return@launch
                    val created = res.value
                    activeRuntimeSessionId = null
                    activeRuntimeChannelId = null
                    _state.update { cur ->
                        cur.copy(
                            tasks = listOf(created) + cur.tasks,
                            selectedTaskId = created.id,
                            activeChannelIdx = 0,
                            channelMessages = emptyList(),
                            channelAttachments = emptyList(),
                            channelChatState = ChatState(),
                            modelPicker = ModelPickerState(catalog = cur.modelPicker.catalog),
                        )
                    }
                    dispatchStore?.syncTask(gateway.gatewayUrl, created)
                    loadModels(withPrices = false)
                }
                is ApiResult.Failed -> _state.update { it.copy(actionFeedback = res.message) }
                is ApiResult.Unavailable -> _state.update { it.copy(actionFeedback = res.message) }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited ->
                    _state.update { it.copy(sessionExpired = true) }
            }
        }
    }

    fun assignProject(taskId: String, projectId: String?) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            val res = directusApi.upsertContext(gateway.gatewayUrl, taskId, UpsertContextRequest(projectId = projectId))
            if (res is ApiResult.Success && boundGateway == gateway) {
                replaceTask(res.value)
                dispatchStore?.syncTask(gateway.gatewayUrl, res.value)
            } else if (res is ApiResult.Failed) {
                _state.update { it.copy(actionFeedback = res.message) }
            }
        }
    }

    fun updateTaskStatus(taskId: String, status: String) {
        val gateway = boundGateway ?: return
        val previousTask = _state.value.tasks.firstOrNull { it.id == taskId }
        viewModelScope.launch {
            val res = directusApi.updateTask(gateway.gatewayUrl, taskId, UpdateTaskRequest(status = status))
            if (res is ApiResult.Success && boundGateway == gateway) {
                val updated = res.value
                if (updated.status == DirectusTask.STATUS_DONE && !_state.value.includeDone) {
                    _state.update { cur ->
                        cur.copy(
                            tasks = cur.tasks.filterNot { it.id == taskId },
                            selectedTaskId = if (cur.selectedTaskId == taskId) null else cur.selectedTaskId,
                        )
                    }
                } else {
                    replaceTask(updated)
                }
                syncSessionLifecycleOnStatusChange(gateway, previousTask, updated)
            } else if (res is ApiResult.Failed) {
                _state.update { it.copy(actionFeedback = res.message) }
            }
        }
    }

    fun updateTaskMatrix(taskId: String, impact: MatrixLevel, urgency: MatrixLevel) {
        val gateway = boundGateway ?: return
        val previous = _state.value.tasks.firstOrNull { it.id == taskId } ?: return
        val syncedPriority = matrixSyncedPriority(impact, urgency)
        val optimistic = previous.copy(
            priority = syncedPriority,
            context = (previous.context ?: TaskContext(taskId = taskId)).copy(
                impact = impact.wire,
                urgency = urgency.wire,
            ),
        )
        replaceTask(optimistic)
        viewModelScope.launch {
            val ctxRes = directusApi.upsertContext(
                gateway.gatewayUrl,
                taskId,
                UpsertContextRequest(impact = impact.wire, urgency = urgency.wire),
            )
            if (boundGateway != gateway) return@launch
            when (ctxRes) {
                is ApiResult.Success -> {
                    val saved = ctxRes.value
                    val priorityRes = if (saved.priority != syncedPriority) {
                        directusApi.updateTask(gateway.gatewayUrl, taskId, UpdateTaskRequest(priority = syncedPriority))
                    } else {
                        null
                    }
                    val finalTask = (priorityRes as? ApiResult.Success)?.value ?: saved.copy(priority = syncedPriority)
                    replaceTask(
                        if (finalTask.replyStatus == null && previous.replyStatus != null) {
                            finalTask.copy(replyStatus = previous.replyStatus)
                        } else {
                            finalTask
                        },
                    )
                }
                is ApiResult.Failed -> {
                    replaceTask(previous)
                    _state.update { it.copy(actionFeedback = ctxRes.message) }
                }
                is ApiResult.Unavailable -> {
                    replaceTask(previous)
                    _state.update { it.copy(actionFeedback = ctxRes.message) }
                }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited -> {
                    replaceTask(previous)
                    _state.update { it.copy(sessionExpired = true) }
                }
            }
        }
    }

    private fun markChannelSeen(gateway: SavedGateway, channel: TaskChannel, latestRoomSeq: Int?) {
        val nowSec = nowMs() / 1000.0
        viewModelScope.launch {
            if (channel.isRoom) {
                if (latestRoomSeq != null && latestRoomSeq > 0) {
                    runCatching { roomSeenStore?.markSeen(gateway.gatewayUrl, channel.id, latestRoomSeq) }
                }
            } else {
                runCatching { seenStore?.markLooked(gateway.gatewayUrl, channel.id, nowSec, channel.profile) }
                runCatching { sessionsApi.setUnread(gateway.gatewayUrl, channel.id, unread = false, profile = channel.profile) }
            }
        }
    }

    fun openOrCreateSessionChannel(task: DirectusTask, profile: String?) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val created = createSessionChannel(gateway, task, profile) ?: return@launch
                replaceTask(created.first)
                val newIdx = (created.first.channels.indexOfFirst { it.id == created.second.id }).coerceAtLeast(0)
                _state.update { it.copy(activeChannelIdx = newIdx) }
                if (externalContextOnly) {
                    if (created.second.id != externalViewingChannelId) {
                        emitOpenChannelNav(created.first, created.second)
                    }
                } else {
                    loadChannelLog(gateway, created.first, newIdx)
                }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    fun openOrCreateRoomChannel(task: DirectusTask, members: List<Bot>) {
        val gateway = boundGateway ?: return
        if (members.size < 2) return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val excludedIds = task.channels.filter { it.isRoom }.mapTo(HashSet()) { it.id }
                val room = createTaskRoomWithFallback(task, members, excludedIds)
                val label = "Room (${members.joinToString(", ") { "@${it.name}" }})"
                val channel = TaskChannel(type = TaskChannel.TYPE_ROOM, id = room.roomId, label = label)
                val mergedChannels = task.channels.filterNot { it.id == channel.id } + channel
                val bots = (task.assignedBots + members.map { it.name }).distinct()
                val res = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(channels = mergedChannels, assignedBots = bots),
                )
                if (res is ApiResult.Success && boundGateway == gateway) {
                    finalizeChannelMutation(
                        gateway = gateway,
                        previousTask = task,
                        updatedTask = res.value,
                        selectChannelId = channel.id,
                        removedChannels = emptyList(),
                    )
                } else if (res is ApiResult.Failed) {
                    _state.update { it.copy(actionFeedback = res.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Could not create room") }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    /** Drops [oldChannel] from [task], creates a new single-agent session for [profile], and updates `assigned_bots`. */
    fun replaceWithSessionChannel(task: DirectusTask, oldChannel: TaskChannel, profile: String?) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val created = createSessionRecord(gateway, task, profile) ?: return@launch
                val remaining = task.channels.filterNot { it.id == oldChannel.id }
                val nextChannels = remaining + created.channel
                val newBot = created.channel.profile ?: defaultBotName()
                val remainingSessionBots = remaining.filter { it.isSession }.map { effectiveChannelProfile(it) }
                val nextBots = (listOf(newBot) + remainingSessionBots).distinct()
                val res = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(channels = nextChannels, assignedBots = nextBots),
                )
                if (res is ApiResult.Success && boundGateway == gateway) {
                    if (!externalContextOnly) {
                        activeRuntimeSessionId = created.runtimeId ?: created.channel.id
                        activeRuntimeChannelId = created.channel.id
                        created.info?.let { info ->
                            _state.update { it.copy(channelChatState = it.channelChatState.withChannelInfo(info)) }
                        }
                    }
                    dispatchStore?.notifyLifecycleChange(gateway.gatewayUrl, unarchivedIds = setOf(created.channel.id))
                    finalizeChannelMutation(
                        gateway = gateway,
                        previousTask = task,
                        updatedTask = res.value,
                        selectChannelId = created.channel.id,
                        removedChannels = listOf(oldChannel),
                    )
                } else if (res is ApiResult.Failed) {
                    _state.update { it.copy(actionFeedback = res.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Could not switch agent") }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    /** Drops [oldChannel] from [task], creates a new hosted room for [members], and updates `assigned_bots`. */
    fun replaceWithRoomChannel(task: DirectusTask, oldChannel: TaskChannel, members: List<Bot>) {
        val gateway = boundGateway ?: return
        if (members.size < 2) return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val excludedIds = task.channels.filter { it.isRoom }.mapTo(HashSet()) { it.id }
                val room = createTaskRoomWithFallback(task, members, excludedIds)
                val label = "Room (${members.joinToString(", ") { "@${it.name}" }})"
                val channel = TaskChannel(type = TaskChannel.TYPE_ROOM, id = room.roomId, label = label)
                val remaining = task.channels.filterNot { it.id == oldChannel.id || it.id == channel.id }
                val nextChannels = remaining + channel
                val remainingSessionBots = remaining.filter { it.isSession }.map { effectiveChannelProfile(it) }
                val nextBots = (members.map { it.name } + remainingSessionBots).distinct()
                val res = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(channels = nextChannels, assignedBots = nextBots),
                )
                if (res is ApiResult.Success && boundGateway == gateway) {
                    finalizeChannelMutation(
                        gateway = gateway,
                        previousTask = task,
                        updatedTask = res.value,
                        selectChannelId = channel.id,
                        removedChannels = listOf(oldChannel),
                    )
                } else if (res is ApiResult.Failed) {
                    _state.update { it.copy(actionFeedback = res.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Could not switch room") }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    /** Removes [channel] from [task], archives it if auto-managed and unshared, and syncs `assigned_bots`. */
    fun unlinkChannel(task: DirectusTask, channel: TaskChannel) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val remaining = task.channels.filterNot { it.id == channel.id }
                val nextBots = when {
                    remaining.isEmpty() -> emptyList()
                    remaining.none { it.isRoom } -> remaining.filter { it.isSession }.map { effectiveChannelProfile(it) }.distinct()
                    else -> {
                        val droppedBot = if (channel.isSession) effectiveChannelProfile(channel) else null
                        val base = if (droppedBot != null) task.assignedBots.filterNot { it.equals(droppedBot, ignoreCase = true) } else task.assignedBots
                        (base + remaining.filter { it.isSession }.map { effectiveChannelProfile(it) }).distinct()
                    }
                }
                val res = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(channels = remaining, assignedBots = nextBots),
                )
                if (res is ApiResult.Success && boundGateway == gateway) {
                    val prevActiveId = _state.value.activeChannel?.id
                    val nextSelectId = if (prevActiveId != null && prevActiveId != channel.id && remaining.any { it.id == prevActiveId }) {
                        prevActiveId
                    } else {
                        remaining.firstOrNull()?.id
                    }
                    finalizeChannelMutation(
                        gateway = gateway,
                        previousTask = task,
                        updatedTask = res.value,
                        selectChannelId = nextSelectId,
                        removedChannels = listOf(channel),
                    )
                } else if (res is ApiResult.Failed) {
                    _state.update { it.copy(actionFeedback = res.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Could not remove channel") }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    /** Switches [task]'s active session to match `assignedBots.firstOrNull()`, creating/reusing one while keeping history. */
    fun switchToAssignedAgent(task: DirectusTask) {
        val gateway = boundGateway ?: return
        val targetBot = task.assignedBots.firstOrNull()?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val resolved = ensureAgentSessionChannel(gateway, task, targetBot) ?: return@launch
                if (boundGateway != gateway) return@launch
                val idx = resolved.first.channels.indexOfFirst { it.id == resolved.second.id }.coerceAtLeast(0)
                _state.update { it.copy(activeChannelIdx = idx) }
                if (externalContextOnly) {
                    if (resolved.second.id != externalViewingChannelId) {
                        emitOpenChannelNav(resolved.first, resolved.second)
                    }
                } else {
                    loadChannelLog(gateway, resolved.first, idx)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Could not switch to @$targetBot") }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    fun sendToActiveChannel(task: DirectusTask, text: String) {
        val gateway = boundGateway ?: return
        val clean = text.trim()
        val attachments = _state.value.channelAttachments
        if (clean.isEmpty() && attachments.isEmpty()) return
        val visibleText = clean.ifEmpty {
            if (attachments.any { it.kind == AttachmentKind.Image }) IMAGE_ONLY_FALLBACK_PROMPT else ""
        }
        val optimisticKey = "user-${Uuid.random()}"
        val optimisticLine = ChannelChatLine(
            key = optimisticKey,
            speaker = null,
            text = visibleText,
            fromUser = true,
            attachments = attachments.map { it.toShown() },
        )
        _state.update { cur ->
            val sameChat = cur.selectedTaskId == task.id
            val watermark = readWatermarkFor(task)
            cur.copy(
                tasks = cur.tasks.map { t ->
                    if (t.id == task.id) t.copy(replyStatus = TaskReplyStatus(hasUnreadReply = false, awaitingReply = false)) else t
                },
                sendingToChannel = true,
                channelError = null,
                channelAttachments = emptyList(),
                channelMessages = if (sameChat) cur.channelMessages + optimisticLine else cur.channelMessages,
                channelTurnRunning = if (sameChat) true else cur.channelTurnRunning,
                channelTurnStatus = if (sameChat) null else cur.channelTurnStatus,
                localReadAtByTaskId = cur.localReadAtByTaskId + (task.id to watermark),
                localUnreadTaskIds = cur.localUnreadTaskIds - task.id,
            )
        }
        viewModelScope.launch {
            try {
                val currentTask = _state.value.tasks.firstOrNull { it.id == task.id } ?: task
                val existing = _state.value.activeChannel?.takeIf { ch -> currentTask.channels.any { it.id == ch.id } }
                val created = if (existing != null) {
                    currentTask to existing
                } else {
                    createSessionChannel(gateway, currentTask, currentTask.assignedBots.firstOrNull())
                }
                if (created == null) {
                    _state.update { cur ->
                        cur.copy(
                            channelAttachments = attachments + cur.channelAttachments,
                            channelMessages = cur.channelMessages.filterNot { it.key == optimisticKey },
                            channelTurnRunning = false,
                            channelTurnStatus = null,
                        )
                    }
                    return@launch
                }
                val (latestTask, channel) = created
                val idx = latestTask.channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                _state.update { cur ->
                    val sameChat = cur.selectedTaskId == latestTask.id
                    cur.copy(
                        activeChannelIdx = if (sameChat) idx else cur.activeChannelIdx,
                    )
                }
                lastStreamEventAtMs = nowMs()
                markChannelSeen(gateway, channel, latestTask.replyStatus?.latestRoomSeq)
                dispatchMessage(channel, visibleText, attachments, taskId = latestTask.id)
                if (!externalContextOnly) {
                    startTurnFallbackPoll(gateway, channel = channel, task = latestTask, channelIdx = idx)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { cur ->
                    cur.copy(
                        channelAttachments = attachments + cur.channelAttachments,
                        channelMessages = cur.channelMessages.filterNot { it.key == optimisticKey },
                        channelTurnRunning = false,
                        channelTurnStatus = null,
                        channelError = e.message ?: "Send failed",
                        actionFeedback = e.message ?: "Send failed",
                    )
                }
            } finally {
                _state.update { it.copy(sendingToChannel = false) }
            }
        }
    }

    fun preparePlan(task: DirectusTask) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            markBusy(task.id, true)
            try {
                val currentTask = _state.value.tasks.firstOrNull { it.id == task.id } ?: task
                val existing = _state.value.activeChannel?.takeIf { ch -> currentTask.channels.any { it.id == ch.id } }
                val (channelTask, channel) = if (existing != null) {
                    currentTask to existing
                } else {
                    createSessionChannel(gateway, currentTask, currentTask.assignedBots.firstOrNull()) ?: return@launch
                }

                val ctxRes = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(agentState = TaskContext.STATE_QUEUED),
                )
                val latestTask = if (ctxRes is ApiResult.Success && boundGateway == gateway) {
                    replaceTask(ctxRes.value)
                    ctxRes.value
                } else {
                    channelTask
                }

                val project = task.projectId?.let { pid -> _state.value.projects.firstOrNull { it.id == pid } }
                val prompt = buildString {
                    append("Prepare an actionable support plan for Directus task ${task.id} (${task.title}). ")
                    if (project != null) append("Project: ${project.name}. ")
                    if (!task.description.isNullOrBlank()) append("Context: ${task.description}. ")
                    append("Read recent tweak_deltas via `directus_task_board`, then save 2-3 inline parameter chips and action options once using `directus_stage_plan` (force=true).")
                }
                val idx = latestTask.channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                val watermark = readWatermarkFor(latestTask)
                _state.update { cur ->
                    cur.copy(
                        activeChannelIdx = if (!externalContextOnly && cur.selectedTaskId == latestTask.id) idx else cur.activeChannelIdx,
                        channelTurnRunning = if (!externalContextOnly && cur.selectedTaskId == latestTask.id) true else cur.channelTurnRunning,
                        channelTurnStatus = if (!externalContextOnly && cur.selectedTaskId == latestTask.id) null else cur.channelTurnStatus,
                        localReadAtByTaskId = cur.localReadAtByTaskId + (latestTask.id to watermark),
                        localUnreadTaskIds = cur.localUnreadTaskIds - latestTask.id,
                    )
                }
                lastStreamEventAtMs = nowMs()
                markChannelSeen(gateway, channel, latestTask.replyStatus?.latestRoomSeq)
                dispatchMessage(channel, prompt, displayKind = "hidden", taskId = latestTask.id)
                if (externalContextOnly) {
                    if (channel.id != externalViewingChannelId) {
                        emitOpenChannelNav(latestTask, channel)
                    }
                } else {
                    loadChannelLog(gateway, latestTask, idx, attachSession = false)
                    startTurnFallbackPoll(gateway, channel = channel, task = latestTask, channelIdx = idx)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(channelTurnRunning = false, actionFeedback = e.message ?: "Could not start plan preparation") }
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    fun answerChannelRequest(request: InputRequest, result: JsonObject) {
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: run {
            _state.update { it.copy(channelError = "Not connected to gateway.") }
            return
        }
        viewModelScope.launch {
            try {
                client.respond(request.id, result)
                _state.update { cur ->
                    cur.copy(channelInputRequests = cur.channelInputRequests.filterNot { it.id == request.id })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(channelError = e.message ?: "Could not send response.") }
            }
        }
    }

    fun approveStagedPlan(
        task: DirectusTask,
        option: ReplyInteraction.Option,
        selectedParams: Map<String, String>,
        tweaks: List<TweakDeltaInput>,
    ) {
        val gateway = boundGateway ?: return
        val plan = task.stagedPlan ?: return
        val params = plan.params.map { ReplyInteraction.Param(it.key, it.label, it.options, it.default) }
        val directive = formatActionCardReply(option, params, selectedParams)
        val conciseSummary = formatActionCardSummary(option, params, selectedParams)
        val requestedStatus = selectedParams["status"] ?: selectedParams["target_status"]
        val normalizedStatus = requestedStatus?.lowercase()?.replace(' ', '_')
            ?.takeIf { it in setOf(DirectusTask.STATUS_TODO, DirectusTask.STATUS_IN_PROGRESS, DirectusTask.STATUS_BLOCKED, DirectusTask.STATUS_DONE) }
            ?: if (task.status == DirectusTask.STATUS_TODO) DirectusTask.STATUS_IN_PROGRESS else null

        val chosenBot = resolveBotName(
            selectedParams["bot"] ?: selectedParams["agent"] ?: selectedParams["owner"],
        )
        val nextBots = if (chosenBot != null) (listOf(chosenBot) + task.assignedBots).distinct() else task.assignedBots

        // Optimistic UI update (< 0.2s) before network round-trip.
        val optimistic = task.copy(
            status = normalizedStatus ?: task.status,
            context = (task.context ?: TaskContext(taskId = task.id)).copy(
                agentState = TaskContext.STATE_RUNNING,
                assignedBots = nextBots,
            ),
        )
        replaceTask(optimistic)

        viewModelScope.launch {
            markBusy(task.id, true)
            var optimisticKey: String? = null
            try {
                val currentActive = _state.value.activeChannel?.takeIf { task.channels.any { ch -> ch.id == it.id } }
                val targetBot = chosenBot ?: nextBots.firstOrNull()?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
                val active = when {
                    currentActive == null -> createSessionChannel(gateway, optimistic, targetBot)?.second
                    !currentActive.isRoom && targetBot != null && !effectiveChannelProfile(currentActive).equals(targetBot, ignoreCase = true) ->
                        ensureAgentSessionChannel(gateway, optimistic, targetBot)?.second ?: currentActive
                    else -> currentActive
                }

                val approveRes = directusApi.approveTask(
                    gateway.gatewayUrl,
                    task.id,
                    ApproveTaskRequest(
                        summary = plan.summary,
                        chosenOption = option.label,
                        tweaks = tweaks,
                        newStatus = normalizedStatus,
                        newAgentState = TaskContext.STATE_RUNNING,
                        projectId = task.projectId,
                        assignedBots = nextBots,
                        channel = active,
                        workLogContent = "Approved on MotherBase: $directive",
                    ),
                )
                if (approveRes is ApiResult.Success && boundGateway == gateway) {
                    val approved = approveRes.value
                    if (approved.status == DirectusTask.STATUS_DONE && !_state.value.includeDone) {
                        _state.update { cur ->
                            cur.copy(
                                tasks = cur.tasks.filterNot { it.id == task.id },
                                selectedTaskId = if (cur.selectedTaskId == task.id) null else cur.selectedTaskId,
                            )
                        }
                    } else {
                        replaceTask(approved)
                    }
                }

                if (active != null) {
                    val latest = _state.value.tasks.firstOrNull { it.id == task.id }
                        ?: (approveRes as? ApiResult.Success)?.value
                        ?: optimistic
                    val idx = latest.channels.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
                    val keptOnBoard = _state.value.tasks.any { it.id == latest.id }
                    // Session turns receive the full instruction via directus-tasks pre_llm_call context,
                    // so the visible chat bubble stays concise. Rooms and failed-approve fallbacks carry the full text.
                    val outboundText = if (!active.isRoom && approveRes is ApiResult.Success) {
                        conciseSummary
                    } else {
                        "[Task: ${task.title}] $directive"
                    }
                    val watermark = readWatermarkFor(latest)
                    _state.update { cur ->
                        cur.copy(
                            localReadAtByTaskId = cur.localReadAtByTaskId + (latest.id to watermark),
                            localUnreadTaskIds = cur.localUnreadTaskIds - latest.id,
                        )
                    }
                    markChannelSeen(gateway, active, latest.replyStatus?.latestRoomSeq)
                    if (!externalContextOnly && keptOnBoard && _state.value.selectedTaskId == latest.id) {
                        val key = "user-${Uuid.random()}"
                        optimisticKey = key
                        _state.update { cur ->
                            cur.copy(
                                activeChannelIdx = idx,
                                channelMessages = cur.channelMessages + ChannelChatLine(
                                    key = key,
                                    speaker = null,
                                    text = outboundText,
                                    fromUser = true,
                                ),
                                channelTurnRunning = true,
                                channelTurnStatus = null,
                            )
                        }
                    }
                    lastStreamEventAtMs = nowMs()
                    dispatchMessage(active, outboundText, taskId = latest.id)
                    if (keptOnBoard) {
                        if (externalContextOnly) {
                            if (active.id != externalViewingChannelId) {
                                emitOpenChannelNav(latest, active)
                            }
                        } else {
                            loadChannelLog(gateway, latest, idx, attachSession = false)
                            startTurnFallbackPoll(gateway, channel = active, task = latest, channelIdx = idx)
                        }
                    }
                }
                if (approveRes is ApiResult.Success && boundGateway == gateway) {
                    syncSessionLifecycleOnStatusChange(gateway, task, approveRes.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { cur ->
                    cur.copy(
                        channelMessages = if (optimisticKey != null) cur.channelMessages.filterNot { it.key == optimisticKey } else cur.channelMessages,
                        channelTurnRunning = false,
                        channelTurnStatus = null,
                        actionFeedback = e.message ?: "Approval failed",
                    )
                }
                refresh()
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    fun openChannelFullscreen(task: DirectusTask, channel: TaskChannel) {
        viewModelScope.launch { emitOpenChannelNav(task, channel) }
    }

    private suspend fun emitOpenChannelNav(task: DirectusTask, channel: TaskChannel) {
        if (channel.isRoom) {
            val room = runCatching { roomsApi.state(channel.id).room }.getOrNull()
                ?: Room(roomId = channel.id, name = task.title)
            _events.send(TasksNavEvent.OpenRoom(room))
        } else {
            _events.send(TasksNavEvent.OpenSession(channel.id, task.title, channel.profile))
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(actionFeedback = null) }
    }

    fun consumeSessionExpired() {
        _state.update { it.copy(sessionExpired = false) }
    }

    private data class CreatedSessionRecord(
        val runtimeId: String?,
        val info: JsonObject?,
        val channel: TaskChannel,
    )

    private fun defaultBotName(): String =
        _state.value.bots.firstOrNull { it.isDefault }?.name ?: Bot.DEFAULT

    private fun effectiveChannelProfile(channel: TaskChannel): String =
        channel.profile ?: defaultBotName()

    private fun resolveBotName(raw: String?): String? {
        val clean = raw?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() } ?: return null
        val bots = _state.value.bots
        bots.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.name }
        bots.firstOrNull { it.label.equals(clean, ignoreCase = true) }?.let { return it.name }
        val parenHandle = clean.substringAfterLast("(@", "").substringBefore(")", "").trim()
        if (parenHandle.isNotEmpty()) {
            bots.firstOrNull { it.name.equals(parenHandle, ignoreCase = true) }?.let { return it.name }
            if (' ' !in parenHandle) return parenHandle
        }
        return clean.takeIf { ' ' !in it }
    }

    private suspend fun createTaskRoomWithFallback(
        task: DirectusTask,
        members: List<Bot>,
        excludedChannelIds: Set<String> = emptySet(),
    ): Room {
        val roster = members.take(6).map { bot ->
            RoomMemberInput(memberId = bot.name, profile = bot.name, handle = bot.name, displayName = bot.label)
        }
        val baseId = "task-${task.id}"
        val fallbackId = "$baseId-${Uuid.random().toString().take(8)}"
        if (baseId !in excludedChannelIds) {
            try {
                return roomsApi.create(roomId = baseId, name = task.title.take(64), members = roster)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Existing active/disbanded room id conflicts; retry with a unique suffix.
            }
        }
        return roomsApi.create(roomId = fallbackId, name = task.title.take(64), members = roster)
    }

    private suspend fun createSessionRecord(
        gateway: SavedGateway,
        task: DirectusTask,
        profile: String?,
    ): CreatedSessionRecord? {
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: run {
            _state.update { it.copy(actionFeedback = "Not connected to gateway.") }
            return null
        }
        val cleanProfile = profile?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val resolvedProfile = cleanProfile ?: _state.value.bots.firstOrNull { it.isDefault }?.name
        val project = task.projectId?.let { pid -> _state.value.projects.firstOrNull { it.id == pid } }
        val cwd = project?.workspaceCwd?.takeIf { it.isNotBlank() }
        val currentCh = _state.value.activeChannel
        val sameProfilePicks = currentCh == null ||
            (!currentCh.isRoom && (cleanProfile == null || effectiveChannelProfile(currentCh).equals(cleanProfile, ignoreCase = true)))
        val picks = if (sameProfilePicks) _state.value.channelChatState else ChatState()
        val result = client.request(
            "session.create",
            buildJsonObject {
                cleanProfile?.let { put("profile", it) }
                put("source", "desktop")
                put("cols", 80)
                cwd?.let { put("cwd", it) }
                if (picks.model != null && picks.provider != null) {
                    put("model", picks.model)
                    put("provider", picks.provider)
                }
                picks.reasoningEffort?.let { put("reasoning_effort", it) }
                picks.fast?.let { put("fast", it) }
            },
        ) as? JsonObject
        val runtimeId = (result?.get("session_id") as? JsonPrimitive)?.contentOrNull
        val storedId = (result?.get("stored_session_id") as? JsonPrimitive)?.contentOrNull ?: runtimeId ?: return null
        val info = result?.get("info") as? JsonObject
        val channelLabel = if (resolvedProfile != null) {
            val botLabel = _state.value.bots.firstOrNull { it.name.equals(resolvedProfile, ignoreCase = true) }
                ?.label?.takeIf { it.isNotBlank() }
            val display = if (botLabel != null && !botLabel.equals(resolvedProfile, ignoreCase = true)) {
                "$botLabel (@$resolvedProfile)"
            } else {
                "@$resolvedProfile"
            }
            "Session ($display)"
        } else {
            "Session (@Hermes)"
        }
        return CreatedSessionRecord(
            runtimeId = runtimeId,
            info = info,
            channel = TaskChannel(
                type = TaskChannel.TYPE_SESSION,
                id = storedId,
                label = channelLabel,
                profile = resolvedProfile,
                managed = true,
            ),
        )
    }

    private suspend fun ensureAgentSessionChannel(
        gateway: SavedGateway,
        task: DirectusTask,
        targetBot: String,
    ): Pair<DirectusTask, TaskChannel>? {
        val existing = task.channels.firstOrNull { ch ->
            ch.isSession && effectiveChannelProfile(ch).equals(targetBot, ignoreCase = true)
        }
        if (existing != null) return task to existing
        return createSessionChannel(gateway, task, targetBot)
    }

    private suspend fun finalizeChannelMutation(
        gateway: SavedGateway,
        previousTask: DirectusTask,
        updatedTask: DirectusTask,
        selectChannelId: String?,
        removedChannels: List<TaskChannel>,
    ) {
        if (removedChannels.isNotEmpty()) {
            archiveUnsharedManagedSessions(
                gateway = gateway,
                channels = removedChannels,
                archived = true,
                excludeTaskId = updatedTask.id,
            )
        }
        replaceTask(updatedTask)
        dispatchStore?.syncTask(gateway.gatewayUrl, updatedTask)
        if (_state.value.selectedTaskId == updatedTask.id) {
            turnPollJob?.cancel()
            activeRuntimeSessionId = null
            activeRuntimeChannelId = null
            val nextIdx = if (selectChannelId != null) {
                updatedTask.channels.indexOfFirst { it.id == selectChannelId }.coerceAtLeast(0)
            } else {
                0
            }
            _state.update { cur ->
                cur.copy(
                    activeChannelIdx = nextIdx,
                    channelMessages = emptyList(),
                    channelAttachments = emptyList(),
                    channelChatState = if (updatedTask.channels.isEmpty()) ChatState() else cur.channelChatState,
                    channelError = null,
                    channelTurnRunning = false,
                    channelTurnStatus = null,
                    channelInputRequests = emptyList(),
                )
            }
            if (updatedTask.channels.isNotEmpty()) {
                if (externalContextOnly) {
                    val selectedChannel = updatedTask.channels.getOrNull(nextIdx)
                    if (selectedChannel != null && selectedChannel.id != externalViewingChannelId) {
                        emitOpenChannelNav(updatedTask, selectedChannel)
                    }
                } else {
                    loadChannelLog(gateway, updatedTask, nextIdx)
                }
            } else if (!externalContextOnly) {
                loadModels(withPrices = false)
            }
        }
    }

    private suspend fun createSessionChannel(
        gateway: SavedGateway,
        task: DirectusTask,
        profile: String?,
    ): Pair<DirectusTask, TaskChannel>? {
        val created = createSessionRecord(gateway, task, profile) ?: return null
        val channel = created.channel
        val mergedChannels = task.channels + channel
        val profileForBots = channel.profile ?: profile
        val nextBots = if (profileForBots != null) (task.assignedBots + profileForBots).distinct() else task.assignedBots
        val res = directusApi.upsertContext(
            gateway.gatewayUrl,
            task.id,
            UpsertContextRequest(channels = mergedChannels, assignedBots = nextBots),
        )
        val updated = if (res is ApiResult.Success) {
            res.value
        } else {
            task.copy(
                context = (task.context ?: TaskContext(taskId = task.id)).copy(channels = mergedChannels, assignedBots = nextBots),
            )
        }
        if (!externalContextOnly) {
            activeRuntimeSessionId = created.runtimeId ?: channel.id
            activeRuntimeChannelId = channel.id
        }
        (created.runtimeId ?: channel.id).let { rtId -> runningTaskSessions[rtId] = task.id }
        if (!externalContextOnly && created.info != null) {
            _state.update { it.copy(channelChatState = it.channelChatState.withChannelInfo(created.info)) }
        }
        replaceTask(updated)
        dispatchStore?.syncTask(gateway.gatewayUrl, updated)
        dispatchStore?.notifyLifecycleChange(gateway.gatewayUrl, unarchivedIds = setOf(channel.id))
        return updated to channel
    }

    private suspend fun attachSessionChannel(channel: TaskChannel) {
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: return
        val resumed = runCatching {
            client.request(
                "session.resume",
                buildJsonObject {
                    put("session_id", channel.id)
                    channel.profile?.let { put("profile", it) }
                    put("source", "desktop")
                    put("cols", 80)
                    put("omit_messages", true)
                },
            ) as? JsonObject
        }.getOrNull() ?: return
        val runtimeId = resumed.str("session_id") ?: return
        val info = resumed["info"] as? JsonObject
        val running = resumed.bool("running") == true
        val openRequests = resumed["open_requests"].asObjects().mapNotNull { snapshot ->
            InputRequest.parse(
                id = snapshot.str("id") ?: return@mapNotNull null,
                method = snapshot.str("method") ?: return@mapNotNull null,
                params = snapshot["params"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
        val owningTaskId = resolveOwningTaskId(runtimeId) ?: resolveOwningTaskId(channel.id)
        if (owningTaskId != null && (running || openRequests.isNotEmpty())) {
            runningTaskSessions[runtimeId] = owningTaskId
        }
        if (externalContextOnly) return
        activeRuntimeSessionId = runtimeId
        activeRuntimeChannelId = channel.id
        val inflightText = (resumed["inflight"] as? JsonObject).str("assistant").orEmpty()
        val botLabel = channel.profile ?: "Hermes"
        _state.update { cur ->
            if (cur.activeChannel?.id != channel.id) return@update cur
            val nextMsgs = if (inflightText.isNotBlank() && cur.channelMessages.none { !it.fromUser && it.streaming }) {
                cur.channelMessages + ChannelChatLine(
                    key = "inflight-${channel.id}",
                    speaker = botLabel,
                    text = inflightText,
                    fromUser = false,
                    profile = channel.profile,
                    streaming = true,
                )
            } else {
                cur.channelMessages
            }
            cur.copy(
                channelChatState = cur.channelChatState.withChannelInfo(info),
                channelTurnRunning = running || openRequests.isNotEmpty(),
                channelInputRequests = openRequests,
                channelMessages = nextMsgs,
            )
        }
        loadModels(withPrices = false)
    }

    private fun handleGatewayEvent(event: GatewayEvent) {
        proposalCoordinator.observeGatewayEvent(event)
        if (event.type in setOf(
                ScheduleProposalCoordinator.EVENT_CHANGED,
                ScheduleProposalCoordinator.EVENT_SYNCED,
                ScheduleProposalCoordinator.EVENT_PROPOSAL_UPDATED,
                ScheduleProposalCoordinator.EVENT_PROPOSAL_DELETED,
            )
        ) {
            boundGateway?.let { gw -> viewModelScope.launch { refreshTaskTimeBlocksSilently(gw) } }
            return
        }
        val payload = event.payload as? JsonObject
        if (event.type == "message.complete" || event.type == "turn.error" || event.type == "session.error") {
            val isActiveForeground = !externalContextOnly &&
                event.sessionId == activeRuntimeSessionId &&
                (!hasObservedVisibility || pageVisible)
            if (isActiveForeground) {
                runningTaskSessions.remove(event.sessionId)
                turnPollJob?.cancel()
                _state.update { cur ->
                    cur.copy(
                        channelTurnRunning = false,
                        channelTurnStatus = null,
                        channelInputRequests = emptyList(),
                        channelMessages = cur.channelMessages.map { if (it.streaming) it.copy(streaming = false) else it },
                    )
                }
                val gw = boundGateway ?: return
                if (_state.value.coordinatorOpen) {
                    viewModelScope.launch {
                        loadCoordinatorLog(gw, attachSession = false)
                        refreshBoardSilently(gw)
                    }
                    return
                }
                val task = _state.value.selectedTask ?: return
                val idx = _state.value.activeChannelIdx
                viewModelScope.launch {
                    loadChannelLog(gw, task, idx, attachSession = false)
                    refreshBoardSilently(gw)
                }
                return
            }
            val evSessionId = event.sessionId ?: return
            val backgroundTaskId = runningTaskSessions.remove(evSessionId) ?: resolveOwningTaskId(evSessionId)
            if (backgroundTaskId != null) {
                val viewingExternally = externalContextOnly && externalViewingTaskId == backgroundTaskId
                if (!viewingExternally) {
                    _state.update { cur ->
                        cur.copy(
                            localUnreadTaskIds = cur.localUnreadTaskIds + backgroundTaskId,
                            localReadAtByTaskId = cur.localReadAtByTaskId - backgroundTaskId,
                        )
                    }
                }
                boundGateway?.let { gw -> viewModelScope.launch { refreshBoardSilently(gw) } }
            }
            return
        }
        val runtimeId = activeRuntimeSessionId ?: return
        if (externalContextOnly || event.sessionId != runtimeId) return
        when (event.type) {
            "message.start" -> {
                lastStreamEventAtMs = nowMs()
                _state.update { it.copy(channelTurnRunning = true, channelError = null, channelTurnStatus = it.channelTurnStatus ?: "Thinking…") }
            }
            "thinking.delta" -> {
                lastStreamEventAtMs = nowMs()
                payload.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { frame ->
                    _state.update { it.copy(channelTurnRunning = true, channelTurnStatus = frame) }
                }
            }
            "tool.start", "tool.complete", "tool.result" -> {
                lastStreamEventAtMs = nowMs()
                val toolName = payload.str("name") ?: payload.str("tool") ?: "tool"
                val interaction = ReplyInteraction.from(
                    toolName = toolName,
                    args = payload?.get("arguments") ?: payload?.get("args"),
                    result = payload?.get("result") ?: payload?.get("output"),
                )
                _state.update { cur ->
                    val nextMsgs = if (interaction != null) {
                        val msgs = cur.channelMessages
                        val last = msgs.lastOrNull()
                        val botLabel = cur.activeChannel?.profile ?: "Hermes"
                        if (last != null && !last.fromUser) {
                            msgs.dropLast(1) + last.copy(interaction = interaction)
                        } else {
                            msgs + ChannelChatLine(
                                key = "stream-${cur.selectedTaskId ?: "coord"}-${msgs.size}",
                                speaker = botLabel,
                                text = "",
                                fromUser = false,
                                profile = cur.activeChannel?.profile,
                                streaming = true,
                                interaction = interaction,
                            )
                        }
                    } else {
                        cur.channelMessages
                    }
                    cur.copy(
                        channelTurnRunning = true,
                        channelTurnStatus = if (event.type == "tool.start") toolName else cur.channelTurnStatus,
                        channelMessages = nextMsgs,
                    )
                }
                if (event.type != "tool.start" && toolName.startsWith("directus_")) {
                    boundGateway?.let { gw -> viewModelScope.launch { refreshBoardSilently(gw) } }
                }
            }
            "message.delta", "message.interim" -> {
                if (event.type == "message.interim" && payload.bool("already_streamed") == true) return
                val chunk = payload.str("text") ?: return
                if (chunk.isEmpty()) return
                lastStreamEventAtMs = nowMs()
                val botLabel = _state.value.activeChannel?.profile ?: "Hermes"
                val added = if (event.type == "message.interim") "$chunk\n\n" else chunk
                _state.update { cur ->
                    val msgs = cur.channelMessages
                    val last = msgs.lastOrNull()
                    val next = if (last != null && !last.fromUser && last.streaming) {
                        msgs.dropLast(1) + last.copy(text = last.text + added)
                    } else {
                        msgs + ChannelChatLine(
                            key = "stream-${cur.selectedTaskId ?: "coord"}-${msgs.size}",
                            speaker = botLabel,
                            text = added,
                            fromUser = false,
                            profile = cur.activeChannel?.profile,
                            streaming = true,
                        )
                    }
                    cur.copy(channelMessages = next, channelTurnRunning = true, channelTurnStatus = null)
                }
            }
            "request.cancel" -> {
                val reqId = payload.str("id") ?: return
                _state.update { cur -> cur.copy(channelInputRequests = cur.channelInputRequests.filterNot { it.id == reqId }) }
            }
        }
    }

    private fun handleServerRequest(request: ServerRequest) {
        val runtimeId = activeRuntimeSessionId ?: return
        if (request.sessionId != runtimeId) return
        val parsed = InputRequest.parse(request.id, request.method, request.params) ?: return
        _state.update { cur ->
            cur.copy(
                channelInputRequests = cur.channelInputRequests.filterNot { it.id == parsed.id } + parsed,
                channelTurnRunning = true,
            )
        }
    }

    private fun startTurnFallbackPoll(
        gateway: SavedGateway,
        channel: TaskChannel,
        task: DirectusTask? = null,
        channelIdx: Int = 0,
    ) {
        turnPollJob?.cancel()
        turnPollJob = viewModelScope.launch {
            repeat(60) {
                delay(3_000)
                val sameTarget = if (task != null) {
                    _state.value.selectedTaskId == task.id
                } else {
                    _state.value.coordinatorOpen
                }
                if (boundGateway != gateway ||
                    !sameTarget ||
                    _state.value.activeChannel?.id != channel.id ||
                    connection.state.value !is ConnectionState.Connected
                ) {
                    return@launch
                }
                val sinceLastEvent = nowMs() - lastStreamEventAtMs
                if (channel.isRoom || sinceLastEvent >= 3_500) {
                    if (task != null) {
                        loadChannelLog(gateway, task, channelIdx, attachSession = false)
                    } else {
                        loadCoordinatorLog(gateway, attachSession = false)
                    }
                    if (!channel.isRoom && _state.value.channelMessages.lastOrNull()?.let { !it.fromUser && !it.streaming } == true) {
                        _state.update { it.copy(channelTurnRunning = false, channelTurnStatus = null) }
                        refreshBoardSilently(gateway)
                        return@launch
                    }
                }
                if (!_state.value.channelTurnRunning) return@launch
            }
        }
    }

    private suspend fun refreshBoardSilently(gateway: SavedGateway) {
        val includeDone = _state.value.includeDone
        val res = directusApi.fetchBoard(gateway.gatewayUrl, includeDone = includeDone)
        if (res !is ApiResult.Success || boundGateway != gateway) return
        val board = res.value
        _state.update { cur ->
            val serverById = board.tasks.associateBy { it.id }
            val keptLocalRead = keptLocalReadAt(cur.localReadAtByTaskId, board.tasks)
            val keptLocalUnread = cur.localUnreadTaskIds.filterTo(HashSet()) { id ->
                val task = serverById[id]
                task != null && task.status != DirectusTask.STATUS_DONE &&
                    !(externalContextOnly && externalViewingTaskId == id)
            }
            cur.copy(
                projects = board.projects,
                tasks = board.tasks,
                todayEpochDay = currentLocalEpochDay(),
                localReadAtByTaskId = keptLocalRead,
                localUnreadTaskIds = keptLocalUnread,
            )
        }
        dispatchStore?.syncBoard(gateway.gatewayUrl, board.tasks, includeDone = includeDone)
    }

    /**
     * Keeps gateway session archive state in sync with Dispatch task transitions:
     * - Moving into `Done` archives auto-managed session channels unless still referenced by another non-`Done` task.
     * - Reopening out of `Done` unarchives those session channels (skipping ones already active on sibling tasks).
     */
    private suspend fun syncSessionLifecycleOnStatusChange(
        gateway: SavedGateway,
        previousTask: DirectusTask?,
        updatedTask: DirectusTask,
    ) {
        val store = dispatchStore
        val managedChannels = (updatedTask.channels + previousTask?.channels.orEmpty())
            .distinctBy { it.id }
            .filter { it.isAutoManagedSession }
        if (managedChannels.isEmpty()) {
            store?.syncTask(gateway.gatewayUrl, updatedTask)
            return
        }

        val movedToDone = updatedTask.status == DirectusTask.STATUS_DONE
        val reopenedFromDone = previousTask?.status == DirectusTask.STATUS_DONE && updatedTask.status != DirectusTask.STATUS_DONE
        if (!movedToDone && !reopenedFromDone) {
            store?.syncTask(gateway.gatewayUrl, updatedTask)
            return
        }

        val firstError = archiveUnsharedManagedSessions(
            gateway = gateway,
            channels = managedChannels,
            archived = movedToDone,
            excludeTaskId = updatedTask.id,
        )
        store?.syncTask(gateway.gatewayUrl, updatedTask)

        if (firstError != null && boundGateway == gateway) {
            _state.update { it.copy(actionFeedback = firstError) }
        }
    }

    private fun sharedActiveSessionIds(gateway: SavedGateway, excludeTaskId: String): Set<String> {
        val activeInBoard = _state.value.tasks
            .filter { it.id != excludeTaskId && it.status != DirectusTask.STATUS_DONE }
            .flatMap { it.channels }
            .filter { it.isSession }
            .mapTo(HashSet()) { it.id }
        val activeInStore = dispatchStore?.byGateway?.value?.get(gateway.gatewayUrl)?.values
            ?.filter { it.taskId != excludeTaskId && it.taskStatus != DirectusTask.STATUS_DONE }
            ?.mapTo(HashSet()) { it.sessionId }
            .orEmpty()
        return activeInBoard + activeInStore
    }

    private suspend fun archiveUnsharedManagedSessions(
        gateway: SavedGateway,
        channels: List<TaskChannel>,
        archived: Boolean,
        excludeTaskId: String,
    ): String? {
        val managed = channels.distinctBy { it.id }.filter { it.isAutoManagedSession }
        if (managed.isEmpty()) return null
        val sharedActiveIds = sharedActiveSessionIds(gateway, excludeTaskId)
        val changedIds = LinkedHashSet<String>()
        var firstError: String? = null
        for (ch in managed) {
            if (ch.id in sharedActiveIds) continue
            when (val res = sessionsApi.setArchived(gateway.gatewayUrl, ch.id, archived = archived, profile = ch.profile)) {
                is ApiResult.Success -> changedIds += ch.id
                is ApiResult.Failed -> {
                    if (res.status == 404) {
                        if (archived) changedIds += ch.id
                    } else if (firstError == null) {
                        firstError = res.message
                    }
                }
                else -> if (firstError == null) firstError = res.errorMessage
            }
        }
        dispatchStore?.notifyLifecycleChange(
            gateway = gateway.gatewayUrl,
            archivedIds = if (archived) changedIds else emptySet(),
            unarchivedIds = if (archived) emptySet() else changedIds,
        )
        return firstError
    }

    private suspend fun setSessionConfig(
        channel: TaskChannel,
        key: String,
        value: JsonElement,
        confirmExpensive: Boolean = false,
    ): JsonObject? {
        val client = (connection.state.value as? ConnectionState.Connected)?.client
            ?: throw RpcException(0, "Not connected to gateway.")
        val runtimeId = ensureRuntimeSession(client, channel, applyInfo = false)
        return client.request(
            "config.set",
            buildJsonObject {
                put("session_id", runtimeId)
                put("key", key)
                put("value", value)
                if (confirmExpensive) put("confirm_expensive_model", true)
            },
        ) as? JsonObject
    }

    private suspend fun ensureRuntimeSession(
        client: JsonRpcClient,
        channel: TaskChannel,
        applyInfo: Boolean = true,
    ): String {
        val existing = activeRuntimeSessionId
        if (existing != null && activeRuntimeChannelId == channel.id) return existing
        val resumed = client.request(
            "session.resume",
            buildJsonObject {
                put("session_id", channel.id)
                channel.profile?.let { put("profile", it) }
                put("source", "desktop")
                put("cols", 80)
                put("omit_messages", true)
            },
        ) as? JsonObject
        val runtimeId = resumed.str("session_id") ?: error("Could not resume session.")
        if (!externalContextOnly) {
            activeRuntimeSessionId = runtimeId
            activeRuntimeChannelId = channel.id
            if (applyInfo) {
                (resumed?.get("info") as? JsonObject)?.let { info ->
                    _state.update { cur ->
                        if (cur.activeChannel?.id == channel.id) cur.copy(channelChatState = cur.channelChatState.withChannelInfo(info))
                        else cur
                    }
                }
            }
        }
        return runtimeId
    }

    private suspend fun dispatchMessage(
        channel: TaskChannel,
        text: String,
        attachments: List<OutgoingAttachment> = emptyList(),
        displayKind: String? = null,
        taskId: String? = null,
    ) {
        if (channel.isRoom) {
            val uploaded = attachments.map { file ->
                if (file.size > OutgoingAttachment.MAX_INLINE_BYTES) {
                    throw RpcException(
                        JsonRpcClient.MESSAGE_TOO_LARGE,
                        "${file.name} is larger than ${OutgoingAttachment.MAX_INLINE_BYTES / 1_000_000} MB.",
                    )
                }
                roomsApi.upload(channel.id, "mb-${Uuid.random()}", file)
            }
            roomsApi.send(
                roomId = channel.id,
                text = text,
                threadId = "main",
                eventId = "mb-${Uuid.random()}",
                attachments = uploaded,
            )
            return
        }
        val client = (connection.state.value as? ConnectionState.Connected)?.client
            ?: error("Not connected to gateway.")
        val runtimeId = ensureRuntimeSession(client, channel)
        if (taskId != null) {
            runningTaskSessions[runtimeId] = taskId
        }
        val gatewayUrl = boundGateway?.gatewayUrl
        val queuedImages = mutableListOf<String>()
        val refs = mutableListOf<String>()
        try {
            if (attachments.isNotEmpty()) {
                val gwUrl = gatewayUrl ?: error("No gateway bound.")
                for (att in attachments) {
                    val uploaded = sessionsApi.attachToSession(gwUrl, client, runtimeId, att, queuedImages)
                    uploaded.refText?.let { refs += it }
                }
            }
            val payloadText = (refs + text).filter { it.isNotEmpty() }.joinToString("\n\n")
            client.request(
                "prompt.submit",
                buildJsonObject {
                    put("session_id", runtimeId)
                    put("text", payloadText)
                    displayKind?.let { put("display_kind", it) }
                },
            )
        } catch (e: CancellationException) {
            detachSessionImages(client, runtimeId, queuedImages)
            throw e
        } catch (e: Exception) {
            detachSessionImages(client, runtimeId, queuedImages)
            throw e
        }
    }

    private fun readWatermarkFor(task: DirectusTask): Double =
        task.replyStatus?.lastReplyAt ?: 0.0

    private fun keptLocalReadAt(
        curMap: Map<String, Double>,
        serverTasks: List<DirectusTask>,
    ): Map<String, Double> {
        if (curMap.isEmpty()) return emptyMap()
        val serverById = serverTasks.associateBy { it.id }
        return buildMap {
            curMap.forEach { (id, readAt) ->
                val rs = serverById[id]?.replyStatus ?: return@forEach
                val roomUnreadCandidate = rs.lastReplyChannelType == TaskChannel.TYPE_ROOM && (rs.latestRoomSeq ?: 0) > 0
                if (!rs.hasUnreadReply && !roomUnreadCandidate) return@forEach
                val replyAt = rs.lastReplyAt
                if (replyAt == null || replyAt <= readAt + 1.0) {
                    put(id, readAt)
                }
            }
        }
    }

    private fun resolveOwningTaskId(sessionId: String): String? {
        _state.value.tasks.firstOrNull { t -> t.channels.any { !it.isRoom && it.id == sessionId } }?.id?.let { return it }
        val gwUrl = boundGateway?.gatewayUrl ?: return null
        return dispatchStore?.byGateway?.value?.get(gwUrl)?.get(sessionId)?.taskId
    }

    private fun replaceTask(updated: DirectusTask) {
        _state.update { cur ->
            val exists = cur.tasks.any { it.id == updated.id }
            val next = if (exists) cur.tasks.map { if (it.id == updated.id) updated else it } else listOf(updated) + cur.tasks
            cur.copy(tasks = next)
        }
    }

    private fun markBusy(taskId: String, busy: Boolean) {
        _state.update { cur ->
            val next = if (busy) cur.busyTaskIds + taskId else cur.busyTaskIds - taskId
            cur.copy(busyTaskIds = next)
        }
    }

    // ── Calendar Time-Block & Schedule Proposal Integration ──────────────────

    private suspend fun refreshTaskTimeBlocksSilently(gateway: SavedGateway) {
        val api = calendarApi ?: return
        val health = api.health(gateway.gatewayUrl)
        if (health is ApiResult.Success && boundGateway == gateway) {
            val tz = health.value.settings.timezone.ifBlank { "Asia/Ho_Chi_Minh" }
            _state.update { it.copy(calendarPluginAvailable = true, calendarTimezone = tz) }
            val overviewRes = api.dispatchOverview(gateway.gatewayUrl, includeDone = _state.value.includeDone)
            if (overviewRes is ApiResult.Success && boundGateway == gateway) {
                val grouped = overviewRes.value.timeBlocks.groupBy { it.taskId }
                _state.update { it.copy(taskTimeBlocks = grouped) }
            }
        }
    }

    fun openScheduleTaskBlock(task: DirectusTask, existingBlock: DispatchTimeBlock? = null) {
        val blocks = _state.value.taskTimeBlocks[task.id].orEmpty()
        val tz = _state.value.calendarTimezone.ifBlank { null }
        val moment = existingBlock?.startAt?.let { calendarZonedFromIso(it, tz) }
        val today = calendarNowZoned(tz).date
        val defaultDate = moment?.date?.iso ?: task.dueDate?.take(10) ?: today.iso
        val defaultStart = moment?.let { formatClock24(it.hour, it.minute) } ?: "09:00"
        val duration = task.estimatedMinutes?.coerceIn(15, 480) ?: 60
        _state.update {
            it.copy(
                schedulingTaskDraft = TaskBlockDraft(
                    task = task.toDispatchCalendarTask(blocks),
                    existingBlockId = existingBlock?.id,
                    label = existingBlock?.label ?: task.title,
                    dateIso = defaultDate,
                    startTime = defaultStart,
                    durationMinutes = duration,
                ),
            )
        }
        refreshCalendarFreeSlots(duration)
    }

    fun closeScheduleTaskBlock() {
        _state.update { it.copy(schedulingTaskDraft = null) }
    }

    fun refreshCalendarFreeSlots(minDurationMinutes: Int = 30) {
        val gateway = boundGateway ?: return
        val api = calendarApi ?: return
        val tz = _state.value.calendarTimezone.ifBlank { null }
        val today = calendarNowZoned(tz).date
        viewModelScope.launch {
            _state.update { it.copy(findingFreeSlots = true) }
            val res = api.findFreeSlots(
                gateway.gatewayUrl,
                startDate = today.iso,
                endDate = today.plusDays(6).iso,
                minDurationMinutes = minDurationMinutes.coerceAtLeast(15),
                bufferMinutes = 10,
            )
            if (boundGateway == gateway) {
                _state.update {
                    it.copy(
                        findingFreeSlots = false,
                        freeSlots = (res as? ApiResult.Success)?.value ?: it.freeSlots,
                    )
                }
            }
        }
    }

    fun saveTaskTimeBlock(draft: TaskBlockDraft) {
        val gateway = boundGateway ?: return
        val api = calendarApi ?: return
        val date = calendarDateFromIso(draft.dateIso.trim()) ?: run {
            _state.update { it.copy(actionFeedback = "Invalid date (YYYY-MM-DD).") }
            return
        }
        val (sh, sm) = parseTimeHHMM(draft.startTime) ?: (9 to 0)
        val endMin = (sh * 60 + sm + draft.durationMinutes.coerceIn(15, 720)).coerceAtMost(23 * 60 + 59)
        val tz = _state.value.calendarTimezone.ifBlank { "Asia/Ho_Chi_Minh" }
        val startUtc = calendarToUtcIso(date, sh, sm, tz)
        val endUtc = calendarToUtcIso(date, endMin / 60, endMin % 60, tz)
        viewModelScope.launch {
            _state.update { it.copy(calendarMutating = true) }
            val res = if (draft.existingBlockId == null) {
                api.createTimeBlock(
                    gateway.gatewayUrl,
                    CreateTimeBlockRequest(
                        taskId = draft.task.id,
                        startAt = startUtc,
                        endAt = endUtc,
                        label = draft.label.trim().ifEmpty { draft.task.title },
                        timezone = tz,
                    ),
                )
            } else {
                api.updateTimeBlock(
                    gateway.gatewayUrl,
                    draft.existingBlockId,
                    UpdateTimeBlockRequest(
                        startAt = startUtc,
                        endAt = endUtc,
                        label = draft.label.trim().ifEmpty { draft.task.title },
                    ),
                )
            }
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(calendarMutating = false, schedulingTaskDraft = null) }
                    refreshTaskTimeBlocksSilently(gateway)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(calendarMutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(calendarMutating = false, actionFeedback = res.errorTextOrNull()) }
            }
        }
    }

    fun deleteTaskTimeBlock(blockId: String) {
        val gateway = boundGateway ?: return
        val api = calendarApi ?: return
        viewModelScope.launch {
            _state.update { it.copy(calendarMutating = true) }
            when (val res = api.deleteTimeBlock(gateway.gatewayUrl, blockId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(calendarMutating = false, schedulingTaskDraft = null) }
                    refreshTaskTimeBlocksSilently(gateway)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(calendarMutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(calendarMutating = false, actionFeedback = res.errorTextOrNull()) }
            }
        }
    }

    fun applyScheduleProposal(proposalId: String, selectedOpIds: List<String>, force: Boolean = false) {
        val gw = boundGateway ?: return
        proposalCoordinator.apply(gw.gatewayUrl, proposalId, selectedOpIds, force)
    }

    fun discardScheduleProposal(proposalId: String) {
        val gw = boundGateway ?: return
        proposalCoordinator.discard(gw.gatewayUrl, proposalId)
    }

    fun revertScheduleProposal(proposalId: String) {
        val gw = boundGateway ?: return
        proposalCoordinator.revert(gw.gatewayUrl, proposalId, force = true)
    }

    fun updateScheduleProposal(proposalId: String, request: UpdateProposalRequest) {
        val gw = boundGateway ?: return
        proposalCoordinator.update(gw.gatewayUrl, proposalId, request)
    }

    fun restageScheduleProposal(proposalId: String) {
        val gw = boundGateway ?: return
        proposalCoordinator.restage(gw.gatewayUrl, proposalId)
    }

    fun deleteScheduleProposal(proposalId: String) {
        val gw = boundGateway ?: return
        proposalCoordinator.delete(gw.gatewayUrl, proposalId)
    }

    fun dismissProposalConflict() = proposalCoordinator.dismissConflict()

    fun confirmForceApplyProposal() {
        val gw = boundGateway ?: return
        proposalCoordinator.confirmForceApply(gw.gatewayUrl)
    }
}

private fun DirectusTask.toDispatchCalendarTask(blocks: List<DispatchTimeBlock>): DispatchCalendarTask =
    DispatchCalendarTask(
        id = id,
        title = title,
        status = status,
        priority = priority,
        dueDate = dueDate,
        completedAt = completedAt,
        projectId = projectId,
        agentState = agentState,
        estimatedMinutes = estimatedMinutes,
        timeBlocks = blocks,
    )

internal fun ChatState.withChannelInfo(info: JsonObject?): ChatState {
    if (info == null) return this
    return copy(
        model = info.str("model")?.takeIf { it.isNotBlank() } ?: model,
        provider = info.str("provider")?.takeIf { it.isNotBlank() } ?: provider,
        reasoningEffort = info.str("reasoning_effort")?.let { it.ifBlank { null } } ?: reasoningEffort.takeIf { "reasoning_effort" !in info },
        reasoningRevision = if ("reasoning_effort" in info) reasoningRevision + 1 else reasoningRevision,
        fast = info.bool("fast") ?: fast,
    )
}

internal fun formatIsoDate(year: Int, month: Int, day: Int): String =
    "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

internal fun parseSessionChatLines(
    allMessages: List<SessionMessage>,
    profile: String?,
    limit: Int = 30,
): List<ChannelChatLine> {
    val botLabel = profile ?: "Hermes"
    val toolResults = allMessages
        .filter { it.role == "tool" && !it.toolCallId.isNullOrBlank() }
        .associate { it.toolCallId!! to it.content }
    return allMessages
        .filterNot { it.isHidden || it.role == "tool" || it.role == "system" }
        .mapNotNull { msg ->
            val raw = msg.text.trim()
            val key = "sess-${msg.id ?: raw.hashCode()}"
            val isUser = msg.role == "user"
            val (refs, cleanText) = if (isUser) splitAttachmentRefs(raw, key) else emptyList<ShownAttachment>() to raw
            val attachments = if (isUser) {
                List(msg.imageCount) { ShownAttachment("$key-img$it", "Image", AttachmentKind.Image) } + refs
            } else {
                emptyList()
            }
            val interaction = if (!isUser) {
                msg.storedToolCalls.asReversed().firstNotNullOfOrNull { call ->
                    ReplyInteraction.from(call.name, call.arguments, toolResults[call.id])
                }
            } else {
                null
            }
            if (cleanText.isEmpty() && attachments.isEmpty() && interaction == null) null else ChannelChatLine(
                key = key,
                speaker = if (isUser) null else botLabel,
                text = cleanText,
                fromUser = isUser,
                profile = profile,
                attachments = attachments,
                interaction = interaction,
            )
        }
        .takeLast(limit)
}

private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.asObjects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
