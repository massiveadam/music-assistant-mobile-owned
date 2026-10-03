package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PersonalItem(
    val uri: String,
    @SerialName("item_id") val itemId: String,
    val provider: String,
    val name: String,
    @SerialName("media_type") val mediaType: String,
    val version: String? = null,
    val year: Int? = null,
    val artists: List<String> = emptyList(),
    val image: ServerMediaItemImage? = null,
    val available: Boolean = true,
    @SerialName("is_playable") val isPlayable: Boolean = true,
    @SerialName("external_ids") val externalIds: List<List<String>> = emptyList(),
) {
    fun asServer(): ServerMediaItem = ServerMediaItem(
        itemId = itemId, provider = provider, name = name, uri = uri, mediaType = mediaType,
        version = version, year = year, image = image, isPlayable = isPlayable && available,
        artists = artists.map { ServerMediaItem(itemId = "", provider = provider, name = it, mediaType = "artist") },
        externalIds = externalIds,
    )
}

@Serializable
data class PersonalSave(
    val uri: String,
    val aliases: List<String> = emptyList(),
    @SerialName("saved_at") val savedAt: Double,
    val item: PersonalItem,
)

@Serializable
data class PersonalMusicSnapshot(
    @SerialName("user_id") val userId: String,
    val entries: List<PersonalSave>,
    val unavailable: Int = 0,
)

@Serializable
enum class CollectionStatus { @SerialName("pending") PENDING, @SerialName("listened") LISTENED }

@Serializable
enum class CollectionKind { @SerialName("listen_later") LISTEN_LATER, @SerialName("custom") CUSTOM }

@Serializable
data class AlbumCollectionEntry(
    val uri: String,
    val aliases: List<String> = emptyList(),
    @SerialName("added_at") val addedAt: Double,
    val status: CollectionStatus,
    @SerialName("listened_at") val listenedAt: Double? = null,
    val item: PersonalItem,
)

@Serializable
data class AlbumCollection(
    val id: String,
    val name: String,
    val kind: CollectionKind,
    val entries: List<AlbumCollectionEntry>,
) {
    fun contains(uris: Set<String>): Boolean = entries.any { entry ->
        entry.uri in uris || entry.aliases.any { it in uris } || entry.item.uri in uris
    }
}

@Serializable
data class AlbumCollectionsSnapshot(
    @SerialName("user_id") val userId: String,
    val collections: List<AlbumCollection>,
)
