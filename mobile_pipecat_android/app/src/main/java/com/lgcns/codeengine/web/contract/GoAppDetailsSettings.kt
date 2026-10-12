package com.lgcns.codeengine.web.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract

class GoAppDetailsSettings : ActivityResultContract<String, Unit>() {
    override fun createIntent(context: Context, input: String): Intent {
        return Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:$input")
            addCategory(Intent.CATEGORY_DEFAULT)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?) {
        // do nothing..
    }
}