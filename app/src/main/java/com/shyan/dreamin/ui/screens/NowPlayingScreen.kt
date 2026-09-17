@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalDensity
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
import com.shyan.dreamin.ui.components.FluidMeshGradientBackground
import com.shyan.dreamin.ui.components.PosterPickerBottomSheet
import com.shyan.dreamin.ui.components.SyncedLyricsView
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 🎚️ Harmonic Acoustic Waveform Slider with live dancing ripple animation when playing.
 */
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RollingTimeText(
                timeMs = currentSecondMs,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            RollingTimeText(
                timeMs = durationMs,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * ⏱️ Formatted time display for current track position and total duration.
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
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showPosterPicker by remember { mutableStateOf(false) }
    var showAudioStreamDetailsSheet by remember { mutableStateOf(false) }

    val song = state.currentSong
    val colors = LocalDreaminColors.current

    BackHandler(enabled = true) {
        if (pagerState.currentPage > 0) {
            scope.launch {
                pagerState.animateScrollToPage(
                    page = 0,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                )
            }
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
    val density = LocalDensity.current.density

    var showHeartBurst by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }
    val heartButtonScale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            heartScale.snapTo(0.6f)
            heartScale.animateTo(1.3f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            heartScale.animateTo(0f, tween(180))
            showHeartBurst = false
        }
    }

    val triggerFavoriteWithAnim = remember(onToggleFavorite) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            artworkScope.launch {
                heartButtonScale.animateTo(0.70f, tween(70, easing = FastOutLinearInEasing))
                heartButtonScale.animateTo(1.28f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                heartButtonScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            }
            onToggleFavorite()
        }
    }

    val currentSongId = state.currentSong?.id
    var previousSongId by remember { mutableStateOf(currentSongId) }
    LaunchedEffect(currentSongId) {
        if (previousSongId != null && currentSongId != null && previousSongId != currentSongId && artworkOffsetX.value == 0f) {
            artworkOffsetX.snapTo(280f)
            artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
        }
        previousSongId = currentSongId
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = swipeOffsetY.value.coerceAtLeast(0f)
            }
            .background(colors.background)
    ) {
        // 🌌 Full-screen Animated Liquid Mesh Gradient Background spanning both pages
        FluidMeshGradientBackground(
            dominantColor = Color(state.dominantColor),
            secondaryColor = Color(state.secondaryColor),
            accentColor = Color(state.accentColor),
            backgroundColor = colors.background,
            isPlaying = state.playbackState is PlaybackState.Playing,
            isHeaderMode = false
        )

        // 🌑 Smooth Non-Aggressive Queue Dimming Scrim Layer
        // Gently dims the background brightness as the user navigates towards the Queue screen.
        // Reading pager progress inside graphicsLayer executes entirely in the Draw phase,
        // ensuring zero recompositions and 120 FPS fluid gestures without any harsh visual cuts.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                    alpha = progress * 0.40f
                }
                .background(Color.Black)
        )

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> if (page == 0) "now_playing" else "queue" }
        ) { page ->
            if (page == 0) {
                // Main Now Playing View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Header with Collapse Arrow on Top Left + Centered Drag handle & Title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse Now Playing",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 48.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
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
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dragAmount > 0f || swipeOffsetY.value > 0f) {
                                                change.consume()
                                                scope.launch {
                                                    swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount).coerceAtLeast(0f))
                                                }
                                            }
                                        }
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
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

                        // ⏱️ Sleep Timer in Top Right (clean icon, no pill background)
                        val isTimerActive = state.sleepTimerEndMs != null
                        val timerText = remember(state.sleepTimerEndMs) {
                            state.sleepTimerEndMs?.let { endMs ->
                                val remainingMins = ((endMs - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                                "${remainingMins}m"
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false, color = Color.White.copy(alpha = 0.35f), radius = 22.dp)
                                ) {
                                    if (isTimerActive) onCancelSleepTimer()
                                    else showSleepTimerDialog = true
                                }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (isTimerActive) colors.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (timerText != null) {
                                    Text(
                                        text = timerText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
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
                            // 3D Floating Artwork with Perspective Tilt, Dynamic Sheen & Breathing Aura
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

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // 🌟 Breathing Ambient Radial Glow Aura
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.96f)
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
                                        .fillMaxSize()
                                        .pointerInput(onNext, onPrevious) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    val currentOffset = artworkOffsetX.value
                                                    if (currentOffset < -75f) {
                                                        triggerNextAnimated()
                                                    } else if (currentOffset > 75f) {
                                                        triggerPreviousAnimated()
                                                    } else {
                                                        artworkScope.launch {
                                                            artworkOffsetX.animateTo(
                                                                0f,
                                                                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                                            )
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    artworkScope.launch {
                                                        artworkOffsetX.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                                        )
                                                    }
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    artworkScope.launch {
                                                        val next = (artworkOffsetX.value + dragAmount).coerceIn(-260f, 260f)
                                                        artworkOffsetX.snapTo(next)
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
                                            shape = artworkShape
                                            clip = true
                                            shadowElevation = 24f
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
                                                triggerFavoriteWithAnim()
                                                showHeartBurst = true
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(
                                        targetState = "${song?.id}_${song?.displayArtworkUrl}",
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                                        },
                                        label = "artwork_cinematic_transition"
                                    ) { _ ->
                                        AsyncImage(
                                            model = song?.displayArtworkUrl,
                                            contentDescription = song?.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    if (heartScale.value > 0f) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = heartScale.value.coerceIn(0f, 1f)),
                                            modifier = Modifier
                                                .size(72.dp)
                                                .graphicsLayer {
                                                    scaleX = heartScale.value
                                                    scaleY = heartScale.value
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Track Info Row (Song Title, Artist, 3-dots, Heart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-24).dp)
                            .padding(bottom = 2.dp),
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
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = colors.primary) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Heart Favorite circular frosted button with tactile spring animation
                        val isFav = state.currentSongIsFavorite
                        val favBgColor by animateColorAsState(
                            targetValue = if (isFav) colors.primary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "fav_bg_color"
                        )
                        val favIconColor by animateColorAsState(
                            targetValue = if (isFav) colors.primary else Color.White,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "fav_icon_color"
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
                                label = "fav_icon_morph"
                            ) { fav ->
                                Icon(
                                    imageVector = if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (fav) "Remove from favorites" else "Add to favorites",
                                    tint = favIconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // 4. Harmonic Acoustic Waveform Slider with live dancing ripple animation
                    NowPlayingProgressSlider(
                        progressFlow = progressFlow,
                        songId = state.currentSong?.id.orEmpty(),
                        isPlaying = state.playbackState is PlaybackState.Playing,
                        onSeek = onSeek,
                        activeColor = animatedDominant,
                        secondaryColor = animatedSecondary
                    )

                    // 6. Playback Controls Row (Skip Previous, Dynamic Play/Pause, Skip Next)
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

                        // Dynamic Color Play / Pause Button with tactile spring & morph animation
                        val isLoading = state.playbackState == PlaybackState.Loading
                        val isPlaying = state.playbackState is PlaybackState.Playing
                        val playInteraction = remember { MutableInteractionSource() }
                        val isPlayPressed by playInteraction.collectIsPressedAsState()
                        val playScale by animateFloatAsState(
                            targetValue = if (isPlayPressed) 0.86f else if (isPlaying) 1.04f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "hero_play_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(72.dp)
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
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
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
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
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
                        // Queue Button (Scrolls down to Queue Screen below)
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = 1,
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                contentDescription = "Queue",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(24.dp)
                            )
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
            } else {
                // Queue Screen (Page 1 - visible directly below Now Playing upon scrolling)
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
                    onBack = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 0,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
                    backHandlerEnabled = pagerState.currentPage == 1,
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = Color.Transparent
                )
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
        val currentPlayback = progressFlow.value
        val remainingMs = (currentPlayback.durationMs - currentPlayback.currentPositionMs).coerceAtLeast(0L)
        SleepTimerDialog(
            currentEndMs = state.sleepTimerEndMs,
            remainingSongMs = remainingMs,
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
 * 🎚️ Custom Point/Dot Slider for Sleep Timer with 5-minute interval points.
 * Matches design reference with inactive track, active track, contrasting dots, and vertical thumb bar.
 */
@Composable
private fun SleepTimerPointSlider(
    selectedMinutes: Int,
    onMinutesChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pointCount: Int = 20, // 5 min interval per point: 5m, 10m, ..., 100m
    isEndOfSong: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val startPaddingDp = 14.dp
    val endPaddingDp = 14.dp
    val startPaddingPx = with(density) { startPaddingDp.toPx() }
    val endPaddingPx = with(density) { endPaddingDp.toPx() }

    fun updateFromOffset(x: Float) {
        val usableWidth = (trackWidthPx - startPaddingPx - endPaddingPx).coerceAtLeast(1f)
        val fraction = ((x - startPaddingPx) / usableWidth).coerceIn(0f, 1f)
        val index = (fraction * (pointCount - 1)).roundToInt().coerceIn(0, pointCount - 1)
        val mins = (index + 1) * 5
        if (mins != selectedMinutes) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onMinutesChanged(mins)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onGloballyPositioned { coordinates ->
                trackWidthPx = coordinates.size.width.toFloat()
            }
            .pointerInput(pointCount) {
                detectTapGestures { offset ->
                    updateFromOffset(offset.x)
                }
            }
            .pointerInput(pointCount) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateFromOffset(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val width = size.width
            val height = size.height
            val trackHeight = 20.dp.toPx()
            val trackTop = (height - trackHeight) / 2f
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            val usableWidth = (width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)
            val stepX = usableWidth / (pointCount - 1)
            val selectedIndex = (selectedMinutes / 5 - 1).coerceIn(0, pointCount - 1)
            val thumbX = startPaddingPx + selectedIndex * stepX
            val centerY = height / 2f

            // 1. Inactive dark background track
            drawRoundRect(
                color = Color(0xFF262C36),
                topLeft = Offset(0f, trackTop),
                size = Size(width, trackHeight),
                cornerRadius = cornerRadius
            )

            // 2. Inactive dots along the entire track
            for (i in 0 until pointCount) {
                val dotX = startPaddingPx + i * stepX
                drawCircle(
                    color = Color(0xFFA8C7FA).copy(alpha = 0.65f),
                    radius = 2.dp.toPx(),
                    center = Offset(dotX, centerY)
                )
            }

            // 3. Active light blue track & dark dots inside it
            if (!isEndOfSong) {
                val activeWidth = (thumbX + 6.dp.toPx()).coerceAtMost(width)
                drawRoundRect(
                    color = Color(0xFFA8C7FA),
                    topLeft = Offset(0f, trackTop),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = cornerRadius
                )

                // Active dark dots inside the active track
                for (i in 0..selectedIndex) {
                    val dotX = startPaddingPx + i * stepX
                    drawCircle(
                        color = Color(0xFF131B26),
                        radius = 2.dp.toPx(),
                        center = Offset(dotX, centerY)
                    )
                }

                // 4. Vertical thumb bar indicator
                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 32.dp.toPx()
                val thumbTop = (height - thumbHeight) / 2f
                drawRoundRect(
                    color = Color(0xFFD3E3FD),
                    topLeft = Offset(thumbX - thumbWidth / 2f, thumbTop),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * ⏱️ Redesigned Sleep Timer Dialog matching the point-slider design reference:
 * - Centered title & dynamic duration subtitle.
 * - 5-minute interval discrete point/dot slider with contrasting active/inactive dots.
 * - Outlined "End of song" button.
 * - "Reset", "Cancel", and "OK" actions.
 */
@Composable
private fun SleepTimerDialog(
    currentEndMs: Long?,
    remainingSongMs: Long,
    onSetMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val initialMins = remember(currentEndMs) {
        if (currentEndMs != null) {
            val remainingMins = ((currentEndMs - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(1)
            (((remainingMins + 2) / 5) * 5).coerceIn(5, 100)
        } else {
            50
        }
    }

    var selectedMinutes by remember { mutableIntStateOf(initialMins) }
    var isEndOfSong by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1E222A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Sleep timer",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subtitle / Current duration
                Text(
                    text = if (isEndOfSong) "End of song" else "$selectedMinutes minutes",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 5-Minute Interval Point Slider
                SleepTimerPointSlider(
                    selectedMinutes = selectedMinutes,
                    onMinutesChanged = { mins ->
                        selectedMinutes = mins
                        isEndOfSong = false
                    },
                    pointCount = 20, // 5 min interval: 5m to 100m
                    isEndOfSong = isEndOfSong,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // "End of song" outlined chip button
                Surface(
                    onClick = {
                        isEndOfSong = !isEndOfSong
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isEndOfSong) Color(0xFFA8C7FA).copy(alpha = 0.22f) else Color.Transparent,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isEndOfSong) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.height(42.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "End of song",
                            fontSize = 14.sp,
                            fontWeight = if (isEndOfSong) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isEndOfSong) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.90f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Action Buttons: Reset on left, Cancel & OK on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onCancel()
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = "Reset",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Cancel",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = {
                                if (isEndOfSong) {
                                    val endMins = ((remainingSongMs + 59999L) / 60000L).coerceAtLeast(1L).toInt()
                                    onSetMinutes(endMins)
                                } else {
                                    onSetMinutes(selectedMinutes)
                                }
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = "OK",
                                color = Color(0xFFA8C7FA),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
