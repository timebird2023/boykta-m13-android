package com.boykta.assistant

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.boykta.assistant.audio.AudioChimes
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.CustomRuleStore
import com.boykta.assistant.nlu.DarijaIntentRouter
import com.boykta.assistant.service.BoyktaAccessibilityService
import com.boykta.assistant.service.BoyktaBackgroundService
import com.boykta.assistant.service.BoyktaEvents
import com.boykta.assistant.storage.ModelDownloader
import com.boykta.assistant.storage.SdCardModelLocator
import com.boykta.assistant.ui.BoyktaRobotView

class MainActivity : AppCompatActivity() {

    private lateinit var robotView: BoyktaRobotView
    private lateinit var tvStatus: TextView
    private lateinit var tvLiveMic: TextView
    private lateinit var tvTtsWarning: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvDownloadStatus: TextView
    private lateinit var progressDownload: ProgressBar
    private lateinit var etCommandTest: EditText
    private lateinit var etCustomTrigger: EditText
    private lateinit var etCustomReply: EditText
    private lateinit var btnDownloadKareemVoice: Button
    private lateinit var btnDownloadFusha: Button
    private lateinit var btnDownloadDarija: Button
    private lateinit var ttsManager: MaleTtsManager

    private val conversationHistory = StringBuilder()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateStatusUi()
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BoyktaEvents.ACTION_STATE_UPDATE) return
            val type = intent.getStringExtra(BoyktaEvents.EXTRA_TYPE) ?: return
            val text = intent.getStringExtra(BoyktaEvents.EXTRA_TEXT) ?: ""
            val reply = intent.getStringExtra(BoyktaEvents.EXTRA_REPLY) ?: ""

            when (type) {
                BoyktaEvents.TYPE_STATUS -> {
                    if (text.isNotEmpty()) {
                        tvLiveMic.text = "حالة المستشعر: $text"
                    }
                }
                BoyktaEvents.TYPE_PARTIAL -> {
                    if (text.isNotEmpty()) {
                        tvLiveMic.text = "الميكروفون يسمع الآن: «$text»"
                    }
                }
                BoyktaEvents.TYPE_WAKE -> {
                    robotView.setMood(BoyktaRobotView.Mood.LISTENING)
                    tvLiveMic.text = "تم التقاط نداء بويكتا! راني نسمع فيك خويا..."
                    appendLog("النظام", "سمعت كلمة بويكتا — راني واجد لأمرك.")
                }
                BoyktaEvents.TYPE_COMMAND -> {
                    if (text.isNotEmpty()) appendLog("أنت (بالصوت)", text)
                    if (reply.isNotEmpty()) appendLog("بويكتا", reply)
                    robotView.setMood(BoyktaRobotView.Mood.HAPPY)
                    updateStatusUi()
                }
                BoyktaEvents.TYPE_ERROR -> {
                    tvLiveMic.text = "تنبيه المستشعر: $text"
                    robotView.setMood(BoyktaRobotView.Mood.ALERT)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        robotView = findViewById(R.id.robotView)
        tvStatus = findViewById(R.id.tvStatus)
        tvLiveMic = findViewById(R.id.tvLiveMic)
        tvTtsWarning = findViewById(R.id.tvTtsWarning)
        tvLog = findViewById(R.id.tvLog)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
        progressDownload = findViewById(R.id.progressDownload)
        etCommandTest = findViewById(R.id.etCommandTest)
        etCustomTrigger = findViewById(R.id.etCustomTrigger)
        etCustomReply = findViewById(R.id.etCustomReply)
        btnDownloadKareemVoice = findViewById(R.id.btnDownloadKareemVoice)
        btnDownloadFusha = findViewById(R.id.btnDownloadFusha)
        btnDownloadDarija = findViewById(R.id.btnDownloadDarija)

        ttsManager = MaleTtsManager(
            context = this,
            onReadyCallback = { runOnUiThread { updateStatusUi() } },
            onSpeakingChanged = { speaking ->
                runOnUiThread {
                    robotView.setMood(if (speaking) BoyktaRobotView.Mood.SPEAKING else BoyktaRobotView.Mood.IDLE)
                }
            }
        )

        ContextCompat.registerReceiver(
            this,
            serviceStateReceiver,
            IntentFilter(BoyktaEvents.ACTION_STATE_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        robotView.setOnClickListener {
            robotView.setMood(BoyktaRobotView.Mood.HAPPY)
            val greet = "أهلا خويا العزيز! أنا بويكتا، قولي برك واش تحب نديرلك في الهاتف."
            appendLog("بويكتا", greet)
            ttsManager.speak(greet)
        }

        findViewById<Button>(R.id.btnGrantPermissions).setOnClickListener {
            requestRequiredPermissions()
        }

        findViewById<Button>(R.id.btnAccessibilityPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnSystemWritePermission).setOnClickListener {
            if (!Settings.System.canWrite(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            requestIgnoreBatteryOptimization()
        }

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                appendLog("النظام", "إذن النافذة المنبثقة فوق التطبيقات مفعل بالفعل.")
            }
        }

        btnDownloadKareemVoice.setOnClickListener {
            startOfflineModelDownload(SdCardModelLocator.PACK_TTS_KAREEM)
        }

        btnDownloadFusha.setOnClickListener {
            startOfflineModelDownload(SdCardModelLocator.PACK_FUSHA_MGB2)
        }

        btnDownloadDarija.setOnClickListener {
            startOfflineModelDownload(SdCardModelLocator.PACK_DARIJA_TN)
        }

        findViewById<Button>(R.id.btnStartBoykta).setOnClickListener {
            startBoyktaService()
        }

        findViewById<Button>(R.id.btnStopBoykta).setOnClickListener {
            stopService(Intent(this, BoyktaBackgroundService::class.java))
            robotView.setMood(BoyktaRobotView.Mood.IDLE)
            tvLiveMic.text = "حالة المستشعر: متوقف حالياً."
            appendLog("النظام", "تم إيقاف خدمة بويكتا.")
        }

        findViewById<Button>(R.id.btnRunCommand).setOnClickListener {
            val text = etCommandTest.text.toString().trim()
            if (text.isNotEmpty()) {
                executeAndSpeak(text)
                etCommandTest.setText("")
            }
        }

        findViewById<Button>(R.id.chipFreeFire).setOnClickListener {
            executeAndSpeak("بويكتا افتح فري فاير هههه خويا")
        }
        findViewById<Button>(R.id.chipCamera).setOnClickListener {
            executeAndSpeak("بويكتا افتح الكاميرا")
        }
        findViewById<Button>(R.id.chipNotifs).setOnClickListener {
            executeAndSpeak("بويكتا هبط الإشعارات")
        }
        findViewById<Button>(R.id.chipCleanRam).setOnClickListener {
            robotView.setMood(BoyktaRobotView.Mood.CLEANING)
            executeAndSpeak("بويكتا نضف الهاتف والرام")
        }
        findViewById<Button>(R.id.chipBattery).setOnClickListener {
            executeAndSpeak("بويكتا شحال الباتري")
        }
        findViewById<Button>(R.id.chipTestWake).setOnClickListener {
            AudioChimes.playWakeChime(this)
            robotView.setMood(BoyktaRobotView.Mood.LISTENING)
            val wakeIntent = Intent(this, BoyktaBackgroundService::class.java).apply {
                action = BoyktaEvents.ACTION_SIMULATE_WAKE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(wakeIntent)
            } else {
                startService(wakeIntent)
            }
            appendLog("النظام", "تم فتح نافذة الاستماع السريع (OK Google style) لمدة 8 ثوانٍ.")
        }

        findViewById<Button>(R.id.btnSaveCustomRule).setOnClickListener {
            val trig = etCustomTrigger.text.toString().trim()
            val rep = etCustomReply.text.toString().trim()
            if (trig.isNotEmpty() && rep.isNotEmpty()) {
                CustomRuleStore.addRule(this, trig, rep)
                etCustomTrigger.setText("")
                etCustomReply.setText("")
                appendLog("النظام", "تم حفظ الأمر المخصص: $trig")
                ttsManager.speak("تم خويا، حفظتلك هاد الأمر الجديد.")
            }
        }

        updateStatusUi()
    }

    private fun executeAndSpeak(utterance: String) {
        appendLog("أنت", utterance)
        val reply = DarijaIntentRouter.executeCommand(this, utterance)
        appendLog("بويكتا", reply)
        ttsManager.speak(reply)
        updateStatusUi()
    }

    private fun appendLog(sender: String, message: String) {
        conversationHistory.insert(0, "[$sender]: $message\n\n")
        tvLog.text = conversationHistory.toString()
    }

    private fun setDownloadButtonsEnabled(enabled: Boolean) {
        btnDownloadKareemVoice.isEnabled = enabled
        btnDownloadFusha.isEnabled = enabled
        btnDownloadDarija.isEnabled = enabled
    }

    private fun startOfflineModelDownload(packId: String) {
        setDownloadButtonsEnabled(false)
        progressDownload.visibility = View.VISIBLE
        tvDownloadStatus.visibility = View.VISIBLE
        progressDownload.progress = 0

        val packLabel = when (packId) {
            SdCardModelLocator.PACK_TTS_KAREEM -> "صوت بويكتا العربي الذكوري المدمج (Kareem 64MB)"
            SdCardModelLocator.PACK_FUSHA_MGB2 -> "حزمة السمع الفصحى الدقيقة (318MB)"
            else -> "حزمة السمع الدارجة الجزائرية/المغاربية (158MB)"
        }
        tvDownloadStatus.text = "جاري تحميل $packLabel إلى بطاقة التخزين 8GB..."

        ModelDownloader.downloadAndExtractArabicModel(
            context = this,
            packId = packId,
            onProgress = { pct, status ->
                runOnUiThread {
                    progressDownload.progress = pct
                    tvDownloadStatus.text = status
                }
            },
            onComplete = { success, message ->
                runOnUiThread {
                    setDownloadButtonsEnabled(true)
                    tvDownloadStatus.text = message
                    if (success) {
                        if (packId != SdCardModelLocator.PACK_TTS_KAREEM) {
                            SdCardModelLocator.setSelectedPack(this, packId)
                        } else {
                            ttsManager.reloadInAppVoice()
                        }
                        notifyServiceReloadModels()
                        robotView.setMood(BoyktaRobotView.Mood.HAPPY)
                        AudioChimes.playConfirmChime()
                        appendLog("النظام", "تم تثبيت $packLabel بنجاح وتفعيله مباشرة.")
                        ttsManager.speak("أبشر خويا، تم تثبيت $packLabel بنجاح، وراني خدام بالصوت العربي الآن.")
                    }
                    updateStatusUi()
                }
            }
        )
    }

    private fun notifyServiceReloadModels() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val reloadIntent = Intent(this, BoyktaBackgroundService::class.java).apply {
            action = BoyktaEvents.ACTION_RELOAD_MODELS
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(reloadIntent)
            } else {
                startService(reloadIntent)
            }
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        ttsManager.refreshArabicCheck()
        updateStatusUi()
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    private fun requestIgnoreBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun startBoyktaService() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            appendLog("النظام", "يرجى الموافقة على إذن الميكروفون أولاً لتشغيل خدمة بويكتا 24/24.")
            ttsManager.speak("خويا، اقبل إذن الميكروفون أولاً باش نقدر نسمعك 24 على 24.")
            requestRequiredPermissions()
            return
        }

        val hasAnyEarModel =
            SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_FUSHA_MGB2) ||
                SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_DARIJA_TN)

        val intent = Intent(this, BoyktaBackgroundService::class.java).apply {
            action = BoyktaEvents.ACTION_RELOAD_MODELS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        if (!hasAnyEarModel) {
            robotView.setMood(BoyktaRobotView.Mood.ALERT)
            tvLiveMic.text = "تنبيه: الميكروفون ينتظر تحميل حزمة الفصحى (318MB) أو الدارجة (158MB) لسماع كلمة بويكتا!"
            appendLog("النظام", "تم تشغيل الخدمة، لكن يرجى تحميل حزمة الدارجة أو الفصحى ليسمع الميكروفون كلمة بويكتا بدون إنترنت.")
        } else {
            robotView.setMood(BoyktaRobotView.Mood.LISTENING)
            AudioChimes.playWakeChime(this)
            ttsManager.speak("السلام خويا، بويكتا راهو خدام 24 على 24 ساعة. عيطلي غير باسمي بويكتا.")
            appendLog("النظام", "تم تشغيل مستشعر كلمة بويكتا 24/24 — قل بويكتا الآن.")
        }
    }

    private fun updateStatusUi() {
        val micOk = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val contactsOk = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val accOk = BoyktaAccessibilityService.isConnected()
        val writeOk = Settings.System.canWrite(this)
        val overlayOk = Settings.canDrawOverlays(this)
        val fushaReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_FUSHA_MGB2)
        val darijaReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_DARIJA_TN)
        val kareemReady = SdCardModelLocator.isKareemTtsInstalled(this)

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeRamMb = mi.availMem / (1024 * 1024)

        val arabicTtsReady = ttsManager.isArabicReady()
        tvTtsWarning.visibility = if (kareemReady) View.GONE else View.VISIBLE

        btnDownloadKareemVoice.text = if (kareemReady) {
            "✓ صوت بويكتا العربي الذكوري المدمج جاهز (Kareem)"
        } else {
            "تحميل صوت بويكتا العربي الذكوري المدمج (64MB) — يتكلم بلا هاتف"
        }

        tvStatus.text = buildString {
            appendLine("حالة Galaxy M13 (armeabi-v7a) — الشحن: $batteryPct% · الرام الحرة: ${freeRamMb}MB")
            appendLine("• الفم (صوت بويكتا العربي المدمج Kareem): ${if (kareemReady) "مثبت ويعمل أوفلاين" else if (arabicTtsReady) "يعمل بصوت النظام" else "غير محمل (اضغط زر التحميل الأخضر)"}")
            appendLine("• الأذن (الفهم 24/24): الفصحى ${if (fushaReady) "✓" else "✗"} · الدارجة ${if (darijaReady) "✓" else "✗"}")
            appendLine("• المايك: ${if (micOk) "✓" else "✗"} · التحكم الشامل: ${if (accOk) "✓" else "✗"} · النافذة المنبثقة: ${if (overlayOk) "✓" else "✗"}")
            append("• السطوع: ${if (writeOk) "✓" else "✗"} · جهات الاتصال: ${if (contactsOk) "✓" else "✗"}")
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (_: Exception) {
        }
        ttsManager.shutdown()
        super.onDestroy()
    }
}
