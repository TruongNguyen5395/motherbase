package dev.hermeskotlin.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Minimize2
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.X
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.display.ProfileScreenState
import dev.hermeskotlin.core.display.ScreenQuality
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.background
import dev.hermeskotlin.designsystem.body
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.code
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.Dialog
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.TextField
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.components.plainTextClipEntry
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.label
import dev.hermeskotlin.designsystem.onAccent
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.stroke
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
import dev.hermeskotlin.designsystem.well
import dev.hermeskotlin.ui.LocalCaptureAllKeys
import dev.hermeskotlin.ui.PlatformBackHandler
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.rememberChangingConfigurations
import dev.hermeskotlin.ui.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val MAX_PASTE_BYTES = 256 * 1024

// Standard X11 keysyms for accessory key bar
private object XKeySyms {
    const val ESCAPE = 0xFF1B
    const val TAB = 0xFF09
    const val RETURN = 0xFF0D
    const val BACKSPACE = 0xFF08
    const val LEFT = 0xFF51
    const val UP = 0xFF52
    const val RIGHT = 0xFF53
    const val DOWN = 0xFF54
    const val CONTROL_L = 0xFFE3
    const val ALT_L = 0xFFE9
}

@Composable
fun BotScreenPane(
    viewModel: BotScreenViewModel,
    title: String,
    fullScreenOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val screenState = profiles[ui.profile] ?: ProfileScreenState(profile = ui.profile)
    val status = screenState.status
    val interactive = screenState.iHoldLease
    val otherHolds = screenState.otherHoldsLease
    val controller = remember { BotScreenController() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val captureAllKeys = LocalCaptureAllKeys.current.value

    // Preserve active lease across Android OTP app switches (`ON_STOP` drops without clean 1000 close)
    val lifecycleOwner = LocalLifecycleOwner.current
    val changingConfigurations = rememberChangingConfigurations()
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!changingConfigurations()) viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BotScreenOrientationEffect(landscape = fullScreenOverlay && ui.landscapeFullscreen)

    PlatformBackHandler(enabled = fullScreenOverlay) {
        viewModel.requestClose()
    }

    val safeModifier = if (fullScreenOverlay) {
        modifier.fillMaxSize().background(Theme[colors][background]).statusBarsPadding().navigationBarsPadding()
    } else {
        modifier.fillMaxSize().background(Theme[colors][background])
    }

    BoxWithConstraints(safeModifier) {
        val compactToolbar = maxWidth < 480.dp

        Column(Modifier.fillMaxSize()) {
            ScreenHeaderBar(
                title = screenState.desktopName.ifBlank { title },
                running = status?.running == true,
                interactive = interactive,
                otherHolds = otherHolds,
                actionBusy = screenState.actionBusy,
                quality = ui.quality,
                compactToolbar = compactToolbar,
                fullScreenOverlay = fullScreenOverlay,
                landscape = ui.landscapeFullscreen,
                keyboardBarOpen = ui.keyboardBarOpen,
                onTakeOver = viewModel::takeOver,
                onHandBack = { viewModel.handBack(force = false) },
                onForceHandBack = { viewModel.handBack(force = true) },
                onCycleQuality = viewModel::cycleQuality,
                onToggleKeyboard = viewModel::toggleKeyboardBar,
                onToggleLandscape = viewModel::toggleLandscape,
                onStop = viewModel::requestStopScreen,
                onClose = viewModel::requestClose,
            )

            if (isDesktop && interactive && captureAllKeys) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Theme[colors][warningSoft])
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UnstyledIcon(Lucide.Keyboard, contentDescription = null, tint = Theme[colors][warning], modifier = Modifier.size(13.dp))
                    Text(
                        stringResource(Res.string.screen_capture_hint),
                        style = Theme[typography][caption],
                        color = Theme[colors][warning],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ui.remoteClipboardText?.let { clipText ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Theme[colors][surface2])
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.screen_remote_clipboard, clipText.take(64).replace('\n', ' ')),
                        style = Theme[typography][caption],
                        color = Theme[colors][textSecondary],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = stringResource(Res.string.screen_copy),
                        onClick = {
                            scope.launch { clipboard.setClipEntry(plainTextClipEntry(clipText)) }
                            viewModel.dismissRemoteClipboard()
                        },
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Small,
                        leadingIcon = Lucide.Copy,
                    )
                    IconButton(Lucide.X, stringResource(Res.string.screen_dismiss), onClick = viewModel::dismissRemoteClipboard, iconSize = 15.dp)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    screenState.unavailable -> ScreenMessageCard(
                        title = stringResource(Res.string.screen_title),
                        message = stringResource(Res.string.screen_unavailable),
                    )
                    status?.imageSwitch != null -> {
                        val sw = status.imageSwitch!!
                        ScreenActionCard(
                            title = stringResource(Res.string.screen_sandbox_switch_title),
                            message = stringResource(Res.string.screen_sandbox_switch_body, sw.currentImage, sw.desktopImage),
                            command = sw.rebuildCommand,
                            primaryLabel = stringResource(Res.string.screen_sandbox_switch_button),
                            busy = screenState.actionBusy,
                            onPrimary = { viewModel.switchSandboxImage(approve = true) },
                        )
                    }
                    status?.needsInstall == true || screenState.installing || screenState.installLogs.isNotEmpty() -> {
                        InstallRuntimeCard(
                            missing = status?.missing.orEmpty(),
                            command = screenState.installCommand ?: status?.installCommand,
                            installing = screenState.installing,
                            logs = screenState.installLogs,
                            exitCode = screenState.installExitCode,
                            sudoPending = screenState.installSudoRequestId != null,
                            onInstall = viewModel::startInstall,
                            onSubmitSudo = viewModel::respondInstallSudo,
                            onRefresh = viewModel::refreshStatus,
                        )
                    }
                    status?.blocker != null -> {
                        val ramLine = if (status.memoryAvailableMb != null && status.memoryLimitMb != null) {
                            "${status.blocker}\n\n" + stringResource(
                                Res.string.screen_blocker_memory,
                                status.memoryAvailableMb!!,
                                status.memoryLimitMb!!,
                            )
                        } else {
                            status.blocker!!
                        }
                        ScreenActionCard(
                            title = stringResource(Res.string.screen_title),
                            message = ramLine,
                            primaryLabel = stringResource(Res.string.screen_retry),
                            busy = screenState.loading || screenState.actionBusy,
                            onPrimary = viewModel::startScreen,
                        )
                    }
                    screenState.loading && status == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Spinner(Modifier.size(28.dp))
                        }
                    }
                    status?.running == false -> {
                        ScreenActionCard(
                            title = stringResource(Res.string.screen_status_off),
                            message = screenState.error ?: stringResource(Res.string.screen_desktop_only_hint),
                            primaryLabel = stringResource(Res.string.screen_start),
                            busy = screenState.actionBusy,
                            onPrimary = viewModel::startScreen,
                        )
                    }
                    ui.bridge != null -> {
                        BotScreenSurface(
                            bridge = ui.bridge!!,
                            interactive = interactive,
                            quality = ui.quality,
                            touchMode = ui.touchMode,
                            dragLocked = ui.dragLocked,
                            controller = controller,
                            onEvent = { _, _ -> },
                            onFailure = viewModel::noteSurfaceFailure,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!ui.wsConnected && ui.disconnectNotice == null) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xBB0D1117))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Spinner(Modifier.size(14.dp))
                                    Text(stringResource(Res.string.screen_status_watching), style = Theme[typography][caption], color = Color.White)
                                }
                            }
                        }
                    }
                    else -> {
                        val msg = ui.surfaceFailure ?: screenState.error ?: disconnectNoticeText(ui.disconnectNotice)
                        ScreenActionCard(
                            title = stringResource(Res.string.screen_title),
                            message = msg.ifBlank { stringResource(Res.string.screen_notice_lost) },
                            primaryLabel = stringResource(Res.string.screen_reconnect),
                            busy = screenState.loading,
                            onPrimary = viewModel::connectLive,
                        )
                    }
                }
            }

            if ((compactToolbar || !isDesktop || ui.keyboardBarOpen) && status?.running == true && ui.bridge != null) {
                MobileControlDock(
                    interactive = interactive,
                    compactToolbar = compactToolbar,
                    quality = ui.quality,
                    touchMode = ui.touchMode,
                    dragLocked = ui.dragLocked,
                    keyboardOpen = ui.keyboardBarOpen,
                    controller = controller,
                    onCycleQuality = viewModel::cycleQuality,
                    onStop = viewModel::requestStopScreen,
                    onSelectTouchMode = viewModel::setTouchMode,
                    onToggleDragLock = viewModel::toggleDragLock,
                    onToggleKeyboard = viewModel::toggleKeyboardBar,
                )
            }
        }
    }

    Dialog(
        visible = ui.showCloseHoldConfirm,
        onDismissRequest = viewModel::dismissCloseHoldConfirm,
        title = stringResource(Res.string.screen_close_confirm_title),
        message = stringResource(Res.string.screen_close_confirm_body),
        actions = {
            Button(
                text = stringResource(Res.string.screen_close_minimize),
                onClick = { viewModel.closeScreen(handBackFirst = false) },
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
            )
            Button(
                text = stringResource(Res.string.screen_close_hand_back),
                onClick = { viewModel.closeScreen(handBackFirst = true) },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Small,
            )
        },
    )

    Dialog(
        visible = ui.showStopConfirm,
        onDismissRequest = viewModel::dismissStopConfirm,
        title = stringResource(Res.string.screen_stop_confirm_title),
        message = stringResource(Res.string.screen_stop_confirm_body),
        actions = {
            Button(
                text = stringResource(Res.string.common_cancel),
                onClick = viewModel::dismissStopConfirm,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            )
            Button(
                text = stringResource(Res.string.screen_stop_force),
                onClick = { viewModel.confirmStopScreen(force = true) },
                variant = ButtonVariant.Danger,
                size = ButtonSize.Small,
            )
        },
    )
}

