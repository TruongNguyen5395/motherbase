package dev.hermeskotlin.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Sparkles
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.bots.Bot
import dev.hermeskotlin.core.chat.ChatState
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.pet.PetSprite
import dev.hermeskotlin.core.pet.PetState
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.body
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.MarkdownText
import dev.hermeskotlin.designsystem.components.MinTouchTarget
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.glow
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.components.scrollbar
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.eyebrow
import dev.hermeskotlin.designsystem.label as labelStyle
import dev.hermeskotlin.designsystem.onUserBubble
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusXLarge
import dev.hermeskotlin.designsystem.sidebar as sidebarColor
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.surface3
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textMuted
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.userBubble
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.ui.bots.BotAvatar
import dev.hermeskotlin.ui.chat.AttachButton
import dev.hermeskotlin.ui.chat.AttachmentPicker
import dev.hermeskotlin.ui.chat.ComposerKey
import dev.hermeskotlin.ui.chat.ComposerTextInput
import dev.hermeskotlin.ui.chat.ComposerTray
import dev.hermeskotlin.ui.chat.DictationButton
import dev.hermeskotlin.ui.chat.DockShadow
import dev.hermeskotlin.ui.chat.LocalModelPillBounds
import dev.hermeskotlin.ui.chat.ModelPickerState
import dev.hermeskotlin.ui.chat.ModelPill
import dev.hermeskotlin.ui.chat.ModelSelection
import dev.hermeskotlin.ui.chat.ReplyInteractionCard
import dev.hermeskotlin.ui.chat.SendButton
import dev.hermeskotlin.ui.chat.SendIcon
import dev.hermeskotlin.ui.chat.SentAttachments
import dev.hermeskotlin.ui.chat.Snug
import dev.hermeskotlin.ui.chat.bubbleGlow
import dev.hermeskotlin.ui.chat.composerKey
import dev.hermeskotlin.ui.chat.fileDrop
import dev.hermeskotlin.ui.chat.isPasteKey
import dev.hermeskotlin.ui.chat.userBubbleShape
import dev.hermeskotlin.ui.enterSends
import dev.hermeskotlin.ui.hasVoice
import dev.hermeskotlin.ui.imeVisible
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.pet.PetAvatar
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.chat_change_model
import dev.hermeskotlin.ui.resources.chat_model_title
import dev.hermeskotlin.ui.resources.chat_thinking
import dev.hermeskotlin.ui.resources.tasks_chat_empty
import dev.hermeskotlin.ui.resources.tasks_chat_log
import dev.hermeskotlin.ui.resources.tasks_no_channel
import dev.hermeskotlin.ui.resources.tasks_open_fullscreen
import dev.hermeskotlin.ui.resources.tasks_quick_steer_hint
import dev.hermeskotlin.ui.voice.DictationState
import org.jetbrains.compose.resources.stringResource

/** Floating circular bot/pet launcher button used by Dispatch Coordinator and Calendar Agent. */
@Composable
internal fun AgentFloatingTrigger(
    bot: Bot,
    avatarBytes: ByteArray?,
    petSprite: PetSprite?,
    petState: PetState,
    badgeActive: Boolean,
    busy: Boolean,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val borderColor = when {
        badgeActive -> Theme[colors][warning]
        busy -> Theme[colors][accent]
        else -> Theme[colors][strokeStrong]
    }
    Box(
        modifier
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            Modifier
                .size(52.dp)
                .dropShadow(shape, DockShadow)
                .clip(shape)
                .background(Theme[colors][surface], shape)
                .border(if (badgeActive) 2.dp else 1.dp, borderColor, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .handCursor(),
            contentAlignment = Alignment.Center,
        ) {
            if (petSprite != null) {
                PetAvatar(
                    sprite = petSprite,
                    state = petState,
                    size = 38.dp,
                )
            } else {
                BotAvatar(
                    bot = bot,
                    picture = avatarBytes,
                    size = 34.dp,
                )
            }
        }
        if (busy) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Theme[colors][surface], CircleShape)
                    .border(1.dp, Theme[colors][strokeStrong], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Spinner(Modifier.size(10.dp))
            }
        } else if (badgeActive) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Theme[colors][warning], CircleShape)
                    .border(1.5.dp, Theme[colors][surface], CircleShape),
            )
        }
    }
}

