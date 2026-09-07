package com.shyan.dreamin.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shyan.dreamin.data.model.AlbumDetail
import com.shyan.dreamin.data.model.AlbumItem
import com.shyan.dreamin.data.model.Song

/**
 * Full-screen detail view for movie soundtrack albums.
 * Features ambient blurred artwork header, metadata overview, "Play All" / "Shuffle",
 * and glassmorphic pill track rows matching the Dreamin design aesthetic.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumDetailScreen(
    album: AlbumDetail,
    currentSongId: String?,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onAddToQueue: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onBack() }
    val colors = LocalDreaminColors.current
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hero Header Section
            item(key = "album_hero_header", contentType = "HeroHeader") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    // Ambient blurred backdrop
                    if (album.artworkUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(album.displayArtworkUrl)
                                .crossfade(200)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(colors.primaryDim, colors.surfaceHighest)
                                    )
                                )
                        )
                    }

                    // Multi-stop gradient overlay for readable contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.30f),
                                        colors.background.copy(alpha = 0.90f),
                                        colors.background
                                    )
                                )
                            )
                    )

                    // Top Bar (Back Button)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.50f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }

                    // Album Artwork Card + Title & Metadata
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Artwork Cover Card
                        Box(
                            modifier = Modifier
                                .size(118.dp)
                                .shadow(14.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                                .background(colors.surfaceHighest)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(album.displayArtworkUrl)
                                    .crossfade(200)
                                    .build(),
                                contentDescription = album.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Title, Year, Artist, Track count
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primary.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "MOVIE ALBUM",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = album.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (album.artist.isNotBlank()) album.artist else "Original Soundtrack",
                                color = Color.White.copy(alpha = 0.80f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${if (album.year.isNotBlank()) "${album.year} • " else ""}${if (album.songs.isNotEmpty()) "${album.songs.size} tracks" else "${album.songCount} tracks"}",
                                color = colors.secondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Action Buttons: Play All & Shuffle
            item(key = "album_action_buttons", contentType = "ActionButtons") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play All",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Play All",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onShuffleAll,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.surfaceHighest.copy(alpha = 0.40f)
                        )
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = colors.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Shuffle",
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Tracklist Header
            item(key = "album_tracklist_header", contentType = "TracklistHeader") {
                Text(
                    text = "Tracklist",
                    color = colors.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Loading state
            if (album.isLoading && album.songs.isEmpty()) {
                item(key = "album_loading", contentType = "LoadingIndicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.primary, strokeWidth = 3.dp)
                    }
                }
            } else if (album.songs.isEmpty()) {
                item(key = "album_empty", contentType = "EmptyState") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No songs available in this album",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // Song Items with Glassmorphic Background Bars
                itemsIndexed(
                    items = album.songs,
                    key = { index, song -> "${song.id}_$index" },
                    contentType = { _, _ -> "AlbumSongBar" }
                ) { index, song ->
                    val isPlaying = currentSongId == song.id
                    AlbumSongRow(
                        song = song,
                        index = index + 1,
                        isPlaying = isPlaying,
                        onClick = { onSongClick(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onPlayNext = { onPlayNext(song) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Individual track row inside AlbumDetailScreen.
 * Styled with rounded glassmorphic background bars, specular top border,
 * and highlighted cyan index numbers matching PlaylistDetailScreen.
 */
@Composable
private fun AlbumSongRow(
    song: Song,
    index: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                brush = if (isPlaying) {
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            colors.primary.copy(alpha = 0.60f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                },
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                if (isPlaying) {
                    Brush.horizontalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.22f),
                            colors.surfaceHighest.copy(alpha = 0.65f),
                            colors.primary.copy(alpha = 0.08f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            colors.surfaceHighest.copy(alpha = 0.40f),
                            colors.surfaceHigh.copy(alpha = 0.25f)
                        )
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Rank Index (01, 02...)
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                Icon(
                    Icons.Filled.Equalizer,
                    contentDescription = "Playing",
                    tint = colors.secondary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = String.format("%02d", index),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (index <= 3) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Artwork Thumbnail
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
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
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle,
                color = if (isPlaying) colors.primary else colors.onSurface,
                fontSize = 14.5.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = song.artist,
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // More Options Button
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Options",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(colors.surfaceHighest)
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = colors.onSurface) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = colors.onSurface)
                    },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = colors.onSurface) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colors.onSurface)
                    },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun AlbumDetailScreenPreview() {
    val sampleAlbum = AlbumDetail(
        id = "preview_album_1",
        title = "Dragon",
        artist = "Anirudh Ravichander",
        artworkUrl = "",
        year = "2025",
        songCount = 4,
        songs = listOf(
            Song(id = "1", title = "Rise of Dragon", artist = "Anirudh Ravichander", artworkUrl = "", duration = 190000),
            Song(id = "2", title = "Fire Theme", artist = "Anirudh Ravichander", artworkUrl = "", duration = 210000)
        )
    )
    AlbumDetailScreen(
        album = sampleAlbum,
        currentSongId = "1",
        onBack = {},
        onSongClick = {},
        onPlayAll = {},
        onShuffleAll = {}
    )
}
