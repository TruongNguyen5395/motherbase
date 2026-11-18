package dev.hermeskotlin.core.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Public configuration state returned by the gateway's `directus-tasks` plugin (never exposes the raw token). */
@Serializable
data class DirectusConfigStatus(
    val configured: Boolean = false,
    val url: String = "",
    @SerialName("token_set") val tokenSet: Boolean = false,
    @SerialName("token_preview") val tokenPreview: String = "",
    val source: String = "none",
)

@Serializable
data class DirectusConfigUpdate(
    val url: String,
    val token: String? = null,
    val verify: Boolean = true,
)

@Serializable
data class DirectusConnectionTestResult(
    val ok: Boolean = false,
    val collections: Map<String, Boolean> = emptyMap(),
)

/** Explicit project grouping (`mb_projects`) mapped optionally to a Hermes project and working folder. */
@Serializable
data class DirectusProject(
    val id: String,
    val name: String,
    @SerialName("hermes_project_id") val hermesProjectId: String? = null,
    @SerialName("workspace_cwd") val workspaceCwd: String? = null,
    @SerialName("default_bots") val defaultBots: List<String> = emptyList(),
    val color: String? = null,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
    @SerialName("hermes_project_id") val hermesProjectId: String? = null,
    @SerialName("workspace_cwd") val workspaceCwd: String? = null,
    @SerialName("default_bots") val defaultBots: List<String> = emptyList(),
    val color: String? = null,
    val sort: Int = 0,
)

@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    @SerialName("hermes_project_id") val hermesProjectId: String? = null,
    @SerialName("workspace_cwd") val workspaceCwd: String? = null,
    @SerialName("default_bots") val defaultBots: List<String>? = null,
    val color: String? = null,
    val sort: Int? = null,
    val archived: Boolean? = null,
)

@Serializable
data class TaskWorkLog(
    @SerialName("logged_at") val loggedAt: String = "",
    val content: String = "",
    val status: String? = null,
)

/** A Hermes execution channel (either a multi-bot Room or a single-bot Session) bound to a Directus task. */
@Serializable
data class TaskChannel(
    val type: String,
    val id: String,
    val label: String = "",
    val profile: String? = null,
) {
    val isRoom: Boolean get() = type == TYPE_ROOM
    val isSession: Boolean get() = type == TYPE_SESSION

    companion object {
        const val TYPE_ROOM = "room"
        const val TYPE_SESSION = "session"
    }
}

/** One tweakable inline chip inside a [StagedPlan] or parameterized reply card. */
@Serializable
data class PlanParam(
    val key: String,
    val label: String,
    val options: List<String> = emptyList(),
    val default: String = "",
)

@Serializable
data class PlanAction(
    val label: String,
    val description: String? = null,
    val instruction: String = label,
)

/** Pre-generated action plan persisted in Directus (`mb_task_contexts.staged_plan`) so idle tasks cost 0 tokens. */
@Serializable
data class StagedPlan(
    val summary: String = "",
    val question: String? = null,
    val params: List<PlanParam> = emptyList(),
    val actions: List<PlanAction> = emptyList(),
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("generated_by") val generatedBy: String? = null,
    @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("approved_option") val approvedOption: String? = null,
)

/** Orchestration record from `mb_task_contexts`, kept separate from the base `tasks` schema. */
@Serializable
data class TaskContext(
    val id: String? = null,
    @SerialName("task_id") val taskId: String = "",
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assigned_bots") val assignedBots: List<String> = emptyList(),
    val channels: List<TaskChannel> = emptyList(),
    @SerialName("agent_state") val agentState: String = STATE_IDLE,
    @SerialName("staged_plan") val stagedPlan: StagedPlan? = null,
    @SerialName("plan_source_hash") val planSourceHash: String? = null,
    @SerialName("last_error") val lastError: String? = null,
) {
    val needsAttention: Boolean get() = agentState == STATE_NEEDS_YOU
    val isRunning: Boolean get() = agentState == STATE_RUNNING

    companion object {
        const val STATE_IDLE = "idle"
        const val STATE_QUEUED = "queued"
        const val STATE_RUNNING = "running"
        const val STATE_NEEDS_YOU = "needs_you"
    }
}

/** Combined view of a Directus task and its optional [TaskContext]. */
@Serializable
data class DirectusTask(
    val id: String,
    val title: String,
    val description: String? = null,
    val status: String = STATUS_TODO,
    val priority: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("work_logs") val workLogs: List<TaskWorkLog> = emptyList(),
    @SerialName("date_created") val dateCreated: String? = null,
    @SerialName("date_updated") val dateUpdated: String? = null,
    @SerialName("status_changed_at") val statusChangedAt: String? = null,
    val context: TaskContext? = null,
) {
    val agentState: String get() = context?.agentState ?: TaskContext.STATE_IDLE
    val projectId: String? get() = context?.projectId
    val assignedBots: List<String> get() = context?.assignedBots.orEmpty()
    val channels: List<TaskChannel> get() = context?.channels.orEmpty()
    val stagedPlan: StagedPlan? get() = context?.stagedPlan

    companion object {
        const val STATUS_TODO = "todo"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_BLOCKED = "blocked"
        const val STATUS_DONE = "done"
    }
}

@Serializable
data class DirectusBoardResponse(
    val configured: Boolean = false,
    val projects: List<DirectusProject> = emptyList(),
    val tasks: List<DirectusTask> = emptyList(),
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val status: String = DirectusTask.STATUS_TODO,
    val priority: String? = "medium",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assigned_bots") val assignedBots: List<String> = emptyList(),
)

@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("append_work_log") val appendWorkLog: String? = null,
)

@Serializable
data class UpsertContextRequest(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assigned_bots") val assignedBots: List<String>? = null,
    val channels: List<TaskChannel>? = null,
    @SerialName("agent_state") val agentState: String? = null,
    @SerialName("staged_plan") val stagedPlan: StagedPlan? = null,
    @SerialName("last_error") val lastError: String? = null,
)

@Serializable
data class TweakDeltaInput(
    @SerialName("param_key") val paramKey: String,
    @SerialName("param_label") val paramLabel: String? = null,
    @SerialName("agent_proposed") val agentProposed: String,
    @SerialName("user_selected") val userSelected: String,
)

@Serializable
data class ApproveTaskRequest(
    val summary: String? = null,
    @SerialName("chosen_option") val chosenOption: String? = null,
    val tweaks: List<TweakDeltaInput> = emptyList(),
    @SerialName("new_status") val newStatus: String? = null,
    @SerialName("new_agent_state") val newAgentState: String? = TaskContext.STATE_RUNNING,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assigned_bots") val assignedBots: List<String>? = null,
    val channel: TaskChannel? = null,
    @SerialName("work_log_content") val workLogContent: String? = null,
)

@Serializable
data class TweakDeltaRecord(
    val id: String = "",
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("param_key") val paramKey: String = "",
    @SerialName("param_label") val paramLabel: String? = null,
    @SerialName("agent_proposed") val agentProposed: String = "",
    @SerialName("user_selected") val userSelected: String = "",
    @SerialName("context_note") val contextNote: String? = null,
    @SerialName("date_created") val dateCreated: String? = null,
)
