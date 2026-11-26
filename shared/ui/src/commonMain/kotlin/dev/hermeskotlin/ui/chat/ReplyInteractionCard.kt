package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheck
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.calendar.ScheduleOperation
import dev.hermeskotlin.core.calendar.ScheduleProposal as CalendarScheduleProposal
import dev.hermeskotlin.core.calendar.calendarZonedFromIso
import dev.hermeskotlin.core.calendar.formatClockCompact
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.chat.computeActionTweaks
import dev.hermeskotlin.core.chat.formatActionCardReply
import dev.hermeskotlin.core.chat.formatInteractionChoices
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.DropdownMenu
import dev.hermeskotlin.designsystem.components.MenuChoice
import dev.hermeskotlin.designsystem.components.MinTouchTarget
import dev.hermeskotlin.designsystem.components.Tooltip
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.rememberHoverIndication
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.success
import dev.hermeskotlin.designsystem.successSoft
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Shared container for non-blocking interactive reply widgets (`motherbase-interact`) attached below
 * the active assistant reply. Dispatches rendering by [ReplyInteraction] subtype.
 */
@Composable
internal fun ReplyInteractionCard(
    replyKey: String,
    interaction: ReplyInteraction,
    sendEnabled: Boolean,
    onSend: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTweaks: ((taskId: String, projectId: String?, summary: String?, optionLabel: String, tweaks: List<TweakDeltaInput>) -> Unit)? = null,
    onApplyProposal: ((proposalId: String, selectedOpIds: List<String>, force: Boolean) -> Unit)? = null,
    onDiscardProposal: ((proposalId: String) -> Unit)? = null,
    onRevertProposal: ((proposalId: String) -> Unit)? = null,
    appliedProposalStatuses: Map<String, String> = emptyMap(),
) {
    val containerShape = RoundedCornerShape(Theme[radii][radiusLarge])
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(containerShape)
            .background(Theme[colors][surface], containerShape)
            .border(1.dp, Theme[colors][stroke], containerShape)
            .padding(10.dp)
            .testTag("reply_interaction_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (interaction) {
            is ReplyInteraction.Choices -> ChoicesInteractionContent(
                replyKey = replyKey,
                choices = interaction,
                sendEnabled = sendEnabled,
                onSend = onSend,
                onFill = onFill,
            )
            is ReplyInteraction.ActionCard -> ParameterizedActionContent(
                cardKey = replyKey,
                question = interaction.question,
                params = interaction.params,
                options = interaction.options,
                sendEnabled = sendEnabled,
                onAction = { option, selected, tweaks ->
                    val tid = interaction.taskId
                    if (tid != null && tweaks.isNotEmpty()) {
                        onTweaks?.invoke(tid, interaction.projectId, interaction.question, option.label, tweaks)
                    }
                    onSend(formatActionCardReply(option, interaction.params, selected))
                },
                onFill = { option, selected ->
                    onFill(formatActionCardReply(option, interaction.params, selected))
                },
            )
            is ReplyInteraction.ScheduleProposal -> ScheduleProposalContent(
                cardKey = replyKey,
                proposal = interaction.proposal,
                overrideStatus = appliedProposalStatuses[interaction.proposal.id],
                enabled = sendEnabled,
                onApply = { selectedIds, force ->
                    val pid = interaction.proposal.id
                    if (pid.isNotBlank() && onApplyProposal != null) {
                        onApplyProposal(pid, selectedIds, force)
                    } else {
                        onSend("Apply schedule proposal: ${interaction.proposal.title} (${selectedIds.size} items)")
                    }
                },
                onDiscard = {
                    val pid = interaction.proposal.id
                    if (pid.isNotBlank() && onDiscardProposal != null) {
                        onDiscardProposal(pid)
                    } else {
                        onSend("Discard schedule proposal: ${interaction.proposal.title}")
                    }
                },
                onRevert = {
                    val pid = interaction.proposal.id
                    if (pid.isNotBlank() && onRevertProposal != null) {
                        onRevertProposal(pid)
                    }
                },
            )
        }
    }
}

/**
 * Shared interactive card body with inline tweakable parameter chips (`params`) plus action buttons.
 * Reused directly by both chat replies ([ReplyInteraction.ActionCard]) and the Tasks Command Board
 * right-rail so parameter tweaking and tweak-delta computation share one UI implementation.
 */
