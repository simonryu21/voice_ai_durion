package com.lgcns.codeengine.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.PUSH_DATA
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.LOAD_URL
import com.lgcns.codeengine.web.PluginPermissions
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

open class FCMSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var pluginPermissions: PluginPermissions

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("Notifications permission granted")
        } else {
            Timber.w("FCM can't post notifications without POST_NOTIFICATIONS permission")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /////////////////////////////////////////////////////
        // FCM 관련 설정입니다.
        // Main Activity는 onCreate()시에
        // 아래 코드가 실행되도록 FCMPushActivity를 상속받습니다.
        /////////////////////////////////////////////////////

        Timber.d("initialize FCM service - Start")

        askNotificationPermission()

        createChannelIfNeeded()

        checkNotificationTapped()

        Timber.d("initialize FCM service - End")
    }

    private fun askNotificationPermission() {
        // This is only necessary for API Level > 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch {
                if (!pluginPermissions.notificationPermission.grant()) {
                    // Directly ask for the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun createChannelIfNeeded() {
        val channelId = getString(R.string.default_notification_channel_id) // 채널(공지, 쿠폰, 결제 등)별로 채널을 나눠서 Notification을 다르게 보여줄 수 있습니다.
        val channelName = getString(R.string.default_notification_channel_name)

        for (channel in NotificationManagerCompat.from(this).notificationChannels) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (channelId == channel.id) return
            }
        }

        // Create channel to show notifications.
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(channelId,
                    channelName, NotificationManager.IMPORTANCE_HIGH)
            )
        } else {
            Timber.e("This version(${Build.VERSION.SDK_INT} does not support the notification channel.")
        }
    }

    private fun checkNotificationTapped() {
        // If a notification message is tapped, any data accompanying the notification
        // message is available in the intent extras.

        intent.extras?.let {
            for (key in it.keySet()) {
                @Suppress("DEPRECATION") val value = intent.extras?.get(key)
                Timber.d("Key: $key Value: $value")
            }
        }

        if (intent.extras?.getString(LOAD_URL) != null ||
            intent.extras?.getString(PUSH_DATA) != null)
        {
            val bundle = Bundle()

            if (intent.extras?.getString(LOAD_URL) != null) bundle.putString(
                LOAD_URL,
                intent.extras?.getString(LOAD_URL)
            )

            if (intent.extras?.getString(PUSH_DATA) != null)
                bundle.putString(
                    PUSH_DATA,
                    intent.extras?.getString(PUSH_DATA)
                )
            else
                bundle.putString(PUSH_DATA, "")

            val intent = Intent(this, WebViewActivity::class.java)
            intent.putExtras(bundle)

            TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(intent)
                .startActivities()
        }
    }

}
