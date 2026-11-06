package io.music_assistant.client.data

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.*
import io.music_assistant.client.data.repository.*
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.item.artist.ArtistDetailsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistCatalogLoadingTest {
    private val models = mutableListOf<ArtistDetailsViewModel>()
    private fun clearModels() = models.forEach { it.viewModelScope.cancel() }
    private class Repository(val pendingReads: Int, val offline: Boolean = false) : MediaItemRepository {
        var catalogReads = 0
        override val itemChanges = MutableSharedFlow<MediaItemChange>()
        override suspend fun fetchMediaItems(request: Request) = Result.success(emptyList<AppMediaItem>())
        override suspend fun fetchAllMediaItems(request: Request) = emptyList<AppMediaItem>()
        override suspend fun search(request: Request): Result<SearchResultData> = error("unused")
        override suspend fun fetchMediaItem(request: Request) = Result.success<AppMediaItem?>(null)
        override suspend fun fetchTrackLyrics(track: Track) = Result.success<Lyrics?>(null)
        override fun publishLocalChange(change: MediaItemChange) = Unit
        override suspend fun supportsSourceRecovery() = false
        override suspend fun supportsInteractiveReads() = true
        override fun personalItems(type: MediaType) = emptyList<AppMediaItem>()
        override fun supportsRecommendationRowItems() = false
        override suspend fun fetchAlbumGroups(itemId: String, provider: String, inLibraryOnly: Boolean, recover: Boolean): Result<MediaItemRepository.AlbumGroups> {
            if (inLibraryOnly) return Result.success(MediaItemRepository.AlbumGroups(emptyList(), emptyList(), emptyList()))
            catalogReads += 1
            val pending = !offline && catalogReads <= pendingReads
            return Result.success(MediaItemRepository.AlbumGroups(emptyList(), emptyList(),
                if (offline || pending) listOf("apple") else emptyList(),
                pendingSources = if (pending) listOf("apple") else emptyList()))
        }
    }

    private fun TestScope.model(repository: Repository): ArtistDetailsViewModel {
        val client = FakeServiceClient()
        val artist = Artist(itemId = "remote-artist", provider = "apple_music", name = "External artist",
            providerMappings = null, metadata = null, favorite = null, uri = "apple_music://artist/remote-artist", images = emptyMap())
        return ArtistDetailsViewModel(artist, repository, client, ProviderRepository(client, backgroundScope))
            .also { models += it }
    }

    @Test fun `pending external catalog is followed until completed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(1)
            val model = model(repository)
            runCurrent(); assertTrue(model.catalogLoading.value)
            advanceTimeBy(2000); runCurrent()
            assertEquals(2, repository.catalogReads)
            assertFalse(model.catalogLoading.value)
            assertTrue(model.discography.value is DataState.Data)
        } finally { clearModels(); Dispatchers.resetMain() }
    }

    @Test fun `leaving artist stops pending followups`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(99)
            model(repository); runCurrent(); clearModels()
            advanceTimeBy(180000); runCurrent()
            assertEquals(1, repository.catalogReads)
        } finally { clearModels(); Dispatchers.resetMain() }
    }

    @Test fun `offline source is not repeatedly requested`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(99, offline = true)
            model(repository); runCurrent(); advanceTimeBy(180000); runCurrent()
            assertEquals(1, repository.catalogReads)
        } finally { clearModels(); Dispatchers.resetMain() }
    }

    @Test fun `followups are bounded for a persistently slow source`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = Repository(99)
            val model = model(repository)
            runCurrent(); advanceTimeBy(180000); runCurrent()
            assertEquals(9, repository.catalogReads)
            assertFalse(model.catalogLoading.value)
        } finally { clearModels(); Dispatchers.resetMain() }
    }
}
