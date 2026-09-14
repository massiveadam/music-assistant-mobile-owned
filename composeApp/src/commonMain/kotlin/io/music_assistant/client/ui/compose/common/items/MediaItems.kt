// Compose layout values (sizes, alphas) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.items.isOwnedItem
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.GenreIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.painters.rememberVinylRecordPainter
import io.music_assistant.client.data.model.client.AlbumType
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.theme.favoriteTint
import io.music_assistant.client.utils.gridItemMinSize
import io.music_assistant.client.utils.rowImageSize
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_album_item
import musicassistantclient.composeapp.generated.resources.cd_favorite
import musicassistantclient.composeapp.generated.resources.cd_fully_played
import musicassistantclient.composeapp.generated.resources.cd_in_progress
import musicassistantclient.composeapp.generated.resources.cd_vinyl_record
import org.jetbrains.compose.resources.stringResource

/** Opacity applied to media items that can't be played (server `is_playable == false`). */
internal const val DISABLED_ITEM_ALPHA = 0.3f

/**
 * Artist media item with circular image.
 *
 * @param item The artist item to display
 * @param onClick Click handler
 */
@Composable
fun ArtistGridItem(
    modifier: Modifier = Modifier,
    item: Artist,
    onClick: (Artist) -> Unit,
    onLongClick: (Artist) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            ArtistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArtistImage(
    item: Artist,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = ArtistIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Album media item with vinyl record design.
 *
 * @param item The album item to display
 * @param onClick Click handler
 */
@Composable
fun AlbumGridItem(
    modifier: Modifier = Modifier,
    item: Album,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(6.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            isOwned = false,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun AlbumImage(
    item: Album,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = AlbumIcon,
        )

        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Playlist media item.
 *
 * @param item The playlist item to display
 * @param onClick Click handler

 */
@Composable
fun PlaylistGridItem(
    modifier: Modifier = Modifier,
    item: Playlist,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PlaylistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaylistImage(
    item: Playlist,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = PlaylistIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun PodcastGridItem(
    modifier: Modifier = Modifier,
    item: Podcast,
    onClick: (Podcast) -> Unit,
    onLongClick: (Podcast) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PodcastImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PodcastImage(
    item: Podcast,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Podcasts,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Track media item with waveform overlay.
 *
 * @param item The track item to display
 * @param onClick Click handler

 */
@Composable
internal fun TrackGridItem(
    modifier: Modifier = Modifier,
    item: Track,
    onClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            TrackImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
private fun TrackImage(
    item: PlayableItem,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = TrackIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )


    }
}

@Composable
internal fun PodcastEpisodeGridItem(
    modifier: Modifier = Modifier,
    item: PodcastEpisode,
    onClick: (PodcastEpisode) -> Unit,
    onLongClick: (PodcastEpisode) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PodcastEpisodeImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
private fun PodcastEpisodeImage(
    item: PlayableItem,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Podcasts,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Draw concentric circles from bottom center
        val circleCount = 8
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val bottomCenter = Offset(size.width / 2f, size.height)
            val maxRadius = size.height / 2f
            val spacing = maxRadius / circleCount

            for (i in 1..circleCount) {
                val alpha = 1f - (i.toFloat() / circleCount) // Fade as circles get bigger
                if (alpha > 0f) {
                    drawCircle(
                        color = primary.copy(alpha = alpha),
                        radius = i * spacing,
                        center = bottomCenter,
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}

/**
 * Radio station media item with wavy octagon shape.
 *
 * @param item The radio station item to display
 * @param onClick Click handler

 */
@Composable
internal fun RadioGridItem(
    modifier: Modifier = Modifier,
    item: RadioStation,
    onClick: (RadioStation) -> Unit,
    onLongClick: (RadioStation) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            RadioImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
private fun RadioImage(
    item: PlayableItem,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = RadioIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Audiobook media item with book spine design.
 *
 * @param item The audiobook item to display
 * @param onClick Click handler

 */
@Composable
internal fun AudiobookGridItem(
    modifier: Modifier = Modifier,
    item: Audiobook,
    onClick: (Audiobook) -> Unit,
    onLongClick: (Audiobook) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            AudiobookImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AudiobookImage(
    item: Audiobook,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = BookAudioIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun GridPlayableItemLabels(item: PlayableItem) {
    val subtitleText = (item as? AppMediaItem)?.localizedSubtitle()
    Spacer(Modifier.height(4.dp))
    MediaItemLabels(
        title = "${item.displayName}${
            item.version
                ?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        }",
        subtitle = subtitleText,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Modifier.gridCellWidth(): Modifier {
    val minSize = gridItemMinSize()
    return this.layout { measurable, constraints ->
        val targetConstraints = if (constraints.hasBoundedWidth) {
            constraints
        } else {
            val width = minSize.roundToPx()
            constraints.copy(minWidth = width, maxWidth = width)
        }
        val placeable = measurable.measure(targetConstraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

/**
 * Common wrapper for media items with click handling.
 */
@Composable
private fun GridItem(
    modifier: Modifier = Modifier,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .gridCellWidth()
            .clearAndSetSemantics {
                contentDescription = description
            }
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        content()
    }
}

@Composable
fun BoxScope.Badges(
    item: AppMediaItem,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    badgeSize: Dp = 16.dp,
    badgePadding: Dp = 0.dp,
    showOwned: Boolean = true,
) {
    val modifier = Modifier.padding(badgePadding).size(badgeSize)
    val bottomEnd = modifier.align(Alignment.BottomEnd)
    if (item.favorite == true) {
        Icon(
            modifier = bottomEnd,
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(Res.string.cd_favorite),
            tint = favoriteTint,
        )
    } else {
        providerIconFetcher?.invoke(
            bottomEnd.background(Color.Gray, CircleShape),
            item.provider,
        )
    }
    if (item.isExplicit) {
        Icon(
            modifier = modifier.align(Alignment.TopEnd)
                .background(Color.White, RoundedCornerShape(2.dp)),
            imageVector = Icons.Filled.Explicit,
            contentDescription = "Explicit",
            tint = Color.Black,
        )
    }
    if (showOwned && item.isOwnedItem) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(3.dp),
                )
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(3.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = "OWNED",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                lineHeight = 9.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Progress indicator badge for audiobooks and podcast episodes.
 * Shows a checkmark for fully played items, or a clock for in-progress items.
 * Positioned at top-end of the image Box (bottom-end is used by Badges).
 */
@Composable
fun BoxScope.ProgressBadge(
    fullyPlayed: Boolean?,
    resumePositionMs: Long?,
) {
    when {
        fullyPlayed == true -> {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        CircleShape,
                    )
                    .padding(2.dp),
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(Res.string.cd_fully_played),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        resumePositionMs != null && resumePositionMs > 0 -> {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        CircleShape,
                    )
                    .padding(2.dp),
                imageVector = Icons.Default.Schedule,
                contentDescription = stringResource(Res.string.cd_in_progress),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

// ── Row layout ────────────────────────────────────────────────────────────────

@Composable
internal fun TrackRowItem(
    modifier: Modifier = Modifier,
    item: Track,
    showTrackNumber: Boolean,
    onClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        isOwned = item.isOwnedItem,
        prefixContent = if (showTrackNumber) {
            item.trackNumber?.toString()?.let { trackNumber ->
                {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = trackNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            {
                TrackImage(item)
                Badges(
                    item = item,
                    providerIconFetcher = providerIconFetcher,
                    showOwned = false,
                )
            }
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun AlbumRowItem(
    modifier: Modifier = Modifier,
    item: Album,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        isOwned = item.isOwnedItem,
        prefixContent = {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
                showOwned = false,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun ArtistRowItem(
    modifier: Modifier = Modifier,
    item: Artist,
    onClick: (Artist) -> Unit,
    onLongClick: (Artist) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        isOwned = item.isOwnedItem,
        prefixContent = {
            ArtistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
                showOwned = false,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun PlaylistRowItem(
    modifier: Modifier = Modifier,
    item: Playlist,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        isOwned = item.isOwnedItem,
        prefixContent = {
            PlaylistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
                showOwned = false,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun PodcastRowItem(
    modifier: Modifier = Modifier,
    item: Podcast,
    onClick: (Podcast) -> Unit,
    onLongClick: (Podcast) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = {
            PodcastImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun PodcastEpisodeRowItem(
    modifier: Modifier = Modifier,
    item: PodcastEpisode,
    onClick: (PodcastEpisode) -> Unit,
    onLongClick: (PodcastEpisode) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = {
            PodcastEpisodeImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun RadioRowItem(
    modifier: Modifier = Modifier,
    item: RadioStation,
    onClick: (RadioStation) -> Unit,
    onLongClick: (RadioStation) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = {
            RadioImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
fun GenreGridItem(
    modifier: Modifier = Modifier,
    item: Genre,
    onClick: (Genre) -> Unit,
    onLongClick: (Genre) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            GenreImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GenreImage(
    item: Genre,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = GenreIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun GenreRowItem(
    modifier: Modifier = Modifier,
    item: Genre,
    onClick: (Genre) -> Unit,
    onLongClick: (Genre) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = {
            GenreImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

/**
 * Browse-only cell for a [RecommendationFolder]: icon + name, no long-press menu — tapping it
 * navigates one level deeper. Folders never appear in library lists, so this is exercised only
 * by the Browse screen.
 */
@Composable
fun FolderCell(
    item: RecommendationFolder,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (RecommendationFolder) -> Unit,
) {
    when (viewMode) {
        ViewMode.LIST -> RowItem(
            modifier = Modifier.fillMaxWidth(),
            name = item.displayName,
            subtitle = null,
            description = contentDescription(item),
            prefixContent = { FolderImage(item) },
            onClick = { onNavigateClick(item) },
            onLongClick = {},
        )

        ViewMode.GRID -> GridItem(
            description = contentDescription(item),
            onClick = { onNavigateClick(item) },
            onLongClick = {},
        ) {
            FolderImage(item)
            Spacer(Modifier.height(4.dp))
            MediaItemLabels(
                title = item.displayName,
                subtitle = null,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FolderImage(item: RecommendationFolder) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Folder,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun AudiobookRowItem(
    modifier: Modifier = Modifier,
    item: Audiobook,
    onClick: (Audiobook) -> Unit,
    onLongClick: (Audiobook) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = {
            AudiobookImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

private val MEDIA_TITLE_WEIGHT = FontWeight.SemiBold
private const val SUBTITLE_ALPHA = 0.6f

/**
 * Canonical title + optional subtitle for every list/grid media item.
 * Title is emphasised via weight; the subtitle is dimmed with alpha rather than a
 * distinct color, keeping the hierarchy sleek and consistent app-wide.
 */
@Composable
internal fun OwnedPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(3.dp),
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.45f),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "OWNED",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun MediaItemLabels(
    title: String,
    subtitle: String?,
    isOwned: Boolean = false,
    textAlign: TextAlign? = null,
    titleMaxLines: Int = 1,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = MEDIA_TITLE_WEIGHT,
        textAlign = textAlign,
        maxLines = titleMaxLines,
        overflow = TextOverflow.Ellipsis,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start,
    ) {
        if (isOwned) {
            OwnedPill()
            Spacer(Modifier.width(6.dp))
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = MEDIA_TITLE_WEIGHT,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The shared library row: leading image slot, title/subtitle, optional trailing slot.
 *
 * Takes strings and slots rather than an [AppMediaItem], so a row that is not a media item —
 * an AI Radio station, say — can reuse the exact same metrics and typography.
 *
 * @param enabled false makes the row inert, ripple included. For a row that carries its action
 *   in [suffixContent] instead, so a tap on the body is not answered with a ripple.
 * @param suffixContent trailing slot, rendered after the weighted label column. When present it
 *   may hold a control of its own, so the row's description is scoped to the labels — see below.
 */
@Composable
internal fun RowItem(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String?,
    description: String,
    isOwned: Boolean = false,
    prefixContent: @Composable (BoxScope.() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    suffixContent: @Composable (RowScope.() -> Unit)? = null,
) {
    // clearAndSetSemantics wipes every descendant's semantics too. On a whole-row tap target
    // that is what we want — one node, one description. But a trailing control would be made
    // invisible and unreachable to accessibility services by it, so a row that has one scopes
    // the description to its labels and lets that control keep its own.
    val describe = Modifier.clearAndSetSemantics { contentDescription = description }
    val describesWholeRow = suffixContent == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(if (describesWholeRow) describe else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prefixContent?.let {
            Box(modifier = Modifier.size(rowImageSize())) { it() }
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (describesWholeRow) Modifier else describe),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MediaItemLabels(
                title = name,
                subtitle = subtitle,
                isOwned = isOwned,
                titleMaxLines = 2,
            )
        }
        suffixContent?.invoke(this)
    }
}

@Composable
private fun contentDescription(appMediaItem: AppMediaItem): String {
    return if (appMediaItem is Album) {
        stringResource(Res.string.cd_album_item, appMediaItem.displayName, appMediaItem.provider)
    } else {
        appMediaItem.displayName
    }
}
