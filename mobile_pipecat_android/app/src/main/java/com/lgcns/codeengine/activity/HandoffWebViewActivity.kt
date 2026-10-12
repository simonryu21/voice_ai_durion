package com.lgcns.codeengine.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.lgcns.codeengine.R
import com.lgcns.codeengine.util.extension.captureFullBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class HandoffWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View

    private var handoffId: String = ""
    private var targetUrl: String = ""
    private var isReturningToAi: Boolean = false

    companion object {
        const val EXTRA_HANDOFF_ID = "handoff_id"
        const val EXTRA_URL = "url"
        const val EXTRA_KIND = "kind"
        const val EXTRA_VENDOR = "vendor"
        const val EXTRA_INTENT_SUMMARY = "intent_summary"

        const val RESULT_HANDOFF_ID = "handoff_id"
        const val RESULT_SCREENSHOT_BASE64 = "screenshot_base64"
        const val RESULT_SCREENSHOT_URI = "screenshot_uri"
        const val RESULT_PAGE_URL = "page_url"
        const val RESULT_PAGE_TITLE = "page_title"

        // Keep RTVI/WebRTC datachannel payload conservative.
        // Base64 expands raw bytes by ~33%, so 8KB JPEG -> ~10.7KB base64 text.
        private const val MAX_THUMBNAIL_JPEG_BYTES = 8_000
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handoff_web_view)

        webView = findViewById(R.id.handoffWebView)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        handoffId = intent.getStringExtra(EXTRA_HANDOFF_ID) ?: ""
        targetUrl = intent.getStringExtra(EXTRA_URL) ?: ""

        setupWebView()
        setupBackButton()

        if (targetUrl.isNotEmpty()) {
            webView.loadUrl(targetUrl)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        showBackConfirmDialog()
                    }
                }
            },
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = handleCustomScheme(request?.url?.toString())

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    handleCustomScheme(url)
            }
            webChromeClient = WebChromeClient()
        }
    }

    // Instagram 페이지 등이 instagram://, whatsapp:// 같은 커스텀 스킴으로 리다이렉트할 때
    // ERR_UNKNOWN_URL_SCHEME 방지: 앱이 있으면 실행, 없으면 Play Store로 유도.
    private fun handleCustomScheme(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val schemeEnd = url.indexOf(':')
        if (schemeEnd <= 0) return false
        val scheme = url.substring(0, schemeEnd).lowercase()
        // WebView가 네이티브로 처리하는 표준 스킴은 그대로 둔다.
        val webViewNative = setOf("http", "https", "file", "data", "javascript", "about", "blob")
        if (scheme in webViewNative) return false

        return try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appIntent.resolveActivity(packageManager) != null) {
                startActivity(appIntent)
            } else {
                Timber.w("커스텀 스킴 앱 미설치, Play Store 검색으로 이동: $scheme")
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://search?q=$scheme")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try {
                    startActivity(marketIntent)
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/search?q=$scheme")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "커스텀 스킴 처리 실패: $url")
            true
        }
    }

    private fun setupBackButton() {
        findViewById<android.view.View>(R.id.btnBack).setOnClickListener {
            showBackConfirmDialog()
        }
    }

    private fun showBackConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("AI 챗봇")
            .setMessage("AI 챗봇 창으로 돌아가시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                captureAndFinish()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun captureAndFinish() {
        if (isReturningToAi) return
        isReturningToAi = true
        setLoadingVisible(true)

        CoroutineScope(Dispatchers.Main).launch {
            val pageUrl = webView.url ?: ""
            val pageTitle = webView.title ?: ""

            try {
                val bitmap = webView.captureFullBitmap()
                val (screenshotUri, base64) = withContext(Dispatchers.IO) {
                    saveScreenshotAndMakeThumbnailBase64(bitmap)
                }

                val resultIntent = Intent().apply {
                    putExtra(RESULT_HANDOFF_ID, handoffId)
                    putExtra(RESULT_SCREENSHOT_BASE64, base64)
                    putExtra(RESULT_SCREENSHOT_URI, screenshotUri)
                    putExtra(RESULT_PAGE_URL, pageUrl)
                    putExtra(RESULT_PAGE_TITLE, pageTitle)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to capture screenshot")
                val resultIntent = Intent().apply {
                    putExtra(RESULT_HANDOFF_ID, handoffId)
                    putExtra(RESULT_PAGE_URL, pageUrl)
                    putExtra(RESULT_PAGE_TITLE, pageTitle)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } finally {
                setLoadingVisible(false)
                finish()
            }
        }
    }

    private fun setLoadingVisible(visible: Boolean) {
        loadingOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        webView.isEnabled = !visible
        findViewById<View>(R.id.btnBack).isEnabled = !visible
    }

    private fun saveScreenshotAndMakeThumbnailBase64(bitmap: Bitmap?): Pair<String, String> {
        if (bitmap == null) return "" to ""

        // 1) Save a full screenshot to cache as a file Uri (safe for Binder).
        val screenshotUri = runCatching {
            val dir = File(cacheDir, "picture").apply { mkdirs() }
            val file = File(dir, "handoff_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file).toString()
        }.getOrElse { e ->
            Timber.w(e, "Failed to save screenshot to cache")
            ""
        }

        // 2) Also return a *small* base64 thumbnail for JS/LLM context.
        //    Keep this VERY small because it will be forwarded over WebRTC datachannel (RTVI),
        //    and oversized messages can cause the channel/connection to close.
        val thumbBytes = bitmapToJpegBytesUnderLimit(bitmap, maxSizeBytes = MAX_THUMBNAIL_JPEG_BYTES)
        val thumbBase64 = if (thumbBytes.isNotEmpty()) {
            Base64.encodeToString(thumbBytes, Base64.NO_WRAP)
        } else {
            ""
        }
        return screenshotUri to thumbBase64
    }

    private fun bitmapToJpegBytesUnderLimit(bitmap: Bitmap, maxSizeBytes: Int): ByteArray {
        var current = bitmap
        var attemptScale = 1.0

        repeat(8) {
            var quality = 85
            while (quality >= 20) {
                val stream = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val bytes = stream.toByteArray()
                if (bytes.size <= maxSizeBytes) return bytes
                quality -= 15
            }

            // If lowering quality isn't enough, scale down and try again.
            attemptScale *= 0.75
            val newW = max(1, (bitmap.width * attemptScale).toInt())
            val newH = max(1, (bitmap.height * attemptScale).toInt())
            if (newW == current.width && newH == current.height) return ByteArray(0)

            current = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        }

        return ByteArray(0)
    }
}

