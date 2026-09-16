@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shyan.dreamin.data.model.*
import kotlinx.coroutines.launch

/**
 * 📜 Breathing curating animated line indicator for dynamic queue fetching.
 */
@Composable
fun BreathingCuratingLine(modifier: Modifier = Modifier) {
    val colors = LocalDreaminColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_line_trans")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "line_alpha"
    )
    val scaleX by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "line_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(scaleX)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            colors.primary.copy(alpha = alpha),
                            colors.secondary.copy(alpha = alpha),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * 🎵 Redesigned High-Fidelity Queue Screen.
 *
 * Implements the sleek dark queue layout matching design reference Screenshot 2:
 * - Mini artwork header with song title, artist, Heart, Lock, and 3-dots actions.
 * - Shuffle, Repeat, and Radio quick mode action pills.
 * - Section header with song count and total remaining queue duration.
 * - Ultra-smooth drag-to-reorder list with active song indicators.
 */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onSongClick: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onRestoreToQueue: (Song, Int) -> Unit = { _, _ -> },
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleQueueLock: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onAddToPlaylist: ((Song, Long) -> Unit)? = null,
    onPlayNext: ((Song) -> Unit)? = null,
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 68.dp.toPx() }

    var showSaveDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { onBack() }

    // Calculate total formatted duration of upcoming tracks
    val totalDurationFormatted = remember(state.queue) {
        val totalSecs = state.queue.sumOf { song: Song -> song.duration }
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    if (showSaveDialog) {
        SaveQueueDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveQueueAsPlaylist(name)
                showSaveDialog = false
            }
        )
    }

    val swipeOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dismissThreshold = with(density) { 110.dp.toPx() }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && swipeOffsetY.value > 0f) {
                    val newOffset = (swipeOffsetY.value + delta).coerceAtLeast(0f)
                    coroutineScope.launch { swipeOffsetY.snapTo(newOffset) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    val newOffset = (swipeOffsetY.value + delta * 0.7f).coerceAtLeast(0f)
                    coroutineScope.launch { swipeOffsetY.snapTo(newOffset) }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (swipeOffsetY.value > dismissThreshold) {
                    onBack()
                    return available
                } else if (swipeOffsetY.value > 0f) {
                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = swipeOffsetY.value.coerceAtLeast(0f)
            }
            .nestedScroll(nestedScrollConnection)
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Subtle Drag Handle Pill indicating swipe down to Now Playing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (swipeOffsetY.value > dismissThreshold) {
                                onBack()
                            } else {
                                coroutineScope.launch {
                                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || swipeOffsetY.value > 0f) {
                                change.consume()
                                coroutineScope.launch {
                                    swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }

        // 1. Top Bar (Mini artwork, Title, Artist, Heart, Lock, More)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (swipeOffsetY.value > dismissThreshold) {
                                onBack()
                            } else {
                                coroutineScope.launch {
                                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || swipeOffsetY.value > 0f) {
                                change.consume()
                                coroutineScope.launch {
                                    swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini artwork thumbnail
            val currentSong = state.currentSong
            AsyncImage(
                model = currentSong?.displayArtworkUrl,
                contentDescription = currentSong?.title,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
            ) {
                Text(
                    text = currentSong?.title ?: "No Track Playing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.artist ?: "Dreamin",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons: Heart, Lock, More
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Heart Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.currentSongIsFavorite) colors.primary.copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.10f)
                        )
                ) {
                    Icon(
                        imageVector = if (state.currentSongIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (state.currentSongIsFavorite) colors.primary else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Lock Button (Playlist isolation lock)
                IconButton(
                    onClick = onToggleQueueLock,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.playlistQueueActive) colors.primary.copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.10f)
                        )
                ) {
                    Icon(
                        imageVector = if (state.playlistQueueActive) Icons.Filled.Lock else Icons.Outlined.Lock,
                        contentDescription = "Queue Lock",
                        tint = if (state.playlistQueueActive) colors.primary else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // More Menu Button
                var showTopMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showTopMenu = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Queue Options",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                        containerColor = colors.surfaceContainer
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save Queue as Playlist", color = Color.White) },
                            onClick = {
                                showTopMenu = false
                                showSaveDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = colors.primary)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Close Queue", color = Color.White) },
                            onClick = {
                                showTopMenu = false
                                onBack()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Close, null, tint = colors.onSurfaceVariant)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Quick Action Pills Row (Shuffle, Repeat, Radio)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Shuffle Pill
            QueueActionPill(
                icon = Icons.Filled.Shuffle,
                label = "Shuffle",
                isActive = state.isShuffle,
                onClick = onToggleShuffle,
                modifier = Modifier.weight(1f)
            )

            // Repeat Pill
            val repeatActive = state.repeatMode != TrackRepeatMode.OFF
            val repeatIcon = if (state.repeatMode == TrackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
            QueueActionPill(
                icon = repeatIcon,
                label = "Repeat",
                isActive = repeatActive,
                onClick = onToggleRepeat,
                modifier = Modifier.weight(1f)
            )

            // Radio Pill
            QueueActionPill(
                icon = Icons.Filled.Sensors,
                label = "Radio",
                isActive = false,
                onClick = onStartRadio,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Section Header ("Continue Playing", "Next in Queue", count, duration)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Continue Playing",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Next in Queue",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${state.queue.size} songs",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = totalDurationFormatted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        AnimatedVisibility(
            visible = state.isFetchingUpNext,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(250))
        ) {
            BreathingCuratingLine(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. Queue List
        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty. Play a song to get started.",
                    color = colors.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                itemsIndexed(
                    items = state.queue,
                    key = { index, song -> "${song.id}_$index" },
                    contentType = { _, _ -> "queue_row" }
                ) { index, song ->
                    val isCurrent = song.id == state.currentSong?.id
                    val isDragging = draggingIndex == index

                    QueueSongItemRow(
                        song = song,
                        isCurrent = isCurrent,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        onClick = { onSongClick(song) },
                        onRemove = { onRemoveFromQueue(song) },
                        onPlayNext = { onPlayNext?.invoke(song) },
                        onAddToPlaylist = { pid -> onAddToPlaylist?.invoke(song, pid) },
                        playlists = state.playlists,
                        onDragStart = {
                            draggingIndex = index
                            dragOffsetY = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { deltaY ->
                            dragOffsetY += deltaY
                            val currIdx = draggingIndex ?: return@QueueSongItemRow
                            val threshold = rowHeightPx * 0.75f

                            if (dragOffsetY > threshold && currIdx < state.queue.lastIndex) {
                                onReorderQueue(currIdx, currIdx + 1)
                                draggingIndex = currIdx + 1
                                dragOffsetY -= rowHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragOffsetY < -threshold && currIdx > 0) {
                                onReorderQueue(currIdx, currIdx - 1)
                                draggingIndex = currIdx - 1
                                dragOffsetY += rowHeightPx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            draggingIndex = null
                            dragOffsetY = 0f
                        }
                    )
                }
            }
        }
    }
}

/**
 * 🔘 Sleek Frosted Quick Action Pill (Shuffle, Repeat, Radio).
 */
@Composable
private fun QueueActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animContentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = animContentColor
            )
        }
    }
}

/**
 * 🎵 Queue Song Item Row matching Screenshot 2.
 */
@Composable
private fun QueueSongItemRow(
    song: Song,
    isCurrent: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = LocalDreaminColors.current
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val formattedDuration = remember(song.duration) {
        if (song.duration > 0) {
            val mins = song.duration / 60
            val secs = song.duration % 60
            String.format("%d:%02d", mins, secs)
        } else ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffsetY
                shadowElevation = if (isDragging) 16f else 0f
            }
            .background(if (isDragging) colors.surfaceHighest.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with optional Play indicator overlay
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist • Duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) colors.primary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (formattedDuration.isNotBlank()) "${song.artist} • $formattedDuration" else song.artist
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Three dots menu button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = colors.surfaceContainer
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onPlayNext()
                        },
                        leadingIcon = { Icon(Icons.Filled.SkipNext, null, tint = colors.primary) }
                    )
                    if (playlists.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showPlaylistPicker = true
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = colors.primary) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Queue", color = Color(0xFFFF6B6B)) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = Color(0xFFFF6B6B)) }
                    )
                }
            }

            // Drag handle (`=`)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { onDragStart() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to Playlist", color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    playlists.forEach { pl ->
                        Text(
                            text = pl.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAddToPlaylist(pl.id)
                                    showPlaylistPicker = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text("Cancel", color = colors.primary)
                }
            },
            containerColor = colors.surfaceContainer
        )
    }
}
