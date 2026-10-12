package com.lgcns.codeengine.web.contract

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShareModel(
    val title: String,
    val text: String,
    val chooserTitle: String,
) : Parcelable

typealias ShareContract = ActivityResultContract<ShareModel, Boolean>

class ShareContractImpl : ShareContract() {
    override fun createIntent(context: Context, input: ShareModel): Intent {
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TITLE, input.title)
            putExtra(Intent.EXTRA_TEXT, input.text)
            type = "text/plain"
        }, input.chooserTitle)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return true
    }
}