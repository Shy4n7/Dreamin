package com.shyan.dreamin.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shyan.dreamin.data.model.LyricsState
import com.shyan.dreamin.data.model.PlaybackProgress
import com.shyan.dreamin.ui.screens.LocalDreaminColors
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SyncedLyricsView(
    lyricsState: LyricsState,
    progressFlow: StateFlow<PlaybackProgress>,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalDreaminColors.current
    val haptic = LocalHapticFeedback.current
    val progress by progressFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF060609)),
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
                        fontSize = 14.sp,
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap retry to search across multi-source databases",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(14.dp),
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
                        Text("Search Again", color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                            fontSize = 18.sp,
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

                // Apple Music-Style fluid spring autoscroll
                LaunchedEffect(activeIndex) {
                    if (activeIndex in lines.indices) {
                        listState.animateScrollToItem(
                            index = activeIndex,
                            scrollOffset = -80
                        )
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
                            animationSpec = tween(350),
                            label = "lyric_alpha"
                        )

                        val animatedScale by animateFloatAsState(
                            targetValue = if (isActive) 1.10f else 0.95f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
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
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Tap-to-Seek action
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                fontSize = if (isActive) 23.sp else 16.5.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                lineHeight = if (isActive) 32.sp else 24.sp
                            )
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
