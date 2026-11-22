package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.rpc.JsonRpcClient
import dev.hermeskotlin.core.rpc.RpcException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class ProfileScreenState(
    val profile: String = DEFAULT_PROFILE,
    val profileKey: String? = null,
    val status: DisplayStatus? = null,
    val loading: Boolean = false,
    val actionBusy: Boolean = false,
    val unavailable: Boolean = false,
    val error: String? = null,
    val viewerId: String? = null,
    val viewerHash: String? = null,
    val liveTicket: String? = null,
    val livePath: String? = null,
    val desktopName: String = "",
    val installing: Boolean = false,
    val installCommand: String? = null,
    val installLogs: List<String> = emptyList(),
    val installExitCode: Int? = null,
    val installSudoRequestId: String? = null,
) {
    val iHoldLease: Boolean get() = status?.lease?.isHeldBy(viewerHash) == true
    val otherHoldsLease: Boolean get() = status?.lease?.isHeldByOther(viewerHash) == true

    companion object {
        const val DEFAULT_PROFILE = "default"
    }
}

data class ScreenAutoRaiseEvent(
    val sessionId: String,
    val toolName: String,
)

/**
 * Shared Bot Screen state across chats, bot cards, and viewers.
 * Guards against out-of-order async status replies (`statusGeneration`), stale `lease.epoch`
 * transitions, and gateway reloads while preserving the local `viewerId` across brief disconnects.
 */
