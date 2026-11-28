package dev.hermeskotlin.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Blocks
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Puzzle
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.bots.BotTrouble
import dev.hermeskotlin.core.bots.forRoster
import dev.hermeskotlin.core.bots.previewLine
import dev.hermeskotlin.core.calendar.CalendarAgentConfig
import dev.hermeskotlin.core.calendar.CalendarAgentStore
import dev.hermeskotlin.core.chat.Waiting
import dev.hermeskotlin.core.cron.CronJob
import dev.hermeskotlin.core.display.ProfileScreenState
import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectIndex
import dev.hermeskotlin.core.rooms.DesktopRoom
import dev.hermeskotlin.core.rooms.Room
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.tasks.DispatchRoomEntry
import dev.hermeskotlin.core.tasks.DispatchSessionEntry
import dev.hermeskotlin.core.threads.ChatThread
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.body
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.code
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.DropdownMenu
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.MenuAction
import dev.hermeskotlin.designsystem.components.MinTouchTarget
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.VerticalScrollbar
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.radiusSmall
import dev.hermeskotlin.designsystem.rememberHoverIndication
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.ui.bots.BotActions
import dev.hermeskotlin.ui.bots.BotAvatar
import dev.hermeskotlin.ui.bots.BotFaces
import dev.hermeskotlin.ui.bots.BotTone
import dev.hermeskotlin.ui.bots.BotsUiState
import dev.hermeskotlin.ui.bots.DeleteBotDialog
import dev.hermeskotlin.ui.bots.DesktopRoomRow
import dev.hermeskotlin.ui.bots.NeedsYou
import dev.hermeskotlin.ui.bots.RoomRow
import dev.hermeskotlin.ui.bots.SectionHeader
import dev.hermeskotlin.ui.bots.StartOverDialog
import dev.hermeskotlin.ui.bots.StatusDot
import dev.hermeskotlin.ui.bots.rememberBotMenuItems
import dev.hermeskotlin.ui.bots.resolveBotStatusLine
import dev.hermeskotlin.ui.inbox.text
import dev.hermeskotlin.ui.inbox.wordsThen
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.resources.*
import dev.hermeskotlin.ui.rooms.RoomAsk
import dev.hermeskotlin.ui.rooms.RoomAskDialogs
import dev.hermeskotlin.ui.rooms.RoomFaces
import dev.hermeskotlin.ui.rooms.roomMembers
import org.jetbrains.compose.resources.stringResource

/** Default number of Recent sessions shown inside an expanded agent before expanding all. */
internal const val DEFAULT_AGENT_RECENT_CAP = 5

/**
 * Orders visible agents for the unified sidebar: the Dispatch Coordinator (or launch/default profile)
 * comes first, followed by remaining bots in [forRoster] order.
 */
internal fun orderAgentsForSidebar(bots: List<Bot>, coordinatorProfile: String?): List<Bot> {
    if (bots.isEmpty()) return emptyList()
    val rosterOrdered = bots.forRoster()
    val coord = coordinatorProfile?.trim()?.takeIf { it.isNotEmpty() } ?: return rosterOrdered
    val primary = rosterOrdered.firstOrNull { it.name.equals(coord, ignoreCase = true) } ?: return rosterOrdered
    return listOf(primary) + rosterOrdered.filterNot { it.name == primary.name }
}

/** An active or attention-needed item across any agent or room, surfaced in the top overview strip. */
internal sealed interface ActiveAttentionItem {
    val key: String

    data class BotNeedsYou(val item: NeedsYou) : ActiveAttentionItem {
        override val key: String get() = "needs:${item.bot.name}"
    }

    data class Dispatch(
        val entry: DispatchSessionEntry,
        val bot: Bot?,
        val waiting: Waiting?,
        val running: Boolean,
    ) : ActiveAttentionItem {
        override val key: String get() = "dispatch:${entry.sessionId}"
    }

    data class BotWorking(
        val bot: Bot,
        val sessionId: String?,
    ) : ActiveAttentionItem {
        override val key: String get() = "working:${bot.name}"
    }

    data class BranchSession(
        val agentName: String,
        val bot: Bot?,
        val session: SessionSummary,
        val waiting: Waiting?,
        val running: Boolean,
    ) : ActiveAttentionItem {
        override val key: String get() = "branch:$agentName:${session.id}"
    }

    data class DesktopRoomNeedsYou(val room: DesktopRoom) : ActiveAttentionItem {
        override val key: String get() = "desktop-room:${room.key}"
    }

    data class RoomAttention(
        val room: Room,
        val dispatchEntry: DispatchRoomEntry?,
    ) : ActiveAttentionItem {
        override val key: String get() = "room:${room.roomId}"
    }
}

/**
 * Builds the deduplicated cross-agent overview list ordered by urgency:
 * waiting items (approvals/questions/troubles/failing routines) -> running items -> unread dispatch rooms.
 */
