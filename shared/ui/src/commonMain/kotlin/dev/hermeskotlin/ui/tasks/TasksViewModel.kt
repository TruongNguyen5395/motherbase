package dev.hermeskotlin.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.chat.formatActionCardReply
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectsApi
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.rooms.RoomLine
import dev.hermeskotlin.core.rooms.RoomMemberInput
import dev.hermeskotlin.core.rooms.RoomsApi
import dev.hermeskotlin.core.rooms.roomLines
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.tasks.ApproveTaskRequest
import dev.hermeskotlin.core.tasks.CreateProjectRequest
import dev.hermeskotlin.core.tasks.CreateTaskRequest
import dev.hermeskotlin.core.tasks.DirectusConfigStatus
import dev.hermeskotlin.core.tasks.DirectusConfigUpdate
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.core.tasks.UpdateTaskRequest
import dev.hermeskotlin.core.tasks.UpsertContextRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
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
    val selectedTaskId: String? = null,
    val activeChannelIdx: Int = 0,
    val channelLoading: Boolean = false,
    val channelMessages: List<ChannelChatLine> = emptyList(),
    val channelError: String? = null,
    val busyTaskIds: Set<String> = emptySet(),
    val sendingToChannel: Boolean = false,
    val actionFeedback: String? = null,
) {
    val selectedTask: DirectusTask?
        get() = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }

    val activeChannel: TaskChannel?
        get() = selectedTask?.channels?.getOrNull(activeChannelIdx) ?: selectedTask?.channels?.firstOrNull()

    val priorityTasks: List<DirectusTask>
        get() = tasks.filter { it.agentState == TaskContext.STATE_NEEDS_YOU }
}

