package com.lgcns.codeengine.web.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.ArrayList

@Parcelize
data class FileSelectModel(
    val mimeTypes: List<String>,
    val isMultiple: Boolean,
) : Parcelable

typealias FileSelectContract = ActivityResultContract<FileSelectModel, Intent?>

class FileSelectContractImpl : FileSelectContract() {
    override fun createIntent(context: Context, input: FileSelectModel): Intent {
        return Intent.createChooser(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, input.mimeTypes.toTypedArray())
            if (input.isMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        }, "")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Intent? {

        if (resultCode != AppCompatActivity.RESULT_OK) {
            return null
        }

        return intent
    }
}