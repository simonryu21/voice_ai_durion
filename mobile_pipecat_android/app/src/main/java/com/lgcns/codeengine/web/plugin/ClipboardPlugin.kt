package com.lgcns.codeengine.web.plugin

import android.content.ClipData
import android.content.ClipboardManager
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject


class ClipboardPlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_SET_CLIPBOARD_TEXT -> setClipboardText(requestId, params)
            ACTION_GET_CLIPBOARD_TEXT -> getClipboardText(requestId, params)
            else -> sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
        }
    }

    private fun setClipboardText(requestId: String, params: JSONObject) {
        val copyText = params.optString("copyText", "")

        context.activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(
                ClipData.newPlainText("text", copyText)
            )

        sendJavaScript.success(requestId)
    }

    private fun getClipboardText(requestId: String, params: JSONObject) {
        val manager = context.activity.getSystemService(ClipboardManager::class.java)
        val firstItem = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)

        sendJavaScript.success(requestId) {
            put("clipText", firstItem?.text?.toString() ?: "")
        }
    }

    companion object {
        const val SERVICE_NAME = "Clipboard"

        private const val ACTION_SET_CLIPBOARD_TEXT = "setClipboardText"
        private const val ACTION_GET_CLIPBOARD_TEXT = "getClipboardText"
    }
}