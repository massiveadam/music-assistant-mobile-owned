package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.data.model.client.ReleaseSort
import io.music_assistant.client.data.model.client.editionLabel
import io.music_assistant.client.data.model.client.filterArtistReleases
import io.music_assistant.client.data.model.client.groupArtistReleases
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.releaseCategory
import io.music_assistant.client.data.model.client.sortArtistReleases
import io.music_assistant.client.data.model.server.ProviderMapping
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure unit tests for the conservative artist-discography grouping/filter/sort
 * algorithm ported from the web client's `helpers/artistDiscography.ts`.
 */
class ArtistDiscographyTest {

    private fun artist(name: String) = Artist(
        itemId = name,
        provider = "test",
        name = name,
        providerMappings = null,
        metadata = null,
        favorite = null,
        uri = "test://artist/$name",
        images = emptyMap(),
    )

    private fun album(
        uri: String,
        name: String = "Album",
        version: String? = null,
        year: Int? = null,
        provider: String = "spotify",
        albumType: AlbumType? = AlbumType.ALBUM,
        releaseGroup: String? = null,
        artists: List<String> = listOf("Test Artist"),
        favorite: Boolean? = null,
        providerInstance: String? = null,
    ) = Album(
        itemId = uri,
        provider = provider,
        name = name,
        providerMappings = providerInstance?.let {
            listOf(ProviderMapping(itemId = uri, providerDomain = it, providerInstance = it))
        },
        metadata = null,
        favorite = favorite,
        uri = uri,
        images = emptyMap(),
        version = version,
        year = year,
        artists = artists.map(::artist),
        albumType = albumType,
        externalIds = releaseGroup
            ?.let { listOf(listOf("musicbrainz_releasegroupid", it)) }
            ?: emptyList(),
    )

    // Grouping

    @Test
    fun `editions sharing a release-group id merge and the owned edition becomes primary`() {
        val streaming = album(uri = "spotify:1", releaseGroup = "rg1", year = 1990)
        val owned = album(uri = "local:1", releaseGroup = "rg1", year = 1990, provider = "filesystem_local")

        val releases = groupArtistReleases(all = listOf(streaming), ownedAlbums = listOf(owned))

        assertEquals(1, releases.size)
        val release = releases.single()
        assertEquals(setOf(streaming.uri, owned.uri), release.editions.map { it.uri }.toSet())
        assertEquals(owned.uri, release.primary.uri)
        assertTrue(release.owned)
        assertEquals(listOf(owned.uri), release.ownedUris)
    }

    @Test
    fun `owned albums missing from the full catalog are still included`() {
        val owned = album(uri = "local:1", releaseGroup = "rg1", provider = "filesystem_local")

        val releases = groupArtistReleases(all = emptyList(), ownedAlbums = listOf(owned))

        assertEquals(1, releases.size)
        assertTrue(releases.single().owned)
    }

    @Test
    fun `same release-group id with distinct recordings stays split`() {
        val studio = album(uri = "a", releaseGroup = "rg1")
        val live = album(uri = "b", name = "Album (Live)", albumType = AlbumType.LIVE, releaseGroup = "rg1")
        val remix = album(uri = "c", version = "Remix", releaseGroup = "rg1")

        val releases = groupArtistReleases(all = listOf(studio, live, remix), ownedAlbums = emptyList())

        assertEquals(3, releases.size)
        assertTrue(releases.all { it.editions.size == 1 })
    }

    @Test
    fun `an album carrying two conflicting release-group ids joins the first match without bridging`() {
        val a = album(uri = "a", releaseGroup = "rg1")
        val b = album(uri = "b", releaseGroup = "rg2")
        val both = album(
            uri = "c",
            releaseGroup = null,
        ).let { base ->
            base.copy(externalIds = listOf(
                listOf("musicbrainz_releasegroupid", "rg1"),
                listOf("musicbrainz_releasegroupid", "rg2"),
            ))
        }

        val releases = groupArtistReleases(all = listOf(a, b, both), ownedAlbums = emptyList())

        // "both" merges into a's group but must not pull a's and b's groups together.
        assertEquals(2, releases.size)
        assertEquals(setOf("a", "c"), releases.first().editions.map { it.uri }.toSet())
        assertEquals(setOf("b"), releases.last().editions.map { it.uri }.toSet())
    }

