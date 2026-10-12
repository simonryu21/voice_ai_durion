package com.lgcns.codeengine.viewmodel

import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.ArrayList
import javax.inject.Inject
import kotlin.math.ceil
import androidx.lifecycle.viewModelScope
import com.lgcns.codeengine.ui.*
import com.lgcns.codeengine.ui.common.dp
import com.lgcns.codeengine.util.BarcodeGenerator
import com.lgcns.codeengine.web.contract.BarcodeModel
import kotlinx.coroutines.launch

@HiltViewModel
class BarcodeViewViewModel @Inject constructor(
) : QrReaderBaseViewModel() {
    val expireFlow = MutableLiveData<Long>()

    val generateDataStateFlow = MutableLiveData<BarcodeData>()

    private val _barcodeType = MutableStateFlow(BarcodeType.CODE_128)

    private val _value = MutableStateFlow<String?>(null)

    private lateinit var barcodeArgs: BarcodeModel

    val uiItem = BarcodeUiItem(
        barcodeType = _barcodeType,
        value = _value,
        )

    fun start(args: BarcodeModel) {
        barcodeArgs = args
        viewModelScope.launch {
            _barcodeType.value = args.barcodeType
            if (args.expireTime > 0) {
                expireFlow.postValue(args.expireTime)
            }
            when (args.barcodeType) {
                BarcodeType.CODE_128 -> {
                    when {
                        args.entity.barcodeNo.isNotEmpty() -> {
                            _value.value = args.entity.barcodeNo
                            createBarcode(BarcodeType.CODE_128, args.entity.barcodeNo,width = 328.dp, height = 120.dp)
                        }
                    }
                }
                BarcodeType.QR_CODE -> {
                    when {
                        args.entity.qrcodeNo.isNotEmpty() -> {
                            _value.value = getFormatBarcode(args.entity.qrcodeNo)
                            createBarcode(BarcodeType.QR_CODE, args.entity.qrcodeNo,width = 56.dp, height = 56.dp)
                        }
                    }
                }
            }
        }
    }

    private fun createBarcode(type: BarcodeType, data: String, width: Int, height: Int) {
        viewModelScope.launch {
            val barcodeGenerator = BarcodeGenerator()
            barcodeGenerator.createPng(
                BarcodeCreateData(type, data, width = width, height = height, textSize = 0, drawCode = false)
            ).also {
                generateDataStateFlow.postValue(BarcodeData(
                    code = data,
                    barcodeType = type,
                    data = it
                ))
            }
        }
    }

    private fun getFormatBarcode(barcodeNo: String): String? {
        return try {
            val digitLength = 4f
            val arrayCode = ArrayList<String>()
            val cnt = ceil((barcodeNo.length / digitLength).toDouble()).toInt()
            for (i in 0 until cnt) {
                val beginIndex = i * digitLength.toInt()
                var endIndex = (i + 1) * digitLength.toInt()
                if (endIndex > barcodeNo.length) endIndex = barcodeNo.length
                arrayCode.add(barcodeNo.substring(beginIndex, endIndex))
            }
            val result = StringBuilder()
            for (s in arrayCode) {
                result.append(s).append(" ")
            }
            result.toString()
        } catch (e: Exception) {
            barcodeNo
        }
    }

    companion object {
        const val QR_VALID_TIME: Long = 60000 * 2 // 2min
    }
}
