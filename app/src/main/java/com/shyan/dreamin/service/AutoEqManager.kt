package com.shyan.dreamin.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.shyan.dreamin.data.model.AutoEqCatalog
import com.shyan.dreamin.data.model.HeadphoneProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutoEqManager {

    private val _activeProfile = MutableStateFlow<HeadphoneProfile?>(null)
    val activeProfile: StateFlow<HeadphoneProfile?> = _activeProfile.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private var isReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val deviceName = try { device?.name } catch (_: SecurityException) { null }
                    if (!deviceName.isNullOrBlank()) {
                        _connectedDeviceName.value = deviceName
                        val matched = AutoEqCatalog.findMatchingProfile(deviceName)
                        if (matched != null) {
                            selectProfile(matched)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    _connectedDeviceName.value = null
                }
            }
        }
    }

    fun init(context: Context) {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            try {
                context.registerReceiver(bluetoothReceiver, filter)
                isReceiverRegistered = true
            } catch (_: Exception) {}
        }
        detectCurrentAudioOutput(context)
    }

    fun detectCurrentAudioOutput(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (dev in devices) {
                    if (dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    ) {
                        val name = dev.productName?.toString()
                        if (!name.isNullOrBlank()) {
                            _connectedDeviceName.value = name
                            val matched = AutoEqCatalog.findMatchingProfile(name)
                            if (matched != null && _activeProfile.value == null) {
                                selectProfile(matched)
                            }
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun selectProfile(profile: HeadphoneProfile?) {
        _activeProfile.value = profile
        AudioFxManager.applyAutoEqProfile(profile)
    }

    fun clearProfile() {
        _activeProfile.value = null
        AudioFxManager.applyAutoEqProfile(null)
    }
}
