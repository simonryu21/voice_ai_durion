package com.lgcns.codeengine.translation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lgcns.codeengine.translation.data.audio.HeadsetManager
import com.lgcns.codeengine.translation.domain.model.HeadsetState
import com.lgcns.codeengine.translation.domain.model.InterpretationState
import com.lgcns.codeengine.translation.domain.model.Language
import com.lgcns.codeengine.translation.domain.usecase.InterpretationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class GeminiLiveTranslationUiState(
    val interpretationState: InterpretationState = InterpretationState.Idle,
    val headsetState: HeadsetState = HeadsetState.Disconnected,
    val isStartEnabled: Boolean = false,
    val showHeadsetWarning: Boolean = false,
    val hasPermissions: Boolean = false,
    val targetLanguage: Language = Language.SUPPORTED_OUTPUT_LANGUAGES.first(),
    val inputLevel: Float = 0f,
    /** Smoothed round-trip latency in ms. null when no measurement is available. */
    val latencyMs: Int? = null,
)

class GeminiLiveTranslationViewModel(
    private val interpretationUseCase: InterpretationUseCase,
    private val headsetManager: HeadsetManager
) : ViewModel() {

    private val _hasPermissions = MutableStateFlow(false)
    private val _targetLanguage = MutableStateFlow(Language.SUPPORTED_OUTPUT_LANGUAGES.first())

    val uiState: StateFlow<GeminiLiveTranslationUiState> = combine(
        interpretationUseCase.state,
        headsetManager.observeHeadsetState(),
        _hasPermissions,
        _targetLanguage
    ) { interpretationState, headsetState, hasPermissions, targetLanguage ->
        GeminiLiveTranslationUiState(
            interpretationState = interpretationState,
            headsetState = headsetState,
            isStartEnabled = headsetState.isConnected &&
                    hasPermissions &&
                    interpretationState is InterpretationState.Idle,
            showHeadsetWarning = !headsetState.isConnected && hasPermissions,
            hasPermissions = hasPermissions,
            targetLanguage = targetLanguage
        )
    }.combine(
        interpretationUseCase.audioAmplitude.onStart { emit(0f) }
    ) { state, inputLevel ->
        state.copy(inputLevel = inputLevel)
    }.combine(
        interpretationUseCase.latencyMs.onStart { emit(null) }
    ) { state, latencyMs ->
        state.copy(latencyMs = latencyMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GeminiLiveTranslationUiState()
    )

    fun onPermissionsGranted() {
        _hasPermissions.value = true
    }

    fun onPermissionsDenied() {
        _hasPermissions.value = false
    }

    fun setTargetLanguage(language: Language) {
        val wasActive = interpretationUseCase.state.value is InterpretationState.Active ||
                interpretationUseCase.state.value is InterpretationState.Connecting

        _targetLanguage.value = language

        // If session is active, restart with new language
        if (wasActive) {
            interpretationUseCase.stop()
            interpretationUseCase.start(language.name)
        }
    }

    fun startInterpretation() {
        interpretationUseCase.start(_targetLanguage.value.name)
    }

    fun stopInterpretation() {
        interpretationUseCase.stop()
    }

    /**
     * Send a typed AskBar message to the interpreter. Starts a session with
     * the current target language if one is not already running.
     */
    fun sendTextInput(text: String) {
        interpretationUseCase.sendText(text, _targetLanguage.value.name)
    }

    override fun onCleared() {
        super.onCleared()
        interpretationUseCase.stop()
    }
}

