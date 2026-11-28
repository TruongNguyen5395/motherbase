package dev.hermeskotlin.core.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One scheduled check-in window for the Dispatch Coordinator: local trigger [time] (`"HH:MM"`),
 * symmetric `[time - windowMinutes, time + windowMinutes]` activation range, and an optional
 * per-schedule standing [instruction].
 */
@Serializable
data class DispatchCheckinSchedule(
    val id: String = "",
    val time: String = "09:00",
    @SerialName("window_minutes") val windowMinutes: Int = 30,
    val instruction: String = "",
    val enabled: Boolean = true,
) {
    fun normalizedId(): String = id.ifBlank { time }
}

/** Matched due check-in slot key and its rule metadata. */
data class DueCheckinMatch(
    val slotKey: String,
    val schedule: DispatchCheckinSchedule,
)

/**
 * Board-level Dispatch Coordinator Agent configuration persisted on the gateway inside `directus-tasks`
 * so all devices share the same coordinator profile, dedicated board session, standing prompt, and check-in state.
 */
@Serializable
data class DispatchCoordinatorConfig(
    val profile: String = "default",
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("default_prompt") val defaultPrompt: String = "",
    @SerialName("checkin_enabled") val checkinEnabled: Boolean = true,
    @SerialName("checkin_times") val checkinTimes: List<String> = DEFAULT_CHECKIN_TIMES,
    @SerialName("checkin_schedules") val checkinSchedules: List<DispatchCheckinSchedule> = emptyList(),
    @SerialName("window_minutes") val windowMinutes: Int = 30,
    @SerialName("grace_minutes") val graceMinutes: Int = 30,
    @SerialName("cooldown_minutes") val cooldownMinutes: Int = 45,
    @SerialName("auto_open_on_launch") val autoOpenOnLaunch: Boolean = true,
    @SerialName("last_checkin_slot") val lastCheckinSlot: String = "",
    @SerialName("last_interaction_at") val lastInteractionAt: Double = 0.0,
) {
    /** Effective schedules, falling back to legacy [checkinTimes] + [windowMinutes] when [checkinSchedules] is empty. */
    fun effectiveCheckinSchedules(): List<DispatchCheckinSchedule> =
        if (checkinSchedules.isNotEmpty()) {
            checkinSchedules
        } else {
            checkinTimes.map { t ->
                DispatchCheckinSchedule(id = t, time = t, windowMinutes = windowMinutes, instruction = "", enabled = true)
            }
        }

    /**
     * Returns the due check-in match when [localMinuteOfDay] (`0..1439`) falls inside an enabled schedule's
     * symmetric `[slot - windowMinutes .. slot + windowMinutes]` range and the user has not interacted with the
     * coordinator within the last [cooldownMinutes].
     */
    fun resolveDueCheckin(
        localDateIso: String,
        localMinuteOfDay: Int,
        nowEpochSeconds: Long,
    ): DueCheckinMatch? {
        if (!checkinEnabled || localDateIso.isBlank()) return null
        val activeSchedules = effectiveCheckinSchedules().filter { it.enabled }
        if (activeSchedules.isEmpty()) return null
        if (lastInteractionAt > 0.0) {
            val elapsedSec = nowEpochSeconds - lastInteractionAt.toLong()
            val cooldownSec = cooldownMinutes.coerceIn(5, 360) * 60L
            if (elapsedSec in 0L until cooldownSec) return null
        }
        val lastDate = lastCheckinSlot.substringBefore('@', "")
        val lastSlotMin = if (lastDate == localDateIso && '@' in lastCheckinSlot) {
            parseSlotMinute(lastCheckinSlot.substringAfter('@', ""))
        } else {
            null
        }
        var bestSlotMin: Int? = null
        var bestSchedule: DispatchCheckinSchedule? = null
        for (rule in activeSchedules) {
            val slotMin = parseSlotMinute(rule.time) ?: continue
            val win = rule.windowMinutes.coerceIn(5, 180)
            val startMin = (slotMin - win).coerceAtLeast(0)
            val endMin = (slotMin + win).coerceAtMost(1439)
            if (localMinuteOfDay in startMin..endMin) {
                if (lastSlotMin != null && lastSlotMin >= slotMin) continue
                if (bestSlotMin == null || slotMin > bestSlotMin) {
                    bestSlotMin = slotMin
                    bestSchedule = rule
                }
            }
        }
        val matchedMin = bestSlotMin ?: return null
        val matchedRule = bestSchedule ?: return null
        return DueCheckinMatch(
            slotKey = "$localDateIso@${formatSlotMinute(matchedMin)}",
            schedule = matchedRule,
        )
    }

    /** Convenience wrapper returning just the slot key (`"YYYY-MM-DD@HH:MM"`). */
    fun resolveDueCheckinSlot(
        localDateIso: String,
        localMinuteOfDay: Int,
        nowEpochSeconds: Long,
    ): String? = resolveDueCheckin(localDateIso, localMinuteOfDay, nowEpochSeconds)?.slotKey

    /**
     * Slot key and matching schedule when the user initiates a manual check-in at [localMinuteOfDay].
     */
    fun activeOrUpcomingCheckin(localDateIso: String, localMinuteOfDay: Int): DueCheckinMatch? {
        val activeSchedules = effectiveCheckinSchedules().filter { it.enabled }
        val active = activeSchedules
            .mapNotNull { rule -> parseSlotMinute(rule.time)?.let { it to rule } }
            .sortedBy { it.first }
            .lastOrNull { (slotMin, rule) ->
                val win = rule.windowMinutes.coerceIn(5, 180)
                localMinuteOfDay in (slotMin - win).coerceAtLeast(0)..(slotMin + win).coerceAtMost(1439)
            }
        if (active != null) {
            return DueCheckinMatch("$localDateIso@${formatSlotMinute(active.first)}", active.second)
        }
        val upcoming = activeSchedules
            .mapNotNull { rule -> parseSlotMinute(rule.time)?.let { it to rule } }
            .sortedBy { it.first }
            .firstOrNull { (slotMin, _) -> slotMin - localMinuteOfDay in 0..90 }
        return upcoming?.let { (slotMin, rule) ->
            DueCheckinMatch("$localDateIso@${formatSlotMinute(slotMin)}", rule)
        }
    }

    /**
     * Slot key to record when the user initiates a manual check-in at [localMinuteOfDay]: claims the active
     * or upcoming check-in slot (within 90 minutes) so the scheduled slot does not re-trigger redundantly.
     */
    fun activeOrUpcomingCheckinSlotId(localDateIso: String, localMinuteOfDay: Int): String =
        activeOrUpcomingCheckin(localDateIso, localMinuteOfDay)?.slotKey
            ?: "$localDateIso@${formatSlotMinute(localMinuteOfDay.coerceIn(0, 1439))}"

    companion object {
        val DEFAULT_CHECKIN_TIMES = listOf("09:00", "16:00")

        fun parseSlotMinute(raw: String): Int? {
            val clean = raw.trim()
            val colon = clean.indexOf(':')
            if (colon <= 0 || colon == clean.lastIndex) return null
            val hh = clean.substring(0, colon).trim().toIntOrNull() ?: return null
            val mm = clean.substring(colon + 1).trim().toIntOrNull() ?: return null
            if (hh !in 0..23 || mm !in 0..59) return null
            return hh * 60 + mm
        }

        fun formatSlotMinute(minuteOfDay: Int): String {
            val clamped = minuteOfDay.coerceIn(0, 1439)
            val hh = (clamped / 60).toString().padStart(2, '0')
            val mm = (clamped % 60).toString().padStart(2, '0')
            return "$hh:$mm"
        }
    }
}

