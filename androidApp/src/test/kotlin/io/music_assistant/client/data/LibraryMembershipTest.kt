package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.hasLibraryEntry
import io.music_assistant.client.data.model.server.ProviderMapping
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.*

class LibraryMembershipTest {
    private fun album(provider: String, mapping: ProviderMapping? = null) = Album(
        itemId = "album", provider = provider, name = "Album", providerMappings = mapping?.let { listOf(it) },
        metadata = null, favorite = false, uri = "$provider://album/album", images = emptyMap(),
        version = null, year = null, artists = emptyList(),
    )

    @Test fun `provider matches alone never imply library membership`() {
        for (domain in listOf("bandcamp", "filesystem_local", "apple_music")) {
            assertFalse(album(domain).hasLibraryEntry)
            assertFalse(album("tidal", ProviderMapping("album", domain, "$domain--1", false)).hasLibraryEntry)
            assertFalse(album("tidal", ProviderMapping("album", domain, "$domain--1")).hasLibraryEntry)
        }
    }
    @Test fun `library items and explicit saved mappings show library membership`() {
        assertTrue(album("library").hasLibraryEntry)
        assertTrue(album("tidal", ProviderMapping("album", "apple_music", "apple_music--1", true)).hasLibraryEntry)
    }
    @Test fun `mapping decoder retains server membership and tolerates older responses`() {
        val raw = """{"item_id":"album","provider_domain":"bandcamp","provider_instance":"bandcamp--1"}"""
        assertNull(Json.decodeFromString<ProviderMapping>(raw).inLibrary)
        assertFalse(Json.decodeFromString<ProviderMapping>(raw.dropLast(1) + ",\"in_library\":false}").inLibrary!!)
        assertTrue(Json.decodeFromString<ProviderMapping>(raw.dropLast(1) + ",\"in_library\":true}").inLibrary!!)
    }
}
