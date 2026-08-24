@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

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





val Background = Color(0xFF07070A)
val SurfaceContainer = Color(0xFF0F0F16)
val SurfaceHigh = Color(0xFF14141E)
val SurfaceHighest = Color(0xFF1C1C2A)
val SurfaceBright = Color(0xFF242436)
val Primary = Color(0xFF8B5CF6)
val PrimaryDim = Color(0xFF6D28D9)
val Secondary = Color(0xFF06B6D4)
val OnSurface = Color.White.copy(alpha = 0.96f)
val OnSurfaceVariant = Color.White.copy(alpha = 0.65f)
val OutlineVariant = Color.White.copy(alpha = 0.08f)








@androidx.compose.runtime.Immutable
data class DreaminColors(
    val background: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val surfaceBright: Color,
    val primary: Color,
    val primaryDim: Color,
    val secondary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color
)

val SonicNocturneColors = DreaminColors(
    background = Background,
    surfaceContainer = SurfaceContainer,
    surfaceHigh = SurfaceHigh,
    surfaceHighest = SurfaceHighest,
    surfaceBright = SurfaceBright,
    primary = Primary,
    primaryDim = PrimaryDim,
    secondary = Secondary,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outlineVariant = OutlineVariant
)

val LocalDreaminColors = compositionLocalOf { SonicNocturneColors }
val LocalPlaylists = staticCompositionLocalOf<List<com.shyan.dreamin.data.local.Playlist>> { emptyList() }

private fun blendDominantTint(base: Color, tint: Color, alpha: Float): Color = Color(
    red   = base.red   * (1f - alpha) + tint.red   * alpha,
    green = base.green * (1f - alpha) + tint.green * alpha,
    blue  = base.blue  * (1f - alpha) + tint.blue  * alpha,
    alpha = 1f
)



// Unified Motion System for fluid 120 FPS animations & transitions
object DreaminMotion {
    val FluidGlide: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 320f
    )
    val FluidSlide: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.85f,
        stiffness = 320f
    )
    val TactileBouncy: SpringSpec<Float> = spring(
        dampingRatio = 0.62f,
        stiffness = 420f
    )
    val SnappySnap: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 600f
    )
    val SmoothTween: TweenSpec<Float> = tween(
        durationMillis = 280,
        easing = FastOutSlowInEasing
    )
}

@Composable
fun DreaminRippleTheme(
    content: @Composable () -> Unit
) {
    val colors = LocalDreaminColors.current
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = colors.primary,
            onPrimary = colors.onSurface,
            surface = colors.surfaceContainer,
            onSurface = colors.onSurface
        ),
        content = content
    )
}





enum class Screen(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Library("Library", Icons.Outlined.LibraryMusic)
}

@Composable
fun MusicPlayerScreen(vm: MusicPlayerViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }

    val colors = SonicNocturneColors

    CompositionLocalProvider(LocalDreaminColors provides colors) {
        DreaminRippleTheme {
            when {
                state.userName == null -> Box(Modifier.fillMaxSize().background(colors.background))
                state.userName?.isBlank() == true -> OnboardingScreen(onNameSubmit = vm::saveUserName)
                else -> MainAppScaffold(
                    state            = state,
                    vm               = vm,
                    currentScreen    = currentScreen,
                    isNowPlayingOpen = isNowPlayingOpen,
                    onScreenChange   = { currentScreen = it },
                    onOpenNowPlaying = { isNowPlayingOpen = true },
                    onCloseNowPlaying = { isNowPlayingOpen = false }
                )
            }
        }
    }
}

