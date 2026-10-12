package com.lgcns.codeengine.web.plugin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.lgcns.codeengine.activity.PipecatPreviewActivity
import com.lgcns.codeengine.translation.bubble.PipecatBubbleManager
import com.lgcns.codeengine.translation.overlay.PipecatOverlayService
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import timber.log.Timber

/**
 * Bridge plugin for Pipecat Voice UI system surfaces.
 *
 * Actions:
 * - `open` — 5-screen preview activity (design review).
 * - `openOverlay` / `closeOverlay` — system overlay bar on top of other apps.
 *   `openOverlay` routes the user to the "draw over other apps" settings when
 *   the `SYSTEM_ALERT_WINDOW` permission is missing.
 * - `openBubble` / `closeBubble` — system Bubble (Android 11+). Reports
 *   `unsupported` below API 30 and `notifications_disabled` when notifications
 *   are off.
 *
 * JS:  `pipecatBridge.execute("PipecatPreview", "openOverlay", {}, requestId)`
 */
class PipecatPreviewPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        when (jsCommand.action) {
            ACTION_OPEN -> open(jsCommand.requestId)
            ACTION_OPEN_OVERLAY -> openOverlay(jsCommand.requestId)
            ACTION_CLOSE_OVERLAY -> closeOverlay(jsCommand.requestId)
            ACTION_OPEN_BUBBLE -> openBubble(jsCommand.requestId)
            ACTION_CLOSE_BUBBLE -> closeBubble(jsCommand.requestId)
            else -> {
                Timber.w("PipecatPreviewPlugin: invalid action ${jsCommand.action}")
                sendJavaScript.error(jsCommand.requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
            }
        }
    }

    private fun open(requestId: String) {
        try {
            PipecatPreviewActivity.startActivity(context.activity)
            sendJavaScript.success(requestId) {
                put("status", "opened")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open Pipecat preview")
            sendJavaScript.error(requestId, "ERROR", "Failed to open preview: ${e.message}")
        }
    }

    private fun openOverlay(requestId: String) {
        val activity = context.activity
        if (!Settings.canDrawOverlays(activity)) {
            // Route to system settings; the JS caller can retry `openOverlay`
            // once the user grants the "draw over other apps" permission.
            runCatching {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                )
            }.onFailure { Timber.e(it, "Failed to open overlay permission settings") }
            sendJavaScript.success(requestId) {
                put("status", "permission_required")
            }
            return
        }
        try {
            PipecatOverlayService.start(activity)
            sendJavaScript.success(requestId) {
                put("status", "opened")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start overlay service")
            sendJavaScript.error(requestId, "ERROR", "Failed to start overlay: ${e.message}")
        }
    }

    private fun closeOverlay(requestId: String) {
        PipecatOverlayService.stop(context.activity)
        sendJavaScript.success(requestId) {
            put("status", "closed")
        }
    }

    private fun openBubble(requestId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            sendJavaScript.success(requestId) {
                put("status", "unsupported")
            }
            return
        }
        try {
            val posted = PipecatBubbleManager.show(context.activity)
            sendJavaScript.success(requestId) {
                put("status", if (posted) "opened" else "notifications_disabled")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to post Pipecat bubble")
            sendJavaScript.error(requestId, "ERROR", "Failed to open bubble: ${e.message}")
        }
    }

    private fun closeBubble(requestId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PipecatBubbleManager.hide(context.activity)
        }
        sendJavaScript.success(requestId) {
            put("status", "closed")
        }
    }

    companion object {
        const val SERVICE_NAME = "PipecatPreview"

        private const val ACTION_OPEN = "open"
        private const val ACTION_OPEN_OVERLAY = "openOverlay"
        private const val ACTION_CLOSE_OVERLAY = "closeOverlay"
        private const val ACTION_OPEN_BUBBLE = "openBubble"
        private const val ACTION_CLOSE_BUBBLE = "closeBubble"
    }
}
