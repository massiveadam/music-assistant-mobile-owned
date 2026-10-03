@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.PersonalApi
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class EditorialHomeState(val session: PersonalApi.Session?) {
    var sources by mutableStateOf<List<EditorialSource>>(emptyList())
    var error by mutableStateOf<String?>(null)
}

@Composable
fun rememberEditorialHome(refresh: Int): EditorialHomeState {
    val api = koinInject<PersonalApi>()
    val connection by api.serviceClient.sessionState.collectAsStateWithLifecycle()
    val session = remember(connection) { api.session() }
    val state = remember(session) { EditorialHomeState(session) }
    LaunchedEffect(state, refresh) {
        val current = state.session ?: return@LaunchedEffect
        state.error = null
        try {
            val catalog = parseEditorialCatalog(api.request("/credits/v1/editorial", session = current))
            api.requireCurrent(current)
            state.sources = catalog.sources
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { state.error = "Review-site albums could not be loaded. Refresh to try again." }
    }
    return state
}

@Composable
fun EditorialShelf(
    source: EditorialSource,
    session: PersonalApi.Session?,
    enabled: Boolean,
    refresh: Int,
    onNavigate: (Album) -> Unit,
) {
    val api = koinInject<PersonalApi>()
    val media = koinInject<MediaItemRepository>()
    val settings = koinInject<SettingsRepository>()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val owner = session?.let { "${it.serverId}:${it.userId}" }
    var feed by remember(source.id, session) { mutableStateOf<EditorialFeed?>(null) }
    var loading by remember(source.id, session) { mutableStateOf(false) }
    var error by remember(source.id, session) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var limit by remember(source.id, session) { mutableStateOf(settings.editorialLimit(source.id, owner)) }
    var searchTitle by remember(session) { mutableStateOf<String?>(null) }
    var searchResults by remember(session) { mutableStateOf<List<Album>?>(null) }
    var searchError by remember(session) { mutableStateOf<String?>(null) }
    var searchGeneration by remember(session) { mutableStateOf(0) }

    LaunchedEffect(source.id, session, enabled, refresh, reload) {
        if (!enabled || session == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            val result = parseEditorialFeed(api.request("/credits/v1/editorial?source=${source.id}", session = session), source.id)
            api.requireCurrent(session)
            feed = result
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "This review list could not be loaded. Try again." }
        finally { loading = false }
    }

    fun openLink(url: String) {
        if (editorialHttpsUrl(url)) runCatching { uriHandler.openUri(url) }
            .onFailure { error = "No app could open this review link." }
    }
    fun find(entry: EditorialEntry) {
        val current = session ?: return
        val generation = ++searchGeneration
        searchTitle = "${entry.artist} - ${entry.title}"
        searchResults = null
        searchError = null
        scope.launch {
            try {
                api.requireCurrent(current)
                val candidates = media.search(Request.Library.search(
                    "${entry.artist} ${entry.title}", listOf(MediaType.ALBUM), 20, libraryOnly = false,
                )).getOrThrow().albums
                api.requireCurrent(current)
                if (generation != searchGeneration) return@launch
                val exact = matchEditorialAlbum(entry, candidates)
                if (exact != null) { searchTitle = null; onNavigate(exact) }
                else searchResults = candidates
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == searchGeneration && current.same(api.session()))
                    searchError = "Album search failed. Close and try again."
            }
        }
    }

    EditorialShelfContent(
        source = source, feed = feed, limit = limit, enabled = enabled,
        loading = loading, error = error,
        onLimit = { count -> limit = count; settings.setEditorialLimit(source.id, count, owner) },
        onReload = { reload++ }, onSource = { openLink(feed?.source?.url ?: source.url) },
        onFind = { find(it) }, onReview = { openLink(it.url) },
    )
    searchTitle?.let { title ->
        fun dismiss() { searchGeneration++; searchTitle = null }
        AlertDialog(onDismissRequest = { dismiss() }, title = { Text(title) },
            text = {
                when {
                    searchError != null -> Text(searchError!!)
                    searchResults == null -> CircularProgressIndicator()
                    searchResults!!.isEmpty() -> Text("No albums found in your available sources. The review is still available.")
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(searchResults!!) { album ->
                            TextButton(onClick = { dismiss(); onNavigate(album) }) {
                                Text("${album.displayName}\n${album.subtitle}\n${album.provider}")
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { dismiss() }) { Text("Close") } })
    }
}
