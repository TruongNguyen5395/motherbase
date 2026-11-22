package dev.hermeskotlin.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotsApi
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
import dev.hermeskotlin.core.tasks.DispatchSessionsStore
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TaskReplyStatus
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.core.tasks.UpdateTaskRequest
import dev.hermeskotlin.core.tasks.UpsertContextRequest
import dev.hermeskotlin.core.tasks.matrixSyncedPriority
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
import kotlin.time.Instant
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
    val showPriorityStrip: Boolean = true,
    val boardViewMode: TasksBoardViewMode = TasksBoardViewMode.PROJECTS,
    val focusedProjectId: String? = null,
    val focusGrouping: TaskFocusGrouping = TaskFocusGrouping.WORKSTREAM,
    val expandedProjectIds: Set<String> = emptySet(),
    val collapsedWorkstreamKeys: Set<String> = emptySet(),
    val todayEpochDay: Long = currentLocalEpochDay(),
    val seenRoomSeqs: Map<String, Int> = emptyMap(),
    val waitingSessionIds: Set<String> = emptySet(),
    val localReadTaskIds: Set<String> = emptySet(),
    val localAckedTaskIds: Set<String> = emptySet(),
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
                localReadTaskIds = localReadTaskIds,
                localAckedTaskIds = localAckedTaskIds,
                localUnreadTaskIds = localUnreadTaskIds,
            )
        }

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
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _events = Channel<TasksNavEvent>(Channel.BUFFERED)
    val events: Flow<TasksNavEvent> = _events.receiveAsFlow()

    private var boundGateway: SavedGateway? = null
    private var activeRuntimeSessionId: String? = null
    private var activeRuntimeChannelId: String? = null
    private var lastStreamEventAtMs: Long = 0L
    private var turnPollJob: Job? = null
    private var modelsLoadJob: Job? = null
    private var roomSeenJob: Job? = null
    private var pageVisible: Boolean = false
    private var lastAutoTriggeredSlot: String? = null
    private var dismissedCheckinSlot: String? = null

    init {
        viewModelScope.launch {
            connection.state.collect { connState ->
                val connected = connState is ConnectionState.Connected
                if (!connected) {
                    activeRuntimeSessionId = null
                    activeRuntimeChannelId = null
                }
                _state.update { it.copy(gatewayConnected = connected) }
                if (connected && (_state.value.selectedTask != null || _state.value.coordinatorOpen) && _state.value.activeChannel?.isRoom != true && _state.value.modelPicker.catalog == null) {
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
    }

    override fun onCleared() {
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        roomSeenJob?.cancel()
    }

    fun bind(gateway: SavedGateway) {
        if (boundGateway == gateway && _state.value.config != null) return
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        roomSeenJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        lastAutoTriggeredSlot = null
        dismissedCheckinSlot = null
        boundGateway = gateway
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
        refresh()
    }

    fun setVisible(visible: Boolean) {
        val becameVisible = visible && !pageVisible
        pageVisible = visible
        if (becameVisible) {
            refreshCheckinEligibility()
            maybeRunAutoCheckin()
        }
    }

    fun refresh() {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
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
        val boardResult = directusApi.fetchBoard(gateway.gatewayUrl, includeDone = includeDone)
        if (boundGateway != gateway) return
        when (boardResult) {
            is ApiResult.SessionExpired -> _state.update { it.copy(loading = false, sessionExpired = true) }
            is ApiResult.Failed -> _state.update { it.copy(loading = false, error = boardResult.message) }
            is ApiResult.Unavailable -> _state.update { it.copy(loading = false, error = boardResult.message) }
            is ApiResult.InvalidCredentials, ApiResult.RateLimited -> _state.update { it.copy(loading = false, sessionExpired = true) }
            is ApiResult.Success -> {
                val board = boardResult.value
                val today = currentLocalEpochDay()
                _state.update { cur ->
                    val mergedConfig = board.coordinator?.let { coord ->
                        cur.config?.copy(coordinator = coord) ?: DirectusConfigStatus(configured = board.configured, coordinator = coord)
                    } ?: cur.config
                    val keptSelected = cur.selectedTaskId?.takeIf { sid -> board.tasks.any { it.id == sid } }
                    val selectedObj = keptSelected?.let { sid -> board.tasks.firstOrNull { it.id == sid } }
                    val safeChannelIdx = if (selectedObj != null && cur.activeChannelIdx < selectedObj.channels.size) cur.activeChannelIdx else 0
                    val keptFocused = cur.focusedProjectId?.takeIf { fid ->
                        fid == UNASSIGNED_PROJECT_BUCKET_ID || board.projects.any { it.id == fid && !it.archived }
                    }
                    val serverById = board.tasks.associateBy { it.id }
                    val keptLocalRead = cur.localReadTaskIds.filterTo(HashSet()) { id ->
                        serverById[id]?.replyStatus?.hasUnreadReply == true
                    }
                    cur.copy(
                        loading = false,
                        error = null,
                        config = mergedConfig,
                        projects = board.projects,
                        tasks = board.tasks,
                        todayEpochDay = today,
                        focusedProjectId = keptFocused,
                        selectedTaskId = if (cur.coordinatorOpen) null else keptSelected,
                        activeChannelIdx = safeChannelIdx,
                        localReadTaskIds = keptLocalRead,
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
                    loadCoordinatorLog(gateway)
                } else {
                    _state.value.selectedTask?.let { task ->
                        loadChannelLog(gateway, task, _state.value.activeChannelIdx)
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

    fun selectTask(taskId: String?) {
        turnPollJob?.cancel()
        modelsLoadJob?.cancel()
        activeRuntimeSessionId = null
        activeRuntimeChannelId = null
        val targetTask = taskId?.let { id -> _state.value.tasks.firstOrNull { it.id == id } }
        val replyChannelId = targetTask?.replyStatus?.lastReplyChannelId
        val preferredChannelIdx = if (targetTask != null && replyChannelId != null) {
            targetTask.channels.indexOfFirst { it.id == replyChannelId }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
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

    fun openCoordinator(autoCheckinPrompt: Boolean = false) {
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
                val profile = when {
                    _state.value.coordinatorOpen -> _state.value.coordinatorConfig.profile.ifBlank { null }
                    else -> _state.value.activeChannel?.profile ?: _state.value.selectedTask?.assignedBots?.firstOrNull()
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
            is ApiResult.Success -> {
                val botLabel = channel.profile ?: "Hermes"
                val allMessages = res.value.messages
                val toolResults = allMessages
                    .filter { it.role == "tool" && !it.toolCallId.isNullOrBlank() }
                    .associate { it.toolCallId!! to it.content }
                val parsed = allMessages
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
                            profile = channel.profile,
                            attachments = attachments,
                            interaction = interaction,
                        )
                    }
                    .takeLast(30)
                parsed to null
            }
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
            cur.copy(
                channelLoading = false,
                channelMessages = merged,
                channelError = if (merged.isEmpty()) linesResult.second else null,
                localReadTaskIds = cur.localReadTaskIds + task.id,
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

    fun ackTaskReply(taskId: String) {
        val gateway = boundGateway ?: return
        val previous = _state.value.tasks.firstOrNull { it.id == taskId } ?: return
        val ackedIso = Instant.fromEpochMilliseconds(nowMs()).toString()
        _state.update { cur ->
            cur.copy(
                localAckedTaskIds = cur.localAckedTaskIds + taskId,
                localReadTaskIds = cur.localReadTaskIds + taskId,
                localUnreadTaskIds = cur.localUnreadTaskIds - taskId,
            )
        }
        replaceTask(
            previous.copy(
                context = (previous.context ?: TaskContext(taskId = taskId)).copy(replyAckedAt = ackedIso),
                replyStatus = previous.replyStatus?.copy(hasUnreadReply = false, awaitingReply = false)
                    ?: TaskReplyStatus(hasUnreadReply = false, awaitingReply = false),
            ),
        )
        viewModelScope.launch {
            previous.channels.forEach { ch ->
                markChannelSeen(gateway, ch, previous.replyStatus?.latestRoomSeq)
            }
            val res = directusApi.upsertContext(
                gateway.gatewayUrl,
                taskId,
                UpsertContextRequest(replyAckedAt = ackedIso),
            )
            if (boundGateway != gateway) return@launch
            when (res) {
                is ApiResult.Success -> {
                    _state.update { cur -> cur.copy(localAckedTaskIds = cur.localAckedTaskIds - taskId) }
                    replaceTask(res.value)
                }
                is ApiResult.Failed -> {
                    _state.update { cur ->
                        cur.copy(
                            localAckedTaskIds = cur.localAckedTaskIds - taskId,
                            actionFeedback = res.message,
                        )
                    }
                    replaceTask(previous)
                }
                is ApiResult.Unavailable -> {
                    _state.update { cur ->
                        cur.copy(
                            localAckedTaskIds = cur.localAckedTaskIds - taskId,
                            actionFeedback = res.message,
                        )
                    }
                    replaceTask(previous)
                }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited -> {
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
                loadChannelLog(gateway, created.first, newIdx)
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
                val roomId = "task-${task.id}"
                val roster = members.take(6).map { bot ->
                    RoomMemberInput(memberId = bot.name, profile = bot.name, handle = bot.name, displayName = bot.label)
                }
                val room = roomsApi.create(roomId = roomId, name = task.title.take(64), members = roster)
                val label = "Room (${members.joinToString(", ") { "@${it.name}" }})"
                val channel = TaskChannel(type = TaskChannel.TYPE_ROOM, id = room.roomId, label = label)
                val mergedChannels = (task.channels.filterNot { it.id == channel.id } + channel)
                val bots = (task.assignedBots + members.map { it.name }).distinct()
                val res = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(channels = mergedChannels, assignedBots = bots),
                )
                if (res is ApiResult.Success && boundGateway == gateway) {
                    replaceTask(res.value)
                    val idx = res.value.channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                    _state.update { it.copy(activeChannelIdx = idx) }
                    loadChannelLog(gateway, res.value, idx)
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
                localReadTaskIds = cur.localReadTaskIds + task.id,
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
                dispatchMessage(channel, visibleText, attachments)
                startTurnFallbackPoll(gateway, channel = channel, task = latestTask, channelIdx = idx)
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
                if (_state.value.selectedTaskId == latestTask.id) {
                    _state.update { it.copy(activeChannelIdx = idx, channelTurnRunning = true, channelTurnStatus = null) }
                }
                lastStreamEventAtMs = nowMs()
                dispatchMessage(channel, prompt)
                loadChannelLog(gateway, latestTask, idx, attachSession = false)
                startTurnFallbackPoll(gateway, channel = channel, task = latestTask, channelIdx = idx)
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
        val requestedStatus = selectedParams["status"] ?: selectedParams["target_status"]
        val normalizedStatus = requestedStatus?.lowercase()?.replace(' ', '_')
            ?.takeIf { it in setOf(DirectusTask.STATUS_TODO, DirectusTask.STATUS_IN_PROGRESS, DirectusTask.STATUS_BLOCKED, DirectusTask.STATUS_DONE) }
            ?: if (task.status == DirectusTask.STATUS_TODO) DirectusTask.STATUS_IN_PROGRESS else null

        val chosenBot = selectedParams["bot"]?.removePrefix("@")?.takeIf { it.isNotBlank() && ' ' !in it }
        val nextBots = if (chosenBot != null) (task.assignedBots + chosenBot).distinct() else task.assignedBots

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
            try {
                val active = _state.value.activeChannel?.takeIf { task.channels.any { ch -> ch.id == it.id } }
                    ?: createSessionChannel(gateway, optimistic, chosenBot ?: nextBots.firstOrNull())?.second

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
                    dispatchMessage(active, "[Task: ${task.title}] $directive")
                    val latest = _state.value.tasks.firstOrNull { it.id == task.id }
                        ?: (approveRes as? ApiResult.Success)?.value
                        ?: optimistic
                    val idx = latest.channels.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
                    if (latest.id !in _state.value.tasks.map { it.id }) {
                        // Task completed and hidden from active board; skip channel log reload.
                    } else {
                        loadChannelLog(gateway, latest, idx)
                    }
                }
                if (approveRes is ApiResult.Success && boundGateway == gateway) {
                    syncSessionLifecycleOnStatusChange(gateway, task, approveRes.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Approval failed") }
                refresh()
            } finally {
                markBusy(task.id, false)
            }
        }
    }

    fun openChannelFullscreen(task: DirectusTask, channel: TaskChannel) {
        viewModelScope.launch {
            if (channel.isRoom) {
                val room = runCatching { roomsApi.state(channel.id).room }.getOrNull()
                    ?: Room(roomId = channel.id, name = task.title)
                _events.send(TasksNavEvent.OpenRoom(room))
            } else {
                _events.send(TasksNavEvent.OpenSession(channel.id, task.title, channel.profile))
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(actionFeedback = null) }
    }

    fun consumeSessionExpired() {
        _state.update { it.copy(sessionExpired = false) }
    }

    private suspend fun createSessionChannel(
        gateway: SavedGateway,
        task: DirectusTask,
        profile: String?,
    ): Pair<DirectusTask, TaskChannel>? {
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: run {
            _state.update { it.copy(actionFeedback = "Not connected to gateway.") }
            return null
        }
        val project = task.projectId?.let { pid -> _state.value.projects.firstOrNull { it.id == pid } }
        val cwd = project?.workspaceCwd?.takeIf { it.isNotBlank() }
        val picks = _state.value.channelChatState
        val result = client.request(
            "session.create",
            buildJsonObject {
                profile?.let { put("profile", it) }
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
        val botName = profile ?: "Hermes"
        val channel = TaskChannel(
            type = TaskChannel.TYPE_SESSION,
            id = storedId,
            label = "Session (@$botName)",
            profile = profile,
            managed = true,
        )
        val mergedChannels = task.channels + channel
        val nextBots = if (profile != null) (task.assignedBots + profile).distinct() else task.assignedBots
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
        activeRuntimeSessionId = runtimeId ?: storedId
        activeRuntimeChannelId = storedId
        if (info != null) {
            _state.update { it.copy(channelChatState = it.channelChatState.withChannelInfo(info)) }
        }
        replaceTask(updated)
        dispatchStore?.syncTask(gateway.gatewayUrl, updated)
        dispatchStore?.notifyLifecycleChange(gateway.gatewayUrl, unarchivedIds = setOf(storedId))
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
        activeRuntimeSessionId = runtimeId
        activeRuntimeChannelId = channel.id
        val info = resumed["info"] as? JsonObject
        val running = resumed.bool("running") == true
        val openRequests = resumed["open_requests"].asObjects().mapNotNull { snapshot ->
            InputRequest.parse(
                id = snapshot.str("id") ?: return@mapNotNull null,
                method = snapshot.str("method") ?: return@mapNotNull null,
                params = snapshot["params"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
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
        val runtimeId = activeRuntimeSessionId ?: return
        if (event.sessionId != runtimeId) return
        val payload = event.payload as? JsonObject
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
            "message.complete", "turn.error", "session.error" -> {
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
            val keptLocalRead = cur.localReadTaskIds.filterTo(HashSet()) { id ->
                serverById[id]?.replyStatus?.hasUnreadReply == true
            }
            cur.copy(
                projects = board.projects,
                tasks = board.tasks,
                todayEpochDay = currentLocalEpochDay(),
                localReadTaskIds = keptLocalRead,
                localUnreadTaskIds = emptySet(),
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

        val activeInBoard = _state.value.tasks
            .filter { it.id != updatedTask.id && it.status != DirectusTask.STATUS_DONE }
            .flatMap { it.channels }
            .filter { it.isSession }
            .mapTo(HashSet()) { it.id }
        val activeInStore = store?.byGateway?.value?.get(gateway.gatewayUrl)?.values
            ?.filter { it.taskId != updatedTask.id && it.taskStatus != DirectusTask.STATUS_DONE }
            ?.mapTo(HashSet()) { it.sessionId }
            .orEmpty()
        val sharedActiveIds = activeInBoard + activeInStore

        val changedIds = LinkedHashSet<String>()
        var firstError: String? = null
        for (ch in managedChannels) {
            if (ch.id in sharedActiveIds) continue
            when (val res = sessionsApi.setArchived(gateway.gatewayUrl, ch.id, archived = movedToDone, profile = ch.profile)) {
                is ApiResult.Success -> changedIds += ch.id
                is ApiResult.Failed -> {
                    if (res.status == 404) {
                        if (movedToDone) changedIds += ch.id
                    } else if (firstError == null) {
                        firstError = res.message
                    }
                }
                else -> if (firstError == null) firstError = res.errorMessage
            }
        }
        store?.syncTask(gateway.gatewayUrl, updatedTask)
        store?.notifyLifecycleChange(
            gateway = gateway.gatewayUrl,
            archivedIds = if (movedToDone) changedIds else emptySet(),
            unarchivedIds = if (movedToDone) emptySet() else changedIds,
        )

        if (firstError != null && boundGateway == gateway) {
            _state.update { it.copy(actionFeedback = firstError) }
        }
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
        return runtimeId
    }

    private suspend fun dispatchMessage(
        channel: TaskChannel,
        text: String,
        attachments: List<OutgoingAttachment> = emptyList(),
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
}

private fun ChatState.withChannelInfo(info: JsonObject?): ChatState {
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

private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.asObjects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
