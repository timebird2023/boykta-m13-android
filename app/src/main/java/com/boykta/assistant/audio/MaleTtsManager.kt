package com.boykta.assistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class MaleTtsManager(
    private val context: Context,
    private val onReadyCallback: (() -> Unit)? = null,
    private val onSpeakingChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        const val STRICT_ARABIC_ONLY = true
    }

    private val inAppEngine = InAppArabicTtsEngine(context.applicationContext)
    private var sysTts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var sysReady = false
    private var sysArabicAvailable = false

    init {
        Thread {
            if (inAppEngine.isInstalled()) {
                inAppEngine.ensureInitialized()
                onReadyCallback?.invoke()
            }
        }.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            sysTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
            sysReady = true
            onReadyCallback?.invoke()
        }
    }

    fun refreshArabicCheck() {
        val localeDz = Locale("ar", "DZ")
        var res = sysTts?.setLanguage(localeDz) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            res = sysTts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        }

        val arabicVoices = sysTts?.voices?.filter { it.locale.language == "ar" } ?: emptyList()
        sysArabicAvailable = arabicVoices.isNotEmpty() ||
            (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED)

        val maleVoice = arabicVoices.firstOrNull { v ->
            v.name.contains("male", true) ||
                v.name.contains("ard", true) ||
                v.name.contains("m", true)
        } ?: arabicVoices.firstOrNull()

        if (maleVoice != null) {
            sysTts?.voice = maleVoice
        }
        sysTts?.setPitch(0.84f)
        sysTts?.setSpeechRate(0.95f)
    }

    fun reloadInAppVoice() {
        inAppEngine.reloadModel()
        onReadyCallback?.invoke()
    }

    fun isInAppVoiceInstalled(): Boolean = inAppEngine.isInstalled()

    fun isArabicReady(): Boolean = inAppEngine.isInstalled() || sysArabicAvailable

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

        // 1. Priority: Self-contained In-App Sherpa-ONNX Male Arabic Voice (Kareem)
        if (inAppEngine.isInstalled()) {
            inAppEngine.speakAsync(
                arabicText = cleanText,
                onStarted = { onSpeakingChanged?.invoke(true) },
                onFinished = { onSpeakingChanged?.invoke(false) }
            )
            return
        }

        // 2. Fallback: System Android TTS only if Arabic pack is installed on phone
        if (sysReady && sysArabicAvailable) {
            sysTts?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "boykta_${System.currentTimeMillis()}"
            )
            return
        }

        // 3. Never speak English! Play crisp confirmation chime while notifying UI to download Kareem 64MB
        onSpeakingChanged?.invoke(true)
        AudioChimes.playConfirmChime()
        Thread {
            Thread.sleep(220L)
            onSpeakingChanged?.invoke(false)
        }.start()
    }

    fun shutdown() {
        inAppEngine.shutdown()
        sysTts?.stop()
        sysTts?.shutdown()
        sysTts = null
    }
}
