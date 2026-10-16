package io.music_assistant.client.offline

import co.touchlab.kermit.Logger
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.ByteReadChannel
import io.music_assistant.client.api.PersonalApi
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A downloaded local-library track. [file] is relative to the offline storage root. */
@Serializable
data class OfflineTrack(
    val uri: String,
    val name: String,
    val artists: List<String> = emptyList(),
    val disc: Int = 1,
    val track: Int = 0,
    val durationSeconds: Double? = null,
    val file: String,
)

@Serializable
data class OfflineAlbum(
    val uri: String,
    val name: String,
    val artists: List<String> = emptyList(),
    val year: Int? = null,
    /** Relative artwork path, or null when the album has no local cover. */
    val artwork: String? = null,
    val tracks: List<OfflineTrack>,
    val bytes: Long,
    val downloadedAt: Long,
) {
    val artistLine get() = artists.joinToString(", ")
}

@Serializable
internal data class OfflineIndex(val version: Int = 1, val albums: List<OfflineAlbum> = emptyList())

/** What the server says can be downloaded for one album. */
data class OfflineManifest(
    val uri: String,
    val name: String,
    val artists: List<String>,
    val year: Int?,
    val artwork: Boolean,
    val tracks: List<ManifestTrack>,
) {
    data class ManifestTrack(
        val uri: String, val name: String, val artists: List<String>, val disc: Int, val track: Int,
        val durationSeconds: Double?, val downloadable: Boolean,
    )

    val downloadable get() = tracks.filter { it.downloadable }
}

sealed interface AlbumDownload {
    data object Unknown : AlbumDownload
    /** No local files: streaming-only albums cannot be kept offline. */
    data object Unavailable : AlbumDownload
    data class Available(val tracks: Int) : AlbumDownload
    data class Downloading(val done: Int, val total: Int) : AlbumDownload
    data object Downloaded : AlbumDownload
    data class Failed(val message: String) : AlbumDownload
}

data class OfflinePlayback(
    val album: OfflineAlbum? = null,
    val index: Int = 0,
    val playing: Boolean = false,
) {
    val track get() = album?.tracks?.getOrNull(index)
}

/** Files under the app's private offline directory. Paths are always relative. */
interface OfflineStorage {
    /** Write [channel] to [relative] atomically and return its size in bytes. */
    suspend fun save(relative: String, channel: ByteReadChannel): Long
    suspend fun deleteTree(relative: String)
    suspend fun readIndex(): String?
    suspend fun writeIndex(text: String)
    fun absolutePath(relative: String): String
}

/** Plays downloaded files on the device, without the server. */
interface OfflinePlayer {
    val playback: StateFlow<OfflinePlayback>
    fun play(album: OfflineAlbum, index: Int)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun stop()
}

interface OfflineMusic {
    val supported: Boolean
    val albums: StateFlow<List<OfflineAlbum>>
    val downloads: StateFlow<Map<String, AlbumDownload>>
    val playback: StateFlow<OfflinePlayback>
    fun check(albumUri: String)
    fun download(albumUri: String)
    fun cancel(albumUri: String)
    fun remove(albumUri: String)
    fun play(album: OfflineAlbum, index: Int = 0)
    fun togglePlayPause()
    fun next()
    fun previous()
    fun absolutePath(relative: String): String
}

/** Platforms without offline playback (iOS for now). */
object UnsupportedOfflineMusic : OfflineMusic {
    override val supported = false
    override val albums: StateFlow<List<OfflineAlbum>> = MutableStateFlow(emptyList())
    override val downloads: StateFlow<Map<String, AlbumDownload>> = MutableStateFlow(emptyMap())
    override val playback: StateFlow<OfflinePlayback> = MutableStateFlow(OfflinePlayback())
    override fun check(albumUri: String) = Unit
    override fun download(albumUri: String) = Unit
    override fun cancel(albumUri: String) = Unit
    override fun remove(albumUri: String) = Unit
    override fun play(album: OfflineAlbum, index: Int) = Unit
    override fun togglePlayPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun absolutePath(relative: String) = relative
}

