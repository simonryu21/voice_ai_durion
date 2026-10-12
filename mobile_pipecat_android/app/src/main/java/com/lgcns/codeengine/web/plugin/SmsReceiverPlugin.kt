package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand

class SmsReceiverPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        when (jsCommand.action) {
            ACTION_REGISTER -> register(requestId)
            ACTION_UNREGISTER -> unregister(requestId)
            else -> sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
        }
    }

    private fun register(requestId: String) {
        context.activity.registerSmsReceiver()
        sendJavaScript.success(requestId)
    }

    private fun unregister(requestId: String) {
        context.activity.unregisterSmsReceiver()
        sendJavaScript.success(requestId)
    }

    companion object {
        const val SERVICE_NAME = "SmsReceiver"

        private const val ACTION_REGISTER = "register"
        private const val ACTION_UNREGISTER = "unregister"
    }
}