@Composable
private fun MainAppScaffold(
    state: PlayerUiState,
    vm: MusicPlayerViewModel,
    currentScreen: Screen,
    isNowPlayingOpen: Boolean,
    onScreenChange: (Screen) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hazeState = remember { HazeState() }
    val navScreens = Screen.entries
    val pagerState = rememberPagerState(
        initialPage = navScreens.indexOf(currentScreen).coerceAtLeast(0),
        pageCount = { navScreens.size }
    )
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.settledPage) {
        val screen = navScreens.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (screen != currentScreen) onScreenChange(screen)
    }

    val trendingCharts = state.trendingCharts
    val onHomeSongClick = remember(onOpenNowPlaying) {
        { song: Song -> vm.playSong(song); onOpenNowPlaying() }
    }
    val onSongClickFromList = remember(onOpenNowPlaying) {
        { song: Song, list: List<Song> -> vm.playSongFromList(song, list); onOpenNowPlaying() }
    }
    val onHomeShuffleFab = remember(onOpenNowPlaying, trendingCharts) {
        { vm.shuffleAndPlayList(trendingCharts); onOpenNowPlaying() }
    }
    val onLibrarySongClick = remember(onOpenNowPlaying) {
        { song: Song -> vm.playSong(song); onOpenNowPlaying() }
    }
    val onLibraryPlayPlaylist = remember(onOpenNowPlaying) {
        { id: Long -> vm.playSongsFromPlaylist(id); onOpenNowPlaying() }
    }
    val onResumeSession = remember(onOpenNowPlaying) {
        { vm.resumeLastSession(); onOpenNowPlaying() }
    }

    BackHandler(enabled = !isNowPlayingOpen && (state.isSearchActive || currentScreen != navScreens.first())) {
        if (state.isSearchActive) {
            vm.clearSearch()
        } else {
            val idx = navScreens.indexOf(currentScreen)
            if (idx > 0) onScreenChange(navScreens[idx - 1])
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = !isNowPlayingOpen && state.currentSong != null,
                        enter = slideInVertically(DreaminMotion.FluidSlide) { it } + fadeIn(tween(180)),
                        exit = slideOutVertically(DreaminMotion.FluidSlide) { it } + fadeOut(tween(140))
                    ) {
                        MiniPlayer(
                            song              = state.currentSong,
                            playbackState     = state.playbackState,
                            progressFlow      = vm.progressFlow,
                            onPlayPause       = vm::togglePlayPause,
                            onNext            = vm::playNext,
                            onPrevious        = vm::playPrevious,
                            onExpand          = onOpenNowPlaying,
                            dominantColor     = Color(state.dominantColor),
                            hazeState         = hazeState
                        )
                    }
                    BottomNavBar(
                        currentScreen  = currentScreen,
                        hazeState      = hazeState,
                        onScreenChange = { screen ->
                            if (screen == Screen.Home && state.isSearchActive) {
                                keyboard?.hide()
                                vm.clearSearch()
                            }
                            val targetIdx = navScreens.indexOf(screen)
                            if (targetIdx >= 0) {
                                onScreenChange(screen)
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        page = targetIdx,
                                        animationSpec = spring(
                                            dampingRatio = 0.86f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            HorizontalPager(
                state                  = pagerState,
                modifier               = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(bottom = padding.calculateBottomPadding()),
                userScrollEnabled      = !state.isSearchActive,
                beyondViewportPageCount = 1,
                key                    = { navScreens[it].name }
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = kotlin.math.abs(pageOffset)
                val pageScale = 1f - (absOffset * 0.035f).coerceIn(0f, 0.035f)
                val pageAlpha = 1f - (absOffset * 0.35f).coerceIn(0f, 0.35f)

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .graphicsLayer {
                            scaleX = pageScale
                            scaleY = pageScale
                            alpha = pageAlpha
                        }
                ) {
                    when (navScreens[page]) {
                        Screen.Home -> HomeScreen(
                            trendingCharts           = trendingCharts,
                            recommendations          = state.recommendations,
                            recentlyPlayed           = state.recentlyPlayed,
                            topSongs                 = state.topSongs,
                            currentSong              = state.currentSong,
                            isPlaying                = state.playbackState is PlaybackState.Playing,
                            isSearchActive           = state.isSearchActive,
                            searchQuery              = state.searchQuery,
                            searchResults            = state.searchResults,
                            isSearching              = state.isSearching,
                            isLoadingChart           = state.isLoadingChart,
                            isLoadingMoreSearch      = state.isLoadingMoreSearch,
                            hasMoreSearchResults     = state.hasMoreSearchResults,
                            recommendationSeedTitle  = state.recommendationSeedTitle,
                            userName                 = state.userName.orEmpty(),
                            playlists                = state.playlists,
                            recentSearches           = state.recentSearches,
                            lastSession              = state.lastSession,
                            onSongClick              = onHomeSongClick,
                            onSongClickFromList      = onSongClickFromList,
                            onShuffleFab             = onHomeShuffleFab,
                            onAddToQueue             = vm::addToQueue,
                            onPlayNext               = vm::playNext,
                            onSearchChange           = vm::setSearchQuery,
                            onClearSearch            = vm::clearSearch,
                            onActivateSearch         = vm::activateSearch,
                            onRefresh                = vm::refreshData,
                            onLoadMoreSearch         = vm::loadMoreSearchResults,
                            onAddToPlaylist          = { song, playlistId -> vm.addSongToPlaylist(playlistId, song) },
                            onEditName               = vm::saveUserName,
                            onClearRecentSearches    = vm::clearRecentSearches,
                            onResumeLastSession      = onResumeSession,
                            searchError              = state.searchError
                        )
                        Screen.Library -> LibraryScreen(
                            state                    = state,
                            onSongClick              = onLibrarySongClick,
                            onCreatePlaylist         = vm::createPlaylist,
                            onDeletePlaylist         = vm::deletePlaylist,
                            onPlayPlaylist           = onLibraryPlayPlaylist,
                            onAddToPlaylist          = { song, playlistId -> vm.addSongToPlaylist(playlistId, song) },
                            onOpenPlaylist           = vm::openPlaylist,
                            onClosePlaylist          = vm::closePlaylist,
                            onRemoveSongFromPlaylist = vm::removeSongFromPlaylist,
                            onRenamePlaylist         = vm::renamePlaylist,
                            onSongClickFromPlaylist  = { song, songs -> vm.playSongFromPlaylist(song, songs); onOpenNowPlaying() },
                            onShufflePlaylist        = { songs -> vm.shuffleAndPlayPlaylist(songs); onOpenNowPlaying() },
                            onDownloadPlaylist       = vm::downloadAllSongsInPlaylist,
                            onDownloadSong           = vm::downloadSong,
                            onDeleteDownload         = vm::deleteDownload,
                            onImportSpotifyPlaylist  = vm::importSpotifyPlaylist,
                            onResetSpotifyImportState = vm::resetSpotifyImportState
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = isNowPlayingOpen,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCloseNowPlaying() }
            )
        }

        AnimatedVisibility(
            visible  = isNowPlayingOpen,
            enter    = slideInVertically(DreaminMotion.FluidSlide) { it },
            exit     = slideOutVertically(DreaminMotion.FluidSlide) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            NowPlayingScreen(
                state              = state,
                progressFlow       = vm.progressFlow,
                onPlayPause        = vm::togglePlayPause,
                onNext             = vm::playNext,
                onPrevious         = vm::playPrevious,
                onSeek             = vm::seekTo,
                onToggleShuffle    = vm::toggleShuffle,
                onToggleRepeat     = vm::toggleRepeat,
                onToggleFavorite   = vm::toggleFavorite,
                onSetSleepTimer    = vm::setSleepTimer,
                onCancelSleepTimer = vm::cancelSleepTimer,
                onSongSelect       = { song -> vm.playSong(song, fromPlaylist = state.playlistQueueActive, preserveQueue = true) },
                onAddToPlaylist    = { playlistId ->
                    state.currentSong?.let { vm.addSongToPlaylist(playlistId, it) }
                },
                onCreatePlaylist   = { name ->
                    vm.createPlaylist(name)
                },
                onRemoveFromQueue    = vm::removeFromQueue,
                onRestoreToQueue     = vm::restoreToQueue,
                onClearQueue         = vm::clearQueue,
                onReorderQueue       = vm::reorderQueue,
                onSaveQueueAsPlaylist = vm::saveQueueAsPlaylist,
                onToggleLyrics       = vm::toggleLyricsView,
                onRetryLyrics        = vm::loadLyricsForCurrentSong,
                onDownload           = vm::downloadSong,
                onDeleteDownload     = vm::deleteDownload,
                onArtistClick        = vm::openArtistProfile,
                onBack               = onCloseNowPlaying
            )
        }

        state.selectedArtistProfile?.let { profile ->
            ArtistDetailScreen(
                profile = profile,
                currentSongId = state.currentSong?.id,
                onBack = vm::closeArtistProfile,
                onSongClick = { song -> vm.playSong(song); onOpenNowPlaying() },
                onShuffleAll = { songs ->
                    if (songs.isNotEmpty()) {
                        vm.shuffleAndPlayList(songs)
                        onOpenNowPlaying()
                    }
                }
            )
        }
    }
}





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
private fun MiniPlayerProgressBar(
    progressFlow: StateFlow<PlaybackProgress>,
    accentColor: Color? = null
) {
    val colors = LocalDreaminColors.current
    val barColor = accentColor ?: colors.primary
    val trackColor = colors.surfaceHighest.copy(alpha = 0.45f)
    val progressState by progressFlow.collectAsStateWithLifecycle()
    val progress = if (progressState.durationMs > 0) (progressState.currentPositionMs.toFloat() / progressState.durationMs).coerceIn(0f, 1f) else 0f
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .drawBehind {
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
fun HomeScreen(
    trendingCharts: List<Song>,
    recommendations: List<Song>,
    recentlyPlayed: List<Song>,
    topSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean = false,
    isSearchActive: Boolean,
    searchQuery: String,
    searchResults: List<Song>,
    isSearching: Boolean = false,
    isLoadingChart: Boolean,
    isLoadingMoreSearch: Boolean,
    hasMoreSearchResults: Boolean,
    recommendationSeedTitle: String?,
    userName: String,
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    recentSearches: List<String>,
    lastSession: com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession?,
    onSongClick: (Song) -> Unit,
    onSongClickFromList: (Song, List<Song>) -> Unit = { song, _ -> onSongClick(song) },
    onShuffleFab: () -> Unit = {},
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onActivateSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMoreSearch: () -> Unit = {},
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onEditName: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onResumeLastSession: () -> Unit = {},
    searchError: String? = null,
) {
    val colors = LocalDreaminColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                300f
            }
        }
    }
    var showEditNameDialog by remember { mutableStateOf(false) }
    val timeOfDay = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    val handleCloseSearch = remember(keyboard, focusManager, onClearSearch) {
        {
            keyboard?.hide()
            focusManager.clearFocus()
            onClearSearch()
        }
    }

    BackHandler(enabled = isSearchActive) { handleCloseSearch() }

    val onSongClickWithKeyboardDismiss = remember(onSongClick, keyboard) {
        { song: Song -> keyboard?.hide(); onSongClick(song) }
    }

    CompositionLocalProvider(LocalPlaylists provides playlists) {
    Box(modifier = Modifier.fillMaxSize()) {
        // High-Performance Ambient Violet Soundwave Header (Zero-GC, Parallax & Playback Resonance)
        AmbientSoundwaveHeader(
            height = 280.dp,
            scrollOffsetProvider = {
                if (isSearchActive) 400f
                else scrollOffset
            },
            isPlaying = isPlaying,
            primaryGlow = colors.primary,
            midPurple = colors.secondary,
            deepIndigo = Color(0xFF2E1065)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 👑 DREAMIN Top Header & Greeting (smoothly fades and collapses 1:1 with scroll)
            val scrollFraction = if (isSearchActive) 1f else (scrollOffset / 110f).coerceIn(0f, 1f)
            val headerAlpha = (1f - scrollFraction * 1.3f).coerceIn(0f, 1f)
            val headerHeightDp = 76.dp * (1f - scrollFraction)

            if (headerHeightDp > 0.5.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeightDp)
                        .graphicsLayer {
                            alpha = headerAlpha
                            translationY = -scrollFraction * 18f
                        }
                        .clipToBounds()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "DREAMIN",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 2.5.sp,
                            modifier = Modifier.weight(1f)
                        )

                        var showInfoSheet by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showInfoSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "App info",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (showInfoSheet) {
                            DreaminInfoSheet(onDismiss = { showInfoSheet = false })
                        }
                    }

                    // Greeting
                    Text(
                        "$timeOfDay, $userName",
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .combinedClickable(onClick = {}, onLongClick = { showEditNameDialog = true })
                    )
                }
            }

            // 🔍 Unified Docking SearchBar (smoothly moves UP to top when active, DOWN to feed when idle)
            DreaminSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                onClear = handleCloseSearch,
                onActivate = onActivateSearch,
                autoFocus = isSearchActive,
                showBackButton = isSearchActive,
                onBack = handleCloseSearch,
                recentSearches = recentSearches,
                onRecentSearchClick = onSearchChange,
                onClearRecentSearches = onClearRecentSearches
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 📄 Body: Live Search Results vs Home Feed Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { 50 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { 50 },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (searchQuery.isNotEmpty()) {
                        SearchResults(
                            songs = searchResults,
                            currentSong = currentSong,
                            onSongClick = onSongClickWithKeyboardDismiss,
                            onAddToQueue = onAddToQueue,
                            isSearching = isSearching,
                            hasMore = hasMoreSearchResults,
                            isLoadingMore = isLoadingMoreSearch,
                            onLoadMore = onLoadMoreSearch,
                            onAddToPlaylist = onAddToPlaylist,
                            errorMessage = searchError
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSearchActive,
                    enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.97f, animationSpec = tween(160)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    PullToRefreshBox(
                        isRefreshing = isLoadingChart,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoadingChart && trendingCharts.isEmpty() && recommendations.isEmpty()) {
                            ShimmerFeedSkeleton()
                        } else {
                            HomeFeedOnlyContent(
                                trending = trendingCharts,
                                recommendations = recommendations,
                                recentlyPlayed = recentlyPlayed,
                                topSongs = topSongs,
                                recommendationSeedTitle = recommendationSeedTitle,
                                currentSong = currentSong,
                                onSongClick = onSongClick,
                                onSongClickFromList = onSongClickFromList,
                                onAddToQueue = onAddToQueue,
                                onPlayNext = onPlayNext,
                                onRefresh = onRefresh,
                                listState = listState,
                                isLoading = isLoadingChart,
                                playlists = playlists,
                                onAddToPlaylist = onAddToPlaylist,
                                lastSession = lastSession,
                                onResumeLastSession = onResumeLastSession
                            )
                        }
                    }
                }
            }
        }

        if (trendingCharts.isNotEmpty() && !isSearchActive) {
            FloatingActionButton(
                onClick = onShuffleFab,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = colors.primary,
                contentColor = colors.background,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Play Random",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    } // end CompositionLocalProvider(LocalPlaylists)

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = userName ?: "",
            onDismiss = { showEditNameDialog = false },
            onSave = { newName ->
                onEditName(newName)
                showEditNameDialog = false
            }
        )
    }
}