internal fun parseOfflineManifest(json: JsonObject): OfflineManifest {
    fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    fun JsonObject.artists() = this["artists"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    return OfflineManifest(
        uri = json.string("uri") ?: error("Missing album"),
        name = json.string("name").orEmpty(),
        artists = json.artists(),
        year = json["year"]?.jsonPrimitive?.intOrNull,
        artwork = json["artwork"]?.jsonPrimitive?.booleanOrNull == true,
        tracks = json["tracks"]?.jsonArray.orEmpty().map { it.jsonObject }.map { track ->
            OfflineManifest.ManifestTrack(
                uri = track.string("uri").orEmpty(), name = track.string("name").orEmpty(),
                artists = track.artists(), disc = track["disc"]?.jsonPrimitive?.intOrNull ?: 1,
                track = track["track"]?.jsonPrimitive?.intOrNull ?: 0,
                durationSeconds = track["duration"]?.jsonPrimitive?.doubleOrNull,
                downloadable = track["downloadable"]?.jsonPrimitive?.booleanOrNull == true &&
                    !track.string("uri").isNullOrBlank(),
            )
        },
    )
}

/** Stable folder name for an album URI (URIs contain ':' and '/'): 64-bit FNV-1a hex. */
internal fun albumFolder(uri: String): String {
    var hash = -0x340d631b7bdddcdbL // FNV offset basis
    for (byte in uri.encodeToByteArray()) {
        hash = (hash xor (byte.toLong() and 0xff)) * 0x100000001b3L
    }
    return "a" + hash.toULong().toString(16).padStart(16, '0')
}

private fun query(uri: String) = "?uri=" + uri.encodeURLParameter()

/** Server access for offline downloads; separated from [PersonalApi] for tests. */
interface OfflineTransport {
    suspend fun json(path: String): JsonObject

    /** Bound to the signed-in account and server at the start of a download. */
    fun downloader(): Downloader

    fun interface Downloader {
        suspend fun save(path: String, sink: suspend (ByteReadChannel) -> Long): Long
    }
}

class PersonalApiTransport(private val api: PersonalApi) : OfflineTransport {
    override suspend fun json(path: String) = api.request(path)
    override fun downloader(): OfflineTransport.Downloader {
        val session = api.session() ?: error("Sign in to download music.")
        return OfflineTransport.Downloader { path, sink -> api.download(path, session, sink) }
    }
}

class OfflineLibrary(
    private val api: OfflineTransport,
    private val storage: OfflineStorage,
    private val player: OfflinePlayer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val now: () -> Long = ::currentTimeMillis,
) : OfflineMusic {
    private val log = Logger.withTag("OfflineLibrary")
    private val _albums = MutableStateFlow<List<OfflineAlbum>>(emptyList())
    private val _downloads = MutableStateFlow<Map<String, AlbumDownload>>(emptyMap())
    private val jobs = mutableMapOf<String, Job>()
    private val loaded = scope.launch {
        _albums.value = runCatching { storage.readIndex()?.let { myJson.decodeFromString<OfflineIndex>(it).albums } }
            .onFailure { log.w(it) { "Unreadable offline index" } }.getOrNull().orEmpty()
    }

    override val supported = true
    override val albums = _albums.asStateFlow()
    override val downloads = _downloads.asStateFlow()
    override val playback get() = player.playback

    private fun setState(uri: String, state: AlbumDownload) = _downloads.update { it + (uri to state) }

    override fun check(albumUri: String) {
        scope.launch {
            loaded.join()
            if (_albums.value.any { it.uri == albumUri }) return@launch setState(albumUri, AlbumDownload.Downloaded)
            if (_downloads.value[albumUri] is AlbumDownload.Downloading) return@launch
            val state = runCatching { manifest(albumUri) }.fold(
                { manifest -> manifest.downloadable.size.let { if (it > 0) AlbumDownload.Available(it) else AlbumDownload.Unavailable } },
                // An older server without offline support simply offers no download.
                { AlbumDownload.Unavailable },
            )
            setState(albumUri, state)
        }
    }

    private suspend fun manifest(albumUri: String) =
        parseOfflineManifest(api.json("/credits/v1/offline/album" + query(albumUri)))

    override fun download(albumUri: String) {
        if (jobs[albumUri]?.isActive == true) return
        jobs[albumUri] = scope.launch {
            val folder = albumFolder(albumUri)
            try {
                loaded.join()
                val downloader = api.downloader()
                val manifest = manifest(albumUri)
                val wanted = manifest.downloadable
                check(wanted.isNotEmpty()) { "This album has no local files to download." }
                setState(albumUri, AlbumDownload.Downloading(0, wanted.size))
                var bytes = 0L
                val tracks = wanted.mapIndexed { index, track ->
                    val file = "$folder/${(index + 1).toString().padStart(3, '0')}.opus"
                    bytes += downloader.save("/credits/v1/offline/track" + query(track.uri)) { storage.save(file, it) }
                    setState(albumUri, AlbumDownload.Downloading(index + 1, wanted.size))
                    OfflineTrack(track.uri, track.name, track.artists, track.disc, track.track,
                        track.durationSeconds, file)
                }
                val artwork = if (manifest.artwork) runCatching {
                    "$folder/cover.jpg".also { path ->
                        bytes += downloader.save("/credits/v1/offline/artwork" + query(albumUri)) { storage.save(path, it) }
                    }
                }.getOrNull() else null
                val album = OfflineAlbum(manifest.uri.ifBlank { albumUri }, manifest.name, manifest.artists,
                    manifest.year, artwork, tracks, bytes, now())
                _albums.update { current -> current.filterNot { it.uri == album.uri } + album }
                persist()
                setState(albumUri, AlbumDownload.Downloaded)
            } catch (cancelled: CancellationException) {
                storage.deleteTree(folder)
                setState(albumUri, AlbumDownload.Unknown)
                throw cancelled
            } catch (error: Throwable) {
                log.w(error) { "Offline download failed" }
                storage.deleteTree(folder)
                setState(albumUri, AlbumDownload.Failed(error.message ?: "Download failed."))
            } finally {
                jobs.remove(albumUri)
            }
        }
    }

    override fun cancel(albumUri: String) {
        jobs[albumUri]?.cancel()
    }

    override fun remove(albumUri: String) {
        scope.launch {
            loaded.join()
            if (player.playback.value.album?.uri == albumUri) player.stop()
            _albums.update { current -> current.filterNot { it.uri == albumUri } }
            persist()
            storage.deleteTree(albumFolder(albumUri))
            _downloads.update { it - albumUri }
        }
    }

    private suspend fun persist() = storage.writeIndex(myJson.encodeToString(OfflineIndex(albums = _albums.value)))

    override fun play(album: OfflineAlbum, index: Int) = player.play(album, index)
    override fun togglePlayPause() = player.togglePlayPause()
    override fun next() = player.next()
    override fun previous() = player.previous()
    override fun absolutePath(relative: String) = storage.absolutePath(relative)
}
