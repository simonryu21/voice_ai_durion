package com.lgcns.codeengine.web.plugin

import android.webkit.CookieManager
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CachePlugin constructor(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript
    
    override fun execute(jsCommand: JSCommand) {
        when (jsCommand.action) {
            ACTION_DELETE_CACHE ->
                deleteCache(jsCommand.requestId, context.json.decodeFromString(jsCommand.params))
            else -> {
                val result = JSONObject()
                    .put(PARAM_ERROR_CODE, "INVALID_PARAMS")
                    .put(PARAM_ERROR_MESSAGE, "Unsupported action. [${jsCommand.action}]")
                sendJavaScript(jsCommand.requestId, RETURN_ERROR, result)
            }
        }
    }

    private fun deleteCache(requestId: String, params: DeleteCacheParams) {
        context.scope.launch {
            var isAllCookieDeleted = false
            if (params.allCookie) {
                isAllCookieDeleted = suspendCoroutine { cont ->
                    CookieManager.getInstance().removeAllCookies { removed ->
                        cont.resume(removed)
                    }
                }
            }

            var isWebViewCacheDeleted = false
            if (params.webViewCache) {
                context.activity.clearWebViewCache()
                isWebViewCacheDeleted = true
            }

            var isAppCacheDeleted = false
            if (params.appCache) {
                isAppCacheDeleted = context.activity.cacheDir.deleteRecursively()
            }

            val result = JSONObject()
                .put("allCookie", isAllCookieDeleted)
                .put("webViewCache", isWebViewCacheDeleted)
                .put("appCache", isAppCacheDeleted)
            sendJavaScript(requestId, RETURN_OK, result)
        }
    }

    @Serializable
    private data class DeleteCacheParams(
        val allCookie: Boolean = false,
        val webViewCache: Boolean = false,
        val appCache: Boolean = false,
    )

    companion object {
        const val SERVICE_NAME = "Cache"

        private const val ACTION_DELETE_CACHE = "deleteCache"
    }
}