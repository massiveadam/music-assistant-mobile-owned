package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.model.server.AudioFormat
import io.music_assistant.client.data.model.server.ServerMediaItem
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class PlaybackMetadataTest {
    private fun track(provider: String = "apple_music", complete: Boolean = true): Track {
        val source = ServerMediaItem(itemId = "123", provider = provider, name = "Track", mediaType = "track",
            uri = "$provider://track/123", duration = 120.0, favorite = true,
            providerMappings = if (complete) listOf(ProviderMapping(itemId = "123", providerDomain = "apple_music", providerInstance = "apple-one", available = true,
                audioFormat = AudioFormat(contentType = "aac", sampleRate = 44100, bitDepth = 16))) else null,
            artists = if (complete) listOf(ServerMediaItem(itemId = "artist", provider = "apple_music", name = "Artist", mediaType = "artist")) else null)
        return Track(itemId = source.itemId, provider = source.provider, name = source.name,
            providerMappings = source.providerMappings, metadata = null, favorite = false, uri = source.uri,
            images = emptyMap(), duration = 120.0, isPlayable = true, artists = emptyList(), album = null,
            discNumber = null, trackNumber = null, position = null, version = null, source = source)
    }
    private fun request(item: Track) = Request.Library.play(item = item, queueOrPlayerId = "phone",
        option = QueueOption.REPLACE, endlessMixMode = false, startItem = "123")

    @Test fun `streaming playback echoes source data rather than another catalog lookup`() {
        val result = request(track()); val media = result.args!!["media"]!!.jsonArray.single().jsonObject
        assertEquals("123", media["item_id"]?.jsonPrimitive?.content)
        assertEquals("track", media["media_type"]?.jsonPrimitive?.content)
        assertEquals("apple-one", media["provider_mappings"]?.jsonArray?.single()?.jsonObject?.get("provider_instance")?.jsonPrimitive?.content)
        assertEquals(true, media["favorite"]?.jsonPrimitive?.boolean) // original DTO, not the UI's private-save false
        assertEquals("phone", result.args!!["queue_id"]?.jsonPrimitive?.content)
        assertEquals("123", result.args!!["start_item"]?.jsonPrimitive?.content)
        assertNull(result.args!!["interactive"])
        assertNull(media["metadata"]) // Null DTO fields must be omitted for MA's dataclass decoder.
        val mapping = media["provider_mappings"]!!.jsonArray.single().jsonObject
        assertEquals(true, mapping["available"]!!.jsonPrimitive.boolean)
        assertEquals(44100, mapping["audio_format"]!!.jsonObject["sample_rate"]!!.jsonPrimitive.int)
        println("PLAYBACK_FIXTURE=" + media.toString())
    }
    @Test fun `library tracks retain authoritative server URI resolution`() {
        assertEquals("library://track/123", request(track("library")).args!!["media"]!!.jsonArray.single().jsonPrimitive.content)
    }
    @Test fun `slim synthetic tracks retain URI fallback`() {
        assertEquals("apple_music://track/123", request(track(complete = false)).args!!["media"]!!.jsonArray.single().jsonPrimitive.content)
    }
}
