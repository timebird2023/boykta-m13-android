package com.boykta.assistant.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.boykta.assistant.R
import com.boykta.assistant.audio.AudioChimes
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.BoyktaReminderStore
import com.boykta.assistant.nlu.BoyktaUserProfileStore
import com.boykta.assistant.service.BoyktaEvents

class BoyktaReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        private const val CHANNEL_ID = "boykta_reminders_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) return

        if (id > 0L) {
            BoyktaReminderStore.markFired(context, id)
        }

        triggerReminderAlert(context, title, (id and 0x7FFF).toInt().coerceAtLeast(2100))
    }

    fun triggerReminderAlert(context: Context, title: String, notifId: Int) {
        val salutation = BoyktaUserProfileStore.salutation(context)
        val spoken = "تنبيه $salutation: $title."

        AudioChimes.playWakeChime(context)
        postReminderNotification(context, notifId, spoken, title)

        val update = Intent(BoyktaEvents.ACTION_STATE_UPDATE).apply {
            setPackage(context.packageName)
            putExtra(BoyktaEvents.EXTRA_TYPE, BoyktaEvents.TYPE_COMMAND)
            putExtra(BoyktaEvents.EXTRA_TEXT, "تذكير مجدول")
            putExtra(BoyktaEvents.EXTRA_REPLY, spoken)
        }
        context.sendBroadcast(update)

        val tts = MaleTtsManager(context.applicationContext)
        tts.speak(spoken)
    }

    private fun postReminderNotification(
        context: Context,
        notifId: Int,
        body: String,
        title: String
    ) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "تذكيرات بويكتا الناطقة",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(ch)
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_boykta_mic)
                .setContentTitle("تذكير من بويكتا: $title")
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(notifId, notif)
        } catch (_: Exception) {
        }
    }
}
