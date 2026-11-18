package dev.hermeskotlin.ui.chat

import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException

/** Private, fixed-size messages. No gateway/URL strings or serialized Java objects cross this pipe. */
internal object PdfWire {
    const val OPEN_FILE = -1
    const val MAX_BYTES = 64 * 1024 * 1024
    const val MAX_FILE_BYTES = 512L * 1024 * 1024
    const val MIXED_CACHE_THRESHOLD_BYTES = 32L * 1024 * 1024
    const val MAX_PAGES = 2_000
    const val MAX_PIXELS = 8_000_000L
    const val READY = 1
    const val LOCKED = 2
    const val BROKEN = 3
    const val LIMIT = 4
    const val FAILED = 5

    fun dimensions(width: Int, height: Int) =
        width in 1..16384 && height in 1..16384 && width.toLong() * height <= MAX_PIXELS
}

private class PdfLimitException(message: String) : Exception(message)

/** Only the child loads PDFBox documents. The parent can retire a parser that stops responding. */
internal object PdfWorker {
    @JvmStatic
    fun main(args: Array<String>) {
        // Reserve stdout strictly for the binary protocol; redirect any stray library prints to stderr.
        val protocolOut = System.out
        System.setOut(System.err)
        val input = DataInputStream(System.`in`.buffered())
        val output = DataOutputStream(protocolOut.buffered())
        try {
            openDocument(input).use { document ->
                if (document.numberOfPages <= 0) throw IOException("PDF has no pages")
                if (document.numberOfPages > PdfWire.MAX_PAGES) {
                    throw PdfLimitException("PDF page count ${document.numberOfPages} exceeds ${PdfWire.MAX_PAGES}")
                }
                val shapes = document.pages.map { page ->
                    val box = page.cropBox
                    val rotated = page.rotation % 180 != 0
                    val width = if (rotated) box.height else box.width
                    val height = if (rotated) box.width else box.height
                    check(width.isFinite() && height.isFinite() && width > 0 && height > 0) {
                        "Invalid PDF page dimensions: ${width}x$height"
                    }
                    (width / height).also {
                        check(it.isFinite() && it > 0) { "Invalid PDF page aspect: $it" }
                    }
                }
                output.writeInt(PdfWire.READY)
                output.writeInt(shapes.size)
                shapes.forEach(output::writeFloat)
                output.flush()
                val renderer = PDFRenderer(document).apply { isSubsamplingAllowed = true }
                while (true) {
                    val index = try {
                        input.readInt()
                    } catch (_: EOFException) {
                        // Normal end of the parent connection once the viewer closes.
                        return
                    }
                    val width = input.readInt()
                    val height = input.readInt()
                    check(index in shapes.indices && PdfWire.dimensions(width, height)) {
                        "Invalid render request: page=$index size=${width}x$height"
                    }
                    val page = document.getPage(index)
                    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                    val graphics = image.createGraphics()
                    try {
                        graphics.color = Color.WHITE
                        graphics.fillRect(0, 0, width, height)
                        // PDFBox clears the page with the background colour first, which is black by default.
                        graphics.background = Color.WHITE
                        renderer.renderPageToGraphics(index, graphics, width / page.width(), height / page.height())
                    } finally {
                        graphics.dispose()
                    }
                    output.writeInt(width)
                    output.writeInt(height)
                    // Raw RGBA avoids sending a compressed image for the parent to parse or expand.
                    val row = IntArray(width)
                    for (y in 0 until height) {
                        image.getRGB(0, y, width, 1, row, 0, width)
                        row.forEach { output.writeInt((it shl 8) or 0xff) }
                    }
                    output.flush()
                    image.flush()
                }
            }
        } catch (_: InvalidPasswordException) {
            runCatching { output.writeInt(PdfWire.LOCKED); output.flush() }
        } catch (_: PdfLimitException) {
            runCatching { output.writeInt(PdfWire.LIMIT); output.flush() }
        } catch (e: Exception) {
            System.err.println("PdfWorker broken/open error: ${e.javaClass.name}: ${e.message}")
            runCatching { output.writeInt(PdfWire.BROKEN); output.flush() }
        } catch (t: Throwable) {
            t.printStackTrace(System.err)
            System.err.flush()
            runCatching { output.writeInt(PdfWire.FAILED); output.flush() }
        }
    }

    private fun openDocument(input: DataInputStream): PDDocument {
        val header = input.readInt()
        if (header == PdfWire.OPEN_FILE) {
            val path = input.readUTF()
            val file = File(path)
            if (!file.isFile) throw IOException("PDF temp file missing")
            val size = file.length()
            if (size <= 0L) throw IOException("PDF file is empty")
            if (size > PdfWire.MAX_FILE_BYTES) {
                throw PdfLimitException("PDF file size $size exceeds ${PdfWire.MAX_FILE_BYTES}")
            }
            val cache = if (size > PdfWire.MIXED_CACHE_THRESHOLD_BYTES) {
                IOUtils.createTempFileOnlyStreamCache()
            } else {
                IOUtils.createMemoryOnlyStreamCache()
            }
            return Loader.loadPDF(file, cache)
        }
        if (header <= 0) throw IOException("Empty PDF payload ($header)")
        if (header > PdfWire.MAX_BYTES) {
            throw PdfLimitException("PDF byte size $header exceeds ${PdfWire.MAX_BYTES}")
        }
        val bytes = ByteArray(header).also(input::readFully)
        return Loader.loadPDF(bytes)
    }

    private fun PDPage.width() = if (rotation % 180 != 0) cropBox.height else cropBox.width
    private fun PDPage.height() = if (rotation % 180 != 0) cropBox.width else cropBox.height
}
