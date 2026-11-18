package dev.hermeskotlin.ui.chat

import androidx.compose.runtime.Composable

internal typealias MediaByteSink = suspend (ByteArray) -> Unit
internal typealias MediaProgress = (downloaded: Long, total: Long?) -> Unit

/** A local temporary file backing streamed previews and saves without holding the file in memory. */
internal interface TempFileHandle : AutoCloseable {
    val byteSize: Long

    suspend fun append(chunk: ByteArray)

    suspend fun finishWrite()

    suspend fun streamTo(onProgress: MediaProgress? = null, write: MediaByteSink)
}

internal interface TempFileFactory {
    suspend fun create(prefix: String, suffix: String): TempFileHandle?
}

@Composable
internal expect fun rememberTempFileFactory(): TempFileFactory

internal fun safeLocalFileName(name: String): String {
    val leaf = name.substringAfterLast('/').substringAfterLast('\\').trim()
    val clean = leaf.replace("..", "_").filterNot { it < ' ' || it in "<>:\"|?*" }
    return clean.ifBlank { "file" }
}
