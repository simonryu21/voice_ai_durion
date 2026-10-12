package com.lgcns.codeengine.translation.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lgcns.codeengine.R
import com.lgcns.codeengine.translation.ui.components.OverlayBar
import com.lgcns.codeengine.translation.ui.theme.PipecatPalette
import timber.log.Timber

/**
 * Foreground service that draws the Pipecat [OverlayBar] on top of other apps
 * via `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
 *
 * The service itself acts as the [LifecycleOwner]/[ViewModelStoreOwner]/
 * [SavedStateRegistryOwner] for the hosted [ComposeView] — Compose needs those
 * tree owners to drive recomposition and `rememberSaveable` outside an Activity.
 *
 * Requires the `SYSTEM_ALERT_WINDOW` permission; if it is not granted the
 * service stops itself immediately (callers should check
 * `Settings.canDrawOverlays` first).
 */
class PipecatOverlayService :
    Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            Timber.w("PipecatOverlayService: SYSTEM_ALERT_WINDOW not granted, stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (overlayView == null) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            showOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
                .onFailure { Timber.e(it, "Failed to remove overlay view") }
        }
        overlayView = null
        store.clear()
        super.onDestroy()
    }

    private fun showOverlay() {
        val wm = windowManager ?: return
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PipecatOverlayService)
            setViewTreeViewModelStoreOwner(this@PipecatOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PipecatOverlayService)
            setContent {
                val palette = remember(isDark) { PipecatPalette.forTheme(isDark) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    OverlayBar(
                        palette = palette,
                        isDark = isDark,
                        onStopTap = { stopSelf() },
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Touches on the bar are delivered; touches outside fall through to
            // the host app, and the bar never grabs keyboard focus.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
        }

        runCatching {
            wm.addView(composeView, params)
            overlayView = composeView
        }.onFailure {
            Timber.e(it, "Failed to add overlay view")
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pipecat 오버레이",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Pipecat 음성 오버레이가 실행 중임을 나타냅니다"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, PipecatOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Pipecat 음성 오버레이가 실행 중입니다")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "중지", stopPending)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1042
        private const val CHANNEL_ID = "pipecat_overlay_channel"
        const val ACTION_STOP = "com.lgcns.codeengine.action.PIPECAT_OVERLAY_STOP"

        /** Start the overlay. Caller must ensure `Settings.canDrawOverlays` is true. */
        fun start(context: Context) {
            val intent = Intent(context, PipecatOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PipecatOverlayService::class.java))
        }
    }
}
