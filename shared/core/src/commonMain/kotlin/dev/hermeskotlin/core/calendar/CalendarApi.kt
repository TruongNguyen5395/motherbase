package dev.hermeskotlin.core.calendar

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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class SettingsEnvelope(val settings: CalendarSettings)

@Serializable
private data class AccountsEnvelope(
    val accounts: List<CalendarAccount> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
)

@Serializable
private data class CalendarsEnvelope(val calendars: List<CalendarInfo> = emptyList())

@Serializable
private data class CalendarEnvelope(val calendar: CalendarInfo)

@Serializable
private data class EventsEnvelope(val events: List<CalendarEvent> = emptyList())

@Serializable
private data class EventEnvelope(val event: CalendarEvent)

@Serializable
private data class FreeSlotsEnvelope(@SerialName("free_slots") val freeSlots: List<FreeSlot> = emptyList())

@Serializable
private data class TimeBlockEnvelope(@SerialName("time_block") val timeBlock: DispatchTimeBlock)

@Serializable
private data class ProposalsEnvelope(val proposals: List<ScheduleProposal> = emptyList())

@Serializable
private data class ProposalEnvelope(val proposal: ScheduleProposal)

@Serializable
private data class ConflictBody(
    val conflicts: List<ProposalOperationConflicts> = emptyList(),
    val proposal: ScheduleProposal? = null,
)

/**
 * Gateway-proxied Calendar API (`/api/plugins/motherbase-calendar/...`).
 * Keeps all external provider OAuth secrets and SQLite occurrence cache on the gateway.
 */
class CalendarApi(private val client: HttpClient) {

