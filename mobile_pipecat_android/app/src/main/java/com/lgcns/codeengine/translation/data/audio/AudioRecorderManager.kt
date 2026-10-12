package com.lgcns.codeengine.translation.data.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

interface AudioRecorderManager {
    fun startRecording(): Flow<ByteArray>
    fun stopRecording()
    val isRecording: Boolean
    val audioAmplitude: Flow<Float>
}

class AudioRecorderManagerImpl(
    private val audioManager: AudioManager? = null
) : AudioRecorderManager {

    private var audioRecord: AudioRecord? = null
    private var _isRecording = false
    override val isRecording: Boolean get() = _isRecording

    private val _audioAmplitude = Channel<Float>(Channel.CONFLATED)
    override val audioAmplitude: Flow<Float> = _audioAmplitude.receiveAsFlow()

    @SuppressLint("MissingPermission")
    override fun startRecording(): Flow<ByteArray> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)

        // 마이크가 다른 프로세스(예: WebView WebRTC)에 점유돼 있으면 AudioRecord
        // 생성/시작이 실패한다. 이때 예외를 던지면 collector(InterpretationUseCase)가
        // 잡지 않아 앱이 FATAL로 죽으므로, 실패 시 빈 flow로 graceful 종료한다.
        val record = try {
            AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord creation failed: ${e.message}")
            return@flow
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed (mic unavailable?)")
            runCatching { record.release() }
            return@flow
        }

        // Force use built-in microphone instead of headset mic
        setPreferredBuiltInMic(record)

        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioRecord startRecording failed (mic busy?): ${e.message}")
            runCatching { record.release() }
            return@flow
        }

        audioRecord = record
        _isRecording = true

        val buffer = ByteArray(CHUNK_SIZE)

        try {
            while (coroutineContext.isActive && _isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: 0
                if (bytesRead > 0) {
                    // Calculate amplitude
                    calculateAmplitude(buffer, bytesRead)
                    emit(buffer.copyOf(bytesRead))
                }
            }
        } finally {
            stopRecording()
        }
    }.flowOn(Dispatchers.IO)

    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int) {
        var sum = 0.0
        // Process 16-bit samples
        for (i in 0 until bytesRead step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += sample * sample
        }
        val rms = sqrt(sum / (bytesRead / 2))
        // Normalize roughly to 0..1 range (assuming max generic volume around 32767)
        val normalized = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
        _audioAmplitude.trySend(normalized)
    }

    override fun stopRecording() {
        _isRecording = false
        _audioAmplitude.trySend(0f) // Reset amplitude
        val record = audioRecord
        audioRecord = null
        if (record == null) return
        // record.stop()은 state가 INITIALIZED여도 native 계층 오류로
        // IllegalStateException을 던질 수 있다(마이크 비정상 종료 등).
        // 잡지 않으면 앱이 FATAL로 죽으므로 방어한다.
        try {
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "AudioRecord stop/release failed: ${e.message}")
            runCatching { record.release() }
        }
    }

    private fun setPreferredBuiltInMic(record: AudioRecord) {
        if (audioManager == null) {
            Log.w(TAG, "AudioManager not available, cannot set preferred mic")
            return
        }

        try {
            // Find built-in microphone
            val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val builtInMic = inputDevices.find { device ->
                device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
            }

            if (builtInMic != null) {
                val success = record.setPreferredDevice(builtInMic)
                Log.d(TAG, "Set preferred device to built-in mic: $success")
            } else {
                Log.w(TAG, "Built-in microphone not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preferred mic: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        // 20ms of audio at 16kHz, 16-bit, mono = 640 bytes
        const val CHUNK_SIZE = 640

        // Buffer size should be at least 4x chunk size for stability
        val BUFFER_SIZE: Int
            get() {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )
                return maxOf(minBufferSize, CHUNK_SIZE * 4)
            }
    }
}

