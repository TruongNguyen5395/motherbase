package dev.hermeskotlin.core.tasks

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.ApiResult
import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.network.apiCall
import dev.hermeskotlin.core.network.map
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ProjectEnvelope(val project: DirectusProject)

@Serializable
private data class ProjectsEnvelope(val projects: List<DirectusProject> = emptyList())

@Serializable
private data class TaskEnvelope(val task: DirectusTask)

@Serializable
private data class DeltasEnvelope(val deltas: List<TweakDeltaRecord> = emptyList())

/**
 * Gateway-proxied Directus Tasks backend (`/api/plugins/directus-tasks/...`), reusing the signed-in
 * MotherBase session so Directus credentials live once on the gateway instead of on every client device.
 */
class DirectusTasksApi(private val client: HttpClient) {

    suspend fun getConfig(url: GatewayUrl): ApiResult<DirectusConfigStatus> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/config"))
    }.map { lenientJson.decodeFromString<DirectusConfigStatus>(it.bodyAsText()) }

    suspend fun saveConfig(url: GatewayUrl, update: DirectusConfigUpdate): ApiResult<DirectusConfigStatus> = apiCall {
        client.put(url.resolve("api/plugins/$PLUGIN/config")) {
            contentType(ContentType.Application.Json)
            setBody(update)
        }
    }.map { lenientJson.decodeFromString<DirectusConfigStatus>(it.bodyAsText()) }

    suspend fun testConnection(
        url: GatewayUrl,
        directusUrl: String? = null,
        token: String? = null,
    ): ApiResult<DirectusConnectionTestResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/config/test")) {
            if (directusUrl != null || token != null) {
                contentType(ContentType.Application.Json)
                setBody(DirectusConfigUpdate(url = directusUrl.orEmpty(), token = token, verify = false))
            }
        }
    }.map { lenientJson.decodeFromString<DirectusConnectionTestResult>(it.bodyAsText()) }

    suspend fun fetchBoard(url: GatewayUrl, includeDone: Boolean = false): ApiResult<DirectusBoardResponse> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/board")) {
            if (includeDone) parameter("include_done", true)
        }
    }.map { lenientJson.decodeFromString<DirectusBoardResponse>(it.bodyAsText()) }

    suspend fun listProjects(url: GatewayUrl, includeArchived: Boolean = false): ApiResult<List<DirectusProject>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/projects")) {
            if (includeArchived) parameter("include_archived", true)
        }
    }.map { lenientJson.decodeFromString<ProjectsEnvelope>(it.bodyAsText()).projects }

    suspend fun createProject(url: GatewayUrl, request: CreateProjectRequest): ApiResult<DirectusProject> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/projects")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ProjectEnvelope>(it.bodyAsText()).project }

    suspend fun updateProject(
        url: GatewayUrl,
        projectId: String,
        request: UpdateProjectRequest,
    ): ApiResult<DirectusProject> = apiCall {
        client.patch(url.resolve("api/plugins/$PLUGIN/projects/${projectId.encodeURLPathPart()}")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ProjectEnvelope>(it.bodyAsText()).project }

    suspend fun archiveProject(url: GatewayUrl, projectId: String): ApiResult<DirectusProject> = apiCall {
        client.delete(url.resolve("api/plugins/$PLUGIN/projects/${projectId.encodeURLPathPart()}"))
    }.map { lenientJson.decodeFromString<ProjectEnvelope>(it.bodyAsText()).project }

    suspend fun createTask(url: GatewayUrl, request: CreateTaskRequest): ApiResult<DirectusTask> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/tasks")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<TaskEnvelope>(it.bodyAsText()).task }

    suspend fun updateTask(
        url: GatewayUrl,
        taskId: String,
        request: UpdateTaskRequest,
    ): ApiResult<DirectusTask> = apiCall {
        client.patch(url.resolve("api/plugins/$PLUGIN/tasks/${taskId.encodeURLPathPart()}")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<TaskEnvelope>(it.bodyAsText()).task }

    suspend fun upsertContext(
        url: GatewayUrl,
        taskId: String,
        request: UpsertContextRequest,
    ): ApiResult<DirectusTask> = apiCall {
        client.put(url.resolve("api/plugins/$PLUGIN/tasks/${taskId.encodeURLPathPart()}/context")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<TaskEnvelope>(it.bodyAsText()).task }

    suspend fun approveTask(
        url: GatewayUrl,
        taskId: String,
        request: ApproveTaskRequest,
    ): ApiResult<DirectusTask> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/tasks/${taskId.encodeURLPathPart()}/approve")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<TaskEnvelope>(it.bodyAsText()).task }

    suspend fun listDeltas(
        url: GatewayUrl,
        projectId: String? = null,
        limit: Int = 30,
    ): ApiResult<List<TweakDeltaRecord>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/deltas")) {
            projectId?.let { parameter("project_id", it) }
            parameter("limit", limit)
        }
    }.map { lenientJson.decodeFromString<DeltasEnvelope>(it.bodyAsText()).deltas }

    companion object {
        const val PLUGIN = "directus-tasks"
        private val lenientJson = Json(HermesJson) { coerceInputValues = true }
    }
}
