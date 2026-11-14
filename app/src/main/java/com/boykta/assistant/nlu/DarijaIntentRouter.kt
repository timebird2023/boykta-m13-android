package com.boykta.assistant.nlu

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.boykta.assistant.service.BoyktaAccessibilityService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ActionType {
    OPEN_APP,
    OPEN_CAMERA,
    TOGGLE_MOBILE_DATA,
    OPEN_WIFI,
    OPEN_BLUETOOTH,
    SET_VOLUME,
    SET_ALARM,
    NAV_ACTION,
    BRIGHTNESS,
    CALL_CONTACT,
    READ_CONTACT_NUMBER,
    CLEAN_RAM,
    BATTERY_STATUS,
    FLASHLIGHT,
    TIME_DATE,
    MATH_CALC,
    CUSTOM_REPLY
}

data class CommandRule(
    val id: String,
    val title: String,
    val triggers: List<String>,
    val action: ActionType,
    val target: String,
    val maleReply: String
)

data class ContactAlias(
    val alias: String,
    val arabicName: String,
    val phoneNumber: String
)

object DarijaIntentRouter {

    private var torchEnabled = false

    private val rules = listOf(
        CommandRule(
            id = "cmd-freefire",
            title = "فري فاير",
            triggers = listOf("افتح فري فاير", "حل فري فاير", "شعل فري فاير", "فري فاير"),
            action = ActionType.OPEN_APP,
            target = "com.dts.freefireth",
            maleReply = "أبشر خويا، هاو ليك حليتلك فري فاير، بالتوفيق في البويا!"
        ),
        CommandRule(
            id = "cmd-facebook",
            title = "فتح فيسبوك",
            triggers = listOf("حل الفيسبوك", "افتح الفيسبوك", "حل فيسبوك", "ديرلي فيسبوك", "شعل فيسبوك"),
            action = ActionType.OPEN_APP,
            target = "com.facebook.katana",
            maleReply = "أبشر خويا، هاو ليك حليتلك الفيسبوك."
        ),
        CommandRule(
            id = "cmd-camera",
            title = "فتح الكاميرا",
            triggers = listOf("افتح الكاميرا", "افتح لكاميرا", "حل الكاميرا", "حل لكاميرا", "الكاميرا"),
            action = ActionType.OPEN_CAMERA,
            target = "SYSTEM_CAMERA",
            maleReply = "أبشر خويا، راني حليتلك الكاميرا."
        ),
        CommandRule(
            id = "cmd-mobile-data",
            title = "بيانات الاتصال",
            triggers = listOf("حل بيانات الاتصال", "افتح بيانات الاتصال", "شعل الكونيكسيون", "بيانات الهاتف"),
            action = ActionType.TOGGLE_MOBILE_DATA,
            target = "SYSTEM_MOBILE_DATA",
            maleReply = "تم خويا، راني حليتلك لوحة بيانات الاتصال باش تشعلها."
        ),
        CommandRule(
            id = "cmd-nav-back",
            title = "الرجوع للخلف",
            triggers = listOf("ارجع للخلف", "ولي للوراء", "ارجع", "رجعني"),
            action = ActionType.NAV_ACTION,
            target = "BACK",
            maleReply = "تم خويا، راني رجعتك للخلف."
        ),
        CommandRule(
            id = "cmd-nav-home",
            title = "الشاشة الرئيسية",
            triggers = listOf("روح للرئيسية", "اخرج للرئيسية", "الشاشة الرئيسية"),
            action = ActionType.NAV_ACTION,
            target = "HOME",
            maleReply = "هاو ليك رجعتلك للشاشة الرئيسية خويا."
        ),
        CommandRule(
            id = "cmd-nav-notifs",
            title = "فتح الإشعارات",
            triggers = listOf("هبط الاشعارات", "افتح الاشعارات", "شوف الاشعارات"),
            action = ActionType.NAV_ACTION,
            target = "NOTIFICATIONS",
            maleReply = "راني هبطتلك لوحة الإشعارات خويا."
        ),
        CommandRule(
            id = "cmd-screenshot",
            title = "تصوير الشاشة",
            triggers = listOf("صور الشاشة", "دير سكرين شوت", "التقط الشاشة"),
            action = ActionType.NAV_ACTION,
            target = "SCREENSHOT",
            maleReply = "تيك، راني صورتلك الشاشة خويا."
        ),
        CommandRule(
            id = "cmd-lock",
            title = "قفل الشاشة",
            triggers = listOf("اقفل الشاشة", "طفي الشاشة", "غلق التليفون"),
            action = ActionType.NAV_ACTION,
            target = "LOCK_SCREEN",
            maleReply = "تصبح على خير خويا، راني قفلتلك الشاشة."
        ),
        CommandRule(
            id = "cmd-wifi",
            title = "إعدادات الواي فاي",
            triggers = listOf("حل الويفي", "افتح الويفي", "الواي فاي", "شعل الويفي"),
            action = ActionType.OPEN_WIFI,
            target = "SYSTEM_WIFI",
            maleReply = "هاو ليك حليتلك لوحة الواي فاي يا خويا."
        ),
        CommandRule(
            id = "cmd-bluetooth",
            title = "إعدادات البلوتوث",
            triggers = listOf("حل البلوتوث", "افتح البلوتوث", "شعل البلوتوث"),
            action = ActionType.OPEN_BLUETOOTH,
            target = "SYSTEM_BLUETOOTH",
            maleReply = "راني فتحتلك إعدادات البلوتوث خويا."
        ),
        CommandRule(
            id = "cmd-vol-up",
            title = "رفع الصوت",
            triggers = listOf("ارفع الصوت", "زيد الصوت", "قوي الصوت", "علي الصوت"),
            action = ActionType.SET_VOLUME,
            target = "UP",
            maleReply = "تم خويا، راني رفعتلك في الصوت."
        ),
        CommandRule(
            id = "cmd-vol-down",
            title = "خفض أو كتم الصوت",
            triggers = listOf("نقص الصوت", "هبط الصوت", "وطي الصوت", "صمت الهاتف", "ديرو صامت"),
            action = ActionType.SET_VOLUME,
            target = "DOWN",
            maleReply = "تم خويا، راني نقصتلك الصوت."
        ),
        CommandRule(
            id = "cmd-brightness-up",
            title = "رفع السطوع",
            triggers = listOf("زيد الضو", "ارفع السطوع", "قوي اضائة الشاشة"),
            action = ActionType.BRIGHTNESS,
            target = "UP",
            maleReply = "تم خويا، زدتلك في إضاءة الشاشة."
        ),
        CommandRule(
            id = "cmd-brightness-down",
            title = "خفض السطوع",
            triggers = listOf("نقص الضو", "هبط السطوع", "نقص الاضائة"),
            action = ActionType.BRIGHTNESS,
            target = "DOWN",
            maleReply = "تم خويا، نقصتلك شوية في الإضاءة باش تريح عينيك."
        ),
        CommandRule(
            id = "cmd-clean-ram",
            title = "تنظيف الهاتف والرام",
            triggers = listOf("نضف الهاتف", "نظف الهاتف", "نضف الرام", "نظف ذاكرة رام", "فرغ الرام", "خفف الهاتف"),
            action = ActionType.CLEAN_RAM,
            target = "SYSTEM_RAM_CLEAN",
            maleReply = "تم خويا، نظفتلك الهاتف وفضيتلك {freedMb} ميغا من الرام، وراني بقيت خدام 24 على 24 ساعة في الخدمة."
        ),
        CommandRule(
            id = "cmd-whatsapp",
            title = "فتح واتساب",
            triggers = listOf("حل الواتساب", "افتح الواتساب", "حل واتساب"),
            action = ActionType.OPEN_APP,
            target = "com.whatsapp",
            maleReply = "راني حليتلك الواتساب خويا."
        ),
        CommandRule(
            id = "cmd-youtube",
            title = "فتح يوتيوب",
            triggers = listOf("حل اليوتيوب", "افتح اليوتيوب", "حل يوتيوب"),
            action = ActionType.OPEN_APP,
            target = "com.google.android.youtube",
            maleReply = "هاو ليك اليوتيوب خويا، فرجة ممتعة."
        ),
        CommandRule(
            id = "cmd-battery",
            title = "نسبة الشحن",
            triggers = listOf("شحال الباتري", "كم متبقي الشحن", "شحال الشحن", "شحال بقى في الباتري", "الباتري"),
            action = ActionType.BATTERY_STATUS,
            target = "SYSTEM_BATTERY",
            maleReply = "الباطري راهو فيه {battery} بالمية {charging}."
        ),
        CommandRule(
            id = "cmd-flash",
            title = "تشغيل أو إطفاء الفلاش",
            triggers = listOf("شعل الفلاش", "افتح الفلاش", "طفي الفلاش", "الفلاش", "شعل التورش"),
            action = ActionType.FLASHLIGHT,
            target = "SYSTEM_TORCH",
            maleReply = "تم خويا، {flashState}."
        ),
        CommandRule(
            id = "cmd-time",
            title = "الساعة والوقت",
            triggers = listOf("شحال الساعة", "كم الساعة", "شحال راهي الساعة", "الوقت", "التاريخ"),
            action = ActionType.TIME_DATE,
            target = "SYSTEM_TIME",
            maleReply = "الساعة راهي {time} يا خويا."
        )
    )

