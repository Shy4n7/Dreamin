package com.shyan.dreamin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.shyan.dreamin.data.local.AppDatabase
import com.shyan.dreamin.ui.screens.MusicPlayerScreen
import com.shyan.dreamin.ui.theme.ResonanceTheme

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        val startTime = System.currentTimeMillis()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Fast-path splash screen condition: holds for at most 300ms while Room DB and DataStore warm up in background
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - startTime < 220L
        }

        // Enforce 120Hz / highest refresh rate on device displays for buttery smooth scrolls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = display
                val modes = display?.supportedModes
                val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxRefreshMode.modeId
                    window.attributes = lp
                }
            } catch (e: Exception) {
                // Fallback gracefully if display mode is locked by OS
            }
        }

        // Kick off DB connection on IO immediately — runs in parallel with Compose inflation
        AppDatabase.warmUp(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ResonanceTheme {
                MusicPlayerScreen()
            }
        }
    }
}
