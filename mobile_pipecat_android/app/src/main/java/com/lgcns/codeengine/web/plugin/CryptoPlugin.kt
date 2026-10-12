package com.lgcns.codeengine.web.plugin

import com.lgcns.codeengine.util.AESEncryptor
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber

class CryptoPlugin (
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val params by lazy { JSONObject(jsCommand.params) }
        val reqId = jsCommand.requestId

        when (jsCommand.action) {
            ACTION_ENCRYPT -> encryptAES(reqId, params)
            ACTION_DECRYPT -> decryptAES(reqId, params)
            else -> sendJavaScript.error(
                reqId, code = INVALID_ACTION,
                message = "Invalid action. [${jsCommand.action}]"
            )
        }
    }

    private fun encryptAES(requestId: String, params: JSONObject) {
        val plainText: String = params.optString("PlainText")
        if (plainText.isEmpty()) {
            sendJavaScript.error(requestId, "INVALID_PARAM", "입력 값이 없습니다.")
            return
        }
        try {
            val dataString: String? = AESEncryptor.encrypt(plainText)
            if (dataString.isNullOrEmpty()) {
                sendJavaScript.error(requestId, "ENCRYPT_FAIL", "암호화에 실패했습니다.")
            } else {
                val resultObj = JSONObject()
                resultObj.put("CipherText", dataString)
                Timber.d("plainText=[${plainText}] cipherText=[$dataString]")
                sendJavaScript.success(requestId, resultObj)
            }
        } catch (e: Exception) {
            sendJavaScript.error(requestId, "ENCRYPT_FAIL", "암호화에 실패했습니다.")
        }
    }

    private fun decryptAES(requestId: String, params: JSONObject) {
        var cipherText = params.optString("CipherText")

        if (cipherText.isNullOrEmpty()) {
            sendJavaScript.error(requestId, "INVALID_PARAM", "입력 값이 없습니다.")
            return
        }

        try {
            if (cipherText.contains("?tTime")) {
                cipherText = cipherText.replace("?tTime", "")
            }
            val dataString = AESEncryptor.decrypt(cipherText)
            if (dataString.isNullOrEmpty()) {
                sendJavaScript.error(requestId, "DECRYPT_FAIL", "복호화에 실패했습니다.")
                return
            }
            Timber.d("decryptAES() : cipherText=[${cipherText}], dataString=[${dataString}]")
            // 복호화 성공
            val resultObj = JSONObject()
            resultObj.put("PlainText", dataString)
            sendJavaScript.success(requestId, resultObj)
        } catch (e: Exception) {
            sendJavaScript.error(requestId, "DECRYPT_FAIL", "복호화에 실패했습니다.")
        }
    }

    companion object {
        private const val TAG = "CryptoPlugin"

        const val SERVICE_NAME = "Crypto"

        private const val ACTION_ENCRYPT = "encryptAES"
        private const val ACTION_DECRYPT = "decryptAES"
    }
}