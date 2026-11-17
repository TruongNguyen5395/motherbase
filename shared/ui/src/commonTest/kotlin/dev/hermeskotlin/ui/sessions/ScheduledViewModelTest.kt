package dev.hermeskotlin.ui.sessions

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import dev.hermeskotlin.core.auth.AuthApi
import dev.hermeskotlin.core.auth.PersistentCookiesStorage
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.cron.CronApi
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.core.inbox.InboxApi
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.push.PushApi
import dev.hermeskotlin.core.push.PushGateway
import dev.hermeskotlin.core.push.PushKeys
import dev.hermeskotlin.core.push.PushRegistration
import dev.hermeskotlin.core.rpc.RpcTransport
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import dev.hermeskotlin.ui.resources.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val gateway = SavedGateway("https://hermes.example.ts.net")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private var jobsBody = """[{"id":"a1","name":"Briefing","prompt":"Brief me","schedule":{"kind":"cron","expr":"0 9 * * *"}}]"""
    private val created = mutableListOf<String>()
    /** Holds a create until completed, so a test can act while the save is in flight. */
    private var createGate: CompletableDeferred<Unit>? = null
    private var createStatus = HttpStatusCode.OK
    private var createdQuery: String? = null
    private var createdBody: String? = null

    private fun viewModel(): ScheduledViewModel {
        // On the test dispatcher, so no request is still finishing on another thread when a test ends.
        val config = MockEngineConfig()
        config.dispatcher = dispatcher
        config.addHandler { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/cron/jobs" -> {
                    createGate?.await()
                    created += request.url.encodedPath
                    createdQuery = request.url.encodedQuery
                    createdBody = request.body.toByteArray().decodeToString()
                    if (createStatus == HttpStatusCode.OK) {
                        respond("""{"id":"n1","name":"New","prompt":"Do it","schedule":{"kind":"cron","expr":"0 9 * * *"}}""", createStatus, json)
                    } else {
                        respond("""{"detail":"Gateway is busy"}""", createStatus, json)
                    }
                }
                request.url.encodedPath == "/api/cron/jobs" -> respond(jobsBody, HttpStatusCode.OK, json)
                request.url.encodedPath.endsWith("/runs") -> respond("""{"runs":[]}""", HttpStatusCode.OK, json)
                else -> respond("""{"targets":[]}""", HttpStatusCode.OK, json)
            }
        }
        val cookies = PersistentCookiesStorage(InMemoryKeyValueStore())
        val client = createHttpClient(MockEngine(config), cookies)
        val connection = GatewayConnection(AuthApi(client, cookies), { _, _ -> error("not connecting in tests") }, CoroutineScope(dispatcher))
        return ScheduledViewModel(CronApi(client), connection)
    }

    private suspend fun ScheduledViewModel.awaitLoaded() = state.first { !it.loading }

    private fun ScheduledViewModel.fillNewJob() {
        newJob()
        prompt.setTextAndPlaceCursorAtEnd("Do it")
        schedule.setTextAndPlaceCursorAtEnd("0 9 * * *")
    }

    private val routinesBody = """[
        {"id":"r1","name":"[bot:scribe] Morning brief","prompt":"Brief me","profile":"scribe","schedule":{"kind":"cron","expr":"0 9 * * *"}},
        {"id":"r2","name":"[bot:scribe] Old style","prompt":"x","profile":"default","schedule":{"kind":"cron","expr":"0 9 * * *"}},
        {"id":"r3","name":"Somebody else's","prompt":"y","profile":"researcher","schedule":{"kind":"cron","expr":"0 9 * * *"}},
        {"id":"a1","name":"Briefing","prompt":"Brief me","profile":"default","schedule":{"kind":"cron","expr":"0 9 * * *"}}
    ]"""

    @Test
    fun aBotsPageListsOnlyItsRoutines() = runTest(dispatcher) {
        jobsBody = routinesBody
        val vm = viewModel()
        vm.bind(gateway, RoutineOwner("scribe", "Scribe"))

        // Its own store's, and one an older Desktop kept in the launch profile's store, tagged for it.
        assertEquals(listOf("r1", "r2"), vm.awaitLoaded().jobs.map { it.id })
        assertEquals("Morning brief", vm.state.value.jobs.first().displayName)
    }

    @Test
    fun aNewRoutineGoesInTheBotsStoreTaggedAndReportingToItsChat() = runTest(dispatcher) {
        jobsBody = routinesBody
        val vm = viewModel()
        vm.bind(gateway, RoutineOwner("scribe", "Scribe"))
        vm.awaitLoaded()
        vm.fillNewJob()
        assertEquals("bot-chat", vm.state.value.editor?.deliver)
        vm.name.setTextAndPlaceCursorAtEnd("Evening wrap")

        vm.saveJob()
        val saved = vm.state.first { it.editor == null }

        assertEquals("profile=scribe", createdQuery)
        assertTrue(createdBody!!.contains(""""name":"[bot:scribe] Evening wrap""""), createdBody)
        assertTrue(createdBody!!.contains(""""deliver":"bot-chat""""), createdBody)
        // The gateway's answer carries no profile; the page keeps it, so the routine stays listed.
        assertEquals("scribe", saved.jobs.first { it.id == "n1" }.profile)
    }

    @Test
    fun aRoutineNeedsAName() = runTest(dispatcher) {
        val vm = viewModel()
        vm.bind(gateway, RoutineOwner("scribe", "Scribe"))
        vm.awaitLoaded()
        vm.fillNewJob()

        vm.saveJob()

        assertEquals(said(Res.string.sessions_job_needs_name), vm.state.value.editor?.error)
        assertTrue(created.isEmpty())
    }

    @Test
    fun editingARoutineShowsItsTitleAndKeepsTheTag() = runTest(dispatcher) {
        jobsBody = routinesBody
        val vm = viewModel()
        vm.bind(gateway, RoutineOwner("scribe", "Scribe"))
        vm.awaitLoaded()
        vm.openJob("r1")

        vm.editJob()

        assertEquals("Morning brief", vm.name.text.toString())
    }

    @Test
    fun savingAJobDeletedElsewhereDoesNotCreateItAgain() = runTest(dispatcher) {
        val vm = viewModel()
        vm.bind(gateway)
        vm.awaitLoaded()
        vm.openJob("a1")
        vm.editJob()

        // Another client removes it, and the cron.changed refetch drops it from the list.
        jobsBody = "[]"
        vm.refresh()
        vm.state.first { it.jobs.isEmpty() }

        vm.saveJob()

        assertTrue(created.isEmpty())
        assertEquals(said(Res.string.sessions_job_gone), vm.state.value.editor?.error)
    }

    @Test
    fun aSaveThatFinishesAfterTheFormClosedLeavesThePageAlone() = runTest(dispatcher) {
        val vm = viewModel()
        vm.bind(gateway)
        vm.awaitLoaded()
        val gate = CompletableDeferred<Unit>().also { createGate = it }
        vm.fillNewJob()
        vm.saveJob()

        // Back out of the form and open another job while the create is still in flight.
        vm.closeEditor()
        vm.openJob("a1")
        gate.complete(Unit)

        val state = vm.state.first { it.message != null }
        assertEquals("a1", state.openJobId)
        assertNull(state.editor)
        assertEquals(said(Res.string.sessions_job_scheduled), state.message)
        assertTrue(state.jobs.any { it.id == "n1" })
    }

    @Test
    fun aSaveThatFailsAfterTheFormClosedSaysSo() = runTest(dispatcher) {
        createStatus = HttpStatusCode.ServiceUnavailable
        val vm = viewModel()
        vm.bind(gateway)
        vm.awaitLoaded()
        val gate = CompletableDeferred<Unit>().also { createGate = it }
        vm.fillNewJob()
        vm.saveJob()

        vm.closeEditor()
        gate.complete(Unit)

        val message = assertIs<Said.Line>(vm.state.first { it.message != null }.message)
        assertEquals(Res.string.sessions_job_save_failed, message.res)
        assertTrue("Gateway is busy" in message.detail.orEmpty(), message.toString())
    }

    @Test
    fun profileFilterTagsRunsAndCronOutputOpensFullOrFallbackViewer() = runTest(dispatcher) {
        val jobs = """[
            {"id":"j-def","name":"Backup","profile":"default","schedule":{"kind":"cron","expr":"0 2 * * *"}},
            {"id":"j-3tn","name":"PM Report","profile":"3tnsolution","schedule":{"kind":"cron","expr":"30 15 * * *"}}
        ]"""
        val runsJson = """{"runs":[
            {"id":"local-3tn-1","source":"cron","title":"PM Run","started_at":20.0,"message_count":10},
            {"id":"cron_output:j-3tn:2026-11-17_05-45-23","source":"cron_output","title":"COMPLETED · # Cron Job: Daily **Job ID:** j-3tn **Run Time:** 2026-11-17 05:45:23 **Mode:** no_agent (script)","preview":"# Cron Job: Daily **Job ID:** j-3tn **Run Time:** 2026-11-17 05:45:23 **Mode:** no_agent (script)","started_at":10.0,"message_count":0},
            {"id":"cron_output:j-3tn:2026-11-17_02-00-00","source":"cron_output","title":"COMPLETED · # Cron Job: Silent **Status:** silent (empty output)","preview":"# Cron Job: Silent **Status:** silent (empty output)","started_at":5.0,"message_count":0}
        ],"limit":50}"""

        val config = MockEngineConfig().apply {
            this.dispatcher = this@ScheduledViewModelTest.dispatcher
            addHandler { req ->
                when {
                    req.url.encodedPath == "/api/auth/ws-ticket" -> respond("""{"ticket":"T","ttl_seconds":30}""", HttpStatusCode.OK, json)
                    req.url.encodedPath == "/api/cron/jobs" -> respond(jobs, HttpStatusCode.OK, json)
                    req.url.encodedPath.endsWith("/runs") -> respond(runsJson, HttpStatusCode.OK, json)
                    else -> respond("""{"targets":[]}""", HttpStatusCode.OK, json)
                }
            }
        }
        val cookies = PersistentCookiesStorage(InMemoryKeyValueStore())
        val client = createHttpClient(MockEngine(config), cookies)
        val socket = object : RpcTransport {
            private val inbound = Channel<String>(Channel.UNLIMITED).apply {
                trySend("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
            }
            override val incoming: Flow<String> = inbound.receiveAsFlow()
            override suspend fun send(text: String) {
                val msg = Json.parseToJsonElement(text).jsonObject
                val id = msg["id"] ?: return
                val arg = msg["params"]?.jsonObject?.get("arg")?.jsonPrimitive?.content.orEmpty()
                val reply = if ("2026-11-17_05-45-23" in arg) {
                    """{"ok":true,"run":{"bot":"3tnsolution","job":"j-3tn","run":"2026-11-17_05-45-23","label":"PM Report","status":"ok","at":10,"text":"# Full Report\n\nAll tasks synced."}}"""
                } else {
                    """{"ok":false,"error":"no such run"}"""
                }
                backgroundScope.launch {
                    inbound.send("""{"jsonrpc":"2.0","id":$id,"result":{"type":"plugin","output":${JsonPrimitive(reply)}}}""")
                }
            }
            override suspend fun close(code: Short, reason: String) { inbound.close() }
        }
        val connection = GatewayConnection(AuthApi(client, cookies), { _, _ -> socket }, backgroundScope)
        connection.start(gateway.gatewayUrl)
        connection.state.first { it is ConnectionState.Connected }
        val keys = object : PushKeys {
            override val deviceId = "dev-1"
            override fun registration(name: String): PushRegistration = error("unused")
            override fun pinned(gatewayUrl: String): PushGateway? = null
            override fun accepts(keys: PushGateway) = false
            override fun pin(gatewayUrl: String, keys: PushGateway) = Unit
            override fun retire(gatewayUrl: String) = Unit
            override fun retired(gatewayUrl: String) = emptySet<String>()
            override fun forgetRetired(gatewayUrl: String, deviceId: String) = Unit
            override fun pinnedGatewayUrl(): String? = null
        }
        val vm = ScheduledViewModel(CronApi(client), connection, InboxApi(PushApi(connection), keys))
        vm.bind(gateway)
        val loaded = vm.awaitLoaded()

        assertEquals(listOf("default", "3tnsolution"), loaded.availableProfiles)
        vm.setProfileFilter("3tnsolution")
        assertEquals(listOf("j-3tn"), vm.state.value.shownJobs.map { it.id })

        vm.openJob("j-3tn")
        val withRuns = vm.state.first { it.runs.size == 3 }
        assertEquals("3tnsolution", withRuns.runs[0].profile)
        assertTrue(withRuns.runs[1].isCronOutput)

        // Full non-silent report loads via InboxApi.read
        vm.openRunOutput(withRuns.openJob!!, withRuns.runs[1])
        val full = vm.state.first { it.openOutput?.loading == false }.openOutput!!
        assertEquals("COMPLETED", full.status)
        assertTrue(full.text.contains("All tasks synced."))

        // Silent run falls back cleanly with normalized markdown lines
        vm.openRunOutput(withRuns.openJob!!, withRuns.runs[2])
        val silent = vm.state.first { it.openOutput?.run?.id == withRuns.runs[2].id && it.openOutput?.loading == false }.openOutput!!
        assertTrue(silent.text.contains("\n**Status:** silent"))
        assertFalse(silent.loading)

        vm.closeRunOutput()
        assertNull(vm.state.value.openOutput)
    }
}
