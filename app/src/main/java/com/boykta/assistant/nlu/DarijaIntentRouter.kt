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
import com.boykta.assistant.audio.ArabicNumberToWords
import com.boykta.assistant.service.BoyktaAccessibilityService
import java.io.File
import java.util.Calendar
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
            maleReply = "حسناً، لقد فتحت لك لعبة فري فاير. أتمنى لك وقتاً ممتعاً."
        ),
        CommandRule(
            id = "cmd-facebook",
            title = "فتح فيسبوك",
            triggers = listOf("حل الفيسبوك", "افتح الفيسبوك", "حل فيسبوك", "افتح فيسبوك", "ديرلي فيسبوك", "شعل فيسبوك"),
            action = ActionType.OPEN_APP,
            target = "com.facebook.katana",
            maleReply = "حسناً، لقد فتحت لك تطبيق فيسبوك."
        ),
        CommandRule(
            id = "cmd-camera",
            title = "فتح الكاميرا",
            triggers = listOf("افتح الكاميرا", "افتح لكاميرا", "حل الكاميرا", "حل لكاميرا", "الكاميرا", "كاميرا"),
            action = ActionType.OPEN_CAMERA,
            target = "SYSTEM_CAMERA",
            maleReply = "حسناً، لقد فتحت لك الكاميرا."
        ),
        CommandRule(
            id = "cmd-mobile-data",
            title = "بيانات الاتصال",
            triggers = listOf("حل بيانات الاتصال", "افتح بيانات الاتصال", "شعل الكونيكسيون", "بيانات الهاتف", "بيانات الاتصال"),
            action = ActionType.TOGGLE_MOBILE_DATA,
            target = "SYSTEM_MOBILE_DATA",
            maleReply = "لقد فتحت لك لوحة بيانات الاتصال لتشغيلها."
        ),
        CommandRule(
            id = "cmd-nav-back",
            title = "الرجوع للخلف",
            triggers = listOf("ارجع للخلف", "ولي للوراء", "ارجع", "رجعني", "الرجوع"),
            action = ActionType.NAV_ACTION,
            target = "BACK",
            maleReply = "حسناً، عدت بك إلى الشاشة السابقة."
        ),
        CommandRule(
            id = "cmd-nav-home",
            title = "الشاشة الرئيسية",
            triggers = listOf("روح للرئيسية", "اخرج للرئيسية", "الشاشة الرئيسية", "الرئيسية"),
            action = ActionType.NAV_ACTION,
            target = "HOME",
            maleReply = "حسناً، انتقلت بك إلى الشاشة الرئيسية."
        ),
        CommandRule(
            id = "cmd-nav-notifs",
            title = "فتح الإشعارات",
            triggers = listOf("هبط الاشعارات", "افتح الاشعارات", "شوف الاشعارات", "الاشعارات"),
            action = ActionType.NAV_ACTION,
            target = "NOTIFICATIONS",
            maleReply = "لقد فتحت لك لوحة الإشعارات."
        ),
        CommandRule(
            id = "cmd-screenshot",
            title = "تصوير الشاشة",
            triggers = listOf("صور الشاشة", "دير لقطة شاشة", "لقطة شاشة", "لقطة للشاشة", "دير سكرين شوت", "التقط الشاشة", "كابتور"),
            action = ActionType.NAV_ACTION,
            target = "SCREENSHOT",
            maleReply = "حسناً، لقد التقطت صورة للشاشة."
        ),
        CommandRule(
            id = "cmd-lock",
            title = "قفل الشاشة",
            triggers = listOf("اقفل الشاشة", "طفي الشاشة", "غلق التليفون", "قفل الهاتف"),
            action = ActionType.NAV_ACTION,
            target = "LOCK_SCREEN",
            maleReply = "حسناً، أغلقت شاشة الهاتف."
        ),
        CommandRule(
            id = "cmd-wifi",
            title = "إعدادات الواي فاي",
            triggers = listOf("حل الويفي", "افتح الويفي", "الواي فاي", "شعل الويفي", "ويفي"),
            action = ActionType.OPEN_WIFI,
            target = "SYSTEM_WIFI",
            maleReply = "لقد فتحت لك إعدادات الشبكة اللاسلكية."
        ),
        CommandRule(
            id = "cmd-bluetooth",
            title = "إعدادات البلوتوث",
            triggers = listOf("حل البلوتوث", "افتح البلوتوث", "شعل البلوتوث", "بلوتوث"),
            action = ActionType.OPEN_BLUETOOTH,
            target = "SYSTEM_BLUETOOTH",
            maleReply = "لقد فتحت لك إعدادات البلوتوث."
        ),
        CommandRule(
            id = "cmd-vol-up",
            title = "رفع الصوت",
            triggers = listOf("ارفع الصوت", "زيد الصوت", "قوي الصوت", "علي الصوت"),
            action = ActionType.SET_VOLUME,
            target = "UP",
            maleReply = "تم رفع مستوى الصوت."
        ),
        CommandRule(
            id = "cmd-vol-down",
            title = "خفض أو كتم الصوت",
            triggers = listOf("نقص الصوت", "هبط الصوت", "وطي الصوت", "صمت الهاتف", "ديرو صامت"),
            action = ActionType.SET_VOLUME,
            target = "DOWN",
            maleReply = "تم خفض مستوى الصوت."
        ),
        CommandRule(
            id = "cmd-brightness-up",
            title = "رفع السطوع",
            triggers = listOf("زيد الضو", "ارفع السطوع", "قوي اضائة الشاشة", "زيد السطوع"),
            action = ActionType.BRIGHTNESS,
            target = "UP",
            maleReply = "تمت زيادة سطوع الشاشة."
        ),
        CommandRule(
            id = "cmd-brightness-down",
            title = "خفض السطوع",
            triggers = listOf("نقص الضو", "هبط السطوع", "نقص الاضائة", "نقص السطوع"),
            action = ActionType.BRIGHTNESS,
            target = "DOWN",
            maleReply = "تم خفض سطوع الشاشة."
        ),
        CommandRule(
            id = "cmd-clean-ram",
            title = "تنظيف الهاتف والرام",
            triggers = listOf("نضف الهاتف", "نظف الهاتف", "نضف الرام", "نظف ذاكرة رام", "فرغ الرام", "خفف الهاتف"),
            action = ActionType.CLEAN_RAM,
            target = "SYSTEM_RAM_CLEAN",
            maleReply = "تم تنظيف الهاتف وتحرير {freedMb} ميغابايت من الذاكرة، وأنا مستمر في العمل على مدار الساعة."
        ),
        CommandRule(
            id = "cmd-whatsapp",
            title = "فتح واتساب",
            triggers = listOf("حل الواتساب", "افتح الواتساب", "حل واتساب", "افتح واتساب"),
            action = ActionType.OPEN_APP,
            target = "com.whatsapp",
            maleReply = "حسناً، لقد فتحت لك تطبيق واتساب."
        ),
        CommandRule(
            id = "cmd-youtube",
            title = "فتح يوتيوب",
            triggers = listOf("حل اليوتيوب", "افتح اليوتيوب", "حل يوتيوب", "افتح يوتيوب"),
            action = ActionType.OPEN_APP,
            target = "com.google.android.youtube",
            maleReply = "حسناً، لقد فتحت لك تطبيق يوتيوب."
        ),
        CommandRule(
            id = "cmd-battery",
            title = "نسبة الشحن",
            triggers = listOf("شحال الباتري", "شحال باتري", "كم متبقي الشحن", "شحال الشحن", "شحال بقى في الباتري", "البطارية", "الشحن"),
            action = ActionType.BATTERY_STATUS,
            target = "SYSTEM_BATTERY",
            maleReply = "نسبة شحن البطارية الآن {battery} بالمئة، {charging}."
        ),
        CommandRule(
            id = "cmd-flash",
            title = "تشغيل أو إطفاء الفلاش",
            triggers = listOf("شعل الفلاش", "افتح الفلاش", "طفي الفلاش", "الفلاش", "شعل التورش"),
            action = ActionType.FLASHLIGHT,
            target = "SYSTEM_TORCH",
            maleReply = "حسناً، {flashState}."
        ),
        CommandRule(
            id = "cmd-time",
            title = "الساعة والوقت",
            triggers = listOf("شحال الساعة", "شحال ساعة", "كم الساعة", "قداش الساعة", "شحال راهي الساعة", "الساعة", "الوقت", "التاريخ"),
            action = ActionType.TIME_DATE,
            target = "SYSTEM_TIME",
            maleReply = "الساعة الآن {time}."
        )
    )

    private val contacts = listOf(
        ContactAlias(
            alias = "بابا",
            arabicName = "الوالد",
            phoneNumber = "0550123456"
        ),
        ContactAlias(
            alias = "يما",
            arabicName = "الوالدة",
            phoneNumber = "0661987654"
        ),
        ContactAlias(
            alias = "أمين",
            arabicName = "أمين",
            phoneNumber = "0770456789"
        ),
        ContactAlias(
            alias = "الخدمة",
            arabicName = "العمل",
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
        "ملفاتي" to listOf("myfiles", "files"),
        "سبوتيفاي" to listOf("spotify"),
        "تويتر" to listOf("twitter", "x."),
        "كاب كات" to listOf("capcut", "lemon"),
        "انشوت" to listOf("inshot")
    )

    private val arabicDays = arrayOf(
        "", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"
    )

    private val arabicMonths = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    fun normalizeArabic(input: String): String {
        return input.trim()
            .replace("ـ", "")
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace('ئ', 'ي')
            .replace('ؤ', 'و')
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
            .replace("البطاريه", "بطاريه")
            .replace("شعال", "شحال")
            .replace("شحل", "شحال")
            .replace("نطف", "نظف")
            .replace("نضف", "نظف")
            .replace("واتس اب", "واتساب")
            .replace("واشساب", "واتساب")
            .replace("مسينجر", "مسنجر")
            .replace("كابتير", "كابتور")
            .replace("سكرين شوت", "سكرين")
            .replace("سكرينشوت", "سكرين")
            .replace(Regex("[؟?!.,،:;«»\"'()\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
    }

    /**
     * Strips Arabic definite article "ال" and common proclitics ("وال", "بال", "لل", "فال", "كال", "ل", "و")
     * so "ساعة" matches "الساعة" and "شاشة" matches "بالشاشة".
     */
    fun stemArabicToken(tok: String): String {
        var w = tok.trim()
        if (w.length <= 2) return w
        val prefixes = listOf("وال", "بال", "كال", "فال", "ولل", "لل", "ال")
        for (p in prefixes) {
            if (w.startsWith(p) && w.length - p.length >= 2) {
                w = w.substring(p.length)
                break
            }
        }
        if (w.length >= 4 && (w[0] == 'و' || w[0] == 'ف' || w[0] == 'ب' || w[0] == 'ل')) {
            val rest = w.substring(1)
            if (rest.startsWith("ال") && rest.length >= 4) {
                w = rest.substring(2)
            }
        }
        return w
    }

    private fun stripNoiseAndChatter(norm: String): String {
        return norm
            .replace(Regex("\\b(ه+|هاها+|خهه+|هه+)\\b"), " ")
            .replace(
                Regex("\\b(والله|اسمع|شوف|زعما|برك|ياخي|صاحبي|عادي|لالا|ايه|واه|هايا|ديريكت|خويا|ياخو|من فضلك|لو سمحت|عافاك|ونضحك|نهدر|يعيشك)\\b"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenMatchesFuzzy(cleanedWords: List<String>, stemmedWords: List<String>, token: String): Boolean {
        val tokStem = stemArabicToken(token)
        if (cleanedWords.any { it == token || it.contains(token) || token.contains(it) }) return true
        if (stemmedWords.any { it == tokStem || it.contains(tokStem) || tokStem.contains(it) }) return true
        if (tokStem.length < 3) return false
        return stemmedWords.any { w -> w.length >= 3 && levenshteinDist(w, tokStem) <= 1 }
    }

    private fun hasAnyConcept(stemmedWords: List<String>, cleaned: String, vararg roots: String): Boolean {
        return roots.any { r ->
            val rNorm = stemArabicToken(normalizeArabic(r))
            cleaned.contains(rNorm) || stemmedWords.any { w ->
                w == rNorm || w.contains(rNorm) || (w.length >= 4 && rNorm.length >= 4 && levenshteinDist(w, rNorm) <= 1)
            }
        }
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
                "أهلاً بك يا صديقي، أنا أسمعك بوضوح. أخبرني بما تريد أن أنفذه لك في الهاتف."
            } else {
                "نعم، أنا معك وأسمعك على مدار الساعة. تفضل بأمرك."
            }
        }

        // 1. Custom user-saved rules
        for (custom in CustomRuleStore.getRules(context)) {
            if (custom.trigger.isNotBlank() && cleaned.contains(normalizeArabic(custom.trigger))) {
                return custom.reply
            }
        }

        // 2. UI click by visible text via Accessibility
        val clickMatch = Regex("(?:اضغط|كليكي|اكليكي|انقر)\\s+(?:على|علي|في)?\\s*(.+)").find(cleaned)
        if (clickMatch != null) {
            val targetText = clickMatch.groupValues[1].trim()
            val acc = BoyktaAccessibilityService.instance
                ?: return "يرجى تفعيل صلاحية التحكم الشامل من التطبيق حتى أتمكن من الضغط على $targetText."
            return if (acc.clickOnScreenText(targetText)) {
                "تم الأمر، لقد ضغطت لك على $targetText."
            } else {
                "عذراً، لم أجد زراً مكتوباً عليه $targetText في الشاشة الحالية."
            }
        }

        // 3. Offline calculator
        if (cleaned.contains("احسب") ||
            Regex("\\d+\\s*(زايد|زائد|ناقص|في|ضرب|تقسيم|[+\\-*/×÷])\\s*\\d+").containsMatchIn(cleaned)
        ) {
            val mathResult = tryOfflineMath(cleaned)
            if (mathResult != null) return mathResult
        }

        // 4. Read contact phone number aloud
        val readNumberTriggers = listOf(
            "اقري رقم", "اقرا رقم", "اقري نمرو", "اقري نيميرو",
            "شحال رقم", "شحال نيميرو", "اعطيني رقم", "ما رقم", "كم رقم",
            "رقم هاتف", "نيميرو تع", "رقم تع", "رقم تاع"
        )
        if (readNumberTriggers.any { cleaned.contains(normalizeArabic(it)) } ||
            (cleaned.contains("رقم") && cleaned.contains("جهات الاتصال"))
        ) {
            return readContactNumberByQuery(context, cleaned)
        }

        // 5. Call a contact directly
        val callVerbs = listOf("عيط", "اتصل", "صوني", "تصل", "كلم")
        if (callVerbs.any { cleaned.contains(it) }) {
            for (c in contacts) {
                if (cleaned.contains(normalizeArabic(c.alias)) ||
                    cleaned.contains(normalizeArabic(c.arabicName))
                ) {
                    placeCall(context, c.phoneNumber)
                    return "حسناً، أنا أتصل الآن بـ ${c.arabicName}."
                }
            }
            val dynamicMatch = findContactInPhone(context, cleaned)
            if (dynamicMatch != null) {
                placeCall(context, dynamicMatch.second)
                return "حسناً، أنا أتصل الآن بـ ${dynamicMatch.first}."
            }
            return "عذراً، لم أجد هذا الاسم في جهات الاتصال."
        }

        val cleanedWords = cleaned.split(" ").filter { it.isNotBlank() }
        val stemmedWords = cleanedWords.map { stemArabicToken(it) }

        // 6. Semantic Concept Engine (understands meaning without needing exact wording)
        trySemanticIntent(context, cleaned, cleanedWords, stemmedWords)?.let { return it }

        // 7. Scored rule matcher (uses stemmed tokens too)
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
                    val hits = tokens.count { tok -> tokenMatchesFuzzy(cleanedWords, stemmedWords, tok) }
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

        // 8. Dynamic app launcher (Arabic hints + phonetic Arabic-to-Latin transliteration)
        val openVerbs = listOf("افتح", "حل", "شعل", "ديرلي", "دير", "ادخل", "روح", "شغل")
        val mentionsAppOrHardware =
            arabicToPackageHints.keys.any { cleaned.contains(normalizeArabic(it)) } ||
                hasAnyConcept(stemmedWords, cleaned, "كاميرا", "بيانات", "ويفي", "واي فاي", "بلوتوث")

        if (openVerbs.any { cleaned.contains(it) } || mentionsAppOrHardware) {
            if (hasAnyConcept(stemmedWords, cleaned, "كاميرا", "كميرا", "تصوير")) {
                return openCamera(context)
            }
            if (hasAnyConcept(stemmedWords, cleaned, "بيانات", "كونيكسيون", "انترنت")) {
                return openMobileDataPanel(context)
            }
            if (hasAnyConcept(stemmedWords, cleaned, "ويفي", "واي فاي", "وايفاي")) {
                return openWifiPanel(context)
            }
            if (hasAnyConcept(stemmedWords, cleaned, "بلوتوث", "بلوتوت")) {
                return openBluetoothSettings(context)
            }
            return findAndLaunchAnyInstalledApp(context, cleaned)
        }

        // 9. Polite Fusha conversational intelligence
        tryPersonaReply(cleaned, hasLaughter)?.let { return it }

        return "لم ألتقط طلبك بوضوح وسط الصوت المحيط. يمكنك أن تقول مثلاً: كم الساعة، التقط لقطة شاشة، افتح فري فاير، أو نظف الذاكرة."
    }

    private fun trySemanticIntent(
        context: Context,
        cleaned: String,
        cleanedWords: List<String>,
        stemmedWords: List<String>
    ): String? {
        // Screenshot: "دير لقطة شاشة", "صور الشاشة", "التقط شاشة", "كابتور"
        val wantsScreenshot =
            (hasAnyConcept(stemmedWords, cleaned, "لقطه", "التقط", "صور", "صوره", "كابتور", "سكرين") &&
                hasAnyConcept(stemmedWords, cleaned, "شاشه", "كابتور", "سكرين")) ||
                cleaned.contains("لقطه شاشه") ||
                cleaned.contains("صور الشاشه")
        if (wantsScreenshot) {
            return performAccessibilityNav("SCREENSHOT", "حسناً، لقد التقطت صورة للشاشة الآن.")
        }

        // Alarm with optional hour
        if (hasAnyConcept(stemmedWords, cleaned, "منبه", "الارم")) {
            val hourMatch = Regex("\\b(\\d{1,2})\\b").find(cleaned)
            val hour = hourMatch?.groupValues?.get(1)?.toIntOrNull()
            if (hour != null && hour in 0..23) {
                return setAlarmForTime(context, hour, 0)
            }
            return openAlarmApp(context, "حسناً، لقد فتحت لك تطبيق المنبه والساعة.")
        }

        // Time and Date: "شحال ساعة", "كم الساعة", "قداه الوقت", "شحال راه الوقت", "تاريخ اليوم"
        val wantsDate = hasAnyConcept(stemmedWords, cleaned, "تاريخ", "التاريخ", "اي يوم", "النهار", "شهر")
        val wantsTime = hasAnyConcept(stemmedWords, cleaned, "ساعه", "الوقت", "وقت") &&
            !hasAnyConcept(stemmedWords, cleaned, "افتح", "تطبيق")
        if (wantsDate) {
            val cal = Calendar.getInstance()
            val dayName = arabicDays.getOrElse(cal.get(Calendar.DAY_OF_WEEK)) { "" }
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            val monthName = arabicMonths.getOrElse(cal.get(Calendar.MONTH)) { "" }
            val year = cal.get(Calendar.YEAR)
            return "اليوم هو $dayName، الموافق لـ $dayNum $monthName سنة $year."
        }
        if (wantsTime) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val min = cal.get(Calendar.MINUTE)
            val dayName = arabicDays.getOrElse(cal.get(Calendar.DAY_OF_WEEK)) { "" }
            val hhMm = String.format(Locale.US, "%02d:%02d", hour, min)
            return "الساعة الآن هي $hhMm، واليوم هو $dayName."
        }

        // Battery level
        if (hasAnyConcept(stemmedWords, cleaned, "باتري", "بطاريه", "شحن", "تشارج")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val state = if (bm.isCharging) "والهاتف قيد الشحن حالياً" else "وهي في حالة جيدة"
            return "نسبة شحن البطارية الآن $pct% $state."
        }

        // Clean RAM & background apps
        if (hasAnyConcept(stemmedWords, cleaned, "نظف", "تنظيف", "خفف", "سرع", "رام", "ذاكره", "تخزين")) {
            return cleanPhoneAndRam(context, "حسناً، لقد نظفت الذاكرة وحررت {freedMb} ميغابايت، وأنا مستمر في العمل على مدار الساعة.")
        }

        // Flashlight
        if (hasAnyConcept(stemmedWords, cleaned, "فلاش", "مصباح", "كشاف", "ظلمه", "بيل")) {
            toggleTorch(context)
            return if (torchEnabled) "حسناً، لقد أضأت لك المصباح الخلفي." else "حسناً، لقد أطفأت المصباح الخلفي."
        }

        // Accessibility navigation
        if (hasAnyConcept(stemmedWords, cleaned, "اشعارات", "نوتيفيكاسيون")) {
            return performAccessibilityNav("NOTIFICATIONS", "حسناً، لقد فتحت لك لوحة الإشعارات.")
        }
        if (hasAnyConcept(stemmedWords, cleaned, "رجوع", "ارجع", "خلف", "وراء") && !cleaned.contains("رساله")) {
            return performAccessibilityNav("BACK", "تم، لقد عدت إلى الخلف.")
        }
        if (hasAnyConcept(stemmedWords, cleaned, "رئيسيه", "هوم", "اخرج للشاشه")) {
            return performAccessibilityNav("HOME", "حسناً، ها هي الشاشة الرئيسية.")
        }
        if (hasAnyConcept(stemmedWords, cleaned, "قفل الشاشه", "اقفل الشاشه", "اطفي الشاشه", "سكر الشاشه")) {
            return performAccessibilityNav("LOCK_SCREEN", "حسناً، سأقفل الشاشة الآن وأبقى مستعداً لندائك.")
        }
        if (hasAnyConcept(stemmedWords, cleaned, "مفتوحه", "اخر التطبيقات", "تطبيقات الخلفيه")) {
            return performAccessibilityNav("RECENTS", "حسناً، هذه هي التطبيقات المفتوحة.")
        }

        // Volume & Brightness
        if (hasAnyConcept(stemmedWords, cleaned, "صوت", "فوليم", "الاذاعة")) {
            val down = hasAnyConcept(stemmedWords, cleaned, "نقص", "اخفض", "وطي", "قلل", "هبط", "ناقص")
            return adjustVolume(context, if (down) "DOWN" else "UP", if (down) "حسناً، لقد خفضت مستوى الصوت." else "حسناً، لقد رفعت مستوى الصوت.")
        }
        if (hasAnyConcept(stemmedWords, cleaned, "سطوع", "اضاءه", "نور الشاشه")) {
            val down = hasAnyConcept(stemmedWords, cleaned, "نقص", "اخفض", "قلل", "هبط", "ضعف")
            return adjustBrightness(context, if (down) "DOWN" else "UP", if (down) "حسناً، لقد خفضت سطوع الشاشة." else "حسناً، لقد رفعت سطوع الشاشة.")
        }

        return null
    }

    private fun tryPersonaReply(cleaned: String, hasLaughter: Boolean): String? {
        if (Regex("^(السلام عليكم|سلام|اهلا|مرحبا|صباح الخير|مساء الخير|واش راك|كيفاش راك|كيف حالك|لباس|لاباس)").containsMatchIn(cleaned)) {
            return "وعليكم السلام ورحمة الله! أنا بخير والحمد لله، ومستعد تماماً لتنفيذ أوامرك في الهاتف."
        }
        if (cleaned.contains("يعطيك الصحه") || cleaned.contains("صحيت") || cleaned.contains("شكرا") || cleaned.contains("بارك الله")) {
            return "على الرحب والسعة، أنا في خدمتك في أي وقت على مدار الساعة."
        }
        if (cleaned.contains("نكته") || cleaned.contains("ضحكني")) {
            return "في أحد الأيام سئل هاتف الذكي لماذا لا تتعب؟ فأجاب لأن معي بويكتا ينظف ذاكرتي ويعمل معي على مدار الساعة."
        }
        if (cleaned.contains("شكون انت") || cleaned.contains("من انت") || cleaned.contains("عرفني")) {
            return "أنا بويكتا، مساعدك الصوتي الشخصي على هاتف غالاكسي إم ثلاثة عشر، أعمل بالكامل بدون إنترنت وأنفذ أوامرك بالعربية الفصحى."
        }
        if (cleaned.contains("ماذا تستطيع") || cleaned.contains("واش تقدر") || cleaned.contains("قدراتك")) {
            return "أستطيع فتح أي تطبيق أو لعبة، التقاط صورة للشاشة، قراءة الساعة والبطارية، الاتصال بجهات الاتصال، وتنظيف الذاكرة بالكامل."
        }
        if (hasLaughter && cleaned.length < 18) {
            return "أدام الله سرورك! إذا احتجت إلى فتح أي تطبيق أو تنفيذ أي أمر فأخبرني فقط."
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
            "/" -> if (b == 0.0) return "لا يمكن القسمة على صفر." else a / b
            else -> return null
        }
        val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
        return "النتيجة هي $formatted."
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
                val chargingText = if (bm.isCharging) "والهاتف قيد الشحن حالياً" else "وهي في حالة جيدة"
                rule.maleReply
                    .replace("{battery}", pct.toString())
                    .replace("{charging}", chargingText)
            }
            ActionType.FLASHLIGHT -> {
                toggleTorch(context)
                val stateMsg = if (torchEnabled) "حسناً، لقد أضأت لك المصباح الخلفي." else "حسناً، لقد أطفأت المصباح الخلفي."
                rule.maleReply.replace("{flashState}", stateMsg)
            }
            ActionType.TIME_DATE -> {
                val cal = Calendar.getInstance()
                val hhMm = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                val dayName = arabicDays.getOrElse(cal.get(Calendar.DAY_OF_WEEK)) { "" }
                rule.maleReply.replace("{time}", "$hhMm، واليوم هو $dayName")
            }
            ActionType.MATH_CALC -> "أخبرني بالعملية الحسابية، مثلاً احسب مئتين وخمسين في أربعة."
            ActionType.CALL_CONTACT -> {
                placeCall(context, rule.target)
                rule.maleReply
            }
            ActionType.CUSTOM_REPLY -> rule.maleReply
        }
    }

    private fun performAccessibilityNav(navTarget: String, reply: String): String {
        val acc = BoyktaAccessibilityService.instance
            ?: return "يرجى تفعيل صلاحية التحكم الشامل من التطبيق حتى أتمكن من تنفيذ هذا الأمر."
        return if (acc.performNavAction(navTarget)) {
            reply
        } else {
            "حاولت تنفيذ الأمر ولكن النظام منع الحركة في هذه الشاشة."
        }
    }

    private fun adjustBrightness(context: Context, direction: String, reply: String): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "يرجى تفعيل إذن تعديل الإعدادات حتى أتمكن من ضبط السطوع مباشرة."
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
            "تعذر تعديل سطوع الشاشة في هذه اللحظة."
        }
    }

    private fun adjustVolume(context: Context, direction: String, reply: String): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val adj = if (direction.equals("UP", true)) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adj, AudioManager.FLAG_SHOW_UI)
        am.adjustStreamVolume(AudioManager.STREAM_RING, adj, 0)
        return reply
    }

    private fun setAlarmForTime(context: Context, hour: Int, minute: Int): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "حسناً، لقد أعددت لك المنبه على الساعة ${ArabicNumberToWords.timeToFushaWords(hour, minute)}."
        } catch (_: Exception) {
            openAlarmApp(context, "لقد فتحت لك تطبيق المنبه.")
        }
    }

    private fun openAlarmApp(context: Context, reply: String): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            reply
        } catch (_: Exception) {
            "تعذر فتح تطبيق الساعة والمنبه."
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
            "حسناً، لقد فتحت لك لوحة الواي فاي."
        } catch (_: Exception) {
            "تعذر فتح إعدادات الواي فاي."
        }
    }

    private fun openBluetoothSettings(context: Context): String {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "حسناً، لقد فتحت لك إعدادات البلوتوث."
        } catch (_: Exception) {
            "تعذر فتح إعدادات البلوتوث."
        }
    }

    private fun openCamera(context: Context): String {
        return try {
            val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(camIntent)
            "حسناً، لقد فتحت لك الكاميرا."
        } catch (_: Exception) {
            "تعذر فتح الكاميرا، يرجى التأكد من تفعيل إذن الكاميرا."
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
            "حسناً، لقد فتحت لك لوحة بيانات الاتصال."
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            "لقد فتحت لك إعدادات الشبكة."
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

    private fun transliterateArabicToLatin(arabic: String): String {
        val map = mapOf(
            'ا' to "a", 'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j",
            'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "th", 'ر' to "r",
            'ز' to "z", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "d",
            'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f",
            'ق' to "q", 'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n",
            'ه' to "h", 'و' to "o", 'ي' to "i", 'پ' to "p", 'ڤ' to "v", 'چ' to "g"
        )
        val sb = StringBuilder()
        for (ch in arabic) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' || ch == ' ' -> sb.append(ch)
                map.containsKey(ch) -> sb.append(map[ch])
            }
        }
        return sb.toString().replace(Regex("\\s+"), "").trim()
    }

    private fun consonantSkeleton(s: String): String {
        return s.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .replace('p', 'b')
            .replace('v', 'f')
            .replace('q', 'k')
            .replace(Regex("[aeiouy]"), "")
    }

    private fun findAndLaunchAnyInstalledApp(context: Context, cleanedQuery: String): String {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)

        val appSearchText = cleanedQuery
            .replace(Regex("(افتح|حل|شعل|ديرلي|دير|ادخل|روح|شغل|تطبيق|برنامج|لعبه|اللعبه)"), " ")
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
                        return "حسناً، لقد فتحت لك ${match.loadLabel(pm)}."
                    }
                }
            }
        }

        val cleanTokens = appSearchText.split(" ").map { stemArabicToken(it) }.filter { it.length >= 2 }
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
                return "حسناً، لقد فتحت لك ${directMatch.loadLabel(pm)}."
            }
        }

        // Phonetic Arabic-to-Latin match for any English-named app installed on M13
        val latinQuery = transliterateArabicToLatin(appSearchText)
        val querySkel = consonantSkeleton(latinQuery)
        if (querySkel.length >= 3) {
            val phoneticMatch = apps.firstOrNull { info ->
                val labelSkel = consonantSkeleton(info.loadLabel(pm).toString())
                val pkgSkel = consonantSkeleton(info.activityInfo.packageName)
                labelSkel.contains(querySkel) || pkgSkel.contains(querySkel) ||
                    (labelSkel.length >= 3 && levenshteinDist(labelSkel.take(querySkel.length), querySkel) <= 1)
            }
            if (phoneticMatch != null) {
                val launch = pm.getLaunchIntentForPackage(phoneticMatch.activityInfo.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return "حسناً، لقد فتحت لك ${phoneticMatch.loadLabel(pm)}."
                }
            }
        }

        return if (appSearchText.isNotEmpty()) {
            "بحثت في الهاتف بالكامل ولم أجد تطبيق $appSearchText مثبتاً لديك."
        } else {
            "بحثت في الهاتف ولم أجد هذا التطبيق، يرجى ذكر اسمه بوضوح."
        }
    }

    private fun readContactNumberByQuery(context: Context, cleanedQuery: String): String {
        val targetName = cleanedQuery
            .replace(
                Regex("(اقري|اقرا|شحال|كم|ما|اعطيني|من|جهات الاتصال|في|رقم|هاتف|نيميرو|نمرو|تاع|تع)"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()

        for (c in contacts) {
            if (targetName.contains(normalizeArabic(c.alias)) ||
                targetName.contains(normalizeArabic(c.arabicName))
            ) {
                val spokenDigits = ArabicNumberToWords.digitByDigit(c.phoneNumber)
                return "رقم ${c.arabicName} في جهات الاتصال هو: $spokenDigits."
            }
        }

        val phoneMatch = findContactInPhone(context, targetName)
        if (phoneMatch != null) {
            val spokenDigits = ArabicNumberToWords.digitByDigit(phoneMatch.second)
            return "رقم ${phoneMatch.first} المسجل لديك هو: $spokenDigits."
        }

        return if (targetName.isNotEmpty()) {
            "بحثت في كامل جهات الاتصال ولم أجد $targetName مسجلاً في الهاتف."
        } else {
            "بحثت في جهات الاتصال ولم أجد هذا الاسم مسجلاً لديك."
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
