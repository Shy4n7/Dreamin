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
                            onSongClick = { song ->
                                keyboard?.hide()
                                onSongClickFromList(song, searchResults)
                            },
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
                    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isLoadingChart,
                        onRefresh = onRefresh,
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                                state = pullRefreshState,
                                isRefreshing = isLoadingChart,
                                containerColor = colors.surfaceHighest,
                                color = colors.primary,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
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
                    val haptic = LocalHapticFeedback.current
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onQueryChange("")
                            onClear()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear search query",
                            tint = colors.primary,
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
fun ShimmerSongList(count: Int) {
    // Single InfiniteTransition shared across all rows — one animation loop, not N
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        repeat(count) {
            ShimmerSongRowWithBrush(brush)
        }
    }
}

@Composable
fun ShimmerSongRowWithBrush(brush: Brush) {
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
        itemsIndexed(
            items = songs,
            key = { idx, song -> "${song.id}_$idx" },
            contentType = { _, _ -> "HorizontalSongCard" }
        ) { index, song ->
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
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(20.dp)
                                .graphicsLayer {
                                    scaleY = bar1
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f)
                                }
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.primary)
                        )
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(20.dp)
                                .graphicsLayer {
                                    scaleY = bar2
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f)
                                }
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.primary)
                        )
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(20.dp)
                                .graphicsLayer {
                                    scaleY = bar3
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f)
                                }
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.primary)
                        )
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
        itemsIndexed(
            items = songs,
            key = { index, song -> "${song.id}_$index" },
            contentType = { _, _ -> "FeaturedHeroCard" }
        ) { index, song ->
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
fun JumpBackInCard(
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
                    onSongClick = { song -> onSongClickFromList(song, recentlyPlayed) }
                )
            }
        }

        if (topSongs.isNotEmpty()) {
            item(key = "section_top") { SectionTitle("Your Top Songs") }
            item(key = "row_top") {
                HorizontalSongCardsRow(
                    songs = topSongs,
                    currentSongId = currentSong?.id,
                    onSongClick = { song -> onSongClickFromList(song, topSongs) }
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
                        onClick = { onSongClickFromList(song, trending) },
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
                        onClick = { onSongClickFromList(song, recommendations) },
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
fun HomeHeader(
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
