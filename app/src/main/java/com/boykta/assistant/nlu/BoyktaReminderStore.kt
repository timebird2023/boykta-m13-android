package com.boykta.assistant.nlu

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.boykta.assistant.receiver.BoyktaReminderReceiver
import org.json.JSONArray
import org.json.JSONObject

data class BoyktaReminderItem(
    val id: Long,
    val title: String,
    val triggerAtMillis: Long,
    val fired: Boolean
)

object BoyktaReminderStore {

    private const val PREF_NAME = "boykta_spoken_reminders_v1"
    private const val KEY_LIST = "reminders_json"

    @Synchronized
    fun getAllReminders(context: Context): List<BoyktaReminderItem> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST, "[]") ?: "[]"
        val list = mutableListOf<BoyktaReminderItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BoyktaReminderItem(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        triggerAtMillis = obj.getLong("triggerAtMillis"),
                        fired = obj.optBoolean("fired", false)
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list.sortedBy { it.triggerAtMillis }
    }

    @Synchronized
    fun getActiveReminders(context: Context): List<BoyktaReminderItem> {
        return getAllReminders(context).filter { !it.fired }
    }

    @Synchronized
    private fun saveAll(context: Context, items: List<BoyktaReminderItem>) {
        val arr = JSONArray()
        for (it in items.takeLast(30)) {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("title", it.title)
            obj.put("triggerAtMillis", it.triggerAtMillis)
            obj.put("fired", it.fired)
            arr.put(obj)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }

    @Synchronized
    fun addReminder(context: Context, title: String, triggerAtMillis: Long): BoyktaReminderItem {
        val cleanTitle = title.trim().ifEmpty { "موعدك المجدول الآن" }
        val item = BoyktaReminderItem(
            id = System.currentTimeMillis(),
            title = cleanTitle,
            triggerAtMillis = triggerAtMillis,
            fired = false
        )
        val current = getAllReminders(context).toMutableList()
        current.add(item)
        saveAll(context, current)
        scheduleSystemAlarm(context, item)
        return item
    }

    @Synchronized
    fun markFired(context: Context, id: Long) {
        val updated = getAllReminders(context).map {
            if (it.id == id) it.copy(fired = true) else it
        }
        saveAll(context, updated)
    }

    @Synchronized
    fun popDueReminders(context: Context, nowMillis: Long): List<BoyktaReminderItem> {
        val all = getAllReminders(context)
        val due = all.filter { !it.fired && it.triggerAtMillis <= nowMillis }
        if (due.isNotEmpty()) {
            val dueIds = due.map { it.id }.toSet()
            saveAll(context, all.map { if (it.id in dueIds) it.copy(fired = true) else it })
        }
        return due
    }

    @Synchronized
    fun clearAll(context: Context) {
        val all = getAllReminders(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (item in all) {
            try {
                am.cancel(buildPendingIntent(context, item))
            } catch (_: Exception) {
            }
        }
        saveAll(context, emptyList())
    }

    fun rescheduleAllPending(context: Context) {
        val now = System.currentTimeMillis()
        for (item in getActiveReminders(context)) {
            if (item.triggerAtMillis > now - 120_000L) {
                scheduleSystemAlarm(context, item)
            }
        }
    }

    private fun buildPendingIntent(context: Context, item: BoyktaReminderItem): PendingIntent {
        val intent = Intent(context, BoyktaReminderReceiver::class.java).apply {
            putExtra(BoyktaReminderReceiver.EXTRA_REMINDER_ID, item.id)
            putExtra(BoyktaReminderReceiver.EXTRA_REMINDER_TITLE, item.title)
        }
        return PendingIntent.getBroadcast(
            context,
            (item.id and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleSystemAlarm(context: Context, item: BoyktaReminderItem) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, item)
        val trigger = item.triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 2000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } catch (_: Exception) {
            }
        }
    }
}
