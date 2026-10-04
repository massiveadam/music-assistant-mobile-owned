package io.music_assistant.client.data

import io.music_assistant.client.api.Event
import io.music_assistant.client.data.model.server.events.*
import kotlinx.serialization.json.*
import org.junit.Test
import kotlin.test.*

class AlbumSyncRefreshTest {
    private fun task(status: String, type: String = "album", domain: String = "music_sync", id: String = "sync") =
        BackgroundSyncTask(id, status, buildJsonObject { put("media_type", type); put("task_domain", domain) })

    @Test fun `refresh once after an observed successful album sync`() {
        val tracker = AlbumSyncCompletionTracker()
        assertFalse(tracker.observe(listOf(task("success"))))
        assertFalse(tracker.observe(listOf(task("running"))))
        assertTrue(tracker.observe(listOf(task("success"))))
        assertFalse(tracker.observe(listOf(task("success"))))
    }
    @Test fun `partial success refreshes but failed or unrelated tasks do not`() {
        val tracker = AlbumSyncCompletionTracker()
        assertFalse(tracker.observe(listOf(task("running"), task("running", type = "artist", id = "artists"))))
        assertFalse(tracker.observe(listOf(task("failed"), task("success", type = "artist", id = "artists"))))
        assertFalse(tracker.observe(listOf(task("pending"))))
        assertTrue(tracker.observe(listOf(task("partial_success"))))
        assertFalse(tracker.observe(listOf(task("running", domain = "other"), task("success", domain = "other"))))
    }
    @Test fun `track scan creates albums after album scan completed`() {
        val tracker = AlbumSyncCompletionTracker()
        assertFalse(tracker.observe(listOf(task("running", id = "albums"), task("running", type = "track", id = "tracks"))))
        assertTrue(tracker.observe(listOf(task("success", id = "albums"))))
        assertTrue(tracker.observe(listOf(task("partial_success", type = "track", id = "tracks"))))
        assertFalse(tracker.observe(listOf(task("partial_success", type = "track", id = "tracks"))))
    }
    @Test fun `account reconnect clears previous tasks`() {
        val tracker = AlbumSyncCompletionTracker()
        tracker.observe(listOf(task("running")))
        tracker.clear()
        assertFalse(tracker.observe(listOf(task("success"))))
    }
    @Test fun `task push payload reaches the native event stream`() {
        val raw = Json.parseToJsonElement("""{"event":"tasks_updated","data":[{"id":"sync","status":"success","metadata":{"task_domain":"music_sync","media_type":"album"},"name":"Sync","logs":[]}]}""").jsonObject
        val decoded = assertIs<TasksUpdatedEvent>(Event(raw).event())
        assertEquals("album", decoded.data.single().metadata["media_type"]?.jsonPrimitive?.content)
    }
    @Test fun `malformed task payload is dropped without interrupting events`() {
        val raw = Json.parseToJsonElement("""{"event":"tasks_updated","data":"unexpected"}""").jsonObject
        assertNull(Event(raw).event())
    }
}
