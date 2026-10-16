package io.music_assistant.client.offline

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import co.touchlab.kermit.Logger
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Drives [OfflinePlaybackService] through a MediaController (binding keeps it alive). */
class AndroidOfflinePlayer(
    private val context: Context,
    private val storage: OfflineStorage,
) : OfflinePlayer {
    private val log = Logger.withTag("OfflinePlayer")
    private val _playback = MutableStateFlow(OfflinePlayback())
    override val playback: StateFlow<OfflinePlayback> = _playback.asStateFlow()
    private var album: OfflineAlbum? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _playback.value = OfflinePlayback(album, player.currentMediaItemIndex, player.isPlaying)
        }
    }

    /** Run [action] once connected; reconnects after the service was stopped. */
    private fun withController(action: (MediaController) -> Unit) {
        val future = controllerFuture?.takeUnless { it.isCancelled }
            ?: MediaController.Builder(context, SessionToken(context, ComponentName(context, OfflinePlaybackService::class.java)))
                .buildAsync().also { controllerFuture = it }
        future.addListener({
            runCatching { future.get() }
                .onSuccess { controller ->
                    if (!controller.isConnected) {
                        controllerFuture = null
                        return@onSuccess withController(action)
                    }
                    controller.removeListener(listener)
                    controller.addListener(listener)
                    action(controller)
                }
                .onFailure {
                    log.w(it) { "Offline player unavailable" }
                    controllerFuture = null
                }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun play(album: OfflineAlbum, index: Int) {
        this.album = album
        val artwork = album.artwork?.let { Uri.fromFile(File(storage.absolutePath(it))) }
        val items = album.tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(Uri.fromFile(File(storage.absolutePath(track.file))))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artists.joinToString(", ").ifEmpty { album.artistLine })
                        .setAlbumTitle(album.name)
                        .setAlbumArtist(album.artistLine)
                        .setTrackNumber(track.track)
                        .setArtworkUri(artwork)
                        .build(),
                )
                .build()
        }
        withController { controller ->
            controller.setMediaItems(items, index.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
            controller.prepare()
            controller.play()
        }
    }

    override fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

    override fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    override fun previous() = withController { it.seekToPrevious() }

    override fun stop() {
        withController { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
        album = null
        _playback.value = OfflinePlayback()
    }
}
