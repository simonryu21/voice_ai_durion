package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand

class FingerprintPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val reqId = jsCommand.requestId

        when (jsCommand.action) {
            ACTION_SHOW_FINGERPRINT_PROMPT -> showFingerprintPrompt(reqId)
            ACTION_CANCEL_FINGERPRINT_PROMPT -> cancelFingerprintPrompt(reqId)
            else -> sendJavaScript.error(
                reqId, code = "INVALID_ACTION",
                message = "Invalid action. [${jsCommand.action}]"
            )
        }
    }

    private fun showFingerprintPrompt(requestId: String) {
        context.activity.showBiometricPrompt(requestId)
    }

    private fun cancelFingerprintPrompt(requestId: String) {
        context.activity.cancelBiometricPrompt(requestId)
    }

    companion object {
        const val SERVICE_NAME = "Fingerprint"

        private const val ACTION_SHOW_FINGERPRINT_PROMPT = "showFingerprintPrompt"
        private const val ACTION_CANCEL_FINGERPRINT_PROMPT = "cancelFingerprintPrompt"
    }
}