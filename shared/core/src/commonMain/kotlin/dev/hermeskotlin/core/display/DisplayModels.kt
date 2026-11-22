package dev.hermeskotlin.core.display

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Who currently holds control over a profile's Bot Screen. */
enum class LeaseHolder {
    Agent,
    Human;

    companion object {
        fun fromWire(value: String?): LeaseHolder =
            if (value.equals("human", ignoreCase = true)) Human else Agent
    }
}

/** Control lease mirrored from `<HERMES_HOME>/bot-desktop/lease.json` (`viewer_id` is redacted on wire). */
data class DisplayLease(
    val holder: LeaseHolder = LeaseHolder.Agent,
    val viewerHash: String? = null,
    val since: Double = 0.0,
    val epoch: Int = 0,
    val reason: String = "",
) {
    fun isHeldBy(myViewerHash: String?): Boolean =
        holder == LeaseHolder.Human && !myViewerHash.isNullOrEmpty() && viewerHash == myViewerHash

    fun isHeldByOther(myViewerHash: String?): Boolean =
        holder == LeaseHolder.Human && !isHeldBy(myViewerHash)

    companion object {
        fun fromJson(element: JsonElement?): DisplayLease {
            val obj = element as? JsonObject ?: return DisplayLease()
            return DisplayLease(
                holder = LeaseHolder.fromWire(obj.str("holder")),
                viewerHash = obj.str("viewer_hash"),
                since = obj.dbl("since") ?: 0.0,
                epoch = obj.int("epoch") ?: 0,
                reason = obj.str("reason").orEmpty(),
            )
        }
    }
}

/** Container image upgrade prompt metadata returned when an image lacks desktop binaries. */
data class DisplayImageSwitch(
    val currentImage: String = "",
    val desktopImage: String = "",
    val rebuildCommand: String? = null,
) {
    companion object {
        fun fromJson(element: JsonElement?): DisplayImageSwitch? {
            val obj = element as? JsonObject ?: return null
            val current = obj.str("current_image").orEmpty()
            val desktop = obj.str("desktop_image").orEmpty()
            val cmd = obj.str("rebuild_command")
            if (current.isEmpty() && desktop.isEmpty() && cmd.isNullOrEmpty()) return null
            return DisplayImageSwitch(
                currentImage = current,
                desktopImage = desktop,
                rebuildCommand = cmd,
            )
        }
    }
}

/** Status snapshot returned by `display.status`, `display.start`, `display.stop`, `display.observe`, and `display.status` events. */
data class DisplayStatus(
    val profile: String = "",
    val profileKey: String = "",
    val supported: Boolean = false,
    val installed: Boolean = false,
    val missing: List<String> = emptyList(),
    val running: Boolean = false,
    val pid: Int? = null,
    val display: String? = null,
    val socket: String? = null,
    val geometry: String = "",
    val installCommand: String? = null,
    val browser: String? = null,
    val blocker: String? = null,
    val memoryAvailableMb: Int? = null,
    val memoryLimitMb: Int? = null,
    val placement: String = "gateway",
    val imageSwitch: DisplayImageSwitch? = null,
    val lease: DisplayLease = DisplayLease(),
) {
    val needsInstall: Boolean get() = supported && !installed && missing.isNotEmpty()

    companion object {
        fun fromJson(element: JsonElement?): DisplayStatus {
            val obj = element as? JsonObject ?: return DisplayStatus()
            val missingList = (obj["missing"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            return DisplayStatus(
                profile = obj.str("profile").orEmpty(),
                profileKey = obj.str("profile_key").orEmpty(),
                supported = obj.flag("supported") ?: false,
                installed = obj.flag("installed") ?: false,
                missing = missingList,
                running = obj.flag("running") ?: false,
                pid = obj.int("pid"),
                display = obj.str("display"),
                socket = obj.str("socket"),
                geometry = obj.str("geometry").orEmpty(),
                installCommand = obj.str("install_command"),
                browser = obj.str("browser"),
                blocker = obj.str("blocker"),
                memoryAvailableMb = obj.int("memory_available_mb"),
                memoryLimitMb = obj.int("memory_limit_mb"),
                placement = obj.str("placement") ?: "gateway",
                imageSwitch = DisplayImageSwitch.fromJson(obj["image_switch"]),
                lease = DisplayLease.fromJson(obj["lease"]),
            )
        }
    }
}

data class DisplayObserveResult(
    val status: DisplayStatus,
    val ticket: String,
    val path: String,
    val viewerId: String,
) {
    companion object {
        fun fromJson(element: JsonElement?): DisplayObserveResult {
            val obj = element as? JsonObject ?: JsonObject(emptyMap())
            return DisplayObserveResult(
                status = DisplayStatus.fromJson(obj),
                ticket = obj.str("ticket").orEmpty(),
                path = obj.str("path") ?: "/api/display/ws",
                viewerId = obj.str("viewer_id").orEmpty(),
            )
        }
    }
}

data class DisplayStopResult(
    val status: DisplayStatus,
    val stopped: Boolean,
)

data class DisplayThumbnailResult(
    val dataUrl: String?,
    val suppressed: String?,
) {
    val humanHasControl: Boolean get() = suppressed == "human_has_control"
}

data class DisplayInstallStartResult(
    val started: Boolean,
    val command: String?,
    val profileKey: String,
)

enum class ScreenQuality(val qualityLevel: Int, val compressionLevel: Int) {
    Sharp(qualityLevel = 7, compressionLevel = 2),
    Balanced(qualityLevel = 5, compressionLevel = 4),
    LowBandwidth(qualityLevel = 3, compressionLevel = 6),
}

object DisplayCloseCodes {
    const val CLEAN = 1000
    const val GOING_AWAY = 1001
    const val DROPPED = 1006
    const val EVICTED = 4000
    const val DISPLAY_STOPPED = 4001
    const val BACKGROUNDED = 4002
    const val UNSUPPORTED_DATA = 1003
    const val UNAUTHORIZED = 4401
    const val FORBIDDEN = 4403
}

object DisplayRpcErrors {
    const val METHOD_NOT_FOUND = -32601
    const val LEASE_HELD_BY_HUMAN = 5300
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

private fun JsonObject.flag(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.dbl(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull
