package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject

class ToastPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_MAKE_TOAST -> makeToast(requestId, params)
            else -> sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
        }
    }

    private fun makeToast(requestId: String, params: JSONObject) {
        showToastMessage(params)

        sendJavaScript.success(requestId)
    }

    private fun showToastMessage(param: JSONObject) {
        val message = param.optString("message")
        val bottomOffsetInDp = param.optInt("bottomOffset", 0).takeIf { it > 0 }
        context.activity.showToast(message, bottomOffsetInDp)
    }

    companion object {
        const val SERVICE_NAME = "Toast"

        private const val ACTION_MAKE_TOAST = "makeToast"
    }
}