@Composable
fun DreaminSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onActivate: () -> Unit = {},
    autoFocus: Boolean = false,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    recentSearches: List<String> = emptyList(),
    onRecentSearchClick: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val showRecents = isFocused && query.isEmpty() && recentSearches.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            kotlinx.coroutines.delay(60)
            try {
                focusRequester.requestFocus()
                keyboard?.show()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                onActivate()
                try {
                    focusRequester.requestFocus()
                    keyboard?.show()
                } catch (_: Exception) {}
            }
        }
    }

    val composerHints = remember {
        listOf(
            "Search AR Rahman...",
            "Search Anirudh...",
            "Search Yuvan Shankar Raja...",
            "Search Harris Jayaraj...",
            "Search Sid Sriram...",
            "Search Ilaiyaraaja...",
            "Search songs, artists..."
        )
    }
    var hintIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            hintIndex = (hintIndex + 1) % composerHints.size
        }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = 1.3.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = if (isFocused) 0.85f else 0.4f),
                            colors.secondary.copy(alpha = if (isFocused) 0.7f else 0.25f),
                            colors.primary.copy(alpha = if (isFocused) 0.5f else 0.18f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        onActivate()
                        try {
                            keyboard?.show()
                        } catch (_: Exception) {}
                    }
                },
            placeholder = {
                AnimatedContent(
                    targetState = composerHints[hintIndex],
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn(tween(250))) togetherWith
                                (slideOutVertically { height -> -height / 2 } + fadeOut(tween(200)))
                    },
                    label = "search_hint_anim"
                ) { hint ->
                    Text(hint, color = colors.onSurfaceVariant.copy(alpha = 0.75f), fontSize = 14.sp)
                }
            },
            leadingIcon = {
                AnimatedContent(
                    targetState = showBackButton,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.75f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.75f))
                    },
                    label = "search_icon_anim"
                ) { isBack ->
                    if (isBack) {
                        IconButton(
                            onClick = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                onBack()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (isFocused) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                    exit = scaleOut(tween(120)) + fadeOut(tween(120))
                ) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            onClear()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = colors.primary,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface
            )
        )

        AnimatedVisibility(visible = showRecents && recentSearches.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(colors.surfaceHigh)
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent", color = colors.onSurfaceVariant, fontSize = 12.sp)
                    TextButton(onClick = onClearRecentSearches) {
                        Text("Clear", color = colors.primary, fontSize = 12.sp)
                    }
                }
                recentSearches.forEach { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = colors.primary)
                            ) { onRecentSearchClick(term) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(term, color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.staggeredEntry(index: Int, baseDelayMs: Int = 22, maxStaggerItems: Int = 12): Modifier {
    val staggerDelay = (index.coerceAtMost(maxStaggerItems) * baseDelayMs)
    val animState = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(staggerDelay.toLong())
        animState.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        alpha = animState.value
        translationY = (1f - animState.value) * 28f
        scaleX = 0.95f + 0.05f * animState.value
        scaleY = 0.95f + 0.05f * animState.value
    }
}

@Composable
fun Modifier.staggeredHorizontalEntry(index: Int, baseDelayMs: Int = 25, maxStaggerItems: Int = 10): Modifier {
    val staggerDelay = (index.coerceAtMost(maxStaggerItems) * baseDelayMs)
    val animState = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(staggerDelay.toLong())
        animState.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        alpha = animState.value
        translationX = (1f - animState.value) * 36f
        scaleX = 0.94f + 0.06f * animState.value
        scaleY = 0.94f + 0.06f * animState.value
    }
}

