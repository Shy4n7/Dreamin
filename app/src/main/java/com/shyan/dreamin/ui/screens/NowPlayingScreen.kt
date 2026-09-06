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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
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
fun NowPlayingProgressSlider(
    progressFlow: StateFlow<PlaybackProgress>,
    songId: String = "",
    isPlaying: Boolean = true,
    onSeek: (Long) -> Unit,
    activeColor: Color = LocalDreaminColors.current.primary,
    secondaryColor: Color = LocalDreaminColors.current.secondary
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    val playbackProgress by progressFlow.collectAsStateWithLifecycle()
    val currentPositionMs = playbackProgress.currentPositionMs
    val durationMs = playbackProgress.durationMs
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress) }
    if (!isSeeking) seekProgress = progress
    val displayProgress = if (isSeeking) seekProgress else progress

    // Continuous live dancing wave animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "anim_phase"
    )
    val thumbHaloRadius by infiniteTransition.animateFloat(
        initialValue = 7.5f,
        targetValue = 11.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thumb_halo"
    )

    // Generate 48 deterministic acoustic amplitude bars tailored to songId
    val barCount = 48
    val waveAmplitudes = remember(songId) {
        val seed = (songId.hashCode() and 0xFFFF).toFloat()
        FloatArray(barCount) { i ->
            val norm = i.toFloat() / barCount
            // Acoustic song envelope: intro buildup, chorus drops at ~35% and ~75%, bridge and outro
            val envelope = (sin(norm * Math.PI.toFloat())).coerceIn(0.25f, 1f)
            val wave1 = sin(i * 0.42f + seed * 0.08f) * 0.38f
            val wave2 = cos(i * 0.88f + seed * 0.19f) * 0.28f
            val wave3 = sin(i * 1.75f + seed * 0.45f) * 0.16f
            val raw = 0.52f + wave1 + wave2 + wave3
            (raw * envelope).coerceIn(0.18f, 1.0f)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        seekProgress = newProgress
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnSeek((newProgress * durationMs).toLong())
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
                            currentOnSeek((seekProgress * durationMs).toLong())
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = {
                            isSeeking = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            seekProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalGapSpace = canvasWidth * 0.30f
                val spacing = totalGapSpace / (barCount - 1)
                val barWidth = (canvasWidth - totalGapSpace) / barCount
                val maxBarHeight = canvasHeight * 0.88f
                val minBarHeight = 11.dp.toPx()
                val unplayedHeight = 9.5.dp.toPx()

                val activeX = displayProgress * canvasWidth

                for (i in 0 until barCount) {
                    val barX = i * (barWidth + spacing)
                    val baseAmp = waveAmplitudes[i]

                    // Smooth morph factor: 0.0 (unplayed sleek pill) -> 1.0 (played animated wave)
                    val transitionWidth = (barWidth + spacing) * 1.2f
                    val waveFactor = ((activeX - (barX - transitionWidth * 0.3f)) / transitionWidth).coerceIn(0f, 1f)

                    // Live harmonic audio dancing ripple (animates only the played portion)
                    val dynamicAmp = if (isPlaying && waveFactor > 0f) {
                        val distFromHead = kotlin.math.abs(i - (displayProgress * barCount))
                        val headBoost = (1f - (distFromHead / 6f).coerceIn(0f, 1f)) * 0.22f
                        val waveOscillation = (sin(animPhase + i * 0.40f) * 0.16f) + (cos(animPhase * 2f + i * 0.25f) * 0.08f)
                        (baseAmp + waveOscillation + headBoost).coerceIn(0.15f, 1.0f)
                    } else {
                        baseAmp
                    }

                    // Morph height from unplayed sleek vertical pill to sculpted acoustic wave
                    val fullWaveHeight = minBarHeight + (maxBarHeight - minBarHeight) * dynamicAmp
                    val barHeight = unplayedHeight + (fullWaveHeight - unplayedHeight) * waveFactor
                    val barY = (canvasHeight - barHeight) / 2f

                    val barColor = if (waveFactor >= 0.99f) {
                        activeColor
                    } else if (waveFactor <= 0.01f) {
                        Color.White.copy(alpha = 0.22f)
                    } else {
                        androidx.compose.ui.graphics.lerp(
                            Color.White.copy(alpha = 0.22f),
                            activeColor,
                            waveFactor
                        )
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(barX, barY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // Glowing Neon Scrubber Bead at current position
                val minThumbX = 5.dp.toPx()
                val maxThumbX = (canvasWidth - minThumbX).coerceAtLeast(minThumbX)
                val thumbX = activeX.coerceIn(minThumbX, maxThumbX)
                val thumbY = canvasHeight / 2f

                // Outer ambient halo glow (pulsing when playing)
                val haloRadiusPx = if (isPlaying) thumbHaloRadius.dp.toPx() else 8.dp.toPx()
                drawCircle(
                    color = activeColor.copy(alpha = 0.38f),
                    radius = haloRadiusPx,
                    center = Offset(thumbX, thumbY)
                )
                // Inner bright bead
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = Offset(thumbX, thumbY)
                )
            }
        }

        val displayedPositionMs = if (isSeeking) {
            (seekProgress * durationMs).toLong()
        } else {
            currentPositionMs
        }
        val currentSecondMs = (displayedPositionMs.coerceAtLeast(0L) / 1000L) * 1000L

        Spacer(modifier = Modifier.height(2.dp))

        DurationLabelsRow(
            currentPositionMs = currentSecondMs,
            durationMs = durationMs,
            textColor = colors.onSurfaceVariant
        )
    }
}

/**
 * Formatted time display for current track position and total duration.
 */
@Composable
fun RollingTimeText(
    timeMs: Long,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier
) {
    Text(
        text = formatDuration(timeMs),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = LocalTextStyle.current.copy(
            fontFeatureSettings = "tnum"
        ),
        modifier = modifier
    )
}

@Composable
private fun DurationLabelsRow(
    currentPositionMs: Long,
    durationMs: Long,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RollingTimeText(
            timeMs = currentPositionMs,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        RollingTimeText(
            timeMs = durationMs,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

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
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showPosterPicker by remember { mutableStateOf(false) }
    val song = state.currentSong
    val colors = LocalDreaminColors.current

    BackHandler(enabled = true) { onBack() }

    val animatedDominant by animateColorAsState(
        targetValue = Color(state.dominantColor),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "dominant_anim"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = Color(state.secondaryColor),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "secondary_anim"
    )
    val animatedAccent by animateColorAsState(
        targetValue = Color(state.accentColor),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "accent_anim"
    )

    val swipeOffsetY = remember { Animatable(0f) }
    val artworkOffsetX = remember { Animatable(0f) }
    val artworkScope = rememberCoroutineScope()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val triggerNextAnimated = remember(onNext) {
        {
            artworkScope.launch {
                artworkOffsetX.animateTo(-380f, tween(130, easing = FastOutLinearInEasing))
                onNext()
                artworkOffsetX.snapTo(320f)
                artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    val triggerPreviousAnimated = remember(onPrevious) {
        {
            artworkScope.launch {
                artworkOffsetX.animateTo(380f, tween(130, easing = FastOutLinearInEasing))
                onPrevious()
                artworkOffsetX.snapTo(-320f)
                artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    var lastObservedSongId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(song?.id) {
        val currentId = song?.id
        val prevId = lastObservedSongId
        lastObservedSongId = currentId
        if (prevId != null && currentId != null && prevId != currentId && artworkOffsetX.value == 0f) {
            artworkOffsetX.snapTo(280f)
            artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = swipeOffsetY.value.coerceAtLeast(0f)
                clip = true
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                shadowElevation = if (swipeOffsetY.value > 0f) 24f else 0f
            }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.background)
    ) {
        // 🌌 Fluid Liquid Mesh Gradient Canvas (Apple Music Style Ambient Drift)
        FluidMeshGradientBackground(
            dominantColor = Color(state.dominantColor),
            secondaryColor = Color(state.secondaryColor),
            accentColor = Color(state.accentColor),
            backgroundColor = colors.background,
            isPlaying = state.playbackState is PlaybackState.Playing
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle pill at the top for intuitive swipe-down gesture
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            )

            // Header row with swipe-down dismissal detector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp)
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceHighest.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close player",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onToggleLyrics,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isLyricsViewOpen) animatedDominant.copy(alpha = 0.28f)
                                else colors.surfaceHighest.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatQuote,
                            contentDescription = "Lyrics",
                            tint = if (state.isLyricsViewOpen) animatedSecondary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceHighest.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerEndMs != null) animatedSecondary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            var currentClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(state.sleepTimerEndMs) {
                if (state.sleepTimerEndMs != null) {
                    while (true) {
                        currentClockMs = System.currentTimeMillis()
                        delay(1000)
                    }
                }
            }

            AnimatedVisibility(visible = state.sleepTimerEndMs != null) {
                val remainingTotalSec = (((state.sleepTimerEndMs ?: 0L) - currentClockMs) / 1000L).coerceAtLeast(0L)
                val label = if (remainingTotalSec >= 60) "${(remainingTotalSec + 59) / 60}m" else "${remainingTotalSec}s"
                SleepTimerChip(
                    timeRemainingLabel = label,
                    onCancel = onCancelSleepTimer
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (song == null) {
                Text(
                    "No song playing",
                    color = colors.onSurfaceVariant,
                    fontSize = 18.sp
                )
                return@Column
            }

            var showHeartBurst by remember { mutableStateOf(false) }
            val heartScale by animateFloatAsState(
                targetValue = if (showHeartBurst) 1f else 0f,
                animationSpec = if (showHeartBurst)
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                else
                    tween(150),
                label = "heart_burst",
                finishedListener = { if (showHeartBurst) showHeartBurst = false }
            )

            // Center View: Crossfade between 3D Artwork and Synced Lyrics
            AnimatedContent(
                targetState = state.isLyricsViewOpen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "artwork_lyrics_switch"
            ) { lyricsOpen ->
                if (lyricsOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
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
                    // Floating 3D Artwork with Unified Disambiguated Gestures (Horizontal Skip + Downward Sheet Dismiss)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .pointerInput(onNext, onPrevious, onBack) {
                                    var isHorizontalDrag = false
                                    var isVerticalDrag = false
                                    detectDragGestures(
                                        onDragStart = {
                                            isHorizontalDrag = false
                                            isVerticalDrag = false
                                        },
                                        onDragEnd = {
                                            if (isVerticalDrag) {
                                                if (swipeOffsetY.value > 120f) {
                                                    onBack()
                                                } else {
                                                    scope.launch {
                                                        swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                                                    }
                                                }
                                            }
                                            if (isHorizontalDrag) {
                                                val currentOffset = artworkOffsetX.value
                                                if (currentOffset < -75f) {
                                                    triggerNextAnimated()
                                                } else if (currentOffset > 75f) {
                                                    triggerPreviousAnimated()
                                                } else {
                                                    artworkScope.launch {
                                                        artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                                    }
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch {
                                                swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                                            }
                                            artworkScope.launch {
                                                artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            val absX = kotlin.math.abs(dragAmount.x)
                                            val absY = kotlin.math.abs(dragAmount.y)

                                            if (!isHorizontalDrag && !isVerticalDrag) {
                                                if (absX > absY * 1.15f && absX > 4f) {
                                                    isHorizontalDrag = true
                                                } else if (dragAmount.y > 0f && absY > absX * 1.15f && absY > 4f) {
                                                    isVerticalDrag = true
                                                }
                                            }

                                            if (isHorizontalDrag) {
                                                change.consume()
                                                artworkScope.launch {
                                                    val next = (artworkOffsetX.value + dragAmount.x).coerceIn(-260f, 260f)
                                                    artworkOffsetX.snapTo(next)
                                                }
                                            } else if (isVerticalDrag) {
                                                if (dragAmount.y > 0f || swipeOffsetY.value > 0f) {
                                                    change.consume()
                                                    scope.launch {
                                                        val next = (swipeOffsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                        swipeOffsetY.snapTo(next)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                .graphicsLayer {
                                    val dragX = artworkOffsetX.value
                                    val pullY = swipeOffsetY.value
                                    translationX = dragX
                                    rotationZ = (dragX / 20f).coerceIn(-10f, 10f)
                                    rotationY = (-dragX / 8f).coerceIn(-22f, 22f)
                                    rotationX = (pullY / 12f).coerceIn(0f, 18f)
                                    val dynamicScale = (1f - (kotlin.math.abs(dragX) / 1100f) - (pullY / 1600f)).coerceIn(0.88f, 1f)
                                    scaleX = dynamicScale
                                    scaleY = dynamicScale
                                    cameraDistance = 16f * density
                                    transformOrigin = TransformOrigin.Center
                                    compositingStrategy = CompositingStrategy.Auto
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val infiniteAura = rememberInfiniteTransition(label = "now_playing_aura")
                            val isPlayingTrack = state.playbackState is PlaybackState.Playing
                            val auraScale by infiniteAura.animateFloat(
                                initialValue = 1.0f,
                                targetValue = if (isPlayingTrack) 1.06f else 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2600, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "aura_scale"
                            )
                            val auraAlpha by infiniteAura.animateFloat(
                                initialValue = 0.35f,
                                targetValue = if (isPlayingTrack) 0.65f else 0.30f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2600, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "aura_alpha"
                            )

                            // 🌟 Breathing Ambient Radial Glow
                            Box(
                                modifier = Modifier
                                    .size(270.dp)
                                    .graphicsLayer {
                                        scaleX = auraScale
                                        scaleY = auraScale
                                        alpha = auraAlpha
                                    }
                                    .blur(48.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                animatedDominant.copy(alpha = 0.75f),
                                                animatedSecondary.copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
                                        ),
                                        CircleShape
                                    )
                            )

                            val artworkShape = remember { RoundedCornerShape(26.dp) }
                            Box(
                                modifier = Modifier
                                    .size(270.dp)
                                    .graphicsLayer {
                                        shape = artworkShape
                                        clip = true
                                        shadowElevation = 20f
                                        compositingStrategy = CompositingStrategy.Auto
                                    }
                                    .clip(artworkShape)
                                    .drawWithContent {
                                        drawContent()
                                        val dragX = artworkOffsetX.value
                                        val sheenOffset = (dragX * 2.2f).coerceIn(-400f, 400f)
                                        val sheenAlpha = (kotlin.math.abs(dragX) / 100f).coerceIn(0f, 0.45f)
                                        val borderAlpha = (0.14f + (kotlin.math.abs(dragX) / 350f)).coerceIn(0.14f, 0.48f)

                                        // Draw smooth border in Draw Phase
                                        drawOutline(
                                            outline = artworkShape.createOutline(size, layoutDirection, this),
                                            color = Color.White.copy(alpha = borderAlpha),
                                            style = Stroke(width = 1.5.dp.toPx())
                                        )

                                        // 🎚️ Holographic Vinyl Specular Sheen in Draw Phase
                                        if (sheenAlpha > 0.005f) {
                                            drawRect(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0xFFFF80BF).copy(alpha = sheenAlpha * 0.32f),
                                                        Color.White.copy(alpha = sheenAlpha * 0.65f),
                                                        Color(0xFF80D8FF).copy(alpha = sheenAlpha * 0.32f),
                                                        Color.Transparent
                                                    ),
                                                    start = Offset(sheenOffset - 100f, -50f),
                                                    end = Offset(sheenOffset + 240f, 320f)
                                                )
                                            )
                                        }
                                    }
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                        onLongClick = {
                                            showPosterPicker = true
                                        },
                                        onDoubleClick = {
                                            onToggleFavorite()
                                            showHeartBurst = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = "${song.id}_${song.displayArtworkUrl}",
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                                    },
                                    label = "artwork_cinematic_transition"
                                ) { _ ->
                                    AsyncImage(
                                        model = song.displayArtworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                if (heartScale > 0f) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = heartScale.coerceIn(0f, 1f)),
                                        modifier = Modifier
                                            .size(72.dp)
                                            .scale(heartScale)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Artist + title with basicMarquee and Download + Favorite actions
            AnimatedContent(
                targetState = song.id,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(130))
                },
                label = "song_meta_crossfade"
            ) { _ ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.displayTitle,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            song.artist,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onArtistClick(song.artist) }
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val isDownloaded = state.downloadedSongs.any { it.id == song.id }
                    val isDownloading = state.downloadingSongIds.contains(song.id)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (state.playlists.isEmpty()) {
                                    showCreatePlaylistDialog = true
                                } else {
                                    showPlaylistPicker = true
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceHighest.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isDownloaded) onDeleteDownload(song.id)
                                else onDownload(song)
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceHighest.copy(alpha = 0.5f))
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = animatedSecondary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.Download,
                                    contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                    tint = if (isDownloaded) animatedSecondary else colors.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        FavoriteSparkleButton(
                            isFavorite = state.currentSongIsFavorite,
                            onClick = onToggleFavorite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            NowPlayingProgressSlider(
                progressFlow = progressFlow,
                songId = state.currentSong?.id.orEmpty(),
                isPlaying = state.playbackState is PlaybackState.Playing,
                onSeek = onSeek,
                activeColor = animatedDominant,
                secondaryColor = animatedSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Shuffle / Repeat row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shuffleTint by animateColorAsState(
                    targetValue = if (state.isShuffle) colors.primary else colors.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "shuffle_tint"
                )
                val shuffleRotation by animateFloatAsState(
                    targetValue = if (state.isShuffle) 360f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "shuffle_rot"
                )
                val shuffleScale by animateFloatAsState(
                    targetValue = if (state.isShuffle) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "shuffle_scale"
                )
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (state.isShuffle) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (state.isShuffle) colors.primary.copy(alpha = 0.45f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = shuffleTint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                rotationZ = shuffleRotation
                                scaleX = shuffleScale
                                scaleY = shuffleScale
                            }
                    )
                }

                // Playback controls — prev / play / next encased in frosted glass capsule
                val controlsShape = RoundedCornerShape(36.dp)
                Box(
                    modifier = Modifier
                        .clip(controlsShape)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    animatedDominant.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            ),
                            shape = controlsShape
                        )
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.surfaceHighest.copy(alpha = 0.65f),
                                    colors.surfaceHigh.copy(alpha = 0.45f)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prevInteraction = remember { MutableInteractionSource() }
                        val isPrevPressed by prevInteraction.collectIsPressedAsState()
                        val prevNudgeX by animateFloatAsState(
                            targetValue = if (isPrevPressed) -10f else 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "prev_nudge"
                        )
                        val prevScale by animateFloatAsState(
                            targetValue = if (isPrevPressed) 0.82f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                            label = "prev_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    translationX = prevNudgeX.dp.toPx()
                                    scaleX = prevScale
                                    scaleY = prevScale
                                }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = prevInteraction,
                                    indication = ripple(bounded = false, radius = 24.dp, color = animatedDominant)
                                ) { triggerPreviousAnimated() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = colors.onSurface, modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        val isLoading = state.playbackState == PlaybackState.Loading
                        val isPlaying = state.playbackState is PlaybackState.Playing
                        val playInteraction = remember { MutableInteractionSource() }
                        val isPlayPressed by playInteraction.collectIsPressedAsState()
                        val playScale by animateFloatAsState(
                            targetValue = if (isPlayPressed) 0.84f else if (isPlaying) 1.05f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "hero_play_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .graphicsLayer {
                                    val s = if (isLoading) 0.95f else playScale
                                    scaleX = s
                                    scaleY = s
                                    shadowElevation = (if (isPlayPressed) 6f else 18f).dp.toPx()
                                    shape = CircleShape
                                    clip = false
                                }
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(animatedDominant, animatedSecondary)
                                    )
                                )
                                .clickable(
                                    interactionSource = playInteraction,
                                    indication = ripple(bounded = false, color = Color.White.copy(alpha = 0.45f), radius = 36.dp),
                                    enabled = !isLoading
                                ) { onPlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                            } else {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = {
                                        (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn(tween(140)))
                                            .togetherWith(scaleOut(tween(90)) + fadeOut(tween(90)))
                                    },
                                    label = "play_pause_morph"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        val nextInteraction = remember { MutableInteractionSource() }
                        val isNextPressed by nextInteraction.collectIsPressedAsState()
                        val nextNudgeX by animateFloatAsState(
                            targetValue = if (isNextPressed) 10f else 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "next_nudge"
                        )
                        val nextScale by animateFloatAsState(
                            targetValue = if (isNextPressed) 0.82f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                            label = "next_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    translationX = nextNudgeX.dp.toPx()
                                    scaleX = nextScale
                                    scaleY = nextScale
                                }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = nextInteraction,
                                    indication = ripple(bounded = false, radius = 24.dp, color = animatedDominant)
                                ) { triggerNextAnimated() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = colors.onSurface, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                val repeatActive = state.repeatMode != TrackRepeatMode.OFF
                val repeatTint by animateColorAsState(
                    targetValue = if (repeatActive) colors.primary else colors.onSurfaceVariant,
                    animationSpec = tween(220),
                    label = "repeat_tint"
                )
                val repeatRotation by animateFloatAsState(
                    targetValue = when (state.repeatMode) {
                        TrackRepeatMode.OFF -> 0f
                        TrackRepeatMode.ALL -> 360f
                        TrackRepeatMode.ONE -> 720f
                    },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "repeat_rot"
                )
                val repeatScale by animateFloatAsState(
                    targetValue = if (repeatActive) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "repeat_scale"
                )
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (repeatActive) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (repeatActive) colors.primary.copy(alpha = 0.45f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (state.repeatMode == TrackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                rotationZ = repeatRotation
                                scaleX = repeatScale
                                scaleY = repeatScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            val upcomingSlice = remember(state.queue, state.currentSong) {
                val idx = state.queue.indexOfFirst { it.id == state.currentSong?.id }
                val from = if (idx >= 0) idx + 1 else 0
                state.queue.drop(from).take(8)
            }
            UpNextTimeline(
                upcomingSongs = upcomingSlice,
                onSongClick = { song -> onSongSelect(song) },
                onRemoveFromQueue = onRemoveFromQueue,
                onRestoreToQueue = onRestoreToQueue,
                onClearQueue = onClearQueue,
                onReorder = { fromSliceIdx, toSliceIdx ->
                    val currentIdx = state.queue.indexOfFirst { it.id == state.currentSong?.id }
                    val queueFrom = (if (currentIdx >= 0) currentIdx + 1 else 0) + fromSliceIdx
                    val queueTo = (if (currentIdx >= 0) currentIdx + 1 else 0) + toSliceIdx
                    onReorderQueue(queueFrom, queueTo)
                },
                onSaveQueueAsPlaylist = { showSavePlaylistDialog = true },
                isFetchingUpNext = state.isFetchingUpNext,
                snackbarHostState = snackbarHostState,
                fullQueue = state.queue,
                currentSongId = state.currentSong?.id
            )

            Spacer(modifier = Modifier.height(16.dp))

            val isTimerActive = state.sleepTimerEndMs != null
            TextButton(
                onClick = {
                    if (isTimerActive) onCancelSleepTimer()
                    else showSleepTimerDialog = true
                }
            ) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = "Sleep Timer",
                    tint = if (isTimerActive) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isTimerActive) {
                        val remaining = ((state.sleepTimerEndMs!! - System.currentTimeMillis()) / 60_000)
                            .coerceAtLeast(0L)
                        "Sleep in ${remaining}m — tap to cancel"
                    } else "Sleep timer",
                    color = if (isTimerActive) colors.primary else colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.surfaceHighest,
                contentColor = colors.onSurface,
                actionColor = colors.primary,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }

    if (showEqualizerSheet) {
        EqualizerBottomSheet(
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showPosterPicker && song != null) {
        com.shyan.dreamin.ui.components.PosterPickerBottomSheet(
            song = song,
            onDismiss = { showPosterPicker = false },
            onPosterSelected = { newPoster ->
                onUpdateSongArtwork?.invoke(song, newPoster)
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onSelect = { minutes ->
                onSetSleepTimer(minutes)
                showSleepTimerDialog = false
            }
        )
    }

    if (showPlaylistPicker) {
        AddToPlaylistDialog(
            playlists = state.playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = { playlistId ->
                val targetPlaylist = state.playlists.find { it.id == playlistId }
                val targetName = targetPlaylist?.name ?: "Playlist"
                onAddToPlaylist(playlistId)
                showPlaylistPicker = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Added to \"$targetName\"",
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onCreateNew = {
                showPlaylistPicker = false
                showCreatePlaylistDialog = true
            }
        )
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { playlistName, coverUri ->
                onCreatePlaylist(playlistName, coverUri)
                showCreatePlaylistDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Created \"$playlistName\"",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    if (showSavePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePlaylistDialog = false },
            title = { Text("Save Queue as Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    placeholder = { Text("Playlist name (e.g. Current Queue)", color = colors.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.4f),
                        cursorColor = colors.primary,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceHigh,
                        unfocusedContainerColor = colors.surfaceHigh
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            onSaveQueueAsPlaylist(playlistName.trim())
                            showSavePlaylistDialog = false
                        }
                    },
                    enabled = playlistName.isNotBlank()
                ) {
                    Text("Save", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePlaylistDialog = false }) { Text("Cancel", color = colors.onSurfaceVariant) }
            },
            containerColor = colors.surfaceHigh,
            titleContentColor = colors.onSurface
        )
    }
}

@Composable
fun FavoriteSparkleButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(1f) }
    val sparkleAnim = remember { Animatable(0f) }

    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            heartScale.snapTo(0.68f)
            sparkleAnim.snapTo(0f)
            scope.launch {
                heartScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            scope.launch {
                sparkleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )
            }
        } else {
            sparkleAnim.snapTo(0f)
            heartScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // ✨ 360° Particle Sparkle Ring Eruption
        if (sparkleAnim.value in 0.01f..0.99f) {
            val progress = sparkleAnim.value
            Canvas(modifier = Modifier.size(60.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val distance = 16.dp.toPx() + (14.dp.toPx() * progress)
                val particleRadius = (2.8.dp.toPx() * (1f - progress)).coerceAtLeast(0.5f)
                val particleAlpha = (1f - progress).coerceIn(0f, 1f)

                for (i in 0 until 6) {
                    val angleRad = Math.toRadians((i * 60.0) + (progress * 25.0))
                    val px = (center.x + distance * kotlin.math.cos(angleRad)).toFloat()
                    val py = (center.y + distance * kotlin.math.sin(angleRad)).toFloat()
                    drawCircle(
                        color = colors.error.copy(alpha = particleAlpha),
                        radius = particleRadius,
                        center = Offset(px, py)
                    )
                    // Secondary gold sparkle
                    val goldDist = distance * 0.75f
                    val goldAngle = Math.toRadians((i * 60.0) + 30.0 - (progress * 20.0))
                    val gx = (center.x + goldDist * kotlin.math.cos(goldAngle)).toFloat()
                    val gy = (center.y + goldDist * kotlin.math.sin(goldAngle)).toFloat()
                    drawCircle(
                        color = colors.secondary.copy(alpha = particleAlpha * 0.85f),
                        radius = particleRadius * 0.75f,
                        center = Offset(gx, gy)
                    )
                }
            }
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surfaceHighest.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) colors.error else colors.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .scale(heartScale.value)
            )
        }
    }
}

@Composable
fun SleepTimerChip(timeRemainingLabel: String, onCancel: () -> Unit) {
    val colors = LocalDreaminColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "sleep_timer_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_border_alpha"
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, colors.primary.copy(alpha = pulseBorderAlpha), CircleShape)
            .background(colors.primary.copy(alpha = pulseAlpha), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onCancel() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        Text(
            "Sleeping in $timeRemainingLabel  ✕",
            color = colors.primary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val colors = LocalDreaminColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sleep Timer", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(
                        onClick = { onSelect(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "$minutes minutes",
                            color = colors.primary,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.onSurfaceVariant)
            }
        },
        containerColor = colors.surfaceHigh,
        titleContentColor = colors.onSurface
    )
}
