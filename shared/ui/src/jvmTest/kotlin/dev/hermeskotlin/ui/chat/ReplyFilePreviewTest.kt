package dev.hermeskotlin.ui.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.chat.ReplyMedia
import dev.hermeskotlin.core.media.FileText
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.components.CodeArtifact
import dev.hermeskotlin.designsystem.components.LocalCodePreview
import dev.hermeskotlin.designsystem.components.LocalFileLinks
import dev.hermeskotlin.designsystem.components.MarkdownText
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReplyFilePreviewTest {

    @Test
    fun replyFilePreviewOpensPreviewableGatewayFilesAndSkipsBinaryArchives() = runDesktopComposeUiTest(width = 640, height = 240) {
        var opened: String? = null
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    CompositionLocalProvider(LocalFileLinks provides { opened = it }) {
                        ReplyFile(ReplyMedia("/opt/data/report.pdf", "report.pdf", isImage = false))
                    }
                }
            }
        }
        onNodeWithContentDescription("Preview").assertIsDisplayed().performClick()
        assertEquals("/opt/data/report.pdf", opened)

        opened = null
        onNodeWithText("report.pdf").performClick()
        assertEquals("/opt/data/report.pdf", opened)

        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    CompositionLocalProvider(LocalFileLinks provides { opened = it }) {
                        ReplyFile(ReplyMedia("/opt/data/archive.zip", "archive.zip", isImage = false))
                    }
                }
            }
        }
        assertTrue(onAllNodesWithContentDescription("Preview").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun markdownCodeBlocksOfferPreviewOnlyForSupportedArtifactLanguages() = runDesktopComposeUiTest(width = 720, height = 360) {
        var opened: CodeArtifact? = null
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    CompositionLocalProvider(LocalCodePreview provides { opened = it }) {
                        MarkdownText("```mermaid\ngraph TD\n  A-->B\n```\n\n```kotlin\nval x = 1\n```")
                    }
                }
            }
        }
        waitForIdle()
        val previewButtons = onAllNodesWithText("Preview").fetchSemanticsNodes()
        assertEquals(1, previewButtons.size)
        onNodeWithText("Preview").performClick()
        assertEquals(CodeArtifact("mermaid", "graph TD\n  A-->B"), opened)
    }

    @Test
    fun fileViewerShowsPreviewAndSourceToggleForSvgAndHtml() = runDesktopComposeUiTest(width = 800, height = 400) {
        val svgText = FileText("<svg viewBox='0 0 10 10'/>", 31, truncated = false, binary = false, language = "svg")
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    FileViewerPage(
                        file = GatewayFile("Chat artifact/diagram.svg"),
                        onDismiss = {},
                        load = { FileLoad.Ready(svgText) },
                        download = { FileDownload.Ready(svgText.text.encodeToByteArray()) },
                        preset = FileLoad.Ready(svgText),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Preview").assertIsDisplayed()
        onNodeWithText("Source").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithText("viewBox", substring = true).assertIsDisplayed()
    }
}
