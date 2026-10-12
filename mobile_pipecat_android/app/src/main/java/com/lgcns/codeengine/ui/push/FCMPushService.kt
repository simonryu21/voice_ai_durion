package com.lgcns.codeengine.ui.push

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.PUSH_DATA
import com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.LOAD_URL
import com.lgcns.codeengine.activity.WebViewActivity
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger


class FCMPushService : FirebaseMessagingService() {

    private val issuer = AtomicInteger(0)

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM(Google Cloud Messaging). Notification messages are only received here in
        // onMessageReceived when the app is in the foreground.
        // When the app is in the background an automatically generated notification is displayed by Android OS.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options

        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Timber.d("onMessageReceived() From: ${remoteMessage.from}")

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Timber.d("Message Notification Body: ${it.body}, Title: ${it.title}")
            val messageData = remoteMessage.data
            sendNotification(it.title, it.body, messageData)
        }

        val messageData = remoteMessage.data

        // Check if message contains a data payload.
        if (messageData.isNotEmpty()) {
            Timber.d("Message data payload: $messageData")
        }

    }

    /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        Timber.d("Refreshed token: $token")
        // We get a latest token by FirebaseMessaging.getInstance().token.await().
        // So we don't need to save this.

        // FCM Notification Message는 Channel을 통해서 받도록 합니다.
        // Topic은 사용자가 subscription, unsubscription을 통해 선택할 수 있을 때에만 사용하는 것이 좋습니다.
        /*
        Firebase.messaging.subscribeToTopic(applicationContext.getString(R.string.default_notification_topic))
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Timber.d("The subscription was succeed to %s.", applicationContext.getString(R.string.default_notification_topic))
                } else {
                    Timber.e("The subscription was failed to %s .", applicationContext.getString(R.string.default_notification_topic))
                }
            }
        */
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param messageBody FCM message body received.
     */
    private fun sendNotification(messageTitle: String?, messageBody: String?, messageData: Map<String, String>) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        Timber.d("PUSH_URL=${messageData[LOAD_URL]}, PUSH_DATA=${messageData[PUSH_DATA]}")

        val bundle = Bundle()

        if (messageData[LOAD_URL] != null) bundle.putString(LOAD_URL, messageData[LOAD_URL])

        if (messageData[PUSH_DATA] != null)
            bundle.putString(PUSH_DATA, messageData[PUSH_DATA])
        else
            bundle.putString(PUSH_DATA, "")

        intent.putExtras(bundle)

        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE)

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(messageTitle)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(issueNotificationId(), notificationBuilder.build())
    }

    /**
     * 알림을 위한 알림 아이디를 발행합니다.
     *
     * @return notificationId to notify a notification.
     */
    private fun issueNotificationId(): Int {
        return issuer.incrementAndGet()
    }
}