@Composable
internal fun TaskChannelLog(
    activeChannel: TaskChannel?,
    loading: Boolean,
    messages: List<ChannelChatLine>,
    bots: List<Bot> = emptyList(),
    botAvatars: Map<String, ByteArray> = emptyMap(),
    coordinatorBotName: String? = null,
    petSprite: PetSprite? = null,
    petState: PetState = PetState.Idle,
    emptyText: String? = null,
    error: String?,
    turnRunning: Boolean,
    turnStatus: String?,
    channelHeaderChip: (@Composable () -> Unit)? = null,
    onRefresh: () -> Unit,
    onOpenFullscreen: (() -> Unit)?,
    onSelectInteraction: ((String) -> Unit)? = null,
    onFillInteraction: ((String) -> Unit)? = null,
    appliedProposalStatuses: Map<String, String> = emptyMap(),
    onApplyProposal: ((String, List<String>, Boolean) -> Unit)? = null,
    onDiscardProposal: ((String) -> Unit)? = null,
    onRevertProposal: ((String) -> Unit)? = null,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lastTextLen = messages.lastOrNull()?.text?.length ?: 0
    val botByName = remember(bots) { bots.associateBy { it.name } }
    LaunchedEffect(activeChannel?.id, messages.size, lastTextLen, turnRunning) {
        if (messages.isNotEmpty() || turnRunning) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier) {
        if (showHeader) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.tasks_chat_log).uppercase(),
                    style = Theme[typography][eyebrow],
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                )
                if (channelHeaderChip != null) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        channelHeaderChip()
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (activeChannel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            Lucide.RefreshCw,
                            contentDescription = stringResource(Res.string.tasks_chat_log),
                            onClick = onRefresh,
                        )
                        if (onOpenFullscreen != null) {
                            IconButton(
                                Lucide.ExternalLink,
                                contentDescription = stringResource(Res.string.tasks_open_fullscreen),
                                onClick = onOpenFullscreen,
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .scrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    activeChannel == null && emptyText == null && messages.isEmpty() && !turnRunning -> {
                        val hintShape = RoundedCornerShape(Theme[radii][radiusLarge])
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(hintShape)
                                .background(Theme[colors][surface])
                                .border(1.dp, Theme[colors][stroke], hintShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UnstyledIcon(Lucide.MessageSquare, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(15.dp))
                            Text(
                                stringResource(Res.string.tasks_no_channel),
                                style = Theme[typography][bodySmall],
                                color = Theme[colors][textSecondary],
                            )
                        }
                    }
                    loading && messages.isEmpty() && !turnRunning -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(Modifier.size(14.dp))
                        Text(stringResource(Res.string.tasks_chat_log), style = Theme[typography][bodySmall], color = Theme[colors][textTertiary])
                    }
                    error != null && messages.isEmpty() && !turnRunning -> {
                        val errShape = RoundedCornerShape(Theme[radii][radiusLarge])
                        Text(
                            error,
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][danger],
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(errShape)
                                .background(Theme[colors][surface])
                                .border(1.dp, Theme[colors][danger], errShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    messages.isEmpty() && !turnRunning -> Text(
                        emptyText ?: stringResource(Res.string.tasks_chat_empty),
                        style = Theme[typography][bodySmall],
                        color = Theme[colors][textTertiary],
                    )
                    else -> Unit
                }

                messages.forEach { line ->
                    if (line.fromUser) {
                        val bubbleShape = userBubbleShape()
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (line.attachments.isNotEmpty()) {
                                Box(Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End)) {
                                    SentAttachments(line.attachments)
                                }
                            }
                            if (line.text.isNotBlank()) {
                                Box(Modifier.fillMaxWidth(0.86f).wrapContentWidth(Alignment.End)) {
                                    Text(
                                        line.text,
                                        style = Theme[typography][bodySmall],
                                        color = Theme[colors][onUserBubble],
                                        modifier = Modifier
                                            .glow(bubbleShape, bubbleGlow(Theme[colors][userBubble]))
                                            .clip(bubbleShape)
                                            .background(Theme[colors][userBubble], bubbleShape)
                                            .padding(horizontal = 13.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        val speakerName = line.speaker?.trim()?.takeIf { it.isNotEmpty() }
                        val speakerBot = speakerName?.let { name ->
                            botByName[name] ?: Bot(name = name, isDefault = name.equals(Bot.DEFAULT, ignoreCase = true))
                        }
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (speakerBot != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (petSprite != null &&
                                        (coordinatorBotName == null || speakerBot.name.equals(coordinatorBotName, ignoreCase = true))
                                    ) {
                                        PetAvatar(
                                            sprite = petSprite,
                                            state = petState,
                                            size = 20.dp,
                                        )
                                    } else {
                                        BotAvatar(
                                            bot = speakerBot,
                                            picture = botAvatars[speakerBot.name],
                                            size = 20.dp,
                                        )
                                    }
                                    Text(
                                        "@${speakerBot.name}",
                                        style = Theme[typography][caption].copy(fontWeight = FontWeight.SemiBold),
                                        color = Theme[colors][accentText],
                                    )
                                }
                            }
                            if (line.text.isNotBlank()) {
                                MarkdownText(
                                    text = line.text,
                                    streaming = line.streaming,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            val interaction = line.interaction
                            if (interaction != null) {
                                ReplyInteractionCard(
                                    replyKey = line.key,
                                    interaction = interaction,
                                    sendEnabled = !turnRunning,
                                    onSend = { text -> onSelectInteraction?.invoke(text) },
                                    onFill = { text -> onFillInteraction?.invoke(text) },
                                    onApplyProposal = onApplyProposal,
                                    onDiscardProposal = onDiscardProposal,
                                    onRevertProposal = onRevertProposal,
                                    appliedProposalStatuses = appliedProposalStatuses,
                                )
                            }
                        }
                    }
                }

                if (turnRunning) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spinner(Modifier.size(14.dp))
                        Text(
                            turnStatus ?: stringResource(Res.string.chat_thinking),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][accentText],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Theme[colors][sidebarColor]))),
            )
        }
    }
}

