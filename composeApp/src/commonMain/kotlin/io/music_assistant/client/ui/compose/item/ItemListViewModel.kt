package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.clientSorted
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ItemListViewModel(
    private val itemList: ItemList,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val items = MutableStateFlow<List<AppMediaItem>?>(null)
    private val loadFailed = MutableStateFlow(false)
    private var sortOption = MutableStateFlow(SortConfig.defaultFor(itemList.mediaType))
    val state = combine(items, sortOption, loadFailed) { items, sortOption, failed ->
        if (failed) {
            State(items = DataState.Error(), sortOption = sortOption)
        } else if (items != null) {
            State(items = DataState.Data(items.clientSorted(sortOption)), sortOption = sortOption)
        } else {
            State(items = DataState.Loading(), sortOption = sortOption)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State(DataState.Loading(), sortOption.value))

    init {
        viewModelScope.launch {
            if (itemList is ItemList.ArtistAlbumGroup) {
                mediaItemRepository.fetchAlbumGroups(itemList.artistId, itemList.providerInstance)
                    .onSuccess { groups ->
                        val base = if (itemList.owned) groups.owned else groups.all
                        val filtered = when (itemList.groupType) {
                            "album" -> base.filter { it.albumType != io.music_assistant.client.data.model.client.AlbumType.EP && it.albumType != io.music_assistant.client.data.model.client.AlbumType.SINGLE }
                            "ep" -> base.filter { it.albumType == io.music_assistant.client.data.model.client.AlbumType.EP }
                            "single" -> base.filter { it.albumType == io.music_assistant.client.data.model.client.AlbumType.SINGLE }
                            else -> base
                        }
                        items.value = filtered
                    }
                    .onFailure { loadFailed.value = true }
                return@launch
            }
            val request = when (itemList) {
                is ItemList.ArtistAlbumGroup -> error("Handled above")

                is ItemList.ArtistAlbums -> Request.Artist.getAlbums(
                    itemList.artistId,
                    itemList.providerInstance,
                )

                is ItemList.ArtistTopTracks -> Request.Artist.getTopTracks(
                    itemList.artistId,
                    itemList.providerInstance,
                )

                is ItemList.ArtistLibrary -> Request.Artist.getAlbums(
                    itemList.artistId,
                    ServerMediaItem.LIBRARY_PROVIDER,
                )
            }

            mediaItemRepository.fetchMediaItems(request) {
                items.value = it
            }
        }
    }

    fun sort(sortOption: SortOption) {
        this.sortOption.value = sortOption
    }

    data class State(val items: DataState<List<AppMediaItem>>, val sortOption: SortOption)
}

@Serializable
sealed interface ItemList {
    @Serializable
    data class ArtistAlbumGroup(
        val providerInstance: String,
        val artistId: String,
        val owned: Boolean,
        val groupType: String? = null,
    ) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
    }

    val mediaType: MediaType

    @Serializable
    data class ArtistAlbums(val providerInstance: String, val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
    }

    @Serializable
    data class ArtistTopTracks(val providerInstance: String, val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.TRACK
    }

    @Serializable
    data class ArtistLibrary(val artistId: String) : ItemList {
        override val mediaType: MediaType = MediaType.ALBUM
    }
}