@Composable
fun SearchResults(
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    isSearching: Boolean = false,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    errorMessage: String? = null
) {
    val colors = LocalDreaminColors.current
    if (isSearching && songs.isEmpty()) {
        ShimmerSearchList()
        return
    }
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(errorMessage ?: "No results found", color = colors.onSurfaceVariant, fontSize = 16.sp)
        }
        return
    }

    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = songs,
            key = { idx, song -> "${song.id}_$idx" },
            contentType = { _, _ -> "SongRow" }
        ) { index, song ->
            Box(modifier = Modifier.staggeredEntry(index)) {
                SongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { playlistId -> onAddToPlaylist(song, playlistId) }
                )
            }
        }
        if (isLoadingMore) {
            item(contentType = "ShimmerRow") { ShimmerSongRow() }
        }
    }
}

private val BlackOverlay50 = Color.Black.copy(alpha = 0.50f)
private val BlackOverlay35 = Color.Black.copy(alpha = 0.35f)

private val shimmerColors = listOf(
    Color.White.copy(alpha = 0.04f),
    Color.White.copy(alpha = 0.16f),
    Color.White.copy(alpha = 0.04f),
)

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "diagonal_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_diag"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, translateAnim * 0.4f),
        end = Offset(translateAnim + 500f, (translateAnim + 500f) * 0.4f)
    )
}

@Composable
fun ShimmerSearchList() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(7) { index ->
            Box(modifier = Modifier.staggeredEntry(index, baseDelayMs = 30)) {
                ShimmerSongRow()
            }
        }
    }
}

@Composable
fun ShimmerFeedSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Horizontal cards skeleton row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(3) {
                Column(
                    modifier = Modifier.width(140.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }

        // Vertical song rows skeleton
        repeat(5) {
            ShimmerSongRow()
        }
    }
}

@Composable
fun ShimmerSongRow() {
    val brush = shimmerBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.40f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun ShimmerSongList(count: Int) {
    // Single InfiniteTransition shared across all rows — one animation loop, not N
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        repeat(count) {
            ShimmerSongRowWithBrush(brush)
        }
    }
}

@Composable
private fun ShimmerSongRowWithBrush(brush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(brush))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.65f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        }
        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(brush))
    }
}

