package com.shyan.dreamin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shyan.dreamin.service.AudioFxManager
import com.shyan.dreamin.service.EqualizerUiState
import com.shyan.dreamin.ui.screens.LocalDreaminColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    onDismiss: () -> Unit
) {
    val eqState by AudioFxManager.uiState.collectAsStateWithLifecycle()
    val colors = LocalDreaminColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceHigh,
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Studio Equalizer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            if (eqState.isEnabled) "32-bit DSP Active" else "Bypassed",
                            fontSize = 12.sp,
                            color = if (eqState.isEnabled) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = { AudioFxManager.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.onSurfaceVariant,
                        uncheckedTrackColor = colors.surfaceHighest
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AutoEq Headphone Profiles Row
            val activeAutoEq = eqState.activeAutoEqProfile
            val connectedDevice by com.shyan.dreamin.service.AutoEqManager.connectedDeviceName.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AUTOEQ CALIBRATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                if (activeAutoEq != null) {
                    Text(
                        "Calibrated (${activeAutoEq.target})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                } else if (!connectedDevice.isNullOrBlank()) {
                    Text(
                        "🎧 $connectedDevice",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.shyan.dreamin.data.model.AutoEqCatalog.PROFILES.forEach { profile ->
                    val isSelected = activeAutoEq?.id == profile.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.primary else colors.surfaceHighest)
                            .clickable {
                                if (isSelected) {
                                    com.shyan.dreamin.service.AutoEqManager.clearProfile()
                                } else {
                                    com.shyan.dreamin.service.AutoEqManager.selectProfile(profile)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎧 ${profile.displayName}",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else colors.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Presets Horizontal Row
            Text(
                "PRESETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                eqState.availablePresets.forEach { preset ->
                    val isSelected = eqState.selectedPresetName == preset && activeAutoEq == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.primary else colors.surfaceHighest)
                            .clickable {
                                com.shyan.dreamin.service.AutoEqManager.clearProfile()
                                AudioFxManager.applyPreset(preset)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else colors.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Audio FX Dials: Bass Boost & 3D Virtualizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bass Boost Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceHighest.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bass Boost", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Text("${eqState.bassBoostStrength}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = eqState.bassBoostStrength.toFloat(),
                            onValueChange = { AudioFxManager.setBassBoost(it.toInt()) },
                            valueRange = 0f..100f,
                            enabled = eqState.isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.surfaceHighest
                            )
                        )
                    }
                }

                // 3D Spatial Virtualizer Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceHighest.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.SurroundSound, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("3D Spatial", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Text("${eqState.virtualizerStrength}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = eqState.virtualizerStrength.toFloat(),
                            onValueChange = { AudioFxManager.setVirtualizer(it.toInt()) },
                            valueRange = 0f..100f,
                            enabled = eqState.isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.surfaceHighest
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5-Band Graphic Equalizer Faders
            Text(
                "5-BAND FREQUENCY SPECTRUM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (eqState.bands.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceHighest.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    eqState.bands.forEach { band ->
                        val freqText = if (band.centerFreqHz >= 1000) {
                            "${band.centerFreqHz / 1000} kHz"
                        } else {
                            "${band.centerFreqHz} Hz"
                        }
                        val gainDb = band.gainMillibels / 100

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurface,
                                modifier = Modifier.width(60.dp)
                            )

                            Slider(
                                value = band.gainMillibels.toFloat(),
                                onValueChange = { AudioFxManager.setBandLevel(band.index, it.toInt()) },
                                valueRange = band.minMillibels.toFloat()..band.maxMillibels.toFloat(),
                                enabled = eqState.isEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.primary,
                                    activeTrackColor = colors.primary,
                                    inactiveTrackColor = colors.surfaceHighest
                                )
                            )

                            Text(
                                text = if (gainDb > 0) "+${gainDb}dB" else "${gainDb}dB",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gainDb != 0) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.width(52.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceHighest.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Equalizer active with playback", color = colors.onSurfaceVariant, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