@OptIn(ExperimentalUuidApi::class)
class TasksViewModel(
    private val directusApi: DirectusTasksApi,
    private val sessionsApi: SessionsApi,
    private val roomsApi: RoomsApi,
    private val projectsApi: ProjectsApi,
    private val botsApi: BotsApi,
    private val connection: GatewayConnection,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val _events = Channel<TasksNavEvent>(Channel.BUFFERED)
    val events: Flow<TasksNavEvent> = _events.receiveAsFlow()

    private var boundGateway: SavedGateway? = null

    fun bind(gateway: SavedGateway) {
        if (boundGateway == gateway && _state.value.config != null) return
        boundGateway = gateway
        _state.update {
            TasksUiState(
                loading = true,
                showPriorityStrip = it.showPriorityStrip,
                includeDone = it.includeDone,
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
                _state.update { cur ->
                    val keptSelected = cur.selectedTaskId?.takeIf { sid -> board.tasks.any { it.id == sid } }
                    val selectedObj = keptSelected?.let { sid -> board.tasks.firstOrNull { it.id == sid } }
                    val safeChannelIdx = if (selectedObj != null && cur.activeChannelIdx < selectedObj.channels.size) cur.activeChannelIdx else 0
                    cur.copy(
                        loading = false,
                        error = null,
                        projects = board.projects,
                        tasks = board.tasks,
                        selectedTaskId = keptSelected,
                        activeChannelIdx = safeChannelIdx,
                    )
                }
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

    fun selectTask(taskId: String?) {
        _state.update {
            it.copy(
                selectedTaskId = taskId,
                activeChannelIdx = 0,
                channelMessages = emptyList(),
                channelError = null,
            )
        }
        val gateway = boundGateway ?: return
        val task = _state.value.selectedTask ?: return
        viewModelScope.launch { loadChannelLog(gateway, task, 0) }
    }

    fun selectChannel(index: Int) {
        val task = _state.value.selectedTask ?: return
        if (index !in task.channels.indices) return
        _state.update { it.copy(activeChannelIdx = index, channelMessages = emptyList(), channelError = null) }
        val gateway = boundGateway ?: return
        viewModelScope.launch { loadChannelLog(gateway, task, index) }
    }

    fun refreshActiveChannel() {
        val gateway = boundGateway ?: return
        val task = _state.value.selectedTask ?: return
        val idx = _state.value.activeChannelIdx
        viewModelScope.launch { loadChannelLog(gateway, task, idx) }
    }

    private suspend fun loadChannelLog(gateway: SavedGateway, task: DirectusTask, channelIdx: Int) {
        val channel = task.channels.getOrNull(channelIdx) ?: run {
            _state.update { it.copy(channelLoading = false, channelMessages = emptyList(), channelError = null) }
            return
        }
        _state.update { it.copy(channelLoading = true, channelError = null) }
        val linesResult: Pair<List<ChannelChatLine>, String?> = if (channel.isRoom) {
            try {
                val roomState = roomsApi.state(channel.id)
                val page = roomsApi.log(channel.id, sinceSeq = 0, limit = 40)
                val parsed = roomLines(page.events, roomState.room.members)
                    .filterIsInstance<RoomLine.Message>()
                    .takeLast(20)
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
            when (val res = sessionsApi.messages(gateway.gatewayUrl, channel.id, limit = 30, profile = channel.profile)) {
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
                        .takeLast(20)
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
        _state.update {
            it.copy(
                channelLoading = false,
                channelMessages = linesResult.first,
                channelError = linesResult.second,
            )
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
            } else if (res is ApiResult.Failed) {
                _state.update { it.copy(actionFeedback = res.message) }
            }
        }
    }

    fun updateTaskStatus(taskId: String, status: String) {
        val gateway = boundGateway ?: return
        viewModelScope.launch {
            val res = directusApi.updateTask(gateway.gatewayUrl, taskId, UpdateTaskRequest(status = status))
            if (res is ApiResult.Success && boundGateway == gateway) {
                if (status == DirectusTask.STATUS_DONE && !_state.value.includeDone) {
                    _state.update { cur ->
                        cur.copy(
                            tasks = cur.tasks.filterNot { it.id == taskId },
                            selectedTaskId = if (cur.selectedTaskId == taskId) null else cur.selectedTaskId,
                        )
                    }
                } else {
                    replaceTask(res.value)
                }
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
        viewModelScope.launch {
            _state.update { it.copy(sendingToChannel = true) }
            try {
                val channel = _state.value.activeChannel?.takeIf { task.channels.any { ch -> ch.id == it.id } }
                    ?: createSessionChannel(gateway, task, task.assignedBots.firstOrNull())?.second
                    ?: return@launch
                dispatchMessage(channel, clean)
                val latestTask = _state.value.tasks.firstOrNull { it.id == task.id } ?: task
                val idx = latestTask.channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                _state.update { it.copy(activeChannelIdx = idx) }
                loadChannelLog(gateway, latestTask, idx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Send failed") }
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
                val channel = _state.value.activeChannel?.takeIf { task.channels.any { ch -> ch.id == it.id } }
                    ?: createSessionChannel(gateway, task, task.assignedBots.firstOrNull())?.second
                    ?: return@launch

                val ctxRes = directusApi.upsertContext(
                    gateway.gatewayUrl,
                    task.id,
                    UpsertContextRequest(agentState = TaskContext.STATE_QUEUED),
                )
                if (ctxRes is ApiResult.Success && boundGateway == gateway) replaceTask(ctxRes.value)

                val project = task.projectId?.let { pid -> _state.value.projects.firstOrNull { it.id == pid } }
                val prompt = buildString {
                    append("Prepare an actionable support plan for Directus task ${task.id} (${task.title}). ")
                    if (project != null) append("Project: ${project.name}. ")
                    if (!task.description.isNullOrBlank()) append("Context: ${task.description}. ")
                    append("Read recent tweak_deltas via `directus_task_board`, then save 2-3 inline parameter chips and action options once using `directus_stage_plan` (force=true).")
                }
                dispatchMessage(channel, prompt)
                val latestTask = _state.value.tasks.firstOrNull { it.id == task.id } ?: task
                val idx = latestTask.channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                loadChannelLog(gateway, latestTask, idx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(actionFeedback = e.message ?: "Could not start plan preparation") }
            } finally {
                markBusy(task.id, false)
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
                    replaceTask(approveRes.value)
                }

                if (active != null) {
                    dispatchMessage(active, "[Task: ${task.title}] $directive")
                    val latest = _state.value.tasks.firstOrNull { it.id == task.id } ?: optimistic
                    val idx = latest.channels.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
                    loadChannelLog(gateway, latest, idx)
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
        )
        val mergedChannels = task.channels + channel
        val nextBots = if (profile != null) (task.assignedBots + profile).distinct() else task.assignedBots
        val res = directusApi.upsertContext(
            gateway.gatewayUrl,
            task.id,
            UpsertContextRequest(channels = mergedChannels, assignedBots = nextBots),
        )
        return if (res is ApiResult.Success) res.value to channel else task.copy(
            context = (task.context ?: TaskContext(taskId = task.id)).copy(channels = mergedChannels, assignedBots = nextBots),
        ) to channel
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
