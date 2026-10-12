package com.lgcns.codeengine.translation.domain.usecase

import com.lgcns.codeengine.translation.data.audio.AudioPlayerManager
import com.lgcns.codeengine.translation.data.audio.AudioRecorderManager
import com.lgcns.codeengine.translation.data.audio.HeadsetManager
import com.lgcns.codeengine.translation.data.network.GeminiLiveService
import com.lgcns.codeengine.translation.data.network.model.ConnectionState
import com.lgcns.codeengine.translation.data.network.model.ServerEvent
import com.lgcns.codeengine.translation.domain.model.InterpretationState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface InterpretationUseCase {
    val state: StateFlow<InterpretationState>
    val audioAmplitude: Flow<Float>
    /**
     * Smoothed round-trip latency in ms — measured as the time between the
     * most recent outgoing mic chunk and the next incoming TTS audio chunk.
     * `null` until the first measurement is available.
     */
    val latencyMs: StateFlow<Int?>

    fun start(targetLanguage: String)
    fun stop()

    /**
     * Send a typed message to the interpreter. When a session is already
     * active the text is sent immediately; otherwise a session is started
     * with [targetLanguage] and the text is flushed once connected.
     *
     * Blank text is ignored.
     */
    fun sendText(text: String, targetLanguage: String)
}

class InterpretationUseCaseImpl(
    private val headsetManager: HeadsetManager,
    private val geminiLiveService: GeminiLiveService,
    private val audioRecorderManager: AudioRecorderManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InterpretationUseCase {

    private val _state = MutableStateFlow<InterpretationState>(InterpretationState.Idle)
    override val state: StateFlow<InterpretationState> = _state.asStateFlow()

    override val audioAmplitude: Flow<Float> = audioRecorderManager.audioAmplitude

    private val _latencyMs = MutableStateFlow<Int?>(null)
    override val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

    @Volatile private var lastSendAtNanos: Long = 0L
    @Volatile private var pendingText: String? = null

    private var scope: CoroutineScope? = null
    private var inputJob: Job? = null
    private var outputJob: Job? = null
    private var connectionObserverJob: Job? = null
    private var serverEventJob: Job? = null
    private var headsetObserverJob: Job? = null

    override fun start(targetLanguage: String) {
        // Check headset first
        if (!headsetManager.isHeadsetConnected()) {
            _state.value = InterpretationState.HeadsetRequired
            pendingText = null
            return
        }

        // Create new scope for this session
        scope = CoroutineScope(SupervisorJob() + mainDispatcher)

        // Start observing headset state
        startHeadsetObserver()

        // Connect to Gemini with target language
        geminiLiveService.connect(targetLanguage)

        // Observe connection state
        connectionObserverJob = scope?.launch {
            geminiLiveService.connectionState.collect { connectionState ->
                when (connectionState) {
                    is ConnectionState.Connecting -> {
                        _state.value = InterpretationState.Connecting
                    }
                    is ConnectionState.Connected -> {
                        _state.value = InterpretationState.Active
                        startInputJob()
                        startOutputJob()
                        startServerEventJob()
                        flushPendingText()
                    }
                    is ConnectionState.Disconnected -> {
                        if (_state.value != InterpretationState.Idle) {
                            _state.value = InterpretationState.Idle
                        }
                    }
                    is ConnectionState.Error -> {
                        _state.value = InterpretationState.Error(connectionState.message)
                    }
                }
            }
        }
    }

    override fun stop() {
        // Stop all jobs
        inputJob?.cancel()
        outputJob?.cancel()
        connectionObserverJob?.cancel()
        serverEventJob?.cancel()
        headsetObserverJob?.cancel()

        // Stop all managers
        audioRecorderManager.stopRecording()
        audioPlayerManager.stop()
        geminiLiveService.disconnect()

        // Reset state
        _state.value = InterpretationState.Idle
        _latencyMs.value = null
        lastSendAtNanos = 0L
        pendingText = null

        // Cancel scope
        scope?.cancel()
        scope = null
    }

    override fun sendText(text: String, targetLanguage: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        when (_state.value) {
            is InterpretationState.Active -> {
                lastSendAtNanos = System.nanoTime()
                geminiLiveService.sendText(trimmed)
            }
            is InterpretationState.Connecting -> {
                // A session is already coming up — flush on Connected.
                pendingText = trimmed
            }
            else -> {
                // Idle / Error / HeadsetRequired — start a session and send
                // the text once the connection is established.
                pendingText = trimmed
                start(targetLanguage)
            }
        }
    }

    private fun flushPendingText() {
        val text = pendingText ?: return
        pendingText = null
        lastSendAtNanos = System.nanoTime()
        geminiLiveService.sendText(text)
    }

    private fun startInputJob() {
        inputJob = scope?.launch(ioDispatcher) {
            audioRecorderManager.startRecording().collect { pcmData ->
                lastSendAtNanos = System.nanoTime()
                geminiLiveService.sendAudio(pcmData)
            }
        }
    }

    private fun startOutputJob() {
        outputJob = scope?.launch(ioDispatcher) {
            audioPlayerManager.startPlayback()
        }
    }

    private fun startServerEventJob() {
        serverEventJob = scope?.launch {
            geminiLiveService.serverEvents.collect { event ->
                when (event) {
                    is ServerEvent.AudioData -> {
                        recordLatencySample()
                        // Queue audio for playback - no clearing!
                        audioPlayerManager.queueAudio(event.data)
                    }
                    is ServerEvent.TurnComplete -> {
                        // For simultaneous interpretation: Do NOT stop playback
                    }
                    is ServerEvent.Interrupted -> {
                        // For simultaneous interpretation: Do NOT clear queue
                    }
                    is ServerEvent.SetupComplete -> {
                        // Already handled by connection state
                    }
                }
            }
        }
    }

    private fun recordLatencySample() {
        val sendAt = lastSendAtNanos
        if (sendAt == 0L) return
        val sampleMs = ((System.nanoTime() - sendAt) / 1_000_000L).toInt()
        if (sampleMs !in LATENCY_SAMPLE_RANGE) return
        val prev = _latencyMs.value
        val next = if (prev == null) {
            sampleMs
        } else {
            (prev * LATENCY_SMOOTHING + sampleMs * (1f - LATENCY_SMOOTHING)).toInt()
        }
        _latencyMs.value = next
    }

    private fun startHeadsetObserver() {
        headsetObserverJob = scope?.launch {
            headsetManager.observeHeadsetState().collect { headsetState ->
                if (!headsetState.isConnected && _state.value == InterpretationState.Active) {
                    // Headset disconnected while active - stop interpretation
                    _state.value = InterpretationState.HeadsetRequired
                    stop()
                }
            }
        }
    }

    private companion object {
        const val LATENCY_SMOOTHING = 0.6f
        val LATENCY_SAMPLE_RANGE = 1..10_000
    }
}
