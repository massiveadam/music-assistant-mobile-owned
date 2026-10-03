package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem

data class DiscoverySource(val id: String, val tag: String, val items: List<AppMediaItem>, val visible: Boolean, val fallback: Boolean = false)
data class DiscoveryEntry(val item: AppMediaItem, val tag: String)
fun AppMediaItem.discoveryId(): String = mediaUri ?: "$provider://${mediaType.serverValue}/$itemId"

private fun discoveryScore(seed: String, key: String): Long {
    var hash = 2166136261L.toInt()
    for (char in "$seed:$key") hash = (hash xor char.code) * 16777619
    hash = (hash xor (hash ushr 16)) * 0x85ebca6bL.toInt()
    hash = (hash xor (hash ushr 13)) * 0xc2b2ae35L.toInt()
    return (hash xor (hash ushr 16)).toLong() and 0xffffffffL
}

/** Stable daily/refresh rotation from visible shelves; recent listens fill only shortfalls. */
fun selectDiscoveryMix(
    sources: List<DiscoverySource>, seed: String,
    recent: Set<String> = emptySet(), previous: Set<String> = emptySet(), limit: Int = 9,
): List<DiscoveryEntry> {
    val ordered = sources.filter { it.visible && it.items.isNotEmpty() }
        .map { source -> source.copy(items = source.items.sortedWith(compareBy<AppMediaItem> { discoveryScore(seed, it.discoveryId()) }.thenBy { it.discoveryId() })) }
        .sortedWith(compareBy<DiscoverySource> { discoveryScore(seed, it.id) }.thenBy { it.id })
    val entries = mutableListOf<DiscoveryEntry>()
    val seen = mutableSetOf<String>()
    for (fallback in listOf(false, true)) for (wasPrevious in listOf(false, true)) {
        val pools = ordered.map { source -> source to source.items.filter { item ->
            (source.fallback || item.discoveryId() in recent) == fallback &&
                (item.discoveryId() in previous) == wasPrevious
        } }
        for (index in 0 until (pools.maxOfOrNull { it.second.size } ?: 0)) for ((source, items) in pools) {
            if (entries.size >= limit) return entries
            val item = items.getOrNull(index) ?: continue
            if (seen.add(item.discoveryId())) entries.add(DiscoveryEntry(item, source.tag))
        }
    }
    return entries
}
