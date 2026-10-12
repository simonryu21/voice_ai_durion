package com.lgcns.codeengine.translation.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

interface AudioPlayerManager {
    val queueSize: Int
    val isPlaying: Boolean

    fun queueAudio(data: ByteArray)
    suspend fun startPlayback()
    fun stop()
}

class AudioPlayerManagerImpl : AudioPlayerManager {

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var audioTrack: AudioTrack? = null
    private val _isPlaying = AtomicBoolean(false)
    private val trackLock = Object()

    override val queueSize: Int get() = audioQueue.size
    override val isPlaying: Boolean get() = _isPlaying.get()

    override fun queueAudio(data: ByteArray) {
        // Copy data to avoid reference issues
        audioQueue.offer(data.copyOf())
    }

    override suspend fun startPlayback() = withContext(Dispatchers.IO) {
        synchronized(trackLock) {
            if (_isPlaying.get()) {
                return@withContext
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            // Small buffer for low-latency real-time playback
            val bufferSize = maxOf(minBufferSize * 2, 4800) // ~100ms buffer

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also { track ->
                    track.play()
                    _isPlaying.set(true)
                    Log.d(TAG, "AudioTrack started, buffer: $bufferSize bytes")
                }
        }

        try {
            // Simple streaming loop - AudioTrack handles buffering internally
            while (coroutineContext.isActive && _isPlaying.get()) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    writeToTrack(chunk)
                } else {
                    // Very short delay when queue empty
                    kotlinx.coroutines.delay(2)
                }
            }
        } finally {
            Log.d(TAG, "Playback loop ended")
        }
    }

    private fun writeToTrack(chunk: ByteArray) {
        synchronized(trackLock) {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // WRITE_BLOCKING ensures data is written before returning
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    override fun stop() {
        _isPlaying.set(false)
        audioQueue.clear()

        synchronized(trackLock) {
            releaseTrack()
        }
    }

    private fun releaseTrack() {
        audioTrack?.let { track ->
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.release()
                } else {
                    // Track not initialized, nothing to do
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing track: ${e.message}")
            }
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "AudioPlayer"
        const val SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}

