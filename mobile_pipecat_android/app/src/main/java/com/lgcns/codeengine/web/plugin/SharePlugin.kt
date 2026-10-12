package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber

class SharePlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val params by lazy { JSONObject(jsCommand.params) }
        val requestId = jsCommand.requestId

        when (jsCommand.action) {
            ACTION_SEND_TEXT -> sendText(requestId, params)
            ACTION_SEND_IMAGE -> sendImage(requestId, params)
            else -> sendJavaScript.error(
                requestId, code = "INVALID_ACTION",
                message = "Invalid action. [${jsCommand.action}]"
            )
        }
    }

    private fun sendText(reqId: String, params: JSONObject) {
        context.activity.shareRequest(
            params.optString("title"),
            params.optString("text"),
            params.optString("chooserTitle"),
            onResult = {
                sendJavaScript.success(reqId)
            }
        )
    }

    private fun sendImage(reqId: String, params: JSONObject) {
        Timber.d("sendImage()")
        val imageUrl = params.optString("imageUrl")
        if (imageUrl.isNullOrEmpty()) {
            sendJavaScript(reqId, RETURN_ERROR, JSONObject())
            return
        }

        context.activity.shareImageRequest(
            imageUrl = params.optString("imageUrl"),
            chooserTitle = params.optString("chooserTitle"),
            onResult = {
                Timber.d("shareImageRequest > onResult it=$it")
                val returnCode = if (it) RETURN_OK else RETURN_ERROR
                sendJavaScript(reqId, returnCode, JSONObject())
            }
        )
    }

    companion object {
        const val SERVICE_NAME = "Share"

        private const val ACTION_SEND_TEXT = "sendText"
        private const val ACTION_SEND_IMAGE = "sendImage"
    }
}