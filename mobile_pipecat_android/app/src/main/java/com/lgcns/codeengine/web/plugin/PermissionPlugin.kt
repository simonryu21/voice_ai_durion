package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.*
import com.lgcns.codeengine.web.contract.GoAppDetailsSettings
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class PermissionPlugin @Inject constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        when (jsCommand.action) {
            ACTION_GET_PERMISSIONS ->
                getPermissions(jsCommand.requestId)
            ACTION_GO_APP_SETTINGS ->
                goAppSettings(jsCommand.requestId)
            else -> {
                val result = JSONObject()
                    .put(PARAM_ERROR_CODE, "INVALID_PARAMS")
                    .put(PARAM_ERROR_MESSAGE, "Unsupported action. [${jsCommand.action}]")
                sendJavaScript(jsCommand.requestId, RETURN_ERROR, result)
            }
        }
    }

    private fun goAppSettings(requestId: String) {
        context.activity.goAppSettings()

        sendJavaScript.success(requestId)
    }

    private fun getPermissions(requestId: String) {
        val permissions = context.activity.getPermissions()

        val resultObj = JSONObject()
        resultObj.put("permissions", permissions)

        sendJavaScript.success(requestId, resultObj)
    }

    companion object {
        const val SERVICE_NAME = "Permission"

        private const val ACTION_GET_PERMISSIONS = "getPermissions"

        private const val ACTION_GO_APP_SETTINGS = "goAppSettings"
    }
}