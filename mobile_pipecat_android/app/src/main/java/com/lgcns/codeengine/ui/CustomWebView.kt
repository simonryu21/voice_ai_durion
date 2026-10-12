package com.lgcns.codeengine.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.PermissionRequest
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.net.http.SslError
import android.Manifest
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.BaseWebViewActivity
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.GET
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.POST
import com.lgcns.codeengine.activity.WebViewActivity
import com.lgcns.codeengine.util.isLocalDevUrl
import com.kakao.sdk.user.UserApiClient
import com.lgcns.codeengine.activity.OutboundWebViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.net.URLDecoder

@SuppressLint("SetJavaScriptEnabled")
class CustomWebView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : WebView(context, attrs, defStyleAttr, defStyleRes)
{

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    var webViewActivity: WebViewActivity? = null

    var loadedUrl: String = ""

    lateinit var uploadMediaType: BaseWebViewActivity.MediaType

    var name: String? = null

    private fun openExternalUrlViaHandoff(url: String): Boolean {
        val activity = webViewActivity ?: return false
        // OAuth는 "새 창/새 Activity"로 뜨면 안되므로,
        // handoff로 보내지 않고 현재 WebView에서 그대로 처리한다.
        if (isOAuthUrl(url)) return false
        // 로컬 dev 서버(localhost/127.0.0.1/10.0.2.2 등)는 "우리 화면"이므로 handoff 대상에서 제외
        if (isLocalDevUrl(url)) return false
        val baseUrl = activity.baseUrl
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (baseUrl.isNotEmpty() && url.startsWith(baseUrl)) return false

        // Delegate to WebViewActivity so it can enforce:
        // 1) HandoffWebViewActivity finishes
        // 2) Reconnect succeeds
        // 3) Then send messages to server via JS
        return activity.openExternalUrlViaHandoff(url)
    }

    // 외부 앱 실행을 위한 커스텀 스킴 여부 (instagram://, whatsapp:// 등)
    // 위쪽에서 별도 처리되거나 WebView가 네이티브로 처리하는 스킴은 제외한다.
    private fun isCustomAppScheme(url: String): Boolean {
        val schemeEnd = url.indexOf(':')
        if (schemeEnd <= 0) return false
        val scheme = url.substring(0, schemeEnd).lowercase()
        val excluded = setOf(
            "http", "https", "file", "data", "javascript", "about", "blob",
            "tel", "mailto", "intent", "onestore", "kakaotalk", "market"
        )
        return scheme !in excluded
    }

    fun loadUrl(url: String, method: String, param: String = "") {
        when (method) {
            GET -> {
                val newUrl = "$url$param"
                super.loadUrl(newUrl)
                Timber.d("loadUrl: $newUrl")
            }
            POST -> {

                super.postUrl(url, param.toByteArray(Charsets.UTF_8))
                Timber.d("postUrl: $url, postData org.=$param")
            }
            else -> {
                super.loadUrl(url)
                Timber.d("default loadUrl: $url")
            }
        }
    }

    init {
        apply {
        Timber.d("init()")

        name = "CustomWebView_$webViewCount"
        Timber.d("The name was set to $name")
        webViewCount++

        webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {

                this@CustomWebView.webViewActivity?.showCommonLoading()

                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadedUrl = url ?: ""
                Timber.d("onPageFinished() loadedUrl=$loadedUrl")

                this@CustomWebView.webViewActivity?.hideCommonLoading()

                extractAndSaveOAuthToken(url)

                injectEchoCancellationScript()

                super.onPageFinished(view, url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view ,request, error)
                Timber.d("onReceivedError: ${error?.errorCode}, ${error?.description}")
                /*
                if (!ErrorShowActivity.isActive) {
                    if (error?.errorCode != -2) { // net::ERR_INTERNET_DISCONNECTED
                        val intent =
                            Intent(context, ErrorShowActivity::class.java)
                        intent.putExtra(
                            ErrorShowActivity.ERROR_CODE,
                            error?.errorCode
                        )
                        intent.putExtra(LOAD_URL, loadedUrl)
                        context.startActivity(intent)
                    }
                }
                 */
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Timber.d("onReceivedHttpError url: ${request?.url} statusCode: ${errorResponse?.statusCode} isForMainFrame: ${request?.isForMainFrame}")
/*
                if (request?.isForMainFrame == true) {
                    if (errorResponse?.statusCode == 404) {
                        val intent = Intent(context, ErrorShowActivity::class.java)
                        intent.putExtra(ErrorShowActivity.ERROR_CODE, 404)
                        context.startActivity(intent)
                    } else if (errorResponse?.statusCode in 500..599) {
                        val intent = Intent(context, ErrorShowActivity::class.java)
                        intent.putExtra(ErrorShowActivity.ERROR_CODE, errorResponse?.statusCode)
                        intent.putExtra(BaseWebViewActivity.LOAD_URL, loadedUrl)
                        context.startActivity(intent)
                    }
                }
 */
            }

            // SSL 인증서 오류 처리 (자체 서명 인증서 등)
            @Deprecated("Deprecated in Java")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                Timber.w("onReceivedSslError: ${error?.toString()}")
                // 개발/테스트 환경에서만 SSL 오류를 무시 (프로덕션에서는 주의 필요)
                handler?.proceed()
            }

            // API Level 23 이하에서는 이 함수를 오버라이드 해야한다.
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return startSchemeIntent(url)
            }