@Composable
private fun ScreenHeaderBar(
    title: String,
    running: Boolean,
    interactive: Boolean,
    otherHolds: Boolean,
    actionBusy: Boolean,
    quality: ScreenQuality,
    compactToolbar: Boolean,
    fullScreenOverlay: Boolean,
    landscape: Boolean,
    keyboardBarOpen: Boolean,
    onTakeOver: () -> Unit,
    onHandBack: () -> Unit,
    onForceHandBack: () -> Unit,
    onCycleQuality: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onToggleLandscape: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke])
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val statusColor = when {
            !running -> Theme[colors][textMuted]
            interactive -> Theme[colors][warning]
            otherHolds -> Theme[colors][danger]
            else -> Theme[colors][success]
        }
        Box(Modifier.size(9.dp).background(statusColor, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = Theme[typography][label].copy(fontWeight = FontWeight.SemiBold),
                color = Theme[colors][textColor],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
                !running -> stringResource(Res.string.screen_status_off)
                interactive -> stringResource(Res.string.screen_status_you_control)
                otherHolds -> stringResource(Res.string.screen_status_other_control)
                else -> stringResource(Res.string.screen_status_watching)
            }
            Text(
                subtitle,
                style = Theme[typography][caption],
                color = if (interactive) Theme[colors][warning] else Theme[colors][textTertiary],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (running) {
            when {
                interactive -> Button(
                    text = stringResource(Res.string.screen_hand_back),
                    onClick = onHandBack,
                    variant = ButtonVariant.Inverse,
                    size = ButtonSize.Small,
                    enabled = !actionBusy,
                    loading = actionBusy,
                )
                otherHolds -> Button(
                    text = stringResource(Res.string.screen_force_hand_back),
                    onClick = onForceHandBack,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                    enabled = !actionBusy,
                    loading = actionBusy,
                )
                else -> Button(
                    text = stringResource(Res.string.screen_take_over),
                    onClick = onTakeOver,
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    enabled = !actionBusy,
                    loading = actionBusy,
                )
            }

            if (!compactToolbar) {
                QualityBadge(quality = quality, onClick = onCycleQuality)
                if (isDesktop) {
                    IconButton(
                        icon = Lucide.Keyboard,
                        contentDescription = stringResource(Res.string.screen_keyboard),
                        onClick = onToggleKeyboard,
                        tint = if (keyboardBarOpen) Theme[colors][accentText] else Theme[colors][textSecondary],
                        iconSize = 16.dp,
                    )
                }
                IconButton(
                    icon = Lucide.Square,
                    contentDescription = stringResource(Res.string.screen_stop),
                    onClick = onStop,
                    tint = Theme[colors][textSecondary],
                    iconSize = 15.dp,
                )
            }

            if (fullScreenOverlay) {
                IconButton(
                    icon = if (landscape) Lucide.Minimize2 else Lucide.Maximize2,
                    contentDescription = stringResource(Res.string.screen_landscape),
                    onClick = onToggleLandscape,
                    tint = if (landscape) Theme[colors][accentText] else Theme[colors][textSecondary],
                    iconSize = 16.dp,
                )
            }
        }

        IconButton(
            icon = Lucide.X,
            contentDescription = stringResource(Res.string.screen_close),
            onClick = onClose,
            iconSize = 17.dp,
        )
    }
}

