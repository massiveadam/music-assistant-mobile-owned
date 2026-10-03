package io.music_assistant.client.data

import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.*

class EditorialMusicTest {
    private val source = """{"id":"aoty","name":"AOTY weekly releases","provider":"aoty","url":"https://www.albumoftheyear.org/releases/","scale":100}"""
    private fun payload(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun feed(score: String = "{\"value\":85,\"max\":100}", url: String = "https://example.com/review", image: String = "null", id: String = "aoty") =
        payload("""{"version":1,"source":$source,"entries":[{"title":"Album","artist":"Artist","url":"$url","image":$image,"scores":{"critic":$score,"user":null}}],"stale":false,"note":"","window":{"start":"2026-09-27","end":"2026-10-03","timezone":"America/New_York","ranking":"critic"}}""")
    private fun album(id: String, provider: String = "tidal", title: String = "Album", artist: String = "Artist", uri: String? = "$provider://album/$id") = Album(
        itemId = id, provider = provider, name = title, providerMappings = null,
        metadata = null, favorite = null, uri = uri, images = emptyMap(), version = null, year = null,
        artists = listOf(Artist(itemId = artist, provider = provider, name = artist, providerMappings = null,
            metadata = null, favorite = null, uri = null, images = emptyMap())),
    )
    private val entry = EditorialEntry("Album", "Artist", "https://example.com/review", scores = EditorialScores())

    @Test fun `weekly feed preserves scores and ordering metadata`() {
        val result = parseEditorialFeed(feed(), "aoty")
        assertEquals(85.0, result.entries.single().scores.critic?.value)
        assertEquals("critic", result.window?.ranking)
        assertNull(result.entries.single().scores.user)
    }
    @Test fun `unrated albums stay unrated`() { assertNull(parseEditorialFeed(feed(score = "null"), "aoty").entries.single().scores.critic) }
    @Test fun `source mismatch rejected`() { assertFailsWith<IllegalArgumentException> { parseEditorialFeed(feed(), "rym") } }
    @Test fun `out of range score rejected`() { assertFailsWith<IllegalArgumentException> { parseEditorialFeed(feed(score = "{\"value\":101,\"max\":100}"), "aoty") } }
    @Test fun `negative score rejected`() { assertFailsWith<IllegalArgumentException> { parseEditorialFeed(feed(score = "{\"value\":-1,\"max\":100}"), "aoty") } }
    @Test fun `unsafe review URL rejected`() { assertFailsWith<IllegalArgumentException> { parseEditorialFeed(feed(url = "javascript:alert(1)"), "aoty") } }
    @Test fun `image credentials rejected`() { assertFailsWith<IllegalArgumentException> { parseEditorialFeed(feed(image = "\"https://user:secret@example.com/image\""), "aoty") } }
    @Test fun `catalog rejects duplicate row identities`() { assertFailsWith<IllegalArgumentException> { parseEditorialCatalog(payload("""{"version":1,"sources":[$source,$source]}""")) } }
    @Test fun `exact library match preferred`() {
        val local = album("1", "library")
        assertEquals(local, matchEditorialAlbum(entry, listOf(album("2"), local)))
    }
    @Test fun `similar title never opens wrong album`() { assertNull(matchEditorialAlbum(entry, listOf(album("1", title = "Album Live")))) }
    @Test fun `wrong artist never opens wrong album`() { assertNull(matchEditorialAlbum(entry, listOf(album("1", artist = "Another Artist")))) }
    @Test fun `ambiguous editions require chooser even without URIs`() {
        assertNull(matchEditorialAlbum(entry, listOf(album("1", uri = null), album("2", uri = null))))
    }
    @Test fun `same result repeated does not cause false ambiguity`() {
        val result = album("1")
        assertEquals(result, matchEditorialAlbum(entry, listOf(result, result)))
    }
}
