package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.handCursor
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusLarge
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.strokeStrong
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.typography
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sqrt
import dev.hermeskotlin.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** An open PDF: the shape of each page, and a way to draw one. Closed when the viewer leaves it. */
internal interface PdfPages : AutoCloseable {
    /** Each page's width over its height, in page order; never empty. */
    val aspects: List<Float>

    /** Page [index] drawn on white to fill [width] by [height] pixels, or null when it can't be (closed, out of memory). */
    suspend fun render(index: Int, width: Int, height: Int): ImageBitmap?
}

/** A PDF's bytes opened for drawing, or why they can't be. */
internal sealed interface PdfOpen {
    class Ready(val pages: PdfPages) : PdfOpen

    data class Failed(val problem: FileProblem) : PdfOpen {
        /** Words already written elsewhere, shown as they are. */
        constructor(message: String) : this(FileProblem.Said(message))
    }
}

/** Opens a PDF's bytes off the main thread. */
@Composable
internal expect fun rememberPdfOpener(): suspend (ByteArray) -> PdfOpen

/** Opens a PDF backed by a local [TempFileHandle] off the main thread without copying its bytes into RAM. */
@Composable
internal expect fun rememberPdfFileOpener(): suspend (TempFileHandle) -> PdfOpen

internal val PDF_LOCKED: FileProblem = FileProblem.PdfLocked
internal val PDF_BROKEN: FileProblem = FileProblem.PdfBroken
internal val PDF_NO_ROOM: FileProblem = FileProblem.PdfNoRoom
internal val PDF_DESKTOP_FAILED: FileProblem = FileProblem.PdfDesktopFailed

/** Maximum PDF size accepted for in-app rendering when streamed to disk (Save / Open in app have no cap). */
internal const val MAX_PDF_VIEWER_BYTES = 512L * 1024 * 1024

/**
 * A PDF's pages one under another, each as wide as the screen and drawn only once it scrolls near.
 * Two fingers zoom (up to [MAX_ZOOM]) around where they are; zoomed in, one finger also moves sideways.
 * Double tap zooms in, or back out. A zoomed page is drawn again, sharper, once the pinch settles.
 */
@Composable
internal fun PdfDocumentView(pages: PdfPages, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    var viewport by remember(pages) { mutableIntStateOf(0) }
    var viewportHeight by remember(pages) { mutableIntStateOf(0) }
    var scale by remember(pages) { mutableFloatStateOf(1f) }
    var offsetX by remember(pages) { mutableFloatStateOf(0f) }

    fun clampX(x: Float, zoom: Float) = x.coerceIn(viewport - viewport * zoom, 0f)

    // Keeps the point under [centroid] where it is while the pages grow or shrink, then moves by [pan].
    fun zoomBy(factor: Float, centroid: Offset, pan: Offset) {
        val next = (scale * factor).coerceIn(1f, MAX_ZOOM).let { if (it < 1.01f) 1f else it }
        val f = next / scale
        offsetX = if (next == 1f) 0f else clampX(centroid.x + pan.x - (centroid.x - offsetX) * f, next)
        // Set as a position for the next layout, at the new size: a scroll now would be clamped to the old
        // content's end, and zooming near the last page would drift. The gaps between pages don't grow.
        val into = (list.firstVisibleItemScrollOffset + centroid.y) * f - centroid.y - pan.y
        list.requestScrollToItem(list.firstVisibleItemIndex, into.roundToInt().coerceAtLeast(0))
        scale = next
    }

    val center = Offset(viewport / 2f, viewportHeight / 2f)

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged {
                viewport = it.width
                viewportHeight = it.height
                offsetX = if (scale <= 1f) 0f else clampX(offsetX, scale)
            }
            // Ctrl/Cmd + mouse wheel zooms around the pointer without consuming ordinary vertical scrolling.
            .pointerInput(pages) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll && (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed)) {
                            val scrollY = event.changes.fold(0f) { acc, c -> acc + c.scrollDelta.y }
                            if (scrollY != 0f) {
                                val at = event.changes.firstOrNull()?.position ?: Offset(viewport / 2f, viewportHeight / 2f)
                                val factor = if (scrollY < 0f) WHEEL_ZOOM_STEP else 1f / WHEEL_ZOOM_STEP
                                zoomBy(factor, at, Offset.Zero)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            // Two fingers belong to the zoom: taken before the list sees them, so it doesn't scroll as well.
            .pointerInput(pages) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            zoomBy(event.calculateZoom(), event.calculateCentroid(useCurrent = true), event.calculatePan())
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(pages) {
                detectTapGestures(onDoubleTap = { at -> zoomBy((if (scale > 1f) 1f else DOUBLE_TAP_ZOOM) / scale, at, Offset.Zero) })
            }
            .draggable(
                rememberDraggableState { dx -> offsetX = clampX(offsetX + dx, scale) },
                Orientation.Horizontal,
                enabled = scale > 1f,
            ),
    ) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                // As wide as the zoom makes it, shifted sideways; the box clips what falls outside.
                .layout { measurable, constraints ->
                    val width = (constraints.maxWidth * scale).roundToInt()
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(offsetX.roundToInt(), 0) }
                },
        ) {
            items(pages.aspects.size) { index -> PdfPage(pages, index, count = pages.aspects.size) }
        }
        PdfZoomBar(
            scale = scale,
            onZoomOut = { zoomBy((scale / BUTTON_ZOOM_STEP).coerceAtLeast(1f) / scale, center, Offset.Zero) },
            onReset = { zoomBy(1f / scale, center, Offset.Zero) },
            onZoomIn = { zoomBy(BUTTON_ZOOM_STEP, center, Offset.Zero) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        )
    }
}

