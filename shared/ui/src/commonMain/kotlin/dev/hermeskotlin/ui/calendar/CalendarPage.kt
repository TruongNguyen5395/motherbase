package dev.hermeskotlin.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.PanelLeft
import com.composables.icons.lucide.PanelRight
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.calendar.CalendarDate
import dev.hermeskotlin.core.calendar.CalendarEvent
import dev.hermeskotlin.core.calendar.CalendarInfo
import dev.hermeskotlin.core.calendar.CalendarItem
import dev.hermeskotlin.core.calendar.CalendarLayoutEngine
import dev.hermeskotlin.core.calendar.DispatchCalendarTask
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.calendarAddMonths
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClockCompact
import dev.hermeskotlin.core.calendar.plusDays
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.gateway.SavedGateway
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
import dev.hermeskotlin.designsystem.components.MarkdownText
import dev.hermeskotlin.designsystem.components.MenuChoice
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.VerticalScrollbar
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.dangerSoft
import dev.hermeskotlin.designsystem.onAccent
import dev.hermeskotlin.designsystem.sidebar
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.chat.InputRequestPanel
import dev.hermeskotlin.ui.chat.ReplyInteractionCard
import dev.hermeskotlin.ui.chat.ScheduleProposalContent
import dev.hermeskotlin.ui.clearOfWindowControls
import dev.hermeskotlin.ui.components.EmptyState
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.app_try_again
import dev.hermeskotlin.ui.resources.cal_agent_panel_empty
import dev.hermeskotlin.ui.resources.cal_agent_staged_empty
import dev.hermeskotlin.ui.resources.cal_ask_agent_hint
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
import dev.hermeskotlin.ui.resources.cal_other_calendars
import dev.hermeskotlin.ui.resources.cal_plan_my_day
import dev.hermeskotlin.ui.resources.cal_plugin_missing
import dev.hermeskotlin.ui.resources.cal_previous
import dev.hermeskotlin.ui.resources.cal_proposal_apply
import dev.hermeskotlin.ui.resources.cal_proposal_heading
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
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal enum class CalendarRightTab {
    TASKS,
    AGENT,
}

@Composable
internal fun CalendarPage(
    gateway: SavedGateway,
    visible: Boolean,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onToggleSidebar: (() -> Unit)? = null,
) {
    val viewModel: CalendarViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(gateway, visible) {
        viewModel.bind(gateway, visible)
    }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    CalendarPageContent(
        state = state,
        onBack = onBack,
        onToggleSidebar = onToggleSidebar,
        onRefresh = viewModel::refresh,
        onSyncNow = { viewModel.syncNow() },
        onSetViewMode = viewModel::setViewMode,
        onStepPeriod = viewModel::stepPeriod,
        onGoToday = viewModel::goToday,
        onSelectDate = viewModel::selectDate,
        onToggleWeekends = viewModel::toggleShowWeekends,
        onToggleDeclined = viewModel::toggleShowDeclined,
        onToggleCompletedTasks = viewModel::toggleShowCompletedTasks,
        onToggleCalendarVisible = viewModel::toggleCalendarVisible,
        onSetTaskTargetCalendar = viewModel::setTaskTargetCalendar,
        onSelectEvent = viewModel::selectEvent,
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
        onAskCoordinator = viewModel::askCoordinator,
        onQuickPlanDay = viewModel::quickPlanDay,
        onRefreshAgentChat = viewModel::refreshCoordinatorChat,
        onAnswerCoordinatorRequest = viewModel::answerCoordinatorRequest,
        onClearFeedback = viewModel::clearFeedback,
    )
}

