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
import androidx.compose.ui.zIndex
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
    onBack: () -> Unit = {},
    backHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val heartButtonScale = remember { Animatable(1f) }

    val triggerFavoriteWithAnim = remember(onToggleFavorite) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch {
                heartButtonScale.animateTo(0.70f, tween(70, easing = FastOutLinearInEasing))
                heartButtonScale.animateTo(1.28f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                heartButtonScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            }
            onToggleFavorite()
        }
    }

    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { 68.dp.toPx() }

    var showSaveDialog by remember { mutableStateOf(false) }

    var pullDownAccumulated by remember { mutableFloatStateOf(0f) }
    var pullDownTriggered by remember { mutableStateOf(false) }
    val pullDownThresholdPx = with(density) { 36.dp.toPx() }

    val queueNestedScrollConnection = remember(listState, onBack) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If user drags downward and the list cannot scroll backward (at the top)
                if (available.y > 0 && !listState.canScrollBackward) {
                    if (!pullDownTriggered) {
                        pullDownAccumulated += available.y
                        if (pullDownAccumulated > pullDownThresholdPx) {
                            pullDownTriggered = true
                            pullDownAccumulated = 0f
                            onBack()
                        }
                    }
                    return Offset(0f, available.y)
                } else if (available.y < 0) {
                    pullDownAccumulated = 0f
                    pullDownTriggered = false
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y > 80f && !listState.canScrollBackward && !pullDownTriggered) {
                    pullDownTriggered = true
                    pullDownAccumulated = 0f
                    onBack()
                    return available
                }
                pullDownAccumulated = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            pullDownAccumulated = 0f
            pullDownTriggered = false
        }
    }

    LaunchedEffect(backHandlerEnabled) {
        if (backHandlerEnabled) {
            pullDownAccumulated = 0f
            pullDownTriggered = false
        }
    }

    val queueItemKeys = remember(state.queue) {
        val countMap = HashMap<String, Int>(state.queue.size)
        state.queue.map { song ->
            val count = countMap[song.id] ?: 0
            countMap[song.id] = count + 1
            if (count == 0) song.id else "${song.id}#$count"
        }
    }

    LaunchedEffect(state.queue.size) {
        if (draggingIndex != null && draggingIndex !in state.queue.indices) {
            draggingIndex = null
            dragOffsetY = 0f
        }
    }

    BackHandler(enabled = backHandlerEnabled) { onBack() }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // Top Header: Collapse Arrow on Top Left + Drag Handle Pill (tap or drag down to return to Now Playing)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Back to Now Playing",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .pointerInput(onBack) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 8f) {
                                    change.consume()
                                    onBack()
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .padding(horizontal = 32.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.40f))
                )
            }
        }

        // 1. Top Bar (Mini artwork, Title, Artist, Heart, Lock, More)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
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

            // Action Button: Heart with tactile spring animation
            val isFav = state.currentSongIsFavorite
            val favBgColor by animateColorAsState(
                targetValue = if (isFav) colors.primary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "queue_fav_bg_color"
            )
            val favIconColor by animateColorAsState(
                targetValue = if (isFav) colors.primary else Color.White,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "queue_fav_icon_color"
            )

            IconButton(
                onClick = triggerFavoriteWithAnim,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = heartButtonScale.value
                        scaleY = heartButtonScale.value
                    }
                    .clip(CircleShape)
                    .background(favBgColor)
            ) {
                AnimatedContent(
                    targetState = isFav,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn(tween(140)))
                            .togetherWith(scaleOut(tween(90)) + fadeOut(tween(90)))
                    },
                    label = "queue_fav_icon_morph"
                ) { fav ->
                    Icon(
                        imageVector = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
                        tint = favIconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Quick Action Pills Row (Shuffle, Repeat)
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Section Header ("Continue Playing", "Next in Queue", count, duration)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(onBack) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 8f) {
                                change.consume()
                                onBack()
                            }
                        }
                    )
                }
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
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(queueNestedScrollConnection),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                itemsIndexed(
                    items = state.queue,
                    key = { index, _ -> queueItemKeys.getOrElse(index) { "${state.queue[index].id}_$index" } },
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

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

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
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                shadowElevation = if (isDragging) 16f else 0f
            }
            .background(if (isDragging) colors.surfaceHighest.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(enabled = !isDragging, onClick = onClick)
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
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Track details (Title, Artist, Duration)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
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
                    modifier = Modifier.size(44.dp)
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
                    .size(44.dp)
                    .pointerInput(song.id) {
                        try {
                            detectVerticalDragGestures(
                                onDragStart = { currentOnDragStart() },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDrag(dragAmount)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragEnd() }
                            )
                        } finally {
                            currentOnDragEnd()
                        }
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
