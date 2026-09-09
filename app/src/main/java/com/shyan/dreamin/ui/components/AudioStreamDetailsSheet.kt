package com.shyan.dreamin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.service.AudioFxManager
import com.shyan.dreamin.ui.screens.LocalDreaminColors

/**
 * 🎧 SpotiFLAC-Grade Live Audio Stream & Decoder Inspector Sheet.
 *
 * Displays:
 * 1. Current active audio codec, sample rate, bit depth/bitrate, and channels.
 * 2. Origin streaming source (JioSaavn CDN vs YouTube Music InnerTube vs Local).
 * 3. Real-time Hardware Loudness Normalization switch and gain level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioStreamDetailsSheet(
    state: PlayerUiState,
    song: Song?,
    onDismiss: () -> Unit
) {
    val colors = LocalDreaminColors.current
    val eqState by AudioFxManager.uiState.collectAsStateWithLifecycle()
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val fmt = state.currentAudioFormat
    val isYt = state.currentSongStreamSource == "youtube" || song?.id?.startsWith("yt_") == true
    val isLossless = fmt?.isLossless == true
    val primaryAccent = when {
        isLossless -> Color(0xFF00E676)
        isYt -> Color(0xFFFF5252)
        else -> colors.primary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceHigh.copy(alpha = 0.94f),
        contentColor = colors.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        },
        shape = sheetShape,
        modifier = Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.30f),
                    primaryAccent.copy(alpha = 0.15f),
                    Color.Transparent
                )
            ),
            shape = sheetShape
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(primaryAccent.copy(alpha = 0.16f))
                        .border(1.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLossless) Icons.Outlined.GraphicEq else if (isYt) Icons.Filled.PlayArrow else Icons.Outlined.HighQuality,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio Stream Specs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isYt) "YouTube Music InnerTube Source" else "JioSaavn 320kbps Master Stream",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Track summary card
            if (song != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceHighest.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.displayArtworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.displayTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Technical Grid
            Text(
                text = "STREAM PROPERTIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceHighest.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StreamSpecRow(
                    icon = Icons.Outlined.HighQuality,
                    label = "Codec",
                    value = fmt?.codec ?: if (isYt) "Opus (libopus)" else "AAC-LC (mp4a)"
                )
                StreamSpecRow(
                    icon = Icons.Outlined.Speed,
                    label = "Bitrate",
                    value = if (fmt != null && fmt.bitrateKbps > 0) "${fmt.bitrateKbps} kbps" else if (isYt) "160 kbps (High Quality)" else "320 kbps (Studio)"
                )
                StreamSpecRow(
                    icon = Icons.Outlined.GraphicEq,
                    label = "Sample Rate",
                    value = "${(fmt?.sampleRateHz ?: if (isYt) 48000 else 44100) / 1000f} kHz"
                )
                StreamSpecRow(
                    icon = Icons.Outlined.SurroundSound,
                    label = "Channels",
                    value = if ((fmt?.channelCount ?: 2) > 1) "Stereo 2.0" else "Mono 1.0"
                )
                StreamSpecRow(
                    icon = Icons.Outlined.Info,
                    label = "Source",
                    value = if (isYt) "YouTube Music InnerTube" else "JioSaavn Cloud CDN"
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Loudness Normalization Control
            Text(
                text = "VOLUME HARMONIZATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surfaceHighest.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Loudness Normalizer",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    "Auto-levels unmastered tracks",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = eqState.isLoudnessNormalizationEnabled,
                            onCheckedChange = { AudioFxManager.setLoudnessNormalization(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryAccent,
                                uncheckedThumbColor = colors.onSurfaceVariant,
                                uncheckedTrackColor = colors.surfaceHighest
                            )
                        )
                    }

                    if (eqState.isLoudnessNormalizationEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Target Level",
                                fontSize = 11.5.sp,
                                color = colors.onSurfaceVariant
                            )
                            val gainDb = eqState.loudnessGainMb / 100f
                            Text(
                                String.format(java.util.Locale.US, "+%.1f dB", gainDb),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent
                            )
                        }
                        Slider(
                            value = eqState.loudnessGainMb.toFloat(),
                            onValueChange = { AudioFxManager.setLoudnessGain(it.toInt()) },
                            valueRange = 0f..600f,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = primaryAccent,
                                inactiveTrackColor = colors.surfaceHighest
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun StreamSpecRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    val colors = LocalDreaminColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.5.sp,
                color = colors.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
