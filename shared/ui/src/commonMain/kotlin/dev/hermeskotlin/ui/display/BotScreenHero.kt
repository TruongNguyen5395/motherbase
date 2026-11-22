package dev.hermeskotlin.ui.display

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Square
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.display.DisplayStore
import dev.hermeskotlin.core.display.LeaseHolder
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.SectionLabel
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.label
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
import dev.hermeskotlin.designsystem.well
import dev.hermeskotlin.ui.chat.decodeImageBitmap
import dev.hermeskotlin.ui.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.io.encoding.Base64

private const val THUMB_POLL_MS = 4_000L
private const val STALE_THRESHOLD = 3

/**
 * Portal card shown inside Bot Editor (and bot detail surfaces) displaying live 4s thumbnails when running,
 * lease status, and Start / Stop / Open Screen actions without polling in the background.
 */
@Composable
fun BotScreenHero(
    profile: String,
    displayStore: DisplayStore,
    visible: Boolean = true,
    onOpenScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles by displayStore.profiles.collectAsStateWithLifecycle()
    val state = profiles[displayStore.normalizeProfile(profile)] ?: displayStore.stateOf(profile)
    if (state.unavailable) return

    val status = state.status
    val scope = rememberCoroutineScope()
    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }

    var previewBitmap by remember(profile) { mutableStateOf<ImageBitmap?>(null) }
    var pausedForHuman by remember(profile) { mutableStateOf(false) }
    var missCount by remember(profile) { mutableIntStateOf(0) }

    LaunchedEffect(profile, visible) {
        if (visible) displayStore.refreshStatus(profile, quiet = true)
    }

    LaunchedEffect(profile, visible, resumed, status?.running) {
        if (!visible || !resumed || status?.running != true) {
            if (status?.running != true) {
                previewBitmap = null
                pausedForHuman = false
                missCount = 0
            }
            return@LaunchedEffect
        }
        while (isActive) {
            val res = displayStore.thumbnail(profile)
            val thumb = res.getOrNull()
            if (thumb == null) {
                missCount++
            } else if (thumb.humanHasControl) {
                pausedForHuman = true
                previewBitmap = null
                missCount = 0
            } else if (!thumb.dataUrl.isNullOrBlank()) {
                pausedForHuman = false
                val bytes = runCatching { Base64.decode(thumb.dataUrl!!.substringAfter("base64,")) }.getOrNull()
                val bmp = bytes?.let { decodeImageBitmap(it, maxEdge = 960) }
                if (bmp != null) {
                    previewBitmap = bmp
                    missCount = 0
                } else {
                    missCount++
                }
            } else {
                pausedForHuman = false
                missCount++
            }
            delay(THUMB_POLL_MS)
        }
    }

    val stale = missCount >= STALE_THRESHOLD
    val cardShape = RoundedCornerShape(Theme[radii][radiusLarge])

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(Res.string.bots_screen_card_title))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][stroke], cardShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dotColor = when {
                    status?.running != true -> Theme[colors][textMuted]
                    state.iHoldLease -> Theme[colors][warning]
                    status.lease.holder == LeaseHolder.Human -> Theme[colors][danger]
                    else -> Theme[colors][success]
                }
                Box(Modifier.size(9.dp).background(dotColor, CircleShape))
                val labelText = when {
                    status?.running != true -> stringResource(Res.string.screen_status_off)
                    state.iHoldLease -> stringResource(Res.string.screen_status_you_control)
                    status.lease.holder == LeaseHolder.Human -> stringResource(Res.string.screen_status_other_control)
                    else -> stringResource(Res.string.screen_status_agent_working)
                }
                Text(
                    labelText,
                    style = Theme[typography][label].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                status?.geometry?.takeIf { it.isNotBlank() }?.let { geo ->
                    Text(geo, style = Theme[typography][caption], color = Theme[colors][textTertiary])
                }
            }

            if (status?.running == true) {
                val thumbShape = RoundedCornerShape(Theme[radii][radiusMedium])
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(thumbShape)
                        .background(Color(0xFF0D1117))
                        .border(1.dp, Theme[colors][stroke], thumbShape)
                        .handCursor()
                        .clickable { onOpenScreen() },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = previewBitmap
                    when {
                        pausedForHuman -> Text(
                            stringResource(Res.string.screen_status_human_paused),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][textSecondary],
                            modifier = Modifier.padding(12.dp),
                        )
                        bmp != null -> {
                            Image(
                                bitmap = bmp,
                                contentDescription = stringResource(Res.string.screen_title),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (stale) {
                                Text(
                                    stringResource(Res.string.screen_status_stale),
                                    style = Theme[typography][caption],
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        else -> Text(
                            if (stale) stringResource(Res.string.screen_status_stale) else stringResource(Res.string.screen_status_watching),
                            style = Theme[typography][bodySmall],
                            color = Theme[colors][textSecondary],
                        )
                    }
                }
            } else {
                Text(
                    stringResource(Res.string.screen_desktop_only_hint),
                    style = Theme[typography][bodySmall],
                    color = Theme[colors][textSecondary],
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    text = stringResource(Res.string.screen_open),
                    onClick = onOpenScreen,
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Small,
                    leadingIcon = Lucide.Monitor,
                    modifier = Modifier.weight(1f),
                )
                if (status?.running == true) {
                    Button(
                        text = stringResource(Res.string.screen_stop),
                        onClick = { scope.launch { displayStore.stopScreen(profile, force = true) } },
                        variant = ButtonVariant.Outline,
                        size = ButtonSize.Small,
                        leadingIcon = Lucide.Square,
                        enabled = !state.actionBusy,
                        loading = state.actionBusy,
                    )
                } else {
                    Button(
                        text = stringResource(Res.string.screen_start),
                        onClick = { scope.launch { displayStore.startScreen(profile) } },
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Small,
                        leadingIcon = Lucide.Play,
                        enabled = !state.actionBusy && status?.needsInstall != true,
                        loading = state.actionBusy,
                    )
                }
            }
        }
    }
}