internal fun buildActiveAttentionItems(
    bots: List<Bot>,
    needsYou: List<NeedsYou>,
    dispatchSessions: Map<String, DispatchSessionEntry>,
    dispatchRooms: Map<String, DispatchRoomEntry>,
    rooms: List<Room>,
    desktopRooms: List<DesktopRoom>,
    unreadRooms: Set<String>,
    liveWaiting: Map<String, Waiting>,
    liveRunning: Map<String, Boolean>,
    agentBranches: Map<String, AgentBranchState>,
    nowSeconds: Double,
    launchProfile: String?,
): List<ActiveAttentionItem> {
    val botByName = bots.associateBy { it.name }
    val seenSessions = HashSet<String>()
    val seenBots = HashSet<String>()
    val waitingItems = ArrayList<ActiveAttentionItem>()
    val runningItems = ArrayList<ActiveAttentionItem>()
    val unreadRoomItems = ArrayList<ActiveAttentionItem>()

    // 1. Waiting Dispatch tasks across all profiles
    for (entry in dispatchSessions.values) {
        val w = liveWaiting[entry.sessionId] ?: continue
        val ownerName = entry.profile ?: launchProfile ?: Bot.DEFAULT
        waitingItems += ActiveAttentionItem.Dispatch(entry, botByName[ownerName], waiting = w, running = liveRunning[entry.sessionId] == true)
        seenSessions += entry.sessionId
    }

    // 2. Bot-level NeedsYou (canonical session waiting, troubles, failing routines)
    for (item in needsYou) {
        val canonicalIds = listOfNotNull(item.bot.canonicalSession?.openId, item.bot.canonicalSession?.id)
        if (canonicalIds.any { it in seenSessions }) continue
        waitingItems += ActiveAttentionItem.BotNeedsYou(item)
        seenSessions.addAll(canonicalIds)
        seenBots += item.bot.name
    }

    // 3. Desktop rooms asking for the user
    for (room in desktopRooms) {
        if (room.needsYou) waitingItems += ActiveAttentionItem.DesktopRoomNeedsYou(room)
    }

    // 4. Running Dispatch tasks across all profiles
    for (entry in dispatchSessions.values) {
        if (entry.sessionId in seenSessions) continue
        if (liveRunning[entry.sessionId] != true) continue
        val ownerName = entry.profile ?: launchProfile ?: Bot.DEFAULT
        runningItems += ActiveAttentionItem.Dispatch(entry, botByName[ownerName], waiting = null, running = true)
        seenSessions += entry.sessionId
    }

    // 5. Working bots (worker session heartbeat or canonical chat running)
    for (bot in bots) {
        if (bot.name in seenBots) continue
        val canonicalIds = listOfNotNull(bot.canonicalSession?.openId, bot.canonicalSession?.id)
        val canonicalRunning = canonicalIds.any { it !in seenSessions && liveRunning[it] == true }
        val workerSid = bot.workerSession?.id
        val workerRunning = bot.isWorking(nowSeconds) && (workerSid == null || workerSid !in seenSessions)
        if (!canonicalRunning && !workerRunning) continue
        val sid = canonicalIds.firstOrNull { liveRunning[it] == true } ?: workerSid ?: bot.canonicalSession?.openId ?: bot.canonicalSession?.id
        runningItems += ActiveAttentionItem.BotWorking(bot, sessionId = sid)
        sid?.let { seenSessions += it }
        seenBots += bot.name
    }

    // 6. Any other running or waiting session in cached agent branches
    for ((agentName, branch) in agentBranches) {
        for (session in branch.sessions) {
            if (session.id in seenSessions) continue
            val w = liveWaiting[session.id]
            val r = liveRunning[session.id] == true
            if (w == null && !r) continue
            val item = ActiveAttentionItem.BranchSession(agentName, botByName[agentName], session, w, r)
            if (w != null) waitingItems += item else runningItems += item
            seenSessions += session.id
        }
    }

    // 7. Unread hosted rooms linked to active Dispatch tasks
    for (room in rooms) {
        if (room.roomId in unreadRooms && room.roomId in dispatchRooms) {
            unreadRoomItems += ActiveAttentionItem.RoomAttention(room, dispatchRooms[room.roomId])
        }
    }

    return waitingItems + runningItems + unreadRoomItems
}

/** Sliced sections rendered inside one expanded agent. */
internal data class AgentSessionsSlice(
    val sections: ThreadSections,
    val shownThreads: List<ThreadRow>,
    val shownDispatch: List<DispatchRow>,
    val shownCalendar: SessionSummary?,
    val pinned: List<SessionSummary>,
    val shownRecent: List<SessionSummary>,
    val totalRecentCount: Int,
    val recentCapped: Boolean,
)

/**
 * Slices an expanded agent's sessions around its permanent canonical chat, threads, dispatch tasks,
 * calendar agent, pinned chats, and capped recent chats.
 */
internal fun sliceAgentSessions(
    bot: Bot,
    threads: List<ChatThread>,
    listed: List<SessionSummary>,
    allRows: List<SessionSummary>,
    project: Project?,
    projectIndex: ProjectIndex,
    dispatchSessions: Map<String, DispatchSessionEntry>,
    calendarAgent: CalendarAgentConfig?,
    statuses: Map<String, RowStatus>,
    drafts: Set<String>,
    selectedId: String?,
    attentionFilter: AttentionFilter,
    threadsCollapsed: Boolean,
    dispatchCollapsed: Boolean,
    pinnedCollapsed: Boolean,
    recentExpanded: Boolean,
): AgentSessionsSlice {
    val canonicalIds = setOfNotNull(bot.canonicalSession?.id, bot.canonicalSession?.openId)
    val rawSections = threadSections(threads, listed, allRows, project, projectIndex, dispatchSessions, calendarAgent)
    val filteredOthers = rawSections.others.filterNot { it.id in canonicalIds }
    val sections = rawSections.copy(others = filteredOthers)

    val shownThreads = sections.threads.filter { attentionFilter.keeps(statuses[it.session.id]) }
    val shownDispatch = sections.dispatch.filter { attentionFilter.keeps(statuses[it.session.id]) }
    val shownCalendar = sections.calendar?.takeIf { attentionFilter.keeps(statuses[it.id]) }

    val visibleOthers = filteredOthers.filter { attentionFilter.keeps(statuses[it.id]) }
    val (pinnedAll, restAll) = visibleOthers.partition { it.pinned }
    val visiblePinned = visiblePinned(pinnedAll, pinnedCollapsed, selectedId, statuses, drafts)

    val canCap = !recentExpanded && attentionFilter == AttentionFilter.All && project == null && restAll.size > DEFAULT_AGENT_RECENT_CAP
    val shownRecent = if (!canCap) {
        restAll
    } else {
        val mustKeep = restAll.filter { it.id == selectedId || rowTone(statuses[it.id], it.id in drafts) != RowTone.Idle }
        val keptIds = mustKeep.mapTo(HashSet()) { it.id }
        val top = restAll.filter { it.id !in keptIds }.take((DEFAULT_AGENT_RECENT_CAP - mustKeep.size).coerceAtLeast(0))
        val visibleIds = keptIds + top.map { it.id }
        restAll.filter { it.id in visibleIds }
    }

    return AgentSessionsSlice(
        sections = sections,
        shownThreads = visibleThreads(shownThreads, threadsCollapsed, selectedId, statuses, drafts),
        shownDispatch = visibleDispatch(shownDispatch, dispatchCollapsed, selectedId, statuses, drafts),
        shownCalendar = shownCalendar,
        pinned = visiblePinned,
        shownRecent = shownRecent,
        totalRecentCount = restAll.size,
        recentCapped = canCap && shownRecent.size < restAll.size,
    )
}

/** Callbacks for per-agent pages accessed from the agent's `⋯` menu. */
internal class AgentPageCallbacks(
    val onOpenArchived: (Bot) -> Unit,
    val onOpenConfig: (Bot) -> Unit,
    val onOpenCapabilities: (Bot) -> Unit,
    val onOpenPlugins: (Bot) -> Unit,
    val onOpenInsights: (Bot) -> Unit,
)

/**
 * Unified Agent-centric left sidebar:
 * 1. Gateway-level workspace buttons ([top])
 * 2. Optional cross-agent Active & Needs You strip
 * 3. Expandable Agents list (with Direct chat, Dispatch, Threads, Pinned, Recent inside each expanded agent)
 * 4. Multi-bot Rooms & Desktop rooms below
 */
