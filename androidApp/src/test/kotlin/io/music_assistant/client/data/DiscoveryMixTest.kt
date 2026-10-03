package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.*
import io.music_assistant.client.data.model.client.items.Album
import org.junit.Test
import kotlin.test.*

class DiscoveryMixTest {
    private fun items(prefix: String, count: Int = 20) = (0 until count).map { id -> Album(
        itemId = "$id", provider = prefix, name = "$prefix $id", providerMappings = null,
        metadata = null, favorite = null, uri = "$prefix://album/$id", images = emptyMap(), version = null,
        year = null, artists = emptyList(),
    ) }
    private val sources = listOf(
        DiscoverySource("saved", "Saved by you", items("saved"), true),
        DiscoverySource("forgotten", "Forgotten albums", items("library"), true),
    )
    @Test fun `refresh rotates the lead across available shelves`() {
        val leads = (0 until 20).map { selectDiscoveryMix(sources, "user:2026-10-03:$it").first().item.discoveryId() }.toSet()
        assertTrue(leads.size > 5)
    }
    @Test fun `background source order cannot reset selection`() {
        assertEquals(selectDiscoveryMix(sources, "day:3"), selectDiscoveryMix(sources.reversed(), "day:3"))
    }
    @Test fun `new day changes mix`() {
        assertNotEquals(selectDiscoveryMix(sources, "2026-10-02:0"), selectDiscoveryMix(sources, "2026-10-03:0"))
    }
    @Test fun `hidden shelves never enter mix`() {
        assertTrue(selectDiscoveryMix(sources.map { it.copy(visible = false) }, "day").isEmpty())
    }
    @Test fun `recent and previous picks are avoided when alternatives exist`() {
        val recent = items("library", 9).mapTo(mutableSetOf()) { it.discoveryId() }
        val previous = items("saved", 9).mapTo(mutableSetOf()) { it.discoveryId() }
        val selected = selectDiscoveryMix(sources, "refresh", recent, previous)
        assertEquals(9, selected.size)
        assertTrue(selected.all { it.item.discoveryId() !in recent && it.item.discoveryId() !in previous })
    }
    @Test fun `small libraries fill shortfalls without duplicates and preserve labels`() {
        val shared = items("shared", 1).single()
        val selected = selectDiscoveryMix(sources.map { it.copy(items = listOf(shared)) }, "day",
            setOf(shared.discoveryId()), setOf(shared.discoveryId()))
        assertEquals(1, selected.size)
        assertTrue(selected.single().tag in sources.map { it.tag })
    }
    @Test fun `recently played shelf is a fallback`() {
        val recent = DiscoverySource("recent", "Recently played", items("recent"), true, fallback = true)
        val selected = selectDiscoveryMix(sources + recent, "day")
        assertTrue(selected.none { it.tag == "Recently played" })
    }
}
