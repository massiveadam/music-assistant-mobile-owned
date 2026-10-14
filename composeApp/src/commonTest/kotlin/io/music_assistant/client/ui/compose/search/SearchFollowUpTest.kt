package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.data.repository.SearchResultData
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.SEARCH_MAX_FOLLOW_UPS
import io.music_assistant.client.ui.compose.search.SearchViewModel.Companion.followUpPlan
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
}
