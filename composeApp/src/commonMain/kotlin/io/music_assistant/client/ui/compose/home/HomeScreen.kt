@file:OptIn(ExperimentalMaterial3Api::class, kotlin.time.ExperimentalTime::class)
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.DiscoverySource
import io.music_assistant.client.data.model.client.discoveryId
import io.music_assistant.client.data.model.client.selectDiscoveryMix
import io.music_assistant.client.data.model.client.Shortcut
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.CenteredText
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.CategoryRow
import io.music_assistant.client.ui.compose.common.items.ItemCategory
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.lazyListKey
import io.music_assistant.client.ui.compose.common.moveToEnabledBoundary
import io.music_assistant.client.ui.compose.common.toDisplayString
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.home_edit_rows
import musicassistantclient.composeapp.generated.resources.home_save_rows
import musicassistantclient.composeapp.generated.resources.home_shortcuts
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.refresh
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(
    homeScreenViewModel: HomeScreenViewModel,
    contentPadding: PaddingValues,
    onNavigateClick: (AppMediaItem) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    actionsViewModel: ActionsViewModel,
    state: HomeScreenState,
    onMyMusic: () -> Unit = {},
    onCollections: () -> Unit = {},
) {
    val homeScreenState by homeScreenViewModel.state.collectAsStateWithLifecycle()
    var editorialRefresh by remember { mutableStateOf(0) }
    val editorial = rememberEditorialHome(editorialRefresh)
    val editorialRows = remember(editorial.sources) {
        editorial.sources.map { source -> HomeRow(ItemCategory(id = source.rowId,
            title = source.name.toDisplayString(), items = emptyList(), lazyListKey = source.rowId), loading = false) }
    }
    val personalRepository = org.koin.compose.koinInject<io.music_assistant.client.data.repository.PersonalMusicRepository>()
    val mediaFactory = org.koin.compose.koinInject<io.music_assistant.client.data.factory.MediaItemFactory>()
    val personalState by personalRepository.state.collectAsStateWithLifecycle()
    val connection by homeScreenViewModel.connectionState.collectAsStateWithLifecycle()
    val person = (connection as? io.music_assistant.client.utils.HasConnectionData)?.user
    val homeTitle = (person?.displayName ?: person?.username)?.let { "$it's music" } ?: "Your music"
    val personalRows = remember(personalState.saves, personalState.albums, personalState.userId) {
        listOf(
            HomeRow(ItemCategory(id = "personal_music", title = "Saved by you".toDisplayString(),
                items = personalState.saves?.entries.orEmpty().sortedByDescending { it.savedAt }.take(12).mapNotNull { mediaFactory.create(it.item.asServer()) },
                lazyListKey = "personal_music"), loading = personalState.saves == null && personalState.loading),
            HomeRow(ItemCategory(id = "personal_listen_later", title = "Listen Later".toDisplayString(),
                items = personalState.albums?.collections?.firstOrNull { it.id == "listen-later" }?.entries.orEmpty()
                    .filter { it.status == io.music_assistant.client.data.model.server.CollectionStatus.PENDING }.sortedByDescending { it.addedAt }.take(12)
                    .mapNotNull { mediaFactory.create(it.item.asServer()) }, lazyListKey = "personal_listen_later"),
                loading = personalState.albums == null && personalState.loading),
        )
    }

    // Reconciled, enabled-first ordering. Authoritative for normal-mode display.
    val recommendationsState = homeScreenState.recommendations
    val shortcutsState = homeScreenState.shortcuts
    val homeRowsConfig = homeScreenState.homeRowsConfig
    val day = kotlin.time.Clock.System.now().toString().take(10)
    var mixRotation by remember(editorial.session, day) { mutableStateOf(0) }
    var previousMix by remember(editorial.session, day) { mutableStateOf(emptySet<String>()) }
    val mix = remember(personalState.saves, recommendationsState, homeRowsConfig, editorial.session, day, mixRotation, previousMix) {
        val hidden = homeRowsConfig.filterNot { it.enabled }.mapTo(mutableSetOf()) { it.id }
        val rows = (recommendationsState as? DataState.Data)?.data.orEmpty()
        val sources = listOf(DiscoverySource("personal_music", "Saved by you",
            personalState.saves?.entries.orEmpty().mapNotNull { mediaFactory.create(it.item.asServer()) },
            visible = "personal_music" !in hidden)) + rows.map { row ->
                DiscoverySource(row.folder.itemId, row.folder.name, row.resolvedItems.orEmpty(),
                    visible = row.folder.itemId !in hidden, fallback = row.folder.itemId == "recently_played")
            }
        val recent = rows.filter { it.folder.itemId == "recently_played" }.flatMap { it.resolvedItems.orEmpty() }.mapTo(mutableSetOf()) { it.discoveryId() }
        selectDiscoveryMix(sources, "${editorial.session?.userId}:$day:$mixRotation", recent, previousMix)
    }
    val mixRows = remember(mix) {
        if (mix.isEmpty()) emptyList() else listOf(HomeRow(ItemCategory(id = "discovery_mix",
            title = "Discovery mix".toDisplayString(), items = mix.map { it.item }, lazyListKey = "discovery_mix"), loading = false))
    }
    val working = remember(recommendationsState, shortcutsState, homeRowsConfig, personalRows, editorialRows, mixRows) {
        getCategories(recommendationsState, shortcutsState, homeRowsConfig, mixRows + personalRows + editorialRows)
    }

    // Edit-mode working copy — isolated from external (real-time) updates while editing;
    // snapshotted fresh on entering edit mode.
    var items by remember { mutableStateOf(working) }
    val enabledCount = items.count { it.second }
    val enabledByKey =
        remember(items) { items.associate { it.first.category.lazyListKey to it.second } }

    var editMode by remember { mutableStateOf(false) }
    val displayedData = remember(editMode, items, working) {
        if (editMode) items.map { it.first } else working.filter { it.second }.map { it.first }
    }

    val reorderableState = rememberReorderableLazyListState(state.lazyListState) { from, to ->
        // Constrain reorder to the contiguous enabled section.
        if (from.index >= enabledCount || to.index >= enabledCount) return@rememberReorderableLazyListState
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    BackHandler(enabled = editMode) { editMode = false }

    TopBarLayout(
        topBar = {
            LandingPageTopBar(
                editMode = editMode,
                title = homeTitle,
                onMyMusic = onMyMusic,
                onCollections = onCollections,
                onRefresh = { editorialRefresh++; homeScreenViewModel.loadData(); state.coroutineScope.launch { personalRepository.refresh() } },
                onToggleEditMode = {
                    if (editMode) {
                        homeScreenViewModel.saveHomeRows(
                            items.map {
                                SettingsRepository.HomeRowPref(
                                    it.first.category.id,
                                    it.second,
                                )
                            },
                        )
                        editMode = false
                    } else {
                        items = working
                        editMode = true
                    }
                },
            )
        },
        topAppBarState = state.topAppBarState,
    ) {
        if (recommendationsState is DataState.Loading) {
            CenteredProgress()
        } else if (recommendationsState !is DataState.Data) {
            CenteredText(
                text = stringResource(Res.string.library_error),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            val onPlayClickLambda: PlayHandler<AppMediaItem> = remember(homeScreenViewModel) {
                {
                    item, option, radio, _ ->
                    homeScreenViewModel.onPlayClick(item, option, radio)
                }
            }
            val rowContent: @Composable (HomeRow) -> Unit = { row ->
                val source = editorial.sources.firstOrNull { it.rowId == row.category.id }
                if (row.category.id == "discovery_mix") {
                    CategoryRow(title = "Discovery mix", mediaItems = row.category.items,
                        actions = { androidx.compose.material3.TextButton(onClick = {
                            previousMix = mix.mapTo(mutableSetOf()) { it.item.discoveryId() }; mixRotation++
                        }) { Text("New mix") } },
                        itemLabels = mix.associate { it.item.discoveryId() to it.tag },
                        onNavigateClick = onNavigateClick, onPlayClick = onPlayClickLambda,
                        playlistActions = actionsViewModel, libraryActions = actionsViewModel,
                        progressActions = actionsViewModel, providerIconFetcher = providerIconFetcher)
                } else if (source != null) {
                    EditorialShelf(source, editorial.session, enabled = !editMode,
                        refresh = editorialRefresh, onNavigate = onNavigateClick)
                } else if (editMode && !row.loading && row.category.items.isEmpty()) {
                    Text(row.category.title.string(), modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface)
                } else CategoryRow(
                    data = if (row.loading) DataState.Loading() else DataState.Data(row.category),
                    itemCategoryProvider = { it },
                    onNavigateClick = onNavigateClick,
                    onPlayClick = onPlayClickLambda,
                    playlistActions = actionsViewModel,
                    libraryActions = actionsViewModel,
                    progressActions = actionsViewModel,
                    providerIconFetcher = providerIconFetcher,
                )
            }

            ProvideClickActions(ClickContext.HOME) {
                LazyColumn(
                    modifier = Modifier.testTag(HomeScreenSemantics.LIST_TAG),
                    state = state.lazyListState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!editMode) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                androidx.compose.material3.TextButton(onClick = onMyMusic) { Text("My music") }
                                androidx.compose.material3.TextButton(onClick = onCollections) { Text("Album collections") }
                            }
                            personalState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                            editorial.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                        }
                    }
                    items(
                        items = displayedData,
                        key = { it.category.lazyListKey },
                    ) { homeRow ->
                        if (editMode) {
                            val enabled = enabledByKey[homeRow.category.lazyListKey] ?: true
                            ReorderableItem(reorderableState, key = homeRow.category.lazyListKey) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    rowContent(homeRow)
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                MaterialTheme.colorScheme.background.copy(
                                                    alpha = if (enabled) 0.60f else 0.80f,
                                                ),
                                            )
                                            .pointerInput(Unit) {
                                                // Swallow taps & long-presses on the row;
                                                // vertical drags pass through so the
                                                // LazyColumn can still scroll.
                                                detectTapGestures(onTap = {}, onLongPress = {})
                                            },
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Keep at least one row visible: block disabling the last enabled one.
                                        Switch(
                                            checked = enabled,
                                            enabled = !enabled || enabledCount > 1,
                                            onCheckedChange = { newEnabled ->
                                                items =
                                                    moveToEnabledBoundary(
                                                        items,
                                                        homeRow,
                                                        newEnabled,
                                                    )
                                            },
                                        )
                                        Icon(
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .then(
                                                    if (enabled) Modifier.draggableHandle() else Modifier,
                                                )
                                                .alpha(if (enabled) 1f else 0.3f)
                                                .size(24.dp),
                                            imageVector = TablerIcons.GripVertical,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                        } else {
                            rowContent(homeRow)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One reconciled home row: its display/reorder identity ([category], valid before
 * the row's items resolve) plus whether those items are still loading, which
 * [CategoryRow]'s [DataState] overload renders as a placeholder.
 */
internal data class HomeRow(
    val category: ItemCategory<Nothing>,
    val loading: Boolean,
) : IdProvider {
    override val id: String get() = category.id
}

// Internal so HomeScreenCategoriesTest can pin the row visibility rules: loading
// rows stay, resolved-empty rows are hidden.
internal fun getCategories(
    recommendationsState: DataState<List<RecommendationRowState>>,
    shortcutsState: DataState<List<Shortcut>>,
    homeRowsConfig: List<SettingsRepository.HomeRowPref>,
    personalRows: List<HomeRow> = emptyList(),
): List<Pair<HomeRow, Boolean>> {
    val baseList = if (recommendationsState is DataState.Data) {
        val recommendations = recommendationsState.data
            // A still-loading row stays visible as a placeholder; a resolved row
            // must contain something the home page can render.
            .filter { row ->
                row.items is DataState.Loading || row.resolvedItems?.any { item ->
                    item is Track ||
                            item is Artist ||
                            item is Album ||
                            item is Playlist ||
                            item is Audiobook ||
                            item is Podcast ||
                            item is PodcastEpisode ||
                            item is RadioStation ||
                            item is Genre
                } == true
            }
            .distinctBy { it.folder.lazyListKey() }
            .map { row ->
                HomeRow(
                    category = ItemCategory(
                        id = row.folder.itemId,
                        title = row.folder.displayName.toDisplayString(),
                        items = row.resolvedItems.orEmpty(),
                        lazyListKey = row.folder.lazyListKey(),
                        tag = HomeScreenSemantics.rowTag(row.folder.itemId),
                    ),
                    loading = row.items is DataState.Loading,
                )
            }

        if (shortcutsState is DataState.Data && shortcutsState.data.isNotEmpty()) {
            val shortcuts = shortcutsState.data
            val shortcutsRow = HomeRow(
                category = ItemCategory(
                    id = SHORTCUTS_CATEGORY_ID,
                    title = Res.string.home_shortcuts.toDisplayString(),
                    items = shortcuts.map { it.item },
                    lazyListKey = SHORTCUTS_CATEGORY_ID,
                    tag = HomeScreenSemantics.SHORTCUTS_ROW_TAG,
                ),
                loading = false,
            )

            recommendations + shortcutsRow
        } else {
            recommendations
        }
    } else {
        emptyList()
    }

    return reconcileHomeRows(personalRows + baseList, homeRowsConfig, onTop = SHORTCUTS_CATEGORY_ID)
}

private const val SHORTCUTS_CATEGORY_ID = "shortcuts"

@Composable
private fun LandingPageTopBar(
    editMode: Boolean,
    onRefresh: () -> Unit,
    onToggleEditMode: () -> Unit,
    title: String = "Your music",
    onMyMusic: () -> Unit = {},
    onCollections: () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onToggleEditMode) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Done else Icons.Default.Edit,
                    contentDescription = stringResource(
                        if (editMode) Res.string.home_save_rows else Res.string.home_edit_rows,
                    ),
                )
            }

            if (!editMode) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.refresh),
                    )
                }
            }
        },
    )
}

class HomeScreenState(
    val topAppBarState: TopAppBarState,
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
) : ScreenState {
    override fun reset() {
        coroutineScope.launch {
            topAppBarState.heightOffset = 0f
            lazyListState.animateScrollToItem(0)
        }
    }

    companion object {
        @Composable
        fun create(): HomeScreenState {
            val topAppBarState = rememberTopAppBarState()
            val lazyListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            return remember(topAppBarState, lazyListState, coroutineScope) {
                HomeScreenState(topAppBarState, lazyListState, coroutineScope)
            }
        }
    }
}

object HomeScreenSemantics {
    fun rowTag(categoryId: String): String = "Row:$categoryId"
    const val SHORTCUTS_ROW_TAG = "ShortcutsRow"
    const val LIST_TAG = "List"
}