@Composable
internal fun CalendarPageContent(
    state: CalendarUiState,
    onBack: () -> Unit = {},
    onToggleSidebar: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSetViewMode: (CalendarViewMode) -> Unit = {},
    onStepPeriod: (Int) -> Unit = {},
    onGoToday: () -> Unit = {},
    onSelectDate: (CalendarDate) -> Unit = {},
    onToggleWeekends: () -> Unit = {},
    onToggleDeclined: () -> Unit = {},
    onToggleCompletedTasks: () -> Unit = {},
    onToggleCalendarVisible: (CalendarInfo) -> Unit = {},
    onSetTaskTargetCalendar: (CalendarInfo) -> Unit = {},
    onSelectEvent: (CalendarEvent?) -> Unit = {},
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
    onSaveSettings: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onDisconnectAccount: (String) -> Unit = {},
    onAskCoordinator: (String) -> Unit = {},
    onQuickPlanDay: () -> Unit = {},
    onRefreshAgentChat: () -> Unit = {},
    onAnswerCoordinatorRequest: (InputRequest, JsonObject) -> Unit = { _, _ -> },
    onClearFeedback: () -> Unit = {},
) {
    var leftRailDockedOpen by rememberSaveable { mutableStateOf(true) }
    var rightRailDockedOpen by rememberSaveable { mutableStateOf(true) }
    var leftSheetOpen by remember { mutableStateOf(false) }
    var rightSheetOpen by remember { mutableStateOf(false) }
    var rightTab by rememberSaveable { mutableStateOf(CalendarRightTab.TASKS) }
    var miniMonthAnchor by remember(state.anchorDate.year, state.anchorDate.month) { mutableStateOf(state.anchorDate) }

    val modals = LocalOpenModals.current

    val effectiveProposals = remember(state.proposals, state.proposalStatuses) {
        state.proposals.map { prop ->
            val override = state.proposalStatuses[prop.id]
            if (override != null && override != prop.status) prop.copy(status = override) else prop
        }
    }
    val stagedCount = remember(effectiveProposals) {
        effectiveProposals.count { it.status == ScheduleProposal.STATUS_STAGED }
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

            val handleItemClick: (CalendarItem) -> Unit = { item ->
                when (item) {
                    is CalendarItem.EventItem -> onSelectEvent(item.event)
                    is CalendarItem.TaskDeadlineItem -> onOpenScheduleTask(item.task, item.task.timeBlocks.firstOrNull())
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
                        if (wideRight) rightRailDockedOpen = !rightRailDockedOpen else rightSheetOpen = true
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
                                        onSelectDate = onSelectDate,
                                        onItemClick = handleItemClick,
                                        onToggleTaskDone = onToggleTaskDone,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        val bannerError = state.error ?: state.feedback
                        if (bannerError != null) {
                            Row(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Theme[colors][dangerSoft])
                                    .border(1.dp, Theme[colors][danger].copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(bannerError, style = Theme[typography][caption], color = Theme[colors][danger], modifier = Modifier.weight(1f, fill = false))
                                IconButton(Lucide.X, contentDescription = stringResource(Res.string.cal_back), onClick = onClearFeedback)
                            }
                        }
                    }

                    if (wideRight && rightRailDockedOpen) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Theme[colors][stroke]))
                        CalendarRightPanel(
                            state = state,
                            effectiveProposals = effectiveProposals,
                            selectedTab = rightTab,
                            onSelectTab = { rightTab = it },
                            onToggleTaskDone = onToggleTaskDone,
                            onScheduleTask = onOpenScheduleTask,
                            onDeleteBlock = onDeleteTaskBlock,
                            onQuickPlanDay = onQuickPlanDay,
                            onRefreshAgentChat = onRefreshAgentChat,
                            onAskCoordinator = onAskCoordinator,
                            onApplyProposal = onApplyProposal,
                            onDiscardProposal = onDiscardProposal,
                            onRevertProposal = onRevertProposal,
                            onAnswerRequest = onAnswerCoordinatorRequest,
                            modifier = Modifier.width(340.dp).fillMaxHeight(),
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
                BottomSheet(visible = rightSheetOpen, onDismiss = { rightSheetOpen = false }) {
                    CalendarRightPanel(
                        state = state,
                        effectiveProposals = effectiveProposals,
                        selectedTab = rightTab,
                        onSelectTab = { rightTab = it },
                        onToggleTaskDone = onToggleTaskDone,
                        onScheduleTask = { t, b ->
                            rightSheetOpen = false
                            onOpenScheduleTask(t, b)
                        },
                        onDeleteBlock = onDeleteTaskBlock,
                        onQuickPlanDay = onQuickPlanDay,
                        onRefreshAgentChat = onRefreshAgentChat,
                        onAskCoordinator = onAskCoordinator,
                        onApplyProposal = onApplyProposal,
                        onDiscardProposal = onDiscardProposal,
                        onRevertProposal = onRevertProposal,
                        onAnswerRequest = onAnswerCoordinatorRequest,
                        modifier = Modifier.fillMaxWidth().height(540.dp),
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

    EventDetailDialog(
        event = state.selectedEvent,
        use24Hour = state.use24Hour,
        mutating = state.mutating,
        onDismiss = { onSelectEvent(null) },
        onEdit = onOpenEditEvent,
        onDelete = onDeleteEvent,
        onUnscheduleBlock = onDeleteTaskBlock,
    )

    EventEditorDialog(
        draft = state.editingEventDraft,
        calendars = state.calendars,
        mutating = state.mutating,
        onDismiss = onCloseEventEditor,
        onSave = onSaveEvent,
        onDelete = { occId -> onDeleteEvent(occId, false) },
    )

    ScheduleTaskBlockDialog(
        draft = state.schedulingTask,
        freeSlots = state.freeSlots,
        findingSlots = state.findingFreeSlots,
        mutating = state.mutating,
        onDismiss = onCloseScheduleTask,
        onRefreshSlots = onRefreshSlots,
        onSave = onSaveTaskBlock,
        onDeleteBlock = onDeleteTaskBlock,
    )

    CalendarSettingsDialog(
        visible = state.settingsDialogOpen,
        settings = state.settings,
        accounts = state.accounts,
        mutating = state.mutating,
        onDismiss = onCloseSettings,
        onSave = onSaveSettings,
        onDisconnectAccount = onDisconnectAccount,
    )

    ScheduleProposalConflictDialog(
        conflict = state.pendingConflict,
        busy = state.pendingConflict?.proposalId in state.busyProposalIds,
        onDismiss = onDismissConflict,
        onForceApply = onForceApplyConflict,
    )
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

    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface])
            .clearOfWindowControls()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleSidebar != null) {
            IconButton(Lucide.PanelLeft, contentDescription = stringResource(Res.string.cal_back), onClick = onToggleSidebar)
        } else {
            IconButton(Lucide.ArrowLeft, contentDescription = stringResource(Res.string.cal_back), onClick = onBack)
        }
        IconButton(Lucide.CalendarDays, contentDescription = stringResource(Res.string.cal_toggle_calendars), onClick = onToggleCalendarsPanel)

        if (!compact) {
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
            IconButton(Lucide.ArrowLeft, contentDescription = stringResource(Res.string.cal_previous), onClick = { onStepPeriod(-1) })
            IconButton(Lucide.ArrowRight, contentDescription = stringResource(Res.string.cal_next), onClick = { onStepPeriod(1) })
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
    selectedTab: CalendarRightTab,
    onSelectTab: (CalendarRightTab) -> Unit,
    onToggleTaskDone: (String, Boolean) -> Unit,
    onScheduleTask: (DispatchCalendarTask, DispatchTimeBlock?) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onQuickPlanDay: () -> Unit,
    onRefreshAgentChat: () -> Unit,
    onAskCoordinator: (String) -> Unit,
    onApplyProposal: (String, List<String>, Boolean) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onRevertProposal: (String) -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val botLabel = state.coordinatorProfile.ifBlank { "Hermes" }
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
                text = botLabel,
                selected = selectedTab == CalendarRightTab.AGENT,
                onClick = { onSelectTab(CalendarRightTab.AGENT) },
                count = effectiveProposals.count { it.status == ScheduleProposal.STATUS_STAGED }.takeIf { it > 0 },
            )
            Spacer(Modifier.weight(1f))
            if (selectedTab == CalendarRightTab.AGENT) {
                IconButton(Lucide.RefreshCw, contentDescription = stringResource(Res.string.cal_sync_now), onClick = onRefreshAgentChat)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        if (selectedTab == CalendarRightTab.TASKS) {
            DispatchTasksBacklogTab(
                state = state,
                onToggleTaskDone = onToggleTaskDone,
                onScheduleTask = onScheduleTask,
                onDeleteBlock = onDeleteBlock,
                onPlanWithAgent = {
                    onSelectTab(CalendarRightTab.AGENT)
                    onQuickPlanDay()
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            AgentSchedulerTab(
                state = state,
                effectiveProposals = effectiveProposals,
                botLabel = botLabel,
                onQuickPlanDay = onQuickPlanDay,
                onAskCoordinator = onAskCoordinator,
                onApplyProposal = onApplyProposal,
                onDiscardProposal = onDiscardProposal,
                onRevertProposal = onRevertProposal,
                onAnswerRequest = onAnswerRequest,
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
                text = stringResource(Res.string.cal_ask_agent_plan, state.coordinatorProfile.ifBlank { "Hermes" }),
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun AgentSchedulerTab(
    state: CalendarUiState,
    effectiveProposals: List<ScheduleProposal>,
    botLabel: String,
    onQuickPlanDay: () -> Unit,
    onAskCoordinator: (String) -> Unit,
    onApplyProposal: (String, List<String>, Boolean) -> Unit,
    onDiscardProposal: (String) -> Unit,
    onRevertProposal: (String) -> Unit,
    onAnswerRequest: (InputRequest, JsonObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val promptState = rememberTextFieldState("")
    val scroll = rememberScrollState()

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    text = stringResource(Res.string.cal_plan_my_day),
                    onClick = onQuickPlanDay,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                    leadingIcon = Lucide.Sparkles,
                    enabled = state.gatewayConnected && !state.agentTurnRunning,
                    modifier = Modifier.fillMaxWidth(),
                )

                val stagedOrApplied = effectiveProposals.take(4)
                if (stagedOrApplied.isEmpty()) {
                    Text(
                        stringResource(Res.string.cal_agent_staged_empty),
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                    )
                } else {
                    stagedOrApplied.forEach { prop ->
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
                                onApply = { opIds, force -> onApplyProposal(prop.id, opIds, force) },
                                onDiscard = { onDiscardProposal(prop.id) },
                                onRevert = { onRevertProposal(prop.id) },
                            )
                        }
                    }
                }

                if (state.agentMessages.isEmpty() && !state.agentTurnRunning) {
                    Text(
                        stringResource(Res.string.cal_agent_panel_empty),
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    state.agentMessages.forEach { line ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (line.fromUser) Theme[colors][accentSoft] else Theme[colors][surface],
                                    RoundedCornerShape(10.dp),
                                )
                                .border(1.dp, Theme[colors][stroke], RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (!line.fromUser) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    UnstyledIcon(Lucide.Bot, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(13.dp))
                                    Text(line.speaker ?: botLabel, style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][accentText])
                                }
                            }
                            if (line.text.isNotBlank()) {
                                MarkdownText(
                                    text = line.text,
                                    streaming = line.streaming,
                                    textStyle = Theme[typography][bodySmall].copy(color = Theme[colors][textColor]),
                                )
                            }
                            line.interaction?.let { inter ->
                                ReplyInteractionCard(
                                    replyKey = line.key,
                                    interaction = inter,
                                    sendEnabled = !state.agentTurnRunning && state.gatewayConnected,
                                    onSend = onAskCoordinator,
                                    onFill = { txt -> promptState.setTextAndPlaceCursorAtEnd(txt) },
                                    onApplyProposal = onApplyProposal,
                                    onDiscardProposal = onDiscardProposal,
                                    onRevertProposal = onRevertProposal,
                                    appliedProposalStatuses = state.proposalStatuses,
                                )
                            }
                        }
                    }
                }

                if (state.agentTurnRunning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spinner(Modifier.size(14.dp))
                        Text(
                            state.agentTurnStatus ?: "…",
                            style = Theme[typography][caption],
                            color = Theme[colors][accentText],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                state.agentError?.let { err ->
                    Text(err, style = Theme[typography][caption], color = Theme[colors][danger])
                }
            }
            VerticalScrollbar(scroll, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(vertical = 2.dp))
        }

        if (state.agentInputRequests.isNotEmpty()) {
            InputRequestPanel(
                requests = state.agentInputRequests,
                connected = state.gatewayConnected,
                onAnswer = onAnswerRequest,
            )
        }

        val submitPrompt = {
            val txt = promptState.text.toString().trim()
            if (txt.isNotEmpty()) {
                promptState.clearText()
                onAskCoordinator(txt)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Theme[colors][stroke], RoundedCornerShape(0.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                state = promptState,
                placeholder = stringResource(Res.string.cal_ask_agent_hint, botLabel),
                singleLine = false,
                maxLines = 3,
                enabled = state.gatewayConnected && !state.agentTurnRunning,
                onKeyboardAction = KeyboardActionHandler { submitPrompt() },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                icon = Lucide.Send,
                contentDescription = stringResource(Res.string.cal_plan_my_day),
                onClick = submitPrompt,
                enabled = state.gatewayConnected && !state.agentTurnRunning && promptState.text.isNotBlank(),
                containerColor = Theme[colors][accent],
                tint = Theme[colors][onAccent],
            )
        }
    }
}
