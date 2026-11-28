package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.calendar.EventAttendee
import dev.hermeskotlin.core.calendar.ScheduleOperation
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.UpdateProposalRequest
import dev.hermeskotlin.core.chat.ChatMessage
import dev.hermeskotlin.core.chat.OFFER_CHOICES_TOOL
import dev.hermeskotlin.core.chat.ReplyInteraction
import dev.hermeskotlin.core.chat.ToolActivity
import dev.hermeskotlin.core.settings.AppSettings
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.LocalAppSettings
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReplyInteractionUiTest {

    @Test
    fun singleChoiceTapSendsAndEditOrLongPressFillsComposer() = runDesktopComposeUiTest(width = 640, height = 480) {
        val sent = mutableListOf<String>()
        val filled = mutableListOf<String>()
        val interaction = ReplyInteraction.Choices(
            question = "Next step?",
            multiSelect = false,
            options = listOf(
                ReplyInteraction.Option("Keep SQLite", description = "Zero ops", value = "Keep SQLite for now"),
                ReplyInteraction.Option("Migrate to Postgres"),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(420.dp)) {
                            ReplyInteractionCard(
                                replyKey = "reply-1",
                                interaction = interaction,
                                sendEnabled = true,
                                onSend = { sent += it },
                                onFill = { filled += it },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Next step?").assertIsDisplayed()
        onNodeWithText("Keep SQLite").assertIsDisplayed()
        onNodeWithText("Zero ops").assertIsDisplayed()

        onNodeWithTag("interaction_choice_option_0").performClick()
        waitForIdle()
        assertEquals(listOf("Keep SQLite for now"), sent)

        onNodeWithTag("interaction_choice_edit_1").performClick()
        waitForIdle()
        assertEquals(listOf("Migrate to Postgres"), filled)

        onNodeWithTag("interaction_choice_option_0").performTouchInput { longClick() }
        waitForIdle()
        assertEquals(listOf("Migrate to Postgres", "Keep SQLite for now"), filled)
    }

    @Test
    fun multiChoiceTogglesAccumulateAndSendOrEditTogether() = runDesktopComposeUiTest(width = 640, height = 480) {
        val sent = mutableListOf<String>()
        val filled = mutableListOf<String>()
        var connected by mutableStateOf(true)
        val interaction = ReplyInteraction.Choices(
            question = "Which checks should run?",
            multiSelect = true,
            options = listOf(
                ReplyInteraction.Option("Unit tests", value = "Run unit tests"),
                ReplyInteraction.Option("UI tests", value = "Run UI tests"),
                ReplyInteraction.Option("Docs check"),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(420.dp)) {
                            ReplyInteractionCard(
                                replyKey = "reply-2",
                                interaction = interaction,
                                sendEnabled = connected,
                                onSend = { sent += it },
                                onFill = { filled += it },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("interaction_choices_send").assertIsNotEnabled()
        onNodeWithTag("interaction_choices_edit").assertIsNotEnabled()

        onNodeWithTag("interaction_choice_option_0").performClick()
        onNodeWithTag("interaction_choice_option_2").performClick()
        waitForIdle()

        onNodeWithText("Send (2)").assertIsDisplayed()
        onNodeWithTag("interaction_choices_send").assertIsEnabled()
        onNodeWithTag("interaction_choices_edit").assertIsEnabled()

        onNodeWithTag("interaction_choices_edit").performClick()
        waitForIdle()
        assertEquals(listOf("- Run unit tests\n- Docs check"), filled)

        onNodeWithTag("interaction_choices_send").performClick()
        waitForIdle()
        assertEquals(listOf("- Run unit tests\n- Docs check"), sent)

        connected = false
        waitForIdle()
        onNodeWithTag("interaction_choices_send").assertIsNotEnabled()
        onNodeWithTag("interaction_choices_edit").assertIsEnabled()
    }

    @Test
    fun assistantReplyHidesInteractiveToolPillAndGuardsVisibilityByTurnState() = runDesktopComposeUiTest(width = 640, height = 480) {
        var showInteraction by mutableStateOf(true)
        var streaming by mutableStateOf(false)
        val interaction = ReplyInteraction.Choices(
            question = "Approve?",
            multiSelect = false,
            options = listOf(ReplyInteraction.Option("Yes"), ReplyInteraction.Option("No")),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(
                    LocalDesktopLayout provides true,
                    LocalAppSettings provides AppSettings(showToolActivity = true),
                ) {
                    ProvideAppWords {
                        Box(Modifier.width(480.dp)) {
                            AssistantReply(
                                message = ChatMessage.Assistant(
                                    key = "reply-3",
                                    text = "Ready to deploy.",
                                    streaming = streaming,
                                    tools = listOf(
                                        ToolActivity(
                                            id = "tool-1",
                                            name = OFFER_CHOICES_TOOL,
                                            running = false,
                                            interaction = interaction,
                                        ),
                                    ),
                                ),
                                onBranch = null,
                                onRegenerate = null,
                                last = true,
                                showInteraction = showInteraction,
                                interactionSendEnabled = true,
                                onInteractionSend = {},
                                onInteractionFill = {},
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("reply_interaction_card").assertIsDisplayed()
        onNodeWithText("Yes").assertIsDisplayed()
        assertTrue(onNodeWithText("Ran 1 tool").let { runCatching { it.assertIsDisplayed() }.isFailure })

        streaming = true
        waitForIdle()
        onNodeWithTag("reply_interaction_card").assertDoesNotExist()

        streaming = false
        showInteraction = false
        waitForIdle()
        onNodeWithTag("reply_interaction_card").assertDoesNotExist()
    }

    @Test
    fun actionCardTweaksParameterChipBeforeSendingAndFitsNarrowPhoneWidth() = runDesktopComposeUiTest(width = 360, height = 520) {
        val sent = mutableListOf<String>()
        val filled = mutableListOf<String>()
        val interaction = ReplyInteraction.ActionCard(
            question = "Next step for task",
            params = listOf(
                ReplyInteraction.Param(key = "bot", label = "Runner", options = listOf("@coder", "@architect"), defaultValue = "@coder"),
                ReplyInteraction.Param(key = "scope", label = "Scope", options = listOf("Prototype", "Full PR"), defaultValue = "Prototype"),
            ),
            options = listOf(
                ReplyInteraction.Option(label = "Approve & Run", value = "Proceed"),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(340.dp)) {
                            ReplyInteractionCard(
                                replyKey = "action-1",
                                interaction = interaction,
                                sendEnabled = true,
                                onSend = { sent += it },
                                onFill = { filled += it },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("interaction_param_bot").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithText("@architect").assertIsDisplayed()

        onNodeWithTag("interaction_choice_option_0").performClick()
        waitForIdle()
        assertEquals(listOf("Proceed (Runner: @architect, Scope: Prototype)"), sent)

        onNodeWithTag("interaction_choice_edit_0").performClick()
        waitForIdle()
        assertEquals(listOf("Proceed (Runner: @architect, Scope: Prototype)"), filled)
    }

    @Test
    fun scheduleProposalSelectsOperationsAndAppliesOnlyCheckedItems() = runDesktopComposeUiTest(width = 480, height = 560) {
        var appliedIds: List<String>? = null
        var discarded = false
        val proposal = ScheduleProposal(
            id = "prop-1",
            title = "Thursday Plan",
            summary = "Protect morning deep work",
            targetDate = "2026-11-26",
            operations = listOf(
                ScheduleOperation(
                    opId = "op-1",
                    kind = "schedule_task",
                    title = "Write API spec",
                    startAt = "2026-11-26T02:00:00Z",
                    endAt = "2026-11-26T03:00:00Z",
                    calendarName = "MotherBase",
                ),
                ScheduleOperation(
                    opId = "op-2",
                    kind = "create_event",
                    title = "Partner sync",
                    startAt = "2026-11-26T08:00:00Z",
                    endAt = "2026-11-26T09:00:00Z",
                    attendees = listOf(EventAttendee(email = "partner@example.com")),
                    hasExternalAttendees = true,
                ),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(340.dp)) {
                            ReplyInteractionCard(
                                replyKey = "sched-1",
                                interaction = ReplyInteraction.ScheduleProposal(proposal),
                                sendEnabled = true,
                                onSend = {},
                                onFill = {},
                                onApplyProposal = { _, ids, _ -> appliedIds = ids },
                                onDiscardProposal = { discarded = true },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Thursday Plan").assertIsDisplayed()
        onNodeWithText("Will notify 1 external attendee(s)").assertIsDisplayed()
        onNodeWithTag("proposal_op_row_1").performClick()
        waitForIdle()
        onNodeWithTag("proposal_apply_button").performClick()
        waitForIdle()
        assertEquals(listOf("op-1"), appliedIds)
        onNodeWithTag("proposal_discard_button").performClick()
        waitForIdle()
        assertTrue(discarded)
    }

    @Test
    fun revertedScheduleProposalAllowsEditRestageAndDelete() = runDesktopComposeUiTest {
        var restaged = false
        var deleted = false
        var savedUpdate: UpdateProposalRequest? = null
        val proposal = ScheduleProposal(
            id = "prop-rev",
            title = "Friday Plan",
            targetDate = "2026-11-27",
            status = ScheduleProposal.STATUS_REVERTED,
            operations = listOf(
                ScheduleOperation(
                    opId = "op-1",
                    kind = "create_event",
                    title = "Deep Work",
                    startAt = "2026-11-27T02:00:00Z",
                    endAt = "2026-11-27T03:00:00Z",
                ),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(340.dp)) {
                            ReplyInteractionCard(
                                replyKey = "sched-rev",
                                interaction = ReplyInteraction.ScheduleProposal(proposal),
                                sendEnabled = true,
                                onSend = {},
                                onFill = {},
                                onUpdateProposal = { _, req -> savedUpdate = req },
                                onRestageProposal = { restaged = true },
                                onDeleteProposal = { deleted = true },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Reverted").assertIsDisplayed()
        onNodeWithTag("proposal_restage_button").performClick()
        waitForIdle()
        assertTrue(restaged)

        onNodeWithTag("proposal_op_edit_0").performClick()
        waitForIdle()
        onNodeWithText("Edit item").assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput(" v2")
        onNodeWithText("Save changes").performClick()
        waitForIdle()
        assertEquals(ScheduleProposal.STATUS_STAGED, savedUpdate?.status)
        assertTrue(savedUpdate?.operations?.singleOrNull()?.title?.contains("Deep Work") == true)

        onNodeWithTag("proposal_delete_button").performClick()
        waitForIdle()
        assertTrue(deleted)
    }
}