@Composable
private fun QualityBadge(quality: ScreenQuality, onClick: () -> Unit) {
    val qLabel = when (quality) {
        ScreenQuality.Sharp -> stringResource(Res.string.screen_quality_sharp)
        ScreenQuality.Balanced -> stringResource(Res.string.screen_quality_balanced)
        ScreenQuality.LowBandwidth -> stringResource(Res.string.screen_quality_low)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Theme[colors][surface2])
            .border(1.dp, Theme[colors][stroke], RoundedCornerShape(8.dp))
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            UnstyledIcon(Lucide.Wifi, contentDescription = null, tint = Theme[colors][textSecondary], modifier = Modifier.size(13.dp))
            Text(qLabel, style = Theme[typography][caption], color = Theme[colors][textSecondary], maxLines = 1)
        }
    }
}

@Composable
private fun MobileControlDock(
    interactive: Boolean,
    compactToolbar: Boolean,
    quality: ScreenQuality,
    touchMode: TouchControlMode,
    dragLocked: Boolean,
    keyboardOpen: Boolean,
    controller: BotScreenController,
    onCycleQuality: () -> Unit,
    onStop: () -> Unit,
    onSelectTouchMode: (TouchControlMode) -> Unit,
    onToggleDragLock: () -> Unit,
    onToggleKeyboard: () -> Unit,
) {
    var ctrlSticky by remember { mutableStateOf(false) }
    var altSticky by remember { mutableStateOf(false) }
    val pasteField = rememberTextFieldState()

    fun dispatchKey(sym: Int, code: String = "") {
        val mods = mutableListOf<Pair<Int, String>>()
        if (ctrlSticky) mods += XKeySyms.CONTROL_L to "ControlLeft"
        if (altSticky) mods += XKeySyms.ALT_L to "AltLeft"
        if (mods.isEmpty()) {
            controller.sendKey(sym, code)
        } else {
            mods += sym to code
            controller.sendKeyCombo(*mods.toTypedArray())
            ctrlSticky = false
            altSticky = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][stroke])
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (keyboardOpen && interactive) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme[radii][radiusMedium]))
                    .background(Theme[colors][surface2])
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextField(
                    state = pasteField,
                    placeholder = stringResource(Res.string.screen_paste_placeholder),
                    password = true,
                    clearable = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        text = stringResource(Res.string.screen_type_into_screen),
                        onClick = {
                            val raw = pasteField.text.toString()
                            if (raw.isNotEmpty() && raw.encodeToByteArray().size <= MAX_PASTE_BYTES) {
                                controller.typeText(raw)
                                pasteField.clearText()
                            }
                        },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Small,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = stringResource(Res.string.screen_send_clipboard),
                        onClick = {
                            val raw = pasteField.text.toString()
                            if (raw.isNotEmpty() && raw.encodeToByteArray().size <= MAX_PASTE_BYTES) {
                                controller.pasteClipboard(raw)
                                pasteField.clearText()
                            }
                        },
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Small,
                    )
                }
            }
        }

        // Row 1: Pointer Mode, Quality, Zoom Reset, Stop
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isDesktop && interactive) {
                KeyChip(
                    label = stringResource(Res.string.screen_touch_trackpad),
                    active = touchMode == TouchControlMode.Trackpad,
                    onClick = { onSelectTouchMode(TouchControlMode.Trackpad) },
                )
                KeyChip(
                    label = stringResource(Res.string.screen_touch_direct),
                    active = touchMode == TouchControlMode.DirectTouch,
                    onClick = { onSelectTouchMode(TouchControlMode.DirectTouch) },
                )
                if (touchMode == TouchControlMode.Trackpad) {
                    KeyChip(
                        label = stringResource(Res.string.screen_drag_lock),
                        active = dragLocked,
                        onClick = onToggleDragLock,
                    )
                }
                KeyChip(
                    label = stringResource(Res.string.screen_right_click),
                    active = false,
                    onClick = controller::sendRightClick,
                )
            }
            if (!isDesktop) {
                KeyChip(
                    label = stringResource(Res.string.screen_reset_zoom),
                    active = false,
                    onClick = controller::resetZoom,
                )
            }
            if (interactive) {
                KeyChip(
                    label = stringResource(Res.string.screen_keyboard),
                    active = keyboardOpen,
                    onClick = onToggleKeyboard,
                )
            }
            if (compactToolbar) {
                QualityBadge(quality = quality, onClick = onCycleQuality)
                KeyChip(
                    label = stringResource(Res.string.screen_stop),
                    active = false,
                    onClick = onStop,
                )
            }
        }

        // Row 2: Desktop / X11 Accessory Key Bar when interactive
        if (interactive) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeyChip("Esc", active = false) { dispatchKey(XKeySyms.ESCAPE, "Escape") }
                KeyChip("Tab", active = false) { dispatchKey(XKeySyms.TAB, "Tab") }
                KeyChip("Ctrl", active = ctrlSticky) { ctrlSticky = !ctrlSticky }
                KeyChip("Alt", active = altSticky) { altSticky = !altSticky }
                KeyChip("Enter", active = false) { dispatchKey(XKeySyms.RETURN, "Enter") }
                KeyChip("Bksp", active = false) { dispatchKey(XKeySyms.BACKSPACE, "Backspace") }
                KeyChip("Ctrl+C", active = false) { controller.sendKeyCombo(XKeySyms.CONTROL_L to "ControlLeft", 'c'.code to "KeyC") }
                KeyChip("Ctrl+V", active = false) { controller.sendKeyCombo(XKeySyms.CONTROL_L to "ControlLeft", 'v'.code to "KeyV") }
                KeyChip("Ctrl+A", active = false) { controller.sendKeyCombo(XKeySyms.CONTROL_L to "ControlLeft", 'a'.code to "KeyA") }
                KeyChip("←", active = false) { dispatchKey(XKeySyms.LEFT, "ArrowLeft") }
                KeyChip("↑", active = false) { dispatchKey(XKeySyms.UP, "ArrowUp") }
                KeyChip("↓", active = false) { dispatchKey(XKeySyms.DOWN, "ArrowDown") }
                KeyChip("→", active = false) { dispatchKey(XKeySyms.RIGHT, "ArrowRight") }
                KeyChip("Ctrl+W", active = false) { controller.sendKeyCombo(XKeySyms.CONTROL_L to "ControlLeft", 'w'.code to "KeyW") }
                KeyChip("Alt+Tab", active = false) { controller.sendKeyCombo(XKeySyms.ALT_L to "AltLeft", XKeySyms.TAB to "Tab") }
            }
        }
    }
}

