@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.shyan.dreamin.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.data.model.TrackRepeatMode
import com.shyan.dreamin.viewmodel.MusicPlayerViewModel





val Background = Color(0xFF000000)
val SurfaceContainer = Color(0xFF0A0A0A)
val SurfaceHigh = Color(0xFF121212)
val SurfaceHighest = Color(0xFF1A1A1A)
val SurfaceBright = Color(0xFF202020)
val Primary = Color(0xFFABA3FF)
val PrimaryDim = Color(0xFF6E60E8)
val Secondary = Color(0xFFB191FF)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceVariant = Color(0xFFADAAAA)
val OutlineVariant = Color(0xFF484847).copy(alpha = 0.15f)








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

    LaunchedEffect(state.currentSong?.id) {
        state.currentSong?.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            vm.extractColorsFromArtwork(url)
        }
    }

    CompositionLocalProvider(LocalDreaminColors provides SonicNocturneColors) {
        DreaminRippleTheme {
            when {
                state.userName == null -> Box(Modifier.fillMaxSize().background(SonicNocturneColors.background))
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
    val progress by vm.progressFlow.collectAsStateWithLifecycle()
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

    LaunchedEffect(currentScreen) {
        val idx = navScreens.indexOf(currentScreen)
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    val trendingCharts = state.trendingCharts
    val onHomeSongClick = remember(onOpenNowPlaying, trendingCharts) {
        { song: Song -> vm.playSongFromList(song, trendingCharts); onOpenNowPlaying() }
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

    BackHandler(enabled = !isNowPlayingOpen && currentScreen != navScreens.first()) {
        val idx = navScreens.indexOf(currentScreen)
        if (idx > 0) onScreenChange(navScreens[idx - 1])
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = !isNowPlayingOpen && state.currentSong != null,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        MiniPlayer(
                            song              = state.currentSong,
                            playbackState     = state.playbackState,
                            currentPositionMs = progress.currentPositionMs,
                            durationMs        = progress.durationMs,
                            onPlayPause       = vm::togglePlayPause,
                            onNext            = vm::playNext,
                            onPrevious        = vm::playPrevious,
                            onExpand          = onOpenNowPlaying
                        )
                    }
                    BottomNavBar(
                        currentScreen  = currentScreen,
                        onScreenChange = { screen ->
                            onScreenChange(screen)
                            scope.launch { pagerState.scrollToPage(navScreens.indexOf(screen)) }
                        }
                    )
                }
            }
        ) { padding ->
            HorizontalPager(
                state             = pagerState,
                modifier          = Modifier.fillMaxSize().padding(padding),
                userScrollEnabled = false,
                key               = { navScreens[it].name }
            ) { page ->
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    when (navScreens[page]) {
                        Screen.Home -> HomeScreen(
                            trendingCharts           = trendingCharts,
                            recommendations          = state.recommendations,
                            recentlyPlayed           = state.recentlyPlayed,
                            topSongs                 = state.topSongs,
                            currentSong              = state.currentSong,
                            isSearchActive           = state.isSearchActive,
                            searchQuery              = state.searchQuery,
                            searchResults            = state.searchResults,
                            isLoadingChart           = state.isLoadingChart,
                            isLoadingMoreSearch      = state.isLoadingMoreSearch,
                            hasMoreSearchResults     = state.hasMoreSearchResults,
                            recommendationSeedTitle  = state.recommendationSeedTitle,
                            userName                 = state.userName.orEmpty(),
                    playlists                = state.playlists,
                    recentSearches           = state.recentSearches,
                    lastSession              = state.lastSession,
                    onSongClick              = onHomeSongClick,
                    onShuffleFab             = onHomeShuffleFab,
                    onAddToQueue             = vm::addToQueue,
                    onSearchChange           = vm::setSearchQuery,
                    onClearSearch            = vm::clearSearch,
                    onActivateSearch         = vm::activateSearch,
                    onRefresh                = vm::refreshData,
                    onLoadMoreSearch         = vm::loadMoreSearchResults,
                    onAddToPlaylist          = { song, playlistId -> vm.addSongToPlaylist(playlistId, song) },
                    onEditName               = vm::saveUserName,
                    onClearRecentSearches    = vm::clearRecentSearches,
                    onResumeLastSession      = onResumeSession
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
                            onShufflePlaylist        = { songs -> vm.shuffleAndPlayPlaylist(songs); onOpenNowPlaying() }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible  = isNowPlayingOpen,
            enter    = slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit     = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            NowPlayingScreen(
                state              = state,
                currentPositionMs  = progress.currentPositionMs,
                durationMs         = progress.durationMs,
                onPlayPause        = vm::togglePlayPause,
                onNext             = vm::playNext,
                onPrevious         = vm::playPrevious,
                onSeek             = vm::seekTo,
                onToggleShuffle    = vm::toggleShuffle,
                onToggleRepeat     = vm::toggleRepeat,
                onToggleFavorite   = vm::toggleFavorite,
                onSetSleepTimer    = vm::setSleepTimer,
                onCancelSleepTimer = vm::cancelSleepTimer,
                onSongSelect       = { song -> vm.playSong(song, fromPlaylist = state.playlistQueueActive) },
                onAddToPlaylist    = { playlistId ->
                    state.currentSong?.let { vm.addSongToPlaylist(playlistId, it) }
                },
                onRemoveFromQueue    = vm::removeFromQueue,
                onReorderQueue       = vm::reorderQueue,
                onSaveQueueAsPlaylist = vm::saveQueueAsPlaylist,
                onBack               = onCloseNowPlaying
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





@Composable
fun BottomNavBar(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit
) {
    val colors = LocalDreaminColors.current
    Surface(color = colors.surfaceContainer, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Screen.entries.forEach { screen ->
                val isSelected = screen == currentScreen
                val tint by animateColorAsState(
                    targetValue    = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    animationSpec  = tween(150),
                    label          = "nav_tint_${screen.name}"
                )
                val iconScale by animateFloatAsState(
                    targetValue   = if (isSelected) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label         = "nav_scale_${screen.name}"
                )
                IconButton(onClick = { onScreenChange(screen) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (isSelected) when (screen) {
                            Screen.Home    -> Icons.Filled.Home
                            Screen.Library -> Icons.Filled.LibraryMusic
                        } else screen.icon,
                        contentDescription = screen.title,
                        tint               = tint,
                        modifier           = Modifier.size(24.dp).scale(iconScale)
                    )
                }
            }
        }
    }
}





@Composable
private fun MiniPlayerProgressBar(positionMs: Long, durationMs: Long, accentColor: Color? = null) {
    val colors = LocalDreaminColors.current
    val barColor = accentColor ?: colors.primary
    val trackColor = colors.surfaceHighest
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawRect(trackColor)
                drawRect(barColor, size = size.copy(width = size.width * progress))
            }
    )
}

@Composable
fun MiniPlayer(
    song: Song?,
    playbackState: PlaybackState,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    onExpand: () -> Unit
) {
    val s = song ?: return
    val colors = LocalDreaminColors.current
    val accentColor = colors.primary
    val swipeXAnim = remember { Animatable(0f) }
    val swipeYAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column {
        MiniPlayerProgressBar(
            positionMs = currentPositionMs,
            durationMs = durationMs,
            accentColor = accentColor
        )

        Surface(
            color = colors.surfaceHighest.copy(alpha = 0.8f),
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .offset { IntOffset(swipeXAnim.value.toInt(), swipeYAnim.value.toInt()) }
                    .pointerInput(onNext, onPrevious, onExpand) {
                        var totalX = 0f
                        var totalY = 0f
                        detectDragGestures(
                            onDragStart = {
                                totalX = 0f
                                totalY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalX += dragAmount.x
                                totalY += dragAmount.y
                                // Only track one axis per gesture
                                val absX = kotlin.math.abs(totalX)
                                val absY = kotlin.math.abs(totalY)
                                if (absY > absX && totalY < -30f) {
                                    scope.launch { swipeYAnim.snapTo(totalY.coerceIn(-120f, 0f)) }
                                } else if (absX > absY && absX > 30f) {
                                    scope.launch { swipeXAnim.snapTo(totalX.coerceIn(-200f, 200f)) }
                                }
                            },
                            onDragEnd = {
                                if (swipeYAnim.value < -80f) onExpand()
                                when {
                                    swipeXAnim.value < -120f -> onNext()
                                    swipeXAnim.value > 120f -> onPrevious()
                                }
                                scope.launch {
                                    launch { swipeXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    launch { swipeYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    launch { swipeXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    launch { swipeYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                }
                            }
                        )
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork + text area is the tap target for expand — separate from swipe zone
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = colors.primary)
                        ) { onExpand() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(s.artworkUrl)
                            .crossfade(200)
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





@Composable
fun HomeScreen(
    trendingCharts: List<Song>,
    recommendations: List<Song>,
    recentlyPlayed: List<Song>,
    topSongs: List<Song>,
    currentSong: Song?,
    isSearchActive: Boolean,
    searchQuery: String,
    searchResults: List<Song>,
    isLoadingChart: Boolean,
    isLoadingMoreSearch: Boolean,
    hasMoreSearchResults: Boolean,
    recommendationSeedTitle: String?,
    userName: String,
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    recentSearches: List<String>,
    lastSession: com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession?,
    onSongClick: (Song) -> Unit,
    onShuffleFab: () -> Unit = {},
    onAddToQueue: (Song) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onActivateSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMoreSearch: () -> Unit = {},
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onEditName: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onResumeLastSession: () -> Unit = {},
) {
    val colors = LocalDreaminColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val titleProgress by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val past = listState.firstVisibleItemIndex > 0
            if (past) 1f else (offset / 200f).coerceIn(0f, 1f)
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

    BackHandler(enabled = isSearchActive) { onClearSearch() }

    val onSongClickWithKeyboardDismiss = remember(onSongClick, keyboard) {
        { song: Song -> keyboard?.hide(); onSongClick(song) }
    }

    CompositionLocalProvider(LocalPlaylists provides playlists) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearchActive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                DreaminSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    onClear = onClearSearch,
                    autoFocus = true,
                    recentSearches = recentSearches,
                    onRecentSearchClick = onSearchChange,
                    onClearRecentSearches = onClearRecentSearches
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (searchQuery.isNotEmpty()) SearchResults(
                    songs = searchResults,
                    currentSong = currentSong,
                    onSongClick = onSongClickWithKeyboardDismiss,
                    onAddToQueue = onAddToQueue,
                    hasMore = hasMoreSearchResults,
                    isLoadingMore = isLoadingMoreSearch,
                    onLoadMore = onLoadMoreSearch,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isLoadingChart,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                HomeContent(
                    trending = trendingCharts,
                    recommendations = recommendations,
                    recentlyPlayed = recentlyPlayed,
                    topSongs = topSongs,
                    recommendationSeedTitle = recommendationSeedTitle,
                    currentSong = currentSong,
                    onSongClick = onSongClick,
                    onAddToQueue = onAddToQueue,
                    onRefresh = onRefresh,
                    listState = listState,
                    isLoading = isLoadingChart,
                    playlists = playlists,
                    onAddToPlaylist = onAddToPlaylist,
                    titleProgress = { titleProgress },
                    timeOfDay = timeOfDay,
                    userName = userName,
                    onSearchChange = onSearchChange,
                    onClearSearch = onClearSearch,
                    onActivateSearch = onActivateSearch,
                    searchQuery = searchQuery,
                    onLongPressName = { showEditNameDialog = true },
                    recentSearches = recentSearches,
                    onClearRecentSearches = onClearRecentSearches,
                    lastSession = lastSession,
                    onResumeLastSession = onResumeLastSession
                )
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
    recentSearches: List<String> = emptyList(),
    onRecentSearchClick: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    var isFocused by remember { mutableStateOf(false) }
    val showRecents = isFocused && query.isEmpty() && recentSearches.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    if (autoFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onActivate()
                },
            placeholder = { Text("Search songs, artists...", color = colors.onSurfaceVariant) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = colors.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF000000),
                unfocusedContainerColor = Color(0xFF000000),
                focusedBorderColor = colors.primary.copy(alpha = 0.3f),
                unfocusedBorderColor = Color.Transparent,
                cursorColor = colors.primary,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface
            )
        )

        AnimatedVisibility(visible = showRecents) {
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
fun SearchResults(
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> }
) {
    val colors = LocalDreaminColors.current
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No results found", color = colors.onSurfaceVariant, fontSize = 16.sp)
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
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isPlaying = currentSong?.id == song.id,
                onClick = { onSongClick(song) },
                onAddToQueue = { onAddToQueue(song) },
                onAddToPlaylist = { playlistId -> onAddToPlaylist(song, playlistId) }
            )
        }
        if (isLoadingMore) {
            item { ShimmerSongRow() }
        }
    }
}





private val BlackOverlay50 = Color.Black.copy(alpha = 0.50f)
private val BlackOverlay35 = Color.Black.copy(alpha = 0.35f)

private val shimmerColors = listOf(
    Color.White.copy(alpha = 0.05f),
    Color.White.copy(alpha = 0.13f),
    Color.White.copy(alpha = 0.05f),
)

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f)
    )
}

@Composable
fun ShimmerSongRow() {
    val brush = shimmerBrush()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
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
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        )
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
fun HorizontalSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDreaminColors.current
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = LocalDreaminColors.current.primary)
            ) { onClick() },
        horizontalAlignment = Alignment.Start
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.artworkUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            song.displayTitle,
            color = if (isPlaying) colors.primary else colors.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            color = colors.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                    .data(session.song.artworkUrl)
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
fun HomeContent(
    trending: List<Song>,
    recommendations: List<Song>,
    recentlyPlayed: List<Song>,
    topSongs: List<Song>,
    recommendationSeedTitle: String?,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState,
    isLoading: Boolean = false,
    playlists: List<com.shyan.dreamin.data.local.Playlist> = emptyList(),
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    titleProgress: () -> Float = { 0f },
    timeOfDay: String = "",
    userName: String = "",
    onSearchChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onActivateSearch: () -> Unit = {},
    searchQuery: String = "",
    onLongPressName: () -> Unit = {},
    recentSearches: List<String> = emptyList(),
    onClearRecentSearches: () -> Unit = {},
    lastSession: com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession? = null,
    onResumeLastSession: () -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    // Single stable lookup map — rebuilt only when song lists change, not on every state update
    val allSongsById = remember(trending, recommendations) {
        (trending + recommendations).associateBy { it.id }
    }
    val onClickById    = remember(onSongClick, allSongsById)     { { id: String -> allSongsById[id]?.let(onSongClick) } }
    val onQueueById    = remember(onAddToQueue, allSongsById)    { { id: String -> allSongsById[id]?.let(onAddToQueue) } }
    val onPlaylistById = remember(onAddToPlaylist, allSongsById) { { id: String, pid: Long -> allSongsById[id]?.let { onAddToPlaylist(it, pid) } } }
    val trendingTop    = remember(trending)       { trending.take(10) }
    val recsTop        = remember(recommendations) { recommendations.take(8) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            HomeHeader(
                titleProgress = titleProgress,
                timeOfDay = timeOfDay,
                userName = userName,
                onLongPressName = onLongPressName,
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                onClearSearch = onClearSearch,
                onActivateSearch = onActivateSearch,
                recentSearches = recentSearches,
                onClearRecentSearches = onClearRecentSearches
            )
        }
        
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
            item(key = "section_trending") {
                SectionTitle("Trending Now")
            }
            items(trendingTop, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onClickById(song.id) },
                    onAddToQueue = { onQueueById(song.id) },
                    onAddToPlaylist = { playlistId -> onPlaylistById(song.id, playlistId) }
                )
            }
        }


        if (recommendations.isNotEmpty()) {
            item(key = "section_recs") {
                SectionTitle(
                    if (recommendationSeedTitle != null) "More like $recommendationSeedTitle"
                    else "Recommended For You"
                )
            }
            items(recsTop, key = { "rec_${it.id}" }) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onClickById(song.id) },
                    onAddToQueue = { onQueueById(song.id) },
                    onAddToPlaylist = { playlistId -> onPlaylistById(song.id, playlistId) }
                )
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
    onAddToPlaylist: (Long) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val playlists = LocalPlaylists.current
    val swipeOffset = remember { Animatable(0f) }
    val swipeThreshold = 100f
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val swipeHintColor = remember(colors.primary) { colors.primary.copy(alpha = 0.2f) }
    val scope = rememberCoroutineScope()

    // Capture callbacks in refs so pointerInput doesn't need to restart on lambda change
    val onAddToQueueRef = rememberUpdatedState(onAddToQueue)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Background hint — only drawn when swiping, no recomposition during drag
        val swipeVal = swipeOffset.value
        if (swipeVal > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(swipeHintColor),
                contentAlignment = Alignment.CenterStart
            ) {
                if (swipeVal > swipeThreshold * 0.5f) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "Add to queue",
                        tint = colors.primary,
                        modifier = Modifier.padding(start = 20.dp)
                    )
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
                            if (captured > swipeThreshold) onAddToQueueRef.value()
                        },
                        onDragCancel = {
                            scope.launch { swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(0f, 200f)) }
                        }
                    )
                }
                .graphicsLayer { translationX = swipeOffset.value }
                .background(
                    if (isPlaying) colors.surfaceHigh else Color.Transparent,
                    RoundedCornerShape(16.dp)
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = colors.primary),
                    onClick = onClick,
                    onLongClick = { if (playlists.isNotEmpty()) showPlaylistPicker = true }
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.artworkUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BlackOverlay50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "Playing",
                            tint = colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
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
        }
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
private fun NowPlayingProgressSlider(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val trackColor = colors.primary
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress) }
    if (!isSeeking) seekProgress = progress
    val displayProgress = if (isSeeking) seekProgress else progress

    Slider(
        value = displayProgress,
        onValueChange = {
            if (!isSeeking) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            seekProgress = it
            isSeeking = true
        },
        onValueChangeFinished = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSeek((seekProgress * durationMs).toLong())
            isSeeking = false
        },
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = trackColor,
            activeTrackColor = trackColor,
            inactiveTrackColor = colors.surfaceHighest
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            formatDuration((displayProgress * durationMs).toLong()),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            formatDuration(durationMs),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    currentPositionMs: Long,
    durationMs: Long,
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
    onRemoveFromQueue: (Song) -> Unit = {},
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val song = state.currentSong
    val colors = LocalDreaminColors.current

    BackHandler(enabled = true) { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close player",
                        tint               = colors.onSurfaceVariant,
                        modifier           = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = state.sleepTimerEndMs != null) {
                val remaining = ((state.sleepTimerEndMs ?: 0L) - System.currentTimeMillis()) / 60_000
                SleepTimerChip(
                    minutesRemaining = remaining.coerceAtLeast(0L),
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

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                    targetState = song.artworkUrl,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                    },
                    label = "artwork_crossfade"
                ) { artworkUrl ->
                    AsyncImage(
                        model = artworkUrl,
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

            Spacer(modifier = Modifier.height(32.dp))

            // Artist + title crossfade on song change
            AnimatedContent(
                targetState = song.id,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "song_meta_crossfade"
            ) { _ ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        song.artist,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            song.displayTitle,
                            fontSize = 16.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (state.currentSongIsFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = if (state.currentSongIsFavorite) Color(0xFFFF6B6B) else colors.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (state.playlists.isNotEmpty()) {
                            IconButton(onClick = { showPlaylistPicker = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.PlaylistAdd,
                                    contentDescription = "Add to playlist",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            NowPlayingProgressSlider(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onSeek = onSeek
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
                    animationSpec = tween(200),
                    label = "shuffle_tint"
                )
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = shuffleTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Playback controls — prev / play / next
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var prevPressed by remember { mutableStateOf(false) }
                    val prevScale by animateFloatAsState(
                        targetValue = if (prevPressed) 0.82f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                        label = "prev_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(prevScale)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 24.dp, color = colors.primary)
                            ) {
                                prevPressed = true
                                onPrevious()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = colors.onSurface, modifier = Modifier.size(32.dp))
                    }
                    LaunchedEffect(prevPressed) { if (prevPressed) { kotlinx.coroutines.delay(120); prevPressed = false } }

                    Spacer(modifier = Modifier.width(20.dp))

                    val isLoading = state.playbackState == PlaybackState.Loading
                    val playScale by animateFloatAsState(
                        targetValue = if (state.playbackState == PlaybackState.Playing) 1f else 0.95f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                        label = "play_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(if (isLoading) 0.95f else playScale)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, color = Color.White.copy(alpha = 0.4f), radius = 32.dp),
                                enabled = !isLoading
                            ) { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.5.dp)
                        } else {
                            Icon(
                                imageVector = if (state.playbackState == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    var nextPressed by remember { mutableStateOf(false) }
                    val nextScale by animateFloatAsState(
                        targetValue = if (nextPressed) 0.82f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                        label = "next_scale"
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(nextScale)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 24.dp, color = colors.primary)
                            ) {
                                nextPressed = true
                                onNext()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = colors.onSurface, modifier = Modifier.size(32.dp))
                    }
                    LaunchedEffect(nextPressed) { if (nextPressed) { kotlinx.coroutines.delay(120); nextPressed = false } }
                }

                val repeatActive = state.repeatMode != TrackRepeatMode.OFF
                val repeatTint by animateColorAsState(
                    targetValue = if (repeatActive) colors.primary else colors.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "repeat_tint"
                )
                IconButton(onClick = onToggleRepeat, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (state.repeatMode == TrackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = repeatTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            val upcomingSlice = remember(state.queue, state.currentSong) {
                val idx = state.queue.indexOfFirst { it.id == state.currentSong?.id }
                val from = if (idx >= 0) idx + 1 else 0
                state.queue.drop(from).take(5)
            }
            UpNextTimeline(
                upcomingSongs = upcomingSlice,
                onSongClick = { song -> onSongSelect(song) }
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

    if (showPlaylistPicker && state.playlists.isNotEmpty()) {
        AddToPlaylistDialog(
            playlists = state.playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = { playlistId ->
                onAddToPlaylist(playlistId)
                showPlaylistPicker = false
            }
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
private fun SleepTimerChip(minutesRemaining: Long, onCancel: () -> Unit) {
    val colors = LocalDreaminColors.current
    Row(
        modifier = Modifier
            .background(colors.primary.copy(alpha = 0.15f), RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onCancel() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        Text(
            "Sleeping in ${minutesRemaining}m  ×",
            color = colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
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
fun UpNextTimeline(
    upcomingSongs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    if (upcomingSongs.isEmpty()) return
    val colors = LocalDreaminColors.current
    val songs = upcomingSongs.take(5)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Up Next",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Text(
                "${songs.size} songs",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
        }

        songs.forEachIndexed { index, song ->
            UpNextRow(
                song = song,
                position = index + 1,
                isNext = index == 0,
                onClick = { onSongClick(song) }
            )
            if (index < songs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 60.dp),
                    thickness = 0.5.dp,
                    color = colors.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(
    song: Song,
    position: Int,
    isNext: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalDreaminColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onClick() }
            .background(
                if (isNext) colors.primary.copy(alpha = 0.07f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isNext) "▶" else "$position",
                fontSize = if (isNext) 10.sp else 12.sp,
                color = if (isNext) colors.primary else colors.onSurfaceVariant,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
            )
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.artworkUrl)
                .crossfade(200)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayTitle,
                color = if (isNext) colors.primary else colors.onSurface,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            Text(
                song.artist,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}





@Composable
fun QueueScreen(
    state: PlayerUiState,
    onSongClick: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
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

    if (showSaveDialog) {
        SaveQueueDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                onSaveQueueAsPlaylist(name)
                showSaveDialog = false
            }
        )
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
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.queue, key = { _, song -> song.id }) { index, song ->
                    val isDragging = draggingIndex == index
                    val offsetY = if (isDragging) dragOffsetY else 0f

                    QueueSongRow(
                        song = song,
                        isPlaying = state.currentSong?.id == song.id,
                        onClick = { if (draggingIndex == null) onSongClick(song) },
                        onRemove = { onRemoveFromQueue(song) },
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
                item { Spacer(modifier = Modifier.height(16.dp)) }
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
                .data(song.artworkUrl)
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
    onShufflePlaylist: (List<Song>) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val tabs = listOf("Playlists", "Favourites")
    val tabPagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalPlaylists provides state.playlists) {

    val openPlaylist = state.openPlaylistId?.let { id -> state.playlists.find { it.id == id } }
    if (openPlaylist != null) {
        PlaylistDetailScreen(
            playlist = openPlaylist,
            songs = state.openPlaylistSongs,
            currentSong = state.currentSong,
            onBack = onClosePlaylist,
            onSongClick = { song -> onSongClickFromPlaylist(song, state.openPlaylistSongs) },
            onPlayAll = { onPlayPlaylist(openPlaylist.id) },
            onShuffle = { onShufflePlaylist(state.openPlaylistSongs) },
            onRemoveSong = { songId -> onRemoveSongFromPlaylist(openPlaylist.id, songId) },
            onRename = { newName -> onRenamePlaylist(openPlaylist.id, newName) }
        )
    } else {

    Column(modifier = Modifier.fillMaxSize()) {
        
        Text(
            "Library",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.primary,
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
                    onOpenPlaylist = onOpenPlaylist
                )
                else -> FavoritesTab(
                    favorites = state.favorites,
                    currentSong = state.currentSong,
                    onSongClick = onSongClick,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }
    }
    } // end else
    } // end CompositionLocalProvider(LocalPlaylists)
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
            items(favorites, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onAddToQueue = {},
                    onAddToPlaylist = { playlistId -> onAddToPlaylist(song, playlistId) }
                )
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
    onOpenPlaylist: (Long) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${playlists.size} playlists", fontSize = 14.sp, color = colors.onSurfaceVariant)
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New playlist", tint = colors.primary)
            }
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.LibraryMusic,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No playlists yet", fontSize = 18.sp, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap + to create your first playlist", fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        artworkUrls = playlistArtworks[playlist.id] ?: emptyList(),
                        onOpen = { onOpenPlaylist(playlist.id) },
                        onPlay = { onPlayPlaylist(playlist.id) },
                        onDelete = { onDeletePlaylist(playlist.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistCoverArt(artworkUrls: List<String>, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val colors = LocalDreaminColors.current
    val urls = artworkUrls.take(4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceHighest)
    ) {
        if (urls.isEmpty()) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .size(size * 0.5f)
                    .align(Alignment.Center)
            )
        } else if (urls.size < 4) {
            AsyncImage(
                model = urls.first(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val cellSize = size / 2
            Column {
                Row {
                    AsyncImage(model = urls[0], contentDescription = null, modifier = Modifier.size(cellSize), contentScale = ContentScale.Crop)
                    AsyncImage(model = urls[1], contentDescription = null, modifier = Modifier.size(cellSize), contentScale = ContentScale.Crop)
                }
                Row {
                    AsyncImage(model = urls[2], contentDescription = null, modifier = Modifier.size(cellSize), contentScale = ContentScale.Crop)
                    AsyncImage(model = urls[3], contentDescription = null, modifier = Modifier.size(cellSize), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: com.shyan.dreamin.data.local.Playlist,
    artworkUrls: List<String> = emptyList(),
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalDreaminColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceHigh, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onOpen() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCoverArt(artworkUrls = artworkUrls, size = 48.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            playlist.name,
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 15.sp
        )
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = colors.primary, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Delete playlist", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val colors = LocalDreaminColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                playlists.forEach { playlist ->
                    TextButton(
                        onClick = { onSelect(playlist.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(playlist.name, color = colors.primary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
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
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val colors = LocalDreaminColors.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = colors.primary)
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

@Composable
private fun PlaylistDetailScreen(
    playlist: com.shyan.dreamin.data.local.Playlist,
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveSong: (String) -> Unit,
    onRename: (String) -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalDreaminColors.current
    var showRenameDialog by remember { mutableStateOf(false) }
    val songCount = remember(songs.size) { songs.size }
    val playingId = remember(currentSong?.id) { currentSong?.id }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = colors.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.surfaceHighest,
                    contentColor = colors.onSurface,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colors.onSurface, modifier = Modifier.size(24.dp))
            }
            Text(
                playlist.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }

        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No songs yet", fontSize = 18.sp, color = colors.onSurface, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Long-press any song to add it here", fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$songCount songs", fontSize = 14.sp, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onShuffle,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(songs.size, key = { songs[it].id }) { idx ->
                    PlaylistSongRow(
                        song = songs[idx],
                        isPlaying = playingId == songs[idx].id,
                        onClick = { onSongClick(songs[idx]) },
                        onRemove = {
                            val title = songs[idx].displayTitle
                            onRemoveSong(songs[idx].id)
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
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
    } // end Scaffold

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
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val bgColor = if (isPlaying) colors.surfaceHigh else Color.Transparent
    val textColor = if (isPlaying) colors.primary else colors.onSurface
    val textWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = colors.primary)
            ) { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkBox(song.artworkUrl, isPlaying, colors)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.displayTitle,
                color = textColor,
                fontWeight = textWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            Text(song.artist, color = colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Remove", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
                        model = song.artworkUrl,
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

    Column {
        // Title row — fixed layout, no scroll-driven recomposition
        // graphicsLayer alpha change is in the draw phase, not composition
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DREAMIN",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showInfoSheet = true }) {
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
        // Greeting fades as user scrolls — graphicsLayer defers alpha to draw phase
        Text(
            "$timeOfDay, $userName",
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .graphicsLayer { alpha = (1f - titleProgress() * 2.5f).coerceIn(0f, 1f) }
                .combinedClickable(onClick = {}, onLongClick = onLongPressName)
        )
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun HorizontalSongCardsRow(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            HorizontalSongCard(
                song = song,
                isPlaying = currentSongId == song.id,
                onClick = { onSongClick(song) }
            )
        }
    }
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