    suspend fun health(url: GatewayUrl): ApiResult<CalendarHealthStatus> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/health"))
    }.map { lenientJson.decodeFromString<CalendarHealthStatus>(it.bodyAsText()) }

    suspend fun getSettings(url: GatewayUrl): ApiResult<CalendarSettings> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/settings"))
    }.map { lenientJson.decodeFromString<SettingsEnvelope>(it.bodyAsText()).settings }

    suspend fun updateSettings(url: GatewayUrl, settings: CalendarSettings): ApiResult<CalendarSettings> = apiCall {
        client.put(url.resolve("api/plugins/$PLUGIN/settings")) {
            contentType(ContentType.Application.Json)
            setBody(settings)
        }
    }.map { lenientJson.decodeFromString<SettingsEnvelope>(it.bodyAsText()).settings }

    suspend fun listAccounts(url: GatewayUrl): ApiResult<Pair<List<CalendarAccount>, List<CalendarInfo>>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/accounts"))
    }.map {
        val env = lenientJson.decodeFromString<AccountsEnvelope>(it.bodyAsText())
        env.accounts to env.calendars
    }

    suspend fun googleAuthUrl(
        url: GatewayUrl,
        request: GoogleAuthUrlRequest = GoogleAuthUrlRequest(),
    ): ApiResult<OAuthUrlResponse> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/google/auth-url")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<OAuthUrlResponse>(it.bodyAsText()) }

    suspend fun exchangeGoogleCode(
        url: GatewayUrl,
        request: GoogleExchangeRequest,
    ): ApiResult<ConnectAccountResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/google/exchange")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ConnectAccountResult>(it.bodyAsText()) }

    suspend fun importHermesGoogle(
        url: GatewayUrl,
        label: String? = null,
        ensureTaskSubcalendar: Boolean = true,
    ): ApiResult<ConnectAccountResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/google/import-hermes")) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    label?.let { put("label", it) }
                    put("ensure_task_subcalendar", ensureTaskSubcalendar)
                },
            )
        }
    }.map { lenientJson.decodeFromString<ConnectAccountResult>(it.bodyAsText()) }

    suspend fun larkAuthUrl(url: GatewayUrl, request: LarkAuthUrlRequest): ApiResult<OAuthUrlResponse> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/lark/auth-url")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<OAuthUrlResponse>(it.bodyAsText()) }

    suspend fun exchangeLarkCode(url: GatewayUrl, request: LarkExchangeRequest): ApiResult<ConnectAccountResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/lark/exchange")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ConnectAccountResult>(it.bodyAsText()) }

    suspend fun microsoftAuthUrl(
        url: GatewayUrl,
        request: MicrosoftAuthUrlRequest,
    ): ApiResult<OAuthUrlResponse> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/microsoft/auth-url")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<OAuthUrlResponse>(it.bodyAsText()) }

    suspend fun exchangeMicrosoftCode(
        url: GatewayUrl,
        request: MicrosoftExchangeRequest,
    ): ApiResult<ConnectAccountResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/microsoft/exchange")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ConnectAccountResult>(it.bodyAsText()) }

    suspend fun connectCalDav(url: GatewayUrl, request: ConnectCalDavRequest): ApiResult<ConnectAccountResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/caldav")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ConnectAccountResult>(it.bodyAsText()) }

    suspend fun connectIcs(url: GatewayUrl, request: ConnectIcsRequest): ApiResult<ConnectAccountResult> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/accounts/ics")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<ConnectAccountResult>(it.bodyAsText()) }

    suspend fun deleteAccount(url: GatewayUrl, accountId: String): ApiResult<Unit> = apiCall {
        client.delete(url.resolve("api/plugins/$PLUGIN/accounts/${accountId.encodeURLPathPart()}"))
    }.map { }

    suspend fun listCalendars(url: GatewayUrl): ApiResult<List<CalendarInfo>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/calendars"))
    }.map { lenientJson.decodeFromString<CalendarsEnvelope>(it.bodyAsText()).calendars }

    suspend fun updateCalendar(
        url: GatewayUrl,
        calendarId: String,
        request: UpdateCalendarRequest,
    ): ApiResult<CalendarInfo> = apiCall {
        client.patch(url.resolve("api/plugins/$PLUGIN/calendars/${calendarId.encodeURLPathPart()}")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<CalendarEnvelope>(it.bodyAsText()).calendar }

    suspend fun sync(url: GatewayUrl, accountId: String? = null): ApiResult<Unit> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/sync")) {
            if (accountId != null) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("account_id", accountId) })
            }
        }
    }.map { }

    suspend fun listEvents(
        url: GatewayUrl,
        startDate: String,
        endDate: String,
        includeDeclined: Boolean = false,
    ): ApiResult<List<CalendarEvent>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/events")) {
            parameter("start_date", startDate)
            parameter("end_date", endDate)
            if (includeDeclined) parameter("include_declined", true)
        }
    }.map { lenientJson.decodeFromString<EventsEnvelope>(it.bodyAsText()).events }

    suspend fun createEvent(url: GatewayUrl, request: CreateEventRequest): ApiResult<CalendarEvent> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/events")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<EventEnvelope>(it.bodyAsText()).event }

    suspend fun updateEvent(
        url: GatewayUrl,
        occurrenceId: String,
        request: UpdateEventRequest,
    ): ApiResult<CalendarEvent> = apiCall {
        client.patch(url.resolve("api/plugins/$PLUGIN/events/${occurrenceId.encodeURLPathPart()}")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<EventEnvelope>(it.bodyAsText()).event }

    suspend fun deleteEvent(
        url: GatewayUrl,
        occurrenceId: String,
        sendUpdates: String = "none",
    ): ApiResult<Unit> = apiCall {
        client.delete(url.resolve("api/plugins/$PLUGIN/events/${occurrenceId.encodeURLPathPart()}")) {
            parameter("send_updates", sendUpdates)
        }
    }.map { }

    suspend fun findFreeSlots(
        url: GatewayUrl,
        startDate: String,
        endDate: String,
        minDurationMinutes: Int = 30,
        bufferMinutes: Int = 0,
    ): ApiResult<List<FreeSlot>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/free-slots")) {
            parameter("start_date", startDate)
            parameter("end_date", endDate)
            parameter("min_duration_minutes", minDurationMinutes)
            parameter("buffer_minutes", bufferMinutes)
        }
    }.map { lenientJson.decodeFromString<FreeSlotsEnvelope>(it.bodyAsText()).freeSlots }

    suspend fun dispatchOverview(
        url: GatewayUrl,
        includeDone: Boolean = false,
    ): ApiResult<DispatchCalendarOverview> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/dispatch/overview")) {
            if (includeDone) parameter("include_done", true)
        }
    }.map { lenientJson.decodeFromString<DispatchCalendarOverview>(it.bodyAsText()) }

    suspend fun createTimeBlock(
        url: GatewayUrl,
        request: CreateTimeBlockRequest,
    ): ApiResult<DispatchTimeBlock> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/time-blocks")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<TimeBlockEnvelope>(it.bodyAsText()).timeBlock }

    suspend fun updateTimeBlock(
        url: GatewayUrl,
        blockId: String,
        request: UpdateTimeBlockRequest,
    ): ApiResult<DispatchTimeBlock> = apiCall {
        client.patch(url.resolve("api/plugins/$PLUGIN/time-blocks/${blockId.encodeURLPathPart()}")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }.map { lenientJson.decodeFromString<TimeBlockEnvelope>(it.bodyAsText()).timeBlock }

    suspend fun deleteTimeBlock(url: GatewayUrl, blockId: String): ApiResult<Unit> = apiCall {
        client.delete(url.resolve("api/plugins/$PLUGIN/time-blocks/${blockId.encodeURLPathPart()}"))
    }.map { }

    suspend fun toggleDispatchTaskDone(
        url: GatewayUrl,
        taskId: String,
        done: Boolean,
    ): ApiResult<Unit> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/dispatch/tasks/${taskId.encodeURLPathPart()}/toggle-done")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("done", done) })
        }
    }.map { }

    suspend fun listProposals(
        url: GatewayUrl,
        status: String? = null,
    ): ApiResult<List<ScheduleProposal>> = apiCall {
        client.get(url.resolve("api/plugins/$PLUGIN/proposals")) {
            status?.let { parameter("status", it) }
        }
    }.map { lenientJson.decodeFromString<ProposalsEnvelope>(it.bodyAsText()).proposals }

    suspend fun applyProposal(
        url: GatewayUrl,
        proposalId: String,
        request: ApplyProposalRequest = ApplyProposalRequest(),
    ): ApiResult<ApplyProposalOutcome> {
        val response = try {
            client.post(url.resolve("api/plugins/$PLUGIN/proposals/${proposalId.encodeURLPathPart()}/apply")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ApiResult.Unavailable(e.message ?: e::class.simpleName ?: "Connection failed")
        }

        if (response.status == HttpStatusCode.Conflict) {
            return try {
                val body = lenientJson.decodeFromString<ConflictBody>(response.bodyAsText())
                ApiResult.Success(ApplyProposalOutcome.Conflict(body.conflicts, body.proposal))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ApiResult.Failed(409, "Schedule conflict: ${e.message}")
            }
        }

        return apiCall { response }.map {
            ApplyProposalOutcome.Applied(lenientJson.decodeFromString<ProposalEnvelope>(it.bodyAsText()).proposal)
        }
    }

    suspend fun discardProposal(url: GatewayUrl, proposalId: String): ApiResult<ScheduleProposal> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/proposals/${proposalId.encodeURLPathPart()}/discard"))
    }.map { lenientJson.decodeFromString<ProposalEnvelope>(it.bodyAsText()).proposal }

    suspend fun revertProposal(
        url: GatewayUrl,
        proposalId: String,
        force: Boolean = false,
    ): ApiResult<ScheduleProposal> = apiCall {
        client.post(url.resolve("api/plugins/$PLUGIN/proposals/${proposalId.encodeURLPathPart()}/revert")) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("force", force) })
        }
    }.map { lenientJson.decodeFromString<ProposalEnvelope>(it.bodyAsText()).proposal }

    companion object {
        const val PLUGIN = "motherbase-calendar"
        private val lenientJson = Json(HermesJson) { isLenient = true }
    }
}
