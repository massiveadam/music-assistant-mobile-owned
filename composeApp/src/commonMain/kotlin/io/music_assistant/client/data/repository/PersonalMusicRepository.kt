package io.music_assistant.client.data.repository

import io.music_assistant.client.api.PersonalApi
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Server-confirmed private state. Nothing is persisted or migrated from shared favorites. */
class PersonalMusicRepository(private val api: PersonalApi,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val errorBus: io.music_assistant.client.api.ErrorMessageBus? = null,
) {
    data class State(
        val userId: String? = null,
        val saves: PersonalMusicSnapshot? = null,
        val albums: AlbumCollectionsSnapshot? = null,
        val loading: Boolean = false,
        val busy: Boolean = false,
        val error: String? = null,
    )
    private val mutex = Mutex()
    private var generation = 0L
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            api.serviceClient.sessionState.map { api.session() }
                .distinctUntilChanged { old, new -> old?.same(new) ?: (new == null) }
                .collectLatest { session ->
                    generation++
                    _state.value = State(userId = session?.userId)
                    if (session != null) refresh()
                }
        }
        scope.launch {
            api.serviceClient.foregroundEvents.collect { refresh() }
        }
        // History credits are listener-scoped by the backend on each refresh, including plays
        // made while this app was closed. Refresh while an authenticated app is foregrounded.
        scope.launch {
            while (isActive) { delay(60000); if (api.session() != null) refresh() }
        }
    }

    fun isSaved(uris: Set<String>): Boolean = _state.value.saves?.entries?.any {
        it.uri in uris || it.item.uri in uris || it.aliases.any { alias -> alias in uris }
    } == true
    fun isSaved(item: AppMediaItem): Boolean = isSaved(item.personalUris())
    fun isSaved(item: ServerMediaItem): Boolean = isSaved(buildSet {
        item.uri?.let(::add)
        add("${item.provider}://${item.mediaType}/${item.itemId}")
        item.providerMappings.orEmpty().forEach { add("${it.providerInstance}://${item.mediaType}/${it.itemId}") }
    })

    suspend fun refresh(): Result<Unit> {
        val session = api.session() ?: return Result.failure(IllegalStateException("Sign in to use your music."))
        val gen = generation
        return mutex.withLock {
            operation(session, gen) {
                _state.update { it.copy(loading = true, error = null) }
                val saves = myJson.decodeFromJsonElement<PersonalMusicSnapshot>(api.request(SAVES, session = session))
                check(saves.userId == session.userId) { "Account changed." }
                commit(session, gen) { it.copy(saves = saves) }
                val albums = myJson.decodeFromJsonElement<AlbumCollectionsSnapshot>(api.request(ALBUMS, session = session))
                check(albums.userId == session.userId) { "Account changed." }
                commit(session, gen) { it.copy(albums = albums) }
            }
        }
    }

    suspend fun setSaved(item: AppMediaItem, saved: Boolean): Result<Unit> {
        val uri = item.mediaUri ?: return Result.failure(IllegalArgumentException("No music URI."))
        val session = api.session() ?: return Result.failure(IllegalStateException("Sign in to save music."))
        val gen = generation
        return mutex.withLock {
            operation(session, gen) {
                val result = api.request(SAVES, buildJsonObject { put("uri", uri); put("saved", saved) }, session)
                check(result["user_id"]?.jsonPrimitive?.content == session.userId) { "Account changed." }
                val previous = _state.value.saves?.entries.orEmpty()
                val aliases = item.personalUris()
                val remaining = previous.filterNot { it.uri in aliases || it.aliases.any { alias -> alias in aliases } }
                val entry = result["entry"]?.takeIf { it !is JsonNull }?.let { myJson.decodeFromJsonElement<PersonalSave>(it) }
                val entries = if (saved) remaining + (entry ?: error("Missing saved item.")) else remaining
                commit(session, gen) { it.copy(saves = PersonalMusicSnapshot(session.userId, entries,
                    it.saves?.unavailable ?: 0)) }
            }
        }
    }

    suspend fun createAndAdd(name: String, album: AppMediaItem): Result<Unit> {
        val uri = album.mediaUri ?: return Result.failure(IllegalArgumentException("No album URI."))
        val session = api.session() ?: return Result.failure(IllegalStateException("Sign in to edit collections."))
        val gen = generation
        return mutex.withLock {
            operation(session, gen) {
                val previous = _state.value.albums?.collections.orEmpty().map { it.id }.toSet()
                val created = myJson.decodeFromJsonElement<AlbumCollectionsSnapshot>(api.request(ALBUMS,
                    buildJsonObject { put("action", "create"); put("name", name.trim()) }, session))
                check(created.userId == session.userId)
                commit(session, gen) { it.copy(albums = created) }
                val shelf = created.collections.singleOrNull { it.id !in previous }
                    ?: error("Could not identify the new collection. Refresh and try again.")
                // Reuse captured session across both operations, never the user's new session.
                val added = myJson.decodeFromJsonElement<AlbumCollectionsSnapshot>(api.request(ALBUMS,
                    buildJsonObject { put("action", "add"); put("collection_id", shelf.id); put("uri", uri) }, session))
                check(added.userId == session.userId)
                commit(session, gen) { it.copy(albums = added) }
            }
        }
    }

    suspend fun collectionAction(action: String, collectionId: String? = null, uri: String? = null,
        name: String? = null, status: CollectionStatus? = null): Result<Unit> {
        val body = buildJsonObject {
            put("action", action)
            collectionId?.let { put("collection_id", it) }
            uri?.let { put("uri", it) }
            name?.let { put("name", it) }
            status?.let { put("status", if (it == CollectionStatus.PENDING) "pending" else "listened") }
        }
        val session = api.session() ?: return Result.failure(IllegalStateException("Sign in to edit collections."))
        val gen = generation
        return mutex.withLock {
            operation(session, gen) {
                val result = myJson.decodeFromJsonElement<AlbumCollectionsSnapshot>(api.request(ALBUMS, body, session))
                check(result.userId == session.userId) { "Account changed." }
                commit(session, gen) { it.copy(albums = result) }
            }
        }
    }

    private suspend fun operation(session: PersonalApi.Session, gen: Long, block: suspend () -> Unit): Result<Unit> = try {
        check(gen == generation); api.requireCurrent(session)
        _state.update { it.copy(busy = true, error = null) }
        block()
        Result.success(Unit)
    } catch (cancel: CancellationException) { throw cancel
    } catch (error: Exception) {
        if (gen == generation && session.same(api.session())) {
            _state.update { it.copy(error = error.message) }
            errorBus?.emit(error.message ?: "Your music could not be updated. Try again.")
        }
        Result.failure(error)
    } finally {
        if (gen == generation && session.same(api.session())) _state.update { it.copy(busy = false, loading = false) }
    }

    private fun commit(session: PersonalApi.Session, gen: Long, transform: (State) -> State) {
        check(gen == generation); api.requireCurrent(session)
        _state.update { current ->
            check(gen == generation); api.requireCurrent(session)
            transform(current)
        }
    }

    companion object {
        const val SAVES = "/credits/v1/personal-music"
        const val ALBUMS = "/credits/v1/album-collections"
        const val LISTEN_LATER = "listen-later"
    }
}

fun AppMediaItem.personalUris(): Set<String> = buildSet {
    mediaUri?.let(::add)
    add("$provider://${mediaType.serverValue}/$itemId")
    providerMappings.orEmpty().forEach { add("${it.providerInstance}://${mediaType.serverValue}/${it.itemId}") }
}
