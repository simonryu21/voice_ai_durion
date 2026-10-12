package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import org.json.JSONObject

class ProgressBarPlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val params by lazy { JSONObject(jsCommand.params) }

        when (jsCommand.action) {
            ACTION_SET_PROGRESS ->
                setProgress(jsCommand.requestId, params)
            else -> {
                val result = JSONObject()
                    .put(PARAM_ERROR_CODE, "INVALID_PARAMS")
                    .put(PARAM_ERROR_MESSAGE, "Unsupported action. [${jsCommand.action}]")
                sendJavaScript(jsCommand.requestId, RETURN_ERROR, result)
            }
        }
    }

    private fun setProgress(requestId: String, params: JSONObject) {
        context.activity.setProgress(params.optBoolean("isLoading"))

        sendJavaScript.success(requestId)
    }

    companion object {
        const val SERVICE_NAME = "ProgressBar"

        private const val ACTION_SET_PROGRESS = "setProgress"
    }
}