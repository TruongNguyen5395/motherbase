package dev.hermeskotlin.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.calendar.CalendarAccount
import dev.hermeskotlin.core.calendar.CalendarApi
import dev.hermeskotlin.core.calendar.CalendarCacheStore
import dev.hermeskotlin.core.calendar.CalendarDate
import dev.hermeskotlin.core.calendar.CalendarEvent
import dev.hermeskotlin.core.calendar.CalendarHealthStatus
import dev.hermeskotlin.core.calendar.CalendarInfo
import dev.hermeskotlin.core.calendar.CalendarLayoutEngine
import dev.hermeskotlin.core.calendar.CalendarOfflineSnapshot
import dev.hermeskotlin.core.calendar.CalendarSettings
import dev.hermeskotlin.core.calendar.ConnectCalDavRequest
import dev.hermeskotlin.core.calendar.ConnectIcsRequest
import dev.hermeskotlin.core.calendar.CreateEventRequest
import dev.hermeskotlin.core.calendar.CreateTimeBlockRequest
import dev.hermeskotlin.core.calendar.DispatchCalendarOverview
import dev.hermeskotlin.core.calendar.DispatchCalendarTask
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.EventAttendee
import dev.hermeskotlin.core.calendar.FreeSlot
import dev.hermeskotlin.core.calendar.GoogleAuthUrlRequest
import dev.hermeskotlin.core.calendar.GoogleExchangeRequest
import dev.hermeskotlin.core.calendar.LarkAuthUrlRequest
import dev.hermeskotlin.core.calendar.LarkExchangeRequest
import dev.hermeskotlin.core.calendar.MicrosoftAuthUrlRequest
import dev.hermeskotlin.core.calendar.MicrosoftExchangeRequest
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.UpdateCalendarRequest
import dev.hermeskotlin.core.calendar.UpdateEventRequest
import dev.hermeskotlin.core.calendar.UpdateTimeBlockRequest
import dev.hermeskotlin.core.calendar.calendarAddMonths
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.calendar.calendarNowZoned
import dev.hermeskotlin.core.calendar.calendarToUtcIso
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClock24
import dev.hermeskotlin.core.calendar.plusDays
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.rpc.GatewayEvent
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import dev.hermeskotlin.core.tasks.DispatchSessionsStore
import dev.hermeskotlin.ui.tasks.ChannelChatLine
import dev.hermeskotlin.ui.tasks.parseSessionChatLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

enum class CalendarViewMode(val apiKey: String) {
    DAY(CalendarSettings.VIEW_DAY),
    THREE_DAYS("three_days"),
    FOUR_DAYS(CalendarSettings.VIEW_FOUR_DAYS),
    WEEK(CalendarSettings.VIEW_WEEK),
    MONTH(CalendarSettings.VIEW_MONTH),
    SCHEDULE(CalendarSettings.VIEW_SCHEDULE),
    ;

    val gridDayCount: Int
        get() = when (this) {
            DAY -> 1
            THREE_DAYS -> 3
            FOUR_DAYS -> 4
            WEEK -> 7
            MONTH, SCHEDULE -> 0
        }

    companion object {
        fun fromSettingsKey(key: String?): CalendarViewMode = when (key) {
            CalendarSettings.VIEW_DAY -> DAY
            "three_days" -> THREE_DAYS
            CalendarSettings.VIEW_WEEK -> WEEK
            CalendarSettings.VIEW_MONTH -> MONTH
            CalendarSettings.VIEW_SCHEDULE -> SCHEDULE
            else -> FOUR_DAYS
        }
    }
}

data class EventEditorDraft(
    val existingOccurrenceId: String? = null,
    val calendarId: String,
    val title: String = "",
    val dateIso: String,
    val endDateIso: String = dateIso,
    val startTime: String = "09:00",
    val endTime: String = "10:00",
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
    val attendeesText: String = "",
    val busy: Boolean = true,
    val sendUpdates: Boolean = false,
    val hasExternalAttendees: Boolean = false,
)

data class TaskBlockDraft(
    val task: DispatchCalendarTask,
    val existingBlockId: String? = null,
    val label: String = task.title,
    val dateIso: String,
    val startTime: String = "09:00",
    val durationMinutes: Int = (task.estimatedMinutes ?: 60).coerceIn(15, 480),
    val targetCalendarId: String? = null,
)

data class OAuthPendingState(
    val provider: String,
    val authUrl: String,
    val redirectUri: String,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val tenantId: String = "common",
    val region: String = "intl",
    val label: String? = null,
    val ensureTaskSubcalendar: Boolean = true,
)

