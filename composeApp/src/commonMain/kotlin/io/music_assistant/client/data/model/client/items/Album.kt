package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.server.ProviderMapping

data class Album(
    override val itemId: String,
    override val provider: String,
    override val name: String,
    override val providerMappings: List<ProviderMapping>?,
    override val metadata: Metadata?,
    override val favorite: Boolean?,
    override val sortName: String? = null,
    override val uri: String?,
    override val images: Map<ImageType, ImageInfo>,
    val version: String?,
    val year: Int?,
    val artists: List<Artist>,
    val albumType: AlbumType? = null,
    val externalIds: List<List<String>> = emptyList(),
) : AppMediaItem() {
    override val mediaType: MediaType = MediaType.ALBUM
    override val canStartEndlessMix: Boolean = true
    override val displayName =
        "${name}${version?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
    override val subtitle: String
        get() {
            val artistNames = artists.joinToString(separator = ", ") { it.displayName }
            val releaseYear = year ?: metadata?.releaseDate?.take(4)?.toIntOrNull()
            return when {
                artistNames.isNotBlank() && releaseYear != null -> "$artistNames • $releaseYear"
                artistNames.isNotBlank() -> artistNames
                releaseYear != null -> releaseYear.toString()
                else -> ""
            }
        }
}
