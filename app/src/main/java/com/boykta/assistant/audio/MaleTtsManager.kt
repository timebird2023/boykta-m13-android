package com.boykta.assistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class MaleTtsManager(
    context: Context,
    private val onReadyCallback: (() -> Unit)? = null,
    private val onSpeakingChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        const val STRICT_ARABIC_ONLY = true
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var arabicAvailable = false
    private val pendingQueue = mutableListOf<String>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeakingChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeakingChanged?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeakingChanged?.invoke(false)
                }
            })
            refreshArabicCheck()
            ready = true
            pendingQueue.forEach { speak(it) }
            pendingQueue.clear()
            onReadyCallback?.invoke()
        }
    }

    fun refreshArabicCheck() {
        val localeDz = Locale("ar", "DZ")
        var res = tts?.setLanguage(localeDz) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            res = tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        }

        val arabicVoices = tts?.voices?.filter { it.locale.language == "ar" } ?: emptyList()
        arabicAvailable = arabicVoices.isNotEmpty() ||
            (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED)

        val maleVoice = arabicVoices.firstOrNull { v ->
            v.name.contains("male", true) ||
                v.name.contains("ard", true) ||
                v.name.contains("m", true)
        } ?: arabicVoices.firstOrNull()

        if (maleVoice != null) {
            tts?.voice = maleVoice
        }
        tts?.setPitch(0.84f)
        tts?.setSpeechRate(0.95f)
    }

    fun isArabicReady(): Boolean = arabicAvailable

    private fun enforceArabicWords(input: String): String {
        return input
            .replace("Free Fire", "فري فاير", ignoreCase = true)
            .replace("Facebook", "فيسبوك", ignoreCase = true)
            .replace("WhatsApp", "واتساب", ignoreCase = true)
            .replace("YouTube", "يوتيوب", ignoreCase = true)
            .replace("Instagram", "انستغرام", ignoreCase = true)
            .replace("TikTok", "تيك توك", ignoreCase = true)
            .replace("Accessibility", "التحكم الشامل", ignoreCase = true)
            .replace("Write Settings", "تعديل الإعدادات", ignoreCase = true)
            .replace("RAM", "الرام", ignoreCase = true)
            .replace("MB", "ميغا", ignoreCase = true)
    }

    fun speak(text: String) {
        val cleanText = if (STRICT_ARABIC_ONLY) enforceArabicWords(text) else text
        if (!ready) {
            pendingQueue.add(cleanText)
            return
        }
        if (STRICT_ARABIC_ONLY && !arabicAvailable) {
            return
        }
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "boykta_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
