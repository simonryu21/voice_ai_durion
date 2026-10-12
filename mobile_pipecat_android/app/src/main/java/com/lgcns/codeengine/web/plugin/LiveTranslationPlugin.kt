package com.lgcns.codeengine.web.plugin

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.activity.BaseWebViewActivity
import com.lgcns.codeengine.activity.TranslationWebViewActivity
import com.lgcns.codeengine.web.PluginContext
import com.lgcns.codeengine.web.params.INVALID_ACTION
import com.lgcns.codeengine.web.params.INVALID_INPUT_ACTION
import com.lgcns.codeengine.web.params.JSCommand
import org.json.JSONObject
import timber.log.Timber

class LiveTranslationPlugin(
    private val context: PluginContext,
) : BasePlugin() {
    private val sendJavaScript = context.activity::sendJavaScript

    override fun execute(jsCommand: JSCommand) {
        Timber.d("LiveTranslationPlugin.execute() called - action: ${jsCommand.action}, requestId: ${jsCommand.requestId}")
        val requestId = jsCommand.requestId
        when (jsCommand.action) {
            ACTION_START_TRANSLATION -> startTranslation(requestId)
            ACTION_STOP_TRANSLATION -> stopTranslation(requestId)
            ACTION_OPEN_TRANSLATION_AGENT -> openTranslationAgent(requestId)
            else -> {
                Timber.w("LiveTranslationPlugin: Invalid action - ${jsCommand.action}")
                sendJavaScript.error(requestId, INVALID_ACTION, INVALID_INPUT_ACTION)
            }
        }
    }

    private fun startTranslation(requestId: String) {
        Timber.d("LiveTranslationPlugin.startTranslation() called - requestId: $requestId")
        try {
            // Start GeminiLiveTranslationActivity instead of starting service
            Timber.d("LiveTranslationPlugin: Starting GeminiLiveTranslationActivity...")
            com.lgcns.codeengine.activity.GeminiLiveTranslationActivity.startActivity(context.activity)

            sendJavaScript.success(requestId) {
                put("status", "started")
            }

            Timber.d("LiveTranslation Activity started successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start translation activity")
            sendJavaScript.error(requestId, "ERROR", "Failed to start translation: ${e.message}")
        }
    }

    private fun stopTranslation(requestId: String) {
        try {
            // Activity will be closed when Stop Session is clicked
            sendJavaScript.success(requestId) {
                put("status", "stopped")
            }

            Timber.d("LiveTranslation stop requested")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop translation")
            sendJavaScript.error(requestId, "ERROR", "Failed to stop translation: ${e.message}")
        }
    }

    /**
     * "번역 Agent" 카드 클릭 시 호출. 턴 기반 번역 전용 WebView Activity를 띄운다.
     * (동시통역 Agent의 [startTranslation] = GeminiLiveTranslationActivity 와 별개)
     */
    private fun openTranslationAgent(requestId: String) {
        Timber.d("LiveTranslationPlugin.openTranslationAgent() called - requestId: $requestId")
        try {
            val intent = Intent(context.activity, TranslationWebViewActivity::class.java).apply {
                // 화면 내 닫기 버튼 노출 (back 버튼과 함께 일반 대화 화면으로 복귀)
                putExtra(BaseWebViewActivity.SHOW_CLOSE_BUTTON, true)
            }
            context.activity.startActivity(intent)

            sendJavaScript.success(requestId) {
                put("status", "opened")
            }

            Timber.d("TranslationWebViewActivity started successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open translation agent")
            sendJavaScript.error(requestId, "ERROR", "Failed to open translation agent: ${e.message}")
        }
    }

    companion object {
        const val SERVICE_NAME = "LiveTranslation"

        private const val ACTION_START_TRANSLATION = "startTranslation"
        private const val ACTION_STOP_TRANSLATION = "stopTranslation"
        private const val ACTION_OPEN_TRANSLATION_AGENT = "openTranslationAgent"
    }
}

