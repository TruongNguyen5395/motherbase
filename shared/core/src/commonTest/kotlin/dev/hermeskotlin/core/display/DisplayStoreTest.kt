package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.connection.ConnectionState
import dev.hermeskotlin.core.rpc.FakeGateway
import dev.hermeskotlin.core.rpc.event
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DisplayStoreTest {

    @Test
    fun enforcesLeaseEpochMonotonicity() = runTest {
        val gateway = FakeGateway(backgroundScope, reconnects = false)
        gateway.ready = { FakeGateway.QUIET_READY }
        val viewerId = "vw-local"
        val viewerHash = DisplayApi.viewerHashOf(viewerId)
        gateway.answer = { call ->
            when (call.method) {
                "display.observe" -> """{
                    "profile":"default",
                    "profile_key":"/opt/data",
                    "supported":true,
                    "installed":true,
                    "running":true,
                    "geometry":"1440x900",
                    "lease":{"holder":"agent","viewer_hash":null,"epoch":2},
                    "ticket":"tk1",
                    "path":"/api/display/ws",
                    "viewer_id":"$viewerId"
                }"""
                "display.lease.acquire" -> """{"lease":{"holder":"human","viewer_hash":"$viewerHash","epoch":3,"reason":"human_takeover"}}"""
                else -> "{}"
            }
        }
        val conn = gateway.start()
        val store = DisplayStore(DisplayApi(conn), conn, backgroundScope)
        runCurrent()

        assertTrue(store.observeScreen("default").isSuccess)
        assertTrue(store.acquireLease("default").isSuccess)
        val held = store.stateOf("default")
        assertTrue(held.iHoldLease)
        assertEquals(3, held.status?.lease?.epoch)

        // Older epoch lease broadcast must be ignored
        gateway.socket.push(event("display.lease", payload = """{"profile_key":"/opt/data","lease":{"holder":"agent","viewer_hash":null,"epoch":2}}"""))
        runCurrent()
        assertTrue(store.stateOf("default").iHoldLease)
        assertEquals(3, store.stateOf("default").status?.lease?.epoch)

        // Newer epoch lease broadcast applies
        gateway.socket.push(event("display.lease", payload = """{"profile_key":"/opt/data","lease":{"holder":"agent","viewer_hash":null,"epoch":4}}"""))
        store.profiles.first { it["default"]?.status?.lease?.epoch == 4 }
        assertFalse(store.stateOf("default").iHoldLease)
        assertEquals(LeaseHolder.Agent, store.stateOf("default").status?.lease?.holder)
    }

    @Test
    fun tracksInstallLogsAndRespondsToSudo() = runTest {
        val gateway = FakeGateway(backgroundScope, reconnects = false)
        gateway.ready = { FakeGateway.QUIET_READY }
        gateway.answer = { call ->
            when (call.method) {
                "display.install" -> """{"started":true,"command":"sudo apt install -y tigervnc","profile_key":"/opt/data"}"""
                else -> "{}"
            }
        }
        val conn = gateway.start()
        val store = DisplayStore(DisplayApi(conn), conn, backgroundScope)
        runCurrent()

        assertTrue(store.startInstall("default").isSuccess)
        gateway.socket.push(event("display.install.log", payload = """{"profile_key":"/opt/data","line":"Reading package lists..."}"""))
        gateway.socket.push("""{"jsonrpc":"2.0","id":"sudo-1","method":"display.install.sudo","params":{"session_id":"","profile_key":"/opt/data"}}""")
        store.profiles.first { it["default"]?.installSudoRequestId == "sudo-1" && it["default"]?.installLogs?.isNotEmpty() == true }
        assertEquals(listOf("Reading package lists..."), store.stateOf("default").installLogs)
        assertEquals("sudo-1", store.stateOf("default").installSudoRequestId)

        store.respondInstallSudo("default", "my-sudo-pw")
        runCurrent()
        assertNull(store.stateOf("default").installSudoRequestId)
        val sudoReply = gateway.socket.sent.value.last().toString()
        assertTrue(sudoReply.contains("my-sudo-pw"))
    }

    @Test
    fun emitsAutoRaiseForComputerUseAndBrowserTools() = runTest {
        val gateway = FakeGateway(backgroundScope, reconnects = false)
        gateway.ready = { FakeGateway.QUIET_READY }
        val conn = gateway.start()
        val store = DisplayStore(DisplayApi(conn), conn, backgroundScope)
        runCurrent()

        val raised = async(start = CoroutineStart.UNDISPATCHED) { store.autoRaise.first() }
        gateway.socket.push(event("tool.start", sessionId = "s-1", payload = """{"tool":"browser_navigate"}"""))
        runCurrent()
        val ev = raised.await()
        assertEquals("s-1", ev.sessionId)
        assertEquals("browser_navigate", ev.toolName)
    }

    @Test
    fun marksUnavailableOnMethodNotFoundAndResetsOnReconnect() = runTest {
        val gateway = FakeGateway(backgroundScope, reconnects = true)
        gateway.ready = { FakeGateway.QUIET_READY }
        gateway.answer = { "error:-32601" }
        val conn = gateway.start()
        val store = DisplayStore(DisplayApi(conn), conn, backgroundScope)
        runCurrent()

        val res = store.refreshStatus("default")
        assertTrue(res.isFailure)
        assertTrue(store.stateOf("default").unavailable)

        // Gateway upgrades and reconnects -> unavailable resets
        gateway.answer = { """{"profile":"default","profile_key":"/opt/data","supported":true,"installed":true,"running":false}""" }
        val before = (conn.state.value as ConnectionState.Connected).client
        gateway.socket.serverClose(1006, "restart")
        advanceTimeBy(2_000)
        conn.state.first { it is ConnectionState.Connected && it.client !== before }
        runCurrent()
        assertFalse(store.stateOf("default").unavailable)
        assertTrue(store.refreshStatus("default").isSuccess)
    }
}
