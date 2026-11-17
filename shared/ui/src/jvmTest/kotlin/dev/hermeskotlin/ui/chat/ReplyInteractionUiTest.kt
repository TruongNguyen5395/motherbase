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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.ColorScheme
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
}
