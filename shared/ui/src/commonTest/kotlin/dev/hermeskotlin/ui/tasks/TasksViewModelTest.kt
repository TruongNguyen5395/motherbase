package dev.hermeskotlin.ui.tasks

import dev.hermeskotlin.core.auth.AuthApi
import dev.hermeskotlin.core.auth.PersistentCookiesStorage
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.projects.ProjectsApi
import dev.hermeskotlin.core.rooms.RoomsApi
import dev.hermeskotlin.core.rpc.RpcTransport
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
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
import kotlinx.coroutines.async
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val gateway = SavedGateway("https://hermes.example.ts.net")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class TasksRpcTransport(
        private val scope: CoroutineScope,
        private val answer: suspend (String, JsonObject) -> String,
    ) : RpcTransport {
        private val inbound = Channel<String>(Channel.UNLIMITED).apply {
            trySend("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
        }
        val calls = mutableListOf<Pair<String, JsonObject>>()

        override val incoming: Flow<String> = inbound.receiveAsFlow()

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
        val vm: TasksViewModel,
        val httpCalls: MutableList<RecordedHttp>,
        val socket: TasksRpcTransport,
        val connection: GatewayConnection,
    )

    private suspend fun TestScope.buildHarness(
        httpRespond: (RecordedHttp) -> Pair<HttpStatusCode, String>,
        rpcAnswer: suspend (String, JsonObject) -> String = { method, _ ->
            when (method) {
                "profiles.list" -> """{"profiles":[]}"""
                "projects.tree", "projects.list" -> """{"projects":[]}"""
                else -> """{}"""
            }
        },
    ): Harness {
        val requests = mutableListOf<RecordedHttp>()
        val cfg = MockEngineConfig().apply {
            dispatcher = this@TasksViewModelTest.dispatcher
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
        val socket = TasksRpcTransport(backgroundScope, rpcAnswer)
        val connection = GatewayConnection(AuthApi(client, cookies), { _, _ -> socket }, backgroundScope)
        connection.start(gateway.gatewayUrl)
        withTimeout(5_000) { connection.state.first { it is ConnectionState.Connected } }
        val vm = TasksViewModel(
            directusApi = DirectusTasksApi(client),
            sessionsApi = SessionsApi(client),
            roomsApi = RoomsApi(connection),
            projectsApi = ProjectsApi(connection),
            botsApi = BotsApi(connection),
            connection = connection,
        )
        return Harness(vm, requests, socket, connection)
    }

    private val boardJson = """
        {
          "configured": true,
          "projects": [
            {"id":"proj-1","name":"Homelab","workspace_cwd":"/srv/homelab","default_bots":["ops"],"sort":1,"archived":false}
          ],
          "tasks": [
            {
              "id":"t1",
              "title":"Prune backup snapshots",
              "status":"todo",
              "priority":"high",
              "context":{
                "id":"ctx-1",
                "task_id":"t1",
                "project_id":"proj-1",
                "assigned_bots":["ops"],
                "agent_state":"needs_you",
                "channels":[
                  {"type":"room","id":"task-t1","label":"Room (@ops, @reviewer)"},
                  {"type":"session","id":"sess-t1","label":"Session (@ops)","profile":"ops"}
                ],
                "staged_plan":{
                  "summary":"Prune old snapshots.",
                  "question":"Choose retention window:",
                  "params":[{"key":"retention","label":"Keep","options":["7 days","14 days"],"default":"7 days"}],
                  "actions":[{"label":"Run cleanup","instruction":"Prune snapshots"}]
                }
              }
            },
            {
              "id":"t2",
              "title":"Rotate TLS certificates",
              "status":"in_progress",
              "priority":"medium",
              "context":{"id":"ctx-2","task_id":"t2","project_id":"proj-1","agent_state":"running"}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun loadsBoardDerivesPriorityTasksAndTogglesPriorityStrip() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true,"token_preview":"tok_••99"}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
        )
        try {
            h.vm.bind(gateway)
            val loaded = withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            assertEquals(2, loaded.tasks.size)
            assertEquals(1, loaded.priorityTasks.size)
            assertEquals("t1", loaded.priorityTasks.single().id)
            assertTrue(loaded.showPriorityStrip)

            h.vm.togglePriorityStrip(false)
            assertFalse(h.vm.state.value.showPriorityStrip)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun selectingTaskSwitchesBetweenRoomAndSessionChannels() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/sessions/sess-t1/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-t1","messages":[{"id":1,"role":"assistant","content":"Session ready."}]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":1}}"""
                    "groups.log" -> """{"events":[{"room_id":"task-t1","seq":1,"event_id":"ev-1","kind":"message.user","actor":{"kind":"user","id":"u"},"payload":{"text":"Check disk usage"}}],"cursor":1,"latest_seq":1,"has_more":false}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.selectTask("t1")
            val roomState = withTimeout(5_000) { h.vm.state.first { !it.channelLoading && it.channelMessages.isNotEmpty() } }
            assertEquals("Check disk usage", roomState.channelMessages.single().text)

            h.vm.selectChannel(1)
            val sessState = withTimeout(5_000) {
                h.vm.state.first { it.activeChannelIdx == 1 && !it.channelLoading && it.channelMessages.isNotEmpty() }
            }
            assertEquals("Session ready.", sessState.channelMessages.single().text)
            assertEquals("ops", sessState.channelMessages.single().speaker)

            val nextEvent = async { h.vm.events.first() }
            val task = h.vm.state.value.selectedTask!!
            h.vm.openChannelFullscreen(task, task.channels[1])
            val nav = withTimeout(5_000) { nextEvent.await() }
            assertTrue(nav is TasksNavEvent.OpenSession)
            assertEquals("sess-t1", nav.sessionId)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun approveStagedPlanUpdatesOptimisticallyPostsTweaksAndDispatchesToChannel() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/plugins/directus-tasks/tasks/t1/approve" ->
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t1",
                                "title":"Prune backup snapshots",
                                "status":"in_progress",
                                "context":{
                                  "id":"ctx-1",
                                  "task_id":"t1",
                                  "project_id":"proj-1",
                                  "agent_state":"running",
                                  "channels":[{"type":"room","id":"task-t1","label":"Room (@ops)"}]
                                }
                              }
                            }
                        """.trimIndent()
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":1}}"""
                    "groups.log" -> """{"events":[],"cursor":0,"latest_seq":0,"has_more":false}"""
                    "groups.send" -> """{"event":{"room_id":"task-t1","seq":2,"event_id":"ev-2","kind":"message.user","actor":{"kind":"user","id":"u"},"payload":{"text":"approved"}}}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            h.vm.selectTask("t1")
            val task = h.vm.state.value.selectedTask!!

            val tweak = TweakDeltaInput(
                paramKey = "retention",
                paramLabel = "Keep",
                agentProposed = "7 days",
                userSelected = "14 days",
            )
            h.vm.approveStagedPlan(
                task = task,
                option = ReplyInteraction.Option(label = "Run cleanup", value = "Prune snapshots"),
                selectedParams = mapOf("retention" to "14 days"),
                tweaks = listOf(tweak),
            )

            val settled = withTimeout(5_000) {
                h.vm.state.first { st ->
                    val cur = st.tasks.firstOrNull { it.id == "t1" }
                    cur?.agentState == TaskContext.STATE_RUNNING &&
                        cur.channels.size == 1 &&
                        "t1" !in st.busyTaskIds
                }
            }
            val updated = settled.tasks.first { it.id == "t1" }
            assertEquals("in_progress", updated.status)
            assertEquals(TaskContext.STATE_RUNNING, updated.agentState)

            val approveReq = h.httpCalls.firstOrNull { it.path == "/api/plugins/directus-tasks/tasks/t1/approve" }
            assertNotNull(approveReq)
            assertTrue(approveReq.body.orEmpty().contains("14 days"))
            val sentGroup = h.socket.calls.firstOrNull { it.first == "groups.send" }
            assertNotNull(sentGroup)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun unconfiguredGatewaySavesDirectusConfigAndLoadsBoard() = runTest(dispatcher) {
        var isConfigured = false
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" -> {
                        if (rec.method == HttpMethod.Put) {
                            isConfigured = true
                            HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true,"token_preview":"dir_••00"}"""
                        } else if (isConfigured) {
                            HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true,"token_preview":"dir_••00"}"""
                        } else {
                            HttpStatusCode.OK to """{"configured":false,"url":"","token_set":false}"""
                        }
                    }
                    "/api/plugins/directus-tasks/board" -> HttpStatusCode.OK to boardJson
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
        )
        try {
            h.vm.bind(gateway)
            val initial = withTimeout(5_000) { h.vm.state.first { !it.loading } }
            assertFalse(initial.config?.configured ?: true)

            h.vm.saveConfig("https://directus.example.com", "dir_secret_00")
            val after = withTimeout(5_000) { h.vm.state.first { it.config?.configured == true && it.tasks.isNotEmpty() } }
            assertEquals(2, after.tasks.size)
            assertTrue(h.httpCalls.any { it.method == HttpMethod.Put && it.path == "/api/plugins/directus-tasks/config" })
        } finally {
            h.connection.stop()
        }
    }
}
