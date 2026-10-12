package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject

class KeyboardPlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)

        when (jsCommand.action) {
            ACTION_SHOW_SOFT_KEYBOARD -> showSoftKeyboard(requestId)
            ACTION_HIDE_SOFT_KEYBOARD -> hideSoftKeyboard(requestId)
            else -> sendJavaScript.error(
                requestId, code = INVALID_ACTION,
                message = "Invalid action. [${jsCommand.action}]"
            )
        }
    }

    private fun showSoftKeyboard(requestId: String) {
        context.activity.setWebViewKeyboardVisibility(true)

        sendJavaScript.success(requestId)
    }

    private fun hideSoftKeyboard(requestId: String) {
        context.activity.setWebViewKeyboardVisibility(false)

        sendJavaScript.success(requestId)
    }

    companion object {
        const val SERVICE_NAME = "Keyboard"

        private const val ACTION_SHOW_SOFT_KEYBOARD = "showSoftKeyboard"
        private const val ACTION_HIDE_SOFT_KEYBOARD = "hideSoftKeyboard"
    }
}