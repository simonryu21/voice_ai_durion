package com.lgcns.codeengine.web.plugin

import android.content.Intent
import android.net.Uri
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class AppHandoffPlugin(
    private val context: PluginContext,
) : BasePlugin() {

    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        Timber.i("AppHandoff.execute action=${jsCommand.action} requestId=${jsCommand.requestId} rawParams=${jsCommand.params}")
        val requestId = jsCommand.requestId
        val params by lazy { JSONObject(jsCommand.params) }
        when (jsCommand.action) {
            ACTION_LAUNCH -> launch(requestId, params)
            else -> {
                Timber.w("AppHandoff: invalid action=${jsCommand.action}")
                sendJavaScript.error(
                    requestId,
                    code = "INVALID_ACTION",
                    message = "Invalid action. (${jsCommand.action})",
                )
            }
        }
    }

    private fun launch(requestId: String, params: JSONObject) {
        val handoffId = params.optString(PARAM_HANDOFF_ID, "")
        val packageName = params.optString(PARAM_PACKAGE_NAME, "").takeIf { it.isNotEmpty() }
        val executionPaths = params.optJSONArray(PARAM_EXECUTION_PATHS) ?: JSONArray()

        Timber.i(
            "AppHandoff.launch ENTER handoffId=%s package=%s paths=%d",
            handoffId, packageName, executionPaths.length(),
        )

        if (executionPaths.length() == 0) {
            Timber.w("AppHandoff.launch: execution_paths empty (handoffId=$handoffId)")
            sendJavaScript.error(
                requestId,
                code = "NO_EXECUTION_PATHS",
                message = "execution_paths is empty",
            )
            return
        }

        val attempts = mutableListOf<JSONObject>()
        for (i in 0 until executionPaths.length()) {
            val path = executionPaths.optJSONObject(i) ?: continue
            val type = path.optString(KEY_TYPE)
            val uri = path.optString(KEY_URI)
            val action = path.optString(KEY_ACTION).takeIf { it.isNotEmpty() }
                ?: Intent.ACTION_VIEW

            Timber.i("AppHandoff.launch attempt[$i] type=$type uri=$uri action=$action")

            val attempt = JSONObject().put(KEY_TYPE, type).put(KEY_URI, uri)

            val intent = buildIntent(type, uri, action)
            if (intent == null) {
                Timber.w("AppHandoff.launch attempt[$i] build_failed type=$type uri=$uri")
                attempts.add(attempt.put("error", "build_failed"))
                continue
            }

            // applink: bind to the target package so the system opens the app directly
            // instead of falling through to a browser/chooser.
            if (type == TYPE_APPLINK && packageName != null) {
                intent.setPackage(packageName)
                Timber.i("AppHandoff.launch attempt[$i] setPackage=$packageName (applink)")
            }

            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Timber.i("AppHandoff.launch attempt[$i] startActivity called (resolved=${intent.resolveActivity(context.activity.packageManager)})")
                context.activity.startActivity(intent)
                Timber.i("AppHandoff.launch SUCCESS type=$type uri=$uri handoffId=$handoffId")
                val result = JSONObject()
                    .put("handoff_id", handoffId)
                    .put("launched_type", type)
                    .put("launched_uri", uri)
                sendJavaScript.success(requestId, result)
                return
            } catch (e: Exception) {
                Timber.w(e, "AppHandoff.launch attempt[$i] startActivity failed type=$type uri=$uri")
                attempts.add(attempt.put("error", e.javaClass.simpleName))
            }
        }

        Timber.w("AppHandoff.launch ALL_PATHS_FAILED handoffId=$handoffId attempts=$attempts")
        sendJavaScript.error(
            requestId,
            code = "ALL_PATHS_FAILED",
            message = "no execution path could be launched",
        ) {
            put("handoff_id", handoffId)
            put("attempts", JSONArray(attempts))
        }
    }

    private fun buildIntent(type: String, uri: String, action: String): Intent? {
        if (uri.isEmpty()) return null
        return try {
            when (type) {
                TYPE_INTENT -> Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                else -> Intent(action, Uri.parse(uri))
            }
        } catch (e: Exception) {
            Timber.w(e, "AppHandoff: failed to build intent type=$type uri=$uri")
            null
        }
    }

    companion object {
        const val SERVICE_NAME = "AppHandoff"

        private const val ACTION_LAUNCH = "launch"

        private const val PARAM_HANDOFF_ID = "handoff_id"
        private const val PARAM_PACKAGE_NAME = "package_name"
        private const val PARAM_EXECUTION_PATHS = "execution_paths"

        private const val KEY_TYPE = "type"
        private const val KEY_URI = "uri"
        private const val KEY_ACTION = "action"

        private const val TYPE_APPLINK = "applink"
        private const val TYPE_INTENT = "intent"
    }
}
