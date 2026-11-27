package dev.hermeskotlin.core.calendar

import dev.hermeskotlin.core.tasks.TaskChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Dedicated Calendar Agent session + profile persisted on the gateway inside `motherbase-calendar`. */
@Serializable
data class CalendarAgentConfig(
    val profile: String = "default",
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("last_interaction_at") val lastInteractionAt: Double = 0.0,
)

/** Partial update payload for `PUT /api/plugins/motherbase-calendar/agent`. */
@Serializable
data class CalendarAgentUpdate(
    val profile: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("last_interaction_at") val lastInteractionAt: Double? = null,
)

/** Sync target configuration for 2-way Dispatch task time-blocks. */
@Serializable
data class DispatchSyncTarget(
    val mode: String = "subcalendar",
    @SerialName("subcalendar_name") val subcalendarName: String = "MotherBase",
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("calendar_id") val calendarId: String? = null,
    @SerialName("auto_sync_enabled") val autoSyncEnabled: Boolean = true,
)

/** Gateway-persisted user preferences for MotherBase Calendar. */
@Serializable
data class CalendarSettings(
    val timezone: String = "Asia/Ho_Chi_Minh",
    @SerialName("working_hours") val workingHours: Map<String, List<String>> = emptyMap(),
    @SerialName("dispatch_sync_target") val dispatchSyncTarget: DispatchSyncTarget = DispatchSyncTarget(),
    @SerialName("default_view") val defaultView: String = VIEW_FOUR_DAYS,
    @SerialName("show_weekends") val showWeekends: Boolean = true,
    @SerialName("show_declined") val showDeclined: Boolean = false,
    @SerialName("show_completed_tasks") val showCompletedTasks: Boolean = true,
    val agent: CalendarAgentConfig = CalendarAgentConfig(),
) {
    companion object {
        const val VIEW_DAY = "day"
        const val VIEW_FOUR_DAYS = "four_days"
        const val VIEW_WEEK = "week"
        const val VIEW_MONTH = "month"
        const val VIEW_SCHEDULE = "schedule"
    }
}

@Serializable
data class CalendarHealthStatus(
    val ok: Boolean = false,
    @SerialName("accounts_count") val accountsCount: Int = 0,
    @SerialName("calendars_count") val calendarsCount: Int = 0,
    @SerialName("has_hermes_google_token") val hasHermesGoogleToken: Boolean = false,
    @SerialName("hermes_google_email") val hermesGoogleEmail: String? = null,
    @SerialName("has_hermes_google_client") val hasHermesGoogleClient: Boolean = false,
    @SerialName("directus_configured") val directusConfigured: Boolean = false,
    val settings: CalendarSettings = CalendarSettings(),
)

/** A connected external calendar account (never carries raw OAuth/CalDAV secrets). */
@Serializable
data class CalendarAccount(
    val id: String,
    val provider: String,
    @SerialName("auth_mode") val authMode: String,
    val label: String,
    val email: String? = null,
    val region: String? = null,
    val status: String = "connected",
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
    @SerialName("has_credentials") val hasCredentials: Boolean = false,
)

/** One selectable calendar layer belonging to a [CalendarAccount]. */
@Serializable
data class CalendarInfo(
    val id: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("provider_cal_id") val providerCalId: String = "",
    val name: String,
    @SerialName("color_hex") val colorHex: String = "#1A73E8",
    val timezone: String = "Asia/Ho_Chi_Minh",
    val writable: Boolean = true,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("is_task_target") val isTaskTarget: Boolean = false,
    val visible: Boolean = true,
    val provider: String = "",
    @SerialName("auth_mode") val authMode: String = "",
    @SerialName("account_label") val accountLabel: String = "",
    @SerialName("last_synced_at") val lastSyncedAt: String? = null,
)

@Serializable
data class EventAttendee(
    val email: String = "",
    val name: String = "",
    @SerialName("response_status") val responseStatus: String? = null,
    val self: Boolean = false,
)

