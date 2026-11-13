package com.boykta.assistant

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.boykta.assistant.audio.MaleTtsManager
import com.boykta.assistant.nlu.CustomRuleStore
import com.boykta.assistant.nlu.DarijaIntentRouter
import com.boykta.assistant.service.BoyktaAccessibilityService
import com.boykta.assistant.service.BoyktaBackgroundService
import com.boykta.assistant.storage.ModelDownloader
import com.boykta.assistant.storage.SdCardModelLocator
import com.boykta.assistant.ui.BoyktaRobotView

class MainActivity : AppCompatActivity() {

    private lateinit var robotView: BoyktaRobotView
    private lateinit var tvStatus: TextView
    private lateinit var tvTtsWarning: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvDownloadStatus: TextView
    private lateinit var progressDownload: ProgressBar
    private lateinit var etCommandTest: EditText
    private lateinit var etCustomTrigger: EditText
    private lateinit var etCustomReply: EditText
    private lateinit var btnDownloadFusha: Button
    private lateinit var btnDownloadDarija: Button
    private lateinit var ttsManager: MaleTtsManager

    private val conversationHistory = StringBuilder()

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateStatusUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        robotView = findViewById(R.id.robotView)
        tvStatus = findViewById(R.id.tvStatus)
        tvTtsWarning = findViewById(R.id.tvTtsWarning)
        tvLog = findViewById(R.id.tvLog)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
        progressDownload = findViewById(R.id.progressDownload)
        etCommandTest = findViewById(R.id.etCommandTest)
        etCustomTrigger = findViewById(R.id.etCustomTrigger)
        etCustomReply = findViewById(R.id.etCustomReply)
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

        findViewById<Button>(R.id.btnInstallArabicVoice).setOnClickListener {
            try {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
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

    private fun startOfflineModelDownload(packId: String) {
        btnDownloadFusha.isEnabled = false
        btnDownloadDarija.isEnabled = false
        progressDownload.visibility = View.VISIBLE
        tvDownloadStatus.visibility = View.VISIBLE
        progressDownload.progress = 0
        val packLabel = if (packId == SdCardModelLocator.PACK_FUSHA_MGB2) {
            "حزمة العربية الفصحى الدقيقة (318MB)"
        } else {
            "حزمة الدارجة الجزائرية والمغاربية (158MB)"
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
                    btnDownloadFusha.isEnabled = true
                    btnDownloadDarija.isEnabled = true
                    tvDownloadStatus.text = message
                    if (success) {
                        SdCardModelLocator.setSelectedPack(this, packId)
                        robotView.setMood(BoyktaRobotView.Mood.HAPPY)
                        ttsManager.speak("أبشر خويا، تم تثبيت $packLabel على بطاقة الذاكرة بنجاح.")
                    }
                    updateStatusUi()
                }
            }
        )
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
        val intent = Intent(this, BoyktaBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        robotView.setMood(BoyktaRobotView.Mood.LISTENING)
        ttsManager.speak("السلام خويا، بويكتا راهو خدام 24 على 24 ساعة. عيطلي غير باسمي بويكتا.")
        appendLog("النظام", "تم تشغيل مستشعر كلمة بويكتا 24/24.")
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
        val fushaReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_FUSHA_MGB2)
        val darijaReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_DARIJA_TN)

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeRamMb = mi.availMem / (1024 * 1024)

        val arabicTtsReady = ttsManager.isArabicReady()
        tvTtsWarning.visibility = if (arabicTtsReady) View.GONE else View.VISIBLE

        tvStatus.text = buildString {
            appendLine("حالة Galaxy M13 (armeabi-v7a) — الشحن: $batteryPct% · الرام الحرة: ${freeRamMb}MB")
            appendLine("• الصوت العربي الذكوري: ${if (arabicTtsReady) "مثبت وجاهز بالعربية فقط" else "غير محمل بعد (اضغط زر تحميل الصوت العربي)"}")
            appendLine("• حزمة الفصحى (318MB): ${if (fushaReady) "مثبتة على SD" else "غير محملة"} · الدارجة (158MB): ${if (darijaReady) "مثبتة على SD" else "غير محملة"}")
            appendLine("• الميكروفون: ${if (micOk) "مفعل" else "غير مفعل"} · التحكم الشامل Accessibility: ${if (accOk) "مفعل" else "غير مفعل"}")
            append("• السطوع والنظام (Write Settings): ${if (writeOk) "مفعل" else "غير مفعل"} · جهات الاتصال: ${if (contactsOk) "مفعل" else "غير مفعل"}")
        }
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }
}
