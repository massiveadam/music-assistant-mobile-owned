package io.music_assistant.client.ui.compose.offline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.offline.AlbumDownload
import io.music_assistant.client.offline.OfflineAlbum
import io.music_assistant.client.offline.OfflineMusic
import org.koin.compose.koinInject

/** Album-page action: keep a local-library album on this phone for offline listening. */
@Composable
fun AlbumDownloadButton(album: Album) {
    val offline = koinInject<OfflineMusic>()
    if (!offline.supported) return
    val uri = album.uri ?: return
    LaunchedEffect(uri) { offline.check(uri) }
    val downloads by offline.downloads.collectAsStateWithLifecycle()
    val state = downloads[uri] ?: AlbumDownload.Unknown
    var confirmRemove by remember(uri) { mutableStateOf(false) }
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-download")
    when (state) {
        AlbumDownload.Unknown, AlbumDownload.Unavailable -> Unit
        is AlbumDownload.Available -> OutlinedButton(onClick = { offline.download(uri) }, modifier = modifier) {
            Text("Download for offline (${state.tracks} tracks)")
        }
        is AlbumDownload.Downloading -> Column(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { offline.cancel(uri) }, modifier = modifier) {
                Text("Downloading ${state.done}/${state.total} · Cancel")
            }
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        AlbumDownload.Downloaded -> OutlinedButton(onClick = { confirmRemove = true }, modifier = modifier) {
            Text("Downloaded · Remove from phone")
        }
        is AlbumDownload.Failed -> Column(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { offline.download(uri) }, modifier = modifier) { Text("Download failed · Retry") }
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmRemove) {
        RemoveDialog(album.name, onConfirm = { offline.remove(uri); confirmRemove = false },
            onDismiss = { confirmRemove = false })
    }
}

@Composable
private fun RemoveDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove download?") },
        text = { Text("$name will no longer be available offline. It stays in your library.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shortcut on the connection screen, shown once something is downloaded. */
@Composable
fun OfflineDownloadsEntry(onOpen: () -> Unit) {
    val offline = koinInject<OfflineMusic>()
    if (!offline.supported) return
    val albums by offline.albums.collectAsStateWithLifecycle()
    if (albums.isEmpty()) return
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-entry")) {
        Text("Downloads · listen offline (${albums.size} ${if (albums.size == 1) "album" else "albums"})")
    }
}

/** Downloaded albums and a small player. Works with no server connection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    val offline = koinInject<OfflineMusic>()
    val albums by offline.albums.collectAsStateWithLifecycle()
    val playback by offline.playback.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<OfflineAlbum?>(null) }
    Scaffold(
        modifier = Modifier.padding(contentPadding).testTag("offline-downloads"),
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = { playback.track?.let { NowPlayingBar(offline, it.name, playback.album?.artistLine.orEmpty(), playback.playing) } },
    ) { padding ->
        when {
            !offline.supported -> Message(padding, "Offline listening isn't available on this device yet.")
            albums.isEmpty() -> Message(padding,
                "No downloads yet. Open an album from your local library and choose Download for offline.\n\n" +
                    "Apple Music and other streaming albums can't be downloaded here.")
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(albums.sortedBy { it.artistLine.lowercase() + "\u0000" + it.name.lowercase() }, key = { it.uri }) { album ->
                    ListItem(
                        modifier = Modifier.clickable { offline.play(album) },
                        leadingContent = {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))) {
                                album.artwork?.let { AsyncImage(model = offline.absolutePath(it), contentDescription = null) }
                            }
                        },
                        headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text("${album.artistLine} · ${album.tracks.size} tracks · ${album.bytes / 1_048_576} MB",
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            IconButton(onClick = { removing = album }) { Icon(Icons.Default.Delete, "Remove ${album.name}") }
                        },
                    )
                }
            }
        }
    }
    removing?.let { album ->
        RemoveDialog(album.name, onConfirm = { offline.remove(album.uri); removing = null }, onDismiss = { removing = null })
    }
}

@Composable
private fun Message(padding: PaddingValues, text: String) {
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NowPlayingBar(offline: OfflineMusic, title: String, artist: String, playing: Boolean) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = offline::previous) { Icon(Icons.Default.SkipPrevious, "Previous") }
            IconButton(onClick = offline::togglePlayPause) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play")
            }
            IconButton(onClick = offline::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}
