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
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalDreaminColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceHighest.copy(alpha = 0.72f),
                        colors.surfaceHigh.copy(alpha = 0.64f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        content()
    }
}





@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomNavBar(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    hazeState: HazeState? = null
) {
    val colors = LocalDreaminColors.current
    val screens = Screen.entries
    val selectedIndex = screens.indexOf(currentScreen).coerceAtLeast(0)

    val hazeModifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeMaterials.ultraThin()
        )
    } else Modifier

    Surface(
        color = if (hazeState != null) colors.surfaceContainer.copy(alpha = 0.70f) else colors.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = hazeModifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth
            val tabWidth = totalWidth / screens.size
            val pillWidth = tabWidth * 0.68f

            val animatedPillOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex + (tabWidth - pillWidth) / 2,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "nav_pill_slide"
            )

            // 🔘 Smooth Sliding Background Pill
            Box(
                modifier = Modifier
                    .offset(x = animatedPillOffset)
                    .width(pillWidth)
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(colors.primary.copy(alpha = 0.16f))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEach { screen ->
                    val isSelected = screen == currentScreen
                    val tint by animateColorAsState(
                        targetValue   = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        animationSpec = tween(200),
                        label         = "nav_tint_${screen.name}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue   = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label         = "nav_scale_${screen.name}"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onScreenChange(screen) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) when (screen) {
                                Screen.Home    -> Icons.Filled.Home
                                Screen.Library -> Icons.Filled.LibraryMusic
                            } else screen.icon,
                            contentDescription = screen.title,
                            tint               = tint,
                            modifier           = Modifier
                                .size(24.dp)
                                .scale(iconScale)
                        )
                    }
                }
            }
        }
    }
}





