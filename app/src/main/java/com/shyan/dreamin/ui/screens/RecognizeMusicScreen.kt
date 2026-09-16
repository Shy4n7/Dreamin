@file:OptIn(ExperimentalMaterial3Api::class)

package com.shyan.dreamin.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shyan.dreamin.data.local.AppDatabase
import com.shyan.dreamin.data.local.entity.RecognizedSongEntity
import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.service.MusicRecognitionService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 🎙️ Redesigned Music Recognition Screen matching reference Screenshot 3.
 *
 * Features:
 * - Clean obsidian header with back navigation and recognition history sheet.
 * - Center interactive circular button with live concentric radar ripple rings
 *   modulated in real-time by microphone amplitude.
 * - Live recognition status states (Idle, Listening, Identifying, Match, Error).
 * - 1-tap playback integration that resolves and plays recognized tracks in Dreamin.
 * - Docked MiniPlayer layout matching Screenshot 3.
 */
@Composable
fun RecognizeMusicScreen(
    state: PlayerUiState,
    onPlaySong: (Song) -> Unit,
    onAddToPlaylist: ((Song, Long) -> Unit)? = null,
    onOpenNowPlaying: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val recognitionState by MusicRecognitionService.recognitionState.collectAsStateWithLifecycle()
    val liveAmplitude by MusicRecognitionService.liveAmplitude.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<RecognizedSongEntity>>(emptyList()) }

    // Load history from Room
    LaunchedEffect(showHistorySheet) {
        if (showHistorySheet) {
            try {
                AppDatabase.getInstance(context).recognizedSongDao().getAll(50).collect { items ->
                    historyItems = items
                }
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            MusicRecognitionService.startRecognition(context)
        }
    }

    fun startListening() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            MusicRecognitionService.startRecognition(context)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = true) {
        MusicRecognitionService.stopRecognition()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            MusicRecognitionService.stopRecognition()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F1117),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        MusicRecognitionService.stopRecognition()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Recognize Music",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // History button
                IconButton(onClick = { showHistorySheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = "Recognition History",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Center Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val curState = recognitionState) {
                    is MusicRecognitionService.RecognitionState.Success -> {
                        RecognizedMatchCard(
                            song = curState.song,
                            album = curState.album,
                            releaseDate = curState.releaseDate,
                            onPlay = {
                                onPlaySong(curState.song)
                                onOpenNowPlaying()
                            },
                            onRecognizeAgain = { startListening() }
                        )
                    }
                    else -> {
                        val isListening = curState is MusicRecognitionService.RecognitionState.Recording
                        val isAnalyzing = curState is MusicRecognitionService.RecognitionState.Analyzing
                        val isError = curState is MusicRecognitionService.RecognitionState.Error
                        val isNotFound = curState is MusicRecognitionService.RecognitionState.NotFound

                        // Radar ripple rings behind the button
                        val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
                        val pulsePhase by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "pulse_phase"
                        )

                        val buttonScale by animateFloatAsState(
                            targetValue = if (isListening) 1.08f + (liveAmplitude * 0.15f) else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "button_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .drawBehind {
                                    if (isListening) {
                                        val maxRadius = size.width * 0.55f
                                        val ampBoost = liveAmplitude * 20f

                                        for (i in 0..2) {
                                            val phase = (pulsePhase + i * 0.33f) % 1f
                                            val currentRadius = 70.dp.toPx() + phase * (maxRadius - 70.dp.toPx()) + ampBoost
                                            val currentAlpha = (1f - phase) * 0.45f

                                            drawCircle(
                                                color = Color(0xFF4A69BD).copy(alpha = currentAlpha),
                                                radius = currentRadius,
                                                center = center,
                                                style = Stroke(width = 2.5.dp.toPx())
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Main Circular Recognition Button (~130dp)
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .scale(buttonScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                Color(0xFF4A6FA5),
                                                Color(0xFF385380)
                                            )
                                        )
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, color = Color.White)
                                    ) {
                                        if (!isListening && !isAnalyzing) {
                                            startListening()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = "Tap to recognize",
                                    tint = Color.White,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Status Label
                        Text(
                            text = when {
                                isListening -> "Listening to music..."
                                isAnalyzing -> "Identifying song..."
                                isNotFound -> "No match found"
                                isError -> (curState as MusicRecognitionService.RecognitionState.Error).message
                                else -> "Tap to recognize"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                isListening -> "Hold your phone close to the music source"
                                isAnalyzing -> "Comparing acoustic fingerprint with catalog..."
                                isNotFound -> "Couldn't identify track. Please tap to try again."
                                isError -> "Tap to try again."
                                else -> "Identify songs playing around you instantly"
                            },
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (isNotFound || isError) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { startListening() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }
    }

    // Recognition History Bottom Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = colors.surfaceContainer,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recognition History",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (historyItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    AppDatabase.getInstance(context).recognizedSongDao().clearAll()
                                    historyItems = emptyList()
                                }
                            }
                        ) {
                            Text("Clear All", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (historyItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recognized songs yet.",
                            color = colors.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(historyItems, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val song = Song(
                                            id = if (item.songId.isNotBlank()) item.songId else "rec_${item.id}",
                                            title = item.title,
                                            artist = item.artist,
                                            artworkUrl = item.artworkUrl,
                                            album = item.album
                                        )
                                        showHistorySheet = false
                                        onPlaySong(song)
                                        onOpenNowPlaying()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val song = Song(
                                            id = if (item.songId.isNotBlank()) item.songId else "rec_${item.id}",
                                            title = item.title,
                                            artist = item.artist,
                                            artworkUrl = item.artworkUrl,
                                            album = item.album
                                        )
                                        showHistorySheet = false
                                        onPlaySong(song)
                                        onOpenNowPlaying()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = colors.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🎵 Match result presentation card for recognized song.
 */
@Composable
private fun RecognizedMatchCard(
    song: Song,
    album: String,
    releaseDate: String,
    onPlay: () -> Unit,
    onRecognizeAgain: () -> Unit
) {
    val colors = LocalDreaminColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.75f))
            .border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artwork
        AsyncImage(
            model = song.displayArtworkUrl,
            contentDescription = song.title,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = song.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Artist & Album
        val subtitle = if (album.isNotBlank()) "${song.artist} • $album" else song.artist
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Actions: Play on Dreamin & Recognize Another
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play Now", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onRecognizeAgain,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Text("Scan Again", color = Color.White)
            }
        }
    }
}
