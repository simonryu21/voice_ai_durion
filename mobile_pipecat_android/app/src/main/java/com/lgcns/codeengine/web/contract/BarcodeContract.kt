package com.lgcns.codeengine.web.contract

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import com.lgcns.codeengine.activity.BarcodeViewActivity
import com.lgcns.codeengine.ui.BarcodeType
import com.lgcns.codeengine.ui.QRBarCodeEntity
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class BarcodeModel(
    val expireTime: Long,
    val barcodeType: BarcodeType,
    val entity: @RawValue QRBarCodeEntity,
    val authToken: String = ""
) : Parcelable

typealias BarcodeContract = ActivityResultContract<BarcodeModel, Boolean>

class BarcodeContractImpl : BarcodeContract() {
    override fun createIntent(context: Context, input: BarcodeModel): Intent {
        return Intent(context, BarcodeViewActivity::class.java).apply {
            putExtra(BarcodeViewActivity.EXTRA_TIMER_VALUE, input.expireTime)
            putExtra(BarcodeViewActivity.BARCODE_TYPE, input.barcodeType)
            putExtra(BarcodeViewActivity.QRCODE_VALUE,input.entity.qrcodeNo)
            putExtra(BarcodeViewActivity.BARCODE_VALUE,input.entity.barcodeNo)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return true
    }
}