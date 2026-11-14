package com.shyan.dreamin.ui.components

import com.shyan.dreamin.ui.theme.rememberDreaminHaptics

import com.shyan.dreamin.ui.theme.LocalDreaminColors
import com.shyan.dreamin.ui.theme.DreaminMotion
import com.shyan.dreamin.ui.theme.DreaminShapes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shyan.dreamin.data.model.LyricsState
import com.shyan.dreamin.data.model.PlaybackProgress
import kotlinx.coroutines.flow.StateFlow

private val ACTIVE_LINE_OFFSET = 32.dp
private const val AUTOSCROLL_RESUME_MS = 3_000L
private const val INACTIVE_LINE_SCALE = 0.8f

@Composable
fun SyncedLyricsView(
    lyricsState: LyricsState,
    progressFlow: StateFlow<PlaybackProgress>,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    val haptic = rememberDreaminHaptics()
    val progress by progressFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(DreaminShapes.xl)
            .background(LocalDreaminColors.current.background),
        contentAlignment = Alignment.Center
    ) {
        when (lyricsState) {
            is LyricsState.Loading -> {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = colors.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Syncing lyrics with music...",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            is LyricsState.NotFound -> {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.MusicOff,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No synced lyrics found",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap retry to search across multi-source databases",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onRetry,
                        shape = DreaminShapes.medium,
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = colors.surfaceHighest.copy(alpha = 0.65f)
                        )
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Retry",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Again", color = colors.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            is LyricsState.Plain -> {
                val lines = remember(lyricsState.text) {
                    lyricsState.text.lines().map { it.trim() }.filter { it.isNotBlank() }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(vertical = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { idx, line -> "${idx}_${line.hashCode()}" },
                        contentType = { _, _ -> "PlainLyricLine" }
                    ) { _, line ->
                        Text(
                            text = line,
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )
                    }
                }
            }

            is LyricsState.Success -> {
                val lines = lyricsState.lines
                if (lines.isEmpty()) {
                    Text("No lyrics found", color = colors.onSurfaceVariant)
                    return@Box
                }

                // If playback is before the first line, activeIndex = -1 (instrumental intro)
                val activeIndex by remember(lines) {
                    derivedStateOf {
                        lines.indexOfLast { it.timestampMs <= progress.currentPositionMs }
                    }
                }

                val activeLineOffsetPx = with(LocalDensity.current) { ACTIVE_LINE_OFFSET.roundToPx() }
                var isUserDragging by remember { mutableStateOf(false) }
                var resumeToken by remember { mutableIntStateOf(0) }
                var isAutoScrollPaused by remember { mutableStateOf(false) }

                LaunchedEffect(listState) {
                    listState.interactionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is DragInteraction.Start -> {
                                isUserDragging = true
                                isAutoScrollPaused = true
                            }
                            is DragInteraction.Stop, is DragInteraction.Cancel -> {
                                isUserDragging = false
                                resumeToken++
                            }
                        }
                    }
                }

                LaunchedEffect(resumeToken, isUserDragging) {
                    if (!isUserDragging && isAutoScrollPaused) {
                        delay(AUTOSCROLL_RESUME_MS)
                        isAutoScrollPaused = false
                    }
                }

                LaunchedEffect(activeIndex, isAutoScrollPaused) {
                    if (isAutoScrollPaused) return@LaunchedEffect
                    if (activeIndex in lines.indices) {
                        listState.animateScrollToItem(index = activeIndex, scrollOffset = -activeLineOffsetPx)
                    } else if (activeIndex == -1) {
                        listState.animateScrollToItem(0, scrollOffset = 0)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 130.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { idx, line -> "${line.timestampMs}_$idx" },
                        contentType = { _, _ -> "SyncedLyricLine" }
                    ) { index, line ->
                        val isActive = index == activeIndex
                        val isPast = activeIndex >= 0 && index < activeIndex

                        val targetAlpha = if (isActive) 1f else if (isPast) 0.38f else 0.30f
                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(DreaminMotion.DurationLong),
                            label = "lyric_alpha"
                        )

                        val animatedScale by animateFloatAsState(
                            targetValue = if (isActive) 1.0f else INACTIVE_LINE_SCALE,
                            animationSpec = DreaminMotion.bouncy(),
                            label = "lyric_scale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                    alpha = animatedAlpha
                                }
                                .clip(DreaminShapes.medium)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Tap-to-Seek action
                                    haptic.tap()
                                    if (lyricsState.isSynced) {
                                        onSeek(line.timestampMs)
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isActive) {
                                // Ambient Kinetic Bloom behind active singing line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .blur(22.dp)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    colors.primary.copy(alpha = 0.38f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }

                            Text(
                                text = line.text,
                                color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 30.sp
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isAutoScrollPaused,
                    enter = fadeIn(tween(DreaminMotion.DurationShort)) + scaleIn(initialScale = 0.9f),
                    exit = fadeOut(tween(DreaminMotion.DurationShort)) + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                ) {
                    Surface(
                        onClick = { isAutoScrollPaused = false },
                        shape = RoundedCornerShape(50),
                        color = colors.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync", color = Color.Black, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LyricsState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = colors.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}
