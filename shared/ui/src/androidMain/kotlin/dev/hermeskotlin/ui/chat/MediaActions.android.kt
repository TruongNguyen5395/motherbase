package dev.hermeskotlin.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.hermeskotlin.ui.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberMediaActions(): MediaActions {
    val context = LocalContext.current
    return remember(context) { AndroidMediaActions(context) }
}

private class AndroidMediaActions(private val context: Context) : MediaActions {

    override suspend fun save(bytes: ByteArray, name: String): String =
        saveStream(name) { write ->
            write(bytes)
            null
        }

    override suspend fun saveStream(
        name: String,
        streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
    ): String = withContext(Dispatchers.IO) {
        val safeName = safeLocalFileName(name)
        // MediaStore renames a file whose extension doesn't match its type (todo.md as text/plain became
        // todo.md.txt), so take the type from Android's own table; unknown types keep the name as is.
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(safeName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val image = mime.startsWith("image/")
        // Before Android 10 this needs a storage permission; sharing to Files covers those phones.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext getString(Res.string.android_save_needs_android_10)
        val collection = if (image) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/MotherBase"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return@withContext getString(Res.string.android_save_failed, safeName)
        var committed = false
        try {
            val raw = resolver.openOutputStream(uri) ?: error("no stream")
            val streamError = BufferedOutputStream(raw).use { out ->
                streamTo { chunk -> if (chunk.isNotEmpty()) out.write(chunk) }
            }
            if (streamError != null) return@withContext streamError
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            committed = true
            getString(Res.string.android_saved_to, folder)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            getString(Res.string.android_save_failed, safeName)
        } finally {
            if (!committed) runCatching { resolver.delete(uri, null, null) }
        }
    }

    override fun share(bytes: ByteArray, name: String) {
        val file = createShareFile(name).apply { writeBytes(bytes) }
        launchShareIntent(file, name)
    }

    override suspend fun shareStream(
        name: String,
        streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
    ): String? {
        var dir: File? = null
        var file: File? = null
        var handedOff = false
        return try {
            withContext(Dispatchers.IO) {
                val created = createShareFile(name)
                dir = created.parentFile
                file = created
                val streamError = BufferedOutputStream(FileOutputStream(created)).use { out ->
                    streamTo { chunk -> if (chunk.isNotEmpty()) out.write(chunk) }
                }
                if (streamError != null) return@withContext streamError
                null
            }?.let { return it }
            launchShareIntent(file!!, name)
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

    private fun createShareFile(name: String): File {
        // A fresh folder per share keeps the original file name, which the receiving app shows.
        val root = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        pruneOldShares(root)
        val dir = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }
        return File(dir, safeLocalFileName(name))
    }

    private fun pruneOldShares(root: File) {
        val cutoff = System.currentTimeMillis() - SHARE_TTL_MS
        root.listFiles()?.forEach { child ->
            if (child.lastModified() in 1 until cutoff) runCatching { child.deleteRecursively() }
        }
    }

    private fun launchShareIntent(file: File, name: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeTypeOf(name))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val SHARE_DIR = "shared"
        const val SHARE_TTL_MS = 24L * 60 * 60 * 1000
    }
}
