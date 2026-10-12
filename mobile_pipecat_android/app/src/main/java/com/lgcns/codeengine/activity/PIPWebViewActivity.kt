package com.lgcns.codeengine.activity

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Rational
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.R
import com.lgcns.codeengine.web.params.JSEventName
import com.lgcns.codeengine.web.params.JSEventType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber

@AndroidEntryPoint
class PIPWebViewActivity : WebViewActivity() {

    var enableSwipeEvent = true

    private var isOnMinimizing = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Timber.e("PIPWebViewActivity onCreate")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pipActivity = this

        isInPIPMode = false
    }

    fun minimize() {
        Timber.d("minimize()")

        if (!isOnMinimizing) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                updatePictureInPictureParams()?.let {
                    Timber.d("enterPictureInPictureMode()")
                    enterPictureInPictureMode(it)
                }
            } else {
                Timber.e("This version(${Build.VERSION.SDK_INT}) does not support PIP.")
            }
        }

        isOnMinimizing = true
        Handler(Looper.getMainLooper()).postDelayed(
            {
                isOnMinimizing = false
            },
            800 // value in milliseconds
        )

    }


    private fun updatePictureInPictureParams(): PictureInPictureParams? {
        // Calculate the aspect ratio of the PiP screen.
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val aspectRatio = Rational(width, height)
        // The movie view turns into the picture-in-picture mode.
        val visibleRect = Rect()
        binding.root.getGlobalVisibleRect(visibleRect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var params: PictureInPictureParams

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    // Specify the portion of the screen that turns into the picture-in-picture mode.
                    // This makes the transition animation smoother.
                    .setSourceRectHint(visibleRect)
                    // The screen automatically turns into the picture-in-picture mode when it is hidden
                    // by the "Home" button.
                    .setAutoEnterEnabled(true)
                    .build()
            } else {
                params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    // Specify the portion of the screen that turns into the picture-in-picture mode.
                    // This makes the transition animation smoother.
                    .setSourceRectHint(visibleRect)
                    .build()
            }

            setPictureInPictureParams(params)

            return params
        } else {
            Timber.e("This version(${Build.VERSION.SDK_INT}) does not support PIP.")
        }

        return null
    }

    // This is called when the activity gets into or out of the picture-in-picture mode.
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        Timber.d("isInPictureInPictureMode=$isInPictureInPictureMode, lifecycle.currentState=${lifecycle.currentState}")

        isInPIPMode = isInPictureInPictureMode
    }

    override fun onPause() {
        Timber.d("onPause()")

        runBlocking {
            minimize()
        }

        super.onPause()
    }

    override fun onDestroy() {
        Timber.d("onDestroy()")
        pipActivity = null
        isInPIPMode = false

        super.onDestroy()
    }

    companion object {
        var pipActivity = null as PIPWebViewActivity?

        var isInPIPMode = false
    }

}