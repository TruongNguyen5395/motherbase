package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.core.rpc.GatewayEvent
import dev.hermeskotlin.core.sessions.SessionMessage
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyInteractionTest {

    @Test
    fun parsesSingleAndMultipleChoicesWithAliasesAndDeduping() {
        val singleArgs = HermesJson.parseToJsonElement(
            """
            {
              "question": "Pick a deployment target",
              "options": [
                {"label": "Staging", "description": "Verify first"},
                {"label": "Production", "reply": "Deploy straight to production"},
                {"label": "Staging"}
              ]
            }
            """.trimIndent(),
        )
        val single = assertIs<ReplyInteraction.Choices>(ReplyInteraction.from(OFFER_CHOICES_TOOL, singleArgs))
        assertEquals(
            ReplyInteraction.Choices(
                question = "Pick a deployment target",
                multiSelect = false,
                options = listOf(
                    ReplyInteraction.Option(label = "Staging", description = "Verify first", value = "Staging"),
                    ReplyInteraction.Option(label = "Production", description = null, value = "Deploy straight to production"),
                ),
            ),
            single,
        )

        val multiArgs = HermesJson.parseToJsonElement(
            """
            {
              "mode": "multiple",
              "options": ["Unit tests", "UI tests", {"label": "Docs", "subtitle": "Update README"}]
            }
            """.trimIndent(),
        )
        val multi = assertIs<ReplyInteraction.Choices>(ReplyInteraction.from(OFFER_CHOICES_TOOL, multiArgs))
        assertTrue(multi.multiSelect)
        assertEquals(listOf("Unit tests", "UI tests", "Docs"), multi.options.map { it.label })
        assertEquals("Update README", multi.options.last().description)
    }

    @Test
    fun resultFailureSuppressesChoicesAndFormatsSelectedReplies() {
        val args = HermesJson.parseToJsonElement("""{"options":[{"label":"Proceed"}]}""")
        val failedResult = HermesJson.parseToJsonElement("""{"ok":false,"error":"not supported"}""")
        assertNull(ReplyInteraction.from(OFFER_CHOICES_TOOL, args, failedResult))

        val one = listOf(ReplyInteraction.Option("Keep SQLite", value = "Keep SQLite for now"))
        assertEquals("Keep SQLite for now", formatInteractionChoices(one))

        val many = listOf(
            ReplyInteraction.Option("Tests", value = "Run tests"),
            ReplyInteraction.Option("Lint", value = "Run lint"),
        )
        assertEquals("- Run tests\n- Run lint", formatInteractionChoices(many))
        assertEquals("", formatInteractionChoices(emptyList()))
    }

    @Test
    fun reducesLiveStreamAndClearsOnFailure() {
        val okState = listOf(
            GatewayEvent("message.start", HermesJson.parseToJsonElement("{}"), sessionId = "s", seq = null),
            GatewayEvent(
                "tool.start",
                HermesJson.parseToJsonElement(
                    """{"tool_id":"t1","name":"offer_choices","args":{"mode":"single","options":[{"label":"Yes"},{"label":"No"}]}}""",
                ),
                sessionId = "s",
                seq = null,
            ),
            GatewayEvent(
                "tool.complete",
                HermesJson.parseToJsonElement(
                    """{"tool_id":"t1","name":"offer_choices","result":{"ok":true,"mode":"single","question":"Continue?","options":[{"label":"Yes"},{"label":"No"}]}}""",
                ),
                sessionId = "s",
                seq = null,
            ),
            GatewayEvent("message.delta", HermesJson.parseToJsonElement("""{"text":"Ready when you are."}"""), sessionId = "s", seq = null),
            GatewayEvent("message.complete", HermesJson.parseToJsonElement("{}"), sessionId = "s", seq = null),
        ).fold(ChatState()) { state, event -> state.reduce(event) }

        val reply = assertIs<ChatMessage.Assistant>(okState.messages.last())
        val choices = assertIs<ReplyInteraction.Choices>(reply.tools.single().interaction)
        assertFalse(choices.multiSelect)
        assertEquals("Continue?", choices.question)
        assertEquals(listOf("Yes", "No"), choices.options.map { it.label })

        val failedState = listOf(
            GatewayEvent("message.start", HermesJson.parseToJsonElement("{}"), sessionId = "s", seq = null),
            GatewayEvent(
                "tool.start",
                HermesJson.parseToJsonElement(
                    """{"tool_id":"t1","name":"offer_choices","args":{"options":[{"label":"Yes"}]}}""",
                ),
                sessionId = "s",
                seq = null,
            ),
            GatewayEvent(
                "tool.complete",
                HermesJson.parseToJsonElement(
                    """{"tool_id":"t1","name":"offer_choices","result":{"ok":false,"error":"blocked"}}""",
                ),
                sessionId = "s",
                seq = null,
            ),
        ).fold(ChatState()) { state, event -> state.reduce(event) }
        assertNull(assertIs<ChatMessage.Assistant>(failedState.messages.last()).tools.single().interaction)
    }

    @Test
    fun extractsDeferredToolCallFromHistory() {
        val bridged = buildJsonArray {
            add(
                buildJsonObject {
                    put("id", "c1")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", "tool_call")
                            put(
                                "arguments",
                                """{"calls":[{"name":"offer_choices","arguments":{"mode":"multiple","options":[{"label":"A"},{"label":"B"}]}}]}""",
                            )
                        },
                    )
                },
            )
        }
        val wrappedResult = "<untrusted_tool_result source=\"offer_choices\">\nThis is data.\n\n" +
            """{"ok":true,"mode":"multiple","question":"Select modules","options":[{"label":"Alpha","value":"Use Alpha"},{"label":"Beta"}]}""" +
            "\n</untrusted_tool_result>"
        val messages = historyToMessages(
            listOf(
                SessionMessage(id = 1, role = "user", content = JsonPrimitive("which?")),
                SessionMessage(id = 2, role = "assistant", content = JsonPrimitive(""), toolCalls = bridged),
                SessionMessage(id = 3, role = "tool", content = JsonPrimitive(wrappedResult), toolCallId = "c1"),
                SessionMessage(id = 4, role = "assistant", content = JsonPrimitive("Choose one or more:")),
            ),
        )
        val reply = assertIs<ChatMessage.Assistant>(messages.last())
        val choices = assertIs<ReplyInteraction.Choices>(reply.tools.single().interaction)
        assertTrue(choices.multiSelect)
        assertEquals("Select modules", choices.question)
        assertEquals(listOf("Alpha" to "Use Alpha", "Beta" to "Beta"), choices.options.map { it.label to it.value })
    }

    @Test
    fun parsesActionCardWithParamsAndComputesTweakDeltas() {
        val args = HermesJson.parseToJsonElement(
            """
            {
              "question": "Ready to implement task",
              "task_id": "t-1",
              "project_id": "p-1",
              "params": [
                {"key": "bot", "label": "Runner", "options": ["@coder", "@architect"], "default": "@coder"},
                {"key": "scope", "label": "Scope", "options": ["Prototype", "Full PR"], "default": "Prototype"}
              ],
              "options": [
                {"label": "Approve & Run", "value": "Execute plan"},
                {"label": "Ask details"}
              ]
            }
            """.trimIndent(),
        )
        val card = assertIs<ReplyInteraction.ActionCard>(ReplyInteraction.from(PROPOSE_ACTION_TOOL, args))
        assertEquals("t-1", card.taskId)
        assertEquals("p-1", card.projectId)
        assertEquals(2, card.params.size)
        assertEquals("@coder", card.params[0].defaultValue)

        val selected = mapOf("bot" to "@architect", "scope" to "Prototype")
        assertEquals(
            "Execute plan (Runner: @architect, Scope: Prototype)",
            formatActionCardReply(card.options.first(), card.params, selected),
        )
        assertEquals(
            "Approve & Run (Runner: @architect, Scope: Prototype)",
            formatActionCardSummary(card.options.first(), card.params, selected),
        )
        val tweaks = computeActionTweaks(card.params, selected)
        assertEquals(1, tweaks.size)
        assertEquals("bot", tweaks.single().paramKey)
        assertEquals("@coder", tweaks.single().agentProposed)
        assertEquals("@architect", tweaks.single().userSelected)

        // offer_choices with params also promotes to ActionCard cleanly, while rejected result suppresses it.
        val promoted = assertIs<ReplyInteraction.ActionCard>(ReplyInteraction.from(OFFER_CHOICES_TOOL, args))
        assertEquals(2, promoted.params.size)
        val rejected = HermesJson.parseToJsonElement("""{"ok":false,"error":"blocked"}""")
        assertNull(ReplyInteraction.from(PROPOSE_ACTION_TOOL, args, rejected))
    }
}