@Composable
internal fun TaskChatComposer(
    state: TextFieldState,
    connected: Boolean,
    attachments: List<OutgoingAttachment>,
    channelChatState: ChatState,
    modelPicker: ModelPickerState,
    showModelPicker: Boolean,
    attachmentPicker: AttachmentPicker?,
    dictation: DictationState = DictationState(),
    onToggleDictation: ((TextFieldState) -> Unit)? = null,
    placeholderText: String? = null,
    inputTestTag: String = "task_chat_composer_input",
    onRemoveAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onOpenModels: () -> Unit,
    onSend: () -> Unit,
) {
    val keyboardUp = imeVisible
    var focused by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf(false) }
    val canSend = (state.text.toString().isNotBlank() || attachments.isNotEmpty()) && connected
    val shape = RoundedCornerShape(Theme[radii][radiusXLarge])
    val borderColor = when {
        dropping -> Theme[colors][accent]
        focused -> Theme[colors][textTertiary]
        else -> Theme[colors][strokeStrong]
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                start = 14.dp,
                end = 14.dp,
                top = 6.dp,
                bottom = when {
                    keyboardUp -> 10.dp
                    isDesktop -> 16.dp
                    else -> 22.dp
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (attachmentPicker != null) {
                        Modifier.fileDrop(onHover = { dropping = it }) { attachmentPicker.attachFiles(it) }
                    } else {
                        Modifier
                    },
                )
                .dropShadow(shape, DockShadow)
                .clip(shape)
                .background(Theme[colors][surface], shape)
                .border(if (dropping) 2.dp else 1.dp, borderColor, shape)
                .padding(
                    start = 6.dp,
                    end = 6.dp,
                    top = if (attachments.isEmpty()) 12.dp else 8.dp,
                    bottom = 8.dp,
                ),
        ) {
            if (attachments.isNotEmpty()) {
                ComposerTray(attachments, onRemove = onRemoveAttachment)
            }
            ComposerTextInput(
                state = state,
                maxHeightInLines = if (isDesktop) 10 else 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .padding(horizontal = 10.dp)
                    .testTag(inputTestTag)
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (!enterSends) {
                            Modifier
                        } else {
                            Modifier.onPreviewKeyEvent { event ->
                                when {
                                    isPasteKey(event) -> attachmentPicker?.pasteAttachments() == true
                                    composerKey(event) == ComposerKey.Send && canSend -> {
                                        onSend()
                                        true
                                    }
                                    else -> false
                                }
                            }
                        },
                    ),
                placeholder = {
                    Text(
                        placeholderText ?: stringResource(Res.string.tasks_quick_steer_hint),
                        style = Theme[typography][body],
                        color = Theme[colors][textTertiary],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachButton(
                    onClick = onAttach,
                    enabled = connected && attachments.size < OutgoingAttachment.MAX_COUNT,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (showModelPicker) {
                        val hasModel = ModelSelection.of(channelChatState, modelPicker.catalog).model != null
                        if (hasModel) {
                            ModelPill(
                                state = channelChatState,
                                picker = modelPicker,
                                onClick = onOpenModels,
                            )
                        } else {
                            FallbackModelPill(
                                loading = modelPicker.loading,
                                enabled = connected,
                                onClick = onOpenModels,
                            )
                        }
                    }
                }
                if (onToggleDictation != null && (hasVoice || dictation.active)) {
                    Snug {
                        DictationButton(
                            state = dictation,
                            enabled = connected,
                            onClick = { onToggleDictation(state) },
                        )
                    }
                }
                Snug {
                    SendButton(
                        icon = SendIcon.Send,
                        onClick = onSend,
                        enabled = canSend,
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackModelPill(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bounds = LocalModelPillBounds.current
    val discHeight = if (isDesktop) 30.dp else 36.dp
    val changeModel = stringResource(Res.string.chat_change_model)
    Box(
        Modifier
            .then(if (bounds != null) Modifier.onGloballyPositioned { bounds.value = it.boundsInWindow() } else Modifier)
            .heightIn(min = MinTouchTarget)
            .testTag("task_model_picker_fallback")
            .handCursor(enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = changeModel },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .heightIn(min = discHeight)
                .clip(CircleShape)
                .background(Theme[colors][surface3], CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (loading) {
                Spinner(Modifier.size(13.dp))
            } else {
                UnstyledIcon(
                    Lucide.Sparkles,
                    contentDescription = null,
                    tint = if (enabled) Theme[colors][accentText] else Theme[colors][textMuted],
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                stringResource(Res.string.chat_model_title),
                style = Theme[typography][caption],
                color = if (enabled) Theme[colors][textSecondary] else Theme[colors][textMuted],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            UnstyledIcon(
                Lucide.ChevronDown,
                contentDescription = null,
                tint = Theme[colors][textTertiary],
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
internal fun SettingsSectionCard(
    title: String,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme[colors][surface2], shape)
            .border(1.dp, Theme[colors][stroke], shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) leading()
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = Theme[typography][labelStyle].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = Theme[typography][caption],
                        color = Theme[colors][textTertiary],
                    )
                }
            }
            if (trailing != null) trailing()
        }
        content()
    }
}
