package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.auth.sha256
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.rpc.JsonRpcClient
import dev.hermeskotlin.core.rpc.RpcException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** JSON-RPC client for Hermes's `display.*` Bot Screen methods (`tui_gateway/methods_display.py`). */
class DisplayApi(private val connection: GatewayConnection) {

    suspend fun status(profile: String?): DisplayStatus =
        DisplayStatus.fromJson(client().request("display.status", scope(profile)))

    suspend fun thumbnail(profile: String?): DisplayThumbnailResult {
        val obj = client().request("display.thumbnail", scope(profile), timeoutMs = 15_000) as? JsonObject
        val dataUrl = (obj?.get("data_url") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        val suppressed = (obj?.get("suppressed") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        return DisplayThumbnailResult(dataUrl = dataUrl, suppressed = suppressed)
    }

    suspend fun start(profile: String?): DisplayStatus =
        DisplayStatus.fromJson(client().request("display.start", scope(profile), timeoutMs = 60_000))

    suspend fun stop(profile: String?, force: Boolean = false): DisplayStopResult {
        val reply = client().request(
            "display.stop",
            buildJsonObject {
                profile?.takeIf { it.isNotBlank() }?.let { put("profile", it) }
                if (force) put("force", true)
            },
            timeoutMs = 30_000,
        ) as? JsonObject
        val stopped = (reply?.get("stopped") as? JsonPrimitive)?.booleanOrNull ?: false
        return DisplayStopResult(status = DisplayStatus.fromJson(reply), stopped = stopped)
    }

    suspend fun observe(profile: String?, viewerId: String? = null): DisplayObserveResult {
        val reply = client().request(
            "display.observe",
            buildJsonObject {
                profile?.takeIf { it.isNotBlank() }?.let { put("profile", it) }
                viewerId?.takeIf { it.isNotBlank() }?.let { put("viewer_id", it) }
            },
            timeoutMs = 30_000,
        )
        return DisplayObserveResult.fromJson(reply)
    }

    suspend fun acquireLease(profile: String?, viewerId: String, reason: String = "human_takeover"): DisplayLease {
        val reply = client().request(
            "display.lease.acquire",
            buildJsonObject {
                profile?.takeIf { it.isNotBlank() }?.let { put("profile", it) }
                put("viewer_id", viewerId)
                if (reason.isNotBlank()) put("reason", reason)
            },
        ) as? JsonObject
        return DisplayLease.fromJson(reply?.get("lease"))
    }

    suspend fun releaseLease(profile: String?, viewerId: String? = null, force: Boolean = false): DisplayLease {
        val reply = client().request(
            "display.lease.release",
            buildJsonObject {
                profile?.takeIf { it.isNotBlank() }?.let { put("profile", it) }
                viewerId?.takeIf { it.isNotBlank() }?.let { put("viewer_id", it) }
                if (force) put("force", true)
            },
        ) as? JsonObject
        return DisplayLease.fromJson(reply?.get("lease"))
    }

    suspend fun install(profile: String?): DisplayInstallStartResult {
        val reply = client().request("display.install", scope(profile), timeoutMs = 30_000) as? JsonObject
        return DisplayInstallStartResult(
            started = (reply?.get("started") as? JsonPrimitive)?.booleanOrNull ?: false,
            command = (reply?.get("command") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() },
            profileKey = (reply?.get("profile_key") as? JsonPrimitive)?.contentOrNull.orEmpty(),
        )
    }

    suspend fun switchSandboxImage(profile: String?, approve: Boolean): Pair<DisplayStatus, String?> {
        val reply = client().request(
            "display.switchSandboxImage",
            buildJsonObject {
                profile?.takeIf { it.isNotBlank() }?.let { put("profile", it) }
                put("approve", approve)
            },
            timeoutMs = 60_000,
        ) as? JsonObject
        val dockerImage = (reply?.get("docker_image") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        return DisplayStatus.fromJson(reply) to dockerImage
    }

    private fun scope(profile: String?): JsonObject = buildJsonObject {
        profile?.takeIf { it.isNotBlank() }?.let { put("profile", it) }
    }

    private fun client(): JsonRpcClient = (connection.state.value as? ConnectionState.Connected)?.client
        ?: throw RpcException(0, "Not connected to the gateway.")

    companion object {
        /** Matches server `hashlib.sha256(viewer_id.encode()).hexdigest()[:12]`. */
        fun viewerHashOf(viewerId: String): String {
            if (viewerId.isEmpty()) return ""
            val digest = sha256(viewerId.encodeToByteArray())
            val sb = StringBuilder(12)
            for (i in 0 until minOf(6, digest.size)) {
                val b = digest[i].toInt() and 0xFF
                sb.append("0123456789abcdef"[b ushr 4])
                sb.append("0123456789abcdef"[b and 0x0F])
            }
            return sb.toString()
        }
    }
}
