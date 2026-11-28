package dev.hermeskotlin.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.core.media.MediaApi
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.designsystem.components.CodeArtifact
import dev.hermeskotlin.designsystem.components.LocalCodePreview
import dev.hermeskotlin.designsystem.components.LocalFileLinks
import dev.hermeskotlin.designsystem.components.Toast
import dev.hermeskotlin.ui.chat.ArtifactPreview
import dev.hermeskotlin.ui.chat.FileDownload
import dev.hermeskotlin.ui.chat.FileLoad
import dev.hermeskotlin.ui.chat.FileProblem
import dev.hermeskotlin.ui.chat.FileViewer
import dev.hermeskotlin.ui.chat.GatewayFileViewer
import dev.hermeskotlin.ui.chat.ImageViewer
import dev.hermeskotlin.ui.chat.LocalMediaLoader
import dev.hermeskotlin.ui.chat.LocalMediaStreamer
import dev.hermeskotlin.ui.chat.LocalNotice
import dev.hermeskotlin.ui.chat.LocalOpenImage
import dev.hermeskotlin.ui.chat.LocalWidgetFiles
import dev.hermeskotlin.ui.chat.MediaByteSink
import dev.hermeskotlin.ui.chat.MediaProgress
import dev.hermeskotlin.ui.chat.ViewerImage
import dev.hermeskotlin.ui.chat.WidgetFiles
import dev.hermeskotlin.ui.chat.fileDownload
import dev.hermeskotlin.ui.chat.fileLoad
import dev.hermeskotlin.ui.chat.fileStream
import dev.hermeskotlin.ui.chat.loadChatMedia
import dev.hermeskotlin.ui.chat.streamChatMedia
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.rooms_file_no_owner
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString

/** Identity that scopes gateway file/media reads to a specific gateway, bot profile, and optional session. */
internal data class ChannelMediaOwner(
    val gatewayUrl: GatewayUrl,
    val profile: String?,
    val sessionId: String?,
    val requireProfile: Boolean = false,
) {
    val canAccessGatewayFiles: Boolean get() = !requireProfile || !profile.isNullOrBlank()
}

internal fun resolveChannelMediaOwner(
    gatewayUrl: GatewayUrl,
    activeChannel: TaskChannel?,
    lineProfile: String?,
): ChannelMediaOwner {
    val cleanLineProfile = lineProfile?.trim()?.takeIf { it.isNotEmpty() }
    return if (activeChannel?.isRoom == true) {
        ChannelMediaOwner(
            gatewayUrl = gatewayUrl,
            profile = cleanLineProfile,
            sessionId = null,
            requireProfile = true,
        )
    } else {
        ChannelMediaOwner(
            gatewayUrl = gatewayUrl,
            profile = cleanLineProfile ?: activeChannel?.profile?.trim()?.takeIf { it.isNotEmpty() },
            sessionId = activeChannel?.id?.takeIf { it.isNotBlank() },
            requireProfile = false,
        )
    }
}

internal interface ChannelMediaHandler {
    suspend fun loadMedia(owner: ChannelMediaOwner, source: String): ByteArray?
    suspend fun streamMedia(
        owner: ChannelMediaOwner,
        source: String,
        onProgress: MediaProgress?,
        write: MediaByteSink,
    ): FileProblem?
    fun openFile(owner: ChannelMediaOwner, link: String)
    fun openImage(owner: ChannelMediaOwner, image: ViewerImage)
    fun openArtifact(artifact: CodeArtifact)
    fun widgetFiles(owner: ChannelMediaOwner): WidgetFiles?
    fun notify(message: String)
}

internal val LocalChannelMediaHandler = staticCompositionLocalOf<ChannelMediaHandler?> { null }
internal val LocalChannelGateway = staticCompositionLocalOf<GatewayUrl?> { null }

private data class ChannelMediaCacheKey(val owner: ChannelMediaOwner, val source: String)

private data class ChannelNotice(val text: String, val seq: Long)

private sealed interface ChannelPreviewTarget {
    data class File(val owner: ChannelMediaOwner, val file: GatewayFile) : ChannelPreviewTarget
    data class Artifact(val preview: ArtifactPreview) : ChannelPreviewTarget
}

private const val MAX_CHANNEL_CACHED_MEDIA = 24
private const val CHANNEL_NOTICE_MS = 3_500L

/**
 * Provides media loading, streaming, inline widgets, file/image viewers, and save/share notices for
 * [TaskChannelLog] panels (Dispatch task inspector, Dispatch coordinator, and Calendar agent).
 */
