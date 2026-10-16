package io.music_assistant.client.offline

import android.content.Context
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Downloads live in app-private storage: removed with the app, never visible to other apps. */
class AndroidOfflineStorage(context: Context) : OfflineStorage {
    private val root = File(context.filesDir, "offline")
    private val index = File(root, "index.json")

    private fun resolve(relative: String): File {
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Invalid offline path" }
        return file
    }

    override suspend fun save(relative: String, channel: ByteReadChannel): Long = withContext(Dispatchers.IO) {
        val target = resolve(relative)
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        try {
            partial.outputStream().use { channel.copyTo(it) }
            check(partial.renameTo(target)) { "Could not save the download." }
            target.length()
        } finally {
            partial.delete()
        }
    }

    override suspend fun deleteTree(relative: String) {
        withContext(Dispatchers.IO) { resolve(relative).deleteRecursively() }
    }

    override suspend fun readIndex(): String? = withContext(Dispatchers.IO) {
        index.takeIf { it.isFile }?.readText()
    }

    override suspend fun writeIndex(text: String) = withContext(Dispatchers.IO) {
        root.mkdirs()
        val partial = File(root, "index.json.part")
        partial.writeText(text)
        check(partial.renameTo(index)) { "Could not save the downloads list." }
    }

    override fun absolutePath(relative: String): String = resolve(relative).path
}
