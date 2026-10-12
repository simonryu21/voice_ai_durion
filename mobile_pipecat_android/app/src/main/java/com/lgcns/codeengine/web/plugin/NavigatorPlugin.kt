package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.activity.BaseWebViewActivity
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.GET
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class NavigatorPlugin @Inject constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params by lazy { JSONObject(jsCommand.params) }
        when (jsCommand.action) {
            ACTION_GO_PREV_PAGE_WITH_DATA -> goPrevPageWithData(requestId, params)
            ACTION_LOAD_URL -> loadUrl(requestId, params)
            ACTION_MOVE_WITH_URL -> moveWithUrl(requestId, params)
            ACTION_OPEN_WITH_BROWSER -> openWithBrowser(requestId, params)
            ACTION_APP_CLOSE -> closeApp()

            else -> sendJavaScript.error(
                requestId, code = "INVALID_ACTION",
                message = "Invalid action. (${jsCommand.action})"
            )
        }
    }

    private fun loadUrl(requestId: String, params: JSONObject) {
        val url = params.optString(URL)
        Timber.d("loadUrl url=$url")

        context.activity.loadUrl(url, GET)

        sendJavaScript.success(requestId)
    }

    private fun moveWithUrl(requestId: String, params: JSONObject) {
        val url = params.optString(URL)
        val data = params.optString(DATA)
        val showCloseButton = params.optBoolean(PARAM_SHOW_CLOSE_BUTTON)
        Timber.d("moveWithUrl url=$url, data=$data, showCloseButton=$showCloseButton")

        context.activity.moveWithUrl(requestId, url, data, showCloseButton)

        //@Note BaseWebViewActivity의 navigatorLauncher의 result 받는 부분에서 Web App으로 보냅니다.
        //sendJavaScript.success(requestId)
    }

    private fun openWithBrowser(requestId: String, params: JSONObject) {
        val url = params.optString(URL)
        Timber.d("openWithBrowser url=$url")

        context.activity.openWithBrowser(url)

        sendJavaScript.success(requestId)
    }

    private fun goPrevPageWithData(requestId: String, params: JSONObject) {
        Timber.d("goPrevPageWithData")
        context.activity.goPrevPageWithData(params)

        sendJavaScript.success(requestId)
    }

    private fun closeApp() {
        context.activity.finishAffinity()
    }

    companion object {
        const val SERVICE_NAME = "Navigator"

        private const val ACTION_GO_PREV_PAGE_WITH_DATA = "goPrevPageWithData"
        private const val ACTION_LOAD_URL = "loadUrl"
        private const val ACTION_MOVE_WITH_URL = "moveWithUrl"
        private const val ACTION_OPEN_WITH_BROWSER = "openWithBrowser"
        private const val ACTION_APP_CLOSE = "closeApp"

        const val URL = "url"
        const val DATA = "data"
        const val PARAM_SHOW_CLOSE_BUTTON = "showCloseButton"
    }
}