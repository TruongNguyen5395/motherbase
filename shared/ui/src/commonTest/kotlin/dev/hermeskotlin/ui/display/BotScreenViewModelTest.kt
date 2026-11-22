package dev.hermeskotlin.ui.display

import dev.hermeskotlin.core.auth.AuthApi
import dev.hermeskotlin.core.auth.PersistentCookiesStorage
import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.connection.GatewayConnection
import dev.hermeskotlin.core.display.DisplayApi
import dev.hermeskotlin.core.display.DisplayRelay
import dev.hermeskotlin.core.display.DisplayStore
import dev.hermeskotlin.core.display.LeaseHolder
import dev.hermeskotlin.core.gateway.GatewayRepository
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.createHttpClient
import dev.hermeskotlin.core.rpc.RpcTransport
import dev.hermeskotlin.core.storage.InMemoryKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
class BotScreenViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRpcTransport(
        private val onCall: (method: String, params: JsonObject) -> String,
    ) : RpcTransport {
        val sentCalls = mutableListOf<Pair<String, JsonObject>>()
        val inbound = Channel<String>(Channel.UNLIMITED).apply {
            trySend("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
        }

        override val incoming: Flow<String> = inbound.receiveAsFlow()

        override suspend fun send(text: String) {
            val obj = Json.parseToJsonElement(text).jsonObject
            val id = obj["id"] ?: return
            val method = obj["method"]?.jsonPrimitive?.contentOrNull ?: return
            val params = obj["params"] as? JsonObject ?: JsonObject(emptyMap())
            sentCalls += method to params
            val result = onCall(method, params)
            inbound.trySend("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
        }

        override suspend fun close(code: Short, reason: String) {
            inbound.close()
        }

        fun pushEvent(type: String, sessionId: String? = null, payload: String = "{}") {
            val sid = sessionId?.let { """"session_id":"$it",""" }.orEmpty()
            inbound.trySend("""{"jsonrpc":"2.0","method":"event","params":{"type":"$type",$sid"payload":$payload}}""")
        }
    }

    private suspend fun TestScope.harness(
        now: () -> Long = { testScheduler.currentTime },
        onCall: (method: String, params: JsonObject) -> String,
    ): Triple<BotScreenViewModel, FakeRpcTransport, DisplayRelay> {
        val transport = FakeRpcTransport(onCall)
        val client = createHttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { req ->
                        if (req.url.encodedPath == "/api/auth/ws-ticket") {
                            respond("""{"ticket":"ws-tkt","ttl_seconds":30}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        } else {
                            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                    }
                },
            ),
        )
        val store = InMemoryKeyValueStore()
        val conn = GatewayConnection(AuthApi(client, PersistentCookiesStorage(store)), { _, _ -> transport }, backgroundScope)
        conn.start(GatewayUrl.parse("https://hermes.example.com"))
        conn.state.first { it is ConnectionState.Connected }

        val displayStore = DisplayStore(DisplayApi(conn), conn, backgroundScope)
        val relay = DisplayRelay(client, CoroutineScope(backgroundScope.coroutineContext))
        val vm = BotScreenViewModel(
            displayStore = displayStore,
            displayRelay = relay,
            gateways = GatewayRepository(store),
            loadAssets = { "<html></html>".encodeToByteArray() to "export default 1;".encodeToByteArray() },
            clockMs = now,
        )
        advanceUntilIdle()
        return Triple(vm, transport, relay)
    }

    @Test
    fun openTakeOverCloseConfirmationAndOtpBackgroundPreserveLease() = runTest {
        val viewerId = "vw-android-1"
        val viewerHash = DisplayApi.viewerHashOf(viewerId)
        val gwUrl = GatewayUrl.parse("https://hermes.example.com")
        val (vm, transport, _) = harness { method, _ ->
            when (method) {
                "display.status" -> """{"profile":"default","profile_key":"/opt/data","supported":true,"installed":true,"running":true,"geometry":"1440x900","lease":{"holder":"agent","epoch":1}}"""
                "display.observe" -> """{"profile":"default","profile_key":"/opt/data","supported":true,"installed":true,"running":true,"lease":{"holder":"agent","epoch":1},"ticket":"tkt-a","path":"/api/display/ws","viewer_id":"$viewerId"}"""
                "display.lease.acquire" -> """{"lease":{"holder":"human","viewer_hash":"$viewerHash","epoch":2,"reason":"human_takeover"}}"""
                "display.lease.release" -> """{"lease":{"holder":"agent","viewer_hash":null,"epoch":3}}"""
                else -> "{}"
            }
        }

        vm.openScreen("default", gwUrl)
        advanceUntilIdle()
        assertTrue(vm.ui.value.screenOpen)
        val initialBridge = vm.ui.value.bridge
        assertNotNull(initialBridge)

        // Take over
        vm.takeOver()
        advanceUntilIdle()
        assertTrue(vm.stateOf("default").iHoldLease)

        // Simulate Android switching to OTP app (ON_STOP -> ON_START):
        // bridge tears down preserving lease, then foreground reconnects with SAME viewer_id
        vm.onAppBackgrounded()
        assertNull(vm.ui.value.bridge)
        assertTrue(vm.ui.value.screenOpen)
        assertTrue(vm.stateOf("default").iHoldLease)

        vm.onAppForegrounded()
        advanceUntilIdle()
        assertNotNull(vm.ui.value.bridge)
        val obsParams = transport.sentCalls.last { it.first == "display.observe" }.second
        assertEquals(viewerId, obsParams["viewer_id"]?.jsonPrimitive?.contentOrNull)

        // Request close while holding control prompts instead of dropping immediately
        vm.requestClose()
        assertTrue(vm.ui.value.showCloseHoldConfirm)

        // Minimize preserves lease
        vm.closeScreen(handBackFirst = false)
        advanceUntilIdle()
        assertFalse(vm.ui.value.screenOpen)
        assertTrue(vm.stateOf("default").iHoldLease)
        assertEquals(0, transport.sentCalls.count { it.first == "display.lease.release" })

        // Reopen then hand back & close sends release
        vm.openScreen("default", gwUrl)
        advanceUntilIdle()
        vm.closeScreen(handBackFirst = true)
        advanceUntilIdle()
        assertEquals(1, transport.sentCalls.count { it.first == "display.lease.release" })
        assertEquals(LeaseHolder.Agent, vm.stateOf("default").status?.lease?.holder)
    }

    @Test
    fun autoRaiseRespectsToggleSessionCooldownAndManualDismiss() = runTest {
        var fakeNow = 100_000L
        val gwUrl = GatewayUrl.parse("https://hermes.example.com")
        val (vm, transport, _) = harness(now = { fakeNow }) { method, _ ->
            when (method) {
                "display.status" -> """{"profile":"bot1","profile_key":"/p/bot1","supported":true,"installed":true,"running":true,"lease":{"holder":"agent","epoch":1}}"""
                "display.observe" -> """{"profile":"bot1","profile_key":"/p/bot1","supported":true,"installed":true,"running":true,"lease":{"holder":"agent","epoch":1},"ticket":"tkt-b","path":"/api/display/ws","viewer_id":"vw-2"}"""
                else -> "{}"
            }
        }

        vm.bindChatContext("bot1", gwUrl, sessionId = "s-1", autoOpenEnabled = true)
        advanceUntilIdle()
        assertFalse(vm.ui.value.screenOpen)

        // Matching session tool.start raises screen
        transport.pushEvent("tool.start", sessionId = "s-1", payload = """{"tool":"computer_use_click"}""")
        advanceUntilIdle()
        assertTrue(vm.ui.value.screenOpen)

        // Manual close sets manualDismissed -> suppresses next tool.start in same chat
        vm.closeScreen(handBackFirst = false)
        advanceUntilIdle()
        fakeNow += 60_000L
        transport.pushEvent("tool.start", sessionId = "s-1", payload = """{"tool":"browser_navigate"}""")
        advanceUntilIdle()
        assertFalse(vm.ui.value.screenOpen)
    }

    @Test
    fun jsEscapingHandlesSpecialCharactersInPasswordsSafely() {
        val weird = "p@ss'\"\\\${alert(1)}\nline2"
        val script = ScreenScriptBuilder.action(BotScreenAction.TypeText(weird))
        assertTrue(script.startsWith("window.mbScreenApi&&window.mbScreenApi.typeText(\""))
        assertTrue(script.contains("\\n"))
        assertTrue(script.contains("\\\""))
    }
}
