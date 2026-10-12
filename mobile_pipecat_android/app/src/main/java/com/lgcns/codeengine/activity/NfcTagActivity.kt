package com.lgcns.codeengine.activity

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.webkit.JsResult
import android.widget.Toast
import com.lgcns.codeengine.R
import com.lgcns.codeengine.databinding.ActivityNfcTagBinding
import com.lgcns.codeengine.ui.CustomDialogFragment
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import timber.log.Timber
import kotlin.experimental.and

@AndroidEntryPoint
class NfcTagActivity : AppCompatActivity() {
    private val binding by lazy { ActivityNfcTagBinding.inflate(layoutInflater) }

    private var nAdapter: NfcAdapter? = null
    private lateinit var pIntent: PendingIntent
    private lateinit var filters: Array<IntentFilter>

    var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate")
        setContentView(binding.root)

        intent?.getStringExtra(com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.REQUEST_ID)?.let { requestId = it }

        nAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nAdapter == null) {
            setSendResult(RESULT_ERROR_NOT_SUPPORTED_DEVICE)
        } else {
            val i = Intent(this, NfcTagActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            pIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_MUTABLE)

            //수신할 태그 데이터 관련 필터 생성
            //Text Record 수신하기 위한 필터
//        val ndef_filter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
//        ndef_filter.addDataType("text/plain")
//        ndef_filter.addDataScheme("https")

//        filters = arrayOf(ndef_filter)

        }

        binding.finishButton.setOnClickListener {
            setSendResult(RESULT_ERROR_USER_CANCEL)
        }

        binding.imgClose.setOnClickListener {
            setSendResult(RESULT_ERROR_USER_CANCEL)
        }
    }

    override fun onResume() {
        super.onResume()
//        nAdapter.enableForegroundDispatch(this, pIntent, filters, null)
        nAdapter?.enableForegroundDispatch(this, pIntent, null, null)

        if (nAdapter != null) {
            if (!nAdapter!!.isEnabled) {
                // 비활성화 시 팝업
                showCustomConfirmDialog()
            }
        }
    }

    private fun showCustomConfirmDialog() {
        CustomDialogFragment.Builder().apply {
            setMessage(getString(R.string.nfc_error_disabled))
            setPositiveButton(getString(R.string.move_button)) {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
            setNegativeButton(getString(R.string.cancel_button)) {
                setSendResult(RESULT_ERROR_USER_CANCEL)
            }
            setCancelable(false)
            show(supportFragmentManager)
        }
    }

    override fun onPause() {
        super.onPause()
        nAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        Timber.d("New Intent action : $action")
        parseData(intent)
    }

    private fun parseData(intent: Intent) {
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {

            //태그의 ID 정보 조회
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            Timber.d("processIntent: ${tag?.id}")

            //태그 안의 데이터의 정보
            var payloadData:String = ""
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMsgs ->
                (rawMsgs[0] as NdefMessage).apply {
                    // record 0 contains the MIME type, record 1 is the AAR, if present
                    val payload: ByteArray = records[0].payload
                    val textEncoding = if ((payload[0] and 128.toByte()).toInt() == 0) Charsets.UTF_8 else Charsets.UTF_16
                    val languageCodeLength: Byte = payload[0] and 63
                    payloadData = String(
                        payload,
                        languageCodeLength + 1,
                        payload.size - languageCodeLength - 1,
                        textEncoding
                    )
                }
            }
            Timber.d("parseData tempPayload: $payloadData")
            Toast.makeText(this, "NFC tag value:$payloadData", Toast.LENGTH_SHORT).show()
            setSendResult(RESULT_SUCCESS, payloadData)
        } else {
            setSendResult(RESULT_ERROR_NO_NDEF)
        }
    }

    private fun setSendResult(errorCode: String, payload: String = "") {
        Intent().also {
            it.putExtra(BaseWebViewActivity.REQUEST_ID, requestId)
            if (errorCode == RESULT_SUCCESS) {
                Timber.d("setSendResult payload: $payload")
                it.putExtra(BaseWebViewActivity.NFCTAG_VALUE, payload)
                setResult(Activity.RESULT_OK, it)
            } else {
                it.putExtra(PARAM_ERROR_CODE, errorCode)
                var errorMessage = ""
                when(errorCode) {
                    RESULT_ERROR_NOT_SUPPORTED_DEVICE -> {
                        errorMessage = getString(R.string.nfc_error_not_supported_device)
                    }
                    RESULT_ERROR_DUPLICATE_TAG -> {
                        errorMessage = getString(R.string.nfc_error_duplicate_tag)
                    }
                    RESULT_ERROR_NO_NDEF -> {
                        errorMessage = getString(R.string.nfc_error_no_ndef)
                    }
                    RESULT_ERROR_USER_CANCEL -> {
                        errorMessage = getString(R.string.nfc_error_user_cancel)
                    }
                }
                it.putExtra(PARAM_ERROR_MESSAGE, errorMessage)
                setResult(Activity.RESULT_CANCELED, it)
            }
            finish()
        }
    }

    companion object {
        const val RESULT_SUCCESS = "RESULT_SUCCESS"
        const val RESULT_ERROR_NOT_SUPPORTED_DEVICE = "NOT_SUPPORTED_DEVICE"
        const val RESULT_ERROR_DUPLICATE_TAG = "DUPLICATE_TAG"
        const val RESULT_ERROR_NO_NDEF = "NO_NDEF"
        const val RESULT_ERROR_USER_CANCEL = "USER_CANCEL"
    }
}