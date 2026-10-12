package com.lgcns.codeengine.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.WebView
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.firebase.dynamiclinks.PendingDynamicLinkData
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.ktx.Firebase
import com.lgcns.codeengine.R
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.databinding.ActivityWebViewBinding
import com.lgcns.codeengine.ui.BarcodeType
import com.lgcns.codeengine.ui.QRBarCodeEntity
import com.lgcns.codeengine.ui.model.VideoDetailsModel
import com.lgcns.codeengine.util.BiometricUtility
import com.lgcns.codeengine.util.BiometricUtility.HARDWARE_IS_OK
import com.lgcns.codeengine.util.extension.collectRepeatOnStarted
import com.lgcns.codeengine.util.extension.compressJpeg
import com.lgcns.codeengine.util.extension.compressPng
import com.lgcns.codeengine.util.extension.format
import com.lgcns.codeengine.util.extension.putInt
import com.lgcns.codeengine.util.extension.putString
import com.lgcns.codeengine.util.registerForSingleActivityResult
import com.lgcns.codeengine.viewmodel.LoginViewModel
import com.lgcns.codeengine.viewmodel.TokenViewModel
import com.lgcns.codeengine.viewmodel.WebDataViewModel
import com.lgcns.codeengine.web.*
import com.lgcns.codeengine.web.contract.*
import com.lgcns.codeengine.web.params.JSEventName
import com.lgcns.codeengine.web.params.JSEventType
import com.lgcns.codeengine.web.params.PARAM_ERROR_CODE
import com.lgcns.codeengine.web.params.PARAM_ERROR_MESSAGE
import com.lgcns.codeengine.web.permission.ActivityPermissionHelper
import com.lgcns.codeengine.web.plugin.BasePlugin.Companion.RETURN_ERROR
import com.lgcns.codeengine.web.plugin.BasePlugin.Companion.RETURN_OK
import com.lgcns.codeengine.web.usecase.WebViewPluginUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow


abstract class BaseWebViewActivity : BaseBindingActivity<ActivityWebViewBinding>() {

    interface ConfirmResult {
        fun confirm()
        fun cancel()
    }

    enum class CaptureTarget {
        SCREEN, FULL_WEB,
    }

    enum class MediaType {
        PICTURE, VIDEO, FILE
    }

    lateinit var webAppBridge: WebAppBridge
    lateinit var pluginContext: PluginContextImpl

    private val webDataViewModel: WebDataViewModel by lazy {
        ViewModelProvider(this)[WebDataViewModel::class.java]
    }

    private val loginViewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    private val tokenViewModel: TokenViewModel by lazy {
        ViewModelProvider(this)[TokenViewModel::class.java]
    }

    @Inject
    lateinit var pluginPermissions: PluginPermissions

    private lateinit var webViewPluginUseCase: WebViewPluginUseCase

    var loadedUrl: String = ""

    private var requestIdSms: String = ""

