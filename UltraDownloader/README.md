# Ultra Downloader — Android Video & Audio Downloader (Kotlin + Jetpack Compose)

مشروع تطبيق أندرويد احترافي ومتكامل (**Ultra Downloader**) مبني باستخدام **Kotlin** و **Jetpack Compose (Material 3)** وفق معمارية **Clean Architecture + MVVM**، مع محرك تنزيل حقيقي يدعم الاستئناف (**HTTP Range 206**)، دمج الصوت والفيديو عبر **Android MediaMuxer**، فحص سلامة الملفات وبصمة **SHA-256**، وحماية مشددة ضد هجمات **SSRF** و **Path Traversal**.

---

## 1. المعمارية والبنية الهندسية للمشروع (Project Structure)

```text
UltraDownloader/
├── settings.gradle.kts                  # إعدادات وحدات Gradle ومستودعات Google/MavenCentral
├── build.gradle.kts                     # إعدادات الإضافات الرئيسية (AGP 8.7.2, Kotlin 2.0.21, Hilt, KSP)
├── gradle.properties                    # إعدادات JVM ومتغير ULTRA_BACKEND_BASE_URL الخارجي
├── gradle/
│   ├── libs.versions.toml               # كتالوج الإصدارات الموحد (Compose BOM, Room, WorkManager, OkHttp, Media3)
│   └── wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts                 # إعدادات تطبيق Android وحقن BuildConfig دون أي مفاتيح مضمنة
│   ├── proguard-rules.pro               # قواعد تقليص الكود وحماية حزم Serialization في نسخة Release
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # أقل أذونات ممكنة، فرض HTTPS، FileProvider، وWorkManager Foreground
│       │   ├── res/
│       │   │   ├── values/strings.xml       # نصوص الواجهة باللغة الإنجليزية (LTR)
│       │   │   ├── values-ar/strings.xml    # نصوص الواجهة باللغة العربية الكاملة (RTL)
│       │   │   ├── xml/network_security_config.xml # حظر HTTP غير المشفر (Cleartext Traffic Disabled)
│       │   │   ├── xml/file_paths.xml       # مسارات FileProvider الآمنة للمشاركة والفتح
│       │   │   └── drawable/ic_launcher_foreground.xml # شعار التطبيق بتدرج البنفسجي والأزرق الكهربائي
│       │   └── java/com/ultradownloader/app/
│       │       ├── UltraDownloaderApp.kt    # تهيئة Hilt و WorkManager مع حد أقصى للتنزيلات المتزامنة
│       │       ├── MainActivity.kt          # نقطة الدخول ودعم استقبال الروابط المشتركة (ACTION_SEND)
│       │       ├── di/AppModule.kt          # حقن الاعتماديات (OkHttp TLS-only, Room DB, Json)
│       │       ├── domain/model/            # نماذج المجال (PlatformType, MediaFormat, DownloadTask, AppError)
│       │       ├── security/
│       │       │   ├── UrlSecurityValidator.kt  # حماية SSRF وفرض HTTPS واكتشاف المنصة تلقائياً
│       │       │   └── FileSecurityValidator.kt # منع Path Traversal وفحص ترويسة MP4/MP3 وحساب SHA-256
│       │       ├── data/
│       │       │   ├── connector/           # موصلات المنصات المستقلة (YouTube, TikTok, Instagram, DirectMedia)
│       │       │   ├── download/            # محرك التنزيل (HttpRangeDownloader, MediaStreamMerger, MediaDownloadWorker)
│       │       │   ├── local/               # قاعدة بيانات Room وتفضيلات DataStore
│       │       │   └── storage/             # إدارة التخزين عبر Android Scoped MediaStore وفحص المساحة الحرة
│       │       └── ui/
│       │           ├── theme/               # الهوية البصرية الفاخرة (Obsidian Dark / Daylight / RTL & LTR)
│       │           ├── components/          # البطاقات الزجاجية والأزرار المتدرجة ومحولات السرعة والوقت
│       │           ├── viewmodel/           # UltraMainViewModel لإدارة الحالة التفاعلية (StateFlow)
│       │           ├── navigation/          # التوجيه المتجاوب (NavigationBar للهواتف و NavigationRail للأجهزة الكبيرة)
│       │           └── screens/             # الشاشات الـ 9 الكاملة للتطبيق
│       └── test/java/com/ultradownloader/app/
│           └── UltraDownloaderUnitTest.kt   # اختبارات JUnit لوحدات التحليل والأمان والتنزيل
├── backend/                             # خدمة Backend اختيارية (FastAPI) لتحليل الروابط مركزيًا عند الحاجة
│   ├── app/main.py
│   ├── app/security.py
│   ├── tests/test_backend_and_engine.py
│   ├── requirements.txt
│   ├── Dockerfile
│   └── .env.example
└── verification/
    └── JavaCoreVerificationSuite.java   # حزمة تحقق تنفيذية بلغة Java 17
```