@Composable
fun MiniPlayerProgressBar(
    progressFlow: StateFlow<PlaybackProgress>,
    accentColor: Color? = null
) {
    val colors = LocalDreaminColors.current
    val barColor = accentColor ?: colors.primary
    val trackColor = colors.surfaceHighest.copy(alpha = 0.45f)
    val progressState by progressFlow.collectAsStateWithLifecycle()
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .drawBehind {
                val progress = if (progressState.durationMs > 0) {
                    (progressState.currentPositionMs.toFloat() / progressState.durationMs).coerceIn(0f, 1f)
                } else 0f
                drawRect(trackColor)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            barColor.copy(alpha = 0.75f),
                            barColor,
                            Color.White.copy(alpha = 0.9f)
                        )
                    ),
                    size = size.copy(width = size.width * progress)
                )
            }
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayer(
    song: Song?,
    playbackState: PlaybackState,
    progressFlow: StateFlow<PlaybackProgress>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    onExpand: () -> Unit,
    dominantColor: Color? = null,
    hazeState: HazeState? = null
) {
    val s = song ?: return
    val colors = LocalDreaminColors.current
    val dynamicAura = dominantColor ?: colors.primary
    val animatedAura by animateColorAsState(
        targetValue = dynamicAura,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "mini_aura_color"
    )
    val isPlaying = playbackState is PlaybackState.Playing
    val speedMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "mini_shimmer_speed"
    )

    // Continuous monotonic time clock for seamless deceleration on pause without phase jumps
    var elapsedTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                    val clampedDt = dt.coerceIn(0.001f, 0.05f)
                    elapsedTime += clampedDt * speedMultiplier
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mini_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = if (isPlaying) 0.28f else 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mini_glow_alpha"
    )

    val swipeXAnim = remember { Animatable(0f) }
    val swipeYAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val expandInteraction = remember { MutableInteractionSource() }
    val isPressed by expandInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = DreaminMotion.TactileBouncy,
        label = "mini_press_scale"
    )

    val gleamIntensity by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "mini_shimmer_gleam_intensity"
    )

    val borderShimmerOffset = (elapsedTime * 360f) % 1800f - 400f
    val cardShape = RoundedCornerShape(20.dp)

    val dynamicBorderBrush = remember(animatedAura, borderShimmerOffset, gleamIntensity) {
        val baseAlpha = 0.18f + 0.05f * gleamIntensity
        val highlightAlpha = 0.20f + 0.25f * gleamIntensity
        val whiteAlpha = 0.08f + 0.67f * gleamIntensity

        Brush.linearGradient(
            colors = listOf(
                animatedAura.copy(alpha = baseAlpha),
                animatedAura.copy(alpha = highlightAlpha),
                Color.White.copy(alpha = whiteAlpha),
                animatedAura.copy(alpha = highlightAlpha),
                animatedAura.copy(alpha = baseAlpha)
            ),
            start = Offset(borderShimmerOffset, -50f),
            end = Offset(borderShimmerOffset + 400f, 150f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        // 🌌 Ambient Diffused Artwork Glow Aura behind the Floating Island
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 6.dp)
                .blur(22.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedAura.copy(alpha = glowAlpha),
                            animatedAura.copy(alpha = glowAlpha * 0.40f),
                            Color.Transparent
                        ),
                        radius = 450f
                    ),
                    cardShape
                )
        )

        // 🏝️ Floating Glassmorphic Pill Surface
        Surface(
            color = colors.surfaceHighest.copy(alpha = 0.86f),
            tonalElevation = 10.dp,
            shape = cardShape,
            border = BorderStroke(
                1.dp,
                dynamicBorderBrush
            ),
            modifier = (if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) else Modifier)
                .clip(cardShape)
        ) {
            Column {
                MiniPlayerProgressBar(
                    progressFlow = progressFlow,
                    accentColor = animatedAura
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .graphicsLayer {
                            translationX = swipeXAnim.value
                            translationY = swipeYAnim.value
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .pointerInput(onNext, onPrevious, onExpand) {
                            var totalX = 0f
                            var totalY = 0f
                            var isDragging = false
                            detectDragGestures(
                                onDragStart = {
                                    totalX = 0f
                                    totalY = 0f
                                    isDragging = false
                                },
                                onDrag = { change, dragAmount ->
                                    totalX += dragAmount.x
                                    totalY += dragAmount.y
                                    val absX = kotlin.math.abs(totalX)
                                    val absY = kotlin.math.abs(totalY)

                                    if (!isDragging && (absX > 10f || absY > 10f)) {
                                        isDragging = true
                                    }

                                    if (isDragging) {
                                        change.consume()
                                        if (absY > absX && totalY < -15f) {
                                            scope.launch { swipeYAnim.snapTo(totalY.coerceIn(-120f, 0f)) }
                                        } else if (absX > absY && absX > 15f) {
                                            scope.launch { swipeXAnim.snapTo(totalX.coerceIn(-200f, 200f)) }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (swipeYAnim.value < -60f) onExpand()
                                    when {
                                        swipeXAnim.value < -90f -> onNext()
                                        swipeXAnim.value > 90f -> onPrevious()
                                    }
                                    val snapSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                                    scope.launch {
                                        launch { swipeXAnim.animateTo(0f, snapSpring) }
                                        launch { swipeYAnim.animateTo(0f, snapSpring) }
                                    }
                                },
                                onDragCancel = {
                                    val snapSpring = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                                    scope.launch {
                                        launch { swipeXAnim.animateTo(0f, snapSpring) }
                                        launch { swipeYAnim.animateTo(0f, snapSpring) }
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = expandInteraction,
                            indication = null
                        ) { onExpand() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(s.displayArtworkUrl)
                            .size(coil.size.Size(160, 160))
                            .crossfade(150)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            s.displayTitle,
                            color = colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                        Text(
                            s.artist,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    if (playbackState == PlaybackState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = colors.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = colors.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
}

@Composable
fun SectionTitle(title: String) {
    val colors = LocalDreaminColors.current
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = colors.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit = {},
    rank: Int? = null,
    onAddToPlaylist: (Long) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val playlists = LocalPlaylists.current
    val swipeOffset = remember { Animatable(0f) }
    val swipeThreshold = 110f
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val onAddToQueueRef = rememberUpdatedState(onAddToQueue)
    var showQuickActions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        val swipeVal = swipeOffset.value
        if (swipeVal > 0f) {
            val progress = (swipeVal / swipeThreshold).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.9f),
                                colors.secondary.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(start = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = 0.6f + 0.4f * progress
                        alpha = progress
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add to queue",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (swipeVal > swipeThreshold * 0.45f) {
                        Text(
                            "ADD TO QUEUE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        val rowInteraction = remember { MutableInteractionSource() }
        val isPressed by rowInteraction.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = DreaminMotion.TactileBouncy,
            label = "row_press_scale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val captured = swipeOffset.value
                            scope.launch { swipeOffset.animateTo(0f, DreaminMotion.FluidGlide) }
                            if (captured > swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAddToQueueRef.value()
                            }
                        },
                        onDragCancel = {
                            scope.launch { swipeOffset.animateTo(0f, DreaminMotion.FluidGlide) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || swipeOffset.value > 0f) {
                                val next = (swipeOffset.value + dragAmount).coerceIn(0f, 220f)
                                if (next > 0f || swipeOffset.value > 0f) {
                                    change.consume()
                                    scope.launch { swipeOffset.snapTo(next) }
                                }
                            }
                        }
                    )
                }
                .graphicsLayer {
                    translationX = swipeOffset.value
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(RoundedCornerShape(18.dp))
                .border(
                    1.dp,
                    if (isPlaying) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.05f),
                    RoundedCornerShape(18.dp)
                )
                .background(
                    if (isPlaying) {
                        Brush.horizontalGradient(
                            listOf(colors.primary.copy(alpha = 0.15f), colors.surfaceHighest.copy(alpha = 0.6f))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(colors.surfaceHighest.copy(alpha = 0.4f), colors.surfaceHigh.copy(alpha = 0.3f))
                        )
                    }
                )
                .combinedClickable(
                    interactionSource = rowInteraction,
                    indication = ripple(bounded = true, color = colors.primary),
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showQuickActions = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Text(
                    text = String.format("%02d", rank),
                    color = if (rank <= 3) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.displayArtworkUrl)
                        .size(coil.size.Size(160, 160))
                        .crossfade(150)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "Playing",
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.displayTitle,
                    color = if (isPlaying) colors.primary else colors.onSurface,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    song.artist,
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onAddToQueue, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.PlaylistAdd,
                    contentDescription = "Add to queue",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showQuickActions) {
        SongQuickActionsBottomSheet(
            song = song,
            onDismiss = { showQuickActions = false },
            onPlayNext = {
                onPlayNext()
                showQuickActions = false
            },
            onAddToQueue = {
                onAddToQueue()
                showQuickActions = false
            },
            onAddToPlaylist = {
                showQuickActions = false
                showPlaylistPicker = true
            }
        )
    }

    if (showPlaylistPicker) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = { playlistId ->
                onAddToPlaylist(playlistId)
                showPlaylistPicker = false
            }
        )
    }
}

@Composable
fun SongQuickActionsBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceHighest,
        contentColor = colors.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.displayArtworkUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.displayTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = colors.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Action Item 1: Play Next (Insert right after current track)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = colors.primary)
                    ) { onPlayNext() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Play Next", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text("Insert right after current song", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }

            // Action Item 2: Add to Queue (Append at end of queue)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = colors.primary)
                    ) { onAddToQueue() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PlaylistAdd, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Add to End of Queue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text("Append to upcoming tracks", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }

            // Action Item 3: Add to Playlist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = colors.primary)
                    ) { onAddToPlaylist() }
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.FolderSpecial, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Add to Playlist", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
                    Text("Save to your personalized library", fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreateNew: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        onCreateNew()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                        Text("+ Create New Playlist", color = colors.secondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                playlists.forEach { playlist ->
                    TextButton(
                        onClick = { onSelect(playlist.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(playlist.name, color = colors.primary, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surfaceHigh,
        titleContentColor = colors.onSurface
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, android.net.Uri?) -> Unit,
    spotifyImportState: SpotifyImportState = SpotifyImportState.Idle,
    onImportSpotify: (String) -> Unit = {},
    onImportTextList: (String, String) -> Unit = { _, _ -> },
    onResetSpotifyImport: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var spotifyUrl by remember { mutableStateOf("") }
    var textImportTitle by remember { mutableStateOf("") }
    var textImportContent by remember { mutableStateOf("") }
    var pickedCoverUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) pickedCoverUri = uri
    }

    AlertDialog(
        onDismissRequest = {
            onResetSpotifyImport()
            onDismiss()
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "New Playlist",
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                // 3-Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceHighest)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) colors.surfaceHigh else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Blank",
                            color = if (selectedTab == 0) colors.primary else colors.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) colors.surfaceHigh else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Spotify URL",
                            color = if (selectedTab == 1) Color(0xFF1DB954) else colors.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 2) colors.surfaceHigh else Color.Transparent)
                            .clickable { selectedTab = 2 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Text List",
                            color = if (selectedTab == 2) colors.secondary else colors.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Playlist name", color = colors.onSurfaceVariant) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.35f),
                            cursorColor = colors.primary,
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceHighest,
                            unfocusedContainerColor = colors.surfaceHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 📷 Optional Custom Cover Photo Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceHighest)
                            .clickable {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (pickedCoverUri != null) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = pickedCoverUri,
                                    contentDescription = "Selected cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cover Selected", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Text("Tap to change photo", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { pickedCoverUri = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = "Pick cover",
                                    tint = colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cover Image", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                Text("Optional • Choose from gallery", fontSize = 11.5.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // Spotify Import
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Paste a Spotify playlist link to import its songs into Dreamin.",
                        fontSize = 12.5.sp,
                        color = colors.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = spotifyUrl,
                        onValueChange = { spotifyUrl = it },
                        placeholder = { Text("https://open.spotify.com/playlist/...", color = colors.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            if (spotifyUrl.isEmpty()) {
                                TextButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) spotifyUrl = clip.trim()
                                    }
                                ) {
                                    Text("Paste", color = Color(0xFF1DB954), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                IconButton(onClick = { spotifyUrl = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.35f),
                            cursorColor = Color(0xFF1DB954),
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceHighest,
                            unfocusedContainerColor = colors.surfaceHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Import progress & status indicators
                    when (val st = spotifyImportState) {
                        is SpotifyImportState.FetchingMetadata -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceHighest)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF1DB954),
                                    strokeWidth = 2.5.dp
                                )
                                Text("Connecting to Spotify...", color = colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        is SpotifyImportState.MatchingTracks -> {
                            val progress = if (st.totalTracks > 0) st.currentTrackIndex.toFloat() / st.totalTracks.toFloat() else 0f
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceHighest)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Importing '${st.playlistTitle}'",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${st.currentTrackIndex}/${st.totalTracks}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF1DB954),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF1DB954),
                                    trackColor = colors.surfaceHigh
                                )
                                Text(
                                    "Matching: ${st.currentTrackName}",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        is SpotifyImportState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(36.dp))
                                Text("Import Complete!", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Imported ${st.matchedCount} of ${st.totalTracks} songs",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is SpotifyImportState.Error -> {
                            Text(
                                st.message,
                                color = Color(0xFFFF5252),
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {}
                    }
                }
            } else {
                // Tab 2: Text List Import
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Paste a list of songs (one per line, e.g. 'Song - Artist'). Dreamin will match and import them automatically.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = textImportTitle,
                        onValueChange = { textImportTitle = it },
                        placeholder = { Text("Playlist name (optional)", color = colors.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 12.5.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.secondary,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.35f),
                            cursorColor = colors.secondary,
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceHighest,
                            unfocusedContainerColor = colors.surfaceHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = textImportContent,
                        onValueChange = { textImportContent = it },
                        placeholder = { Text("1. Song Title - Artist\n2. Another Song by Artist\n...", color = colors.onSurfaceVariant.copy(alpha = 0.4f), fontSize = 12.sp) },
                        minLines = 4,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (textImportContent.isEmpty()) {
                                TextButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) textImportContent = clip.trim()
                                    }
                                ) {
                                    Text("Paste", color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.secondary,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.35f),
                            cursorColor = colors.secondary,
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceHighest,
                            unfocusedContainerColor = colors.surfaceHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Text Import Progress State
                    when (val st = spotifyImportState) {
                        is SpotifyImportState.MatchingTracks -> {
                            val progress = if (st.totalTracks > 0) st.currentTrackIndex.toFloat() / st.totalTracks.toFloat() else 0f
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceHighest)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Searching & Matching Songs...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        "${st.currentTrackIndex}/${st.totalTracks}",
                                        fontSize = 12.sp,
                                        color = colors.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = colors.secondary,
                                    trackColor = colors.surfaceHigh
                                )
                                Text(
                                    "Matching: ${st.currentTrackName}",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        is SpotifyImportState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(36.dp))
                                Text("Import Complete!", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Imported ${st.matchedCount} of ${st.totalTracks} songs into '${st.playlistTitle}'",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is SpotifyImportState.Error -> {
                            Text(st.message, color = Color(0xFFFF5252), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        else -> {}
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) onCreate(name, pickedCoverUri)
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Create", color = if (name.isNotBlank()) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            } else if (selectedTab == 1) {
                when (spotifyImportState) {
                    is SpotifyImportState.Idle -> {
                        TextButton(
                            onClick = {
                                if (spotifyUrl.isNotBlank()) onImportSpotify(spotifyUrl)
                            },
                            enabled = spotifyUrl.isNotBlank()
                        ) {
                            Text("Import", color = if (spotifyUrl.isNotBlank()) Color(0xFF1DB954) else colors.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        }
                    }
                    is SpotifyImportState.Success -> {
                        TextButton(onClick = {
                            onResetSpotifyImport()
                            onDismiss()
                        }) {
                            Text("Done", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                        }
                    }
                    is SpotifyImportState.Error -> {
                        TextButton(onClick = onResetSpotifyImport) {
                            Text("Try Again", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {}
                }
            } else {
                // Tab 2: Text List
                when (spotifyImportState) {
                    is SpotifyImportState.Idle -> {
                        TextButton(
                            onClick = {
                                if (textImportContent.isNotBlank()) {
                                    val title = textImportTitle.ifBlank { "Imported List" }
                                    onImportTextList(textImportContent, title)
                                }
                            },
                            enabled = textImportContent.isNotBlank()
                        ) {
                            Text("Import List", color = if (textImportContent.isNotBlank()) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        }
                    }
                    is SpotifyImportState.Success -> {
                        TextButton(onClick = {
                            onResetSpotifyImport()
                            onDismiss()
                        }) {
                            Text("Done", color = colors.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                    is SpotifyImportState.Error -> {
                        TextButton(onClick = onResetSpotifyImport) {
                            Text("Try Again", color = colors.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {}
                }
            }
        },
        dismissButton = {
            if (spotifyImportState !is SpotifyImportState.FetchingMetadata && spotifyImportState !is SpotifyImportState.MatchingTracks) {
                TextButton(onClick = {
                    onResetSpotifyImport()
                    onDismiss()
                }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            }
        },
        containerColor = colors.surfaceHigh,
        titleContentColor = colors.onSurface
    )
}

@Composable
fun SaveQueueDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = LocalDreaminColors.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Queue as Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist name", color = colors.onSurfaceVariant) },
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
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surfaceHigh,
        titleContentColor = colors.onSurface
    )
}

@Composable
fun MiniEqualizerIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mini_eq")
    val h1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "eq3"
    )

    Row(
        modifier = modifier.size(width = 18.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(3.5.dp).fillMaxHeight(h1).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.width(3.5.dp).fillMaxHeight(h2).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.width(3.5.dp).fillMaxHeight(h3).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
fun EditNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = LocalDreaminColors.current
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Name", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Your name", color = colors.onSurfaceVariant) },
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
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
        containerColor = colors.surfaceHigh,
        titleContentColor = colors.onSurface
    )
}

@Composable
fun DreaminInfoSheet(onDismiss: () -> Unit) {
    val colors = LocalDreaminColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceHigh,
        titleContentColor = colors.onSurface,
        title = {
            Text("Tips & Tricks", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = colors.onSurface)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoFeatureRow(Icons.Outlined.SwipeRight, "Swipe right on a song", "Adds it to the end of your queue")
                InfoFeatureRow(Icons.Outlined.TouchApp, "Long-press a song", "Quickly add it to a playlist")
                InfoFeatureRow(Icons.Outlined.Person, "Long-press your name", "Change what the app calls you")
                InfoFeatureRow(Icons.Outlined.Favorite, "Double-tap artwork", "Instantly favourites the playing song")
                InfoFeatureRow(Icons.Outlined.Timer, "Sleep timer", "In Now Playing — auto-pauses after set time")
                InfoFeatureRow(Icons.Outlined.LockClock, "Auto-resume", "Starts exactly where you left off")
                InfoFeatureRow(Icons.Outlined.KeyboardArrowDown, "Close Now Playing", "Tap the arrow at the top to go back")
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "DREAMIN · made by Shyan",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                TextButton(onClick = onDismiss) {
                    Text("Got it", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

@Composable
fun InfoFeatureRow(icon: ImageVector, title: String, description: String) {
    val colors = LocalDreaminColors.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column {
            Text(title, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
