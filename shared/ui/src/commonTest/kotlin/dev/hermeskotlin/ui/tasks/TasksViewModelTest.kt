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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    private suspend fun buildHarness(
        httpRespond: (RecordedHttp) -> Pair<HttpStatusCode, String>,
        rpcAnswer: suspend (String, JsonObject) -> String = { method, _ ->
            when (method) {
                "profiles.list" -> """{"profiles":[]}"""
                "projects.list" -> """{"projects":[]}"""
                else -> """{}"""
            }
        },
    ): Triple<TasksViewModel, MutableList<RecordedHttp>, TasksRpcTransport> {
        val requests = mutableListOf<RecordedHttp>()
        val cfg = MockEngineConfig().apply {
            dispatcher = this@TasksViewModelTest.dispatcher
            addHandler { req: HttpRequestData ->
                val path = req.url.encodedPath
                if (path == "/api/ws-ticket") {
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
        val socket = TasksRpcTransport(CoroutineScope(dispatcher), rpcAnswer)
        val connection = GatewayConnection(AuthApi(client, cookies), { _, _ -> socket }, CoroutineScope(dispatcher))
        connection.start(gateway.gatewayUrl)
        connection.state.first { it is ConnectionState.Connected }
        val vm = TasksViewModel(
            directusApi = DirectusTasksApi(client),
            sessionsApi = SessionsApi(client),
            roomsApi = RoomsApi(connection),
            projectsApi = ProjectsApi(connection),
            botsApi = BotsApi(connection),
            connection = connection,
        )
        return Triple(vm, requests, socket)
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
        val (vm, _, _) = buildHarness(
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
        vm.bind(gateway)
        val loaded = vm.state.first { !it.loading && it.tasks.isNotEmpty() }
        assertEquals(2, loaded.tasks.size)
        assertEquals(1, loaded.priorityTasks.size)
        assertEquals("t1", loaded.priorityTasks.single().id)
        assertTrue(loaded.showPriorityStrip)

        vm.togglePriorityStrip(false)
        assertFalse(vm.state.value.showPriorityStrip)
    }

    @Test
    fun selectingTaskSwitchesBetweenRoomAndSessionChannels() = runTest(dispatcher) {
        val (vm, _, _) = buildHarness(
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
            rpcAnswer = { method, params ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":1}}"""
                    "groups.log" -> """{"events":[{"room_id":"task-t1","seq":1,"event_id":"ev-1","kind":"message.user","actor":{"kind":"user","id":"u"},"payload":{"text":"Check disk usage"}}],"cursor":1,"latest_seq":1,"has_more":false}"""
                    else -> """{}"""
                }
            },
        )
        vm.bind(gateway)
        vm.state.first { !it.loading && it.tasks.isNotEmpty() }

        vm.selectTask("t1")
        val roomState = vm.state.first { !it.channelLoading && it.channelMessages.isNotEmpty() }
        assertEquals("Check disk usage", roomState.channelMessages.single().text)

        vm.selectChannel(1)
        val sessState = vm.state.first { it.activeChannelIdx == 1 && !it.channelLoading && it.channelMessages.isNotEmpty() }
        assertEquals("Session ready.", sessState.channelMessages.single().text)
        assertEquals("ops", sessState.channelMessages.single().speaker)

        val nextEvent = async { vm.events.first() }
        val task = vm.state.value.selectedTask!!
        vm.openChannelFullscreen(task, task.channels[1])
        val nav = nextEvent.await()
        assertTrue(nav is TasksNavEvent.OpenSession)
        assertEquals("sess-t1", nav.sessionId)
    }

    @Test
    fun approveStagedPlanUpdatesOptimisticallyPostsTweaksAndDispatchesToChannel() = runTest(dispatcher) {
        val (vm, httpCalls, rpcSocket) = buildHarness(
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
                    "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":1}}"""
                    "groups.log" -> """{"events":[],"cursor":0,"latest_seq":0,"has_more":false}"""
                    "groups.send" -> """{"event":{"room_id":"task-t1","seq":2,"event_id":"ev-2","kind":"message.user","actor":{"kind":"user","id":"u"},"payload":{"text":"approved"}}}"""
                    else -> """{}"""
                }
            },
        )
        vm.bind(gateway)
        vm.state.first { !it.loading && it.tasks.isNotEmpty() }
        vm.selectTask("t1")
        val task = vm.state.value.selectedTask!!

        val tweak = TweakDeltaInput(
            paramKey = "retention",
            paramLabel = "Keep",
            agentProposed = "7 days",
            userSelected = "14 days",
        )
        vm.approveStagedPlan(
            task = task,
            option = ReplyInteraction.Option(label = "Run cleanup", value = "Prune snapshots"),
            selectedParams = mapOf("retention" to "14 days"),
            tweaks = listOf(tweak),
        )

        val updated = vm.state.value.tasks.first { it.id == "t1" }
        assertEquals("in_progress", updated.status)
        assertEquals(TaskContext.STATE_RUNNING, updated.agentState)

        val approveReq = httpCalls.firstOrNull { it.path == "/api/plugins/directus-tasks/tasks/t1/approve" }
        assertNotNull(approveReq)
        assertTrue(approveReq.body.orEmpty().contains("14 days"))
        val sentGroup = rpcSocket.calls.firstOrNull { it.first == "groups.send" }
        assertNotNull(sentGroup)
    }

    @Test
    fun unconfiguredGatewaySavesDirectusConfigAndLoadsBoard() = runTest(dispatcher) {
        var isConfigured = false
        val (vm, httpCalls, _) = buildHarness(
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
        vm.bind(gateway)
        val initial = vm.state.first { !it.loading }
        assertFalse(initial.config?.configured ?: true)

        vm.saveConfig("https://directus.example.com", "dir_secret_00")
        val after = vm.state.first { it.config?.configured == true && it.tasks.isNotEmpty() }
        assertEquals(2, after.tasks.size)
        assertTrue(httpCalls.any { it.method == HttpMethod.Put && it.path == "/api/plugins/directus-tasks/config" })
    }
}
