package com.lgcns.codeengine.web.contract

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class ShareImageModel(
    val imageUri: Uri,
    val chooserTitle: String,
) : Parcelable

typealias ShareImageContract = ActivityResultContract<ShareImageModel, Boolean>

class ShareImageContractImpl : ShareImageContract() {
    override fun createIntent(context: Context, input: ShareImageModel): Intent {
        Timber.e("input.chooserTitle=[${input.chooserTitle}]")

        val chooserIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, input.imageUri)
            type = "image/*"
        }, input.chooserTitle)

        val resolveInfoList = context.packageManager.queryIntentActivities(
            chooserIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        for (info in resolveInfoList) {
            val packageName = info.activityInfo.packageName
            context.grantUriPermission(
                packageName, input.imageUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        return chooserIntent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return true
    }
}
