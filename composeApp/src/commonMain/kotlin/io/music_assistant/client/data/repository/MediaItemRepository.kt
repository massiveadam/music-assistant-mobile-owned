package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.server.ArtistAlbumGroups
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val boundedReadCommands = setOf("music/artists/get", "music/artists/get_artist",
    "music/albums/get", "music/albums/get_album", "music/albums/album_tracks", "music/artists/album_groups")
private val sourceReadCommands = setOf("music/artists/top_tracks", "music/artists/similar_artists")

/** Wire-only browsing augmentation; never changes playback or library writes. */
fun performanceBrowsingRequest(request: Request, supported: Boolean): Request {
    val bounded = request.command in boundedReadCommands
    val perSource = request.command in sourceReadCommands
    if (!supported || (!bounded && !perSource)) return request
    return request.copy(args = buildJsonObject {
        request.args?.forEach { (key, value) -> put(key, value) }
        if (bounded) put("interactive", JsonPrimitive(true))
        val provider = request.args?.get("provider_instance_id_or_domain")?.jsonPrimitive?.contentOrNull
        if (perSource && provider != null && provider != "library") put("provider_filter", JsonPrimitive(provider))
    })
}

/**
 * Search augmentation. With source status, a slow source is reported "pending" and the
 * search screen re-checks it, so the short browsing deadline cannot produce a false
 * zero-result; without it, keep the normal server search budget.
 */
fun searchRequest(request: Request, sourceStatus: Boolean, fastReads: Boolean): Request {
    if (!sourceStatus) return request
    return request.copy(args = buildJsonObject {
        request.args?.forEach { (key, value) -> put(key, value) }
        put("include_source_status", JsonPrimitive(true))
        if (fastReads) put("interactive", JsonPrimitive(true))
    })
}

interface MediaItemRepository {
    suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>>
    val itemChanges: SharedFlow<MediaItemChange>
    suspend fun search(request: Request): Result<SearchResultData>
    suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?>
    fun publishLocalChange(change: MediaItemChange)

    suspend fun supportsSourceRecovery(): Boolean
    suspend fun supportsInteractiveReads(): Boolean
    suspend fun fetchAlbumGroups(itemId: String, provider: String, inLibraryOnly: Boolean = false, recover: Boolean = false): Result<AlbumGroups>
    val personalState get() = personalMusic?.state
    fun personalItems(type: io.music_assistant.client.data.model.client.MediaType): List<AppMediaItem>
    fun supportsRecommendationRowItems(): Boolean

    data class AlbumGroups(
        val owned: List<Album>,
        val all: List<Album>,
        val unavailableSources: List<String>,
        val unlinkedSources: List<String> = emptyList(),
    )

    val personalMusic: PersonalMusicRepository?
        get() = null
}

/**
 * Single seam between RPC + DTO land and the UI's typed `AppMediaItem` world.
 *
 * Wraps [ServiceClient.sendRequest] + [MediaItemFactory] so ViewModels stop
 * seeing [ServerMediaItem], [SearchResult], or `MediaItem*Event` directly,
 * and centralizes the "decode payload + map to client model" boilerplate
 * that was duplicated across every list/get/search call site.
 */
