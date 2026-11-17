package dev.hermeskotlin.core.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

const val OFFER_CHOICES_TOOL = "offer_choices"

private const val MAX_CHOICES = 8
private val MULTI_MODES = setOf("multiple", "multi", "multi_select", "multi-select", "multiple_choice")

/** One clickable quick-reply option attached to an assistant reply by [OFFER_CHOICES_TOOL]. */
data class QuickChoiceOption(
    val label: String,
    val description: String? = null,
    val value: String = label,
)

/**
 * Non-blocking quick-reply buttons offered by the `offer_choices` Hermes plugin.
 * Clicking in single-choice mode sends [QuickChoiceOption.value] directly (or fills the composer);
 * [multiSelect] lets the user toggle multiple picks before sending or editing.
 */
data class QuickChoiceGroup(
    val question: String? = null,
    val multiSelect: Boolean = false,
    val options: List<QuickChoiceOption>,
) {
    companion object {
        /**
         * Parses quick choices from a live event (`tool.start` / `tool.complete`) or stored transcript call.
         * Supports both direct `offer_choices` invocations and deferred `tool_call` bridge invocations.
         * Prefers [result] when present (so validation failures `ok=false` suppress broken cards and
         * server-normalized fields win), falling back to [args] while the call is still running or if
         * the gateway returned plain text.
         */
        fun from(
            toolName: String,
            args: JsonElement?,
            result: JsonElement? = null,
            knownOfferChoices: Boolean = false,
        ): QuickChoiceGroup? {
            val normalizedName = storedToolName(toolName, args)
            val isOfferTool = knownOfferChoices || normalizedName == OFFER_CHOICES_TOOL
            if (!isOfferTool && toolName != "tool_call") return null

            if (isOfferTool) {
                val resObj = parseJsonObject(result)
                if (resObj != null) {
                    if (resObj.boolean("ok") == false || resObj.boolean("success") == false) return null
                    if (resObj["error"] != null && resObj["options"] == null) return null
                    parseChoiceObject(resObj)?.let { return it }
                }
                val unwrappedArgs = unwrapToolCallArgs(toolName, args)
                return unwrappedArgs?.let(::parseChoiceObject)
            }

            // Batched `tool_call` where `calls` had multiple entries: inspect matching result/call entry.
            val batchResult = parseBatchOfferChoices(args, result)
            return batchResult
        }

        private fun parseBatchOfferChoices(args: JsonElement?, result: JsonElement?): QuickChoiceGroup? {
            val resObj = parseJsonObject(result)
            val resEntries = (resObj?.get("results") as? JsonArray)
                ?.mapNotNull { parseJsonObject(it) }
                .orEmpty()
            for (entry in resEntries) {
                val entryName = entry.string("name") ?: entry.string("tool")
                if (entryName == OFFER_CHOICES_TOOL) {
                    val inner = parseJsonObject(entry["result"] ?: entry["output"] ?: entry)
                    if (inner != null) {
                        if (inner.boolean("ok") == false || inner.boolean("success") == false) return null
                        parseChoiceObject(inner)?.let { return it }
                    }
                }
            }
            val argObj = parseJsonObject(args) ?: return null
            val callEntry = (argObj["calls"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.lastOrNull { it.string("name") == OFFER_CHOICES_TOOL }
                ?: return null
            return parseJsonObject(callEntry["arguments"])?.let(::parseChoiceObject)
        }

        private fun unwrapToolCallArgs(toolName: String, args: JsonElement?): JsonObject? {
            val obj = parseJsonObject(args) ?: return null
            if (toolName != "tool_call") return obj
            val single = (obj["calls"] as? JsonArray)?.singleOrNull() as? JsonObject ?: obj
            return parseJsonObject(single["arguments"]) ?: obj
        }

        private fun parseChoiceObject(obj: JsonObject): QuickChoiceGroup? {
            val rawOptions = obj["options"] as? JsonArray ?: return null
            if (rawOptions.isEmpty()) return null

            val multi = when (val modeRaw = obj.string("mode")?.lowercase()) {
                null -> obj.boolean("multi_select") ?: false
                else -> modeRaw in MULTI_MODES
            }
            val question = obj.string("question")

            val seen = LinkedHashSet<Pair<String, String>>()
            val parsed = ArrayList<QuickChoiceOption>(minOf(rawOptions.size, MAX_CHOICES))
            for (item in rawOptions) {
                if (parsed.size >= MAX_CHOICES) break
                val opt = parseOption(item) ?: continue
                if (seen.add(opt.label to opt.value)) parsed.add(opt)
            }
            if (parsed.isEmpty()) return null
            return QuickChoiceGroup(question = question, multiSelect = multi, options = parsed)
        }

        private fun parseOption(element: JsonElement): QuickChoiceOption? = when (element) {
            is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { QuickChoiceOption(label = it, value = it) }
            is JsonObject -> {
                val label = element.string("label") ?: element.string("title") ?: element.string("text") ?: return null
                val description = element.string("description") ?: element.string("subtitle")
                val value = element.string("value") ?: element.string("reply") ?: label
                QuickChoiceOption(label = label, description = description, value = value)
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

/** Formats chosen options into the reply message sent or placed into the composer. */
fun formatQuickChoiceReply(selected: List<QuickChoiceOption>): String {
    val values = selected.mapNotNull { it.value.trim().takeIf(String::isNotEmpty) }
    if (values.isEmpty()) return ""
    return if (values.size == 1) values.first() else values.joinToString("\n") { "- $it" }
}
