package com.shyan.dreamin.ui.screens

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
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(text = trailing, fontSize = 13.sp, color = colors.onSurfaceVariant)
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
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_bg"
    )
    val animBorder by animateColorAsState(
        targetValue = if (isActive) colors.primary.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_border"
    )
    val animContentColor by animateColorAsState(
        targetValue = if (isActive) colors.primary else Color.White.copy(alpha = 0.9f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_content"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
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
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = animContentColor)
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "drag_scale"
    )
    val dragElevation by animateFloatAsState(
        targetValue = if (isDragging) 14f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "drag_elevation"
    )
    val rowBgColor by animateColorAsState(
        targetValue = when {
            isDragging -> colors.surfaceHighest.copy(alpha = 0.55f)
            isCurrent -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "row_bg"
    )
    val durationMs = QueueSections.normalizedDurationMs(song.duration)
    val subtitle = if (durationMs > 0L) "${song.artist} • ${formatDuration(durationMs)}" else song.artist
    val hasMenu = onPlayNext != null || onRemove != null || (onAddToPlaylist != null && playlists.isNotEmpty())

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
            .clip(RoundedCornerShape(14.dp))
            .background(rowBgColor)
            .clickable(enabled = !isDragging, onClick = onClick)
            .alpha(if (isHistory) HISTORY_ALPHA else 1f)
            .padding(horizontal = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.displayArtworkUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Playing, tap to pause" else "Paused, tap to play",
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) colors.primary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
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
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = colors.surfaceContainer
                    ) {
                        if (onPlayNext != null) {
                            DropdownMenuItem(
                                text = { Text("Play Next", color = Color.White) },
                                onClick = { showMenu = false; onPlayNext() },
                                leadingIcon = { Icon(Icons.Filled.SkipNext, null, tint = colors.primary) }
                            )
                        }
                        if (onAddToPlaylist != null && playlists.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist", color = Color.White) },
                                onClick = { showMenu = false; showPlaylistPicker = true },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = colors.primary) }
                            )
                        }
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from Queue", color = colors.error) },
                                onClick = { showMenu = false; onRemove() },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = colors.error) }
                            )
                        }
                    }
                }
            }

            if (onDragStart != null) {
                DragHandle(dragKey, isDragging, onDragStart, onDrag, onDragEnd)
            }
        }
    }

    if (showPlaylistPicker && onAddToPlaylist != null) {
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { id -> onAddToPlaylist(id); showPlaylistPicker = false },
            onDismiss = { showPlaylistPicker = false }
        )
    }
}

@Composable
private fun DragHandle(
    dragKey: String,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val handleScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "drag_handle_scale"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(dragKey) {
                detectVerticalDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Reorder",
            tint = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = handleScale; scaleY = handleScale }
        )
    }
}

@Composable
private fun PlaylistPickerDialog(playlists: List<Playlist>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalDreaminColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist", color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                playlists.forEach { pl ->
                    Text(
                        text = pl.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(pl.id) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.primary) } },
        containerColor = colors.surfaceContainer
    )
}
