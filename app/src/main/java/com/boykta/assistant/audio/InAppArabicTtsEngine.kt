package com.boykta.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.boykta.assistant.storage.SdCardModelLocator
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors

class InAppArabicTtsEngine(private val context: Context) {

    @Volatile
    private var offlineTts: OfflineTts? = null

    @Volatile
    private var loadedPath: String? = null

    private val executor = Executors.newSingleThreadExecutor()

    @Synchronized
    fun ensureInitialized(): Boolean {
        val dir = SdCardModelLocator.findKareemTtsDir(context) ?: return false
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
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 0.96f
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

    fun isInstalled(): Boolean = SdCardModelLocator.findKareemTtsDir(context) != null

    fun reloadModel() {
        executor.execute {
            loadedPath = null
            ensureInitialized()
        }
    }

    fun speakAsync(
        arabicText: String,
        onStarted: () -> Unit,
        onFinished: () -> Unit
    ) {
        executor.execute {
            val ok = ensureInitialized()
            val engine = offlineTts
            if (!ok || engine == null) {
                onFinished()
                return@execute
            }

            onStarted()
            try {
                val prepared = prepareArabicForPiper(arabicText)
                val generated = engine.generate(text = prepared, sid = 0, speed = 1.03f)
                val samples = generated.samples
                val sampleRate = generated.sampleRate
                if (samples.isEmpty() || sampleRate <= 0) {
                    onFinished()
                    return@execute
                }

                val pcm = ShortArray(samples.size) { i ->
                    val clamped = (samples[i] * 32767f).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    clamped.toShort()
                }

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val byteLen = pcm.size * 2
                val bufSize = maxOf(minBuf, byteLen)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                var offset = 0
                val chunkSize = 2048
                while (offset < pcm.size) {
                    val count = minOf(chunkSize, pcm.size - offset)
                    track.write(pcm, offset, count)
                    offset += count
                }
                Thread.sleep(160L)
                track.stop()
                track.release()
            } catch (_: Throwable) {
            } finally {
                onFinished()
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

    fun shutdown() {
        try {
            offlineTts?.release()
            offlineTts = null
            executor.shutdownNow()
        } catch (_: Throwable) {
        }
    }
}
