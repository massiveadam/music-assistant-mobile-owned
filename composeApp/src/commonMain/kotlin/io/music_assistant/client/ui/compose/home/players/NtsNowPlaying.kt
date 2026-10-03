package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ktor.http.encodeURLParameter
import io.music_assistant.client.api.PersonalApi
import io.music_assistant.client.data.model.server.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun NtsNowPlaying(stationUri: String, playing: Boolean, compact: Boolean = false) {
    val api = koinInject<PersonalApi>()
    val connection by api.serviceClient.sessionState.collectAsStateWithLifecycle()
    val session = remember(connection) { api.session() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val foreground = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var feed by remember(stationUri, session) { mutableStateOf<NtsFeed?>(null) }
    var supported by remember(stationUri, session) { mutableStateOf(false) }
    var error by remember(stationUri, session) { mutableStateOf<String?>(null) }
    var dialog by remember(stationUri, session) { mutableStateOf(false) }
    var resolution by remember(stationUri, session) { mutableStateOf<NtsResolution?>(null) }
    var resolving by remember(stationUri, session) { mutableStateOf(false) }
    var sequence by remember(stationUri, session) { mutableStateOf(0) }

    LaunchedEffect(stationUri, session, playing, foreground) {
        sequence++
        resolution = null
        resolving = false
        feed = null
        supported = false
        error = null
        if (!playing || !foreground || session == null) { dialog = false; return@LaunchedEffect }
        while (true) {
            try {
                val result = parseNtsFeed(api.request("/credits/v1/nts-live?uri=${stationUri.encodeURLParameter()}", session = session), session.userId, stationUri)
                api.requireCurrent(session)
                feed = result; supported = result.status != "unsupported"; error = null
                if (result.status == "unsupported") return@LaunchedEffect
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                error = "NTS tracks are unavailable. Retrying shortly."
                feed = null
            }
            delay(15000)
        }
    }
    fun open(url: String) {
        if (ntsHttpsUrl(url)) runCatching { uriHandler.openUri(url) }.onFailure { error = "No app could open this link." }
    }
    fun resolve(track: NtsTrack) {
        val current = session ?: return
        val requestSequence = ++sequence
        resolving = true; resolution = null; error = null
        scope.launch {
            try {
                val data = api.request("/credits/v1/nts-resolve?uri=${stationUri.encodeURLParameter()}&startedAt=${track.startedAt.encodeURLParameter()}", session = current)
                api.requireCurrent(current)
                val result = parseNtsResolution(data, current.userId, stationUri, track.startedAt)
                if (requestSequence == sequence) resolution = result
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current.same(api.session()) && requestSequence == sequence) error = "Track lookup failed. Try again." }
            finally { if (current.same(api.session()) && requestSequence == sequence) resolving = false }
        }
    }
    val currentFeed = feed
    if ((currentFeed != null && currentFeed.status != "unsupported") || (supported && error != null)) {
        TextButton(onClick = { dialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 0.dp else 16.dp)) {
            Text(if (error != null) "NTS track identification is unavailable" else currentFeed?.label ?: "NTS tracks", style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                maxLines = if (compact) 1 else 2)
        }
    }
    if (dialog) {
        AlertDialog(onDismissRequest = { dialog = false; sequence++ }, title = { Text("NTS live tracks") },
            confirmButton = { TextButton(onClick = { dialog = false; sequence++ }) { Text("Close") } },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Live broadcast data from NTS. Your player may be behind the broadcast. Rare or unreleased tracks may be unidentified.")
                    if (currentFeed?.status == "disconnected") {
                        TextButton(onClick = { open("https://sanchez.drongo-banjo.ts.net:8443/credits/v1/nts-connect") }) { Text("Connect your supporter account") }
                    }
                    currentFeed?.tracks?.forEachIndexed { index, track ->
                        Text(if (index == 0) "Latest broadcast entry" else "Previously played", style = MaterialTheme.typography.labelMedium)
                        Text(track.label)
                        Text(track.startedAt, style = MaterialTheme.typography.labelSmall)
                        if (track.identified) TextButton(onClick = { resolve(track) }, enabled = !resolving) { Text("Find on my services") }
                    }
                    if (resolving) Text("Searching your connected music services…")
                    error?.let { Text(it) }
                    resolution?.let { result ->
                        Text(if (result.matches.isNotEmpty()) "Matching recordings. Choose the release you want."
                            else "No exact recording match. These are search results, so check the title and artist.")
                        if (result.matches.isEmpty() && result.alternatives.isEmpty()) Text("No matching track was found on your connected services.")
                        (result.matches + result.alternatives).forEach { match ->
                            HorizontalDivider()
                            Text("${match.artists.joinToString(", ")} · ${match.name} ${match.version}")
                            match.album?.let { Text(it) }
                            if (match.links.isEmpty()) Text("This result has no external service link.")
                            match.links.forEach { link -> TextButton(onClick = { open(link.url) }) { Text("Open ${link.service}") } }
                        }
                    }
                }
            })
    }
}
