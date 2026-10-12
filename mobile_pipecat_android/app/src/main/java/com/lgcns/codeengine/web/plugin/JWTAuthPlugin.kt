package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject

class JWTAuthPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_LOGIN -> login(requestId, params)
            ACTION_LOGOUT -> logout(requestId)
            ACTION_GET_TOKEN -> getToken(requestId)
            ACTION_UPDATE_TOKEN -> updateToken(requestId, params)
            else -> sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
        }
    }

    private fun login(requestId: String, params: JSONObject) {
        context.activity.login(requestId, Auth(params.optString("id"), params.optString("password")))
    }

    private fun logout(requestId: String) {
        context.activity.logout(requestId)
    }

    private fun getToken(requestId: String) {
        context.activity.getToken(requestId)
    }

    private fun updateToken(requestId: String, params: JSONObject) {
        context.activity.updateToken(requestId, params.optString("token"))
    }

    companion object {
        const val SERVICE_NAME = "JWTAuth"

        private const val ACTION_LOGIN = "login"
        private const val ACTION_LOGOUT = "logout"
        private const val ACTION_GET_TOKEN = "getToken"
        private const val ACTION_UPDATE_TOKEN = "updateToken"
    }
}