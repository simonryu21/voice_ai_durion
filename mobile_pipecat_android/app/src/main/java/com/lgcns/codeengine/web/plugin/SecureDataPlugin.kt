package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.data.exception.SecureDataError
import com.lgcns.codeengine.data.exception.SecureDataException
import com.lgcns.codeengine.util.extension.optStringList
import com.lgcns.codeengine.util.extension.putString
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.usecase.WebViewPluginUseCase
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class SecureDataPlugin (
    private val context: PluginContext,
    ) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    private val webViewPluginUseCase: WebViewPluginUseCase =
        context.useCaseEntryPoint.provideWebViewPluginUseCase()

    override fun execute(jsCommand: JSCommand) {
        val params = JSONObject(jsCommand.params)
        val requestId = jsCommand.requestId

        when (jsCommand.action) {
            ACTION_ENCRYPT_SPLIT -> encryptSplit(requestId, params)
            ACTION_DECRYPT_JOIN -> decryptJoin(requestId, params)
            ACTION_REMOVE -> remove(requestId, params)
            ACTION_CONTAINS -> contains(requestId, params)
            ACTION_SET_VALUE -> setValueWithEncryption(requestId, params)
            ACTION_GET_VALUE -> getValueWithDecryption(requestId, params)
            else -> sendJavaScript.error(
                requestId, code = "INVALID_ACTION",
                message = "Invalid action. (${jsCommand.action})"
            )
        }
    }

    private fun encryptSplit(requestId: String, params: JSONObject) {
        val dataKey = params.optString("dataKey")
        val plainText = params.optString("plainText")
        if (dataKey.isEmpty() || plainText.isEmpty()) {
            sendJavaScript.error(
                requestId, code = "INVALID_PARAMS",
                message = "Required dataKey and plainText string value."
            )
            return
        }
        context.scope.launch {
            try {
                val halfCipherText = webViewPluginUseCase.secureDataStoreRepository.encryptSplit(dataKey, plainText)
                sendJavaScript.success(requestId) {
                    put("halfCipherText", halfCipherText)
                }
            } catch (e: SecureDataException) {
                val (code, message) = when (e.error) {
                    SecureDataError.UNSUPPORTED -> "UNSUPPORTED" to "Unsupported environment. ${e.message}"
                    SecureDataError.INVALID_STATE -> "INVALID_STATE" to "Invalid state. ${e.message}"
                    SecureDataError.INVALID_DATA -> "INVALID_DATA" to "Invalid input data. ${e.message}"
                    SecureDataError.UNKNOWN -> "UNKNOWN" to "Unknown error. ${e.message}"
                }
                Timber.e("Failed to encrypt split. ${e.message}")
                sendJavaScript.error(requestId, code = code, message = message)
            }
        }
    }

    private fun decryptJoin(requestId: String, params: JSONObject) {
        val dataKey = params.optString("dataKey")
        val halfCipherText = params.optString("halfCipherText")
        if (dataKey.isEmpty() || halfCipherText.isEmpty()) {
            sendJavaScript.error(
                requestId, code = "INVALID_PARAMS",
                message = "Required dataKey and halfCipherText string value."
            )
            return
        }
        context.scope.launch {
            try {
                val plainText = webViewPluginUseCase.secureDataStoreRepository.decryptJoin(dataKey, halfCipherText)
                sendJavaScript.success(requestId) {
                    putString("plainText", plainText)
                }
            } catch (e: SecureDataException) {
                val (code, message) = when (e.error) {
                    SecureDataError.UNSUPPORTED -> "UNSUPPORTED" to "Unsupported environment. ${e.message}"
                    SecureDataError.INVALID_STATE -> "INVALID_STATE" to "Invalid state. ${e.message}"
                    SecureDataError.INVALID_DATA -> "INVALID_DATA" to "Invalid input data. ${e.message}"
                    SecureDataError.UNKNOWN -> "UNKNOWN" to "Unknown error. ${e.message}"
                }
                Timber.e("Failed to decrypt join. ${e.message}")
                sendJavaScript.error(requestId, code = code, message = message)
            }
        }
    }

    private fun remove(requestId: String, params: JSONObject) {
        val dataKeys = params.optStringList("dataKeys")
        if (dataKeys.isEmpty()) {
            sendJavaScript.error(
                requestId, code = "INVALID_PARAMS",
                message = "Required dataKeys."
            )
            return
        }
        context.scope.launch {
            val removeResult = webViewPluginUseCase.secureDataStoreRepository.remove(dataKeys)
            sendJavaScript.success(requestId) {
                removeResult.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
    }

    private fun contains(requestId: String, params: JSONObject) {
        val dataKeys = params.optStringList("dataKeys")
        if (dataKeys.isEmpty()) {
            sendJavaScript.error(
                requestId, code = "INVALID_PARAMS",
                message = "Required dataKeys."
            )
            return
        }
        context.scope.launch {
            val containsResult = webViewPluginUseCase.secureDataStoreRepository.contains(dataKeys)
            sendJavaScript.success(requestId) {
                containsResult.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
    }

    private fun setValueWithEncryption(requestId: String, params: JSONObject) {

        val key = params.optString(KEY)
        val value = params.optString(KEY_VALUE)
        context.scope.launch {
            try {
                webViewPluginUseCase.secureDataStoreRepository.setEncryptedValue(key, value)

                sendJavaScript.success(requestId)
            } catch (e: SecureDataException) {
                val (code, message) = when (e.error) {
                    SecureDataError.UNSUPPORTED -> "UNSUPPORTED" to "Unsupported environment. ${e.message}"
                    SecureDataError.INVALID_STATE -> "INVALID_STATE" to "Invalid state. ${e.message}"
                    SecureDataError.INVALID_DATA -> "INVALID_DATA" to "Invalid input data. ${e.message}"
                    SecureDataError.UNKNOWN -> "UNKNOWN" to "Unknown error. ${e.message}"
                }
                Timber.e("Failed to encrypt split. ${e.message}")
                sendJavaScript.error(requestId, code = code, message = message)
            }
        }
    }

    private fun getValueWithDecryption(requestId: String, params: JSONObject) {
        val key = params.optString(KEY)
        context.scope.launch {
            try {
                val value = webViewPluginUseCase.secureDataStoreRepository.getDecryptedValue(key)
                sendJavaScript.success(requestId) {
                    put(KEY_VALUE, value)
                }
            } catch (e: SecureDataException) {
                val (code, message) = when (e.error) {
                    SecureDataError.UNSUPPORTED -> "UNSUPPORTED" to "Unsupported environment. ${e.message}"
                    SecureDataError.INVALID_STATE -> "INVALID_STATE" to "Invalid state. ${e.message}"
                    SecureDataError.INVALID_DATA -> "INVALID_DATA" to "Invalid input data. ${e.message}"
                    SecureDataError.UNKNOWN -> "UNKNOWN" to "Unknown error. ${e.message}"
                }
                Timber.e("Failed to encrypt split. ${e.message}")
                sendJavaScript.error(requestId, code = code, message = message)
            }
        }
    }

    companion object {
        private const val TAG = "SecureDataPlugin"

        const val SERVICE_NAME = "SecureData"

        private const val ACTION_ENCRYPT_SPLIT = "encryptSplit"
        private const val ACTION_DECRYPT_JOIN = "decryptJoin"
        private const val ACTION_REMOVE = "remove"
        private const val ACTION_CONTAINS = "contains"

        private const val ACTION_SET_VALUE = "setValue"
        private const val ACTION_GET_VALUE = "getValue"

        private const val SIMPLE_TRANSFER_ID = "simpleTransferId"
        private const val KEY = "key"
        private const val KEY_VALUE = "value"

        private const val ALIAS_FORCE = "shinhanforce"
    }
}