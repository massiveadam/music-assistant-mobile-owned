package io.music_assistant.client.offline

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val ALBUM = "library://album/572"

private fun manifest(vararg downloadable: Boolean, artwork: Boolean = true) = myJson.decodeFromString<JsonObject>(
    """{"uri":"$ALBUM","name":"Manning Fireworks","artists":["MJ Lenderman"],"year":2024,"artwork":$artwork,
    "tracks":[${downloadable.mapIndexed { i, ok ->
        """{"uri":"library://track/${i + 1}","name":"Track ${i + 1}","artists":["MJ Lenderman"],"disc":1,
        "track":${i + 1},"duration":180,"downloadable":$ok}"""
    }.joinToString(",")}]}""",
)

private class FakeTransport(var manifest: JsonObject?, val failOn: String? = null) : OfflineTransport {
    val downloaded = mutableListOf<String>()
    override suspend fun json(path: String) = manifest ?: error("HTTP 404")
    override fun downloader() = OfflineTransport.Downloader { path, sink ->
        check(failOn == null || !path.contains(failOn)) { "Download failed (HTTP 502)." }
        downloaded += path
        sink(ByteReadChannel(byteArrayOf(1, 2, 3)))
    }
}

private class FakeStorage : OfflineStorage {
    val files = mutableMapOf<String, ByteArray>()
    var index: String? = null
    override suspend fun save(relative: String, channel: ByteReadChannel): Long =
        channel.readRemaining().readByteArray().also { files[relative] = it }.size.toLong()
    override suspend fun deleteTree(relative: String) { files.keys.removeAll { it.startsWith("$relative/") } }
    override suspend fun readIndex() = index
    override suspend fun writeIndex(text: String) { index = text }
    override fun absolutePath(relative: String) = "/data/offline/$relative"
}

private class FakePlayer : OfflinePlayer {
    override val playback = MutableStateFlow(OfflinePlayback())
    var stopped = false
    override fun play(album: OfflineAlbum, index: Int) { playback.value = OfflinePlayback(album, index, true) }
    override fun togglePlayPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun stop() { stopped = true; playback.value = OfflinePlayback() }
}

class OfflineLibraryTest {
    // Not backgroundScope: TestScope.advanceUntilIdle() does not wait for background work.
    private fun TestScope.library(transport: FakeTransport, storage: FakeStorage = FakeStorage(), player: FakePlayer = FakePlayer()) =
        OfflineLibrary(transport, storage, player,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), now = { 42L })

    @Test
    fun `album with local files offers a download of only those tracks`() = runTest {
        val offline = library(FakeTransport(manifest(true, false, true)))
        offline.check(ALBUM)
        advanceUntilIdle()
        assertEquals(AlbumDownload.Available(2), offline.downloads.value[ALBUM])
    }

    @Test
    fun `streaming only album or an old server offers no download`() = runTest {
        val offline = library(FakeTransport(manifest(false, false)))
        offline.check(ALBUM)
        val old = library(FakeTransport(null))
        old.check("library://album/9")
        advanceUntilIdle()
        assertEquals(AlbumDownload.Unavailable, offline.downloads.value[ALBUM])
        assertEquals(AlbumDownload.Unavailable, old.downloads.value["library://album/9"])
    }

    @Test
    fun `download saves tracks in order with artwork and persists the index`() = runTest {
        val transport = FakeTransport(manifest(true, false, true))
        val storage = FakeStorage()
        val offline = library(transport, storage)
        offline.download(ALBUM)
        advanceUntilIdle()
        assertEquals(AlbumDownload.Downloaded, offline.downloads.value[ALBUM])
        val album = offline.albums.value.single()
        assertEquals(listOf("Track 1", "Track 3"), album.tracks.map { it.name })
        assertEquals(9L, album.bytes) // two tracks and the cover, 3 bytes each
        assertEquals(42L, album.downloadedAt)
        assertTrue(album.artwork!!.endsWith("/cover.jpg"))
        assertEquals(3, storage.files.size)
        assertEquals(listOf("library%3A%2F%2Ftrack%2F1", "library%3A%2F%2Ftrack%2F3"),
            transport.downloaded.filter { "/track" in it }.map { it.substringAfter("uri=") })
        assertTrue(storage.index!!.contains("Manning Fireworks"))

        // A new process reads the saved index.
        val reloaded = library(FakeTransport(null), storage)
        advanceUntilIdle()
        assertEquals(album, reloaded.albums.value.single())
        reloaded.check(ALBUM)
        advanceUntilIdle()
        assertEquals(AlbumDownload.Downloaded, reloaded.downloads.value[ALBUM])
    }

    @Test
    fun `a failed track leaves no partial album behind`() = runTest {
        val storage = FakeStorage()
        val offline = library(FakeTransport(manifest(true, true), failOn = "track%2F2"), storage)
        offline.download(ALBUM)
        advanceUntilIdle()
        val state = offline.downloads.value[ALBUM]
        assertIs<AlbumDownload.Failed>(state)
        assertEquals("Download failed (HTTP 502).", state.message)
        assertTrue(offline.albums.value.isEmpty())
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `removing the playing album stops playback and deletes its files`() = runTest {
        val storage = FakeStorage()
        val player = FakePlayer()
        val offline = library(FakeTransport(manifest(true)), storage, player)
        offline.download(ALBUM)
        advanceUntilIdle()
        offline.play(offline.albums.value.single())
        offline.remove(ALBUM)
        advanceUntilIdle()
        assertTrue(player.stopped)
        assertTrue(offline.albums.value.isEmpty())
        assertTrue(storage.files.isEmpty())
        assertEquals(null, offline.downloads.value[ALBUM])
    }

    @Test
    fun `folder names are stable and distinct`() {
        assertEquals(albumFolder(ALBUM), albumFolder(ALBUM))
        assertNotEquals(albumFolder(ALBUM), albumFolder("library://album/5720"))
        assertTrue(albumFolder("apple_music://album/1/../x").matches(Regex("a[0-9a-f]{16}")))
    }
}
