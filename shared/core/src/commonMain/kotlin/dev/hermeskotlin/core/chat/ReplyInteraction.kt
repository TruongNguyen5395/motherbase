package dev.hermeskotlin.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

const val OFFER_CHOICES_TOOL = "offer_choices"

/** Tools from the `motherbase-interact` plugin that render as interactive reply cards instead of raw tool rows. */
val INTERACTIVE_REPLY_TOOLS: Set<String> = setOf(OFFER_CHOICES_TOOL)

private const val MAX_CHOICES = 8
private val MULTI_MODES = setOf("multiple", "multi", "multi_select", "multi-select", "multiple_choice")

/**
 * Non-blocking interactive prompt attached to the end of an assistant reply.
 * User actions either send a follow-up user turn immediately or load formatted text into the composer.
 * Designed as a closed hierarchy so future interactive modes (`Approval`, `Form`, etc.) share the same
 * reducer, persistence, and card hosting pipeline without colliding with blocking [InputRequest].
 */
sealed interface ReplyInteraction {

    /** One selectable option inside [Choices]. */
    data class Option(
        val label: String,
        val description: String? = null,
        val value: String = label,
    )

    /** Single-choice or multiple-choice quick-reply options produced by [OFFER_CHOICES_TOOL]. */
    data class Choices(
        val question: String? = null,
        val multiSelect: Boolean = false,
        val options: List<Option>,
    ) : ReplyInteraction

    companion object {
        /**
         * Parses an interactive reply payload from a live event (`tool.start` / `tool.complete`) or stored
         * transcript call, handling both direct invocations and deferred `tool_call` bridge invocations.
         * Prefers [result] when available so server-side normalization applies and `ok=false` suppresses invalid cards.
         */
        fun from(
            toolName: String,
            args: JsonElement?,
            result: JsonElement? = null,
            knownInteractiveTool: Boolean = false,
        ): ReplyInteraction? {
            val resolvedName = storedToolName(toolName, args)
            if (resolvedName in INTERACTIVE_REPLY_TOOLS || knownInteractiveTool) {
                return parseByToolName(
                    toolName = if (resolvedName in INTERACTIVE_REPLY_TOOLS) resolvedName else OFFER_CHOICES_TOOL,
                    rawToolName = toolName,
                    args = args,
                    result = result,
                )
            }
            if (toolName == "tool_call") {
                return parseBatchBridgeCall(args, result)
            }
            return null
        }

        private fun parseByToolName(
            toolName: String,
            rawToolName: String,
            args: JsonElement?,
            result: JsonElement?,
        ): ReplyInteraction? = when (toolName) {
            OFFER_CHOICES_TOOL -> parseOfferChoices(rawToolName, args, result)
            else -> null
        }

        private fun parseOfferChoices(
            rawToolName: String,
            args: JsonElement?,
            result: JsonElement?,
        ): Choices? {
            val resObj = parseJsonObject(result)
            if (resObj != null) {
                if (isRejectedResult(resObj)) return null
                parseChoicesObject(resObj)?.let { return it }
            }
            val unwrappedArgs = unwrapToolCallArgs(rawToolName, args)
            return unwrappedArgs?.let(::parseChoicesObject)
        }

        private fun parseBatchBridgeCall(args: JsonElement?, result: JsonElement?): ReplyInteraction? {
            val resObj = parseJsonObject(result)
            val resEntries = (resObj?.get("results") as? JsonArray)
                ?.mapNotNull { parseJsonObject(it) }
                .orEmpty()
            for (entry in resEntries) {
                val entryName = entry.string("name") ?: entry.string("tool") ?: continue
                if (entryName in INTERACTIVE_REPLY_TOOLS) {
                    val inner = parseJsonObject(entry["result"] ?: entry["output"] ?: entry) ?: continue
                    if (isRejectedResult(inner)) return null
                    parseByToolName(entryName, entryName, inner, inner)?.let { return it }
                }
            }
            val argObj = parseJsonObject(args) ?: return null
            val callEntry = (argObj["calls"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.lastOrNull { it.string("name") in INTERACTIVE_REPLY_TOOLS }
                ?: return null
            val targetName = callEntry.string("name") ?: return null
            val innerArgs = parseJsonObject(callEntry["arguments"])
            return parseByToolName(targetName, targetName, innerArgs, null)
        }

        private fun isRejectedResult(obj: JsonObject): Boolean {
            if (obj.boolean("ok") == false || obj.boolean("success") == false) return true
            return obj["error"] != null && obj["options"] == null
        }

        private fun unwrapToolCallArgs(toolName: String, args: JsonElement?): JsonObject? {
            val obj = parseJsonObject(args) ?: return null
            if (toolName != "tool_call") return obj
            val single = (obj["calls"] as? JsonArray)?.singleOrNull() as? JsonObject ?: obj
            return parseJsonObject(single["arguments"]) ?: obj
        }

        private fun parseChoicesObject(obj: JsonObject): Choices? {
            val rawOptions = obj["options"] as? JsonArray ?: return null
            if (rawOptions.isEmpty()) return null

            val multi = when (val modeRaw = obj.string("mode")?.lowercase()) {
                null -> obj.boolean("multi_select") ?: false
                else -> modeRaw in MULTI_MODES
            }
            val question = obj.string("question")

            val seen = LinkedHashSet<Pair<String, String>>()
            val parsed = ArrayList<Option>(minOf(rawOptions.size, MAX_CHOICES))
            for (item in rawOptions) {
                if (parsed.size >= MAX_CHOICES) break
                val opt = parseOption(item) ?: continue
                if (seen.add(opt.label to opt.value)) parsed.add(opt)
            }
            if (parsed.isEmpty()) return null
            return Choices(question = question, multiSelect = multi, options = parsed)
        }

        private fun parseOption(element: JsonElement): Option? = when (element) {
            is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { Option(label = it, value = it) }
            is JsonObject -> {
                val label = element.string("label") ?: element.string("title") ?: element.string("text") ?: return null
                val description = element.string("description") ?: element.string("subtitle")
                val value = element.string("value") ?: element.string("reply") ?: label
                Option(label = label, description = description, value = value)
            }
            else -> null
        }

        private fun parseJsonObject(element: JsonElement?): JsonObject? = when (element) {
            is JsonObject -> element
            is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull
                ?.let { ToolDetails.unwrapUntrusted(it).trim() }
                ?.takeIf { it.startsWith("{") }
                ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            else -> null
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonObject.boolean(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    }
}

/** Formats selected choices into the user prompt sent or inserted into the composer. */
fun formatInteractionChoices(selected: List<ReplyInteraction.Option>): String {
    val values = selected.mapNotNull { it.value.trim().takeIf(String::isNotEmpty) }
    if (values.isEmpty()) return ""
    return if (values.size == 1) values.first() else values.joinToString("\n") { "- $it" }
}
