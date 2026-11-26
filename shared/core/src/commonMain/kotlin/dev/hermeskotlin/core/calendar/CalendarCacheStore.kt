package dev.hermeskotlin.core.calendar

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.storage.KeyValueStore
import kotlinx.serialization.json.Json

/** Per-gateway offline snapshot store for instant Calendar cold-start rendering. */
class CalendarCacheStore(
    private val store: KeyValueStore,
) {
    suspend fun load(gateway: GatewayUrl): CalendarOfflineSnapshot? {
        val raw = store.get(cacheKey(gateway)) ?: return null
        return runCatching { lenientJson.decodeFromString(CalendarOfflineSnapshot.serializer(), raw) }.getOrNull()
    }

    suspend fun save(gateway: GatewayUrl, snapshot: CalendarOfflineSnapshot) {
        val encoded = runCatching {
            lenientJson.encodeToString(CalendarOfflineSnapshot.serializer(), snapshot)
        }.getOrNull() ?: return
        store.put(cacheKey(gateway), encoded)
    }

    private fun cacheKey(gateway: GatewayUrl): String = "calendar.snapshot.v1.${gateway.value}"

    companion object {
        private val lenientJson = Json(HermesJson) { isLenient = true }
    }
}
