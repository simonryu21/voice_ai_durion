package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject

class SMSPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val params by lazy { JSONObject(jsCommand.params) }
        val reqId = jsCommand.requestId

        when (jsCommand.action) {
            ACTION_SEND_SMS_WITH_CAPTURE_IMAGE -> sendSMSWithCaptureImage(reqId, params)
            ACTION_SEND_SMS -> sendSMS(reqId, params)
            else -> sendJavaScript.error(
                reqId, code = "INVALID_ACTION",
                message = "Invalid action. [${jsCommand.action}]"
            )
        }
    }

    private fun sendSMSWithCaptureImage(requestId: String, params: JSONObject) {
        params.put("sendSMSWithCaptureImage() requestId", requestId)
        context.activity.sendSMSWithCaptureImage(params)
    }

    private fun sendSMS(requestId: String, params: JSONObject) {
        params.put("sendSMS() requestId", requestId)
        context.activity.sendSMS(params)
    }

    companion object {
        const val SERVICE_NAME = "SMS"

        private const val ACTION_SEND_SMS_WITH_CAPTURE_IMAGE = "sendSMSWithCaptureImage"
        private const val ACTION_SEND_SMS = "sendSMS"
    }
}