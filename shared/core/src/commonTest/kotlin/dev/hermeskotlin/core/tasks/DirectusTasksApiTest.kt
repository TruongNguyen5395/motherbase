package dev.hermeskotlin.core.tasks

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DirectusTasksApiTest {

    private val gatewayUrl = GatewayUrl.parse("https://hermes.example.ts.net")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun readsAndSavesMaskedConfig() = runTest {
        val requests = mutableListOf<String>()
        val api = DirectusTasksApi(
            createHttpClient(
                MockEngine { req ->
                    requests += "${req.method.value} ${req.url.encodedPath}"
                    when (req.method) {
                        HttpMethod.Get -> respond(
                            """{"configured":false,"url":"","token_set":false,"token_preview":"","source":"none"}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        HttpMethod.Put -> {
                            val body = req.body.toByteArray().decodeToString()
                            assertTrue("https://directus.example.com" in body)
                            respond(
                                """{"configured":true,"url":"https://directus.example.com","token_set":true,"token_preview":"••••4321","source":"saved"}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        }
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                },
            ),
        )

        val initial = assertIs<ApiResult.Success<DirectusConfigStatus>>(api.getConfig(gatewayUrl)).value
        assertFalse(initial.configured)

        val saved = assertIs<ApiResult.Success<DirectusConfigStatus>>(
            api.saveConfig(gatewayUrl, DirectusConfigUpdate(url = "https://directus.example.com", token = "demo-token-4321")),
        ).value
        assertTrue(saved.configured)
        assertEquals("••••4321", saved.tokenPreview)
        assertEquals(
            listOf(
                "GET /api/plugins/directus-tasks/config",
                "PUT /api/plugins/directus-tasks/config",
            ),
            requests,
        )
    }

    @Test
    fun fetchesBoardWithProjectsAndStagedPlanContext() = runTest {
        val api = DirectusTasksApi(
            createHttpClient(
                MockEngine { req ->
                    assertEquals("/api/plugins/directus-tasks/board", req.url.encodedPath)
                    respond(
                        """{
                            "configured": true,
                            "projects": [
                                {"id": "p1", "name": "Project Alpha", "hermes_project_id": "alpha", "workspace_cwd": "/work/alpha", "default_bots": ["coder"]}
                            ],
                            "tasks": [
                                {
                                    "id": "t1",
                                    "title": "Design widget protocol",
                                    "status": "todo",
                                    "priority": "high",
                                    "work_logs": [{"logged_at": "2026-11-18T10:00:00Z", "content": "Drafted schema", "status": "todo"}],
                                    "context": {
                                        "id": "c1",
                                        "task_id": "t1",
                                        "project_id": "p1",
                                        "assigned_bots": ["coder", "architect"],
                                        "channels": [
                                            {"type": "room", "id": "task-t1", "label": "Room (@coder, @architect)"},
                                            {"type": "session", "id": "sess-1", "label": "Session (@coder)", "profile": "coder"}
                                        ],
                                        "agent_state": "needs_you",
                                        "staged_plan": {
                                            "summary": "Implement API layer",
                                            "params": [
                                                {"key": "bot", "label": "Runner", "options": ["@coder", "@architect"], "default": "@coder"}
                                            ],
                                            "actions": [{"label": "Approve", "instruction": "Start implementation"}]
                                        }
                                    }
                                }
                            ]
                        }""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                },
            ),
        )

        val board = assertIs<ApiResult.Success<DirectusBoardResponse>>(api.fetchBoard(gatewayUrl)).value
        assertTrue(board.configured)
        assertEquals("Project Alpha", board.projects.single().name)
        val task = board.tasks.single()
        assertEquals("needs_you", task.agentState)
        assertEquals("p1", task.projectId)
        assertEquals(2, task.channels.size)
        assertTrue(task.channels[0].isRoom)
        assertTrue(task.channels[1].isSession)
        assertEquals("@coder", task.stagedPlan?.params?.single()?.default)
    }

    @Test
    fun approvesTaskWithTweakDeltas() = runTest {
        var sentBody = ""
        val api = DirectusTasksApi(
            createHttpClient(
                MockEngine { req ->
                    assertEquals(HttpMethod.Post, req.method)
                    assertEquals("/api/plugins/directus-tasks/tasks/t1/approve", req.url.encodedPath)
                    sentBody = req.body.toByteArray().decodeToString()
                    respond(
                        """{"task":{"id":"t1","title":"Design widget protocol","status":"in_progress","context":{"id":"c1","task_id":"t1","project_id":"p1","agent_state":"running","assigned_bots":["architect"],"channels":[{"type":"session","id":"sess-2","label":"Session (@architect)","profile":"architect"}]}}}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                },
            ),
        )

        val updated = assertIs<ApiResult.Success<DirectusTask>>(
            api.approveTask(
                gatewayUrl,
                "t1",
                ApproveTaskRequest(
                    summary = "Implement API layer",
                    newStatus = DirectusTask.STATUS_IN_PROGRESS,
                    tweaks = listOf(
                        TweakDeltaInput(
                            paramKey = "bot",
                            paramLabel = "Runner",
                            agentProposed = "@coder",
                            userSelected = "@architect",
                        ),
                    ),
                    assignedBots = listOf("architect"),
                    channel = TaskChannel(TaskChannel.TYPE_SESSION, "sess-2", "Session (@architect)", "architect"),
                    workLogContent = "Assigned to @architect",
                ),
            ),
        ).value

        assertTrue("user_selected" in sentBody)
        assertEquals("in_progress", updated.status)
        assertEquals(TaskContext.STATE_RUNNING, updated.agentState)
    }

    @Test
    fun savesCoordinatorConfigAndEvaluatesCheckinWindowWithCooldownAndGrace() = runTest {
        var sentBody = ""
        val api = DirectusTasksApi(
            createHttpClient(
                MockEngine { req ->
                    assertEquals(HttpMethod.Put, req.method)
                    assertEquals("/api/plugins/directus-tasks/coordinator", req.url.encodedPath)
                    sentBody = req.body.toByteArray().decodeToString()
                    respond(
                        """{
                            "configured": true,
                            "url": "https://directus.example.com",
                            "token_set": true,
                            "token_preview": "••••4321",
                            "source": "saved",
                            "coordinator": {
                                "profile": "default",
                                "session_id": "coord-1",
                                "default_prompt": "Check energy, blockers, and due dates.",
                                "checkin_enabled": true,
                                "checkin_times": ["09:00", "16:00"],
                                "checkin_schedules": [
                                    {"id": "09:00", "time": "09:00", "window_minutes": 30, "instruction": "Morning priorities", "enabled": true},
                                    {"id": "16:00", "time": "16:00", "window_minutes": 45, "instruction": "Review blockers", "enabled": true}
                                ],
                                "window_minutes": 30,
                                "grace_minutes": 30,
                                "cooldown_minutes": 45,
                                "auto_open_on_launch": true,
                                "last_checkin_slot": "2026-11-20@09:00",
                                "last_interaction_at": 1000.0
                            }
                        }""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                },
            ),
        )

        val res = assertIs<ApiResult.Success<DirectusConfigStatus>>(
            api.saveCoordinatorConfig(
                gatewayUrl,
                DispatchCoordinatorUpdate(
                    profile = "default",
                    sessionId = "coord-1",
                    defaultPrompt = "Check energy, blockers, and due dates.",
                    checkinTimes = listOf("09:00", "16:00"),
                    lastCheckinSlot = "2026-11-20@09:00",
                ),
            ),
        ).value
        assertTrue("coord-1" in sentBody)
        val coord = res.coordinator
        assertEquals("coord-1", coord.sessionId)
        assertEquals(2, coord.checkinSchedules.size)

        // Morning slot (09:00) was already checked in today -> returns null at 09:15 (minute 555).
        assertEquals(null, coord.resolveDueCheckinSlot("2026-11-20", 9 * 60 + 15, nowEpochSeconds = 10_000L))

        // Afternoon slot (16:00, symmetric ±45m window -> 15:15..16:45) triggers at 15:40 (minute 940) with instruction.
        val due = coord.resolveDueCheckin("2026-11-20", 15 * 60 + 40, nowEpochSeconds = 10_000L)
        assertEquals("2026-11-20@16:00", due?.slotKey)
        assertEquals("Review blockers", due?.schedule?.instruction)

        // Outside the ±45m window (17:20 -> minute 1040) does NOT auto-trigger.
        assertEquals(
            null,
            coord.resolveDueCheckinSlot("2026-11-20", 17 * 60 + 20, nowEpochSeconds = 10_000L),
        )

        // Cooldown gate: if user already chatted 10 minutes ago (now = 1600, lastInteractionAt = 1000), skip auto prompt.
        assertEquals(
            null,
            coord.resolveDueCheckinSlot("2026-11-20", 16 * 60, nowEpochSeconds = 1_600L),
        )

        // Manual check-in at 15:10 (50m before 16:00) claims the upcoming 16:00 slot.
        assertEquals("2026-11-20@16:00", coord.activeOrUpcomingCheckinSlotId("2026-11-20", 15 * 60 + 10))
    }
}
