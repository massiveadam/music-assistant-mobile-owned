package io.music_assistant.client.ui.compose.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.isOwnedItem
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.data.repository.SearchResultData
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import org.jetbrains.compose.resources.StringResource
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Intent to escalate an empty in-library quick search to the global Search tab.
 *
 * @param mediaType the referring library's type, used to pre-select the matching filter chip;
 *   null (or a type with no chip, e.g. GENRE) searches all types.
 */
data class GlobalSearchRequest(val query: String, val mediaType: MediaType?)

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
class SearchViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    val searchJob = AtomicReference<Job?>(null)

    private val searchTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _state = MutableStateFlow(
        State(
            searchState = SearchState(
                query = "",
                mediaTypes = listOf(
                    MediaType.ARTIST,
                    MediaType.ALBUM,
                    MediaType.TRACK,
                    MediaType.PLAYLIST,
                    MediaType.AUDIOBOOK,
                    MediaType.PODCAST,
                    MediaType.RADIO,
                    // TODO server doesn't return genre in this endpoint yet,
                    //  need to fetch separately if we want to show it
                    // MediaType.GENRE,
                ).map { MediaTypeSelect(it, false) },
                libraryOnly = false,
            ),
            resultsState = DataState.NoData(),
        ),
    )
    val state = _state.asStateFlow()

    init {
        // Explicit-trigger search, debounced for sanity (rapid taps, filter toggles).
        viewModelScope.launch {
            searchTrigger
                .debounce(Timings.INPUT_DEBOUNCE)
                .collect {
                    val searchState = _state.value.searchState
                    if (searchState.query.isNotBlank()) {
                        performSearch(searchState)
                    } else {
                        _state.update { it.copy(resultsState = DataState.NoData()) }
                    }
                }
        }

        // Listen to real-time library changes; refresh any tracks already shown.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                (change.item as? Track)?.let { updateSearchResultsIfNeeded(it) }
            }
        }
    }

    /**
     * Escalation from an empty in-library quick search: pre-fill the query, pre-select the
     * referring library's filter chip, and run the search.
     */
    fun applyGlobalSearch(request: GlobalSearchRequest) {
        _state.update { state ->
            state.copy(
                searchState = state.searchState.copy(
                    query = request.query,
                    mediaTypes = state.searchState.mediaTypes.map { mediaTypeSelect ->
                        // Select only the referring type; an unmatched/null type (e.g. GENRE,
                        // which has no chip) leaves all unselected → search all types.
                        mediaTypeSelect.copy(isSelected = mediaTypeSelect.type == request.mediaType)
                    },
                ),
            )
        }
        searchTrigger.tryEmit(Unit)
    }

    fun onQueryChanged(query: String) {
        _state.update { it.copy(searchState = it.searchState.copy(query = query)) }
    }

    fun onSearch() {
        searchTrigger.tryEmit(Unit)
    }

    fun onFiltersChanged(mediaTypes: List<MediaType>, libraryOnly: Boolean) {
        _state.update { state ->
            state.copy(
                searchState = state.searchState.copy(
                    mediaTypes = state.searchState.mediaTypes.map { mediaTypeSelect ->
                        mediaTypeSelect.copy(isSelected = mediaTypes.contains(mediaTypeSelect.type))
                    },
                    libraryOnly = libraryOnly,
                ),
            )
        }

        searchTrigger.tryEmit(Unit)
    }

    fun onPlayClick(
        track: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        viewModelScope.launch {
            mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                track.mediaUri?.let { mediaUri ->
                    Logger.withTag("PlayDispatch")
                        .i { "SearchViewModel: uri=$mediaUri option=$option radio=$radio queue=$queueId" }
                    apiClient.sendRequest(
                        Request.Library.play(
                            item = track,
                            queueOrPlayerId = queueId,
                            option = option,
                            endlessMixMode = radio && track !is Genre,
                        ),
                    )
                }
            }
        }
    }

    private fun updateSearchResultsIfNeeded(changed: Track) {
        val resultsData = (_state.value.resultsState as? DataState.Data)?.data
        if (resultsData != null) {
            val updatedTracks = resultsData.tracks.map { track ->
                if (track.hasAnyMappingFrom(changed)) changed else track
            }
            _state.update {
                it.copy(resultsState = DataState.Data(resultsData.copy(tracks = updatedTracks)))
            }
        }
    }

    fun retrySources() { performSearch(_state.value.searchState) }

    private fun performSearch(searchState: SearchState) {
        searchJob.exchange(
            viewModelScope.launch {
                _state.update { it.copy(resultsState = DataState.Loading()) }

                // A source still searching server-side is re-checked quietly, so its results
                // slot in without "Retry sources"; only then is it reported as incomplete.
                var followUp = 0
                var pending = emptyList<String>()
                while (isActive) {
                    val result = mediaItemRepository.search(
                        Request.Library.search(
                            query = searchState.query,
                            mediaTypes = searchState.selectedMediaTypes,
                            limit = searchLimitFor(searchState.selectedMediaTypes.size),
                            libraryOnly = searchState.libraryOnly,
                        ),
                    )
                    if (!isActive) break
                    val data = result.getOrNull()
                    if (data == null) {
                        if (followUp == 0) {
                            _state.update { it.copy(resultsState = DataState.Error()) }
                        } else {
                            // Keep the ready results; the still-pending sources become incomplete.
                            _state.update { state ->
                                val ready = (state.resultsState as? DataState.Data)?.data
                                    ?: return@update state
                                state.copy(resultsState = DataState.Data(
                                    ready.copy(incompleteSources = (ready.incompleteSources + pending).distinct()),
                                ))
                            }
                        }
                        break
                    }
                    val plan = followUpPlan(data, followUp)
                    pending = data.pendingSources
                    val results = SearchResults(
                        artists = data.artists.sortedByDescending { it.isOwnedItem },
                        albums = data.albums.sortedByDescending { it.isOwnedItem },
                        tracks = data.tracks.sortedByDescending { it.isOwnedItem },
                        playlists = data.playlists.sortedByDescending { it.isOwnedItem },
                        audiobooks = data.audiobooks.sortedByDescending { it.isOwnedItem },
                        podcasts = data.podcasts.sortedByDescending { it.isOwnedItem },
                        radios = data.radios.sortedByDescending { it.isOwnedItem },
                        genres = data.genres,
                        incompleteSources = plan.visibleIncompleteSources,
                    )
                    _state.update { it.copy(resultsState = DataState.Data(results)) }
                    if (!plan.followUp) break
                    followUp++
                    delay(SEARCH_FOLLOW_UP_DELAY_MS)
                }
            },
        )?.cancel()
    }

    /** Whether to re-check, and which sources to report as incomplete meanwhile. */
    data class FollowUpPlan(val followUp: Boolean, val visibleIncompleteSources: List<String>)

    data class State(
        val searchState: SearchState,
        val resultsState: DataState<SearchResults>,
    )

    data class SearchState(
        val query: String,
        val mediaTypes: List<MediaTypeSelect>,
        val libraryOnly: Boolean,
    ) {
        val selectedMediaTypes = mediaTypes.filter { it.isSelected }.map { it.type }
    }

    data class MediaTypeSelect(
        val type: MediaType,
        val isSelected: Boolean,
    )

    data class SearchResults(
        val incompleteSources: List<String> = emptyList(),
        val artists: List<Artist>,
        val albums: List<Album>,
        val tracks: List<Track>,
        val playlists: List<Playlist>,
        val audiobooks: List<Audiobook>,
        val podcasts: List<Podcast>,
        val radios: List<RadioStation>,
        val genres: List<Genre>,
    ) {
        val nonEmptyLists = listOf(
            Item(Res.string.media_type_tracks, tracks),
            Item(Res.string.media_type_artists, artists),
            Item(Res.string.media_type_albums, albums),
            Item(Res.string.media_type_playlists, playlists),
            Item(Res.string.media_type_podcasts, podcasts),
            Item(Res.string.media_type_audiobooks, audiobooks),
            Item(Res.string.media_type_radio, radios),
            Item(Res.string.media_type_genres, genres),
        ).filter { it.items.isNotEmpty() }

        data class Item(
            val mediaTypeName: StringResource,
            val items: List<AppMediaItem>,
        )
    }

    internal companion object {
        const val SEARCH_FOLLOW_UP_DELAY_MS = 1500L
        const val SEARCH_MAX_FOLLOW_UPS = 4

        /** Pending sources stay hidden from "incomplete" while another follow-up is due. */
        fun followUpPlan(data: SearchResultData, followUpsDone: Int): FollowUpPlan {
            val again = data.pendingSources.isNotEmpty() && followUpsDone < SEARCH_MAX_FOLLOW_UPS
            return FollowUpPlan(
                followUp = again,
                visibleIncompleteSources =
                    if (again) data.incompleteSources - data.pendingSources.toSet() else data.incompleteSources,
            )
        }

        // music/search applies `limit` PER MEDIA TYPE, and streaming providers page it in
        // chunks of 10 behind a rate limiter (~1.0-1.5s per page). The server abandons a
        // provider that takes longer than its soft timeout (8s) and reports it as empty,
        // so ceil(limit / 10) pages must stay well inside that budget. See #929.

        /** Multi-type results render as carousels; a shallow list per type is enough. */
        const val SEARCH_LIMIT_OVERVIEW = 10

        /** A single selected type renders as a full list, so fetch deeper. */
        const val SEARCH_LIMIT_FOCUSED = 30

        /** Pick the limit from the number of selected filter chips (0 = all types). */
        fun searchLimitFor(selectedMediaTypeCount: Int) =
            if (selectedMediaTypeCount == 1) SEARCH_LIMIT_FOCUSED else SEARCH_LIMIT_OVERVIEW
    }
}