---

## 2. فحص إمكانات المنصات ومصفوفة الامتثال القانوني (Platform Capabilities & Policy Matrix)

تم تصميم كل منصة في **وحدة مستقلة (`PlatformConnector`)** قابلة للتحديث بمعزل عن بقية التطبيق، مع التزام صارم بشروط الخدمة وحقوق النشر:

| المنصة | الموصل البرمجي | ما يتم تنفيذه فعلياً وبصورة قانونية | القيود المفروضة من المنصة والبديل الرسمي المدمج |
| :--- | :--- | :--- | :--- |
| **YouTube** | `YouTubeConnector.kt` | جلب عنوان الفيديو، اسم القناة، الرابط الرسمي، والصورة المصغرة عالية الدقة عبر واجهة **YouTube Official oEmbed API** (`https://www.youtube.com/oembed`)، وتنزيل الروابط المباشرة المصرح بها أو المملوكة للمستخدم. | تمنع شروط خدمة YouTube (القسم II.J و API Policy) فك تشفير البث أو سحب ملفات الفيديو العامة من تطبيقات الطرف الثالث. عند إدخال رابط يوتيوب قياسي لا يرفق تدفقاً مصرحاً، يعرض التطبيق المعاينة الكاملة ويوجه المستخدم بوضوح لتنزيله عبر تطبيق YouTube الرسمي أو **YouTube Studio** دون إظهار أزرار وهمية. |
| **TikTok** | `TikTokConnector.kt` | استخراج بيانات الفيديو العام وصانع المحتوى والصورة المصغرة عبر **TikTok Official oEmbed API** (`https://www.tiktok.com/oembed`)، وتنزيل ملفات MP4 العامة عندما يوفر المصدر رابط التنزيل المباشر (`download_url`). | عندما يغلق صانع المحتوى خيار التنزيل العام أو يكون الحساب خاصاً، لا يتحايل التطبيق على الخصوصية ويعرض زر فتح خيار «حفظ الفيديو» الرسمي في TikTok. |
| **Instagram** | `InstagramConnector.kt` | التعرف على روابط Reels والمنشورات العامة (`/reel/`, `/p/`, `/tv/`) واستخراج بيانات OpenGraph العامة (`og:title`, `og:image`, `og:video:secure_url`) وتنزيل المقاطع المملوكة أو المسموح بتنزيلها. | لا يطلب التطبيق تسجيل الدخول ولا يخزن الكوكيز ولا يتجاوز جدار الحماية للحسابات الخاصة (Private Accounts). عند طلب Instagram تسجيل الدخول، يعرض التطبيق تنبيهاً واضحاً مع زر الانتقال للتطبيق الرسمي. |
| **Direct Media** | `DirectMediaConnector.kt` | دعم كامل لتنزيل روابط الفيديو والصوت المباشرة عبر HTTPS (`.mp4`, `.m4a`, `.mp3`, `.webm`, `.mov`) مع قراءة `Content-Length` و `Accept-Ranges` الفعلية من الخادم وعرض الدقة الحقيقية فقط دون ادعاء جودة 4K/8K غير موجودة. | يتم رفض روابط HTTP غير المشفرة أو الروابط التي تشير إلى عناوين شبكات داخلية (SSRF). |

---

## 3. الصفحات الـ 9 المنفذة في التطبيق

