package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.ui.WebViewDialogFragment
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber

class ImageViewerPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    private var popupDialog: WebViewDialogFragment? = null

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_OPEN_POPUP -> openPopup(requestId, params)
            ACTION_CLOSE_POPUP -> closePopup(requestId)
            else -> sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
        }
    }

    private fun openPopup(requestId: String, params: JSONObject) {
        val title = params.optString("title")
        val url = params.optString("url")
        Timber.d("openPopup() url =$url")

        popupDialog = WebViewDialogFragment.Builder()
            .setTitle(title)
            .setUrl(url)
            .setPositiveButton(params.optString("confirmText")) {
                sendJavaScript(requestId, RETURN_OK, JSONObject()
                    .put("clicked", "confirm")
                )
            }
            .setNegativeButton(params.optString("cancelText")) {
                sendJavaScript(requestId, RETURN_OK, JSONObject()
                    .put("clicked", "cancel")
                )
            }
            .showCloseButton(params.optBoolean("showCloseButton"))
            .show(context.activity.supportFragmentManager)

        sendJavaScript.success(requestId)
    }

    private fun closePopup(requestId: String) {

        popupDialog?.dismiss()

        sendJavaScript.success(requestId)
    }

    companion object {
        const val SERVICE_NAME = "ImageViewer"

        private const val ACTION_OPEN_POPUP = "openPopup"
        private const val ACTION_CLOSE_POPUP = "closePopup"
    }
}