@Composable
internal fun ParameterizedActionContent(
    cardKey: String,
    question: String?,
    params: List<ReplyInteraction.Param>,
    options: List<ReplyInteraction.Option>,
    sendEnabled: Boolean,
    onAction: (option: ReplyInteraction.Option, selected: Map<String, String>, tweaks: List<TweakDeltaInput>) -> Unit,
    onFill: ((option: ReplyInteraction.Option, selected: Map<String, String>) -> Unit)? = null,
) {
    val selectedValues = remember(cardKey, params) {
        mutableStateMapOf<String, String>().apply {
            params.forEach { put(it.key, it.defaultValue) }
        }
    }

    if (!question.isNullOrBlank()) {
        Text(
            question,
            style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][textSecondary],
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }

    if (params.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            params.forEach { param ->
                val current = selectedValues[param.key] ?: param.defaultValue
                val tweaked = current != param.defaultValue
                InlineParamChip(
                    param = param,
                    value = current,
                    tweaked = tweaked,
                    onPick = { next -> selectedValues[param.key] = next },
                )
            }
        }
    }

    val snapshot = params.associate { it.key to (selectedValues[it.key] ?: it.defaultValue) }
    val tweaks = remember(params, snapshot) { computeActionTweaks(params, snapshot) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, option ->
            SingleChoiceOptionRow(
                index = index,
                option = option,
                sendEnabled = sendEnabled,
                onSend = { onAction(option, snapshot, tweaks) },
                onFill = onFill?.let { fill -> { fill(option, snapshot) } },
            )
        }
    }
}

