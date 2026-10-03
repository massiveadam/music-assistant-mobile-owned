package io.music_assistant.client.data

import io.music_assistant.client.data.model.server.parseEditorialFeed
import io.music_assistant.client.ui.compose.personal.decodeInboxSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class AlbumInboxTest {
    private fun message(id: String = "message-1", note: String = "Listen to this", sentAt: Double = 100.0) = buildJsonObject {
        put("id", id)
        put("sender_name", "Meg")
        put("album", buildJsonObject {
            put("name", "Syro")
            put("artists", buildJsonArray { add("Aphex Twin") })
        })
        put("note", note)
        put("sent_at", sentAt)
        put("read_at", JsonNull)
    }
    private fun inbox(userId: String = "adam", messages: List<JsonObject> = listOf(message()), unread: Int = 1) = buildJsonObject {
        put("user_id", userId)
        put("messages", JsonArray(messages))
        put("unread", unread)
    }

    @Test fun `valid inbox keeps shared metadata and unread state`() {
        val result = decodeInboxSnapshot(inbox(), "adam")
        assertEquals("adam", result.userId)
        assertEquals(1, result.unread)
        assertEquals("Syro", result.messages.single().album.name)
        assertEquals(listOf("Aphex Twin"), result.messages.single().album.artists)
        assertNull(result.messages.single().readAt)
    }

    @Test fun `another account response cannot become the current inbox`() {
        assertFailsWith<IllegalArgumentException> { decodeInboxSnapshot(inbox("meg"), "adam") }
    }

    @Test fun `duplicate message IDs are rejected`() {
        assertFailsWith<IllegalArgumentException> { decodeInboxSnapshot(inbox(messages = listOf(message(), message())), "adam") }
    }

    @Test fun `maximum inbox size is accepted and overflow is rejected`() {
        assertEquals(200, decodeInboxSnapshot(inbox(messages = List(200) { message("message-$it") }), "adam").messages.size)
        assertFailsWith<IllegalArgumentException> {
            decodeInboxSnapshot(inbox(messages = List(201) { message("message-$it") }), "adam")
        }
    }

    @Test fun `note accepts maximum length and rejects overflow`() {
        assertEquals(500, decodeInboxSnapshot(inbox(messages = listOf(message(note = "x".repeat(500)))), "adam").messages.single().note.length)
        assertFailsWith<IllegalArgumentException> { decodeInboxSnapshot(inbox(messages = listOf(message(note = "x".repeat(501)))), "adam") }
    }

    @Test fun `invalid unread count and timestamp are rejected`() {
        assertFailsWith<IllegalArgumentException> { decodeInboxSnapshot(inbox(unread = -1), "adam") }
        assertFailsWith<IllegalArgumentException> { decodeInboxSnapshot(inbox(messages = listOf(message(sentAt = -1.0))), "adam") }
    }

    @Test fun `unexpected private source fields are rejected`() {
        val leaked = JsonObject(message() + ("uri" to JsonPrimitive("private-source://album/1")))
        assertFailsWith<SerializationException> { decodeInboxSnapshot(inbox(messages = listOf(leaked)), "adam") }
    }

    private fun editorialScore(count: JsonElement? = null, label: JsonElement? = null) = buildJsonObject {
        put("value", 85)
        put("max", 100)
        count?.let { put("count", it) }
        label?.let { put("count_label", it) }
    }
    private fun editorialFeed(score: JsonObject) = buildJsonObject {
        put("version", 1)
        put("source", buildJsonObject {
            put("id", "aoty_weekly")
            put("name", "AOTY weekly releases")
            put("provider", "aoty")
            put("url", "https://www.albumoftheyear.org/")
            put("scale", 100)
        })
        put("entries", buildJsonArray {
            add(buildJsonObject {
                put("title", "Syro")
                put("artist", "Aphex Twin")
                put("url", "https://www.albumoftheyear.org/album/1-syro.php")
                put("scores", buildJsonObject { put("critic", score) })
            })
        })
        put("stale", false)
        put("note", "")
    }

    @Test fun `score preserves review and rating totals including zero`() {
        for (label in listOf("reviews", "ratings")) {
            val score = parseEditorialFeed(editorialFeed(editorialScore(JsonPrimitive(27), JsonPrimitive(label))), "aoty_weekly").entries.single().scores.critic!!
            assertEquals(27L, score.count)
            assertEquals(label, score.countLabel)
        }
        assertEquals(0L, parseEditorialFeed(editorialFeed(editorialScore(JsonPrimitive(0), JsonPrimitive("reviews"))), "aoty_weekly").entries.single().scores.critic!!.count)
    }

    @Test fun `missing count remains unknown rather than zero`() {
        val score = parseEditorialFeed(editorialFeed(editorialScore()), "aoty_weekly").entries.single().scores.critic!!
        assertNull(score.count)
        assertNull(score.countLabel)
    }

    @Test fun `invalid score count and label are rejected`() {
        for (count in listOf(JsonPrimitive(-1), JsonPrimitive(1_000_000_001L))) {
            assertFailsWith<IllegalArgumentException> { parseEditorialFeed(editorialFeed(editorialScore(count, JsonPrimitive("reviews"))), "aoty_weekly") }
        }
        assertFailsWith<IllegalArgumentException> {
            parseEditorialFeed(editorialFeed(editorialScore(JsonPrimitive(1), JsonPrimitive("votes"))), "aoty_weekly")
        }
        assertFailsWith<SerializationException> {
            parseEditorialFeed(editorialFeed(editorialScore(JsonPrimitive(1.5), JsonPrimitive("ratings"))), "aoty_weekly")
        }
    }
}
