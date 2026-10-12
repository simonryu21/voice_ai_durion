package com.lgcns.codeengine.activity

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Outbound URL의 경우 Inbound와 구분하기 위해 OutboundWebViewActivity를 사용합니다.
 */
@AndroidEntryPoint
class OutboundWebViewActivity : WebViewActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("OutboundWebViewActivity onCreate()")
    }

}