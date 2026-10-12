package com.lgcns.codeengine.translation.domain.model

sealed class HeadsetState {
    data object WiredConnected : HeadsetState()
    data object BluetoothConnected : HeadsetState()
    data object Disconnected : HeadsetState()

    val isConnected: Boolean
        get() = this is WiredConnected || this is BluetoothConnected
}

