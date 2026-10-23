package io.music_assistant.client.ui.compose.item.artist

import io.music_assistant.client.utils.HasConnectionData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.FetchArtistItemsUseCase
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.itemList
import io.music_assistant.client.data.model.client.items.set
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.map
import io.music_assistant.client.ui.compose.common.mapData
import io.music_assistant.client.ui.compose.item.ItemDetailsViewModel.Companion.ARTIST_SECTION_LIMIT
import io.music_assistant.client.ui.compose.item.ItemList
import io.music_assistant.client.ui.compose.item.toRequests
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArtistDetailsViewModel(
    private val artist: Artist,
    private val mediaItemRepository: MediaItemRepository,
    private val serviceClient: ServiceClient,
) : ViewModel() {
    private val _discography = MutableStateFlow<DataState<MediaItemRepository.AlbumGroups>>(DataState.Loading())
    val discography = _discography
    private val _recovering = MutableStateFlow(false)
    val recovering = _recovering

    fun loadDiscography(recover: Boolean = false) {
        viewModelScope.launch {
            if (recover) _recovering.value = true
            val owned = mediaItemRepository.fetchAlbumGroups(artist.itemId, artist.provider, inLibraryOnly = true)
            owned.onSuccess { _discography.value = DataState.Data(it) }
            val groups = mediaItemRepository.fetchAlbumGroups(artist.itemId, artist.provider, recover = recover)
            groups.fold(
                onSuccess = { _discography.value = DataState.Data(it) },
                onFailure = { if (_discography.value !is DataState.Data) _discography.value = DataState.Error() },
            )
            _recovering.value = false
        }
    }

    private val fetchArtistItemsUseCase = FetchArtistItemsUseCase(mediaItemRepository)

    private val libraryItems = itemList(mediaItemRepository)
    val library = libraryItems.asFlow()
        .mapData {
            Section(
                items = it
                    .filterIsInstance<Album>()
                    .take(ARTIST_SECTION_LIMIT),
                itemList = ItemList.ArtistLibrary(artist.itemId),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val allItems = itemList(mediaItemRepository)
    private val allProviderFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val all = allItems.asFlow()
        .combine(allProviderFilter) { items, providerFilter ->
            items.map {
                Section(
                    items = it
                        .filterIsInstance<Album>()
                        .take(ARTIST_SECTION_LIMIT),
                    itemList = providerFilter?.current,
                    providerFilter = providerFilter,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    private val topTrackItems = itemList(mediaItemRepository)
    private val topTracksFilter = MutableStateFlow<Section.ProviderFilter?>(null)
    val topTracks = topTrackItems.asFlow()
        .combine(topTracksFilter) { items, providerFilter ->
            items.map {
                Section(
                    items = it
                        .filterIsInstance<Track>()
                        .take(ARTIST_SECTION_LIMIT),
                    itemList = providerFilter?.current,
                    providerFilter = providerFilter,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataState.Loading())

    init {
        loadSections(artist)
        loadDiscography()
    }

    private fun loadSections(artist: Artist) {
        viewModelScope.launch {
            val providers = if (artist.isInLibrary) {
                serviceClient.sendRequest(Request.Provider.all())
                    .resultAs<List<ServerProviderInstance>>()
                    .orEmpty()
            } else {
                emptyList()
            }
            val allowed = (serviceClient.sessionState.value as? HasConnectionData)?.user?.providerFilter.orEmpty()
            val visibleProviders = providers.filter { it.type != "music" || allowed.isEmpty() || it.instanceId in allowed }
            val result = fetchArtistItemsUseCase.run(artist.topTracksOptions(visibleProviders))

            if (result != null) {
                val (items, itemList, options) = result
                topTracksFilter.value = Section.ProviderFilter(itemList, options)
                topTrackItems.set(items, itemList.toRequests())
            } else {
                topTrackItems.setEmpty()
            }
        }
    }

    fun loadAll(itemList: ItemList) {
        allProviderFilter.update {
            it?.copy(current = itemList)
        }

        viewModelScope.launch {
            allItems.set(itemList.toRequests())
        }
    }

    fun loadTopTracks(itemList: ItemList) {
        topTracksFilter.update { it?.copy(current = itemList) }

        viewModelScope.launch {
            topTrackItems.set(itemList.toRequests())
        }
    }

    data class Section<T : AppMediaItem>(
        val items: List<T>,
        val itemList: ItemList?,
        val providerFilter: ProviderFilter? = null,
    ) {
        data class ProviderFilter(
            val current: ItemList,
            val options: List<ItemList>,
        )
    }
}
