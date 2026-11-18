package dev.hermeskotlin.ui.chat

import dev.hermeskotlin.ui.PlatformBackHandler
import dev.hermeskotlin.ui.isDesktop
import dev.hermeskotlin.ui.clearOfWindowControls
import dev.hermeskotlin.ui.fullScreenDialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import dev.hermeskotlin.designsystem.components.HorizontalScrollbar
import dev.hermeskotlin.designsystem.components.VerticalScrollbar
import dev.hermeskotlin.ui.CopyableOnDesktop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import dev.hermeskotlin.designsystem.components.verticalScrollWithBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.FileImage
import com.composables.icons.lucide.FileSpreadsheet
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.FileWarning
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Minimize2
import dev.hermeskotlin.ui.shareIcon
import com.composables.icons.lucide.WrapText
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.media.DelimitedTable
import dev.hermeskotlin.core.media.FileText
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.core.media.GatewayFileKind
import dev.hermeskotlin.core.media.MediaApi
import dev.hermeskotlin.core.media.parseDelimited
import dev.hermeskotlin.core.media.prettyJson
import dev.hermeskotlin.designsystem.accent
import dev.hermeskotlin.designsystem.accentSoft
import dev.hermeskotlin.designsystem.accentText
import dev.hermeskotlin.designsystem.background
import dev.hermeskotlin.designsystem.bodySmall
import dev.hermeskotlin.designsystem.caption
import dev.hermeskotlin.designsystem.code
import dev.hermeskotlin.designsystem.colors
import dev.hermeskotlin.designsystem.components.Button
import dev.hermeskotlin.designsystem.components.ButtonSize
import dev.hermeskotlin.designsystem.components.ButtonVariant
import dev.hermeskotlin.designsystem.components.CodeView
import dev.hermeskotlin.designsystem.components.IconButton
import dev.hermeskotlin.designsystem.components.LocalFileLinks
import dev.hermeskotlin.designsystem.components.LocalMarkdownPictures
import dev.hermeskotlin.designsystem.components.MarkdownPicture
import dev.hermeskotlin.designsystem.components.MarkdownText
import dev.hermeskotlin.designsystem.components.SegmentedControl
import dev.hermeskotlin.designsystem.components.Spinner
import dev.hermeskotlin.designsystem.components.plainTextClipEntry
import dev.hermeskotlin.designsystem.danger
import dev.hermeskotlin.designsystem.dangerSoft
import dev.hermeskotlin.designsystem.label
import dev.hermeskotlin.designsystem.radii
import dev.hermeskotlin.designsystem.radiusMedium
import dev.hermeskotlin.designsystem.stroke
import dev.hermeskotlin.designsystem.success
import dev.hermeskotlin.designsystem.successSoft
import dev.hermeskotlin.designsystem.surface
import dev.hermeskotlin.designsystem.surface2
import dev.hermeskotlin.designsystem.text as textColor
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.textTertiary
import dev.hermeskotlin.designsystem.title
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.designsystem.warning
import dev.hermeskotlin.designsystem.warningSoft
import dev.hermeskotlin.designsystem.well
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.hermeskotlin.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Where a file preview stands: loading, its text, or why it can't be shown. */
sealed interface FileLoad {
    data object Loading : FileLoad

    data class Ready(val text: FileText) : FileLoad

    data class Failed(val problem: FileProblem) : FileLoad {
        /** Words already written elsewhere, shown as they are. */
        constructor(message: String) : this(FileProblem.Said(message))
    }

    /** A PDF fetched whole and opened: its pages, drawn as they scroll near. */
    class Pdf internal constructor(internal val pages: PdfPages) : FileLoad
}

/** A linked file's whole bytes for Save and Share, or why the gateway wouldn't hand them over. */
sealed interface FileDownload {
    class Ready(val bytes: ByteArray) : FileDownload

    data class Failed(val problem: FileProblem) : FileDownload {
        /** Words already written elsewhere, shown as they are. */
        constructor(message: String) : this(FileProblem.Said(message))
    }
}

/** Why a file or widget can't be shown; [fileProblemText] puts it into words. */
sealed interface FileProblem {
    /** Words from elsewhere (the gateway's own reason, another screen's), shown as they are. */
    data class Said(val text: String) : FileProblem

    /** The gateway refused for a [reason] it gave. */
    data class Refused(val reason: String) : FileProblem

    data object NotConnected : FileProblem
    data object Missing : FileProblem
    data object Private : FileProblem
    data object TooLargeToPreview : FileProblem
    data object TooLargeToHandOver : FileProblem
    data object SignInAgain : FileProblem
    data object Busy : FileProblem
    data object Unreachable : FileProblem
    data object CouldNotOpen : FileProblem
    data object PdfLocked : FileProblem
    data object PdfBroken : FileProblem
    data object PdfNoRoom : FileProblem
    data object PdfDesktopLimit : FileProblem
    data object PdfDesktopFailed : FileProblem
    data class PdfTooLarge(val limitBytes: Long) : FileProblem
    data object WidgetNotHtml : FileProblem
    data object WidgetTooBig : FileProblem
    data object WidgetEmpty : FileProblem
    data object WidgetSlow : FileProblem
    data object WidgetUnreadable : FileProblem
    data object WidgetNotLoaded : FileProblem
    data object WidgetNotFetched : FileProblem
}

