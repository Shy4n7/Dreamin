package com.shyan.dreamin.ui.screens

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shyan.dreamin.ui.theme.LocalDreaminColors
import com.shyan.dreamin.ui.theme.DreaminMotion
import com.shyan.dreamin.ui.theme.DreaminShapes
import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shyan.dreamin.data.local.Playlist
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.queue.QueueSections

private const val HISTORY_ALPHA = 0.55f

@Composable
internal fun QueueSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalDreaminColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (expanded != null) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide history" else "Show history",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(20.dp)
            )
        }
    }
}

@Composable
internal fun QueueActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    val animBg by animateColorAsState(
        targetValue = if (isActive) colors.primary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        animationSpec = DreaminMotion.smooth(),
        label = "pill_bg"
    )
    val animBorder by animateColorAsState(
        targetValue = if (isActive) colors.primary.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = DreaminMotion.smooth(),
        label = "pill_border"
    )
    val animContentColor by animateColorAsState(
        targetValue = if (isActive) colors.primary else Color.White.copy(alpha = 0.9f),
        animationSpec = DreaminMotion.smooth(),
        label = "pill_content"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = DreaminShapes.medium,
        color = animBg,
        border = BorderStroke(1.dp, animBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = animContentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = animContentColor)
        }
    }
}

@Composable
internal fun QueueSongItemRow(
    song: Song,
    dragKey: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isHistory: Boolean,
    isDragging: Boolean,
    dragOffsetY: () -> Float,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToPlaylist: ((Long) -> Unit)?,
    playlists: List<Playlist>,
    onDragStart: (() -> Unit)?,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1.0f,
        animationSpec = DreaminMotion.bouncy(),
        label = "drag_scale"
    )
    val dragElevation by animateFloatAsState(
        targetValue = if (isDragging) 14f else 0f,
        animationSpec = DreaminMotion.smooth(),
        label = "drag_elevation"
    )
    val rowBgColor by animateColorAsState(
        targetValue = when {
            isDragging -> colors.surfaceHighest.copy(alpha = 0.55f)
            isCurrent -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(DreaminMotion.DurationShort),
        label = "row_bg"
    )
    val durationMs = QueueSections.normalizedDurationMs(song.duration)
    val subtitle = if (durationMs > 0L) "${song.artist} • ${formatDuration(durationMs)}" else song.artist
    val hasMenu = onPlayNext != null || onRemove != null || onAddToPlaylist != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY()
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation
            }
            .clip(DreaminShapes.medium)
            .background(rowBgColor)
            .clickable(enabled = !isDragging, onClick = onClick)
            .alpha(if (isHistory) HISTORY_ALPHA else 1f)
            .padding(horizontal = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(DreaminShapes.small),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.displayArtworkUrl,
                    contentDescription = song.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.shyan.dreamin.ui.components.NowPlayingIndicator(
                            modifier = Modifier.semantics {
                                contentDescription = if (isPlaying) "Playing, tap to pause" else "Paused, tap to play"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = song.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) colors.primary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (hasMenu) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.MoreVert, "More options", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    SongOptionsMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        actions = listOfNotNull(
                            onPlayNext?.let { SongMenuActions.playNext(it) },
                            onAddToPlaylist?.let { SongMenuActions.addToPlaylist { showPlaylistPicker = true } },
                            onRemove?.let { SongMenuActions.removeFromQueue(it) }
                        )
                    )
                }
            }

            if (onDragStart != null) {
                com.shyan.dreamin.ui.components.ReorderHandle(dragKey, isDragging, onDragStart, onDrag, onDragEnd)
            }
        }
    }

    if (showPlaylistPicker && onAddToPlaylist != null) {
        AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = onAddToPlaylist
        )
    }
}

