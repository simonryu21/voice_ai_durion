package com.lgcns.codeengine.web.plugin

import android.graphics.BitmapFactory
import android.util.Base64
import com.lgcns.codeengine.util.extension.compressJpeg
import com.lgcns.codeengine.util.extension.makeThumbnail
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import kotlinx.coroutines.*
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.InputStream


class ImageEditPlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        val requestId = jsCommand.requestId
        val params = JSONObject(jsCommand.params)

        when (jsCommand.action) {
            ACTION_EDIT_FROM_CAMERA -> editImage(requestId, ACTION_EDIT_FROM_CAMERA, params)
            ACTION_EDIT_FROM_GALLERY -> editImage(requestId, ACTION_EDIT_FROM_GALLERY, params)
            else -> sendJavaScript.error(
                requestId, code = "INVALID_ACTION",
                message = "Unsupported action. [${jsCommand.action}]"
            )
        }
    }

    private fun editImage(requestId: String, action: String, params: JSONObject) {
        context.scope.launch {
            if (!context.activity.pluginPermissions.cameraPermission.grant()) {
                sendJavaScript.error(
                    requestId, code = "PERMISSION_ERROR",
                    message = "Denied permission to access camera."
                )
                return@launch
            }

            context.activity.viewImageEdit(action, params.optInt("maxSize")) {
                if (it == null) {
                    Timber.e("Uri is null.")
                    return@viewImageEdit
                }

                val resultStream: InputStream? = context.activity.contentResolver.openInputStream(it)
                val resultData: ByteArray? = resultStream?.readBytes()

                if (resultData == null || resultData.isEmpty()) {
                    Timber.e("result is empty.")
                    return@viewImageEdit
                }

                val thumbnailData: ByteArray = BitmapFactory.decodeByteArray(resultData, 0, resultData.size).makeThumbnail(true).compressJpeg(100,true) ?: ByteArray(0)

                resultStream.close()

                val file = File(it.path!!)
                if(file.delete()) Timber.d("Temp file is deleted.")

                sendJavaScript(requestId, RETURN_OK, JSONObject()
                    .put("file", Base64.encodeToString(resultData, Base64.DEFAULT))
                    .put("fileName", file.name)
                    .put("fileSize", resultData.size)
                    .put("thumbnail", Base64.encodeToString(thumbnailData, Base64.DEFAULT))
                )
            }
        }
    }

    companion object {
        const val SERVICE_NAME = "ImageEdit"

        const val ACTION_EDIT_FROM_CAMERA = "editFromCamera"
        const val ACTION_EDIT_FROM_GALLERY = "editFromGallery"
    }
}