package com.boykta.assistant.nlu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UserCustomRule(
    val trigger: String,
    val reply: String
)

object CustomRuleStore {
    private const val PREF_NAME = "boykta_custom_rules"
    private const val KEY_RULES = "rules_json"

    fun getRules(context: Context): List<UserCustomRule> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RULES, "[]") ?: "[]"
        val list = mutableListOf<UserCustomRule>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UserCustomRule(
                        trigger = obj.optString("trigger", ""),
                        reply = obj.optString("reply", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun addRule(context: Context, trigger: String, reply: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_RULES, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val obj = JSONObject().apply {
            put("trigger", trigger)
            put("reply", reply)
        }
        arr.put(obj)
        prefs.edit().putString(KEY_RULES, arr.toString()).apply()
    }
}
