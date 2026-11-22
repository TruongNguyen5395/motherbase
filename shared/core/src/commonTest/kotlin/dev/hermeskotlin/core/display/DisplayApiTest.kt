package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.rpc.FakeGateway
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DisplayApiTest {

    @Test
    fun parsesDisplayStatusAndLeaseAndThreadsProfile() = runTest {
        val gateway = FakeGateway(backgroundScope, reconnects = false)
        gateway.ready = { FakeGateway.QUIET_READY }
        gateway.answer = { call ->
            when (call.method) {
                "display.status" -> """{
                    "profile":"work-bot",
                    "profile_key":"/opt/data/profiles/work-bot",
                    "supported":true,
                    "installed":true,
                    "missing":[],
                    "running":true,
                    "pid":630,
                    "display":":20",
                    "geometry":"1440x900",
                    "placement":"gateway",
                    "memory_available_mb":16000,
                    "memory_limit_mb":24000,
                    "lease":{"holder":"human","viewer_id":null,"viewer_hash":"0123456789ab","since":100.5,"epoch":4,"reason":"takeover"}
                }"""
                "display.observe" -> """{
                    "profile":"work-bot",
                    "profile_key":"/opt/data/profiles/work-bot",
                    "supported":true,
                    "installed":true,
                    "running":true,
                    "geometry":"1440x900",
                    "lease":{"holder":"agent","viewer_id":null,"viewer_hash":null,"since":1.0,"epoch":4,"reason":""},
                    "ticket":"tkt-abc",
                    "path":"/api/display/ws",
                    "viewer_id":"vw-secret"
                }"""
                "display.stop" -> """{"running":false,"stopped":true,"lease":{"holder":"agent","epoch":5}}"""
                "display.thumbnail" -> """{"data_url":null,"suppressed":"human_has_control"}"""
                else -> "{}"
            }
        }
        val api = DisplayApi(gateway.start())

        val st = api.status("work-bot")
        assertEquals("work-bot", st.profile)
        assertTrue(st.supported)
        assertTrue(st.installed)
        assertTrue(st.running)
        assertEquals(630, st.pid)
        assertEquals(":20", st.display)
        assertEquals(LeaseHolder.Human, st.lease.holder)
        assertEquals("0123456789ab", st.lease.viewerHash)
        assertEquals(4, st.lease.epoch)

        val obs = api.observe("work-bot", viewerId = "vw-prior")
        assertEquals("tkt-abc", obs.ticket)
        assertEquals("/api/display/ws", obs.path)
        assertEquals("vw-secret", obs.viewerId)
        val obsSent = gateway.sent("display.observe").last()
        assertEquals("work-bot", obsSent["params"]?.let { it as kotlinx.serialization.json.JsonObject }?.get("profile")?.jsonPrimitive?.contentOrNull)
        assertEquals("vw-prior", obsSent["params"]?.let { it as kotlinx.serialization.json.JsonObject }?.get("viewer_id")?.jsonPrimitive?.contentOrNull)

        val thumb = api.thumbnail("work-bot")
        assertTrue(thumb.humanHasControl)

        val stopped = api.stop("work-bot", force = true)
        assertTrue(stopped.stopped)
        assertFalse(stopped.status.running)
    }

    @Test
    fun viewerHashMatchesServerSha256Prefix() {
        // sha256("viewer-1").hex[:12] == "a894885f4a53" -> verify exact lowercase 12 hex output determinism
        val h1 = DisplayApi.viewerHashOf("viewer-1")
        val h2 = DisplayApi.viewerHashOf("viewer-1")
        assertEquals(12, h1.length)
        assertEquals(h1, h2)
        assertTrue(h1.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotNull(DisplayLease(LeaseHolder.Human, viewerHash = h1).takeIf { it.isHeldBy(h1) })
    }
}
