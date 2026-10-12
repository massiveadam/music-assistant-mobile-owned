package io.music_assistant.client.data

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.RpcEngine
import io.music_assistant.client.data.repository.performanceBrowsingRequest
import io.music_assistant.client.data.model.server.parseEditorialCatalog
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class PerformanceBrowsingTest {
    @Test fun `search never gains the short browsing deadline`() {
        val request = Request("music/search", buildJsonObject { put("search_query", "Marina Herlop") })
        assertSame(request, performanceBrowsingRequest(request, true))
    }
    @Test fun `optional source status and unlinked groups decode without changing old servers`() {
        val old = Json.decodeFromString<io.music_assistant.client.data.model.server.SearchResult>("{}")
        assertNull(old.sourceStatus)
        val value = Json.decodeFromString<io.music_assistant.client.data.model.server.SearchResult>(
            """{"source_status":{"complete":false,"sources":{"apple":"pending"}}}""")
        assertEquals("pending", value.sourceStatus?.sources?.get("apple"))
        val group = Json.decodeFromString<io.music_assistant.client.data.model.server.ArtistAlbumGroups>(
            """{"owned":[],"all":[],"unavailable_sources":[],"unlinked_sources":["apple"]}""")
        assertEquals(listOf("apple"), group.unlinkedSources)
    }
    @Test fun `old servers receive their unchanged request`() {
        val request = Request.Album.getTracks("45", "library", true)
        assertSame(request, performanceBrowsingRequest(request, false))
        assertNull(request.args?.get("interactive"))
    }
    @Test fun `fast reads preserve the library phase and item identity`() {
        val request = performanceBrowsingRequest(Request.Album.getTracks("45", "library", true), true)
        assertEquals("45", request.args?.get("item_id")?.jsonPrimitive?.content)
        assertEquals(true, request.args?.get("in_library_only")?.jsonPrimitive?.boolean)
        assertEquals(true, request.args?.get("interactive")?.jsonPrimitive?.boolean)
    }
    @Test fun `per source requests do not fan out to every remembered provider`() {
        for (command in listOf("music/artists/top_tracks", "music/artists/similar_artists")) {
            val request = performanceBrowsingRequest(Request(command, buildJsonObject {
                put("item_id", "artist"); put("provider_instance_id_or_domain", "apple-one")
            }), true)
            assertEquals("apple-one", request.args?.get("provider_filter")?.jsonPrimitive?.content)
        }
    }
    @Test fun `playback and writes never acquire browsing flags`() {
        for (command in listOf("player_queues/play_media", "music/library/add_item", "music/favorites/add_item")) {
            val request = Request(command, buildJsonObject { put("item_id", "123") })
            assertSame(request, performanceBrowsingRequest(request, true))
        }
    }
    @Test fun `unsupported probe is quiet but real errors and authentication remain visible`() {
        val messages = mutableListOf<String>(); var auth = 0; var answers = 0
        val engine = RpcEngine(onAuthError = { auth++ }, onError = { messages.add(it) })
        fun reply(id: String, code: Int) = engine.handleResponse(Json.parseToJsonElement(
            """{"message_id":"$id","error_code":$code,"details":"Error $code"}""").jsonObject)
        engine.registerCallback("probe", setOf(12)) { answers++ }; reply("probe", 12)
        assertTrue(messages.isEmpty()); assertEquals(1, answers)
        engine.registerCallback("normal") { answers++ }; reply("normal", 12)
        assertEquals(listOf("Error 12"), messages)
        engine.registerCallback("auth", setOf(12)) { answers++ }; reply("auth", 20)
        assertEquals(1, auth); assertEquals(3, answers)
    }
    @Test fun `publisher update status is explicit and backward compatible`() {
        fun source(extra: String) = Json.parseToJsonElement("""{"version":1,"sources":[{"id":"aoty","name":"AOTY","provider":"aoty","url":"https://www.albumoftheyear.org/releases/","scale":100$extra}]}""").jsonObject
        assertEquals("unknown", parseEditorialCatalog(source("")).sources.single().refreshMode)
        val result = parseEditorialCatalog(source(",\"refresh_mode\":\"manual\",\"refresh_note\":\"Automatic updates unavailable.\"")).sources.single()
        assertEquals("manual", result.refreshMode)
        assertEquals("Automatic updates unavailable.", result.refreshNote)
    }
}
