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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheck
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.chat.QuickChoiceGroup
import dev.hermeskotlin.core.chat.QuickChoiceOption
import dev.hermeskotlin.core.chat.formatQuickChoiceReply
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.MinTouchTarget
import dev.hermeskotlin.designsystem.components.Tooltip
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.rememberHoverIndication
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.chat_quick_choice_edit
import dev.hermeskotlin.ui.resources.chat_quick_choice_edit_short
import dev.hermeskotlin.ui.resources.chat_quick_choice_multi_hint
import dev.hermeskotlin.ui.resources.chat_quick_choice_send
import dev.hermeskotlin.ui.resources.chat_quick_choice_send_count
import org.jetbrains.compose.resources.stringResource

/**
 * Non-blocking quick-reply choices attached below the active assistant reply.
 * Single choice sends immediately on click/tap, or inserts into the composer on edit/long-press.
 * Multiple choice lets the user toggle selections before sending or editing together.
 */
@Composable
internal fun QuickChoicesCard(
    replyKey: String,
    group: QuickChoiceGroup,
    sendEnabled: Boolean,
    onSend: (String) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerShape = RoundedCornerShape(Theme[radii][radiusLarge])
    var selectedMask by rememberSaveable(replyKey, group.options.size, group.multiSelect) {
        mutableIntStateOf(0)
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(containerShape)
            .background(Theme[colors][surface], containerShape)
            .border(1.dp, Theme[colors][stroke], containerShape)
            .padding(10.dp)
            .testTag("quick_choices_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!group.question.isNullOrBlank() || group.multiSelect) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!group.question.isNullOrBlank()) {
                    Text(
                        group.question.orEmpty(),
                        style = Theme[typography][bodySmall].copy(fontWeight = FontWeight.Medium),
                        color = Theme[colors][textSecondary],
                        modifier = Modifier.weight(1f),
                    )
                }
                if (group.multiSelect) {
                    Text(
                        stringResource(Res.string.chat_quick_choice_multi_hint),
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.options.forEachIndexed { index, option ->
                val isSelected = (selectedMask and (1 shl index)) != 0
                if (group.multiSelect) {
                    MultiChoiceRow(
                        index = index,
                        option = option,
                        selected = isSelected,
                        onToggle = { selectedMask = selectedMask xor (1 shl index) },
                    )
                } else {
                    SingleChoiceRow(
                        index = index,
                        option = option,
                        sendEnabled = sendEnabled,
                        onSend = { onSend(option.value) },
                        onFill = { onFill(option.value) },
                    )
                }
            }
        }

        if (group.multiSelect) {
            val chosen = remember(group.options, selectedMask) {
                group.options.filterIndexed { i, _ -> (selectedMask and (1 shl i)) != 0 }
            }
            val replyText = remember(chosen) { formatQuickChoiceReply(chosen) }
            val sendText = if (chosen.isEmpty()) {
                stringResource(Res.string.chat_quick_choice_send)
            } else {
                stringResource(Res.string.chat_quick_choice_send_count, chosen.size)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    text = stringResource(Res.string.chat_quick_choice_edit_short),
                    onClick = { onFill(replyText) },
                    modifier = Modifier.testTag("quick_choices_edit"),
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                    enabled = chosen.isNotEmpty(),
                    leadingIcon = Lucide.Pencil,
                )
                Button(
                    text = sendText,
                    onClick = { onSend(replyText) },
                    modifier = Modifier.testTag("quick_choices_send"),
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    enabled = sendEnabled && chosen.isNotEmpty(),
                    leadingIcon = Lucide.Send,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleChoiceRow(
    index: Int,
    option: QuickChoiceOption,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    onFill: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val rowInteraction = remember { MutableInteractionSource() }
    val editLabel = stringResource(Res.string.chat_quick_choice_edit)
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
                    onLongClickLabel = editLabel,
                    onLongClick = onFill,
                    onClick = onSend,
                )
                .alpha(if (sendEnabled) 1f else 0.55f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("quick_choice_option_$index"),
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
                    .testTag("quick_choice_edit_$index"),
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

@Composable
private fun MultiChoiceRow(
    index: Int,
    option: QuickChoiceOption,
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
            .testTag("quick_choice_option_$index"),
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
