@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.ui.components.AudioStreamDetailsSheet
import com.shyan.dreamin.ui.components.EqualizerBottomSheet
import com.shyan.dreamin.ui.components.FluidMeshGradientBackground
import com.shyan.dreamin.ui.components.PosterPickerBottomSheet
import com.shyan.dreamin.ui.components.SyncedLyricsView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 🎚️ Sleek Minimalist Scrubber Slider matching design reference Screenshot 1.
 */
@Composable
fun SleekScrubberSlider(
    progressFlow: StateFlow<PlaybackProgress>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val playbackProgress by progressFlow.collectAsStateWithLifecycle()
    val currentPositionMs = playbackProgress.currentPositionMs
    val durationMs = playbackProgress.durationMs
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress) }
    if (!isSeeking) seekProgress = progress
    val displayProgress = if (isSeeking) seekProgress else progress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    seekProgress = newProgress
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSeek((newProgress * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isSeeking = true
                        seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        isSeeking = false
                        onSeek((seekProgress * durationMs).toLong())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragCancel = { isSeeking = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount / size.width
                        seekProgress = (seekProgress + delta).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track line with phase-deferred drawBehind for 120fps smooth performance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // Inactive track
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2, h / 2)
                    )
                    // Active track
                    val activeW = w * displayProgress
                    if (activeW > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    Color.White
                                )
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(activeW, h),
                            cornerRadius = CornerRadius(h / 2, h / 2)
                        )
                    }
                }
        )

        // Sleek minimal circular thumb
        Box(
            modifier = Modifier
                .offset(x = (displayProgress * 100).dp.minus(6.dp))
                .graphicsLayer {
                    translationX = (displayProgress * (this.size.width - 12.dp.toPx()))
                }
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 🌌 Redesigned Full-Screen Now Playing Screen.
 *
 * Precisely matches design reference Screenshot 1:
 * - Full-screen fluid animated liquid mesh gradient background across entire display.
 * - Centered "Now Playing" and song title header.
 * - Large 1:1 rounded artwork card (~315dp) with ambient dominant glow & horizontal skip gestures.
 * - Bold title, artist, circular 3-dots More and Heart Favorite buttons.
 * - Sleek progress slider with "0:00", dynamic "OPUS • 142 kbps" codec pill, and total duration.
 * - Double-triangle skip controls and solid white circular play/pause button.
 * - Bottom action bar: Queue button (smoothly slides to QueueScreen), Speaker/Timer pill, Lyrics button.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onSongSelect: (Song) -> Unit,
    onAddToPlaylist: (Long) -> Unit = {},
    onCreatePlaylist: (String, android.net.Uri?) -> Unit = { _, _ -> },
    onRemoveFromQueue: (Song) -> Unit = {},
    onRestoreToQueue: (Song, Int) -> Unit = { _, _ -> },
    onClearQueue: () -> Unit = {},
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onToggleLyrics: () -> Unit = {},
    onRetryLyrics: () -> Unit = {},
    onDownload: (Song) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onUpdateSongArtwork: ((Song, String) -> Unit)? = null,
    onBack: () -> Unit = {}
) {
    var showQueueView by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showPosterPicker by remember { mutableStateOf(false) }
    var showAudioStreamDetailsSheet by remember { mutableStateOf(false) }

    val song = state.currentSong
    val colors = LocalDreaminColors.current

    BackHandler(enabled = true) {
        if (showQueueView) {
            showQueueView = false
        } else {
            onBack()
        }
    }

    val animatedDominant by animateColorAsState(
        targetValue = Color(state.dominantColor),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "mesh_dominant"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = Color(state.secondaryColor),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "mesh_secondary"
    )
    val animatedAccent by animateColorAsState(
        targetValue = Color(state.accentColor),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "mesh_accent"
    )

    val swipeOffsetY = remember { Animatable(0f) }
    val artworkOffsetX = remember { Animatable(0f) }
    val artworkScope = rememberCoroutineScope()
    val scope = rememberCoroutineScope()

    val triggerNextAnimated = remember(onNext) {
        {
            artworkScope.launch {
                artworkOffsetX.animateTo(-360f, tween(130, easing = FastOutLinearInEasing))
                onNext()
                artworkOffsetX.snapTo(320f)
                artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    val triggerPreviousAnimated = remember(onPrevious) {
        {
            artworkScope.launch {
                artworkOffsetX.animateTo(360f, tween(130, easing = FastOutLinearInEasing))
                onPrevious()
                artworkOffsetX.snapTo(-320f)
                artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    // 🌊 Seamless Slide/Fade Animation between Now Playing Screen and Queue Screen
    AnimatedContent(
        targetState = showQueueView,
        transitionSpec = {
            if (targetState) {
                // Now Playing -> Queue Screen (Slide up from bottom with spring)
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)) togetherWith
                slideOutVertically(
                    targetOffsetY = { -it / 4 },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(tween(180))
            } else {
                // Queue Screen -> Now Playing (Slide down smoothly)
                slideInVertically(
                    initialOffsetY = { -it / 4 },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)) togetherWith
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(tween(180))
            }
        },
        label = "now_playing_queue_switch"
    ) { isQueueOpen ->
        if (isQueueOpen) {
            QueueScreen(
                state = state,
                onSongClick = { s -> onSongSelect(s) },
                onRemoveFromQueue = onRemoveFromQueue,
                onRestoreToQueue = onRestoreToQueue,
                onReorderQueue = onReorderQueue,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onToggleFavorite = onToggleFavorite,
                onToggleQueueLock = { /* Handled via playlistQueueActive */ },
                onStartRadio = { /* Starts radio from seed */ },
                onAddToPlaylist = { s, pid -> onAddToPlaylist(pid) },
                onPlayNext = { /* Handled via ViewModel */ },
                onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
                onBack = { showQueueView = false }
            )
        } else {
            // Main Now Playing View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = swipeOffsetY.value.coerceAtLeast(0f)
                    }
                    .background(colors.background)
            ) {
                // 🌌 Full-screen Animated Liquid Mesh Gradient Background
                FluidMeshGradientBackground(
                    dominantColor = animatedDominant,
                    secondaryColor = animatedSecondary,
                    accentColor = animatedAccent,
                    backgroundColor = colors.background,
                    isPlaying = state.playbackState is PlaybackState.Playing,
                    isHeaderMode = false
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (swipeOffsetY.value > 120f) {
                                        onBack()
                                    } else {
                                        scope.launch {
                                            swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (dragAmount.y > 0f || swipeOffsetY.value > 0f) {
                                        change.consume()
                                        scope.launch {
                                            swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount.y).coerceAtLeast(0f))
                                        }
                                    }
                                }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Centered Header: "Now Playing" and Song Title
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Now Playing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.70f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song?.title ?: "Dreamin",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 2. Center View: 1:1 Large Rounded Artwork (or Synced Lyrics overlay)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isLyricsViewOpen) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.92f)
                                    .clip(RoundedCornerShape(24.dp))
                            ) {
                                SyncedLyricsView(
                                    lyricsState = state.lyricsState,
                                    progressFlow = progressFlow,
                                    onSeek = onSeek,
                                    onRetry = onRetryLyrics,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            // Large 1:1 Rounded Artwork with Soft Ambient Drop Glow
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        translationX = artworkOffsetX.value
                                        rotationZ = (artworkOffsetX.value * 0.035f).coerceIn(-8f, 8f)
                                    }
                                    .pointerInput(onNext, onPrevious) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (artworkOffsetX.value < -80f) {
                                                    triggerNextAnimated()
                                                } else if (artworkOffsetX.value > 80f) {
                                                    triggerPreviousAnimated()
                                                } else {
                                                    scope.launch {
                                                        artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f))
                                                    }
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    artworkOffsetX.snapTo(artworkOffsetX.value + dragAmount)
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Subtle ambient glow
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.95f)
                                        .drawBehind {
                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        animatedDominant.copy(alpha = 0.40f),
                                                        Color.Transparent
                                                    ),
                                                    center = center,
                                                    radius = size.width * 0.70f
                                                )
                                            )
                                        }
                                )

                                AsyncImage(
                                    model = song?.displayArtworkUrl,
                                    contentDescription = song?.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(24.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // 3. Track Info Row (Song Title, Artist, 3-dots, Heart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song?.title ?: "No Track Playing",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = song?.artist ?: "Dreamin",
                                fontSize = 15.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    song?.artist?.let { onArtistClick(it) }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Three dots circular frosted button
                        var showOptionsMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showOptionsMenu = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                                containerColor = colors.surfaceContainer
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Stream Details", color = Color.White) },
                                    onClick = {
                                        showOptionsMenu = false
                                        showAudioStreamDetailsSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Info, null, tint = colors.primary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Equalizer", color = Color.White) },
                                    onClick = {
                                        showOptionsMenu = false
                                        showEqualizerSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, tint = colors.primary) }
                                )
                                if (onUpdateSongArtwork != null && song != null) {
                                    DropdownMenuItem(
                                        text = { Text("Change Poster", color = Color.White) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showPosterPicker = true
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Image, null, tint = colors.primary) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist", color = Color.White) },
                                    onClick = {
                                        showOptionsMenu = false
                                        showPlaylistPicker = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist", tint = colors.primary) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Heart Favorite circular frosted button
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.currentSongIsFavorite) colors.primary.copy(alpha = 0.35f)
                                    else Color.White.copy(alpha = 0.12f)
                                )
                        ) {
                            Icon(
                                imageVector = if (state.currentSongIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (state.currentSongIsFavorite) colors.primary else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 4. Scrubber Slider
                    SleekScrubberSlider(
                        progressFlow = progressFlow,
                        onSeek = onSeek
                    )

                    // 5. Time & Audio Badge Row ("0:00", "OPUS • 142 kbps", total duration)
                    val playbackProgress by progressFlow.collectAsStateWithLifecycle()
                    val currentPos = playbackProgress.currentPositionMs / 1000
                    val durPos = playbackProgress.durationMs / 1000
                    val currentStr = String.format("%d:%02d", currentPos / 60, currentPos % 60)
                    val durStr = String.format("%d:%02d", durPos / 60, durPos % 60)

                    val audioBadgeText = remember(state.currentAudioFormat) {
                        val fmt = state.currentAudioFormat
                        if (fmt != null) {
                            "${fmt.codec.uppercase()} • ${fmt.bitrateKbps} kbps"
                        } else {
                            "OPUS • 142 kbps"
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentStr,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.70f),
                            fontWeight = FontWeight.Medium
                        )

                        // Frosted pill chip for Audio Stream Info
                        Surface(
                            onClick = { showAudioStreamDetailsSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = audioBadgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Text(
                            text = durStr,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.70f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 6. Playback Controls Row (Skip Previous, Solid Play/Pause, Skip Next)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Skip Previous (Double Triangle)
                        IconButton(
                            onClick = { triggerPreviousAnimated() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastRewind,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Large Solid White Play / Pause Button
                        val isPlaying = state.playbackState is PlaybackState.Playing
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF1E1E24),
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Skip Next (Double Triangle)
                        IconButton(
                            onClick = { triggerNextAnimated() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // 7. Bottom Action Bar (Queue Icon, Center Pill with Speaker & Timer, Lyrics Icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Queue Button (Opens Queue Screen)
                        IconButton(
                            onClick = { showQueueView = true },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatListBulleted,
                                contentDescription = "Queue",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Center Pill: Speaker/Equalizer + Sleep Timer
                        val isTimerActive = state.sleepTimerEndMs != null
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Equalizer / Speaker button
                                IconButton(
                                    onClick = { showEqualizerSheet = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = "Equalizer",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Sleep Timer button
                                IconButton(
                                    onClick = {
                                        if (isTimerActive) onCancelSleepTimer()
                                        else showSleepTimerDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = "Sleep Timer",
                                        tint = if (isTimerActive) colors.primary else Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Lyrics Button
                        IconButton(
                            onClick = onToggleLyrics,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatQuote,
                                contentDescription = "Lyrics",
                                tint = if (state.isLyricsViewOpen) colors.primary else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Sheets & Dialogs
    if (showAudioStreamDetailsSheet) {
        AudioStreamDetailsSheet(
            state = state,
            song = song,
            onDismiss = { showAudioStreamDetailsSheet = false }
        )
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showPosterPicker && song != null && onUpdateSongArtwork != null) {
        PosterPickerBottomSheet(
            song = song,
            onDismiss = { showPosterPicker = false },
            onPosterSelected = { poster ->
                onUpdateSongArtwork(song, poster)
                showPosterPicker = false
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentEndMs = state.sleepTimerEndMs,
            onSetMinutes = { mins ->
                onSetSleepTimer(mins)
                showSleepTimerDialog = false
            },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showPlaylistPicker && song != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to Playlist", color = Color.White) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.playlists.forEach { pl ->
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

/**
 * ⏱️ Sleep Timer Selection Dialog.
 */
@Composable
private fun SleepTimerDialog(
    currentEndMs: Long?,
    onSetMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val options = listOf(15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (currentEndMs != null) {
                    val remainingMins = ((currentEndMs - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                    Text(
                        text = "Timer active: stops in $remainingMins min",
                        color = colors.primary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                options.forEach { mins ->
                    Text(
                        text = "$mins minutes",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetMinutes(mins) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (currentEndMs != null) {
                TextButton(onClick = onCancel) {
                    Text("Turn Off Timer", color = Color(0xFFFF6B6B))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surfaceContainer
    )
}
