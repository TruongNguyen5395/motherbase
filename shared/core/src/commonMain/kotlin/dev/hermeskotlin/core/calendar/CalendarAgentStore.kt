package dev.hermeskotlin.core.calendar

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.storage.KeyValueStore
import dev.hermeskotlin.core.tasks.SessionLifecycleChange
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

/** Navigation request to open Calendar (optionally focusing the Agent panel). */
data class CalendarJumpTarget(
    val gateway: GatewayUrl,
    val openAgent: Boolean = true,
    val requestSeq: Long = 0L,
)

/**
 * Shared in-memory + persistent index tracking each gateway's dedicated Calendar Agent session
 * ([CalendarAgentConfig]) independently from Dispatch Coordinator.
 */
class CalendarAgentStore(
    private val store: KeyValueStore? = null,
    private val nowMs: () -> Long = { getTimeMillis() },
) {
    private val syncMutex = Mutex()
    private val lastSyncedAt = HashMap<GatewayUrl, Long>()
    private val pluginMissing = HashMap<GatewayUrl, Boolean>()

    private val _agentByGateway = MutableStateFlow<Map<GatewayUrl, CalendarAgentConfig>>(emptyMap())
    val agentByGateway: StateFlow<Map<GatewayUrl, CalendarAgentConfig>> = _agentByGateway.asStateFlow()

    private val _pendingJump = MutableStateFlow<CalendarJumpTarget?>(null)
    val pendingJump: StateFlow<CalendarJumpTarget?> = _pendingJump.asStateFlow()

    private val _lifecycleChanges = MutableSharedFlow<SessionLifecycleChange>(extraBufferCapacity = 32)
    val lifecycleChanges: SharedFlow<SessionLifecycleChange> = _lifecycleChanges.asSharedFlow()

    fun agentFor(gateway: GatewayUrl): Flow<CalendarAgentConfig?> =
        agentByGateway.map { it[gateway] }.distinctUntilChanged()

    /** Requests navigation back to Calendar (focusing the Agent tab by default). */
    fun requestOpenCalendar(gateway: GatewayUrl, openAgent: Boolean = true) {
        _pendingJump.update { cur ->
            CalendarJumpTarget(
                gateway = gateway,
                openAgent = openAgent,
                requestSeq = (cur?.requestSeq ?: 0L) + 1L,
            )
        }
    }

    /** Clears [expected] once handled by the sidebar without dropping a newer jump request. */
    fun consumeJump(expected: CalendarJumpTarget) {
        _pendingJump.compareAndSet(expected, null)
    }

    /** Hydrates the cached [CalendarAgentConfig] from disk for [gateway] if not loaded yet. */
    suspend fun warm(gateway: GatewayUrl) {
        if (store == null || gateway in _agentByGateway.value) return
        val cached = store.get(cacheKey(gateway))?.let { raw ->
            runCatching { HermesJson.decodeFromString(CalendarAgentConfig.serializer(), raw) }.getOrNull()
        }
        if (cached != null) {
            _agentByGateway.update { cur ->
                if (gateway in cur) cur else cur + (gateway to cached)
            }
        }
    }

    /** Updates the cached [CalendarAgentConfig] for [gateway] on disk and in memory. */
    suspend fun syncAgent(gateway: GatewayUrl, agent: CalendarAgentConfig) {
        pluginMissing[gateway] = false
        lastSyncedAt[gateway] = nowMs()
        val prev = _agentByGateway.value[gateway]
        _agentByGateway.update { cur -> cur + (gateway to agent) }
        if (prev != agent && store != null) {
            store.put(cacheKey(gateway), HermesJson.encodeToString(CalendarAgentConfig.serializer(), agent))
        }
    }

    /** Clears cached Calendar Agent info for [gateway]. */
    suspend fun clearGateway(gateway: GatewayUrl) {
        lastSyncedAt[gateway] = nowMs()
        _agentByGateway.update { cur -> cur - gateway }
        store?.remove(cacheKey(gateway))
    }

    /**
     * Fetches `/agent` at most once per [SYNC_TTL_MS] unless [force] is true,
     * skipping gateways without the `motherbase-calendar` plugin.
     */
    suspend fun refreshIfNeeded(
        gateway: GatewayUrl,
        api: CalendarApi,
        force: Boolean = false,
    ) {
        syncMutex.withLock {
            if (!force) {
                if (pluginMissing[gateway] == true) return
                val elapsed = nowMs() - (lastSyncedAt[gateway] ?: 0L)
                if (lastSyncedAt.containsKey(gateway) && elapsed in 0L until SYNC_TTL_MS) return
            }
            when (val res = api.getAgentConfig(gateway)) {
                is ApiResult.Success -> syncAgent(gateway, res.value)
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

    private fun cacheKey(gateway: GatewayUrl): String = "calendar.agent.v1.${gateway.value}"

    companion object {
        const val AGENT_SESSION_TITLE = "Calendar · Agent"
        private const val SYNC_TTL_MS = 60_000L
    }
}
