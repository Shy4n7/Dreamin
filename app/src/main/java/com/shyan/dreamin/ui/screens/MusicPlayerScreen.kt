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

    LaunchedEffect(currentScreen) {
        val targetIdx = navScreens.indexOf(currentScreen)
        if (targetIdx >= 0 && pagerState.currentPage != targetIdx) {
            pagerState.animateScrollToPage(
                page = targetIdx,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
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

    BackHandler(enabled = !isNowPlayingOpen && state.openPlaylistId == null && (state.isSearchActive || currentScreen != navScreens.first())) {
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
                            onCreatePlaylist         = { name, coverUri -> vm.createPlaylist(name, coverUri) },
                            onDeletePlaylist         = vm::deletePlaylist,
                            onPlayPlaylist           = onLibraryPlayPlaylist,
                            onAddToPlaylist          = { song, playlistId -> vm.addSongToPlaylist(playlistId, song) },
                            onOpenPlaylist           = vm::openPlaylist,
                            onClosePlaylist          = vm::closePlaylist,
                            onRemoveSongFromPlaylist = vm::removeSongFromPlaylist,
                            onRenamePlaylist         = vm::renamePlaylist,
                            onUpdatePlaylistCover    = vm::updatePlaylistCover,
                            onSongClickFromPlaylist  = { song, songs -> vm.playSongFromPlaylist(song, songs); onOpenNowPlaying() },
                            onShufflePlaylist        = { songs -> vm.shuffleAndPlayPlaylist(songs); onOpenNowPlaying() },
                            onDownloadPlaylist       = vm::downloadAllSongsInPlaylist,
                            onDownloadSong           = vm::downloadSong,
                            onDeleteDownload         = vm::deleteDownload,
                            onImportSpotifyPlaylist  = vm::importSpotifyPlaylist,
                            onImportTextList         = vm::importSongsFromTextList,
                            onResetSpotifyImportState = vm::resetSpotifyImportState,
                            onSearchOnline           = vm::searchSongsDirect,
                            onPlayNext               = vm::playNext,
                            onAddToQueue             = vm::addToQueue
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

        // Playlist detail overlay — shown full-screen with floating MiniPlayer at bottom when playing
        val openPlaylist = state.openPlaylistId?.let { id -> state.playlists.find { it.id == id } }
        if (openPlaylist != null) {
            PlaylistDetailScreen(
                playlist = openPlaylist,
                songs = state.openPlaylistSongs,
                currentSong = state.currentSong,
                playbackState = state.playbackState,
                progressFlow = vm.progressFlow,
                onPlayPause = vm::togglePlayPause,
                onNext = vm::playNext,
                onPrevious = vm::playPrevious,
                onExpandNowPlaying = onOpenNowPlaying,
                downloadedSongIds = remember(state.downloadedSongs) { state.downloadedSongs.map { it.id }.toSet() },
                downloadingSongIds = state.downloadingSongIds,
                onBack = vm::closePlaylist,
                onSongClick = { song -> vm.playSongFromPlaylist(song, state.openPlaylistSongs); onOpenNowPlaying() },
                onPlayAll = { vm.playSongsFromPlaylist(openPlaylist.id); onOpenNowPlaying() },
                onShuffle = { vm.shuffleAndPlayPlaylist(state.openPlaylistSongs); onOpenNowPlaying() },
                onDownloadAll = { vm.downloadAllSongsInPlaylist(state.openPlaylistSongs) },
                onDownloadSong = vm::downloadSong,
                onDeleteDownload = vm::deleteDownload,
                onRemoveSong = { songId -> vm.removeSongFromPlaylist(openPlaylist.id, songId) },
                onRename = { newName -> vm.renamePlaylist(openPlaylist.id, newName) },
                onUpdateCover = { uri -> vm.updatePlaylistCover(openPlaylist.id, uri) },
                onAddSong = { song -> vm.addSongToPlaylist(openPlaylist.id, song) },
                onSearchOnline = vm::searchSongsDirect,
                onPlayNext = vm::playNext,
                onAddToQueue = vm::addToQueue,
                quickPickSongs = remember(state.favorites, state.trendingCharts, state.recentlyPlayed) {
                    (state.favorites + state.trendingCharts + state.recentlyPlayed).distinctBy { it.id }
                }
            )
        }

        // NowPlaying full-screen sheet overlay (Topmost in stack)
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
                onCreatePlaylist   = { name, coverUri ->
                    vm.createPlaylist(name, coverUri)
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
    }
}