/** Unified calendar occurrence (timed or all-day; external or Dispatch time-block). */
@Serializable
data class CalendarEvent(
    val id: String,
    @SerialName("calendar_id") val calendarId: String,
    @SerialName("account_id") val accountId: String = "",
    @SerialName("provider_event_id") val providerEventId: String = "",
    @SerialName("recurring_event_id") val recurringEventId: String? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("start_epoch_ms") val startEpochMs: Long? = null,
    @SerialName("end_epoch_ms") val endEpochMs: Long? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    val timezone: String = "Asia/Ho_Chi_Minh",
    val status: String = "confirmed",
    @SerialName("response_status") val responseStatus: String? = null,
    @SerialName("busy_state") val busyState: String = "busy",
    val attendees: List<EventAttendee> = emptyList(),
    @SerialName("conference_url") val conferenceUrl: String? = null,
    @SerialName("html_link") val htmlLink: String? = null,
    val etag: String? = null,
    @SerialName("source_kind") val sourceKind: String = "external",
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("time_block_id") val timeBlockId: String? = null,
    @SerialName("calendar_name") val calendarName: String = "",
    @SerialName("color_hex") val colorHex: String = "#1A73E8",
    val provider: String = "",
    @SerialName("calendar_writable") val calendarWritable: Boolean = true,
) {
    val isDispatchBlock: Boolean get() = sourceKind == "dispatch_block" || timeBlockId != null
    val hasExternalAttendees: Boolean get() = attendees.any { !it.self && it.email.isNotBlank() }
}

@Serializable
data class FreeSlot(
    val date: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("start_local") val startLocal: String,
    @SerialName("end_local") val endLocal: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
)

/** A 2-way synced Directus `mb_time_blocks` row. */
@Serializable
data class DispatchTimeBlock(
    val id: String,
    @SerialName("task_id") val taskId: String,
    val label: String? = null,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    val timezone: String = "Asia/Ho_Chi_Minh",
    val status: String = "scheduled",
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("calendar_id") val calendarId: String? = null,
    @SerialName("external_event_id") val externalEventId: String? = null,
    @SerialName("external_etag") val externalEtag: String? = null,
    @SerialName("sync_hash") val syncHash: String? = null,
)

@Serializable
data class DispatchCalendarTask(
    val id: String,
    val title: String,
    val status: String = "todo",
    val priority: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assigned_bots") val assignedBots: List<String> = emptyList(),
    val channels: List<TaskChannel> = emptyList(),
    @SerialName("agent_state") val agentState: String = "idle",
    val impact: String? = null,
    val urgency: String? = null,
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    @SerialName("time_blocks") val timeBlocks: List<DispatchTimeBlock> = emptyList(),
)

@Serializable
data class DispatchProjectSummary(
    val id: String,
    val name: String,
    @SerialName("workspace_cwd") val workspaceCwd: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
)

@Serializable
data class DispatchCalendarOverview(
    val configured: Boolean = false,
    val projects: List<DispatchProjectSummary> = emptyList(),
    val tasks: List<DispatchCalendarTask> = emptyList(),
    @SerialName("time_blocks") val timeBlocks: List<DispatchTimeBlock> = emptyList(),
)

@Serializable
data class ProposalConflict(
    val id: String = "",
    @SerialName("provider_event_id") val providerEventId: String = "",
    val title: String = "",
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("calendar_name") val calendarName: String? = null,
)

@Serializable
data class ScheduleOperation(
    @SerialName("op_id") val opId: String,
    val kind: String,
    val title: String,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("occurrence_id") val occurrenceId: String? = null,
    @SerialName("time_block_id") val timeBlockId: String? = null,
    @SerialName("calendar_id") val calendarId: String? = null,
    @SerialName("calendar_name") val calendarName: String? = null,
    @SerialName("color_hex") val colorHex: String = "#0F766E",
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val timezone: String = "Asia/Ho_Chi_Minh",
    val description: String? = null,
    val location: String? = null,
    val attendees: List<EventAttendee> = emptyList(),
    @SerialName("has_external_attendees") val hasExternalAttendees: Boolean = false,
    @SerialName("send_updates") val sendUpdates: String = "none",
    val reason: String? = null,
    val selected: Boolean = true,
    val conflicts: List<ProposalConflict> = emptyList(),
)

@Serializable
data class ScheduleProposal(
    val id: String,
    @SerialName("session_id") val sessionId: String? = null,
    val title: String,
    val summary: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    val status: String = STATUS_STAGED,
    val operations: List<ScheduleOperation> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    companion object {
        const val STATUS_STAGED = "staged"
        const val STATUS_APPLIED = "applied"
        const val STATUS_DISCARDED = "discarded"
        const val STATUS_REVERTED = "reverted"
    }
}

@Serializable
data class ProposalOperationConflicts(
    @SerialName("op_id") val opId: String,
    val title: String,
    val conflicts: List<ProposalConflict> = emptyList(),
)