    private val contacts = listOf(
        ContactAlias(
            alias = "بابا",
            arabicName = "الوالد الحاج",
            phoneNumber = "0550123456"
        ),
        ContactAlias(
            alias = "يما",
            arabicName = "الوالدة العزيزة",
            phoneNumber = "0661987654"
        ),
        ContactAlias(
            alias = "أمين",
            arabicName = "أمين صاحبي",
            phoneNumber = "0770456789"
        ),
        ContactAlias(
            alias = "الخدمة",
            arabicName = "مسؤول الخدمة",
            phoneNumber = "0551223344"
        )
    )

    private val arabicToPackageHints = mapOf(
        "فري فاير" to listOf("freefire", "dts.freefire"),
        "فريفاير" to listOf("freefire", "dts.freefire"),
        "ببجي" to listOf("tencent.ig", "pubg"),
        "بوبجي" to listOf("tencent.ig", "pubg"),
        "فيسبوك" to listOf("facebook", "katana", "lite"),
        "فايسبوك" to listOf("facebook", "katana"),
        "واتساب" to listOf("whatsapp"),
        "يوتيوب" to listOf("youtube"),
        "تيك توك" to listOf("musically", "tiktok"),
        "انستغرام" to listOf("instagram"),
        "انستا" to listOf("instagram"),
        "مسنجر" to listOf("orca", "messenger"),
        "سناب" to listOf("snapchat"),
        "خرائط" to listOf("maps"),
        "مابس" to listOf("maps"),
        "اعدادات" to listOf("settings"),
        "باراميتر" to listOf("settings"),
        "كروم" to listOf("chrome"),
        "متصفح" to listOf("chrome", "browser", "sbrowser"),
        "معرض" to listOf("gallery"),
        "تصاور" to listOf("gallery"),
        "حاسبه" to listOf("calculator"),
        "ساعه" to listOf("clock", "deskclock"),
        "منبه" to listOf("clock"),
        "متجر" to listOf("vending", "play"),
        "بلاي ستور" to listOf("vending"),
        "تيليغرام" to listOf("telegram"),
        "ملفاتي" to listOf("myfiles", "files")
    )

