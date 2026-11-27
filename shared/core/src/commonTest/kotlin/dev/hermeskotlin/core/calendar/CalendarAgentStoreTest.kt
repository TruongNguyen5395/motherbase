package dev.hermeskotlin.core.calendar

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarAgentStoreTest {

    private val gateway = GatewayUrl.parse("https://hermes.example.ts.net")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun syncAgentPersistsAndWarmsAcrossStoreInstances() = runTest {
        val kv = InMemoryKeyValueStore()
        var now = 1_000L
        val writer = CalendarAgentStore(kv) { now }
        val cfg = CalendarAgentConfig(profile = "arnold", sessionId = "cal-sess-1", lastInteractionAt = 12.5)
        writer.syncAgent(gateway, cfg)

        assertEquals(cfg, writer.agentByGateway.value[gateway])

        val reader = CalendarAgentStore(kv) { now }
        reader.warm(gateway)
        assertEquals(cfg, reader.agentByGateway.value[gateway])

        reader.clearGateway(gateway)
        assertNull(reader.agentByGateway.value[gateway])
    }

    @Test
    fun refreshIfNeededRespectsTtlForceMissingPluginAndJumpTarget() = runTest {
        var now = 10_000L
        var calls = 0
        var missing = false
        val api = CalendarApi(
            createHttpClient(
                MockEngine { req ->
                    if (req.url.encodedPath == "/api/plugins/motherbase-calendar/agent") {
                        calls++
                        if (missing) {
                            respond("""{"detail":"Not Found"}""", HttpStatusCode.NotFound, jsonHeaders)
                        } else {
                            respond(
                                """{"agent":{"profile":"ops","session_id":"cal-sess-9","last_interaction_at":55.0}}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        }
                    } else {
                        respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                },
            ),
        )
        val store = CalendarAgentStore(InMemoryKeyValueStore()) { now }

        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(1, calls)
        assertEquals("cal-sess-9", store.agentByGateway.value[gateway]?.sessionId)

        now += 15_000L
        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(1, calls)

        store.refreshIfNeeded(gateway, api, force = true)
        assertEquals(2, calls)

        missing = true
        now += 120_000L
        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(3, calls)
        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(3, calls)

        store.requestOpenCalendar(gateway, openAgent = true)
        val jump = store.pendingJump.value
        assertEquals(gateway, jump?.gateway)
        assertEquals(true, jump?.openAgent)
        if (jump != null) store.consumeJump(jump)
        assertNull(store.pendingJump.value)
    }
}
