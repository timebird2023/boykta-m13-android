package com.boykta.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.boykta.assistant.audio.ArabicNumberToWords
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.BoyktaUserProfileStore

class CallAnnouncerReceiver : BroadcastReceiver() {

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingUnknownRunnable: Runnable? = null
        private var lastSpokenKey: String = ""
        private var lastSpokenAtMs: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!true) return
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.trim()
        val appCtx = context.applicationContext
        val sal = BoyktaUserProfileStore.salutation(appCtx)

        if (!incomingNumber.isNullOrBlank()) {
            pendingUnknownRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingUnknownRunnable = null

            val cleanDigits = incomingNumber.filter { it.isDigit() || it == '+' }
            val key = cleanDigits.ifEmpty { incomingNumber }
            val now = System.currentTimeMillis()
            if (key == lastSpokenKey && now - lastSpokenAtMs < 8000L) return
            lastSpokenKey = key
            lastSpokenAtMs = now

            val contactName = lookupContactName(appCtx, incomingNumber)
            val msg = if (!contactName.isNullOrBlank()) {
                BoyktaUserProfileStore.pickVaried(
                    "caller_name",
                    listOf(
                        "$sal، يتصل بك الآن $contactName.",
                        "مكالمة واردة $sal من $contactName."
                    )
                )
            } else {
                val spokenDigits = ArabicNumberToWords.digitByDigit(cleanDigits.ifEmpty { incomingNumber })
                BoyktaUserProfileStore.pickVaried(
                    "caller_digits",
                    listOf(
                        "مكالمة واردة $sal من الرقم: $spokenDigits.",
                        "$sal، يتصل بك الرقم: $spokenDigits."
                    )
                )
            }
            MaleTtsManager(appCtx).speak(msg)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastSpokenAtMs < 8000L) return
            pendingUnknownRunnable?.let { mainHandler.removeCallbacks(it) }
            val fallback = Runnable {
                val innerNow = System.currentTimeMillis()
                if (innerNow - lastSpokenAtMs < 8000L) return@Runnable
                lastSpokenKey = "UNKNOWN"
                lastSpokenAtMs = innerNow
                MaleTtsManager(appCtx).speak("مكالمة واردة $sal من رقم مخفي.")
            }
            pendingUnknownRunnable = fallback
            mainHandler.postDelayed(fallback, 450L)
        }
    }

    private fun lookupContactName(context: Context, phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val cursor = try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
        } catch (_: SecurityException) {
            null
        } ?: return null

        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }
}
