package dev.hermeskotlin.ui.calendar

import dev.hermeskotlin.core.auth.AuthApi
import dev.hermeskotlin.core.auth.PersistentCookiesStorage
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.calendar.CalendarApi
import dev.hermeskotlin.core.calendar.CalendarCacheStore
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.rpc.RpcTransport
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val gateway = SavedGateway("https://hermes.example.ts.net")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class CalendarRpcTransport(
        private val scope: CoroutineScope,
        private val answer: suspend (String, JsonObject) -> String,
    ) : RpcTransport {
        private val inbound = Channel<String>(Channel.UNLIMITED).apply {
            trySend("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
        }
        val calls = mutableListOf<Pair<String, JsonObject>>()

        override val incoming: Flow<String> = inbound.receiveAsFlow()

        fun push(raw: String) {
            inbound.trySend(raw)
        }

        override suspend fun send(text: String) {
            val message = Json.parseToJsonElement(text).jsonObject
            val id = message["id"] ?: return
            val method = message["method"]?.jsonPrimitive?.content ?: return
            val params = message["params"]?.jsonObject ?: JsonObject(emptyMap())
            calls += method to params
            scope.launch {
                val reply = answer(method, params)
                inbound.send("""{"jsonrpc":"2.0","id":$id,"result":$reply}""")
            }
        }

        override suspend fun close(code: Short, reason: String) {
            inbound.close()
        }
    }

    private data class RecordedHttp(val method: HttpMethod, val path: String, val body: String?)

    private data class Harness(
        val vm: CalendarViewModel,
        val requests: MutableList<RecordedHttp>,
        val socket: CalendarRpcTransport,
        val cacheStore: CalendarCacheStore,
        val connection: GatewayConnection,
    )

    private suspend fun TestScope.buildHarness(
        httpRespond: (RecordedHttp) -> Pair<HttpStatusCode, String>,
        rpcAnswer: suspend (String, JsonObject) -> String = { method, _ ->
            when (method) {
                "profiles.list" -> """{"profiles":[]}"""
                "session.resume" -> """{"session_id":"rt-1"}"""
                "session.start" -> """{"session_id":"rt-new","stored_session_id":"sess-new"}"""
                "prompt.submit" -> """{"ok":true}"""
                else -> """{}"""
            }
        },
    ): Harness {
        val requests = mutableListOf<RecordedHttp>()
        val cfg = MockEngineConfig().apply {
            dispatcher = this@CalendarViewModelTest.dispatcher
            addHandler { req: HttpRequestData ->
                val path = req.url.encodedPath
                if (path == "/api/auth/ws-ticket") {
                    respond("""{"ticket":"T","ttl_seconds":30}""", HttpStatusCode.OK, jsonHeaders)
                } else {
                    val bodyText = (req.body as? TextContent)?.text
                    val rec = RecordedHttp(req.method, path, bodyText)
                    requests += rec
                    val (code, json) = httpRespond(rec)
                    respond(json, code, jsonHeaders)
                }
            }
        }
        val cookies = PersistentCookiesStorage(InMemoryKeyValueStore())
        val client = createHttpClient(MockEngine(cfg), cookies)
        val socket = CalendarRpcTransport(backgroundScope, rpcAnswer)
        val connection = GatewayConnection(AuthApi(client, cookies), { _, _ -> socket }, backgroundScope)
        connection.start(gateway.gatewayUrl)
        withTimeout(5_000) { connection.state.first { it is ConnectionState.Connected } }
        val cache = CalendarCacheStore(InMemoryKeyValueStore())
        val vm = CalendarViewModel(
            calendarApi = CalendarApi(client),
            connection = connection,
            cacheStore = cache,
            directusApi = DirectusTasksApi(client),
            sessionsApi = SessionsApi(client),
            botsApi = BotsApi(connection),
            nowMs = { 1_795_680_000_000L },
        )
        return Harness(vm, requests, socket, cache, connection)
    }

    private fun defaultRespond(rec: RecordedHttp): Pair<HttpStatusCode, String> = when {
        rec.path.endsWith("/health") -> HttpStatusCode.OK to """
            {
              "ok": true,
              "accounts_count": 1,
              "calendars_count": 2,
              "has_hermes_google_token": true,
              "hermes_google_email": "team@example.com",
              "settings": {
                "timezone": "Asia/Ho_Chi_Minh",
                "default_view": "four_days",
                "show_weekends": true,
                "show_declined": false,
                "show_completed_tasks": true
              }
            }
        """
        rec.path.endsWith("/accounts") -> HttpStatusCode.OK to """
            {
              "accounts": [{"id":"acc-1","provider":"google","auth_mode":"oauth","label":"Work"}],
              "calendars": [
                {"id":"cal-1","account_id":"acc-1","name":"Primary","color_hex":"#E67C73","is_primary":true,"writable":true,"visible":true},
                {"id":"cal-2","account_id":"acc-1","name":"MotherBase","color_hex":"#0F766E","is_task_target":true,"writable":true,"visible":true}
              ]
            }
        """
        rec.path.endsWith("/events") -> HttpStatusCode.OK to """
            {
              "events": [
                {"id":"occ-1","calendar_id":"cal-1","title":"Standup","start_at":"2026-11-26T02:00:00Z","end_at":"2026-11-26T03:00:00Z","timezone":"Asia/Ho_Chi_Minh"}
              ]
            }
        """
        rec.path.endsWith("/dispatch/overview") -> HttpStatusCode.OK to """
            {
              "configured": true,
              "projects": [{"id":"proj-1","name":"Core","color":"#0284C7"}],
              "tasks": [{"id":"task-1","title":"Write docs","status":"todo","estimated_minutes":45}],
              "time_blocks": []
            }
        """
        rec.path.endsWith("/proposals") -> HttpStatusCode.OK to """
            {
              "proposals": [
                {
                  "id": "prop-1",
                  "title": "Plan Afternoon",
                  "status": "staged",
                  "operations": [
                    {"op_id":"op-1","kind":"schedule_task","title":"Write docs","task_id":"task-1","start_at":"2026-11-26T08:00:00Z","end_at":"2026-11-26T08:45:00Z","selected":true}
                  ]
                }
              ]
            }
        """
        rec.path.endsWith("/api/plugins/directus-tasks/config") -> HttpStatusCode.OK to """
            {"configured":true,"coordinator":{"profile":"default","session_id":"coord-1"}}
        """
        rec.path.endsWith("/bots") -> HttpStatusCode.OK to """{"bots":[]}"""
        rec.path.contains("/sessions/") && rec.path.endsWith("/messages") -> HttpStatusCode.OK to """{"messages":[]}"""
        else -> HttpStatusCode.OK to """{}"""
    }

    @Test
    fun extractOAuthCodeHandlesRawCodeAndRedirectUrls() {
        assertEquals("abc123", extractOAuthCode("  abc123 "))
        assertEquals("4/0AfJohX", extractOAuthCode("http://localhost:8765/callback?state=xyz&code=4%2F0AfJohX&scope=cal"))
        assertEquals("code-val", extractOAuthCode("https://example.com/cb?code=code-val#fragment"))
    }

    @Test
    fun parseTimeAndBoundsComputeCorrectWindows() {
        assertEquals(9 to 30, parseTimeHHMM("09:30"))
        assertNull(parseTimeHHMM("25:00"))
        assertNull(parseTimeHHMM("bad"))

        val anchor = calendarDateFromIso("2026-11-26")!!
        assertEquals("2026-11-26" to "2026-11-30", computeVisibleBounds(CalendarViewMode.FOUR_DAYS, anchor))
        assertEquals("2026-11-22" to "2026-11-29", computeVisibleBounds(CalendarViewMode.WEEK, anchor))
        assertEquals("2026-11-01" to "2026-12-06", computeVisibleBounds(CalendarViewMode.MONTH, anchor))
    }

    @Test
    fun bindLoadsWorkspaceAndPopulatesOfflineCache() = runTest(dispatcher) {
        val h = buildHarness(::defaultRespond)
        try {
            h.vm.bind(gateway, isVisible = true)
            val s = withTimeout(5_000) { h.vm.state.first { !it.loading && it.calendars.isNotEmpty() } }

            assertFalse(s.loading)
            assertEquals(2, s.calendars.size)
            assertEquals(1, s.events.size)
            assertEquals("Standup", s.events.first().title)
            assertEquals(1, s.proposals.size)
            assertTrue(s.health?.hasHermesGoogleToken == true)

            val cached = h.cacheStore.load(gateway.gatewayUrl)
            assertNotNull(cached)
            assertEquals(2, cached.calendars.size)
            assertEquals("Standup", cached.events.first().title)
        } finally {
            h.connection.stop()
            h.vm.stopForTest()
        }
    }

    @Test
    fun proposalApplyHandlesConflictThenForceAppliesAndLiveEventUpdates() = runTest(dispatcher) {
        var forceCount = 0
        var proposalStatus = ScheduleProposal.STATUS_STAGED
        val h = buildHarness({ rec ->
            when {
                rec.path.endsWith("/proposals") -> HttpStatusCode.OK to """
                    {
                      "proposals": [
                        {
                          "id": "prop-1",
                          "title": "Plan Afternoon",
                          "status": "$proposalStatus",
                          "operations": [
                            {"op_id":"op-1","kind":"schedule_task","title":"Write docs","task_id":"task-1","start_at":"2026-11-26T08:00:00Z","end_at":"2026-11-26T08:45:00Z","selected":true}
                          ]
                        }
                      ]
                    }
                """
                rec.path.endsWith("/proposals/prop-1/apply") -> {
                    val isForce = rec.body?.contains("\"force\":true") == true
                    if (!isForce) {
                        HttpStatusCode.Conflict to """
                            {
                              "conflicts": [{"op_id":"op-1","title":"Write docs","conflicts":[{"id":"ev-x","title":"Busy Call","start_at":"2026-11-26T08:00:00Z","end_at":"2026-11-26T09:00:00Z"}]}],
                              "proposal": {"id":"prop-1","title":"Plan Afternoon","status":"staged","operations":[]}
                            }
                        """
                    } else {
                        forceCount++
                        proposalStatus = ScheduleProposal.STATUS_APPLIED
                        HttpStatusCode.OK to """
                            {"proposal":{"id":"prop-1","title":"Plan Afternoon","status":"applied","operations":[]}}
                        """
                    }
                }
                else -> defaultRespond(rec)
            }
        })
        try {
            h.vm.bind(gateway, isVisible = true)
            withTimeout(5_000) { h.vm.state.first { !it.loading } }

            h.vm.applyProposal("prop-1", listOf("op-1"), force = false)
            withTimeout(5_000) { h.vm.state.first { it.pendingConflict != null } }

            h.vm.confirmForceApplyProposal()
            withTimeout(5_000) { h.vm.state.first { it.proposalStatuses["prop-1"] == ScheduleProposal.STATUS_APPLIED } }
            assertNull(h.vm.state.value.pendingConflict)
            assertEquals(1, forceCount)

            proposalStatus = ScheduleProposal.STATUS_REVERTED
            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"type":"plugin.motherbase-calendar.calendar.proposal_updated","payload":{"proposal_id":"prop-1","status":"reverted"}}}""")
            withTimeout(5_000) { h.vm.state.first { it.proposalStatuses["prop-1"] == ScheduleProposal.STATUS_REVERTED } }
        } finally {
            h.connection.stop()
            h.vm.stopForTest()
        }
    }

    @Test
    fun askCoordinatorResumesSessionAndSubmitsPrompt() = runTest(dispatcher) {
        val h = buildHarness(::defaultRespond)
        try {
            h.vm.bind(gateway, isVisible = true)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.coordinatorSessionId != null } }

            h.vm.askCoordinator("Plan my afternoon tasks")

            withTimeout(5_000) { h.vm.state.first { it.agentTurnRunning } }
            assertTrue(h.socket.calls.any { it.first == "session.resume" && it.second["session_id"]?.jsonPrimitive?.content == "coord-1" })
            assertTrue(h.socket.calls.any { it.first == "prompt.submit" && it.second["text"]?.jsonPrimitive?.content == "Plan my afternoon tasks" })
        } finally {
            h.connection.stop()
            h.vm.stopForTest()
        }
    }

    @Test
    fun toggleCalendarVisibilityAndTaskDoneCallApiOptimistically() = runTest(dispatcher) {
        var taskDone = false
        val h = buildHarness({ rec ->
            when {
                rec.path.endsWith("/calendars/cal-1") -> HttpStatusCode.OK to """
                    {"calendar":{"id":"cal-1","account_id":"acc-1","name":"Primary","color_hex":"#E67C73","is_primary":true,"writable":true,"visible":false}}
                """
                rec.path.endsWith("/dispatch/tasks/task-1/toggle-done") -> {
                    taskDone = true
                    HttpStatusCode.OK to """{"ok":true}"""
                }
                rec.path.endsWith("/dispatch/overview") -> HttpStatusCode.OK to """
                    {
                      "configured": true,
                      "projects": [{"id":"proj-1","name":"Core","color":"#0284C7"}],
                      "tasks": [{"id":"task-1","title":"Write docs","status":"${if (taskDone) "done" else "todo"}","estimated_minutes":45}],
                      "time_blocks": []
                    }
                """
                else -> defaultRespond(rec)
            }
        })
        try {
            h.vm.bind(gateway, isVisible = true)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.calendars.isNotEmpty() } }

            val cal = h.vm.state.value.calendars.first { it.id == "cal-1" }
            h.vm.toggleCalendarVisible(cal)
            assertFalse(h.vm.state.value.calendars.first { it.id == "cal-1" }.visible)

            h.vm.toggleTaskDone("task-1", true)
            assertEquals("done", h.vm.state.value.dispatchOverview.tasks.first { it.id == "task-1" }.status)
            assertTrue(h.requests.any { it.path.endsWith("/dispatch/tasks/task-1/toggle-done") })
        } finally {
            h.connection.stop()
            h.vm.stopForTest()
        }
    }
}
