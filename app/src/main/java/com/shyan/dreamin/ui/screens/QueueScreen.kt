@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.blur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shyan.dreamin.ui.components.AmbientSoundwaveHeader
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.ui.components.EqualizerBottomSheet
import com.shyan.dreamin.ui.components.FluidMeshGradientBackground
import com.shyan.dreamin.ui.components.SyncedLyricsView
import com.shyan.dreamin.viewmodel.MusicPlayerViewModel
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke






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
                    Brush.horizontalGradient(
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

@Composable
fun UpNextTimeline(
    upcomingSongs: List<Song>,
    onSongClick: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit = {},
    onRestoreToQueue: (Song, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    onReorder: (Int, Int) -> Unit = { _, _ -> },
    onSaveQueueAsPlaylist: () -> Unit = {},
    isFetchingUpNext: Boolean = false,
    snackbarHostState: SnackbarHostState? = null,
    fullQueue: List<Song> = emptyList(),
    currentSongId: String? = null
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    if (upcomingSongs.isEmpty() && !isFetchingUpNext) return

    var displaySongs by remember(upcomingSongs) { mutableStateOf(upcomingSongs.take(8)) }
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var originalDragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var removingSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRestoringQueue by remember { mutableStateOf(false) }

    val rowHeightPx = with(density) { 62.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = if (isFetchingUpNext || isRestoringQueue) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Up Next",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }

        AnimatedVisibility(
            visible = isFetchingUpNext || isRestoringQueue,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(250))
        ) {
            BreathingCuratingLine(modifier = Modifier.padding(bottom = 8.dp))
        }

        displaySongs.forEachIndexed { index, song ->
            key(song.id) {
                val isDragging = draggingItemIndex == index
                val isRemoving = removingSongIds.contains(song.id)

                AnimatedVisibility(
                    visible = !isRemoving,
                    enter = fadeIn(tween(150)),
                    exit = shrinkVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(160))
                ) {
                    Box(modifier = Modifier.staggeredEntry(index, baseDelayMs = 24)) {
                        Column {
                            UpNextRow(
                            song = song,
                            position = index + 1,
                            isNext = index == 0,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            onDragStart = {
                                draggingItemIndex = index
                                originalDragIndex = index
                                dragOffsetY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { deltaY ->
                                dragOffsetY += deltaY
                                val currentIndex = draggingItemIndex ?: return@UpNextRow
                                val threshold = rowHeightPx * 0.75f

                                if (dragOffsetY > threshold && currentIndex < displaySongs.lastIndex) {
                                    // Move down
                                    val nextIndex = currentIndex + 1
                                    val mutable = displaySongs.toMutableList()
                                    val item = mutable.removeAt(currentIndex)
                                    mutable.add(nextIndex, item)
                                    displaySongs = mutable
                                    draggingItemIndex = nextIndex
                                    dragOffsetY -= rowHeightPx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (dragOffsetY < -threshold && currentIndex > 0) {
                                    // Move up
                                    val prevIndex = currentIndex - 1
                                    val mutable = displaySongs.toMutableList()
                                    val item = mutable.removeAt(currentIndex)
                                    mutable.add(prevIndex, item)
                                    displaySongs = mutable
                                    draggingItemIndex = prevIndex
                                    dragOffsetY += rowHeightPx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                val from = originalDragIndex
                                val to = draggingItemIndex
                                if (from != null && to != null && from != to) {
                                    onReorder(from, to)
                                }
                                draggingItemIndex = null
                                originalDragIndex = null
                                dragOffsetY = 0f
                            },
                            onClick = { onSongClick(song) },
                            onRemove = {
                                val currentIdx = fullQueue.indexOfFirst { it.id == currentSongId }
                                val queueIndex = (if (currentIdx >= 0) currentIdx + 1 else 0) + index
                                removingSongIds = removingSongIds + song.id
                                scope.launch {
                                    delay(220)
                                    onRemoveFromQueue(song)
                                    removingSongIds = removingSongIds - song.id
                                    if (snackbarHostState != null) {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Removed \"${song.displayTitle}\"",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            isRestoringQueue = true
                                            onRestoreToQueue(song, queueIndex)
                                            scope.launch {
                                                delay(1800)
                                                isRestoringQueue = false
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        if (index < displaySongs.lastIndex) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun UpNextRow(
    song: Song,
    position: Int,
    isNext: Boolean,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onClick: () -> Unit,
    onRemove: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val swipeOffset = remember(song.id) { Animatable(0f) }
    val swipeThreshold = 100f
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val onRemoveRef = rememberUpdatedState(onRemove)

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "row_elev"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "row_scale"
    )

    val rowShape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                shape = rowShape
                clip = false
            }
    ) {
        // Glowing red full-height remove background
        val swipeVal = swipeOffset.value
        if (swipeVal < 0f) {
            val progress = (-swipeVal / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFEF4444).copy(alpha = 0.7f),
                                Color(0xFFDC2626).copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = 0.6f + 0.4f * progress
                        alpha = progress
                    }
                ) {
                    Text("REMOVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val captured = swipeOffset.value
                            scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            if (captured < -swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemoveRef.value()
                            }
                        },
                        onDragCancel = {
                            scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f || swipeOffset.value < 0f) {
                                val next = (swipeOffset.value + dragAmount).coerceIn(-180f, 0f)
                                if (next < 0f || swipeOffset.value < 0f) {
                                    change.consume()
                                    scope.launch { swipeOffset.snapTo(next) }
                                }
                            }
                        }
                    )
                }
                .graphicsLayer { translationX = swipeOffset.value }
                .clip(rowShape)
                .border(
                    if (isDragging) 1.5.dp else 1.dp,
                    if (isDragging) colors.primary
                    else if (isNext) colors.primary.copy(alpha = 0.45f)
                    else Color.White.copy(alpha = 0.08f),
                    rowShape
                )
                .background(
                    if (isDragging) colors.surfaceHighest.copy(alpha = 0.98f)
                    else if (isNext) colors.primary.copy(alpha = 0.12f)
                    else colors.surfaceHighest.copy(alpha = 0.4f),
                    rowShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = colors.primary)
                ) { onClick() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isNext) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Current",
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "$position",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.displayArtworkUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.displayTitle,
                    color = if (isNext) colors.primary else colors.onSurface,
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
                Text(
                    song.artist,
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove from queue",
                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Dedicated Drag-and-Drop Reorder Handle
            Box(
                modifier = Modifier
                    .size(36.dp)
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
                    contentDescription = "Reorder track",
                    tint = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}





@Composable
fun QueueScreen(
    state: PlayerUiState,
    onSongClick: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onRestoreToQueue: (Song, Int) -> Unit = { _, _ -> },
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    onSaveQueueAsPlaylist: (String) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = remember { mutableStateOf(80f) }
    val scope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Queue",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primary
                )
                Text(
                    "${state.queue.size} songs",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state.queue.isNotEmpty()) {
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = "Save queue as playlist",
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

    var isRestoringQueue by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SaveQueueDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveQueueAsPlaylist(name)
                showSaveDialog = false
            }
        )
    }

        AnimatedVisibility(
            visible = state.isFetchingUpNext || isRestoringQueue,
            enter = fadeIn(tween(180)) + expandVertically(tween(220)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(250))
        ) {
            BreathingCuratingLine(modifier = Modifier.padding(bottom = 10.dp))
        }

        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Queue is empty. Add songs to get started.",
                    color = colors.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            var removingQueueSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.queue,
                    key = { it.id },
                    contentType = { "QueueSongRow" }
                ) { song ->
                    val index = state.queue.indexOfFirst { it.id == song.id }
                    val isDragging = draggingIndex == index
                    val offsetY = if (isDragging) dragOffsetY else 0f
                    val isRemoving = removingQueueSongIds.contains(song.id)

                    AnimatedVisibility(
                        visible = !isRemoving,
                        enter = fadeIn(tween(150)),
                        exit = shrinkVertically(
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(160))
                    ) {
                        QueueSongRow(
                            song = song,
                            isPlaying = state.currentSong?.id == song.id,
                            onClick = { if (draggingIndex == null) onSongClick(song) },
                            onRemove = {
                                val origIndex = index
                                removingQueueSongIds = removingQueueSongIds + song.id
                                scope.launch {
                                    delay(220)
                                    onRemoveFromQueue(song)
                                    removingQueueSongIds = removingQueueSongIds - song.id
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed \"${song.displayTitle}\"",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        isRestoringQueue = true
                                        onRestoreToQueue(song, origIndex)
                                        scope.launch {
                                            delay(1800)
                                            isRestoringQueue = false
                                        }
                                    }
                                }
                            },
                            isDragging = isDragging,
                            dragOffsetY = offsetY,
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { delta ->
                                dragOffsetY += delta
                                val newIndex = (index + (dragOffsetY / itemHeightPx.value).toInt())
                                    .coerceIn(0, state.queue.lastIndex)
                                if (newIndex != index) {
                                    onReorderQueue(index, newIndex)
                                    dragOffsetY -= (newIndex - index) * itemHeightPx.value
                                    draggingIndex = newIndex
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffsetY = 0f
                            },
                            onHeightMeasured = { itemHeightPx.value = it }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF13111C).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 14.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = data.visuals.message,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    data.visuals.actionLabel?.let { actionLabel ->
                        TextButton(
                            onClick = { data.performAction() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)
                        ) {
                            Text(
                                text = actionLabel.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueSongRow(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onHeightMeasured: (Float) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val swipeOffset = remember(song.id) { Animatable(0f) }
    val swipeThreshold = 100f
    val scope = rememberCoroutineScope()
    val onRemoveRef = rememberUpdatedState(onRemove)

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 16.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "drag_elevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "drag_scale"
    )

    val rowShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.toInt()) }
            .onGloballyPositioned { onHeightMeasured(it.size.height.toFloat()) }
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
                shape = rowShape
                clip = false
            }
    ) {
        // Glowing red remove backdrop on swipe left
        val swipeVal = swipeOffset.value
        if (swipeVal < 0f) {
            val progress = (-swipeVal / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(rowShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xFFEF4444).copy(alpha = 0.7f),
                                Color(0xFFDC2626).copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = 0.6f + 0.4f * progress
                        alpha = progress
                    }
                ) {
                    Text("REMOVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val captured = swipeOffset.value
                            scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            if (captured < -swipeThreshold) {
                                onRemoveRef.value()
                            }
                        },
                        onDragCancel = {
                            scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount < 0f || swipeOffset.value < 0f) {
                                val next = (swipeOffset.value + dragAmount).coerceIn(-180f, 0f)
                                if (next < 0f || swipeOffset.value < 0f) {
                                    change.consume()
                                    scope.launch { swipeOffset.snapTo(next) }
                                }
                            }
                        }
                    )
                }
                .graphicsLayer { translationX = swipeOffset.value }
                .clip(rowShape)
                .border(
                    width = if (isDragging) 1.5.dp else 0.dp,
                    brush = if (isDragging) Brush.horizontalGradient(listOf(colors.primary, colors.secondary)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                    shape = rowShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = colors.primary)
                ) { onClick() }
                .background(
                    if (isDragging) colors.surfaceHighest.copy(alpha = 0.95f)
                    else if (isPlaying) colors.surfaceHigh
                    else Color.Transparent,
                    rowShape
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "Drag to reorder ${song.displayTitle}",
                tint = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { _, dragAmount -> onDrag(dragAmount.y) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )

            Spacer(modifier = Modifier.width(8.dp))

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.displayArtworkUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = "Artwork for ${song.displayTitle}",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.displayTitle,
                    color = if (isPlaying) colors.primary else colors.onSurface,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp
                )
                Text(
                    song.artist,
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.RemoveCircleOutline,
                    contentDescription = "Remove ${song.displayTitle} from queue",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
