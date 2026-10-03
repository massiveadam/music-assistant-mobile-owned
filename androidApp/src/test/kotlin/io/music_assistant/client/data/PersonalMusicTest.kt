package io.music_assistant.client.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.music_assistant.client.api.*
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.*
import io.music_assistant.client.data.repository.PersonalMusicRepository
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class PersonalMusicTest {
    private class Client : ServiceClient by FakeServiceClient() {
        override val sessionState = MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
        fun signIn(user: String = "adam", server: String = "sanchez", host: String = "sanchez.test") {
            sessionState.value = SessionState.Connected.Direct(ConnectionInfo(host, 8095, false),
                ConnectionData(serverInfo = ServerInfo(server), user = User(userId = user), token = "test-$user-$server"))
        }
    }
    private val itemJson = """{"uri":"library://album/51","item_id":"51","provider":"library","name":"Syro","media_type":"album","year":2014,"image":{"type":"thumb","path":"opaque","provider":"proxy","proxy_id":"safe-image-id"}}"""
    private fun saveJson() = """{"uri":"library://album/51","aliases":["stream://album/123"],"saved_at":100,"item":$itemJson}"""
    private fun saves(user: String = "adam", filled: Boolean = false) = """{"user_id":"$user","entries":[${if (filled) saveJson() else ""}],"unavailable":0}"""
    private fun albums(user: String = "adam") = """{"user_id":"$user","collections":[{"id":"listen-later","name":"Listen Later","kind":"listen_later","entries":[]}]}"""
    private fun album(client: Client): AppMediaItem = requireNotNull(MediaItemFactory(client).create(
        ServerMediaItem("51", "library", "Syro", mediaType = "album", uri = "library://album/51")))
    private fun http(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = HttpClient(MockEngine { request -> handler(request) })
    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(3000) { while (!condition()) delay(5) }

    @Test fun `HTTP follows current connection and authenticates with current proven token`() = runBlocking {
        val client = Client().apply { signIn(host = "private.test") }
        val http = http { request ->
            assertEquals("private.test", request.url.host)
            assertEquals("/credits/v1/personal-music", request.url.encodedPath)
            assertEquals("Bearer test-adam-sanchez", request.headers[HttpHeaders.Authorization])
            respond(saves(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try { assertEquals("adam", PersonalApi(client, http).request(PersonalMusicRepository.SAVES)["user_id"]?.jsonPrimitive?.content) }
        finally { http.close() }
    }

    @Test fun `old account response is rejected before publication`() = runBlocking {
        val client = Client().apply { signIn() }
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine {
            started.complete(Unit); finish.await()
            respond(saves(filled = true), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val api = PersonalApi(client, http)
            val request = async { runCatching { api.request(PersonalMusicRepository.SAVES) } }
            started.await(); client.signIn(user = "meg"); finish.complete(Unit)
            assertTrue(request.await().isFailure)
        } finally { http.close() }
    }

    @Test fun `captured old session cannot send a queued write after switching servers`() = runBlocking {
        val client = Client().apply { signIn() }; var requests = 0
        val http = http { requests++; respond(saves()) }
        try {
            val api = PersonalApi(client, http); val session = requireNotNull(api.session())
            client.signIn(server = "other", host = "other.test")
            assertFailsWith<IllegalStateException> { api.request(PersonalMusicRepository.SAVES, buildJsonObject { put("saved", true) }, session) }
            assertEquals(0, requests)
        } finally { http.close() }
    }

    @Test fun `shared favorites are ignored and aliases identify private saves`() = runBlocking {
        val client = Client().apply { signIn() }
        val http = http { request -> respond(if (request.url.encodedPath.endsWith("album-collections")) albums() else saves(filled = true),
            headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = PersonalMusicRepository(PersonalApi(client, http), scope)
            awaitCondition { repository.state.value.saves != null }
            assertTrue(repository.isSaved(setOf("stream://album/123")))
            assertFalse(repository.isSaved(setOf("library://album/999")))
            val factory = MediaItemFactory(client, repository)
            val shared = ServerMediaItem("999", "library", "Shared favorite", favorite = true, mediaType = "album", uri = "library://album/999")
            assertEquals(false, factory.create(shared)?.favorite)
            assertEquals("safe-image-id", repository.state.value.saves?.entries?.first()?.item?.asServer()?.image?.proxyId)
        } finally { scope.cancel(); http.close() }
    }

    @Test fun `logout drops both private stores and switch loads only the new owner`() = runBlocking {
        val client = Client().apply { signIn() }
        val http = http { request ->
            val owner = if (request.headers[HttpHeaders.Authorization]?.contains("meg") == true) "meg" else "adam"
            respond(if (request.url.encodedPath.endsWith("album-collections")) albums(owner) else saves(owner, owner == "adam"),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = PersonalMusicRepository(PersonalApi(client, http), scope)
            awaitCondition { repository.state.value.saves != null }
            client.sessionState.value = SessionState.Disconnected.ByUser
            awaitCondition { repository.state.value.userId == null }
            assertNull(repository.state.value.saves); assertNull(repository.state.value.albums)
            client.signIn("meg")
            awaitCondition { repository.state.value.albums?.userId == "meg" }
            assertTrue(repository.state.value.saves?.entries?.isEmpty() == true)
        } finally { scope.cancel(); http.close() }
    }

    @Test fun `response with different owner cannot fill the private store`() = runBlocking {
        val client = Client().apply { signIn() }
        val http = http { respond(saves("other", true), headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = PersonalMusicRepository(PersonalApi(client, http), scope)
            awaitCondition { repository.state.value.error != null }
            assertNull(repository.state.value.saves)
        } finally { scope.cancel(); http.close() }
    }

    @Test fun `failed save mutation preserves the last confirmed state`() = runBlocking {
        val client = Client().apply { signIn() }
        val http = http { request ->
            if (request.method == HttpMethod.Post) respond("""{"error":"Unavailable"}""", HttpStatusCode.ServiceUnavailable,
                headersOf(HttpHeaders.ContentType, "application/json"))
            else respond(if (request.url.encodedPath.endsWith("album-collections")) albums() else saves(filled = true),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = PersonalMusicRepository(PersonalApi(client, http), scope)
            awaitCondition { repository.state.value.albums != null }
            assertTrue(repository.setSaved(album(client), false).isFailure)
            assertTrue(repository.isSaved(album(client)))
        } finally { scope.cancel(); http.close() }
    }

    @Test fun `unavailable albums retain removable identity and listened state`() {
        val json = """{"user_id":"adam","collections":[{"id":"listen-later","name":"Listen Later","kind":"listen_later","entries":[{"uri":"removed://album/51","aliases":[],"added_at":100,"status":"listened","listened_at":200,"item":{"uri":"removed://album/51","item_id":"","provider":"","name":"Unavailable album","media_type":"album","available":false,"artists":[]}}]}]}"""
        val result = myJson.decodeFromString<AlbumCollectionsSnapshot>(json)
        val entry = result.collections.single().entries.single()
        assertEquals(CollectionStatus.LISTENED, entry.status)
        assertEquals("removed://album/51", entry.uri)
        assertFalse(entry.item.available)
    }
}
