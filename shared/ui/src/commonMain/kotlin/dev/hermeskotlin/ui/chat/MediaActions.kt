package dev.hermeskotlin.ui.chat

import androidx.compose.runtime.Composable

/** Saving and sharing a picture or file the chat shows. */
interface MediaActions {
    /** Saves to the device's shared storage; returns a sentence saying where, or what went wrong. */
    suspend fun save(bytes: ByteArray, name: String): String

    /**
     * [save], and what came of it, so a caller doesn't read that from the sentence (which is in the user's
     * language): null when a Save dialog was cancelled and there's nothing to tell. Where saving has no dialog and
     * can't tell, the sentence counts as saved.
     */
    suspend fun saveOutcome(bytes: ByteArray, name: String): SaveOutcome? = SaveOutcome(save(bytes, name), saved = true)

    /** Opens the system share sheet for the file. */
    fun share(bytes: ByteArray, name: String)

    /**
     * Picks or opens the save destination first, then streams chunks through [streamTo] straight to disk.
     * [streamTo] returns null on success or a localized failure reason when transfer fails.
     */
    suspend fun saveStream(
        name: String,
        streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
    ): String {
        val chunks = mutableListOf<ByteArray>()
        val error = streamTo { chunks += it }
        if (error != null) return error
        val total = chunks.sumOf { it.size }
        val merged = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(merged, offset)
            offset += chunk.size
        }
        return save(merged, name)
    }

    /**
     * Streams chunks into temporary storage through [streamTo] and hands the file to the system
     * opener or share sheet. Returns a localized error notice on failure, or null on success.
     */
    suspend fun shareStream(
        name: String,
        streamTo: suspend (write: suspend (ByteArray) -> Unit) -> String?,
    ): String? {
        val chunks = mutableListOf<ByteArray>()
        val error = streamTo { chunks += it }
        if (error != null) return error
        val total = chunks.sumOf { it.size }
        val merged = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(merged, offset)
            offset += chunk.size
        }
        share(merged, name)
        return null
    }
}

/** What a save said, and whether the file was written. */
class SaveOutcome(val text: String, val saved: Boolean)

@Composable
expect fun rememberMediaActions(): MediaActions

/** A MIME type from a file name, for saving and sharing. */
internal fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "avif" -> "image/avif"
    "ico" -> "image/x-icon"
    "tif", "tiff" -> "image/tiff"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    "txt", "log", "md" -> "text/plain"
    "csv" -> "text/csv"
    "html", "htm" -> "text/html"
    "json" -> "application/json"
    "zip" -> "application/zip"
    "mp3" -> "audio/mpeg"
    "mp4" -> "video/mp4"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    else -> "application/octet-stream"
}
