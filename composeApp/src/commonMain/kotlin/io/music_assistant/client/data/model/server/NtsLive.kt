package io.music_assistant.client.data.model.server

import io.ktor.http.Url
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NtsTrack(val title: String? = null, val artists: List<String>, val startedAt: String) {
    val identified: Boolean get() = !title.isNullOrBlank() && artists.any { it.isNotBlank() }
    val label: String get() = if (identified) "${artists.joinToString(", ")} · $title" else "Track not identified"
}
@Serializable
data class NtsFeed(@SerialName("user_id") val userId: String, val stationUri: String,
    val stream: String? = null, val station: String, val status: String, val tracks: List<NtsTrack>) {
    val label: String get() = when (status) {
        "identified" -> "NTS live: ${tracks.firstOrNull()?.label ?: "Track not identified"}"
        "disconnected" -> "Connect NTS for live tracks"
        "stale" -> "NTS track identification is out of date"
        else -> "NTS has not identified this track"
    }
}
@Serializable data class NtsLink(val service: String, val url: String)
@Serializable data class NtsMatch(val uri: String, val name: String, val artists: List<String>,
    val album: String? = null, val version: String = "", val links: List<NtsLink>)
@Serializable data class NtsResolution(@SerialName("user_id") val userId: String, val stationUri: String,
    val track: NtsTrack, val search: String, val matches: List<NtsMatch>, val alternatives: List<NtsMatch>)

fun ntsHttpsUrl(value: String): Boolean = runCatching {
    val url = Url(value)
    val authority = value.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
    value.length <= 2048 && value.startsWith("https://", ignoreCase = true) && authority.isNotBlank() && '@' !in authority &&
        url.protocol.name == "https" && url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty()
}.getOrDefault(false)

fun parseNtsFeed(data: JsonObject, user: String, uri: String): NtsFeed {
    val feed = myJson.decodeFromString<NtsFeed>(data.toString())
    require(feed.userId == user && feed.stationUri == uri)
    require(feed.status in setOf("identified", "unidentified", "stale", "disconnected", "unsupported"))
    require(feed.tracks.size <= 3 && feed.tracks.all { it.artists.size <= 30 && it.startedAt.isNotBlank() })
    require(feed.status != "identified" || feed.tracks.firstOrNull()?.identified == true)
    return feed
}

fun parseNtsResolution(data: JsonObject, user: String, uri: String, stamp: String): NtsResolution {
    val result = myJson.decodeFromString<NtsResolution>(data.toString())
    require(result.userId == user && result.stationUri == uri && result.track.startedAt == stamp)
    require(result.matches.size + result.alternatives.size <= 25)
    require((result.matches + result.alternatives).all { it.links.all { link -> ntsHttpsUrl(link.url) } })
    return result
}
