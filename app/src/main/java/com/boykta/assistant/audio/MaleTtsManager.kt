package com.boykta.assistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.boykta.assistant.storage.SdCardModelLocator
import java.util.Locale

class MaleTtsManager(
    private val context: Context,
    private val onReadyCallback: (() -> Unit)? = null,
    private val onSpeakingChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        const val STRICT_ARABIC_ONLY = true
    }

    private val appContext = context.applicationContext
    private var sysTts: TextToSpeech? = TextToSpeech(appContext, this)
    private var sysReady = false
    private var sysArabicAvailable = false

    private val speakingListener: (Boolean) -> Unit = { speaking ->
        onSpeakingChanged?.invoke(speaking)
    }

    init {
        InAppArabicTtsEngine.addSpeakingListener(speakingListener)
        Thread {
            if (InAppArabicTtsEngine.isInstalled(appContext)) {
                InAppArabicTtsEngine.ensureInitialized(appContext)
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
        val localeSa = Locale("ar", "SA")
        var res = sysTts?.setLanguage(localeSa) ?: TextToSpeech.LANG_NOT_SUPPORTED
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
        sysTts?.setPitch(0.88f)
        sysTts?.setSpeechRate(0.96f)
    }

    fun reloadInAppVoice() {
        InAppArabicTtsEngine.reloadModel(appContext)
        onReadyCallback?.invoke()
    }

    fun isInAppVoiceInstalled(): Boolean = InAppArabicTtsEngine.isInstalled(appContext)

    fun isSystemArabicAvailable(): Boolean = sysReady && sysArabicAvailable

    fun isArabicReady(): Boolean = InAppArabicTtsEngine.isInstalled(appContext) || sysArabicAvailable

    fun speak(text: String) {
        val cleanText = if (STRICT_ARABIC_ONLY) ArabicNumberToWords.prepareForFushaTts(text) else text
        val preferSystem = SdCardModelLocator.getPreferSystemTts(appContext)

        if (preferSystem && sysReady && sysArabicAvailable) {
            sysTts?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "boykta_${System.currentTimeMillis()}"
            )
            return
        }

        if (InAppArabicTtsEngine.isInstalled(appContext)) {
            InAppArabicTtsEngine.speakAsync(
                context = appContext,
                arabicText = text
            )
            return
        }

        if (sysReady && sysArabicAvailable) {
            sysTts?.speak(
                cleanText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "boykta_${System.currentTimeMillis()}"
            )
            return
        }

        onSpeakingChanged?.invoke(true)
        AudioChimes.playConfirmChime()
        Thread {
            Thread.sleep(220L)
            onSpeakingChanged?.invoke(false)
        }.start()
    }

    fun shutdown() {
        InAppArabicTtsEngine.removeSpeakingListener(speakingListener)
        sysTts?.stop()
        sysTts?.shutdown()
        sysTts = null
    }
}
