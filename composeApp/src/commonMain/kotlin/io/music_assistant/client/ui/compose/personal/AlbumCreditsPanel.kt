package io.music_assistant.client.ui.compose.personal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.PersonalApi
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.CenteredText
import io.music_assistant.client.ui.compose.common.ConfirmationDialog
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import org.koin.compose.koinInject

// ---------------------------------------------------------------------------
// DTOs — contract with the authenticated /credits/v1 sidecar, mirroring the
// web client's useCredits.ts schemas. Nothing is fabricated client-side:
// only server-returned credits are ever displayed.
// ---------------------------------------------------------------------------

@Serializable
data class Credit(
    val id: String,
    val name: String,
    val role: String,
    val instrument: String? = null,
    val scope: String,
    val track: Int? = null,
    val title: String? = null,
    @SerialName("source_url") val sourceUrl: String,
)

@Serializable
data class CreditAlbum(
    val uri: String,
    val name: String,
    val provider: String,
    @SerialName("item_id") val itemId: String,
    val year: Int? = null,
    val artist: String? = null,
    val image: String? = null,
)

@Serializable
data class AlbumCredits(
    val status: String,
    val credits: List<Credit>,
    val album: CreditAlbum,
    @SerialName("release_id") val releaseId: String? = null,
    @SerialName("manual_release_id") val manualReleaseId: String? = null,
    @SerialName("matched_title") val matchedTitle: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    val stale: Boolean = false,
    val warning: String? = null,
) {
    val isUnresolved: Boolean get() = status == STATUS_UNRESOLVED

    companion object {
        const val STATUS_READY = "ready"
        const val STATUS_UNRESOLVED = "unresolved"
    }
}

@Serializable
data class CreditCoverage(
    val indexed: Int,
    @SerialName("library_checked") val libraryChecked: Int,
    val complete: Boolean,
)

@Serializable
data class ContributorAlbumCredit(
    val role: String,
    val instrument: String? = null,
    val scope: String,
    val track: Int? = null,
    val title: String? = null,
    @SerialName("source_url") val sourceUrl: String,
    val album: CreditAlbum,
)

@Serializable
data class ContributorCredits(
    val id: String,
    val name: String? = null,
    val credits: List<ContributorAlbumCredit>,
    val coverage: CreditCoverage,
)

@Serializable
data class ReleaseCandidate(
    val id: String,
    val title: String,
    val artist: String? = null,
    val date: String? = null,
    val country: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    @SerialName("source_url") val sourceUrl: String,
)

@Serializable
private data class CandidatesPayload(val candidates: List<ReleaseCandidate>)

/** Parses an /album response, rejecting payloads with an unrecognised status. */
fun parseAlbumCredits(payload: JsonObject): AlbumCredits {
    val parsed = myJson.decodeFromJsonElement<AlbumCredits>(payload)
    require(
        parsed.status == AlbumCredits.STATUS_READY ||
            parsed.status == AlbumCredits.STATUS_UNRESOLVED,
    ) { "Unknown credits status." }
    return parsed
}

/** Parses a /contributor response. Coverage is required so partial discographies are shown honestly. */
fun parseContributorCredits(payload: JsonObject): ContributorCredits =
    myJson.decodeFromJsonElement<ContributorCredits>(payload)

/** Parses a /candidates response. */
fun parseReleaseCandidates(payload: JsonObject): List<ReleaseCandidate> =
    myJson.decodeFromJsonElement<CandidatesPayload>(payload).candidates

/**
 * Only https://musicbrainz.org/ links from the sidecar may be opened. Anything else
 * (other hosts, non-TLS, non-URL garbage) is dropped instead of handed to the OS.
 */
fun musicBrainzUrl(raw: String?): String? =
    raw?.trim()?.takeIf { it.startsWith("https://musicbrainz.org/") }

// ---------------------------------------------------------------------------
// State holder — one per (album uri, authenticated session). When the account
// or server changes the whole holder is discarded, so private credits from a
// previous session can never linger on screen.
// ---------------------------------------------------------------------------

