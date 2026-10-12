package com.lgcns.codeengine.translation.data.audio

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.lgcns.codeengine.translation.domain.model.HeadsetState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface HeadsetManager {
    fun getHeadsetState(): HeadsetState
    fun isHeadsetConnected(): Boolean
    fun observeHeadsetState(): Flow<HeadsetState>
}

class HeadsetManagerImpl(
    private val audioManager: AudioManager
) : HeadsetManager {

    override fun getHeadsetState(): HeadsetState {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Check for wired headset first (higher priority)
        val hasWired = devices.any { device ->
            device.type in listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET
            )
        }
        if (hasWired) return HeadsetState.WiredConnected

        // Check for bluetooth headset
        val hasBluetooth = devices.any { device ->
            device.type in listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            )
        }
        if (hasBluetooth) return HeadsetState.BluetoothConnected

        return HeadsetState.Disconnected
    }

    override fun isHeadsetConnected(): Boolean {
        return getHeadsetState().isConnected
    }

    override fun observeHeadsetState(): Flow<HeadsetState> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getHeadsetState())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getHeadsetState())
            }
        }

        // Emit initial state
        trySend(getHeadsetState())

        val handler = Handler(Looper.getMainLooper())
        audioManager.registerAudioDeviceCallback(callback, handler)

        awaitClose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }
}

