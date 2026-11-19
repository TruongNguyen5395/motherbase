package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.rpc.JsonRpcClient
import dev.hermeskotlin.core.rpc.RpcException
import dev.hermeskotlin.core.sessions.SessionsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Fallback prompt text sent with an image-only message (matches Desktop and [ChatSession]). */
const val IMAGE_ONLY_FALLBACK_PROMPT = "What's in this image?"

/** Gateway-side reference produced when attaching a file or image to a live runtime session. */
data class UploadedSessionAttachment(val refText: String?, val gatewayPath: String?)

/**
 * Uploads one [OutgoingAttachment] to a live runtime session ([runtimeId]), matching [ChatSession]'s
 * protocol: small images go via `image.attach_bytes`, small files/PDFs via `file.attach` (`data_url`),
 * and oversized files stage over HTTP via [SessionsApi.uploads] before `file.attach` by path.
 */
suspend fun SessionsApi.attachToSession(
    gateway: GatewayUrl,
    client: JsonRpcClient,
    runtimeId: String,
    attachment: OutgoingAttachment,
    queuedImages: MutableList<String>,
): UploadedSessionAttachment {
    if (attachment.size > OutgoingAttachment.MAX_BYTES) {
        throw RpcException(
            JsonRpcClient.MESSAGE_TOO_LARGE,
            "${attachment.name} is larger than ${OutgoingAttachment.MAX_SIZE}, too large to send.",
        )
    }
    val failure = "Couldn't attach ${attachment.name}"
    if (!attachment.inline) {
        val staged = uploads.stage(gateway, attachment)
        var usedInPlace = false
        try {
            val result = client.request(
                "file.attach",
                buildJsonObject {
                    put("session_id", runtimeId)
                    put("path", staged.path)
                    put("name", attachment.name)
                },
                timeoutMs = UPLOAD_RPC_TIMEOUT_MS,
            ) as? JsonObject
            usedInPlace = result.bool("uploaded")?.not() ?: (result.str("path") == staged.path)
            val refText = result.str("ref_text")?.takeIf { result.bool("attached") == true }
                ?: throw RpcException(0, failure)
            return UploadedSessionAttachment(refText, result.str("path") ?: result.str("ref_path") ?: staged.path)
        } finally {
            if (!usedInPlace) {
                withContext(NonCancellable) {
                    runCatching { uploads.discard(gateway, staged) }
                }
            }
        }
    }
    return when (attachment.kind) {
        AttachmentKind.Image -> {
            val result = client.request(
                "image.attach_bytes",
                buildJsonObject {
                    put("session_id", runtimeId)
                    put("content_base64", attachment.base64())
                    put("filename", attachment.name)
                },
                timeoutMs = UPLOAD_RPC_TIMEOUT_MS,
            ) as? JsonObject
            if (result.bool("attached") != true) {
                throw RpcException(0, result.str("message") ?: failure)
            }
            val path = result.str("path")?.also(queuedImages::add)
            UploadedSessionAttachment(null, path)
        }
        AttachmentKind.Pdf, AttachmentKind.File -> {
            val result = client.request(
                "file.attach",
                buildJsonObject {
                    put("session_id", runtimeId)
                    put("name", attachment.name)
                    put("data_url", "data:${attachment.mimeType};base64,${attachment.base64()}")
                },
                timeoutMs = UPLOAD_RPC_TIMEOUT_MS,
            ) as? JsonObject
            val refText = result.str("ref_text")?.takeIf { result.bool("attached") == true }
                ?: throw RpcException(0, failure)
            UploadedSessionAttachment(refText, result.str("path") ?: result.str("ref_path"))
        }
    }
}

/** Best effort: removes images queued on [runtimeId] if a send fails before `prompt.submit` succeeds. */
suspend fun detachSessionImages(client: JsonRpcClient?, runtimeId: String?, paths: List<String>) {
    if (client == null || runtimeId == null) return
    for (path in paths) {
        try {
            client.request(
                "image.detach",
                buildJsonObject {
                    put("session_id", runtimeId)
                    put("path", path)
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
    }
}

private const val UPLOAD_RPC_TIMEOUT_MS = 60_000L

private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