sealed interface ApplyProposalOutcome {
    data class Applied(val proposal: ScheduleProposal) : ApplyProposalOutcome
    data class Conflict(
        val conflicts: List<ProposalOperationConflicts>,
        val proposal: ScheduleProposal?,
    ) : ApplyProposalOutcome
}

// ── Request payloads ─────────────────────────────────────────────────────────

@Serializable
data class OAuthUrlResponse(
    @SerialName("auth_url") val authUrl: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("used_hermes_client") val usedHermesClient: Boolean = false,
    val region: String? = null,
)

@Serializable
data class GoogleAuthUrlRequest(
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@Serializable
data class GoogleExchangeRequest(
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
    val code: String,
    val label: String? = null,
    @SerialName("ensure_task_subcalendar") val ensureTaskSubcalendar: Boolean = true,
)

@Serializable
data class LarkAuthUrlRequest(
    @SerialName("app_id") val appId: String,
    val region: String = "intl",
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@Serializable
data class LarkExchangeRequest(
    @SerialName("app_id") val appId: String,
    @SerialName("app_secret") val appSecret: String,
    val region: String = "intl",
    val code: String,
    val label: String? = null,
    @SerialName("ensure_task_subcalendar") val ensureTaskSubcalendar: Boolean = false,
)

@Serializable
data class MicrosoftAuthUrlRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("tenant_id") val tenantId: String = "common",
    @SerialName("redirect_uri") val redirectUri: String? = null,
)

@Serializable
data class MicrosoftExchangeRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("tenant_id") val tenantId: String = "common",
    @SerialName("redirect_uri") val redirectUri: String? = null,
    val code: String,
    val label: String? = null,
    @SerialName("ensure_task_subcalendar") val ensureTaskSubcalendar: Boolean = false,
)

@Serializable
data class ConnectCalDavRequest(
    val provider: String = "caldav",
    @SerialName("server_url") val serverUrl: String,
    val username: String,
    val password: String,
    val label: String? = null,
    val region: String? = null,
)

@Serializable
data class ConnectIcsRequest(
    val provider: String = "ics",
    @SerialName("feed_url") val feedUrl: String,
    @SerialName("calendar_name") val calendarName: String,
    val label: String? = null,
    @SerialName("color_hex") val colorHex: String = "#10B981",
)

@Serializable
data class ConnectAccountResult(
    val account: CalendarAccount,
    val calendars: List<CalendarInfo> = emptyList(),
)

@Serializable
data class UpdateCalendarRequest(
    val visible: Boolean? = null,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("is_task_target") val isTaskTarget: Boolean? = null,
)

@Serializable
data class CreateEventRequest(
    @SerialName("calendar_id") val calendarId: String,
    val title: String,
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val timezone: String? = null,
    val description: String? = null,
    val location: String? = null,
    @SerialName("busy_state") val busyState: String = "busy",
    val attendees: List<EventAttendee> = emptyList(),
    @SerialName("send_updates") val sendUpdates: String = "none",
)

@Serializable
data class UpdateEventRequest(
    val title: String? = null,
    @SerialName("all_day") val allDay: Boolean? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val timezone: String? = null,
    val description: String? = null,
    val location: String? = null,
    @SerialName("busy_state") val busyState: String? = null,
    val attendees: List<EventAttendee>? = null,
    @SerialName("send_updates") val sendUpdates: String = "none",
)

@Serializable
data class CreateTimeBlockRequest(
    @SerialName("task_id") val taskId: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    val label: String? = null,
    val timezone: String? = null,
    @SerialName("calendar_id") val calendarId: String? = null,
)

@Serializable
data class UpdateTimeBlockRequest(
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    val label: String? = null,
    val status: String? = null,
)

@Serializable
data class ApplyProposalRequest(
    @SerialName("selected_op_ids") val selectedOpIds: List<String>? = null,
    @SerialName("send_updates_override") val sendUpdatesOverride: String? = null,
    val force: Boolean = false,
)

/** Cached local snapshot of calendar workspace for fast offline/cold rendering per gateway. */
@Serializable
data class CalendarOfflineSnapshot(
    val accounts: List<CalendarAccount> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val dispatchOverview: DispatchCalendarOverview = DispatchCalendarOverview(),
    val settings: CalendarSettings = CalendarSettings(),
    val savedAtMs: Long = 0L,
)
