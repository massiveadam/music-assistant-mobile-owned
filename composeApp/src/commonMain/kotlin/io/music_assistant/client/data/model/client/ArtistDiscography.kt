package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.Album

/**
 * Conservative artist-discography grouping, ported from the web client's
 * `helpers/artistDiscography.ts`. Display-only: the original [Album] instances and
 * their provider mappings are never mutated.
 *
 * Grouping is deliberately cautious — MusicBrainz release-group IDs are the only
 * strong link; title/artist/year matches are a fallback that never bridges
 * conflicting IDs, and ambiguous matches stay unmerged rather than risk a wrong join.
 * Live/remix/other distinct recordings are always kept as separate releases.
 */

enum class ReleaseSort { NEWEST, OLDEST, TITLE }

data class ArtistRelease(
    val key: String,
    val primary: Album,
    val editions: List<Album>,
    val owned: Boolean,
    val ownedUris: List<String>,
    /** Resolved [AlbumType], or null when unknown/conflicting ("Other" section). */
    val category: AlbumType?,
    val year: Int?,
)

/** Display order of the categorized sections; null is the "Other" bucket. */
val DISCOGRAPHY_CATEGORY_ORDER: List<AlbumType?> = listOf(
    AlbumType.ALBUM,
    AlbumType.EP,
    AlbumType.SINGLE,
    AlbumType.LIVE,
    AlbumType.COMPILATION,
    AlbumType.SOUNDTRACK,
    null,
)

/** Server `ExternalID.MB_RELEASEGROUP` value. */
private const val MB_RELEASE_GROUP = "musicbrainz_releasegroupid"

private val editionWords =
    Regex("\\b(deluxe|expanded|remaster(?:ed)?|anniversary|special edition|bonus tracks?)\\b", RegexOption.IGNORE_CASE)
private val distinctRecording =
    Regex("\\b(live|remix|mix|acoustic|instrumental|demo|re.?recorded)\\b", RegexOption.IGNORE_CASE)
private val trailingSuffix = Regex("""[\[(]([^()\[\]]+)[)\]]\s*$""")
private val liveInName = Regex("""[\[(]live\b""", RegexOption.IGNORE_CASE)
private val epLabel = Regex("(?:\\bEP|\\(EP\\)|\\[EP\\])\\s*$", RegexOption.IGNORE_CASE)
private val explicitEditionSuffix =
    Regex(
        """[\[(][^()\[\]]*(?:deluxe|expanded|remaster|anniversary|special edition|bonus tracks?)[^()\[\]]*[)\]]\s*$""",
        RegexOption.IGNORE_CASE,
    )

/**
 * NFKC + lowercase + strip non-alphanumerics in the original. Kotlin multiplatform has
 * no built-in NFKC normalizer (java.text.Normalizer is JVM-only), so compatibility
 * characters (ligatures, fullwidth forms) are not folded here; everything else matches.
 */
