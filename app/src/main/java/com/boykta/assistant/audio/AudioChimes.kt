package com.boykta.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object AudioChimes {

    private const val SAMPLE_RATE = 16000

    fun playWakeChime(context: Context) {
        vibratePulse(context, 38L)
        Thread {
            playTones(listOf(587.33 to 95, 880.0 to 135))
        }.start()
    }

    fun playConfirmChime() {
        Thread {
            playTones(listOf(740.0 to 75))
        }.start()
    }

    fun playAttentionChime() {
        Thread {
            playTones(listOf(440.0 to 80, 523.25 to 90))
        }.start()
    }

    private fun vibratePulse(context: Context, ms: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun playTones(notes: List<Pair<Double, Int>>) {
        try {
            val chunks = notes.map { (freq, durMs) ->
                val numSamples = (SAMPLE_RATE * durMs) / 1000
                ShortArray(numSamples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = 0.5 * (1.0 - cos(2.0 * PI * i / numSamples))
                    val sample = sin(2.0 * PI * freq * t) * env * 0.45
                    (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            val totalSamples = chunks.sumOf { it.size }
            val merged = ShortArray(totalSamples)
            var offset = 0
            for (c in chunks) {
                c.copyInto(merged, offset)
                offset += c.size
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(merged.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(merged, 0, merged.size)
            track.play()
            Thread.sleep(((totalSamples * 1000L) / SAMPLE_RATE) + 40L)
            track.stop()
            track.release()
        } catch (_: Throwable) {
        }
    }
}
