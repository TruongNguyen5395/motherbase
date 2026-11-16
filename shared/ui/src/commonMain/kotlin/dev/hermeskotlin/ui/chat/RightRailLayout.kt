package dev.hermeskotlin.ui.chat

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.ResizeHorizontalPointer
import dev.hermeskotlin.designsystem.sidebar as sidebarColor
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.ui.PlatformBackHandler
import dev.hermeskotlin.ui.isDesktop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal val DEFAULT_RAIL_WIDTH = 420.dp
internal val MIN_RAIL_WIDTH = 320.dp
internal val MIN_CHAT_BESIDE_RAIL = 400.dp
internal val RAIL_MIN_PANE = 880.dp

private val DIVIDER_WIDTH = 1.dp
private val EDGE_GRIP = 8.dp
private val DRAWER_RADIUS = 28.dp
private val RightDrawerShape = RoundedCornerShape(topStart = DRAWER_RADIUS, bottomStart = DRAWER_RADIUS)
private const val FLING_VELOCITY = 800f

/** Open/closed state of [RightRailLayout] for both docked desktop rails and overlay mobile drawers. */
@Stable
internal class RightRailState {
    private var dockedOpen by mutableStateOf(false)
    private var drawerOpen by mutableStateOf(false)
    private var progress by mutableFloatStateOf(0f)
    private val motion = MutatorMutex()

    var docked by mutableStateOf(false)
        private set

    val isOpen: Boolean get() = if (docked) dockedOpen else drawerOpen
    val fraction: Float get() = progress

    suspend fun open() = settle(true)
    suspend fun close() = settle(false)
    suspend fun toggle() = settle(!isOpen)

    internal suspend fun settle(open: Boolean) {
        if (docked) dockedOpen = open else drawerOpen = open
        motion.mutate {
            animate(progress, if (open) 1f else 0f, animationSpec = spring(stiffness = 600f)) { value, _ ->
                progress = value
            }
        }
    }

    internal suspend fun stopAnimation() = motion.mutate(MutatePriority.UserInput) {}

    internal fun dragBy(fraction: Float) {
        progress = (progress + fraction).coerceIn(0f, 1f)
    }

    internal suspend fun updateDocked(value: Boolean) {
        docked = value
        motion.mutate { progress = if (isOpen) 1f else 0f }
    }
}

@Composable
internal fun rememberRightRailState(): RightRailState = remember { RightRailState() }

/**
 * Hosts the main chat [content] alongside a right-side panel [rail]:
 * - On desktop when wide enough (`>= RAIL_MIN_PANE`), docks the panel on the right with a draggable left edge.
 * - On mobile (or narrow windows), slides the panel in as a right-side overlay drawer with directional right-to-left
 *   swipe support that yields rightward drags to the parent left sidebar.
 */
