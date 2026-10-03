package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.ArtistRelease
import io.music_assistant.client.data.model.client.DISCOGRAPHY_CATEGORY_ORDER
import io.music_assistant.client.data.model.client.ReleaseSort
import io.music_assistant.client.data.model.client.filterArtistReleases
import io.music_assistant.client.data.model.client.groupArtistReleases
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.sortArtistReleases
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.personal.personalFavorite

private const val DEFAULT_SECTION_LIMIT = 12
private const val TYPE_OTHER = "other"
private const val OWNED_SECTION_ID = "owned"

private enum class DiscographyPicker { SOURCE, TYPE, SORT, YEAR }

/**
 * Unified artist discography: deduplicates editions across sources into releases via
 * [groupArtistReleases], shows owned releases first, then non-empty categorized
 * sections (Albums / EPs / Singles / Live / Compilations / Soundtracks / Other).
 *
 * Designed to be placed as a single `LazyColumn.item` on the artist details screen —
 * it renders plain [Column]s and two-column [Row]s of [AlbumWithMenu] grid items, so
 * it never introduces a nested vertically scrollable container. The control row
 * scrolls horizontally and value pickers open as sheets, keeping it usable on narrow
 * phones.
 *
 * The favorites filter uses the root-provided [personalFavorite] state.
 */