@Composable
fun HorizontalSongCardsRow(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        itemsIndexed(songs, key = { idx, song -> "${song.id}_$idx" }) { index, song ->
            Box(modifier = Modifier.staggeredHorizontalEntry(index)) {
                HorizontalSongCard(
                    song = song,
                    isPlaying = currentSongId == song.id,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
fun HorizontalSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: (Long) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    var showQuickActions by remember { mutableStateOf(false) }
    val cardInteraction = remember { MutableInteractionSource() }
    val isPressed by cardInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = DreaminMotion.TactileBouncy,
        label = "card_press_scale"
    )

    Column(
        modifier = Modifier
            .width(135.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                interactionSource = cardInteraction,
                indication = ripple(bounded = true, color = colors.primary),
                onClick = onClick,
                onLongClick = { showQuickActions = true }
            )
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = if (isPlaying) 1.5.dp else 1.dp,
                    color = if (isPlaying) colors.primary else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.displayArtworkUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Mini 3-bar animated equalizer wave
                    val infiniteTransition = rememberInfiniteTransition(label = "eq_transition")
                    val bar1 by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
                        label = "bar1"
                    )
                    val bar2 by infiniteTransition.animateFloat(
                        initialValue = 0.8f, targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
                        label = "bar2"
                    )
                    val bar3 by infiniteTransition.animateFloat(
                        initialValue = 0.4f, targetValue = 0.95f,
                        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
                        label = "bar3"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(20.dp)
                    ) {
                        Box(modifier = Modifier.width(3.5.dp).height(20.dp * bar1).clip(RoundedCornerShape(2.dp)).background(colors.primary))
                        Box(modifier = Modifier.width(3.5.dp).height(20.dp * bar2).clip(RoundedCornerShape(2.dp)).background(colors.primary))
                        Box(modifier = Modifier.width(3.5.dp).height(20.dp * bar3).clip(RoundedCornerShape(2.dp)).background(colors.primary))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            song.displayTitle,
            color = if (isPlaying) colors.primary else colors.onSurface,
            fontSize = 13.sp,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            song.artist,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showQuickActions) {
        SongQuickActionsBottomSheet(
            song = song,
            onDismiss = { showQuickActions = false },
            onAddToQueue = {
                onAddToQueue()
                showQuickActions = false
            },
            onAddToPlaylist = {
                showQuickActions = false
            }
        )
    }
}



@Composable
fun FeaturedHeroCarousel(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit
) {
    val colors = LocalDreaminColors.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        itemsIndexed(songs, key = { index, song -> "${song.id}_$index" }) { index, song ->
            val isPlaying = currentSongId == song.id
            val heroInteraction = remember { MutableInteractionSource() }
            val isPressed by heroInteraction.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = DreaminMotion.TactileBouncy,
                label = "hero_press_scale"
            )
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(130.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, if (isPlaying) colors.primary else colors.outlineVariant, RoundedCornerShape(22.dp))
                    .background(colors.surfaceHighest)
                    .clickable(
                        interactionSource = heroInteraction,
                        indication = ripple(bounded = true, color = colors.primary)
                    ) { onSongClick(song) }
            ) {
                // Background artwork
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.displayArtworkUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.primary.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("#${index + 1} TOP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Column {
                        Text(
                            song.displayTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpBackInCard(
    session: com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession,
    onClick: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val progress = if (session.song.duration > 0)
        (session.positionMs.toFloat() / session.song.duration).coerceIn(0f, 1f)
    else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(colors.primary.copy(alpha = 0.18f), colors.surfaceHigh)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(session.song.displayArtworkUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BlackOverlay35),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Jump back in",
                fontSize = 11.sp,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Text(
                session.song.displayTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                session.song.artist,
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surfaceHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(colors.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeFeedOnlyContent(
    trending: List<Song>,
    recommendations: List<Song>,
    recentlyPlayed: List<Song>,
    topSongs: List<Song>,
    recommendationSeedTitle: String?,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onRefresh: () -> Unit,
    listState: LazyListState,
    isLoading: Boolean = false,
    playlists: List<com.shyan.dreamin.data.local.Playlist> = emptyList(),
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onSongClickFromList: (Song, List<Song>) -> Unit = { song, _ -> onSongClick(song) },
    lastSession: com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession?,
    onResumeLastSession: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val allSongsById = remember(trending, recommendations) {
        (trending + recommendations).associateBy { it.id }
    }
    val onClickById    = remember(onSongClick, allSongsById)     { { id: String -> allSongsById[id]?.let(onSongClick) } }
    val onQueueById    = remember(onAddToQueue, allSongsById)    { { id: String -> allSongsById[id]?.let(onAddToQueue) } }
    val onPlayNextById = remember(onPlayNext, allSongsById)      { { id: String -> allSongsById[id]?.let(onPlayNext) } }
    val onPlaylistById = remember(onAddToPlaylist, allSongsById) { { id: String, pid: Long -> allSongsById[id]?.let { onAddToPlaylist(it, pid) } } }
    val trendingTop    = remember(trending)       { trending.take(10) }
    val recsTop        = remember(recommendations) { recommendations.take(8) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 0.dp,
            end = 0.dp,
            top = 4.dp,
            bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        if (lastSession != null && currentSong == null) {
            item(key = "jump_back_in") {
                JumpBackInCard(
                    session = lastSession,
                    onClick = onResumeLastSession
                )
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item(key = "section_recent") { SectionTitle("Recently Played") }
            item(key = "row_recent") {
                HorizontalSongCardsRow(
                    songs = recentlyPlayed,
                    currentSongId = currentSong?.id,
                    onSongClick = onSongClick
                )
            }
        }

        if (topSongs.isNotEmpty()) {
            item(key = "section_top") { SectionTitle("Your Top Songs") }
            item(key = "row_top") {
                HorizontalSongCardsRow(
                    songs = topSongs,
                    currentSongId = currentSong?.id,
                    onSongClick = onSongClick
                )
            }
        }

        if (trending.isNotEmpty()) {
            item(key = "section_trending", contentType = "SectionHeader") {
                SectionTitle("Trending Now")
            }
            itemsIndexed(
                items = trendingTop,
                key = { index, it -> "${it.id}_$index" },
                contentType = { _, _ -> "SongRow" }
            ) { index, song ->
                Box(modifier = Modifier.staggeredEntry(index)) {
                    SongRow(
                        song = song,
                        rank = index + 1,
                        isPlaying = currentSong?.id == song.id,
                        onClick = { onSongClick(song) },
                        onAddToQueue = { onQueueById(song.id) },
                        onPlayNext = { onPlayNextById(song.id) },
                        onAddToPlaylist = { playlistId -> onPlaylistById(song.id, playlistId) }
                    )
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            item(key = "section_recs", contentType = "SectionHeader") {
                SectionTitle(
                    if (recommendationSeedTitle != null) "More like $recommendationSeedTitle"
                    else "Recommended For You"
                )
            }
            itemsIndexed(
                items = recsTop,
                key = { index, it -> "rec_${it.id}_$index" },
                contentType = { _, _ -> "SongRow" }
            ) { index, song ->
                Box(modifier = Modifier.staggeredEntry(index)) {
                    SongRow(
                        song = song,
                        rank = null,
                        isPlaying = currentSong?.id == song.id,
                        onClick = { onSongClick(song) },
                        onAddToQueue = { onQueueById(song.id) },
                        onPlayNext = { onPlayNextById(song.id) },
                        onAddToPlaylist = { playlistId -> onPlaylistById(song.id, playlistId) }
                    )
                }
            }
        }

        
        if (trending.isEmpty() && recommendations.isEmpty()) {
            if (isLoading) {
                // Hoist shimmer brush so all 7 rows share one InfiniteTransition
                item(key = "shimmer_list") {
                    ShimmerSongList(count = 7)
                }
            } else {
                
                item {
                    val colors = LocalDreaminColors.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Couldn't load songs",
                                color = colors.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = onRefresh,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = colors.primary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        
        item { Spacer(modifier = Modifier.height(16.dp)) }
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
private fun NowPlayingProgressSlider(
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

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration((displayProgress * durationMs).toLong()),
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                formatDuration(durationMs),
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
    onCreatePlaylist: (String) -> Unit = {},
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
    onBack: () -> Unit = {}
) {
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSavePlaylistDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
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
                                                    artworkScope.launch {
                                                        artworkOffsetX.animateTo(-420f, tween(140, easing = FastOutLinearInEasing))
                                                        onNext()
                                                        artworkOffsetX.snapTo(380f)
                                                        artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
                                                    }
                                                } else if (currentOffset > 75f) {
                                                    artworkScope.launch {
                                                        artworkOffsetX.animateTo(420f, tween(140, easing = FastOutLinearInEasing))
                                                        onPrevious()
                                                        artworkOffsetX.snapTo(-380f)
                                                        artworkOffsetX.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
                                                    }
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

                            val dragX = artworkOffsetX.value
                            val sheenOffset = (dragX * 2.2f).coerceIn(-400f, 400f)
                            val sheenAlpha = (kotlin.math.abs(dragX) / 100f).coerceIn(0f, 0.45f)
                            val borderAlpha = (0.14f + (kotlin.math.abs(dragX) / 350f)).coerceIn(0.14f, 0.48f)

                            val artworkShape = RoundedCornerShape(26.dp)
                            Box(
                                modifier = Modifier
                                    .size(270.dp)
                                    .graphicsLayer {
                                        shape = artworkShape
                                        clip = true
                                        shadowElevation = 20f
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .clip(artworkShape)
                                    .border(1.5.dp, Color.White.copy(alpha = borderAlpha), artworkShape)
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                        onDoubleClick = {
                                            onToggleFavorite()
                                            showHeartBurst = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = song.id,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(200))
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

                                // 🎚️ Holographic Vinyl Specular Sheen Light Reflection on 3D Drag
                                if (sheenAlpha > 0.005f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
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
                targetState = song,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "song_meta_crossfade"
            ) { currentSong ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentSong.displayTitle,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currentSong.artist,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.68f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onArtistClick(currentSong.artist) }
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val isDownloaded = state.downloadedSongs.any { it.id == currentSong.id }
                    val isDownloading = state.downloadingSongIds.contains(currentSong.id)

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
                                if (isDownloaded) onDeleteDownload(currentSong.id)
                                else onDownload(currentSong)
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

                // Playback controls — prev / play / next
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
                            ) { onPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = colors.onSurface, modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.width(20.dp))

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

                    Spacer(modifier = Modifier.width(20.dp))

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
                            ) { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = colors.onSurface, modifier = Modifier.size(32.dp))
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
            onCreate = { playlistName ->
                onCreatePlaylist(playlistName)
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
private fun EditNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
                        color = Color(0xFFFF5252).copy(alpha = particleAlpha),
                        radius = particleRadius,
                        center = Offset(px, py)
                    )
                    // Secondary gold sparkle
                    val goldDist = distance * 0.75f
                    val goldAngle = Math.toRadians((i * 60.0) + 30.0 - (progress * 20.0))
                    val gx = (center.x + goldDist * kotlin.math.cos(goldAngle)).toFloat()
                    val gy = (center.y + goldDist * kotlin.math.sin(goldAngle)).toFloat()
                    drawCircle(
                        color = Color(0xFFFFD166).copy(alpha = particleAlpha * 0.85f),
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
                tint = if (isFavorite) Color(0xFFFF5252) else colors.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .scale(heartScale.value)
            )
        }
    }
}

@Composable
private fun SleepTimerChip(timeRemainingLabel: String, onCancel: () -> Unit) {
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
private fun UpNextRow(
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
                        Icons.Outlined.PlaylistAdd,
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
                .padding(bottom = 16.dp)
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
    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 8f else 0f,
        label = "drag_elevation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.toInt()) }
            .onGloballyPositioned { onHeightMeasured(it.size.height.toFloat()) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { shadowElevation = elevation }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onClick() }
            .background(
                if (isPlaying || isDragging) colors.surfaceHigh else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = "Drag to reorder",
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
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
            contentDescription = null,
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
                contentDescription = "Remove",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}





@Composable
fun LibraryScreen(
    state: PlayerUiState,
    onSongClick: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (Long) -> Unit = {},
    onPlayPlaylist: (Long) -> Unit = {},
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
    onRemoveSongFromPlaylist: (Long, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
    onSongClickFromPlaylist: (Song, List<Song>) -> Unit = { song, _ -> onSongClick(song) },
    onShufflePlaylist: (List<Song>) -> Unit = {},
    onDownloadPlaylist: (List<Song>) -> Unit = {},
    onDownloadSong: (Song) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    onImportSpotifyPlaylist: (String) -> Unit = {},
    onResetSpotifyImportState: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val tabs = listOf("Playlists", "Favourites", "Downloads")
    val tabPagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalPlaylists provides state.playlists) {

    val openPlaylist = state.openPlaylistId?.let { id -> state.playlists.find { it.id == id } }
    if (openPlaylist != null) {
        PlaylistDetailScreen(
            playlist = openPlaylist,
            songs = state.openPlaylistSongs,
            currentSong = state.currentSong,
            downloadedSongIds = remember(state.downloadedSongs) { state.downloadedSongs.map { it.id }.toSet() },
            downloadingSongIds = state.downloadingSongIds,
            onBack = onClosePlaylist,
            onSongClick = { song -> onSongClickFromPlaylist(song, state.openPlaylistSongs) },
            onPlayAll = { onPlayPlaylist(openPlaylist.id) },
            onShuffle = { onShufflePlaylist(state.openPlaylistSongs) },
            onDownloadAll = { onDownloadPlaylist(state.openPlaylistSongs) },
            onDownloadSong = onDownloadSong,
            onDeleteDownload = onDeleteDownload,
            onRemoveSong = { songId -> onRemoveSongFromPlaylist(openPlaylist.id, songId) },
            onRename = { newName -> onRenamePlaylist(openPlaylist.id, newName) }
        )
    } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {

        Text(
            "Library",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        TabRow(
            selectedTabIndex = tabPagerState.currentPage,
            containerColor = colors.background,
            contentColor = colors.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tabPagerState.currentPage == index,
                    onClick = { scope.launch { tabPagerState.scrollToPage(index) } },
                    text = {
                        Text(
                            title,
                            color = if (tabPagerState.currentPage == index) colors.primary else colors.onSurfaceVariant,
                            fontWeight = if (tabPagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = tabPagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            key = { tabs[it] }
        ) { page ->
            when (page) {
                0 -> PlaylistsTab(
                    playlists = state.playlists,
                    playlistArtworks = state.playlistArtworks,
                    onCreatePlaylist = onCreatePlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onPlayPlaylist = onPlayPlaylist,
                    onOpenPlaylist = onOpenPlaylist,
                    spotifyImportState = state.spotifyImportState,
                    onImportSpotify = onImportSpotifyPlaylist,
                    onResetSpotifyImport = onResetSpotifyImportState
                )
                1 -> FavoritesTab(
                    favorites = state.favorites,
                    currentSong = state.currentSong,
                    onSongClick = onSongClick,
                    onAddToPlaylist = onAddToPlaylist
                )
                else -> DownloadsTab(
                    downloadedSongs = state.downloadedSongs,
                    currentSong = state.currentSong,
                    onSongClick = onSongClick,
                    onDeleteDownload = onDeleteDownload,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }
    }
    } // end else
    } // end CompositionLocalProvider(LocalPlaylists)
}

@Composable
private fun DownloadsTab(
    downloadedSongs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> }
) {
    val colors = LocalDreaminColors.current
    if (downloadedSongs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No offline downloads yet", fontSize = 18.sp, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap the download icon on any song to listen without internet",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${downloadedSongs.size} offline tracks",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Ready Offline",
                            color = colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            itemsIndexed(
                items = downloadedSongs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "SongRow" }
            ) { index, song ->
                Box(modifier = Modifier.staggeredEntry(index)) {
                    SongRow(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        onClick = { onSongClick(song) },
                        onAddToQueue = {},
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(song, playlistId) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> }
) {
    val colors = LocalDreaminColors.current
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No favorites yet", fontSize = 18.sp, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap \u2665 on any song to save it here",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = favorites,
                key = { _, song -> song.id },
                contentType = { _, _ -> "SongRow" }
            ) { index, song ->
                Box(modifier = Modifier.staggeredEntry(index)) {
                    SongRow(
                        song = song,
                        isPlaying = currentSong?.id == song.id,
                        onClick = { onSongClick(song) },
                        onAddToQueue = {},
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(song, playlistId) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    playlistArtworks: Map<Long, List<String>> = emptyMap(),
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    spotifyImportState: SpotifyImportState = SpotifyImportState.Idle,
    onImportSpotify: (String) -> Unit = {},
    onResetSpotifyImport: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(spotifyImportState) {
        if (spotifyImportState !is SpotifyImportState.Idle) {
            showCreateDialog = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${playlists.size} playlists",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primary.copy(alpha = 0.15f))
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Text("New", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "create_new_playlist_card") {
                CreatePlaylistCard(onClick = { showCreateDialog = true })
            }

            itemsIndexed(
                items = playlists,
                key = { _, playlist -> playlist.id },
                contentType = { _, _ -> "PlaylistCard" }
            ) { index, playlist ->
                Box(modifier = Modifier.staggeredEntry(index + 1, baseDelayMs = 28)) {
                    PlaylistGridCard(
                        playlist = playlist,
                        artworkUrls = playlistArtworks[playlist.id] ?: emptyList(),
                        onOpen = { onOpenPlaylist(playlist.id) },
                        onPlay = { onPlayPlaylist(playlist.id) },
                        onDelete = { onDeletePlaylist(playlist.id) }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
            spotifyImportState = spotifyImportState,
            onImportSpotify = onImportSpotify,
            onResetSpotifyImport = onResetSpotifyImport
        )
    }
}

@Composable
fun PlaylistCoverArt(
    artworkUrls: List<String>,
    coverUrl: String? = null,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceHighest),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val urls = artworkUrls.filter { it.isNotBlank() }.distinct().take(4)
            when {
                urls.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        colors.primary.copy(alpha = 0.25f),
                                        colors.secondary.copy(alpha = 0.15f),
                                        colors.surfaceHighest
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(size * 0.45f)
                        )
                    }
                }
                urls.size < 4 -> {
                    AsyncImage(
                        model = urls.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AsyncImage(model = urls[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                            Spacer(modifier = Modifier.width(1.dp).background(colors.background))
                            AsyncImage(model = urls[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        }
                        Spacer(modifier = Modifier.height(1.dp).background(colors.background))
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AsyncImage(model = urls[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                            Spacer(modifier = Modifier.width(1.dp).background(colors.background))
                            AsyncImage(model = urls[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistCard(
    onClick: () -> Unit
) {
    val colors = LocalDreaminColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = 0.55f),
                        colors.secondary.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .background(colors.surfaceHigh.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceHighest.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "New Playlist",
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "New Playlist",
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Create or Import",
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1
        )
        Text(
            text = "Spotify & Local",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: com.shyan.dreamin.data.local.Playlist,
    artworkUrls: List<String> = emptyList(),
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalDreaminColors.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceHigh.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onOpen() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
        ) {
            PlaylistCoverArt(
                artworkUrls = artworkUrls,
                coverUrl = playlist.coverUrl,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxSize()
            )

            // Quick Play floating action overlay button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White)
                    ) { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = colors.background,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                Text(
                    text = playlist.name,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (artworkUrls.isNotEmpty()) "${artworkUrls.size} tracks" else "Playlist",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(colors.surfaceHighest, RoundedCornerShape(14.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Play", color = colors.onSurface) },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.primary) },
                        onClick = {
                            showMenu = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFEF4444)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
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
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    spotifyImportState: SpotifyImportState = SpotifyImportState.Idle,
    onImportSpotify: (String) -> Unit = {},
    onResetSpotifyImport: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var spotifyUrl by remember { mutableStateOf("") }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

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
                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceHighest)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                            "Custom Name",
                            color = if (selectedTab == 0) colors.primary else colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) colors.surfaceHigh else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Spotify Import",
                            color = if (selectedTab == 1) Color(0xFF1DB954) else colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            if (selectedTab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create a blank playlist and add songs anytime:",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.5.sp
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Playlist name", color = colors.onSurfaceVariant) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
                            cursorColor = colors.primary,
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceHighest,
                            unfocusedContainerColor = colors.surfaceHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val st = spotifyImportState) {
                        is SpotifyImportState.Idle -> {
                            Text(
                                "Paste public Spotify playlist URL or share link to match and import tracks:",
                                color = colors.onSurfaceVariant,
                                fontSize = 12.5.sp
                            )
                            OutlinedTextField(
                                value = spotifyUrl,
                                onValueChange = { spotifyUrl = it },
                                placeholder = { Text("https://open.spotify.com/playlist/...", color = colors.onSurfaceVariant, fontSize = 11.5.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    if (spotifyUrl.isBlank()) {
                                        TextButton(onClick = {
                                            val clip = clipboardManager.getText()?.text.orEmpty()
                                            if (clip.isNotBlank()) spotifyUrl = clip
                                        }) {
                                            Text("PASTE", color = Color(0xFF1DB954), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        IconButton(onClick = { spotifyUrl = "" }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1DB954),
                                    unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
                                    cursorColor = Color(0xFF1DB954),
                                    focusedTextColor = colors.onSurface,
                                    unfocusedTextColor = colors.onSurface,
                                    focusedContainerColor = colors.surfaceHighest,
                                    unfocusedContainerColor = colors.surfaceHighest
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is SpotifyImportState.FetchingMetadata -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF1DB954),
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        "Extracting Spotify tracklist...",
                                        color = colors.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                // 🌊 Luminous Shimmer Rows while metadata loads
                                repeat(3) {
                                    ShimmerSongRow()
                                }
                            }
                        }
                        is SpotifyImportState.MatchingTracks -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (st.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(st.coverUrl)
                                                .crossfade(200)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            st.playlistTitle,
                                            color = colors.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Matched ${st.matchedCount} of ${st.totalTracks} songs",
                                            color = Color(0xFF1DB954),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                val progress = if (st.totalTracks > 0)
                                    (st.currentTrackIndex.toFloat() / st.totalTracks.toFloat()).coerceIn(0f, 1f)
                                else 0f

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF1DB954),
                                    trackColor = colors.surfaceHighest
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
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    "Import Complete!",
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Saved ${st.matchedCount} songs into '${st.playlistTitle}'",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        is SpotifyImportState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    st.message,
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) onCreate(name)
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Create", color = if (name.isNotBlank()) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            } else {
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
private fun SaveQueueDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
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

private fun formatTotalPlaylistDuration(songs: List<Song>): String {
    val totalSecs = songs.sumOf { it.duration.toLong() }
    if (totalSecs <= 0) return ""
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    return if (hours > 0) {
        "${hours} hr ${minutes} min"
    } else {
        "${minutes} min"
    }
}

@Composable
private fun MiniEqualizerIndicator(
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
private fun PlaylistDetailScreen(
    playlist: com.shyan.dreamin.data.local.Playlist,
    songs: List<Song>,
    currentSong: Song?,
    downloadedSongIds: Set<String> = emptySet(),
    downloadingSongIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit = {},
    onDownloadSong: (Song) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    onRemoveSong: (String) -> Unit,
    onRename: (String) -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalDreaminColors.current
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val songCount = remember(songs.size) { songs.size }
    val playingId = remember(currentSong?.id) { currentSong?.id }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val durationLabel = remember(songs) { formatTotalPlaylistDuration(songs) }

    val allDownloaded = remember(songs, downloadedSongIds) {
        songs.isNotEmpty() && songs.all { downloadedSongIds.contains(it.id) }
    }

    val heroArt = playlist.coverUrl ?: songs.firstOrNull()?.displayArtworkUrl

    // 🎨 1. Dynamic Dominant Color Extraction & Smooth Bloom (Instant 0ms LRU Cache)
    var extractedDominant by remember(heroArt) {
        mutableStateOf(com.shyan.dreamin.data.service.PaletteMemoryCache.getCachedColor(heroArt) ?: colors.primary)
    }
    LaunchedEffect(heroArt) {
        if (!heroArt.isNullOrBlank()) {
            val cached = com.shyan.dreamin.data.service.PaletteMemoryCache.getCachedColor(heroArt)
            if (cached != null) {
                extractedDominant = cached
            } else {
                val color = com.shyan.dreamin.data.service.PaletteMemoryCache.extractDominantColor(context, heroArt)
                if (color != null) {
                    extractedDominant = color
                }
            }
        }
    }
    val animatedDominant by animateColorAsState(
        targetValue = extractedDominant,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "playlist_ambient_dominant"
    )

    // 🔍 2. In-Playlist Instant Filter & Search
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else {
            val q = searchQuery.trim().lowercase()
            songs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.surfaceHighest,
                    contentColor = colors.onSurface,
                    actionColor = colors.primary,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 🌌 Ambient Fluid Color Bloom Header at Top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedDominant.copy(alpha = 0.52f),
                                    animatedDominant.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(200f, 60f),
                                radius = 800f
                            )
                        )
                )
                if (!heroArt.isNullOrBlank()) {
                    AsyncImage(
                        model = heroArt,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .blur(64.dp)
                            .alpha(0.30f),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to colors.background.copy(alpha = 0.65f),
                                    0.85f to colors.background.copy(alpha = 0.95f),
                                    1.0f to colors.background
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar with Animated Search Input
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    },
                    label = "playlist_search_bar"
                ) { searchActive ->
                    if (searchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = colors.onSurface
                                )
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Filter tracks in playlist...", color = colors.onSurfaceVariant, fontSize = 13.5.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.onSurfaceVariant)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = animatedDominant,
                                    unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.35f),
                                    focusedContainerColor = colors.surfaceHighest.copy(alpha = 0.85f),
                                    unfocusedContainerColor = colors.surfaceHighest.copy(alpha = 0.65f),
                                    cursorColor = animatedDominant
                                ),
                                modifier = Modifier.weight(1f).height(50.dp)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = colors.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                playlist.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { isSearchActive = true }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = "Search in playlist",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Rename",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(animatedDominant.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.MusicNote,
                                    contentDescription = null,
                                    tint = animatedDominant,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Text("Playlist is empty", fontSize = 20.sp, color = colors.onSurface, fontWeight = FontWeight.Bold)
                            Text(
                                "Add songs by searching or tapping \"Add to playlist\" on any song",
                                fontSize = 14.sp,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (filteredSongs.isEmpty() && searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                "No tracks found for \"$searchQuery\"",
                                color = colors.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = { searchQuery = "" }) {
                                Text("Clear filter", color = animatedDominant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Hero Header Item
                        if (!isSearchActive || searchQuery.isBlank()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    PlaylistCoverArt(
                                        artworkUrls = songs.map { it.displayArtworkUrl },
                                        coverUrl = playlist.coverUrl,
                                        size = 164.dp,
                                        shape = RoundedCornerShape(22.dp),
                                        modifier = Modifier.graphicsLayer { shadowElevation = 18f }
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = playlist.name,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    Text(
                                        text = "$songCount ${if (songCount == 1) "track" else "tracks"}${if (durationLabel.isNotBlank()) " • $durationLabel" else ""}",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                                    )

                                    // Action Buttons Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = onPlayAll,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = animatedDominant,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f).height(46.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Play All", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = onShuffle,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.surfaceHigh.copy(alpha = 0.85f),
                                                contentColor = colors.onSurface
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f).height(46.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Shuffle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Shuffle", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        }

                                        IconButton(
                                            onClick = {
                                                if (!allDownloaded) {
                                                    onDownloadAll()
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Downloading all tracks...",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (allDownloaded) colors.secondary.copy(alpha = 0.2f)
                                                    else colors.surfaceHigh.copy(alpha = 0.85f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (allDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.Download,
                                                contentDescription = "Download all",
                                                tint = if (allDownloaded) colors.secondary else colors.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        itemsIndexed(
                            items = filteredSongs,
                            key = { idx, song -> "${song.id}_$idx" },
                            contentType = { _, _ -> "PlaylistSongRow" }
                        ) { idx, song ->
                            val isDownloaded = downloadedSongIds.contains(song.id)
                            val isDownloading = downloadingSongIds.contains(song.id)

                            Box(modifier = Modifier.staggeredEntry(idx)) {
                                PlaylistSongRow(
                                    song = song,
                                    index = idx + 1,
                                    isPlaying = playingId == song.id,
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    onClick = { onSongClick(song) },
                                    onDownload = {
                                        if (isDownloaded) onDeleteDownload(song.id)
                                        else onDownloadSong(song)
                                    },
                                    onRemove = {
                                        val title = song.displayTitle
                                        onRemoveSong(song.id)
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar(
                                                message = "\"$title\" removed from playlist",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                onRename(newName)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistSongRow(
    song: Song,
    index: Int,
    isPlaying: Boolean,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onClick: () -> Unit,
    onDownload: () -> Unit = {},
    onRemove: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val bgColor = if (isPlaying) colors.primary.copy(alpha = 0.12f) else Color.Transparent
    val textColor = if (isPlaying) colors.primary else colors.onSurface
    val textWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Index or Playing Indicator
        Box(
            modifier = Modifier.width(26.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                MiniEqualizerIndicator(color = colors.primary)
            } else {
                Text(
                    text = String.format("%02d", index),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Artwork
        ArtworkBox(song.displayArtworkUrl, isPlaying, colors)

        Spacer(modifier = Modifier.width(12.dp))

        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle,
                color = textColor,
                fontWeight = textWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = colors.onSurfaceVariant,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Download Action
        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.secondary, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.Download,
                    contentDescription = if (isDownloaded) "Downloaded" else "Download",
                    tint = if (isDownloaded) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        // Remove Action
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.RemoveCircleOutline,
                contentDescription = "Remove",
                tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun ArtworkBox(
    artworkUrl: String,
    isPlaying: Boolean,
    colors: DreaminColors
) {
    Box(modifier = Modifier.size(52.dp)) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BlackOverlay50),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val colors = LocalDreaminColors.current
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
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
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }, enabled = name.isNotBlank()) {
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
private fun StatsTab(stats: com.shyan.dreamin.data.model.ListeningStats) {
    val colors = LocalDreaminColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("This week", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatsCard(
                    modifier = Modifier.weight(1f),
                    label = "Songs played",
                    value = "${stats.songsThisWeek}"
                )
                StatsCard(
                    modifier = Modifier.weight(1f),
                    label = "Minutes listened",
                    value = "${stats.minutesThisWeek}"
                )
            }
        }
        stats.topSongThisWeek?.let { song ->
            item { Text("Top song", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceHigh, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = song.displayArtworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(song.displayTitle, color = colors.onSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
        stats.topArtistThisWeek?.let { artist ->
            item { Text("Top artist", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceHigh, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(artist, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
        if (stats.songsThisWeek == 0) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Play some songs to see your stats here", color = colors.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatsCard(modifier: Modifier = Modifier, label: String, value: String) {
    val colors = LocalDreaminColors.current
    Column(
        modifier = modifier
            .background(colors.surfaceHigh, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
}





@Composable
private fun DreaminInfoSheet(onDismiss: () -> Unit) {
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
private fun InfoFeatureRow(icon: ImageVector, title: String, description: String) {
    val colors = LocalDreaminColors.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column {
            Text(title, color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeHeader(
    titleProgress: () -> Float,
    timeOfDay: String,
    userName: String,
    onLongPressName: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onActivateSearch: () -> Unit,
    recentSearches: List<String>,
    onClearRecentSearches: () -> Unit
) {
    val colors = LocalDreaminColors.current
    var showInfoSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DREAMIN",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.5.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showInfoSheet = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "App info",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showInfoSheet) {
            DreaminInfoSheet(onDismiss = { showInfoSheet = false })
        }

        // Greeting (clean text without emojis, long click to edit name)
        Text(
            "$timeOfDay, $userName",
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .graphicsLayer { alpha = (1f - titleProgress() * 2.5f).coerceIn(0f, 1f) }
                .combinedClickable(onClick = {}, onLongClick = onLongPressName)
        )
        Spacer(modifier = Modifier.height(10.dp))
        DreaminSearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            onClear = onClearSearch,
            onActivate = onActivateSearch,
            recentSearches = recentSearches,
            onRecentSearchClick = onSearchChange,
            onClearRecentSearches = onClearRecentSearches
        )
    }
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