@Composable
internal fun ProvideChannelMedia(
    gateway: GatewayUrl,
    activeChannel: TaskChannel?,
    mediaApi: MediaApi,
    content: @Composable () -> Unit,
) {
    var viewingImage by remember(gateway) { mutableStateOf<Pair<ChannelMediaOwner, ViewerImage>?>(null) }
    var previewing by remember(gateway) { mutableStateOf<ChannelPreviewTarget?>(null) }
    var notice by remember(gateway) { mutableStateOf<ChannelNotice?>(null) }
    var shownNotice by remember(gateway) { mutableStateOf<ChannelNotice?>(null) }
    if (notice != null) shownNotice = notice
    var nextArtifactId by remember { mutableLongStateOf(0L) }
    var nextNoticeSeq by remember { mutableLongStateOf(0L) }
    val mediaCache = remember(gateway) { mutableMapOf<ChannelMediaCacheKey, ByteArray>() }

    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(CHANNEL_NOTICE_MS)
        notice = null
    }

    val handler = remember(gateway, mediaApi) {
        object : ChannelMediaHandler {
            override suspend fun loadMedia(owner: ChannelMediaOwner, source: String): ByteArray? {
                val isExternal = source.startsWith("data:") || source.startsWith("http://") || source.startsWith("https://")
                if (!isExternal && !owner.canAccessGatewayFiles) return null
                val key = ChannelMediaCacheKey(owner, source)
                mediaCache[key]?.let { return it }
                val loaded = mediaApi.loadChatMedia(owner.gatewayUrl, source, owner.profile, owner.sessionId) ?: return null
                if (mediaCache.size >= MAX_CHANNEL_CACHED_MEDIA) mediaCache.remove(mediaCache.keys.first())
                mediaCache[key] = loaded
                return loaded
            }

            override suspend fun streamMedia(
                owner: ChannelMediaOwner,
                source: String,
                onProgress: MediaProgress?,
                write: MediaByteSink,
            ): FileProblem? {
                val isExternal = source.startsWith("data:") || source.startsWith("http://") || source.startsWith("https://")
                if (!isExternal && !owner.canAccessGatewayFiles) {
                    return FileProblem.Said(getString(Res.string.rooms_file_no_owner))
                }
                return mediaApi.streamChatMedia(owner.gatewayUrl, source, owner.profile, owner.sessionId, onProgress, write)
            }

            override fun openFile(owner: ChannelMediaOwner, link: String) {
                val file = GatewayFile.fromLink(link) ?: return
                previewing = ChannelPreviewTarget.File(owner, file)
            }

            override fun openImage(owner: ChannelMediaOwner, image: ViewerImage) {
                viewingImage = owner to image
            }

            override fun openArtifact(artifact: CodeArtifact) {
                previewing = ChannelPreviewTarget.Artifact(ArtifactPreview(artifact, ++nextArtifactId))
            }

            override fun widgetFiles(owner: ChannelMediaOwner): WidgetFiles? {
                if (!owner.canAccessGatewayFiles) return null
                return WidgetFiles(
                    identity = owner,
                    load = { file -> mediaApi.fileLoad(owner.gatewayUrl, file, owner.profile, owner.sessionId) },
                    download = { file -> mediaApi.fileDownload(owner.gatewayUrl, file, owner.profile, owner.sessionId) },
                )
            }

            override fun notify(message: String) {
                notice = ChannelNotice(message, ++nextNoticeSeq)
            }
        }
    }

    val defaultOwner = remember(gateway, activeChannel?.id, activeChannel?.type, activeChannel?.profile) {
        resolveChannelMediaOwner(gateway, activeChannel, lineProfile = null)
    }
    val defaultLoader = remember<suspend (String) -> ByteArray?>(handler, defaultOwner) {
        { source -> handler.loadMedia(defaultOwner, source) }
    }
    val defaultStreamer = remember<suspend (String, MediaProgress?, MediaByteSink) -> FileProblem?>(handler, defaultOwner) {
        { source, onProgress, write -> handler.streamMedia(defaultOwner, source, onProgress, write) }
    }
    val defaultWidgetFiles = remember(handler, defaultOwner) { handler.widgetFiles(defaultOwner) }
    val defaultOpenImage = remember<(ViewerImage) -> Unit>(handler, defaultOwner) {
        { image -> handler.openImage(defaultOwner, image) }
    }
    val defaultFileLinks = remember<(String) -> Unit>(handler, defaultOwner) {
        { link -> handler.openFile(defaultOwner, link) }
    }

    CompositionLocalProvider(
        LocalChannelMediaHandler provides handler,
        LocalChannelGateway provides gateway,
        LocalMediaLoader provides defaultLoader,
        LocalMediaStreamer provides defaultStreamer,
        LocalWidgetFiles provides defaultWidgetFiles,
        LocalOpenImage provides defaultOpenImage,
        LocalFileLinks provides defaultFileLinks,
        LocalCodePreview provides handler::openArtifact,
        LocalNotice provides handler::notify,
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                shownNotice?.let { Toast(message = it.text) }
            }
        }

        viewingImage?.let { (owner, image) ->
            CompositionLocalProvider(
                LocalMediaLoader provides { source -> handler.loadMedia(owner, source) },
            ) {
                ImageViewer(image, onDismiss = { viewingImage = null })
            }
        }

        previewing?.let { current ->
            key(current) {
                when (current) {
                    is ChannelPreviewTarget.File -> GatewayFileViewer(
                        current.file,
                        onDismiss = { previewing = null },
                        load = { linked ->
                            if (!current.owner.canAccessGatewayFiles) {
                                FileLoad.Failed(FileProblem.Said(getString(Res.string.rooms_file_no_owner)))
                            } else {
                                mediaApi.fileLoad(current.owner.gatewayUrl, linked, current.owner.profile, current.owner.sessionId)
                            }
                        },
                        download = { linked ->
                            if (!current.owner.canAccessGatewayFiles) {
                                FileDownload.Failed(FileProblem.Said(getString(Res.string.rooms_file_no_owner)))
                            } else {
                                mediaApi.fileDownload(current.owner.gatewayUrl, linked, current.owner.profile, current.owner.sessionId)
                            }
                        },
                        stream = { linked, maxBytes, onProgress, write ->
                            if (!current.owner.canAccessGatewayFiles) {
                                FileProblem.Said(getString(Res.string.rooms_file_no_owner))
                            } else {
                                mediaApi.fileStream(current.owner.gatewayUrl, linked, current.owner.profile, current.owner.sessionId, maxBytes, onProgress, write)
                            }
                        },
                    )
                    is ChannelPreviewTarget.Artifact -> FileViewer(
                        current.preview.file,
                        onDismiss = { previewing = null },
                        load = { current.preview.load },
                        download = { FileDownload.Ready(current.preview.bytes) },
                        preset = current.preview.load,
                    )
                }
            }
        }
    }
}