class ServiceClientMediaItemRepository(
    private val apiClient: ServiceClient,
    private val factory: MediaItemFactory,
    private val personalMusic: PersonalMusicRepository? = null,
) : MediaItemRepository {
    // Singleton-scoped app-lifetime job; only used to keep [itemChanges]
    // hot. Survives subscriber turnover so quick navigation between screens
    // doesn't churn the upstream `apiClient.events` subscription.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val capabilitiesLock = Mutex()
    private var capabilitiesServer: Any? = null
    private var fastReads = false
    private var performanceCaps: Map<String, Boolean> = emptyMap()

    override suspend fun supportsSourceRecovery(): Boolean {
        supportsInteractiveReads()
        return performanceCaps["artist_source_recovery"] == true
    }

    override suspend fun supportsInteractiveReads(): Boolean = capabilitiesLock.withLock {
        val server = (apiClient.sessionState.value as? HasConnectionData)?.serverInfo ?: return@withLock false
        if (capabilitiesServer !== server) {
            val answer = withTimeoutOrNull(1200) {
                apiClient.sendRequest(Request("music/performance_capabilities"))
            }
            performanceCaps = answer?.getOrNull()?.resultAs<Map<String, Boolean>>().orEmpty()
            fastReads = performanceCaps["interactive_reads"] == true
            capabilitiesServer = server
        }
        fastReads
    }

    private suspend fun browsingRequest(request: Request): Request {
        if (request.command == "music/search") {
            val fastReads = supportsInteractiveReads()
            return searchRequest(request, sourceStatus = performanceCaps["source_status"] == true, fastReads = fastReads)
        }
        if (request.command !in boundedReadCommands && request.command !in sourceReadCommands) return request
        return performanceBrowsingRequest(request, supportsInteractiveReads())
    }

    override val personalState get() = personalMusic?.state
    override fun personalItems(type: io.music_assistant.client.data.model.client.MediaType): List<AppMediaItem> =
        personalMusic?.state?.value?.saves?.entries.orEmpty()
            .filter { it.item.mediaType == type.serverValue }
            .mapNotNull { factory.create(it.item.asServer()) }

    override suspend fun fetchAlbumGroups(itemId: String, provider: String, inLibraryOnly: Boolean = false,
                                 recover: Boolean = false): Result<AlbumGroups> =
        apiClient.sendRequest(if (recover && supportsSourceRecovery()) Request("music/artists/recover_sources", buildJsonObject {
            put("item_id", JsonPrimitive(itemId)); put("provider_instance_id_or_domain", JsonPrimitive(provider))
            put("refresh_catalog", JsonPrimitive(true))
        }) else browsingRequest(Request.Artist.getAlbumGroups(itemId, provider, inLibraryOnly))).mapCatching { answer ->
            val groups = answer.resultAs<ArtistAlbumGroups>() ?: error("Missing album groups")
            AlbumGroups(
                owned = factory.createList(groups.owned).filterIsInstance<Album>(),
                all = factory.createList(groups.all).filterIsInstance<Album>(),
                unavailableSources = groups.unavailableSources,
                unlinkedSources = groups.unlinkedSources,
            )
        }

    override suspend fun fetchMediaItems(request: Request): Result<List<AppMediaItem>> =
        apiClient.sendRequest(browsingRequest(request)).mapCatching { answer ->
            answer.resultAs<List<ServerMediaItem>>()
                ?.let(factory::createList)
                ?: error("Missing or undecodable media list payload")
        }

    suspend fun fetchMediaItems(request: Request, observer: (List<AppMediaItem>) -> Unit) {
        val result = fetchMediaItems(request)
        val items = result.getOrNull()

        if (items != null) {
            observer(items)
            itemChanges
                .scan(items) { items, change -> items.replacing(change.item) }
                .collect { observer(it) }
        }
    }

    /**
     * The home-page recommendation rows as the server returned them, with or
     * without embedded items (see [supportsRecommendationRowItems]).
     */
    suspend fun fetchRecommendationRows(): Result<List<RecommendationFolder>> =
        withContext(Dispatchers.IO) {
            fetchMediaItems(Request.Library.recommendations())
                .map { items -> items.filterIsInstance<RecommendationFolder>().filterNot {
                    (it.provider in listOf("library", "recommendations") && it.itemId == "listen_later_unplayed") || (it.provider.startsWith("listen_later") && it.itemId == "album_collection")
                }.map { if (it.provider in listOf("library", "recommendations") && it.itemId == "listen_later_starred") it.copy(name = "Saved albums") else it } }
        }

    /**
     * Whether the connected server strips the items from `music/recommendations`
     * rows and serves each row's contents via `music/recommendations/items`
     * instead. This can be simplified once v2.10 is our minimum supported server
     * version.
     */
    override fun supportsRecommendationRowItems(): Boolean =
        (apiClient.sessionState.value as? HasConnectionData)?.serverInfo?.schemaVersion
            ?.let { it >= RECOMMENDATION_ITEMS_SCHEMA } == true

    /**
     * One-shot, fully-resolved recommendation rows for consumers without a
     * progressive UI (e.g. CarPlay lists).
     */
    suspend fun fetchRecommendationFolders(): Result<List<RecommendationFolder>> {
        val folders = fetchRecommendationRows().getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        if (!supportsRecommendationRowItems()) return Result.success(folders)

        return Result.success(
            coroutineScope {
                folders.map { folder ->
                    async {
                        folder.copy(items = fetchRecommendationRowItems(folder).orEmpty())
                    }
                }.awaitAll()
            },
        )
    }

    /** Items of one recommendation row, or null when the fetch failed (logged). */
    suspend fun fetchRecommendationRowItems(
        folder: RecommendationFolder,
    ): List<AppMediaItem>? = withContext(Dispatchers.IO) {
        fetchMediaItems(
            Request.Library.recommendationItems(folder.provider, folder.itemId),
        ).getOrElse { error ->
            if (error is CancellationException) throw error
            Logger.w(
                "Failed fetching recommendation items for " +
                        "${folder.provider}/${folder.itemId}: $error",
            )
            null
        }
    }

    /**
     * Issue [request] and decode its payload as a single client media item.
     * Returns `Result.success(null)` for an absent payload so a 404-style
     * "not found" stays distinguishable from a transport failure.
     */
    override suspend fun fetchMediaItem(request: Request): Result<AppMediaItem?> =
        apiClient.sendRequest(browsingRequest(request)).map { answer ->
            answer.resultAs<ServerMediaItem>()?.let(factory::create)
        }

    /** Issue [request] and decode its payload as a typed [SearchResultData]. */
    override suspend fun search(request: Request): Result<SearchResultData> =
        apiClient.sendRequest(browsingRequest(request)).mapCatching { answer ->
            answer.resultAs<SearchResult>()
                ?.let(factory::createSearchResult)
                ?: error("Missing or undecodable search payload")
        }

    // Client-originated changes for mutations the server doesn't echo back as an
    // event (e.g. mark played/unplayed only writes the playlog server-side). Merged
    // into [itemChanges] so the same subscribers reconcile them like server events.
    private val localChanges = MutableSharedFlow<MediaItemChange>(extraBufferCapacity = 16)

    /** Publish an optimistic, client-originated [change] to [itemChanges] subscribers. */
    override fun publishLocalChange(change: MediaItemChange) {
        localChanges.tryEmit(change)
    }

    /**
     * Hot stream of library lifecycle changes mapped from the corresponding
     * server events (plus client-originated [localChanges]). ViewModels collect
     * this instead of filtering `apiClient.events` and re-running
     * `mediaItemFactory.create(...)` themselves. Replay is zero — late
     * subscribers only see future changes.
     */
    override val itemChanges: SharedFlow<MediaItemChange> = merge(
        localChanges,
        apiClient.events.mapNotNull { event ->
            when (event) {
                is MediaItemAddedEvent ->
                    factory.create(event.data)?.let(MediaItemChange::Added)

                is MediaItemUpdatedEvent ->
                    factory.create(event.data)?.let(MediaItemChange::Updated)

                is MediaItemDeletedEvent ->
                    factory.create(event.data.withLibraryStripped())
                        ?.let(MediaItemChange::Deleted)

                else -> null
            }
        },
    ).shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)

    // When the server announces the deletion of a library record, the
    // *underlying* provider item still exists. Re-key the DTO to its first
    // non-library provider mapping so subscribers can fall through to the
    // source identity instead of dangling on a library id that's about to
    // 404. No-op when there's no fallback mapping.
    private fun ServerMediaItem.withLibraryStripped(): ServerMediaItem {
        val fallback = providerMappings?.firstOrNull() ?: return this
        return copy(
            itemId = fallback.itemId,
            provider = fallback.providerInstance,
            favorite = null,
            uri = "${fallback.providerInstance}://$mediaType/${fallback.itemId}",
        )
    }
}

