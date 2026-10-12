package com.lgcns.codeengine.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lgcns.codeengine.ui.dev.WebTestDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class WebViewTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("Start WebViewTestActivity..")
    }

    override fun onResume() {
        super.onResume()

        WebTestDialogFragment.show(supportFragmentManager)
    }

}