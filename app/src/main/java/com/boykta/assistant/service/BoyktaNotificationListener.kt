package com.boykta.assistant.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.BoyktaUserProfileStore
import java.util.concurrent.ConcurrentHashMap

class BoyktaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: BoyktaNotificationListener? = null
            private set

        private val messagingPackages = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.instagram.android",
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "com.snapchat.android",
            "com.viber.voip",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging"
        )

        private val ignoredTitles = setOf(
            "whatsapp", "whatsapp business", "messenger", "instagram",
            "telegram", "messages", "snapchat", "viber",
            "واتساب", "رسائل", "الرسائل", "ماسنجر", "انستغرام"
        )

        fun isPermissionGranted(context: Context): Boolean {
            if (instance != null) return true
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val flat = ComponentName(context, BoyktaNotificationListener::class.java).flattenToString()
            return enabled.contains(flat) || enabled.contains(context.packageName)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pendingChecks = ConcurrentHashMap<String, Runnable>()
    private val lastAnnouncedBySender = ConcurrentHashMap<String, Long>()
    private var ttsManager: MaleTtsManager? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        if (ttsManager == null) {
            ttsManager = MaleTtsManager(applicationContext)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName || pkg !in messagingPackages) return

        val notif = sbn.notification ?: return
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val rawTitle = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (rawTitle.length < 2) return
        val cleanSender = rawTitle.replace(Regex("\\s*\\(\\d+.*?\\)$"), "").trim()
        val senderNorm = cleanSender.lowercase()
        if (senderNorm.length < 2 || senderNorm in ignoredTitles) return

        val freq = BoyktaUserProfileStore.recordSenderNotification(this, senderNorm)
        if (freq < 2) {
            return
        }

        pendingChecks.remove(sbn.key)?.let { handler.removeCallbacks(it) }

        val checkRunnable = Runnable {
            pendingChecks.remove(sbn.key)
            if (BoyktaUserProfileStore.isQuietNightHours()) return@Runnable

            val now = System.currentTimeMillis()
            val lastAnn = lastAnnouncedBySender[senderNorm] ?: 0L
            if (now - lastAnn < 180_000L) return@Runnable
            lastAnnouncedBySender[senderNorm] = now

            val salutation = BoyktaUserProfileStore.salutation(this)
            val msg = BoyktaUserProfileStore.pickVaried(
                "unread_msg",
                listOf(
                    "تنبيه $salutation، أرسل لك $cleanSender رسالة لم تقرأها بعد.",
                    "$salutation، لديك رسالة غير مقروءة من $cleanSender.",
                    "أخبرك $salutation أن $cleanSender بعث لك رسالة جديدة ولم تفتحها بعد."
                )
            )

            val update = Intent(BoyktaEvents.ACTION_STATE_UPDATE).apply {
                setPackage(packageName)
                putExtra(BoyktaEvents.EXTRA_TYPE, BoyktaEvents.TYPE_COMMAND)
                putExtra(BoyktaEvents.EXTRA_TEXT, "إشعار غير مقروء من $cleanSender")
                putExtra(BoyktaEvents.EXTRA_REPLY, msg)
            }
            sendBroadcast(update)

            if (ttsManager == null) {
                ttsManager = MaleTtsManager(applicationContext)
            }
            ttsManager?.speak(msg)
        }

        pendingChecks[sbn.key] = checkRunnable
        handler.postDelayed(checkRunnable, 24_000L)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        pendingChecks.remove(sbn.key)?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        for (r in pendingChecks.values) {
            handler.removeCallbacks(r)
        }
        pendingChecks.clear()
        ttsManager?.shutdown()
        ttsManager = null
        instance = null
        super.onDestroy()
    }
}
