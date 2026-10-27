package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.SERVER_PAGE_SIZE
import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.events.Event
import io.music_assistant.client.utils.ConnectionData
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * [MediaItemRepository.fetchAllMediaItems] must load a whole playlist on both server generations.
 * 2.10 ignores `limit`/`offset` and streams everything on one request, so paging it would repeat
 * the list on every page. 2.11 answers one 500-item page, so the pages must be walked.
 */
class MediaItemRepositoryPagingTest {
    private companion object {
        const val SERVER_2_10_SCHEMA = 65
        const val TOTAL = 700
    }

    private fun trackJson(itemId: Int) = """
        {"item_id":"$itemId","provider":"library","name":"Track $itemId",
         "media_type":"track","is_playable":true}
    """.trimIndent()

    private fun tracksJson(ids: IntRange) = ids.joinToString(",", "[", "]", transform = ::trackJson)

    private class FakeClient(
        schemaVersion: Int,
        private val resultFor: (limit: Int?, offset: Int?) -> String,
    ) : StubServiceClient() {
        val requestArgs = mutableListOf<JsonObject?>()

        override val events: Flow<Event<out Any>> = emptyFlow()

        override val sessionState: StateFlow<SessionState> = MutableStateFlow(
            SessionState.Connected.Direct(
                connectionInfo = ConnectionInfo(host = "test", port = 8095, isTls = false),
                connectionData = ConnectionData(
                    serverInfo = ServerInfo(serverId = "test", schemaVersion = schemaVersion),
                ),
            ),
        )

        override suspend fun sendRequest(request: Request): Result<Answer> {
            requestArgs += request.args
            val json = resultFor(
                request.args?.get("limit")?.jsonPrimitive?.int,
                request.args?.get("offset")?.jsonPrimitive?.int,
            )
            return Result.success(
                Answer(
                    buildJsonObject {
                        put("message_id", "test")
                        put("result", myJson.parseToJsonElement(json))
                    },
                ),
            )
        }
    }

    private fun repository(client: FakeClient) =
        ServiceClientMediaItemRepository(client, MediaItemFactory(client))

    private val request = Request.Playlist.getTracks(itemId = "p1", providerInstanceIdOrDomain = "library")

    @Test
    fun server210GetsOnePlainRequestThatStreamsEverything() = runTest {
        val client = FakeClient(SERVER_2_10_SCHEMA) { limit, offset ->
            assertNull(limit)
            assertNull(offset)
            tracksJson(1..TOTAL)
        }

        val items = repository(client).fetchAllMediaItems(request)

        assertEquals((1..TOTAL).map(Int::toString), items?.map { it.itemId })
        assertEquals(
            setOf("item_id", "provider_instance_id_or_domain"),
            client.requestArgs.single()?.keys,
        )
    }

    @Test
    fun server211WalksThePagesUntilAShortOne() = runTest {
        val client = FakeClient(SERVER_2_11_SCHEMA) { limit, offset ->
            assertEquals(SERVER_PAGE_SIZE, limit)
            when (offset) {
                0 -> tracksJson(1..SERVER_PAGE_SIZE)
                SERVER_PAGE_SIZE -> tracksJson(SERVER_PAGE_SIZE + 1..TOTAL)
                else -> fail("unexpected offset $offset")
            }
        }

        val items = repository(client).fetchAllMediaItems(request)

        assertEquals((1..TOTAL).map(Int::toString), items?.map { it.itemId })
        assertEquals(
            listOf(0, SERVER_PAGE_SIZE),
            client.requestArgs.map { it?.get("offset")?.jsonPrimitive?.int },
        )
    }
}
