# Boykta M13 — بويكتا المساعد الصوتي بدون إنترنت (armeabi-v7a)

مشروع Android Kotlin متكامل لهاتف **Samsung Galaxy M13** بمعمارية `armeabi-v7a` يعمل 24/24 بصوت ذكوري عربي **مدمج بالكامل داخل التطبيق (Sherpa-ONNX Kareem)** مع استجابة فورية لكلمة **"بويكتا"** بنفس أسلوب OK Google.

## المزايا الرئيسية (الإصدار 3.1)
- **نطق عربي صافي بلا تقطيع (`InAppArabicTtsEngine.kt`)**: محرك Sherpa-ONNX موحّد (`object` Singleton) يستخدم `AudioTrack.MODE_STATIC` + `AudioFocusRequest` + مهلة صامتة 165ms في نهاية كل جملة لضمان عدم قص آخر الكلام أبداً.
- **تدريب نداء بويكتا على صوتك (`CustomWakeStore.kt` & `WakeWordMatcher.kt`)**: يتعرف تلقائياً على بدائل Vosk (بما فيها «ورقدتها») مع زر **"علّمني نطقي لكلمة بويكتا"** لحفظ نطقك الشخصي للأبد، وبقاء الميكروفون مفتوحاً 10 ثوانٍ بدون مقاطعة بعد الرنة.
- **حفظ دائم للحزم وتوقيع ثابت (`build-apk.yml` & `SdCardModelLocator.kt`)**: توقيع موحّد لكل التحديثات + نسخ تلقائي للحزم إلى `/storage/emulated/0/BoyktaModels` حتى لا تعيد تحميل الـ 158MB و 64MB أبداً!
