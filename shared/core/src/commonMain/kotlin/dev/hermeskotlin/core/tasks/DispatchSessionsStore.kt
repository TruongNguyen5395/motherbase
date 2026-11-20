package dev.hermeskotlin.core.tasks

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.storage.KeyValueStore
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Cached link between a Hermes session and its owning Directus Dispatch task. */
@Serializable
data class DispatchSessionEntry(
    val sessionId: String,
    val taskId: String,
    val taskTitle: String,
    val taskStatus: String = DirectusTask.STATUS_TODO,
    val profile: String? = null,
    val managed: Boolean = false,
)

/** Gateway session archive/unarchive changes triggered by Dispatch task transitions. */
data class SessionLifecycleChange(
    val gateway: GatewayUrl,
    val archivedIds: Set<String> = emptySet(),
    val unarchivedIds: Set<String> = emptySet(),
)

/**
 * Shared in-memory + persistent index mapping Dispatch session IDs to their task titles/statuses
 * per gateway. Keeps the left sidebar's `DISPATCH` group responsive with zero cold-start flicker and
 * prevents duplicate `/board` fetches across [SessionsViewModel] and [TasksViewModel].
 */
class DispatchSessionsStore(
    private val store: KeyValueStore? = null,
    private val nowMs: () -> Long = { getTimeMillis() },
) {
    private val stateMutex = Mutex()
    private val syncMutex = Mutex()
    private val lastSyncedAt = HashMap<GatewayUrl, Long>()
    private val pluginMissing = HashMap<GatewayUrl, Boolean>()

    private val _byGateway = MutableStateFlow<Map<GatewayUrl, Map<String, DispatchSessionEntry>>>(emptyMap())
    val byGateway: StateFlow<Map<GatewayUrl, Map<String, DispatchSessionEntry>>> = _byGateway.asStateFlow()

    private val _coordinatorByGateway = MutableStateFlow<Map<GatewayUrl, DispatchCoordinatorConfig>>(emptyMap())
    val coordinatorByGateway: StateFlow<Map<GatewayUrl, DispatchCoordinatorConfig>> = _coordinatorByGateway.asStateFlow()

    private val _lifecycleChanges = MutableSharedFlow<SessionLifecycleChange>(extraBufferCapacity = 32)
    val lifecycleChanges: SharedFlow<SessionLifecycleChange> = _lifecycleChanges.asSharedFlow()

    fun entriesFor(gateway: GatewayUrl): Flow<Map<String, DispatchSessionEntry>> =
        byGateway.map { it[gateway].orEmpty() }.distinctUntilChanged()

    fun coordinatorFor(gateway: GatewayUrl): Flow<DispatchCoordinatorConfig?> =
        coordinatorByGateway.map { it[gateway] }.distinctUntilChanged()

    /** Hydrates the in-memory index from disk for [gateway] if not loaded yet. */
    suspend fun warm(gateway: GatewayUrl) {
        if (store == null) return
        if (gateway !in _byGateway.value) {
            val cached = store.get(cacheKey(gateway))?.let { raw ->
                runCatching { HermesJson.decodeFromString(LIST_SERIALIZER, raw) }.getOrNull()
            }
            if (cached != null) {
                val indexed = cached.associateBy { it.sessionId }
                _byGateway.update { cur ->
                    if (gateway in cur) cur else cur + (gateway to indexed)
                }
            }
        }
        if (gateway !in _coordinatorByGateway.value) {
            val cachedCoord = store.get(coordinatorCacheKey(gateway))?.let { raw ->
                runCatching { HermesJson.decodeFromString(DispatchCoordinatorConfig.serializer(), raw) }.getOrNull()
            }
            if (cachedCoord != null) {
                _coordinatorByGateway.update { cur ->
                    if (gateway in cur) cur else cur + (gateway to cachedCoord)
                }
            }
        }
    }

    /** Updates the cached [DispatchCoordinatorConfig] for [gateway] and binds its session in the Dispatch index. */
    suspend fun syncCoordinator(gateway: GatewayUrl, coordinator: DispatchCoordinatorConfig) {
        val prevCoord = _coordinatorByGateway.value[gateway]
        _coordinatorByGateway.update { cur -> cur + (gateway to coordinator) }
        if (prevCoord != coordinator && store != null) {
            store.put(coordinatorCacheKey(gateway), HermesJson.encodeToString(DispatchCoordinatorConfig.serializer(), coordinator))
        }
        val nextSid = coordinator.sessionId.trim()
        val prevSid = prevCoord?.sessionId?.trim().orEmpty()
        if (nextSid.isEmpty() && prevSid.isEmpty()) return
        val nextMap = stateMutex.withLock {
            val current = _byGateway.value[gateway].orEmpty()
            val updated = LinkedHashMap<String, DispatchSessionEntry>()
            for ((sid, entry) in current) {
                if (entry.taskId != COORDINATOR_TASK_ID) updated[sid] = entry
            }
            if (nextSid.isNotEmpty()) {
                updated[nextSid] = DispatchSessionEntry(
                    sessionId = nextSid,
                    taskId = COORDINATOR_TASK_ID,
                    taskTitle = COORDINATOR_SESSION_TITLE,
                    taskStatus = DirectusTask.STATUS_IN_PROGRESS,
                    profile = coordinator.profile,
                    managed = true,
                )
            }
            updated.toMap()
        }
        publish(gateway, nextMap)
    }

    /**
     * Synchronizes [gateway]'s cached Dispatch session index with [tasks]. When [includeDone] is false
     * (the default active-board fetch), tasks omitted from [tasks] retain their entries as `done` so
     * non-archived sessions don't fall back into regular Chats if closed externally on Directus.
     */
    suspend fun syncBoard(gateway: GatewayUrl, tasks: List<DirectusTask>, includeDone: Boolean = false) {
        pluginMissing[gateway] = false
        lastSyncedAt[gateway] = nowMs()
        val boardTaskIds = tasks.mapTo(HashSet()) { it.id }
        val nextMap = stateMutex.withLock {
            val previous = _byGateway.value[gateway].orEmpty()
            val merged = LinkedHashMap<String, DispatchSessionEntry>()
            if (!includeDone) {
                for ((sid, entry) in previous) {
                    if (entry.taskId == COORDINATOR_TASK_ID) {
                        merged[sid] = entry
                    } else if (entry.taskId !in boardTaskIds && merged.size < MAX_RETAINED_DONE_ENTRIES) {
                        merged[sid] = entry.copy(taskStatus = DirectusTask.STATUS_DONE)
                    }
                }
            } else {
                for ((sid, entry) in previous) {
                    if (entry.taskId == COORDINATOR_TASK_ID) {
                        merged[sid] = entry
                    }
                }
            }
            upsertTasksInto(merged, tasks)
            merged.toMap()
        }
        publish(gateway, nextMap)
    }

    /** Applies a single updated [task] (e.g., new channel attached or task reopened/completed). */
    suspend fun syncTask(gateway: GatewayUrl, task: DirectusTask) {
        val nextMap = stateMutex.withLock {
            val current = _byGateway.value[gateway].orEmpty()
            val updated = LinkedHashMap<String, DispatchSessionEntry>()
            for ((sid, entry) in current) {
                if (entry.taskId != task.id) updated[sid] = entry
            }
            upsertTasksInto(updated, listOf(task))
            updated.toMap()
        }
        publish(gateway, nextMap)
    }

    /** Removes cached Directus entries for [gateway] (e.g., Directus not configured there). */
    suspend fun clearGateway(gateway: GatewayUrl) {
        stateMutex.withLock {
            lastSyncedAt[gateway] = nowMs()
        }
        publish(gateway, emptyMap())
    }

    /**
     * Fetches `/board?include_done=false` at most once per [SYNC_TTL_MS] unless [force] is true,
     * avoiding duplicate requests across ViewModels and skipping gateways without the plugin.
     */
    suspend fun refreshIfNeeded(
        gateway: GatewayUrl,
        api: DirectusTasksApi,
        force: Boolean = false,
    ) {
        syncMutex.withLock {
            if (!force) {
                if (pluginMissing[gateway] == true) return
                val elapsed = nowMs() - (lastSyncedAt[gateway] ?: 0L)
                if (lastSyncedAt.containsKey(gateway) && elapsed in 0L until SYNC_TTL_MS) return
            }
            when (val res = api.fetchBoard(gateway, includeDone = false)) {
                is ApiResult.Success -> {
                    pluginMissing[gateway] = false
                    res.value.coordinator?.let { coord -> syncCoordinator(gateway, coord) }
                    if (res.value.configured) {
                        syncBoard(gateway, res.value.tasks, includeDone = false)
                    } else {
                        clearGateway(gateway)
                    }
                }
                is ApiResult.Failed -> {
                    if (res.status == 404 || res.status == 501) {
                        pluginMissing[gateway] = true
                    }
                }
                else -> Unit
            }
        }
    }

    fun notifyLifecycleChange(
        gateway: GatewayUrl,
        archivedIds: Set<String> = emptySet(),
        unarchivedIds: Set<String> = emptySet(),
    ) {
        if (archivedIds.isEmpty() && unarchivedIds.isEmpty()) return
        _lifecycleChanges.tryEmit(
            SessionLifecycleChange(
                gateway = gateway,
                archivedIds = archivedIds,
                unarchivedIds = unarchivedIds,
            ),
        )
    }

    private fun upsertTasksInto(
        target: MutableMap<String, DispatchSessionEntry>,
        tasks: List<DirectusTask>,
    ) {
        // Insert done tasks first so active tasks cleanly take ownership if two tasks share a session id.
        val ordered = tasks.sortedBy { if (it.status == DirectusTask.STATUS_DONE) 0 else 1 }
        for (task in ordered) {
            for (ch in task.channels) {
                if (!ch.isSession) continue
                val existing = target[ch.id]
                val existingIsActiveOther = existing != null &&
                    existing.taskId != task.id &&
                    existing.taskStatus != DirectusTask.STATUS_DONE
                if (existingIsActiveOther && task.status == DirectusTask.STATUS_DONE) continue
                target[ch.id] = DispatchSessionEntry(
                    sessionId = ch.id,
                    taskId = task.id,
                    taskTitle = task.title,
                    taskStatus = task.status,
                    profile = ch.profile,
                    managed = ch.isAutoManagedSession,
                )
            }
        }
    }

    private suspend fun publish(gateway: GatewayUrl, map: Map<String, DispatchSessionEntry>) {
        val changed = _byGateway.value[gateway] != map
        _byGateway.update { cur -> cur + (gateway to map) }
        if (changed && store != null) {
            if (map.isEmpty()) store.remove(cacheKey(gateway))
            else store.put(cacheKey(gateway), HermesJson.encodeToString(LIST_SERIALIZER, map.values.toList()))
        }
    }

    private fun cacheKey(gateway: GatewayUrl): String = "dispatch.sessions.v1.${gateway.value}"
    private fun coordinatorCacheKey(gateway: GatewayUrl): String = "dispatch.coordinator.v1.${gateway.value}"

    companion object {
        const val COORDINATOR_TASK_ID = "__dispatch_coordinator__"
        const val COORDINATOR_SESSION_TITLE = "Dispatch · Coordinator"
        private val LIST_SERIALIZER = ListSerializer(DispatchSessionEntry.serializer())
        private const val SYNC_TTL_MS = 60_000L
        private const val MAX_RETAINED_DONE_ENTRIES = 200
    }
}