private fun FileProblem.resource(): Pair<StringResource, String?>? = when (this) {
    is FileProblem.Said -> null
    is FileProblem.Refused -> Res.string.chat_file_refused to reason.trimEnd('.')
    FileProblem.NotConnected -> Res.string.chat_file_not_connected to null
    FileProblem.Missing -> Res.string.chat_file_missing to null
    FileProblem.Private -> Res.string.chat_file_private to null
    FileProblem.TooLargeToPreview -> Res.string.chat_file_too_large_preview to null
    FileProblem.TooLargeToHandOver -> Res.string.chat_file_too_large_whole to null
    FileProblem.SignInAgain -> Res.string.chat_file_sign_in to null
    FileProblem.Busy -> Res.string.chat_file_busy to null
    FileProblem.Unreachable -> Res.string.chat_file_unreachable to null
    FileProblem.CouldNotOpen -> Res.string.chat_file_could_not_open to null
    FileProblem.PdfLocked -> Res.string.chat_pdf_locked to null
    FileProblem.PdfBroken -> Res.string.chat_pdf_broken to null
    FileProblem.PdfNoRoom -> Res.string.chat_pdf_no_room to null
    FileProblem.PdfDesktopLimit -> Res.string.chat_pdf_desktop_limit to null
    FileProblem.PdfDesktopFailed -> Res.string.chat_pdf_desktop_failed to null
    is FileProblem.PdfTooLarge -> Res.string.chat_pdf_too_large to formatBytes(limitBytes)
    FileProblem.WidgetNotHtml -> Res.string.chat_widget_not_html to null
    FileProblem.WidgetTooBig -> Res.string.chat_widget_too_big to null
    FileProblem.WidgetEmpty -> Res.string.chat_widget_empty to null
    FileProblem.WidgetSlow -> Res.string.chat_widget_slow to null
    FileProblem.WidgetUnreadable -> Res.string.chat_widget_unreadable to null
    FileProblem.WidgetNotLoaded -> Res.string.chat_widget_not_loaded to null
    FileProblem.WidgetNotFetched -> Res.string.chat_widget_not_fetched to null
}

/** [problem] in the words the viewer shows. */
@Composable
internal fun fileProblemText(problem: FileProblem): String {
    val (res, arg) = problem.resource() ?: return (problem as FileProblem.Said).text
    return if (arg != null) stringResource(res, arg) else stringResource(res)
}

/** The same, for a notice set from a coroutine. */
internal suspend fun fileProblemWords(problem: FileProblem): String {
    val (res, arg) = problem.resource() ?: return (problem as FileProblem.Said).text
    return if (arg != null) getString(res, arg) else getString(res)
}

/**
 * A file on the gateway that a reply linked to, opened full screen over the chat: Markdown rendered, JSON
 * indented, CSV and TSV as a table, code and text with line numbers, scrolled both ways, a PDF's pages.
 * Save and Share fetch the whole file; a text preview only reads the start the gateway hands out, while
 * a PDF is fetched whole once and kept for them. A file with nothing to show (an archive, a file the
 * gateway calls binary) gets Save and Share buttons. A link in a Markdown file to another file opens it
 * here, relative ones against the file's folder; Back goes back to the file before, and closes the viewer
 * from the first one.
 */
@Composable
internal fun FileViewer(
    file: GatewayFile?,
    onDismiss: () -> Unit,
    load: suspend (GatewayFile) -> FileLoad,
    download: suspend (GatewayFile) -> FileDownload,
    /** Drawn in place, in the desktop's rail beside the chat, rather than over it. */
    docked: Boolean = false,
    /** Already-loaded file content (e.g. an inline code artifact opened from a reply). */
    preset: FileLoad? = null,
    /** When [docked], whether this viewer is popped out into a full-screen dialog over the chat. */
    expanded: Boolean = false,
    /** When [docked], toggles the full-screen popup dialog; null hides the expand button. */
    onToggleExpand: (() -> Unit)? = null,
    /** Called when navigation inside the viewer opens a linked gateway file or image. */
    onFileOpened: ((GatewayFile) -> Unit)? = null,
    /** Streams whole files straight to disk without holding bytes in RAM (used for Save, Open in app, and large PDFs). */
    stream: (suspend (GatewayFile, maxBytes: Long?, onProgress: MediaProgress?, write: MediaByteSink) -> FileProblem?)? = null,
) {
    if (file == null) return
    // The files opened from links, the one from the reply first.
    var stack by rememberSaveable(file, stateSaver = StackSaver) { mutableStateOf(listOf(file)) }
    var picture by remember(file) { mutableStateOf<ViewerImage?>(null) }
    // Each page's scroll position, kept while a file it linked to is open on top of it.
    val pages = rememberSaveableStateHolder()
    // What the files below the top one showed, so Back finds a page as tall as it was and its scroll fits.
    val loaded = remember(file) { mutableMapOf<GatewayFile, FileLoad>() }
    // Keep fetched PDF bytes/temp files while open so toggling full-screen popup or Save/Share doesn't re-download them.
    val pdfCache = remember(file) { mutableStateMapOf<GatewayFile, ByteArray>() }
    val pdfFiles = remember(file) { mutableStateMapOf<GatewayFile, TempFileHandle>() }
    DisposableEffect(file) {
        onDispose {
            pdfFiles.values.forEach { runCatching { it.close() } }
            pdfFiles.clear()
        }
    }
    val cachedDownload: suspend (GatewayFile) -> FileDownload = { target ->
        pdfCache[target]?.let { FileDownload.Ready(it) } ?: download(target).also { got ->
            if (got is FileDownload.Ready && target.kind == GatewayFileKind.Pdf) pdfCache[target] = got.bytes
        }
    }
    val scope = rememberCoroutineScope()
    val pictures = remember(file) { LinkedPictures(scope) }
    fun pageKey(index: Int) = "$index|${stack[index].path}"
    fun back() {
        when {
            stack.size > 1 -> {
                pages.removeState(pageKey(stack.lastIndex))
                stack = stack.dropLast(1)
            }
            docked && expanded -> onToggleExpand?.invoke()
            else -> onDismiss()
        }
    }
    val page = @Composable {
        val top = stack.last()
        val initialPreset = preset.takeIf { stack.size == 1 && top == file }
        pages.SaveableStateProvider(pageKey(stack.lastIndex)) {
            FileViewerPage(
                top,
                onDismiss = ::back,
                load = { linked -> load(linked).also { if (it is FileLoad.Ready) loaded[linked] = it } },
                download = cachedDownload,
                preset = loaded[top] ?: initialPreset,
                canGoBack = stack.size > 1,
                expanded = docked && expanded,
                onToggleExpand = onToggleExpand.takeIf { docked },
                onOpenFile = { linked ->
                    when {
                        // A link to a line of this same file: rendered Markdown has no lines to go to.
                        linked.path == top.path -> Unit
                        // A picture opens in the picture viewer, like one linked from a reply.
                        linked.kind == GatewayFileKind.Image -> {
                            onFileOpened?.invoke(linked)
                            picture = ViewerImage(linked.name, fetch = { cachedDownload(linked) })
                        }
                        else -> {
                            onFileOpened?.invoke(linked)
                            stack = stack + linked
                        }
                    }
                },
                onOpenPicture = { img ->
                    img.source?.let { GatewayFile.fromLink(it) }?.let { onFileOpened?.invoke(it) }
                    picture = img
                },
                pictures = pictures,
                stream = stream,
                cachedPdfFile = { pdfFiles[top] },
                onCachePdfFile = { target, handle ->
                    val previous = pdfFiles.put(target, handle)
                    if (previous != null && previous !== handle) runCatching { previous.close() }
                },
            )
        }
    }
    // Docked, Esc steps back as the dialog's Back does: to the file below, then back to history / closed.
    PlatformBackHandler(enabled = docked && !expanded) { back() }
    if (docked && !expanded) {
        page()
    } else {
        Dialog(
            onDismissRequest = { if (docked && expanded) onToggleExpand?.invoke() else back() },
            properties = fullScreenDialogProperties(),
        ) { page() }
    }
    picture?.let { ImageViewer(it, onDismiss = { picture = null }) }
}

