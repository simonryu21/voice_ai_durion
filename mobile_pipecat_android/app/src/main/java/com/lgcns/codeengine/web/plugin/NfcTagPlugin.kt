package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class NfcTagPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        when (jsCommand.action) {
            ACTION_READ -> read(requestId)
            else -> sendJavaScript(requestId, RETURN_ERROR, JSONObject())
        }
    }

    private fun read(requestId: String) {
        Timber.d("NfcTagPlugin.read()")
        context.scope.launch {
            if (!context.activity.pluginPermissions.nfcPermission.grant()) {
                sendJavaScript.error(
                    requestId, code = "PERMISSION_ERROR",
                    message = "Denied permission to access camera."
                )
                return@launch
            }
            context.activity.readNfcTag(requestId)
        }
    }

    companion object {
        const val SERVICE_NAME = "NfcTag"

        private const val ACTION_READ = "read"
    }
}