package com.lgcns.codeengine.translation.data.network.model

sealed class ServerEvent {
    data class AudioData(val data: ByteArray) : ServerEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AudioData
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data object TurnComplete : ServerEvent()
    data object Interrupted : ServerEvent()
    data object SetupComplete : ServerEvent()
}

