package com.shyan.nigharam.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.shyan.nigharam.data.model.*
import com.shyan.nigharam.ui.theme.*
import com.shyan.nigharam.viewmodel.MusicPlayerViewModel

@Composable
fun MusicPlayerScreen(vm: MusicPlayerViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // Dynamic blurred background from album art
        state.currentSong?.artworkUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.15f),
                contentScale = ContentScale.Crop
            )
        }

        // Dark overlay gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(state.dominantColor).copy(alpha = 0.25f),
                            DeepBlack
                        )
                    )
                )
        )

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top bar
            TopBar(
                isQueueVisible = state.isQueueVisible,
                onQueueToggle = vm::toggleQueue
            )

            // Search bar
            SearchBar(
                query = state.searchQuery,
                onQueryChange = vm::setSearchQuery,
                onClear = vm::clearSearch
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content area
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isQueueVisible -> QueuePanel(
                        queue = state.queue,
                        currentSong = state.currentSong,
                        onSongClick = vm::playSong,
                        onRemove = vm::removeFromQueue
                    )
                    state.isSearchActive && state.searchQuery.isNotEmpty() -> SongList(
                        title = "Results for \"${state.searchQuery}\"",
                        songs = state.searchResults,
                        currentSong = state.currentSong,
                        searchError = state.searchError,
                        onSongClick = { vm.playSong(it); vm.clearSearch() },
                        onAddToQueue = vm::addToQueue
                    )
                    else -> HomeContent(
                        trending = state.trendingCharts,
                        recommendations = state.recommendations,
                        currentSong = state.currentSong,
                        onSongClick = vm::playSong,
                        onAddToQueue = vm::addToQueue
                    )
                }
            }

            // Player panel — always visible at the bottom
            PlayerPanel(
                state = state,
                onPlayPause = vm::togglePlayPause,
                onNext = vm::playNext,
                onPrevious = vm::playPrevious,
                onSeek = vm::seekTo
            )
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@Composable
fun TopBar(isQueueVisible: Boolean, onQueueToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("DREAMIN", fontSize = 11.sp, letterSpacing = 4.sp,
                color = CyanAccent, fontWeight = FontWeight.W600)
            Text("Your Music", fontSize = 20.sp, color = OnSurface,
                fontWeight = FontWeight.Bold)
        }

        IconButton(onClick = onQueueToggle) {
            Icon(
                imageVector = if (isQueueVisible) Icons.Filled.QueueMusic else Icons.Outlined.QueueMusic,
                contentDescription = "Queue",
                tint = if (isQueueVisible) CyanAccent else OnSurfaceMed,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// ── Search Bar ────────────────────────────────────────────────────────────────

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        placeholder = { Text("Search songs, artists…", color = OnSurfaceLow) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceMed) },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = { onClear(); focusManager.clearFocus() }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = OnSurfaceMed)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardBlack,
            unfocusedContainerColor = SurfaceBlack,
            focusedBorderColor = CyanAccent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = CyanAccent,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

// ── Home Content ──────────────────────────────────────────────────────────────

@Composable
fun HomeContent(
    trending: List<Song>,
    recommendations: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (trending.isNotEmpty()) {
            item {
                SectionHeader("🔥 Trending Now")
            }
            items(trending.take(10)) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onAddToQueue = { onAddToQueue(song) }
                )
            }
        }

        if (recommendations.isNotEmpty()) {
            item {
                SectionHeader("✨ Recommended For You")
            }
            items(recommendations.take(8)) { song ->
                SongRow(
                    song = song,
                    isPlaying = currentSong?.id == song.id,
                    onClick = { onSongClick(song) },
                    onAddToQueue = { onAddToQueue(song) }
                )
            }
        }

        if (trending.isEmpty() && recommendations.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, contentDescription = null,
                            tint = OnSurfaceLow, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Search for a song to get started",
                            color = OnSurfaceLow, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Song List (search results) ────────────────────────────────────────────────

@Composable
fun SongList(
    title: String,
    songs: List<Song>,
    currentSong: Song?,
    searchError: String? = null,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    LazyColumn {
        item { SectionHeader(title) }
        if (searchError != null) {
            item {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = searchError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (songs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Text("No results found", color = OnSurfaceLow)
                }
            }
        }
        items(songs) { song ->
            SongRow(
                song = song,
                isPlaying = currentSong?.id == song.id,
                onClick = { onSongClick(song) },
                onAddToQueue = { onAddToQueue(song) }
            )
        }
    }
}

// ── Queue Panel ───────────────────────────────────────────────────────────────

@Composable
fun QueuePanel(
    queue: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onRemove: (Song) -> Unit
) {
    LazyColumn {
        item { SectionHeader("Up Next — ${queue.size} songs") }
        if (queue.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Text("Queue is empty. Add songs to get started.",
                        color = OnSurfaceLow, fontSize = 14.sp)
                }
            }
        }
        items(queue, key = { it.id }) { song ->
            SongRow(
                song = song,
                isPlaying = currentSong?.id == song.id,
                onClick = { onSongClick(song) },
                onAddToQueue = { onRemove(song) },
                trailingIcon = Icons.Default.RemoveCircleOutline
            )
        }
    }
}

// ── Section Header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp, end = 20.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = OnSurfaceMed,
        letterSpacing = 0.5.sp
    )
}

// ── Song Row ──────────────────────────────────────────────────────────────────

@Composable
fun SongRow(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.AddCircleOutline
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isPlaying) CardBlack else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art
        Box {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Playing",
                        tint = CyanAccent, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = if (isPlaying) CyanAccent else OnSurface,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(song.artist, color = OnSurfaceMed, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onAddToQueue) {
            Icon(trailingIcon, contentDescription = null,
                tint = OnSurfaceLow, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Player Panel ──────────────────────────────────────────────────────────────

@Composable
fun PlayerPanel(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val song = state.currentSong

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceBlack,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (song == null) {
                // Empty state
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = OnSurfaceLow)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Nothing playing", color = OnSurfaceLow, fontSize = 14.sp)
                }
            } else {

            // Song info row
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = OnSurface, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                    Text(song.artist, color = OnSurfaceMed, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Progress bar
            val progress = if (state.durationMs > 0)
                (state.currentPositionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            else 0f

            Slider(
                value = progress,
                onValueChange = { onSeek((it * state.durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = CyanAccent,
                    activeTrackColor = CyanAccent,
                    inactiveTrackColor = OnSurfaceLow
                )
            )

            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.currentPositionMs.formatDuration(), color = OnSurfaceLow, fontSize = 11.sp)
                Text(state.durationMs.formatDuration(), color = OnSurfaceLow, fontSize = 11.sp)
            }

            Spacer(Modifier.height(4.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous",
                        tint = OnSurface, modifier = Modifier.size(32.dp))
                }

                // Play/Pause button (large, accented)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when (state.playbackState) {
                                is PlaybackState.Loading -> OnSurfaceLow
                                else -> CyanAccent
                            }
                        )
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    when (state.playbackState) {
                        is PlaybackState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = DeepBlack,
                            strokeWidth = 2.dp
                        )
                        is PlaybackState.Playing -> Icon(
                            Icons.Default.Pause, contentDescription = "Pause",
                            tint = DeepBlack, modifier = Modifier.size(28.dp)
                        )
                        else -> Icon(
                            Icons.Default.PlayArrow, contentDescription = "Play",
                            tint = DeepBlack, modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next",
                        tint = OnSurface, modifier = Modifier.size(32.dp))
                }
            }

            // Error message
            if (state.playbackState is PlaybackState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (state.playbackState as PlaybackState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
            } // end else (song != null)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun Long.formatDuration(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