class DisplayStore(
    private val api: DisplayApi,
    private val connection: GatewayConnection,
    private val scope: CoroutineScope,
) {
    private val _profiles = MutableStateFlow<Map<String, ProfileScreenState>>(emptyMap())
    val profiles: StateFlow<Map<String, ProfileScreenState>> = _profiles.asStateFlow()

    private val _autoRaise = MutableSharedFlow<ScreenAutoRaiseEvent>(extraBufferCapacity = 16)
    val autoRaise: SharedFlow<ScreenAutoRaiseEvent> = _autoRaise.asSharedFlow()

    private val profileKeyIndex = mutableMapOf<String, String>()
    private val generations = mutableMapOf<String, Long>()
    private val connectedClients = MutableStateFlow<JsonRpcClient?>(null)

    init {
        scope.launch {
            connection.state.collect { state ->
                val client = (state as? ConnectionState.Connected)?.client
                if (client !== connectedClients.value) {
                    if (client == null) {
                        // Drop ephemeral tickets/install prompts on disconnect while keeping viewerId for OTP / reconnect continuity
                        _profiles.update { current ->
                            current.mapValues { (_, p) ->
                                p.copy(
                                    loading = false,
                                    actionBusy = false,
                                    liveTicket = null,
                                    installSudoRequestId = null,
                                )
                            }
                        }
                    } else {
                        // Fresh gateway session: reset unavailable cache so updated gateways expose display.*
                        _profiles.update { current ->
                            current.mapValues { (_, p) -> p.copy(unavailable = false, error = null) }
                        }
                    }
                    connectedClients.value = client
                }
            }
        }

        scope.launch {
            connectedClients.collectLatest { client ->
                if (client == null) return@collectLatest
                launch {
                    client.events.collect { ev ->
                        handleEvent(ev.type, ev.payload as? JsonObject ?: JsonObject(emptyMap()), ev.sessionId)
                    }
                }
                launch {
                    client.serverRequests.collect { req ->
                        if (req.method == "display.install.sudo") {
                            val profileKey = (req.params?.get("profile_key") as? JsonPrimitive)?.contentOrNull.orEmpty()
                            val targetId = resolveProfileForKey(profileKey)
                                ?: _profiles.value.entries.firstOrNull { it.value.installing }?.key
                                ?: ProfileScreenState.DEFAULT_PROFILE
                            updateProfile(targetId) { it.copy(installSudoRequestId = req.id) }
                        }
                    }
                }
            }
        }
    }

    fun normalizeProfile(profile: String?): String =
        profile?.trim()?.takeIf { it.isNotEmpty() } ?: ProfileScreenState.DEFAULT_PROFILE

    fun stateOf(profile: String?): ProfileScreenState =
        _profiles.value[normalizeProfile(profile)] ?: ProfileScreenState(profile = normalizeProfile(profile))

    suspend fun refreshStatus(profile: String?, quiet: Boolean = false): Result<DisplayStatus> {
        val pid = normalizeProfile(profile)
        val gen = nextGeneration(pid)
        if (!quiet) {
            updateProfile(pid) { it.copy(loading = true, error = null) }
        }
        return try {
            val status = api.status(pid)
            if (isCurrentGeneration(pid, gen)) {
                applyStatus(pid, status, clearBusy = true)
            }
            Result.success(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            if (isCurrentGeneration(pid, gen)) {
                handleRpcFailure(pid, e, clearLoading = true)
            }
            Result.failure(e)
        } catch (e: Exception) {
            if (isCurrentGeneration(pid, gen)) {
                updateProfile(pid) { it.copy(loading = false, error = e.message ?: "Failed to read screen status.") }
            }
            Result.failure(e)
        }
    }

    suspend fun startScreen(profile: String?): Result<DisplayStatus> {
        val pid = normalizeProfile(profile)
        val gen = nextGeneration(pid)
        updateProfile(pid) { it.copy(actionBusy = true, error = null) }
        return try {
            val status = api.start(pid)
            applyStatus(pid, status, clearBusy = true)
            Result.success(status)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            handleRpcFailure(pid, e, clearBusy = true)
            Result.failure(e)
        } catch (e: Exception) {
            if (isCurrentGeneration(pid, gen)) {
                updateProfile(pid) { it.copy(actionBusy = false, error = e.message ?: "Could not start Bot Screen.") }
            }
            Result.failure(e)
        }
    }

    suspend fun stopScreen(profile: String?, force: Boolean = false): Result<DisplayStopResult> {
        val pid = normalizeProfile(profile)
        nextGeneration(pid)
        updateProfile(pid) { it.copy(actionBusy = true, error = null) }
        return try {
            val res = api.stop(pid, force = force)
            applyStatus(pid, res.status, clearBusy = true)
            if (res.stopped) {
                updateProfile(pid) { it.copy(liveTicket = null, livePath = null, desktopName = "") }
            }
            Result.success(res)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            handleRpcFailure(pid, e, clearBusy = true)
            Result.failure(e)
        } catch (e: Exception) {
            updateProfile(pid) { it.copy(actionBusy = false, error = e.message ?: "Could not stop Bot Screen.") }
            Result.failure(e)
        }
    }

    suspend fun observeScreen(profile: String?): Result<DisplayObserveResult> {
        val pid = normalizeProfile(profile)
        val priorViewerId = stateOf(pid).viewerId
        nextGeneration(pid)
        updateProfile(pid) { it.copy(loading = true, error = null) }
        return try {
            val res = api.observe(pid, viewerId = priorViewerId)
            val hash = DisplayApi.viewerHashOf(res.viewerId)
            applyStatus(pid, res.status, clearBusy = true)
            updateProfile(pid) {
                it.copy(
                    loading = false,
                    unavailable = false,
                    error = null,
                    viewerId = res.viewerId.ifEmpty { it.viewerId },
                    viewerHash = hash.ifEmpty { it.viewerHash },
                    liveTicket = res.ticket,
                    livePath = res.path,
                )
            }
            Result.success(res)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            handleRpcFailure(pid, e, clearLoading = true)
            Result.failure(e)
        } catch (e: Exception) {
            updateProfile(pid) { it.copy(loading = false, error = e.message ?: "Could not open Bot Screen stream.") }
            Result.failure(e)
        }
    }

    suspend fun acquireLease(profile: String?, reason: String = "human_takeover"): Result<DisplayLease> {
        val pid = normalizeProfile(profile)
        val viewerId = stateOf(pid).viewerId
            ?: return Result.failure(IllegalStateException("Open live screen view first to acquire a viewer ID."))
        updateProfile(pid) { it.copy(actionBusy = true, error = null) }
        return try {
            val lease = api.acquireLease(pid, viewerId, reason)
            applyLease(pid, lease)
            updateProfile(pid) { it.copy(actionBusy = false) }
            Result.success(lease)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateProfile(pid) { it.copy(actionBusy = false, error = e.message ?: "Could not take control.") }
            Result.failure(e)
        }
    }

    suspend fun releaseLease(profile: String?, force: Boolean = false): Result<DisplayLease> {
        val pid = normalizeProfile(profile)
        val viewerId = stateOf(pid).viewerId
        updateProfile(pid) { it.copy(actionBusy = true, error = null) }
        return try {
            val lease = api.releaseLease(pid, viewerId = viewerId, force = force)
            applyLease(pid, lease)
            updateProfile(pid) { it.copy(actionBusy = false) }
            Result.success(lease)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateProfile(pid) { it.copy(actionBusy = false, error = e.message ?: "Could not hand control back.") }
            Result.failure(e)
        }
    }

    suspend fun startInstall(profile: String?): Result<DisplayInstallStartResult> {
        val pid = normalizeProfile(profile)
        updateProfile(pid) {
            it.copy(
                installing = true,
                installLogs = emptyList(),
                installExitCode = null,
                installSudoRequestId = null,
                error = null,
            )
        }
        return try {
            val res = api.install(pid)
            if (res.profileKey.isNotEmpty()) {
                profileKeyIndex[res.profileKey] = pid
            }
            updateProfile(pid) {
                it.copy(
                    profileKey = res.profileKey.ifEmpty { it.profileKey },
                    installing = res.started,
                    installCommand = res.command ?: it.installCommand,
                )
            }
            Result.success(res)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateProfile(pid) { it.copy(installing = false, error = e.message ?: "Install failed.") }
            Result.failure(e)
        }
    }

    suspend fun respondInstallSudo(profile: String?, password: String?) {
        val pid = normalizeProfile(profile)
        val reqId = stateOf(pid).installSudoRequestId ?: return
        val client = connectedClients.value ?: return
        updateProfile(pid) { it.copy(installSudoRequestId = null) }
        client.respond(
            reqId,
            buildJsonObject { put("value", password.orEmpty()) },
        )
    }

    suspend fun switchSandboxImage(profile: String?, approve: Boolean): Result<Pair<DisplayStatus, String?>> {
        val pid = normalizeProfile(profile)
        nextGeneration(pid)
        updateProfile(pid) { it.copy(actionBusy = true, error = null) }
        return try {
            val res = api.switchSandboxImage(pid, approve)
            applyStatus(pid, res.first, clearBusy = true)
            Result.success(res)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateProfile(pid) { it.copy(actionBusy = false, error = e.message ?: "Could not switch sandbox image.") }
            Result.failure(e)
        }
    }

    suspend fun thumbnail(profile: String?): Result<DisplayThumbnailResult> =
        try {
            Result.success(api.thumbnail(normalizeProfile(profile)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RpcException) {
            if (e.code == DisplayRpcErrors.METHOD_NOT_FOUND) {
                updateProfile(normalizeProfile(profile)) { it.copy(unavailable = true) }
            }
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun noteDesktopName(profile: String?, name: String) {
        val pid = normalizeProfile(profile)
        updateProfile(pid) { it.copy(desktopName = name) }
    }

    fun clearLiveTicket(profile: String?) {
        val pid = normalizeProfile(profile)
        updateProfile(pid) { it.copy(liveTicket = null, livePath = null) }
    }

    fun clearError(profile: String?) {
        val pid = normalizeProfile(profile)
        updateProfile(pid) { it.copy(error = null) }
    }

    private fun handleEvent(type: String, payload: JsonObject, sessionId: String?) {
        when (type) {
            "display.status" -> {
                val status = DisplayStatus.fromJson(payload)
                val pid = resolveProfileForStatus(status) ?: return
                nextGeneration(pid)
                applyStatus(pid, status, clearBusy = false)
            }
            "display.lease" -> {
                val profileKey = (payload["profile_key"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val pid = resolveProfileForKey(profileKey) ?: return
                val lease = DisplayLease.fromJson(payload["lease"])
                applyLease(pid, lease)
            }
            "display.install.log" -> {
                val profileKey = (payload["profile_key"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val line = (payload["line"] as? JsonPrimitive)?.contentOrNull ?: return
                val pid = resolveProfileForKey(profileKey)
                    ?: _profiles.value.entries.firstOrNull { it.value.installing }?.key
                    ?: return
                updateProfile(pid) { prev ->
                    val nextLogs = (prev.installLogs + line).takeLast(MAX_INSTALL_LOG_LINES)
                    prev.copy(installing = true, installLogs = nextLogs)
                }
            }
            "display.install.done" -> {
                val profileKey = (payload["profile_key"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val code = (payload["code"] as? JsonPrimitive)?.intOrNull
                val pid = resolveProfileForKey(profileKey)
                    ?: _profiles.value.entries.firstOrNull { it.value.installing }?.key
                    ?: return
                val statusObj = payload["status"] as? JsonObject
                if (statusObj != null) {
                    applyStatus(pid, DisplayStatus.fromJson(statusObj), clearBusy = true)
                }
                updateProfile(pid) {
                    it.copy(
                        installing = false,
                        installExitCode = code,
                        installSudoRequestId = null,
                    )
                }
            }
            "tool.start" -> {
                val toolName = (payload["tool"] as? JsonPrimitive)?.contentOrNull
                    ?: (payload["name"] as? JsonPrimitive)?.contentOrNull
                    ?: return
                if (toolName.startsWith("computer_use") || toolName.startsWith("browser_")) {
                    val sid = sessionId
                        ?: (payload["session_id"] as? JsonPrimitive)?.contentOrNull
                        ?: ""
                    if (sid.isNotEmpty()) {
                        _autoRaise.tryEmit(ScreenAutoRaiseEvent(sessionId = sid, toolName = toolName))
                    }
                }
            }
        }
    }

    private fun resolveProfileForStatus(status: DisplayStatus): String? {
        if (status.profile.isNotBlank()) return normalizeProfile(status.profile)
        return resolveProfileForKey(status.profileKey)
    }

    private fun resolveProfileForKey(profileKey: String): String? {
        if (profileKey.isBlank()) return null
        profileKeyIndex[profileKey]?.let { return it }
        return _profiles.value.entries.firstOrNull { it.value.profileKey == profileKey }?.key
    }

    private fun applyStatus(pid: String, incoming: DisplayStatus, clearBusy: Boolean) {
        if (incoming.profileKey.isNotEmpty()) {
            profileKeyIndex[incoming.profileKey] = pid
        }
        updateProfile(pid) { prev ->
            val currentStatus = prev.status
            val mergedLease = if (
                currentStatus == null ||
                prev.profileKey != incoming.profileKey ||
                incoming.lease.epoch >= currentStatus.lease.epoch
            ) {
                incoming.lease
            } else {
                currentStatus.lease
            }
            val nextStatus = incoming.copy(lease = mergedLease)
            prev.copy(
                profileKey = incoming.profileKey.ifEmpty { prev.profileKey },
                status = nextStatus,
                loading = false,
                actionBusy = if (clearBusy) false else prev.actionBusy,
                unavailable = false,
                liveTicket = if (nextStatus.running) prev.liveTicket else null,
            )
        }
    }

    private fun applyLease(pid: String, lease: DisplayLease) {
        updateProfile(pid) { prev ->
            val current = prev.status ?: return@updateProfile prev
            if (lease.epoch < current.lease.epoch) return@updateProfile prev
            prev.copy(status = current.copy(lease = lease))
        }
    }

    private fun handleRpcFailure(pid: String, e: RpcException, clearLoading: Boolean = false, clearBusy: Boolean = false) {
        val unavailable = e.code == DisplayRpcErrors.METHOD_NOT_FOUND
        updateProfile(pid) { prev ->
            prev.copy(
                loading = if (clearLoading) false else prev.loading,
                actionBusy = if (clearBusy) false else prev.actionBusy,
                unavailable = prev.unavailable || unavailable,
                error = if (unavailable) null else (e.message ?: "Bot Screen error (${e.code})."),
            )
        }
    }

    private fun updateProfile(pid: String, transform: (ProfileScreenState) -> ProfileScreenState) {
        _profiles.update { map ->
            val current = map[pid] ?: ProfileScreenState(profile = pid)
            map + (pid to transform(current))
        }
    }

    private fun nextGeneration(pid: String): Long {
        val next = (generations[pid] ?: 0L) + 1L
        generations[pid] = next
        return next
    }

    private fun isCurrentGeneration(pid: String, gen: Long): Boolean = generations[pid] == gen

    companion object {
        private const val MAX_INSTALL_LOG_LINES = 120
    }
}
