package dev.hermeskotlin.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.hermeskotlin.core.media.GatewayFile
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.ui.ProvideAppLanguage
import dev.hermeskotlin.ui.applyAppLanguage
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopPdfTest {
    @Test
    fun desktopLimitGuidanceUsesSpanish() = runComposeUiTest {
        val locale = Locale.getDefault()
        try {
            setContent {
                ProvideAppLanguage("es") {
                    HermesTheme {
                        FileViewerPage(GatewayFile("/fixture.pdf"), {}, { error("PDFs download whole") },
                            { FileDownload.Ready(byteArrayOf(1)) },
                            openPdf = { PdfOpen.Failed(PDF_DESKTOP_LIMIT) })
                    }
                }
            }
            onNodeWithText("Este PDF supera los límites del visor de escritorio. Guárdalo para abrirlo en otra aplicación.").assertExists()
        } finally {
            applyAppLanguage(null)
            Locale.setDefault(locale)
        }
    }

    @Test
    fun desktopOpenerDrawsPdfPages() = runComposeUiTest {
        lateinit var open: suspend (ByteArray) -> PdfOpen
        setContent { open = rememberPdfOpener() }
        waitForIdle()
        runBlocking {
            val result = assertIs<PdfOpen.Ready>(open(pdf()))
            result.pages.use { pages ->
                assertEquals(listOf(0.5f, 2f), pages.aspects)
                val image = assertNotNull(pages.render(0, 100, 200))
                assertEquals(100, image.width)
                assertEquals(200, image.height)
                assertEquals(Color.Red, image.toPixelMap()[50, 100])
                assertNotNull(pages.render(1, 200, 100))
                assertNotNull(pages.render(0, 250, 500))
            }
        }
    }

    @Test
    fun malformedAndEmptyDocumentsHaveAnExplanation() = runBlocking {
        assertEquals(PdfOpen.Failed(PDF_BROKEN), openDesktopPdf("not a PDF".toByteArray()))
        assertEquals(PdfOpen.Failed(PDF_BROKEN), openDesktopPdf(byteArrayOf()))
    }

    @Test
    fun protectedDocumentsKeepTheSaveFallback() = runBlocking {
        val encrypted = Loader.loadPDF(pdf()).use { document ->
            document.protect(StandardProtectionPolicy("owner", "reader", AccessPermission()))
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
        assertEquals(PdfOpen.Failed(PDF_LOCKED), openDesktopPdf(encrypted))
    }

    @Test
    fun textFontsRenderIntoPixels() = runBlocking {
        val bytes = PDDocument().use { document ->
            val page = PDPage().also(document::addPage)
            org.apache.pdfbox.pdmodel.PDPageContentStream(document, page).use {
                it.beginText()
                it.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 24f)
                it.newLineAtOffset(50f, 700f)
                it.showText("PDF preview")
                it.endText()
            }
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
        assertIs<PdfOpen.Ready>(openDesktopPdf(bytes)).pages.use {
            val pixels = assertNotNull(it.render(0, 612, 792)).toPixelMap()
            assertTrue((50..220).any { x -> (70..100).any { y -> pixels[x, y] != Color.White } })
            // A page with no fill of its own is white paper, not the renderer's black clear colour.
            assertEquals(Color.White, pixels[300, 500])
            assertEquals(Color.White, pixels[5, 5])
        }
    }

    @Test
    fun rotatedPageShapeAndPixelsAgree() = runBlocking {
        val bytes = Loader.loadPDF(pdf()).use { document ->
            document.getPage(0).rotation = 90
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
        val pages = assertIs<PdfOpen.Ready>(openDesktopPdf(bytes)).pages
        pages.use {
            assertEquals(2f, it.aspects.first())
            assertEquals(Color.Red, assertNotNull(it.render(0, 200, 100)).toPixelMap()[100, 50])
        }
    }

    @Test
    fun limitsRejectAllocationAndLeaveValidPagesUsable() = runBlocking {
        var launched = false
        assertIs<PdfOpen.Failed>(openDesktopPdf(ByteArray(PdfWire.MAX_BYTES + 1)) {
            launched = true
            error("must not launch")
        })
        assertFalse(launched)
        val pages = assertIs<PdfOpen.Ready>(openDesktopPdf(pdf())).pages
        pages.use {
            assertNull(it.render(-1, 100, 100))
            assertNull(it.render(2, 100, 100))
            assertNull(it.render(0, 0, 100))
            assertNull(it.render(0, Int.MAX_VALUE, Int.MAX_VALUE))
            assertNull(it.render(0, 3000, 3000))
            assertNotNull(it.render(0, 100, 200))
        }
        assertNull(pages.render(0, 100, 200))
        pages.close()
    }

    @Test
    fun pageCountIsBounded() = runBlocking {
        val bytes = PDDocument().use { document ->
            repeat(PdfWire.MAX_PAGES + 1) { document.addPage(PDPage()) }
            ByteArrayOutputStream().also(document::save).toByteArray()
        }
        assertEquals(PdfOpen.Failed(PDF_DESKTOP_LIMIT), openDesktopPdf(bytes))
    }

    @Test
    fun closingRepeatedDocumentsRetiresTheirProcesses() = runBlocking {
        repeat(3) {
            lateinit var process: Process
            val pages = assertIs<PdfOpen.Ready>(openDesktopPdf(pdf()) {
                fixture("ready").also { process = it }
            }).pages
            assertTrue(process.isAlive)
            pages.close()
            pages.close()
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            assertNull(pages.render(0, 10, 10))
        }
    }

    @Test
    fun deadlineRetiresAWorkerStuckDuringOpen() = runBlocking {
        lateinit var process: Process
        assertEquals(PdfOpen.Failed(PDF_DESKTOP_FAILED), withTimeout(5_000) {
            openDesktopPdf(pdf(), timeoutMillis = 300) { fixture("hang-open").also { process = it } }
        })
        assertTrue(process.waitFor(3, TimeUnit.SECONDS))
    }

    @Test
    fun workerCrashReportsDesktopFailureNotSizeLimit() = runBlocking {
        lateinit var process: Process
        assertEquals(PdfOpen.Failed(PDF_DESKTOP_FAILED), openDesktopPdf(pdf()) {
            fixture("early-exit").also { process = it }
        })
        assertTrue(process.waitFor(3, TimeUnit.SECONDS))
    }

    @Test
    fun fileBackedDesktopPdfOpensAndDrawsPages() = runBlocking {
        val temp = File.createTempFile("desktop-pdf-test-", ".pdf")
        try {
            temp.writeBytes(pdf())
            val opened = assertIs<PdfOpen.Ready>(openDesktopPdf(temp))
            opened.pages.use { pages ->
                assertEquals(listOf(0.5f, 2f), pages.aspects)
                val rendered = assertNotNull(pages.render(0, 100, 200))
                assertEquals(Color.Red, rendered.toPixelMap()[50, 100])
            }
        } finally {
            temp.delete()
        }
    }

    @Test
    fun fileTempHandleStreamsAndDeletesFileOnClose() = runBlocking {
        val file = File.createTempFile("temp-handle-test-", ".bin")
        val handle = FileTempHandle(file)
        handle.append("hello ".encodeToByteArray())
        handle.append("world".encodeToByteArray())
        handle.finishWrite()
        assertEquals(11L, handle.byteSize)

        val read = ByteArrayOutputStream()
        handle.streamTo { chunk -> read.write(chunk) }
        assertEquals("hello world", read.toByteArray().decodeToString())

        handle.close()
        assertFalse(file.exists())
    }

    @Test
    fun tooLargePreviewStillOffersSaveAndOpenInApp() = runComposeUiTest {
        val locale = Locale.getDefault()
        try {
            setContent {
                ProvideAppLanguage("en") {
                    HermesTheme {
                        dev.hermeskotlin.ui.components.ProvideAppWords {
                            FileViewerPage(
                                GatewayFile("/huge.log"),
                                onDismiss = {},
                                load = { FileLoad.Failed(FileProblem.TooLargeToPreview) },
                                download = { FileDownload.Failed(FileProblem.Said("unused")) },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Save").assertExists()
            onNodeWithText("Open in app").assertExists()
        } finally {
            applyAppLanguage(null)
            Locale.setDefault(locale)
        }
    }

    @Test
    fun streamedPdfReusesCachedTempFileOnExpandToggle() = runComposeUiTest {
        val locale = Locale.getDefault()
        var visible by mutableStateOf(true)
        var expanded by mutableStateOf(false)
        var streams = 0
        try {
            setContent {
                ProvideAppLanguage("en") {
                    HermesTheme {
                        dev.hermeskotlin.ui.components.ProvideAppWords {
                            if (visible) FileViewer(
                                GatewayFile("/streamed.pdf"),
                                onDismiss = { visible = false },
                                load = { error("PDFs stream instead of text load") },
                                download = { error("Streamed PDFs avoid whole-ByteArray download") },
                                docked = true,
                                expanded = expanded,
                                onToggleExpand = { expanded = !expanded },
                                stream = { _, _, _, write ->
                                    streams++
                                    write(pdf())
                                    null
                                },
                            )
                        }
                    }
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText("2 pages", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("2 pages", substring = true).assertExists()
            runOnIdle { expanded = true }
            waitForIdle()
            assertEquals(1, streams)
            runOnIdle { visible = false }
            waitForIdle()
        } finally {
            applyAppLanguage(null)
            Locale.setDefault(locale)
        }
    }

    @Test
    fun cancellationDuringOpenRetiresTheWorker() = runBlocking {
        val started = CompletableDeferred<Process>()
        val opening = async {
            openDesktopPdf(pdf()) { fixture("hang-open").also { started.complete(it) } }
        }
        val process = withTimeout(5_000) { started.await() }
        withTimeout(5_000) { opening.cancelAndJoin() }
        assertTrue(opening.isCancelled)
        assertTrue(process.waitFor(3, TimeUnit.SECONDS))
    }

    @Test
    fun cancellationDuringRenderRetiresTheWorker() = runBlocking {
        lateinit var process: Process
        var launches = 0
        val pages = assertIs<PdfOpen.Ready>(openDesktopPdf(pdf()) {
            fixture(if (launches++ == 0) "ready" else "draw").also { process = it }
        }).pages
        pages.use {
            val rendering = async { it.render(0, 100, 200) }
            // The fixture acknowledges the request by closing stderr, independently of the response pipe.
            withTimeout(5_000) { kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) { process.errorStream.read() } }
            withTimeout(5_000) { rendering.cancelAndJoin() }
            assertTrue(rendering.isCancelled)
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            assertNotNull(it.render(0, 100, 200), "A new zoom target must still render after cancellation")
            assertEquals(2, launches)
        }
    }

    @Test
    fun renderDeadlineAndExplicitCloseUnblockPipeReads() = runBlocking {
        for (explicitClose in listOf(false, true)) {
            lateinit var process: Process
            val pages = assertIs<PdfOpen.Ready>(openDesktopPdf(pdf(), timeoutMillis = 500) {
                fixture("ready").also { process = it }
            }).pages
            pages.use {
                val rendering = async { it.render(0, 100, 200) }
                withTimeout(5_000) {
                    kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) { process.errorStream.read() }
                }
                if (explicitClose) it.close()
                assertNull(withTimeout(5_000) { rendering.await() })
                assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun closeDuringReplacementStartupCannotPublishALiveWorker() = runBlocking {
        var launches = 0
        val replacement = CompletableDeferred<Process>()
        val release = java.util.concurrent.CountDownLatch(1)
        lateinit var process: Process
        val pages = assertIs<PdfOpen.Ready>(openDesktopPdf(pdf(), timeoutMillis = 300) {
            fixture("ready").also {
                process = it
                if (++launches == 2) {
                    replacement.complete(it)
                    release.await(3, TimeUnit.SECONDS)
                }
            }
        }).pages
        try {
            assertNull(pages.render(0, 100, 200))
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
            val rendering = async { pages.render(0, 100, 200) }
            val next = withTimeout(5_000) { replacement.await() }
            pages.close()
            release.countDown()
            assertNull(withTimeout(5_000) { rendering.await() })
            assertTrue(next.waitFor(3, TimeUnit.SECONDS))
            assertNull(pages.render(0, 100, 200))
            assertEquals(2, launches)
        } finally {
            release.countDown()
            pages.close()
            process.destroyForcibly()
        }
    }

    @Test
    fun cancelledDeliveryOfOpenedPagesRetiresTheWorker() = runBlocking {
        val ownerThread = Thread.currentThread()
        val delivery = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (Thread.currentThread() != ownerThread) context[Job]!!.cancel()
                block.run()
            }
        }
        lateinit var process: Process
        val opening = async {
            withContext(delivery) {
                openDesktopPdf(pdf()) { fixture("ready").also { process = it } }
            }
        }
        opening.join()
        assertTrue(opening.isCancelled)
        try {
            assertTrue(process.waitFor(2, TimeUnit.SECONDS), "Undelivered pages must not leave a worker alive")
        } finally {
            process.destroyForcibly()
        }
    }

    @Test
    fun invalidWorkerHeadersAreRejectedAndRetired() = runBlocking {
        for (mode in listOf("huge-count", "bad-aspect", "wrong-dimensions", "early-exit")) {
            lateinit var process: Process
            val opened = openDesktopPdf(pdf()) { fixture(mode).also { process = it } }
            if (mode == "wrong-dimensions") {
                assertIs<PdfOpen.Ready>(opened).pages.use { assertNull(it.render(0, 10, 20)) }
            } else {
                assertIs<PdfOpen.Failed>(opened)
            }
            assertTrue(process.waitFor(3, TimeUnit.SECONDS))
        }
    }

    private fun fixture(mode: String): Process {
        val paths = linkedSetOf<String>()
        paths.addAll(System.getProperty("java.class.path").split(File.pathSeparator))
        generateSequence(javaClass.classLoader) { it.parent }.forEach { loader ->
            (loader as? URLClassLoader)?.urLs?.filter { it.protocol == "file" }?.forEach { paths.add(File(it.toURI()).path) }
        }
        val executable = if (File.separatorChar == '\\') "java.exe" else "java"
        return ProcessBuilder(File(System.getProperty("java.home"), "bin/$executable").path,
            "-cp", paths.joinToString(File.pathSeparator), PdfWorkerFixture::class.java.name, mode).start()
    }

    private fun pdf(): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 200] /Contents 5 0 R >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 100] /Contents 5 0 R >>",
            "<< /Length 27 >>\nstream\n1 0 0 rg 0 0 100 200 re f\nendstream",
        )
        val body = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, value ->
            body.length.also { body.append("${index + 1} 0 obj\n$value\nendobj\n") }
        }
        val xref = body.length
        body.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { body.append("%010d 00000 n \n".format(it)) }
        body.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return body.toString().toByteArray(Charsets.US_ASCII)
    }
}
