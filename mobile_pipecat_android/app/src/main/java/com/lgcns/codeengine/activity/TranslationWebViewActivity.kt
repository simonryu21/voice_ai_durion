package com.lgcns.codeengine.activity

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * "번역 Agent" 전용 WebView Activity.
 *
 * 메인 대화 WebView와 **별개의 WebView/WebRTC 연결**을 맺어, 봇 클라이언트를
 * `?mode=translation` 으로 로드한다. 봇(`run_bot`)은 이 플래그를 보고 메인 대화 대신
 * 턴 기반 번역 파이프라인(STT → 번역 LLM → TTS)으로 분기한다. "새 연결 = 파이프라인
 * 1개"라 예전 번역 모드처럼 하나의 transport를 두 파이프라인이 공유해 충돌하던
 * 문제가 없다.
 *
 * back 버튼 또는 닫기 버튼으로 종료하면 `finish()` 되어 원래 대화 화면으로 복귀한다.
 * 동시통역 Agent(`GeminiLiveTranslationActivity`)와는 무관한 별도 화면이다.
 */
@AndroidEntryPoint
class TranslationWebViewActivity : WebViewActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("TranslationWebViewActivity onCreate()")
    }

    /**
     * 이 Activity가 로드하는 모든 URL에 `mode=translation` 플래그를 강제로 붙인다.
     * 봇 클라이언트는 이 쿼리 파라미터를 보고 offer body에 번역 모드를 실어 보낸다.
     */
    override fun loadUrl(url: String, method: String, param: String) {
        val translationUrl = if (url.contains("mode=translation")) {
            url
        } else {
            val separator = if (url.contains("?")) "&" else "?"
            "$url${separator}mode=translation"
        }
        Timber.d("TranslationWebViewActivity.loadUrl() -> $translationUrl")
        super.loadUrl(translationUrl, method, param)
    }
}