@Composable
fun ArtistDiscography(
    owned: List<Album>,
    all: List<Album>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
) {
    val releases = remember(owned, all) { groupArtistReleases(all = all, ownedAlbums = owned) }
    if (releases.isEmpty()) return

    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(ReleaseSort.NEWEST.name) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var fromYear by rememberSaveable { mutableStateOf("") }
    var toYear by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf<DiscographyPicker?>(null) }
    var versionsRelease by remember { mutableStateOf<ArtistRelease?>(null) }

    val sources = remember(releases) {
        releases.flatMap { it.editions }
            .flatMap { it.providerMappings.orEmpty() }
            .map { it.providerInstance }
            .distinct()
            .sorted()
    }
    val availableTypes = remember(releases) {
        DISCOGRAPHY_CATEGORY_ORDER.filter { category -> releases.any { it.category == category } }
    }

    // personalFavorite is @Composable, so it is sampled eagerly (only when the
    // favorites filter is on) and handed to the pure filter as a plain lookup.
    val favoriteByUri: Map<String, Boolean> = if (favoritesOnly) {
        val map = HashMap<String, Boolean>()
        for (release in releases) {
            for (album in release.editions) {
                album.uri?.let { map[it] = personalFavorite(album) }
            }
        }
        map
    } else {
        emptyMap()
    }

    val currentTypeFilter = typeFilter
    val visible = remember(releases, query, source, favoritesOnly, favoriteByUri, currentTypeFilter, sort, fromYear, toYear) {
        val filtered = filterArtistReleases(
            releases = releases,
            source = source,
            query = query,
            favorites = favoritesOnly,
            isFavorite = { album -> album.uri?.let(favoriteByUri::get) == true },
            fromYear = fromYear.toIntOrNull(), toYear = toYear.toIntOrNull(),
        )
        val typed = when (currentTypeFilter) {
            null -> filtered
            TYPE_OTHER -> filtered.filter { it.category == null }
            else -> runCatching { AlbumType.valueOf(currentTypeFilter) }.getOrNull()
                ?.let { type -> filtered.filter { it.category == type } }
                ?: filtered
        }
        sortArtistReleases(
            typed,
            runCatching { ReleaseSort.valueOf(sort) }.getOrDefault(ReleaseSort.NEWEST),
        )
    }
    val ownedReleases = visible.filter { it.owned }
    val catalog = visible.filter { !it.owned }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Discography",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search releases") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = source != null,
                onClick = { picker = DiscographyPicker.SOURCE },
                label = { Text(source ?: "Source") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            FilterChip(
                selected = currentTypeFilter != null,
                onClick = { picker = DiscographyPicker.TYPE },
                label = { Text(typeFilterLabel(currentTypeFilter)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            FilterChip(
                selected = false,
                onClick = { picker = DiscographyPicker.SORT },
                label = { Text("Sort: ${sortLabel(sort)}") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            FilterChip(
                selected = fromYear.isNotEmpty() || toYear.isNotEmpty(),
                onClick = { picker = DiscographyPicker.YEAR },
                label = { Text(if (fromYear.isEmpty() && toYear.isEmpty()) "Year" else "${fromYear.ifEmpty { "Any" }} to ${toYear.ifEmpty { "Any" }}") },
            )
            if (query.isNotEmpty() || source != null || currentTypeFilter != null || favoritesOnly || fromYear.isNotEmpty() || toYear.isNotEmpty()) {
                TextButton(onClick = { query = ""; source = null; typeFilter = null; favoritesOnly = false; fromYear = ""; toYear = "" }) { Text("Clear") }
            }
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Saved") },
            )
        }

        if (ownedReleases.isNotEmpty()) {
            DiscographySection(
                id = OWNED_SECTION_ID,
                title = "Owned albums",
                releases = ownedReleases,
                defaultExpanded = true,
                onNavigateClick = onNavigateClick,
                onShowVersions = { versionsRelease = it },
                onPlayClick = onPlayClick,
                playlistActions = playlistActions,
                libraryActions = libraryActions,
                providerIconFetcher = providerIconFetcher,
            )
        }
        DISCOGRAPHY_CATEGORY_ORDER.forEach { category ->
            val sectionReleases = catalog.filter { it.category == category }
            if (sectionReleases.isNotEmpty()) {
                DiscographySection(
                    id = "category_${category?.name ?: TYPE_OTHER}",
                    title = categoryLabel(category),
                    releases = sectionReleases,
                    defaultExpanded = true,
                    onNavigateClick = onNavigateClick,
                    onShowVersions = { versionsRelease = it },
                    onPlayClick = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        }
        if (visible.isEmpty()) {
            Text(
                text = "No releases match the current filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    when (picker) {
        DiscographyPicker.SOURCE -> DiscographyPickerSheet(
            title = "Source",
            options = listOf<Pair<String?, String>>(null to "All sources") +
                sources.map { it to it },
            selected = source,
            onSelect = { source = it },
            onDismiss = { picker = null },
        )
        DiscographyPicker.TYPE -> DiscographyPickerSheet(
            title = "Release type",
            options = listOf<Pair<String?, String>>(null to "All types") +
                availableTypes.map { (it?.name ?: TYPE_OTHER) to categoryLabel(it) },
            selected = currentTypeFilter,
            onSelect = { typeFilter = it },
            onDismiss = { picker = null },
        )
        DiscographyPicker.SORT -> DiscographyPickerSheet(
            title = "Sort",
            options = ReleaseSort.entries.map { it.name to sortLabel(it.name) },
            selected = sort,
            onSelect = { sort = it },
            onDismiss = { picker = null },
        )
        DiscographyPicker.YEAR -> AlertDialog(
            onDismissRequest = { picker = null }, title = { Text("Release years") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromYear, { fromYear = it.filter(Char::isDigit).take(4) }, label = { Text("From year") }, singleLine = true)
                OutlinedTextField(toYear, { toYear = it.filter(Char::isDigit).take(4) }, label = { Text("To year") }, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = { picker = null }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { fromYear = ""; toYear = ""; picker = null }) { Text("Clear") } },
        )
        null -> Unit
    }

    versionsRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { versionsRelease = null },
            confirmButton = {
                TextButton(onClick = { versionsRelease = null }) { Text("Close") }
            },
            title = { Text("Versions") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = release.primary.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // Exact editions, rendered with the native album row (name + version).
                    release.editions.forEach { edition ->
                        AlbumWithMenu(
                            item = edition,
                            viewMode = ViewMode.LIST,
                            onNavigateClick = { album ->
                                versionsRelease = null
                                onNavigateClick(album)
                            },
                            navigateToItem = { item ->
                                versionsRelease = null
                                onNavigateClick(item)
                            },
                            onPlayOption = { album, option, radio, fromHere ->
                                versionsRelease = null
                                onPlayClick(album, option, radio, fromHere)
                            },
                            playlistActions = playlistActions,
                            libraryActions = libraryActions,
                            providerIconFetcher = providerIconFetcher,
                        )
                    }
                }
            },
        )
    }
}

/** Collapsible section with a two-column grid, capped at [DEFAULT_SECTION_LIMIT] until expanded. */
@Composable
private fun DiscographySection(
    id: String,
    title: String,
    releases: List<ArtistRelease>,
    defaultExpanded: Boolean,
    onNavigateClick: (AppMediaItem) -> Unit,
    onShowVersions: (ArtistRelease) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
) {
    var expanded by rememberSaveable("${id}_expanded") { mutableStateOf(defaultExpanded) }
    var visibleCount by rememberSaveable("${id}_visible_count") { mutableStateOf(DEFAULT_SECTION_LIMIT) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${releases.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            val shown = releases.take(visibleCount)
            shown.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { release ->
                        Box(modifier = Modifier.weight(1f)) {
                            ReleaseGridItem(
                                release = release,
                                onNavigateClick = onNavigateClick,
                                onShowVersions = onShowVersions,
                                onPlayClick = onPlayClick,
                                playlistActions = playlistActions,
                                libraryActions = libraryActions,
                                providerIconFetcher = providerIconFetcher,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (releases.size > visibleCount) {
                TextButton(
                    onClick = { visibleCount += DEFAULT_SECTION_LIMIT },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Show more (${releases.size - visibleCount})")
                }
            }
        }
    }
}

@Composable
private fun ReleaseGridItem(
    release: ArtistRelease,
    onNavigateClick: (AppMediaItem) -> Unit,
    onShowVersions: (ArtistRelease) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AlbumWithMenu(
            item = release.primary,
            viewMode = ViewMode.GRID,
            onNavigateClick = { album ->
                if (release.editions.size > 1) onShowVersions(release) else onNavigateClick(album)
            },
            navigateToItem = onNavigateClick,
            onPlayOption = { album, option, radio, fromHere ->
                onPlayClick(album, option, radio, fromHere)
            },
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            providerIconFetcher = providerIconFetcher,
        )
        if (release.editions.size > 1) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Text(
                    text = "${release.editions.size} versions",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DiscographyPickerSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(value)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == selected, onClick = null)
                    Text(text = label, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private fun categoryLabel(type: AlbumType?): String = when (type) {
    AlbumType.ALBUM -> "Albums"
    AlbumType.EP -> "EPs"
    AlbumType.SINGLE -> "Singles"
    AlbumType.LIVE -> "Live"
    AlbumType.COMPILATION -> "Compilations"
    AlbumType.SOUNDTRACK -> "Soundtracks"
    null -> "Other"
}

private fun typeFilterLabel(typeFilter: String?): String = when (typeFilter) {
    null -> "Type"
    TYPE_OTHER -> "Other"
    else -> runCatching { AlbumType.valueOf(typeFilter) }.getOrNull()?.let(::categoryLabel) ?: "Type"
}

private fun sortLabel(sort: String): String = when (sort) {
    ReleaseSort.OLDEST.name -> "Oldest"
    ReleaseSort.TITLE.name -> "Title (A–Z)"
    else -> "Newest"
}
