@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
import kotlin.time.Instant

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
    var chooseLimit by remember { mutableStateOf(false) }
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

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(source.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { chooseLimit = true }) { Text("$limit albums") }
                DropdownMenu(expanded = chooseLimit, onDismissRequest = { chooseLimit = false }) {
                    listOf(10, 20, 25, 50).forEach { count ->
                        DropdownMenuItem(text = { Text("$count albums") }, onClick = {
                            limit = count; settings.setEditorialLimit(source.id, count, owner); chooseLimit = false
                        })
                    }
                }
            }
            TextButton(onClick = { reload++ }, enabled = enabled && !loading) { Text("Reload") }
            TextButton(onClick = { openLink(feed?.source?.url ?: source.url) }) { Text("Open source") }
        }
        feed?.let { value ->
            val window = value.window
            val range = window?.let { "${it.start} to ${it.end}. " }.orEmpty()
            val order = when (window?.ranking) { "critic" -> "Critic score order."; "user" -> "User score order."; else -> "Source chart order." }
            val period = if (window?.dateField == "review_date") "Reviews published this week." else "Released in the last seven days."
            Text("$range$period $order" + if (value.stale) " Older snapshot." else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            val captured = value.fetchedAt?.let { runCatching { Instant.fromEpochSeconds(it.toLong()).toString().take(10) }.getOrNull() }
            Text(captured?.let { "Snapshot captured $it." } ?: "Capture date unavailable.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            if (value.note.isNotBlank()) Text(value.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
        }
        when {
            !enabled -> Text("Enable this row to see albums and scores.", modifier = Modifier.padding(16.dp))
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            loading && feed == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            feed?.entries?.isEmpty() == true -> Text(
                if (feed?.window?.dateField == "review_date") "No Best New Music reviews from the last seven days in the current snapshot."
                else "No releases from the last seven days in the current snapshot.", modifier = Modifier.padding(16.dp))
            else -> LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(feed?.entries.orEmpty().take(limit), key = { "${it.artist}:${it.title}" }) { entry ->
                    Column(Modifier.width(176.dp)) {
                        Box(Modifier.size(176.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text(entry.title.take(1), style = MaterialTheme.typography.displayLarge)
                            AsyncImage(model = entry.image, contentDescription = "${entry.title} by ${entry.artist}",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
                        Text(entry.artist, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        entry.releaseDate?.let { Text("Released $it", style = MaterialTheme.typography.bodySmall) }
                        fun scoreText(score: EditorialScore) = "${score.value}/${score.max}"
                        entry.scores.critic?.let {
                            Column { Text("Critics ${scoreText(it)}", style = MaterialTheme.typography.bodySmall)
                                it.count?.let { count -> Text("$count ${it.countLabel ?: "reviews"}", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        entry.scores.user?.let {
                            Column { Text("Users ${scoreText(it)}", style = MaterialTheme.typography.bodySmall)
                                it.count?.let { count -> Text("$count ${it.countLabel ?: "ratings"}", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        if (entry.scores.critic == null && entry.scores.user == null) Text("No score available", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { find(entry) }) { Text("Find album") }
                        TextButton(onClick = { openLink(entry.url) }) { Text("Read review") }
                    }
                }
            }
        }
    }
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
