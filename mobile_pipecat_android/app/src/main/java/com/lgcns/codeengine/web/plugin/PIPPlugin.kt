package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.activity.PIPWebViewActivity
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import org.json.JSONObject
import timber.log.Timber

class PIPPlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val params by lazy { JSONObject(jsCommand.params) }

        when (jsCommand.action) {
            ACTION_LAUNCH_PIP_WINDOW ->
                launchPIPWindow(jsCommand.requestId, params)
            ACTION_MINIMIZE ->
                minimize()
            ACTION_CLOSE ->
                close()
            else -> {
                val result = JSONObject()
                    .put(PARAM_ERROR_CODE, "INVALID_PARAMS")
                    .put(PARAM_ERROR_MESSAGE, "Unsupported action. [${jsCommand.action}]")
                sendJavaScript(jsCommand.requestId, RETURN_ERROR, result)
            }
        }
    }

    private fun launchPIPWindow(requestId: String, params: JSONObject) {
        val url = params.optString(NavigatorPlugin.URL)
        val data = params.optString(NavigatorPlugin.DATA)
        val showCloseButton = params.optBoolean(NavigatorPlugin.PARAM_SHOW_CLOSE_BUTTON)
        Timber.d("launchPIPWindow url=$url, data=$data, showCloseButton=$showCloseButton")

        context.activity.launchPIPWindow(requestId, url, data, showCloseButton)

        //@Note BaseWebViewActivity의 navigatorLauncher의 result 받는 부분에서 Web App으로 보냅니다.
        //sendJavaScript.success(requestId)
    }

    private fun minimize() {
        if (context.activity is PIPWebViewActivity)
            (context.activity as PIPWebViewActivity).minimize()
        else
            Timber.e("This is not PIPWebViewActivity.")
    }

    private fun close() {
        context.activity.goPrevPageWithData(JSONObject())
    }

    companion object {
        const val SERVICE_NAME = "PIP"

        private const val ACTION_LAUNCH_PIP_WINDOW = "launchPIPWindow"
        private const val ACTION_MINIMIZE = "minimize"
        private const val ACTION_CLOSE = "close"
    }
}