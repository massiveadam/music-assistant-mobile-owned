package io.music_assistant.client.data.model.server.events

import io.music_assistant.client.data.model.server.EventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BackgroundSyncTask(val id: String, val status: String, val metadata: JsonObject)

@Serializable
data class TasksUpdatedEvent(
    @SerialName("event") override val event: EventType,
    @SerialName("object_id") override val objectId: String? = null,
    override val data: List<BackgroundSyncTask> = emptyList(),
) : Event<List<BackgroundSyncTask>>

/** Ignore initial terminal snapshots and repeated completions, as the web client does. */
class AlbumSyncCompletionTracker {
    private val statuses = mutableMapOf<String, String>()
    fun clear() = statuses.clear()
    fun observe(tasks: List<BackgroundSyncTask>): Boolean {
        var completed = false
        for (task in tasks) {
            fun field(name: String) = runCatching { task.metadata[name]?.jsonPrimitive?.content }.getOrNull()
            // Track scans can create albums after the separate album task completes.
            if (field("task_domain") != "music_sync" || field("media_type") !in listOf("album", "track")) continue
            val previous = statuses.put(task.id, task.status)
            if (previous in listOf("pending", "running") && task.status in listOf("success", "partial_success")) completed = true
        }
        return completed
    }
}
