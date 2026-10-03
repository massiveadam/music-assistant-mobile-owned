@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.music_assistant.client.ui.compose.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.*
import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.data.repository.PersonalMusicRepository
import io.music_assistant.client.data.repository.personalUris
import io.music_assistant.client.ui.compose.common.items.*
import io.music_assistant.client.settings.ViewMode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun personalFavorite(item: AppMediaItem): Boolean {
    if (LocalInspectionMode.current) return item.favorite == true
    val repository = koinInject<PersonalMusicRepository>()
    val state by repository.state.collectAsStateWithLifecycle()
    return remember(state.saves, state.userId, item) { repository.isSaved(item) }
}

@Composable
fun PersonalSaveButton(item: AppMediaItem) {
    val repository = koinInject<PersonalMusicRepository>()
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val saved = personalFavorite(item)
    IconButton(enabled = !state.busy && state.userId != null, onClick = {
        scope.launch { repository.setSaved(item, !saved) }
    }) {
        Icon(if (saved) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            if (saved) "Remove from My music" else "Save to My music")
    }
}

@Composable
fun AlbumCollectionControls(album: Album) {
    val repository = koinInject<PersonalMusicRepository>()
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var chooser by rememberSaveable(album.uri) { mutableStateOf(false) }
    val inLater = state.albums?.collections?.firstOrNull { it.id == PersonalMusicRepository.LISTEN_LATER }
        ?.contains(album.personalUris()) == true
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PersonalSaveButton(album)
        OutlinedButton(enabled = !state.busy && state.albums != null, onClick = {
            scope.launch { repository.collectionAction(if (inLater) "remove" else "add",
                PersonalMusicRepository.LISTEN_LATER, album.mediaUri) }
        }) { Text(if (inLater) "In Listen Later" else "Listen Later") }
        SendAlbumButton(album)
        OutlinedButton(onClick = { chooser = true }) { Text("Collections") }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (chooser) AlbumCollectionChooser(album) { chooser = false }
}

@Composable
fun AlbumCollectionChooser(album: Album, onDismiss: () -> Unit) {
    val repository = koinInject<PersonalMusicRepository>()
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (state.albums == null) repository.refresh() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add album to collections") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(state.albums?.collections.orEmpty(), key = { it.id }) { collection ->
                        val checked = collection.contains(album.personalUris())
                        Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy) {
                            scope.launch { repository.collectionAction(if (checked) "remove" else "add", collection.id, album.mediaUri) }
                        }) {
                            Checkbox(checked, enabled = !state.busy, onCheckedChange = { next ->
                                scope.launch { repository.collectionAction(if (next) "add" else "remove", collection.id, album.mediaUri) }
                            })
                            Text(collection.name, Modifier.padding(top = 12.dp))
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("New collection") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                TextButton(enabled = name.trim().length in 1..120 && !state.busy, onClick = {
                    val entered = name.trim()
                    scope.launch {
                        if (repository.createAndAdd(entered, album).isSuccess) name = ""
                    }
                }) { Text("Create and add album") }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun PersonalMusicScreen(contentPadding: PaddingValues, onBack: () -> Unit,
    onNavigateClick: (AppMediaItem) -> Unit, onPlayClick: PlayHandler<AppMediaItem>,
    actions: io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
) {
    val repository = koinInject<PersonalMusicRepository>()
    val factory = koinInject<MediaItemFactory>()
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("all") }
    var sortByTitle by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.refresh() }
    val entries = state.saves?.entries.orEmpty().sortedByDescending { it.savedAt }.filter {
        (type == "all" || it.item.mediaType == type) && it.item.name.contains(query.trim(), true)
    }.let { entries -> if (sortByTitle) entries.sortedBy { it.item.name.lowercase() } else entries }
        .mapNotNull { factory.create(it.item.asServer()) }
    Column(Modifier.fillMaxSize()) {
        PersonalTopBar("My music", onBack, { scope.launch { repository.refresh() } })
        OutlinedTextField(query, { query = it }, label = { Text("Search your saves") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all", "album", "artist", "track", "playlist", "radio").forEach { value ->
                FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !sortByTitle, onClick = { sortByTitle = false }, label = { Text("Recently saved") })
            FilterChip(selected = sortByTitle, onClick = { sortByTitle = true }, label = { Text("Title") })
        }
        PersonalError(state)
        if (state.saves?.unavailable?.let { it > 0 } == true) Text("${state.saves?.unavailable} saves currently unavailable", Modifier.padding(16.dp))
        if (entries.isEmpty() && !state.loading) Text("Save music with the heart. Your saves appear here on every device.", Modifier.padding(16.dp))
        LazyColumn(Modifier.testTag("PersonalMusicList"), contentPadding = contentPadding) {
            items(entries, key = { it.mediaUri ?: "${it.provider}/${it.itemId}" }) { item ->
                PersonalItemRow(item, onNavigateClick, onPlayClick, actions, providerIconFetcher)
            }
        }
    }
}

@Composable
private fun PersonalItemRow(item: AppMediaItem, onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>, actions: io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
) {
    when (item) {
        is Album -> AlbumWithMenu(item, ViewMode.LIST, onNavigateClick, onNavigateClick,
            onPlayOption = onPlayClick, playlistActions = actions, libraryActions = actions, providerIconFetcher = providerIconFetcher)
        is Artist -> ArtistWithMenu(item, ViewMode.LIST, onNavigateClick, onPlayClick, actions, providerIconFetcher)
        is Track -> TrackWithMenu(item = item, viewMode = ViewMode.LIST, navigateToItem = onNavigateClick,
            onPlayOption = onPlayClick, playlistActions = actions, libraryActions = actions, providerIconFetcher = providerIconFetcher)
        is Playlist -> PlaylistWithMenu(item = item, viewMode = ViewMode.LIST, onNavigateClick = onNavigateClick,
            onPlayOption = onPlayClick, libraryActions = actions, providerIconFetcher = providerIconFetcher)
        is RadioStation -> RadioWithMenu(item = item, viewMode = ViewMode.LIST, onPlayOption = onPlayClick,
            playlistActions = actions, libraryActions = actions, providerIconFetcher = providerIconFetcher)
        else -> Text(item.displayName)
    }
}

