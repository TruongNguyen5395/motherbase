package dev.hermeskotlin.ui.tasks

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import com.composeunstyled.DropdownMenuPanelScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClockCompact
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
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
import dev.hermeskotlin.designsystem.components.MenuAction
import dev.hermeskotlin.designsystem.components.MenuChoice
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.components.scrollbar
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.eyebrow
import dev.hermeskotlin.designsystem.heading
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusFull
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.radiusSmall
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.success
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.ui.bots.BotAvatar
import dev.hermeskotlin.ui.calendar.ScheduleProposalConflictDialog
import dev.hermeskotlin.ui.calendar.ScheduleTaskBlockDialog
import dev.hermeskotlin.ui.chat.DockShadow
import dev.hermeskotlin.ui.chat.ParameterizedActionContent
import dev.hermeskotlin.ui.clearOfWindowControls
import dev.hermeskotlin.ui.resources.*
import org.jetbrains.compose.resources.stringResource

internal val STATUS_OPTIONS = listOf(
    DirectusTask.STATUS_TODO,
    DirectusTask.STATUS_IN_PROGRESS,
    DirectusTask.STATUS_BLOCKED,
    DirectusTask.STATUS_DONE,
)

internal data class PickChannelTarget(
    val task: DirectusTask,
    val replaceChannel: TaskChannel? = null,
)

internal data class ChannelHandlerInfo(
    val shortLabel: String,
    val fullChipLabel: String,
    val bot: Bot?,
    val effectiveProfile: String?,
)

@Composable
internal fun MiniBadge(
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

/** Compact unread indicator dot with soft outer ring and optional numeric badge count. */
@Composable
internal fun UnreadIndicatorDot(
    color: Color,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(ringColor),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

/** Compact numeric unread counter paired with a small dot (avoids noisy text labels). */
@Composable
internal fun UnreadCountIndicator(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Theme[radii][radiusFull]))
            .background(Theme[colors][accentSoft])
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Theme[colors][accent]))
        Text(
            count.toString(),
            style = Theme[typography][caption].copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = Theme[colors][accentText],
            maxLines = 1,
        )
    }
}

@Composable
internal fun FeedbackBanner(message: String, onDismiss: () -> Unit) {
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
internal fun TaskDescriptionCard(
    description: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val descShape = RoundedCornerShape(Theme[radii][radiusMedium])
    Box(
        Modifier
            .fillMaxWidth()
            .clip(descShape)
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke], descShape)
            .clickable(onClick = onToggleExpand)
            .handCursor()
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        MarkdownText(
            text = description,
            textStyle = Theme[typography][bodySmall].copy(color = Theme[colors][textSecondary]),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier else Modifier.heightIn(max = 96.dp).clipToBounds()),
        )
    }
}

@Composable
internal fun TaskHandlerMismatchBanner(
    task: DirectusTask,
    activeChannel: TaskChannel?,
    bots: List<Bot>,
    busy: Boolean,
    onSwitchToAssignedAgent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeHandler = remember(activeChannel, bots, task.assignedBots) {
        activeChannel?.let { resolveChannelHandler(it, bots, task.assignedBots) }
    }
    val assignedPrimary = task.assignedBots.firstOrNull()?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
    val handlerMismatch = activeChannel != null &&
        !activeChannel.isRoom &&
        assignedPrimary != null &&
        activeHandler?.effectiveProfile?.equals(assignedPrimary, ignoreCase = true) == false
    if (!handlerMismatch) return

    val bannerShape = RoundedCornerShape(Theme[radii][radiusMedium])
    Row(
        modifier
            .fillMaxWidth()
            .clip(bannerShape)
            .background(Theme[colors][warningSoft].compositeOver(Theme[colors][surface]))
            .border(1.dp, Theme[colors][warning].copy(alpha = 0.45f), bannerShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.TriangleAlert, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(13.dp))
            Text(
                stringResource(
                    Res.string.tasks_agent_mismatch_notice,
                    "@$assignedPrimary",
                    "@${activeHandler?.effectiveProfile.orEmpty()}",
                ),
                style = Theme[typography][caption],
                color = Theme[colors][textColor],
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier
                .testTag("inspector_switch_assigned_agent")
                .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][strokeStrong], RoundedCornerShape(Theme[radii][radiusSmall]))
                .clickable(enabled = !busy, role = Role.Button, onClick = onSwitchToAssignedAgent)
                .handCursor(enabled = !busy)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.RefreshCw, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(11.dp))
            Text(
                stringResource(Res.string.tasks_switch_to_agent, "@$assignedPrimary"),
                style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][accentText],
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskMetadataPickers(
    currentProjectId: String?,
    currentProjectLabel: String,
    projects: List<DirectusProject>,
    unassignedLabel: String,
    currentStatus: String,
    matrixPosition: TaskMatrixPosition,
    onAssignProject: (String?) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onOpenMatrixPad: () -> Unit,
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

        MatrixPickerPill(
            position = matrixPosition,
            onClick = onOpenMatrixPad,
        )
    }
}

