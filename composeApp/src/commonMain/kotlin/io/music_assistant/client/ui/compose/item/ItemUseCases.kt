package io.music_assistant.client.ui.compose.item

import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.server.ProviderMapping
import io.music_assistant.client.data.repository.MediaItemRepository

object ItemUseCases {
    suspend inline fun <reified T : AppMediaItem> fetchArtistItemsAcrossProviders(
        mediaItemRepository: MediaItemRepository,
        artist: Artist,
        request: (itemId: String, providerInstance: String) -> Request,
    ): ItemsWithMappings<T>? {
        if (artist.providerMappings.isNullOrEmpty()) {
            return null
        }

        val ownedDomains = setOf("filesystem_local", "filesystem_smb", "filesystem_nfs", "bandcamp")
        val sortedMappings = artist.providerMappings.sortedByDescending {
            it.providerDomain in ownedDomains ||
                it.providerInstance in ownedDomains ||
                it.providerDomain.startsWith("filesystem") ||
                it.providerInstance.startsWith("filesystem")
        }

        for (mapping in sortedMappings) {
            val itemId = mapping.itemId
            val providerInstance = mapping.providerInstance
            val result = mediaItemRepository.fetchMediaItems(request(itemId, providerInstance))
            val items = result.getOrNull()?.filterIsInstance<T>() ?: emptyList()
            if (items.isNotEmpty()) {
                return ItemsWithMappings(items, mapping)
            }
        }

        return ItemsWithMappings(emptyList(), artist.providerMappings.first())
    }

    data class ItemsWithMappings<T : AppMediaItem>(
        val items: List<T>,
        val mapping: ProviderMapping,
    )
}
