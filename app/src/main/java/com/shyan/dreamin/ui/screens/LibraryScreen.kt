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
fun LibraryScreen(
    state: PlayerUiState,
    onSongClick: (Song) -> Unit,
    onCreatePlaylist: (String, android.net.Uri?) -> Unit = { _, _ -> },
    onDeletePlaylist: (Long) -> Unit = {},
    onPlayPlaylist: (Long) -> Unit = {},
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
    onRemoveSongFromPlaylist: (Long, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (Long, String) -> Unit = { _, _ -> },
    onUpdatePlaylistCover: (Long, android.net.Uri?) -> Unit = { _, _ -> },
    onSongClickFromPlaylist: (Song, List<Song>) -> Unit = { song, _ -> onSongClick(song) },
    onShufflePlaylist: (List<Song>) -> Unit = {},
    onDownloadPlaylist: (List<Song>) -> Unit = {},
    onDownloadSong: (Song) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    onImportSpotifyPlaylist: (String) -> Unit = {},
    onResetSpotifyImportState: () -> Unit = {},
    onSearchOnline: (String) -> Unit = {},
    onAddSuggestedTrack: (Long, Song, String) -> Unit = { _, _, _ -> },
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {}
) {
    val colors = LocalDreaminColors.current
    val tabs = listOf("Playlists", "Favourites", "Downloads")
    val tabPagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    val isPlaylistOpen = state.openPlaylistId != null
    var wasPlaylistOpen by remember { mutableStateOf(false) }
    var returnSpringKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(isPlaylistOpen) {
        if (wasPlaylistOpen && !isPlaylistOpen) {
            returnSpringKey++
        }
        wasPlaylistOpen = isPlaylistOpen
    }

    CompositionLocalProvider(LocalPlaylists provides state.playlists) {

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
            val pageOffset = ((tabPagerState.currentPage - page) + tabPagerState.currentPageOffsetFraction)
            val absOffset = kotlin.math.abs(pageOffset)
            val pageScale = 1f - (absOffset * 0.05f).coerceIn(0f, 0.05f)
            val pageAlpha = 1f - (absOffset * 0.40f).coerceIn(0f, 0.40f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pageScale
                        scaleY = pageScale
                        alpha = pageAlpha
                    }
            ) {
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
                        onResetSpotifyImport = onResetSpotifyImportState,
                        onSearchOnline = onSearchOnline,
                        onAddSuggestedTrack = onAddSuggestedTrack,
                        triggerKey = returnSpringKey
                    )
                    1 -> FavoritesTab(
                        favorites = state.favorites,
                        currentSong = state.currentSong,
                        onSongClick = onSongClick,
                        onAddToPlaylist = onAddToPlaylist,
                        triggerKey = returnSpringKey
                    )
                    else -> DownloadsTab(
                        downloadedSongs = state.downloadedSongs,
                        currentSong = state.currentSong,
                        onSongClick = onSongClick,
                        onDeleteDownload = onDeleteDownload,
                        onAddToPlaylist = onAddToPlaylist,
                        triggerKey = returnSpringKey
                    )
                }
            }
        }
    }
    } // end CompositionLocalProvider(LocalPlaylists)
}

@Composable
fun DownloadsTab(
    downloadedSongs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    triggerKey: Any? = Unit
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
                Box(modifier = Modifier.staggeredEntry(index, triggerKey = triggerKey)) {
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
fun FavoritesTab(
    favorites: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    triggerKey: Any? = Unit
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
                Box(modifier = Modifier.staggeredEntry(index, triggerKey = triggerKey)) {
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
fun PlaylistsTab(
    playlists: List<com.shyan.dreamin.data.local.Playlist>,
    playlistArtworks: Map<Long, List<String>> = emptyMap(),
    onCreatePlaylist: (String, android.net.Uri?) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    spotifyImportState: SpotifyImportState = SpotifyImportState.Idle,
    onImportSpotify: (String) -> Unit = {},
    onResetSpotifyImport: () -> Unit = {},
    onSearchOnline: (String) -> Unit = {},
    onAddSuggestedTrack: (Long, Song, String) -> Unit = { _, _, _ -> },
    triggerKey: Any? = Unit
) {
    val colors = LocalDreaminColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                Box(modifier = Modifier.staggeredEntry(0, baseDelayMs = 28, triggerKey = triggerKey)) {
                    CreatePlaylistCard(onClick = { showCreateDialog = true })
                }
            }

            itemsIndexed(
                items = playlists,
                key = { _, playlist -> playlist.id },
                contentType = { _, _ -> "PlaylistCard" }
            ) { index, playlist ->
                Box(modifier = Modifier.staggeredEntry(index + 1, baseDelayMs = 28, triggerKey = triggerKey)) {
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
            onCreate = { name, coverUri ->
                onCreatePlaylist(name, coverUri)
                showCreateDialog = false
            },
            spotifyImportState = spotifyImportState,
            onImportSpotify = onImportSpotify,
            onResetSpotifyImport = onResetSpotifyImport,
            onSearchOnline = onSearchOnline,
            onAddSuggestedTrack = onAddSuggestedTrack
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
fun CreatePlaylistCard(
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
fun PlaylistGridCard(
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
                    text = when {
                        playlist.songCount == 1 -> "1 track"
                        playlist.songCount > 1 -> "${playlist.songCount} tracks"
                        artworkUrls.isNotEmpty() -> "${artworkUrls.size} tracks"
                        else -> "0 tracks"
                    },
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
fun StatsTab(stats: com.shyan.dreamin.data.model.ListeningStats) {
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
fun StatsCard(modifier: Modifier = Modifier, label: String, value: String) {
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
