package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.media.GatewayFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A file created or modified by the agent via a file tool in this chat. */
data class ModifiedFileArtifact(
    val path: String,
    val name: String,
    val folder: String,
    val running: Boolean = false,
)

/** A file or image uploaded by the user in this chat. */
data class UploadArtifact(
    val attachment: ShownAttachment,
) {
    val name: String get() = attachment.name
    val kind: AttachmentKind get() = attachment.kind
    val gatewayPath: String? get() = attachment.gatewayPath
    val canPreview: Boolean get() = attachment.gatewayPath != null || attachment.original != null
}

/** A pre-existing gateway file linked in chat replies/prompts or opened in the viewer during this chat. */
data class ReferencedFileArtifact(
    val path: String,
    val name: String,
    val folder: String,
    val line: Int? = null,
)

/** Per-conversation artifact history shown in the right sidebar. */
data class ChatArtifacts(
    val modifiedFiles: List<ModifiedFileArtifact> = emptyList(),
    val uploads: List<UploadArtifact> = emptyList(),
    val referencedFiles: List<ReferencedFileArtifact> = emptyList(),
) {
    val isEmpty: Boolean get() = modifiedFiles.isEmpty() && uploads.isEmpty() && referencedFiles.isEmpty()
}

private val FILE_EDIT_TOOLS = setOf("write_file", "patch", "edit_file", "append_file")
private val V4A_PATH_LINE = Regex("""^\*\*\* (?:Update File|Add File|Move to):\s*(.+)$""", RegexOption.MULTILINE)

/**
 * Extracts file paths touched by file-modifying tools (`write_file`, `patch`, `edit_file`, `append_file`)
 * from either live event payloads or stored tool call arguments/results. Prefers gateway-resolved paths
 * returned in [result] when present, falling back to [args].
 */
internal fun extractEditedFilePaths(
    toolName: String,
    args: JsonElement?,
    result: JsonElement? = null,
): List<String> {
    if (toolName.trim().lowercase() !in FILE_EDIT_TOOLS) return emptyList()
    val fromResult = parseObject(result)?.let { obj ->
        buildList {
            (obj["files_modified"] as? JsonArray)?.forEach { item ->
                (item as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            (obj["resolved_path"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
    }.orEmpty().distinct()
    if (fromResult.isNotEmpty()) return fromResult

    val argObj = parseObject(args) ?: return emptyList()
    return buildList {
        listOf("path", "file_path").forEach { key ->
            (argObj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
        (argObj["patch"] as? JsonPrimitive)?.contentOrNull?.let { patchText ->
            V4A_PATH_LINE.findAll(patchText).forEach { match ->
                match.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }.distinct()
}

private fun parseObject(element: JsonElement?): JsonObject? = when (element) {
    is JsonObject -> element
    is JsonPrimitive -> element.contentOrNull
        ?.let { ToolDetails.unwrapUntrusted(it).trim() }
        ?.takeIf { it.startsWith("{") }
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    else -> null
}

private data class SplitArtifactPath(
    val raw: String,
    val normalized: String,
    val name: String,
    val folder: String,
)

private fun splitArtifactPath(rawPath: String): SplitArtifactPath? {
    val trimmed = rawPath.trim()
    if (trimmed.isEmpty()) return null
    val normalized = trimmed.trimEnd('/', '\\').replace('\\', '/')
    if (normalized.isEmpty()) return null
    val name = normalized.substringAfterLast('/').ifEmpty { trimmed }
    val folder = normalized.substringBeforeLast('/', "")
    return SplitArtifactPath(raw = trimmed, normalized = normalized, name = name, folder = folder)
}

/**
 * Collects per-conversation [ModifiedFileArtifact]s, [UploadArtifact]s, and [ReferencedFileArtifact]s
 * (newest first, cross-deduped so modified and uploaded files do not repeat under referenced files)
 * from loaded [messages] and any additionally [openedFiles].
 */
fun extractChatArtifacts(
    messages: List<ChatMessage>,
    openedFiles: List<GatewayFile> = emptyList(),
): ChatArtifacts {
    val modified = mutableListOf<ModifiedFileArtifact>()
    val seenModified = mutableSetOf<String>()
    val uploads = mutableListOf<UploadArtifact>()
    val seenUploadKeys = mutableSetOf<String>()
    val seenUploadPaths = mutableSetOf<String>()

    messages.asReversed().forEach { message ->
        when (message) {
            is ChatMessage.Assistant -> message.tools.asReversed().forEach { tool ->
                if (tool.failed || tool.editedFiles.isEmpty()) return@forEach
                tool.editedFiles.asReversed().forEach { rawPath ->
                    val parts = splitArtifactPath(rawPath) ?: return@forEach
                    if (seenModified.add(parts.normalized)) {
                        modified += ModifiedFileArtifact(
                            path = parts.raw,
                            name = parts.name,
                            folder = parts.folder,
                            running = tool.running,
                        )
                    }
                }
            }
            is ChatMessage.User -> message.attachments.asReversed().forEach { attachment ->
                val gatewayNorm = attachment.gatewayPath?.let(::splitArtifactPath)?.normalized
                val dedupKey = gatewayNorm ?: attachment.key
                if (seenUploadKeys.add(dedupKey)) {
                    if (gatewayNorm != null) seenUploadPaths += gatewayNorm
                    uploads += UploadArtifact(attachment)
                }
            }
            else -> Unit
        }
    }

    val seenReferenced = (seenModified + seenUploadPaths).toMutableSet()
    val fromMessages = mutableListOf<ReferencedFileArtifact>()
    messages.asReversed().forEach { message ->
        val linked = when (message) {
            is ChatMessage.Assistant -> {
                val generated = message.tools.mapNotNull { it.generatedImage }
                extractLinkedFiles(message.text, generated)
            }
            is ChatMessage.User -> extractLinkedFiles(message.text)
            else -> emptyList()
        }
        linked.asReversed().forEach { file ->
            val parts = splitArtifactPath(file.path) ?: return@forEach
            if (seenReferenced.add(parts.normalized)) {
                fromMessages += ReferencedFileArtifact(
                    path = parts.raw,
                    name = parts.name,
                    folder = parts.folder,
                    line = file.line,
                )
            }
        }
    }

    val extraOpened = buildList {
        openedFiles.forEach { file ->
            if (file.path.startsWith("Chat artifact/")) return@forEach
            val parts = splitArtifactPath(file.path) ?: return@forEach
            if (seenReferenced.add(parts.normalized)) {
                add(
                    ReferencedFileArtifact(
                        path = parts.raw,
                        name = parts.name,
                        folder = parts.folder,
                        line = file.line,
                    ),
                )
            }
        }
    }

    return ChatArtifacts(
        modifiedFiles = modified,
        uploads = uploads,
        referencedFiles = extraOpened + fromMessages,
    )
}
