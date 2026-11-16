package dev.hermeskotlin.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.platform.testTag
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.chat.AttachmentKind
import dev.hermeskotlin.core.chat.ChatArtifacts
import dev.hermeskotlin.core.chat.ModifiedFileArtifact
import dev.hermeskotlin.core.chat.ReferencedFileArtifact
import dev.hermeskotlin.core.chat.ShownAttachment
import dev.hermeskotlin.core.chat.UploadArtifact
import dev.hermeskotlin.core.media.FileText
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ArtifactSidebarAndPdfTest {

    @Test
    fun pdfZoomBarStepsZoomInAndResetsTo100Percent() = runDesktopComposeUiTest(width = 640, height = 480) {
        val pages = object : PdfPages {
            override val aspects = listOf(0.75f)
            override suspend fun render(index: Int, width: Int, height: Int): ImageBitmap = ImageBitmap(width, height)
            override fun close() = Unit
        }
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    PdfDocumentView(pages, Modifier.fillMaxSize())
                }
            }
        }
        waitForIdle()
        onNodeWithText("100%").assertIsDisplayed()
        onNodeWithContentDescription("Zoom in").performClick()
        waitForIdle()
        onNodeWithText("125%").assertIsDisplayed()
        onNodeWithText("125%").performClick()
        waitForIdle()
        onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun dockedFileViewerTogglesFullScreenPopupAndBack() = runDesktopComposeUiTest(width = 960, height = 640) {
        val text = FileText("# Hello", 7, truncated = false, binary = false, language = "markdown")
        var expanded by mutableStateOf(false)
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    FileViewer(
                        file = GatewayFile("/opt/data/readme.md"),
                        onDismiss = {},
                        load = { FileLoad.Ready(text) },
                        download = { FileDownload.Ready(text.text.encodeToByteArray()) },
                        docked = true,
                        preset = FileLoad.Ready(text),
                        expanded = expanded,
                        onToggleExpand = { expanded = !expanded },
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Open full screen").assertIsDisplayed().performClick()
        waitForIdle()
        assertTrue(expanded)
        onNodeWithContentDescription("Exit full screen").assertIsDisplayed().performClick()
        waitForIdle()
        assertFalse(expanded)
    }

    @Test
    fun artifactHistoryPanelListsModifiedFilesAndUploadsAndOpensItems() = runDesktopComposeUiTest(width = 420, height = 640) {
        val openedFiles = mutableListOf<String>()
        val openedImages = mutableListOf<String>()
        val artifacts = ChatArtifacts(
            modifiedFiles = listOf(ModifiedFileArtifact("/opt/data/out.pdf", "out.pdf", "/opt/data")),
            uploads = listOf(
                UploadArtifact(ShownAttachment("u1", "brief.pdf", AttachmentKind.Pdf, gatewayPath = "attachments/brief.pdf")),
                UploadArtifact(ShownAttachment("u2", "shot.png", AttachmentKind.Image, gatewayPath = "/opt/data/shot.png")),
            ),
            referencedFiles = listOf(
                ReferencedFileArtifact("/opt/prev/legacy.md", "legacy.md", "/opt/prev", line = 42),
                ReferencedFileArtifact("/opt/prev/diagram.png", "diagram.png", "/opt/prev"),
            ),
        )
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    ArtifactHistoryPanel(
                        artifacts = artifacts,
                        onOpenFile = { openedFiles += it.path },
                        onOpenImage = { openedImages += it.source ?: it.name },
                        onClose = {},
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Modified Files").assertIsDisplayed()
        onNodeWithText("Uploads").assertIsDisplayed()
        onNodeWithText("Referenced").assertIsDisplayed()
        onNodeWithText("out.pdf").performClick()
        onNodeWithText("brief.pdf").performClick()
        onNodeWithText("shot.png").performClick()
        onNodeWithText("legacy.md").performClick()
        onNodeWithText("diagram.png").performClick()
        assertEquals(listOf("/opt/data/out.pdf", "attachments/brief.pdf", "/opt/prev/legacy.md"), openedFiles)
        assertEquals(listOf("/opt/data/shot.png", "/opt/prev/diagram.png"), openedImages)
    }

    @Test
    fun mobileRightDrawerOpensOnLeftSwipeAndIgnoresRightSwipeWhenClosed() = runDesktopComposeUiTest(width = 400, height = 720) {
        lateinit var railState: RightRailState
        setContent {
            HermesTheme(ColorScheme.Dark) {
                ProvideAppWords {
                    CompositionLocalProvider(LocalDesktopLayout provides false) {
                        railState = rememberRightRailState()
                        RightRailLayout(
                            state = railState,
                            swipeEnabled = true,
                            rail = { Box(Modifier.fillMaxSize().testTag("right-drawer")) },
                            content = { mod -> Box(mod.fillMaxSize().testTag("chat-surface")) },
                        )
                    }
                }
            }
        }
        waitForIdle()
        // Rightward swipe when closed yields to parent left drawer and keeps right drawer closed.
        onNodeWithTag("chat-surface").performTouchInput { swipeRight() }
        waitForIdle()
        assertFalse(railState.isOpen)

        // Leftward swipe opens the right drawer.
        onNodeWithTag("chat-surface").performTouchInput { swipeLeft() }
        waitForIdle()
        assertTrue(railState.isOpen)
        onNodeWithTag("right-drawer").assertIsDisplayed()
    }
}
