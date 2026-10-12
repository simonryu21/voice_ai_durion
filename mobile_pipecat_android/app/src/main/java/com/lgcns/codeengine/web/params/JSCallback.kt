package com.lgcns.codeengine.web.params

import kotlinx.serialization.Serializable

@Serializable
data class JSCallback(
    val requestId: String,
    val resultCode: String,
    val params: String,
)
