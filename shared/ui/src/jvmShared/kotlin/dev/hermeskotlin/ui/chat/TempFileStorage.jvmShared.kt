package dev.hermeskotlin.ui.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Disk-backed [TempFileHandle] shared by Android and Desktop. */
internal class FileTempHandle(
    val file: File,
    deleteOnJvmExit: Boolean = false,
) : TempFileHandle {
    private var out: BufferedOutputStream? = BufferedOutputStream(FileOutputStream(file))
    private var written = 0L

    init {
        if (deleteOnJvmExit) file.deleteOnExit()
    }

    override val byteSize: Long
        get() = if (out == null) file.length() else written

    override suspend fun append(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        withContext(Dispatchers.IO) {
            val stream = out ?: error("Temp file stream already finalized")
            stream.write(chunk)
            written += chunk.size
        }
    }

    override suspend fun finishWrite() {
        withContext(Dispatchers.IO) {
            out?.let { stream ->
                stream.flush()
                stream.close()
                out = null
            }
        }
    }

    override suspend fun streamTo(onProgress: MediaProgress?, write: MediaByteSink) {
        withContext(Dispatchers.IO) {
            val total = file.length()
            val buffer = ByteArray(STREAM_BUFFER_BYTES)
            var sent = 0L
            FileInputStream(file).buffered().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    write(chunk)
                    sent += read
                    onProgress?.invoke(sent, total)
                }
            }
        }
    }

    override fun close() {
        runCatching { out?.close() }
        out = null
        runCatching { file.delete() }
    }

    private companion object {
        const val STREAM_BUFFER_BYTES = 64 * 1024
    }
}

internal class DirectoryTempFileFactory(
    private val directory: () -> File,
    private val deleteOnJvmExit: Boolean = false,
) : TempFileFactory {
    override suspend fun create(prefix: String, suffix: String): TempFileHandle? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = directory().apply { mkdirs() }
            pruneStale(dir)
            FileTempHandle(File.createTempFile(prefix, suffix, dir), deleteOnJvmExit)
        }.getOrNull()
    }

    private fun pruneStale(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_MS
        dir.listFiles()?.forEach { child ->
            if (child.isFile && child.lastModified() in 1 until cutoff) {
                runCatching { child.delete() }
            }
        }
    }

    private companion object {
        const val STALE_MS = 24L * 60 * 60 * 1000
    }
}
