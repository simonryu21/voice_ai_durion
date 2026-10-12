package com.lgcns.codeengine.translation.data.network

import android.util.Base64
import android.util.Log
import com.lgcns.codeengine.translation.data.network.model.ConnectionState
import com.lgcns.codeengine.translation.data.network.model.ServerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

interface GeminiLiveService {
    val connectionState: StateFlow<ConnectionState>
    val serverEvents: Flow<ServerEvent>

    fun buildWebSocketUrl(): String
    fun buildSetupMessage(targetLanguage: String): String
    fun connect(targetLanguage: String)
    fun disconnect()
    fun sendAudio(pcmData: ByteArray)
    fun sendText(text: String)
}

class GeminiLiveServiceImpl(
    private val okHttpClient: OkHttpClient,
    private val apiKey: String
) : GeminiLiveService {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _serverEvents = Channel<ServerEvent>(Channel.BUFFERED)
    override val serverEvents: Flow<ServerEvent> = _serverEvents.receiveAsFlow()

    private var webSocket: WebSocket? = null
    private var currentTargetLanguage: String = "English"
    private var connectionScope: CoroutineScope? = null
    private var setupTimeoutJob: Job? = null
    private var setupMessageSent = false

    override fun buildWebSocketUrl(): String {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
    }

    override fun buildSetupMessage(targetLanguage: String): String {
        val systemInstruction = buildSystemInstruction(targetLanguage)
        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$MODEL_ID")
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", "Charon")
                            })
                        })
                    })
                    // Enable affective dialog for voice style preservation
                    put("enable_affective_dialog", true)
                })
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                // Configure VAD for fast simultaneous interpretation
                // HIGH end sensitivity + 100ms silence = very quick response
                // NO_INTERRUPTION prevents speaker output from interrupting model response
                put("realtime_input_config", JSONObject().apply {
                    put("activity_handling", "NO_INTERRUPTION")
                    put("automatic_activity_detection", JSONObject().apply {
                        put("disabled", false)
                        put("start_of_speech_sensitivity", "START_SENSITIVITY_HIGH")
                        put("end_of_speech_sensitivity", "END_SENSITIVITY_HIGH")
                        put("prefix_padding_ms", 0)
                        put("silence_duration_ms", 300)
                    })
                })
            })
        }
        Log.d(TAG, "Full setup message: $setupJson")
        return setupJson.toString()
    }

    private fun buildSystemInstruction(targetLanguage: String): String {
        return """You are a simultaneous interpreter. Translate speech to $targetLanguage in real-time.

RULES:
- Output ONLY in $targetLanguage
- Translate quickly in short chunks
- Don't wait for complete sentences
- Translate as you hear, phrase by phrase
- Never mix languages in output
- No explanations, just translate immediately

Respond exclusively in $targetLanguage."""
    }

    override fun connect(targetLanguage: String) {
        if (_connectionState.value == ConnectionState.Connecting ||
            _connectionState.value == ConnectionState.Connected) {
            Log.d(TAG, "Already connecting or connected, skipping")
            return
        }

        // Validate API key
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is empty or blank")
            _connectionState.value = ConnectionState.Error("API key is not configured. Please set GEMINI_API_KEY in local.properties")
            return
        }

        currentTargetLanguage = targetLanguage
        setupMessageSent = false
        _connectionState.value = ConnectionState.Connecting

        // Create scope for connection lifecycle
        connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val url = buildWebSocketUrl()
        Log.d(TAG, "Connecting to WebSocket: ${url.take(80)}...")
        Log.d(TAG, "Target language: $targetLanguage")
        Log.d(TAG, "API key length: ${apiKey.length}")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
        Log.d(TAG, "WebSocket connection initiated")

        // Start setup timeout (10 seconds)
        setupTimeoutJob = connectionScope?.launch {
            delay(10000) // 10 seconds timeout
            if (!setupMessageSent || _connectionState.value == ConnectionState.Connecting) {
                Log.e(TAG, "Setup timeout: No response from server after 10 seconds")
                _connectionState.value = ConnectionState.Error("Connection timeout: Server did not respond to setup message")
                webSocket?.close(1000, "Setup timeout")
            }
        }
    }

    override fun disconnect() {
        setupTimeoutJob?.cancel()
        setupTimeoutJob = null
        connectionScope?.cancel()
        connectionScope = null
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        setupMessageSent = false
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sendAudio(pcmData: ByteArray) {
        if (_connectionState.value != ConnectionState.Connected) return

        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Audio)
                    })
                })
            })
        }.toString()

        webSocket?.send(message)
    }

    override fun sendText(text: String) {
        if (_connectionState.value != ConnectionState.Connected) return

        val message = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }.toString()

        webSocket?.send(message)
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened: ${response.code}")
                Log.d(TAG, "Response headers: ${response.headers}")
                
                // Send setup message with current target language
                val setupMsg = buildSetupMessage(currentTargetLanguage)
                Log.d(TAG, "Sending setup message: ${setupMsg.take(200)}...")
                
                try {
                    val sent = webSocket.send(setupMsg)
                    setupMessageSent = true
                    Log.d(TAG, "Setup message sent successfully: $sent")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send setup message: ${e.message}", e)
                    _connectionState.value = ConnectionState.Error("Failed to send setup message: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received TEXT message (len=${text.length}): ${text.take(500)}")
                parseServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                Log.d(TAG, "Received BINARY message (len=${bytes.size}): ${text.take(500)}")
                parseServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                setupTimeoutJob?.cancel()
                _connectionState.value = ConnectionState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                Log.e(TAG, "Response: ${response?.code} - ${response?.message}")
                if (response != null) {
                    try {
                        response.body?.let { body ->
                            val bodyString = body.string()
                            Log.e(TAG, "Response body: $bodyString")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to read response body: ${e.message}")
                    }
                }
                setupTimeoutJob?.cancel()
                _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
            }
        }
    }

    private fun parseServerMessage(text: String) {
        try {
            Log.d(TAG, "Parsing server message: ${text.take(1000)}")
            val json = JSONObject(text)

            // Check for setup complete
            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete received!")
                setupTimeoutJob?.cancel()
                _connectionState.value = ConnectionState.Connected
                _serverEvents.trySend(ServerEvent.SetupComplete)
                return
            }

            // Check for error responses
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMessage = error.optString("message", "Unknown error")
                val errorCode = error.optInt("code", -1)
                Log.e(TAG, "Server error: $errorCode - $errorMessage")
                setupTimeoutJob?.cancel()
                _connectionState.value = ConnectionState.Error("Server error: $errorMessage")
                return
            }

            // Check for server content
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Check for turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    _serverEvents.trySend(ServerEvent.TurnComplete)
                    return
                }

                // Check for interrupted
                if (serverContent.optBoolean("interrupted", false)) {
                    _serverEvents.trySend(ServerEvent.Interrupted)
                    return
                }

                // Check for model turn with audio
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                if (mimeType.contains("audio")) {
                                    val base64Data = inlineData.getString("data")
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    _serverEvents.trySend(ServerEvent.AudioData(audioBytes))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GeminiLive"
        private const val MODEL_ID = "gemini-2.5-flash-native-audio-preview-12-2025"
    }
}

