package com.lgcns.codeengine.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lgcns.codeengine.R
import com.lgcns.codeengine.databinding.ActivityBarcodeViewBinding
import com.lgcns.codeengine.ui.BarcodeType
import com.lgcns.codeengine.ui.QRBarCodeEntity
import com.lgcns.codeengine.util.eventflow.SingleLocalEvent
import com.lgcns.codeengine.viewmodel.BarcodeViewViewModel
import com.lgcns.codeengine.viewmodel.BarcodeViewViewModel.Companion.QR_VALID_TIME
import com.lgcns.codeengine.web.contract.BarcodeModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil


@AndroidEntryPoint
class BarcodeViewActivity : BaseBindingActivity<ActivityBarcodeViewBinding>() {
    override var layoutResourceId: Int = R.layout.activity_barcode_view

    private val viewModel: BarcodeViewViewModel by lazy {
        ViewModelProvider(this)[BarcodeViewViewModel::class.java]
    }

    private var barcodeTimer: CountDownTimer? = null
    var remainingTime : Long = QR_VALID_TIME

    private var params: WindowManager.LayoutParams? = null
    private var brightness = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.item = viewModel.uiItem
        binding.closeButton.setOnClickListener {
            finish()
        }

        params = window.attributes

        // prevent taking screenshot in this activity
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE)

        viewModel.start(intent.getArguments())

        setObserver()
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        // 기존 밝기 저장
        brightness = params?.screenBrightness ?: 1f
        // 최대 밝기로 설정
        params?.screenBrightness = 1f
        // 밝기 설정 적용
        window.attributes = params
    }

    override fun onPause() {
        // 최대 밝기로 설정
        params?.screenBrightness = brightness
        // 밝기 설정 적용
        window.attributes = params

        super.onPause()
    }

    override fun onDestroy() {
        hideLoading()
        barcodeTimer?.cancel()
        super.onDestroy()
    }

    private fun startCountdownTimer(expireTime: Long) {

        barcodeTimer?.cancel()

        barcodeTimer = object : CountDownTimer(expireTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val date = Date(millisUntilFinished)
                val formatter = SimpleDateFormat("mm:ss", Locale.getDefault())
                val formatted = formatter.format(date)
                remainingTime = millisUntilFinished
                binding.timer.text = formatted
                binding.qrtimer.text = formatted
            }

            override fun onFinish() {
                cancel()
            }
        }.start()
    }

    private fun Intent.getArguments(): BarcodeModel {
        @Suppress("DEPRECATION")
        return BarcodeModel (
            expireTime = getLongExtra(EXTRA_TIMER_VALUE, 0),
            barcodeType = (getSerializableExtra(BARCODE_TYPE) ?: BarcodeType.CODE_128) as BarcodeType,
            entity = QRBarCodeEntity(
                qrcodeNo = getStringExtra(QRCODE_VALUE) ?: "qrcode",
                barcodeNo = getStringExtra(BARCODE_VALUE) ?: "barcode",
            ),
            authToken = getStringExtra(EXTRA_AUTH_TOKEN) ?: ""
        )
    }

    private fun byteArrayToBitmap(data: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    private fun getFormatBarcode(barcodeNo: String): String? {
        return try {
            val DIGIT_LENGTH = 4f
            val arrayCode = ArrayList<String>()
            val cnt = ceil((barcodeNo.length / DIGIT_LENGTH).toDouble()).toInt()
            for (i in 0 until cnt) {
                val beginIndex = i * DIGIT_LENGTH.toInt()
                var endIndex = (i + 1) * DIGIT_LENGTH.toInt()
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

    private fun setObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.expireFlow.removeObservers(this@BarcodeViewActivity)
                    viewModel.expireFlow.observe(this@BarcodeViewActivity) { value ->
                        startCountdownTimer(value)
                    }
                }
                launch {
                    viewModel.generateDataStateFlow.removeObservers(this@BarcodeViewActivity)
                    viewModel.generateDataStateFlow.observe(this@BarcodeViewActivity) {
                        it?.let {
                            if (it.barcodeType == BarcodeType.CODE_128) {
                                it.data?.let { png ->
                                    binding.imgBarcode.setImageBitmap(byteArrayToBitmap(png))
                                }
                                binding.tvBarcode.text = getFormatBarcode(it.code)
                            } else {
                                it.data?.let { png ->
                                    binding.imgQrcode.setImageBitmap(byteArrayToBitmap(png))
                                }
                            }
                        }
                    }
                }
                launch {
                    SingleLocalEvent.dataEvent.collect {
                        Timber.d("SingleLocalEvent.dataEvent.collect -- it = $it")
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TIMER_VALUE = "extra.TIMER_VALUE"
        const val BARCODE_TYPE = "extra.BARCODE_TYPE"
        const val BARCODE_VALUE = "extra.BARCODE_VALUE"
        const val QRCODE_VALUE = "extra.QRCODE_VALUE"
        const val EXTRA_AUTH_TOKEN = "extra.AUTH_TOKEN"
    }
}
