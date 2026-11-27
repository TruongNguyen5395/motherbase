package dev.hermeskotlin.ui.tasks

import dev.hermeskotlin.core.auth.AuthApi
import dev.hermeskotlin.core.auth.PersistentCookiesStorage
import dev.hermeskotlin.core.bots.BotsApi
import dev.hermeskotlin.core.chat.AttachmentKind
import dev.hermeskotlin.core.chat.InputRequest
import dev.hermeskotlin.core.chat.OutgoingAttachment
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.models.ModelOption
import dev.hermeskotlin.core.models.ModelsApi
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.projects.ProjectsApi
import dev.hermeskotlin.core.rooms.RoomsApi
import dev.hermeskotlin.core.rpc.RpcTransport
import dev.hermeskotlin.core.sessions.SessionsApi
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.DirectusTasksApi
import dev.hermeskotlin.core.tasks.DispatchCoordinatorConfig
import dev.hermeskotlin.core.tasks.DispatchSessionsStore
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TweakDeltaInput
import dev.hermeskotlin.ui.components.localMoment
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val responses = mutableListOf<Pair<String, JsonObject>>()

        override val incoming: Flow<String> = inbound.receiveAsFlow()

        fun push(raw: String) {
            inbound.trySend(raw)
        }

        override suspend fun send(text: String) {
            val message = Json.parseToJsonElement(text).jsonObject
            val id = message["id"] ?: return
            val method = message["method"]?.jsonPrimitive?.content
            if (method == null) {
                val resObj = message["result"]?.jsonObject
                if (resObj != null) {
                    responses += id.jsonPrimitive.content to resObj
                }
                return
            }
            val params = message["params"]?.jsonObject ?: JsonObject(emptyMap())
            calls += method to params
            scope.launch {
                val reply = answer(method, params)
                if (reply.startsWith("ERROR:")) {
                    val msg = reply.removePrefix("ERROR:")
                    inbound.send("""{"jsonrpc":"2.0","id":$id,"error":{"code":-32000,"message":"$msg"}}""")
                } else {
                    inbound.send("""{"jsonrpc":"2.0","id":$id,"result":$reply}""")
                }
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
        val dispatchStore: DispatchSessionsStore,
    )

    private suspend fun TestScope.buildHarness(
        httpRespond: (RecordedHttp) -> Pair<HttpStatusCode, String>,
        rpcAnswer: suspend (String, JsonObject) -> String = { method, _ ->
            when (method) {
                "profiles.list" -> """{"profiles":[]}"""
                "projects.tree", "projects.list" -> """{"projects":[]}"""
                "model.options" -> """{"providers":[]}"""
                else -> """{}"""
            }
        },
        dispatchStore: DispatchSessionsStore = DispatchSessionsStore(InMemoryKeyValueStore()),
        includeModelsApi: Boolean = true,
        nowMs: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
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
            dispatchStore = dispatchStore,
            modelsApi = if (includeModelsApi) ModelsApi(connection) else null,
            nowMs = nowMs,
        )
        return Harness(vm, requests, socket, connection, dispatchStore)
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

    @Test
    fun focusProjectExpandAndWorkstreamCollapseUpdateStateAndSurviveRefresh() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.focusProject("proj-1")
            h.vm.setFocusGrouping(TaskFocusGrouping.STATUS)
            h.vm.toggleProjectExpanded("proj-1")
            h.vm.toggleWorkstreamCollapsed("wg-1")

            assertEquals("proj-1", h.vm.state.value.focusedProjectId)
            assertEquals(TaskFocusGrouping.STATUS, h.vm.state.value.focusGrouping)
            assertTrue("proj-1" in h.vm.state.value.expandedProjectIds)
            assertTrue("wg-1" in h.vm.state.value.collapsedWorkstreamKeys)

            h.vm.refresh()
            withTimeout(5_000) { h.vm.state.first { !it.loading } }
            assertEquals("proj-1", h.vm.state.value.focusedProjectId)

            h.vm.focusProject(null)
            assertEquals(null, h.vm.state.value.focusedProjectId)
            assertTrue(h.vm.state.value.collapsedWorkstreamKeys.isEmpty())
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun sendingMessageOnUnlinkedTaskAttachesSessionChannelStreamsLiveReplyAndAnswersClarify() = runTest(dispatcher) {
        var sessionMessagesJson = """{"session_id":"stored-new","messages":[]}"""
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/plugins/directus-tasks/tasks/t2/context" ->
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t2",
                                "title":"Rotate TLS certificates",
                                "status":"in_progress",
                                "priority":"medium",
                                "context":{
                                  "id":"ctx-2",
                                  "task_id":"t2",
                                  "project_id":"proj-1",
                                  "agent_state":"running",
                                  "channels":[{"type":"session","id":"stored-new","label":"Session (@Hermes)"}]
                                }
                              }
                            }
                        """.trimIndent()
                    "/api/sessions/stored-new/messages" ->
                        HttpStatusCode.OK to sessionMessagesJson
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.create" -> """{"session_id":"rt-new","stored_session_id":"stored-new"}"""
                    "session.resume" -> """{"session_id":"rt-new","stored_session_id":"stored-new","running":false,"open_requests":[]}"""
                    "prompt.submit" -> """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.selectTask("t2")
            val t2 = withTimeout(5_000) { h.vm.state.first { it.selectedTaskId == "t2" && !it.channelLoading }.selectedTask!! }
            assertTrue(t2.channels.isEmpty())

            h.vm.sendToActiveChannel(t2, "Check storage stack status")

            val dispatched = withTimeout(5_000) {
                h.vm.state.first { st ->
                    val task = st.tasks.firstOrNull { it.id == "t2" }
                    task?.channels?.any { it.id == "stored-new" } == true &&
                        st.channelMessages.any { it.fromUser && it.text == "Check storage stack status" } &&
                        h.socket.calls.any { it.first == "prompt.submit" }
                }
            }
            assertTrue(dispatched.channelTurnRunning)
            assertEquals("stored-new", dispatched.activeChannel?.id)

            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"rt-new","type":"message.start","payload":{}}}""")
            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"rt-new","type":"tool.start","payload":{"name":"directus_task_board"}}}""")
            val runningTool = withTimeout(5_000) { h.vm.state.first { it.channelTurnStatus == "directus_task_board" } }
            assertEquals("directus_task_board", runningTool.channelTurnStatus)

            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"rt-new","type":"message.delta","payload":{"text":"Checking nginx "}}}""")
            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"rt-new","type":"message.delta","payload":{"text":"workers."}}}""")
            val streamingState = withTimeout(5_000) {
                h.vm.state.first { st -> st.channelMessages.any { !it.fromUser && it.streaming && it.text == "Checking nginx workers." } }
            }
            assertEquals(2, streamingState.channelMessages.size)

            h.socket.push("""{"jsonrpc":"2.0","id":"srq-1","method":"clarify","params":{"session_id":"rt-new","question":"Restart nginx?","choices":["Yes","No"]}}""")
            val withClarify = withTimeout(5_000) { h.vm.state.first { it.channelInputRequests.any { req -> req.id == "srq-1" } } }
            val clarifyReq = withClarify.channelInputRequests.single()
            h.vm.answerChannelRequest(clarifyReq, buildJsonObject { put("answer", "Yes") })
            val afterAnswer = withTimeout(5_000) {
                h.vm.state.first { st -> st.channelInputRequests.isEmpty() && h.socket.responses.any { it.first == "srq-1" } }
            }
            assertEquals("Yes", h.socket.responses.single().second["answer"]?.jsonPrimitive?.content)
            assertTrue(afterAnswer.channelInputRequests.isEmpty())

            sessionMessagesJson = """
                {
                  "session_id":"stored-new",
                  "messages":[
                    {"id":1,"role":"user","content":"Check storage stack status"},
                    {"id":2,"role":"assistant","content":"Storage nginx & PHP-FPM healthy."}
                  ]
                }
            """.trimIndent()
            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"rt-new","type":"message.complete","payload":{}}}""")
            val completed = withTimeout(5_000) {
                h.vm.state.first { st ->
                    !st.channelTurnRunning &&
                        st.channelMessages.none { it.streaming } &&
                        st.channelMessages.lastOrNull()?.text == "Storage nginx & PHP-FPM healthy."
                }
            }
            assertEquals(2, completed.channelMessages.size)
            assertFalse(completed.channelTurnRunning)
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }

    @Test
    fun preparePlanOnUnlinkedTaskAttachesSessionChannelAndStartsTurn() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/plugins/directus-tasks/tasks/t2/context" ->
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t2",
                                "title":"Rotate TLS certificates",
                                "status":"in_progress",
                                "priority":"medium",
                                "context":{
                                  "id":"ctx-2",
                                  "task_id":"t2",
                                  "project_id":"proj-1",
                                  "agent_state":"queued",
                                  "channels":[{"type":"session","id":"stored-plan","label":"Session (@Hermes)"}]
                                }
                              }
                            }
                        """.trimIndent()
                    "/api/sessions/stored-plan/messages" ->
                        HttpStatusCode.OK to """{"session_id":"stored-plan","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.create" -> """{"session_id":"rt-plan","stored_session_id":"stored-plan"}"""
                    "session.resume" -> """{"session_id":"rt-plan","stored_session_id":"stored-plan","running":false,"open_requests":[]}"""
                    "prompt.submit" -> """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            h.vm.selectTask("t2")
            val t2 = h.vm.state.value.selectedTask!!
            h.vm.preparePlan(t2)

            val started = withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.channelTurnRunning &&
                        st.tasks.first { it.id == "t2" }.channels.any { it.id == "stored-plan" } &&
                        h.socket.calls.any { it.first == "prompt.submit" }
                }
            }
            assertTrue(started.channelTurnRunning)
            val planSubmit = h.socket.calls.first { it.first == "prompt.submit" }.second
            assertTrue(planSubmit["text"]?.jsonPrimitive?.content.orEmpty().contains("directus_stage_plan"))
            assertEquals("hidden", planSubmit["display_kind"]?.jsonPrimitive?.content)
            h.socket.push("""{"jsonrpc":"2.0","method":"event","params":{"session_id":"rt-plan","type":"message.complete","payload":{}}}""")
            withTimeout(5_000) { h.vm.state.first { !it.channelTurnRunning } }
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }

    @Test
    fun createSessionChannelStampsManagedAndSyncsDispatchStore() = runTest(dispatcher) {
        val capturedContextBodies = mutableListOf<String>()
        val h = buildHarness(
            httpRespond = { rec ->
                when {
                    rec.path == "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    rec.path == "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    rec.path == "/api/plugins/directus-tasks/tasks/t2/context" -> {
                        rec.body?.let { capturedContextBodies += it }
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t2",
                                "title":"Rotate TLS certificates",
                                "status":"in_progress",
                                "priority":"medium",
                                "context":{
                                  "id":"ctx-2",
                                  "task_id":"t2",
                                  "project_id":"proj-1",
                                  "agent_state":"idle",
                                  "channels":[{"type":"session","id":"stored-m","label":"Session (@ops)","profile":"ops","managed":true}]
                                }
                              }
                            }
                        """.trimIndent()
                    }
                    rec.path == "/api/sessions/stored-m/messages" ->
                        HttpStatusCode.OK to """{"session_id":"stored-m","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.create" -> """{"session_id":"rt-m","stored_session_id":"stored-m"}"""
                    "session.resume" -> """{"session_id":"rt-m","stored_session_id":"stored-m","running":false,"open_requests":[]}"""
                    else -> """{}"""
                }
            },
        )
        try {
            val events = mutableListOf<dev.hermeskotlin.core.tasks.SessionLifecycleChange>()
            val collectJob = backgroundScope.launch {
                h.dispatchStore.lifecycleChanges.collect { events += it }
            }
            h.vm.bind(gateway)
            val loaded = withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            val t2 = loaded.tasks.first { it.id == "t2" }

            h.vm.openOrCreateSessionChannel(t2, "ops")

            withTimeout(5_000) {
                h.vm.state.first { st -> st.tasks.first { it.id == "t2" }.channels.any { it.id == "stored-m" } }
            }
            assertTrue(capturedContextBodies.single().contains(""""managed":true"""), capturedContextBodies.single())
            val entry = h.dispatchStore.byGateway.value[gateway.gatewayUrl]?.get("stored-m")
            assertNotNull(entry)
            assertTrue(entry.managed)
            assertEquals("Rotate TLS certificates", entry.taskTitle)
            assertTrue(events.any { "stored-m" in it.unarchivedIds })
            collectJob.cancel()
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }

    @Test
    fun markingDoneAutoArchivesManagedSessionsButSkipsSharedAndManualAndReopenUnarchives() = runTest(dispatcher) {
        val lifecycleBoardJson = """
            {
              "configured": true,
              "projects": [{"id":"proj-1","name":"Homelab","sort":1,"archived":false}],
              "tasks": [
                {
                  "id":"t-close",
                  "title":"Deploy storage service",
                  "status":"in_progress",
                  "priority":"medium",
                  "context":{
                    "id":"ctx-close",
                    "task_id":"t-close",
                    "project_id":"proj-1",
                    "agent_state":"idle",
                    "channels":[
                      {"type":"session","id":"sess-owned","label":"Deploy thread","profile":"ops","managed":true},
                      {"type":"session","id":"sess-legacy","label":"Session (@coder)","profile":"coder"},
                      {"type":"session","id":"sess-shared","label":"Shared dispatch","profile":"ops","managed":true},
                      {"type":"session","id":"sess-manual","label":"Manual side chat"}
                    ]
                  }
                },
                {
                  "id":"t-keep",
                  "title":"Ongoing sibling task",
                  "status":"todo",
                  "priority":"medium",
                  "context":{
                    "id":"ctx-keep",
                    "task_id":"t-keep",
                    "project_id":"proj-1",
                    "agent_state":"idle",
                    "channels":[
                      {"type":"session","id":"sess-shared","label":"Shared dispatch","profile":"ops","managed":true}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        var currentStatus = DirectusTask.STATUS_IN_PROGRESS
        val sessionPatches = mutableListOf<Pair<String, String>>()
        val h = buildHarness(
            httpRespond = { rec ->
                when {
                    rec.path == "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    rec.path == "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to lifecycleBoardJson
                    rec.method == HttpMethod.Patch && rec.path == "/api/plugins/directus-tasks/tasks/t-close" -> {
                        val next = Json.parseToJsonElement(rec.body.orEmpty()).jsonObject["status"]?.jsonPrimitive?.content ?: currentStatus
                        currentStatus = next
                        HttpStatusCode.OK to """
                            {
                              "task":{
                                "id":"t-close",
                                "title":"Deploy storage service",
                                "status":"$currentStatus",
                                "priority":"medium",
                                "context":{
                                  "id":"ctx-close",
                                  "task_id":"t-close",
                                  "project_id":"proj-1",
                                  "agent_state":"idle",
                                  "channels":[
                                    {"type":"session","id":"sess-owned","label":"Deploy thread","profile":"ops","managed":true},
                                    {"type":"session","id":"sess-legacy","label":"Session (@coder)","profile":"coder"},
                                    {"type":"session","id":"sess-shared","label":"Shared dispatch","profile":"ops","managed":true},
                                    {"type":"session","id":"sess-manual","label":"Manual side chat"}
                                  ]
                                }
                              }
                            }
                        """.trimIndent()
                    }
                    rec.method == HttpMethod.Patch && rec.path.startsWith("/api/sessions/") -> {
                        val sid = rec.path.removePrefix("/api/sessions/")
                        sessionPatches += sid to rec.body.orEmpty()
                        HttpStatusCode.OK to "{}"
                    }
                    rec.path.endsWith("/mutation-snapshot") ->
                        HttpStatusCode.NotFound to """{"detail":"Not Found"}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
        )
        try {
            val lifecycleEvents = mutableListOf<dev.hermeskotlin.core.tasks.SessionLifecycleChange>()
            val collector = backgroundScope.launch {
                h.dispatchStore.lifecycleChanges.collect { lifecycleEvents += it }
            }

            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.size == 2 } }
            h.vm.toggleIncludeDone()
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.includeDone } }

            // 1. Transition to Done: archives owned + legacy Dispatch sessions, skips shared & manual.
            h.vm.updateTaskStatus("t-close", DirectusTask.STATUS_DONE)
            withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.tasks.firstOrNull { it.id == "t-close" }?.status == DirectusTask.STATUS_DONE &&
                        lifecycleEvents.any { it.archivedIds.isNotEmpty() }
                }
            }
            val archivedIds = lifecycleEvents.last().archivedIds
            assertEquals(setOf("sess-owned", "sess-legacy"), archivedIds)
            assertEquals(setOf("sess-owned", "sess-legacy"), sessionPatches.map { it.first }.toSet())
            assertTrue(sessionPatches.all { """"archived":true""" in it.second })
            assertEquals(DirectusTask.STATUS_DONE, h.dispatchStore.byGateway.value[gateway.gatewayUrl]?.get("sess-owned")?.taskStatus)

            // 2. Reopen Done -> In progress: unarchives only auto-managed sessions (skips manual).
            sessionPatches.clear()
            h.vm.updateTaskStatus("t-close", DirectusTask.STATUS_IN_PROGRESS)
            withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.tasks.firstOrNull { it.id == "t-close" }?.status == DirectusTask.STATUS_IN_PROGRESS &&
                        lifecycleEvents.any { "sess-owned" in it.unarchivedIds }
                }
            }
            assertEquals(setOf("sess-owned", "sess-legacy"), lifecycleEvents.last().unarchivedIds)
            assertEquals(setOf("sess-owned", "sess-legacy"), sessionPatches.map { it.first }.toSet())
            assertTrue(sessionPatches.all { """"archived":false""" in it.second })
            assertEquals(DirectusTask.STATUS_IN_PROGRESS, h.dispatchStore.byGateway.value[gateway.gatewayUrl]?.get("sess-owned")?.taskStatus)
            collector.cancel()
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun approvingStagedPlanWithDoneStatusAutoArchivesManagedSession() = runTest(dispatcher) {
        val stagedDoneBoardJson = """
            {
              "configured": true,
              "projects": [{"id":"proj-1","name":"Homelab","sort":1,"archived":false}],
              "tasks": [
                {
                  "id":"t-stage",
                  "title":"Verify backup snapshot",
                  "status":"in_progress",
                  "priority":"medium",
                  "context":{
                    "id":"ctx-stage",
                    "task_id":"t-stage",
                    "project_id":"proj-1",
                    "agent_state":"needs_you",
                    "channels":[{"type":"session","id":"sess-stage","label":"Session (@ops)","profile":"ops","managed":true}],
                    "staged_plan":{
                      "summary":"Confirm snapshot and close task",
                      "params":[{"key":"status","label":"Status","options":["done","in_progress"],"default":"done"}],
                      "actions":[{"label":"Close with note","instruction":"Record final checksum and mark done"}]
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val sessionPatches = mutableListOf<Pair<String, String>>()
        val h = buildHarness(
            httpRespond = { rec ->
                when {
                    rec.path == "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    rec.path == "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to stagedDoneBoardJson
                    rec.path == "/api/plugins/directus-tasks/tasks/t-stage/approve" ->
                        HttpStatusCode.OK to """
                            {
                              "task":{
                                "id":"t-stage",
                                "title":"Verify backup snapshot",
                                "status":"done",
                                "priority":"medium",
                                "context":{
                                  "id":"ctx-stage",
                                  "task_id":"t-stage",
                                  "project_id":"proj-1",
                                  "agent_state":"running",
                                  "channels":[{"type":"session","id":"sess-stage","label":"Session (@ops)","profile":"ops","managed":true}]
                                }
                              }
                            }
                        """.trimIndent()
                    rec.method == HttpMethod.Patch && rec.path == "/api/sessions/sess-stage" -> {
                        sessionPatches += rec.path to rec.body.orEmpty()
                        HttpStatusCode.OK to "{}"
                    }
                    rec.path == "/api/sessions/sess-stage/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-stage","messages":[]}"""
                    rec.path.endsWith("/mutation-snapshot") ->
                        HttpStatusCode.NotFound to """{"detail":"Not Found"}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.resume" -> """{"session_id":"rt-stage","stored_session_id":"sess-stage","running":false,"open_requests":[]}"""
                    "prompt.submit" -> """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            val loaded = withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            val task = loaded.tasks.single()
            val option = ReplyInteraction.Option(label = "Close with note", value = "Record final checksum and mark done")

            h.vm.approveStagedPlan(task, option, mapOf("status" to "done"), emptyList())

            withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.tasks.none { it.id == "t-stage" } && sessionPatches.isNotEmpty()
                }
            }
            assertEquals(1, sessionPatches.size)
            assertTrue(""""archived":true""" in sessionPatches.single().second)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun sessionModelPickerLoadsOptionsSwitchesWithExpensiveConfirmAndAppliesEffortAndFast() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/sessions/sess-t1/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-t1","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, params ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":0}}"""
                    "groups.log" -> """{"events":[],"cursor":0,"latest_seq":0,"has_more":false}"""
                    "session.resume" -> """{"session_id":"rt-t1","stored_session_id":"sess-t1","running":false,"open_requests":[],"info":{"model":"claude-sonnet-4","provider":"anthropic","reasoning_effort":"medium","fast":false}}"""
                    "model.options" -> """{"current_model":"claude-sonnet-4","current_provider":"anthropic","providers":[{"slug":"anthropic","name":"Anthropic","authenticated":true,"models":["claude-sonnet-4","claude-opus-5"],"capabilities":{"claude-opus-5":{"reasoning":true,"fast":true}}}]}"""
                    "config.set" -> {
                        val key = params["key"]?.jsonPrimitive?.content
                        if (key == "model" && params["confirm_expensive_model"]?.jsonPrimitive?.content != "true") {
                            """{"confirm_required":true,"confirm_message":"Opus costs 5x more."}"""
                        } else {
                            """{"ok":true}"""
                        }
                    }
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.selectTask("t1")
            h.vm.selectChannel(1)
            val ready = withTimeout(5_000) {
                h.vm.state.first {
                    it.activeChannelIdx == 1 &&
                        it.channelChatState.model == "claude-sonnet-4" &&
                        it.modelPicker.catalog?.providers?.isNotEmpty() == true
                }
            }
            assertEquals("anthropic", ready.channelChatState.provider)
            assertEquals("medium", ready.channelChatState.reasoningEffort)
            assertEquals(false, ready.channelChatState.fast)

            val opus = ModelOption(id = "claude-opus-5", provider = "anthropic", reasoning = true, fast = true)
            h.vm.selectModel(opus, confirmed = false)
            val needsConfirm = withTimeout(5_000) { h.vm.state.first { it.modelPicker.confirm != null } }
            assertEquals("Opus costs 5x more.", needsConfirm.modelPicker.confirm?.message)
            assertEquals("claude-sonnet-4", needsConfirm.channelChatState.model)

            h.vm.selectModel(opus, confirmed = true)
            val switched = withTimeout(5_000) {
                h.vm.state.first { it.modelPicker.confirm == null && it.channelChatState.model == "claude-opus-5" }
            }
            assertEquals("anthropic", switched.channelChatState.provider)

            h.vm.setReasoningEffort("high")
            h.vm.setFast(true)
            withTimeout(5_000) {
                h.vm.state.first { it.channelChatState.reasoningEffort == "high" && it.channelChatState.fast == true }
            }

            val configCalls = h.socket.calls.filter { it.first == "config.set" }
            assertTrue(configCalls.any { it.second["key"]?.jsonPrimitive?.content == "model" && it.second["confirm_expensive_model"]?.jsonPrimitive?.content == "true" })
            assertTrue(configCalls.any { it.second["key"]?.jsonPrimitive?.content == "reasoning" && it.second["value"]?.jsonPrimitive?.content == "high" })
            assertTrue(configCalls.any { it.second["key"]?.jsonPrimitive?.content == "fast" && it.second["value"]?.jsonPrimitive?.content == "fast" })
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun prePickedModelIsSentWhenCreatingNewSessionOnUnlinkedTask() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/plugins/directus-tasks/tasks/t2/context" ->
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t2",
                                "title":"Rotate TLS certificates",
                                "status":"in_progress",
                                "priority":"medium",
                                "context":{
                                  "id":"ctx-2",
                                  "task_id":"t2",
                                  "project_id":"proj-1",
                                  "agent_state":"running",
                                  "channels":[{"type":"session","id":"sess-new-t2","label":"Session (@Hermes)","managed":true}]
                                }
                              }
                            }
                        """.trimIndent()
                    "/api/sessions/sess-new-t2/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-new-t2","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "model.options" -> """{"providers":[{"slug":"openai-codex","name":"Codex","authenticated":true,"models":["gpt-5.4"]}]}"""
                    "session.create" -> """{"session_id":"rt-new-t2","stored_session_id":"sess-new-t2","info":{"model":"gpt-5.4","provider":"openai-codex","reasoning_effort":"high","fast":true}}"""
                    "session.resume" -> """{"session_id":"rt-new-t2","stored_session_id":"sess-new-t2","running":false,"open_requests":[]}"""
                    "prompt.submit" -> """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.selectTask("t2")
            withTimeout(5_000) { h.vm.state.first { it.selectedTaskId == "t2" && it.modelPicker.catalog != null } }

            h.vm.selectModel(ModelOption(id = "gpt-5.4", provider = "openai-codex", reasoning = true, fast = true))
            h.vm.setReasoningEffort("high")
            h.vm.setFast(true)

            val task = h.vm.state.value.selectedTask!!
            h.vm.sendToActiveChannel(task, "Check cert expiry")
            withTimeout(5_000) { h.vm.state.first { !it.sendingToChannel && h.socket.calls.any { c -> c.first == "prompt.submit" } } }

            val createCall = h.socket.calls.firstOrNull { it.first == "session.create" }
            assertNotNull(createCall)
            assertEquals("gpt-5.4", createCall.second["model"]?.jsonPrimitive?.content)
            assertEquals("openai-codex", createCall.second["provider"]?.jsonPrimitive?.content)
            assertEquals("high", createCall.second["reasoning_effort"]?.jsonPrimitive?.content)
            assertEquals("true", createCall.second["fast"]?.jsonPrimitive?.content)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun sessionAttachmentsUploadImagesAndFilesAndRollbackOnSubmitFailure() = runTest(dispatcher) {
        var failSubmit = false
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/sessions/sess-t1/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-t1","messages":[{"id":1,"role":"user","content":"@file:/srv/report.pdf\n\nPlease review"}]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":0}}"""
                    "groups.log" -> """{"events":[],"cursor":0,"latest_seq":0,"has_more":false}"""
                    "model.options" -> """{"providers":[]}"""
                    "session.resume" -> """{"session_id":"rt-t1","stored_session_id":"sess-t1","running":false,"open_requests":[]}"""
                    "image.attach_bytes" -> """{"attached":true,"path":"/tmp/hermes-img-1.png"}"""
                    "file.attach" -> """{"attached":true,"ref_text":"@file:/tmp/spec.pdf","path":"/tmp/spec.pdf"}"""
                    "image.detach" -> """{"detached":true}"""
                    "prompt.submit" -> if (failSubmit) "ERROR:gateway busy" else """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.selectTask("t1")
            h.vm.selectChannel(1)
            val loaded = withTimeout(5_000) {
                h.vm.state.first { it.activeChannelIdx == 1 && !it.channelLoading && it.channelMessages.isNotEmpty() }
            }
            // History @file: ref is parsed into a clean attachment chip without raw directive text.
            assertEquals("Please review", loaded.channelMessages.single().text)
            assertEquals(listOf("report.pdf"), loaded.channelMessages.single().attachments.map { it.name })

            val photo = OutgoingAttachment("a1", "rack.png", "image/png", byteArrayOf(1, 2, 3))
            val doc = OutgoingAttachment("a2", "spec.pdf", "application/pdf", byteArrayOf(4, 5, 6))
            val extra = OutgoingAttachment("a3", "drop.txt", "text/plain", byteArrayOf(7))
            h.vm.addAttachments(listOf(photo, doc, extra))
            assertEquals(3, h.vm.state.value.channelAttachments.size)
            h.vm.removeAttachment("a3")
            assertEquals(2, h.vm.state.value.channelAttachments.size)

            val task = h.vm.state.value.selectedTask!!
            h.vm.sendToActiveChannel(task, "Inspect these")
            withTimeout(5_000) { h.vm.state.first { !it.sendingToChannel && h.socket.calls.any { c -> c.first == "prompt.submit" } } }

            assertTrue(h.vm.state.value.channelAttachments.isEmpty())
            assertTrue(h.socket.calls.any { it.first == "image.attach_bytes" && it.second["filename"]?.jsonPrimitive?.content == "rack.png" })
            assertTrue(h.socket.calls.any { it.first == "file.attach" && it.second["name"]?.jsonPrimitive?.content == "spec.pdf" })
            val submit = h.socket.calls.last { it.first == "prompt.submit" }
            assertEquals("@file:/tmp/spec.pdf\n\nInspect these", submit.second["text"]?.jsonPrimitive?.content)
            val optimistic = h.vm.state.value.channelMessages.last()
            assertEquals(listOf(AttachmentKind.Image, AttachmentKind.Pdf), optimistic.attachments.map { it.kind })

            // Failure restores queued attachments and detaches queued images on the gateway.
            failSubmit = true
            h.vm.addAttachments(listOf(photo))
            h.vm.sendToActiveChannel(task, "")
            val rolledBack = withTimeout(5_000) {
                h.vm.state.first { !it.sendingToChannel && it.channelError != null }
            }
            assertEquals(listOf("a1"), rolledBack.channelAttachments.map { it.id })
            assertTrue(h.socket.calls.any { it.first == "image.detach" && it.second["path"]?.jsonPrimitive?.content == "/tmp/hermes-img-1.png" })
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun roomAttachmentsUploadViaGroupsApiAndSkipSessionModelConfig() = runTest(dispatcher) {
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":0}}"""
                    "groups.log" -> """{"events":[],"cursor":0,"latest_seq":0,"has_more":false}"""
                    "groups.attachment.upload" -> """{"attachment_id":"att-room-1","kind":"file","name":"notes.txt","mime":"text/plain","size":5}"""
                    "groups.send" -> """{"event":{"room_id":"task-t1","seq":1,"event_id":"ev-1","kind":"message.user","actor":{"kind":"user","id":"u"},"payload":{"text":""}}}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.selectTask("t1")
            withTimeout(5_000) { h.vm.state.first { it.selectedTaskId == "t1" && !it.channelLoading } }

            // Room channel ignores session-level model/effort/fast calls.
            h.vm.selectModel(ModelOption(id = "claude-opus-5", provider = "anthropic"))
            h.vm.setReasoningEffort("high")
            h.vm.setFast(true)
            assertNull(h.vm.state.value.channelChatState.model)
            assertTrue(h.socket.calls.none { it.first == "config.set" })

            val note = OutgoingAttachment("r1", "notes.txt", "text/plain", "hello".encodeToByteArray())
            h.vm.addAttachments(listOf(note))
            val task = h.vm.state.value.selectedTask!!
            h.vm.sendToActiveChannel(task, "")
            withTimeout(5_000) { h.vm.state.first { !it.sendingToChannel && h.socket.calls.any { c -> c.first == "groups.send" } } }

            assertTrue(h.vm.state.value.channelAttachments.isEmpty())
            assertTrue(h.socket.calls.any { it.first == "groups.attachment.upload" && it.second["name"]?.jsonPrimitive?.content == "notes.txt" })
            val sendCall = h.socket.calls.last { it.first == "groups.send" }
            assertTrue(sendCall.second.toString().contains("att-room-1"))
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun coordinatorCreatesSessionTriggersQuickCheckinAndResetsByArchivingPreviousSession() = runTest(dispatcher) {
        var sessionCounter = 0
        var activeSessionId = ""
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """
                            {
                              "configured": true,
                              "url": "https://directus.example.com",
                              "token_set": true,
                              "coordinator": {
                                "profile": "default",
                                "session_id": "$activeSessionId",
                                "default_prompt": "Always prioritize blocked items.",
                                "checkin_enabled": true,
                                "checkin_times": ["09:00", "16:00"],
                                "window_minutes": 30
                              }
                            }
                        """.trimIndent()
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    "/api/plugins/directus-tasks/coordinator" -> {
                        val body = rec.body.orEmpty()
                        if (body.contains("\"session_id\"")) {
                            val parsed = Json.parseToJsonElement(body).jsonObject
                            activeSessionId = parsed["session_id"]?.jsonPrimitive?.content ?: activeSessionId
                        }
                        HttpStatusCode.OK to """
                            {
                              "configured": true,
                              "url": "https://directus.example.com",
                              "token_set": true,
                              "coordinator": {
                                "profile": "default",
                                "session_id": "$activeSessionId",
                                "default_prompt": "Always prioritize blocked items.",
                                "checkin_enabled": true,
                                "checkin_times": ["09:00", "16:00"],
                                "window_minutes": 30
                              }
                            }
                        """.trimIndent()
                    }
                    "/api/sessions/coord-1/mutation-snapshot" ->
                        HttpStatusCode.OK to """{"exists":true,"runtime_revision":1,"runtime_generation":1}"""
                    "/api/sessions/coord-1" ->
                        HttpStatusCode.OK to """{"session_id":"coord-1","archived":true}"""
                    "/api/sessions/coord-1/messages", "/api/sessions/coord-2/messages" ->
                        HttpStatusCode.OK to """{"session_id":"$activeSessionId","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.create", "session.ensure" -> {
                        sessionCounter += 1
                        val id = "coord-$sessionCounter"
                        activeSessionId = id
                        """{"session_id":"$id","stored_session_id":"$id","resumed":false}"""
                    }
                    "session.title.set" -> """{"ok":true}"""
                    "prompt.submit" -> """{"ok":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            // Trigger an on-demand check-in via quick chip: opens coordinator rail, creates session coord-1, and submits prompt.
            h.vm.triggerQuickCoordinatorAction(CoordinatorQuickAction.CHECK_IN)
            val afterCheckin = withTimeout(5_000) {
                h.vm.state.first {
                    it.coordinatorOpen &&
                        it.coordinatorConfig.sessionId == "coord-1" &&
                        h.socket.calls.any { c -> c.first == "prompt.submit" }
                }
            }
            assertEquals("coord-1", afterCheckin.activeChannel?.id)
            val submit = h.socket.calls.last { it.first == "prompt.submit" }
            assertEquals("coord-1", submit.second["session_id"]?.jsonPrimitive?.content)
            assertTrue(submit.second["text"]?.jsonPrimitive?.content.orEmpty().contains("check-in", ignoreCase = true))

            // Resetting the coordinator session archives coord-1 and creates coord-2.
            h.vm.resetCoordinatorSession()
            val afterReset = withTimeout(5_000) {
                h.vm.state.first { !it.resettingCoordinator && it.coordinatorConfig.sessionId == "coord-2" }
            }
            assertEquals("coord-2", afterReset.activeChannel?.id)
            assertTrue(h.httpCalls.any { it.method == HttpMethod.Patch && it.path == "/api/sessions/coord-1" })
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun checkInOnlyAutoOpensDuringConfiguredWindowAndDismissDoesNotReopenOnRefresh() = runTest(dispatcher) {
        val fakeNowMs = 1_700_000_000_000L
        val moment = localMoment(fakeNowMs)
        val nowMinute = moment.hour * 60 + moment.minute
        val currentSlot = DispatchCoordinatorConfig.formatSlotMinute(nowMinute)
        val distantSlot = DispatchCoordinatorConfig.formatSlotMinute((nowMinute + 240) % 1440)
        var scheduleTime = distantSlot
        var scheduleInstruction = ""

        val h = buildHarness(
            nowMs = { fakeNowMs },
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://d.example","token_preview":"tok","source":"saved"}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to """
                        {
                          "configured": true,
                          "projects": [],
                          "tasks": [],
                          "coordinator": {
                            "profile": "default",
                            "session_id": "",
                            "default_prompt": "",
                            "checkin_enabled": true,
                            "checkin_times": ["$scheduleTime"],
                            "checkin_schedules": [
                              {"id": "rule-1", "time": "$scheduleTime", "window_minutes": 25, "instruction": "$scheduleInstruction", "enabled": true}
                            ],
                            "window_minutes": 25,
                            "grace_minutes": 25,
                            "cooldown_minutes": 45,
                            "auto_open_on_launch": true,
                            "last_checkin_slot": "",
                            "last_interaction_at": 0.0
                          }
                        }
                        """.trimIndent()
                    "/api/plugins/directus-tasks/coordinator" ->
                        HttpStatusCode.OK to """
                        {
                          "coordinator": {
                            "profile": "default",
                            "session_id": "coord-win",
                            "default_prompt": "",
                            "checkin_enabled": true,
                            "checkin_times": ["$scheduleTime"],
                            "checkin_schedules": [
                              {"id": "rule-1", "time": "$scheduleTime", "window_minutes": 25, "instruction": "$scheduleInstruction", "enabled": true}
                            ],
                            "window_minutes": 25,
                            "grace_minutes": 25,
                            "cooldown_minutes": 45,
                            "auto_open_on_launch": true,
                            "last_checkin_slot": "",
                            "last_interaction_at": 0.0
                          }
                        }
                        """.trimIndent()
                    "/api/sessions/coord-win/messages" ->
                        HttpStatusCode.OK to """{"session_id":"coord-win","messages":[]}"""
                    else -> HttpStatusCode.NotFound to """{"error":"${rec.path}"}"""
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "bots.list" -> """{"bots":[{"name":"default","is_default":true}]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "model.options" -> """{"providers":[]}"""
                    "session.create", "session.ensure" -> """{"session_id":"rt-win","stored_session_id":"coord-win"}"""
                    "prompt.submit" -> """{"ok":true}"""
                    else -> """{}"""
                }
            },
        )

        try {
            h.vm.setVisible(true)
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.config?.configured == true } }

            // Outside configured check-in window: sidebar stays closed and no due slot is set.
            assertFalse(h.vm.state.value.coordinatorOpen)
            assertNull(h.vm.state.value.checkinDueSlot)

            // Inside active check-in window with custom standing instruction:
            // coordinator opens proactively and includes the schedule instruction in the prompt.
            scheduleTime = currentSlot
            scheduleInstruction = "Verify sprint risks and pending plans"
            h.vm.refresh()
            withTimeout(5_000) {
                h.vm.state.first {
                    it.coordinatorOpen &&
                        h.socket.calls.any { c -> c.first == "prompt.submit" }
                }
            }
            val submitText = h.socket.calls.last { it.first == "prompt.submit" }.second["text"]?.jsonPrimitive?.content.orEmpty()
            assertTrue(submitText.contains("Focus for this check-in window: Verify sprint risks and pending plans"))

            // Closing the sidebar dismisses the active slot so subsequent refreshes do not yank it open again.
            h.vm.closeCoordinator()
            assertFalse(h.vm.state.value.coordinatorOpen)
            h.vm.refresh()
            withTimeout(5_000) {
                h.vm.state.first {
                    !it.loading && h.httpCalls.count { call -> call.path == "/api/plugins/directus-tasks/board" } >= 3
                }
            }
            assertFalse(h.vm.state.value.coordinatorOpen)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun updateTaskMatrixPersistsImpactUrgencyAndSyncedPriority() = runTest(dispatcher) {
        val contextBodies = mutableListOf<String>()
        val taskPatchBodies = mutableListOf<String>()
        val h = buildHarness(
            httpRespond = { rec ->
                when {
                    rec.path == "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    rec.path == "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardJson
                    rec.path == "/api/plugins/directus-tasks/tasks/t2/context" && rec.method == HttpMethod.Put -> {
                        rec.body?.let { contextBodies += it }
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t2",
                                "title":"Rotate TLS certificates",
                                "status":"in_progress",
                                "priority":"medium",
                                "context":{"id":"ctx-2","task_id":"t2","project_id":"proj-1","impact":"high","urgency":"high"}
                              }
                            }
                        """.trimIndent()
                    }
                    rec.path == "/api/plugins/directus-tasks/tasks/t2" && rec.method == HttpMethod.Patch -> {
                        rec.body?.let { taskPatchBodies += it }
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t2",
                                "title":"Rotate TLS certificates",
                                "status":"in_progress",
                                "priority":"urgent",
                                "context":{"id":"ctx-2","task_id":"t2","project_id":"proj-1","impact":"high","urgency":"high"}
                              }
                            }
                        """.trimIndent()
                    }
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }

            h.vm.updateTaskMatrix("t2", MatrixLevel.HIGH, MatrixLevel.HIGH)

            val updated = withTimeout(5_000) {
                h.vm.state.first { st ->
                    val t = st.tasks.firstOrNull { it.id == "t2" }
                    t?.impact == "high" && t.urgency == "high" && t.priority == "urgent" && taskPatchBodies.isNotEmpty()
                }
            }
            assertEquals("high", updated.tasks.first { it.id == "t2" }.impact)
            assertEquals("high", updated.tasks.first { it.id == "t2" }.urgency)
            assertTrue(contextBodies.any { it.contains("\"impact\":\"high\"") && it.contains("\"urgency\":\"high\"") })
            assertTrue(taskPatchBodies.any { it.contains("\"priority\":\"urgent\"") })
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun ackTaskReplyClearsReplyBadgeAndPersistsReplyAckedAt() = runTest(dispatcher) {
        val boardWithReply = """
            {
              "configured": true,
              "projects": [{"id":"proj-1","name":"Ops","sort":1}],
              "tasks": [
                {
                  "id":"t1",
                  "title":"Check logs",
                  "status":"in_progress",
                  "priority":"medium",
                  "context":{
                    "id":"ctx-1",
                    "task_id":"t1",
                    "project_id":"proj-1",
                    "channels":[{"type":"session","id":"sess-1","label":"Session (@ops)","profile":"ops"}]
                  },
                  "reply_status":{
                    "has_unread_reply":true,
                    "awaiting_reply":true,
                    "last_reply_at":1700000000.0,
                    "last_reply_channel_id":"sess-1",
                    "last_reply_channel_type":"session"
                  }
                }
              ]
            }
        """.trimIndent()
        val ackBodies = mutableListOf<String>()
        var markedSessionRead = false
        val h = buildHarness(
            httpRespond = { rec ->
                when {
                    rec.path == "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    rec.path == "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to boardWithReply
                    rec.path == "/api/sessions/sess-1/mutation-snapshot" ->
                        HttpStatusCode.OK to """{"exists":true,"runtime_revision":1,"runtime_generation":1}"""
                    rec.path == "/api/sessions/sess-1" && rec.method == HttpMethod.Patch -> {
                        markedSessionRead = true
                        HttpStatusCode.OK to """{}"""
                    }
                    rec.path == "/api/plugins/directus-tasks/tasks/t1/context" && rec.method == HttpMethod.Put -> {
                        rec.body?.let { ackBodies += it }
                        HttpStatusCode.OK to """
                            {
                              "task": {
                                "id":"t1",
                                "title":"Check logs",
                                "status":"in_progress",
                                "priority":"medium",
                                "context":{"id":"ctx-1","task_id":"t1","project_id":"proj-1","reply_acked_at":"2026-11-22T07:00:00Z"},
                                "reply_status":{"has_unread_reply":false,"awaiting_reply":false}
                              }
                            }
                        """.trimIndent()
                    }
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
        )
        try {
            h.vm.bind(gateway)
            val initial = withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            assertEquals(TaskReplyBadge.UNREAD, initial.replyBadges["t1"])
            assertEquals(1, initial.attentionSplit.agentReplies.size)

            h.vm.ackTaskReply("t1")

            val cleared = withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.replyBadges["t1"] == TaskReplyBadge.NONE &&
                        ackBodies.isNotEmpty() &&
                        st.tasks.firstOrNull { it.id == "t1" }?.replyAckedAt == "2026-11-22T07:00:00Z"
                }
            }
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(0, cleared.attentionSplit.agentReplies.size)
            assertTrue(ackBodies.any { it.contains("reply_acked_at") })
            assertTrue(markedSessionRead)
        } finally {
            h.connection.stop()
        }
    }

    @Test
    fun approveStagedPlanOnSessionDispatchesConciseSummaryAndShowsOptimisticBubble() = runTest(dispatcher) {
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
                                  "channels":[
                                    {"type":"room","id":"task-t1","label":"Room (@ops, @reviewer)"},
                                    {"type":"session","id":"sess-t1","label":"Session (@ops)","profile":"ops"}
                                  ],
                                  "staged_plan":{
                                    "summary":"Prune old snapshots.",
                                    "approved_at":"2026-11-26T08:00:00Z",
                                    "approved_option":"Run cleanup"
                                  }
                                }
                              }
                            }
                        """.trimIndent()
                    "/api/sessions/sess-t1/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-t1","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "groups.state" -> """{"room":{"room_id":"task-t1","name":"Prune backup snapshots","members":[],"latest_seq":0}}"""
                    "groups.log" -> """{"events":[],"cursor":0,"latest_seq":0,"has_more":false}"""
                    "session.resume" -> """{"session_id":"rt-t1","stored_session_id":"sess-t1","running":false,"open_requests":[]}"""
                    "prompt.submit" -> """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            h.vm.selectTask("t1")
            h.vm.selectChannel(1)
            withTimeout(5_000) { h.vm.state.first { it.activeChannelIdx == 1 && !it.channelLoading } }

            val task = h.vm.state.value.selectedTask!!
            h.vm.approveStagedPlan(
                task = task,
                option = ReplyInteraction.Option(
                    label = "Run cleanup",
                    value = "Call directus_log_context with detailed cleanup SQL and prune all stale backups older than retention",
                ),
                selectedParams = mapOf("retention" to "14 days"),
                tweaks = emptyList(),
            )

            val settled = withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.tasks.firstOrNull { it.id == "t1" }?.agentState == TaskContext.STATE_RUNNING &&
                        h.socket.calls.any { it.first == "prompt.submit" } &&
                        "t1" !in st.busyTaskIds
                }
            }
            val submitText = h.socket.calls.last { it.first == "prompt.submit" }.second["text"]?.jsonPrimitive?.content.orEmpty()
            assertEquals("Run cleanup (Keep: 14 days)", submitText)
            assertTrue(settled.channelTurnRunning)
            assertEquals("Run cleanup (Keep: 14 days)", settled.channelMessages.last().text)
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }

    @Test
    fun openExternalTaskAutoEnablesIncludeDoneWhenTargetTaskIsCompleted() = runTest(dispatcher) {
        var boardCalls = 0
        val doneBoardJson = """
            {
              "configured": true,
              "projects": [{"id":"proj-1","name":"Ops","archived":false}],
              "tasks": [
                {
                  "id":"t-done",
                  "title":"Completed delivery",
                  "status":"done",
                  "priority":"medium",
                  "project_id":"proj-1",
                  "context":{
                    "id":"ctx-done",
                    "task_id":"t-done",
                    "project_id":"proj-1",
                    "agent_state":"idle",
                    "assigned_bots":["ops"],
                    "channels":[{"type":"session","id":"sess-done","label":"Session (@ops)","profile":"ops","managed":true}]
                  }
                }
              ]
            }
        """.trimIndent()
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" -> {
                        boardCalls += 1
                        // Calls 1 (bind) and 2 (openExternalTask retry check with include_done=false) omit the done task;
                        // call 3 happens after TasksViewModel auto-enables includeDone=true.
                        if (boardCalls <= 2) HttpStatusCode.OK to boardJson
                        else HttpStatusCode.OK to doneBoardJson
                    }
                    "/api/sessions/sess-done/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-done","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.resume" -> """{"session_id":"rt-done","stored_session_id":"sess-done","running":false,"open_requests":[]}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            assertFalse(h.vm.state.value.includeDone)

            h.vm.openExternalTask("t-done", "sess-done")
            val settled = withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.includeDone && st.selectedTaskId == "t-done" && !st.loading
                }
            }
            assertEquals("sess-done", settled.activeChannel?.id)
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }

    @Test
    fun approveStagedPlanSwitchesSessionWhenSelectedAgentDiffersAndPreservesHistory() = runTest(dispatcher) {
        val mixedBoardJson = """
            {
              "configured": true,
              "projects": [{"id":"proj-1","name":"Ops","archived":false}],
              "tasks": [
                {
                  "id":"t-mix",
                  "title":"Deliver website",
                  "status":"in_progress",
                  "priority":"high",
                  "project_id":"proj-1",
                  "context":{
                    "id":"ctx-mix",
                    "task_id":"t-mix",
                    "project_id":"proj-1",
                    "agent_state":"needs_you",
                    "assigned_bots":["coder"],
                    "channels":[{"type":"session","id":"sess-default","label":"Session (@Hermes)","managed":true}],
                    "staged_plan":{
                      "summary":"Send final invoice.",
                      "params":[{"key":"owner","label":"Owner","options":["@coder","@default"],"default":"@coder"}],
                      "options":[{"label":"Proceed","value":"Send it"}]
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val upsertProfiles = mutableListOf<String?>()
        val h = buildHarness(
            httpRespond = { rec ->
                when (rec.path) {
                    "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to mixedBoardJson
                    "/api/plugins/directus-tasks/tasks/t-mix/context" -> {
                        HttpStatusCode.OK to """
                            {
                              "task":{
                                "id":"t-mix",
                                "title":"Deliver website",
                                "status":"in_progress",
                                "project_id":"proj-1",
                                "context":{
                                  "id":"ctx-mix",
                                  "task_id":"t-mix",
                                  "project_id":"proj-1",
                                  "agent_state":"needs_you",
                                  "assigned_bots":["default","coder"],
                                  "channels":[
                                    {"type":"session","id":"sess-default","label":"Session (@Hermes)","managed":true},
                                    {"type":"session","id":"sess-coder","label":"Session (@coder)","profile":"coder","managed":true}
                                  ]
                                }
                              }
                            }
                        """.trimIndent()
                    }
                    "/api/plugins/directus-tasks/tasks/t-mix/approve" ->
                        HttpStatusCode.OK to """
                            {
                              "task":{
                                "id":"t-mix",
                                "title":"Deliver website",
                                "status":"in_progress",
                                "project_id":"proj-1",
                                "context":{
                                  "id":"ctx-mix",
                                  "task_id":"t-mix",
                                  "project_id":"proj-1",
                                  "agent_state":"running",
                                  "assigned_bots":["coder"],
                                  "channels":[
                                    {"type":"session","id":"sess-default","label":"Session (@Hermes)","managed":true},
                                    {"type":"session","id":"sess-coder","label":"Session (@coder)","profile":"coder","managed":true}
                                  ]
                                }
                              }
                            }
                        """.trimIndent()
                    "/api/sessions/sess-default/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-default","messages":[]}"""
                    "/api/sessions/sess-coder/messages" ->
                        HttpStatusCode.OK to """{"session_id":"sess-coder","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, params ->
                when (method) {
                    "profiles.list" -> """{"profiles":[{"name":"default","label":"Ops Lead","is_default":true},{"name":"coder","label":"Coder"}]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.create" -> {
                        upsertProfiles += params["profile"]?.jsonPrimitive?.content
                        """{"session_id":"rt-coder","stored_session_id":"sess-coder"}"""
                    }
                    "session.resume" -> """{"session_id":"rt-default","stored_session_id":"sess-default","running":false,"open_requests":[]}"""
                    "prompt.submit" -> """{"accepted":true}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() && it.bots.isNotEmpty() } }
            h.vm.selectTask("t-mix")
            withTimeout(5_000) { h.vm.state.first { it.selectedTaskId == "t-mix" && !it.channelLoading } }

            val task = h.vm.state.value.selectedTask!!
            h.vm.approveStagedPlan(
                task = task,
                option = ReplyInteraction.Option(label = "Proceed", value = "Send it"),
                selectedParams = mapOf("owner" to "@coder"),
                tweaks = emptyList(),
            )

            val settled = withTimeout(5_000) {
                h.vm.state.first { st ->
                    st.activeChannel?.id == "sess-coder" && "t-mix" !in st.busyTaskIds
                }
            }
            assertEquals<List<String?>>(listOf("coder"), upsertProfiles)
            assertEquals(2, settled.selectedTask?.channels?.size)
            assertEquals("sess-default", settled.selectedTask?.channels?.get(0)?.id)
            assertEquals("sess-coder", settled.selectedTask?.channels?.get(1)?.id)
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }

    @Test
    fun replaceAndUnlinkChannelUpdateContextArchiveDroppedSessionAndSyncStore() = runTest(dispatcher) {
        val archivedPaths = mutableListOf<String>()
        val h = buildHarness(
            httpRespond = { rec ->
                when {
                    rec.path == "/api/plugins/directus-tasks/config" ->
                        HttpStatusCode.OK to """{"configured":true,"url":"https://directus.example.com","token_set":true}"""
                    rec.path == "/api/plugins/directus-tasks/board" ->
                        HttpStatusCode.OK to """
                            {
                              "configured": true,
                              "projects": [{"id":"proj-1","name":"Ops","archived":false}],
                              "tasks": [
                                {
                                  "id":"t-rep",
                                  "title":"Switch agent test",
                                  "status":"in_progress",
                                  "project_id":"proj-1",
                                  "context":{
                                    "id":"ctx-rep",
                                    "task_id":"t-rep",
                                    "project_id":"proj-1",
                                    "agent_state":"idle",
                                    "assigned_bots":["default"],
                                    "channels":[{"type":"session","id":"sess-old","label":"Session (@Hermes)","managed":true}]
                                  }
                                }
                              ]
                            }
                        """.trimIndent()
                    rec.path == "/api/plugins/directus-tasks/tasks/t-rep/context" -> {
                        val hasOld = rec.body?.contains("sess-old") == true
                        val hasNew = rec.body?.contains("sess-new") == true
                        val channelsJson = when {
                            hasNew -> """[{"type":"session","id":"sess-new","label":"Session (@ops)","profile":"ops","managed":true}]"""
                            !hasOld -> "[]"
                            else -> """[{"type":"session","id":"sess-old","label":"Session (@Hermes)","managed":true}]"""
                        }
                        val botsJson = if (hasNew) """["ops"]""" else "[]"
                        HttpStatusCode.OK to """
                            {
                              "task":{
                                "id":"t-rep",
                                "title":"Switch agent test",
                                "status":"in_progress",
                                "project_id":"proj-1",
                                "context":{
                                  "id":"ctx-rep",
                                  "task_id":"t-rep",
                                  "project_id":"proj-1",
                                  "agent_state":"idle",
                                  "assigned_bots":$botsJson,
                                  "channels":$channelsJson
                                }
                              }
                            }
                        """.trimIndent()
                    }
                    rec.path.endsWith("/mutation-snapshot") ->
                        HttpStatusCode.OK to """{"exists":true,"runtime_revision":1,"runtime_generation":1}"""
                    rec.method == HttpMethod.Patch && rec.path.startsWith("/api/sessions/") -> {
                        archivedPaths += rec.path
                        HttpStatusCode.OK to "{}"
                    }
                    rec.path.startsWith("/api/sessions/") && rec.path.endsWith("/messages") ->
                        HttpStatusCode.OK to """{"session_id":"x","messages":[]}"""
                    else -> HttpStatusCode.NotFound to "{}"
                }
            },
            rpcAnswer = { method, _ ->
                when (method) {
                    "profiles.list" -> """{"profiles":[{"name":"default","label":"Ops Lead","is_default":true},{"name":"ops","label":"Ops"}]}"""
                    "projects.tree", "projects.list" -> """{"projects":[]}"""
                    "session.create" -> """{"session_id":"rt-new","stored_session_id":"sess-new"}"""
                    "session.resume" -> """{"session_id":"rt-res","stored_session_id":"sess-old","running":false,"open_requests":[]}"""
                    else -> """{}"""
                }
            },
        )
        try {
            h.vm.bind(gateway)
            withTimeout(5_000) { h.vm.state.first { !it.loading && it.tasks.isNotEmpty() } }
            h.vm.selectTask("t-rep")
            withTimeout(5_000) { h.vm.state.first { it.selectedTaskId == "t-rep" && !it.channelLoading } }

            val initial = h.vm.state.value.selectedTask!!
            h.vm.replaceWithSessionChannel(initial, initial.channels.single(), "ops")

            val afterReplace = withTimeout(5_000) {
                h.vm.state.first { st -> st.activeChannel?.id == "sess-new" && "t-rep" !in st.busyTaskIds }
            }
            assertEquals(listOf("ops"), afterReplace.selectedTask?.assignedBots)
            assertTrue(archivedPaths.any { "sess-old" in it })
            assertNull(h.dispatchStore.byGateway.value[gateway.gatewayUrl]?.get("sess-old"))
            assertEquals("t-rep", h.dispatchStore.byGateway.value[gateway.gatewayUrl]?.get("sess-new")?.taskId)

            h.vm.unlinkChannel(afterReplace.selectedTask!!, afterReplace.activeChannel!!)
            val afterUnlink = withTimeout(5_000) {
                h.vm.state.first { st -> st.selectedTask?.channels?.isEmpty() == true && "t-rep" !in st.busyTaskIds }
            }
            assertNull(afterUnlink.activeChannel)
            assertTrue(archivedPaths.any { "sess-new" in it })
            assertNull(h.dispatchStore.byGateway.value[gateway.gatewayUrl]?.get("sess-new"))
        } finally {
            h.vm.selectTask(null)
            h.connection.stop()
        }
    }
}