@Composable
fun AlbumCollectionsScreen(contentPadding: PaddingValues, onBack: () -> Unit,
    onNavigateClick: (AppMediaItem) -> Unit, onPlayClick: PlayHandler<AppMediaItem>,
    actions: io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
) {
    val repository = koinInject<PersonalMusicRepository>()
    val factory = koinInject<MediaItemFactory>()
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(state.userId) { mutableStateOf(PersonalMusicRepository.LISTEN_LATER) }
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("pending") }
    var sortNewest by rememberSaveable { mutableStateOf(true) }
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { repository.refresh() }
    val collections = state.albums?.collections.orEmpty()
    val collection = collections.firstOrNull { it.id == selected } ?: collections.firstOrNull()
    val entries = collection?.entries.orEmpty().filter {
        (statusFilter == "all" || (it.status == CollectionStatus.PENDING) == (statusFilter == "pending")) &&
            (it.item.name + " " + it.item.artists.joinToString()).contains(query.trim(), true)
    }.let { if (sortNewest) it.sortedByDescending { entry -> entry.addedAt } else it.sortedBy { entry -> entry.item.name.lowercase() } }
    Column(Modifier.fillMaxSize()) {
        PersonalTopBar("Album collections", onBack, { scope.launch { repository.refresh() } })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            collections.forEach { shelf -> FilterChip(selected = shelf.id == collection?.id,
                onClick = { selected = shelf.id; query = ""; statusFilter = if (shelf.kind == CollectionKind.LISTEN_LATER) "pending" else "all" }, label = { Text("${shelf.name} (${shelf.entries.size})") }) }
            OutlinedButton(onClick = { name = ""; naming = "create" }, enabled = !state.busy) { Text("New") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Find an album or artist") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pending", "listened", "all").forEach { value -> FilterChip(selected = value == statusFilter,
                onClick = { statusFilter = value }, label = { Text(if (value == "pending") "To listen" else value.replaceFirstChar { it.uppercase() }) }) }
            FilterChip(selected = sortNewest, onClick = { sortNewest = !sortNewest }, label = { Text(if (sortNewest) "Recently added" else "Title") })
            if (collection?.kind == CollectionKind.CUSTOM) {
                TextButton(onClick = { name = collection.name; naming = "rename" }) { Text("Rename") }
                TextButton(onClick = { deleting = true }) { Text("Delete") }
            }
        }
        PersonalError(state)
        if (entries.isEmpty() && !state.loading) Text("Add whole albums using Listen Later or Collections on an album page or its menu.", Modifier.padding(16.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.testTag("AlbumCollectionsGrid"),
            contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.uri }) { entry ->
                Column(Modifier.padding(horizontal = 8.dp)) {
                    val album = factory.create(entry.item.asServer()) as? Album
                    if (album != null && entry.item.available) AlbumWithMenu(album,
                        onNavigateClick = onNavigateClick, navigateToItem = onNavigateClick, onPlayOption = onPlayClick,
                        playlistActions = actions, libraryActions = actions, providerIconFetcher = providerIconFetcher)
                    else Text("Unavailable album", Modifier.padding(16.dp))
                    Text(if (entry.status == CollectionStatus.PENDING) "To listen" else "Listened", style = MaterialTheme.typography.labelMedium)
                    TextButton(enabled = !state.busy, onClick = {
                        scope.launch { repository.collectionAction("status", collection?.id, entry.uri,
                            status = if (entry.status == CollectionStatus.PENDING) CollectionStatus.LISTENED else CollectionStatus.PENDING) }
                    }) { Text(if (entry.status == CollectionStatus.PENDING) "Mark listened" else "Listen again") }
                    TextButton(enabled = !state.busy, onClick = {
                        scope.launch { repository.collectionAction("remove", collection?.id, entry.uri) }
                    }) { Text("Remove") }
                }
            }
        }
    }
    LaunchedEffect(state.userId) { naming = null; deleting = false }
    naming?.let { action -> AlertDialog(onDismissRequest = { naming = null }, title = { Text(if (action == "create") "New album collection" else "Rename collection") },
        text = { Column { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }); state.error?.let { Text(it) } } },
        confirmButton = { TextButton(enabled = !state.busy && name.trim().length in 1..120, onClick = {
            val previous = collections.map { it.id }.toSet()
            scope.launch { if (repository.collectionAction(action, collectionId = collection?.id.takeIf { action == "rename" }, name = name.trim()).isSuccess) {
                if (action == "create") repository.state.value.albums?.collections?.singleOrNull { it.id !in previous }?.let { selected = it.id; statusFilter = "all"; query = "" }
                naming = null
            } }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } }) }
    if (deleting && collection?.kind == CollectionKind.CUSTOM) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete ${collection.name}?") },
        text = { Text("This removes the collection and its album references.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { scope.launch {
            if (repository.collectionAction("delete", collection.id).isSuccess) { selected = PersonalMusicRepository.LISTEN_LATER; deleting = false }
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}

@Composable
private fun PersonalError(state: PersonalMusicRepository.State) {
    if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
}

@Composable
private fun PersonalTopBar(title: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
    } }, actions = { TextButton(onClick = onRefresh) { Text("Refresh") } })
}