@Composable
private fun KeyChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (active) Theme[colors][accent] else Theme[colors][surface2]
    val fg = if (active) Theme[colors][onAccent] else Theme[colors][textColor]
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (active) Theme[colors][accent] else Theme[colors][stroke], RoundedCornerShape(8.dp))
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium), color = fg, maxLines = 1)
    }
}

@Composable
private fun InstallRuntimeCard(
    missing: List<String>,
    command: String?,
    installing: Boolean,
    logs: List<String>,
    exitCode: Int?,
    sudoPending: Boolean,
    onInstall: () -> Unit,
    onSubmitSudo: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    val sudoField = rememberTextFieldState()
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val cardShape = RoundedCornerShape(Theme[radii][radiusLarge])
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], cardShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnstyledIcon(Lucide.Download, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.screen_install_title), style = Theme[typography][body].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][textColor])
            }
            if (missing.isNotEmpty()) {
                Text(
                    stringResource(Res.string.screen_install_missing, missing.joinToString(", ")),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textSecondary],
                )
            }
            if (!command.isNullOrBlank()) {
                Text(
                    command,
                    style = Theme[typography][code].copy(fontSize = 12.sp),
                    color = Theme[colors][textSecondary],
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Theme[colors][well])
                        .padding(10.dp),
                )
            }

            if (sudoPending) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Theme[colors][warningSoft])
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(Res.string.screen_sudo_title), style = Theme[typography][label].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][warning])
                    Text(stringResource(Res.string.screen_sudo_hint), style = Theme[typography][caption], color = Theme[colors][textSecondary])
                    TextField(state = sudoField, password = true, clearable = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            text = stringResource(Res.string.screen_sudo_confirm),
                            onClick = {
                                val pw = sudoField.text.toString()
                                sudoField.clearText()
                                onSubmitSudo(pw)
                            },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Small,
                        )
                        Button(
                            text = stringResource(Res.string.common_cancel),
                            onClick = {
                                sudoField.clearText()
                                onSubmitSudo("")
                            },
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Small,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    text = if (installing) stringResource(Res.string.screen_install_running) else stringResource(Res.string.screen_install_button),
                    onClick = onInstall,
                    enabled = !installing,
                    loading = installing,
                    size = ButtonSize.Small,
                )
                Button(
                    text = stringResource(Res.string.screen_retry),
                    onClick = onRefresh,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Small,
                    leadingIcon = Lucide.RefreshCw,
                )
            }

            if (exitCode != null) {
                Text(
                    if (exitCode == 0) stringResource(Res.string.screen_install_done_ok) else stringResource(Res.string.screen_install_done_err, exitCode),
                    style = Theme[typography][bodySmall],
                    color = if (exitCode == 0) Theme[colors][success] else Theme[colors][danger],
                )
            }

            if (logs.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Theme[colors][well])
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(logs) { line ->
                        Text(line, style = Theme[typography][code].copy(fontSize = 11.5.sp), color = Theme[colors][textSecondary])
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenActionCard(
    title: String,
    message: String,
    primaryLabel: String,
    busy: Boolean,
    onPrimary: () -> Unit,
    command: String? = null,
) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(Theme[radii][radiusLarge])
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UnstyledIcon(Lucide.Monitor, contentDescription = null, tint = Theme[colors][accentText], modifier = Modifier.size(18.dp))
                Text(title, style = Theme[typography][body].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][textColor])
            }
            Text(message, style = Theme[typography][bodySmall], color = Theme[colors][textSecondary])
            if (!command.isNullOrBlank()) {
                Text(
                    command,
                    style = Theme[typography][code].copy(fontSize = 12.sp),
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Theme[colors][well]).padding(10.dp),
                )
            }
            Button(
                text = primaryLabel,
                onClick = onPrimary,
                enabled = !busy,
                loading = busy,
                size = ButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScreenMessageCard(title: String, message: String) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(Theme[radii][radiusLarge])
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = Theme[typography][body].copy(fontWeight = FontWeight.SemiBold), color = Theme[colors][textColor])
            Text(message, style = Theme[typography][bodySmall], color = Theme[colors][textSecondary])
        }
    }
}

@Composable
private fun disconnectNoticeText(notice: ScreenDisconnectNotice?): String = when (notice) {
    ScreenDisconnectNotice.Evicted -> stringResource(Res.string.screen_notice_evicted)
    ScreenDisconnectNotice.Stopped -> stringResource(Res.string.screen_notice_stopped)
    ScreenDisconnectNotice.Expired -> stringResource(Res.string.screen_notice_expired)
    ScreenDisconnectNotice.Lost -> stringResource(Res.string.screen_notice_lost)
    null -> ""
}
