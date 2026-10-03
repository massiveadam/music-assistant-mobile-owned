@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.server.*
import kotlin.time.Instant

/** Shared rendering for all editorial sources; data and album lookup stay in EditorialShelf. */
@Composable
fun EditorialShelfContent(
    source: EditorialSource, feed: EditorialFeed?, limit: Int, enabled: Boolean,
    loading: Boolean, error: String?, onLimit: (Int) -> Unit, onReload: () -> Unit,
    onSource: () -> Unit, onFind: (EditorialEntry) -> Unit, onReview: (EditorialEntry) -> Unit,
    artworkOverrides: Map<String, String> = emptyMap(),
) {
    var chooseLimit by remember(source.id) { mutableStateOf(false) }
    var showDetails by remember(source.id) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Keep badges and actions readable when Android's text size increases.
    val cardWidth = (164 * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(source.name, fontSize = 20.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(onClick = { chooseLimit = true }, modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text("$limit albums", style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(start = 4.dp).size(16.dp))
                        }
                        DropdownMenu(expanded = chooseLimit, onDismissRequest = { chooseLimit = false }) {
                            listOf(10, 20, 25, 50).forEach { count ->
                                DropdownMenuItem(text = { Text("$count albums") }, onClick = {
                                    onLimit(count); chooseLimit = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onReload, enabled = enabled && !loading, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Refresh, "Refresh ${source.name}", Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSource, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open ${source.provider} source", Modifier.size(19.dp))
                    }
                    IconButton(onClick = { showDetails = true }, enabled = feed != null, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Info, "About ${source.name}", Modifier.size(19.dp))
                    }
                }
                feed?.let { value ->
                    val order = when (value.window?.ranking) {
                        "critic" -> "Critic score order"; "user" -> "User score order"; else -> "Chart order"
                    }
                    Text(value.window?.let { "${it.start} to ${it.end} · $order" } ?: order,
                        style = MaterialTheme.typography.bodySmall, color = muted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    if (value.stale) Text("Older snapshot. Refresh for the latest available list.",
                        style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            when {
                !enabled -> Text("Enable this row to see albums and scores.", modifier = Modifier.padding(16.dp))
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                loading && feed == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                feed?.entries?.isEmpty() == true -> Text(
                    if (feed.window?.dateField == "review_date") "No Best New Music reviews from the last seven days in the current snapshot."
                    else "No releases from the last seven days in the current snapshot.", modifier = Modifier.padding(16.dp))
                else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(feed?.entries.orEmpty().take(limit), key = { "${it.artist}:${it.title}" }) { entry ->
                        EditorialAlbumCard(entry, Modifier.width(cardWidth), onFind = { onFind(entry) },
                            onReview = { onReview(entry) }, artworkOverride = artworkOverrides[entry.url])
                    }
                }
            }
        }
        if (showDetails && feed != null) AlertDialog(
            onDismissRequest = { showDetails = false }, title = { Text(source.name) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (feed.window?.dateField == "review_date") "Best New Music reviews published in the last seven days."
                        else "Albums released in the last seven days.")
                    val captured = feed.fetchedAt?.let {
                        runCatching { Instant.fromEpochSeconds(it.toLong()).toString().take(10) }.getOrNull()
                    }
                    Text(captured?.let { "Updated $it." } ?: "Update date unavailable.")
                    if (feed.note.isNotBlank()) Text(feed.note)
                }
            }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Close") } },
        )
    }
}

@Composable
fun EditorialAlbumCard(entry: EditorialEntry, modifier: Modifier = Modifier, onFind: () -> Unit, onReview: () -> Unit,
    artworkOverride: String? = null,
) {
    var failedArtwork by remember(entry.url, entry.image) { mutableStateOf<Set<String>>(emptySet()) }
    val artwork = listOfNotNull(artworkOverride, entry.image).distinct().firstOrNull { it !in failedArtwork }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val singleBadgeWidth = (104 * LocalDensity.current.fontScale).dp
    Column(modifier.testTag("editorial-card:${entry.title}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).testTag("editorial-cover:${entry.title}").clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(entry.title.take(1), fontSize = 56.sp, color = muted.copy(alpha = .5f))
            AsyncImage(model = artwork, contentDescription = "${entry.title} by ${entry.artist}",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onError = { state ->
                    // The failed request may predate a newly arrived catalog cover.
                    (state.result.request.data as? String)?.let { failedArtwork = failedArtwork + it }
                })
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                entry.scores.critic?.let { EditorialScoreBadge("Critics", it, "reviews",
                    if (entry.scores.user != null) Modifier.weight(1f) else Modifier.widthIn(max = singleBadgeWidth)) }
                entry.scores.user?.let { EditorialScoreBadge("Users", it, "ratings",
                    if (entry.scores.critic != null) Modifier.weight(1f) else Modifier.widthIn(max = singleBadgeWidth)) }
                if (entry.scores.critic == null && entry.scores.user == null) {
                    Surface(color = Color.Black.copy(alpha = .85f), contentColor = Color.White, shape = RoundedCornerShape(6.dp)) {
                        Text("No score", fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
        Text(entry.title, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(entry.artist, fontSize = 12.sp, lineHeight = 17.sp, color = muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        Text(entry.releaseDate?.let { "Released $it" } ?: " ", fontSize = 10.sp, lineHeight = 15.sp,
            color = muted, minLines = 1, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = onFind, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("editorial-find:${entry.title}"),
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                Text("Find album", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedIconButton(onClick = onReview, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Read review for ${entry.title}", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditorialScoreBadge(label: String, score: EditorialScore, defaultCountLabel: String, modifier: Modifier) {
    Surface(modifier, color = Color(0xFF08080C).copy(alpha = .9f), contentColor = Color.White,
        shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .2f))) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 5.dp)) {
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = Color.White.copy(alpha = .75f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(score.value.toString().removeSuffix(".0"), fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("/${score.max.toString().removeSuffix(".0")}", fontSize = 9.sp, lineHeight = 14.sp, color = Color.White.copy(alpha = .7f))
            }
            score.count?.let { count ->
                val number = count.toString().reversed().chunked(3).joinToString(",").reversed()
                val countLabel = score.countLabel ?: defaultCountLabel
                Text("$number ${if (count == 1L) countLabel.removeSuffix("s") else countLabel}", fontSize = 9.sp,
                    lineHeight = 12.sp, color = Color.White.copy(alpha = .8f), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
