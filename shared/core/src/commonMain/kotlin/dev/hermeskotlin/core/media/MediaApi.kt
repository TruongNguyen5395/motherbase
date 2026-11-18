package dev.hermeskotlin.core.media

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.apiCall
import dev.hermeskotlin.core.network.map
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class MediaResponse(@SerialName("data_url") val dataUrl: String = "")

@Serializable
private data class ReadTextResponse(
    val text: String = "",
    val byteSize: Long = 0,
    val truncated: Boolean = false,
    val binary: Boolean = false,
    val language: String? = null,
)

/**
 * Gateway-side images for a remote client (hermes_cli/web_routers/files.py `GET /api/media`), e.g. the
 * photos a stored prompt refers to with `@image:<path>`. Limited by the gateway to its media roots.
 */
class MediaApi(
    private val client: HttpClient,
    /** For pictures from the open web: no gateway cookies go along. */
    private val web: HttpClient = client,
    /** The most a linked file is fetched into memory for Save or Share ([download]). */
    private val maxDownloadBytes: Long = MAX_DOWNLOAD_BYTES,
) {

    /**
     * Any file on the gateway by absolute path (`GET /api/files/download`, what Desktop opens remote
     * `MEDIA:` files with), falling back to [image] for pictures when managed files are locked down.
     */
    suspend fun file(url: GatewayUrl, path: String): ApiResult<ByteArray> {
        val download = apiCall {
            client.get(url.resolve("api/files/download")) { parameter("path", path) }
        }.map { it.body<ByteArray>() }
        if (download is ApiResult.Success) return download
        val isImage = path.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        return if (isImage) image(url, path) else download
    }

    /**
     * The start of a gateway file as text, for a preview (`GET /api/fs/read-text`, Desktop's file
     * preview): up to the gateway's 512 KB cap, with whether it stopped short and whether the file
     * looked binary. [profile] picks the profile's backend when it runs in a sandbox.
     */
    suspend fun readText(url: GatewayUrl, path: String, profile: String? = null, sessionId: String? = null): ApiResult<FileText> = apiCall {
        client.get(url.resolve("api/fs/read-text")) {
            parameter("path", path)
            profile?.let { parameter("profile", it) }
            sessionId?.let { parameter("session_id", it) }
        }
    }.map { response ->
        val body = response.body<ReadTextResponse>()
        FileText(body.text, body.byteSize, body.truncated, body.binary, body.language?.takeIf { it.isNotBlank() && it != "text" })
    }

    /**
     * Streams a gateway file a reply linked to (`GET /api/fs/download`, falling back to `api/files/download`
     * when no [profile] is asked for and the gateway lacks `/api/fs/download`) into [write] without holding
     * the whole file in memory. When [maxBytes] is null, there is no client-side size limit.
     */
    suspend fun downloadTo(
        url: GatewayUrl,
        path: String,
        profile: String? = null,
        sessionId: String? = null,
        maxBytes: Long? = null,
        onProgress: ((downloaded: Long, total: Long?) -> Unit)? = null,
        write: suspend (ByteArray) -> Unit,
    ): ApiResult<Long> {
        val primary = streamRoute(
            endpoint = "api/fs/download",
            url = url,
            path = path,
            profile = profile,
            sessionId = sessionId,
            maxBytes = maxBytes,
            onProgress = onProgress,
            write = write,
        )
        val result = primary.result
        val noRoute = result is ApiResult.Failed &&
            (result.status == 405 || result.status == 404 && result.message in setOf("Not Found", "HTTP 404"))
        if (!noRoute) return result
        // The managed route reads the host's disk, not the profile's backend: the same path in a
        // sandboxed profile could be a different file there, or none.
        if (profile != null) return ApiResult.Failed(501, NO_PROFILE_DOWNLOAD)

        val fallback = streamRoute(
            endpoint = "api/files/download",
            url = url,
            path = path,
            maxBytes = maxBytes,
            onProgress = onProgress,
            write = write,
        ).result
        if (fallback is ApiResult.Success || !isImagePath(path)) return fallback
        return when (val img = image(url, path)) {
            is ApiResult.Success -> {
                val bytes = img.value
                if (maxBytes != null && bytes.size > maxBytes) {
                    ApiResult.Failed(413, TOO_LARGE)
                } else {
                    if (bytes.isNotEmpty()) write(bytes)
                    onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
                    ApiResult.Success(bytes.size.toLong())
                }
            }
            is ApiResult.Failed -> fallback
            else -> img.map { 0L }
        }
    }

    /**
     * A whole gateway file in memory (`GET /api/fs/download`), capped at [maxDownloadBytes]. Prefer
     * [downloadTo] for Save, Open in app, and large files.
     */
    suspend fun download(url: GatewayUrl, path: String, profile: String? = null, sessionId: String? = null): ApiResult<ByteArray> {
        val buffer = Buffer()
        return downloadTo(url, path, profile, sessionId, maxBytes = maxDownloadBytes) { chunk ->
            buffer.write(chunk)
        }.map { buffer.readByteArray() }
    }

    /**
     * Reads at most the first [maxBytes] of a gateway file using HTTP `Range` on `/api/fs/download` (used when
     * `/api/fs/read-text` refuses files larger than its 64 MiB source limit).
     */
    suspend fun readTextHead(
        url: GatewayUrl,
        path: String,
        profile: String? = null,
        sessionId: String? = null,
        maxBytes: Int = TEXT_HEAD_BYTES,
    ): ApiResult<FileText> {
        require(maxBytes > 0)
        var buffer = Buffer()
        val reply = streamRoute(
            endpoint = "api/fs/download",
            url = url,
            path = path,
            profile = profile,
            sessionId = sessionId,
            maxBytes = null,
            rangeEndInclusive = maxBytes.toLong() - 1,
            stopAtBytes = maxBytes.toLong(),
            onProgress = null,
        ) { chunk -> buffer.write(chunk) }
        val result = reply.result
        val noRoute = result is ApiResult.Failed &&
            (result.status == 405 || result.status == 404 && result.message in setOf("Not Found", "HTTP 404"))
        val resolved = if (noRoute && profile == null) {
            buffer = Buffer()
            streamRoute(
                endpoint = "api/files/download",
                url = url,
                path = path,
                maxBytes = null,
                rangeEndInclusive = maxBytes.toLong() - 1,
                stopAtBytes = maxBytes.toLong(),
                onProgress = null,
            ) { chunk -> buffer.write(chunk) }
        } else if (noRoute) {
            return ApiResult.Failed(501, NO_PROFILE_DOWNLOAD)
        } else {
            reply
        }
        return resolved.result.map {
            val raw = buffer.readByteArray()
            val totalSize = resolved.totalSize ?: raw.size.toLong()
            if (containsBinaryNul(raw)) {
                FileText("", byteSize = totalSize, truncated = false, binary = true, language = null)
            } else {
                val truncated = totalSize > raw.size
                val clean = if (truncated) trimIncompleteUtf8Tail(raw) else raw
                FileText(
                    text = clean.decodeToString(),
                    byteSize = totalSize,
                    truncated = totalSize > clean.size,
                    binary = false,
                    language = null,
                )
            }
        }
    }

    private suspend fun streamRoute(
        endpoint: String,
        url: GatewayUrl,
        path: String,
        profile: String? = null,
        sessionId: String? = null,
        maxBytes: Long? = null,
        rangeEndInclusive: Long? = null,
        stopAtBytes: Long? = null,
        onProgress: ((downloaded: Long, total: Long?) -> Unit)? = null,
        write: suspend (ByteArray) -> Unit,
    ): StreamReply {
        var totalSize: Long? = null
        val result = try {
            client.prepareGet(url.resolve(endpoint)) {
                parameter("path", path)
                profile?.let { parameter("profile", it) }
                sessionId?.let { parameter("session_id", it) }
                rangeEndInclusive?.let { header(HttpHeaders.Range, "bytes=0-$it") }
                // A large file over a slow link takes as long as it takes; only a stall gives up.
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = DOWNLOAD_STALL_MS
                }
            }.execute { response ->
                val checked = apiCall { response }
                if (checked !is ApiResult.Success) return@execute checked.map { 0L }
                val total = parseTotalLength(response)
                totalSize = total
                if (maxBytes != null && total != null && total > maxBytes) {
                    return@execute ApiResult.Failed(413, TOO_LARGE)
                }
                val channel = response.bodyAsChannel()
                var written = 0L
                while (true) {
                    var limit = STREAM_CHUNK_BYTES
                    if (maxBytes != null) {
                        val remainingAllowed = (maxBytes - written + 1).coerceAtLeast(1L)
                        limit = minOf(limit, remainingAllowed)
                    }
                    if (stopAtBytes != null) {
                        val remainingWanted = stopAtBytes - written
                        if (remainingWanted <= 0L) break
                        limit = minOf(limit, remainingWanted)
                    }
                    val chunk = try {
                        channel.readRemaining(limit).readByteArray()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return@execute ApiResult.Unavailable(e.message ?: "Download failed")
                    }
                    if (chunk.isEmpty()) break
                    written += chunk.size
                    if (maxBytes != null && written > maxBytes) {
                        return@execute ApiResult.Failed(413, TOO_LARGE)
                    }
                    try {
                        write(chunk)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw LocalSinkException(e)
                    }
                    onProgress?.invoke(written, total)
                    if (stopAtBytes != null && written >= stopAtBytes) break
                }
                ApiResult.Success(written)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalSinkException) {
            throw e.cause
        } catch (e: Exception) {
            ApiResult.Unavailable(e.message ?: e::class.simpleName ?: "Connection failed")
        }
        return StreamReply(result, totalSize)
    }

    private class LocalSinkException(override val cause: Exception) : Exception(cause)

    private class StreamReply(val result: ApiResult<Long>, val totalSize: Long?)

    private fun parseTotalLength(response: HttpResponse): Long? {
        val contentRange = response.headers[HttpHeaders.ContentRange]
        if (!contentRange.isNullOrBlank()) {
            // e.g. "bytes 0-524287/104857600"
            val totalPart = contentRange.substringAfterLast('/').trim()
            if (totalPart != "*") totalPart.toLongOrNull()?.let { return it }
        }
        return response.contentLength()
    }

    private fun isImagePath(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /**
     * Makes the folder [path] on the gateway's machine, and any folders above it that are missing
     * (`POST /api/files/mkdir`, Desktop's New folder). One already there is fine. `~/` paths are the
     * gateway's home; like the managed downloads, this is the host's disk, not a sandboxed profile's.
     * The route makes missing folders rather than answering 404, so any 404 is a gateway without it. So is a
     * 405: the dashboard's catch-all only takes GET, so a POST to a route it lacks is "Method Not Allowed".
     */
    suspend fun makeFolder(url: GatewayUrl, path: String): ApiResult<Unit> = apiCall {
        client.post(url.resolve("api/files/mkdir")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("path", path) })
        }
    }.map { }.let { if (it is ApiResult.Failed && (it.status == 404 || it.status == 405)) ApiResult.Failed(404, NO_MKDIR) else it }

    /** A reply's picture from the web (`![alt](https://...)`). */
    suspend fun remote(url: String): ApiResult<ByteArray> = apiCall { web.get(url) }.map { response ->
        // A reply can point anywhere; a huge "picture" mustn't fill the phone's memory.
        check((response.contentLength() ?: 0) <= MAX_REMOTE_BYTES) { "Picture is too large" }
        response.body<ByteArray>().also { check(it.size <= MAX_REMOTE_BYTES) { "Picture is too large" } }
    }

    companion object {
        private const val MAX_REMOTE_BYTES = 20L * 1024 * 1024

        private const val STREAM_CHUNK_BYTES = 64L * 1024

        private const val DOWNLOAD_STALL_MS = 30_000L

        const val TEXT_HEAD_BYTES = 512 * 1024

        const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024

        const val TOO_LARGE = "It's over 64 MB, too large to fetch to the phone from the chat."

        /** Why [makeFolder] failed on a gateway older than `POST /api/files/mkdir`. */
        const val NO_MKDIR = "This gateway can't make folders. Update Hermes, or make the folder on the gateway's machine."

        const val NO_PROFILE_DOWNLOAD = "This gateway can't hand over whole files from a profile yet. Update Hermes on it."

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

        private const val BINARY_SAMPLE_BYTES = 8192

        internal fun containsBinaryNul(bytes: ByteArray): Boolean {
            val end = minOf(bytes.size, BINARY_SAMPLE_BYTES)
            for (i in 0 until end) {
                if (bytes[i] == 0.toByte()) return true
            }
            return false
        }

        /** Drops an incomplete multi-byte UTF-8 sequence when a preview slice stopped mid-character. */
        internal fun trimIncompleteUtf8Tail(bytes: ByteArray): ByteArray {
            if (bytes.isEmpty()) return bytes
            var start = bytes.size - 1
            var continuations = 0
            while (start >= 0 && continuations < 3 && (bytes[start].toInt() and 0xC0) == 0x80) {
                start--
                continuations++
            }
            if (start < 0) return bytes
            val lead = bytes[start].toInt() and 0xFF
            val expectedLen = when {
                lead and 0x80 == 0 -> 1
                lead and 0xE0 == 0xC0 -> 2
                lead and 0xF0 == 0xE0 -> 3
                lead and 0xF8 == 0xF0 -> 4
                else -> 1
            }
            val actualLen = bytes.size - start
            return if (actualLen < expectedLen) bytes.copyOf(start) else bytes
        }
    }

    /** The image's bytes, decoded from the `data_url` the gateway answers with (media folders only). */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun image(url: GatewayUrl, path: String): ApiResult<ByteArray> = apiCall {
        client.get(url.resolve("api/media")) { parameter("path", path) }
    }.map { response ->
        val dataUrl = response.body<MediaResponse>().dataUrl
        Base64.decode(dataUrl.substringAfter("base64,"))
    }
}