@Composable
internal fun UnifiedAgentSidebar(
    botsState: BotsUiState,
    avatars: Map<String, ByteArray>,
    coordinatorProfile: String?,
    launchProfile: String?,
    expandedAgents: Set<String>,
    boundAgentName: String,
    boundUiState: SessionsUiState,
    boundThreads: List<ChatThread>,
    boundStatuses: Map<String, RowStatus>,
    agentBranches: Map<String, AgentBranchState>,
    liveWaiting: Map<String, Waiting>,
    liveRunning: Map<String, Boolean>,
    drafts: Set<String>,
    dispatchSessions: Map<String, DispatchSessionEntry>,
    dispatchRooms: Map<String, DispatchRoomEntry>,
    calendarAgent: CalendarAgentConfig?,
    attentionFilter: AttentionFilter,
    selectedId: String?,
    selectedRunning: Boolean,
    nowSeconds: Double,
    showActiveOverview: Boolean,
    projectsAsDropdown: Boolean,
    threadsCollapsed: Boolean,
    dispatchCollapsed: Boolean,
    pinnedCollapsed: Boolean,
    reorderingThreads: Boolean,
    troubles: Map<String, BotTrouble>,
    failingRoutines: Map<String, CronJob>,
    needsYou: List<NeedsYou>,
    rooms: List<Room>,
    roomsAvailable: Boolean,
    unreadRooms: Set<String>,
    roomsNotice: dev.hermeskotlin.ui.inbox.Words?,
    screenStates: Map<String, ProfileScreenState>,
    showScreenAction: Boolean,
    botActions: BotActions,
    pageCallbacks: AgentPageCallbacks,
    onToggleAgent: (Bot) -> Unit,
    onNewChatInAgent: (Bot) -> Unit,
    onOpenSession: (Bot, SessionSummary) -> Unit,
    onSessionActions: (Bot, SessionSummary) -> Unit,
    sessionMenu: ((Bot, SessionSummary) -> List<RowMenuItem>)?,
    swipeRight: (SessionSummary) -> SwipeAction?,
    swipeLeft: (SessionSummary) -> SwipeAction?,
    onOpenThread: (Bot, ThreadRow) -> Unit,
    onThreadActions: (Bot, ThreadRow) -> Unit,
    onReorderedThreads: (List<String>) -> Unit,
    onToggleThreads: () -> Unit,
    onToggleDispatch: () -> Unit,
    onTogglePinned: () -> Unit,
    onSelectProject: (Project?) -> Unit,
    onNewProject: (() -> Unit)?,
    onProjectOptions: (Project, Rect?) -> Unit,
    onSelectAttentionFilter: (AttentionFilter) -> Unit,
    onLoadMore: () -> Unit,
    onRefreshBound: () -> Unit,
    onOpenActiveDispatch: (DispatchSessionEntry) -> Unit,
    onOpenActiveBranchSession: (String, SessionSummary) -> Unit,
    onOpenRoom: (Room) -> Unit,
    onOpenDesktopRoom: (DesktopRoom) -> Unit,
    onNewRoom: () -> Unit,
    onRenameRoom: (Room, String) -> Unit,
    onDeleteRoom: (Room) -> Unit,
    onDismissRoomsNotice: () -> Unit,
    onRetryBots: () -> Unit,
    onDismissBotsNotice: () -> Unit,
    top: @Composable () -> Unit,
) {
    val orderedVisible = remember(botsState.bots, coordinatorProfile) {
        orderAgentsForSidebar(botsState.bots, coordinatorProfile)
    }
    val orderedHidden = remember(botsState.hidden, coordinatorProfile) {
        orderAgentsForSidebar(botsState.hidden, coordinatorProfile)
    }
    val sameName = remember(botsState.all) {
        botsState.all.groupBy { it.label.lowercase() }.filterValues { it.size > 1 }.keys
    }
    val roomFaces = remember(botsState.all, avatars) { BotFaces(botsState.all, avatars) }
    val desktopRooms = botsState.desktopRooms

    val activeItems = remember(
        showActiveOverview,
        botsState.all,
        needsYou,
        dispatchSessions,
        dispatchRooms,
        rooms,
        desktopRooms,
        unreadRooms,
        liveWaiting,
        liveRunning,
        agentBranches,
        nowSeconds,
        launchProfile,
    ) {
        if (!showActiveOverview) emptyList()
        else buildActiveAttentionItems(
            bots = botsState.all,
            needsYou = needsYou,
            dispatchSessions = dispatchSessions,
            dispatchRooms = dispatchRooms,
            rooms = rooms,
            desktopRooms = desktopRooms,
            unreadRooms = unreadRooms,
            liveWaiting = liveWaiting,
            liveRunning = liveRunning,
            agentBranches = agentBranches,
            nowSeconds = nowSeconds,
            launchProfile = launchProfile,
        )
    }

    var hiddenOpen by remember { mutableStateOf(false) }
    var recentExpandedAgents by remember { mutableStateOf<Set<String>>(emptySet()) }
    var startOverTarget by remember { mutableStateOf<Bot?>(null) }
    var deleteTarget by remember { mutableStateOf<Bot?>(null) }
    var roomAsk by remember { mutableStateOf<RoomAsk?>(null) }
    RoomAskDialogs(roomAsk, onDismiss = { roomAsk = null }, onRename = onRenameRoom, onDelete = onDeleteRoom)

    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    val boundProjectIndex = remember(boundUiState.projects) { ProjectIndex(boundUiState.projects) }
    val emptyProjectIndex = remember { ProjectIndex(emptyList()) }

    val heldUpByBot = remember(needsYou) {
        needsYou.filterIsInstance<NeedsYou.Answer>().associate { it.bot.name to it.waiting }
    }

    fun LazyListScope.agentBlock(bot: Bot, hidden: Boolean) {
        val isExpanded = bot.name in expandedAgents
        val isCoordinator = coordinatorProfile != null && bot.name.equals(coordinatorProfile, ignoreCase = true)
        val isBound = bot.name == boundAgentName

        val branchLoading: Boolean
        val branchError: String?
        val slice: AgentSessionsSlice
        val rowStatuses: Map<String, RowStatus>
        val rowDrafts: Set<String>
        val activeProjectIndex: ProjectIndex
        val activeShownProject: Project?
        val activeAttentionFilter: AttentionFilter

        if (isBound) {
            branchLoading = boundUiState.loading
            branchError = boundUiState.error
            rowStatuses = boundStatuses
            rowDrafts = drafts
            activeProjectIndex = boundProjectIndex
            activeShownProject = boundUiState.project
            activeAttentionFilter = attentionFilter
            slice = sliceAgentSessions(
                bot = bot,
                threads = boundThreads,
                listed = boundUiState.listed,
                allRows = boundUiState.sessions,
                project = boundUiState.project,
                projectIndex = boundProjectIndex,
                dispatchSessions = dispatchSessions,
                calendarAgent = calendarAgent,
                statuses = boundStatuses,
                drafts = drafts,
                selectedId = selectedId,
                attentionFilter = attentionFilter,
                threadsCollapsed = threadsCollapsed,
                dispatchCollapsed = dispatchCollapsed,
                pinnedCollapsed = pinnedCollapsed,
                recentExpanded = bot.name in recentExpandedAgents,
            )
        } else {
            val branch = agentBranches[bot.name] ?: AgentBranchState()
            branchLoading = branch.loading
            branchError = branch.error
            rowStatuses = branch.sessions.mapNotNull { s ->
                RowStatus(liveWaiting[s.id], s.unread, liveRunning[s.id] == true)
                    .takeIf { it.needsAttention || it.running }?.let { s.id to it }
            }.toMap()
            rowDrafts = emptySet()
            activeProjectIndex = emptyProjectIndex
            activeShownProject = null
            activeAttentionFilter = AttentionFilter.All
            slice = sliceAgentSessions(
                bot = bot,
                threads = branch.threads,
                listed = branch.sessions,
                allRows = branch.sessions,
                project = null,
                projectIndex = emptyProjectIndex,
                dispatchSessions = dispatchSessions,
                calendarAgent = calendarAgent,
                statuses = rowStatuses,
                drafts = emptySet(),
                selectedId = selectedId,
                attentionFilter = AttentionFilter.All,
                threadsCollapsed = threadsCollapsed,
                dispatchCollapsed = dispatchCollapsed,
                pinnedCollapsed = pinnedCollapsed,
                recentExpanded = bot.name in recentExpandedAgents,
            )
        }

        val chat = bot.canonicalSession
        val canonicalSelected = selectedId != null && chat != null && (selectedId == chat.id || selectedId == chat.openId)
        val canonicalIds = listOfNotNull(chat?.openId, chat?.id)
        val botLiveWaiting = heldUpByBot[bot.name] ?: canonicalIds.firstNotNullOfOrNull { liveWaiting[it] }
        val botLiveRunning = (canonicalSelected && selectedRunning) ||
            bot.isWorking(nowSeconds) ||
            canonicalIds.any { liveRunning[it] == true } ||
            dispatchSessions.values.any { (it.profile ?: launchProfile ?: Bot.DEFAULT) == bot.name && liveRunning[it.sessionId] == true }

        val branchWaitCount = dispatchSessions.values.count {
            (it.profile ?: launchProfile ?: Bot.DEFAULT) == bot.name && liveWaiting[it.sessionId] != null
        }

        item(key = "agent:${bot.name}") {
            AgentHeaderRow(
                bot = bot,
                picture = avatars[bot.name]?.takeIf { dev.hermeskotlin.core.bots.showsPicture(bot, it) },
                expanded = isExpanded,
                coordinator = isCoordinator,
                selected = canonicalSelected || (isExpanded && selectedId != null && slice.sections.let { sec ->
                    sec.threads.any { it.session.id == selectedId || it.thread.sessionId == selectedId } ||
                        sec.dispatch.any { it.session.id == selectedId } ||
                        sec.calendar?.id == selectedId ||
                        sec.others.any { it.id == selectedId }
                }),
                unread = bot.name in botsState.unread,
                thinking = canonicalSelected && selectedRunning,
                working = botLiveRunning,
                opening = botsState.opening == bot.name,
                hidden = hidden,
                showHandle = bot.label.lowercase() in sameName,
                trouble = troubles[bot.name],
                failingRoutine = failingRoutines[bot.name],
                waiting = botLiveWaiting,
                extraWaitCount = branchWaitCount,
                screenState = screenStates[bot.name],
                showScreenAction = showScreenAction,
                botActions = botActions,
                pageCallbacks = pageCallbacks,
                onToggle = { onToggleAgent(bot) },
                onNewChat = { onNewChatInAgent(bot) },
                onStartOver = { startOverTarget = bot },
                onDelete = { deleteTarget = bot },
            )
        }

        if (!isExpanded) return@agentBlock

        // Direct / permanent Bot Chat row
        item(key = "agent:${bot.name}:direct", contentType = SessionRowType) {
            AgentIndentBox {
                DirectBotChatRow(
                    bot = bot,
                    selected = canonicalSelected,
                    unread = bot.name in botsState.unread,
                    thinking = canonicalSelected && selectedRunning,
                    working = bot.isWorking(nowSeconds) || canonicalIds.any { liveRunning[it] == true },
                    opening = botsState.opening == bot.name,
                    waiting = botLiveWaiting,
                    onClick = { botActions.open(bot) },
                )
            }
        }

        if (isBound) {
            boundUiState.savedCopyAt?.takeIf { boundUiState.project == null }?.let { savedAt ->
                item(key = "agent:${bot.name}:saved-copy") {
                    AgentIndentBox {
                        ListNotice(
                            stringResource(Res.string.sessions_saved_copy_unreachable, savedCopyText(savedAt)),
                            action = stringResource(Res.string.sessions_try_again),
                            onAction = onRefreshBound,
                        )
                    }
                }
            }
        }

        when {
            branchLoading && slice.totalRecentCount == 0 && slice.shownDispatch.isEmpty() && slice.shownThreads.isEmpty() && slice.pinned.isEmpty() -> {
                item(key = "agent:${bot.name}:loading") { AgentIndentBox { ListSpinner() } }
            }
            branchError != null && slice.totalRecentCount == 0 -> {
                item(key = "agent:${bot.name}:error") {
                    AgentIndentBox {
                        ListNotice(
                            stringResource(Res.string.sessions_load_failed_detail, branchError),
                            action = if (isBound) stringResource(Res.string.sessions_try_again) else null,
                            onAction = onRefreshBound,
                        )
                    }
                }
            }
        }

        // Dispatch section inside this agent
        if (slice.sections.dispatch.isNotEmpty()) {
            item(key = "agent:${bot.name}:label-dispatch", contentType = SectionLabelType) {
                AgentIndentBox {
                    SectionToggle(
                        stringResource(Res.string.sessions_section_dispatch),
                        slice.sections.dispatch.size,
                        dispatchCollapsed,
                        stringResource(Res.string.sessions_show_dispatch),
                        stringResource(Res.string.sessions_hide_dispatch),
                        onToggleDispatch,
                    )
                }
            }
            items(
                slice.shownDispatch,
                key = { "agent:${bot.name}:dispatch:${it.session.id}" },
                contentType = { SessionRowType },
            ) { d ->
                AgentIndentBox {
                    SessionListSwipeRow(
                        displaySession = d.displaySession,
                        actionSession = d.session,
                        showProjectLabel = true,
                        selectedId = selectedId,
                        showSnippets = false,
                        sectioned = true,
                        statuses = rowStatuses,
                        drafts = rowDrafts,
                        projectIndex = activeProjectIndex,
                        shownProject = activeShownProject,
                        requesters = requesters,
                        swipeRight = swipeRight,
                        swipeLeft = swipeLeft,
                        onOpen = { onOpenSession(bot, it) },
                        onActions = { onSessionActions(bot, it) },
                        menu = sessionMenu?.let { mk -> { s -> mk(bot, s) } },
                    )
                }
            }
        }

        // Dedicated Calendar Agent session (if hosted by this agent)
        slice.shownCalendar?.let { cal ->
            item(key = "agent:${bot.name}:label-calendar", contentType = SectionLabelType) {
                AgentIndentBox { ListLabel(stringResource(Res.string.sessions_section_calendar)) }
            }
            item(key = "agent:${bot.name}:calendar:${cal.id}", contentType = SessionRowType) {
                val calLabel = stringResource(Res.string.sessions_section_calendar)
                val display = if (cal.title.isNullOrBlank()) cal.copy(title = CalendarAgentStore.AGENT_SESSION_TITLE) else cal
                AgentIndentBox {
                    SessionListSwipeRow(
                        displaySession = display,
                        actionSession = cal,
                        showProjectLabel = false,
                        selectedId = selectedId,
                        showSnippets = false,
                        sectioned = true,
                        statuses = rowStatuses,
                        drafts = rowDrafts,
                        projectIndex = activeProjectIndex,
                        shownProject = activeShownProject,
                        requesters = requesters,
                        swipeRight = swipeRight,
                        swipeLeft = swipeLeft,
                        onOpen = { onOpenSession(bot, it) },
                        onActions = { onSessionActions(bot, it) },
                        menu = sessionMenu?.let { mk -> { s -> mk(bot, s) } },
                        markIcon = Lucide.Calendar,
                        markDescription = calLabel,
                    )
                }
            }
        }

        // Threads section inside this agent
        if (slice.sections.threads.isNotEmpty()) {
            if (isBound && reorderingThreads) {
                item(key = "agent:${bot.name}:threads-reorder") {
                    AgentIndentBox {
                        ThreadReorder(slice.shownThreads, { activeProjectIndex.labelFor(it.session, activeShownProject) }, onDone = onReorderedThreads)
                    }
                }
            } else {
                item(key = "agent:${bot.name}:label-threads", contentType = SectionLabelType) {
                    AgentIndentBox {
                        SectionToggle(
                            stringResource(Res.string.sessions_section_threads),
                            slice.sections.threads.size,
                            threadsCollapsed,
                            stringResource(Res.string.sessions_show_threads),
                            stringResource(Res.string.sessions_hide_threads),
                            onToggleThreads,
                        )
                    }
                }
                items(
                    slice.shownThreads,
                    key = { "agent:${bot.name}:thread:${it.thread.id}" },
                    contentType = { SessionRowType },
                ) { row ->
                    AgentIndentBox {
                        SessionRow(
                            row.session.copy(title = row.thread.name),
                            selected = row.session.id == selectedId || row.thread.sessionId == selectedId,
                            showSnippet = false,
                            status = rowStatuses[row.session.id],
                            draft = row.session.id in rowDrafts,
                            projectLabel = activeProjectIndex.labelFor(row.session, activeShownProject),
                            thread = true,
                            onClick = { onOpenThread(bot, row) },
                            onActions = { onThreadActions(bot, row) },
                            modifier = if (isDesktop) Modifier.onSecondaryPress { onThreadActions(bot, row) } else Modifier,
                        )
                    }
                }
            }
        }

        // Pinned chats inside this agent
        val hasPinned = slice.sections.others.any { it.pinned }
        if (hasPinned) {
            val pinnedCount = slice.sections.others.count { it.pinned }
            item(key = "agent:${bot.name}:label-pinned", contentType = SectionLabelType) {
                AgentIndentBox {
                    SectionToggle(
                        stringResource(Res.string.sessions_section_pinned),
                        pinnedCount,
                        pinnedCollapsed,
                        stringResource(Res.string.sessions_show_pinned),
                        stringResource(Res.string.sessions_hide_pinned),
                        onTogglePinned,
                    )
                }
            }
            items(
                slice.pinned,
                key = { "agent:${bot.name}:pinned:${it.id}" },
                contentType = { SessionRowType },
            ) { s ->
                AgentIndentBox {
                    SessionListSwipeRow(
                        displaySession = s,
                        actionSession = s,
                        showProjectLabel = true,
                        selectedId = selectedId,
                        showSnippets = false,
                        sectioned = true,
                        statuses = rowStatuses,
                        drafts = rowDrafts,
                        projectIndex = activeProjectIndex,
                        shownProject = activeShownProject,
                        requesters = requesters,
                        swipeRight = swipeRight,
                        swipeLeft = swipeLeft,
                        onOpen = { onOpenSession(bot, it) },
                        onActions = { onSessionActions(bot, it) },
                        menu = sessionMenu?.let { mk -> { item -> mk(bot, item) } },
                    )
                }
            }
        }

        // Compact project / attention filters only when relevant on the bound agent
        if (isBound && !boundUiState.loading && boundUiState.error == null) {
            if (boundUiState.projects.isNotEmpty() || boundUiState.canMakeProjects) {
                item(key = "agent:${bot.name}:projects") {
                    AgentIndentBox {
                        ProjectFilters(
                            boundUiState.projects,
                            boundUiState.project,
                            asDropdown = projectsAsDropdown,
                            onSelect = onSelectProject,
                            onNew = onNewProject,
                            onOptions = onProjectOptions,
                        )
                    }
                }
            }
            val runningCount = boundUiState.listed.count { boundStatuses[it.id]?.running == true }
            val attentionCount = boundUiState.listed.count { boundStatuses[it.id]?.needsAttention == true }
            if (runningCount > 0 || attentionCount > 0 || activeAttentionFilter != AttentionFilter.All) {
                item(key = "agent:${bot.name}:filters") {
                    AgentIndentBox {
                        AttentionFilters(
                            selected = activeAttentionFilter,
                            running = runningCount,
                            needsAttention = attentionCount,
                            onSelect = onSelectAttentionFilter,
                        )
                    }
                }
            }
        }

        // Recent chats inside this agent (capped unless expanded)
        if (slice.shownRecent.isNotEmpty()) {
            item(key = "agent:${bot.name}:label-recent", contentType = SectionLabelType) {
                AgentIndentBox { ListLabel(stringResource(Res.string.sessions_section_recent)) }
            }
            items(
                slice.shownRecent,
                key = { "agent:${bot.name}:recent:${it.id}" },
                contentType = { SessionRowType },
            ) { s ->
                AgentIndentBox {
                    SessionListSwipeRow(
                        displaySession = s,
                        actionSession = s,
                        showProjectLabel = false,
                        selectedId = selectedId,
                        showSnippets = false,
                        sectioned = true,
                        statuses = rowStatuses,
                        drafts = rowDrafts,
                        projectIndex = activeProjectIndex,
                        shownProject = activeShownProject,
                        requesters = requesters,
                        swipeRight = swipeRight,
                        swipeLeft = swipeLeft,
                        onOpen = { onOpenSession(bot, it) },
                        onActions = { onSessionActions(bot, it) },
                        menu = sessionMenu?.let { mk -> { item -> mk(bot, item) } },
                    )
                }
            }
            if (slice.recentCapped || (bot.name in recentExpandedAgents && slice.totalRecentCount > DEFAULT_AGENT_RECENT_CAP)) {
                item(key = "agent:${bot.name}:recent-toggle") {
                    AgentIndentBox {
                        val isAll = bot.name in recentExpandedAgents
                        RecentCapToggle(
                            label = if (isAll) stringResource(Res.string.sessions_show_fewer) else stringResource(Res.string.sessions_show_all_count, slice.totalRecentCount),
                            onClick = {
                                recentExpandedAgents = if (isAll) recentExpandedAgents - bot.name else recentExpandedAgents + bot.name
                            },
                        )
                    }
                }
            }
            if (isBound && bot.name in recentExpandedAgents && boundUiState.canLoadMore && activeAttentionFilter == AttentionFilter.All && activeShownProject == null) {
                item(key = "agent:${bot.name}:load-more") {
                    AgentIndentBox {
                        if (boundUiState.loadingMore) {
                            ListSpinner()
                        } else {
                            RecentCapToggle(label = stringResource(Res.string.sessions_show_all_count, boundUiState.sessions.size + 1), onClick = onLoadMore)
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
        ) {
            item(key = "top") { top() }

            if (activeItems.isNotEmpty()) {
                item(key = "active-overview-label", contentType = SectionLabelType) {
                    SectionHeader(stringResource(Res.string.sessions_active_overview), top = 4.dp)
                }
                items(activeItems, key = { "active:${it.key}" }) { item ->
                    ActiveAttentionRow(
                        item = item,
                        avatars = avatars,
                        roomFaces = roomFaces,
                        onClick = {
                            when (item) {
                                is ActiveAttentionItem.BotNeedsYou -> {
                                    if (item.item is NeedsYou.Routine) botActions.routines(item.item.bot)
                                    else botActions.open(item.item.bot)
                                }
                                is ActiveAttentionItem.Dispatch -> onOpenActiveDispatch(item.entry)
                                is ActiveAttentionItem.BotWorking -> botActions.open(item.bot)
                                is ActiveAttentionItem.BranchSession -> onOpenActiveBranchSession(item.agentName, item.session)
                                is ActiveAttentionItem.DesktopRoomNeedsYou -> onOpenDesktopRoom(item.room)
                                is ActiveAttentionItem.RoomAttention -> onOpenRoom(item.room)
                            }
                        },
                    )
                }
            }

            item(key = "agents-label", contentType = SectionLabelType) {
                SectionHeader(stringResource(Res.string.sessions_agents_section), top = if (activeItems.isEmpty()) 2.dp else 8.dp) {
                    IconButton(
                        Lucide.Plus,
                        contentDescription = stringResource(Res.string.bots_new_bot),
                        onClick = botActions::create,
                        tint = Theme[colors][textTertiary],
                    )
                }
            }

            when {
                botsState.loading && botsState.all.isEmpty() -> item(key = "agents-loading") { ListSpinner() }
                botsState.error != null && botsState.all.isEmpty() -> item(key = "agents-error") {
                    ListNotice(
                        wordsThen(Res.string.bots_load_failed, botsState.error).text(),
                        action = stringResource(Res.string.bots_try_again),
                        onAction = onRetryBots,
                    )
                }
                botsState.all.isEmpty() -> item(key = "agents-empty") { ListNotice(stringResource(Res.string.bots_none)) }
            }

            orderedVisible.forEach { bot -> agentBlock(bot, hidden = false) }

            if (orderedHidden.isNotEmpty()) {
                item(key = "hidden-agents-toggle") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouchTarget)
                            .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                            .handCursor()
                            .clickable(
                                onClickLabel = stringResource(if (hiddenOpen) Res.string.bots_collapse_hidden else Res.string.bots_show_hidden),
                            ) { hiddenOpen = !hiddenOpen }
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UnstyledIcon(
                            if (hiddenOpen) Lucide.ChevronDown else Lucide.ChevronRight,
                            contentDescription = null,
                            tint = Theme[colors][textTertiary],
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(Res.string.bots_hidden_count, orderedHidden.size),
                            style = Theme[typography][caption],
                            color = Theme[colors][textTertiary],
                        )
                    }
                }
                if (hiddenOpen) {
                    orderedHidden.forEach { bot -> agentBlock(bot, hidden = true) }
                }
            }

            if (roomsAvailable || desktopRooms.isNotEmpty()) {
                item(key = "rooms-label", contentType = SectionLabelType) {
                    SectionHeader(stringResource(Res.string.rooms_section), top = 10.dp) {
                        if (roomsAvailable) {
                            IconButton(
                                Lucide.Plus,
                                contentDescription = stringResource(Res.string.rooms_new_room),
                                onClick = onNewRoom,
                                tint = Theme[colors][textTertiary],
                            )
                        }
                    }
                }
                if (roomsAvailable) {
                    roomsNotice?.let { message ->
                        item(key = "rooms-notice") {
                            MessageBanner(message.text(), onDismiss = onDismissRoomsNotice, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    if (rooms.isEmpty() && desktopRooms.isEmpty()) {
                        item(key = "rooms-empty") { ListNotice(stringResource(Res.string.rooms_none, 2, 6)) }
                    } else {
                        items(rooms, key = { "room:${it.roomId}" }) { room ->
                            RoomRow(
                                room = room,
                                faces = roomFaces,
                                unread = room.roomId in unreadRooms,
                                taskTitle = dispatchRooms[room.roomId]?.taskTitle,
                                onClick = { onOpenRoom(room) },
                                onRename = { roomAsk = RoomAsk.Rename(room) },
                                onDelete = { roomAsk = RoomAsk.Delete(room) },
                            )
                        }
                    }
                }
                if (desktopRooms.isNotEmpty()) {
                    item(key = "desktop-rooms-label", contentType = SectionLabelType) {
                        SectionHeader(stringResource(Res.string.rooms_desktop_status), top = 8.dp)
                    }
                    items(desktopRooms, key = { "desktop-room:${it.key}" }) { room ->
                        DesktopRoomRow(room, roomFaces, onClick = { onOpenDesktopRoom(room) })
                    }
                }
            }
        }
        VerticalScrollbar(listState, Modifier.align(Alignment.TopEnd).fillMaxHeight().padding(top = 2.dp, bottom = 96.dp, end = 1.dp))
        botsState.notice?.let { message ->
            MessageBanner(message.text(), onDismiss = onDismissBotsNotice, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
    }

    StartOverDialog(startOverTarget, onDismiss = { startOverTarget = null }, onConfirm = botActions::startFresh)
    DeleteBotDialog(deleteTarget, onDismiss = { deleteTarget = null }, onConfirm = botActions::delete)
}

@Composable
private fun AgentIndentBox(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 12.dp)) {
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
                .background(Theme[colors][stroke]),
        )
        Box(Modifier.weight(1f).padding(start = 6.dp)) {
            content()
        }
    }
}

@Composable
private fun RecentCapToggle(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (isDesktop) 30.dp else MinTouchTarget)
            .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][accentText],
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgentHeaderRow(
    bot: Bot,
    picture: ByteArray?,
    expanded: Boolean,
    coordinator: Boolean,
    selected: Boolean,
    unread: Boolean,
    thinking: Boolean,
    working: Boolean,
    opening: Boolean,
    hidden: Boolean,
    showHandle: Boolean,
    trouble: BotTrouble?,
    failingRoutine: CronJob?,
    waiting: Waiting?,
    extraWaitCount: Int,
    screenState: ProfileScreenState?,
    showScreenAction: Boolean,
    botActions: BotActions,
    pageCallbacks: AgentPageCallbacks,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onStartOver: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][if (isDesktop) radiusSmall else radiusMedium])
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var contextMenuOpen by remember { mutableStateOf(false) }

    val status = resolveBotStatusLine(bot, thinking, working, trouble, failingRoutine, waiting)
    val effectiveTone = if (extraWaitCount > 0 && status.tone != BotTone.Attention) BotTone.Attention else status.tone

    val archivedWord = stringResource(Res.string.sessions_nav_archived)
    val configWord = stringResource(Res.string.sessions_nav_config)
    val capabilitiesWord = stringResource(Res.string.sessions_nav_capabilities)
    val pluginsWord = stringResource(Res.string.sessions_nav_plugins)
    val insightsWord = stringResource(Res.string.sessions_nav_insights)

    val extraItems = listOf(
        RowMenuItem(archivedWord, Lucide.Archive) { pageCallbacks.onOpenArchived(bot) },
        RowMenuItem(configWord, Lucide.SlidersHorizontal) { pageCallbacks.onOpenConfig(bot) },
        RowMenuItem(capabilitiesWord, Lucide.Blocks) { pageCallbacks.onOpenCapabilities(bot) },
        RowMenuItem(pluginsWord, Lucide.Puzzle) { pageCallbacks.onOpenPlugins(bot) },
        RowMenuItem(insightsWord, Lucide.ChartColumn) { pageCallbacks.onOpenInsights(bot) },
    )
    val menuItems = rememberBotMenuItems(
        bot = bot,
        hidden = hidden,
        trouble = trouble,
        screenState = screenState,
        showScreenAction = showScreenAction,
        actions = botActions,
        onStartOver = onStartOver,
        onDelete = onDelete,
        extraItems = extraItems,
    )

    val clickLabel = stringResource(
        if (expanded) Res.string.sessions_collapse_agent else Res.string.sessions_expand_agent,
        bot.label,
    )
    val stateLabel = stringResource(if (expanded) Res.string.sessions_expanded else Res.string.sessions_collapsed)

    Box(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        RowContextMenu(contextMenuOpen, onDismiss = { contextMenuOpen = false }, items = menuItems)
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("agent_row_${bot.name}")
                .heightIn(min = if (isDesktop) 42.dp else MinTouchTarget)
                .clip(shape)
                .background(if (expanded || selected) Theme[colors][surface2] else Theme[colors][surface].copy(alpha = 0f))
                .handCursor()
                .combinedClickable(
                    onClick = onToggle,
                    onClickLabel = clickLabel,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onLongClickLabel = stringResource(Res.string.bots_actions_of, bot.label),
                    interactionSource = null,
                    indication = rememberHoverIndication(Theme[colors][text]),
                )
                .then(if (isDesktop) Modifier.onSecondaryPress { contextMenuOpen = true } else Modifier)
                .semantics { stateDescription = stateLabel }
                .alpha(if (hidden) 0.6f else 1f)
                .padding(start = 6.dp, end = 2.dp, top = if (isDesktop) 4.dp else 6.dp, bottom = if (isDesktop) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(
                Lucide.ChevronDown,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else -90f),
            )
            BotAvatar(bot, picture, size = if (isDesktop) 26.dp else 30.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (bot.meta.pinned) {
                        UnstyledIcon(Lucide.Pin, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(11.dp))
                    }
                    Text(
                        bot.label,
                        style = Theme[typography][body].copy(
                            fontSize = if (isDesktop) 14.sp else 14.5.sp,
                            fontWeight = if (unread || expanded) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = Theme[colors][text],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (coordinator) {
                        Text(
                            stringResource(Res.string.sessions_agent_coordinator),
                            style = Theme[typography][caption].copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                            color = Theme[colors][accentText],
                            maxLines = 1,
                            modifier = Modifier
                                .background(Theme[colors][accentSoft], RoundedCornerShape(Theme[radii][radiusSmall]))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    if (showHandle) {
                        Text(
                            "@${bot.name}",
                            style = Theme[typography][code].copy(fontSize = 11.sp),
                            color = Theme[colors][textTertiary],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (trouble != null || failingRoutine != null) {
                        UnstyledIcon(Lucide.TriangleAlert, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(12.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (opening) Spinner(Modifier.size(10.dp)) else StatusDot(effectiveTone, unread)
                    Text(
                        status.text,
                        style = Theme[typography][caption].copy(
                            fontSize = 11.5.sp,
                            fontStyle = if (status.italic) FontStyle.Italic else FontStyle.Normal,
                        ),
                        color = when (effectiveTone) {
                            BotTone.Attention -> Theme[colors][warning]
                            BotTone.Active -> Theme[colors][accentText]
                            BotTone.Idle -> Theme[colors][textTertiary]
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    Lucide.Plus,
                    contentDescription = stringResource(Res.string.bots_new_chat_with, bot.label),
                    onClick = onNewChat,
                    tint = Theme[colors][textTertiary],
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onExpandedChange = { menuOpen = it },
                    items = { menuItems().forEach { item -> MenuAction(item.label, item.icon, { menuOpen = false; item.run() }) } },
                ) {
                    IconButton(
                        Lucide.Ellipsis,
                        contentDescription = stringResource(Res.string.bots_actions_of, bot.label),
                        onClick = { menuOpen = true },
                        tint = Theme[colors][textTertiary],
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectBotChatRow(
    bot: Bot,
    selected: Boolean,
    unread: Boolean,
    thinking: Boolean,
    working: Boolean,
    opening: Boolean,
    waiting: Waiting?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][if (isDesktop) radiusSmall else radiusMedium])
    val tone = when {
        waiting != null -> RowTone.Approval
        working || thinking -> RowTone.Running
        unread -> RowTone.Reply
        else -> RowTone.Idle
    }
    val toneColor = Theme[colors][
        when (tone) {
            RowTone.Approval, RowTone.Question -> warning
            RowTone.Running, RowTone.Reply -> accentText
            RowTone.Draft -> textTertiary
            RowTone.Idle -> textMuted
        },
    ]
    val subtitle = when {
        waiting != null -> waiting.label
        thinking -> stringResource(Res.string.bots_thinking)
        working -> stringResource(Res.string.bots_working)
        unread -> stringResource(Res.string.sessions_status_new_reply)
        else -> bot.previewLine()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (isDesktop) 36.dp else MinTouchTarget)
            .clip(shape)
            .then(if (selected) Modifier.background(Theme[colors][surface2], shape) else Modifier)
            .handCursor()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = if (isDesktop) 5.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isDesktop) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (opening) {
            Box(Modifier.size(if (isDesktop) DESKTOP_DISC else 28.dp), contentAlignment = Alignment.Center) {
                Spinner(Modifier.size(12.dp))
            }
        } else {
            StatusDisc(tone, toneColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(Res.string.sessions_agent_direct_chat),
                style = Theme[typography][body].copy(
                    fontSize = if (isDesktop) 13.5.sp else 14.sp,
                    fontWeight = if (selected || tone != RowTone.Idle) FontWeight.Medium else FontWeight.Normal,
                ),
                color = Theme[colors][text],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = Theme[typography][caption].copy(fontSize = 12.sp),
                    color = if (tone != RowTone.Idle) toneColor else Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        bot.canonicalSession?.activityAt?.let { at ->
            Text(
                shortAge(at),
                style = Theme[typography][code].copy(fontSize = 11.5.sp),
                color = Theme[colors][textMuted],
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActiveAttentionRow(
    item: ActiveAttentionItem,
    avatars: Map<String, ByteArray>,
    roomFaces: BotFaces,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val isAttention = when (item) {
        is ActiveAttentionItem.BotNeedsYou -> true
        is ActiveAttentionItem.Dispatch -> item.waiting != null
        is ActiveAttentionItem.BranchSession -> item.waiting != null
        is ActiveAttentionItem.DesktopRoomNeedsYou -> true
        is ActiveAttentionItem.BotWorking, is ActiveAttentionItem.RoomAttention -> false
    }
    val rowBg = if (isAttention) Theme[colors][warningSoft] else Theme[colors][surface]
    val rowBorder = if (isAttention) Theme[colors][warning].copy(alpha = 0.35f) else Theme[colors][stroke]

    val title: String
    val subtitle: String
    val ownerBadge: String?
    val bot: Bot?

    when (item) {
        is ActiveAttentionItem.BotNeedsYou -> {
            bot = item.item.bot
            title = item.item.bot.label
            subtitle = item.item.reason.text()
            ownerBadge = null
        }
        is ActiveAttentionItem.Dispatch -> {
            bot = item.bot
            title = item.entry.taskTitle.ifBlank { item.entry.sessionId }
            subtitle = item.waiting?.label ?: stringResource(Res.string.sessions_status_running)
            ownerBadge = item.bot?.label ?: item.entry.profile
        }
        is ActiveAttentionItem.BotWorking -> {
            bot = item.bot
            title = item.bot.workerSession?.title?.takeIf { it.isNotBlank() }
                ?: item.bot.canonicalSession?.title?.takeIf { it.isNotBlank() }
                ?: item.bot.label
            subtitle = stringResource(Res.string.bots_working)
            ownerBadge = item.bot.label
        }
        is ActiveAttentionItem.BranchSession -> {
            bot = item.bot
            title = item.session.displayTitle
            subtitle = item.waiting?.label ?: stringResource(Res.string.sessions_status_running)
            ownerBadge = item.bot?.label ?: item.agentName
        }
        is ActiveAttentionItem.DesktopRoomNeedsYou -> {
            bot = null
            title = item.room.name
            subtitle = stringResource(Res.string.sessions_waiting_unknown)
            ownerBadge = stringResource(Res.string.rooms_section)
        }
        is ActiveAttentionItem.RoomAttention -> {
            bot = null
            title = item.dispatchEntry?.taskTitle?.takeIf { it.isNotBlank() } ?: item.room.name
            subtitle = stringResource(Res.string.sessions_status_new_reply)
            ownerBadge = item.room.name
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .testTag("active_item_${item.key}")
            .padding(vertical = 2.dp)
            .heightIn(min = if (isDesktop) 38.dp else MinTouchTarget)
            .clip(shape)
            .background(rowBg)
            .border(1.dp, rowBorder, shape)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            bot != null -> BotAvatar(bot, avatars[bot.name]?.takeIf { dev.hermeskotlin.core.bots.showsPicture(bot, it) }, size = 24.dp)
            item is ActiveAttentionItem.RoomAttention -> RoomFaces(item.room.members, roomFaces, size = 24.dp, max = 2, ring = rowBg)
            item is ActiveAttentionItem.DesktopRoomNeedsYou -> RoomFaces(item.room.roomMembers(), roomFaces, size = 24.dp, max = 2, ring = rowBg)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = Theme[typography][body].copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = Theme[colors][text],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!ownerBadge.isNullOrBlank()) {
                    Text(
                        ownerBadge,
                        style = Theme[typography][caption].copy(fontSize = 11.sp),
                        color = Theme[colors][textSecondary],
                        maxLines = 1,
                        modifier = Modifier
                            .background(Theme[colors][surface2], RoundedCornerShape(Theme[radii][radiusSmall]))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                subtitle,
                style = Theme[typography][caption].copy(fontSize = 11.5.sp),
                color = if (isAttention) Theme[colors][warning] else Theme[colors][accentText],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isAttention && item !is ActiveAttentionItem.RoomAttention) {
            Spinner(Modifier.size(12.dp), color = Theme[colors][accentText])
        }
    }
}
