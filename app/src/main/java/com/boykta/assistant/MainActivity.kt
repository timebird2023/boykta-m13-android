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
import com.boykta.assistant.nlu.BoyktaReminderStore
import com.boykta.assistant.nlu.BoyktaUserProfileStore
import com.boykta.assistant.nlu.CustomRuleStore
import com.boykta.assistant.nlu.CustomWakeStore
import com.boykta.assistant.nlu.DarijaIntentRouter
import com.boykta.assistant.service.BoyktaAccessibilityService
import com.boykta.assistant.service.BoyktaBackgroundService
import com.boykta.assistant.service.BoyktaEvents
import com.boykta.assistant.service.BoyktaNotificationListener
import com.boykta.assistant.storage.ModelDownloader
import com.boykta.assistant.storage.SdCardModelLocator
import com.boykta.assistant.ui.BoyktaRobotView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var robotView: BoyktaRobotView
    private lateinit var tvStatus: TextView
    private lateinit var tvLiveMic: TextView
    private lateinit var tvTtsWarning: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvDownloadStatus: TextView
    private lateinit var tvRemindersList: TextView
    private lateinit var progressDownload: ProgressBar
    private lateinit var etCommandTest: EditText
    private lateinit var etUserName: EditText
    private lateinit var etReminderText: EditText
    private lateinit var etReminderTime: EditText
    private lateinit var etCustomTrigger: EditText
    private lateinit var etCustomReply: EditText
    private lateinit var btnDownloadMiroVoice: Button
    private lateinit var btnToggleTtsSource: Button
    private lateinit var btnDownloadMoonshine: Button
    private lateinit var btnDownloadFusha: Button
    private lateinit var btnDownloadDarija: Button
    private lateinit var btnTeachWakeWord: Button
    private lateinit var btnStoragePermission: Button
    private lateinit var btnNotifListenerPermission: Button
    private lateinit var btnToggleIdleRadioGuard: Button
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
                    tvLiveMic.text = "تم التقاط نداء بويكتا! أنا أسمعك الآن..."
                    appendLog("النظام", "تم سماع كلمة بويكتا — جاهز لتنفيذ أمرك.")
                }
                BoyktaEvents.TYPE_LEARNED_WAKE -> {
                    robotView.setMood(BoyktaRobotView.Mood.HAPPY)
                    tvLiveMic.text = "تم حفظ نطقك لكلمة بويكتا: «$text» ✓"
                    appendLog("النظام", "تم تدريب بويكتا على نطقك الشخصي: «$text».")
                    updateStatusUi()
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
        tvRemindersList = findViewById(R.id.tvRemindersList)
        progressDownload = findViewById(R.id.progressDownload)
        etCommandTest = findViewById(R.id.etCommandTest)
        etUserName = findViewById(R.id.etUserName)
        etReminderText = findViewById(R.id.etReminderText)
        etReminderTime = findViewById(R.id.etReminderTime)
        etCustomTrigger = findViewById(R.id.etCustomTrigger)
        etCustomReply = findViewById(R.id.etCustomReply)
        btnDownloadMiroVoice = findViewById(R.id.btnDownloadMiroVoice)
        btnToggleTtsSource = findViewById(R.id.btnToggleTtsSource)
        btnDownloadMoonshine = findViewById(R.id.btnDownloadMoonshine)
        btnDownloadFusha = findViewById(R.id.btnDownloadFusha)
        btnDownloadDarija = findViewById(R.id.btnDownloadDarija)
        btnTeachWakeWord = findViewById(R.id.btnTeachWakeWord)
        btnStoragePermission = findViewById(R.id.btnStoragePermission)
        btnNotifListenerPermission = findViewById(R.id.btnNotifListenerPermission)
        btnToggleIdleRadioGuard = findViewById(R.id.btnToggleIdleRadioGuard)

        val savedName = BoyktaUserProfileStore.getUserName(this)
        if (savedName.isNotEmpty()) {
            etUserName.setText(savedName)
        }

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
            val sal = BoyktaUserProfileStore.salutation(this)
            val greet = BoyktaUserProfileStore.pickVaried(
                "robot_tap",
                listOf(
                    "أهلاً $sal، أنا مستعد لتنفيذ أي أمر في الهاتف.",
                    "مرحباً $sal، أخبرني بما تحتاج إليه الآن.",
                    "نعم $sal، بويكتا معك في أي لحظة."
                )
            )
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

        btnStoragePermission.setOnClickListener {
            requestPersistentStorageAccess()
        }

        btnNotifListenerPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnToggleIdleRadioGuard.setOnClickListener {
            val next = !BoyktaUserProfileStore.getAutoCloseIdleRadios(this)
            BoyktaUserProfileStore.setAutoCloseIdleRadios(this, next)
            updateStatusUi()
            val sal = BoyktaUserProfileStore.salutation(this)
            val msg = if (next) {
                "حسناً $sal، تم تفعيل حارس البلوتوث والواي فاي الذكي لحفظ البطارية."
            } else {
                "تم إيقاف الإغلاق التلقائي، سأكتفي بتنبيهك صوتياً فقط."
            }
            appendLog("النظام", msg)
            ttsManager.speak(msg)
        }

        findViewById<Button>(R.id.btnSaveUserName).setOnClickListener {
            val name = etUserName.text.toString().trim()
            BoyktaUserProfileStore.setUserName(this, name)
            updateStatusUi()
            val msg = if (name.isNotEmpty()) {
                "تشرفنا يا $name، سأناديك بهذا الاسم دائماً."
            } else {
                "تم مسح الاسم، سأناديك بصديقي."
            }
            appendLog("النظام", msg)
            ttsManager.speak(msg)
        }

        findViewById<Button>(R.id.btnAddReminder).setOnClickListener {
            val desc = etReminderText.text.toString().trim()
            val timeRaw = etReminderTime.text.toString().trim()
            if (desc.isEmpty()) {
                appendLog("النظام", "اكتب وصف التذكير الذي تريد أن أقوله لك.")
                return@setOnClickListener
            }
            val triggerAt = parseReminderTriggerMillis(timeRaw)
            BoyktaReminderStore.addReminder(this, desc, triggerAt)
            etReminderText.setText("")
            etReminderTime.setText("")
            updateStatusUi()
            val formatted = SimpleDateFormat("HH:mm", Locale.US).format(Date(triggerAt))
            val sal = BoyktaUserProfileStore.salutation(this)
            val confirm = "حسناً $sal، سأذكرك في الساعة $formatted بقولي: $desc."
            appendLog("بويكتا", confirm)
            ttsManager.speak(confirm)
        }

        findViewById<Button>(R.id.btnClearReminders).setOnClickListener {
            BoyktaReminderStore.clearAll(this)
            updateStatusUi()
            appendLog("النظام", "تم مسح جميع التذكيرات.")
            ttsManager.speak("تم مسح التذكيرات يا ${BoyktaUserProfileStore.shortNameOrFriend(this)}.")
        }

        btnTeachWakeWord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                appendLog("النظام", "يرجى الموافقة على إذن الميكروفون أولاً.")
                requestRequiredPermissions()
                return@setOnClickListener
            }
            robotView.setMood(BoyktaRobotView.Mood.LISTENING)
            tvLiveMic.text = "🎯 قل كلمة «بويكتا» بصوتك الطبيعي الآن حتى أحفظ نطقك..."
            val teachIntent = Intent(this, BoyktaBackgroundService::class.java).apply {
                action = BoyktaEvents.ACTION_TEACH_WAKE_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(teachIntent)
            } else {
                startService(teachIntent)
            }
        }

        btnDownloadMiroVoice.setOnClickListener {
            startOfflineModelDownload(SdCardModelLocator.PACK_TTS_MIRO)
        }

        btnToggleTtsSource.setOnClickListener {
            val next = !SdCardModelLocator.getPreferSystemTts(this)
            SdCardModelLocator.setPreferSystemTts(this, next)
            updateStatusUi()
            val msg = if (next && ttsManager.isSystemArabicAvailable()) {
                "تم التحويل إلى صوت النظام العربي."
            } else if (next) {
                "صوت النظام العربي غير متوفر على الهاتف، سأستخدم الصوت الفصيح المدمج."
            } else {
                "تم تفعيل صوت بويكتا الفصيح المدمج عالي الدقة."
            }
            appendLog("النظام", msg)
            ttsManager.speak(msg)
        }

        btnDownloadMoonshine.setOnClickListener {
            startOfflineModelDownload(SdCardModelLocator.PACK_MOONSHINE_AR)
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

        findViewById<Button>(R.id.chipScreenshot).setOnClickListener {
            executeAndSpeak("بويكتا دير لقطة شاشة")
        }
        findViewById<Button>(R.id.chipTime).setOnClickListener {
            executeAndSpeak("بويكتا شحال ساعة")
        }
        findViewById<Button>(R.id.chipFreeFire).setOnClickListener {
            executeAndSpeak("بويكتا افتح فري فاير")
        }
        findViewById<Button>(R.id.chipCleanRam).setOnClickListener {
            robotView.setMood(BoyktaRobotView.Mood.CLEANING)
            executeAndSpeak("بويكتا نظف الرام والهاتف")
        }
        findViewById<Button>(R.id.chipBattery).setOnClickListener {
            executeAndSpeak("بويكتا كم نسبة الشحن")
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
            appendLog("النظام", "تم فتح نافذة الاستماع السريع لمدة عشر ثوانٍ.")
        }

        findViewById<Button>(R.id.btnSaveCustomRule).setOnClickListener {
            val trig = etCustomTrigger.text.toString().trim()
            val rep = etCustomReply.text.toString().trim()
            if (trig.isNotEmpty() && rep.isNotEmpty()) {
                CustomRuleStore.addRule(this, trig, rep)
                etCustomTrigger.setText("")
                etCustomReply.setText("")
                appendLog("النظام", "تم حفظ الأمر المخصص: $trig")
                ttsManager.speak("حسناً، تم حفظ الأمر الجديد.")
            }
        }

        migrateModelsIfPermitted()
        updateStatusUi()
    }

    private fun requestPersistentStorageAccess() {
        if (SdCardModelLocator.hasAllFilesPermission(this)) {
            migrateModelsIfPermitted()
            appendLog("النظام", "مجلد الحفظ الدائم (/BoyktaModels) مفعل بالفعل.")
            updateStatusUi()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun migrateModelsIfPermitted() {
        if (!SdCardModelLocator.hasAllFilesPermission(this)) return
        Thread {
            val copied = SdCardModelLocator.migrateAppPrivateModelsToPersistentStorage(this)
            if (copied > 0) {
                runOnUiThread {
                    appendLog("النظام", "تم حفظ $copied حزم في المجلد الدائم لحمايتها عند التحديث.")
                    updateStatusUi()
                }
            }
        }.start()
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
        btnDownloadMiroVoice.isEnabled = enabled
        btnDownloadMoonshine.isEnabled = enabled
        btnDownloadFusha.isEnabled = enabled
        btnDownloadDarija.isEnabled = enabled
    }

    private fun startOfflineModelDownload(packId: String) {
        setDownloadButtonsEnabled(false)
        progressDownload.visibility = View.VISIBLE
        tvDownloadStatus.visibility = View.VISIBLE
        progressDownload.progress = 0

        val packLabel = when (packId) {
            SdCardModelLocator.PACK_TTS_MIRO -> "صوت بويكتا الفصيح عالي الدقة (Miro V2 High)"
            SdCardModelLocator.PACK_TTS_KAREEM -> "صوت بويكتا المدمج (Kareem)"
            SdCardModelLocator.PACK_MOONSHINE_AR -> "محرك السمع العصبي الذكي (Moonshine Arabic)"
            SdCardModelLocator.PACK_FUSHA_MGB2 -> "حزمة الفصحى (Vosk 318MB)"
            else -> "حزمة الدارجة (Vosk 158MB)"
        }
        tvDownloadStatus.text = "جاري تحميل $packLabel..."

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
                        if (packId != SdCardModelLocator.PACK_TTS_MIRO && packId != SdCardModelLocator.PACK_TTS_KAREEM) {
                            SdCardModelLocator.setSelectedPack(this, packId)
                        } else {
                            ttsManager.reloadInAppVoice()
                        }
                        notifyServiceReloadModels()
                        robotView.setMood(BoyktaRobotView.Mood.HAPPY)
                        AudioChimes.playConfirmChime()
                        appendLog("النظام", "تم تثبيت $packLabel بنجاح وتفعيله مباشرة.")
                        ttsManager.speak("حسناً، تم تثبيت الحزمة الجديدة بنجاح وأنا جاهز للعمل.")
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
        migrateModelsIfPermitted()
        updateStatusUi()
    }

    private fun parseReminderTriggerMillis(raw: String): Long {
        val now = System.currentTimeMillis()
        val clean = raw.trim()
        val colonMatch = Regex("(\\d{1,2}):(\\d{2})").find(clean)
        if (colonMatch != null) {
            val hh = colonMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
            val mm = colonMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hh)
                set(Calendar.MINUTE, mm)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
        val mins = Regex("(\\d{1,4})").find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 15L
        return now + mins.coerceAtLeast(1L) * 60_000L
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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
            appendLog("النظام", "يرجى الموافقة على إذن الميكروفون أولاً لتشغيل الخدمة.")
            ttsManager.speak("يرجى تفعيل إذن الميكروفون أولاً حتى أتمكن من سماعك.")
            requestRequiredPermissions()
            return
        }

        val hasAnyEarModel =
            SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_MOONSHINE_AR) ||
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
            tvLiveMic.text = "تنبيه: حمل محرك السمع الذكي Moonshine (114MB) حتى يفهمك بويكتا بدقة عالية!"
            appendLog("النظام", "تم تشغيل الخدمة، يرجى تحميل محرك السمع الذكي Moonshine (114MB).")
        } else {
            robotView.setMood(BoyktaRobotView.Mood.LISTENING)
            AudioChimes.playWakeChime(this)
            ttsManager.speak("أهلاً بك، بويكتا يعمل الآن على مدار الساعة. نادني باسمي بويكتا في أي وقت.")
            appendLog("النظام", "تم تشغيل مستشعر كلمة بويكتا على مدار الساعة.")
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
        val persistentOk = SdCardModelLocator.hasAllFilesPermission(this)

        val moonshineReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_MOONSHINE_AR)
        val fushaReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_FUSHA_MGB2)
        val darijaReady = SdCardModelLocator.isPackInstalled(this, SdCardModelLocator.PACK_DARIJA_TN)
        val miroReady = SdCardModelLocator.isMiroTtsInstalled(this)
        val kareemReady = SdCardModelLocator.isKareemTtsInstalled(this)
        val preferSysTts = SdCardModelLocator.getPreferSystemTts(this)
        val customWakeCount = CustomWakeStore.getPhrases(this).size

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeRamMb = mi.availMem / (1024 * 1024)

        val arabicTtsReady = ttsManager.isArabicReady()
        val notifOk = BoyktaNotificationListener.isPermissionGranted(this)
        val autoRadioGuard = BoyktaUserProfileStore.getAutoCloseIdleRadios(this)
        val activeReminders = BoyktaReminderStore.getActiveReminders(this)
        val userName = BoyktaUserProfileStore.shortNameOrFriend(this)
        tvTtsWarning.visibility = if (miroReady) View.GONE else View.VISIBLE

        btnDownloadMiroVoice.text = if (miroReady) {
            "✓ الصوت الفصيح عالي الدقة جاهز (Miro V2 High)"
        } else if (kareemReady) {
            "⬆️ ترقية الصوت إلى الفصيح عالي الدقة (Miro V2 · 64MB — أوضح بكثير)"
        } else {
            "✨ تحميل الصوت الفصيح عالي الدقة (Miro V2 · 64MB — واضح 100%)"
        }

        btnDownloadMoonshine.text = if (moonshineReady) {
            "✓ محرك السمع العصبي الذكي جاهز (Moonshine Arabic)"
        } else {
            "🧠 تحميل محرك السمع الذكي Moonshine (114MB — يفهمك بلا أخطاء)"
        }

        btnToggleTtsSource.text = if (preferSysTts) {
            "🔊 مصدر النطق: صوت النظام العربي (اضغط للتبديل إلى المدمج)"
        } else {
            "🔊 مصدر النطق: صوت بويكتا المدمج الفصيح (اضغط للتبديل لصوت النظام)"
        }

        btnStoragePermission.text = if (persistentOk) {
            "✓ الحفظ الدائم مفعّل (/BoyktaModels)"
        } else {
            "💾 تفعيل الحفظ الدائم (/BoyktaModels) لحماية الحزم"
        }

        btnNotifListenerPermission.text = if (notifOk) {
            "✓ مراقب الإشعارات غير المقروءة مفعّل"
        } else {
            "📩 تفعيل مراقب الرسائل غير المقروءة"
        }

        btnToggleIdleRadioGuard.text = if (autoRadioGuard) {
            "🔋 حارس البلوتوث والواي فاي: إغلاق وتنبيه تلقائي"
        } else {
            "🔋 حارس البلوتوث والواي فاي: تنبيه صوتي فقط"
        }

        btnTeachWakeWord.text = if (customWakeCount > 0) {
            "🎯 علّمني نطقي لكلمة بويكتا (محفوظ $customWakeCount)"
        } else {
            "🎯 علّمني نطقي لكلمة بويكتا (اضغط وقل «بويكتا» الآن)"
        }

        val voiceStatusStr = when {
            preferSysTts && ttsManager.isSystemArabicAvailable() -> "صوت النظام العربي"
            miroReady -> "Miro V2 الفصيح عالي الدقة (أوفلاين)"
            kareemReady -> "Kareem القديم (ينصح بالترقية إلى Miro V2)"
            arabicTtsReady -> "صوت النظام العربي"
            else -> "غير محمل (حمل Miro V2)"
        }

        tvRemindersList.text = if (activeReminders.isEmpty()) {
            "لا توجد تذكيرات نشطة حالياً."
        } else {
            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            activeReminders.joinToString("\n") { rem ->
                "⏰ ${fmt.format(Date(rem.triggerAtMillis))} — ${rem.title}"
            }
        }

        tvStatus.text = buildString {
            appendLine("مرحباً $userName · Galaxy M13 (armeabi-v7a) — الشحن: $batteryPct% · الرام الحرة: ${freeRamMb}MB")
            appendLine("• النطق (الفصحى): $voiceStatusStr")
            appendLine("• السمع: Moonshine ${if (moonshineReady) "✓" else "✗"} · الفصحى ${if (fushaReady) "✓" else "✗"} · الدارجة ${if (darijaReady) "✓" else "✗"}")
            appendLine("• المايك: ${if (micOk) "✓" else "✗"} · الشامل: ${if (accOk) "✓" else "✗"} · الإشعارات: ${if (notifOk) "✓" else "✗"} · الدائم: ${if (persistentOk) "✓" else "✗"}")
            append("• السطوع: ${if (writeOk) "✓" else "✗"} · الأسماء: ${if (contactsOk) "✓" else "✗"} · تذكيرات: ${activeReminders.size}")
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
