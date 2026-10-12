package com.lgcns.codeengine.web.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import com.canhub.cropper.parcelable
import com.lgcns.codeengine.activity.ImageEditActivity
import com.lgcns.codeengine.activity.ImageEditActivity.Companion.DEFAULT_MAX_SIZE
import com.lgcns.codeengine.activity.ImageEditActivity.Companion.RESULT_URI
import kotlinx.parcelize.Parcelize

@Parcelize
data class ImageEditModel(
    val action: String = "",
    val maxSize: Int = DEFAULT_MAX_SIZE
) : Parcelable

typealias ImageEditContract = ActivityResultContract<ImageEditModel, Uri?>

class ImageEditContractImpl : ImageEditContract() {
    override fun createIntent(context: Context, input: ImageEditModel): Intent {
        return Intent(context, ImageEditActivity::class.java).apply {
            putExtra(ImageEditActivity.ACTION_TYPE, input.action)
            putExtra(ImageEditActivity.MAX_SIZE, input.maxSize)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.parcelable(RESULT_URI)
    }
}