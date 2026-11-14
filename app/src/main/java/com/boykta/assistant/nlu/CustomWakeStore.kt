package com.boykta.assistant.nlu

import android.content.Context
import org.json.JSONArray

object CustomWakeStore {
    private const val PREF_NAME = "boykta_custom_wake"
    private const val KEY_PHRASES = "phrases_json"

    fun getPhrases(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PHRASES, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val p = arr.optString(i, "").trim()
                if (p.isNotEmpty()) list.add(p)
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun addPhrase(context: Context, phrase: String): String {
        val cleaned = WakeWordMatcher.normalize(phrase)
        if (cleaned.length < 2) return ""
        val existing = getPhrases(context).toMutableList()
        if (existing.none { it == cleaned }) {
            existing.add(cleaned)
        }
        val arr = JSONArray()
        existing.takeLast(12).forEach { arr.put(it) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PHRASES, arr.toString())
            .apply()
        return cleaned
    }
}
