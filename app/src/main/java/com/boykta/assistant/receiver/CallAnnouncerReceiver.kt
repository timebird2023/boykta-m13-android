package com.boykta.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.boykta.assistant.audio.MaleTtsManager

class CallAnnouncerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!true) return
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            @Suppress("DEPRECATION")
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            val callerName = if (!incomingNumber.isNullOrBlank()) {
                lookupContactName(context, incomingNumber) ?: "رقم غير مسجل"
            } else {
                "شخص ما"
            }
            val tts = MaleTtsManager(context)
            tts.speak("تنبيه، المتصل هو $callerName.")
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
