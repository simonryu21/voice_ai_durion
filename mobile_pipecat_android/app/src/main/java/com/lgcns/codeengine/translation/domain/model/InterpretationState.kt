package com.lgcns.codeengine.translation.domain.model

sealed class InterpretationState {
    data object Idle : InterpretationState()
    data object Connecting : InterpretationState()
    data object Active : InterpretationState()
    data object HeadsetRequired : InterpretationState()
    data class Error(val message: String) : InterpretationState()
}

