package com.lgcns.codeengine.ui

import com.lgcns.codeengine.util.PngByteArray
import kotlinx.coroutines.flow.StateFlow

enum class BarcodeType {
    CODE_128,
    QR_CODE
}

data class QRBarCodeEntity(var qrcodeNo: String = "", var barcodeNo: String = "")

fun QRBarCodeEntity.isEmpty(): Boolean {
    return barcodeNo.isEmpty() && qrcodeNo.isEmpty()
}

data class BarcodeCreateData(
    val type: BarcodeType,
    val data: String,
    val width: Int,
    val height: Int,
    val textSize: Int,
    val drawCode: Boolean = true
)

data class BarcodeData (
    val code: String,
    val barcodeType: BarcodeType,
    val data: PngByteArray?
)

data class BarcodeUiItem (
    val barcodeType: StateFlow<BarcodeType?>,
    val value: StateFlow<String?>,
)