    @Test
    fun `an unidentified album matching two groups stays an unmerged orphan`() {
        val a = album(uri = "a", name = "Greatest Hits", year = 2000, releaseGroup = "rg-a")
        val b = album(uri = "b", name = "Greatest Hits", year = 2000, releaseGroup = "rg-b")
        val unidentified = album(uri = "c", name = "Greatest Hits", year = 2000)

        val releases = groupArtistReleases(all = listOf(a, b, unidentified), ownedAlbums = emptyList())

        assertEquals(3, releases.size)
        val orphan = releases.single { it.primary.uri == "c" }
        assertEquals(1, orphan.editions.size)
    }

    @Test
    fun `an unidentified album with an unambiguous title artist and year match joins that group`() {
        val identified = album(uri = "a", name = "Greatest Hits", year = 2000, releaseGroup = "rg1")
        val unidentified = album(uri = "b", name = "Greatest Hits", year = 2000)

        val releases = groupArtistReleases(all = listOf(identified, unidentified), ownedAlbums = emptyList())

        assertEquals(1, releases.size)
        assertEquals(setOf("a", "b"), releases.single().editions.map { it.uri }.toSet())
    }

    @Test
    fun `fallback matching rejects the same title from a different artist`() {
        val identified = album(uri = "a", name = "Greatest Hits", year = 2000, releaseGroup = "rg1")
        val otherArtist = album(
            uri = "b",
            name = "Greatest Hits",
            year = 2000,
            artists = listOf("Someone Else"),
        )

        val releases = groupArtistReleases(all = listOf(identified, otherArtist), ownedAlbums = emptyList())

        assertEquals(2, releases.size)
    }

    // Categories and labels

    @Test
    fun `explicit EP suffix categorizes an untyped album, vague titles stay unknown`() {
        assertEquals(AlbumType.EP, releaseCategory(album(uri = "a", name = "Cool EP", albumType = null)))
        assertEquals(AlbumType.EP, releaseCategory(album(uri = "b", name = "Cool (EP)", albumType = null)))
        assertNull(releaseCategory(album(uri = "c", name = "Cool", albumType = null)))
        // A server-provided type always wins.
        assertEquals(
            AlbumType.SINGLE,
            releaseCategory(album(uri = "d", name = "Cool EP", albumType = AlbumType.SINGLE)),
        )
    }

    @Test
    fun `editionLabel prefers the version field then an edition suffix in the name`() {
        assertEquals("Deluxe", editionLabel(album(uri = "a", name = "Album (Remastered)", version = "Deluxe")))
        assertEquals("Remastered", editionLabel(album(uri = "b", name = "Album (Remastered)")))
        // A live suffix is a distinct recording, not an edition label.
        assertNull(editionLabel(album(uri = "c", name = "Album (Live)")))
        assertNull(editionLabel(album(uri = "d")))
    }

    @Test
    fun `release category falls back to a single known edition type, null on conflict`() {
        val untyped = album(uri = "a", albumType = null, releaseGroup = "rg1")
        val ep = album(uri = "b", albumType = AlbumType.EP, releaseGroup = "rg1")

        val resolved = groupArtistReleases(all = listOf(untyped, ep), ownedAlbums = emptyList())
        assertEquals(AlbumType.EP, resolved.single().category)

        // With an untyped primary and two conflicting known edition types, the
        // category stays unresolved (the "Other" bucket).
        val single = album(uri = "c", albumType = AlbumType.SINGLE, releaseGroup = "rg1")
        val conflicting = groupArtistReleases(all = listOf(untyped, ep, single), ownedAlbums = emptyList())
        assertEquals(untyped.uri, conflicting.single().primary.uri)
        assertNull(conflicting.single().category)

        // A typed primary always wins over the other editions.
        val typedPrimary = groupArtistReleases(all = listOf(ep, single), ownedAlbums = emptyList())
        assertEquals(AlbumType.EP, typedPrimary.single().category)
    }

    // Sorting

    @Test
    fun `sort newest and oldest put unknown years last`() {
        val releases = groupArtistReleases(
            all = listOf(
                album(uri = "a", name = "Mid", year = 1990, releaseGroup = "rg-a"),
                album(uri = "b", name = "New", year = 2001, releaseGroup = "rg-b"),
                album(uri = "c", name = "Undated", year = null, releaseGroup = "rg-c"),
            ),
            ownedAlbums = emptyList(),
        )

        assertEquals(listOf("New", "Mid", "Undated"), sortArtistReleases(releases, ReleaseSort.NEWEST).names())
        assertEquals(listOf("Mid", "New", "Undated"), sortArtistReleases(releases, ReleaseSort.OLDEST).names())
    }

