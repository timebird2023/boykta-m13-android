package com.boykta.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.boykta.assistant.storage.SdCardModelLocator
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

object InAppArabicTtsEngine {

    @Volatile
    private var offlineTts: OfflineTts? = null

    @Volatile
    private var loadedPath: String? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()
    private val generationId = AtomicLong(0L)

    fun addSpeakingListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeSpeakingListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifySpeaking(speaking: Boolean) {
        listeners.forEach { cb ->
            try {
                cb(speaking)
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        val appCtx = context.applicationContext
        val dir = SdCardModelLocator.findKareemTtsDir(appCtx) ?: return false
        if (offlineTts != null && loadedPath == dir.absolutePath) return true

        return try {
            offlineTts?.release()
            val onnxFile = File(dir, "ar_JO-kareem-low.onnx")
            val tokensFile = File(dir, "tokens.txt")
            val espeakDir = File(dir, "espeak-ng-data")
            if (!onnxFile.exists() || !tokensFile.exists() || !espeakDir.exists()) {
                return false
            }
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = onnxFile.absolutePath,
                        tokens = tokensFile.absolutePath,
                        dataDir = espeakDir.absolutePath,
                        noiseScale = 0.6f,
                        noiseScaleW = 0.75f,
                        lengthScale = 1.0f
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            )
            offlineTts = OfflineTts(assetManager = null, config = config)
            loadedPath = dir.absolutePath
            true
        } catch (_: Throwable) {
            offlineTts = null
            loadedPath = null
            false
        }
    }

    fun isInstalled(context: Context): Boolean = SdCardModelLocator.findKareemTtsDir(context.applicationContext) != null

    fun reloadModel(context: Context) {
        val appCtx = context.applicationContext
        executor.execute {
            loadedPath = null
            ensureInitialized(appCtx)
        }
    }

    fun speakAsync(
        context: Context,
        arabicText: String,
        onFinishedExtra: (() -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        val myGen = generationId.incrementAndGet()

        executor.execute {
            if (myGen != generationId.get()) {
                onFinishedExtra?.invoke()
                return@execute
            }

            val ok = ensureInitialized(appCtx)
            val engine = offlineTts
            if (!ok || engine == null) {
                onFinishedExtra?.invoke()
                return@execute
            }

            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            var focusRequest: AudioFocusRequest? = null
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            if (am != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attrs)
                            .build()
                        am.requestAudioFocus(focusRequest)
                    } else {
                        @Suppress("DEPRECATION")
                        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    }
                } catch (_: Throwable) {
                }
            }

            notifySpeaking(true)
            try {
                val prepared = prepareArabicForPiper(arabicText)
                val phrases = splitIntoSmoothPhrases(prepared)
                for (index in phrases.indices) {
                    if (myGen != generationId.get()) break
                    val phrase = phrases[index]
                    if (phrase.isBlank()) continue
                    val generated = engine.generate(text = phrase, sid = 0, speed = 1.0f)
                    if (generated.samples.isNotEmpty() && generated.sampleRate > 0) {
                        playSamplesStaticAndWait(generated.samples, generated.sampleRate, attrs, myGen)
                    }
                    if (index < phrases.lastIndex && myGen == generationId.get()) {
                        Thread.sleep(65L)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                if (am != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                            am.abandonAudioFocusRequest(focusRequest)
                        } else {
                            @Suppress("DEPRECATION")
                            am.abandonAudioFocus(null)
                        }
                    } catch (_: Throwable) {
                    }
                }
                if (myGen == generationId.get()) {
                    notifySpeaking(false)
                }
                onFinishedExtra?.invoke()
            }
        }
    }

    private fun splitIntoSmoothPhrases(text: String): List<String> {
        val rawParts = text.split('.', '،', '!', '؟', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawParts.isEmpty()) return listOf(text)

        val result = mutableListOf<String>()
        for (part in rawParts) {
            if (part.length <= 130) {
                result.add(part)
            } else {
                val words = part.split(" ")
                val sb = StringBuilder()
                for (w in words) {
                    if (sb.length + w.length + 1 > 125 && sb.isNotEmpty()) {
                        result.add(sb.toString().trim())
                        sb.clear()
                    }
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(w)
                }
                if (sb.isNotEmpty()) result.add(sb.toString().trim())
            }
        }
        return result
    }

    private fun playSamplesStaticAndWait(
        samples: FloatArray,
        sampleRate: Int,
        attrs: AudioAttributes,
        myGen: Long
    ) {
        var maxPeak = 0.001f
        for (s in samples) {
            val a = abs(s)
            if (a > maxPeak) maxPeak = a
        }
        val gain = (0.88f / maxPeak).coerceIn(0.8f, 2.2f)

        val leadSilence = (sampleRate * 12) / 1000
        val tailSilence = (sampleRate * 165) / 1000
        val fadeInSamples = minOf((sampleRate * 10) / 1000, samples.size / 4)
        val fadeOutSamples = minOf((sampleRate * 24) / 1000, samples.size / 4)
        val totalFrames = leadSilence + samples.size + tailSilence
        val pcm = ShortArray(totalFrames)

        for (i in samples.indices) {
            var env = 1.0f
            if (fadeInSamples > 0 && i < fadeInSamples) {
                env *= (0.5 * (1.0 - cos(PI * i / fadeInSamples))).toFloat()
            }
            val distFromEnd = samples.size - 1 - i
            if (fadeOutSamples > 0 && distFromEnd < fadeOutSamples) {
                env *= (0.5 * (1.0 - cos(PI * distFromEnd / fadeOutSamples))).toFloat()
            }
            val scaled = (samples[i] * gain * env * 32767f).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[leadSilence + i] = scaled.toShort()
        }

        val byteLen = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(byteLen)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val doneLatch = CountDownLatch(1)
        try {
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                return
            }
            track.setNotificationMarkerPosition(totalFrames)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    doneLatch.countDown()
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })

            track.play()
            val expectedMs = (totalFrames * 1000L) / sampleRate
            val deadlineMs = System.currentTimeMillis() + expectedMs + 850L
            while (System.currentTimeMillis() < deadlineMs && myGen == generationId.get()) {
                if (doneLatch.await(90L, TimeUnit.MILLISECONDS)) break
                val head = track.playbackHeadPosition
                if (head >= totalFrames - 32) break
            }
        } finally {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.stop()
            } catch (_: Throwable) {
            }
            try {
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun prepareArabicForPiper(input: String): String {
        return input
            .replace("24/24", " أربعة وعشرين على أربعة وعشرين ")
            .replace("M13", " إم ثلاثة عشر ", ignoreCase = true)
            .replace("%", " بالمئة ")
            .replace("MB", " ميغابايت ", ignoreCase = true)
            .replace("GB", " جيغابايت ", ignoreCase = true)
            .replace("OK Google", " أوكي غوغل ", ignoreCase = true)
            .replace("Free Fire", " فري فاير ", ignoreCase = true)
            .replace("Facebook", " فيسبوك ", ignoreCase = true)
            .replace("WhatsApp", " واتساب ", ignoreCase = true)
            .replace("Accessibility", " التحكم الشامل ", ignoreCase = true)
            .replace("Write Settings", " تعديل الإعدادات ", ignoreCase = true)
            .replace("RAM", " الرام ", ignoreCase = true)
            .replace("/", " على ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
