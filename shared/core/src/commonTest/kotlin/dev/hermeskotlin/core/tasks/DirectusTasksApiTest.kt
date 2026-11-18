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
}
