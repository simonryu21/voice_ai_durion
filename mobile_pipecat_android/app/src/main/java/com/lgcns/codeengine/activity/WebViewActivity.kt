package com.lgcns.codeengine.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import androidx.activity.result.ActivityResult
import androidx.lifecycle.lifecycleScope
import com.google.firebase.dynamiclinks.PendingDynamicLinkData
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.ktx.Firebase
import com.lgcns.codeengine.BuildConfig
import com.lgcns.codeengine.R
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.ui.CustomDialogFragment
import com.lgcns.codeengine.ui.common.CustomSnackBar
import com.lgcns.codeengine.ui.common.SoftKeyboardVisibilityHelper
import com.lgcns.codeengine.util.isLocalDevUrl
import com.lgcns.codeengine.util.extension.captureBitmap
import com.lgcns.codeengine.util.extension.captureFullBitmap
import com.lgcns.codeengine.web.*
import com.lgcns.codeengine.web.params.JSCallback
import com.lgcns.codeengine.web.params.JSEventCallback
import com.lgcns.codeengine.web.params.JSEventName
import com.lgcns.codeengine.web.params.JSEventType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.coroutines.resume

/**
 * WebView Plugin을 위한 Activity입니다.
 * Plugin에서 Activity를 참조하기 위한 코드는 parent abstract class인 BaseWebViewActivity에 구현되어 있습니다.
 * plugin.js에서 CodeEngineBridge.postMessage()를 호출하면 WebAppBridge의 postMessage()가 호출되고,
 * 결과는 plugin.js의 callbackListener() 함수로 호출됩니다.
 * 호출할때 requestId를 저장해서 callback 함수 instance를 비교하기 때문에
 * 비동기 호출이 여러번 호출되어도 호출한 callback 함수 instance로 return되도록 되어있습니다.
 */
@AndroidEntryPoint
open class WebViewActivity : BaseWebViewActivity() {

    override var layoutResourceId = R.layout.activity_web_view

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    private var pushData: String? = null

    private var gestureDetector: GestureDetector? = null

    lateinit var baseUrl: String
    private val webAssetPrefs by lazy { getSharedPreferences("web_assets", MODE_PRIVATE) }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            baseUrl = if (BuildConfig.DEBUG) {
                dataStoreRepository.getDataStoreStringOnce(DataStoreRepository.REACT_TEST_URL)
                    ?: BuildConfig.BASE_URL
            } else {
                BuildConfig.BASE_URL
            }

            maybeInvalidateWebAssets(baseUrl)

            // 직전 페이지에서 데이터가 넘어온 경우
            intent?.getStringExtra(EXTRA_DATA)?.let { params -> setWebData(params) }

            // FCM Push Message로 PUSH_URL이 왔으면 해당 URL로 이동
            val url = intent?.getStringExtra(LOAD_URL) ?: BuildConfig.WEBVIEW_PLUGIN_TEST

            // swipe event를 처리하기 위해 GestureDetector를 사용
            gestureDetector = GestureDetector(this@WebViewActivity, GestureListener())

            binding.webView.setOnTouchListener { _, event ->
                gestureDetector?.onTouchEvent(event)

                return@setOnTouchListener false
            }

                // FCM Push Message로 PUSH_DATA가 왔으면 resume시 Web App으로 전달
            if (intent?.getStringExtra(PUSH_DATA) != null) {
                pushData = intent?.getStringExtra(PUSH_DATA)
                Timber.d("pushData=$pushData")
            }

            // REQUEST_ID가 있으면 Activity가 종료될 때 해당 REQUEST_ID를 넘겨주고 Web App에 callback을 보낸다.
            intent?.getStringExtra(REQUEST_ID)?.let { requestId = it }

            initialize(intent)

