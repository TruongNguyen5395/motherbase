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
import dev.hermeskotlin.core.chat.QuickChoiceGroup
import dev.hermeskotlin.core.chat.QuickChoiceOption
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
class QuickChoicesTest {

    @Test
    fun singleChoiceTapSendsAndEditOrLongPressFillsComposer() = runDesktopComposeUiTest(width = 640, height = 480) {
        val sent = mutableListOf<String>()
        val filled = mutableListOf<String>()
        val group = QuickChoiceGroup(
            question = "Next step?",
            multiSelect = false,
            options = listOf(
                QuickChoiceOption("Keep SQLite", description = "Zero ops", value = "Keep SQLite for now"),
                QuickChoiceOption("Migrate to Postgres"),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(420.dp)) {
                            QuickChoicesCard(
                                replyKey = "reply-1",
                                group = group,
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

        onNodeWithTag("quick_choice_option_0").performClick()
        waitForIdle()
        assertEquals(listOf("Keep SQLite for now"), sent)

        onNodeWithTag("quick_choice_edit_1").performClick()
        waitForIdle()
        assertEquals(listOf("Migrate to Postgres"), filled)

        onNodeWithTag("quick_choice_option_0").performTouchInput { longClick() }
        waitForIdle()
        assertEquals(listOf("Migrate to Postgres", "Keep SQLite for now"), filled)
    }

    @Test
    fun multiChoiceTogglesAccumulateAndSendOrEditTogether() = runDesktopComposeUiTest(width = 640, height = 480) {
        val sent = mutableListOf<String>()
        val filled = mutableListOf<String>()
        var connected by mutableStateOf(true)
        val group = QuickChoiceGroup(
            question = "Which checks should run?",
            multiSelect = true,
            options = listOf(
                QuickChoiceOption("Unit tests", value = "Run unit tests"),
                QuickChoiceOption("UI tests", value = "Run UI tests"),
                QuickChoiceOption("Docs check"),
            ),
        )

        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(LocalDesktopLayout provides true) {
                    ProvideAppWords {
                        Box(Modifier.width(420.dp)) {
                            QuickChoicesCard(
                                replyKey = "reply-2",
                                group = group,
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
        onNodeWithTag("quick_choices_send").assertIsNotEnabled()
        onNodeWithTag("quick_choices_edit").assertIsNotEnabled()

        onNodeWithTag("quick_choice_option_0").performClick()
        onNodeWithTag("quick_choice_option_2").performClick()
        waitForIdle()

        onNodeWithText("Send (2)").assertIsDisplayed()
        onNodeWithTag("quick_choices_send").assertIsEnabled()
        onNodeWithTag("quick_choices_edit").assertIsEnabled()

        onNodeWithTag("quick_choices_edit").performClick()
        waitForIdle()
        assertEquals(listOf("- Run unit tests\n- Docs check"), filled)

        onNodeWithTag("quick_choices_send").performClick()
        waitForIdle()
        assertEquals(listOf("- Run unit tests\n- Docs check"), sent)

        connected = false
        waitForIdle()
        onNodeWithTag("quick_choices_send").assertIsNotEnabled()
        onNodeWithTag("quick_choices_edit").assertIsEnabled()
    }

    @Test
    fun assistantReplyHidesOfferChoicesToolPillAndGuardsVisibilityByTurnState() = runDesktopComposeUiTest(width = 640, height = 480) {
        var showQuick by mutableStateOf(true)
        var streaming by mutableStateOf(false)
        val group = QuickChoiceGroup(
            question = "Approve?",
            multiSelect = false,
            options = listOf(QuickChoiceOption("Yes"), QuickChoiceOption("No")),
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
                                            quickChoices = group,
                                        ),
                                    ),
                                ),
                                onBranch = null,
                                onRegenerate = null,
                                last = true,
                                showQuickChoices = showQuick,
                                quickChoiceSendEnabled = true,
                                onSendChoice = {},
                                onFillChoice = {},
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("quick_choices_card").assertIsDisplayed()
        onNodeWithText("Yes").assertIsDisplayed()
        assertTrue(onNodeWithText("Ran 1 tool").let { runCatching { it.assertIsDisplayed() }.isFailure })

        streaming = true
        waitForIdle()
        onNodeWithTag("quick_choices_card").assertDoesNotExist()

        streaming = false
        showQuick = false
        waitForIdle()
        onNodeWithTag("quick_choices_card").assertDoesNotExist()
    }
}
