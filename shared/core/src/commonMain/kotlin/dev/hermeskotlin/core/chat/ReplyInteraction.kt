package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.tasks.TweakDeltaInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

const val OFFER_CHOICES_TOOL = "offer_choices"
const val PROPOSE_ACTION_TOOL = "propose_action_plan"

/** Tools from the `motherbase-interact` plugin that render as interactive reply cards instead of raw tool rows. */
val INTERACTIVE_REPLY_TOOLS: Set<String> = setOf(OFFER_CHOICES_TOOL, PROPOSE_ACTION_TOOL)

private const val MAX_CHOICES = 8
private const val MAX_PARAMS = 4
private const val MAX_PARAM_OPTIONS = 6
private val MULTI_MODES = setOf("multiple", "multi", "multi_select", "multi-select", "multiple_choice")

/**
 * Non-blocking interactive prompt attached to the end of an assistant reply.
 * User actions either send a follow-up user turn immediately or load formatted text into the composer.
 * Designed as a closed hierarchy so interactive modes share the same reducer, persistence, and card
 * hosting pipeline without colliding with blocking [InputRequest].
 */
sealed interface ReplyInteraction {

    /** One selectable option inside [Choices] or [ActionCard]. */
    data class Option(
        val label: String,
        val description: String? = null,
        val value: String = label,
    )

    /** One inline tweakable parameter chip inside [ActionCard]. */
    data class Param(
        val key: String,
        val label: String,
        val options: List<String>,
        val defaultValue: String = options.firstOrNull().orEmpty(),
    )

    /** Single-choice or multiple-choice quick-reply options produced by [OFFER_CHOICES_TOOL]. */
    data class Choices(
        val question: String? = null,
        val multiSelect: Boolean = false,
        val options: List<Option>,
    ) : ReplyInteraction

    /**
     * Action card with inline parameter chips (`params`) that can be tweaked in one tap before approving
     * or sending an action, shared across chat replies and the Tasks Command Board right-rail.
     */
    data class ActionCard(
        val question: String? = null,
        val params: List<Param>,
        val options: List<Option>,
        val taskId: String? = null,
        val projectId: String? = null,
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
        ): ReplyInteraction? {
            val payload = resolvePayloadObject(rawToolName, args, result) ?: return null
            return when (toolName) {
                PROPOSE_ACTION_TOOL -> parseActionCardObject(payload)
                OFFER_CHOICES_TOOL -> parseActionCardObject(payload) ?: parseChoicesObject(payload)
                else -> null
            }
        }

        private fun resolvePayloadObject(
            rawToolName: String,
            args: JsonElement?,
            result: JsonElement?,
        ): JsonObject? {
            val resObj = parseJsonObject(result)
            if (resObj != null) {
                if (isRejectedResult(resObj)) return null
                return resObj
            }
            return unwrapToolCallArgs(rawToolName, args)
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
            return obj["error"] != null && obj["options"] == null && obj["actions"] == null
        }

        private fun unwrapToolCallArgs(toolName: String, args: JsonElement?): JsonObject? {
            val obj = parseJsonObject(args) ?: return null
            if (toolName != "tool_call") return obj
            val single = (obj["calls"] as? JsonArray)?.singleOrNull() as? JsonObject ?: obj
            return parseJsonObject(single["arguments"]) ?: obj
        }

        private fun parseActionCardObject(obj: JsonObject): ActionCard? {
            val params = parseParams(obj["params"])
            if (params.isEmpty()) return null
            val rawOptions = (obj["options"] ?: obj["actions"]) as? JsonArray ?: return null
            val parsedOptions = parseOptionsList(rawOptions)
            if (parsedOptions.isEmpty()) return null
            return ActionCard(
                question = obj.string("question") ?: obj.string("summary"),
                params = params,
                options = parsedOptions,
                taskId = obj.string("task_id"),
                projectId = obj.string("project_id"),
            )
        }

        private fun parseParams(element: JsonElement?): List<Param> {
            val arr = element as? JsonArray ?: return emptyList()
            val seenKeys = HashSet<String>()
            val out = ArrayList<Param>(minOf(arr.size, MAX_PARAMS))
            for (item in arr) {
                if (out.size >= MAX_PARAMS) break
                val obj = item as? JsonObject ?: continue
                val key = obj.string("key") ?: obj.string("name") ?: continue
                if (!seenKeys.add(key)) continue
                val label = obj.string("label") ?: key
                val rawOpts = (obj["options"] ?: obj["values"]) as? JsonArray ?: continue
                val opts = rawOpts.mapNotNull {
                    (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
                }.distinct().take(MAX_PARAM_OPTIONS)
                if (opts.isEmpty()) continue
                val defaultRaw = obj.string("default")
                val defaultValue = if (defaultRaw != null && defaultRaw in opts) defaultRaw else opts.first()
                out += Param(key = key, label = label, options = opts, defaultValue = defaultValue)
            }
            return out
        }

        private fun parseChoicesObject(obj: JsonObject): Choices? {
            val rawOptions = obj["options"] as? JsonArray ?: return null
            if (rawOptions.isEmpty()) return null

            val multi = when (val modeRaw = obj.string("mode")?.lowercase()) {
                null -> obj.boolean("multi_select") ?: false
                else -> modeRaw in MULTI_MODES
            }
            val question = obj.string("question")
            val parsed = parseOptionsList(rawOptions)
            if (parsed.isEmpty()) return null
            return Choices(question = question, multiSelect = multi, options = parsed)
        }

        private fun parseOptionsList(rawOptions: JsonArray): List<Option> {
            val seen = LinkedHashSet<Pair<String, String>>()
            val parsed = ArrayList<Option>(minOf(rawOptions.size, MAX_CHOICES))
            for (item in rawOptions) {
                if (parsed.size >= MAX_CHOICES) break
                val opt = parseOption(item) ?: continue
                if (seen.add(opt.label to opt.value)) parsed.add(opt)
            }
            return parsed
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

/** Formats an [ReplyInteraction.ActionCard] choice with its active inline parameter values. */
fun formatActionCardReply(
    option: ReplyInteraction.Option,
    params: List<ReplyInteraction.Param>,
    selected: Map<String, String>,
): String {
    val base = option.value.trim().ifEmpty { option.label.trim() }
    if (base.isEmpty()) return ""
    if (params.isEmpty()) return base
    val paramSummary = params.mapNotNull { param ->
        val value = (selected[param.key] ?: param.defaultValue).trim()
        value.takeIf { it.isNotEmpty() }?.let { "${param.label}: $it" }
    }
    return if (paramSummary.isEmpty()) base else "$base (${paramSummary.joinToString(", ")})"
}

/** Computes [TweakDeltaInput] records for every parameter whose chosen value differs from [ReplyInteraction.Param.defaultValue]. */
fun computeActionTweaks(
    params: List<ReplyInteraction.Param>,
    selected: Map<String, String>,
): List<TweakDeltaInput> = params.mapNotNull { param ->
    val chosen = selected[param.key] ?: param.defaultValue
    if (chosen == param.defaultValue || param.defaultValue.isBlank() || chosen.isBlank()) null
    else TweakDeltaInput(
        paramKey = param.key,
        paramLabel = param.label,
        agentProposed = param.defaultValue,
        userSelected = chosen,
    )
}