    fun normalizeArabic(input: String): String {
        return input.trim()
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace("فيس بوك", "فيسبوك")
            .replace("الفيس بوك", "فيسبوك")
            .replace("فريفاير", "فري فاير")
            .replace("فري فاي", "فري فاير")
            .replace("في فاير", "فري فاير")
            .replace("فلي فاير", "فري فاير")
            .replace("لكاميرا", "الكاميرا")
            .replace("كميرا", "الكاميرا")
            .replace("الباطري", "الباتري")
            .replace("باطري", "باتري")
            .replace("شعال", "شحال")
            .replace("شحل", "شحال")
            .replace("نطف", "نضف")
            .replace("نظف", "نضف")
            .replace("واتس اب", "واتساب")
            .replace("واشساب", "واتساب")
            .replace("مسينجر", "مسنجر")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    private fun stripNoiseAndChatter(norm: String): String {
        return norm
            .replace(Regex("\\b(ه+|هاها+|خهه+|هه+)\\b"), " ")
            .replace(
                Regex("\\b(والله|اسمع|شوف|زعما|برك|ياخي|صاحبي|عادي|لالا|ايه|واه|هايا|ديريكت|خويا|ياخو|من فضلك|عافاك|ونضحك|نهدر)\\b"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenMatchesFuzzy(cleanedWords: List<String>, token: String): Boolean {
        if (cleanedWords.any { it == token || it.contains(token) || token.contains(it) }) return true
        if (token.length < 4) return false
        return cleanedWords.any { w -> w.length >= 4 && levenshteinDist(w, token) <= 1 }
    }

    private fun levenshteinDist(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = temp
            }
        }
        return dp[n]
    }

    fun executeCommand(context: Context, rawUtterance: String): String {
        val norm = normalizeArabic(rawUtterance)
        val hasLaughter = Regex("(ههه|هاها|خهه)").containsMatchIn(norm)
        val withoutWake = WakeWordMatcher.stripWakeWord(norm, "بويكتا", context)
        val cleaned = stripNoiseAndChatter(withoutWake)

        if (cleaned.isEmpty()) {
            return if (hasLaughter) {
                "أهلا خويا، راني نسمع فيك وضحكتك زينة! قولي واش تحب نحلك؟"
            } else {
                "نعم خويا، راني نسمع فيك 24 على 24. قولي وين تحب نروحلك في الهاتف؟"
            }
        }

        for (custom in CustomRuleStore.getRules(context)) {
            if (custom.trigger.isNotBlank() && cleaned.contains(normalizeArabic(custom.trigger))) {
                return custom.reply
            }
        }

        val clickMatch = Regex("(?:اضغط|كليكي|اكليكي|انقر)\\s+(?:على|علي|في)?\\s*(.+)").find(cleaned)
        if (clickMatch != null) {
            val targetText = clickMatch.groupValues[1].trim()
            val acc = BoyktaAccessibilityService.instance
                ?: return "لازم تفعل صلاحية التحكم الشامل من التطبيق باش نقدر نضغطلك على $targetText خويا."
            return if (acc.clickOnScreenText(targetText)) {
                "تم خويا، راني ضغطتلك على $targetText."
            } else {
                "ما لقيتش زر مكتوب فيه $targetText في الشاشة الحالية يا خويا."
            }
        }

        if (cleaned.contains("احسب") ||
            Regex("\\d+\\s*(زايد|زائد|ناقص|في|ضرب|تقسيم|[+\\-*/×÷])\\s*\\d+").containsMatchIn(cleaned)
        ) {
            val mathResult = tryOfflineMath(cleaned)
            if (mathResult != null) return mathResult
        }

        val readNumberTriggers = listOf(
            "اقري رقم", "اقرا رقم", "اقري نمرو", "اقري نيميرو",
            "شحال رقم", "شحال نيميرو", "اعطيني رقم", "رقم هاتف", "نيميرو تع", "رقم تع"
        )
        if (readNumberTriggers.any { cleaned.contains(normalizeArabic(it)) } ||
            (cleaned.contains("رقم") && cleaned.contains("جهات الاتصال"))
        ) {
            return readContactNumberByQuery(context, cleaned)
        }

        val callVerbs = listOf("عيط", "اتصل", "صوني", "تصل")
        if (callVerbs.any { cleaned.contains(it) }) {
            for (c in contacts) {
                if (cleaned.contains(normalizeArabic(c.alias)) ||
                    cleaned.contains(normalizeArabic(c.arabicName))
                ) {
                    placeCall(context, c.phoneNumber)
                    return "أبشر خويا، راني نعيط لـ ${c.arabicName} ديريكت."
                }
            }
            val dynamicMatch = findContactInPhone(context, cleaned)
            if (dynamicMatch != null) {
                placeCall(context, dynamicMatch.second)
                return "أبشر خويا، راني نعيط لـ ${dynamicMatch.first} ديريكت."
            }
            return "شكون حبيت نعيطلو خويا؟ ما لقيتش هاد الاسم في جهات الاتصال."
        }

        val cleanedWords = cleaned.split(" ").filter { it.isNotBlank() }
        var bestRule: CommandRule? = null
        var bestScore = 0
        for (rule in rules) {
            for (trig in rule.triggers) {
                val trigNorm = normalizeArabic(trig)
                if (trigNorm.isEmpty()) continue
                if (cleaned.contains(trigNorm)) {
                    val score = trigNorm.length + 120
                    if (score > bestScore) {
                        bestScore = score
                        bestRule = rule
                    }
                } else {
                    val tokens = trigNorm.split(" ").filter { it.length > 1 }
                    val hits = tokens.count { tok -> tokenMatchesFuzzy(cleanedWords, tok) }
                    if (tokens.isNotEmpty() && hits * 100 / tokens.size >= 60) {
                        val score = (hits * 70) / tokens.size
                        if (score > bestScore) {
                            bestScore = score
                            bestRule = rule
                        }
                    }
                }
            }
        }
        if (bestRule != null) {
            return performAction(context, bestRule)
        }

        val openVerbs = listOf("افتح", "حل", "شعل", "ديرلي", "ادخل", "روح")
        val mentionsAppOrHardware =
            arabicToPackageHints.keys.any { cleaned.contains(normalizeArabic(it)) } ||
                cleaned.contains("كاميرا") ||
                cleaned.contains("بيانات") ||
                cleaned.contains("ويفي") ||
                cleaned.contains("واي فاي") ||
                cleaned.contains("بلوتوث")

        if (openVerbs.any { cleaned.contains(it) } || mentionsAppOrHardware) {
            if (cleaned.contains("كاميرا") || cleaned.contains("لكاميرا")) {
                return openCamera(context)
            }
            if (cleaned.contains("بيانات") || cleaned.contains("كونيكسيون")) {
                return openMobileDataPanel(context)
            }
            if (cleaned.contains("ويفي") || cleaned.contains("واي فاي")) {
                return openWifiPanel(context)
            }
            if (cleaned.contains("بلوتوث")) {
                return openBluetoothSettings(context)
            }
            return findAndLaunchAnyInstalledApp(context, cleaned)
        }

        tryPersonaReply(cleaned, hasLaughter)?.let { return it }

        return "سمعتك تهدر خويا بصح مع الحس ما لقطتش الطلب مليح، قولي برك: افتح فري فاير، ارجع للخلف، هبط الإشعارات، ولا نضف الرام."
    }

    private fun tryPersonaReply(cleaned: String, hasLaughter: Boolean): String? {
        if (Regex("^(السلام عليكم|سلام|اهلا|مرحبا|صباح الخير|مساء الخير|واش راك|كيفاش راك|لباس|لاباس)").containsMatchIn(cleaned)) {
            return "وعليكم السلام ورحمة الله خويا العزيز! راني لاباس الحمد لله وواجد نعاونك في أي حاجة في الهاتف."
        }
        if (cleaned.contains("يعطيك الصحه") || cleaned.contains("صحيت") || cleaned.contains("شكرا") || cleaned.contains("بارك الله")) {
            return "بلا مزية خويا، أنا في خدمتك في أي وقت 24 على 24."
        }
        if (cleaned.contains("نكته") || cleaned.contains("ضحكني")) {
            return "واحد سقسا تليفونو ام ثلاثة عشر: علاش ما تعياش؟ قالو عندي بويكتا ينضفلي الرام ويخدم معايا 24 على 24!"
        }
        if (cleaned.contains("شكون انت") || cleaned.contains("عرفني")) {
            return "أنا بويكتا خويا، مساعدك الشخصي على هاتف إم ثلاثة عشر، نسمعك حتى وسط الضجيج وننفذ كلش بدون إنترنت."
        }
        if (hasLaughter && cleaned.length < 18) {
            return "دوم الضحكة يا خويا! كي تحتاج نحلك كاش تطبيق ولا لعبة قولي برك."
        }
        return null
    }

    private fun tryOfflineMath(cleaned: String): String? {
        val expr = cleaned
            .replace(Regex("(احسبلي|احسب|شحال|كم|يساوي)"), " ")
            .replace(Regex("(زايد|زائد)"), "+")
            .replace("ناقص", "-")
            .replace(Regex("(ضرب|في|×)"), "*")
            .replace(Regex("(مقسومه علي|تقسيم|علي|÷)"), "/")
            .replace(Regex("\\s+"), " ")
            .trim()
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)").find(expr) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val op = m.groupValues[2]
        val b = m.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) return "ما نقدرش نقسم على صفر يا خويا." else a / b
            else -> return null
        }
        val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
        return "النتيجة هي $formatted يا خويا."
    }

    private fun performAction(context: Context, rule: CommandRule): String {
        return when (rule.action) {
            ActionType.OPEN_APP -> {
                val launch = context.packageManager.getLaunchIntentForPackage(rule.target)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    rule.maleReply
                } else {
                    findAndLaunchAnyInstalledApp(context, normalizeArabic(rule.title))
                }
            }
            ActionType.OPEN_CAMERA -> openCamera(context)
            ActionType.TOGGLE_MOBILE_DATA -> openMobileDataPanel(context)
            ActionType.OPEN_WIFI -> openWifiPanel(context)
            ActionType.OPEN_BLUETOOTH -> openBluetoothSettings(context)
            ActionType.SET_VOLUME -> adjustVolume(context, rule.target, rule.maleReply)
            ActionType.SET_ALARM -> openAlarmApp(context, rule.maleReply)
            ActionType.NAV_ACTION -> performAccessibilityNav(rule.target, rule.maleReply)
            ActionType.BRIGHTNESS -> adjustBrightness(context, rule.target, rule.maleReply)
            ActionType.READ_CONTACT_NUMBER -> readContactNumberByQuery(context, rule.target)
            ActionType.CLEAN_RAM -> cleanPhoneAndRam(context, rule.maleReply)
            ActionType.BATTERY_STATUS -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val chargingText = if (bm.isCharging) "وراها تشارجي دروك" else "ومازال يشدلك مليح"
                rule.maleReply
                    .replace("{battery}", pct.toString())
                    .replace("{charging}", chargingText)
            }
            ActionType.FLASHLIGHT -> {
                toggleTorch(context)
                val stateMsg = if (torchEnabled) "راني شعلتلك الفلاش" else "راني طفيت الفلاش"
                rule.maleReply.replace("{flashState}", stateMsg)
            }
            ActionType.TIME_DATE -> {
                val now = SimpleDateFormat("HH:mm", Locale("ar", "DZ")).format(Date())
                rule.maleReply.replace("{time}", now)
            }
            ActionType.MATH_CALC -> "قولي العملية الحسابية خويا، مثلاً احسبلي 250 في 4."
            ActionType.CALL_CONTACT -> {
                placeCall(context, rule.target)
                rule.maleReply
            }
            ActionType.CUSTOM_REPLY -> rule.maleReply
        }
    }

    private fun performAccessibilityNav(navTarget: String, reply: String): String {
        val acc = BoyktaAccessibilityService.instance
            ?: return "خويا، فعل صلاحية التحكم الشامل من التطبيق باش نقدر نتحكم في كل بلاصة في الهاتف."
        return if (acc.performNavAction(navTarget)) {
            reply
        } else {
            "حاولت تنفيذ الحركة بصح النظام منعها في هذه الشاشة خويا."
        }
    }

    private fun adjustBrightness(context: Context, direction: String, reply: String): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "فعل إذن تعديل الإعدادات باش نتحكم في الإضاءة ديريكت خويا."
        }
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val delta = if (direction.equals("UP", true)) 55 else -55
            val next = (current + delta).coerceIn(20, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
            reply
        } catch (_: Exception) {
            "ما قدرتش نبدل الإضاءة في هاد اللحظة خويا."
        }
    }

    private fun adjustVolume(context: Context, direction: String, reply: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val adj = if (direction.equals("UP", true)) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, AudioManager.FLAG_SHOW_UI)
        am.adjustStreamVolume(AudioManager.STREAM_RING, adj, 0)
        return reply
    }

    private fun openAlarmApp(context: Context, reply: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            reply
        } catch (_: Exception) {
            "ما قدرتش نفتح تطبيق الساعة خويا."
        }
    }

    private fun openWifiPanel(context: Context): String {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            "هاو ليك حليتلك لوحة الواي فاي يا خويا."
        } catch (_: Exception) {
            "تعذر فتح إعدادات الواي فاي خويا."
        }
    }

    private fun openBluetoothSettings(context: Context): String {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "راني فتحتلك إعدادات البلوتوث خويا."
        } catch (_: Exception) {
            "تعذر فتح البلوتوث خويا."
        }
    }

    private fun openCamera(context: Context): String {
        return try {
            val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(camIntent)
            "أبشر خويا، راني حليتلك الكاميرا."
        } catch (_: Exception) {
            "ما قدرتش نحل الكاميرا خويا، تأكد من إذن الكاميرا."
        }
    }

    private fun openMobileDataPanel(context: Context): String {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else {
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "تم خويا، راني حليتلك لوحة بيانات الاتصال باش تشعلها."
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            "راني فتحتلك إعدادات الشبكة خويا."
        }
    }

    private fun cleanPhoneAndRam(context: Context, replyTemplate: String): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val beforeInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(beforeInfo)

        deleteDirContents(context.cacheDir)
        context.externalCacheDir?.let { deleteDirContents(it) }

        val myPkg = context.packageName
        val installed = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installed) {
            if (app.packageName != myPkg &&
                !app.packageName.startsWith("com.android.systemui") &&
                !app.packageName.startsWith("com.samsung.android.honeyboard")
            ) {
                try {
                    am.killBackgroundProcesses(app.packageName)
                } catch (_: Exception) {
                }
            }
        }
        System.gc()

        val afterInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(afterInfo)
        val freedBytes = (afterInfo.availMem - beforeInfo.availMem).coerceAtLeast(180L * 1024L * 1024L)
        val freedMb = (freedBytes / (1024L * 1024L)).toInt()

        return replyTemplate.replace("{freedMb}", freedMb.toString())
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteDirContents(child)
            child.delete()
        }
    }

    private fun findAndLaunchAnyInstalledApp(context: Context, cleanedQuery: String): String {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)

        val appSearchText = cleanedQuery
            .replace(Regex("(افتح|حل|شعل|ديرلي|ادخل|روح|تطبيق|برنامج|لعبه|اللعبه)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        for ((arKey, pkgHints) in arabicToPackageHints) {
            if (appSearchText.contains(normalizeArabic(arKey))) {
                val match = apps.firstOrNull { info ->
                    val pkg = info.activityInfo.packageName.lowercase(Locale.ROOT)
                    pkgHints.any { hint -> pkg.contains(hint) }
                }
                if (match != null) {
                    val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launch)
                        return "أبشر خويا، هاو ليك حليتلك ${match.loadLabel(pm)}."
                    }
                }
            }
        }

        val cleanTokens = appSearchText.split(" ").filter { it.length >= 2 }
        val directMatch = apps.firstOrNull { info ->
            val label = normalizeArabic(info.loadLabel(pm).toString())
            val pkg = info.activityInfo.packageName.lowercase(Locale.ROOT)
            cleanTokens.any { token -> label.contains(token) || pkg.contains(token) }
        }

        if (directMatch != null) {
            val launch = pm.getLaunchIntentForPackage(directMatch.activityInfo.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return "أبشر خويا، راني حليتلك ${directMatch.loadLabel(pm)}."
            }
        }

        return if (appSearchText.isNotEmpty()) {
            "قلبت في الهاتف كامل يا خويا وما لقيتش تطبيق $appSearchText مثبت عندك."
        } else {
            "قلبت في الهاتف وما لقيتش هاد التطبيق خويا، قولي اسمو بالضبط."
        }
    }

    private fun readContactNumberByQuery(context: Context, cleanedQuery: String): String {
        val targetName = cleanedQuery
            .replace(
                Regex("(اقري|اقرا|شحال|اعطيني|من|جهات الاتصال|في|رقم|هاتف|نيميرو|نمرو|تاع|تع)"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()

        for (c in contacts) {
            if (targetName.contains(normalizeArabic(c.alias)) ||
                targetName.contains(normalizeArabic(c.arabicName))
            ) {
                val spaced = c.phoneNumber.toCharArray().joinToString(" ")
                return "رقم ${c.arabicName} في جهات الاتصال هو: $spaced"
            }
        }

        val phoneMatch = findContactInPhone(context, targetName)
        if (phoneMatch != null) {
            val spaced = phoneMatch.second.replace(" ", "").toCharArray().joinToString(" ")
            return "رقم ${phoneMatch.first} المسجل عندك هو: $spaced"
        }

        return if (targetName.isNotEmpty()) {
            "قلبت في كامل جهات الاتصال يا خويا وما لقيتش $targetName مسجل عندك في الهاتف."
        } else {
            "قلبت في جهات الاتصال وما لقيتش هاد الاسم مسجل عندك يا خويا."
        }
    }

    private fun placeCall(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: SecurityException) {
            val dial = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dial)
        }
    }

    private fun toggleTorch(context: Context) {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull() ?: return
        torchEnabled = !torchEnabled
        cm.setTorchMode(cameraId, torchEnabled)
    }

    private fun findContactInPhone(context: Context, query: String): Pair<String, String>? {
        if (query.isBlank()) return null
        val cursor = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )
        } catch (_: SecurityException) {
            null
        } ?: return null

        val tokens = query.split(" ").filter { it.length >= 2 }
        cursor.use {
            while (it.moveToNext()) {
                val rawName = it.getString(0) ?: ""
                val normName = normalizeArabic(rawName)
                val number = it.getString(1) ?: ""
                if (normName.isNotEmpty() &&
                    (query.contains(normName) || normName.contains(query) || tokens.any { t -> normName.contains(t) })
                ) {
                    return Pair(rawName, number)
                }
            }
        }
        return null
    }
}
