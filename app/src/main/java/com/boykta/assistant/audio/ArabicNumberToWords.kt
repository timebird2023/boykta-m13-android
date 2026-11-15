package com.boykta.assistant.audio

object ArabicNumberToWords {

    private val ones = arrayOf(
        "صِفْر", "وَاحِد", "اثْنَان", "ثَلَاثَة", "أَرْبَعَة", "خَمْسَة",
        "سِتَّة", "سَبْعَة", "ثَمَانِيَة", "تِسْعَة", "عَشَرَة",
        "أَحَدَ عَشَر", "اثْنَا عَشَر", "ثَلَاثَةَ عَشَر", "أَرْبَعَةَ عَشَر", "خَمْسَةَ عَشَر",
        "سِتَّةَ عَشَر", "سَبْعَةَ عَشَر", "ثَمَانِيَةَ عَشَر", "تِسْعَةَ عَشَر"
    )

    private val tens = arrayOf(
        "", "", "عِشْرُون", "ثَلَاثُون", "أَرْبَعُون", "خَمْسُون",
        "سِتُّون", "سَبْعُون", "ثَمَانُون", "تِسْعُون"
    )

    private val hundreds = arrayOf(
        "", "مِئَة", "مِئَتَان", "ثَلَاثُمِئَة", "أَرْبَعُمِئَة", "خَمْسُمِئَة",
        "سِتُّمِئَة", "سَبْعُمِئَة", "ثَمَانُمِئَة", "تِسْعُمِئَة"
    )

    private val hourOrdinals = arrayOf(
        "الثَّانِيَةَ عَشْرَة", "الوَاحِدَة", "الثَّانِيَة", "الثَّالِثَة", "الرَّابِعَة",
        "الخَامِسَة", "السَّادِسَة", "السَّابِعَة", "الثَّامِنَة", "التَّاسِعَة",
        "العَاشِرَة", "الحَادِيَةَ عَشْرَة", "الثَّانِيَةَ عَشْرَة",
        "الوَاحِدَةَ بَعْدَ الظُّهْر", "الثَّانِيَةَ بَعْدَ الظُّهْر", "الثَّالِثَةَ عَصْراً",
        "الرَّابِعَةَ عَصْراً", "الخَامِسَةَ مَسَاءً", "السَّادِسَةَ مَسَاءً",
        "السَّابِعَةَ مَسَاءً", "الثَّامِنَةَ مَسَاءً", "التَّاسِعَةَ لَيْلاً",
        "العَاشِرَةَ لَيْلاً", "الحَادِيَةَ عَشْرَةَ لَيْلاً"
    )

    fun intToArabicWords(n: Int): String {
        if (n < 0) return "سَالِب " + intToArabicWords(-n)
        if (n < 20) return ones[n]
        if (n < 100) {
            val t = n / 10
            val o = n % 10
            return if (o == 0) tens[t] else "${ones[o]} وَ${tens[t]}"
        }
        if (n < 1000) {
            val h = n / 100
            val rem = n % 100
            return if (rem == 0) hundreds[h] else "${hundreds[h]} وَ${intToArabicWords(rem)}"
        }
        if (n < 1000000) {
            val k = n / 1000
            val rem = n % 1000
            val kWord = when (k) {
                1 -> "أَلْف"
                2 -> "أَلْفَان"
                in 3..10 -> "${intToArabicWords(k)} آلَاف"
                else -> "${intToArabicWords(k)} أَلْفاً"
            }
            return if (rem == 0) kWord else "$kWord وَ${intToArabicWords(rem)}"
        }
        return n.toString()
    }

    fun timeToFushaWords(hour24: Int, minute: Int): String {
        val hWord = hourOrdinals[hour24.coerceIn(0, 23)]
        return when (minute) {
            0 -> "$hWord تَمَاماً"
            1 -> "$hWord وَدَقِيقَةٌ وَاحِدَة"
            2 -> "$hWord وَدَقِيقَتَان"
            in 3..10 -> "$hWord وَ${intToArabicWords(minute)} دَقَائِق"
            15 -> "$hWord وَالرُّبْع"
            30 -> "$hWord وَالنِّصْف"
            else -> "$hWord وَ${intToArabicWords(minute)} دَقِيقَة"
        }
    }

    fun digitByDigit(digits: String): String {
        return digits.mapNotNull { ch ->
            if (ch in '0'..'9') ones[ch - '0'] else null
        }.joinToString(" ")
    }

    fun prepareForFushaTts(input: String): String {
        var text = input
            .replace("24/24", " أَرْبَعَةٍ وَعِشْرِينَ سَاعَةً ")
            .replace("M13", " إِمْ ثَلَاثَةَ عَشَر ", ignoreCase = true)
            .replace("Galaxy", " غَالَاكْسِي ", ignoreCase = true)
            .replace("OK Google", " أُوكِي غُوغِل ", ignoreCase = true)
            .replace("Free Fire", " فْرِي فَايَر ", ignoreCase = true)
            .replace("Facebook", " فَيْسْبُوك ", ignoreCase = true)
            .replace("WhatsApp", " وَاتْسَاب ", ignoreCase = true)
            .replace("YouTube", " يُوتْيُوب ", ignoreCase = true)
            .replace("Moonshine", " مُونْشَايْن ", ignoreCase = true)
            .replace("Accessibility", " التَّحَكُّم الشَّامِل ", ignoreCase = true)
            .replace("RAM", " الذَّاكِرَة ", ignoreCase = true)
            .replace("MB", " مِيغَابَايْت ", ignoreCase = true)
            .replace("GB", " جِيغَابَايْت ", ignoreCase = true)
            .replace("%", " بِالمِئَة ")

        // Replace HH:MM times with crisp Fusha hour+minute phrases
        text = Regex("\\b(\\d{1,2}):(\\d{2})\\b").replace(text) { m ->
            val hh = m.groupValues[1].toIntOrNull() ?: 0
            val mm = m.groupValues[2].toIntOrNull() ?: 0
            if (hh in 0..23 && mm in 0..59) timeToFushaWords(hh, mm) else m.value
        }

        // Replace standalone numbers with Arabic Fusha words
        text = Regex("\\b(\\d{1,5})\\b").replace(text) { m ->
            val v = m.groupValues[1].toIntOrNull()
            if (v != null) intToArabicWords(v) else m.value
        }

        // Light Fusha vocalization on frequent words for clear Miro V2 / Kareem pronunciation
        text = text
            .replace("حسنا", "حَسَناً")
            .replace("بويكتا", "بُويِكْتَا")
            .replace("الساعة الآن", "السَّاعَةُ الآنَ")
            .replace("نسبة شحن البطارية", "نِسْبَةُ شَحْنِ البَطَّارِيَّةِ")
            .replace("لقد فتحت لك", "لَقَدْ فَتَحْتُ لَكَ")
            .replace("لقطة شاشة", "لَقْطَةَ شَاشَةٍ")
            .replace("صورة للشاشة", "صُورَةً لِلشَّاشَةِ")
            .replace("بالمئة", "بِالمِئَة")
            .replace("ميغابايت", "مِيغَابَايْت")
            .replace("/", " عَلَى ")
            .replace(Regex("[«»—()\\[\\]\"':!؟]"), " ، ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return text
    }
}
