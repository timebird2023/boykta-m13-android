package com.boykta.assistant.nlu

import android.content.Context
import java.util.Locale

object WakeWordMatcher {

    private val knownVoskVariants = listOf(
        "بويكتا",
        "بوكتا",
        "بويكته",
        "بيكتا",
        "بيكته",
        "بيكتها",
        "بوكتها",
        "بويكتها",
        "بوكيتا",
        "بايكتا",
        "بايكته",
        "باكتا",
        "ويكتا",
        "ويكتها",
        "وكتا",
        "وكتها",
        "بويقتا",
        "بويقته",
        "بوقتا",
        "بوقتها",
        "ورقدتها",
        "ورقتها",
        "وركتها",
        "رقدتها",
        "وقتها",
        "فوقتها",
        "فقتها",
        "بركتها",
        "ويقتا",
        "فويكتا",
        "مويكتا",
        "بويكاتا",
        "بوكاتا",
        "بوياكتا",
        "بويك تا",
        "بوي كتا",
        "بو يكتا",
        "بيك تا",
        "بويكت",
        "بوكت",
        "بيكت",
        "بوكتو",
        "بويكتو",
        "بو يكت",
        "يا بوكتا",
        "يا بويكتا",
        "يا بيكتا",
        "اوكي بويكتا",
        "اوكي بوكتا",
        "يا مساعد",
        "يا روبوت"
    )

    private val rootChars = setOf('ب', 'و', 'ي', 'ك', 'ت', 'ق', 'ط', 'ر', 'د')

    fun normalize(input: String): String {
        return input.trim()
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    fun containsWakeWord(
        rawText: String,
        configuredWakeWord: String = "بويكتا",
        context: Context? = null
    ): Boolean {
        val norm = normalize(rawText)
        if (norm.isEmpty()) return false

        val wakeNorm = normalize(configuredWakeWord)
        if (wakeNorm.isNotEmpty() && norm.contains(wakeNorm)) return true
        if (knownVoskVariants.any { norm.contains(it) }) return true

        val customPhrases = if (context != null) CustomWakeStore.getPhrases(context) else emptyList()
        if (customPhrases.any { p -> p.length >= 2 && norm.contains(p) }) return true

        val words = norm.split(" ").filter { it.isNotBlank() }
        for (i in words.indices) {
            if (isFuzzyMatch(words[i], customPhrases)) return true
            if (i + 1 < words.size) {
                val combined = words[i] + words[i + 1]
                if (isFuzzyMatch(combined, customPhrases)) return true
                val spaced = words[i] + " " + words[i + 1]
                if (customPhrases.any { p -> levenshtein(spaced, p) <= 1 }) return true
            }
        }
        return false
    }

    fun stripWakeWord(
        rawText: String,
        configuredWakeWord: String = "بويكتا",
        context: Context? = null
    ): String {
        var norm = normalize(rawText)
        val wakeNorm = normalize(configuredWakeWord)
        if (wakeNorm.isNotEmpty()) {
            norm = norm.replace(wakeNorm, " ")
        }
        val customPhrases = if (context != null) CustomWakeStore.getPhrases(context) else emptyList()
        for (phrase in customPhrases) {
            norm = norm.replace(phrase, " ")
        }
        for (variant in knownVoskVariants) {
            norm = norm.replace(variant, " ")
        }
        val words = norm.split(" ").filter { it.isNotBlank() }
        val kept = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            if (isFuzzyMatch(words[i], customPhrases)) {
                i++
                continue
            }
            if (i + 1 < words.size && isFuzzyMatch(words[i] + words[i + 1], customPhrases)) {
                i += 2
                continue
            }
            kept.add(words[i])
            i++
        }
        return kept.joinToString(" ")
            .replace(Regex("^(يا|اهلا|اوكي|اسمع|خويا)\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isFuzzyMatch(candidate: String, customPhrases: List<String> = emptyList()): Boolean {
        if (candidate.length >= 3 && customPhrases.any { p -> p.length >= 3 && levenshtein(candidate, p) <= 1 }) {
            return true
        }
        if (candidate.length !in 4..9) return false
        val first = candidate[0]
        if (first !in listOf('ب', 'و', 'ف', 'م', 'ي', 'ك', 'ر')) return false
        val sharedRootCount = candidate.count { it in rootChars }
        if (sharedRootCount < 3) return false
        return levenshtein(candidate, "بويكتا") <= 2 ||
            levenshtein(candidate, "بوكتا") <= 1 ||
            levenshtein(candidate, "ورقدتها") <= 1
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) {
                    prev
                } else {
                    1 + minOf(prev, dp[j], dp[j - 1])
                }
                prev = temp
            }
        }
        return dp[n]
    }
}
