package dev.hermeskotlin.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.chat.formatActionCardReply
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.errorMessage
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectsApi
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.rooms.RoomLine
import dev.hermeskotlin.core.rooms.RoomMemberInput
import dev.hermeskotlin.core.rooms.RoomsApi
import dev.hermeskotlin.core.rooms.roomLines
import dev.hermeskotlin.core.rpc.GatewayEvent
import dev.hermeskotlin.core.rpc.ServerRequest
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.tasks.ApproveTaskRequest
import dev.hermeskotlin.core.tasks.CreateProjectRequest
import dev.hermeskotlin.core.tasks.CreateTaskRequest
import dev.hermeskotlin.core.tasks.DirectusConfigStatus
import dev.hermeskotlin.core.tasks.DirectusConfigUpdate
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import dev.hermeskotlin.core.tasks.DispatchSessionsStore
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.core.tasks.UpdateTaskRequest
import dev.hermeskotlin.core.tasks.UpsertContextRequest
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface TasksNavEvent {
    data class OpenSession(val sessionId: String, val title: String, val profile: String?) : TasksNavEvent
    data class OpenRoom(val room: Room) : TasksNavEvent
}

data class ChannelChatLine(
    val key: String,
    val speaker: String?,
    val text: String,
    val fromUser: Boolean,
    val profile: String? = null,
    val streaming: Boolean = false,
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
    val includeDone: Boolean = false,
    val showPriorityStrip: Boolean = true,
    val focusedProjectId: String? = null,
    val focusGrouping: TaskFocusGrouping = TaskFocusGrouping.WORKSTREAM,
    val expandedProjectIds: Set<String> = emptySet(),
    val collapsedWorkstreamKeys: Set<String> = emptySet(),
    val todayEpochDay: Long = currentLocalEpochDay(),
    val selectedTaskId: String? = null,
    val activeChannelIdx: Int = 0,
    val channelLoading: Boolean = false,
    val channelMessages: List<ChannelChatLine> = emptyList(),
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

    val activeChannel: TaskChannel?
        get() = selectedTask?.channels?.getOrNull(activeChannelIdx) ?: selectedTask?.channels?.firstOrNull()

    val priorityTasks: List<DirectusTask>
        get() = tasks.filter { it.agentState == TaskContext.STATE_NEEDS_YOU }

    internal val attentionSplit: AttentionSplit
        get() = buildAttentionSplit(tasks, todayEpochDay)
}