data class CalendarUiState(
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val mutating: Boolean = false,
    val error: String? = null,
    val feedback: String? = null,
    val pluginMissing: Boolean = false,
    val sessionExpired: Boolean = false,
    val gatewayConnected: Boolean = true,
    val viewMode: CalendarViewMode = CalendarViewMode.FOUR_DAYS,
    val anchorDate: CalendarDate = calendarNowZoned().date,
    val todayDate: CalendarDate = calendarNowZoned().date,
    val currentMinuteOfDay: Int = calendarNowZoned().minuteOfDay,
    val showWeekends: Boolean = true,
    val showDeclined: Boolean = false,
    val showCompletedTasks: Boolean = false,
    val use24Hour: Boolean = false,
    val health: CalendarHealthStatus? = null,
    val settings: CalendarSettings = CalendarSettings(),
    val accounts: List<CalendarAccount> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val loadedRange: Pair<String, String>? = null,
    val dispatchOverview: DispatchCalendarOverview = DispatchCalendarOverview(),
    val proposals: List<ScheduleProposal> = emptyList(),
    val proposalStatuses: Map<String, String> = emptyMap(),
    val busyProposalIds: Set<String> = emptySet(),
    val pendingConflict: PendingProposalConflict? = null,
    val freeSlots: List<FreeSlot> = emptyList(),
    val findingFreeSlots: Boolean = false,
    val selectedEvent: CalendarEvent? = null,
    val editingEventDraft: EventEditorDraft? = null,
    val schedulingTask: TaskBlockDraft? = null,
    val connectDialogOpen: Boolean = false,
    val settingsDialogOpen: Boolean = false,
    val oauthPending: OAuthPendingState? = null,
    val oauthError: String? = null,
    val oauthBusy: Boolean = false,
    val bots: List<Bot> = emptyList(),
    val coordinatorSessionId: String? = null,
    val coordinatorProfile: String = Bot.DEFAULT,
    val agentMessages: List<ChannelChatLine> = emptyList(),
    val agentLoading: Boolean = false,
    val agentTurnRunning: Boolean = false,
    val agentTurnStatus: String? = null,
    val agentError: String? = null,
    val agentInputRequests: List<InputRequest> = emptyList(),
)

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class CalendarViewModel(
    private val calendarApi: CalendarApi,
    private val connection: GatewayConnection,
    private val cacheStore: CalendarCacheStore? = null,
    private val directusApi: DirectusTasksApi? = null,
    private val sessionsApi: SessionsApi? = null,
    private val botsApi: BotsApi? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private var boundGateway: SavedGateway? = null
    private var visible: Boolean = false
    private var clockJob: Job? = null
    private var refreshEventsJob: Job? = null
    private var coordinatorRuntimeId: String? = null
    private var fallbackPollJob: Job? = null
    private var lastStreamEventAtMs: Long = 0L

    private val proposalCoordinator = ScheduleProposalCoordinator(
        calendarApi = calendarApi,
        scope = viewModelScope,
        onError = { msg -> _state.update { it.copy(error = msg) } },
        onSessionExpired = { _state.update { it.copy(sessionExpired = true) } },
        onProposalChanged = {
            boundGateway?.let { gw -> refreshWorkspaceSilently(gw) }
        },
    )

    init {
        viewModelScope.launch {
            connection.state.collect { connState ->
                val connected = connState is ConnectionState.Connected
                if (!connected) coordinatorRuntimeId = null
                _state.update { it.copy(gatewayConnected = connected) }
                val gw = boundGateway
                if (connected && visible && gw != null && _state.value.health == null) {
                    refreshAll(gw, syncRemote = false)
                }
            }
        }
        viewModelScope.launch {
            proposalCoordinator.statuses.collect { map ->
                _state.update { it.copy(proposalStatuses = map) }
            }
        }
        viewModelScope.launch {
            proposalCoordinator.busyProposalIds.collect { busy ->
                _state.update { it.copy(busyProposalIds = busy) }
            }
        }
        viewModelScope.launch {
            proposalCoordinator.pendingConflict.collect { conflict ->
                _state.update { it.copy(pendingConflict = conflict) }
            }
        }
        viewModelScope.launch {
            connection.events.collect { event -> handleGatewayEvent(event) }
        }
        viewModelScope.launch {
            connection.serverRequests.collect { request ->
                val runtimeId = coordinatorRuntimeId ?: return@collect
                if (request.sessionId != runtimeId) return@collect
                val parsed = InputRequest.parse(request.id, request.method, request.params) ?: return@collect
                _state.update { cur ->
                    cur.copy(
                        agentInputRequests = cur.agentInputRequests.filterNot { it.id == parsed.id } + parsed,
                        agentTurnRunning = true,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        clockJob?.cancel()
        refreshEventsJob?.cancel()
        fallbackPollJob?.cancel()
    }

    fun bind(gateway: SavedGateway, isVisible: Boolean) {
        val prevGateway = boundGateway
        visible = isVisible
        if (isVisible) ensureClockRunning() else clockJob?.cancel()
        if (prevGateway == gateway) {
            if (isVisible && _state.value.health == null && !_state.value.loading) {
                viewModelScope.launch { refreshAll(gateway, syncRemote = false) }
            }
            return
        }
        boundGateway = gateway
        coordinatorRuntimeId = null
        fallbackPollJob?.cancel()
        proposalCoordinator.clear()
        val nowMoment = calendarNowZoned()
        _state.value = CalendarUiState(
            loading = true,
            gatewayConnected = connection.state.value is ConnectionState.Connected,
            anchorDate = nowMoment.date,
            todayDate = nowMoment.date,
            currentMinuteOfDay = nowMoment.minuteOfDay,
        )
        viewModelScope.launch {
            cacheStore?.load(gateway.gatewayUrl)?.let { snap ->
                if (boundGateway == gateway) {
                    applySnapshot(snap)
                }
            }
            refreshAll(gateway, syncRemote = false)
        }
    }

    private fun applySnapshot(snap: CalendarOfflineSnapshot) {
        val tz = snap.settings.timezone.ifBlank { null }
        val nowMoment = calendarNowZoned(tz)
        _state.update { cur ->
            cur.copy(
                loading = false,
                settings = snap.settings,
                viewMode = CalendarViewMode.fromSettingsKey(snap.settings.defaultView),
                showWeekends = snap.settings.showWeekends,
                showDeclined = snap.settings.showDeclined,
                showCompletedTasks = snap.settings.showCompletedTasks,
                accounts = snap.accounts,
                calendars = snap.calendars,
                events = snap.events,
                dispatchOverview = snap.dispatchOverview,
                todayDate = nowMoment.date,
                currentMinuteOfDay = nowMoment.minuteOfDay,
            )
        }
    }

    private fun ensureClockRunning() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (isActive) {
                val tz = _state.value.settings.timezone.ifBlank { null }
                val nowMoment = calendarNowZoned(tz)
                _state.update { cur ->
                    cur.copy(todayDate = nowMoment.date, currentMinuteOfDay = nowMoment.minuteOfDay)
                }
                delay(30_000L)
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, feedback = null) }
    }

    fun setViewMode(mode: CalendarViewMode) {
        if (_state.value.viewMode == mode) return
        _state.update { it.copy(viewMode = mode) }
        val gw = boundGateway ?: return
        ensureWindowLoaded(gw)
    }

    fun stepPeriod(delta: Int) {
        val cur = _state.value
        val next = when (cur.viewMode) {
            CalendarViewMode.DAY -> cur.anchorDate.plusDays(delta)
            CalendarViewMode.THREE_DAYS -> cur.anchorDate.plusDays(delta * 3)
            CalendarViewMode.FOUR_DAYS -> cur.anchorDate.plusDays(delta * 4)
            CalendarViewMode.WEEK -> cur.anchorDate.plusDays(delta * 7)
            CalendarViewMode.MONTH -> {
                val (y, m) = calendarAddMonths(cur.anchorDate.year, cur.anchorDate.month, delta)
                calendarDateFromIso("${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-01")
                    ?: cur.anchorDate
            }
            CalendarViewMode.SCHEDULE -> cur.anchorDate.plusDays(delta * 14)
        }
        selectDate(next)
    }

    fun goToday() {
        val tz = _state.value.settings.timezone.ifBlank { null }
        val today = calendarNowZoned(tz).date
        selectDate(today)
    }

    fun selectDate(date: CalendarDate) {
        _state.update { it.copy(anchorDate = date) }
        boundGateway?.let { ensureWindowLoaded(it) }
    }

    fun toggleShowWeekends() {
        val next = !_state.value.showWeekends
        _state.update { it.copy(showWeekends = next) }
        persistViewFlag { it.copy(showWeekends = next) }
    }

    fun toggleShowDeclined() {
        val next = !_state.value.showDeclined
        _state.update { it.copy(showDeclined = next) }
        persistViewFlag { it.copy(showDeclined = next) }
        boundGateway?.let { gw -> viewModelScope.launch { refreshEventsWindow(gw) } }
    }

    fun toggleShowCompletedTasks() {
        val next = !_state.value.showCompletedTasks
        _state.update { it.copy(showCompletedTasks = next) }
        persistViewFlag { it.copy(showCompletedTasks = next) }
        boundGateway?.let { gw -> viewModelScope.launch { refreshDispatchOverview(gw) } }
    }

    fun toggleUse24Hour() {
        _state.update { it.copy(use24Hour = !it.use24Hour) }
    }

    private fun persistViewFlag(transform: (CalendarSettings) -> CalendarSettings) {
        val gw = boundGateway ?: return
        val nextSettings = transform(_state.value.settings)
        _state.update { it.copy(settings = nextSettings) }
        viewModelScope.launch {
            calendarApi.updateSettings(gw.gatewayUrl, nextSettings)
        }
    }

    fun syncNow(accountId: String? = null) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null) }
            when (val res = calendarApi.sync(gw.gatewayUrl, accountId)) {
                is ApiResult.Success -> refreshAll(gw, syncRemote = false)
                is ApiResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
                else -> res.errorTextOrNull()?.let { msg -> _state.update { it.copy(error = msg) } }
            }
            _state.update { it.copy(syncing = false) }
        }
    }

    fun refresh() {
        val gw = boundGateway ?: return
        viewModelScope.launch { refreshAll(gw, syncRemote = false) }
    }

    private suspend fun refreshAll(gateway: SavedGateway, syncRemote: Boolean) {
        if (_state.value.accounts.isEmpty() && _state.value.events.isEmpty()) {
            _state.update { it.copy(loading = true, error = null) }
        }
        when (val healthRes = calendarApi.health(gateway.gatewayUrl)) {
            is ApiResult.SessionExpired, ApiResult.InvalidCredentials, ApiResult.RateLimited -> {
                if (boundGateway == gateway) _state.update { it.copy(loading = false, sessionExpired = true) }
                return
            }
            is ApiResult.Failed -> {
                if (boundGateway == gateway) {
                    val missing = healthRes.status == 404
                    _state.update {
                        it.copy(
                            loading = false,
                            pluginMissing = missing,
                            error = if (missing) null else healthRes.message,
                        )
                    }
                }
                return
            }
            is ApiResult.Unavailable -> {
                if (boundGateway == gateway) {
                    _state.update { it.copy(loading = false, error = healthRes.message) }
                }
                return
            }
            is ApiResult.Success -> {
                if (boundGateway != gateway) return
                val health = healthRes.value
                val tz = health.settings.timezone.ifBlank { null }
                val nowMoment = calendarNowZoned(tz)
                val hadHealth = _state.value.health != null
                _state.update { cur ->
                    cur.copy(
                        pluginMissing = false,
                        health = health,
                        settings = health.settings,
                        viewMode = if (hadHealth) cur.viewMode else CalendarViewMode.fromSettingsKey(health.settings.defaultView),
                        showWeekends = health.settings.showWeekends,
                        showDeclined = health.settings.showDeclined,
                        showCompletedTasks = health.settings.showCompletedTasks,
                        todayDate = nowMoment.date,
                        currentMinuteOfDay = nowMoment.minuteOfDay,
                    )
                }
            }
        }

        if (syncRemote) {
            calendarApi.sync(gateway.gatewayUrl)
        }

        coroutineScope {
            val (startIso, endIso) = computeFetchRange(_state.value.viewMode, _state.value.anchorDate)
            val accountsDeferred = async { calendarApi.listAccounts(gateway.gatewayUrl) }
            val eventsDeferred = async {
                calendarApi.listEvents(
                    gateway.gatewayUrl,
                    startDate = startIso,
                    endDate = endIso,
                    includeDeclined = _state.value.showDeclined,
                )
            }
            val dispatchDeferred = async {
                calendarApi.dispatchOverview(gateway.gatewayUrl, includeDone = _state.value.showCompletedTasks)
            }
            val proposalsDeferred = async { calendarApi.listProposals(gateway.gatewayUrl) }
            val botsDeferred = async { runCatching { botsApi?.roster()?.bots }.getOrNull() }
            val cfgDeferred = async { directusApi?.getConfig(gateway.gatewayUrl) }

            val accountsRes = accountsDeferred.await()
            val eventsRes = eventsDeferred.await()
            val dispatchRes = dispatchDeferred.await()
            val proposalsRes = proposalsDeferred.await()
            val nextBots = botsDeferred.await() ?: _state.value.bots
            val cfgRes = cfgDeferred.await()

            if (boundGateway != gateway) return@coroutineScope

            val (nextAccounts, nextCals) = (accountsRes as? ApiResult.Success)?.value ?: (_state.value.accounts to _state.value.calendars)
            val nextEvents = (eventsRes as? ApiResult.Success)?.value ?: _state.value.events
            val nextDispatch = (dispatchRes as? ApiResult.Success)?.value ?: _state.value.dispatchOverview
            val nextProposals = (proposalsRes as? ApiResult.Success)?.value ?: _state.value.proposals
            val coordCfg = (cfgRes as? ApiResult.Success)?.value?.coordinator
            proposalCoordinator.seedFromProposals(nextProposals)

            val firstErr = accountsRes.errorTextOrNull() ?: eventsRes.errorTextOrNull()
            _state.update { cur ->
                cur.copy(
                    loading = false,
                    error = if (cur.events.isEmpty() && nextEvents.isEmpty()) firstErr else cur.error,
                    accounts = nextAccounts,
                    calendars = nextCals,
                    events = nextEvents,
                    loadedRange = if (eventsRes is ApiResult.Success) startIso to endIso else cur.loadedRange,
                    dispatchOverview = nextDispatch,
                    proposals = nextProposals,
                    bots = nextBots,
                    coordinatorSessionId = coordCfg?.sessionId?.takeIf { it.isNotBlank() } ?: cur.coordinatorSessionId,
                    coordinatorProfile = coordCfg?.profile?.takeIf { it.isNotBlank() } ?: cur.coordinatorProfile,
                )
            }

            cacheStore?.save(
                gateway.gatewayUrl,
                CalendarOfflineSnapshot(
                    accounts = nextAccounts,
                    calendars = nextCals,
                    events = nextEvents,
                    dispatchOverview = nextDispatch,
                    settings = _state.value.settings,
                    savedAtMs = nowMs(),
                ),
            )

            loadCoordinatorHistory(gateway, _state.value.coordinatorSessionId, _state.value.coordinatorProfile)
        }
    }

    private fun ensureWindowLoaded(gateway: SavedGateway) {
        val cur = _state.value
        val (needStart, needEnd) = computeVisibleBounds(cur.viewMode, cur.anchorDate)
        val loaded = cur.loadedRange
        if (loaded != null && needStart >= loaded.first && needEnd <= loaded.second) return
        refreshEventsJob?.cancel()
        refreshEventsJob = viewModelScope.launch { refreshEventsWindow(gateway) }
    }

    private suspend fun refreshEventsWindow(gateway: SavedGateway) {
        val cur = _state.value
        val (startIso, endIso) = computeFetchRange(cur.viewMode, cur.anchorDate)
        when (val res = calendarApi.listEvents(gateway.gatewayUrl, startIso, endIso, includeDeclined = cur.showDeclined)) {
            is ApiResult.Success -> if (boundGateway == gateway) {
                _state.update { it.copy(events = res.value, loadedRange = startIso to endIso) }
            }
            is ApiResult.SessionExpired -> if (boundGateway == gateway) _state.update { it.copy(sessionExpired = true) }
            else -> Unit
        }
    }

    private suspend fun refreshDispatchOverview(gateway: SavedGateway) {
        val includeDone = _state.value.showCompletedTasks
        val res = calendarApi.dispatchOverview(gateway.gatewayUrl, includeDone = includeDone)
        if (res is ApiResult.Success && boundGateway == gateway) {
            _state.update { it.copy(dispatchOverview = res.value) }
        }
    }

    private suspend fun refreshWorkspaceSilently(gateway: SavedGateway) {
        coroutineScope {
            val eventsDef = async { refreshEventsWindow(gateway) }
            val dispatchDef = async { refreshDispatchOverview(gateway) }
            val propDef = async { calendarApi.listProposals(gateway.gatewayUrl) }
            eventsDef.await()
            dispatchDef.await()
            val props = (propDef.await() as? ApiResult.Success)?.value ?: return@coroutineScope
            if (boundGateway == gateway) {
                proposalCoordinator.seedFromProposals(props)
                _state.update { it.copy(proposals = props) }
            }
        }
    }

    // ── Calendars & Accounts ─────────────────────────────────────────────────

    fun toggleCalendarVisible(calendar: CalendarInfo) {
        val gw = boundGateway ?: return
        val next = !calendar.visible
        _state.update { cur ->
            cur.copy(calendars = cur.calendars.map { if (it.id == calendar.id) it.copy(visible = next) else it })
        }
        viewModelScope.launch {
            when (val res = calendarApi.updateCalendar(gw.gatewayUrl, calendar.id, UpdateCalendarRequest(visible = next))) {
                is ApiResult.Success -> {
                    _state.update { cur ->
                        cur.copy(calendars = cur.calendars.map { if (it.id == calendar.id) res.value else it })
                    }
                    refreshEventsWindow(gw)
                }
                else -> {
                    _state.update { cur ->
                        cur.copy(calendars = cur.calendars.map { if (it.id == calendar.id) calendar else it })
                    }
                    res.errorTextOrNull()?.let { msg -> _state.update { it.copy(error = msg) } }
                }
            }
        }
    }

    fun setTaskTargetCalendar(calendar: CalendarInfo) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            when (val res = calendarApi.updateCalendar(gw.gatewayUrl, calendar.id, UpdateCalendarRequest(isTaskTarget = true))) {
                is ApiResult.Success -> {
                    refreshAll(gw, syncRemote = false)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
                else -> res.errorTextOrNull()?.let { msg -> _state.update { it.copy(error = msg) } }
            }
            _state.update { it.copy(mutating = false) }
        }
    }

    fun openConnectDialog() {
        _state.update { it.copy(connectDialogOpen = true, oauthPending = null, oauthError = null) }
    }

    fun closeConnectDialog() {
        _state.update { it.copy(connectDialogOpen = false, oauthPending = null, oauthError = null, oauthBusy = false) }
    }

    fun importHermesGoogle(label: String? = null) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            when (val res = calendarApi.importHermesGoogle(gw.gatewayUrl, label = label?.ifBlank { null }, ensureTaskSubcalendar = true)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(oauthBusy = false, connectDialogOpen = false, oauthPending = null) }
                    refreshAll(gw, syncRemote = false)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun startGoogleOAuth(
        clientId: String?,
        clientSecret: String?,
        label: String?,
        ensureSubcal: Boolean,
        onOpenUrl: (String) -> Unit,
    ) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            val cleanId = clientId?.trim()?.ifEmpty { null }
            when (val res = calendarApi.googleAuthUrl(gw.gatewayUrl, GoogleAuthUrlRequest(clientId = cleanId))) {
                is ApiResult.Success -> {
                    val pending = OAuthPendingState(
                        provider = "google",
                        authUrl = res.value.authUrl,
                        redirectUri = res.value.redirectUri,
                        clientId = cleanId,
                        clientSecret = clientSecret?.trim()?.ifEmpty { null },
                        label = label?.trim()?.ifEmpty { null },
                        ensureTaskSubcalendar = ensureSubcal,
                    )
                    _state.update { it.copy(oauthBusy = false, oauthPending = pending) }
                    onOpenUrl(res.value.authUrl)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun startLarkOAuth(
        appId: String,
        appSecret: String,
        region: String = "intl",
        label: String?,
        ensureSubcal: Boolean,
        onOpenUrl: (String) -> Unit,
    ) {
        val gw = boundGateway ?: return
        val cleanId = appId.trim()
        val cleanSecret = appSecret.trim()
        if (cleanId.isEmpty() || cleanSecret.isEmpty()) {
            _state.update { it.copy(oauthError = "App ID and App Secret are required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            when (val res = calendarApi.larkAuthUrl(gw.gatewayUrl, LarkAuthUrlRequest(appId = cleanId, region = region))) {
                is ApiResult.Success -> {
                    val pending = OAuthPendingState(
                        provider = "lark",
                        authUrl = res.value.authUrl,
                        redirectUri = res.value.redirectUri,
                        clientId = cleanId,
                        clientSecret = cleanSecret,
                        region = region,
                        label = label?.trim()?.ifEmpty { null },
                        ensureTaskSubcalendar = ensureSubcal,
                    )
                    _state.update { it.copy(oauthBusy = false, oauthPending = pending) }
                    onOpenUrl(res.value.authUrl)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun startMicrosoftOAuth(
        clientId: String,
        clientSecret: String?,
        tenantId: String,
        label: String?,
        ensureSubcal: Boolean,
        onOpenUrl: (String) -> Unit,
    ) {
        val gw = boundGateway ?: return
        val cleanId = clientId.trim()
        val cleanTenant = tenantId.trim().ifEmpty { "common" }
        if (cleanId.isEmpty()) {
            _state.update { it.copy(oauthError = "Microsoft Client ID is required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            when (val res = calendarApi.microsoftAuthUrl(gw.gatewayUrl, MicrosoftAuthUrlRequest(clientId = cleanId, tenantId = cleanTenant))) {
                is ApiResult.Success -> {
                    val pending = OAuthPendingState(
                        provider = "microsoft",
                        authUrl = res.value.authUrl,
                        redirectUri = res.value.redirectUri,
                        clientId = cleanId,
                        clientSecret = clientSecret?.trim()?.ifEmpty { null },
                        tenantId = cleanTenant,
                        label = label?.trim()?.ifEmpty { null },
                        ensureTaskSubcalendar = ensureSubcal,
                    )
                    _state.update { it.copy(oauthBusy = false, oauthPending = pending) }
                    onOpenUrl(res.value.authUrl)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun completeOAuth(rawCodeOrUrl: String) {
        val gw = boundGateway ?: return
        val pending = _state.value.oauthPending ?: return
        val code = extractOAuthCode(rawCodeOrUrl)
        if (code.isBlank()) {
            _state.update { it.copy(oauthError = "Authorization code or redirect URL is empty.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            val res = when (pending.provider) {
                "google" -> calendarApi.exchangeGoogleCode(
                    gw.gatewayUrl,
                    GoogleExchangeRequest(
                        clientId = pending.clientId,
                        clientSecret = pending.clientSecret,
                        redirectUri = pending.redirectUri,
                        code = code,
                        label = pending.label,
                        ensureTaskSubcalendar = pending.ensureTaskSubcalendar,
                    ),
                )
                "lark" -> calendarApi.exchangeLarkCode(
                    gw.gatewayUrl,
                    LarkExchangeRequest(
                        appId = pending.clientId.orEmpty(),
                        appSecret = pending.clientSecret.orEmpty(),
                        region = pending.region,
                        code = code,
                        label = pending.label,
                        ensureTaskSubcalendar = pending.ensureTaskSubcalendar,
                    ),
                )
                else -> calendarApi.exchangeMicrosoftCode(
                    gw.gatewayUrl,
                    MicrosoftExchangeRequest(
                        clientId = pending.clientId.orEmpty(),
                        clientSecret = pending.clientSecret,
                        tenantId = pending.tenantId,
                        redirectUri = pending.redirectUri,
                        code = code,
                        label = pending.label,
                        ensureTaskSubcalendar = pending.ensureTaskSubcalendar,
                    ),
                )
            }
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(oauthBusy = false, oauthPending = null, connectDialogOpen = false) }
                    refreshAll(gw, syncRemote = false)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun connectCalDav(serverUrl: String, username: String, password: String, label: String?) {
        val gw = boundGateway ?: return
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _state.update { it.copy(oauthError = "CalDAV URL, username, and password are required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            val res = calendarApi.connectCalDav(
                gw.gatewayUrl,
                ConnectCalDavRequest(
                    serverUrl = serverUrl.trim(),
                    username = username.trim(),
                    password = password,
                    label = label?.trim()?.ifEmpty { null },
                ),
            )
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(oauthBusy = false, connectDialogOpen = false) }
                    refreshAll(gw, syncRemote = false)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun connectIcs(feedUrl: String, calendarName: String, label: String?, colorHex: String = "#10B981") {
        val gw = boundGateway ?: return
        if (feedUrl.isBlank() || calendarName.isBlank()) {
            _state.update { it.copy(oauthError = "ICS feed URL and calendar name are required.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(oauthBusy = true, oauthError = null) }
            val res = calendarApi.connectIcs(
                gw.gatewayUrl,
                ConnectIcsRequest(
                    feedUrl = feedUrl.trim(),
                    calendarName = calendarName.trim(),
                    label = label?.trim()?.ifEmpty { null },
                    colorHex = colorHex,
                ),
            )
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(oauthBusy = false, connectDialogOpen = false) }
                    refreshAll(gw, syncRemote = false)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(oauthBusy = false, sessionExpired = true) }
                else -> _state.update { it.copy(oauthBusy = false, oauthError = res.errorTextOrNull()) }
            }
        }
    }

    fun disconnectAccount(accountId: String) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            when (val res = calendarApi.deleteAccount(gw.gatewayUrl, accountId)) {
                is ApiResult.Success -> refreshAll(gw, syncRemote = false)
                is ApiResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
                else -> res.errorTextOrNull()?.let { msg -> _state.update { it.copy(error = msg) } }
            }
            _state.update { it.copy(mutating = false) }
        }
    }

    // ── Event CRUD ───────────────────────────────────────────────────────────

    fun selectEvent(event: CalendarEvent?) {
        _state.update { it.copy(selectedEvent = event) }
    }

    fun openCreateEvent(date: CalendarDate? = null, startMinute: Int? = null, allDay: Boolean = false) {
        val writable = _state.value.calendars.filter { it.writable }
        val defaultCal = writable.firstOrNull { it.isPrimary } ?: writable.firstOrNull()
        if (defaultCal == null) {
            openConnectDialog()
            return
        }
        val d = date ?: _state.value.anchorDate
        val sm = (startMinute ?: 540).coerceIn(0, 23 * 60)
        val em = (sm + 60).coerceAtMost(23 * 60 + 55)
        _state.update {
            it.copy(
                selectedEvent = null,
                editingEventDraft = EventEditorDraft(
                    calendarId = defaultCal.id,
                    dateIso = d.iso,
                    endDateIso = d.iso,
                    startTime = formatClock24(sm / 60, sm % 60),
                    endTime = formatClock24(em / 60, em % 60),
                    allDay = allDay,
                ),
            )
        }
    }

    fun openEditEvent(event: CalendarEvent) {
        val tz = event.timezone.ifBlank { _state.value.settings.timezone.ifBlank { null } }
        val startM = event.startAt?.let { calendarZonedFromIso(it, tz) }
        val endM = event.endAt?.let { calendarZonedFromIso(it, tz) }
        val dIso = event.startDate ?: startM?.date?.iso ?: _state.value.anchorDate.iso
        val eIso = event.endDate ?: endM?.date?.iso ?: dIso
        _state.update {
            it.copy(
                selectedEvent = null,
                editingEventDraft = EventEditorDraft(
                    existingOccurrenceId = event.id,
                    calendarId = event.calendarId,
                    title = event.title,
                    dateIso = dIso,
                    endDateIso = eIso,
                    startTime = startM?.let { m -> formatClock24(m.hour, m.minute) } ?: "09:00",
                    endTime = endM?.let { m -> formatClock24(m.hour, m.minute) } ?: "10:00",
                    allDay = event.allDay,
                    location = event.location.orEmpty(),
                    description = event.description.orEmpty(),
                    attendeesText = event.attendees.filterNot { a -> a.self }.joinToString(", ") { a -> a.email },
                    busy = event.busyState != "free",
                    hasExternalAttendees = event.hasExternalAttendees,
                ),
            )
        }
    }

    fun closeEventEditor() {
        _state.update { it.copy(editingEventDraft = null) }
    }

    fun saveEvent(draft: EventEditorDraft) {
        val gw = boundGateway ?: return
        val cleanTitle = draft.title.trim()
        if (cleanTitle.isEmpty()) {
            _state.update { it.copy(error = "Event title cannot be empty.") }
            return
        }
        val date = calendarDateFromIso(draft.dateIso.trim()) ?: run {
            _state.update { it.copy(error = "Invalid date format (YYYY-MM-DD).") }
            return
        }
        val tz = _state.value.settings.timezone.ifBlank { "Asia/Ho_Chi_Minh" }
        val attendees = draft.attendeesText.split(',').mapNotNull { raw ->
            raw.trim().takeIf { it.contains('@') }?.let { EventAttendee(email = it) }
        }
        val sendUpdates = if (draft.sendUpdates && attendees.isNotEmpty()) "all" else "none"
        val busyState = if (draft.busy) "busy" else "free"

        val startAtUtc: String?
        val endAtUtc: String?
        val startDateOut: String?
        val endDateOut: String?
        if (draft.allDay) {
            val endDate = calendarDateFromIso(draft.endDateIso.trim())?.let {
                if (it.epochDay <= date.epochDay) date.plusDays(1) else it
            } ?: date.plusDays(1)
            startAtUtc = null
            endAtUtc = null
            startDateOut = date.iso
            endDateOut = endDate.iso
        } else {
            val (sh, sm) = parseTimeHHMM(draft.startTime) ?: (9 to 0)
            val (eh, em) = parseTimeHHMM(draft.endTime) ?: ((sh + 1).coerceAtMost(23) to sm)
            val endDate = if (eh * 60 + em <= sh * 60 + sm) date else calendarDateFromIso(draft.endDateIso.trim()) ?: date
            startAtUtc = calendarToUtcIso(date, sh, sm, tz)
            endAtUtc = calendarToUtcIso(endDate, eh, em, tz)
            startDateOut = null
            endDateOut = null
        }

        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            val res = if (draft.existingOccurrenceId == null) {
                calendarApi.createEvent(
                    gw.gatewayUrl,
                    CreateEventRequest(
                        calendarId = draft.calendarId,
                        title = cleanTitle,
                        allDay = draft.allDay,
                        startAt = startAtUtc,
                        endAt = endAtUtc,
                        startDate = startDateOut,
                        endDate = endDateOut,
                        timezone = tz,
                        description = draft.description.trim().ifEmpty { null },
                        location = draft.location.trim().ifEmpty { null },
                        busyState = busyState,
                        attendees = attendees,
                        sendUpdates = sendUpdates,
                    ),
                )
            } else {
                calendarApi.updateEvent(
                    gw.gatewayUrl,
                    draft.existingOccurrenceId,
                    UpdateEventRequest(
                        title = cleanTitle,
                        allDay = draft.allDay,
                        startAt = startAtUtc,
                        endAt = endAtUtc,
                        startDate = startDateOut,
                        endDate = endDateOut,
                        timezone = tz,
                        description = draft.description.trim(),
                        location = draft.location.trim(),
                        busyState = busyState,
                        attendees = attendees,
                        sendUpdates = sendUpdates,
                    ),
                )
            }
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(mutating = false, editingEventDraft = null) }
                    refreshWorkspaceSilently(gw)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(mutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(mutating = false, error = res.errorTextOrNull()) }
            }
        }
    }

    fun deleteEvent(occurrenceId: String, notifyAttendees: Boolean = false) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            val sendUpdates = if (notifyAttendees) "all" else "none"
            when (val res = calendarApi.deleteEvent(gw.gatewayUrl, occurrenceId, sendUpdates = sendUpdates)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(mutating = false, selectedEvent = null, editingEventDraft = null) }
                    refreshWorkspaceSilently(gw)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(mutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(mutating = false, error = res.errorTextOrNull()) }
            }
        }
    }

    // ── Dispatch Tasks & Time-Blocks ─────────────────────────────────────────

    fun toggleTaskDone(taskId: String, done: Boolean) {
        val gw = boundGateway ?: return
        val nextStatus = if (done) "done" else "todo"
        _state.update { cur ->
            val ov = cur.dispatchOverview
            cur.copy(
                dispatchOverview = ov.copy(
                    tasks = ov.tasks.map { t -> if (t.id == taskId) t.copy(status = nextStatus) else t },
                ),
            )
        }
        viewModelScope.launch {
            when (val res = calendarApi.toggleDispatchTaskDone(gw.gatewayUrl, taskId, done)) {
                is ApiResult.Success -> refreshWorkspaceSilently(gw)
                is ApiResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
                else -> {
                    res.errorTextOrNull()?.let { msg -> _state.update { it.copy(error = msg) } }
                    refreshDispatchOverview(gw)
                }
            }
        }
    }

    fun openScheduleTask(
        task: DispatchCalendarTask,
        existingBlock: DispatchTimeBlock? = null,
    ) {
        val tz = _state.value.settings.timezone.ifBlank { null }
        val moment = existingBlock?.startAt?.let { calendarZonedFromIso(it, tz) }
        val dueIso = task.dueDate?.take(10)
        val defaultDate = moment?.date?.iso ?: dueIso ?: _state.value.anchorDate.iso
        val defaultStart = moment?.let { formatClock24(it.hour, it.minute) } ?: "09:00"
        val duration = task.estimatedMinutes?.coerceIn(15, 480) ?: 60
        _state.update {
            it.copy(
                schedulingTask = TaskBlockDraft(
                    task = task,
                    existingBlockId = existingBlock?.id,
                    label = existingBlock?.label ?: task.title,
                    dateIso = defaultDate,
                    startTime = defaultStart,
                    durationMinutes = duration,
                ),
            )
        }
        findFreeSlots(duration)
    }

    fun closeScheduleTaskDialog() {
        _state.update { it.copy(schedulingTask = null) }
    }

    fun findFreeSlots(minDurationMinutes: Int = 30) {
        val gw = boundGateway ?: return
        val anchor = _state.value.anchorDate
        viewModelScope.launch {
            _state.update { it.copy(findingFreeSlots = true) }
            val res = calendarApi.findFreeSlots(
                gw.gatewayUrl,
                startDate = anchor.iso,
                endDate = anchor.plusDays(6).iso,
                minDurationMinutes = minDurationMinutes.coerceAtLeast(15),
                bufferMinutes = 10,
            )
            if (boundGateway == gw) {
                _state.update {
                    it.copy(
                        findingFreeSlots = false,
                        freeSlots = (res as? ApiResult.Success)?.value ?: it.freeSlots,
                    )
                }
            }
        }
    }

    fun saveTaskBlock(draft: TaskBlockDraft) {
        val gw = boundGateway ?: return
        val date = calendarDateFromIso(draft.dateIso.trim()) ?: run {
            _state.update { it.copy(error = "Invalid date (YYYY-MM-DD).") }
            return
        }
        val (sh, sm) = parseTimeHHMM(draft.startTime) ?: (9 to 0)
        val totalEnd = (sh * 60 + sm + draft.durationMinutes.coerceIn(15, 720)).coerceAtMost(23 * 60 + 59)
        val tz = _state.value.settings.timezone.ifBlank { "Asia/Ho_Chi_Minh" }
        val startUtc = calendarToUtcIso(date, sh, sm, tz)
        val endUtc = calendarToUtcIso(date, totalEnd / 60, totalEnd % 60, tz)

        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            val res = if (draft.existingBlockId == null) {
                calendarApi.createTimeBlock(
                    gw.gatewayUrl,
                    CreateTimeBlockRequest(
                        taskId = draft.task.id,
                        startAt = startUtc,
                        endAt = endUtc,
                        label = draft.label.trim().ifEmpty { draft.task.title },
                        timezone = tz,
                        calendarId = draft.targetCalendarId,
                    ),
                )
            } else {
                calendarApi.updateTimeBlock(
                    gw.gatewayUrl,
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
                    _state.update { it.copy(mutating = false, schedulingTask = null) }
                    refreshWorkspaceSilently(gw)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(mutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(mutating = false, error = res.errorTextOrNull()) }
            }
        }
    }

    fun deleteTaskBlock(blockId: String) {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            when (val res = calendarApi.deleteTimeBlock(gw.gatewayUrl, blockId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(mutating = false, selectedEvent = null, schedulingTask = null) }
                    refreshWorkspaceSilently(gw)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(mutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(mutating = false, error = res.errorTextOrNull()) }
            }
        }
    }

    // ── Proposals (Stage -> Confirm -> Apply/Discard/Revert) ─────────────────

    fun applyProposal(proposalId: String, selectedOpIds: List<String>, force: Boolean = false) {
        val gw = boundGateway ?: return
        proposalCoordinator.apply(gw.gatewayUrl, proposalId, selectedOpIds, force)
    }

    fun discardProposal(proposalId: String) {
        val gw = boundGateway ?: return
        proposalCoordinator.discard(gw.gatewayUrl, proposalId)
    }

    fun revertProposal(proposalId: String, force: Boolean = true) {
        val gw = boundGateway ?: return
        proposalCoordinator.revert(gw.gatewayUrl, proposalId, force)
    }

    fun dismissProposalConflict() = proposalCoordinator.dismissConflict()

    fun confirmForceApplyProposal() {
        val gw = boundGateway ?: return
        proposalCoordinator.confirmForceApply(gw.gatewayUrl)
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun openSettingsDialog() {
        _state.update { it.copy(settingsDialogOpen = true) }
    }

    fun closeSettingsDialog() {
        _state.update { it.copy(settingsDialogOpen = false) }
    }

    fun saveSettings(
        timezone: String,
        workStart: String,
        workEnd: String,
        subcalendarName: String,
    ) {
        val gw = boundGateway ?: return
        val cur = _state.value.settings
        val days = listOf("mon", "tue", "wed", "thu", "fri")
        val hours = if (workStart.isNotBlank() && workEnd.isNotBlank()) {
            cur.workingHours.toMutableMap().apply {
                days.forEach { d -> put(d, listOf(workStart.trim(), workEnd.trim())) }
            }
        } else {
            cur.workingHours
        }
        val next = cur.copy(
            timezone = timezone.trim().ifEmpty { cur.timezone },
            workingHours = hours,
            dispatchSyncTarget = cur.dispatchSyncTarget.copy(
                subcalendarName = subcalendarName.trim().ifEmpty { cur.dispatchSyncTarget.subcalendarName },
            ),
        )
        viewModelScope.launch {
            _state.update { it.copy(mutating = true, error = null) }
            when (val res = calendarApi.updateSettings(gw.gatewayUrl, next)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(mutating = false, settings = res.value, settingsDialogOpen = false) }
                    refreshWorkspaceSilently(gw)
                }
                is ApiResult.SessionExpired -> _state.update { it.copy(mutating = false, sessionExpired = true) }
                else -> _state.update { it.copy(mutating = false, error = res.errorTextOrNull()) }
            }
        }
    }

    // ── Coordinator Scheduling Assistant ─────────────────────────────────────

    fun refreshCoordinatorChat() {
        val gw = boundGateway ?: return
        viewModelScope.launch {
            loadCoordinatorHistory(gw, _state.value.coordinatorSessionId, _state.value.coordinatorProfile)
        }
    }

    fun quickPlanDay() {
        val targetDate = _state.value.anchorDate.iso
        val tz = _state.value.settings.timezone
        askCoordinator(
            "Plan my schedule for $targetDate ($tz). Check existing calendar events and open Dispatch tasks, then stage a proposal with propose_schedule_plan for my confirmation.",
        )
    }

    fun askCoordinator(rawPrompt: String) {
        val gw = boundGateway ?: return
        val text = rawPrompt.trim()
        if (text.isEmpty()) return
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: run {
            _state.update { it.copy(agentError = "Gateway not connected.") }
            return
        }
        val optKey = "user-${Uuid.random()}"
        val userLine = ChannelChatLine(
            key = optKey,
            speaker = null,
            text = text,
            fromUser = true,
        )
        _state.update { cur ->
            cur.copy(
                agentError = null,
                agentMessages = cur.agentMessages + userLine,
                agentTurnRunning = true,
                agentTurnStatus = null,
            )
        }
        viewModelScope.launch {
            try {
                val (runtimeId, storedId, profile) = ensureCoordinatorRuntimeSession(gw, client)
                lastStreamEventAtMs = nowMs()
                client.request(
                    "prompt.submit",
                    buildJsonObject {
                        put("session_id", runtimeId)
                        put("text", text)
                    },
                )
                startCoordinatorFallbackPoll(gw, storedId, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { cur ->
                    cur.copy(
                        agentMessages = cur.agentMessages.filterNot { it.key == optKey },
                        agentTurnRunning = false,
                        agentTurnStatus = null,
                        agentError = e.message ?: "Could not send message.",
                    )
                }
            }
        }
    }

    fun answerCoordinatorRequest(request: InputRequest, result: JsonObject) {
        val client = (connection.state.value as? ConnectionState.Connected)?.client ?: return
        viewModelScope.launch {
            runCatching {
                client.respond(request.id, result)
                _state.update { cur ->
                    cur.copy(agentInputRequests = cur.agentInputRequests.filterNot { it.id == request.id })
                }
            }
        }
    }

    private suspend fun ensureCoordinatorRuntimeSession(
        gateway: SavedGateway,
        client: dev.hermeskotlin.core.rpc.JsonRpcClient,
    ): Triple<String, String, String> {
        val cur = _state.value
        val profile = cur.coordinatorProfile.ifBlank { Bot.DEFAULT }
        val stored = cur.coordinatorSessionId
        coordinatorRuntimeId?.takeIf { stored != null }?.let { return Triple(it, stored!!, profile) }

        if (stored != null) {
            val resumed = client.request(
                "session.resume",
                buildJsonObject {
                    put("session_id", stored)
                    put("profile", profile)
                    put("source", "desktop")
                    put("cols", 80)
                    put("omit_messages", true)
                },
            ) as? JsonObject
            val runtimeId = resumed.str("session_id") ?: error("Could not resume coordinator session.")
            coordinatorRuntimeId = runtimeId
            return Triple(runtimeId, stored, profile)
        }

        val started = client.request(
            "session.start",
            buildJsonObject {
                put("title", DispatchSessionsStore.COORDINATOR_SESSION_TITLE)
                put("profile", profile)
                put("source", "desktop")
                put("cols", 80)
            },
        ) as? JsonObject ?: error("Could not start scheduler session.")
        val runtimeId = started.str("session_id") ?: error("Missing session_id")
        val storedId = started.str("stored_session_id") ?: runtimeId
        coordinatorRuntimeId = runtimeId
        _state.update { it.copy(coordinatorSessionId = storedId) }
        return Triple(runtimeId, storedId, profile)
    }

    private suspend fun loadCoordinatorHistory(
        gateway: SavedGateway,
        sessionId: String?,
        profile: String,
    ) {
        val sApi = sessionsApi ?: return
        if (sessionId.isNullOrBlank()) return
        if (_state.value.agentMessages.isEmpty()) {
            _state.update { it.copy(agentLoading = true, agentError = null) }
        }
        when (val res = sApi.messages(gateway.gatewayUrl, sessionId, limit = 30, profile = profile)) {
            is ApiResult.Success -> if (boundGateway == gateway) {
                val parsed = parseSessionChatLines(res.value.messages, profile)
                _state.update { cur ->
                    val streaming = cur.agentMessages.any { it.streaming }
                    cur.copy(
                        agentLoading = false,
                        agentMessages = if (cur.agentTurnRunning && streaming) cur.agentMessages else parsed,
                        agentError = null,
                    )
                }
            }
            is ApiResult.Failed -> if (boundGateway == gateway) {
                _state.update { it.copy(agentLoading = false, agentError = res.message) }
            }
            is ApiResult.Unavailable -> if (boundGateway == gateway) {
                _state.update { it.copy(agentLoading = false, agentError = res.message) }
            }
            else -> if (boundGateway == gateway) _state.update { it.copy(agentLoading = false) }
        }
    }

    private fun startCoordinatorFallbackPoll(gateway: SavedGateway, storedId: String, profile: String) {
        fallbackPollJob?.cancel()
        fallbackPollJob = viewModelScope.launch {
            repeat(60) {
                delay(3_000L)
                if (boundGateway != gateway || !_state.value.agentTurnRunning) return@launch
                if (nowMs() - lastStreamEventAtMs < 6_000L) return@repeat
                val res = sessionsApi?.messages(gateway.gatewayUrl, storedId, limit = 10, profile = profile)
                if (res is ApiResult.Success) {
                    val lastRole = res.value.messages.lastOrNull { !it.isHidden && it.role != "tool" }?.role
                    if (lastRole == "assistant" && res.value.messages.lastOrNull()?.text?.isNotBlank() == true) {
                        _state.update { it.copy(agentTurnRunning = false, agentTurnStatus = null) }
                        loadCoordinatorHistory(gateway, storedId, profile)
                        refreshWorkspaceSilently(gateway)
                        return@launch
                    }
                }
            }
        }
    }

    private fun handleGatewayEvent(event: GatewayEvent) {
        proposalCoordinator.observeGatewayEvent(event)
        val gw = boundGateway
        if (gw != null && event.type in setOf(
                ScheduleProposalCoordinator.EVENT_SYNCED,
                ScheduleProposalCoordinator.EVENT_CHANGED,
                ScheduleProposalCoordinator.EVENT_PROPOSAL_STAGED,
                ScheduleProposalCoordinator.EVENT_PROPOSAL_UPDATED,
            )
        ) {
            viewModelScope.launch { refreshWorkspaceSilently(gw) }
            return
        }

        val runtimeId = coordinatorRuntimeId ?: return
        if (event.sessionId != runtimeId) return
        val payload = event.payload as? JsonObject
        when (event.type) {
            "message.start" -> {
                lastStreamEventAtMs = nowMs()
                _state.update { it.copy(agentTurnRunning = true, agentError = null, agentTurnStatus = it.agentTurnStatus ?: "Thinking…") }
            }
            "thinking.delta" -> {
                lastStreamEventAtMs = nowMs()
                payload.str("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { frame ->
                    _state.update { it.copy(agentTurnRunning = true, agentTurnStatus = frame) }
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
                        val last = cur.agentMessages.lastOrNull()
                        if (last != null && !last.fromUser) {
                            cur.agentMessages.dropLast(1) + last.copy(interaction = interaction)
                        } else {
                            cur.agentMessages + ChannelChatLine(
                                key = "cal-stream-${cur.agentMessages.size}",
                                speaker = cur.coordinatorProfile,
                                text = "",
                                fromUser = false,
                                profile = cur.coordinatorProfile,
                                streaming = true,
                                interaction = interaction,
                            )
                        }
                    } else {
                        cur.agentMessages
                    }
                    cur.copy(
                        agentTurnRunning = true,
                        agentTurnStatus = if (event.type == "tool.start") toolName else cur.agentTurnStatus,
                        agentMessages = nextMsgs,
                    )
                }
            }
            "message.delta", "message.interim" -> {
                if (event.type == "message.interim" && payload.bool("already_streamed") == true) return
                val chunk = payload.str("text") ?: return
                if (chunk.isEmpty()) return
                lastStreamEventAtMs = nowMs()
                val added = if (event.type == "message.interim") "$chunk\n\n" else chunk
                _state.update { cur ->
                    val last = cur.agentMessages.lastOrNull()
                    val next = if (last != null && !last.fromUser && last.streaming) {
                        cur.agentMessages.dropLast(1) + last.copy(text = last.text + added)
                    } else {
                        cur.agentMessages + ChannelChatLine(
                            key = "cal-stream-${cur.agentMessages.size}",
                            speaker = cur.coordinatorProfile,
                            text = added,
                            fromUser = false,
                            profile = cur.coordinatorProfile,
                            streaming = true,
                        )
                    }
                    cur.copy(agentMessages = next, agentTurnRunning = true, agentTurnStatus = null)
                }
            }
            "message.complete", "turn.error", "session.error" -> {
                fallbackPollJob?.cancel()
                _state.update { cur ->
                    cur.copy(
                        agentTurnRunning = false,
                        agentTurnStatus = null,
                        agentInputRequests = emptyList(),
                        agentMessages = cur.agentMessages.map { if (it.streaming) it.copy(streaming = false) else it },
                    )
                }
                if (gw != null) {
                    viewModelScope.launch {
                        loadCoordinatorHistory(gw, _state.value.coordinatorSessionId, _state.value.coordinatorProfile)
                        refreshWorkspaceSilently(gw)
                    }
                }
            }
            "request.cancel" -> {
                val reqId = payload.str("id") ?: return
                _state.update { cur -> cur.copy(agentInputRequests = cur.agentInputRequests.filterNot { it.id == reqId }) }
            }
        }
    }
}

internal fun extractOAuthCode(raw: String): String {
    val trimmed = raw.trim()
    val qIdx = trimmed.indexOf('?')
    if (qIdx >= 0) {
        val query = trimmed.substring(qIdx + 1).substringBefore('#')
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "code") {
                return percentDecodeOAuth(pair.substring(eq + 1))
            }
        }
    }
    return trimmed
}

private fun percentDecodeOAuth(s: String): String {
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                out.append(hex.toChar())
                i += 3
                continue
            }
        } else if (c == '+') {
            out.append(' ')
            i++
            continue
        }
        out.append(c)
        i++
    }
    return out.toString()
}

