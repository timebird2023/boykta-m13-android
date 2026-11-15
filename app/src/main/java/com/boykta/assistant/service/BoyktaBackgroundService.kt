package com.boykta.assistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import com.boykta.assistant.audio.AudioChimes
import com.boykta.assistant.audio.ArabicNumberToWords
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.BoyktaReminderStore
import com.boykta.assistant.nlu.BoyktaUserProfileStore
import com.boykta.assistant.nlu.CustomWakeStore
import com.boykta.assistant.nlu.DarijaIntentRouter
import com.boykta.assistant.nlu.WakeWordMatcher
import com.boykta.assistant.receiver.BoyktaReminderReceiver
import com.boykta.assistant.storage.SdCardModelLocator
import com.boykta.assistant.ui.BoyktaRobotView
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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

    private enum class AsrBackend { NONE, MOONSHINE, VOSK }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleStatusText: TextView? = null
    private var quickDrawer: LinearLayout? = null

    private var wakeSheetView: View? = null
    private var wakeSheetRobot: BoyktaRobotView? = null
    private var wakeSheetStatus: TextView? = null
    private var wakeSheetHeard: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var ttsManager: MaleTtsManager
    private var wakeLock: PowerManager.WakeLock? = null

    // Engine 1: Silero VAD + Moonshine Base Arabic (114MB high-accuracy neural ASR)
    private var vadEngine: Vad? = null
    private var moonshineRecognizer: OfflineRecognizer? = null
    private var loadedMoonshinePath: String? = null
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private val isMoonshineLoopRunning = AtomicBoolean(false)
    private val moonshineExecutor = Executors.newSingleThreadExecutor()

    // Engine 2: Legacy Vosk fallback
    private var voskModel: Model? = null
    private var loadedVoskPath: String? = null
    private var speechService: SpeechService? = null

    @Volatile
    private var activeBackend: AsrBackend = AsrBackend.NONE

    @Volatile
    private var isLoadingModel = false

    @Volatile
    private var isSpeakingNow = false

    @Volatile
    private var echoCooldownUntilMs = 0L

    @Volatile
    private var awakeCommandUntilMs = 0L

    @Volatile
    private var isTeachingWakeMode = false

    @Volatile
    private var teachWakeExpiresAtMs = 0L

    private var lastWakeChimeAtMs = 0L
    private var lastLowBatteryAlertAtMs = 0L
    private var lastFullBatteryAlertAtMs = 0L
    private var fullBatteryAlertSentForCharge = false
    private var btIdleSinceMs = 0L
    private var wifiIdleSinceMs = 0L
    private var lastBtGuardAtMs = 0L
    private var lastWifiGuardAtMs = 0L

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
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    fullBatteryAlertSentForCharge = false
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    fullBatteryAlertSentForCharge = false
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val pct = if (level >= 0) (level * 100) / scale else -1
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val chargingNow = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    isCharging = chargingNow
                    if (!chargingNow && pct in 0..89) {
                        fullBatteryAlertSentForCharge = false
                    }
                    checkBatteryVoiceAlerts(pct, chargingNow, status == BatteryManager.BATTERY_STATUS_FULL)
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    isHeadsetConnected = intent.getIntExtra("state", 0) == 1
                }
            }
            evaluateBatterySafeListeningGate()
        }
    }

    private val radioAndReminderTicker = object : Runnable {
        override fun run() {
            try {
                checkDueRemindersBackup()
                checkIdleRadiosGuard()
            } catch (_: Throwable) {
            }
            mainHandler.postDelayed(this, 60_000L)
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        ttsManager = MaleTtsManager(
            context = this,
            onSpeakingChanged = { speaking ->
                isSpeakingNow = speaking
                if (!speaking) {
                    echoCooldownUntilMs = System.currentTimeMillis() + 420L
                    try {
                        vadEngine?.reset()
                    } catch (_: Throwable) {
                    }
                }
                try {
                    if (speaking) {
                        speechService?.setPause(true)
                    } else {
                        mainHandler.postDelayed({
                            if (!isSpeakingNow) {
                                try {
                                    speechService?.setPause(false)
                                } catch (_: Exception) {
                                }
                            }
                        }, 380L)
                    }
                } catch (_: Exception) {
                }
                mainHandler.post {
                    wakeSheetRobot?.setMood(
                        if (speaking) BoyktaRobotView.Mood.SPEAKING
                        else if (System.currentTimeMillis() < awakeCommandUntilMs) BoyktaRobotView.Mood.LISTENING
                        else BoyktaRobotView.Mood.IDLE
                    )
                }
            }
        )
        createNotificationChannel()
        startForeground(
            NOTIF_ID,
            buildNotification("بويكتا يجهز مستشعر الصوت 24/24...")
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
            addAction(Intent.ACTION_BATTERY_CHANGED)
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

        mainHandler.postDelayed(radioAndReminderTicker, 25_000L)
        loadOfflineArabicModel(forceReload = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BoyktaEvents.ACTION_RELOAD_MODELS -> {
                ttsManager.reloadInAppVoice()
                loadOfflineArabicModel(forceReload = true)
            }
            BoyktaEvents.ACTION_SIMULATE_WAKE -> {
                isTeachingWakeMode = false
                val sal = BoyktaUserProfileStore.salutation(this)
                val prompt = BoyktaUserProfileStore.pickVaried(
                    "svc_wake",
                    listOf("تفضل $sal، أنا أسمعك.", "نعم $sal، ما طلبك؟", "حاضر $sal، تفضل.")
                )
                triggerOkGoogleWakeUi(prompt)
            }
            BoyktaEvents.ACTION_TEACH_WAKE_START -> {
                isTeachingWakeMode = true
                teachWakeExpiresAtMs = System.currentTimeMillis() + 9000L
                manualTriggerActive = true
                isSpeakingNow = false
                AudioChimes.playWakeChime(this)
                broadcastState(BoyktaEvents.TYPE_STATUS, "قل كلمة «بويكتا» بصوتك الطبيعي الآن للحفظ...")
                showWakeOverlaySheet("تدريب كلمة بويكتا 🎯", "انطق «بويكتا» الآن...")
                startRecognizerStream()
                mainHandler.removeCallbacks(hideWakeSheetRunnable)
                mainHandler.postDelayed(hideWakeSheetRunnable, 9500L)
            }
            else -> {
                if (activeBackend == AsrBackend.NONE) {
                    loadOfflineArabicModel(forceReload = false)
                } else {
                    evaluateBatterySafeListeningGate()
                }
            }
        }
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

    private fun loadOfflineArabicModel(forceReload: Boolean) {
        if (isLoadingModel) return
        Thread {
            isLoadingModel = true
            try {
                val selectedPack = SdCardModelLocator.getSelectedPack(this)
                val moonshineDir = SdCardModelLocator.findMoonshineModelDir(this)
                val voskDir = SdCardModelLocator.findActiveVoskModelDir(this)

                // Priority 1: Moonshine Base Arabic (114MB neural model) + Silero VAD v5
                val shouldUseMoonshine = moonshineDir != null &&
                    (selectedPack == SdCardModelLocator.PACK_MOONSHINE_AR || voskDir == null)

                if (shouldUseMoonshine && moonshineDir != null) {
                    if (!forceReload && activeBackend == AsrBackend.MOONSHINE &&
                        moonshineRecognizer != null && loadedMoonshinePath == moonshineDir.absolutePath
                    ) {
                        evaluateBatterySafeListeningGate()
                        return@Thread
                    }

                    broadcastState(BoyktaEvents.TYPE_STATUS, "جاري تشغيل محرك السمع العصبي Moonshine...")
                    stopRecognizerStream()
                    try {
                        voskModel?.close()
                    } catch (_: Throwable) {
                    }
                    voskModel = null
                    loadedVoskPath = null

                    try {
                        vadEngine?.release()
                    } catch (_: Throwable) {
                    }
                    try {
                        moonshineRecognizer?.release()
                    } catch (_: Throwable) {
                    }

                    val vadCfg = VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = "silero_vad.onnx",
                            threshold = 0.44f,
                            minSilenceDuration = 0.35f,
                            minSpeechDuration = 0.22f,
                            windowSize = 512,
                            maxSpeechDuration = 8.0f
                        ),
                        sampleRate = 16000,
                        numThreads = 1,
                        provider = "cpu",
                        debug = false
                    )
                    vadEngine = Vad(assetManager = assets, config = vadCfg)

                    val recognizerCfg = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                        modelConfig = OfflineModelConfig(
                            moonshine = OfflineMoonshineModelConfig(
                                encoder = File(moonshineDir, "encoder_model.ort").absolutePath,
                                mergedDecoder = File(moonshineDir, "decoder_model_merged.ort").absolutePath
                            ),
                            tokens = File(moonshineDir, "tokens.txt").absolutePath,
                            numThreads = 2,
                            debug = false,
                            provider = "cpu"
                        )
                    )
                    moonshineRecognizer = OfflineRecognizer(assetManager = null, config = recognizerCfg)
                    loadedMoonshinePath = moonshineDir.absolutePath
                    activeBackend = AsrBackend.MOONSHINE
                    evaluateBatterySafeListeningGate()
                    return@Thread
                }

                // Priority 2: Vosk offline packs if Moonshine is not installed yet
                if (voskDir != null) {
                    if (!forceReload && activeBackend == AsrBackend.VOSK &&
                        voskModel != null && loadedVoskPath == voskDir.absolutePath
                    ) {
                        evaluateBatterySafeListeningGate()
                        return@Thread
                    }

                    broadcastState(BoyktaEvents.TYPE_STATUS, "جاري تحميل حزمة السمع العربية...")
                    stopRecognizerStream()
                    try {
                        voskModel?.close()
                    } catch (_: Throwable) {
                    }
                    voskModel = Model(voskDir.absolutePath)
                    loadedVoskPath = voskDir.absolutePath
                    activeBackend = AsrBackend.VOSK
                    evaluateBatterySafeListeningGate()
                    return@Thread
                }

                activeBackend = AsrBackend.NONE
                broadcastState(
                    BoyktaEvents.TYPE_ERROR,
                    "بانتظار تحميل محرك السمع الذكي Moonshine (114MB) من التطبيق."
                )
                updateNotification("بانتظار تحميل محرك السمع Moonshine (114MB)")
            } catch (e: Throwable) {
                activeBackend = AsrBackend.NONE
                broadcastState(
                    BoyktaEvents.TYPE_ERROR,
                    "تعذر تشغيل محرك السمع: ${e.localizedMessage ?: "يرجى إعادة التثبيت"}"
                )
            } finally {
                isLoadingModel = false
            }
        }.start()
    }

    private fun triggerOkGoogleWakeUi(statusPrompt: String) {
        val now = System.currentTimeMillis()
        awakeCommandUntilMs = now + 10000L
        manualTriggerActive = true
        if (now - lastWakeChimeAtMs > 1100L) {
            lastWakeChimeAtMs = now
            AudioChimes.playWakeChime(this)
        }
        broadcastState(BoyktaEvents.TYPE_WAKE, statusPrompt)
        showWakeOverlaySheet(statusPrompt, "في انتظار أمرك الصوتي...")
        startRecognizerStream()
        mainHandler.removeCallbacks(hideWakeSheetRunnable)
        mainHandler.postDelayed(hideWakeSheetRunnable, 10500L)
    }

    private val hideWakeSheetRunnable = Runnable {
        if (System.currentTimeMillis() >= teachWakeExpiresAtMs) {
            isTeachingWakeMode = false
        }
        if (System.currentTimeMillis() >= awakeCommandUntilMs && !isTeachingWakeMode) {
            manualTriggerActive = false
            hideWakeOverlaySheet()
        }
    }

    private fun showWakeOverlaySheet(status: String, heardText: String) {
        if (!Settings.canDrawOverlays(this)) return
        mainHandler.post {
            try {
                if (windowManager == null) {
                    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                }
                if (wakeSheetView == null) {
                    val view = LayoutInflater.from(this).inflate(R.layout.overlay_wake_sheet, null)
                    wakeSheetRobot = view.findViewById(R.id.wakeSheetRobot)
                    wakeSheetStatus = view.findViewById(R.id.tvWakeSheetStatus)
                    wakeSheetHeard = view.findViewById(R.id.tvWakeSheetHeard)
                    view.findViewById<TextView>(R.id.btnWakeSheetClose).setOnClickListener {
                        awakeCommandUntilMs = 0L
                        manualTriggerActive = false
                        hideWakeOverlaySheet()
                    }
                    val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        y = 32
                    }
                    windowManager?.addView(view, params)
                    wakeSheetView = view
                }
                wakeSheetRobot?.setMood(BoyktaRobotView.Mood.LISTENING)
                wakeSheetStatus?.text = status
                wakeSheetHeard?.text = heardText
            } catch (_: Exception) {
            }
        }
    }

    private fun hideWakeOverlaySheet() {
        mainHandler.post {
            try {
                wakeSheetView?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            } finally {
                wakeSheetView = null
                wakeSheetRobot = null
                wakeSheetStatus = null
                wakeSheetHeard = null
            }
        }
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
        if (activeBackend == AsrBackend.NONE) {
            ttsManager.speak("يرجى فتح التطبيق وتحميل محرك السمع الذكي حتى أتمكن من سماعك.")
            quickDrawer?.visibility =
                if (quickDrawer?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            return
        }
        triggerOkGoogleWakeUi("أنا أسمعك الآن، تفضل بأمرك.")
        ttsManager.speak("أنا أسمعك الآن.")
    }

    private fun evaluateBatterySafeListeningGate() {
        if (activeBackend == AsrBackend.NONE) {
            updateBubbleLabel("بلا حزمة")
            return
        }

        val engineTag = if (activeBackend == AsrBackend.MOONSHINE) "Moonshine الذكي" else "Vosk"

        if (ALWAYS_ON_24_7) {
            startRecognizerStream()
            updateBubbleLabel("24/24")
            updateNotification("بويكتا نشط 24/24 ($engineTag) — قل بويكتا")
            broadcastState(BoyktaEvents.TYPE_STATUS, "نشط على مدار الساعة ($engineTag) — قل «بويكتا» الآن!")
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
            updateNotification("الميكروفون نشط ($engineTag) — قل بويكتا")
            broadcastState(BoyktaEvents.TYPE_STATUS, "الميكروفون نشط ($engineTag) — قل «بويكتا»!")
        } else if (!manualTriggerActive) {
            stopRecognizerStream()
            val label = if (pocketStop) "في الجيب" else "نايم"
            updateBubbleLabel(label)
            broadcastState(BoyktaEvents.TYPE_STATUS, "في وضع توفير البطارية ($label)")
        }
    }

    private fun startRecognizerStream() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            broadcastState(BoyktaEvents.TYPE_ERROR, "يرجى منح إذن الميكروفون أولاً.")
            return
        }

        if (activeBackend == AsrBackend.MOONSHINE) {
            startMoonshineVadLoop()
            return
        }

        val model = voskModel ?: return
        if (speechService != null) {
            try {
                speechService?.setPause(isSpeakingNow)
            } catch (_: Exception) {
            }
            return
        }
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f).also {
                it.startListening(this)
                if (isSpeakingNow) it.setPause(true)
            }
        } catch (e: Exception) {
            stopRecognizerStream()
            broadcastState(
                BoyktaEvents.TYPE_ERROR,
                "تعذر فتح الميكروفون: ${e.localizedMessage ?: "تأكد من إذن التسجيل"}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMoonshineVadLoop() {
        val vad = vadEngine ?: return
        val recognizer = moonshineRecognizer ?: return
        if (isMoonshineLoopRunning.get()) return

        val sampleRate = 16000
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBytes * 2
            )
        } catch (e: Throwable) {
            broadcastState(BoyktaEvents.TYPE_ERROR, "تعذر تشغيل الميكروفون: ${e.localizedMessage ?: ""}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            broadcastState(BoyktaEvents.TYPE_ERROR, "الميكروفون غير متاح حالياً.")
            return
        }

        audioRecord = recorder
        isMoonshineLoopRunning.set(true)
        try {
            vad.reset()
            recorder.startRecording()
        } catch (e: Throwable) {
            isMoonshineLoopRunning.set(false)
            recorder.release()
            audioRecord = null
            return
        }

        audioThread = Thread {
            val shortBuf = ShortArray(512)
            var wasSpeakingDetected = false

            while (isMoonshineLoopRunning.get()) {
                val read = try {
                    recorder.read(shortBuf, 0, shortBuf.size)
                } catch (_: Throwable) {
                    -1
                }
                if (read <= 0) continue

                val now = System.currentTimeMillis()
                if (isSpeakingNow || now < echoCooldownUntilMs) {
                    if (wasSpeakingDetected) {
                        wasSpeakingDetected = false
                    }
                    try {
                        vad.clear()
                    } catch (_: Throwable) {
                    }
                    continue
                }

                val floatSamples = FloatArray(read) { i -> shortBuf[i] / 32768.0f }
                try {
                    vad.acceptWaveform(floatSamples)
                    val detected = vad.isSpeechDetected()
                    if (detected && !wasSpeakingDetected) {
                        wasSpeakingDetected = true
                        broadcastState(BoyktaEvents.TYPE_PARTIAL, "يستمع لصوتك الآن...")
                        mainHandler.post {
                            wakeSheetHeard?.text = "جاري التقاط كلامك..."
                        }
                    } else if (!detected && wasSpeakingDetected) {
                        wasSpeakingDetected = false
                    }

                    while (!vad.empty()) {
                        val segment = vad.front()
                        vad.pop()
                        val segSamples = segment.samples
                        if (segSamples.size >= 2400) {
                            moonshineExecutor.execute {
                                if (isSpeakingNow || System.currentTimeMillis() < echoCooldownUntilMs) {
                                    return@execute
                                }
                                try {
                                    val stream = recognizer.createStream()
                                    stream.acceptWaveform(segSamples, sampleRate)
                                    recognizer.decode(stream)
                                    val text = recognizer.getResult(stream).text.trim()
                                    stream.release()
                                    if (text.isNotEmpty()) {
                                        handleRecognizedUtterance(text)
                                    }
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }.apply {
            name = "boykta-vad-moonshine"
            start()
        }
    }

    private fun stopRecognizerStream() {
        isMoonshineLoopRunning.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        audioThread = null

        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
    }

    override fun onPartialResult(hypothesis: String?) {
        if (isSpeakingNow || System.currentTimeMillis() < echoCooldownUntilMs) return
        val json = hypothesis ?: return
        val partial = try {
            JSONObject(json).optString("partial", "").trim()
        } catch (_: Exception) {
            ""
        }
        if (partial.isEmpty()) return

        broadcastState(BoyktaEvents.TYPE_PARTIAL, partial)
        mainHandler.post {
            wakeSheetHeard?.text = "سمعت: «$partial»"
        }

        if (isTeachingWakeMode && System.currentTimeMillis() <= teachWakeExpiresAtMs) {
            return
        }

        if (System.currentTimeMillis() > awakeCommandUntilMs &&
            WakeWordMatcher.containsWakeWord(partial, WAKE_WORD, this)
        ) {
            val sal = BoyktaUserProfileStore.salutation(this)
            triggerOkGoogleWakeUi("تفضل $sal، أنا أسمعك الآن...")
        }
    }

    override fun onResult(hypothesis: String?) {
        if (isSpeakingNow || System.currentTimeMillis() < echoCooldownUntilMs) return
        val json = hypothesis ?: return
        val text = try {
            JSONObject(json).optString("text", "").trim()
        } catch (_: Exception) {
            ""
        }
        if (text.isEmpty()) return
        handleRecognizedUtterance(text)
    }

    private fun handleRecognizedUtterance(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        broadcastState(BoyktaEvents.TYPE_PARTIAL, text)

        val now = System.currentTimeMillis()
        if (isTeachingWakeMode && now <= teachWakeExpiresAtMs) {
            isTeachingWakeMode = false
            manualTriggerActive = false
            mainHandler.removeCallbacks(hideWakeSheetRunnable)
            val saved = CustomWakeStore.addPhrase(this, text)
            val displaySaved = saved.ifEmpty { text }
            broadcastState(BoyktaEvents.TYPE_LEARNED_WAKE, displaySaved)
            mainHandler.post {
                wakeSheetRobot?.setMood(BoyktaRobotView.Mood.HAPPY)
                wakeSheetStatus?.text = "تم حفظ نطقك!"
                wakeSheetHeard?.text = displaySaved
            }
            mainHandler.postDelayed({ hideWakeOverlaySheet() }, 2000L)
            ttsManager.speak("حسناً، لقد حفظت نطقك لكلمة بويكتا بنجاح.")
            return
        }

        val isAwakeWindow = now <= awakeCommandUntilMs
        val hasWakeWord = WakeWordMatcher.containsWakeWord(text, WAKE_WORD, this)

        if (!hasWakeWord && !isAwakeWindow && !manualTriggerActive) {
            return
        }

        val commandOnly = WakeWordMatcher.stripWakeWord(text, WAKE_WORD, this)

        if (commandOnly.isEmpty() && !isAwakeWindow) {
            val sal = BoyktaUserProfileStore.salutation(this)
            val prompt = BoyktaUserProfileStore.pickVaried(
                "wake_cmd",
                listOf("نعم $sal، تفضل.", "أنا أسمعك $sal.", "حاضر $sal، ما طلبك؟")
            )
            triggerOkGoogleWakeUi(prompt)
            return
        }

        if (commandOnly.isEmpty()) {
            return
        }

        awakeCommandUntilMs = 0L
        manualTriggerActive = false
        mainHandler.removeCallbacks(hideWakeSheetRunnable)

        val reply = DarijaIntentRouter.executeCommand(this, commandOnly)
        broadcastState(BoyktaEvents.TYPE_COMMAND, text, reply)

        mainHandler.post {
            wakeSheetRobot?.setMood(BoyktaRobotView.Mood.HAPPY)
            wakeSheetStatus?.text = "تم التنفيذ بنجاح"
            wakeSheetHeard?.text = reply
        }
        mainHandler.postDelayed({ hideWakeOverlaySheet() }, 2200L)

        ttsManager.speak(reply)
        evaluateBatterySafeListeningGate()
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        stopRecognizerStream()
        if (ALWAYS_ON_24_7 && activeBackend != AsrBackend.NONE) {
            mainHandler.postDelayed({ startRecognizerStream() }, 500L)
        }
    }

    override fun onTimeout() {
        manualTriggerActive = false
        awakeCommandUntilMs = 0L
        hideWakeOverlaySheet()
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

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun broadcastState(type: String, text: String, reply: String = "") {
        val intent = Intent(BoyktaEvents.ACTION_STATE_UPDATE).apply {
            setPackage(packageName)
            putExtra(BoyktaEvents.EXTRA_TYPE, type)
            putExtra(BoyktaEvents.EXTRA_TEXT, text)
            putExtra(BoyktaEvents.EXTRA_REPLY, reply)
        }
        sendBroadcast(intent)
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
        mainHandler.removeCallbacks(radioAndReminderTicker)
        hideWakeOverlaySheet()
        stopRecognizerStream()
        try {
            vadEngine?.release()
        } catch (_: Throwable) {
        }
        try {
            moonshineRecognizer?.release()
        } catch (_: Throwable) {
        }
        try {
            voskModel?.close()
        } catch (_: Throwable) {
        }
        try {
            unregisterReceiver(contextReceiver)
        } catch (_: Exception) {
        }
        sensorManager.unregisterListener(this)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        bubbleView?.let { windowManager?.removeView(it) }
        ttsManager.shutdown()
        super.onDestroy()
    }

    private fun checkDueRemindersBackup() {
        val due = BoyktaReminderStore.popDueReminders(this, System.currentTimeMillis())
        for (item in due) {
            BoyktaReminderReceiver().triggerReminderAlert(
                this,
                item.title,
                (item.id and 0x7FFF).toInt().coerceAtLeast(2100)
            )
        }
    }

    private fun checkBatteryVoiceAlerts(pct: Int, charging: Boolean, isFullStatus: Boolean) {
        if (pct !in 0..100) return
        val now = System.currentTimeMillis()
        if (BoyktaUserProfileStore.isQuietNightHours()) return
        val sal = BoyktaUserProfileStore.salutation(this)

        if (!charging && pct in 1..15 && now - lastLowBatteryAlertAtMs > 25 * 60_000L) {
            lastLowBatteryAlertAtMs = now
            val msg = BoyktaUserProfileStore.pickVaried(
                "batt_low",
                listOf(
                    "تنبيه $sal، البطارية منخفضة عند ${ArabicNumberToWords.intToArabicWords(pct)} بالمئة. ضع الهاتف في الشاحن.",
                    "$sal، الشحن أوشك على النفاد عند $pct%. يُستحسن توصيل الشاحن الآن."
                )
            )
            broadcastState(BoyktaEvents.TYPE_COMMAND, "تنبيه البطارية", msg)
            ttsManager.speak(msg)
            return
        }

        if (charging && (isFullStatus || pct >= 95) && !fullBatteryAlertSentForCharge &&
            now - lastFullBatteryAlertAtMs > 30 * 60_000L
        ) {
            fullBatteryAlertSentForCharge = true
            lastFullBatteryAlertAtMs = now
            val msg = BoyktaUserProfileStore.pickVaried(
                "batt_full",
                listOf(
                    "$sal، اكتمل شحن الهاتف الآن عند $pct%. يمكنك نزع الشاحن.",
                    "بشرى سارة $sal، البطارية وصلت إلى $pct% والشحن جاهز."
                )
            )
            broadcastState(BoyktaEvents.TYPE_COMMAND, "اكتمال الشحن", msg)
            ttsManager.speak(msg)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkIdleRadiosGuard() {
        val now = System.currentTimeMillis()
        if (BoyktaUserProfileStore.isQuietNightHours()) return
        val sal = BoyktaUserProfileStore.salutation(this)
        val autoClose = BoyktaUserProfileStore.getAutoCloseIdleRadios(this)

        // 1. Bluetooth idle check (enabled + no connected audio/GATT device)
        val btManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter
        val btPermOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (btAdapter != null && btPermOk) {
            val btEnabled = try { btAdapter.isEnabled } catch (_: SecurityException) { false }
            if (btEnabled) {
                val hasConnectedBt = try {
                    val a2dp = btAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                    val hs = btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                    val gatt = btManager?.getConnectedDevices(BluetoothProfile.GATT)?.isNotEmpty() == true
                    a2dp || hs || gatt
                } catch (_: SecurityException) {
                    true
                }
                if (!hasConnectedBt) {
                    if (btIdleSinceMs == 0L) btIdleSinceMs = now
                    if (now - btIdleSinceMs >= 4 * 60_000L && now - lastBtGuardAtMs >= 25 * 60_000L) {
                        lastBtGuardAtMs = now
                        var closed = false
                        if (autoClose && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            closed = try { btAdapter.disable() } catch (_: SecurityException) { false }
                        }
                        val msg = if (closed) {
                            "$sal، أغلقتُ البلوتوث تلقائياً لأنه كان مفتوحاً دون اتصال لحفظ البطارية."
                        } else {
                            BoyktaUserProfileStore.pickVaried(
                                "bt_idle_warn",
                                listOf(
                                    "أغلق البلوتوث $sal، إنه مفتوح دون اتصال ويستهلك البطارية.",
                                    "تنبيه $sal، البلوتوث شغال دون أي جهاز متصل، يفضل إغلاقه."
                                )
                            )
                        }
                        broadcastState(BoyktaEvents.TYPE_COMMAND, "حماية البلوتوث", msg)
                        ttsManager.speak(msg)
                    }
                } else {
                    btIdleSinceMs = 0L
                }
            } else {
                btIdleSinceMs = 0L
            }
        }

        // 2. Wi-Fi idle check (enabled + not connected to any Wi-Fi network)
        val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (wifiMgr != null && wifiMgr.isWifiEnabled) {
            val net = cm?.activeNetwork
            val caps = if (net != null && cm != null) cm.getNetworkCapabilities(net) else null
            val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (!wifiConnected) {
                if (wifiIdleSinceMs == 0L) wifiIdleSinceMs = now
                if (now - wifiIdleSinceMs >= 4 * 60_000L && now - lastWifiGuardAtMs >= 25 * 60_000L) {
                    lastWifiGuardAtMs = now
                    var closed = false
                    if (autoClose && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        closed = try { wifiMgr.setWifiEnabled(false) } catch (_: SecurityException) { false }
                    }
                    val msg = if (closed) {
                        "$sal، أغلقتُ الواي فاي تلقائياً لعدم اتصاله بأي شبكة."
                    } else {
                        "أغلق الواي فاي $sal، إنه يعمل دون شبكة ويستهلك البطارية."
                    }
                    broadcastState(BoyktaEvents.TYPE_COMMAND, "حماية الواي فاي", msg)
                    ttsManager.speak(msg)
                }
            } else {
                wifiIdleSinceMs = 0L
            }
        } else {
            wifiIdleSinceMs = 0L
        }
    }
}