@Serializable
data class DispatchCoordinatorUpdate(
    val profile: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("default_prompt") val defaultPrompt: String? = null,
    @SerialName("checkin_enabled") val checkinEnabled: Boolean? = null,
    @SerialName("checkin_times") val checkinTimes: List<String>? = null,
    @SerialName("checkin_schedules") val checkinSchedules: List<DispatchCheckinSchedule>? = null,
    @SerialName("window_minutes") val windowMinutes: Int? = null,
    @SerialName("grace_minutes") val graceMinutes: Int? = null,
    @SerialName("cooldown_minutes") val cooldownMinutes: Int? = null,
    @SerialName("auto_open_on_launch") val autoOpenOnLaunch: Boolean? = null,
    @SerialName("last_checkin_slot") val lastCheckinSlot: String? = null,
    @SerialName("last_interaction_at") val lastInteractionAt: Double? = null,
)

/** Public configuration state returned by the gateway's `directus-tasks` plugin (never exposes the raw token). */
@Serializable
data class DirectusConfigStatus(
    val configured: Boolean = false,
    val url: String = "",
    @SerialName("token_set") val tokenSet: Boolean = false,
    @SerialName("token_preview") val tokenPreview: String = "",
    val source: String = "none",
    val coordinator: DispatchCoordinatorConfig = DispatchCoordinatorConfig(),
)