    private val smsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // SMS 발송 성공이 되어도 RESULT_CODE 가 "RESULT_CANCELED" 으로 들어오고 , intent는 null 임
            sendJavaScript(requestIdSms, RETURN_OK, JSONObject())
        }

    var requestId: String = ""

    private lateinit var permissionList: Array<PermissionData>

    private lateinit var smsReceiver: SMSReceiver

    val navigatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

            var receivedRequestId = it.data?.getStringExtra(REQUEST_ID) ?: ""
            val param = it.data?.getStringExtra(EXTRA_DATA)
            Timber.d("activity result: receivedRequestId=$receivedRequestId, param=$param")

            val jsonObject: JSONObject = if (param != null) JSONObject(param) else JSONObject()

            sendJavaScript(receivedRequestId, RETURN_OK, jsonObject)
        }

    private var handoffResultCallback: ((ActivityResult) -> Unit)? = null

    private val handoffLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handoffResultCallback?.invoke(result)
            handoffResultCallback = null
        }

    fun launchHandoffWebView(intent: Intent, callback: (ActivityResult) -> Unit) {
        handoffResultCallback = callback
        handoffLauncher.launch(intent)
    }

    fun getWebView(): WebView = binding.webView

    private val barcodeScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

            var receivedRequestId = it.data?.getStringExtra(REQUEST_ID) ?: ""
            val barcodeValue = it.data?.getStringExtra(BARCODE_VALUE)
            Timber.d("activity result: receivedRequestId=$receivedRequestId, barcodeValue=$barcodeValue")

            val jsonObject: JSONObject = if (barcodeValue != null) JSONObject().putString("barcodeValue", barcodeValue) else JSONObject()

            sendJavaScript(receivedRequestId, RETURN_OK, jsonObject)
        }

    private val nfcTagReadLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            var receivedRequestId = it.data?.getStringExtra(REQUEST_ID) ?: ""

            if (it.resultCode == Activity.RESULT_OK) {
                val nfcTagValue = it.data?.getStringExtra(NFCTAG_VALUE)
                Timber.d("activity result: receivedRequestId=$receivedRequestId, nfcTagValue=$nfcTagValue")
                val jsonObject: JSONObject = if (nfcTagValue != null) JSONObject().putString("nfcTagValue", nfcTagValue) else JSONObject()

                sendJavaScript(receivedRequestId, RETURN_OK, jsonObject)
            } else {
                val errorCode = it.data?.getStringExtra(PARAM_ERROR_CODE)
                val errorMessage = it.data?.getStringExtra(PARAM_ERROR_MESSAGE)
                val jsonObject = JSONObject().apply {
                    put(PARAM_ERROR_CODE, errorCode)
                    put(PARAM_ERROR_MESSAGE, errorMessage)
                }
                sendJavaScript(receivedRequestId, RETURN_ERROR, jsonObject)
            }
        }

    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    @Inject
    lateinit var featurePermissionHelper: ActivityPermissionHelper

    private val goAppDetailsSettings = registerForActivityResult(GoAppDetailsSettings()) {
        // Do nothing
    }

    private val shareLauncher = registerForSingleActivityResult(ShareContractImpl())

    private val shareImageLauncher = registerForSingleActivityResult(ShareImageContractImpl())

    private val barcodeViewLauncher = registerForSingleActivityResult(BarcodeContractImpl())

    private val editImageLauncher = registerForSingleActivityResult(ImageEditContractImpl())

    private val fileSelectLauncher = registerForSingleActivityResult(FileSelectContractImpl())

    lateinit var uploadMediaType: MediaType

    var mediaValueCallback: ValueCallback<Array<Uri>>? = null

    private lateinit var cameraMediaUri: Uri

    private val attachedUris = mutableListOf<Uri>()
    private val attachedResultUris = mutableListOf<Uri>()
    private val attachedData = mutableListOf<VideoDetailsModel>()

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("Notifications permission granted")
        } else {
            Timber.w("FCM can't post notifications without POST_NOTIFICATIONS permission")
        }
    }

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Timber.d("selectImageLauncher ActivityResult: it.data=$it.data")

            clearAttachedData()

            if (it.resultCode != RESULT_OK) {
                mediaValueCallback?.onReceiveValue(null)
                mediaValueCallback = null
                return@registerForActivityResult
            }

            if (it.data == null || (it.data!!.data == null && it.data!!.clipData == null)) {
                Timber.d("[Camera] $cameraMediaUri")
                // 카메라로 찍어 바로 올릴 때
                val newIntent = Intent()
                newIntent.data = cameraMediaUri

                mediaValueCallback?.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(
                        it.resultCode,
                        newIntent
                    )
                )
            } else if (it.data != null && it.data!!.data != null) {
                // 갤러리에서 한장만 선택하는 경우 (Intent.EXTRA_ALLOW_MULTIPLE이 false인경우)
                Timber.d("[Gallery] ${(it.data!!.data as Uri).path}")
                mediaValueCallback?.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(
                        it.resultCode,
                        it.data
                    )
                )
            } else {
                // 갤러리에서 여러장 선택하는 경우 (Intent.EXTRA_ALLOW_MULTIPLE이 true인경우)
                val results = ArrayList<Uri>()
                for (i in 0 until it.data!!.clipData!!.itemCount) {
                    Timber.d(
                        "[Gallery Multiple] add uri: %s",
                        it.data!!.clipData!!.getItemAt(i).uri
                    )
                    results.add(it.data!!.clipData!!.getItemAt(i).uri)
                }

                mediaValueCallback?.onReceiveValue(results.toTypedArray())
            }
            mediaValueCallback = null
        }

    private val selectVideoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Timber.d("selectVideoLauncher ActivityResult: it.data=$it.data")

            clearAttachedData()

            if (it.resultCode != RESULT_OK) {
                mediaValueCallback?.onReceiveValue(null)
                mediaValueCallback = null
                return@registerForActivityResult
            }

            if (it.data == null || (it.data!!.data == null && it.data!!.clipData == null)) {
                // 카메라로 찍어 바로 올릴 때
                Timber.d("[Camera] $cameraMediaUri")
                attachedUris.add(cameraMediaUri)
            } else if (it.data != null && it.data!!.data != null) {
                // 갤러리에서 한장만 선택하는 경우 (Intent.EXTRA_ALLOW_MULTIPLE이 false인경우)
                Timber.d("add uri: %s", it.data!!.data!!)
                attachedUris.add(it.data!!.data!!)
            } else {
                // 갤러리에서 여러장 선택하는 경우 (Intent.EXTRA_ALLOW_MULTIPLE이 true인경우)
                for (i in 0 until it.data!!.clipData!!.itemCount) {
                    Timber.d("add uri: %s", it.data!!.clipData!!.getItemAt(i).uri)
                    attachedUris.add(it.data!!.clipData!!.getItemAt(i).uri)
                }
            }

            compressVideo(it.data)

        }

    private fun clearAttachedData() {
        attachedUris.clear()
        attachedData.clear()
        attachedResultUris.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("onCreate()")

        checkNotificationTapped(intent)

        smsReceiver = SMSReceiver(this)

        observeDataAndEvent()

        pluginContext = PluginContextImpl(this)

        webViewPluginUseCase = pluginContext.useCaseEntryPoint.provideWebViewPluginUseCase()

        webAppBridge = WebAppBridge(pluginContext)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        executor = ContextCompat.getMainExecutor(this)

        initBiometricPrompt()

        // BiometricPrompt.AuthenticationCallback()이 시나리오별로 여러개 있는 경우,
        // biometricPrompt.authenticate(promptInfo) 전에 setBiometricPrompt()로 callback Listener를 변경해줍니다.
        setBiometricPrompt()

        permissionList = arrayOf(
            PermissionData(
                PERMISSION_PLUGIN_DEVICE_EMAIL_ADDRESS,
                pluginPermissions.deviceEmailAddress
            ),
            PermissionData(PERMISSION_PLUGIN_ALL_CONTACTS, pluginPermissions.allContactsPermission),
            PermissionData(
                PERMISSION_PLUGIN_WRITE_PUBLIC_PICTURES,
                pluginPermissions.publicPicturesWritePermission
            ),
            PermissionData(PERMISSION_PLUGIN_FIND_LOCATION, pluginPermissions.locationPermission),
            PermissionData(
                PERMISSION_PLUGIN_GET_PHONE_NUMBER,
                pluginPermissions.phoneNumberPermission
            ),
            PermissionData(PERMISSION_PLUGIN_RECORD_AUDIO, pluginPermissions.recordAudioPermission),
            PermissionData(
                PERMISSION_PLUGIN_CALENDAR_ACCESS,
                pluginPermissions.calendarAccessPermission
            ),
            PermissionData(PERMISSION_PLUGIN_CAMERA, pluginPermissions.cameraPermission),
            PermissionData(
                PERMISSION_PLUGIN_PHONE_CALL_STATE,
                pluginPermissions.phoneCallStatePermission
            ),
            PermissionData(
                PERMISSION_PLUGIN_POST_NOTIFICATIONS,
                pluginPermissions.notificationPermission
            ),
            PermissionData(
                PERMISSION_PLUGIN_NFC,
                pluginPermissions.nfcPermission
            ),
        )

        /*
        //make fully transparent Status bar
        if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT < 21) {
            setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, true)
        }
        if (Build.VERSION.SDK_INT >= 19) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        if (Build.VERSION.SDK_INT >= 21) {
            setWindowFlag(this, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false)
            window.statusBarColor = Color.TRANSPARENT
        }

        // make fully transparent Navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val w = window
            w.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
        */
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("onNewIntent()")

        checkNotificationTapped(intent)
    }

    private fun checkNotificationTapped(intent: Intent) {
        // If a notification message is tapped, any data accompanying the notification
        // message is available in the intent extras.

        Timber.d("checkNotificationTapped()")

        intent.extras?.let {
            for (key in it.keySet()) {
                @Suppress("DEPRECATION") val value = intent.extras?.get(key)
                Timber.d("Key: $key Value: $value")
            }
        }

        val msgTag = intent.extras?.getString("msgTag")
        val mode = intent.extras?.getString("mode")
        val lCode = intent.extras?.getString("lCode")
        Timber.d("msgTag=$msgTag, mode=$mode")

        if (msgTag?.isNotEmpty() == true && mode?.isNotEmpty() == true) {
            // 알림을 터치한 것을 서버에 알려줍니다.
        }

    }

    /**
     * 네이티브 -> 하이브리드 호출 할 때 사용.
     */
    abstract fun sendJavaScript(requestId: String, resultCode: String, params: JSONObject)

    abstract fun callbackAppEvent(
        eventName: JSEventName,
        eventType: JSEventType,
        params: JSONObject
    )

    abstract fun clearWebViewCache()

    abstract fun showToast(message: String, bottomOffsetInDp: Int?)

    abstract fun setWebViewKeyboardVisibility(visible: Boolean)

    abstract suspend fun takeScreenshotBitmap(target: CaptureTarget): Bitmap?

    abstract fun loadUrl(url: String, method: String, param: String = "")

    abstract fun moveWithUrl(requestId: String, url: String, data: String = "", showCloseButton: Boolean = false)

    fun goPrevPageWithData(param: JSONObject) {
        Timber.d("goPrevPageWithData() requestId=$requestId, param=$param")
        val intent = Intent()
            .putExtra(EXTRA_DATA, param.toString())
            .putExtra(REQUEST_ID, requestId)

        setResult(RESULT_OK, intent)
        finish()
    }


    fun launchPIPWindow(requestId: String, url: String, data: String, showCloseButton: Boolean) {
        Timber.d("launchPIPWindow requestId : $requestId / url : $url / data : $data")

        // launch로 띄우면 PIP를 minimize한 후 다른 PIP 상품을 선택하면,
        // minimize된 화면이 커지면서 update가 되는 게 아니라, minimize된 화면은 그냥 있고 뒤에 있는 화면의 url이 바뀜
        val intent = Intent(this, PIPWebViewActivity::class.java).apply {
            putExtra(LOAD_URL, url)
            putExtra(EXTRA_DATA, data)
            putExtra(REQUEST_ID, requestId)
            putExtra(SHOW_CLOSE_BUTTON, showCloseButton)
        }

        startActivity(intent)
        /*
                navigatorLauncher.launch(Intent(this, PIPWebViewActivity::class.java).apply {
                    putExtra(LOAD_URL, url)
                    putExtra(EXTRA_DATA, data)
                    putExtra(REQUEST_ID, requestId)
                    putExtra(SHOW_CLOSE_BUTTON, showCloseButton)
                })
        */

    }

    fun openWithBrowser(url: String) {
        Timber.d("openWithBrowser url : $url")

        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun getWebJsonData() = webDataViewModel.getWebJsonData()

    fun setWebJsonData(data: JSONObject) = webDataViewModel.setWebJsonData(data)

    fun getWebValue(keys: List<String>) = webDataViewModel.getWebValue(keys)

    fun setWebValue(records: Map<String, String?>) = webDataViewModel.setWebValue(records)

    fun setWebData(data: String) {
        if (data.isNotEmpty()) {
            try {
                Timber.d("setWebData parameter EXTRA_DATA : $data")
                webDataViewModel.setWebJsonData(JSONObject(data))
            } catch (e: Exception) {
                Timber.w(e.toString())
            }
        }
    }

    fun sendSMSWithCaptureImage(param: JSONObject) {
        lifecycleScope.launch {
            requestIdSms = param.optString(REQUEST_ID)
            Timber.e("sendSMSWithCaptureImage() : param=$param")
            var phoneNum = param.optString(KEY_PHONE_NUM)
            val message = param.optString(KEY_MESSAGE)
            val imageYn = param.optString(KEY_IMAGE_YN)

            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(KEY_SMS_BODY, message)

            if (!phoneNum.isNullOrEmpty()) {
                phoneNum = phoneNum.replace("-", "").replace(" ", "")

                if (Build.MANUFACTURER.equals(KEY_SAMSUNG, true)) {
                    phoneNum = phoneNum.replace(";", ",")
                }
                intent.putExtra(KEY_ADDRESS, phoneNum)
            }

            if (imageYn == "Y") {
                val screenshot = takeScreenshotBitmap(CaptureTarget.SCREEN)
                val pngBytes = screenshot?.compressPng()
                screenshot?.recycle()

                pngBytes?.let {
                    val pngFile: File? = webViewPluginUseCase.mediaRepository.saveToAppCache(
                        source = it,
                        relativePath = "picture",
                        filename = "screenshot_${now().format("yyyyMMdd_hhmmss")}.png",
                    )

                    if (pngFile != null) {
                        val fileUri: Uri = getUriForFile(pngFile)
                        intent.putExtra(Intent.EXTRA_STREAM, fileUri)
                        intent.type = "image/*"

                    } else {
                        intent.type = "text/plain"
                    }
                }

                if (pngBytes == null) {
                    intent.type = "text/plain"
                }
            } else {
                intent.type = "text/plain"
            }

            try {
                if (intent.resolveActivity(packageManager) != null) {
                    smsLauncher.launch(intent)
                } else {
                    Timber.e("sendSMSWithCaptureImage() resolveActivity null")
                    sendJavaScript(
                        requestIdSms,
                        RETURN_ERROR,
                        JSONObject().put(PARAM_ERROR_CODE, "SMS_SHARING_FAILED")
                            .put(PARAM_ERROR_MESSAGE, "")
                    )
                }
            } catch (ex: Exception) {
                Timber.e("sendSMSWithCaptureImage() : $ex")
                sendJavaScript(
                    requestIdSms,
                    RETURN_ERROR,
                    JSONObject().put(PARAM_ERROR_CODE, "SMS_SHARING_FAILED")
                        .put(PARAM_ERROR_MESSAGE, ex.message)
                )
            }
        }
    }

    @SuppressLint("IntentReset")
    fun sendSMS(param: JSONObject) {
        Timber.d("param=${param}")

        requestIdSms = param.optString(REQUEST_ID)

        var phoneNum = param.optString(KEY_PHONE_NUM)
        val message = param.optString(KEY_MESSAGE)
        if (!phoneNum.isNullOrEmpty()) {
            phoneNum = phoneNum.replace("-", "").replace(" ", "")

            if (Build.MANUFACTURER.equals(KEY_SAMSUNG, true)) {
                phoneNum = phoneNum.replace(";", ",")
            }
        }

        val intent = Intent("android.intent.action.SENDTO").apply {
            addCategory("android.intent.category.DEFAULT")
            type = "vnd.android-dir/mms-sms"
            data = Uri.parse("sms:$phoneNum")
            putExtra("sms_body", message)
        }
        smsLauncher.launch(intent)
    }

    override fun onResume() {
        Timber.d("onResume() called")
        super.onResume()

        sensorManager?.registerListener(
            sensorListener, sensorManager!!.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER
            ), SensorManager.SENSOR_DELAY_NORMAL
        )

        callbackAppEvent(JSEventName.SCREEN, JSEventType.RESUME, JSONObject())
        
        // 포그라운드로 전환됨 - 서버에 상태 전송
        sendAppStateToServer("foreground")
        
        // GeminiLiveTranslationActivity가 종료되고 돌아온 경우 번역 모드 종료 처리
        endTranslationModeIfNeeded()
    }
    
    /**
     * GeminiLiveTranslationActivity가 종료되고 BaseWebViewActivity로 돌아온 경우
     * 번역 모드가 활성화되어 있으면 종료하도록 프론트엔드에 요청
     */
    private fun endTranslationModeIfNeeded() {
        try {
            // WebView가 로드되었는지 확인
            val webView = binding.webView
            val currentUrl = webView.url
            if (currentUrl.isNullOrEmpty()) {
                Timber.d("WebView not loaded yet, skipping translation mode end check")
                return
            }
            
            // JavaScript를 통해 번역 모드 상태 확인 및 종료 요청
            lifecycleScope.launch(Dispatchers.Main) {
                val jsCode = """
                    (function() {
                        try {
                            // 먼저 번역 모드가 활성화되어 있는지 확인
                            var isTranslationModeActive = false;
                            
                            // 번역 모드 상태 확인 방법들 시도
                            if (typeof window.isTranslationModeActive === 'function') {
                                isTranslationModeActive = window.isTranslationModeActive();
                            } else if (window.isTranslationModeActive !== undefined) {
                                isTranslationModeActive = window.isTranslationModeActive;
                            } else if (typeof window.translationModeActive !== 'undefined') {
                                isTranslationModeActive = window.translationModeActive;
                            }
                            
                            // 번역 모드가 활성화되어 있지 않으면 종료하지 않음
                            if (!isTranslationModeActive) {
                                return 'translation_mode_not_active';
                            }
                            
                            // 번역 모드가 활성화되어 있으면 종료 요청
                            // RTVI 클라이언트를 통해 end_translation_mode 메시지 전송
                            if (typeof window.sendTranslationModeEnd !== 'undefined' && typeof window.sendTranslationModeEnd === 'function') {
                                window.sendTranslationModeEnd();
                                return 'sent_via_custom_function';
                            }
                            // PipecatClient를 통해 메시지 전송
                            else if (window.pipecatClient && typeof window.pipecatClient.sendClientMessage === 'function') {
                                window.pipecatClient.sendClientMessage('end_translation_mode', {});
                                return 'sent_via_pipecat_client';
                            }
                            // RTVI 이벤트를 통해 메시지 전송
                            else if (window.client && typeof window.client.sendClientMessage === 'function') {
                                window.client.sendClientMessage('end_translation_mode', {});
                                return 'sent_via_rtvi_client';
                            }
                            return 'no_handler_found';
                        } catch (e) {
                            return 'error: ' + e.message;
                        }
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(jsCode) { result ->
                    val cleanResult = result?.removeSurrounding("\"")?.replace("\\\"", "\"")
                    Timber.d("Translation mode end request result: $cleanResult")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to end translation mode on resume")
        }
    }
    
    override fun onPause() {
        Timber.d("onPause() called")

        sensorManager!!.unregisterListener(sensorListener)

        callbackAppEvent(JSEventName.SCREEN, JSEventType.PAUSE, JSONObject())
        
        // 백그라운드로 전환됨 - 서버에 상태 전송
        sendAppStateToServer("background")

        super.onPause()
    }
    
    /**
     * 앱 상태(foreground/background)를 서버에 전송
     * WebView의 JavaScript를 통해 Daily.co 또는 RTVI로 메시지 전송
     */
    private fun sendAppStateToServer(state: String) {
        try {
            // NOTE:
            // Pipecat mobile client -> server app_state payload format (observed in server logs):
            //   { t: "app_state", d: { state: "background" | "foreground" } }
            // So we primarily route through the RTVI client API (sendClientMessage),
            // and keep "raw object" fallbacks for older frontends.
            val rtviType = "app_state"
            val rtviData = JSONObject().apply { put("state", state) }

            val legacyMessage = JSONObject().apply {
                put("type", rtviType)
                put("state", state)
            }

            val rtviTypeJson = JSONObject.quote(rtviType)
            val rtviDataJson = rtviData.toString()
                .replace("\\", "\\\\")  // 백슬래시 이스케이프
                .replace("\"", "\\\"")   // 따옴표 이스케이프
                .replace("\n", "\\n")    // 줄바꿈 이스케이프
                .replace("\r", "\\r")    // 캐리지 리턴 이스케이프

            val legacyMessageJson = legacyMessage.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")

            Timber.d("Sending app state to server: state=$state")
            
            // binding이 초기화되었는지 안전하게 확인하고 사용
            try {
                // binding이 초기화되지 않았으면 UninitializedPropertyAccessException 발생
                val webView = binding.webView
                
                // WebView가 로드되었는지 확인
                val currentUrl = webView.url
                if (currentUrl.isNullOrEmpty()) {
                    Timber.w("WebView not loaded yet, skipping app state message. URL: $currentUrl")
                    return
                }
                
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        // 여러 방법으로 메시지 전송 시도
                        val jsCode = """
                            (function() {
                                try {
                                    var result = 'no_handler_found';
                                    var type = $rtviTypeJson;
                                    var data = JSON.parse("$rtviDataJson");
                                    
                                    // 1) Preferred: RTVI client wrapper (matches server log shape).
                                    if (window.pipecatClient && typeof window.pipecatClient.sendClientMessage === 'function') {
                                        window.pipecatClient.sendClientMessage(type, data);
                                        result = 'sent_via_pipecat_client_sendClientMessage';
                                    } else if (window.client && typeof window.client.sendClientMessage === 'function') {
                                        window.client.sendClientMessage(type, data);
                                        result = 'sent_via_rtvi_client_sendClientMessage';
                                    }
                                    // 2) Optional custom hook for host apps.
                                    else if (typeof window.sendAppStateMessage === 'function') {
                                        window.sendAppStateMessage({ t: type, d: data });
                                        result = 'sent_via_custom_function';
                                    }
                                    // 3) Legacy fallbacks: Daily app message / generic message buses.
                                    else if (window.daily && typeof window.daily.sendAppMessage === 'function') {
                                        window.daily.sendAppMessage(JSON.parse("$legacyMessageJson"), '*');
                                        result = 'sent_via_daily_sendAppMessage';
                                    } else if (window.DailyIframe && typeof window.DailyIframe.sendAppMessage === 'function') {
                                        window.DailyIframe.sendAppMessage(JSON.parse("$legacyMessageJson"), '*');
                                        result = 'sent_via_daily_iframe_sendAppMessage';
                                    } else if (window.rtvi && typeof window.rtvi.sendMessage === 'function') {
                                        window.rtvi.sendMessage({ t: type, d: data });
                                        result = 'sent_via_rtvi_sendMessage';
                                    } else if (window.ws && window.ws.readyState === WebSocket.OPEN) {
                                        window.ws.send(JSON.stringify({ t: type, d: data }));
                                        result = 'sent_via_websocket';
                                    } else {
                                        var event = new CustomEvent('appStateChange', { detail: { t: type, d: data } });
                                        window.dispatchEvent(event);
                                        result = 'sent_via_custom_event';
                                    }
                                    
                                    return JSON.stringify({
                                        success: true,
                                        method: result,
                                        t: type,
                                        d: data
                                    });
                                } catch (e) {
                                    return JSON.stringify({
                                        success: false,
                                        error: e.message,
                                        stack: e.stack
                                    });
                                }
                            })();
                        """.trimIndent()

                        webView.evaluateJavascript(jsCode) { result ->
                            // result는 JSON 문자열로 반환되므로 파싱 필요
                            val cleanResult = result?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\n", "")
                            Timber.d("App state message send result: $cleanResult")
                            
                            if (cleanResult?.contains("no_handler_found") == true) {
                                Timber.w("No handler found for app state message")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send app state to server")
                    }
                }
            } catch (e: kotlin.UninitializedPropertyAccessException) {
                Timber.w("Binding not initialized, cannot send app state: ${e.message}")
                return
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating app state message")
        }
    }

    fun handleDynamicLinks() {
        // sample: https://sscp.page.link/?link=https://sscp.page.link/deeplink?pageid=56789&apn=com.lgcns.codeengine

        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener(this) { pendingDynamicLinkData: PendingDynamicLinkData? ->
                // Get deep link from result (may be null if no link is found)
                var deepLink: Uri? = null
                if (pendingDynamicLinkData != null) {
                    deepLink = pendingDynamicLinkData.link
                }

                if (deepLink != null) {
                    Timber.d("deepLink=$deepLink")
                    val pageid = Uri.parse(deepLink.toString()).getQueryParameter("pageid")
                    Timber.d("pageid=$pageid")
                    callbackAppEvent(
                        JSEventName.DEEPLINK,
                        JSEventType.MOVE,
                        JSONObject().put("pageid", pageid)
                    )
                }
            }
            .addOnFailureListener(this) { e -> Timber.w("getDynamicLink:onFailure $e") }
    }

    private fun now(): Date = Calendar.getInstance().time

    private fun getUriForFile(file: File): Uri =
        FileProvider.getUriForFile(applicationContext, "${packageName}.fileprovider", file)

    private fun getUriForCompressedFile(file: File): Uri {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val mediaFile = File(dir, file.name)
        if (mediaFile.parentFile?.exists() == false) mediaFile.parentFile?.mkdir()
        return FileProvider.getUriForFile(
            applicationContext,
            "${packageName}.fileprovider",
            mediaFile
        )
    }

    fun setProgress(isLoading: Boolean) = webDataViewModel.setProgress(isLoading)

    private fun observeDataAndEvent() {
        webDataViewModel.progress.collectRepeatOnStarted(this) { (isLoading, message) ->
            if (isLoading) showCommonLoading(message) else hideCommonLoading()
        }

        loginViewModel.loginResponse.observe(this) {
            it.let {
                when (it) {
                    is Result.Error -> sendJavaScript(requestId, RETURN_ERROR, JSONObject()
                        .put(PARAM_ERROR_CODE, "LOGIN_FAILED")
                        .put(PARAM_ERROR_MESSAGE, it.exception.message ?: "")
                    )
                    is Result.Success -> {
                        tokenViewModel.saveToken(it.data.token)
                        sendJavaScript(requestId, RETURN_OK, JSONObject().put("token", it.data.token))
                    }
                }
            }
        }
    }

    fun getPermissions(): JSONArray {
        val permissions = JSONArray()
        try {
            for (i in permissionList.indices) {
                val permission = JSONObject()
                permission.put(
                    permissionList[i].permissionName,
                    permissionList[i].pluginPermission.isGranted()
                )
                permissions.put(permission)
            }
        } catch (e: JSONException) {
            Timber.e(e.message)
        }

        return permissions
    }

    fun goAppSettings() {
        goAppDetailsSettings.launch(featurePermissionHelper.packageName)
    }

    fun shareRequest(
        title: String,
        text: String,
        chooserTitle: String,
        onResult: (Boolean) -> Unit
    ) {
        shareLauncher.launch(
            ShareModel(
                title = title,
                text = text,
                chooserTitle = chooserTitle
            ), onResult
        )
    }

    fun shareImageRequest(imageUrl: String, chooserTitle: String, onResult: (Boolean) -> Unit) {
        lifecycleScope.launch {

            var jpgBytes = ByteArray(0)
            try {
                val url = URL(imageUrl)
                val image = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeStream(url.openConnection().getInputStream())
                }
                jpgBytes = image?.compressJpeg() ?: ByteArray(0)
                Timber.d("jpgBytes= ${jpgBytes.size}")
                image?.recycle()
            } catch (e: IOException) {
                Timber.e(e)
            }

            val jpgFile: File? = webViewPluginUseCase.mediaRepository.saveToAppCache(
                source = jpgBytes,
                relativePath = "picture",
                filename = "screenshot_${now().format("yyyyMMdd_hhmmss")}.jpg",
            )

            if (jpgFile != null) {
                val fileUri: Uri = getUriForFile(jpgFile)
                shareImageLauncher.launch(
                    ShareImageModel(
                        imageUri = fileUri,
                        chooserTitle = chooserTitle
                    ), onResult
                )
            } else {
                onResult(false)
            }
        }
    }

    fun viewBarcode(
        barcodeType: BarcodeType,
        entity: QRBarCodeEntity,
        expireTime: Long = 0L,
        authToken: String = "",
        onResult: (Boolean) -> Unit
    ) {
        barcodeViewLauncher.launch(
            BarcodeModel(
                expireTime = expireTime,
                barcodeType = barcodeType,
                entity = entity,
                authToken = authToken
            ), onResult
        )
    }

    fun scanBarcode(requestId: String) {
        barcodeScanLauncher.launch(Intent(this, com.lgcns.codeengine.activity.BarcodeDetectActivity::class.java).apply {
            putExtra(REQUEST_ID, requestId)
        })
    }

    fun viewImageEdit(actionType: String, maxSize: Int, onResult: (Uri?) -> Unit) {
        editImageLauncher.launch(
            ImageEditModel(
                actionType,
                maxSize
            ), onResult
        )
    }

    @SuppressLint("IntentReset")
    fun selectMedia(mediaType: MediaType, fileChooserParams: WebChromeClient.FileChooserParams?) {
        val intentCamera: Intent?

        val currentDate = now().format("yyyyMMdd_hhmmss")
        val fileName: String?
        val destDir: String?
        when (mediaType) {
            MediaType.VIDEO -> {
                fileName = "camera_$currentDate.mp4"
                destDir = cacheDir.absolutePath + "/movie"

                intentCamera = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            }

            MediaType.PICTURE -> {
                fileName = "camera_$currentDate.jpg"
                destDir = cacheDir.absolutePath + "/picture"

                intentCamera = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            }

            else -> {
                Timber.e("Unhandled case.")
                return
            }
        }

        val file = File(destDir, fileName)
        file.parentFile?.mkdirs()

        // File 객체의 URI 를 얻는다. 갤러리 선택이 아닌 카메라로 찍는 경우의 파일명
        cameraMediaUri = getUriForFile(file)

        Timber.d("fileChooser file = ${file.path}")
        Timber.d("fileChooser file.exists() = ${file.exists()}")
        Timber.d("fileChooser cameraImageUri = ${cameraMediaUri.path}")

        intentCamera.putExtra(MediaStore.EXTRA_OUTPUT, cameraMediaUri)

        val pickIntent = Intent(Intent.ACTION_PICK)
        val pickTitle: String
        when (mediaType) {
            MediaType.VIDEO -> {
                pickIntent.type = MediaStore.Video.Media.CONTENT_TYPE
                pickIntent.data = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                if (fileChooserParams?.mode == MODE_OPEN_MULTIPLE) pickIntent.putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    true
                )
                pickTitle = getString(R.string.choose_the_way_to_get_video)
                val chooserIntent = Intent.createChooser(pickIntent, pickTitle)
                // 카메라 intent 포함
                chooserIntent.putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    arrayOf<Parcelable>(intentCamera)
                )
                selectVideoLauncher.launch(chooserIntent)
            }

            MediaType.PICTURE -> {
                pickIntent.type = MediaStore.Images.Media.CONTENT_TYPE
                pickIntent.data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                if (fileChooserParams?.mode == MODE_OPEN_MULTIPLE) pickIntent.putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    true
                )
                pickTitle = getString(R.string.choose_the_way_to_get_picture)
                val chooserIntent = Intent.createChooser(pickIntent, pickTitle)
                // 카메라 intent 포함
                chooserIntent.putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    arrayOf<Parcelable>(intentCamera)
                )
                selectImageLauncher.launch(chooserIntent)
            }

            else -> {
                Timber.e("Unhandled case.")
                return
            }
        }
    }

    private fun compressVideo(receivedData: Intent?) {
        lifecycleScope.launch {
            val vNames = ArrayList<String>()
            for (i in 0 until attachedUris.size) {
                val name = "cv_${now().format("yyyyMMdd_hhmmss")}_$i"
                Timber.d("vNames: $name")
                vNames.add(name)
            }

            VideoCompressor.start(
                context = applicationContext,
                attachedUris,
                isStreamable = false,
                sharedStorageConfiguration = SharedStorageConfiguration(
                    saveAt = SaveLocation.movies,
                ),
                configureWith = Configuration(
                    quality = VideoQuality.LOW,
                    isMinBitrateCheckEnabled = true,
                    videoNames = vNames
                ),
                listener = object : CompressionListener {
                    override fun onProgress(index: Int, percent: Float) {
                        //Update UI
                        if (percent <= 100 && percent.toInt() % 5 == 0)
                            runOnUiThread {
                                attachedData[index] = VideoDetailsModel(
                                    "",
                                    attachedUris[index],
                                    "",
                                    percent
                                )
                                hideCommonLoading()
                                val message =
                                    getString(R.string.video_compressing) + " ${percent.toInt()}%"
                                //Timber.d(message)
                                showCommonLoading(message)
                            }
                    }

                    override fun onStart(index: Int) {
                        Timber.d("onStart() index=$index")

                        attachedData.add(
                            index,
                            VideoDetailsModel("", attachedUris[index], "")
                        )
                        Timber.d("Compressing.. ")
                        runOnUiThread {
                            hideCommonLoading()
                            showCommonLoading(getString(R.string.video_compressing))
                        }
                    }

                    override fun onSuccess(index: Int, size: Long, path: String?) {
                        attachedData[index] = VideoDetailsModel(
                            path,
                            attachedUris[index],
                            getFileSize(size),
                            100F
                        )
                        Timber.d("Succeed!! fileSize=${attachedData[index].newSize}")
                        runOnUiThread {
                            hideCommonLoading()
                        }
                        // 압축한 파일의 Uri를 Web App에 전달합니다.

                        val mediaUri = getUriForCompressedFile(File(path ?: ""))
                        Timber.d("onSuccess() index=$index, path=$path, uri=$mediaUri")
                        attachedResultUris.add(mediaUri)
                        if (attachedResultUris.size == attachedUris.size) {
                            Timber.d("total size = ${attachedResultUris.size}")
                            mediaValueCallback?.onReceiveValue(attachedResultUris.toTypedArray())
                            mediaValueCallback = null
                        }
                    }

                    override fun onFailure(index: Int, failureMessage: String) {
                        Timber.e(failureMessage)
                        runOnUiThread {
                            hideCommonLoading()
                        }

                        // File Size가 작은 등의 이유로 압축 실패가 나서,
                        // 압축하지 않은 원래 Uri를 Web App에 전달합니다.
                        attachedResultUris.add(attachedUris[index])
                        Timber.d("onFailure() index=$index, path=${attachedUris[index].path}, uri=${attachedUris[index]}")
                        if (attachedResultUris.size == attachedUris.size) {
                            Timber.d("total size = ${attachedResultUris.size}")
                            mediaValueCallback?.onReceiveValue(attachedResultUris.toTypedArray())
                            mediaValueCallback = null
                        }
                    }

                    override fun onCancelled(index: Int) {
                        Timber.w("compression has been cancelled")
                        runOnUiThread {
                            hideCommonLoading()
                        }
                        // make UI changes, cleanup, etc

                        // 어떤 이유로 압축 과정 중 취소가 됐다면,
                        // 압축하지 않은 원래 Uri를 Web App에 전달합니다.
                        mediaValueCallback?.onReceiveValue(
                            WebChromeClient.FileChooserParams.parseResult(
                                RESULT_OK,
                                receivedData
                            )
                        )
                        mediaValueCallback = null
                    }
                },
            )
        }
    }

    private fun getFileSize(size: Long): String {
        if (size <= 0)
            return "0"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

        return DecimalFormat("#,##0.#").format(
            size / 1024.0.pow(digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }

    fun login(requestId: String, auth: Auth) {
        this.requestId = requestId

        loginViewModel.login(auth)
    }

    fun logout(requestId: String) {
        tokenViewModel.deleteToken()

        sendJavaScript(requestId, RETURN_OK, JSONObject())
    }

    fun getToken(requestId: String) {
        lifecycleScope.launch {
            val token = tokenViewModel.getToken()
            sendJavaScript(requestId, RETURN_OK, JSONObject().put("token", token))
        }
    }

    fun updateToken(requestId: String, token: String) {
        lifecycleScope.launch {
            val token = tokenViewModel.saveToken(token)
            sendJavaScript(requestId, RETURN_OK, JSONObject())
        }
    }

    fun saveNativeToken(token: String) {
        tokenViewModel.saveToken(token)
    }

    private fun setBiometricPrompt() {
        val thisContext : Context = this
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    Timber.e("Authentication error: $errString, error code: $errorCode")

                    if (errorCode == 11 && BiometricUtility.deviceHasPasswordPinLock(thisContext)) {
                        // 지문, 얼굴이 모두 등록이 안되어 있는데 PIN 또는 Pattern 인증은 할 수 있는 경우
                        showPINorPatternPrompt()
                    } else {
                        sendJavaScript(
                            requestId,
                            RETURN_ERROR,
                            JSONObject().put(PARAM_ERROR_CODE, "AUTHENTICATION_ERROR")
                                .put(PARAM_ERROR_MESSAGE, errString)
                        )
                    }
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    Timber.d("Authentication succeeded.")
                    sendJavaScript(requestId, RETURN_OK, JSONObject())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Timber.d("Authentication failed")
                    //@Note 위에서는 한개의 requestId로 한 번만 처리하므로 여러 번 response가 올라가면 안됨.
                    /*sendJavaScript(
                        requestId,
                        RETURN_ERROR,
                        JSONObject().put(PARAM_ERROR_CODE, "AUTHENTICATION_FAILED")
                    )*/
                }
            })
    }

    private fun initBiometricPrompt() {
        var setDeviceCred = false
        if (BiometricUtility.isBiometricHardWareAvailable(this) != HARDWARE_IS_OK &&
            BiometricUtility.deviceHasPasswordPinLock(this)
        ) setDeviceCred = true

        if (setDeviceCred) {
            showPINorPatternPrompt()
        } else {
            promptInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { //Android 11 이상에서 생체인증이 잠긴경우
                    PromptInfo.Builder()
                        .setTitle(getString(R.string.biometric_authentication))
                        .setSubtitle(getString(R.string.biometric_authentication_subtitle))
                        .setDescription(getString(R.string.biometric_authentication_description))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) // BIOMETRIC_STRONG : 지문, 얼굴 인식 모두 해당하나 디바이스 센서의 보안 수준에 따라 포함 범위 다름
                        .setConfirmationRequired(false) // 명시적인 사용자 작업 ( 생체 인식 전 한번 더 체크 ) 없이 인증할건지 default : true
                        .build()
                } else {
                    PromptInfo.Builder()
                        .setTitle(getString(R.string.biometric_authentication))
                        .setSubtitle(getString(R.string.biometric_authentication_subtitle))
                        .setDescription(getString(R.string.biometric_authentication_description))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK) // BIOMETRIC_STRONG : 지문, 얼굴 인식 모두 해당하나 디바이스 센서의 보안 수준에 따라 포함 범위 다름
                        .setConfirmationRequired(false) // 명시적인 사용자 작업 ( 생체 인식 전 한번 더 체크 ) 없이 인증할건지 default : true
                        .setNegativeButtonText(getString(R.string.cancel_button))
                        .build()
                }
        }
    }

    // 잠금해제 패턴 또는 PIN
    private fun showPINorPatternPrompt() {
        /* For API level >= 30
          Newer API setAllowedAuthenticators is used */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authFlag =
                BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_STRONG

            promptInfo = PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_authentication))
                .setSubtitle("")
                .setDescription(getString(R.string.biometric_authentication_description))
                .setAllowedAuthenticators(authFlag)
                .build()
        } else {
            /* SetDeviceCredentials method deprecation is ignored here
              as this block is for API level < 30 */
            @Suppress("DEPRECATION")
            promptInfo = PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_authentication))
                .setSubtitle("")
                .setDescription(getString(R.string.biometric_authentication_description))
                .setDeviceCredentialAllowed(true)
                .build()
        }
    }

    fun showBiometricPrompt(requestId: String) {
        this.requestId = requestId

        val isHardwareAvailable = BiometricUtility.isBiometricHardWareAvailable(this)

        if (isHardwareAvailable != HARDWARE_IS_OK &&
            !BiometricUtility.deviceHasPasswordPinLock(this)
        ) {
            Timber.w("The device doesn't have BiometricFeatures, and FingerPrint function.")

            if (isHardwareAvailable == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                (this@BaseWebViewActivity as WebViewActivity).showCustomConfirmDialog(
                    getString(R.string.need_to_enroll_biometric),
                    object : ConfirmResult {
                        override fun confirm() {
                            Timber.d("confirm()")
                            val intent: Intent?
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                                intent.putExtra(
                                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                                )
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                @Suppress("DEPRECATION")
                                intent = Intent(Settings.ACTION_FINGERPRINT_ENROLL)
                            } else {
                                intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            }

                            this@BaseWebViewActivity.startActivity(intent)
                        }

                        override fun cancel() {
                            Timber.d("cancel()")
                        }
                    })
                return
            }

            sendJavaScript(
                requestId,
                RETURN_ERROR,
                JSONObject().put(PARAM_ERROR_CODE, "AUTHENTICATION_UNAVAILABLE")
                    .put(
                        PARAM_ERROR_MESSAGE,
                        "The device doesn't have BiometricFeatures, and FingerPrint function."
                    )
            )
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    fun cancelBiometricPrompt(requestId: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            biometricPrompt.cancelAuthentication()

            sendJavaScript(requestId, RETURN_OK, JSONObject())
        }
    }

    fun selectFile(
        mimeTypes: List<String>,
        isMultiple: Boolean,
        onResult: (Intent?) -> Unit
    ) {
        Timber.d("mimeTypes:$mimeTypes, isMultiple:$isMultiple")
        fileSelectLauncher.launch(
            FileSelectModel(
                mimeTypes = mimeTypes,
                isMultiple = isMultiple
            ), onResult
        )
    }

    fun isNetworkAvailable(context: Context): Boolean {

        // register activity with the connectivity manager service
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Returns a Network object corresponding to
        // the currently active default data network.
        val network = connectivityManager.activeNetwork ?: return false

        // Representation of the capabilities of an active network.
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            // Indicates this network uses a Wi-Fi transport,
            // or WiFi has network connectivity
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

            // Indicates this network uses a Cellular transport. or
            // Cellular has network connectivity
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

            // else return false
            else -> false
        }
    }

    fun readNfcTag(requestId: String) {
        Timber.d("readNfcTag()")
        nfcTagReadLauncher.launch(Intent(this, NfcTagActivity::class.java).apply {
            putExtra(REQUEST_ID, requestId)
        })
    }

    private fun getAppSignatures(context: Context): List<String> {
        val appCodes = mutableListOf<String>()

        try {
            val packageName = context.packageName
            val packageManager = context.packageManager
            @Suppress("DEPRECATION") val signatures =
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            for (signature in signatures) {
                val hash = getHash(packageName, signature.toCharsString())

                if (hash != null) {
                    appCodes.add(String.format("%s", hash))
                }

                Timber.d("The hash value of this app. : $hash")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("Unable to find package to obtain hash. : $e")
        }

        return appCodes
    }

    private fun getHash(packageName: String, signature: String): String? {
        val hashType = "SHA-256"
        val numHashedBytes = 9
        val numBase64Char = 11

        val appInfo = "$packageName $signature"

        try {
            val messageDigest = MessageDigest.getInstance(hashType)

            messageDigest.update(appInfo.toByteArray(StandardCharsets.UTF_8))

            val hashSignature = Arrays.copyOfRange(messageDigest.digest(), 0, numHashedBytes)
            val base64Hash = android.util.Base64
                .encodeToString(
                    hashSignature,
                    android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                )
                .substring(0, numBase64Char)

            Timber.d("Package : $packageName, Hash : $base64Hash")

            return base64Hash

        } catch (e: NoSuchAlgorithmException) {
            Timber.e("hash:NoSuchAlgorithm : $e")
        }

        return null
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerSmsReceiver() {
        registerReceiver(smsReceiver, smsReceiver.doFilter())

        val task = SmsRetriever.getClient(this)
            .startSmsRetriever()

        task.addOnSuccessListener {
            Timber.d("addOnSuccessListener ${getAppSignatures(this)}")
        }

        task.addOnFailureListener {
            Timber.e("addOnFailureListener $it")
        }
    }

    fun unregisterSmsReceiver() {
        unregisterReceiver(smsReceiver)
    }

    /*
    ** 아래와 같은 형식으로 발송하면 됨. 인증번호는 6자리 고정임.
    * <#>[인증번호:707123]-LG CNS gPTRFeBtvpQ
     */
    class SMSReceiver(activity: BaseWebViewActivity?) : BroadcastReceiver() {
        private val thisActivity = activity

        constructor() : this(null)

        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                @Suppress("DEPRECATION") val status =
                    extras?.getParcelable<Status>(SmsRetriever.EXTRA_STATUS)

                when (status?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                        Timber.d("onReceive() SUCCESS: $message")

                        if (!message.isNullOrEmpty()) {
                            val num = message.substring(message.indexOf(":")+1, message.length).trim().substring(0, 6)

                            thisActivity?.callbackAppEvent(
                                JSEventName.SMS,
                                JSEventType.AUTH_NUM,
                                JSONObject().putInt("num", num.toInt())
                            )
                        }
                    }

                    CommonStatusCodes.TIMEOUT -> {
                        Timber.d("onReceive() TIMEOUT")
                    }
                }
            }
        }

        fun doFilter(): IntentFilter = IntentFilter().apply {
            addAction(SmsRetriever.SMS_RETRIEVED_ACTION)
        }
    }

    @Suppress("DEPRECATION")
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta
            //Timber.d("onSensorChanged() $acceleration")
            if (acceleration > 18) {
                Timber.d("Shake event detected")

                lifecycleScope.launch {
                    callbackAppEvent(JSEventName.MOTION, JSEventType.SHAKE, JSONObject())

                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                500,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        vibrator.vibrate(500)
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    /*
    fun setWindowFlag(activity: Activity, bits: Int, on: Boolean) {
        val win = activity.window
        val winParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }
     */

    companion object {
        const val BRIDGE_NAME = "CodeEngineBridge" // 하이브리드 웹에서 네이티브 플러그인을 호출할 때 사용되는 이름

        const val REQUEST_ID = "requestId"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val LOAD_URL = "LOAD_URL"
        const val LOAD_DATA = "LOAD_DATA"
        const val PUSH_DATA = "PUSH_DATA"
        const val PREV_URL = "PREV_URL"
        const val SHOW_CLOSE_BUTTON = "SHOW_CLOSE_BUTTON"
        const val BARCODE_VALUE = "BARCODE_VALUE"

        const val METHOD = "method"
        const val POST = "post"
        const val GET = "get"
        const val GET_PARAM = "GET_PARAM"

        const val NFCTAG_VALUE = "NFCTAG_VALUE"

        // For SMSPlugin Start. /////////////////////////////////
        private const val KEY_PHONE_NUM = "phoneNum"
        private const val KEY_MESSAGE = "message"
        private const val KEY_IMAGE_YN = "imageYn"
        private const val KEY_SMS_BODY = "sms_body"
        private const val KEY_ADDRESS = "address"
        private const val KEY_SAMSUNG = "Samsung"
        // For SMSPlugin End. /////////////////////////////////

    }
}
