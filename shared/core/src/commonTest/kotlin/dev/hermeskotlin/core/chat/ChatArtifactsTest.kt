package dev.hermeskotlin.core.chat

import dev.hermeskotlin.core.sessions.SessionMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatArtifactsTest {

    @Test
    fun extractsModifiedFilesFromWritePatchAndHistoryExcludingFailedTools() {
        val storedPatch = """
            {"mode":"patch","patch":"*** Begin Patch\n*** Update File: src/Main.kt\n@@\n*** Add File: docs/readme.md\n*** Delete File: old.txt\n*** End Patch"}
        """.trimIndent()
        val storedWrite = """{"path":"/opt/data/report.pdf","content":"..."}"""
        val storedFailed = """{"path":"/opt/data/ignored.txt","content":"..."}"""
        val messages = historyToMessages(
            listOf(
                SessionMessage(
                    id = 1,
                    role = "assistant",
                    content = JsonPrimitive("Done"),
                    toolCalls = Json.parseToJsonElement(
                        """[
                            {"id":"t1","function":{"name":"write_file","arguments":$storedWrite}},
                            {"id":"t2","function":{"name":"patch","arguments":$storedPatch}},
                            {"id":"t3","function":{"name":"write_file","arguments":$storedFailed}}
                        ]""",
                    ),
                ),
                SessionMessage(id = 2, role = "tool", toolCallId = "t1", content = JsonPrimitive("""{"resolved_path":"/opt/data/report.pdf"}""")),
                SessionMessage(id = 3, role = "tool", toolCallId = "t2", content = JsonPrimitive("""{"ok":true}""")),
                SessionMessage(id = 4, role = "tool", toolCallId = "t3", content = JsonPrimitive("""{"error":"Permission denied"}""")),
            ),
        )
        val artifacts = extractChatArtifacts(messages)
        assertEquals(
            listOf("docs/readme.md", "src/Main.kt", "/opt/data/report.pdf"),
            artifacts.modifiedFiles.map { it.path },
        )
        assertEquals("readme.md", artifacts.modifiedFiles.first().name)
        assertEquals("docs", artifacts.modifiedFiles.first().folder)
    }

    @Test
    fun extractsUploadsAndPreservesGatewayPathsForPdfsAndFiles() {
        val messages = historyToMessages(
            listOf(
                SessionMessage(id = 1, role = "user", content = JsonPrimitive("@file:attachments/notes.txt\n@file:\"attachments/my spec.pdf\"\n\nCheck both")),
                SessionMessage(id = 2, role = "user", content = JsonPrimitive("@image:/opt/data/images/photo.jpg\n\nLook")),
                SessionMessage(id = 3, role = "user", content = JsonPrimitive("@file:attachments/notes.txt\n\nAgain")),
            ),
        )
        val artifacts = extractChatArtifacts(messages)
        // Newest first, deduplicated by gatewayPath.
        assertEquals(
            listOf("attachments/notes.txt", "/opt/data/images/photo.jpg", "attachments/my spec.pdf"),
            artifacts.uploads.map { it.gatewayPath },
        )
        assertEquals(
            listOf(AttachmentKind.File, AttachmentKind.Image, AttachmentKind.Pdf),
            artifacts.uploads.map { it.kind },
        )
        assertTrue(artifacts.uploads.all { it.canPreview })
    }

    @Test
    fun ignoresReadOnlyToolsWhenBuildingModifiedFiles() {
        val artifacts = extractChatArtifacts(
            listOf(
                ChatMessage.Assistant(
                    key = "a1",
                    tools = listOf(
                        ToolActivity("1", "read_file", editedFiles = extractEditedFilePaths("read_file", Json.parseToJsonElement("""{"path":"/opt/data/a.txt"}"""))),
                        ToolActivity("2", "write_file", editedFiles = extractEditedFilePaths("write_file", Json.parseToJsonElement("""{"path":"/opt/data/out.html"}"""))),
                    ),
                ),
            ),
        )
        assertEquals(listOf("/opt/data/out.html"), artifacts.modifiedFiles.map { it.path })
        assertFalse(artifacts.isEmpty)
    }
}
