package com.lgcns.codeengine.web.permission

interface FeaturePermissionUiInteraction {
    fun confirmRequestPermissionRationale(message: CharSequence, onConfirmed: (Boolean) -> Unit)
    fun confirmGoAppDetailsSettings(message: CharSequence, onConfirmed: (Boolean) -> Unit)
}