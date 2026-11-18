package dev.hermeskotlin.ui.chat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.hermeskotlin.core.storage.appDataDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.URLClassLoader
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal val PDF_DESKTOP_LIMIT: FileProblem = FileProblem.PdfDesktopLimit

private const val DEFAULT_OPEN_TIMEOUT_MS = 30_000L
private const val MAX_STDERR_BYTES = 4096

internal sealed interface PdfSource {
    class Bytes(val bytes: ByteArray) : PdfSource
    class LocalFile(val file: File) : PdfSource
}

internal suspend fun openDesktopPdf(
    bytes: ByteArray,
    timeoutMillis: Long = DEFAULT_OPEN_TIMEOUT_MS,
    start: (() -> Process)? = null,
): PdfOpen = openDesktopPdf(PdfSource.Bytes(bytes), timeoutMillis, start)

internal suspend fun openDesktopPdf(
    file: File,
    timeoutMillis: Long = DEFAULT_OPEN_TIMEOUT_MS,
    start: (() -> Process)? = null,
): PdfOpen = openDesktopPdf(PdfSource.LocalFile(file), timeoutMillis, start)

private suspend fun openDesktopPdf(
    source: PdfSource,
    timeoutMillis: Long,
    start: (() -> Process)?,
): PdfOpen {
    var worker: DesktopPdfPages? = null
    var handedOff = false
    try {
        val result = withContext(Dispatchers.IO) {
            when (source) {
                is PdfSource.Bytes -> {
                    if (source.bytes.isEmpty()) return@withContext PdfOpen.Failed(PDF_BROKEN)
                    if (source.bytes.size > PdfWire.MAX_BYTES) return@withContext PdfOpen.Failed(PDF_DESKTOP_LIMIT)
                }
                is PdfSource.LocalFile -> {
                    if (!source.file.isFile || source.file.length() <= 0L) return@withContext PdfOpen.Failed(PDF_BROKEN)
                    if (source.file.length() > PdfWire.MAX_FILE_BYTES) return@withContext PdfOpen.Failed(PDF_DESKTOP_LIMIT)
                }
            }
            val pages = try {
                DesktopPdfPages(source, start, timeoutMillis).also { worker = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordWorkerDiagnostic(null, "construct", e.toString())
                return@withContext PdfOpen.Failed(PDF_DESKTOP_FAILED)
            }
            pages.open()
        }
        handedOff = result is PdfOpen.Ready
        return result
    } finally {
        // withContext may discard a successful result when delivery to its caller is cancelled.
        if (!handedOff) worker?.close()
    }
}

/** The runtime's own launcher, which the PDF child needs; a runtime packaged without one can't draw pages. */
internal fun pdfWorkerLauncher(): File =
    File(System.getProperty("java.home"), "bin/" + if (File.separatorChar == '\\') "java.exe" else "java")

private val INHERITED_ENV_KEYS = setOf("LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SYSTEMROOT", "WINDIR")

private fun startPdfWorker(stderrFile: File?): Process {
    val java = pdfWorkerLauncher()
    // Gradle's test loader carries URLs; installed launchers supply java.class.path directly.
    val paths = linkedSetOf<String>()
    System.getProperty("java.class.path").orEmpty().split(File.pathSeparator).filterTo(paths) { it.isNotBlank() }
    generateSequence(PdfWorker::class.java.classLoader) { it.parent }.forEach { loader ->
        (loader as? URLClassLoader)?.urLs?.filter { it.protocol == "file" }?.forEach { paths.add(File(it.toURI()).path) }
    }
    runCatching { PdfWorker::class.java.protectionDomain?.codeSource?.location }
        .getOrNull()?.takeIf { it.protocol == "file" }?.let { paths.add(File(it.toURI()).path) }
    return ProcessBuilder(
        java.path, "-Xmx512m", "-XX:MaxDirectMemorySize=64m", "-XX:+ExitOnOutOfMemoryError",
        "-Djava.awt.headless=true",
        "-Dorg.apache.commons.logging.LogFactory=org.apache.commons.logging.impl.LogFactoryImpl",
        "-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog",
        "-cp", paths.joinToString(File.pathSeparator), PdfWorker::class.java.name,
    ).apply {
        // Match case-insensitively so Windows keys like "windir" and "Temp" survive environment isolation.
        val inherited = environment().filterKeys { it.uppercase() in INHERITED_ENV_KEYS }
        environment().clear()
        environment().putAll(inherited)
        if (stderrFile != null) {
            redirectError(ProcessBuilder.Redirect.to(stderrFile))
        } else {
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }
    }.start()
}

private fun readStderrTail(file: File?): String? {
    if (file == null || !file.isFile) return null
    return runCatching {
        val len = file.length()
        if (len <= 0L) return@runCatching null
        val bytes = file.inputStream().use { input ->
            if (len > MAX_STDERR_BYTES) input.skip(len - MAX_STDERR_BYTES)
            input.readNBytes(MAX_STDERR_BYTES)
        }
        bytes.decodeToString().trim().ifEmpty { null }
    }.getOrNull()
}

private fun recordWorkerDiagnostic(status: Int?, phase: String, detail: String?) {
    if (detail.isNullOrBlank() && status == null && phase == "close") return
    runCatching {
        val logFile = File(appDataDir, "pdf-worker.log")
        logFile.parentFile?.mkdirs()
        val message = buildString {
            append(Instant.now()).append(" phase=").append(phase)
            if (status != null) append(" status=").append(status)
            if (!detail.isNullOrBlank()) append(" detail=").append(detail)
            append('\n')
        }
        logFile.appendText(message)
        System.err.print("PdfWorker failure: $message")
    }
}

private class DesktopPdfPages(
    private val source: PdfSource,
    private val customStart: (() -> Process)?,
    private val timeoutMillis: Long,
) : PdfPages {
    private val connection = AtomicReference<PdfConnection?>(null)
    private val closed = AtomicBoolean(false)
    private val mutex = Mutex()
    private val lastDiagnostic = AtomicReference<String?>(null)
    override var aspects: List<Float> = emptyList()
        private set

    suspend fun open(): PdfOpen {
        val status = transaction { worker ->
            worker.open(source).also { if (it == PdfWire.READY) aspects = worker.aspects }
        }
        return when (status) {
            PdfWire.READY -> PdfOpen.Ready(this)
            PdfWire.LOCKED -> PdfOpen.Failed(PDF_LOCKED)
            PdfWire.BROKEN -> PdfOpen.Failed(PDF_BROKEN)
            PdfWire.LIMIT -> PdfOpen.Failed(PDF_DESKTOP_LIMIT)
            else -> {
                val diag = connection.get()?.snapshotStderr() ?: lastDiagnostic.get()
                recordWorkerDiagnostic(status, "open", diag ?: "no response within ${timeoutMillis}ms")
                PdfOpen.Failed(PDF_DESKTOP_FAILED)
            }
        }
    }

    override suspend fun render(index: Int, width: Int, height: Int): ImageBitmap? {
        if (index !in aspects.indices || !PdfWire.dimensions(width, height)) return null
        return transaction { worker ->
            // A zoom or scrolled-away page may cancel a render. Its replacement opens the same document lazily.
            if (!worker.opened) require(worker.open(source) == PdfWire.READY && worker.aspects == aspects)
            worker.output.writeInt(index)
            worker.output.writeInt(width)
            worker.output.writeInt(height)
            worker.output.flush()
            require(worker.input.readInt() == width && worker.input.readInt() == height)
            val pixels = ByteArray(width * height * 4).also(worker.input::readFully)
            Image.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), pixels, width * 4)
                .toComposeImageBitmap()
        }
    }

    private fun startWorkerProcess(): PdfConnection {
        if (customStart != null) return PdfConnection(customStart.invoke(), stderrFile = null)
        val err = runCatching { File.createTempFile("motherbase-pdf-", ".log").apply { deleteOnExit() } }.getOrNull()
        val proc = try {
            startPdfWorker(err)
        } catch (e: Exception) {
            lastDiagnostic.compareAndSet(null, e.toString())
            runCatching { err?.delete() }
            throw e
        }
        return PdfConnection(proc, err)
    }

    private suspend fun <T> transaction(block: (PdfConnection) -> T): T? = mutex.withLock {
        if (closed.get()) return@withLock null
        try {
            withTimeoutOrNull(timeoutMillis) {
                coroutineScope {
                    val finished = AtomicBoolean(false)
                    val aborted = AtomicBoolean(false)
                    // Pipe reads may ignore interruption. Cancellation must kill the writer as well.
                    val retirement = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally {
                            if (!finished.get()) {
                                aborted.set(true)
                                retireWorker()
                            }
                        }
                    }
                    try {
                        runInterruptible(Dispatchers.IO) {
                            val worker = connection.get() ?: startWorkerProcess().also {
                                connection.set(it)
                                // Cancellation/close may have happened while ProcessBuilder was starting.
                                if (aborted.get() || closed.get()) {
                                    retireWorker()
                                    throw IOException("PDF worker retired")
                                }
                            }
                            block(worker)
                        }
                    } finally {
                        finished.set(true)
                        retirement.cancel()
                    }
                }
            }.also { if (it == null) retireWorker() }
        } catch (e: CancellationException) {
            retireWorker()
            throw e
        } catch (e: Exception) {
            lastDiagnostic.compareAndSet(null, e.toString())
            retireWorker()
            null
        }
    }

    private fun retireWorker() {
        connection.getAndSet(null)?.let { conn ->
            conn.snapshotStderr()?.let { lastDiagnostic.compareAndSet(null, it) }
            conn.close()
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) retireWorker()
    }
}