@OptIn(ExperimentalUuidApi::class)
class TasksViewModel(
    private val directusApi: DirectusTasksApi,
    private val sessionsApi: SessionsApi,
    private val roomsApi: RoomsApi,
    private val projectsApi: ProjectsApi,
    private val botsApi: BotsApi,
    private val connection: GatewayConnection,
    private val dispatchStore: DispatchSessionsStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _events = Channel<TasksNavEvent>(Channel.BUFFERED)
    val events: Flow<TasksNavEvent> = _events.receiveAsFlow()

    private var boundGateway: SavedGateway? = null
    private var activeRuntimeSessionId: String? = null
    private var lastStreamEventAtMs: Long = 0L
    private var turnPollJob: Job? = null

    init {
        viewModelScope.launch {
            connection.state.collect { connState ->
                _state.update { it.copy(gatewayConnected = connState is ConnectionState.Connected) }
            }
        }
        viewModelScope.launch {
            connection.events.collect { event -> handleGatewayEvent(event) }
        }
        viewModelScope.launch {
            connection.serverRequests.collect { request -> handleServerRequest(request) }
        }
    }

    override fun onCleared() {
        turnPollJob?.cancel()
    }

    fun bind(gateway: SavedGateway) {
        if (boundGateway == gateway && _state.value.config != null) return
        turnPollJob?.cancel()
        activeRuntimeSessionId = null
        boundGateway = gateway
        _state.update {
            TasksUiState(
                loading = true,
                showPriorityStrip = it.showPriorityStrip,
                includeDone = it.includeDone,
                focusGrouping = it.focusGrouping,
                todayEpochDay = currentLocalEpochDay(),
                gatewayConnected = connection.state.value is ConnectionState.Connected,
            )
        }
        refresh()
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
                    val keptSelected = cur.selectedTaskId?.takeIf { sid -> board.tasks.any { it.id == sid } }
                    val selectedObj = keptSelected?.let { sid -> board.tasks.firstOrNull { it.id == sid } }
                    val safeChannelIdx = if (selectedObj != null && cur.activeChannelIdx < selectedObj.channels.size) cur.activeChannelIdx else 0
                    val keptFocused = cur.focusedProjectId?.takeIf { fid ->
                        fid == UNASSIGNED_PROJECT_BUCKET_ID || board.projects.any { it.id == fid && !it.archived }
                    }
                    cur.copy(
                        loading = false,
                        error = null,
                        projects = board.projects,
                        tasks = board.tasks,
                        todayEpochDay = today,
                        focusedProjectId = keptFocused,
                        selectedTaskId = keptSelected,
                        activeChannelIdx = safeChannelIdx,
                    )
                }
                dispatchStore?.syncBoard(gateway.gatewayUrl, board.tasks, includeDone = includeDone)
                _state.value.selectedTask?.let { task ->
                    loadChannelLog(gateway, task, _state.value.activeChannelIdx)
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
        }
    }

    fun saveConfig(url: String, token: String) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(savingConfig = true, configError = null) }
            when (val res = directusApi.saveConfig(gateway.gatewayUrl, DirectusConfigUpdate(url = url.trim(), token = token.trim().ifEmpty { null }))) {
                is ApiResult.Success -> {
                    if (boundGateway != gateway) return@launch
                    _state.update { it.copy(savingConfig = false, config = res.value, configError = null) }
                    refresh()
                }
                is ApiResult.Failed -> if (boundGateway == gateway) _state.update { it.copy(savingConfig = false, configError = res.message) }
                is ApiResult.Unavailable -> if (boundGateway == gateway) _state.update { it.copy(savingConfig = false, configError = res.message) }
                is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited ->
                    if (boundGateway == gateway) _state.update { it.copy(savingConfig = false, sessionExpired = true) }
            }
        }
    }

    fun togglePriorityStrip(enabled: Boolean) {
        _state.update { it.copy(showPriorityStrip = enabled) }
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
        activeRuntimeSessionId = null
        _state.update {
            it.copy(
                selectedTaskId = taskId,
                activeChannelIdx = 0,
                channelMessages = emptyList(),
                channelError = null,
                channelTurnRunning = false,
                channelTurnStatus = null,
                channelInputRequests = emptyList(),
            )
        }
        val gateway = boundGateway ?: return
        val task = _state.value.selectedTask ?: return
        viewModelScope.launch { loadChannelLog(gateway, task, 0) }
    }

    fun selectChannel(index: Int) {
        val task = _state.value.selectedTask ?: return
        if (index !in task.channels.indices) return
        turnPollJob?.cancel()
        activeRuntimeSessionId = null
        _state.update {
            it.copy(
                activeChannelIdx = index,
                channelMessages = emptyList(),
                channelError = null,
                channelTurnRunning = false,
                channelTurnStatus = null,
                channelInputRequests = emptyList(),
            )
        }
        val gateway = boundGateway ?: return
        viewModelScope.launch { loadChannelLog(gateway, task, index) }
    }

    fun refreshActiveChannel() {
        val gateway = boundGateway ?: return
        val task = _state.value.selectedTask ?: return
        val idx = _state.value.activeChannelIdx
        viewModelScope.launch { loadChannelLog(gateway, task, idx) }
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
        val linesResult: Pair<List<ChannelChatLine>, String?> = if (channel.isRoom) {
            try {
                val roomState = roomsApi.state(channel.id)
                val page = roomsApi.log(channel.id, sinceSeq = 0, limit = 50)
                val parsed = roomLines(page.events, roomState.room.members)
                    .filterIsInstance<RoomLine.Message>()
                    .takeLast(30)
                    .map { msg ->
                        ChannelChatLine(
                            key = "room-${msg.seq}-${msg.eventId}",
                            speaker = if (msg.fromUser) null else msg.speaker ?: msg.profile,
                            text = msg.text,
                            fromUser = msg.fromUser,
                            profile = msg.profile,
                        )
                    }
                parsed to null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList<ChannelChatLine>() to (e.message ?: "Could not read room log")
            }
        } else {
            when (val res = sessionsApi.messages(gateway.gatewayUrl, channel.id, limit = 40, profile = channel.profile)) {
                is ApiResult.Success -> {
                    val botLabel = channel.profile ?: "Hermes"
                    val parsed = res.value.messages
                        .filterNot { it.isHidden || it.role == "tool" || it.role == "system" }
                        .mapNotNull { msg ->
                            val txt = msg.text.trim()
                            if (txt.isEmpty()) null else ChannelChatLine(
                                key = "sess-${msg.id ?: txt.hashCode()}",
                                speaker = if (msg.role == "user") null else botLabel,
                                text = txt,
                                fromUser = msg.role == "user",
                                profile = channel.profile,
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
            )
        }
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
                    _state.update { cur ->
                        cur.copy(
                            tasks = listOf(created) + cur.tasks,
                            selectedTaskId = created.id,
                            activeChannelIdx = 0,
                            channelMessages = emptyList(),
                        )
                    }
                    dispatchStore?.syncTask(gateway.gatewayUrl, created)
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
        if (clean.isEmpty()) return
        val optimisticKey = "user-${Uuid.random()}"
        val optimisticLine = ChannelChatLine(
            key = optimisticKey,
            speaker = null,
            text = clean,
            fromUser = true,
        )
        _state.update { cur ->
            val sameChat = cur.selectedTaskId == task.id
            cur.copy(
                sendingToChannel = true,
                channelError = null,
                channelMessages = if (sameChat) cur.channelMessages + optimisticLine else cur.channelMessages,
                channelTurnRunning = if (sameChat) true else cur.channelTurnRunning,
                channelTurnStatus = if (sameChat) null else cur.channelTurnStatus,
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
                lastStreamEventAtMs = Clock.System.now().toEpochMilliseconds()
                dispatchMessage(channel, clean)
                startTurnFallbackPoll(gateway, latestTask, idx, channel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
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
                lastStreamEventAtMs = Clock.System.now().toEpochMilliseconds()
                dispatchMessage(channel, prompt)
                loadChannelLog(gateway, latestTask, idx, attachSession = false)
                startTurnFallbackPoll(gateway, latestTask, idx, channel)
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
        val result = client.request(
            "session.create",
            buildJsonObject {
                profile?.let { put("profile", it) }
                put("source", "desktop")
                put("cols", 80)
                cwd?.let { put("cwd", it) }
            },
        ) as? JsonObject
        val runtimeId = (result?.get("session_id") as? JsonPrimitive)?.contentOrNull
        val storedId = (result?.get("stored_session_id") as? JsonPrimitive)?.contentOrNull ?: runtimeId ?: return null
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
                channelTurnRunning = running || openRequests.isNotEmpty(),
                channelInputRequests = openRequests,
                channelMessages = nextMsgs,
            )
        }
    }

    private fun handleGatewayEvent(event: GatewayEvent) {
        val runtimeId = activeRuntimeSessionId ?: return
        if (event.sessionId != runtimeId) return
        val payload = event.payload as? JsonObject
        when (event.type) {
            "message.start" -> {
                lastStreamEventAtMs = Clock.System.now().toEpochMilliseconds()
                _state.update { it.copy(channelTurnRunning = true, channelError = null, channelTurnStatus = it.channelTurnStatus ?: "Thinking…") }
            }
            "thinking.delta" -> {
                lastStreamEventAtMs = Clock.System.now().toEpochMilliseconds()
                payload.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { frame ->
                    _state.update { it.copy(channelTurnRunning = true, channelTurnStatus = frame) }
                }
            }
            "tool.start" -> {
                lastStreamEventAtMs = Clock.System.now().toEpochMilliseconds()
                val toolName = payload.str("name") ?: "tool"
                _state.update { it.copy(channelTurnRunning = true, channelTurnStatus = toolName) }
            }
            "message.delta", "message.interim" -> {
                if (event.type == "message.interim" && payload.bool("already_streamed") == true) return
                val chunk = payload.str("text") ?: return
                if (chunk.isEmpty()) return
                lastStreamEventAtMs = Clock.System.now().toEpochMilliseconds()
                val botLabel = _state.value.activeChannel?.profile ?: "Hermes"
                val added = if (event.type == "message.interim") "$chunk\n\n" else chunk
                _state.update { cur ->
                    val msgs = cur.channelMessages
                    val last = msgs.lastOrNull()
                    val next = if (last != null && !last.fromUser && last.streaming) {
                        msgs.dropLast(1) + last.copy(text = last.text + added)
                    } else {
                        msgs + ChannelChatLine(
                            key = "stream-${cur.selectedTaskId}-${msgs.size}",
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
        task: DirectusTask,
        channelIdx: Int,
        channel: TaskChannel,
    ) {
        turnPollJob?.cancel()
        turnPollJob = viewModelScope.launch {
            repeat(60) {
                delay(3_000)
                if (boundGateway != gateway ||
                    _state.value.selectedTaskId != task.id ||
                    _state.value.activeChannel?.id != channel.id ||
                    connection.state.value !is ConnectionState.Connected
                ) {
                    return@launch
                }
                val sinceLastEvent = Clock.System.now().toEpochMilliseconds() - lastStreamEventAtMs
                if (channel.isRoom || sinceLastEvent >= 3_500) {
                    loadChannelLog(gateway, task, channelIdx, attachSession = false)
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
            cur.copy(
                projects = board.projects,
                tasks = board.tasks,
                todayEpochDay = currentLocalEpochDay(),
            )
        }
        dispatchStore?.syncBoard(gateway.gatewayUrl, board.tasks, includeDone = includeDone)
    }

    /**
     * Keeps gateway session archive state in sync with Dispatch task transitions:
     * - Moving into `Done` archives auto-managed session channels unless still referenced by another non-`Done` task.
     * - Reopening out of `Done` unarchives those session channels so they return cleanly to the `DISPATCH` group.
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

        val changedIds = LinkedHashSet<String>()
        var firstError: String? = null
        if (movedToDone) {
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

            for (ch in managedChannels) {
                if (ch.id in sharedActiveIds) continue
                when (val res = sessionsApi.setArchived(gateway.gatewayUrl, ch.id, archived = true, profile = ch.profile)) {
                    is ApiResult.Success -> changedIds += ch.id
                    is ApiResult.Failed -> {
                        if (res.status == 404) changedIds += ch.id
                        else if (firstError == null) firstError = res.message
                    }
                    else -> if (firstError == null) firstError = res.errorMessage
                }
            }
            store?.syncTask(gateway.gatewayUrl, updatedTask)
            store?.notifyLifecycleChange(gateway.gatewayUrl, archivedIds = changedIds)
        } else {
            for (ch in managedChannels) {
                when (val res = sessionsApi.setArchived(gateway.gatewayUrl, ch.id, archived = false, profile = ch.profile)) {
                    is ApiResult.Success -> changedIds += ch.id
                    is ApiResult.Failed -> if (res.status != 404 && firstError == null) firstError = res.message
                    else -> if (firstError == null) firstError = res.errorMessage
                }
            }
            store?.syncTask(gateway.gatewayUrl, updatedTask)
            store?.notifyLifecycleChange(gateway.gatewayUrl, unarchivedIds = changedIds)
        }

        if (firstError != null && boundGateway == gateway) {
            _state.update { it.copy(actionFeedback = firstError) }
        }
    }

    private suspend fun dispatchMessage(channel: TaskChannel, text: String) {
        if (channel.isRoom) {
            roomsApi.send(
                roomId = channel.id,
                text = text,
                threadId = "main",
                eventId = "mb-${Uuid.random()}",
            )
            return
        }
        val client = (connection.state.value as? ConnectionState.Connected)?.client
            ?: error("Not connected to gateway.")
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
        val runtimeId = (resumed?.get("session_id") as? JsonPrimitive)?.contentOrNull ?: error("Could not resume session.")
        activeRuntimeSessionId = runtimeId
        client.request(
            "prompt.submit",
            buildJsonObject {
                put("session_id", runtimeId)
                put("text", text)
            },
        )
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

private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.asObjects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
