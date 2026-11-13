package com.boykta.assistant.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.boykta.assistant.R
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.DarijaIntentRouter
import com.boykta.assistant.storage.SdCardModelLocator
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

class BoyktaBackgroundService : Service(), SensorEventListener, RecognitionListener {

    companion object {
        const val CHANNEL_ID = "boykta_service_channel"
        const val NOTIF_ID = 1301
        const val WAKE_WORD = "بويكتا"
        const val ALWAYS_ON_24_7 = true
        const val MIN_BATTERY_CUTOFF = 10
        const val LISTEN_SCREEN_ON = true
        const val LISTEN_CHARGING = true
        const val PROXIMITY_GATE = false
        const val LISTEN_HEADSET = true
        const val FLOATING_BUBBLE_ENABLED = false
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleStatusText: TextView? = null
    private var quickDrawer: LinearLayout? = null

    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var ttsManager: MaleTtsManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var voskModel: Model? = null
    private var speechService: SpeechService? = null

    private var isScreenOn = true
    private var isCharging = false
    private var isInPocket = false
    private var isHeadsetConnected = false
    private var manualTriggerActive = false

    private val contextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
                Intent.ACTION_POWER_CONNECTED -> isCharging = true
                Intent.ACTION_POWER_DISCONNECTED -> isCharging = false
                Intent.ACTION_HEADSET_PLUG -> {
                    isHeadsetConnected = intent.getIntExtra("state", 0) == 1
                }
            }
            evaluateBatterySafeListeningGate()
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        ttsManager = MaleTtsManager(this)
        createNotificationChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(
                if (ALWAYS_ON_24_7) "بويكتا شغال 24/24 ينتظر ندائك"
                else "بويكتا جاهز في وضع توفير البطارية"
            )
        )

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive

        if (ALWAYS_ON_24_7) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boykta:always_on_kws").apply {
                acquire()
            }
        }

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        isCharging = bm.isCharging

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (!ALWAYS_ON_24_7 && PROXIMITY_GATE && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        ContextCompat.registerReceiver(
            this,
            contextReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        if (FLOATING_BUBBLE_ENABLED && Settings.canDrawOverlays(this)) {
            attachFloatingBubble()
        }

        loadOfflineArabicModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        evaluateBatterySafeListeningGate()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (ALWAYS_ON_24_7) {
            val restartIntent = Intent(applicationContext, BoyktaBackgroundService::class.java)
            val pending = PendingIntent.getService(
                this,
                1,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pending)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun loadOfflineArabicModel() {
        Thread {
            val dir: File? = SdCardModelLocator.findActiveVoskModelDir(this)
            if (dir != null) {
                try {
                    voskModel = Model(dir.absolutePath)
                    evaluateBatterySafeListeningGate()
                } catch (_: Exception) {
                    updateBubbleLabel("بويكتا")
                }
            }
        }.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleView = LayoutInflater.from(this).inflate(R.layout.overlay_floating_bubble, null)
        bubbleStatusText = bubbleView?.findViewById(R.id.tvBubbleLabel)
        quickDrawer = bubbleView?.findViewById(R.id.llQuickDrawer)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }

        bubbleView?.findViewById<TextView>(R.id.btnDrawerCleanRam)?.setOnClickListener {
            val reply = DarijaIntentRouter.executeCommand(this, "نضف الهاتف والرام")
            ttsManager.speak(reply)
            quickDrawer?.visibility = View.GONE
        }

        bubbleView?.findViewById<TextView>(R.id.btnDrawerFlash)?.setOnClickListener {
            val reply = DarijaIntentRouter.executeCommand(this, "شعل الفلاش")
            ttsManager.speak(reply)
            quickDrawer?.visibility = View.GONE
        }

        bubbleView?.findViewById<TextView>(R.id.btnDrawerCamera)?.setOnClickListener {
            val reply = DarijaIntentRouter.executeCommand(this, "افتح الكاميرا")
            ttsManager.speak(reply)
            quickDrawer?.visibility = View.GONE
        }

        val micBtn = bubbleView?.findViewById<ImageButton>(R.id.btnFloatingMic)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L

        micBtn?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - initialTouchX) +
                        kotlin.math.abs(event.rawY - initialTouchY)
                    val heldMs = System.currentTimeMillis() - downTime
                    if (moved < 15f) {
                        if (heldMs > 450L) {
                            quickDrawer?.visibility =
                                if (quickDrawer?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        } else {
                            onBubbleTapped()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(bubbleView, params)
    }

    private fun onBubbleTapped() {
        manualTriggerActive = true
        if (voskModel == null) {
            ttsManager.speak("أهلا خويا، افتح التطبيق وحمل حزمة الفصحى أو الدارجة على بطاقة الذاكرة باش نسمعك بلا انترنت.")
            quickDrawer?.visibility =
                if (quickDrawer?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            return
        }
        ttsManager.speak("راني نسمع فيك خويا")
        startRecognizerStream()
    }

    private fun evaluateBatterySafeListeningGate() {
        if (ALWAYS_ON_24_7) {
            startRecognizerStream()
            updateBubbleLabel("24/24")
            return
        }

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val lowBatteryStop = batteryPct in 1..MIN_BATTERY_CUTOFF && !isCharging
        val pocketStop = PROXIMITY_GATE && isInPocket && !isHeadsetConnected
        val allowedContext =
            (LISTEN_SCREEN_ON && isScreenOn) ||
                (LISTEN_CHARGING && isCharging) ||
                (LISTEN_HEADSET && isHeadsetConnected)

        if (!lowBatteryStop && !pocketStop && allowedContext) {
            startRecognizerStream()
            updateBubbleLabel("بويكتا")
        } else if (!manualTriggerActive) {
            stopRecognizerStream()
            updateBubbleLabel(if (pocketStop) "في الجيب" else "نايم")
        }
    }

    private fun startRecognizerStream() {
        val model = voskModel ?: return
        if (speechService != null) return
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f).also {
                it.startListening(this)
            }
        } catch (_: Exception) {
            stopRecognizerStream()
        }
    }

    private fun stopRecognizerStream() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    override fun onPartialResult(hypothesis: String?) {}

    override fun onResult(hypothesis: String?) {
        val json = hypothesis ?: return
        val text = try {
            JSONObject(json).optString("text", "").trim()
        } catch (_: Exception) {
            ""
        }
        if (text.isEmpty()) return

        val containsWake = text.contains(WAKE_WORD) ||
            text.contains("بوكتا") ||
            text.contains("بويكتة") ||
            manualTriggerActive

        if (containsWake) {
            manualTriggerActive = false
            val reply = DarijaIntentRouter.executeCommand(this, text)
            ttsManager.speak(reply)
            evaluateBatterySafeListeningGate()
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        stopRecognizerStream()
        if (ALWAYS_ON_24_7) {
            startRecognizerStream()
        }
    }

    override fun onTimeout() {
        manualTriggerActive = false
        evaluateBatterySafeListeningGate()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val maxRange = event.sensor.maximumRange
            isInPocket = event.values[0] < maxRange && event.values[0] < 3.0f
            evaluateBatterySafeListeningGate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateBubbleLabel(label: String) {
        bubbleStatusText?.post { bubbleStatusText?.text = label }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Boykta Voice Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مساعد بويكتا (M13)")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_boykta_mic)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecognizerStream()
        voskModel?.close()
        unregisterReceiver(contextReceiver)
        sensorManager.unregisterListener(this)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        bubbleView?.let { windowManager?.removeView(it) }
        ttsManager.shutdown()
        super.onDestroy()
    }
}
