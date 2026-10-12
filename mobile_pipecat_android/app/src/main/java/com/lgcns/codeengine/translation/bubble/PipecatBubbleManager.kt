package com.lgcns.codeengine.translation.bubble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.PipecatBubbleActivity

/**
 * Posts (and cancels) the Pipecat system Bubble — a floating, expandable
 * conversation surface available on Android 11+ (API 30).
 *
 * A bubble notification must be a "conversation" notification, so this also
 * publishes a long-lived dynamic shortcut and attaches a [Person] +
 * `MessagingStyle`; the expanded content is [PipecatBubbleActivity].
 */
@RequiresApi(Build.VERSION_CODES.R)
object PipecatBubbleManager {

    private const val CHANNEL_ID = "pipecat_bubble_channel"
    private const val SHORTCUT_ID = "pipecat_bubble_shortcut"
    private const val NOTIFICATION_ID = 1043

    /**
     * Posts the bubble notification.
     *
     * @return false when notifications are disabled (the bubble cannot show);
     *         true when the notification was posted.
     */
    fun show(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        createChannel(context)

        val person = Person.Builder()
            .setName("Pipecat")
            .setKey("pipecat-assistant")
            .setBot(true)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .build()

        pushShortcut(context, person)

        val target = Intent(context, PipecatBubbleActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            target,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)

        val bubble = NotificationCompat.BubbleMetadata.Builder(pendingIntent, icon)
            .setDesiredHeight(BUBBLE_HEIGHT_DP)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setShortcutId(SHORTCUT_ID)
            .addPerson(person)
            .setBubbleMetadata(bubble)
            .setStyle(
                NotificationCompat.MessagingStyle(person)
                    .addMessage("음성 비서 준비됨", System.currentTimeMillis(), person),
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    /** Cancels the bubble notification (collapses and removes the bubble). */
    fun hide(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pipecat 버블",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Pipecat 음성 비서 버블"
            setAllowBubbles(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun pushShortcut(context: Context, person: Person) {
        val target = Intent(context, PipecatBubbleActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setLongLived(true)
            .setShortLabel("Pipecat")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(target)
            .setPerson(person)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private const val BUBBLE_HEIGHT_DP = 120
}