1. **شاشة الترحيب (`OnboardingScreen.kt`)**: شاشة ترحيب أنيقة من 3 بطاقات تفاعلية تشرح مزايا التطبيق مع زر **«تخطي / Skip»** واضح.
2. **الشاشة الرئيسية (`HomeScreen.kt`)**: تتضمن شعار التطبيق، حقل إدخال الرابط، زر **«لصق من الحافظة»** (لا يقرأ الحافظة إلا عند ضغط المستخدم)، شارة اكتشاف المنصة تلقائياً، زر **«تحليل الرابط»**، ومعاينة سريعة لآخر فيديو تم تحليله.
3. **شاشة معاينة الفيديو واختيار الجودة (`VideoPreviewScreen.kt`)**: تعرض الصورة المصغرة والعنوان واسم الحساب، وتوفر قائمة الجودات المتاحة فعلياً (360p, 480p, 720p, 1080p+ أو MP3) مع الحجم المتوقع، أو رسالة السياسة الرسمية عند تقييد المنصة للتنزيل.
4. **شاشة التنزيلات الجارية (`ActiveDownloadsScreen.kt`)**: تعرض شريط التقدم، النسبة المئوية، الحجم المنقول، السرعة اللحظية (`KB/s` أو `MB/s`)، الوقت المتبقي (`ETA`)، مع أزرار **إيقاف مؤقت (Pause)**، **استئناف (Resume)**، **إعادة المحاولة (Retry)**، و**إلغاء وحذف (Cancel)**.
5. **شاشة التنزيلات المكتملة (`CompletedDownloadsScreen.kt`)**: تعرض قائمة التنزيلات المحفوظة بعد التحقق من بصمة `SHA-256`، مع أزرار **تشغيل (Play)**، **مشاركة (Share)**، و**حذف (Delete)**.
6. **مشغل الفيديو المحلي (`LocalVideoPlayerScreen.kt`)**: مشغل مدمج مبني على **AndroidX Media3 ExoPlayer** مع تحرير تلقائي للموارد (`DisposableEffect`) لمنع تسرب الذاكرة، بالإضافة إلى زر الفتح في مشغل خارجي.
7. **شاشة الإعدادات (`SettingsScreen.kt`)**: التحكم في المظهر (**Obsidian Dark** / **Daylight Light** / **System**)، اللغة (**العربية RTL** / **English LTR**)، التنزيل عبر Wi-Fi فقط، والحد الأقصى للتنزيلات المتزامنة (1 إلى 5).
8. **شاشة حول التطبيق وسياسة الخصوصية (`AboutPrivacyScreen.kt`)**: توضح سياسة عدم جمع البيانات وعدم طلب حسابات التواصل الاجتماعي.
9. **شاشة تشخيص الأخطاء والحلول المقترحة (`ErrorDiagnosticScreen.kt`)**: تعرض سبب الخطأ بدقة (رابط غير صالح، حظر SSRF، انقطاع شبكة، نقص مساحة التخزين، أو قيد منصة) مع خطوات عملية للحل.

---

## 4. متى نحتاج إلى الـ Backend الاختياري وكيفية تشغيله وتأمينه؟

يعمل تطبيق الأندرويد بشكل مستقل تماماً على الجهاز (Standalone On-Device) افتراضياً. تم توفير خدمة Backend اختيارية (`backend/app/main.py` المبنية بـ **FastAPI**) للحالات التالية فقط:
- الرغبة في تحديث قواعد تحليل الروابط مركزيًا دون الحاجة لإصدار تحديث جديد لتطبيق الأندرويد على المتجر.
- تطبيق سياسات **Rate Limiting** وحماية **SSRF** على مستوى الخادم المؤسسي.

### خطوات تشغيل وتأمين الـ Backend:
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# تشغيل الخادم محلياً للاختبار
uvicorn app.main:app --host 127.0.0.1 --port 8443

# أو تشغيله في بيئة الإنتاج مع شهادة TLS/HTTPS:
uvicorn app.main:app --host 0.0.0.0 --port 8443 \
  --ssl-keyfile /etc/ssl/private/ultra_server.key \
  --ssl-certfile /etc/ssl/certs/ultra_server.crt
```

---

## 5. خطوات إعداد المشروع وبناء ملف APK وتثبيته على الهاتف

### المتطلبات الأساسية:
- **Android Studio Ladybug (2024.2.1)** أو أحدث.
- **JDK 17** (مضبوط في `compileOptions` و `kotlinOptions`).
- **Android SDK 35** (مع الحد الأدنى `minSdk = 26` لدعم Android 8.0 فما فوق).

### خطوات البناء عبر سطر الأوامر أو Android Studio:

1. **فك ضغط المشروع وفتحه في Android Studio**:
   ```bash
   unzip UltraDownloader_Android_Project.zip -d UltraDownloader
   cd UltraDownloader
   ```
2. **(اختياري) ضبط عنوان خادم الـ Backend في `local.properties`** (لا يتم حفظ أي أسرار داخل الكود المصدري):
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ULTRA_BACKEND_BASE_URL=https://api.yourdomain.com/
   ```
3. **بناء نسخة Debug APK للتجربة الفورية**:
   ```bash
   ./gradlew assembleDebug
   ```
   سيتم إنتاج ملف الـ APK في المسار:
   `app/build/outputs/apk/debug/app-debug.apk`
4. **تثبيت الـ APK على الهاتف المتصل عبر ADB**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. **بناء نسخة Release APK الموقعة والمضغوطة (R8/ProGuard)**:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 6. تشغيل الاختبارات والتحقق البرمجي (Running Tests)

1. **تشغيل اختبارات الوحدات لتطبيق الأندرويد (JUnit)**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. **تشغيل حزمة التحقق بلغة Java 17 مباشرة**:
   ```bash
   java verification/JavaCoreVerificationSuite.java
   ```
3. **تشغيل اختبارات الـ Backend ومحرك الاستئناف HTTP Range 206 وفحص اللغات (Python unittest)**:
   ```bash
   python3 backend/tests/test_backend_and_engine.py
   ```