private const val ALBUM_PATH = "/credits/v1/album"
private const val CONTRIBUTOR_PATH = "/credits/v1/contributor"
private const val CANDIDATES_PATH = "/credits/v1/candidates"

private data class CreditsUiState(
    val loading: Boolean = false,
    val result: AlbumCredits? = null,
    val error: String? = null,
    val candidates: List<ReleaseCandidate> = emptyList(),
    val searching: Boolean = false,
)

private data class ContributorUiState(
    val id: String,
    val loading: Boolean = true,
    val result: ContributorCredits? = null,
    val error: String? = null,
)

private class AlbumCreditsController(
    private val api: PersonalApi,
    private val session: PersonalApi.Session?,
    private val uri: String,
) {
    var state by mutableStateOf(CreditsUiState())
        private set
    var contributor by mutableStateOf<ContributorUiState?>(null)
        private set
    private var generation = 0L

    fun cancel() {
        generation++
        contributor = null
    }

    fun load(
        scope: CoroutineScope,
        refresh: Boolean = false,
        releaseId: String? = null,
        clearMatch: Boolean = false,
    ) {
        val active = session ?: run {
            state = state.copy(error = "Sign in to Music Assistant to use credits.")
            return
        }
        val explicit = refresh || releaseId != null || clearMatch
        if (!explicit && (state.loading || state.result != null)) return
        val gen = ++generation
        scope.launch {
            commit(gen, active) { copy(loading = true, error = null) }
            try {
                val body = buildJsonObject {
                    put("uri", uri)
                    if (refresh) put("refresh", true)
                    releaseId?.let { put("release_id", it) }
                    if (clearMatch) put("clear_match", true)
                }
                val parsed = parseAlbumCredits(api.request(ALBUM_PATH, body, active))
                commit(gen, active) {
                    copy(
                        loading = false,
                        result = parsed,
                        candidates = if (explicit) emptyList() else candidates,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                commit(gen, active) {
                    copy(loading = false, error = failure.message ?: "Credits could not be loaded.")
                }
            }
        }
    }

    fun findCandidates(scope: CoroutineScope) {
        val active = session ?: return
        val gen = ++generation
        scope.launch {
            commit(gen, active) { copy(searching = true, error = null) }
            try {
                val body = buildJsonObject { put("uri", uri) }
                val parsed = parseReleaseCandidates(api.request(CANDIDATES_PATH, body, active))
                commit(gen, active) { copy(searching = false, candidates = parsed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                commit(gen, active) {
                    copy(searching = false, error = failure.message ?: "Credits could not be loaded.")
                }
            }
        }
    }

    fun openContributor(scope: CoroutineScope, id: String) {
        val active = session ?: return
        val gen = generation
        contributor = ContributorUiState(id)
        scope.launch {
            try {
                val body = buildJsonObject { put("id", id) }
                val parsed = parseContributorCredits(api.request(CONTRIBUTOR_PATH, body, active))
                if (gen == generation && active.same(api.session()) && contributor?.id == id) {
                    contributor = ContributorUiState(id, loading = false, result = parsed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (gen == generation && contributor?.id == id) {
                    contributor = ContributorUiState(
                        id = id,
                        loading = false,
                        error = failure.message ?: "Could not load contributor.",
                    )
                }
            }
        }
    }

    fun retryContributor(scope: CoroutineScope) {
        contributor?.let { openContributor(scope, it.id) }
    }

    fun closeContributor() {
        contributor = null
    }

    /** Commits only when the request generation and authenticated session are both still current. */
    private inline fun commit(
        gen: Long,
        session: PersonalApi.Session,
        crossinline transform: CreditsUiState.() -> CreditsUiState,
    ) {
        if (gen != generation || !session.same(api.session())) return
        state = state.transform()
    }
}

// ---------------------------------------------------------------------------
// Grouping — same role/contributor dedup and summary rules as AlbumCredits.vue.
// ---------------------------------------------------------------------------

private data class ContributorTrack(
    val number: Int?,
    val title: String?,
    val scope: String,
    val instrument: String?,
    val sourceUrl: String?,
)

private data class GroupedContributor(
    val id: String,
    val name: String,
    val summary: String,
    val tracks: List<ContributorTrack>,
    val hasTrackDetail: Boolean,
)

private data class RoleGroup(val role: String, val contributors: List<GroupedContributor>)

private fun buildRoleGroups(credits: List<Credit>): List<RoleGroup> {
    if (credits.isEmpty()) return emptyList()
    return credits.groupBy { it.role }.map { (role, roleCredits) ->
        val contributors = roleCredits.groupBy { it.id }.map { (id, own) ->
            val instruments = own
                .mapNotNull { it.instrument?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
            val trackNumbers = own.mapNotNull { it.track }.distinct().sorted()
            val isRelease = own.any { it.scope == "release" }
            val trackSummary = when {
                trackNumbers.size == 1 -> "Track ${trackNumbers.first()}"
                trackNumbers.size in 2..4 -> "Tracks ${trackNumbers.joinToString(", ")}"
                trackNumbers.size > 4 -> "${trackNumbers.size} tracks"
                isRelease -> "Album credit"
                else -> "${own.size} credits"
            }
            val tracks = own.map {
                ContributorTrack(it.track, it.title, it.scope, it.instrument, it.sourceUrl)
            }
            GroupedContributor(
                id = id,
                name = own.first().name,
                summary = listOfNotNull(
                    instruments.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    trackSummary,
                ).joinToString(" · "),
                tracks = tracks,
                hasTrackDetail = tracks.any { it.number != null || it.title != null },
            )
        }.sortedBy { it.name.lowercase() }
        RoleGroup(role, contributors)
    }.sortedBy { it.role.lowercase() }
}

private sealed interface PendingConfirm {
    data class SaveRelease(val releaseId: String) : PendingConfirm
    data class ChooseCandidate(val candidate: ReleaseCandidate) : PendingConfirm
    data object ClearMatch : PendingConfirm
}

// ---------------------------------------------------------------------------
// Panel
// ---------------------------------------------------------------------------

/**
 * Collapsed "Credits" button that expands into the album's MusicBrainz credits:
 * contributors grouped by role with instrument and track scope, a manual release
 * picker (candidates, custom release ID, clear override, refresh) when the album
 * is unresolved, and a contributor bottom sheet listing the person's credited
 * library albums. [onNavigateToAlbum] receives (itemId, provider).
 */
@Composable
fun AlbumCreditsPanel(
    album: Album,
    onNavigateToAlbum: (itemId: String, provider: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uri = album.uri ?: return
    val api: PersonalApi = koinInject()
    val sessionState by api.serviceClient.sessionState.collectAsStateWithLifecycle()
    val session = remember(sessionState) { api.session() }
    val sessionKey = session?.let {
        "${it.serverId} ${it.userId} ${it.origin} ${it.remoteId} ${it.token.hashCode()}"
    }
    val controller = remember(uri, sessionKey) { AlbumCreditsController(api, session, uri) }
    DisposableEffect(controller) { onDispose { controller.cancel() } }

    var expanded by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(controller, expanded) {
        if (expanded) controller.load(this)
    }

    if (!expanded) {
        OutlinedButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Credits")
        }
        return
    }

    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val state = controller.state
    val result = state.result
    var showReleaseDialog by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var releaseIdInput by remember { mutableStateOf("") }
    var expandedTracks by remember { mutableStateOf(setOf<String>()) }
    val openSource: (String?) -> Unit = { raw ->
        musicBrainzUrl(raw)?.let { url -> runCatching { uriHandler.openUri(url) } }
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Credits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                result?.credits?.size?.takeIf { it > 0 }?.let { count ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showReleaseDialog = true }) {
                    Text("Release details")
                }
                IconButton(onClick = { expanded = false }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Collapse credits",
                    )
                }
            }

            result?.matchedTitle?.let { matched ->
                Text(
                    text = "Matched release: $matched",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.loading && result == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }

            state.error?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { controller.load(scope) }) {
                        Text("Retry")
                    }
                }
            }

            result?.warning?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (result?.isUnresolved == true) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "No MusicBrainz release is matched to this album.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Search for matching releases on MusicBrainz or enter a " +
                            "release ID to view verified credits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { controller.findCandidates(scope) },
                            enabled = !state.searching,
                        ) {
                            Text(if (state.searching) "Searching…" else "Find matching release")
                        }
                        TextButton(onClick = { showReleaseDialog = true }) {
                            Text("Enter release ID")
                        }
                    }
                }
            }

            if (state.candidates.isNotEmpty()) {
                Text(
                    text = "Possible MusicBrainz releases",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                state.candidates.forEach { candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = listOfNotNull(
                                    candidate.artist,
                                    candidate.date,
                                    candidate.trackCount?.let { "$it tracks" },
                                    candidate.country,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        musicBrainzUrl(candidate.sourceUrl)?.let {
                            TextButton(onClick = { openSource(candidate.sourceUrl) }) {
                                Text("View ↗")
                            }
                        }
                        TextButton(
                            onClick = { pendingConfirm = PendingConfirm.ChooseCandidate(candidate) },
                        ) {
                            Text("Use this release")
                        }
                    }
                }
            }

            val groups = remember(result?.credits) {
                result?.credits?.let(::buildRoleGroups).orEmpty()
            }
            if (groups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                groups.forEach { group ->
                    Text(
                        text = group.role.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    group.contributors.forEach { contributor ->
                        val trackKey = "${group.role}:${contributor.id}"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { controller.openContributor(scope, contributor.id) }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = contributor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = contributor.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (contributor.tracks.size > 1 || contributor.hasTrackDetail) {
                                TextButton(
                                    onClick = {
                                        expandedTracks = expandedTracks.let {
                                            if (trackKey in it) it - trackKey else it + trackKey
                                        }
                                    },
                                ) {
                                    Text(
                                        if (trackKey in expandedTracks) {
                                            "Hide tracks"
                                        } else {
                                            "Show tracks"
                                        },
                                    )
                                }
                            }
                            if (trackKey in expandedTracks) {
                                contributor.tracks.forEach { track ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                                    ) {
                                        Text(
                                            text = buildString {
                                                track.number?.let { append("Track $it") }
                                                if (track.number != null && track.title != null) {
                                                    append(": ")
                                                }
                                                track.title?.let { append(it) }
                                                listOfNotNull(
                                                    track.instrument,
                                                    if (track.scope == "release") {
                                                        "Album release"
                                                    } else {
                                                        null
                                                    },
                                                ).forEach {
                                                    if (isNotEmpty()) append(" · ")
                                                    append(it)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        musicBrainzUrl(track.sourceUrl)?.let {
                                            Text(
                                                text = "↗",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clickable { openSource(track.sourceUrl) }
                                                    .padding(4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (result != null && !result.isUnresolved && !state.loading) {
                Text(
                    text = "No credits are available for this release.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    if (showReleaseDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseDialog = false },
            title = { Text("Release match & source") },
            text = {
                Column {
                    result?.matchedTitle?.let { matched ->
                        Text(
                            text = "Matched MusicBrainz release",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = matched,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        musicBrainzUrl(result.sourceUrl)?.let {
                            Text(
                                text = "Open release in MusicBrainz ↗",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { openSource(result.sourceUrl) }
                                    .padding(vertical = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    result?.releaseId?.let { releaseId ->
                        Text(
                            text = "Release ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = releaseId,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Custom MusicBrainz release ID",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Provide a specific release UUID to override the automatic match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    OutlinedTextField(
                        value = releaseIdInput,
                        onValueChange = { releaseIdInput = it },
                        label = { Text("MusicBrainz release UUID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                pendingConfirm = PendingConfirm.SaveRelease(releaseIdInput.trim())
                                showReleaseDialog = false
                            },
                            enabled = releaseIdInput.isNotBlank(),
                        ) {
                            Text("Save release")
                        }
                        if (result?.manualReleaseId != null) {
                            TextButton(
                                onClick = {
                                    pendingConfirm = PendingConfirm.ClearMatch
                                    showReleaseDialog = false
                                },
                            ) {
                                Text("Clear saved override")
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                controller.findCandidates(scope)
                                showReleaseDialog = false
                            },
                        ) {
                            Text("Search candidate releases")
                        }
                        TextButton(
                            onClick = {
                                controller.load(scope, refresh = true)
                                showReleaseDialog = false
                            },
                        ) {
                            Text("Refresh credits")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    pendingConfirm?.let { confirm ->
        val (title, message) = when (confirm) {
            is PendingConfirm.SaveRelease ->
                "Save MusicBrainz release" to
                    "Save this MusicBrainz release for this album? You can clear it later."
            is PendingConfirm.ChooseCandidate ->
                "Use this release" to
                    "Use “${confirm.candidate.title}” as the MusicBrainz release for this " +
                    "album? You can clear it later."
            PendingConfirm.ClearMatch ->
                "Clear release match" to
                    "Clear the saved MusicBrainz release and retry the album’s own match?"
        }
        ConfirmationDialog(
            title = title,
            message = message,
            confirmLabel = "Confirm",
            onConfirm = {
                when (confirm) {
                    is PendingConfirm.SaveRelease ->
                        controller.load(scope, refresh = true, releaseId = confirm.releaseId)
                    is PendingConfirm.ChooseCandidate ->
                        controller.load(scope, refresh = true, releaseId = confirm.candidate.id)
                    PendingConfirm.ClearMatch ->
                        controller.load(scope, refresh = true, clearMatch = true)
                }
            },
            onDismiss = { pendingConfirm = null },
        )
    }

    controller.contributor?.let { contributorState ->
        ContributorSheet(
            state = contributorState,
            onRetry = { controller.retryContributor(scope) },
            onOpenAlbum = { creditAlbum ->
                controller.closeContributor()
                onNavigateToAlbum(creditAlbum.itemId, creditAlbum.provider)
            },
            onDismiss = controller::closeContributor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributorSheet(
    state: ContributorUiState,
    onRetry: () -> Unit,
    onOpenAlbum: (CreditAlbum) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = state.result?.name ?: "Contributor",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
            when {
                state.loading -> CenteredProgress()
                state.error != null -> Column {
                    CenteredText(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Retry")
                    }
                }
                state.result != null -> ContributorContent(state.result, onOpenAlbum)
            }
        }
    }
}

@Composable
private fun ContributorContent(
    result: ContributorCredits,
    onOpenAlbum: (CreditAlbum) -> Unit,
) {
    val albumGroups = remember(result) {
        result.credits
            .groupBy { it.album.uri }
            .map { (_, credits) -> credits.first().album to credits }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp, end = 24.dp, bottom = 16.dp,
        ),
    ) {
        item {
            Text(
                text = "${albumGroups.size} " +
                    (if (albumGroups.size == 1) "album" else "albums") +
                    " in library · ${result.credits.size} " +
                    (if (result.credits.size == 1) "credit" else "credits"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        albumGroups.forEach { (creditAlbum, credits) ->
            item(key = creditAlbum.uri) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAlbum(creditAlbum) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = creditAlbum.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    listOfNotNull(creditAlbum.artist, creditAlbum.year?.toString())
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    credits.forEach { credit ->
                        Text(
                            text = listOfNotNull(
                                credit.instrument?.let { "${credit.role} ($it)" }
                                    ?: credit.role,
                                credit.track?.let { "Track $it" },
                                credit.title,
                                if (credit.scope == "release") "Album release" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Credits reflect ${result.coverage.indexed} of " +
                    "${result.coverage.libraryChecked} indexed library albums. " +
                    "Discography may be partial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