            initWebView()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!pluginPermissions.notificationPermission.grant()) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // Firebase Dynamiclinks로 넘어온 경우 체크
        // sample: https://ssgd.page.link?screenId=SYS0001
        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener(this@WebViewActivity) { pendingDynamicLinkData: PendingDynamicLinkData? ->
                // Get deep link from result (may be null if no link is found)
                var deepLink: Uri? = null
                if (pendingDynamicLinkData != null) {
                    deepLink = pendingDynamicLinkData.link
                }

                if (deepLink != null) {
                    Timber.d("deepLink=$deepLink")
                    var lastUrl = baseUrl

                    Timber.d("initWebView() lastUrl=$lastUrl")

                    loadUrl(lastUrl, GET)
                }
            }
            .addOnFailureListener(this@WebViewActivity) { e ->
                Timber.w("getDynamicLink:onFailure $e")
            }
    }

    private fun initialize(intent: Intent?) {
        Timber.d("initialize()")

        var url:String? = null
        if (binding.webView.loadedUrl.isEmpty()) { // WebViewActivity 처음 실행시 (즉, onCreate() 시)
            url = if (intent?.getStringExtra(LOAD_URL) != null) {
                intent.getStringExtra(LOAD_URL)
            } else if (intent?.data != null) {
                val appendUrl =
                    when(intent.data.toString()) {
                        "SHORTCUT_MENU1" -> "?screenId=SEA0001"
                        "SHORTCUT_MENU2" -> "?screenId=SBM0003"
                        "SHORTCUT_MENU3" -> "?screenId=MYP0003"
                        else -> "my-page/coupon"
                    }
                baseUrl + appendUrl
            } else {
                baseUrl
            }
        } else { // 기존 WebViewActivity 재사용시 (onNewIntent() 시)
            if (intent?.getStringExtra(LOAD_URL) != null && intent.getStringExtra(LOAD_URL) != binding.webView.loadedUrl) {
                Timber.d("before url=${binding.webView.loadedUrl}, after url=${intent.getStringExtra(LOAD_URL)}")
                url = intent.getStringExtra(LOAD_URL)
            }
        }
        Timber.d("initialize() url = $url")

        if (!isNetworkAvailable(this)) { // 네트워크 연결이 안됐을 때
            val errIntent = Intent(this@WebViewActivity, ErrorShowActivity::class.java)
            errIntent.putExtra(ErrorShowActivity.ERROR_CODE, -2)
            errIntent.putExtra(LOAD_URL, url)
            startActivity(errIntent)
            return
        }

        if (intent?.hasExtra("com.google.firebase.dynamiclinks.DYNAMIC_LINK_DATA") == false) { //Dynamic Link 가 아니면
            if (intent.getStringExtra(PUSH_DATA) != null) {
                // 푸시 알림, ActiveNotice 등 Push Data가 있으면
                Timber.d("PUSH_DATA=${intent.getStringExtra(PUSH_DATA)}, loadedUrl=${binding.webView.loadedUrl}, baseUrl=$baseUrl")
                if (binding.webView.loadedUrl.isEmpty() || !binding.webView.loadedUrl.startsWith(baseUrl)) {
                    // WebViewActivity가 처음 실행시 (즉, onCreate() 시) 또는 기존 페이지가 in-bound SPA 화면이 아니면
                    var fullUrl = "$baseUrl?$PUSH_DATA=$pushData"
                    Timber.d("load full url=$fullUrl")
                    loadUrl(fullUrl, intent.getStringExtra(METHOD) ?: GET, intent.getStringExtra(GET_PARAM) ?: "")
                } else { // 기존 WebViewActivity 재사용시 (onNewIntent()로 오는 경우)
                    callbackAppEvent(
                        JSEventName.PUSH, JSEventType.MESSAGE,
                        JSONObject().put(PUSH_DATA, intent.getStringExtra(PUSH_DATA))
                    )
                }
            } else {
                // 일반적인 url loading (dynamic link도 아니고 푸시도 아님)
                if (url != null) loadUrl(url, intent.getStringExtra(METHOD) ?: GET, intent.getStringExtra(GET_PARAM) ?: "")
            }
        }
    }

    private fun initWebView() {

        binding.webView.settings.mixedContentMode = 0
        binding.webView.settings.offscreenPreRaster = true
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        Timber.d("isHardwareAccelerated=${binding.webView.isHardwareAccelerated}")

        binding.webView.webViewActivity = this@WebViewActivity
        binding.webView.addJavascriptInterface(webAppBridge, BRIDGE_NAME)
        setWebContentsDebuggingEnabled()

        if (intent?.getBooleanExtra(SHOW_CLOSE_BUTTON, false) == true) {
            binding.closeBtn.visibility = View.VISIBLE
            binding.closeBtn.setOnClickListener {
                finish()
            }
        }
    }

    /**
     * Outbound 링크를 HandoffWebViewActivity로 열고, 돌아온 후에는
     * WebRTC/RTVI 재연결이 성공한 뒤에만 server로 관련 메시지를 전달한다.
     */
    fun openExternalUrlViaHandoff(url: String): Boolean {
        // OAuth는 "새 창/새 Activity"로 뜨면 안되므로 handoff로 보내지 않는다.
        // (OAuth 판별은 CustomWebView에서 선행됨)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        // 로컬 dev 서버(localhost/127.0.0.1/10.0.2.2 등)는 "우리 화면"이므로 handoff 대상에서 제외
        if (isLocalDevUrl(url)) return false
        if (baseUrl.isNotEmpty() && url.startsWith(baseUrl)) return false

        try {
            // JS → React → RTVI로 "opened" 이벤트 전달 (LLM이 컨텍스트로 인지)
            getWebView().evaluateJavascript(
                "window.onHandoffOpened({url: ${JSONObject.quote(url)}});",
                null,
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify JS about handoff opened")
        }

        return try {
            val intent = Intent(this, HandoffWebViewActivity::class.java).apply {
                putExtra(HandoffWebViewActivity.EXTRA_HANDOFF_ID, "")
                putExtra(HandoffWebViewActivity.EXTRA_URL, url)
                putExtra(HandoffWebViewActivity.EXTRA_KIND, "general")
                putExtra(HandoffWebViewActivity.EXTRA_VENDOR, "")
                putExtra(HandoffWebViewActivity.EXTRA_INTENT_SUMMARY, "")
            }

            launchHandoffWebView(intent) { result: ActivityResult ->
                handleHandoffResultAfterReconnect(result)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to open external URL via handoff")
            false
        }
    }

    private fun handleHandoffResultAfterReconnect(result: ActivityResult) {
        val data = result.data
        val isOk = result.resultCode == Activity.RESULT_OK && data != null

        val screenshotBase64 =
            if (isOk) data?.getStringExtra(HandoffWebViewActivity.RESULT_SCREENSHOT_BASE64) ?: "" else ""
        val screenshotUri =
            if (isOk) data?.getStringExtra(HandoffWebViewActivity.RESULT_SCREENSHOT_URI) ?: "" else ""
        val pageUrl = if (isOk) data?.getStringExtra(HandoffWebViewActivity.RESULT_PAGE_URL) ?: "" else ""
        val pageTitle = if (isOk) data?.getStringExtra(HandoffWebViewActivity.RESULT_PAGE_TITLE) ?: "" else ""

        val jsData = JSONObject().apply {
            put("handoff_id", "")
            if (isOk) {
                put("screenshot_base64", screenshotBase64)
                put("screenshot_uri", screenshotUri)
                put("page_url", pageUrl)
                put("page_title", pageTitle)
            }
        }

        lifecycleScope.launch {
            val connected = ensurePipecatConnected(timeoutMs = 10_000)
            Timber.d("handoff back: ensurePipecatConnected=$connected")

            val jsCall = if (isOk) {
                "window.sendHandoffScreenshot($jsData);"
            } else {
                "window.cancelHandoffBack($jsData);"
            }

            if (!connected) {
                Timber.w("handoff back: reconnect not confirmed; still delivering JS event (may be queued by frontend)")
            }

            withContext(Dispatchers.Main) {
                try {
                    getWebView().evaluateJavascript(jsCall, null)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deliver handoff result to JS")
                }
            }
        }
    }

    private suspend fun ensurePipecatConnected(timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()

        // Kick reconnect if available and not already connected.
        val preState = getPipecatConnectionState()
        Timber.d("ensurePipecatConnected() preState=$preState")
        if (preState != "connected") {
            runCatching {
                evalJs(
                    """
                    (function() {
                      try {
                        if (typeof window.reconnect === 'function') { window.reconnect(); return 'called_reconnect'; }
                        if (typeof window.connect === 'function') { window.connect(); return 'called_connect'; }
                        return 'no_reconnect_hook';
                      } catch (e) { return 'error:' + (e && e.message ? e.message : e); }
                    })();
                    """.trimIndent(),
                )
            }.onFailure { e -> Timber.w(e, "Failed to invoke window.reconnect/connect") }
        }

        while (System.currentTimeMillis() - start < timeoutMs) {
            val state = getPipecatConnectionState()
            if (state == "connected") return true
            delay(250)
        }
        return getPipecatConnectionState() == "connected"
    }

    private suspend fun getPipecatConnectionState(): String {
        val raw = evalJs(
            """
            (function() {
              try { return window.pipecatConnectionState || 'unknown'; } catch (e) { return 'unknown'; }
            })();
            """.trimIndent(),
        )
        // WebView returns JSON-encoded strings like "\"connected\"" or "null"
        val cleaned = raw
            ?.trim()
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            ?.replace("\\\"", "\"")
            ?: "unknown"
        return cleaned
    }

    private suspend fun evalJs(js: String): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                try {
                    binding.webView.evaluateJavascript(js) { value ->
                        if (cont.isActive) cont.resume(value)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "evalJs failed")
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("onNewIntent()")

        initialize(intent)

        // Firebase Dynamiclinks로 넘어온 경우 체크
        // sample: https://ssgd.page.link?screenId=SYS0001
        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener(this@WebViewActivity) { pendingDynamicLinkData: PendingDynamicLinkData? ->
                // Get deep link from result (may be null if no link is found)
                var deepLink: Uri? = null
                if (pendingDynamicLinkData != null) {
                    deepLink = pendingDynamicLinkData.link
                }

                if (deepLink != null) {
                    Timber.d("deepLink=$deepLink")

                    val screenId = deepLink.getQueryParameter("screenId") ?: ""
                    val screenDetail = deepLink.getQueryParameter("screenDetail") ?: ""
                    Timber.d("screenId=$screenId, screenDetail=$screenDetail")

                    callbackAppEvent(
                        JSEventName.DEEPLINK,
                        JSEventType.MOVE,
                        JSONObject()
                            .put("screenId", screenId)
                            .put("screenDetail", screenDetail)
                    )
                }
            }
            .addOnFailureListener(this@WebViewActivity) { e ->
                Timber.w("getDynamicLink:onFailure $e")
            }

    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setWebContentsDebuggingEnabled() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /**
     * 네이티브 -> 하이브리드 호출 할 때 사용.
     */
    override fun sendJavaScript(requestId: String, resultCode: String, params: JSONObject) {
        val result = JSCallback(
            requestId = requestId,
            resultCode = resultCode,
            params = params.toString()
        )

        val jsonString = pluginContext.json.encodeToString(result)

        lifecycleScope.launch {
            // Some web entrypoints (e.g. Pipecat console) may not define callbackListener().
            // Guard to avoid JS runtime errors that can break subsequent bridge calls.
            val js = """
                (function() {
                  try {
                    if (typeof window.callbackListener === 'function') {
                      window.callbackListener($jsonString);
                      return 'ok';
                    }
                    console.warn('[AndroidBridge] callbackListener is not defined');
                    return 'missing_callbackListener';
                  } catch (e) {
                    console.error('[AndroidBridge] callbackListener failed', e);
                    return 'error';
                  }
                })();
            """.trimIndent()
            binding.webView.evaluateJavascript(js) {
                Timber.d("sendJavaScript: $jsonString")
            }
        }
    }

    override fun callbackAppEvent(
        eventName: JSEventName,
        eventType: JSEventType,
        params: JSONObject,
    ) {

        val result = JSEventCallback(
            eventName = eventName.type,
            eventType = eventType.type,
            params = params.toString()
        )

        val jsonString = pluginContext.json.encodeToString(result)

        lifecycleScope.launch {
            // Some web entrypoints (e.g. Pipecat console) may not define callbackAppEvent().
            // Guard to avoid Uncaught ReferenceError and prevent breaking later JS execution.
            val js = """
                (function() {
                  try {
                    if (typeof window.callbackAppEvent === 'function') {
                      window.callbackAppEvent($jsonString);
                      return 'ok';
                    }
                    console.warn('[AndroidBridge] callbackAppEvent is not defined');
                    return 'missing_callbackAppEvent';
                  } catch (e) {
                    console.error('[AndroidBridge] callbackAppEvent failed', e);
                    return 'error';
                  }
                })();
            """.trimIndent()
            binding.webView.evaluateJavascript(js) {
                Timber.d("callbackAppEvent: $jsonString")
            }
        }
    }

    override fun clearWebViewCache() {
        binding.webView.clearCache(true)
    }

    override fun onStart() {
        super.onStart()
        Timber.d("onStart hashCode: ${hashCode()}")
    }

    override fun onDestroy() {
        Timber.d("onDestroy()")

        binding.webView.removeJavascriptInterface(BRIDGE_NAME)
        binding.webView.removeAllViews()
        binding.webView.destroy()

        super.onDestroy()
    }

    override fun moveWithUrl(requestId: String, url: String, data: String, showCloseButton: Boolean) {
        Timber.d("moveWithUrl requestId : $requestId / url : $url / data : $data")

        if (url.startsWith(baseUrl)) {
            Timber.d("Inbound move")
            navigatorLauncher.launch(Intent(this, WebViewActivity::class.java).apply {
                putExtra(LOAD_URL, url)
                putExtra(EXTRA_DATA, data)
                putExtra(REQUEST_ID, requestId)
                putExtra(SHOW_CLOSE_BUTTON, showCloseButton)
            })
        } else {
            Timber.d("Outbound move")
            navigatorLauncher.launch(Intent(this, OutboundWebViewActivity::class.java).apply {
                putExtra(LOAD_URL, url)
                putExtra(EXTRA_DATA, data)
                putExtra(REQUEST_ID, requestId)
                putExtra(SHOW_CLOSE_BUTTON, showCloseButton)
            })
        }
    }

    private fun onResultSelectFile(it: Intent?) {
        if (it == null) {
            mediaValueCallback?.onReceiveValue(null)
            mediaValueCallback = null
            return
        }

        if (it.data != null) {
            val uri: Uri? = it.data
            Timber.d("uri=$uri")
            mediaValueCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    RESULT_OK,
                    it
                )
            )
        } else {
            if (it.clipData != null) {
                val results = ArrayList<Uri>()
                val size = it.clipData?.itemCount ?: 0
                for (i in 0 until size) {
                    Timber.d("add uri: %s", it.clipData?.getItemAt(i)?.uri)
                    it.clipData?.getItemAt(i)?.let { it1 -> results.add(it1.uri) }
                }
                mediaValueCallback?.onReceiveValue(results.toTypedArray())
            } else {
                Timber.e("Unhandled case.")
            }
        }

        mediaValueCallback = null
    }

    fun showCustomConfirmDialog(message: String?, result: ConfirmResult?): CustomDialogFragment {
        CustomDialogFragment.Builder().apply {
            setMessage(message ?: "")
            setPositiveButton(getString(R.string.ok_move_button)) { result?.confirm() }
            setNegativeButton(getString(R.string.cancel_button)) { result?.cancel() }

            setCancelable(false)
            return show(supportFragmentManager)
        }
    }

    override fun showToast(message: String, bottomOffsetInDp: Int?) {
        CustomSnackBar.make(binding.root, message)
            .setBottomMargin(bottomOffsetInDp ?: CustomSnackBar.MARGIN_BOTTOM_OVER_BOTTOM_NAVIGATION)
            .show()
    }

    override fun setWebViewKeyboardVisibility(visible: Boolean) {
        lifecycleScope.launch {
            SoftKeyboardVisibilityHelper(binding.webView).setVisibility(visible)
        }
    }

    override suspend fun takeScreenshotBitmap(target: CaptureTarget): Bitmap? =
        when (target) {
            CaptureTarget.SCREEN -> window.decorView.captureBitmap()
            CaptureTarget.FULL_WEB -> binding.webView.captureFullBitmap()
        }

    override fun loadUrl(url: String, method: String, param: String) {
        lifecycleScope.launch {
            Timber.d("loadUrl() url=$url method=$method")
            binding.webView.loadUrl(url, method, param)
            loadedUrl = url
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (onKeyDown(keyCode, event.action, event)) return true

        return super.onKeyDown(keyCode, event)
    }

    private fun onKeyDown(keyCode: Int, eventAction: Int, event: KeyEvent?= null): Boolean {
        if (eventAction == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (this is PIPWebViewActivity) {
                        minimize()
                        return true
                    }

                    if (binding.webView.canGoBack()) {
                        binding.webView.goBack()
                    } else {
                        finish()
                    }
                    callbackAppEvent(JSEventName.NAVIGATE, JSEventType.BACK, JSONObject())

                    return true
                }
            }
        }

        return false
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            try {
                val diffY = e2.y - (e1?.y ?: 0F)
                val diffX = e2.x - (e1?.x ?: 0F)

                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getMetrics(displayMetrics)

                if (abs(diffX) > abs(diffY)) {
                    /*if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            Timber.d("onSwipeRight()")
                        } else {
                            Timber.d("onSwipeLeft()")
                        }
                    }*/
                } else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        //Timber.d("onSwipeBottom()")
                        if (abs(diffY) > displayMetrics.heightPixels * 0.3 && this@WebViewActivity is PIPWebViewActivity &&
                            this@WebViewActivity.enableSwipeEvent)
                        {
                            minimize()
                        }
                    } else {
                        Timber.d("onSwipeTop()")
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }

            return true
        }
    }

    companion object {
        const val SWIPE_THRESHOLD = 100
        const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    private suspend fun maybeInvalidateWebAssets(baseUrl: String) {
        val versionUrl = baseUrl.trimEnd('/') + "/version.json"
        val remoteVersion = fetchWebAppVersion(versionUrl) ?: return

        val lastVersion = webAssetPrefs.getString("last_frontend_version", null)
        if (lastVersion == remoteVersion) return

        Timber.w("Frontend version changed. last=$lastVersion new=$remoteVersion -> clearing WebView data")
        clearWebViewCache()
        webAssetPrefs.edit().putString("last_frontend_version", remoteVersion).apply()
    }

    private suspend fun fetchWebAppVersion(url: String): String? {
        return runCatching {
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TimeUnit.SECONDS.toMillis(3).toInt()
                    readTimeout = TimeUnit.SECONDS.toMillis(3).toInt()
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }

                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        Timber.w("Version check failed: HTTP $code url=$url")
                        return@withContext null
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    json.optString("version").takeIf { it.isNotBlank() }
                } finally {
                    conn.disconnect()
                }
            }
        }.getOrElse { e ->
            Timber.w(e, "Version check failed url=$url")
            null
        }
    }
}
