package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.ui.BarcodeType
import com.lgcns.codeengine.ui.QRBarCodeEntity
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import org.json.JSONObject

class BarcodePlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_VIEW_BARCODE -> viewBarcode(requestId, params)
            else -> sendJavaScript(requestId, RETURN_ERROR, JSONObject())
        }
    }

    private fun viewBarcode(requestId: String, params: JSONObject) {
        val type = params.optString("type").toBarcodeType()
            ?: run {
                val result = JSONObject()
                    .put(PARAM_ERROR_CODE, "UNSUPPORTED_FORMAT")
                    .put(PARAM_ERROR_MESSAGE, "Unsupported barcode format.")
                sendJavaScript(requestId, RETURN_ERROR, result)
                return
            }
        val data = params.optString("data")

        val entity = QRBarCodeEntity()
        if (type == BarcodeType.QR_CODE) entity.qrcodeNo = data
        else if (type == BarcodeType.CODE_128) entity.barcodeNo = data

        context.activity.viewBarcode(type, entity) {
            sendJavaScript(requestId, if (it) RETURN_OK else RETURN_ERROR, JSONObject())
        }

    }

    private fun String.toBarcodeType() =
        when (this) {
            "code128" -> BarcodeType.CODE_128
            "qrcode" -> BarcodeType.QR_CODE
            else -> null
        }

    companion object {
        const val SERVICE_NAME = "Barcode"

        private const val ACTION_VIEW_BARCODE = "viewBarcode"
    }
}