@Composable
internal fun PickerPill(
    icon: ImageVector,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        UnstyledIcon(Lucide.ChevronDown, contentDescription = null, tint = Theme[colors][textTertiary], modifier = Modifier.size(13.dp))
    }
}

@Composable
internal fun ActionPill(
    icon: ImageVector,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskChannelsSection(
    channels: List<TaskChannel>,
    bots: List<Bot>,
    assignedBots: List<String>,
    activeIdx: Int,
    busy: Boolean,
    onSelectChannel: (Int) -> Unit,
    onOpenPickSession: () -> Unit,
    onOpenPickRoom: () -> Unit,
    onReplaceWithSession: (TaskChannel) -> Unit,
    onReplaceWithRoom: (TaskChannel) -> Unit,
    onUnlinkChannel: (TaskChannel) -> Unit,
) {
    val menuHint = stringResource(Res.string.tasks_drop_pick_agent)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        channels.forEachIndexed { idx, ch ->
            val info = remember(ch, bots, assignedBots) { resolveChannelHandler(ch, bots, assignedBots) }
            val isActive = idx == activeIdx
            var menuOpen by remember(ch.id) { mutableStateOf(false) }
            DropdownMenu(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                items = {
                    if (!isActive) {
                        MenuAction(
                            text = stringResource(Res.string.tasks_open_channel),
                            icon = if (ch.isRoom) Lucide.Users else Lucide.MessageSquare,
                            onClick = {
                                menuOpen = false
                                onSelectChannel(idx)
                            },
                        )
                    }
                    ChannelDropMenuActions(
                        onReplaceWithSession = {
                            menuOpen = false
                            onReplaceWithSession(ch)
                        },
                        onReplaceWithRoom = {
                            menuOpen = false
                            onReplaceWithRoom(ch)
                        },
                        onUnlink = {
                            menuOpen = false
                            onUnlinkChannel(ch)
                        },
                    )
                },
            ) {
                Chip(
                    text = info.fullChipLabel,
                    selected = isActive,
                    onClick = {
                        if (!busy) {
                            if (channels.size == 1 || isActive) menuOpen = true else onSelectChannel(idx)
                        }
                    },
                    onLongClick = { if (!busy) menuOpen = true },
                    onLongClickLabel = menuHint,
                )
            }
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

internal fun resolveChannelHandler(
    channel: TaskChannel,
    bots: List<Bot>,
    assignedBots: List<String> = emptyList(),
): ChannelHandlerInfo {
    if (channel.isRoom) {
        val raw = channel.label.trim()
        val roomMembersText = when {
            raw.startsWith("Room (", ignoreCase = true) && raw.endsWith(")") ->
                raw.removePrefix("Room (").removePrefix("room (").removeSuffix(")")
            raw.isNotBlank() -> raw
            assignedBots.isNotEmpty() -> assignedBots.joinToString(", ") { "@${it.removePrefix("@")}" }
            else -> ""
        }
        val full = when {
            raw.startsWith("Room", ignoreCase = true) -> raw
            roomMembersText.isNotBlank() -> "Room · $roomMembersText"
            else -> "Room"
        }
        val short = if (roomMembersText.isNotBlank() && !raw.startsWith("Room", ignoreCase = true)) {
            "Room · $roomMembersText"
        } else {
            full
        }
        return ChannelHandlerInfo(
            shortLabel = short,
            fullChipLabel = full,
            bot = null,
            effectiveProfile = null,
        )
    }
    val defaultBot = bots.firstOrNull { it.isDefault }
    val explicitProfile = channel.profile?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
    val effectiveProfile = explicitProfile ?: defaultBot?.name ?: Bot.DEFAULT
    val matchedBot = explicitProfile?.let { p ->
        bots.firstOrNull { it.name.equals(p, ignoreCase = true) }
    } ?: defaultBot
    val botLabel = matchedBot?.label?.trim()?.takeIf { it.isNotBlank() }
    val displayName = when {
        botLabel != null && !botLabel.equals(effectiveProfile, ignoreCase = true) -> "$botLabel (@$effectiveProfile)"
        else -> "@$effectiveProfile"
    }
    val rawLabel = channel.label.trim()
    val fullLabel = when {
        rawLabel.isBlank() || rawLabel.equals("Session (@Hermes)", ignoreCase = true) -> displayName
        effectiveProfile.lowercase() in rawLabel.lowercase() -> rawLabel
        else -> "$rawLabel ($displayName)"
    }
    return ChannelHandlerInfo(
        shortLabel = displayName,
        fullChipLabel = fullLabel,
        bot = matchedBot,
        effectiveProfile = effectiveProfile,
    )
}

@Composable
internal fun DropdownMenuPanelScope.ChannelDropMenuActions(
    onReplaceWithSession: () -> Unit,
    onReplaceWithRoom: () -> Unit,
    onUnlink: () -> Unit,
) {
    MenuAction(
        text = stringResource(Res.string.tasks_drop_pick_agent),
        icon = Lucide.Bot,
        onClick = onReplaceWithSession,
    )
    MenuAction(
        text = stringResource(Res.string.tasks_drop_pick_room),
        icon = Lucide.Users,
        onClick = onReplaceWithRoom,
    )
    MenuAction(
        text = stringResource(Res.string.tasks_remove_channel),
        icon = Lucide.Trash2,
        onClick = onUnlink,
    )
}

@Composable
internal fun TaskChannelHandlerBadge(
    channels: List<TaskChannel>,
    activeIdx: Int,
    activeChannel: TaskChannel?,
    bots: List<Bot>,
    botAvatars: Map<String, ByteArray>,
    assignedBots: List<String>,
    busy: Boolean,
    onSelectChannel: (Int) -> Unit,
    onOpenPickSession: () -> Unit,
    onOpenPickRoom: () -> Unit,
    onReplaceWithSession: (TaskChannel) -> Unit,
    onReplaceWithRoom: (TaskChannel) -> Unit,
    onUnlinkChannel: (TaskChannel) -> Unit,
) {
    var menuOpen by remember(activeChannel?.id, channels.size) { mutableStateOf(false) }
    val activeInfo = remember(activeChannel, bots, assignedBots) {
        activeChannel?.let { resolveChannelHandler(it, bots, assignedBots) }
    }
    val unassignedHandler = remember(activeChannel, assignedBots, bots) {
        if (activeChannel != null) null
        else assignedBots.firstOrNull()?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }?.let { prof ->
            val bot = bots.firstOrNull { it.name.equals(prof, ignoreCase = true) }
            val label = bot?.label?.trim()?.takeIf { it.isNotBlank() && !it.equals(prof, ignoreCase = true) }
            val text = if (label != null) "$label (@$prof)" else "@$prof"
            bot to text
        }
    }
    val badgeShape = RoundedCornerShape(Theme[radii][radiusFull])
    val pillText = activeInfo?.shortLabel
        ?: unassignedHandler?.second
        ?: stringResource(Res.string.tasks_pick_agent_short)
    val badgeBot = activeInfo?.bot ?: unassignedHandler?.first

    DropdownMenu(
        expanded = menuOpen,
        onExpandedChange = { menuOpen = it },
        items = {
            if (activeChannel == null) {
                MenuAction(
                    text = stringResource(Res.string.tasks_pick_bot_title),
                    icon = Lucide.Bot,
                    onClick = {
                        menuOpen = false
                        onOpenPickSession()
                    },
                )
                MenuAction(
                    text = stringResource(Res.string.tasks_add_room),
                    icon = Lucide.Users,
                    onClick = {
                        menuOpen = false
                        onOpenPickRoom()
                    },
                )
            } else {
                if (channels.size > 1) {
                    channels.forEachIndexed { idx, ch ->
                        val info = resolveChannelHandler(ch, bots, assignedBots)
                        MenuChoice(
                            text = info.fullChipLabel,
                            icon = if (ch.isRoom) Lucide.Users else Lucide.Bot,
                            selected = idx == activeIdx,
                            onClick = {
                                menuOpen = false
                                onSelectChannel(idx)
                            },
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .height(1.dp)
                            .background(Theme[colors][stroke]),
                    )
                }
                ChannelDropMenuActions(
                    onReplaceWithSession = {
                        menuOpen = false
                        onReplaceWithSession(activeChannel)
                    },
                    onReplaceWithRoom = {
                        menuOpen = false
                        onReplaceWithRoom(activeChannel)
                    },
                    onUnlink = {
                        menuOpen = false
                        onUnlinkChannel(activeChannel)
                    },
                )
            }
        },
    ) {
        Row(
            Modifier
                .testTag("task_channel_handler_chip")
                .clip(badgeShape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][strokeStrong], badgeShape)
                .clickable(enabled = !busy, role = Role.Button) { menuOpen = true }
                .handCursor(enabled = !busy)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                activeChannel?.isRoom == true -> UnstyledIcon(
                    Lucide.Users,
                    contentDescription = null,
                    tint = Theme[colors][accentText],
                    modifier = Modifier.size(13.dp),
                )
                badgeBot != null -> BotAvatar(
                    bot = badgeBot,
                    picture = botAvatars[badgeBot.name],
                    size = 15.dp,
                )
                else -> UnstyledIcon(
                    Lucide.Bot,
                    contentDescription = null,
                    tint = Theme[colors][accentText],
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                pillText,
                style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (channels.size > 1) {
                Text(
                    "+${channels.size - 1}",
                    style = Theme[typography][caption],
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                )
            }
            UnstyledIcon(
                Lucide.ChevronDown,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskTimeBlocksSection(
    task: DirectusTask,
    timeBlocks: List<DispatchTimeBlock>,
    timezone: String,
    onScheduleBlock: (DispatchTimeBlock?) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onOpenCalendar: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface], RoundedCornerShape(Theme[radii][radiusMedium]))
            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(Theme[radii][radiusMedium]))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.Calendar, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(14.dp))
            Text(
                stringResource(Res.string.cal_time_blocks_heading),
                style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                modifier = Modifier.weight(1f),
            )
            task.estimatedMinutes?.let { est ->
                Text(
                    stringResource(Res.string.cal_estimated_minutes, est),
                    style = Theme[typography][caption],
                    color = Theme[colors][accentText],
                )
            }
            if (onOpenCalendar != null) {
                ActionPill(
                    icon = Lucide.ExternalLink,
                    label = stringResource(Res.string.cal_view_in_calendar),
                    loading = false,
                    enabled = true,
                    onClick = onOpenCalendar,
                )
            }
        }
        if (timeBlocks.isEmpty()) {
            Text(
                stringResource(Res.string.cal_no_time_blocks),
                style = Theme[typography][caption],
                color = Theme[colors][textTertiary],
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                timeBlocks.forEach { blk ->
                    val s = calendarZonedFromIso(blk.startAt, timezone.ifBlank { null })
                    val e = calendarZonedFromIso(blk.endAt, timezone.ifBlank { null })
                    val timeLabel = if (s != null && e != null) {
                        "${s.date.iso.drop(5)} ${formatClockCompact(s.minuteOfDay, use24Hour = true)}–${formatClockCompact(e.minuteOfDay, use24Hour = true)}"
                    } else blk.startAt
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(Theme[radii][radiusSmall]))
                            .background(Theme[colors][surface2])
                            .border(1.dp, Theme[colors][strokeStrong], RoundedCornerShape(Theme[radii][radiusSmall]))
                            .clickable(role = Role.Button) { onScheduleBlock(blk) }
                            .handCursor()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(timeLabel, style = Theme[typography][caption], color = Theme[colors][textColor])
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { onDeleteBlock(blk.id) }
                                .padding(2.dp),
                        ) {
                            UnstyledIcon(Lucide.X, contentDescription = stringResource(Res.string.cal_unschedule_block), tint = Theme[colors][textTertiary], modifier = Modifier.size(11.dp))
                        }
                    }
                }
            }
        }
        ActionPill(
            icon = Lucide.CalendarClock,
            label = stringResource(Res.string.cal_schedule_task),
            loading = false,
            enabled = true,
            onClick = { onScheduleBlock(null) },
        )
    }
}

