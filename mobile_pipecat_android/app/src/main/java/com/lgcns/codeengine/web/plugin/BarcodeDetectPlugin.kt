package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import kotlinx.coroutines.launch
import org.json.JSONObject

class BarcodeDetectPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_SCAN -> scan(requestId)
            else -> sendJavaScript(requestId, RETURN_ERROR, JSONObject())
        }
    }

    private fun scan(requestId: String) {
        context.scope.launch {
            if (!context.activity.pluginPermissions.cameraPermission.grant()) {
                sendJavaScript.error(
                    requestId, code = "PERMISSION_ERROR",
                    message = "Denied permission to access camera."
                )
                return@launch
            }
            context.activity.scanBarcode(requestId)
        }
    }

    companion object {
        const val SERVICE_NAME = "BarcodeDetect"

        private const val ACTION_SCAN = "scan"
    }
}