@Composable
internal fun RightRailLayout(
    state: RightRailState,
    railWidth: Dp = DEFAULT_RAIL_WIDTH,
    onRailWidthChange: ((Dp) -> Unit)? = null,
    swipeEnabled: Boolean = !isDesktop,
    rail: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val docked = isDesktop && maxWidth >= RAIL_MIN_PANE
        LaunchedEffect(docked) { state.updateDocked(docked) }

        var dragged by remember { mutableStateOf<Dp?>(null) }
        val maxRail = maxOf(MIN_RAIL_WIDTH, maxWidth - MIN_CHAT_BESIDE_RAIL)
        val width = if (docked) (dragged ?: railWidth).coerceIn(MIN_RAIL_WIDTH, maxRail) else min(maxWidth * 0.86f, 360.dp)
        val p = state.fraction
        val widthPx = with(density) { width.toPx().coerceAtLeast(1f) }
        val scope = rememberCoroutineScope()

        val hidden = if (!state.isOpen) {
            Modifier
                .clearAndSetSemantics { }
                .focusProperties { onEnter = { cancelFocusChange() } }
                .focusGroup()
        } else {
            Modifier
        }

        PlatformBackHandler(enabled = !docked && state.isOpen) {
            scope.launch { state.close() }
        }

        val swipeModifier = if (!docked && swipeEnabled) {
            Modifier.rightDrawerSwipe(
                state = state,
                drawerWidthPx = widthPx,
                onFling = { open -> scope.launch { state.settle(open) } },
                onDragStart = { scope.launch { state.stopAnimation() } },
            )
        } else {
            Modifier
        }

        Box(Modifier.fillMaxSize().then(swipeModifier), contentAlignment = Alignment.TopCenter) {
            content(if (docked && p > 0f) Modifier.padding(end = width * p + DIVIDER_WIDTH) else Modifier)

            if (!docked && p > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .background(Color.Black.copy(alpha = 0.4f * p))
                        .pointerInput(Unit) { detectTapGestures { scope.launch { state.close() } } },
                )
            }

            if (docked) {
                if (p > 0f) {
                    val shownWidth = width * p
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .width(shownWidth)
                            .fillMaxHeight()
                            .clipToBounds()
                            .then(hidden),
                    ) {
                        Box(Modifier.requiredWidth(width).fillMaxHeight().align(Alignment.CenterStart)) {
                            rail()
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = -shownWidth)
                            .width(DIVIDER_WIDTH)
                            .fillMaxHeight()
                            .background(Theme[colors][stroke]),
                    )
                    if (onRailWidthChange != null && p == 1f) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = -(width - EDGE_GRIP / 2))
                                .width(EDGE_GRIP)
                                .fillMaxHeight()
                                .pointerHoverIcon(ResizeHorizontalPointer)
                                .pointerInput(onRailWidthChange) {
                                    detectTapGestures(onDoubleTap = { onRailWidthChange(DEFAULT_RAIL_WIDTH) })
                                }
                                .draggable(
                                    state = rememberDraggableState { delta ->
                                        dragged = ((dragged ?: width) - with(density) { delta.toDp() }).coerceIn(MIN_RAIL_WIDTH, maxRail)
                                    },
                                    orientation = Orientation.Horizontal,
                                    onDragStarted = { dragged = width },
                                    onDragStopped = {
                                        dragged?.let(onRailWidthChange)
                                        dragged = null
                                    },
                                )
                                .clearAndSetSemantics { },
                        )
                    }
                }
            } else if (p > 0f || state.isOpen) {
                val edge = Theme[colors][stroke]
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .requiredWidth(width)
                        .fillMaxHeight()
                        .zIndex(2f)
                        .offset { IntOffset((widthPx * (1f - p)).roundToInt(), 0) }
                        .clip(RightDrawerShape)
                        .background(Theme[colors][sidebarColor], RightDrawerShape)
                        .drawWithContent {
                            drawContent()
                            val r = DRAWER_RADIUS.toPx()
                            val s = 1.dp.toPx()
                            val h = size.height
                            val path = Path().apply {
                                moveTo(r, s / 2)
                                arcTo(Rect(s / 2, s / 2, 2 * r, 2 * r), -90f, -90f, false)
                                lineTo(s / 2, h - r)
                                arcTo(Rect(s / 2, h - 2 * r, 2 * r, h - s / 2), 180f, -90f, false)
                            }
                            drawPath(path, edge, style = Stroke(s))
                        }
                        .then(hidden),
                ) {
                    rail()
                }
            }
        }
    }
}

private fun Modifier.rightDrawerSwipe(
    state: RightRailState,
    drawerWidthPx: Float,
    onFling: (Boolean) -> Unit,
    onDragStart: () -> Unit,
): Modifier = pointerInput(drawerWidthPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var dragging = false
        var velocityPxPerSec = 0f
        var lastMoveMs = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
            if (!change.pressed) {
                if (dragging) {
                    val stale = change.uptimeMillis - lastMoveMs > 100L
                    val vx = if (stale) 0f else velocityPxPerSec
                    val open = when {
                        vx < -FLING_VELOCITY -> true
                        vx > FLING_VELOCITY -> false
                        else -> state.fraction > 0.5f
                    }
                    onFling(open)
                }
                break
            }
            if (change.isConsumed) {
                if (dragging) onFling(state.fraction > 0.5f)
                break
            }
            val dx = change.position.x - change.previousPosition.x
            if (!dragging) {
                val netDx = change.position.x - down.position.x
                val netDy = change.position.y - down.position.y
                if (abs(netDy) > slop && abs(netDy) > abs(netDx)) break
                val wantsDrag = abs(netDx) > slop && abs(netDx) > abs(netDy) && (state.fraction > 0f || netDx < -slop)
                if (wantsDrag) {
                    dragging = true
                    onDragStart()
                    state.dragBy(-netDx / drawerWidthPx)
                    change.consume()
                    lastMoveMs = change.uptimeMillis
                }
            } else if (dx != 0f) {
                val dtMs = (change.uptimeMillis - lastMoveMs).coerceAtLeast(1L)
                velocityPxPerSec = (dx / dtMs) * 1000f
                lastMoveMs = change.uptimeMillis
                state.dragBy(-dx / drawerWidthPx)
                change.consume()
            }
        }
    }
}
