package dev.hermeskotlin.core.calendar

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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CalendarApiTest {

    private val gatewayUrl = GatewayUrl.parse("https://hermes.example.ts.net")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun fetchesHealthAccountsEventsAndDispatchOverview() = runTest {
        val requests = mutableListOf<String>()
        val api = CalendarApi(
            createHttpClient(
                MockEngine { req ->
                    requests += "${req.method.value} ${req.url.encodedPath}"
                    when (req.url.encodedPath) {
                        "/api/plugins/motherbase-calendar/health" -> respond(
                            """{"ok":true,"accounts_count":1,"calendars_count":2,"has_hermes_google_token":true,"directus_configured":true}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        "/api/plugins/motherbase-calendar/accounts" -> respond(
                            """{
                                "accounts":[{"id":"acc-1","provider":"google","auth_mode":"hermes_import","label":"Work","status":"connected","has_credentials":true}],
                                "calendars":[{"id":"cal-1","account_id":"acc-1","name":"MotherBase","color_hex":"#0F766E","writable":true,"is_task_target":true,"visible":true}]
                            }""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        "/api/plugins/motherbase-calendar/events" -> respond(
                            """{"events":[{"id":"occ-1","calendar_id":"cal-1","title":"Focus Block","start_at":"2026-11-26T02:00:00Z","end_at":"2026-11-26T03:00:00Z","source_kind":"dispatch_block","task_id":"t-1","time_block_id":"b-1"}]}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        "/api/plugins/motherbase-calendar/dispatch/overview" -> respond(
                            """{"configured":true,"projects":[{"id":"p-1","name":"Core"}],"tasks":[{"id":"t-1","title":"Spec","status":"todo","estimated_minutes":60,"time_blocks":[]}]}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                },
            ),
        )

        val health = assertIs<ApiResult.Success<CalendarHealthStatus>>(api.health(gatewayUrl)).value
        assertTrue(health.ok)
        assertTrue(health.hasHermesGoogleToken)

        val (accounts, calendars) = assertIs<ApiResult.Success<Pair<List<CalendarAccount>, List<CalendarInfo>>>>(api.listAccounts(gatewayUrl)).value
        assertEquals("google", accounts.single().provider)
        assertTrue(calendars.single().isTaskTarget)

        val events = assertIs<ApiResult.Success<List<CalendarEvent>>>(api.listEvents(gatewayUrl, "2026-11-26", "2026-11-26")).value
        assertTrue(events.single().isDispatchBlock)
        assertEquals("t-1", events.single().taskId)

        val overview = assertIs<ApiResult.Success<DispatchCalendarOverview>>(api.dispatchOverview(gatewayUrl)).value
        assertTrue(overview.configured)
        assertEquals(60, overview.tasks.single().estimatedMinutes)
    }

    @Test
    fun applyProposalReturnsConflictOutcomeOn409AndAppliesWithForce() = runTest {
        val api = CalendarApi(
            createHttpClient(
                MockEngine { req ->
                    assertEquals(HttpMethod.Post, req.method)
                    assertEquals("/api/plugins/motherbase-calendar/proposals/prop-1/apply", req.url.encodedPath)
                    val body = req.body.toByteArray().decodeToString()
                    if ("\"force\":true" in body) {
                        respond(
                            """{"proposal":{"id":"prop-1","title":"Plan","status":"applied","operations":[{"op_id":"op-1","kind":"schedule_task","title":"Spec","selected":true}]}}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                    } else {
                        respond(
                            """{"detail":"Conflict","conflict_detected":true,"conflicts":[{"op_id":"op-1","title":"Spec","conflicts":[{"id":"occ-2","title":"Standup"}]}],"proposal":{"id":"prop-1","title":"Plan","status":"staged","operations":[]}}""",
                            HttpStatusCode.Conflict,
                            jsonHeaders,
                        )
                    }
                },
            ),
        )

        val conflict = assertIs<ApiResult.Success<ApplyProposalOutcome>>(
            api.applyProposal(gatewayUrl, "prop-1", ApplyProposalRequest(selectedOpIds = listOf("op-1"), force = false)),
        ).value
        val c = assertIs<ApplyProposalOutcome.Conflict>(conflict)
        assertEquals("Standup", c.conflicts.single().conflicts.single().title)

        val applied = assertIs<ApiResult.Success<ApplyProposalOutcome>>(
            api.applyProposal(gatewayUrl, "prop-1", ApplyProposalRequest(selectedOpIds = listOf("op-1"), force = true)),
        ).value
        assertEquals(ScheduleProposal.STATUS_APPLIED, assertIs<ApplyProposalOutcome.Applied>(applied).proposal.status)
    }

    @Test
    fun getsAndSavesDedicatedCalendarAgentConfig() = runTest {
        val api = CalendarApi(
            createHttpClient(
                MockEngine { req ->
                    assertEquals("/api/plugins/motherbase-calendar/agent", req.url.encodedPath)
                    when (req.method) {
                        HttpMethod.Get -> respond(
                            """{"agent":{"profile":"arnold","session_id":"cal-sess-1","last_interaction_at":42.0}}""",
                            HttpStatusCode.OK,
                            jsonHeaders,
                        )
                        HttpMethod.Put -> {
                            val body = req.body.toByteArray().decodeToString()
                            assertTrue("cal-sess-2" in body)
                            respond(
                                """{"agent":{"profile":"ops","session_id":"cal-sess-2","last_interaction_at":99.0}}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        }
                        else -> respond("{}", HttpStatusCode.MethodNotAllowed, jsonHeaders)
                    }
                },
            ),
        )

        val current = assertIs<ApiResult.Success<CalendarAgentConfig>>(api.getAgentConfig(gatewayUrl)).value
        assertEquals("arnold", current.profile)
        assertEquals("cal-sess-1", current.sessionId)

        val saved = assertIs<ApiResult.Success<CalendarAgentConfig>>(
            api.saveAgentConfig(gatewayUrl, CalendarAgentUpdate(profile = "ops", sessionId = "cal-sess-2")),
        ).value
        assertEquals("ops", saved.profile)
        assertEquals("cal-sess-2", saved.sessionId)
    }
}