/** A gateway file a link opened: a picture in the picture viewer, anything else in [FileViewer]. */
@Composable
internal fun GatewayFileViewer(
    file: GatewayFile?,
    onDismiss: () -> Unit,
    load: suspend (GatewayFile) -> FileLoad,
    download: suspend (GatewayFile) -> FileDownload,
    onFileOpened: ((GatewayFile) -> Unit)? = null,
    stream: (suspend (GatewayFile, maxBytes: Long?, onProgress: MediaProgress?, write: MediaByteSink) -> FileProblem?)? = null,
) {
    if (file?.kind == GatewayFileKind.Image) {
        val image = remember(file) { ViewerImage(file.name, fetch = { download(file) }) }
        ImageViewer(image, onDismiss)
    } else {
        FileViewer(file, onDismiss, load, download, onFileOpened = onFileOpened, stream = stream)
    }
}

private enum class TransferKind { Save, Share }

/**
 * The viewer's page, apart from its window, so previews can draw it; [preset] skips loading for screenshots.
 * [onOpenFile] gets a gateway file a link in the text points at; [onOpenPicture] a picture shown in it.
 */
@Composable
internal fun FileViewerPage(
    file: GatewayFile,
    onDismiss: () -> Unit,
    load: suspend (GatewayFile) -> FileLoad,
    download: suspend (GatewayFile) -> FileDownload,
    preset: FileLoad? = null,
    canGoBack: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onOpenFile: (GatewayFile) -> Unit = {},
    onOpenPicture: (ViewerImage) -> Unit = {},
    pictures: LinkedPictures? = null,
    openPdf: suspend (ByteArray) -> PdfOpen = rememberPdfOpener(),
    openPdfFile: suspend (TempFileHandle) -> PdfOpen = rememberPdfFileOpener(),
    stream: (suspend (GatewayFile, maxBytes: Long?, onProgress: MediaProgress?, write: MediaByteSink) -> FileProblem?)? = null,
    cachedPdfFile: (() -> TempFileHandle?)? = null,
    onCachePdfFile: ((GatewayFile, TempFileHandle) -> Unit)? = null,
) {
    val kind = file.kind
    val artifact = remember(file) { artifactKind(file) }
    val tempFiles = rememberTempFileFactory()
    // A PDF's whole bytes/file once fetched for its pages, so Save and Share don't fetch it again.
    var fetched by remember(file) { mutableStateOf<ByteArray?>(null) }
    var localPdf by remember(file) { mutableStateOf(cachedPdfFile?.invoke()) }
    var uncachedHandle by remember(file) { mutableStateOf<TempFileHandle?>(null) }
    var downloadProgress by remember(file) { mutableStateOf<Pair<Long, Long?>?>(null) }
    DisposableEffect(file) {
        onDispose {
            runCatching { uncachedHandle?.close() }
            uncachedHandle = null
        }
    }
    val result by produceState(preset ?: if (kind.previewable) FileLoad.Loading else null, file, preset) {
        if (preset != null || !kind.previewable) return@produceState
        if (kind != GatewayFileKind.Pdf) {
            value = load(file)
            return@produceState
        }
        val existingHandle = cachedPdfFile?.invoke()
        if (existingHandle != null) {
            localPdf = existingHandle
            when (val opened = openPdfFile(existingHandle)) {
                is PdfOpen.Failed -> value = FileLoad.Failed(opened.problem)
                is PdfOpen.Ready -> {
                    if (!isActive) {
                        opened.pages.close()
                        return@produceState
                    }
                    value = FileLoad.Pdf(opened.pages)
                    awaitDispose { opened.pages.close() }
                }
            }
            return@produceState
        }
        if (stream != null) {
            val handle = tempFiles.create("viewer-", ".pdf")
            if (handle != null) {
                var handedToCache = false
                var handedToPage = false
                try {
                    val problem = stream(file, MAX_PDF_VIEWER_BYTES, { done, total -> downloadProgress = done to total }) { chunk ->
                        handle.append(chunk)
                    }
                    if (problem != null) {
                        val shown = if (problem is FileProblem.TooLargeToHandOver || (problem is FileProblem.Said && problem.text == MediaApi.TOO_LARGE)) {
                            FileProblem.PdfTooLarge(MAX_PDF_VIEWER_BYTES)
                        } else {
                            problem
                        }
                        value = FileLoad.Failed(shown)
                        return@produceState
                    }
                    handle.finishWrite()
                    downloadProgress = null
                    if (onCachePdfFile != null) {
                        onCachePdfFile(file, handle)
                        handedToCache = true
                    } else {
                        uncachedHandle = handle
                        handedToPage = true
                    }
                    localPdf = handle
                    when (val opened = openPdfFile(handle)) {
                        is PdfOpen.Failed -> value = FileLoad.Failed(opened.problem)
                        is PdfOpen.Ready -> {
                            if (!isActive) {
                                opened.pages.close()
                                return@produceState
                            }
                            value = FileLoad.Pdf(opened.pages)
                            awaitDispose { opened.pages.close() }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    value = FileLoad.Failed(FileProblem.PdfNoRoom)
                } finally {
                    downloadProgress = null
                    if (!handedToCache && !handedToPage) runCatching { handle.close() }
                }
                return@produceState
            }
        }
        val got = download(file)
        if (got is FileDownload.Failed) {
            value = FileLoad.Failed(got.problem)
            return@produceState
        }
        fetched = (got as FileDownload.Ready).bytes
        // Platform openers own cancellation cleanup; an opened document closes with this page.
        when (val opened = openPdf(got.bytes)) {
            is PdfOpen.Failed -> value = FileLoad.Failed(opened.problem)
            is PdfOpen.Ready -> {
                if (!isActive) {
                    opened.pages.close()
                    return@produceState
                }
                value = FileLoad.Pdf(opened.pages)
                awaitDispose { opened.pages.close() }
            }
        }
    }
    val pdf = result as? FileLoad.Pdf
    // Save and Share wait for the PDF the preview is already fetching, rather than fetch it a second time.
    val fetching = kind == GatewayFileKind.Pdf && result == FileLoad.Loading
    val ready = (result as? FileLoad.Ready)?.text?.takeUnless { it.binary }
    var wrap by remember(file) { mutableStateOf(false) }
    var raw by remember(file) { mutableStateOf(file.line != null) }
    val actions = rememberMediaActions()
    val scope = rememberCoroutineScope()
    var busy by remember(file) { mutableStateOf(false) }
    var notice by remember(file) { mutableStateOf<Notice?>(null) }
    val clipboard = LocalClipboard.current
    var copied by remember(file) { mutableStateOf(false) }

    fun startTransfer(transfer: TransferKind) {
        if (busy || fetching) return
        busy = true
        downloadProgress = null
        scope.launch {
            try {
                val streamTo: suspend (MediaByteSink) -> String? = { write ->
                    val cachedHandle = localPdf
                    val cachedBytes = fetched
                    val problem = when {
                        cachedHandle != null -> {
                            cachedHandle.streamTo({ done, total -> downloadProgress = done to total }, write)
                            null
                        }
                        cachedBytes != null -> {
                            if (cachedBytes.isNotEmpty()) write(cachedBytes)
                            null
                        }
                        stream != null -> stream(file, null, { done, total -> downloadProgress = done to total }, write)
                        else -> when (val dl = download(file)) {
                            is FileDownload.Ready -> {
                                if (dl.bytes.isNotEmpty()) write(dl.bytes)
                                null
                            }
                            is FileDownload.Failed -> dl.problem
                        }
                    }
                    problem?.let { getString(Res.string.chat_file_get_failed, file.name, fileProblemWords(it)) }
                }
                when (transfer) {
                    TransferKind.Save -> notice = Notice(actions.saveStream(file.name, streamTo))
                    TransferKind.Share -> actions.shareStream(file.name, streamTo)?.let { err ->
                        notice = Notice(err, error = true)
                    }
                }
            } finally {
                downloadProgress = null
                busy = false
            }
        }
    }

    val textual = ready != null && (kind != GatewayFileKind.Markdown && kind != GatewayFileKind.Table && artifact == null || raw)

    Column(Modifier.fillMaxSize().background(Theme[colors][background]).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            // Full screen on the desktop it's in the title bar's row: Back and Open in app stay clear of its buttons.
            Modifier.fillMaxWidth().clearOfWindowControls().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(Lucide.ArrowLeft, contentDescription = stringResource(if (canGoBack || expanded) Res.string.common_back else Res.string.chat_close), onClick = onDismiss, tint = Theme[colors][textColor])
            KindTile(kind)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    file.name,
                    style = Theme[typography][title].copy(fontWeight = FontWeight.SemiBold),
                    color = Theme[colors][textColor],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    listOfNotNull(
                        file.folder.ifEmpty { "/" },
                        (ready?.byteSize ?: localPdf?.byteSize ?: fetched?.size?.toLong())?.let(::formatBytes),
                        pdf?.pages?.aspects?.size?.let { pluralStringResource(Res.plurals.chat_pdf_pages, it, it) },
                        file.line?.let { stringResource(Res.string.chat_file_line, it) },
                    ).joinToString(" · "),
                    style = Theme[typography][caption],
                    color = Theme[colors][textTertiary],
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            if (busy || fetching) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Spinner(Modifier.size(18.dp)) }
            } else {
                IconButton(Lucide.Download, contentDescription = stringResource(Res.string.chat_save_named, file.name), onClick = { startTransfer(TransferKind.Save) })
                IconButton(shareIcon, contentDescription = shareNamed(file.name), onClick = { startTransfer(TransferKind.Share) })
            }
            if (onToggleExpand != null) {
                IconButton(
                    if (expanded) Lucide.Minimize2 else Lucide.Maximize2,
                    contentDescription = stringResource(if (expanded) Res.string.chat_file_collapse else Res.string.chat_file_expand),
                    onClick = onToggleExpand,
                )
            }
        }

        // A toolbar for what the text can do: view as rendered or source, wrap, copy.
        if (ready != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The switch takes the room the buttons leave: it fills its width and would push them off screen.
                if (kind == GatewayFileKind.Markdown || kind == GatewayFileKind.Table || kind == GatewayFileKind.Json || artifact != null) {
                    val source = stringResource(Res.string.chat_file_source)
                    val shown = stringResource(if (kind == GatewayFileKind.Json) Res.string.chat_file_formatted else Res.string.chat_file_preview)
                    SegmentedControl(
                        options = listOf(false, true),
                        selected = raw,
                        onSelect = { raw = it },
                        optionLabel = { if (it) source else shown },
                        modifier = Modifier.weight(1f),
                        optionIcon = { if (it) Lucide.FileCode else if (artifact != null) Lucide.Eye else kindIcon(kind) },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (textual) {
                    IconButton(
                        Lucide.WrapText,
                        contentDescription = stringResource(if (wrap) Res.string.chat_file_no_wrap else Res.string.chat_file_wrap),
                        onClick = { wrap = !wrap },
                        tint = if (wrap) Theme[colors][accentText] else Theme[colors][textSecondary],
                        containerColor = if (wrap) Theme[colors][accentSoft] else Color.Transparent,
                    )
                }
                IconButton(
                    if (copied) Lucide.Check else Lucide.Copy,
                    contentDescription = stringResource(Res.string.chat_file_copy_text),
                    onClick = {
                        scope.launch {
                            val clip = clipFor(ready)
                            // A clip is sent whole through the system; a failed one mustn't take the app down.
                            try {
                                clipboard.setClipEntry(plainTextClipEntry(clip.text))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                notice = Notice(getString(Res.string.chat_file_copy_failed), error = true)
                                return@launch
                            }
                            clip.note?.let { notice = Notice(clipNoteWords(it)) }
                            copied = true
                            kotlinx.coroutines.delay(1_500)
                            copied = false
                        }
                    },
                    tint = if (copied) Theme[colors][success] else Theme[colors][textSecondary],
                )
            }
        }

        notice?.let { NoticeLine(it) }
        if (busy) {
            downloadProgress?.let { (done, total) ->
                Banner(
                    "${stringResource(Res.string.update_downloading)} ${progressLabel(done, total)}",
                    tint = Theme[colors][accentText],
                    background = Theme[colors][accentSoft],
                )
            }
        }
        if (ready?.truncated == true) {
            val shownBytes = remember(ready) { ready.text.encodeToByteArray().size.toLong() }
            Banner(
                stringResource(Res.string.chat_file_showing_first, formatBytes(shownBytes), formatBytes(ready.byteSize)),
                tint = Theme[colors][warning],
                background = Theme[colors][warningSoft],
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            AnimatedContent(result, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it?.let { r -> r::class } }) { state ->
                when {
                    state == FileLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Spinner()
                            downloadProgress?.let { (done, total) ->
                                Text(
                                    progressLabel(done, total),
                                    style = Theme[typography][caption],
                                    color = Theme[colors][textTertiary],
                                )
                            }
                        }
                    }
                    state is FileLoad.Pdf -> PdfDocumentView(state.pages, Modifier.fillMaxSize())
                    // A file that failed to preview (too large, password, desktop worker error, damage) can still be saved or opened elsewhere.
                    state is FileLoad.Failed -> {
                        val recoverable = fetched != null || localPdf != null || !state.problem.isUnavailableOnGateway()
                        Unavailable(
                            file,
                            kind,
                            fileProblemText(state.problem),
                            onSave = { startTransfer(TransferKind.Save) }.takeIf { recoverable },
                            onShare = { startTransfer(TransferKind.Share) }.takeIf { recoverable },
                        )
                    }
                    ready == null -> Unavailable(
                        file,
                        kind,
                        null,
                        onSave = { startTransfer(TransferKind.Save) },
                        onShare = { startTransfer(TransferKind.Share) },
                    )
                    else -> {
                        val shown = rememberLinkedPictures(file, pictures, download, onOpenPicture)
                        val onLink by rememberUpdatedState(onOpenFile)
                        val links = remember(file) { { link: String -> file.resolve(link)?.let { onLink(it) }; Unit } }
                        CompositionLocalProvider(
                            LocalFileLinks provides links,
                            LocalMarkdownPictures provides shown,
                        ) {
                            FileBody(file, kind, artifact, ready, raw = raw, wrap = wrap)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The pictures a viewer's Markdown files show, fetched and decoded once while it's open: the library asks for
 * a picture from more than one place (in the line, then as a block of its own once it's taller than one), and
 * Back to a file shouldn't fetch its pictures again.
 */
internal class LinkedPictures(private val scope: CoroutineScope) {
    sealed interface Entry {
        data object Loading : Entry

        class Ready(val bitmap: ImageBitmap, val bytes: ByteArray) : Entry

        data object Failed : Entry
    }

    private val entries = mutableStateMapOf<GatewayFile, Entry>()

    operator fun get(file: GatewayFile): Entry? = entries[file]

    /** Starts fetching [file] unless it's already fetched or on its way. */
    fun request(file: GatewayFile, download: suspend (GatewayFile) -> FileDownload) {
        if (file in entries) return
        entries[file] = Entry.Loading
        scope.launch {
            entries[file] = when (val result = download(file)) {
                is FileDownload.Ready -> withContext(Dispatchers.Default) { decodeImageBitmap(result.bytes, PICTURE_EDGE) }
                    ?.let { Entry.Ready(it, result.bytes) } ?: Entry.Failed
                is FileDownload.Failed -> Entry.Failed
            }
        }
    }
}

/** Shows the pictures a Markdown file links to (`![chart](chart.png)`, relative to its folder) from [cache]. */
@Composable
private fun rememberLinkedPictures(
    file: GatewayFile,
    cache: LinkedPictures?,
    download: suspend (GatewayFile) -> FileDownload,
    onOpen: (ViewerImage) -> Unit,
): @Composable (String) -> MarkdownPicture? {
    val scope = rememberCoroutineScope()
    val pictures = cache ?: remember { LinkedPictures(scope) }
    val fetch by rememberUpdatedState(download)
    val open by rememberUpdatedState(onOpen)
    return remember(file, pictures) {
        { link ->
            val target = remember(link) { file.resolve(link)?.takeIf { it.kind == GatewayFileKind.Image } }
            if (target == null) {
                null
            } else {
                LaunchedEffect(target) { pictures.request(target) { fetch(it) } }
                when (val entry = pictures[target]) {
                    null, LinkedPictures.Entry.Loading -> MarkdownPicture.Loading
                    LinkedPictures.Entry.Failed -> MarkdownPicture.Failed
                    is LinkedPictures.Entry.Ready -> remember(entry) {
                        MarkdownPicture.Ready(entry.bitmap) { open(ViewerImage(target.name, bytes = entry.bytes)) }
                    }
                }
            }
        }
    }
}

/** The viewer's files as paths and lines, so the stack outlives a recreated activity. */
private val StackSaver = listSaver<List<GatewayFile>, Any>(
    save = { stack -> stack.flatMap { listOf(it.path, it.line ?: 0) } },
    restore = { saved -> saved.chunked(2).map { (path, line) -> GatewayFile(path as String, (line as Int).takeIf { it > 0 }) } },
)

/** A picture in a Markdown file is decoded at most this many pixels on a side: wider than the screen, never a whole photo. */
private const val PICTURE_EDGE = 1600

@Composable
private fun FileBody(file: GatewayFile, kind: GatewayFileKind, artifact: ArtifactKind?, text: FileText, raw: Boolean, wrap: Boolean) {
    val shape = RoundedCornerShape(Theme[radii][radiusMedium])
    val framed = Modifier.fillMaxSize().clip(shape).background(Theme[colors][well]).border(1.dp, Theme[colors][stroke], shape)
    when {
        // Rendered Markdown reads as a page of its own, not a box on one.
        kind == GatewayFileKind.Markdown && !raw ->
            Column(Modifier.fillMaxSize().verticalScrollWithBar().padding(horizontal = 8.dp, vertical = 4.dp)) {
                SelectionContainer { MarkdownText(text.text) }
            }
        kind == GatewayFileKind.Table && !raw -> {
            val delimiter = if (file.extension == "tsv") '\t' else ','
            val table = remember(text) { parseDelimited(text.text, delimiter, truncated = text.truncated) }
            DataTable(table, framed)
        }
        artifact != null && !raw -> ArtifactBody(file, artifact, text, framed, shape, wrap)
        else -> SourceCodeBody(file, kind, text, raw = raw, wrap = wrap, modifier = framed)
    }
}

@Composable
private fun SourceCodeBody(file: GatewayFile, kind: GatewayFileKind, text: FileText, raw: Boolean, wrap: Boolean, modifier: Modifier) {
    // Indented off the main thread: parsing the whole 512 KB preview would stall a frame or more.
    val formatting = kind == GatewayFileKind.Json && !text.truncated
    val pretty by produceState<Pair<FileText, String?>?>(null, text, formatting) {
        value = if (formatting) text to withContext(Dispatchers.Default) { prettyJson(text.text) } else null
    }
    val indented = pretty?.takeIf { it.first == text }?.second
    val shown = if (!raw && indented != null) indented else text.text
    CodeView(
        shown,
        language = text.language ?: languageOf(file),
        modifier = modifier.padding(vertical = 10.dp),
        focusLine = file.line.takeIf { shown === text.text },
        wrap = wrap,
    )
}

@Composable
private fun ArtifactBody(file: GatewayFile, artifact: ArtifactKind, text: FileText, framed: Modifier, shape: RoundedCornerShape, wrap: Boolean) {
    val docProblem = remember(text) { widgetProblem(text) }
    var webFailure by remember(file, text) { mutableStateOf<String?>(null) }
    val mermaidState by produceState<Pair<Boolean, String?>>(false to null, artifact, file, text) {
        if (artifact == ArtifactKind.Mermaid && docProblem == null) {
            value = true to runCatching { loadMermaidJs() }.getOrNull()
        } else {
            value = false to null
        }
    }
    val (mermaidReady, mermaidJs) = mermaidState
    val fallbackReason = when {
        docProblem != null -> fileProblemText(docProblem)
        webFailure != null -> webFailure
        artifact == ArtifactKind.Mermaid && mermaidReady && mermaidJs == null -> stringResource(Res.string.chat_widget_unreadable)
        else -> null
    }
    if (fallbackReason != null) {
        Column(Modifier.fillMaxSize()) {
            Banner(fallbackReason, tint = Theme[colors][warning], background = Theme[colors][warningSoft])
            val codeModifier = Modifier.fillMaxWidth().weight(1f).clip(shape).background(Theme[colors][well]).border(1.dp, Theme[colors][stroke], shape)
            SourceCodeBody(file, GatewayFileKind.Code, text, raw = true, wrap = wrap, modifier = codeModifier)
        }
        return
    }
    val theme = rememberWidgetTheme()
    if (artifact == ArtifactKind.Mermaid) {
        if (!mermaidReady) {
            Box(framed, contentAlignment = Alignment.Center) { Spinner() }
            return
        }
        val js = mermaidJs ?: return
        WidgetWebView(
            remember(text.text, theme, js) { mermaidDocument(text.text, theme, js) },
            theme,
            file.name,
            framed,
            onFailure = { webFailure = it },
            prewrapped = true,
        )
        return
    }
    when (artifact) {
        ArtifactKind.Html -> WidgetWebView(text.text, theme, file.name, framed, onFailure = { webFailure = it })
        ArtifactKind.Svg -> WidgetWebView(remember(text.text) { svgWidgetHtml(text.text) }, theme, file.name, framed, onFailure = { webFailure = it })
        ArtifactKind.Mermaid -> Unit
    }
}

/** A CSV or TSV file as a grid: the first row as the header, columns as wide as their cells (within reason). */
@Composable
private fun DataTable(table: DelimitedTable, modifier: Modifier) {
    if (table.rows.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.chat_file_empty), style = Theme[typography][bodySmall], color = Theme[colors][textTertiary])
        }
        return
    }
    val style = Theme[typography][bodySmall].copy(color = Theme[colors][textColor])
    val headerStyle = style.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(style, density) { with(density) { measurer.measure("Ab0-.Mw1 x", style).size.width.toDp() / 10 } }
    val widths: List<Dp> = remember(table, charWidth) {
        (0 until table.columns).map { column ->
            val longest = table.rows.take(200).maxOf { it.getOrNull(column)?.length ?: 0 }
            charWidth * longest.coerceIn(3, 32) * 1.2f + 32.dp
        }
    }
    val numberWidth = charWidth * table.rows.size.toString().length + 20.dp
    val lineColor = Theme[colors][stroke]
    val sideways = rememberScrollState()
    val list = rememberLazyListState()
    Column(modifier) {
        // On the desktop: bars down the right and along the bottom to move about a big file by, and cells to
        // select and copy, the row numbers left out.
        Box(Modifier.weight(1f)) {
        Box(Modifier.fillMaxSize().horizontalScroll(sideways)) {
            CopyableOnDesktop {
            LazyColumn(Modifier.width(numberWidth + widths.fold(0.dp) { sum, w -> sum + w }), state = list) {
                itemsIndexed(table.rows) { index, row ->
                    val header = index == 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (header) Theme[colors][surface2] else if (index % 2 == 0) Theme[colors][surface] else Color.Transparent),
                    ) {
                        DisableSelection {
                            Text(
                                if (header) "" else index.toString(),
                                style = Theme[typography][caption],
                                color = Theme[colors][textTertiary],
                                modifier = Modifier.width(numberWidth).padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                        widths.forEachIndexed { column, width ->
                            Text(
                                row.getOrNull(column).orEmpty(),
                                style = if (header) headerStyle else style,
                                maxLines = if (header) 2 else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(width).padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(lineColor))
                }
            }
            }
        }
        if (isDesktop) {
            VerticalScrollbar(list, Modifier.align(Alignment.TopEnd).fillMaxHeight())
            HorizontalScrollbar(sideways, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        }
        Text(
            listOfNotNull(
                pluralStringResource(Res.plurals.chat_table_rows, table.rows.size - 1, table.rows.size - 1),
                pluralStringResource(Res.plurals.chat_table_columns, table.columns, table.columns),
                if (table.more) stringResource(Res.string.chat_table_more) else null,
            ).joinToString(" · "),
            style = Theme[typography][caption],
            color = Theme[colors][textTertiary],
            modifier = Modifier.fillMaxWidth().background(Theme[colors][surface2]).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** No preview: why, and Save / Share so the file can still be had. */
@Composable
private fun Unavailable(
    file: GatewayFile,
    kind: GatewayFileKind,
    error: String?,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val tint = if (error != null) Theme[colors][danger] else Theme[colors][accent]
        Box(
            Modifier.size(64.dp).background(if (error != null) Theme[colors][dangerSoft] else Theme[colors][accentSoft], RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            UnstyledIcon(if (error != null) Lucide.FileWarning else kindIcon(kind), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                error != null -> stringResource(Res.string.chat_file_open_failed, file.name)
                file.extension.isEmpty() -> stringResource(Res.string.chat_file_no_preview)
                else -> stringResource(Res.string.chat_file_no_preview_type, file.extension.uppercase())
            },
            style = Theme[typography][label],
            color = Theme[colors][textColor],
        )
        Spacer(Modifier.height(6.dp))
        Text(
            error ?: stringResource(if (isDesktop) Res.string.chat_file_save_hint_desktop else Res.string.chat_file_save_hint),
            style = Theme[typography][bodySmall],
            color = Theme[colors][textTertiary],
            textAlign = TextAlign.Center,
        )
        if (onSave != null || onShare != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onSave != null) {
                    Button(stringResource(Res.string.chat_save), onClick = onSave, leadingIcon = Lucide.Download, size = ButtonSize.Small)
                }
                if (onShare != null) {
                    Button(shareText(), onClick = onShare, leadingIcon = shareIcon, variant = ButtonVariant.Secondary, size = ButtonSize.Small)
                }
            }
        }
    }
}

private fun FileProblem.isUnavailableOnGateway(): Boolean = when (this) {
    FileProblem.NotConnected,
    FileProblem.Missing,
    FileProblem.Private,
    is FileProblem.Refused,
    FileProblem.SignInAgain -> true
    else -> false
}

private fun progressLabel(downloaded: Long, total: Long?): String =
    if (total != null && total > 0) "${formatBytes(downloaded)} / ${formatBytes(total)}" else formatBytes(downloaded)

private class Notice(val text: String, val error: Boolean = false)

@Composable
private fun NoticeLine(notice: Notice) = Banner(
    notice.text,
    tint = if (notice.error) Theme[colors][danger] else Theme[colors][success],
    background = if (notice.error) Theme[colors][dangerSoft] else Theme[colors][successSoft],
)

@Composable
private fun Banner(text: String, tint: Color, background: Color) {
    Text(
        text,
        style = Theme[typography][caption],
        color = tint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .background(background, RoundedCornerShape(Theme[radii][radiusMedium]))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** The file's type on a tinted tile, the way the viewer's header names it at a glance. */
@Composable
private fun KindTile(kind: GatewayFileKind) {
    Box(
        Modifier.size(40.dp).background(Theme[colors][accentSoft], RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        UnstyledIcon(kindIcon(kind), contentDescription = kindLabel(kind), tint = Theme[colors][accentText], modifier = Modifier.size(22.dp))
    }
}

internal fun kindIcon(kind: GatewayFileKind): ImageVector = when (kind) {
    GatewayFileKind.Markdown, GatewayFileKind.Text -> Lucide.FileText
    GatewayFileKind.Json -> Lucide.Braces
    GatewayFileKind.Table -> Lucide.FileSpreadsheet
    GatewayFileKind.Code -> Lucide.FileCode
    GatewayFileKind.Image -> Lucide.FileImage
    GatewayFileKind.Pdf -> Lucide.BookOpen
    GatewayFileKind.Other -> Lucide.File
}

/** The highlighter's name for a file the gateway gave no language for. */
private fun languageOf(file: GatewayFile): String? = when (file.kind) {
    GatewayFileKind.Json -> "json"
    GatewayFileKind.Code -> file.extension.ifEmpty { file.name.lowercase() }
    else -> null
}

/** What Copy puts on the clipboard, and what to tell when that isn't the whole file. */
internal class Clip(val text: String, val note: ClipNote?)

/** Why Copy didn't take the whole file: only the part shown came, or the first [chars] characters. */
internal sealed interface ClipNote {
    data object PartShown : ClipNote

    data class FirstChars(val chars: Int) : ClipNote
}

private suspend fun clipNoteWords(note: ClipNote): String = when (note) {
    ClipNote.PartShown -> getString(Res.string.chat_file_copied_part)
    is ClipNote.FirstChars -> getString(Res.string.chat_file_copied_first, groupThousands(note.chars))
}

/** Share on the phone, Open in app on the desktop. */
@Composable
internal fun shareText(): String = stringResource(if (isDesktop) Res.string.chat_open_in_app else Res.string.chat_share)

/** [shareText] for one file, as a button's description. */
@Composable
internal fun shareNamed(name: String): String =
    stringResource(if (isDesktop) Res.string.chat_open_in_app_named else Res.string.chat_share_named, name)

/** A file kind's name, for screen readers. */
@Composable
internal fun kindLabel(kind: GatewayFileKind): String = stringResource(
    when (kind) {
        GatewayFileKind.Markdown -> Res.string.chat_kind_markdown
        GatewayFileKind.Json -> Res.string.chat_kind_json
        GatewayFileKind.Table -> Res.string.chat_kind_table
        GatewayFileKind.Code -> Res.string.chat_kind_code
        GatewayFileKind.Text -> Res.string.chat_kind_text
        GatewayFileKind.Image -> Res.string.chat_kind_picture
        GatewayFileKind.Pdf -> Res.string.chat_kind_pdf
        GatewayFileKind.Other -> Res.string.chat_kind_file
    },
)

/**
 * The preview's text for the clipboard, cut to [max] characters: Android hands a clip to the system in
 * one Binder call, and the 512 KB preview as UTF-16 is past its 1 MB limit. A cut never splits a
 * surrogate pair.
 */
internal fun clipFor(file: FileText, max: Int = MAX_CLIP_CHARS): Clip {
    if (file.text.length <= max) {
        return Clip(file.text, if (file.truncated) ClipNote.PartShown else null)
    }
    val end = if (file.text[max - 1].isHighSurrogate()) max - 1 else max
    return Clip(file.text.substring(0, end), ClipNote.FirstChars(end))
}

/** 100000 as 100,000. */
private fun groupThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** About 200 KB as UTF-16 in a Binder parcel: well inside its limit. */
private const val MAX_CLIP_CHARS = 100_000

/** 1.4 KB, 12 MB: one decimal under ten, none above. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val shown = if (value < 10) ((value * 10).toLong() / 10.0).toString().removeSuffix(".0") else value.toLong().toString()
    return "$shown ${units[unit]}"
}
