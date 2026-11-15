# Boykta M13 — بويكتا المساعد الصوتي بدون إنترنت (armeabi-v7a)

مشروع Android Kotlin متكامل لهاتف **Samsung Galaxy M13** بمعمارية `armeabi-v7a` يعمل 24/24 بصوت ذكوري عربي فصيح عالي الوضوح **مدمج بالكامل داخل التطبيق (Sherpa-ONNX Miro V2 High)** مع محرك سمع عصبي **Moonshine Arabic + Silero VAD v5**.

## المزايا الرئيسية (الإصدار 4.0)
- **نطق عربي فصيح واضح 100% (`InAppArabicTtsEngine.kt` & `ArabicNumberToWords.kt`)**: يعتمد على صوت **Miro V2 High** مع تحويل تلقائي للأرقام والساعات والنسب المئوية إلى كلمات فصحى مشكولة.
- **سمع عصبي دقيق بلا هلوسة (`BoyktaBackgroundService.kt`)**: يجمع بين **Silero VAD v5** (مضمّن في التطبيق) ومحرك **Moonshine Base Arabic Quantized (114MB)** مع دعم كامل لحزم Vosk المحفوظة مسبقاً.
- **فهم دلالي ذكي وليس حفظاً حرفياً (`DarijaIntentRouter.kt`)**: يفهم الدارجة والفصحى («شحال ساعة»، «دير لقطة شاشة»، «افتح أي تطبيق») ويجيب دائماً بالعربية الفصحى.
- **حفظ دائم للحزم وتوقيع ثابت (`build-apk.yml` & `SdCardModelLocator.kt`)**: توقيع موحّد لكل التحديثات + حفظ دائم في `/storage/emulated/0/BoyktaModels`.