@Composable
private fun PdfZoomBar(
    scale: Float,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Theme[radii][radiusLarge])
    val zoomed = scale > 1.001f
    val percent = (scale * 100f).roundToInt()
    Row(
        modifier
            .clip(shape)
            .background(Theme[colors][surface].copy(alpha = 0.94f), shape)
            .border(1.dp, Theme[colors][strokeStrong], shape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            Lucide.Minus,
            contentDescription = stringResource(Res.string.chat_pdf_zoom_out),
            onClick = onZoomOut,
            enabled = zoomed,
            tint = Theme[colors][textSecondary],
            iconSize = 15.dp,
        )
        Text(
            "$percent%",
            style = Theme[typography][caption].copy(fontWeight = FontWeight.Medium),
            color = Theme[colors][textColor],
            modifier = Modifier
                .clip(shape)
                .handCursor(zoomed)
                .clickable(
                    enabled = zoomed,
                    onClickLabel = stringResource(Res.string.chat_pdf_zoom_reset),
                    role = Role.Button,
                    onClick = onReset,
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        IconButton(
            Lucide.Plus,
            contentDescription = stringResource(Res.string.chat_pdf_zoom_in),
            onClick = onZoomIn,
            enabled = scale < MAX_ZOOM - 0.001f,
            tint = Theme[colors][textSecondary],
            iconSize = 15.dp,
        )
    }
}

@Composable
private fun PdfPage(pages: PdfPages, index: Int, count: Int) {
    val aspect = pages.aspects[index].takeIf { it.isFinite() && it > 0f }?.coerceIn(0.1f, 10f) ?: (1f / 1.414f)
    val pageLabel = stringResource(Res.string.chat_pdf_page_of, index + 1, count)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(Color.White)
            .border(1.dp, Theme[colors][stroke])
            .semantics { contentDescription = pageLabel },
    ) {
        val target = renderWidth(constraints.maxWidth, aspect)
        var bitmap by remember(pages, index) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(pages, index, target) {
            // A page already on screen waits for the pinch to settle before it's drawn again.
            if (bitmap != null) delay(RERENDER_DELAY_MS)
            // Drawn in the proportions it's laid out in, capped within MAX_PAGE_PIXELS even under rounding.
            val height = (target / aspect).toInt().coerceAtLeast(1).coerceAtMost((MAX_PAGE_PIXELS / target).toInt().coerceAtLeast(1))
            pages.render(index, target, height)?.let { bitmap = it }
        }
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * How many pixels wide to draw a page shown [shown] pixels wide: in steps, so a pinch doesn't redraw it
 * every frame, and within [MAX_PAGE_PIXELS] so a tall page or a deep zoom can't run the phone out of memory.
 */
internal fun renderWidth(shown: Int, aspect: Float): Int {
    val stepped = ((shown + RENDER_STEP - 1) / RENDER_STEP) * RENDER_STEP
    val fit = sqrt(MAX_PAGE_PIXELS * aspect.toDouble()).toInt()
    return stepped.coerceAtMost(fit).coerceAtLeast(1)
}

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val BUTTON_ZOOM_STEP = 1.25f
private const val WHEEL_ZOOM_STEP = 1.15f
private const val RENDER_STEP = 256
private const val RERENDER_DELAY_MS = 200L

/** About 32 MB as ARGB: a page's sharpness, a few times over, without risking the heap. */
private const val MAX_PAGE_PIXELS = 8_000_000.0
