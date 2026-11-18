package dev.hermeskotlin.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.hermeskotlin.ui.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.awt.Desktop
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Save is the system's Save dialog; with no share sheet, Share opens the file in its default app. */
@Composable
actual fun rememberMediaActions(): MediaActions = remember {
    object : MediaActions {
        override suspend fun save(bytes: ByteArray, name: String): String =
            saveOutcome(bytes, name)?.text ?: getString(Res.string.desktop_save_cancelled)

        override suspend fun saveOutcome(bytes: ByteArray, name: String): SaveOutcome? =
            saveStreamOutcome(name) { write ->
                write(bytes)
                null
            }

        override suspend fun saveStream(
            name: String,
            streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
        ): String = saveStreamOutcome(name, streamTo)?.text ?: getString(Res.string.desktop_save_cancelled)

        private suspend fun saveStreamOutcome(
            name: String,
            streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
        ): SaveOutcome? {
            val title = getString(Res.string.desktop_save_title)
            val targetName = safeLocalFileName(name)
            val file = fromDialog { saveFile(title, targetName) }
                .getOrElse { return SaveOutcome(getString(Res.string.desktop_save_dialog_failed), saved = false) }
                ?: return null
            return withContext(Dispatchers.IO) {
                val part = runCatching {
                    file.parentFile?.mkdirs()
                    File.createTempFile(".motherbase-save-", ".part", file.parentFile)
                }.getOrNull()
                val dest = part ?: file
                var committed = false
                try {
                    val streamError = BufferedOutputStream(FileOutputStream(dest)).use { out ->
                        streamTo { chunk -> if (chunk.isNotEmpty()) out.write(chunk) }
                    }
                    if (streamError != null) {
                        return@withContext SaveOutcome(streamError, saved = false)
                    }
                    if (part != null) {
                        try {
                            Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        } catch (_: Exception) {
                            part.copyTo(file, overwrite = true)
                            part.delete()
                        }
                    }
                    committed = true
                    SaveOutcome(getString(Res.string.desktop_saved_to, file.path), saved = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SaveOutcome(getString(Res.string.desktop_save_failed, e.message.toString()), saved = false)
                } finally {
                    if (!committed) runCatching { part?.delete() }
                }
            }
        }

        override fun share(bytes: ByteArray, name: String) {
            runCatching {
                val dir = Files.createTempDirectory("herald").toFile().apply { deleteOnExit() }
                val file = File(dir, safeLocalFileName(name)).apply { writeBytes(bytes); deleteOnExit() }
                Desktop.getDesktop().open(file)
            }
        }

        override suspend fun shareStream(
            name: String,
            streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
        ): String? = withContext(Dispatchers.IO) {
            var dir: File? = null
            var file: File? = null
            var handedOff = false
            try {
                dir = Files.createTempDirectory("herald").toFile().apply { deleteOnExit() }
                file = File(dir, safeLocalFileName(name)).apply { deleteOnExit() }
                val streamError = BufferedOutputStream(FileOutputStream(file)).use { out ->
                    streamTo { chunk -> if (chunk.isNotEmpty()) out.write(chunk) }
                }
                if (streamError != null) return@withContext streamError
                Desktop.getDesktop().open(file)
                handedOff = true
                null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                getString(Res.string.chat_file_could_not_open)
            } finally {
                if (!handedOff) {
                    runCatching { file?.delete() }
                    runCatching { dir?.delete() }
                }
            }
        }
    }
}
