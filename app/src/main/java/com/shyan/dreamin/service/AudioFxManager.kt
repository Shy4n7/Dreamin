package com.shyan.dreamin.service

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val gainMillibels: Int,
    val minMillibels: Int,
    val maxMillibels: Int
)

data class EqualizerUiState(
    val isEnabled: Boolean = true,
    val selectedPresetName: String = "Studio Flat",
    val bassBoostStrength: Int = 40,      // 0 - 100%
    val virtualizerStrength: Int = 30,    // 0 - 100%
    val loudnessGainMb: Int = 200,        // 0 - 1000 mB
    val bands: List<EqualizerBand> = emptyList(),
    val availablePresets: List<String> = listOf("Studio Flat", "Bass Heavy", "Vocal Clarity", "Electronic", "Rock", "Acoustic")
)

object AudioFxManager {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var prefs: SharedPreferences? = null

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private var currentSessionId: Int = 0

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("dreamin_audiofx_prefs", Context.MODE_PRIVATE)
            loadSavedSettings()
        }
    }

    fun attachAudioSession(sessionId: Int) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId
        release()

        // 1. Hardware Equalizer
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _uiState.value.isEnabled
            }
            refreshBandsFromHardware()
            applyCurrentPreset()
        } catch (e: Exception) {
            android.util.Log.w("AudioFxManager", "Equalizer init skipped: ${e.message}")
        }

        // 2. Dynamic BassBoost
        try {
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = _uiState.value.isEnabled
                if (strengthSupported) {
                    setStrength(((_uiState.value.bassBoostStrength / 100f) * 1000).toInt().toShort())
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioFxManager", "BassBoost init skipped: ${e.message}")
        }

        // 3. 3D Spatial Virtualizer
        try {
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = _uiState.value.isEnabled
                if (strengthSupported) {
                    setStrength(((_uiState.value.virtualizerStrength / 100f) * 1000).toInt().toShort())
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioFxManager", "Virtualizer init skipped: ${e.message}")
        }

        // 4. Loudness Enhancer
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    enabled = _uiState.value.isEnabled
                    setTargetGain(_uiState.value.loudnessGainMb)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioFxManager", "LoudnessEnhancer init skipped: ${e.message}")
        }
    }

    private fun refreshBandsFromHardware() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0].toInt()
            val maxLevel = eq.bandLevelRange[1].toInt()

            val bandList = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val freq = eq.getCenterFreq(i.toShort()) / 1000 // mHz to Hz
                val currentLevel = eq.getBandLevel(i.toShort()).toInt()
                bandList.add(
                    EqualizerBand(
                        index = i,
                        centerFreqHz = freq,
                        gainMillibels = currentLevel,
                        minMillibels = minLevel,
                        maxMillibels = maxLevel
                    )
                )
            }
            _uiState.value = _uiState.value.copy(bands = bandList)
        } catch (e: Exception) {
            android.util.Log.w("AudioFxManager", "refreshBands failed: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            loudnessEnhancer?.enabled = enabled
            saveSettings()
        } catch (_: Exception) {}
    }

    fun setBandLevel(bandIndex: Int, levelMillibels: Int) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), levelMillibels.toShort())
            val updated = _uiState.value.bands.map {
                if (it.index == bandIndex) it.copy(gainMillibels = levelMillibels) else it
            }
            _uiState.value = _uiState.value.copy(bands = updated, selectedPresetName = "Custom")
            saveSettings()
        } catch (_: Exception) {}
    }

    fun setBassBoost(strengthPercent: Int) {
        _uiState.value = _uiState.value.copy(bassBoostStrength = strengthPercent)
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(((strengthPercent / 100f) * 1000).toInt().toShort())
            }
            saveSettings()
        } catch (_: Exception) {}
    }

    fun setVirtualizer(strengthPercent: Int) {
        _uiState.value = _uiState.value.copy(virtualizerStrength = strengthPercent)
        try {
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(((strengthPercent / 100f) * 1000).toInt().toShort())
            }
            saveSettings()
        } catch (_: Exception) {}
    }

    fun applyPreset(presetName: String) {
        _uiState.value = _uiState.value.copy(selectedPresetName = presetName)
        applyCurrentPreset()
        saveSettings()
    }

    private fun applyCurrentPreset() {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        val maxLevel = (eq.bandLevelRange[1].toInt()).coerceAtLeast(1000)

        // Percentage adjustments per band (-1.0f to +1.0f)
        val gains = when (_uiState.value.selectedPresetName) {
            "Bass Heavy" -> listOf(0.70f, 0.40f, 0.00f, 0.20f, 0.30f)
            "Vocal Clarity" -> listOf(-0.20f, 0.10f, 0.60f, 0.40f, 0.10f)
            "Electronic" -> listOf(0.60f, 0.30f, -0.10f, 0.35f, 0.55f)
            "Rock" -> listOf(0.50f, 0.30f, -0.15f, 0.30f, 0.45f)
            "Acoustic" -> listOf(0.30f, 0.20f, 0.30f, 0.30f, 0.20f)
            else -> listOf(0.00f, 0.00f, 0.00f, 0.00f, 0.00f) // Studio Flat
        }

        try {
            for (i in 0 until numBands) {
                val ratio = gains.getOrElse(i) { 0f }
                val targetMb = (ratio * maxLevel).toInt()
                eq.setBandLevel(i.toShort(), targetMb.toShort())
            }
            val updated = _uiState.value.bands.mapIndexed { i, band ->
                val ratio = gains.getOrElse(i) { 0f }
                band.copy(gainMillibels = (ratio * maxLevel).toInt())
            }
            val defaultBass = when (_uiState.value.selectedPresetName) {
                "Bass Heavy" -> 80
                "Electronic" -> 65
                "Rock" -> 45
                "Vocal Clarity" -> 15
                "Acoustic" -> 30
                else -> 40
            }
            setBassBoost(defaultBass)
            _uiState.value = _uiState.value.copy(bands = updated)
        } catch (_: Exception) {}
    }

    private fun saveSettings() {
        val p = prefs ?: return
        p.edit()
            .putBoolean("enabled", _uiState.value.isEnabled)
            .putString("preset", _uiState.value.selectedPresetName)
            .putInt("bass", _uiState.value.bassBoostStrength)
            .putInt("virt", _uiState.value.virtualizerStrength)
            .apply()
    }

    private fun loadSavedSettings() {
        val p = prefs ?: return
        _uiState.value = _uiState.value.copy(
            isEnabled = p.getBoolean("enabled", true),
            selectedPresetName = p.getString("preset", "Studio Flat") ?: "Studio Flat",
            bassBoostStrength = p.getInt("bass", 40),
            virtualizerStrength = p.getInt("virt", 30)
        )
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }
}
