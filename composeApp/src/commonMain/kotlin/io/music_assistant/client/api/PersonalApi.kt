package io.music_assistant.client.api

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.authenticatedToken
import io.music_assistant.client.utils.createPlatformHttpClient
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Same authenticated connection as playback. Never uses a saved token from another server. */
class PersonalApi(
    val serviceClient: ServiceClient,
    private val http: HttpClient = createPlatformHttpClient {
        install(io.ktor.client.plugins.HttpTimeout) { requestTimeoutMillis = 60000; connectTimeoutMillis = 10000 }
        followRedirects = false // never forward a user token to a redirect origin
    },
) {
    class Session internal constructor(
        val serverId: String,
        val userId: String,
        val origin: String?,
        val remoteId: String?,
        internal val token: String,
    ) {
        fun same(other: Session?): Boolean = other != null && serverId == other.serverId &&
            userId == other.userId && origin == other.origin && remoteId == other.remoteId && token == other.token
        override fun toString(): String = "PersonalSession(authenticated)"
    }

    fun session(): Session? {
        val state = serviceClient.sessionState.value as? SessionState.Connected ?: return null
        val token = state.authenticatedToken() ?: return null
        val userId = state.user?.userId ?: return null
        val serverId = state.serverInfo?.serverId ?: return null
        return Session(serverId, userId,
            (state as? SessionState.Connected.Direct)?.connectionInfo?.webUrl,
            (state as? SessionState.Connected.WebRTC)?.remoteId?.toString(), token)
    }

    fun requireCurrent(session: Session) {
        check(session.same(session())) { "Account or server changed. Try again." }
    }

    suspend fun request(path: String, body: JsonObject? = null, session: Session = session()
        ?: error("Sign in to use your music.")): JsonObject {
        require(path.startsWith("/credits/v1/") && !path.contains(".."))
        requireCurrent(session)
        val (status, text) = session.origin?.let { origin ->
            val response = http.request(origin.trimEnd('/') + path) {
                method = if (body == null) HttpMethod.Get else HttpMethod.Post
                header("Authorization", "Bearer ${session.token}")
                header("Accept", "application/json")
                if (body != null) {
                    header("Content-Type", "application/json")
                    setBody(body.toString())
                }
            }
            response.status.value to response.bodyAsText()
        } ?: run {
            // Upstream's WebRTC HTTP proxy supports reads, but does not forward POST bodies.
            check(body == null) { "Connect using the Sanchez HTTPS address to edit saves or collections." }
            val proxy = serviceClient.webRTCHttpProxy ?: error("Connection unavailable. Try again.")
            val response = proxy.get(path, mapOf("Authorization" to "Bearer ${session.token}"), 60000)
            response.status to response.body.decodeToString()
        }
        requireCurrent(session)
        val result = runCatching { myJson.decodeFromString<JsonObject>(text) }.getOrNull()
        check(status in 200..299) {
            (result?.get("error") as? JsonPrimitive)?.contentOrNull ?: "Could not load your music (HTTP $status)."
        }
        return result ?: error("Invalid response from your music service.")
    }
}
