package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.usecase.WebViewPluginUseCase
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class UserPrefPlugin(
    private val context: PluginContext,
) : BasePlugin() {

    private val scope = context.scope
    private val entryPoint = context.useCaseEntryPoint

    private val webViewPluginUseCase: WebViewPluginUseCase =
        entryPoint.provideWebViewPluginUseCase()

    override fun execute(jsCommand: JSCommand) {
        val requestId: String = jsCommand.requestId
        val params = JSONObject(jsCommand.params)
        Timber.d("jsCommand.action=${jsCommand.action}")

        when (jsCommand.action) {
            ACTION_SET_VALUE -> setValue(requestId, params)
            ACTION_GET_VALUE -> getValue(requestId, params)
            ACTION_REMOVE_VALUE -> removeValue(requestId, params)
            else -> context.activity.sendJavaScript(requestId, RETURN_ERROR, JSONObject())
        }
    }

    private fun setValue(requestId: String, params: JSONObject) {

        scope.launch {
            try {
                params.keys().forEach { key ->
                    val value = params.optString(key)
                    webViewPluginUseCase.webDataStoreRepository.setDataStoreString(key, value)
                }
                context.activity.sendJavaScript(requestId, RETURN_OK, JSONObject())
            } catch (ex: Exception) {
                Timber.e("Exception ${(ex.message)}")
                context.activity.sendJavaScript(requestId, RETURN_ERROR, JSONObject())
            }
        }
    }

    private fun getValue(requestId: String, params: JSONObject) {

        scope.launch {
            try {
                val resultObject = JSONObject()
                val keys = params.optJSONArray(DATA_KEYS)?.toStringList() ?: emptyList()
                keys.forEach { key ->
                    val value = webViewPluginUseCase.webDataStoreRepository.getDataStoreString(key) ?: JSONObject.NULL
                    resultObject.put(key, value)
                }
                context.activity.sendJavaScript(requestId, RETURN_OK, resultObject)
            } catch (ex: Exception) {
                Timber.e("Exception ${(ex.message)}")
                context.activity.sendJavaScript(requestId, RETURN_ERROR, JSONObject())
            }
        }
    }

    private fun removeValue(requestId: String, params: JSONObject) {
        scope.launch {
            try {
                val keys = params.optJSONArray(DATA_KEYS)?.toStringList() ?: emptyList()
                keys.forEach { key ->
                    webViewPluginUseCase.webDataStoreRepository.removeDataStoreString(key)
                }
                context.activity.sendJavaScript(requestId, RETURN_OK, JSONObject())
            } catch (ex: Exception) {
                Timber.e("Exception ${(ex.message)}")
                context.activity.sendJavaScript(requestId, RETURN_ERROR, JSONObject())
            }

        }
    }

    private fun JSONArray.toStringList(): List<String> {
        val result = mutableListOf<String>()
        for (index in 0 until length()) {
            optString(index, null)?.run(result::add)
        }
        return result
    }

    companion object {
        const val SERVICE_NAME: String = "UserPref"

        const val ACTION_SET_VALUE = "setValue"
        const val ACTION_GET_VALUE = "getValue"
        const val ACTION_REMOVE_VALUE = "removeValue"

        const val DATA_KEYS: String = "keys"
    }
}