@Serializable
data class DirectusConfigUpdate(
    val url: String,
    val token: String? = null,
    val verify: Boolean = true,
    val coordinator: DispatchCoordinatorUpdate? = null,
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
    /** True when Dispatch created and owns this channel's session lifecycle (auto-archive/unarchive). */
    val managed: Boolean = false,
) {
    val isRoom: Boolean get() = type == TYPE_ROOM
    val isSession: Boolean get() = type == TYPE_SESSION

    /**
     * Eligible for automatic archive on Done and unarchive on reopen: explicitly [managed] sessions plus
     * legacy Dispatch-created `"Session (@...)"` channels created before the flag existed. Manually linked
     * custom-labeled chats remain untouched.
     */
    val isAutoManagedSession: Boolean get() = isSession && (managed || label.startsWith("Session (@"))

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

/** Discrete 3-level axis for the 2D Impact × Urgency Dispatch priority matrix. */
enum class MatrixLevel(val wire: String, val rank: Int) {
    LOW("low", 1),
    MEDIUM("medium", 2),
    HIGH("high", 3),
    ;

    companion object {
        fun fromWire(raw: String?): MatrixLevel? = when (raw?.trim()?.lowercase()) {
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            else -> null
        }

        fun fromRank(rank: Int): MatrixLevel = when {
            rank >= 3 -> HIGH
            rank == 2 -> MEDIUM
            else -> LOW
        }
    }
}

/** 2×2 Eisenhower quadrant derived from (Impact, effective Urgency). */
enum class EisenhowerQuadrant(
    val defaultImpact: MatrixLevel,
    val defaultUrgency: MatrixLevel,
) {
    DO_NOW(MatrixLevel.HIGH, MatrixLevel.HIGH),
    SCHEDULE(MatrixLevel.HIGH, MatrixLevel.MEDIUM),
    DELEGATE(MatrixLevel.MEDIUM, MatrixLevel.HIGH),
    LATER(MatrixLevel.LOW, MatrixLevel.LOW),
    ;

    companion object {
        fun of(impact: MatrixLevel, effectiveUrgency: MatrixLevel): EisenhowerQuadrant = when {
            impact == MatrixLevel.HIGH && effectiveUrgency == MatrixLevel.HIGH -> DO_NOW
            impact == MatrixLevel.HIGH -> SCHEDULE
            effectiveUrgency == MatrixLevel.HIGH -> DELEGATE
            else -> LATER
        }
    }
}

/** Maps a 2D (impact, urgency) cell back onto Directus's 1D `priority` field so external views stay aligned. */
fun matrixSyncedPriority(impact: MatrixLevel, urgency: MatrixLevel): String = when {
    impact == MatrixLevel.HIGH && urgency == MatrixLevel.HIGH -> "urgent"
    impact == MatrixLevel.HIGH || (impact == MatrixLevel.MEDIUM && urgency == MatrixLevel.HIGH) -> "high"
    impact == MatrixLevel.MEDIUM || urgency == MatrixLevel.HIGH -> "medium"
    else -> "low"
}

/** Gateway-enriched reply & unread activity summary across a task's bound Session and Room channels. */
@Serializable
data class TaskReplyStatus(
    @SerialName("has_unread_reply") val hasUnreadReply: Boolean = false,
    @SerialName("awaiting_reply") val awaitingReply: Boolean = false,
    @SerialName("last_reply_at") val lastReplyAt: Double? = null,
    @SerialName("last_reply_channel_id") val lastReplyChannelId: String? = null,
    @SerialName("last_reply_channel_type") val lastReplyChannelType: String? = null,
    @SerialName("latest_room_seq") val latestRoomSeq: Int? = null,
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
    val impact: String? = null,
    val urgency: String? = null,
    @SerialName("reply_acked_at") val replyAckedAt: String? = null,
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
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
    @SerialName("reply_status") val replyStatus: TaskReplyStatus? = null,
) {
    val agentState: String get() = context?.agentState ?: TaskContext.STATE_IDLE
    val projectId: String? get() = context?.projectId
    val assignedBots: List<String> get() = context?.assignedBots.orEmpty()
    val channels: List<TaskChannel> get() = context?.channels.orEmpty()
    val stagedPlan: StagedPlan? get() = context?.stagedPlan
    val impact: String? get() = context?.impact
    val urgency: String? get() = context?.urgency
    val replyAckedAt: String? get() = context?.replyAckedAt
    val estimatedMinutes: Int? get() = context?.estimatedMinutes

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
    val coordinator: DispatchCoordinatorConfig? = null,
)

/** Paginated response from `GET /api/plugins/directus-tasks/tasks/completed`. */
@Serializable
data class CompletedTasksResponse(
    val configured: Boolean = false,
    val projects: List<DirectusProject> = emptyList(),
    val tasks: List<DirectusTask> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
) {
    companion object {
        /** Sentinel `project_id` query value matching completed tasks not linked to an active or archived project. */
        const val UNASSIGNED_PROJECT_FILTER = "__unassigned__"
    }
}

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
    val impact: String? = null,
    val urgency: String? = null,
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
    val impact: String? = null,
    val urgency: String? = null,
    @SerialName("reply_acked_at") val replyAckedAt: String? = null,
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
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