private fun normalize(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

fun editionLabel(album: Album): String? {
    album.version?.takeIf { it.isNotEmpty() }?.let { return it }
    val suffix = trailingSuffix.find(album.name)?.groupValues?.get(1)
    return suffix?.takeIf { editionWords.containsMatchIn(it) }
}

private fun baseTitle(album: Album): String = normalize(
    trailingSuffix.replace(album.name) { match ->
        val label = match.groupValues[1]
        if (editionWords.containsMatchIn(label) && !distinctRecording.containsMatchIn(label)) {
            ""
        } else {
            match.value
        }
    },
)

private fun recording(album: Album): String {
    if (album.albumType == AlbumType.LIVE || liveInName.containsMatchIn(album.name)) return "live"
    val version = album.version
    if (version.isNullOrEmpty() ||
        (editionWords.containsMatchIn(version) && !distinctRecording.containsMatchIn(version))
    ) {
        return ""
    }
    return normalize(version)
}

private fun identity(album: Album): String = album.uri?.takeIf { it.isNotBlank() } ?: "${album.provider}://album/${album.itemId}"

private fun groupIds(album: Album): List<String> =
    album.externalIds.mapNotNull { entry ->
        val type = entry.getOrNull(0)
        val id = entry.getOrNull(1)
        if (type == MB_RELEASE_GROUP && !id.isNullOrEmpty()) id.lowercase() else null
    }

fun releaseCategory(album: Album): AlbumType? =
    album.albumType
        // An explicit EP label is evidence; duration, track counts and vague titles are not.
        ?: if (epLabel.containsMatchIn(album.name)) AlbumType.EP else null

private fun fallbackMatch(a: Album, b: Album): Boolean {
    val aIds = groupIds(a)
    val bIds = groupIds(b)
    if (aIds.isNotEmpty() && bIds.isNotEmpty() && aIds.none { it in bIds }) return false
    if (baseTitle(a).isEmpty() || baseTitle(a) != baseTitle(b) || recording(a) != recording(b)) {
        return false
    }
    // Artist pages can include aliases and guest releases. A title alone is insufficient.
    val artists = { item: Album ->
        item.artists.map { normalize(it.name) }.sorted().joinToString("|")
    }
    if (a.artists.isEmpty() || artists(a) != artists(b)) return false
    val aType = releaseCategory(a)
    val bType = releaseCategory(b)
    if (aType != bType && aType != null && bType != null) return false
    val explicitEdition = { item: Album ->
        item.version?.let { editionWords.containsMatchIn(it) } == true ||
            explicitEditionSuffix.containsMatchIn(item.name)
    }
    return (a.year != null && a.year == b.year) || explicitEdition(a) || explicitEdition(b)
}

private fun makeRelease(editions: List<Album>, owned: Set<String>): ArtistRelease {
    val ranked = editions.sortedWith(
        compareBy<Album>(
            { if (identity(it) in owned) 0 else 1 },
            { if (it.provider == "library") 0 else 1 },
            { if (!it.version.isNullOrEmpty() || editionWords.containsMatchIn(it.name)) 1 else 0 },
            { it.uri ?: "" },
        ),
    )
    val primary = ranked.first()
    val years = editions.mapNotNull { album -> album.year?.takeIf { it > 0 } }
    val knownTypes = editions.mapNotNull(::releaseCategory).toSet()
    val ownedUris = editions.mapNotNull { album -> identity(album).takeIf(owned::contains) }
    return ArtistRelease(
        key = editions.map(::identity).sorted().joinToString("|"),
        primary = primary,
        editions = ranked,
        owned = ownedUris.isNotEmpty(),
        ownedUris = ownedUris,
        category = releaseCategory(primary) ?: knownTypes.singleOrNull(),
        year = years.minOrNull(),
    )
}

/** Group for display only. Original albums and their source mappings are never mutated. */
fun groupArtistReleases(all: List<Album>, ownedAlbums: List<Album>): List<ArtistRelease> {
    val owned = ownedAlbums.map(::identity).toSet()
    val albums = LinkedHashMap<String, Album>().apply {
        (ownedAlbums + all).forEach { put(identity(it), it) }
    }.values.toList()
    val groups = mutableListOf<MutableList<Album>>()
    val unlinked = mutableListOf<Album>()
    for (album in albums) {
        val ids = groupIds(album)
        if (ids.isEmpty()) {
            unlinked.add(album)
            continue
        }
        val group = groups.find { editions ->
            editions.all { other ->
                groupIds(other).any { it in ids } && recording(other) == recording(album)
            }
        }
        if (group != null) group.add(album) else groups.add(mutableListOf(album))
    }
    // Evaluate ambiguous matches before changing groups, so an unidentified edition
    // cannot bridge two conflicting release-group IDs, regardless of input order.
    val candidates = unlinked.map { album ->
        album to groups.filter { editions -> editions.all { fallbackMatch(album, it) } }
    }
    val orphanGroups = mutableListOf<MutableList<Album>>()
    for ((album, matches) in candidates) {
        val match = matches.singleOrNull()
        if (match != null && match.all { fallbackMatch(album, it) }) {
            match.add(album)
            continue
        }
        if (matches.size > 1) {
            orphanGroups.add(mutableListOf(album))
            continue
        }
        val orphan = orphanGroups.find { editions -> editions.all { fallbackMatch(album, it) } }
        if (orphan != null) orphan.add(album) else orphanGroups.add(mutableListOf(album))
    }
    return (groups + orphanGroups).map { makeRelease(it, owned) }
}

/** localeCompare(numeric: true) approximation: digit runs compare numerically. */
private fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var iEnd = i
            while (iEnd < a.length && a[iEnd].isDigit()) iEnd++
            var jEnd = j
            while (jEnd < b.length && b[jEnd].isDigit()) jEnd++
            val na = a.substring(i, iEnd).trimStart('0')
            val nb = b.substring(j, jEnd).trimStart('0')
            val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
            if (cmp != 0) return cmp
            i = iEnd
            j = jEnd
        } else {
            val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    if (i < a.length || j < b.length) return (a.length - i) - (b.length - j)
    return a.compareTo(b)
}

fun sortArtistReleases(releases: List<ArtistRelease>, order: ReleaseSort): List<ArtistRelease> =
    releases.sortedWith(
        Comparator { a, b ->
            if (order != ReleaseSort.TITLE) {
                if (a.year == null && b.year != null) return@Comparator 1
                if (b.year == null && a.year != null) return@Comparator -1
                val years = ((a.year ?: 0) - (b.year ?: 0)) * if (order == ReleaseSort.NEWEST) -1 else 1
                if (years != 0) return@Comparator years
            }
            naturalCompare(a.primary.name, b.primary.name).takeIf { it != 0 }
                ?: a.key.compareTo(b.key)
        },
    )

fun filterArtistReleases(
    releases: List<ArtistRelease>,
    source: String? = null,
    query: String = "",
    favorites: Boolean = false,
    isFavorite: (Album) -> Boolean = { it.favorite == true },
    fromYear: Int? = null,
    toYear: Int? = null,
): List<ArtistRelease> {
    val normalizedQuery = query.trim().lowercase()
    return releases.mapNotNull { release ->
        if ((fromYear != null && (release.year == null || release.year < fromYear)) ||
            (toYear != null && (release.year == null || release.year > toYear))) return@mapNotNull null
        val editions = release.editions.filter { album ->
            (source.isNullOrEmpty() ||
                album.providerMappings?.any { it.providerInstance == source } == true) &&
                (!favorites || isFavorite(album))
        }
        if (editions.isEmpty()) return@mapNotNull null
        if (normalizedQuery.isNotEmpty() &&
            editions.none { "${it.name} ${it.version ?: ""}".lowercase().contains(normalizedQuery) }
        ) {
            return@mapNotNull null
        }
        val selected = makeRelease(editions, release.ownedUris.toSet())
        selected.copy(
            key = release.key,
            owned = release.owned,
            category = release.category,
            year = release.year,
        )
    }
}