            // API Level 24 부터는 이 함수를 오버라이드 해야한다.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                return startSchemeIntent(request?.url.toString())
            }

            private fun startSchemeIntent(url: String?): Boolean {
                Timber.d("startSchemeIntent url=$url")

                if (url.isNullOrEmpty()) {
                    return false
                }

                // 1) Outbound http(s) link → open in HandoffWebViewActivity.
                //    (OAuth는 handoff로 보내지 않고 현재 WebView에서 처리)
                if (openExternalUrlViaHandoff(url)) {
                    return true
                }

                try {
                    if (url.startsWith("tel:")) {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(url)))
                        return true
                    } else if (url.startsWith("mailto:")) {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
                        return true
                    } else if (isCustomAppScheme(url)) {
                        // instagram://, whatsapp://, fb://, youtube://, twitter:// 등
                        // ERR_UNKNOWN_URL_SCHEME 방지: 앱이 있으면 실행, 없으면 Play Store로 유도
                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (appIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(appIntent)
                        } else {
                            val scheme = url.substringBefore(':')
                            Timber.w("커스텀 스킴 앱 미설치, Play Store 검색으로 이동: $scheme")
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://search?q=$scheme")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            try {
                                context.startActivity(marketIntent)
                            } catch (e: ActivityNotFoundException) {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/search?q=$scheme")
                                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                )
                            }
                        }
                        return true
                    }
                } catch (e: ActivityNotFoundException) {
                    Timber.e("ActivityNotFoundException")
                    return false
                }

                val retVal = runBlocking(Dispatchers.IO) {
                    try {
                        val connection = URL(url).openConnection()
                        val contentType = connection.contentType
                        Timber.d("contentType=$contentType")

                        if (contentType.contains("image/") || contentType.contains("video/") || contentType.contains("application/")) {
                            connection.connect()
                            var content = connection?.getHeaderField("Content-Disposition")
                            //fileName=attachment; filename*=UTF-8''QR%E1%84%8F%E1%85%A9%E1%84%83%E1%85%B3.pdf
                            var fileName = URLDecoder.decode(content?.substring(content.lastIndexOf('\'')+1) ?: "", "UTF-8")
                            Timber.d("fileName=$fileName")
                            if (fileName == null || fileName.isEmpty()) {
                                fileName = url.substring(url.lastIndexOf('/') + 1)
                                Timber.d("[2] fileName = $fileName")
                            }

                            if (contentType.contains("image/") || contentType.contains("video/")) {
                                this@CustomWebView.downloadFile(url, Environment.DIRECTORY_DCIM, fileName)
                                return@runBlocking true
                            } else if (contentType.contains("application/")) {
                                this@CustomWebView.downloadFile(url, Environment.DIRECTORY_DOWNLOADS, fileName)
                                return@runBlocking true
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e)
                    }

                    return@runBlocking false
                }

                Timber.d("retVal=$retVal")
                if (retVal) return true

                // Kakao 공유 기능 지원 - Start
                try {
                    // Intent 생성
                    val intent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
                    Timber.d("intent=${intent}")

                    // OAuth 인증 URL은 WebView 내에서 처리 (새 창/새 Activity 금지)
                    if (isOAuthUrl(url)) {
                        Timber.d("OAuth URL - handling in current WebView: $url")
                        return false
                    }

                    // 카카오링크 등 "intent://"로 시작하는 url이면 해당 앱을 띄웁니다.
                    // 또는 outlink이고 OutboundActivity가 아니면 브라우저 앱을 띄웁니다.
                    if (url.indexOf("intent:") != -1 || url.indexOf("onestore:") != -1 || url.indexOf("kakaotalk:") != -1 ||  url.indexOf("market:") != -1 ||
                        (!url.startsWith(this@CustomWebView.webViewActivity?.baseUrl ?: "") &&
                             this@CustomWebView.webViewActivity !is OutboundWebViewActivity)
                    )
                    {
                        // 실행 가능한 앱이 있으면 앱 실행
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                            Timber.d("className: ${intent.resolveActivity(context.packageManager).className}")
                            return true
                        } else if (intent.`package` != null) {
                            // 삼성카드
                            // url=intent://?xid=8922432#Intent;scheme=mpocket.online.ansimclick;package=kr.co.samsungcard.mpocket;end;
                            // intent=Intent { act=android.intent.action.VIEW dat=mpocket.online.ansimclick://?xid=8922432 pkg=kr.co.samsungcard.mpocket }
                            // 현대카드
                            // url=intent:hdcardappcardansimclick://appcard?acctid=202312261412441407000774434261#Intent;package=com.hyundaicard.appcard;end;
                            // intent=Intent { act=android.intent.action.VIEW dat=hdcardappcardansimclick://appcard/... pkg=com.hyundaicard.appcard }
                            // 삼성카드 백신앱
                            // url=onestore://common/product/0000771328?view_type=install&refType=2&callerType=8
                            // intent=Intent { act=android.intent.action.VIEW dat=onestore://common/... }
                            try {
                                Timber.d("intent.`package`=${intent.`package`}")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val notInstalled = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=${intent.`package`}")
                                )
                                context.startActivity(notInstalled)
                            }
                            return true
                        } else if (url.indexOf("kakaolink") != -1 || url.indexOf("klipwallet") != -1 || url.indexOf("kakaopay") != -1 &&
                            !UserApiClient.instance.isKakaoTalkLoginAvailable(this@CustomWebView.context))
                        {
                            // 카카오링크인데 앱이 설치되어있지 않으면 구글 스토어로 이동
                            val kakaoIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.kakao.talk")
                            )
                            context.startActivity(kakaoIntent)
                            return true
                        } else if (url.indexOf("market:") != -1) {
                            val bookCube = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                            )
                            context.startActivity(bookCube)
                            return true
                        } else {
                            // package 명을 모르는 앱일때 구글 스토어 앱 메인화면으로 이동
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps")
                                )
                            )
                            return true
                        }
                    }

                    // Fallback URL이 있으면 현재 웹뷰에 로딩
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (fallbackUrl != null) {
                        loadUrl(fallbackUrl)
                        Timber.d("FALLBACK: $fallbackUrl")
                        return true
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
                // - End

                return false
            }


        }

        webChromeClient = object : WebChromeClient() {

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val url = view.hitTestResult.extra
                Timber.d("onCreateWindow() url = $url")

                // OAuth는 새 창/새 Activity로 뜨면 안됨 → 현재 WebView에서 처리
                if (!url.isNullOrEmpty() && isOAuthUrl(url)) {
                    Timber.d("onCreateWindow OAuth - loading in current WebView: $url")
                    view.loadUrl(url)
                    return false
                }

                // 일반 outbound 링크는 handoff activity로
                if (!url.isNullOrEmpty() && openExternalUrlViaHandoff(url)) {
                    return true
                }

                val newWebView = WebView(view.context)
                val settings = newWebView.settings
                settings.javaScriptEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(true)

                val dialog = Dialog(view.context, R.style.SimpleFullScreenDialog)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(newWebView)
                dialog.setOnKeyListener { dialog, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        if (newWebView.canGoBack()) {
                            newWebView.goBack()
                        } else {
                            newWebView.visibility = GONE
                            newWebView.destroy()
                            dialog.dismiss()
                        }
                        true
                    } else {
                        false
                    }
                }
                dialog.show()

                newWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Timber.d("onPageFinished() url=$url")
                    }
                }

                newWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        dialog.dismiss()
                    }
                }
                val transport: WebView.WebViewTransport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()

                return true
            }

            override fun onCloseWindow(window: WebView) {
                window.visibility = GONE
                window.destroy()
                super.onCloseWindow(window)
            }

            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?,
            ): Boolean {
                showCustomAlertDialog(message, result)
                return true
            }

            override fun onJsConfirm(
                view: WebView?, url: String?, message: String?, result: JsResult?,
            ): Boolean {
                showCustomConfirmDialog(message, result)
                return true
            }

            override fun onShowFileChooser (
                webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
            ): Boolean {
                Timber.d("onShowFileChooser() called. fileChooserParams=$fileChooserParams, mode=${fileChooserParams?.mode}")

                if (webViewActivity?.mediaValueCallback != null) {
                    // 파일을 한번 오픈했으면 mediaValueCallback을 초기화를 해줘야함
                    // 그렇지 않으면 다시 파일 오픈 시 열리지 않는 경우 발생
                    webViewActivity?.mediaValueCallback!!.onReceiveValue(null)
                    webViewActivity?.mediaValueCallback = null
                }

                webViewActivity?.mediaValueCallback = filePathCallback

                for (type in fileChooserParams!!.acceptTypes) {
                    Timber.d("fileChooserParams!!.acceptTypes : $type")
                    when (type) {
                        ".jpeg", ".gif", ".jpg", ".png", "image/*", "image/jpeg", "image/jpg", "image/png" -> {
                            uploadMediaType = BaseWebViewActivity.MediaType.PICTURE
                            break
                        }
                        ".mp4", ".mov", "video/*", "video/mp4", "video/mov" -> {
                            uploadMediaType = BaseWebViewActivity.MediaType.VIDEO
                            break
                        } // "application/pdf" 등
                        else -> {
                            uploadMediaType = BaseWebViewActivity.MediaType.FILE
                            //break --> 다음에 PICTURE 또는 VIDEO type이 나올 수 있으므로 break하지 말 것
                        }
                    }
                }
                Timber.d("uploadMediaType=$uploadMediaType, isCaptureEnabled=${fileChooserParams.isCaptureEnabled}")

                if (uploadMediaType == BaseWebViewActivity.MediaType.FILE) {
                    val isMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    webViewActivity?.selectFile(fileChooserParams.acceptTypes.toList(), isMultiple) {
                        onResultSelectFile(it)
                    }
                } else {
                    webViewActivity?.lifecycleScope?.launch {
                        if (webViewActivity?.pluginPermissions?.cameraPermission?.grant() == true) {
                            Timber.d("Granted camera access permission.")
                            webViewActivity?.selectMedia(uploadMediaType, fileChooserParams)
                        } else {
                            Timber.w("Denied camera access permission.")
                        }
                    }

                    if (webViewActivity?.pluginPermissions?.cameraPermission?.isGranted() == false) return true
                }

                return true
            }

            // WebView에서 위치 정보 권한 요청 처리
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                Timber.d("onGeolocationPermissionsShowPrompt: origin=$origin")
                
                if (callback == null) {
                    return
                }
                
                // BaseWebViewActivity의 권한 처리 시스템 사용
                webViewActivity?.lifecycleScope?.launch {
                    if (webViewActivity?.pluginPermissions?.locationPermission?.grant() == true) {
                        Timber.d("Location permission granted, allowing geolocation access")
                        callback.invoke(origin, true, false)
                    } else {
                        Timber.w("Location permission denied, denying geolocation access")
                        callback.invoke(origin, false, false)
                    }
                }
            }

            // WebView에서 마이크/카메라 권한 요청 처리 (TTS, STT 등)
            override fun onPermissionRequest(request: PermissionRequest?) {
                Timber.d("onPermissionRequest: ${request?.resources?.joinToString()}")
                
                if (request == null) {
                    return
                }
                
                val requestedResources = request.resources
                val permissionsToRequest = mutableListOf<String>()
                
                // WebView가 요청한 리소스에 따라 Android 권한 매핑
                for (resource in requestedResources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                        }
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                            permissionsToRequest.add(Manifest.permission.CAMERA)
                        }
                    }
                }
                
                if (permissionsToRequest.isEmpty()) {
                    // 알 수 없는 리소스는 거부
                    request.deny()
                    return
                }
                
                // BaseWebViewActivity의 권한 처리 시스템 사용
                webViewActivity?.lifecycleScope?.launch {
                    var allGranted = true
                    
                    for (permission in permissionsToRequest) {
                        when (permission) {
                            Manifest.permission.RECORD_AUDIO -> {
                                if (webViewActivity?.pluginPermissions?.recordAudioPermission?.grant() != true) {
                                    allGranted = false
                                    break
                                }
                            }
                            Manifest.permission.CAMERA -> {
                                if (webViewActivity?.pluginPermissions?.cameraPermission?.grant() != true) {
                                    allGranted = false
                                    break
                                }
                            }
                        }
                    }
                    
                    if (allGranted) {
                        Timber.d("All permissions granted, allowing WebView permission request")
                        request.grant(requestedResources)
                    } else {
                        Timber.w("Some permissions denied, denying WebView permission request")
                        request.deny()
                    }
                }
            }

            // Video Tag 사용시 Full Screen 아이콘 안나오는 현상 관련 추가 - Start
            var fullScreen: View? = null

            override fun onHideCustomView() {
                fullScreen!!.visibility = View.GONE
                this@CustomWebView.visibility = View.VISIBLE
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                this@CustomWebView.visibility = View.GONE
                if (fullScreen != null) {
                    (webViewActivity?.window?.decorView as FrameLayout).removeView(fullScreen)
                }
                fullScreen = view

                var notchHeight = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val displayCutout = webViewActivity?.window?.decorView?.rootWindowInsets?.displayCutout
                    if ((displayCutout?.boundingRects?.size ?: 0) > 0) {
                        val notchRect = displayCutout?.boundingRects?.get(0)
                        notchHeight = notchRect?.height() ?: 0
                        Timber.d("notch height in pixels=$notchHeight")
                    }
                } else {
                    Timber.d("There is no safe area(notch area).")
                }

                (webViewActivity?.window?.decorView as FrameLayout).addView(
                    fullScreen,
                    FrameLayout.LayoutParams(webViewActivity?.binding?.root?.width ?: -1,
                        (webViewActivity?.binding?.root?.height ?: -1) + notchHeight)
                )
                fullScreen!!.visibility = View.VISIBLE
            }
            // - End

        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.textZoom = 100
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // TTS/오디오 재생을 위해 사용자 제스처 없이도 미디어 재생 허용
        settings.mediaPlaybackRequiresUserGesture = false

        // added for kakao popup window
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        // Remove "; wv)" from the default WebView UA so Google OAuth doesn't block it
        // (Google returns 403 disallowed_useragent when it detects the WebView marker)
        val cleanedUa = settings.userAgentString
            .replace("; wv)", ")")   // e.g. "... Build/XXX; wv) ..." → "... Build/XXX) ..."
            .replace(";Mobile", "")  // remove the old ;Mobile suffix we were appending
        settings.userAgentString = "$cleanedUa Mobile"
        // end.
        }
    }

    private fun extractAndSaveOAuthToken(url: String?) {
        if (url == null) return
        try {
            val uri = Uri.parse(url)
            if (uri.getQueryParameter("success") == "true") {
                val token = uri.getQueryParameter("token")
                if (!token.isNullOrBlank()) {
                    Timber.i("OAuth token extracted from callback URL, saving to native DataStore")
                    webViewActivity?.saveNativeToken(token)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract OAuth token from URL")
        }
    }

    private fun isOAuthUrl(url: String): Boolean {
        return runCatching {
            val uri = Uri.parse(url)
            val host = (uri.host ?: "").lowercase()
            // Keep OAuth flows in the current WebView (no new activity/window).
            // Google login commonly hops across multiple hosts (accounts.youtube.com, accounts.googleusercontent.com, etc).
            val isGoogleOAuthHost =
                host == "accounts.google.com" ||
                    host.endsWith(".accounts.google.com") ||
                    host == "accounts.youtube.com" ||
                    host.endsWith(".accounts.youtube.com") ||
                    host.endsWith(".googleusercontent.com") ||
                    host == "oauth2.googleapis.com" ||
                    host.endsWith(".oauth2.googleapis.com") ||
                    host.endsWith(".gstatic.com")

            val path = (uri.path ?: "").lowercase()
            val isOAuthishPath =
                path.contains("oauth") ||
                    path.contains("signin") ||
                    path.contains("authorize") ||
                    path.contains("checkcookie") ||
                    path.contains("setsid")

            val isOAuthRedirect =
                url.contains("redirect_uri=", ignoreCase = true) ||
                    url.contains("response_type=code", ignoreCase = true) ||
                    url.contains("code=", ignoreCase = true) ||
                    url.contains("state=", ignoreCase = true)

            val isKakaoOAuthHost =
                host == "kauth.kakao.com" ||
                    host == "kapi.kakao.com" ||
                    host == "accounts.kakao.com" ||
                    host.endsWith(".accounts.kakao.com")

            isGoogleOAuthHost ||
                // Other providers
                host == "nid.naver.com" ||
                isKakaoOAuthHost ||
                // app backend OAuth endpoints
                url.contains("/api/auth/", ignoreCase = true) ||
                // defensive: if it looks like OAuth and is on a google-ish host, keep it in current webview
                (isOAuthishPath && (host.contains("google") || host.contains("youtube"))) ||
                // defensive: if redirect/params indicate OAuth, keep it in current webview
                (isOAuthRedirect && (host.contains("google") || host.contains("youtube"))) ||
                // Kakao: login + consent hops across kauth.kakao.com → accounts.kakao.com → callback
                (isOAuthRedirect && host.contains("kakao"))
        }.getOrDefault(false)
    }

    private fun downloadFile(url: String, dir: String, fileName: String?) {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setTitle(fileName ?: "")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(dir, fileName)

        val downloadManager = context.getSystemService(AppCompatActivity.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
    }

    private fun showCustomAlertDialog(message: String?, result: JsResult?) {
        CustomDialogFragment.Builder()
            .setMessage(message ?: "")
            .setPositiveButton(context.getString(R.string.ok_button)) { result?.confirm() }
            .setCancelable(false)
            .show(webViewActivity!!.supportFragmentManager)
    }

    private fun showCustomConfirmDialog(message: String?, result: JsResult?) {
        CustomDialogFragment.Builder().apply {
            try {
                val jsonMessage = JSONObject(message.toString())

                setTitle(jsonMessage.optString("title"))
                setMessage(jsonMessage.optString("message"))
                setPositiveButton(jsonMessage.optString("confirmText")) { result?.confirm() }
                setNegativeButton(jsonMessage.optString("cancelText")) { result?.cancel() }
            } catch (e: Exception) {
                setMessage(message ?: "")
                setPositiveButton(context.getString(R.string.ok_button)) { result?.confirm() }
                setNegativeButton(context.getString(R.string.cancel_button)) { result?.cancel() }
            }

            setCancelable(false)
            show(webViewActivity!!.supportFragmentManager)
        }
    }

    private fun onResultSelectFile(it: Intent?) {
        if (it == null) {
            webViewActivity?.mediaValueCallback?.onReceiveValue(null)
            webViewActivity?.mediaValueCallback = null
            return
        }

        if (it.data != null) {
            val uri: Uri? = it.data
            Timber.d("uri=$uri")
            webViewActivity?.mediaValueCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    AppCompatActivity.RESULT_OK,
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
                webViewActivity?.mediaValueCallback?.onReceiveValue(results.toTypedArray())
            } else {
                Timber.e("Unhandled case.")
            }
        }

        webViewActivity?.mediaValueCallback = null
    }

    /**
     * 에코 캔슬레이션 활성화 스크립트 주입
     * getUserMedia 호출 시 echoCancellation을 자동으로 활성화하여
     * TTS 출력이 STT 입력으로 들어가는 것을 방지합니다.
     * 마이크는 계속 켜져 있으므로 VAD 기능도 정상 작동합니다.
     */
    private fun injectEchoCancellationScript() {
        val script = """
            (function() {
                'use strict';
                
                // 이미 주입된 경우 중복 실행 방지
                if (window.__echoCancellationInjected) {
                    return;
                }
                window.__echoCancellationInjected = true;
                
                // getUserMedia를 오버라이드하여 echoCancellation 자동 활성화
                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    const originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
                    
                    navigator.mediaDevices.getUserMedia = function(constraints) {
                        // constraints에 echoCancellation 추가
                        if (constraints && constraints.audio !== false) {
                            if (typeof constraints.audio === 'object' && constraints.audio !== null) {
                                constraints.audio.echoCancellation = true;
                                constraints.audio.noiseSuppression = true;
                                constraints.audio.autoGainControl = true;
                            } else if (constraints.audio === true || constraints.audio === undefined) {
                                constraints.audio = {
                                    echoCancellation: true,
                                    noiseSuppression: true,
                                    autoGainControl: true
                                };
                            }
                        }
                        
                        console.log('[Echo Cancellation] getUserMedia called with constraints:', JSON.stringify(constraints));
                        return originalGetUserMedia(constraints);
                    };
                }
                
                console.log('[Echo Cancellation] 스크립트 주입 완료');
            })();
        """.trimIndent()

        webViewActivity?.lifecycleScope?.launch {
            evaluateJavascript(script) { result ->
                Timber.d("Echo cancellation script injected: $result")
            }
        }
    }

    companion object {
        var webViewCount: Int = 0
   }
}