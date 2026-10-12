package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.util.extension.optStringList
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber

class WebViewDataPlugin(
    private val context: PluginContext
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId: String = jsCommand.requestId
        val params = JSONObject(jsCommand.params)

        Timber.d("execute requestId:$requestId, params:$params")

        when (jsCommand.action) {
            ACTION_GET_DATA -> getJsonData(requestId)
            ACTION_SET_DATA -> setJsonData(requestId, params)
            ACTION_SET_VALUE -> setValue(requestId, params)
            ACTION_GET_VALUE -> getValue(requestId, params)
        }
    }

    private fun getJsonData(requestId: String) {
        val jsonData = context.activity.getWebJsonData()
        Timber.d("fetchData data:$jsonData")
        sendJavaScript.success(requestId, jsonData)
    }

    private fun setJsonData(requestId: String, params: JSONObject) {

        context.activity.setWebJsonData(params)

        sendJavaScript.success(requestId)
    }

    private fun setValue(requestId: String, params: JSONObject) {
        val records = mutableMapOf<String, String?>()

        params.keys().forEach { key -> records.put(key, params.optStringNullable(key)) }

        context.activity.setWebValue(records)

        sendJavaScript.success(requestId)
    }

    private fun getValue(requestId: String, params: JSONObject) {

        val keys = params.optStringList("keys")
        var values : Map<String, String?> = context.activity.getWebValue(keys)
        sendJavaScript.success(requestId) {
            values.forEach { (key, value) ->
                put(key, value ?: JSONObject.NULL)
            }
        }

    }

    private fun JSONObject.optStringNullable(name: String): String? =
        when (val value = opt(name)) {
            null -> null
            is String -> value
            else -> value.toString()
        }

    companion object {
        private const val TAG = "WebViewDataPlugin"
        const val SERVICE_NAME = "WebViewData"

        private const val ACTION_GET_DATA = "getJsonData"
        private const val ACTION_SET_DATA = "setJsonData"
        private const val ACTION_SET_VALUE = "setValue"
        private const val ACTION_GET_VALUE = "getValue"
    }
}