@Composable
private fun InlineParamChip(
    param: ReplyInteraction.Param,
    value: String,
    tweaked: Boolean,
    onPick: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val interaction = remember { MutableInteractionSource() }
    val fill = if (tweaked) Theme[colors][accentSoft] else Theme[colors][surface2]
    val outline = if (tweaked) Theme[colors][accent] else Theme[colors][strokeStrong]
    val minHeight = if (isDesktop) 30.dp else 38.dp
    val cycleOrMenu = {
        if (param.options.size <= 3) {
            val idx = param.options.indexOf(value)
            val next = param.options[(idx + 1).mod(param.options.size)]
            onPick(next)
        } else {
            menuOpen = true
        }
    }

    Box {
        DropdownMenu(
            expanded = menuOpen,
            onExpandedChange = { menuOpen = it },
            above = !isDesktop,
            items = {
                param.options.forEach { opt ->
                    MenuChoice(
                        text = opt,
                        icon = Lucide.CircleDot,
                        selected = opt == value,
                        onClick = {
                            menuOpen = false
                            onPick(opt)
                        },
                    )
                }
            },
        ) {
            Row(
                Modifier
                    .heightIn(min = minHeight)
                    .clip(shape)
                    .background(fill, shape)
                    .border(1.dp, outline, shape)
                    .handCursor(param.options.size > 1)
                    .indication(interaction, rememberHoverIndication(Theme[colors][textColor], shape))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = param.options.size > 1,
                        role = Role.Button,
                        onClick = cycleOrMenu,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("interaction_param_${param.key}"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${param.label}:",
                    style = Theme[typography][caption],
                    color = Theme[colors][textSecondary],
                    maxLines = 1,
                )
                Text(
                    value,
                    style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][if (tweaked) accentText else textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (param.options.size > 1) {
                    UnstyledIcon(
                        Lucide.ChevronDown,
                        contentDescription = null,
                        tint = Theme[colors][if (tweaked) accentText else textTertiary],
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoicesInteractionContent(
    replyKey: String,
    choices: ReplyInteraction.Choices,
    sendEnabled: Boolean,
    onSend: (String) -> Unit,
    onFill: (String) -> Unit,
) {
    var selectedMask by rememberSaveable(replyKey, choices.options.size, choices.multiSelect) {
        mutableIntStateOf(0)
    }

    if (!choices.question.isNullOrBlank() || choices.multiSelect) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!choices.question.isNullOrBlank()) {
                Text(
                    choices.question.orEmpty(),
                    style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.weight(1f),
                )
            }
            if (choices.multiSelect) {
                Text(
                    stringResource(Res.string.chat_interaction_multi_hint),
                    style = Theme[typography][caption],
                    color = Theme[colors][textTertiary],
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.options.forEachIndexed { index, option ->
            val isSelected = (selectedMask and (1 shl index)) != 0
            if (choices.multiSelect) {
                MultiChoiceOptionRow(
                    index = index,
                    option = option,
                    selected = isSelected,
                    onToggle = { selectedMask = selectedMask xor (1 shl index) },
                )
            } else {
                SingleChoiceOptionRow(
                    index = index,
                    option = option,
                    sendEnabled = sendEnabled,
                    onSend = { onSend(option.value) },
                    onFill = { onFill(option.value) },
                )
            }
        }
    }

    if (choices.multiSelect) {
        val chosen = remember(choices.options, selectedMask) {
            choices.options.filterIndexed { i, _ -> (selectedMask and (1 shl i)) != 0 }
        }
        val replyText = remember(chosen) { formatInteractionChoices(chosen) }
        val sendText = if (chosen.isEmpty()) {
            stringResource(Res.string.chat_interaction_send)
        } else {
            stringResource(Res.string.chat_interaction_send_count, chosen.size)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                text = stringResource(Res.string.chat_interaction_edit_short),
                onClick = { onFill(replyText) },
                modifier = Modifier.testTag("interaction_choices_edit"),
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                enabled = chosen.isNotEmpty(),
                leadingIcon = Lucide.Pencil,
            )
            Button(
                text = sendText,
                onClick = { onSend(replyText) },
                modifier = Modifier.testTag("interaction_choices_send"),
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = sendEnabled && chosen.isNotEmpty(),
                leadingIcon = Lucide.Send,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleChoiceOptionRow(
    index: Int,
    option: ReplyInteraction.Option,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    onFill: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val rowInteraction = remember { MutableInteractionSource() }
    val editLabel = stringResource(Res.string.chat_interaction_edit)
    val rowHeight = if (isDesktop) 36.dp else MinTouchTarget
    val editButtonSize = if (isDesktop) 32.dp else 40.dp

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .clip(shape)
            .background(Theme[colors][surface2], shape)
            .border(1.dp, Theme[colors][strokeStrong], shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = rowHeight)
                .handCursor(sendEnabled)
                .indication(rowInteraction, rememberHoverIndication(Theme[colors][textColor], shape))
                .combinedClickable(
                    interactionSource = rowInteraction,
                    indication = null,
                    role = Role.Button,
                    enabled = sendEnabled,
                    onLongClickLabel = editLabel.takeIf { onFill != null },
                    onLongClick = onFill,
                    onClick = onSend,
                )
                .alpha(if (sendEnabled) 1f else 0.55f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("interaction_choice_option_$index"),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                option.label,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
            )
            option.description?.let { desc ->
                Text(
                    desc,
                    style = Theme[typography][caption],
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (onFill != null) {
            Tooltip(editLabel) {
                val btnShape = RoundedCornerShape(Theme[radii][radiusMedium])
                UnstyledButton(
                    onClick = onFill,
                    indication = rememberHoverIndication(Theme[colors][textSecondary], Theme[colors][accent], btnShape),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(editButtonSize)
                        .clip(btnShape)
                        .handCursor()
                        .testTag("interaction_choice_edit_$index"),
                ) {
                    UnstyledIcon(
                        Lucide.Pencil,
                        contentDescription = editLabel,
                        tint = Theme[colors][textTertiary],
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChoiceOptionRow(
    index: Int,
    option: ReplyInteraction.Option,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val interaction = remember { MutableInteractionSource() }
    val fill = if (selected) Theme[colors][accentSoft] else Theme[colors][surface2]
    val outline = if (selected) Theme[colors][accent] else Theme[colors][strokeStrong]
    val rowHeight = if (isDesktop) 36.dp else MinTouchTarget

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, outline, shape)
            .handCursor()
            .indication(interaction, rememberHoverIndication(Theme[colors][textColor], shape))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onClick = onToggle,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("interaction_choice_option_$index"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(
            if (selected) Lucide.SquareCheck else Lucide.Square,
            contentDescription = null,
            tint = Theme[colors][if (selected) accentText else textTertiary],
            modifier = Modifier.size(17.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                option.label,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][textColor],
            )
            option.description?.let { desc ->
                Text(
                    desc,
                    style = Theme[typography][caption],
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Interactive staged schedule proposal card allowing selective item toggling, external-attendee
 * notification warnings, conflict banners, and explicit `Apply` / `Discard` / `Revert` confirmation.
 */
@Composable
internal fun ScheduleProposalContent(
    cardKey: String,
    proposal: CalendarScheduleProposal,
    overrideStatus: String? = null,
    enabled: Boolean = true,
    onApply: (selectedOpIds: List<String>, force: Boolean) -> Unit,
    onDiscard: () -> Unit,
    onRevert: () -> Unit,
) {
    val effectiveStatus = overrideStatus ?: proposal.status
    val isStaged = effectiveStatus == CalendarScheduleProposal.STATUS_STAGED
    val selectedOps = remember(cardKey, proposal.id, proposal.operations) {
        mutableStateMapOf<String, Boolean>().apply {
            proposal.operations.forEach { put(it.opId, it.selected) }
        }
    }
    val chosenOps = proposal.operations.filter { selectedOps[it.opId] != false }
    val attendeeCount = chosenOps.sumOf { it.attendees.size }
    val hasConflicts = chosenOps.any { it.conflicts.isNotEmpty() }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledIcon(
            Lucide.Calendar,
            contentDescription = null,
            tint = Theme[colors][accentText],
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                proposal.title,
                style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
            )
            proposal.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    summary,
                    style = Theme[typography][caption],
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        val statusBadge = when (effectiveStatus) {
            CalendarScheduleProposal.STATUS_APPLIED -> stringResource(Res.string.cal_proposal_applied) to (Theme[colors][successSoft] to Theme[colors][success])
            CalendarScheduleProposal.STATUS_DISCARDED -> stringResource(Res.string.cal_proposal_discarded) to (Theme[colors][surface2] to Theme[colors][textTertiary])
            CalendarScheduleProposal.STATUS_REVERTED -> stringResource(Res.string.cal_proposal_reverted) to (Theme[colors][surface2] to Theme[colors][textTertiary])
            else -> null
        }
        if (statusBadge != null) {
            val (label, pair) = statusBadge
            Text(
                label,
                style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
                color = pair.second,
                modifier = Modifier
                    .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                    .background(pair.first)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        proposal.operations.forEachIndexed { idx, op ->
            val checked = selectedOps[op.opId] != false
            val rowShape = RoundedCornerShape(Theme[radii][radiusMedium])
            val rowBg = if (checked && isStaged) Theme[colors][accentSoft] else Theme[colors][surface2]
            val rowBorder = if (checked && isStaged) Theme[colors][accent] else Theme[colors][stroke]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(rowBg, rowShape)
                    .border(1.dp, rowBorder, rowShape)
                    .then(
                        if (isStaged && enabled) {
                            Modifier.handCursor().clickable(role = Role.Checkbox) {
                                selectedOps[op.opId] = !checked
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("proposal_op_row_$idx"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isStaged) {
                    UnstyledIcon(
                        if (checked) Lucide.SquareCheck else Lucide.Square,
                        contentDescription = null,
                        tint = Theme[colors][if (checked) accentText else textTertiary],
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        op.title,
                        style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                        color = Theme[colors][textColor],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val timeLabel = formatProposalOpWindow(op)
                    val meta = listOfNotNull(timeLabel, op.calendarName, op.reason).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = Theme[typography][caption],
                            color = Theme[colors][textSecondary],
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (op.conflicts.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UnstyledIcon(
                                Lucide.TriangleAlert,
                                contentDescription = null,
                                tint = Theme[colors][warning],
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                stringResource(Res.string.cal_proposal_conflict_warning, op.conflicts.first().title),
                                style = Theme[typography][caption],
                                color = Theme[colors][warning],
                            )
                        }
                    }
                }
            }
        }
    }

    if (isStaged && attendeeCount > 0) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                .background(Theme[colors][warningSoft])
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledIcon(Lucide.TriangleAlert, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(14.dp))
            Text(
                stringResource(Res.string.cal_proposal_attendee_warning, attendeeCount),
                style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
                color = Theme[colors][warning],
            )
        }
    }

    if (isStaged) {
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                text = stringResource(Res.string.cal_proposal_discard),
                onClick = onDiscard,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                enabled = enabled,
                leadingIcon = Lucide.X,
                modifier = Modifier.testTag("proposal_discard_button"),
            )
            Button(
                text = if (hasConflicts) {
                    stringResource(Res.string.cal_proposal_force_apply)
                } else {
                    stringResource(Res.string.cal_proposal_apply_count, chosenOps.size)
                },
                onClick = { onApply(chosenOps.map { it.opId }, hasConflicts) },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
                enabled = enabled && chosenOps.isNotEmpty(),
                leadingIcon = Lucide.Check,
                modifier = Modifier.testTag("proposal_apply_button"),
            )
        }
    } else if (effectiveStatus == CalendarScheduleProposal.STATUS_APPLIED) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                text = stringResource(Res.string.cal_proposal_revert),
                onClick = onRevert,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
                enabled = enabled,
                leadingIcon = Lucide.RotateCcw,
                modifier = Modifier.testTag("proposal_revert_button"),
            )
        }
    }
}

private fun formatProposalOpWindow(op: ScheduleOperation): String? {
    if (op.allDay) return op.startDate
    val s = op.startAt?.let { calendarZonedFromIso(it, op.timezone) } ?: return null
    val e = op.endAt?.let { calendarZonedFromIso(it, op.timezone) }
    val startStr = "${s.date.iso} ${formatClockCompact(s.minuteOfDay, use24Hour = true)}"
    return if (e != null) "$startStr – ${formatClockCompact(e.minuteOfDay, use24Hour = true)}" else startStr
}
