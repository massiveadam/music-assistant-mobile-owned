package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.repository.SearchResultData
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.SEARCH_MAX_FOLLOW_UPS
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.followUpPlan
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.searchResultsFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchFollowUpTest {
    private val pendingApple = SearchResultData(
        incompleteSources = listOf("apple_music--a", "tidal--b"),
        pendingSources = listOf("apple_music--a"),
    )

    @Test
    fun `a pending source is re-checked and not reported as incomplete yet`() {
        val plan = followUpPlan(pendingApple, followUpsDone = 0)
        assertTrue(plan.followUp)
        assertEquals(listOf("tidal--b"), plan.visibleIncompleteSources)
    }

    @Test
    fun `after the last follow-up a pending source is reported as incomplete`() {
        val plan = followUpPlan(pendingApple, followUpsDone = SEARCH_MAX_FOLLOW_UPS)
        assertFalse(plan.followUp)
        assertEquals(listOf("apple_music--a", "tidal--b"), plan.visibleIncompleteSources)
    }

    @Test
    fun `a failed source alone is never re-checked automatically`() {
        val plan = followUpPlan(SearchResultData(incompleteSources = listOf("tidal--b")), followUpsDone = 0)
        assertFalse(plan.followUp)
        assertEquals(listOf("tidal--b"), plan.visibleIncompleteSources)
    }

    @Test
    fun `a complete search needs no follow-up`() {
        val plan = followUpPlan(SearchResultData(), followUpsDone = 0)
        assertFalse(plan.followUp)
        assertEquals(emptyList(), plan.visibleIncompleteSources)
    }

    @Test
    fun `search keeps the server order instead of moving owned items first`() {
        // Server order for "radiohead": Apple's popular track, a library-only track, another Apple track.
        val tracks = listOf(track("apple_music--a", "Creep"), track("library", "15 Step"), track("apple_music--a", "Let Down"))
        val results = searchResultsFrom(SearchResultData(tracks = tracks), emptyList())
        assertEquals(listOf("Creep", "15 Step", "Let Down"), results.tracks.map { it.name })
    }

    private fun track(provider: String, name: String) = Track(
        itemId = name, provider = provider, name = name, providerMappings = null, metadata = null,
        favorite = null, uri = null, images = emptyMap(), duration = null, isPlayable = true,
        artists = emptyList(), album = null, discNumber = null, trackNumber = null, position = null, version = null,
    )
}
