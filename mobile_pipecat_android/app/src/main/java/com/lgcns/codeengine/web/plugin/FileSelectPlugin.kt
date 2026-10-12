package com.lgcns.codeengine.web.plugin

import android.app.Activity
import android.net.Uri
import android.util.Base64
import com.lgcns.codeengine.util.extension.toStringList
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream

class FileSelectPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        val params by lazy { JSONObject(jsCommand.params) }
        val requestId = jsCommand.requestId

        when (jsCommand.action) {
            ACTION_SELECT_WITH_MIME -> selectWithMimeTypes(requestId, params)
            else -> sendJavaScript.error(
                requestId, code = "INVALID_ACTION",
                message = "Invalid action. [${jsCommand.action}]"
            )
        }
    }

    private fun selectWithMimeTypes(reqId: String, params: JSONObject) {
        context.activity.selectFile(
            params.getJSONArray("mimeTypes").toStringList(),
            params.optBoolean("multiple"),
            onResult = {
                if (it == null) return@selectFile

                if (it.data != null) {
                    val resultStream: InputStream? = it.data?.let { uri ->
                        Timber.d("uri=$uri")
                        context.activity.contentResolver.openInputStream(uri)
                    }
                    val resultData: ByteArray? = resultStream?.readBytes()

                    sendJavaScript(reqId, RETURN_OK, JSONObject()
                        .put("file", Base64.encodeToString(resultData, Base64.DEFAULT)))

                } else {
                    if (it.clipData != null) {
                        val jsonObject = JSONObject()
                        val size = it.clipData?.itemCount ?: 0
                        for (i in 0 until size) {
                            val resultStream: InputStream? = it.clipData?.getItemAt(i)?.uri?.let { uri ->
                                Timber.d("add uri: $uri")
                                context.activity.contentResolver.openInputStream(uri)
                            }
                            val resultData: ByteArray? = resultStream?.readBytes()

                            jsonObject.put("file_${i+1}", Base64.encodeToString(resultData, Base64.DEFAULT))
                        }

                        sendJavaScript(reqId, RETURN_OK, jsonObject)
                    } else {
                        Timber.e("Unhandled case.")
                    }
                }
            }
        )
    }

    companion object {
        const val SERVICE_NAME = "FileSelect"

        private const val ACTION_SELECT_WITH_MIME = "selectWithMimeTypes"
    }
}