/**
 * The home-page recommendation rows as the server returned them
 */
suspend fun MediaItemRepository.fetchRecommendationRows(): Result<List<RecommendationFolder>> =
    withContext(Dispatchers.IO) {
        fetchMediaItems(Request.Library.recommendations())
            .map { items -> items.filterIsInstance<RecommendationFolder>() }
    }

/** Items of one recommendation row, or null when the fetch failed (logged). */
suspend fun MediaItemRepository.fetchRecommendationRowItems(
    folder: RecommendationFolder,
): List<AppMediaItem>? = withContext(Dispatchers.IO) {
    fetchMediaItems(
        Request.Library.recommendationItems(folder.provider, folder.itemId),
    ).getOrElse { error ->
        if (error is CancellationException) throw error
        Logger.w(
            "Failed fetching recommendation items for " +
                    "${folder.provider}/${folder.itemId}: $error",
        )
        null
    }
}

/**
 * One-shot, fully-resolved recommendation rows for consumers without a
 * progressive UI (e.g. CarPlay lists).
 */
suspend fun MediaItemRepository.fetchRecommendationFolders(): Result<List<RecommendationFolder>> {
    val folders = fetchRecommendationRows().getOrElse { error ->
        if (error is CancellationException) throw error
        return Result.failure(error)
    }

    return Result.success(
        coroutineScope {
            folders.map { folder ->
                async {
                    folder.copy(items = fetchRecommendationRowItems(folder).orEmpty())
                }
            }.awaitAll()
        },
    )
}

/**
 * Fetch items for multiple requests and combine them into one list. Returns a failure Result
 * if any of the requests fail (and fails fast).
 */
suspend fun MediaItemRepository.fetchMediaItems(requests: List<Request>): Result<List<AppMediaItem>> {
    val items = mutableListOf<AppMediaItem>()
    for (request in requests) {
        val result = this.fetchMediaItems(request)
        if (result.isSuccess) {
            items += result.getOrEmptyList()
        } else {
            return result
        }
    }

    return Result.success(items)
}

private fun <T : AppMediaItem> List<T>.replacing(changed: T): List<T> =
    map { if (it.itemId == changed.itemId) changed else it }

/** Server schema version that split `music/recommendations` into rows + per-row items. */
private const val RECOMMENDATION_ITEMS_SCHEMA = 39
