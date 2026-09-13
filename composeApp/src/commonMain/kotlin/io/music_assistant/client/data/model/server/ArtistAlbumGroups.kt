package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistAlbumGroups(
    val owned: List<ServerMediaItem>,
    val all: List<ServerMediaItem>,
    @SerialName("unavailable_sources") val unavailableSources: List<String> = emptyList(),
)
