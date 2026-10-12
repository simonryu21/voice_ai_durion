package com.lgcns.codeengine.web.params

import kotlinx.serialization.Serializable

@Serializable
data class JSEventCallback(
    val eventName: String,
    val eventType: String,
    val params: String,
)