private class PdfConnection(
    private val process: Process,
    private val stderrFile: File?,
) : AutoCloseable {
    val input = DataInputStream(process.inputStream.buffered())
    val output = DataOutputStream(process.outputStream.buffered())
    var aspects: List<Float> = emptyList()
        private set
    var opened = false
        private set

    fun snapshotStderr(): String? = readStderrTail(stderrFile)

    fun open(source: PdfSource): Int {
        when (source) {
            is PdfSource.Bytes -> {
                output.writeInt(source.bytes.size)
                output.write(source.bytes)
            }
            is PdfSource.LocalFile -> {
                output.writeInt(PdfWire.OPEN_FILE)
                output.writeUTF(source.file.absolutePath)
            }
        }
        output.flush()
        val status = input.readInt()
        if (status == PdfWire.READY) {
            val count = input.readInt()
            require(count in 1..PdfWire.MAX_PAGES)
            aspects = List(count) { input.readFloat().also { require(it.isFinite() && it > 0) } }
            opened = true
        }
        return status
    }

    override fun close() {
        process.destroyForcibly()
        // Never wait for a worker or a locked pipe on the UI thread.
        process.onExit().thenRun {
            runCatching { input.close() }
            runCatching { output.close() }
            runCatching { stderrFile?.delete() }
        }
    }
}