    @Test
    fun `sort by title ignores years and compares numbers naturally`() {
        val releases = groupArtistReleases(
            all = listOf(
                album(uri = "a", name = "Album 10", year = 2001, releaseGroup = "rg-a"),
                album(uri = "b", name = "Album 2", year = 1990, releaseGroup = "rg-b"),
                album(uri = "c", name = "Album 1", year = null, releaseGroup = "rg-c"),
            ),
            ownedAlbums = emptyList(),
        )

        assertEquals(
            listOf("Album 1", "Album 2", "Album 10"),
            sortArtistReleases(releases, ReleaseSort.TITLE).names(),
        )
    }

    // Filtering

    @Test
    fun `source filter keeps matching editions and preserves release metadata`() {
        val spotify = album(uri = "a", year = 1990, releaseGroup = "rg1", providerInstance = "spotify")
        val bandcamp = album(
            uri = "b",
            year = 2011,
            releaseGroup = "rg1",
            provider = "bandcamp",
            providerInstance = "bandcamp",
        )
        val release = groupArtistReleases(all = listOf(spotify, bandcamp), ownedAlbums = emptyList()).single()

        val filtered = filterArtistReleases(listOf(release), source = "bandcamp").single()

        assertEquals(listOf("b"), filtered.editions.map { it.uri })
        assertEquals("b", filtered.primary.uri)
        // Key, category and year describe the whole release, not the filtered subset.
        assertEquals(release.key, filtered.key)
        assertEquals(release.category, filtered.category)
        assertEquals(1990, filtered.year)
    }

    @Test
    fun `query filter matches name and version case-insensitively`() {
        val plain = album(uri = "a", name = "Plain", releaseGroup = "rg-a")
        val deluxe = album(uri = "b", name = "Other", version = "Deluxe", releaseGroup = "rg-b")
        val releases = groupArtistReleases(all = listOf(plain, deluxe), ownedAlbums = emptyList())

        assertEquals(listOf("Plain"), filterArtistReleases(releases, query = " PLAIN ").names())
        assertEquals(listOf("Other"), filterArtistReleases(releases, query = "deluxe").names())
        assertTrue(filterArtistReleases(releases, query = "nothing matches").isEmpty())
    }

    @Test
    fun `favorites filter uses the supplied predicate and drops empty releases`() {
        val favorite = album(uri = "a", releaseGroup = "rg-a")
        val notFavorite = album(uri = "b", releaseGroup = "rg-b")
        val releases = groupArtistReleases(all = listOf(favorite, notFavorite), ownedAlbums = emptyList())

        val filtered = filterArtistReleases(
            releases,
            favorites = true,
            isFavorite = { it.uri == "a" },
        )

        assertEquals(listOf("a"), filtered.flatMap { it.editions }.map { it.uri })
    }

    @Test
    fun `missing optional URI preserves distinct albums and owned identity`() {
        val owned = album("1", name = "Owned", provider = "library").copy(uri = null)
        val stream = album("2", name = "Stream", provider = "tidal").copy(uri = null)
        val releases = groupArtistReleases(listOf(owned, stream), listOf(owned))
        assertEquals(2, releases.size)
        assertEquals("Owned", releases.single { it.owned }.primary.name)
    }

    @Test
    fun `year bounds include endpoints and exclude unknown years until cleared`() {
        val releases = groupArtistReleases(listOf(
            album("a", name = "Early", year = 1990), album("b", name = "Middle", year = 2000),
            album("c", name = "Later", year = 2010), album("d", name = "Unknown"),
        ), emptyList())
        assertEquals(listOf("Middle"), filterArtistReleases(releases, fromYear = 2000, toYear = 2000).names())
        assertEquals(setOf("Middle", "Later"), filterArtistReleases(releases, fromYear = 2000).names().toSet())
        assertEquals(4, filterArtistReleases(releases).size)
    }

    private fun List<io.music_assistant.client.data.model.client.ArtistRelease>.names() =
        map { it.primary.name }
}
