# IKTISHAFAT Teleprompter · ملقّن اكتشافات

تطبيق Android أصلي (Kotlin + Jetpack Compose) للملقّن الاحترافي مع تتبع الصوت واستوديو كاميرا، مُحسّن لجهاز Samsung Galaxy Tab S7 Ultra.
هذا هو **المرحلة الأولى** من مشروع IKTISHAFAT AI STUDIO. حالة كل ميزة موجودة في [`docs/STATUS.md`](docs/STATUS.md)، والبنية في [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## العربية

### المتطلبات
- Android Studio (إصدار Ladybug 2024.2 أو أحدث) مع JDK 17 المدمج.
- Android SDK 35 (يثبّته Android Studio تلقائياً).
- الجهاز: Android 8.0 (API 26) أو أحدث.

### طريقة 1: البناء في Android Studio على حاسوبك
1. فكّ ضغط المجلد `IktishafatTeleprompter`.
2. في Android Studio: **File → Open** واختر المجلد.
3. انتظر حتى ينتهي Gradle Sync (أول مرة تستغرق عدة دقائق لأنها تنزّل المكتبات).
4. وصّل الجهاز اللوحي بكابل USB وفعّل **خيارات المطوّر → تصحيح USB**.
5. اضغط زر ▶ **Run**. يُثبَّت التطبيق باسم «ملقّن اكتشافات» (نسخة التطوير تنتهي بـ `.debug`).

تشغيل الاختبارات:
```
gradlew testDebugUnitTest            # اختبارات المحرك (بدون جهاز)
gradlew connectedDebugAndroidTest    # اختبارات قاعدة البيانات والشاشات على الجهاز أو المحاكي
```

### طريقة 2: البناء التلقائي على GitHub (GitHub Actions)
1. أنشئ مستودعاً جديداً على GitHub (يمكن أن يكون خاصاً)، مثلاً `IktishafatTeleprompter`.
2. ارفع محتوى المجلد إليه (من GitHub Desktop أو بالأوامر):
   ```
   git init
   git add .
   git commit -m "IKTISHAFAT Teleprompter – phase 1"
   git branch -M main
   git remote add origin https://github.com/<اسم-حسابك>/IktishafatTeleprompter.git
   git push -u origin main
   ```
3. افتح تبويب **Actions** في المستودع. يبدأ البناء تلقائياً ويشمل:
   - اختبارات المحرك، فحص Lint، وبناء ملف APK.
   - اختبارات على محاكي جهاز لوحي (Android 14).
4. بعد نجاح البناء افتح التشغيل (Run) ونزّل **iktishafat-teleprompter-apk** من قسم *Artifacts*.

### ملف APK موقّع (للإصدار)
1. أنشئ مفتاح التوقيع مرة واحدة: شغّل `scripts\create-keystore.bat` (ويندوز). **احفظ الملف `release.jks` وكلمات السر في مكان آمن**؛ كل التحديثات المستقبلية يجب أن توقَّع بالمفتاح نفسه.
2. على حاسوبك: انسخ `keystore.properties.example` إلى `keystore.properties` واكتب كلمات السر، ثم:
   ```
   gradlew assembleRelease
   ```
   الملف الناتج: `app/build/outputs/apk/release/app-release.apk`.
3. على GitHub: **Settings → Secrets and variables → Actions** وأضف:
   | الاسم | القيمة |
   |---|---|
   | `IKT_KEYSTORE_BASE64` | محتوى `release.jks` بصيغة Base64 |
   | `IKT_KEYSTORE_PASSWORD` | كلمة سر المخزن |
   | `IKT_KEY_ALIAS` | `iktishafat` |
   | `IKT_KEY_PASSWORD` | كلمة سر المفتاح |
   
   بعدها يبني GitHub نسخة الإصدار الموقّعة تلقائياً. ملفات المفاتيح مستثناة في `.gitignore` ولا تُرفع أبداً.

### التثبيت على Galaxy Tab S7 Ultra
1. انقل ملف APK إلى الجهاز (USB أو Google Drive أو البريد).
2. افتحه، واسمح عند الطلب بـ **تثبيت تطبيقات غير معروفة** للتطبيق الذي فتحت منه الملف.
3. عند أول استخدام للاستوديو يطلب التطبيق إذن الكاميرا والميكروفون.
4. لتتبع الصوت دون إنترنت: **الإعدادات → الإدارة العامة → اللغة → تحويل الكلام إلى نص** ونزّل لغة العربية/الألمانية من محرك Google.
5. للقراءة الصوتية: **الإعدادات → تثبيت أصوات** داخل التطبيق.

### ريموت البلوتوث
اقرن الريموت أو لوحة المفاتيح من إعدادات بلوتوث الجهاز، ثم افتح الملقّن:

| الزر | الوظيفة |
|---|---|
| المسافة / Enter / زر الوسائط / رفع الصوت | تشغيل / إيقاف |
| Page Down / ↓ / خفض الصوت | تقدّم ربع شاشة |
| Page Up / ↑ | رجوع |
| → أو + | أسرع |
| ← أو − | أبطأ |
| R | من البداية |
| M | عكس الصورة |
| Esc | خروج |

---

## English (short)

Native Android teleprompter (Kotlin, Compose, Material 3, Room, CameraX, SpeechRecognizer, TextToSpeech), MVVM + clean architecture, offline-first, Arabic RTL first-class.

- **Android Studio:** File → Open → this folder → Run. Tests: `gradlew testDebugUnitTest` and `gradlew connectedDebugAndroidTest`.
- **GitHub Actions:** push to `main`; the workflow in `.github/workflows/android.yml` runs unit tests, lint, builds the APK, and runs instrumented tests on a tablet emulator. Download the APK from the run's *Artifacts*.
- **Signed release:** create a key with `scripts/create-keystore.*`, then either `keystore.properties` locally or the four `IKT_*` secrets in GitHub.
- Feature status: `docs/STATUS.md`. Architecture: `docs/ARCHITECTURE.md`.
