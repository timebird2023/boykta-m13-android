package com.boykta.assistant.nlu

import android.content.Context
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object BoyktaUserProfileStore {

    private const val PREF_NAME = "boykta_user_profile_prefs"
    private const val PREF_FREQ = "boykta_notif_sender_freq"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_AUTO_CLOSE_IDLE_RADIOS = "auto_close_idle_radios"
    private const val KEY_LAST_TOPIC = "last_topic"
    private const val DEFAULT_NAME = "يونس"

    private val lastPoolIndexes = ConcurrentHashMap<String, Int>()

    fun getUserName(context: Context): String {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_NAME, DEFAULT_NAME)?.trim().orEmpty()
        return raw
    }

    fun setUserName(context: Context, name: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_NAME, name.trim())
            .apply()
    }

    fun salutation(context: Context): String {
        val name = getUserName(context)
        return if (name.isNotEmpty()) "يا $name" else "يا صديقي"
    }

    fun shortNameOrFriend(context: Context): String {
        val name = getUserName(context)
        return if (name.isNotEmpty()) name else "صديقي"
    }

    fun isQuietNightHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 7
    }

    fun getAutoCloseIdleRadios(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CLOSE_IDLE_RADIOS, true)
    }

    fun setAutoCloseIdleRadios(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CLOSE_IDLE_RADIOS, enabled)
            .apply()
    }

    fun rememberLastTopic(context: Context, topic: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TOPIC, topic)
            .apply()
    }

    fun getLastTopic(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_TOPIC, "") ?: ""
    }

    fun recordSenderNotification(context: Context, senderKey: String): Int {
        val clean = senderKey.trim().lowercase()
        if (clean.isEmpty()) return 0
        val prefs = context.getSharedPreferences(PREF_FREQ, Context.MODE_PRIVATE)
        val next = prefs.getInt(clean, 0) + 1
        prefs.edit().putInt(clean, next).apply()
        return next
    }

    fun getSenderFrequency(context: Context, senderKey: String): Int {
        val clean = senderKey.trim().lowercase()
        if (clean.isEmpty()) return 0
        return context.getSharedPreferences(PREF_FREQ, Context.MODE_PRIVATE).getInt(clean, 0)
    }

    fun pickVaried(poolKey: String, options: List<String>): String {
        if (options.isEmpty()) return ""
        if (options.size == 1) return options[0]
        val prev = lastPoolIndexes[poolKey] ?: -1
        val next = (prev + 1 + (System.currentTimeMillis() % options.size).toInt()) % options.size
        val chosen = if (next == prev) (prev + 1) % options.size else next
        lastPoolIndexes[poolKey] = chosen
        return options[chosen]
    }
}
