@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.data.queue.QueueRow
import com.shyan.dreamin.data.queue.QueueSections
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val KEY_HISTORY_HEADER = "header:history"
private const val KEY_NOW_PLAYING_HEADER = "header:now_playing"
private const val KEY_FROM_YOU_HEADER = "header:from_you"
private const val KEY_NEXT_UP_HEADER = "header:next_up"
private const val KEY_CURATING = "footer:curating"

private data class DragSession(val key: String, val rows: List<QueueRow>, val committedKeys: List<String>)

@Composable
fun BreathingCuratingLine(modifier: Modifier = Modifier) {
    val colors = LocalDreaminColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_line_trans")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "line_alpha"
    )
    val scaleX by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "line_scale"
    )
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(scaleX)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color.Transparent, colors.primary.copy(alpha = alpha), colors.secondary.copy(alpha = alpha), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun QueueScreen(
    state: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onSongClick: (Int, Song) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemoveFromQueue: (Int, Song, Boolean) -> Unit,
    onRestoreToQueue: (Song, Int, Boolean, Boolean) -> Unit,
    onReorderQueue: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onBack: () -> Unit,
    backHandlerEnabled: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val sections = remember(state.queue, state.currentQueueIndex, state.userQueuedSongIds) {
        QueueSections.build(state.queue, state.currentQueueIndex, state.userQueuedSongIds)
    }
    val latestSections by rememberUpdatedState(sections)
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = backHandlerEnabled) { onBack() }

    LaunchedEffect(backHandlerEnabled) {
        if (!backHandlerEnabled) return@LaunchedEffect
        val nowPlayingIndex = if (historyExpanded && sections.history.isNotEmpty()) 1 + sections.history.size else 0
        listState.scrollToItem(nowPlayingIndex)
    }

    val trySwap: () -> Unit = trySwap@{
        val session = dragSession ?: return@trySwap
        val swapped = swapAcrossNeighbor(session, dragOffsetY, listState, latestSections.fromYou.size) ?: return@trySwap
        dragSession = swapped.first
        dragOffsetY = swapped.second
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val endDrag: () -> Unit = endDrag@{
        val session = dragSession ?: return@endDrag
        val committed = latestSections
        val localIndex = session.rows.indexOfFirst { it.key == session.key }
        val row = session.rows.getOrNull(localIndex)
        val unchanged = committed.upcoming.map { it.key } == session.committedKeys
        val upcomingStart = (committed.current?.index ?: -1) + 1
        if (row != null && unchanged && row.index != upcomingStart + localIndex) {
            onReorderQueue(row.index, upcomingStart + localIndex)
        }
        dragSession = null
        dragOffsetY = 0f
    }

    EdgeAutoScroll(
        draggingKey = dragSession?.key,
        listState = listState,
        dragOffsetY = { dragOffsetY },
        onScrolled = { consumed ->
            dragOffsetY += consumed
            trySwap()
        }
    )

    val removeWithUndo: (QueueRow, Boolean, Boolean) -> Unit = { row, wasUserQueued, wasUpcoming ->
        onRemoveFromQueue(row.index, row.song, wasUserQueued)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Removed “${row.song.title}”",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onRestoreToQueue(row.song, row.index, wasUserQueued, wasUpcoming)
        }
    }

    if (showSaveDialog) {
        SaveQueueDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name -> onSaveQueueAsPlaylist(name); showSaveDialog = false }
        )
    }

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            QueueTopHandle(onBack)
            QueueNowPlayingBar(
                state = state,
                hasUpcoming = sections.upcoming.isNotEmpty(),
                onBack = onBack,
                onToggleFavorite = onToggleFavorite,
                onSaveQueue = { showSaveDialog = true },
                onClearUpcoming = onClearUpcoming
            )
            QueueModePills(state, onToggleShuffle, onToggleRepeat)
            QueueSummaryHeader(sections, progressFlow)

            if (state.queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Queue is empty. Play a song to get started.", color = colors.onSurfaceVariant, fontSize = 15.sp)
                }
            } else {
                val upcomingRows = dragSession?.rows ?: sections.upcoming
                val fromYouCount = sections.fromYou.size
                val isPlaying = state.playbackState is PlaybackState.Playing

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    if (sections.history.isNotEmpty()) {
                        item(key = KEY_HISTORY_HEADER) {
                            QueueSectionHeader(
                                title = "History",
                                trailing = sections.history.size.toString(),
                                expanded = historyExpanded,
                                onClick = { historyExpanded = !historyExpanded }
                            )
                        }
                        if (historyExpanded) {
                            items(sections.history, key = { it.key }) { row ->
                                QueueSongItemRow(
                                    song = row.song,
                                    dragKey = row.key,
                                    isCurrent = false,
                                    isPlaying = false,
                                    isHistory = true,
                                    isDragging = false,
                                    dragOffsetY = { 0f },
                                    onClick = { onSongClick(row.index, row.song) },
                                    onRemove = { removeWithUndo(row, false, false) },
                                    onPlayNext = { onPlayNext(row.song) },
                                    onAddToPlaylist = { pid -> onAddToPlaylist(row.song, pid) },
                                    playlists = state.playlists,
                                    onDragStart = null,
                                    onDrag = {},
                                    onDragEnd = {},
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    sections.current?.let { current ->
                        item(key = KEY_NOW_PLAYING_HEADER) { QueueSectionHeader(title = "Now playing") }
                        item(key = current.key) {
                            QueueSongItemRow(
                                song = current.song,
                                dragKey = current.key,
                                isCurrent = true,
                                isPlaying = isPlaying,
                                isHistory = false,
                                isDragging = false,
                                dragOffsetY = { 0f },
                                onClick = onTogglePlayPause,
                                onRemove = null,
                                onPlayNext = null,
                                onAddToPlaylist = { pid -> onAddToPlaylist(current.song, pid) },
                                playlists = state.playlists,
                                onDragStart = null,
                                onDrag = {},
                                onDragEnd = {},
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    upcomingRows.forEachIndexed { localIndex, row ->
                        if (localIndex == 0 && fromYouCount > 0) {
                            item(key = KEY_FROM_YOU_HEADER) { QueueSectionHeader(title = "Next from you") }
                        }
                        if (localIndex == fromYouCount) {
                            item(key = KEY_NEXT_UP_HEADER) {
                                QueueSectionHeader(title = if (state.playlistQueueActive) "Next from playlist" else "Next up")
                            }
                        }
                        item(key = row.key) {
                            UpcomingRow(
                                row = row,
                                wasUserQueued = localIndex < fromYouCount,
                                isDragging = dragSession?.key == row.key,
                                dragOffsetY = { if (dragSession?.key == row.key) dragOffsetY else 0f },
                                playlists = state.playlists,
                                onSongClick = onSongClick,
                                onRemove = removeWithUndo,
                                onPlayNext = onPlayNext,
                                onAddToPlaylist = onAddToPlaylist,
                                onDragStart = {
                                    dragSession = DragSession(row.key, latestSections.upcoming, latestSections.upcoming.map { it.key })
                                    dragOffsetY = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { delta ->
                                    dragOffsetY += delta
                                    trySwap()
                                },
                                onDragEnd = endDrag
                            )
                        }
                    }

                    item(key = KEY_CURATING) {
                        AnimatedVisibility(
                            visible = state.isFetchingUpNext,
                            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(300)) + shrinkVertically(tween(250))
                        ) {
                            BreathingCuratingLine(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.surfaceContainer,
                contentColor = Color.White,
                actionColor = colors.primary
            )
        }
    }
}

@Composable
private fun LazyItemScope.UpcomingRow(
    row: QueueRow,
    wasUserQueued: Boolean,
    isDragging: Boolean,
    dragOffsetY: () -> Float,
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    onSongClick: (Int, Song) -> Unit,
    onRemove: (QueueRow, Boolean, Boolean) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    DisposableEffect(isDragging) {
        onDispose { if (isDragging) currentOnDragEnd() }
    }
    val placement = if (isDragging) Modifier else Modifier.animateItem(
        fadeInSpec = tween(220, easing = LinearOutSlowInEasing),
        fadeOutSpec = tween(180, easing = FastOutLinearInEasing),
        placementSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    )
    QueueSongItemRow(
        song = row.song,
        dragKey = row.key,
        isCurrent = false,
        isPlaying = false,
        isHistory = false,
        isDragging = isDragging,
        dragOffsetY = dragOffsetY,
        onClick = { onSongClick(row.index, row.song) },
        onRemove = { onRemove(row, wasUserQueued, true) },
        onPlayNext = { onPlayNext(row.song) },
        onAddToPlaylist = { pid -> onAddToPlaylist(row.song, pid) },
        playlists = playlists,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        modifier = placement
    )
}

private fun swapAcrossNeighbor(
    session: DragSession,
    dragOffsetY: Float,
    listState: LazyListState,
    fromYouCount: Int
): Pair<DragSession, Float>? {
    val index = session.rows.indexOfFirst { it.key == session.key }
    if (index < 0) return null
    val sectionRange = if (index < fromYouCount) 0 until fromYouCount else fromYouCount..session.rows.lastIndex
    val visible = listState.layoutInfo.visibleItemsInfo
    val dragged = visible.firstOrNull { it.key == session.key } ?: return null
    val draggedCenter = dragged.offset + dragged.size / 2f + dragOffsetY
    val targetIndex = when {
        dragOffsetY > 0f && index + 1 in sectionRange -> index + 1
        dragOffsetY < 0f && index - 1 in sectionRange -> index - 1
        else -> return null
    }
    val neighbor = visible.firstOrNull { it.key == session.rows[targetIndex].key } ?: return null
    val neighborCenter = neighbor.offset + neighbor.size / 2f
    val crossed = if (targetIndex > index) draggedCenter > neighborCenter else draggedCenter < neighborCenter
    if (!crossed) return null
    val newTop = if (targetIndex > index) neighbor.offset + neighbor.size - dragged.size else neighbor.offset
    val reordered = session.rows.toMutableList().apply { add(targetIndex, removeAt(index)) }.toList()
    return session.copy(rows = reordered) to (dragOffsetY - (newTop - dragged.offset))
}

@Composable
private fun EdgeAutoScroll(
    draggingKey: String?,
    listState: LazyListState,
    dragOffsetY: () -> Float,
    onScrolled: (Float) -> Unit
) {
    val density = LocalDensity.current
    val edgePx = with(density) { 64.dp.toPx() }
    val maxStepPx = with(density) { 14.dp.toPx() }
    LaunchedEffect(draggingKey) {
        val key = draggingKey ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = item.offset + dragOffsetY()
            val bottom = top + item.size
            val step = when {
                top < info.viewportStartOffset + edgePx ->
                    -maxStepPx * ((info.viewportStartOffset + edgePx - top) / edgePx).coerceIn(0f, 1f)
                bottom > info.viewportEndOffset - edgePx ->
                    maxStepPx * ((bottom - (info.viewportEndOffset - edgePx)) / edgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step == 0f) continue
            val consumed = listState.scrollBy(step)
            if (consumed != 0f) onScrolled(consumed)
        }
    }
}

@Composable
private fun QueueTopHandle(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Back to Now Playing", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() }
                .padding(horizontal = 32.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.40f)))
        }
    }
}

@Composable
private fun QueueNowPlayingBar(
    state: PlayerUiState,
    hasUpcoming: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveQueue: () -> Unit,
    onClearUpcoming: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(1f) }
    var showMenu by remember { mutableStateOf(false) }
    val currentSong = state.currentSong
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

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = currentSong?.displayArtworkUrl,
            contentDescription = currentSong?.title,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() },
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() }
        ) {
            Text(currentSong?.title ?: "No Track Playing", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(currentSong?.artist ?: "Dreamin", fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    heartScale.animateTo(0.92f, tween(70, easing = FastOutLinearInEasing))
                    heartScale.animateTo(1.10f, spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow))
                    heartScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                }
                onToggleFavorite()
            },
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = heartScale.value; scaleY = heartScale.value }
                .clip(CircleShape)
                .background(favBgColor)
        ) {
            AnimatedContent(
                targetState = isFav,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)) + fadeIn(tween(140)))
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
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.MoreVert, "Queue options", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = colors.surfaceContainer) {
                DropdownMenuItem(
                    text = { Text("Save queue as playlist", color = Color.White) },
                    onClick = { showMenu = false; onSaveQueue() },
                    enabled = state.queue.isNotEmpty(),
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, tint = colors.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Clear upcoming", color = if (hasUpcoming) colors.error else colors.onSurfaceVariant) },
                    onClick = { showMenu = false; onClearUpcoming() },
                    enabled = hasUpcoming,
                    leadingIcon = { Icon(Icons.Filled.ClearAll, null, tint = if (hasUpcoming) colors.error else colors.onSurfaceVariant) }
                )
            }
        }
    }
}

@Composable
private fun QueueModePills(state: PlayerUiState, onToggleShuffle: () -> Unit, onToggleRepeat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QueueActionPill(Icons.Filled.Shuffle, "Shuffle", state.isShuffle, onToggleShuffle, Modifier.weight(1f))
        QueueActionPill(
            icon = if (state.repeatMode == TrackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            label = "Repeat",
            isActive = state.repeatMode != TrackRepeatMode.OFF,
            onClick = onToggleRepeat,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QueueSummaryHeader(sections: QueueSections, progressFlow: StateFlow<PlaybackProgress>) {
    val colors = LocalDreaminColors.current
    val progress by progressFlow.collectAsStateWithLifecycle()
    val upcomingMs = remember(sections) { QueueSections.upcomingDurationMs(sections) }
    val currentMs = remember(sections) { sections.current?.let { QueueSections.normalizedDurationMs(it.song.duration) } ?: 0L }
    val remainingMs = upcomingMs + (currentMs - progress.currentPositionMs).coerceAtLeast(0L)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Up next", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            text = "${QueueSections.songCountLabel(sections.upcoming.size)} · ${formatDuration(remainingMs)} left",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant
        )
    }
}
