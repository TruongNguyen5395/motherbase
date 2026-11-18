package dev.hermeskotlin.core.tasks

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DispatchSessionsStoreTest {

    private val gateway = GatewayUrl("https://hermes.example.ts.net")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun syncBoardPersistsAndWarmsAcrossStoreInstances() = runTest {
        val kv = InMemoryKeyValueStore()
        var now = 1_000L
        val writer = DispatchSessionsStore(kv) { now }

        val task = DirectusTask(
            id = "t1",
            title = "Migrate Google email",
            status = DirectusTask.STATUS_IN_PROGRESS,
            context = TaskContext(
                taskId = "t1",
                channels = listOf(
                    TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-1", label = "Session (@ops)", profile = "ops", managed = true),
                    TaskChannel(type = TaskChannel.TYPE_ROOM, id = "room-1", label = "Ops room"),
                ),
            ),
        )
        writer.syncBoard(gateway, listOf(task), includeDone = false)

        val entries = writer.byGateway.value[gateway].orEmpty()
        assertEquals(setOf("sess-1"), entries.keys)
        assertEquals("Migrate Google email", entries["sess-1"]?.taskTitle)
        assertTrue(entries["sess-1"]?.managed == true)

        // Fresh cold-start instance hydrates synchronously from disk without network calls.
        val reader = DispatchSessionsStore(kv) { now }
        reader.warm(gateway)
        assertEquals(entries, reader.byGateway.value[gateway].orEmpty())
    }

    @Test
    fun refreshIfNeededRespectsTtlForceAndMissingPlugin() = runTest {
        var now = 10_000L
        var boardCalls = 0
        var missing = false
        val client = createHttpClient(
            MockEngine { req ->
                if (req.url.encodedPath == "/api/plugins/directus-tasks/board") {
                    boardCalls++
                    if (missing) {
                        respond("""{"detail":"Not Found"}""", HttpStatusCode.NotFound, jsonHeaders)
                    } else {
                        respond(
                            """
                            {
                              "configured": true,
                              "projects": [],
                              "tasks": [
                                {
                                  "id": "t1",
                                  "title": "Monitor cluster",
                                  "status": "in_progress",
                                  "context": {
                                    "task_id": "t1",
                                    "channels": [{"type":"session","id":"s-nc","label":"Session (@Hermes)","managed":true}]
                                  }
                                }
                              ]
                            }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    }
                } else {
                    respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                }
            },
        )
        val api = DirectusTasksApi(client)
        val store = DispatchSessionsStore(InMemoryKeyValueStore()) { now }

        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(1, boardCalls)
        assertEquals("Monitor cluster", store.byGateway.value[gateway]?.get("s-nc")?.taskTitle)

        // Within 60s TTL: second non-forced call is deduplicated.
        now += 15_000L
        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(1, boardCalls)

        // Forced refresh hits network even within TTL.
        store.refreshIfNeeded(gateway, api, force = true)
        assertEquals(2, boardCalls)

        // Once 404 is returned, non-forced calls stop probing missing plugin.
        missing = true
        now += 120_000L
        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(3, boardCalls)
        store.refreshIfNeeded(gateway, api, force = false)
        assertEquals(3, boardCalls)
    }

    @Test
    fun activeTaskTakesPriorityOverDoneTaskWhenSharingSession() = runTest {
        val store = DispatchSessionsStore(InMemoryKeyValueStore()) { 1_000L }
        val doneTask = DirectusTask(
            id = "t-old",
            title = "Old task",
            status = DirectusTask.STATUS_DONE,
            context = TaskContext(
                taskId = "t-old",
                channels = listOf(TaskChannel(TaskChannel.TYPE_SESSION, "sess-shared", "Session (@ops)", managed = true)),
            ),
        )
        val activeTask = DirectusTask(
            id = "t-new",
            title = "Active follow-up",
            status = DirectusTask.STATUS_IN_PROGRESS,
            context = TaskContext(
                taskId = "t-new",
                channels = listOf(TaskChannel(TaskChannel.TYPE_SESSION, "sess-shared", "Session (@ops)", managed = true)),
            ),
        )

        store.syncBoard(gateway, listOf(activeTask, doneTask), includeDone = true)
        val entry = store.byGateway.value[gateway]?.get("sess-shared")
        assertEquals("t-new", entry?.taskId)
        assertEquals("Active follow-up", entry?.taskTitle)
        assertEquals(DirectusTask.STATUS_IN_PROGRESS, entry?.taskStatus)
    }

    @Test
    fun autoManagedFlagAndLegacyPrefixLeaveManualChatsUntouched() {
        val managedExplicit = TaskChannel(TaskChannel.TYPE_SESSION, "s1", label = "Custom label", managed = true)
        val legacyCreated = TaskChannel(TaskChannel.TYPE_SESSION, "s2", label = "Session (@coder)", managed = false)
        val manualChat = TaskChannel(TaskChannel.TYPE_SESSION, "s3", label = "My normal chat", managed = false)
        val room = TaskChannel(TaskChannel.TYPE_ROOM, "r1", label = "Session (@ops)", managed = true)

        assertTrue(managedExplicit.isAutoManagedSession)
        assertTrue(legacyCreated.isAutoManagedSession)
        assertFalse(manualChat.isAutoManagedSession)
        assertFalse(room.isAutoManagedSession)
    }
}
