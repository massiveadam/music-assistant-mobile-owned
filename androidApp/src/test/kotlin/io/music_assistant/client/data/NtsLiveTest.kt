package io.music_assistant.client.data

import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import kotlin.test.*

class NtsLiveTest {
    private fun data(status: String = "identified", user: String = "adam", title: String = "Song", artists: String = "[\"Artist\"]") = myJson.decodeFromString<JsonObject>(
        """{"user_id":"$user","stationUri":"library://radio/1","stream":"nts1","station":"NTS 1","status":"$status","tracks":[{"title":"$title","artists":$artists,"startedAt":"2026-10-03T14:00:00Z"}]}""")
    @Test fun `identified title and artist form the live label`() {
        assertEquals("NTS live: Artist · Song", parseNtsFeed(data(), "adam", "library://radio/1").label)
    }
    @Test fun `unknown markers are kept as unknown`() {
        val feed = parseNtsFeed(data("unidentified", title = "", artists = "[]"), "adam", "library://radio/1")
        assertFalse(feed.tracks.single().identified)
        assertEquals("NTS has not identified this track", feed.label)
    }
    @Test fun `stale metadata never presents a current title`() {
        assertEquals("NTS track identification is out of date", parseNtsFeed(data("stale"), "adam", "library://radio/1").label)
    }
    @Test fun `responses from another account or station are rejected`() {
        assertFailsWith<IllegalArgumentException> { parseNtsFeed(data(user = "other"), "adam", "library://radio/1") }
        assertFailsWith<IllegalArgumentException> { parseNtsFeed(data(), "adam", "library://radio/2") }
    }
    @Test fun `false identified state and invalid status are rejected`() {
        assertFailsWith<IllegalArgumentException> { parseNtsFeed(data(title = "", artists = "[]"), "adam", "library://radio/1") }
        assertFailsWith<IllegalArgumentException> { parseNtsFeed(data("invented"), "adam", "library://radio/1") }
    }
    @Test fun `external links require HTTPS and no embedded credentials`() {
        assertTrue(ntsHttpsUrl("https://tidal.com/track/1"))
        for (url in listOf("javascript:alert(1)", "http://example.com", "https://user:secret@example.com", "https://")) assertFalse(ntsHttpsUrl(url))
    }
    @Test fun `resolution is tied to the selected feed entry`() {
        val response = myJson.decodeFromString<JsonObject>("""{"user_id":"adam","stationUri":"library://radio/1","track":{"title":"Song","artists":["Artist"],"startedAt":"stamp"},"search":"Artist Song","matches":[],"alternatives":[]}""")
        assertEquals("Artist Song", parseNtsResolution(response, "adam", "library://radio/1", "stamp").search)
        assertFailsWith<IllegalArgumentException> { parseNtsResolution(response, "adam", "library://radio/1", "another") }
    }
}
