package io.music_assistant.client.data

import io.music_assistant.client.ui.compose.personal.musicBrainzUrl
import io.music_assistant.client.ui.compose.personal.parseAlbumCredits
import io.music_assistant.client.ui.compose.personal.parseContributorCredits
import io.music_assistant.client.ui.compose.personal.parseReleaseCandidates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the /credits/v1 sidecar payloads consumed by AlbumCreditsPanel.
 * The panel must reject unknown statuses and malformed payloads rather than
 * fabricate credits, and must only ever open https://musicbrainz.org/ links.
 */
class AlbumCreditsContractTest {

    private fun payload(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `ready payload decodes with full credit fields`() {
        val result = parseAlbumCredits(
            payload(
                """
                {
                  "status": "ready",
                  "credits": [
                    {
                      "id": "artist-1",
                      "name": "Jane Doe",
                      "role": "performer",
                      "instrument": "violin",
                      "scope": "track",
                      "track": 3,
                      "title": "Song Three",
                      "source_url": "https://musicbrainz.org/recording/abc"
                    }
                  ],
                  "album": {
                    "uri": "library://album/1",
                    "name": "Album",
                    "provider": "library",
                    "item_id": "1",
                    "year": 1999,
                    "artist": "Someone",
                    "image": null
                  },
                  "release_id": "76df3287-6cda-33eb-8e9a-044b5e15ffdd",
                  "manual_release_id": null,
                  "matched_title": "Album (1999)",
                  "source_url": "https://musicbrainz.org/release/76df3287",
                  "stale": false
                }
                """,
            ),
        )
        assertEquals("ready", result.status)
        assertFalse(result.isUnresolved)
        assertEquals(1, result.credits.size)
        val credit = result.credits.first()
        assertEquals("artist-1", credit.id)
        assertEquals("violin", credit.instrument)
        assertEquals(3, credit.track)
        assertEquals("1", result.album.itemId)
        assertEquals("76df3287-6cda-33eb-8e9a-044b5e15ffdd", result.releaseId)
    }

    @Test
    fun `unresolved payload keeps empty credits without fabricating`() {
        val result = parseAlbumCredits(
            payload(
                """
                {
                  "status": "unresolved",
                  "credits": [],
                  "album": {
                    "uri": "library://album/2",
                    "name": "Mystery",
                    "provider": "library",
                    "item_id": "2"
                  }
                }
                """,
            ),
        )
        assertTrue(result.isUnresolved)
        assertTrue(result.credits.isEmpty())
        assertNull(result.matchedTitle)
        assertNull(result.manualReleaseId)
    }

    @Test
    fun `unknown status is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAlbumCredits(
                payload(
                    """
                    {
                      "status": "partial",
                      "credits": [],
                      "album": {
                        "uri": "library://album/3",
                        "name": "A",
                        "provider": "library",
                        "item_id": "3"
                      }
                    }
                    """,
                ),
            )
        }
    }

    @Test
    fun `malformed payload without status is rejected`() {
        assertFailsWith<Exception> {
            parseAlbumCredits(
                payload("""{"credits": [], "album": {"uri": "u", "name": "n", "provider": "p", "item_id": "i"}}"""),
            )
        }
    }

    @Test
    fun `credit missing required identity is rejected`() {
        assertFailsWith<Exception> {
            parseAlbumCredits(
                payload(
                    """
                    {
                      "status": "ready",
                      "credits": [
                        {"role": "producer", "scope": "release", "source_url": "https://musicbrainz.org/release/x"}
                      ],
                      "album": {"uri": "u", "name": "n", "provider": "p", "item_id": "i"}
                    }
                    """,
                ),
            )
        }
    }

    @Test
    fun `contributor payload decodes coverage`() {
        val result = parseContributorCredits(
            payload(
                """
                {
                  "id": "artist-1",
                  "name": "Jane Doe",
                  "credits": [
                    {
                      "role": "producer",
                      "scope": "release",
                      "source_url": "https://musicbrainz.org/release/y",
                      "album": {"uri": "u", "name": "n", "provider": "library", "item_id": "9"}
                    }
                  ],
                  "coverage": {"indexed": 4, "library_checked": 10, "complete": false}
                }
                """,
            ),
        )
        assertEquals("Jane Doe", result.name)
        assertEquals(4, result.coverage.indexed)
        assertEquals(10, result.coverage.libraryChecked)
        assertFalse(result.coverage.complete)
        assertEquals("9", result.credits.first().album.itemId)
    }

    @Test
    fun `contributor payload without coverage is rejected`() {
        assertFailsWith<Exception> {
            parseContributorCredits(
                payload("""{"id": "artist-1", "credits": []}"""),
            )
        }
    }

    @Test
    fun `candidates payload decodes release metadata`() {
        val candidates = parseReleaseCandidates(
            payload(
                """
                {
                  "candidates": [
                    {
                      "id": "rel-1",
                      "title": "Album (Deluxe)",
                      "artist": "Someone",
                      "date": "2001-05-01",
                      "country": "GB",
                      "track_count": 12,
                      "source_url": "https://musicbrainz.org/release/rel-1"
                    }
                  ]
                }
                """,
            ),
        )
        assertEquals(1, candidates.size)
        assertEquals(12, candidates.first().trackCount)
        assertEquals("GB", candidates.first().country)
    }

    @Test
    fun `musicBrainzUrl accepts only https musicbrainz links`() {
        assertEquals(
            "https://musicbrainz.org/release/rel-1",
            musicBrainzUrl("https://musicbrainz.org/release/rel-1"),
        )
        assertNull(musicBrainzUrl("http://musicbrainz.org/release/rel-1"))
        assertNull(musicBrainzUrl("https://evil.example.org/musicbrainz.org/"))
        assertNull(musicBrainzUrl("javascript:alert(1)"))
        assertNull(musicBrainzUrl("not a url"))
        assertNull(musicBrainzUrl(null))
    }
}
