package dev.hermeskotlin.ui.chat

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.media.FileText
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.core.media.MediaApi
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.designsystem.components.CodeArtifact
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Loads raw bytes for inline reply/attachment images: `data:` URLs, web URLs, or gateway files
 * read through [profile] / [sessionId] with legacy `/api/files/download` fallback.
 */
@OptIn(ExperimentalEncodingApi::class)
internal suspend fun MediaApi.loadChatMedia(
    gateway: GatewayUrl?,
    source: String,
    profile: String?,
    sessionId: String? = null,
): ByteArray? = when {
    source.startsWith("data:") -> runCatching { Base64.decode(source.substringAfter("base64,")) }.getOrNull()
    source.startsWith("http://") || source.startsWith("https://") -> (remote(source) as? ApiResult.Success)?.value
    else -> {
        val url = gateway ?: return null
        val cleanPath = source.removePrefix("file://")
        val preferFs = profile != null || cleanPath.startsWith("/") || cleanPath.startsWith("~/")
        val primary = if (preferFs) download(url, cleanPath, profile, sessionId) else file(url, cleanPath)
        val resolved = if (primary is ApiResult.Success) {
            primary
        } else if (preferFs) {
            file(url, cleanPath)
        } else {
            download(url, cleanPath, profile, sessionId)
        }
        (resolved as? ApiResult.Success)?.value
    }
}

/**
 * Streams a media or file [source] straight to [write] without holding the whole payload in memory,
 * falling back to legacy `/api/files/download` or inline remote/data reads when needed.
 */
internal suspend fun MediaApi.streamChatMedia(
    gateway: GatewayUrl?,
    source: String,
    profile: String?,
    sessionId: String? = null,
    onProgress: MediaProgress? = null,
    write: MediaByteSink,
): FileProblem? {
    if (source.startsWith("data:") || source.startsWith("http://") || source.startsWith("https://")) {
        val bytes = loadChatMedia(gateway = null, source = source, profile = null) ?: return FileProblem.CouldNotOpen
        if (bytes.isNotEmpty()) write(bytes)
        onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
        return null
    }
    val url = gateway ?: return FileProblem.NotConnected
    val cleanPath = source.removePrefix("file://")
    val res = downloadTo(url, cleanPath, profile, sessionId, maxBytes = null, onProgress = onProgress, write = write)
    if (res is ApiResult.Success) return null
    if (res is ApiResult.Failed && res.status in setOf(404, 405, 501)) {
        val legacy = file(url, cleanPath)
        if (legacy is ApiResult.Success) {
            if (legacy.value.isNotEmpty()) write(legacy.value)
            onProgress?.invoke(legacy.value.size.toLong(), legacy.value.size.toLong())
            return null
        }
    }
    return fileError(res, whole = true)
}

/** The start of gateway [file] for its viewer, read through [profile]; falls back to Range head for > 64 MiB text files. */
internal suspend fun MediaApi.fileLoad(gateway: GatewayUrl, file: GatewayFile, profile: String?, sessionId: String? = null): FileLoad {
    val result = readText(gateway, file.path, profile, sessionId)
    if (result is ApiResult.Success) return FileLoad.Ready(result.value)
    if (result is ApiResult.Failed && result.status == 413) {
        val head = readTextHead(gateway, file.path, profile, sessionId)
        if (head is ApiResult.Success) return FileLoad.Ready(head.value)
        return FileLoad.Failed(FileProblem.TooLargeToPreview)
    }
    return FileLoad.Failed(fileError(result))
}

/** The whole of gateway [file], read through [profile]: for Save, Share and a linked picture. */
internal suspend fun MediaApi.fileDownload(gateway: GatewayUrl, file: GatewayFile, profile: String?, sessionId: String? = null): FileDownload =
    when (val result = download(gateway, file.path, profile, sessionId)) {
        is ApiResult.Success -> FileDownload.Ready(result.value)
        else -> FileDownload.Failed(fileError(result, whole = true))
    }

/** Streams gateway [file] into [write] without holding the file in memory. */
internal suspend fun MediaApi.fileStream(
    gateway: GatewayUrl,
    file: GatewayFile,
    profile: String?,
    sessionId: String? = null,
    maxBytes: Long? = null,
    onProgress: MediaProgress? = null,
    write: MediaByteSink,
): FileProblem? = when (val result = downloadTo(gateway, file.path, profile, sessionId, maxBytes, onProgress, write)) {
    is ApiResult.Success -> null
    else -> fileError(result, whole = true)
}

/**
 * Why a gateway file couldn't be read, in the words the file viewer shows. [whole] is for the whole file
 * (Save, Share, a linked picture), where too large means past the phone's cap, not the preview's.
 */
internal fun fileError(result: ApiResult<*>, whole: Boolean = false): FileProblem = when (result) {
    is ApiResult.Failed -> when (result.status) {
        404 -> FileProblem.Missing
        403 -> if (result.message.contains("sensitive", ignoreCase = true)) {
            FileProblem.Private
        } else {
            FileProblem.Refused(result.message)
        }
        413 -> when {
            !whole -> FileProblem.TooLargeToPreview
            result.message == MediaApi.TOO_LARGE -> FileProblem.Said(MediaApi.TOO_LARGE)
            else -> FileProblem.TooLargeToHandOver
        }
        else -> FileProblem.Said(result.message.trimEnd('.') + ".")
    }
    ApiResult.SessionExpired -> FileProblem.SignInAgain
    ApiResult.RateLimited -> FileProblem.Busy
    is ApiResult.Unavailable -> FileProblem.Unreachable
    else -> FileProblem.CouldNotOpen
}

/** In-memory preview metadata for a fenced `html` / `svg` / `mermaid` code block opened in [FileViewer]. */
internal data class ArtifactPreview(val artifact: CodeArtifact, val id: Long) {
    private val ext: String
        get() = when (artifact.language) {
            "html" -> "html"
            "svg" -> "svg"
            "mermaid" -> "mmd"
            else -> "txt"
        }

    val bytes: ByteArray by lazy { artifact.content.encodeToByteArray() }
    val file: GatewayFile = GatewayFile("Chat artifact/artifact-$id.$ext")
    val load: FileLoad.Ready by lazy {
        FileLoad.Ready(FileText(artifact.content, bytes.size.toLong(), truncated = false, binary = false, language = artifact.language))
    }
}
