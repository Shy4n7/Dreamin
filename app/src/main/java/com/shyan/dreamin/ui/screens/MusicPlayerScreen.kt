package com.shyan.dreamin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shyan.dreamin.data.model.PlaybackState
import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.viewmodel.MusicPlayerViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

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
                            onScreenChange(screen)
                        }
                    )
                }
            }
        ) { padding ->
            val isHome = currentScreen == Screen.Home
            val isLibrary = currentScreen == Screen.Library

            var isLibraryPreWarmed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                // Pre-warm the library composition tree right after first frame
                isLibraryPreWarmed = true
            }

            val homeAlpha by animateFloatAsState(
                targetValue = if (isHome) 1f else 0f,
                animationSpec = tween(durationMillis = 140),
                label = "home_tab_alpha"
            )
            val libraryAlpha by animateFloatAsState(
                targetValue = if (isLibrary) 1f else 0f,
                animationSpec = tween(durationMillis = 140),
                label = "library_tab_alpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState)
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                // 1. Home Screen (Rendered & Interactive when active)
                if (homeAlpha > 0f || isHome) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = homeAlpha }
                            .zIndex(if (isHome) 1f else 0f)
                    ) {
                        HomeScreen(
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
                            onOpenPlaylist           = vm::openPlaylist,
                            onEditName               = vm::saveUserName,
                            onClearRecentSearches    = vm::clearRecentSearches,
                            onResumeLastSession      = onResumeSession,
                            searchError              = state.searchError
                        )
                    }
                }

                // 2. Library Screen (Pre-warmed on launch, stays warm in memory)
                if (isLibrary || isLibraryPreWarmed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = libraryAlpha }
                            .zIndex(if (isLibrary) 1f else 0f)
                    ) {
                        LibraryScreen(
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
                            onResetSpotifyImportState = vm::resetSpotifyImportState,
                            onSearchOnline           = { query ->
                                onScreenChange(Screen.Home)
                                vm.activateSearch()
                                vm.setSearchQuery(query)
                            },
                            onAddSuggestedTrack      = vm::addSuggestedTrackToPlaylist,
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

        AnimatedVisibility(
            visible = state.selectedArtistProfile != null,
            enter = scaleIn(
                initialScale = 0.93f,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { 70 } + fadeIn(tween(200)),
            exit = scaleOut(
                targetScale = 0.93f,
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + slideOutVertically(
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { 70 } + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            val profile = state.selectedArtistProfile
            if (profile != null) {
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

        // Playlist detail overlay — shown full-screen with floating MiniPlayer at bottom when playing
        val openPlaylist = state.openPlaylistId?.let { id -> state.playlists.find { it.id == id } }
        AnimatedVisibility(
            visible = openPlaylist != null,
            enter = scaleIn(
                initialScale = 0.95f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMedium
                )
            ) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMedium
                )
            ) { 45 } + fadeIn(tween(140)),
            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { 50 },
            modifier = Modifier.fillMaxSize()
        ) {
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