internal fun parseTimeHHMM(raw: String): Pair<Int, Int>? {
    val clean = raw.trim()
    val parts = clean.split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

internal fun computeVisibleBounds(mode: CalendarViewMode, anchor: CalendarDate): Pair<String, String> =
    when (mode) {
        CalendarViewMode.DAY -> anchor.iso to anchor.plusDays(1).iso
        CalendarViewMode.THREE_DAYS -> anchor.iso to anchor.plusDays(3).iso
        CalendarViewMode.FOUR_DAYS -> anchor.iso to anchor.plusDays(4).iso
        CalendarViewMode.WEEK -> {
            val days = CalendarLayoutEngine.visibleGridDays(anchor, 7, weekStartsSunday = true, showWeekends = true)
            days.first().iso to days.last().plusDays(1).iso
        }
        CalendarViewMode.MONTH -> {
            val weeks = CalendarLayoutEngine.monthGridWeeks(anchor.year, anchor.month, weekStartsSunday = true)
            if (weeks.isEmpty()) anchor.iso to anchor.plusDays(31).iso
            else weeks.first().first().iso to weeks.last().last().plusDays(1).iso
        }
        CalendarViewMode.SCHEDULE -> anchor.iso to anchor.plusDays(45).iso
    }

internal fun computeFetchRange(mode: CalendarViewMode, anchor: CalendarDate): Pair<String, String> =
    when (mode) {
        CalendarViewMode.MONTH -> {
            val weeks = CalendarLayoutEngine.monthGridWeeks(anchor.year, anchor.month, weekStartsSunday = true)
            val start = weeks.firstOrNull()?.firstOrNull()?.plusDays(-7) ?: anchor.plusDays(-7)
            val end = weeks.lastOrNull()?.lastOrNull()?.plusDays(14) ?: anchor.plusDays(35)
            start.iso to end.iso
        }
        CalendarViewMode.SCHEDULE -> anchor.plusDays(-3).iso to anchor.plusDays(60).iso
        else -> anchor.plusDays(-7).iso to anchor.plusDays(21).iso
    }

private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