@Composable
internal fun TaskStagedPlanSheet(
    task: DirectusTask,
    busy: Boolean,
    maxSheetHeight: Dp,
    onCollapse: () -> Unit,
    onPreparePlan: () -> Unit,
    onApprovePlan: (ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val plan = task.stagedPlan ?: return
    val pendingApproval = plan.approvedAt == null
    val approveFallback = stringResource(Res.string.tasks_approve_action)
    val sheetScroll = rememberScrollState()
    val borderColor = if (pendingApproval) Theme[colors][warning].copy(alpha = 0.6f) else Theme[colors][strokeStrong]

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .testTag("task_plan_sheet")
            .dropShadow(shape, DockShadow)
            .clip(shape)
            .background(Theme[colors][surface], shape)
            .border(1.dp, borderColor, shape)
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
                    tint = if (pendingApproval) Theme[colors][warning] else Theme[colors][accentText],
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(Res.string.tasks_plan_heading).uppercase(),
                    style = Theme[typography][eyebrow],
                    color = if (pendingApproval) Theme[colors][warning] else Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
internal fun TaskPlanDockBar(
    hasPlan: Boolean,
    pendingApproval: Boolean,
    expanded: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val enabled = hasPlan || !busy
    val surfaceBg = Theme[colors][surface]
    val fillBg = if (pendingApproval) Theme[colors][warningSoft].compositeOver(surfaceBg) else surfaceBg
    val borderColor = if (pendingApproval) Theme[colors][warning].copy(alpha = 0.7f) else Theme[colors][strokeStrong]
    val iconTint = if (pendingApproval) Theme[colors][warning] else Theme[colors][accentText]
    val labelColor = when {
        !enabled -> Theme[colors][textMuted]
        pendingApproval -> Theme[colors][warning]
        else -> Theme[colors][textColor]
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
            .testTag("task_plan_dock_button")
            .clip(shape)
            .background(fillBg, shape)
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
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            stringResource(if (hasPlan) Res.string.tasks_plan_heading else Res.string.tasks_prepare_plan),
            style = Theme[typography][bodySmall].copy(
                fontWeight = if (pendingApproval) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasPlan) {
            UnstyledIcon(
                if (expanded) Lucide.ChevronDown else Lucide.ChevronUp,
                contentDescription = null,
                tint = if (pendingApproval) Theme[colors][warning] else Theme[colors][textTertiary],
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
internal fun PickSessionBotDialog(
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onPick: (profile: String?) -> Unit,
) {
    val visibleBots = remember(bots) { bots.filterNot { it.meta.hidden } }
    val defaultBot = remember(visibleBots) { visibleBots.firstOrNull { it.isDefault } }
    val nonDefaultBots = remember(visibleBots, defaultBot) {
        if (defaultBot != null) visibleBots.filterNot { it.name == defaultBot.name } else visibleBots
    }
    Dialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.tasks_pick_bot_title),
        actions = {
            Button(stringResource(Res.string.common_cancel), onClick = onDismiss, variant = ButtonVariant.Ghost)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (defaultBot != null) {
                BotPickRow(
                    label = "${defaultBot.label} (${stringResource(Res.string.tasks_default_bot)})",
                    sub = "@${defaultBot.name}",
                    onClick = { onPick(defaultBot.name) },
                )
            } else {
                BotPickRow(
                    label = stringResource(Res.string.tasks_default_bot),
                    sub = null,
                    onClick = { onPick(null) },
                )
            }
            nonDefaultBots.forEach { bot ->
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
internal fun PickRoomBotsDialog(
    bots: List<Bot>,
    confirmLabel: String = stringResource(Res.string.tasks_add_room),
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
                confirmLabel,
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
internal fun stateDotColor(task: DirectusTask, overdueDays: Int): Color = when {
    task.status == DirectusTask.STATUS_BLOCKED || overdueDays > 0 -> Theme[colors][danger]
    task.agentState == TaskContext.STATE_RUNNING || task.agentState == TaskContext.STATE_QUEUED || task.status == DirectusTask.STATUS_IN_PROGRESS -> Theme[colors][accent]
    task.status == DirectusTask.STATUS_DONE -> Theme[colors][success]
    else -> Theme[colors][textTertiary]
}

internal fun taskStatusLabel(status: String): String = when (status) {
    DirectusTask.STATUS_IN_PROGRESS -> "In progress"
    DirectusTask.STATUS_BLOCKED -> "Blocked"
    DirectusTask.STATUS_DONE -> "Done"
    else -> "Todo"
}

@Composable
internal fun agentStateLabel(agentState: String): String = when (agentState) {
    TaskContext.STATE_RUNNING -> stringResource(Res.string.tasks_state_running)
    TaskContext.STATE_QUEUED -> stringResource(Res.string.tasks_state_queued)
    else -> stringResource(Res.string.tasks_state_idle)
}

/**
 * Full-featured Task Inspector panel embedded inside Dispatch session or room views.
 * Shares metadata editors, channel management, schedule blocks, and Action Proposal UI with [TaskDetailInspector].
 */
@Composable
fun DispatchTaskPanelHost(
    viewModel: TasksViewModel,
    state: TasksUiState,
    activeChannelId: String?,
    clearWindowControls: Boolean = true,
    onClose: () -> Unit,
    onOpenCalendar: (() -> Unit)? = null,
) {
    DispatchTaskSidePanel(
        state = state,
        activeChannelId = activeChannelId,
        clearWindowControls = clearWindowControls,
        onClose = onClose,
        onAssignProject = { pid -> state.selectedTask?.let { viewModel.assignProject(it.id, pid) } },
        onUpdateStatus = { st -> state.selectedTask?.let { viewModel.updateTaskStatus(it.id, st) } },
        onUpdateMatrix = { imp, urg -> state.selectedTask?.let { viewModel.updateTaskMatrix(it.id, imp, urg) } },
        onSelectChannel = viewModel::selectChannel,
        onCreateSessionChannel = viewModel::openOrCreateSessionChannel,
        onCreateRoomChannel = viewModel::openOrCreateRoomChannel,
        onReplaceWithSession = viewModel::replaceWithSessionChannel,
        onReplaceWithRoom = viewModel::replaceWithRoomChannel,
        onUnlinkChannel = viewModel::unlinkChannel,
        onSwitchToAssignedAgent = viewModel::switchToAssignedAgent,
        onPreparePlan = viewModel::preparePlan,
        onApprovePlan = viewModel::approveStagedPlan,
        onOpenScheduleBlock = viewModel::openScheduleTaskBlock,
        onDeleteTimeBlock = viewModel::deleteTaskTimeBlock,
        onOpenCalendar = onOpenCalendar,
        onDismissFeedback = viewModel::clearFeedback,
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

@Composable
internal fun DispatchTaskSidePanel(
    state: TasksUiState,
    activeChannelId: String?,
    clearWindowControls: Boolean = true,
    onClose: () -> Unit,
    onAssignProject: (String?) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onUpdateMatrix: (MatrixLevel, MatrixLevel) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onCreateSessionChannel: (DirectusTask, String?) -> Unit,
    onCreateRoomChannel: (DirectusTask, List<Bot>) -> Unit,
    onReplaceWithSession: (DirectusTask, TaskChannel, String?) -> Unit,
    onReplaceWithRoom: (DirectusTask, TaskChannel, List<Bot>) -> Unit,
    onUnlinkChannel: (DirectusTask, TaskChannel) -> Unit,
    onSwitchToAssignedAgent: (DirectusTask) -> Unit,
    onPreparePlan: (DirectusTask) -> Unit,
    onApprovePlan: (DirectusTask, ReplyInteraction.Option, Map<String, String>, List<TweakDeltaInput>) -> Unit,
    onOpenScheduleBlock: (DirectusTask, DispatchTimeBlock?) -> Unit,
    onDeleteTimeBlock: (String) -> Unit,
    onOpenCalendar: (() -> Unit)? = null,
    onDismissFeedback: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val task = state.selectedTask
    var pickSessionFor by remember(task?.id) { mutableStateOf<PickChannelTarget?>(null) }
    var pickRoomFor by remember(task?.id) { mutableStateOf<PickChannelTarget?>(null) }
    var matrixPadOpen by remember(task?.id) { mutableStateOf(false) }

    Column(modifier.fillMaxSize().testTag("dispatch_task_panel")) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (clearWindowControls) Modifier.clearOfWindowControls() else Modifier)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                task?.title ?: stringResource(Res.string.tasks_panel_title),
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

        state.actionFeedback?.let { msg ->
            FeedbackBanner(message = msg, onDismiss = onDismissFeedback)
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][stroke]))

        if (task == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    Spinner(Modifier.size(24.dp).testTag("dispatch_task_panel_loading"))
                } else {
                    Text(
                        state.error ?: stringResource(Res.string.tasks_error_load),
                        style = Theme[typography][bodySmall],
                        color = Theme[colors][textSecondary],
                    )
                }
            }
            return@Column
        }

        val busy = task.id in state.busyTaskIds
        val matrixPos = remember(task, state.todayEpochDay) { resolveTaskMatrix(task, state.todayEpochDay) }
        val unassignedLabel = stringResource(Res.string.tasks_unassigned_project)
        val currentProjectLabel = state.projects.firstOrNull { it.id == task.projectId }?.name ?: unassignedLabel
        val activeChannel = activeChannelId?.let { cid -> task.channels.firstOrNull { it.id == cid } } ?: state.activeChannel
        val activeChannelIdx = activeChannel?.let { ch -> task.channels.indexOfFirst { it.id == ch.id }.takeIf { it >= 0 } }
            ?: state.activeChannelIdx
        val timeBlocks = state.taskTimeBlocks[task.id].orEmpty()
        val plan = task.stagedPlan
        var descExpanded by remember(task.id) { mutableStateOf(false) }
        var planSheetOpen by remember(task.id) { mutableStateOf(false) }
        var openPlanOnArrival by remember(task.id) { mutableStateOf(false) }
        var lastSeenPlanStamp by remember(task.id) { mutableStateOf(plan?.generatedAt to plan?.summary) }

        LaunchedEffect(task.id, plan?.generatedAt, plan?.summary) {
            val currentStamp = plan?.generatedAt to plan?.summary
            if (plan != null && openPlanOnArrival && currentStamp != lastSeenPlanStamp) {
                planSheetOpen = true
                openPlanOnArrival = false
            }
            lastSeenPlanStamp = currentStamp
        }
        val triggerPreparePlan: () -> Unit = {
            openPlanOnArrival = true
            onPreparePlan(task)
        }

        val matrixCode = "${matrixLevelShortCode(matrixPos.impact)}/${matrixLevelShortCode(matrixPos.effectiveUrgency)}"
        val summaryText = "$currentProjectLabel · ${taskStatusLabel(task.status)} · ${quadrantTitle(matrixPos.quadrant)} $matrixCode"
        val bodyScroll = rememberScrollState()

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sheetMaxHeight = (maxHeight * 0.78f).coerceIn(180.dp, 420.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .scrollbar(bodyScroll)
                    .verticalScroll(bodyScroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(quadrantAccentColor(matrixPos.quadrant)))
                    Text(
                        summaryText,
                        style = Theme[typography][caption],
                        color = Theme[colors][textSecondary],
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                TaskHandlerMismatchBanner(
                    task = task,
                    activeChannel = activeChannel,
                    bots = state.bots,
                    busy = busy,
                    onSwitchToAssignedAgent = { onSwitchToAssignedAgent(task) },
                )

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
                    projects = state.projects,
                    unassignedLabel = unassignedLabel,
                    currentStatus = task.status,
                    matrixPosition = matrixPos,
                    onAssignProject = onAssignProject,
                    onUpdateStatus = onUpdateStatus,
                    onOpenMatrixPad = { matrixPadOpen = true },
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.tasks_channel_heading).uppercase(),
                        style = Theme[typography][eyebrow],
                        color = Theme[colors][textTertiary],
                    )
                    TaskChannelsSection(
                        channels = task.channels,
                        bots = state.bots,
                        assignedBots = task.assignedBots,
                        activeIdx = activeChannelIdx,
                        busy = busy,
                        onSelectChannel = onSelectChannel,
                        onOpenPickSession = { pickSessionFor = PickChannelTarget(task) },
                        onOpenPickRoom = { pickRoomFor = PickChannelTarget(task) },
                        onReplaceWithSession = { ch -> pickSessionFor = PickChannelTarget(task, replaceChannel = ch) },
                        onReplaceWithRoom = { ch -> pickRoomFor = PickChannelTarget(task, replaceChannel = ch) },
                        onUnlinkChannel = { ch -> onUnlinkChannel(task, ch) },
                    )
                }

                if (state.calendarPluginAvailable || timeBlocks.isNotEmpty() || task.estimatedMinutes != null) {
                    TaskTimeBlocksSection(
                        task = task,
                        timeBlocks = timeBlocks,
                        timezone = state.calendarTimezone,
                        onScheduleBlock = { blk -> onOpenScheduleBlock(task, blk) },
                        onDeleteBlock = onDeleteTimeBlock,
                        onOpenCalendar = onOpenCalendar,
                    )
                }
            }

            AnimatedVisibility(
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
                            onApprovePlan(task, opt, selectedParams, tweaks)
                        },
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            TaskPlanDockBar(
                hasPlan = plan != null,
                pendingApproval = plan != null && plan.approvedAt == null,
                expanded = plan != null && planSheetOpen,
                busy = busy,
                onClick = {
                    if (plan == null) triggerPreparePlan() else planSheetOpen = !planSheetOpen
                },
            )
        }
    }

    if (matrixPadOpen && task != null) {
        TaskMatrixPadDialog(
            task = task,
            todayEpochDay = state.todayEpochDay,
            onDismiss = { matrixPadOpen = false },
            onApply = onUpdateMatrix,
        )
    }

    pickSessionFor?.let { target ->
        PickSessionBotDialog(
            bots = state.bots,
            onDismiss = { pickSessionFor = null },
            onPick = { profile ->
                if (target.replaceChannel != null) {
                    onReplaceWithSession(target.task, target.replaceChannel, profile)
                } else {
                    onCreateSessionChannel(target.task, profile)
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
                    onReplaceWithRoom(target.task, target.replaceChannel, members)
                } else {
                    onCreateRoomChannel(target.task, members)
                }
                pickRoomFor = null
            },
        )
    }
}
