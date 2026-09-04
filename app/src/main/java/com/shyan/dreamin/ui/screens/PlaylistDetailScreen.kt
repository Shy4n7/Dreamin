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
import androidx.compose.ui.layout.onSizeChanged
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

enum class PlaylistSortOrder(val label: String) {
    Custom("Custom"),
    Title("Title"),
    Artist("Artist"),
    Duration("Duration")
}

@Composable
fun PlaylistDetailScreen(
    playlist: com.shyan.dreamin.data.local.Playlist,
    songs: List<Song>,
    currentSong: Song?,
    playbackState: PlaybackState = PlaybackState.Idle,
    progressFlow: kotlinx.coroutines.flow.StateFlow<PlaybackProgress> = kotlinx.coroutines.flow.MutableStateFlow(PlaybackProgress()),
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onExpandNowPlaying: () -> Unit = {},
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
    onRemoveSongs: (Set<String>) -> Unit = {},
    onReorderSong: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRename: (String) -> Unit,
    onUpdateCover: (android.net.Uri?) -> Unit = {},
    onAddSong: (Song) -> Unit = {},
    onSearchOnline: suspend (String) -> List<Song> = { emptyList() },
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onUpdateSongArtwork: ((Song, String) -> Unit)? = null,
    onSyncSpotify: () -> Unit = {},
    isSyncingSpotify: Boolean = false,
    quickPickSongs: List<Song> = emptyList()
) {
    val colors = LocalDreaminColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var selectedSongForPosterPicker by remember { mutableStateOf<Song?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val songCount = remember(songs.size) { songs.size }
    val playingId = remember(currentSong?.id) { currentSong?.id }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val durationLabel = remember(songs) { formatTotalPlaylistDuration(songs) }

    var sortOrder by remember { mutableStateOf(PlaylistSortOrder.Custom) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedSongIds = remember { mutableStateListOf<String>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = remember { mutableFloatStateOf(70f) }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedSongIds.clear()
    }
    BackHandler(enabled = !isMultiSelectMode) {
        onBack()
    }

    // Guard against flash of empty-state while DB flow delivers first emission
    var isInitialLoad by remember(playlist.id) { mutableStateOf(true) }
    LaunchedEffect(playlist.id, songs.size) {
        if (songs.isNotEmpty()) {
            isInitialLoad = false
        } else {
            // Safety timeout: if songs stay empty after 700ms, it really is an empty playlist
            kotlinx.coroutines.delay(700)
            isInitialLoad = false
        }
    }

    val detailCoverPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) onUpdateCover(uri)
    }

    val allDownloaded = remember(songs, downloadedSongIds) {
        songs.isNotEmpty() && songs.all { downloadedSongIds.contains(it.id) }
    }

    // 🎨 1. Dynamic Dominant Background Bloom extracted from Top Track / Cover
    var extractedDominant by remember(playlist.id) { mutableStateOf(colors.primary) }
    val heroArt = remember(playlist.coverUrl, songs) {
        playlist.coverUrl?.takeIf { it.isNotBlank() } ?: songs.firstOrNull()?.displayArtworkUrl
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

    // 🔍 2. In-Playlist Sorting & Instant Search
    val listState = rememberLazyListState()
    val sortedSongs = remember(songs, sortOrder, sortAscending) {
        when (sortOrder) {
            PlaylistSortOrder.Custom -> songs
            PlaylistSortOrder.Title -> if (sortAscending) songs.sortedBy { it.displayTitle.lowercase() } else songs.sortedByDescending { it.displayTitle.lowercase() }
            PlaylistSortOrder.Artist -> if (sortAscending) songs.sortedBy { it.artist.lowercase() } else songs.sortedByDescending { it.artist.lowercase() }
            PlaylistSortOrder.Duration -> if (sortAscending) songs.sortedBy { it.duration } else songs.sortedByDescending { it.duration }
        }
    }
    val filteredSongs = remember(sortedSongs, searchQuery) {
        if (searchQuery.isBlank()) sortedSongs
        else {
            val q = searchQuery.trim().lowercase()
            sortedSongs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q)
            }
        }
    }

    val isScrolledPastHero by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 310
        }
    }

    val isPlayingThisPlaylist = remember(currentSong?.id, songs, playbackState) {
        currentSong != null && songs.any { it.id == currentSong.id } && playbackState == PlaybackState.Playing
    }

    // Compute readable text colour on top of the dynamic dominant button background
    val playAllContentColor = remember(animatedDominant) {
        val luminance = 0.2126f * animatedDominant.red + 0.7152f * animatedDominant.green + 0.0722f * animatedDominant.blue
        if (luminance > 0.45f) Color.Black else Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 🌌 Ambient Fluid Color Bloom Header at Top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    animatedDominant.copy(alpha = 0.42f),
                                    animatedDominant.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = Offset(x = 540f, y = 180f),
                                radius = 700f
                            )
                        )
                )
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                // Top App Bar with MultiSelect, Sticky Mini Header & Search
                if (isMultiSelectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedSongIds.clear()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close selection", tint = colors.onSurface)
                        }
                        Text(
                            text = "${selectedSongIds.size} selected",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        TextButton(
                            onClick = {
                                if (selectedSongIds.size == filteredSongs.size) {
                                    selectedSongIds.clear()
                                } else {
                                    selectedSongIds.clear()
                                    selectedSongIds.addAll(filteredSongs.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                if (selectedSongIds.size == filteredSongs.size) "Deselect" else "Select All",
                                color = animatedDominant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
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

                                if (isScrolledPastHero) {
                                    // 🚀 Fluid Collapsing Mini Header
                                    Row(
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AsyncImage(
                                            model = heroArt,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                playlist.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "$songCount ${if (songCount == 1) "track" else "tracks"}",
                                                fontSize = 11.5.sp,
                                                color = colors.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Pinned Play/Pause & Shuffle Mini Buttons
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (isPlayingThisPlaylist) onPlayPause()
                                                else onPlayAll()
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(animatedDominant)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThisPlaylist) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = if (isPlayingThisPlaylist) "Pause" else "Play all",
                                                tint = playAllContentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = onShuffle,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(colors.surfaceHigh)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Shuffle,
                                                contentDescription = "Shuffle",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                } else {
                                    val titleAlpha by remember {
                                        derivedStateOf {
                                            if (listState.firstVisibleItemIndex > 0) 1.0f
                                            else (listState.firstVisibleItemScrollOffset / 280f).coerceIn(0f, 1f)
                                        }
                                    }
                                    Text(
                                        playlist.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                            .graphicsLayer {
                                                alpha = titleAlpha
                                                translationY = (1f - titleAlpha) * 16f
                                            }
                                    )
                                    if (playlist.spotifyPlaylistId != null) {
                                        IconButton(
                                            onClick = onSyncSpotify,
                                            enabled = !isSyncingSpotify,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            if (isSyncingSpotify) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = colors.primary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Filled.Sync,
                                                    contentDescription = "Sync with Spotify",
                                                    tint = colors.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = { showAddSongsSheet = true }, modifier = Modifier.size(44.dp)) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Add songs to playlist",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
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
                                            contentDescription = "Edit Playlist",
                                            tint = colors.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = if (filteredSongs.size >= 8) 20.dp else 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                    // Hero Header Item (Always shown)
                    if (!isSearchActive || searchQuery.isBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    contentAlignment = Alignment.BottomEnd,
                                    modifier = Modifier.graphicsLayer {
                                        val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 400f
                                        val fraction = (scrollOffset / 450f).coerceIn(0f, 1f)
                                        scaleX = 1f - (fraction * 0.14f)
                                        scaleY = 1f - (fraction * 0.14f)
                                        translationY = scrollOffset * 0.22f
                                        alpha = (1f - (fraction * 0.45f)).coerceIn(0.55f, 1f)
                                        shadowElevation = (20f * (1f - fraction)).coerceAtLeast(0f)
                                    }
                                ) {
                                    PlaylistCoverArt(
                                        artworkUrls = songs.map { it.displayArtworkUrl },
                                        coverUrl = playlist.coverUrl,
                                        size = 164.dp,
                                        shape = RoundedCornerShape(22.dp)
                                    )
                                }

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
                                if (songs.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = onPlayAll,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = animatedDominant,
                                                contentColor = playAllContentColor
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
                                            onClick = { showAddSongsSheet = true },
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(colors.surfaceHigh.copy(alpha = 0.85f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Add songs",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
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
                                } else {
                                    // Empty State Actions: Add Songs + Choose Cover
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { showAddSongsSheet = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = animatedDominant,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1.2f).height(48.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Add Songs", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                detailCoverPickerLauncher.launch(
                                                    androidx.activity.result.PickVisualMediaRequest(
                                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, colors.onSurfaceVariant.copy(alpha = 0.35f)),
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Edit,
                                                contentDescription = null,
                                                tint = colors.onSurface,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Edit Cover", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Sort & Filter Bar
                    if (!isInitialLoad && songs.isNotEmpty()) {
                        item(key = "playlist_sort_filter_bar", contentType = "SortFilterBar") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Sort Dropdown Chip
                                Box {
                                    Surface(
                                        onClick = { showSortDropdown = true },
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.surfaceHigh.copy(alpha = 0.65f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (sortOrder != PlaylistSortOrder.Custom) animatedDominant.copy(alpha = 0.6f)
                                            else colors.outlineVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = "Sort",
                                                tint = if (sortOrder != PlaylistSortOrder.Custom) animatedDominant else colors.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "${sortOrder.label} ${if (sortOrder != PlaylistSortOrder.Custom) (if (sortAscending) "↑" else "↓") else ""}".trim(),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (sortOrder != PlaylistSortOrder.Custom) colors.onSurface else colors.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showSortDropdown,
                                        onDismissRequest = { showSortDropdown = false },
                                        modifier = Modifier
                                            .background(colors.surfaceHighest, RoundedCornerShape(16.dp))
                                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                                            .width(180.dp)
                                    ) {
                                        PlaylistSortOrder.values().forEach { order ->
                                            val isSelected = sortOrder == order
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            order.label,
                                                            color = if (isSelected) animatedDominant else colors.onSurface,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 13.5.sp
                                                        )
                                                        if (isSelected && order != PlaylistSortOrder.Custom) {
                                                            Text(if (sortAscending) "Asc" else "Desc", fontSize = 11.sp, color = animatedDominant)
                                                        }
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = when (order) {
                                                            PlaylistSortOrder.Custom -> Icons.Outlined.Reorder
                                                            PlaylistSortOrder.Title -> Icons.Outlined.SortByAlpha
                                                            PlaylistSortOrder.Artist -> Icons.Outlined.Person
                                                            PlaylistSortOrder.Duration -> Icons.Outlined.Schedule
                                                        },
                                                        contentDescription = null,
                                                        tint = if (isSelected) animatedDominant else colors.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    if (sortOrder == order && order != PlaylistSortOrder.Custom) {
                                                        sortAscending = !sortAscending
                                                    } else {
                                                        sortOrder = order
                                                        sortAscending = true
                                                    }
                                                    showSortDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Mode indicators: Drag order active / Multi-select toggle
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {

                                    // Quick Select / Multi-Select Button
                                    IconButton(
                                        onClick = {
                                            isMultiSelectMode = !isMultiSelectMode
                                            selectedSongIds.clear()
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = "Select multiple",
                                            tint = if (isMultiSelectMode) animatedDominant else colors.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isInitialLoad) {
                        // Skeleton shimmer while songs are loading from DB
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                repeat(5) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(colors.surfaceHighest.copy(alpha = 0.45f))
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surfaceHigh)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.62f)
                                                    .height(14.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(colors.surfaceHigh)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.40f)
                                                    .height(11.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.surfaceHigh.copy(alpha = 0.6f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (songs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.surfaceHighest.copy(alpha = 0.55f))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Quick Add Recommendations",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.onSurface
                                    )
                                    TextButton(onClick = { showAddSongsSheet = true }) {
                                        Text("Search all", color = animatedDominant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }

                                val suggested = quickPickSongs.take(6)
                                if (suggested.isNotEmpty()) {
                                    suggested.forEach { s ->
                                        val isAdded = songs.any { it.id == s.id }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surfaceHigh.copy(alpha = 0.4f))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = s.displayArtworkUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    s.displayTitle,
                                                    color = colors.onSurface,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    s.artist,
                                                    color = colors.onSurfaceVariant,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (!isAdded) {
                                                        onAddSong(s)
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "Added \"${s.displayTitle}\"",
                                                                duration = SnackbarDuration.Short
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                                                    contentDescription = "Add",
                                                    tint = if (isAdded) colors.secondary else animatedDominant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        "No songs in playlist yet. Tap below to search and add tracks from the catalog.",
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { showAddSongsSheet = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = animatedDominant),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Search Songs to Add", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (filteredSongs.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(32.dp)
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
                        itemsIndexed(
                            items = filteredSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "PlaylistSongRow" }
                        ) { idx, song ->
                            val isDownloaded = downloadedSongIds.contains(song.id)
                            val isDownloading = downloadingSongIds.contains(song.id)
                            val isSelected = selectedSongIds.contains(song.id)
                            val isDragging = draggingIndex == idx
                            val isReorderable = sortOrder == PlaylistSortOrder.Custom && searchQuery.isBlank() && !isMultiSelectMode

                            PlaylistSongRow(
                                song = song,
                                index = idx + 1,
                                isPlaying = playingId == song.id,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                isReorderable = isReorderable,
                                isDragging = isDragging,
                                dragOffsetY = if (isDragging) dragOffsetY else 0f,
                                onToggleSelect = {
                                    if (isSelected) selectedSongIds.remove(song.id)
                                    else selectedSongIds.add(song.id)
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = true
                                        selectedSongIds.add(song.id)
                                    }
                                },
                                onClick = {
                                    if (isMultiSelectMode) {
                                        if (isSelected) selectedSongIds.remove(song.id)
                                        else selectedSongIds.add(song.id)
                                    } else {
                                        onSongClick(song)
                                    }
                                },
                                onDragStart = {
                                    draggingIndex = idx
                                    dragOffsetY = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { delta ->
                                    dragOffsetY += delta
                                    val targetIdx = (idx + (dragOffsetY / itemHeightPx.floatValue).toInt())
                                        .coerceIn(0, filteredSongs.lastIndex)
                                    if (targetIdx != idx) {
                                        onReorderSong(idx, targetIdx)
                                        dragOffsetY -= (targetIdx - idx) * itemHeightPx.floatValue
                                        draggingIndex = targetIdx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    dragOffsetY = 0f
                                },
                                onHeightMeasured = { h -> itemHeightPx.floatValue = h },
                                onSwipeAddToQueue = {
                                    onAddToQueue(song)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Added \"${song.displayTitle}\" to queue", duration = SnackbarDuration.Short)
                                    }
                                },
                                onSwipeRemove = {
                                    val removedSong = song
                                    val title = removedSong.displayTitle
                                    onRemoveSong(removedSong.id)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "\"$title\" removed from playlist",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onAddSong(removedSong)
                                        }
                                    }
                                },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onChangePoster = { selectedSongForPosterPicker = song },
                                onDownload = {
                                    if (isDownloaded) onDeleteDownload(song.id)
                                    else onDownloadSong(song)
                                },
                                onRemove = {
                                    val removedSong = song
                                    val title = removedSong.displayTitle
                                    onRemoveSong(removedSong.id)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "\"$title\" removed from playlist",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onAddSong(removedSong)
                                        }
                                    }
                                }
                            )
                        }

                        // Sparse Playlist Smart Recommendations (when playlist has 1-4 songs)
                        if (songs.size in 1..4 && searchQuery.isBlank()) {
                            item(key = "sparse_recommendations", contentType = "SparseRecommendations") {
                                val unaddedSuggestions = remember(quickPickSongs, songs) {
                                    val existingIds = songs.map { it.id }.toSet()
                                    quickPickSongs.filterNot { existingIds.contains(it.id) }.take(4)
                                }
                                if (unaddedSuggestions.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp, bottom = 6.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(colors.surfaceHighest.copy(alpha = 0.45f))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Recommended for this playlist",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = colors.onSurface
                                            )
                                            TextButton(
                                                onClick = { showAddSongsSheet = true },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("More", color = animatedDominant, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        unaddedSuggestions.forEach { s ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.surfaceHigh.copy(alpha = 0.35f))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                AsyncImage(
                                                    model = s.displayArtworkUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(s.displayTitle, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(s.artist, color = colors.onSurfaceVariant, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onAddSong(s)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Added \"${s.displayTitle}\"", duration = SnackbarDuration.Short)
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = animatedDominant, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = { showAddSongsSheet = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, animatedDominant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add More Songs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        item { Spacer(modifier = Modifier.height(if (currentSong != null) 90.dp else 24.dp)) }
                    }
                }
                if (filteredSongs.size >= 8) {
                    PlaylistFastScroller(
                        listState = listState,
                        itemCount = filteredSongs.size,
                        songs = filteredSongs,
                        dominantColor = animatedDominant,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(top = 16.dp, bottom = if (currentSong != null) 96.dp else 24.dp, end = 2.dp)
                    )
                }
            }
        }
    }

        // Multi-select floating bottom action bar
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = slideInVertically { it } + fadeIn(tween(180)),
            exit = slideOutVertically { it } + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (currentSong != null) 78.dp else 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surfaceHighest.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, animatedDominant.copy(alpha = 0.5f)),
                shadowElevation = 18.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${selectedSongIds.size} selected",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = if (selectedSongIds.size == filteredSongs.size) "All tracks" else "of ${filteredSongs.size} tracks",
                            fontSize = 11.5.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Queue Selected
                        IconButton(
                            onClick = {
                                val toQueue = filteredSongs.filter { selectedSongIds.contains(it.id) }
                                toQueue.forEach { onAddToQueue(it) }
                                val count = toQueue.size
                                isMultiSelectMode = false
                                selectedSongIds.clear()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Added $count tracks to queue", duration = SnackbarDuration.Short)
                                }
                            },
                            enabled = selectedSongIds.isNotEmpty(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceHigh)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.PlaylistAdd,
                                contentDescription = "Queue selected",
                                tint = if (selectedSongIds.isNotEmpty()) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Download Selected
                        IconButton(
                            onClick = {
                                val toDownload = filteredSongs.filter { selectedSongIds.contains(it.id) }
                                toDownload.forEach { onDownloadSong(it) }
                                val count = toDownload.size
                                isMultiSelectMode = false
                                selectedSongIds.clear()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Downloading $count tracks...", duration = SnackbarDuration.Short)
                                }
                            },
                            enabled = selectedSongIds.isNotEmpty(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceHigh)
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = "Download selected",
                                tint = if (selectedSongIds.isNotEmpty()) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Delete Selected
                        IconButton(
                            onClick = {
                                val idsToDelete = selectedSongIds.toSet()
                                val count = idsToDelete.size
                                onRemoveSongs(idsToDelete)
                                isMultiSelectMode = false
                                selectedSongIds.clear()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Removed $count tracks from playlist", duration = SnackbarDuration.Short)
                                }
                            },
                            enabled = selectedSongIds.isNotEmpty(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.error.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove selected",
                                tint = if (selectedSongIds.isNotEmpty()) colors.error else colors.error.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // MiniPlayer floating inside playlist screen at the bottom
        AnimatedVisibility(
            visible = currentSong != null,
            enter = slideInVertically(DreaminMotion.FluidSlide) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(DreaminMotion.FluidSlide) { it } + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 6.dp, start = 8.dp, end = 8.dp)
        ) {
            MiniPlayer(
                song = currentSong,
                playbackState = playbackState,
                progressFlow = progressFlow,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onExpand = onExpandNowPlaying,
                dominantColor = animatedDominant
            )
        }

        if (selectedSongForPosterPicker != null) {
            com.shyan.dreamin.ui.components.PosterPickerBottomSheet(
                song = selectedSongForPosterPicker!!,
                onDismiss = { selectedSongForPosterPicker = null },
                onPosterSelected = { newPoster ->
                    onUpdateSongArtwork?.invoke(selectedSongForPosterPicker!!, newPoster)
                }
            )
        }

        // Snackbar anchored above mini player or bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (currentSong != null) 80.dp else 16.dp)
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

    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Edit Playlist", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Artwork Preview & Change Action
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        PlaylistCoverArt(
                            artworkUrls = songs.map { it.displayArtworkUrl },
                            coverUrl = playlist.coverUrl,
                            size = 110.dp,
                            shape = RoundedCornerShape(18.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                detailCoverPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = animatedDominant),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Photo", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        if (playlist.coverUrl != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onUpdateCover(null) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Remove", fontSize = 13.sp, color = colors.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Playlist Name", color = colors.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = animatedDominant,
                            focusedLabelColor = animatedDominant,
                            cursorColor = animatedDominant
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRename(newTitle.trim())
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = animatedDominant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            },
            containerColor = colors.surfaceHighest,
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showAddSongsSheet) {
        AddSongsToPlaylistDialog(
            playlistName = playlist.name,
            existingSongIds = remember(songs) { songs.map { it.id }.toSet() },
            quickPickSongs = quickPickSongs,
            accentColor = animatedDominant,
            onSearchOnline = onSearchOnline,
            onAddSong = onAddSong,
            onDismiss = { showAddSongsSheet = false }
        )
    }
}

@Composable
fun AddSongsToPlaylistDialog(
    playlistName: String,
    existingSongIds: Set<String>,
    quickPickSongs: List<Song>,
    accentColor: Color,
    onSearchOnline: suspend (String) -> List<Song>,
    onAddSong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalDreaminColors.current
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val addedSongIds = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            isSearching = true
            kotlinx.coroutines.delay(250)
            try {
                val res = onSearchOnline(query.trim())
                searchResults = res
            } catch (_: Exception) {
                searchResults = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    val displayList = remember(query, searchResults, quickPickSongs) {
        if (query.trim().length >= 2) searchResults
        else quickPickSongs
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(colors.surfaceHighest)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume click
                    )
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Add Songs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            "To \"$playlistName\"",
                            fontSize = 12.5.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search songs, artists, albums...", color = colors.onSurfaceVariant, fontSize = 13.5.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.onSurfaceVariant)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = colors.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = colors.surfaceHigh,
                        unfocusedContainerColor = colors.surfaceHigh,
                        cursorColor = accentColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(32.dp))
                    }
                }

                if (query.isBlank()) {
                    Text(
                        "Suggestions from Favorites & Trending",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (displayList.isEmpty() && !isSearching) {
                        item(key = "empty_state", contentType = "EmptyState") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (query.isNotBlank()) "No songs found for \"$query\"" else "No suggestions available",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    items(
                        items = displayList,
                        key = { it.id },
                        contentType = { "AddSongRow" }
                    ) { song ->
                        val isAlreadyInPlaylist = existingSongIds.contains(song.id) || addedSongIds.contains(song.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceHigh.copy(alpha = 0.6f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = song.displayArtworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.displayTitle,
                                    color = colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isAlreadyInPlaylist) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.secondary.copy(alpha = 0.2f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = colors.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "Added",
                                        color = colors.secondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        addedSongIds.add(song.id)
                                        onAddSong(song)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistSongRow(
    song: Song,
    index: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    isReorderable: Boolean = false,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onHeightMeasured: (Float) -> Unit = {},
    onSwipeAddToQueue: () -> Unit = {},
    onSwipeRemove: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onChangePoster: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRemove: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isPressed by rowInteractionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "playlist_row_press"
    )

    val animatedBgAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.14f else 0f,
        animationSpec = tween(300),
        label = "playlist_row_bg"
    )
    val bgColor = if (animatedBgAlpha > 0f) colors.primary.copy(alpha = animatedBgAlpha) else Color.Transparent
    val textColor = if (isPlaying) colors.primary else colors.onSurface
    val textWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold
    var showOptionsSheet by remember { mutableStateOf(false) }

    val swipeOffsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeightMeasured(it.size.height.toFloat()) }
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = if (isDragging) 1.03f else pressScale
                scaleY = if (isDragging) 1.03f else pressScale
                shadowElevation = if (isDragging) 24f else 0f
            }
            .zIndex(if (isDragging) 10f else 0f)
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Swipe action background indicators
        if (swipeOffsetX.value > 12f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary.copy(alpha = 0.22f))
                    .padding(start = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Queue", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        } else if (swipeOffsetX.value < -12f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.error.copy(alpha = 0.22f))
                    .padding(end = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Remove", color = colors.error, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Foreground Song Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeOffsetX.value
                }
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) colors.primary.copy(alpha = 0.2f) else bgColor)
                .combinedClickable(
                    interactionSource = rowInteractionSource,
                    indication = ripple(bounded = true, color = colors.primary),
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .pointerInput(isMultiSelectMode, isDragging) {
                    if (!isMultiSelectMode && !isDragging) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (swipeOffsetX.value > 110f) {
                                        onSwipeAddToQueue()
                                    } else if (swipeOffsetX.value < -110f) {
                                        onSwipeRemove()
                                    }
                                    swipeOffsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    swipeOffsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val current = swipeOffsetX.value
                                    swipeOffsetX.snapTo((current + dragAmount * 0.75f).coerceIn(-150f, 150f))
                                }
                            }
                        )
                    }
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox in MultiSelect mode OR Index/Equalizer
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onToggleSelect() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            } else {
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
                Spacer(modifier = Modifier.width(6.dp))
            }

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

            // Reorder handle (Custom mode, not multi-select)
            if (isReorderable && !isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { _, dragAmount -> onDrag(dragAmount.y) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 3 Dots
            Box {
                IconButton(
                    onClick = { showOptionsSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.secondary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Song options",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showOptionsSheet,
                    onDismissRequest = { showOptionsSheet = false },
                    modifier = Modifier
                        .background(colors.surfaceHighest, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                        .width(210.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play next", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp)) },
                        onClick = { showOptionsSheet = false; onPlayNext() }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp)) },
                        onClick = { showOptionsSheet = false; onAddToQueue() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isDownloaded) "Delete download" else "Download", color = if (isDownloaded) colors.error else colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(if (isDownloaded) Icons.Outlined.Delete else Icons.Outlined.Download, contentDescription = null, tint = if (isDownloaded) colors.error else colors.onSurface, modifier = Modifier.size(20.dp)) },
                        onClick = { showOptionsSheet = false; onDownload() }
                    )
                    DropdownMenuItem(
                        text = { Text("Change Poster", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) },
                        onClick = { showOptionsSheet = false; onChangePoster() }
                    )
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                    DropdownMenuItem(
                        text = { Text("Remove from playlist", color = colors.error, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp)) },
                        onClick = { showOptionsSheet = false; onRemove() }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtworkBox(
    artworkUrl: String,
    isPlaying: Boolean,
    colors: DreaminColors
) {
    Box(
        modifier = Modifier.size(52.dp)
    ) {
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
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val colors = LocalDreaminColors.current
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist") },
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

/**
 * Sleek, glassmorphic vertical fast-scroll slider and scrubber for playlists.
 * Allows effortless navigation across 300+ tracks with a floating index indicator.
 */
@Composable
fun PlaylistFastScroller(
    listState: LazyListState,
    itemCount: Int,
    songs: List<Song>,
    dominantColor: Color,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 1) return
    val colors = LocalDreaminColors.current
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(1f) }

    val isScrollInProgress = listState.isScrollInProgress

    val scrollFraction by remember {
        derivedStateOf {
            if (isDragging) dragFraction
            else {
                val effectiveIdx = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                (effectiveIdx.toFloat() / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }

    val alphaAnim by animateFloatAsState(
        targetValue = if (isDragging || isScrollInProgress) 1f else 0.25f,
        animationSpec = tween(if (isDragging || isScrollInProgress) 120 else 400),
        label = "fast_scroll_alpha"
    )

    val currentTargetIndex = if (isDragging) {
        (dragFraction * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    } else 0
    val currentSongSnippet = if (isDragging) songs.getOrNull(currentTargetIndex) else null

    Box(
        modifier = modifier
            .width(42.dp)
            .graphicsLayer { alpha = alphaAnim }
            .onSizeChanged { containerHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(itemCount) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val fraction = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        dragFraction = fraction
                        val targetIdx = (fraction * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                        scope.launch {
                            listState.scrollToItem((targetIdx + 1).coerceIn(0, itemCount))
                        }
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        dragFraction = fraction
                        val targetIdx = (fraction * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                        scope.launch {
                            listState.scrollToItem((targetIdx + 1).coerceIn(0, itemCount))
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopEnd
    ) {
        // Track Line
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(3.dp)
                .padding(vertical = 12.dp)
                .clip(CircleShape)
                .background(colors.surfaceHighest.copy(alpha = 0.45f))
        )

        // Floating Bubble showing Track Index & Title when dragging
        if (isDragging) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val availableH = (containerHeightPx - 100f).coerceAtLeast(0f)
                        translationY = availableH * scrollFraction
                        translationX = -120f
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceHighest.copy(alpha = 0.95f))
                    .border(1.dp, dominantColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(dominantColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${currentTargetIndex + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = dominantColor
                    )
                }
                Text(
                    text = currentSongSnippet?.displayTitle ?: "Track ${currentTargetIndex + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp)
                )
            }
        }

        // Thumb Pill (Phase-Deferred Translation, zero recomposition during scrolling)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 6.dp, height = 36.dp)
                .graphicsLayer {
                    val availableH = (containerHeightPx - 100f).coerceAtLeast(0f)
                    translationY = availableH * scrollFraction
                }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(dominantColor, colors.primary)
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (isDragging) 0.8f else 0.3f),
                    shape = CircleShape
                )
        )
    }
}
