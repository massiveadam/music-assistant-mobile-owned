package io.music_assistant.client.data.model.server

import io.ktor.http.Url
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class EditorialSource(val id: String, val name: String, val provider: String, val url: String, val scale: Double) {
    val rowId: String get() = "editorial://$id"
}
@Serializable
data class EditorialCatalog(val version: Int, val sources: List<EditorialSource>)
@Serializable
data class EditorialScore(val value: Double, val max: Double,
    val count: Long? = null, @SerialName("count_label") val countLabel: String? = null)
@Serializable
data class EditorialScores(val critic: EditorialScore? = null, val user: EditorialScore? = null)
@Serializable
data class EditorialEntry(
    val title: String, val artist: String, val url: String, val image: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val scores: EditorialScores,
)
@Serializable
data class EditorialWindow(
    val start: String, val end: String, val timezone: String, val ranking: String,
    @SerialName("date_field") val dateField: String = "release_date",
)
@Serializable
data class EditorialFeed(
    val version: Int, val source: EditorialSource, val entries: List<EditorialEntry>,
    @SerialName("fetched_at") val fetchedAt: Double? = null,
    val stale: Boolean, val note: String, val window: EditorialWindow? = null,
)

fun editorialHttpsUrl(value: String): Boolean = runCatching {
    val url = Url(value)
    url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null
}.getOrDefault(false)

private fun EditorialSource.validate() {
    require(id.matches(Regex("[a-z0-9_-]+")) && name.isNotBlank() && provider.isNotBlank())
    require(editorialHttpsUrl(url) && scale.isFinite() && scale > 0)
}

fun parseEditorialCatalog(payload: JsonObject): EditorialCatalog =
    myJson.decodeFromJsonElement<EditorialCatalog>(payload).also { catalog ->
        require(catalog.version == 1 && catalog.sources.size <= 30)
        require(catalog.sources.distinctBy { it.id }.size == catalog.sources.size)
        catalog.sources.forEach { it.validate() }
    }

fun parseEditorialFeed(payload: JsonObject, sourceId: String): EditorialFeed =
    myJson.decodeFromJsonElement<EditorialFeed>(payload).also { feed ->
        require(feed.version == 1 && feed.source.id == sourceId && feed.entries.size <= 100)
        feed.source.validate()
        require(feed.fetchedAt == null || (feed.fetchedAt.isFinite() && feed.fetchedAt > 0))
        feed.entries.forEach { entry ->
            require(entry.title.isNotBlank() && entry.artist.isNotBlank() && editorialHttpsUrl(entry.url))
            require(entry.image == null || editorialHttpsUrl(entry.image))
            require(entry.releaseDate == null || entry.releaseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            listOfNotNull(entry.scores.critic, entry.scores.user).forEach { score ->
                require(score.count == null || score.count in 0L..1_000_000_000L)
                require(score.countLabel == null || score.countLabel in listOf("reviews", "ratings"))
                require(score.value.isFinite() && score.max.isFinite() && score.max > 0 && score.value in 0.0..score.max)
            }
        }
        feed.window?.let {
            require(it.ranking in listOf("critic", "user", "source"))
            require(it.dateField in listOf("release_date", "review_date"))
        }
    }

/** Only an exact, unambiguous catalog match can skip the native chooser. */
fun matchEditorialAlbum(entry: EditorialEntry, candidates: List<Album>): Album? {
    fun normalized(value: String) = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    val matches = candidates.filter { album ->
        normalized(album.name) == normalized(entry.title) &&
            album.artists.any { normalized(it.name) == normalized(entry.artist) }
    }
    val owned = matches.filter { it.provider == "library" }
    return (owned.ifEmpty { matches }).distinctBy { it.mediaUri ?: "${it.provider}:${it.itemId}" }.singleOrNull()
}
