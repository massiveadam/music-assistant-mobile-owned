package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistEpisodePagingSupportTest {
    @Test
    fun pagesFromTheServer211Schema() {
        assertFalse(pagesPlaylistTracksAndEpisodes(null))
        // 2.10.6 streams the whole list and ignores `limit`/`offset`.
        assertFalse(pagesPlaylistTracksAndEpisodes(65))
        assertFalse(pagesPlaylistTracksAndEpisodes(SERVER_2_11_SCHEMA - 1))
        assertTrue(pagesPlaylistTracksAndEpisodes(SERVER_2_11_SCHEMA))
    }
}
