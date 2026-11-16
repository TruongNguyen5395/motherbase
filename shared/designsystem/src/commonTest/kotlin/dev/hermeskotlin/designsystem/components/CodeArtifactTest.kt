package dev.hermeskotlin.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeArtifactTest {
    @Test
    fun recognizesSupportedArtifactFenceLanguages() {
        assertEquals("html", previewableArtifactLanguage("html"))
        assertEquals("html", previewableArtifactLanguage(" HTM "))
        assertEquals("svg", previewableArtifactLanguage("SVG"))
        assertEquals("mermaid", previewableArtifactLanguage("mermaid"))
        assertEquals("mermaid", previewableArtifactLanguage("mmd"))
        assertNull(previewableArtifactLanguage("kotlin"))
        assertNull(previewableArtifactLanguage("python"))
        assertNull(previewableArtifactLanguage(null))
        assertNull(previewableArtifactLanguage(""))
    }
}
