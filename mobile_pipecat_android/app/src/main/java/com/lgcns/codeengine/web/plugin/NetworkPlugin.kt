package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand

class NetworkPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        when (jsCommand.action) {
            ACTION_GET_STATUS -> getStatus(requestId)
            else -> sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
        }
    }

    private fun getStatus(requestId: String) {
        val isConnected = context.activity.isNetworkAvailable(context.activity)
        val result = if (isConnected) "connected" else "disconnected"
        sendJavaScript.success(requestId) {
            put("status", result)
        }
    }

    companion object {
        const val SERVICE_NAME = "Network"

        private const val ACTION_GET_STATUS = "getStatus"
    }
}