package com.boykta.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

class BoyktaRobotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mood {
        IDLE, LISTENING, SPEAKING, HAPPY, CLEANING, ALERT
    }

    private var currentMood: Mood = Mood.IDLE
    private var phase = 0f
    private var blinkProgress = 0f
    private var lastBlinkTime = System.currentTimeMillis()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 0, 0, 0)
    }
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#475569")
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val eyeSparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECFDF5")
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(42, 255, 255, 255)
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rectHead = RectF()
    private val rectVisor = RectF()
    private val rectEyeLeft = RectF()
    private val rectEyeRight = RectF()
    private val rectEarLeft = RectF()
    private val rectEarRight = RectF()
    private val torsoPath = Path()
    private val leftArmPath = Path()
    private val rightArmPath = Path()
    private val smilePath = Path()

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 2800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { anim ->
            phase = anim.animatedValue as Float
            val now = System.currentTimeMillis()
            val sinceBlink = now - lastBlinkTime
            if (sinceBlink > 3300L) {
                blinkProgress = if (sinceBlink < 3460L) 1f else 0f
                if (sinceBlink >= 3460L) lastBlinkTime = now
            }
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setMood(mood: Mood) {
        currentMood = mood
        invalidate()
    }

    private fun getAccentColor(): Int {
        return when (currentMood) {
            Mood.ALERT -> Color.parseColor("#F59E0B")
            Mood.CLEANING -> Color.parseColor("#38BDF8")
            else -> Color.parseColor("#10B981")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val scale = minOf(w, h) / 220f
        val accent = getAccentColor()
        accentPaint.color = accent
        accentStrokePaint.color = accent

        val floatY = when (currentMood) {
            Mood.HAPPY -> -7f * kotlin.math.abs(sin(phase * 2f))
            else -> -5f * sin(phase)
        }

        canvas.save()
        canvas.translate((w - 220f * scale) / 2f, (h - 220f * scale) / 2f)
        canvas.scale(scale, scale)

        // Subtle glowing aura behind companion
        auraPaint.shader = RadialGradient(
            110f,
            106f,
            98f,
            Color.argb(if (currentMood == Mood.LISTENING) 85 else 48, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(110f, 106f, 96f, auraPaint)

        // Ground shadow
        canvas.drawOval(64f, 197f, 156f, 211f, shadowPaint)

        canvas.translate(0f, floatY)

        // Sculpted floating torso
        bodyPaint.shader = LinearGradient(0f, 138f, 0f, 196f, Color.parseColor("#263348"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP)
        torsoPath.reset()
        torsoPath.moveTo(76f, 146f)
        torsoPath.cubicTo(76f, 138f, 144f, 138f, 144f, 146f)
        torsoPath.lineTo(152f, 178f)
        torsoPath.cubicTo(152f, 190f, 134f, 196f, 110f, 196f)
        torsoPath.cubicTo(86f, 196f, 68f, 190f, 68f, 178f)
        torsoPath.close()
        canvas.drawPath(torsoPath, bodyPaint)
        strokePaint.strokeWidth = 2f
        canvas.drawPath(torsoPath, strokePaint)

        // Chest Core Emblem
        visorPaint.shader = LinearGradient(100f, 160f, 120f, 178f, Color.parseColor("#090E17"), Color.parseColor("#05080F"), Shader.TileMode.CLAMP)
        canvas.drawCircle(110f, 169f, 9f, visorPaint)
        accentStrokePaint.strokeWidth = 2f
        canvas.drawCircle(110f, 169f, 9f, accentStrokePaint)
        canvas.drawCircle(110f, 169f, 3.8f + 1.1f * sin(phase * 2f), accentPaint)

        // Articulated curved floating arms
        shellPaint.shader = LinearGradient(0f, 38f, 0f, 150f, Color.parseColor("#334155"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP)
        val armWave = if (currentMood == Mood.HAPPY) 12f * sin(phase * 3f) else 3f * sin(phase)

        canvas.save()
        canvas.rotate(-armWave, 64f, 154f)
        leftArmPath.reset()
        leftArmPath.moveTo(65f, 152f)
        leftArmPath.cubicTo(50f, 154f, 44f, 170f, 52f, 180f)
        leftArmPath.cubicTo(56f, 184f, 62f, 182f, 64f, 174f)
        leftArmPath.close()
        canvas.drawPath(leftArmPath, shellPaint)
        canvas.drawPath(leftArmPath, strokePaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(armWave, 156f, 154f)
        rightArmPath.reset()
        rightArmPath.moveTo(155f, 152f)
        rightArmPath.cubicTo(170f, 154f, 176f, 170f, 168f, 180f)
        rightArmPath.cubicTo(164f, 184f, 158f, 182f, 156f, 174f)
        rightArmPath.close()
        canvas.drawPath(rightArmPath, shellPaint)
        canvas.drawPath(rightArmPath, strokePaint)
        canvas.restore()

        // Antenna
        strokePaint.strokeWidth = 3.5f
        canvas.drawLine(110f, 38f, 110f, 23f, strokePaint)
        canvas.drawCircle(110f, 19f, 5.8f + 1.2f * sin(phase * 2f), accentPaint)

        // Audio Ear Pods
        rectEarLeft.set(34f, 80f, 46f, 116f)
        rectEarRight.set(174f, 80f, 186f, 116f)
        canvas.drawRoundRect(rectEarLeft, 6f, 6f, bodyPaint)
        canvas.drawRoundRect(rectEarRight, 6f, 6f, bodyPaint)
        accentStrokePaint.strokeWidth = 1.6f
        canvas.drawRoundRect(rectEarLeft, 6f, 6f, accentStrokePaint)
        canvas.drawRoundRect(rectEarRight, 6f, 6f, accentStrokePaint)

        // Sculpted Rounded Head
        rectHead.set(44f, 38f, 176f, 142f)
        canvas.drawRoundRect(rectHead, 44f, 44f, shellPaint)
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(rectHead, 44f, 44f, strokePaint)

        // Top Gloss Highlight
        glossPaint.strokeWidth = 3f
        smilePath.reset()
        smilePath.moveTo(74f, 47f)
        smilePath.cubicTo(96f, 42f, 124f, 42f, 146f, 47f)
        canvas.drawPath(smilePath, glossPaint)

        // Curved OLED Glass Visor
        rectVisor.set(56f, 56f, 164f, 126f)
        canvas.drawRoundRect(rectVisor, 30f, 30f, visorPaint)
        strokePaint.strokeWidth = 2f
        canvas.drawRoundRect(rectVisor, 30f, 30f, strokePaint)

        // Expressive Eyes
        when {
            currentMood == Mood.HAPPY -> {
                accentStrokePaint.strokeWidth = 4.5f
                smilePath.reset()
                smilePath.moveTo(74f, 88f)
                smilePath.quadTo(84f, 74f, 94f, 88f)
                canvas.drawPath(smilePath, accentStrokePaint)

                smilePath.reset()
                smilePath.moveTo(126f, 88f)
                smilePath.quadTo(136f, 74f, 146f, 88f)
                canvas.drawPath(smilePath, accentStrokePaint)
            }
            blinkProgress > 0.5f -> {
                accentStrokePaint.strokeWidth = 4f
                canvas.drawLine(74f, 86f, 94f, 86f, accentStrokePaint)
                canvas.drawLine(126f, 86f, 146f, 86f, accentStrokePaint)
            }
            else -> {
                val eyeHeight = if (currentMood == Mood.LISTENING) 24f else 20f
                val scanOffset = if (currentMood == Mood.CLEANING) 4f * sin(phase * 3f) else 0f
                rectEyeLeft.set(74f + scanOffset, 74f, 94f + scanOffset, 74f + eyeHeight)
                rectEyeRight.set(126f + scanOffset, 74f, 146f + scanOffset, 74f + eyeHeight)
                canvas.drawRoundRect(rectEyeLeft, 10f, 10f, accentPaint)
                canvas.drawRoundRect(rectEyeRight, 10f, 10f, accentPaint)
                canvas.drawCircle(80f + scanOffset, 80f, 3f, eyeSparkPaint)
                canvas.drawCircle(132f + scanOffset, 80f, 3f, eyeSparkPaint)
            }
        }

        // Mouth / Voice Waveform
        when (currentMood) {
            Mood.SPEAKING -> {
                val bars = floatArrayOf(94f, 102f, 110f, 118f, 126f)
                for (i in bars.indices) {
                    val hBar = 6f + 8f * kotlin.math.abs(sin(phase * 4f + i * 0.9f))
                    val bx = bars[i]
                    rectEyeLeft.set(bx - 2f, 111f - hBar / 2f, bx + 2f, 111f + hBar / 2f)
                    canvas.drawRoundRect(rectEyeLeft, 2f, 2f, accentPaint)
                }
            }
            Mood.HAPPY -> {
                accentStrokePaint.strokeWidth = 3.5f
                smilePath.reset()
                smilePath.moveTo(96f, 106f)
                smilePath.quadTo(110f, 118f, 124f, 106f)
                canvas.drawPath(smilePath, accentStrokePaint)
            }
            Mood.LISTENING -> {
                val rx = 7f + 3.5f * kotlin.math.abs(sin(phase * 3f))
                canvas.drawOval(110f - rx, 106f, 110f + rx, 114f, accentPaint)
            }
            else -> {
                accentStrokePaint.strokeWidth = 3f
                smilePath.reset()
                smilePath.moveTo(99f, 109f)
                smilePath.quadTo(110f, 114f, 121f, 109f)
                canvas.drawPath(smilePath, accentStrokePaint)
            }
        }

        canvas.restore()
    }
}
