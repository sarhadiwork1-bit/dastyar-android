# دستیار (Dastyar) — دستیار صوتی اندروید

دستیار صوتی فارسی/انگلیسی با کلمه بیدارباش قابل‌تغییر (پیش‌فرض: «کیارش»)، با مغز Claude.

## ساخت مخزن و APK (از صفر)
1. وارد github.com شوید ← علامت **+** ← **New repository**.
2. نام: `dastyar-android` ، حالت **Private** ، تیک README را نزنید ← **Create repository**.
3. در صفحه مخزن روی **uploading an existing file** بزنید.
4. محتوای پوشه‌ی zip را (نه خود zip را) بکشید و رها کنید؛ حتما پوشه‌ی `.github` هم باشد.
   اگر `.github` آپلود نشد: **Add file ← Create new file** ، در نام فایل بنویسید
   `.github/workflows/build.yml` و محتوای همان فایل را paste کنید.
5. **Commit changes** را بزنید.
6. تب **Actions** ← اجرای «Build Android APK» ← صبر (۵ تا ۸ دقیقه) ← پایین صفحه، بخش **Artifacts** ← `dastyar-android-debug-*` را دانلود کنید.
7. zip دانلودشده را باز کنید و `app-debug.apk` را روی گوشی نصب کنید.

## راه‌اندازی روی گوشی (شیائومی / HyperOS)
1. نصب APK ← باز کردن برنامه.
2. تب خانه: «دانلود مدل صوتی» (یا انتخاب فایل zip مدل `vosk-model-small-fa-0.42`).
3. «مجوزهای اصلی» را بدهید.
4. **سرویس دسترسی‌پذیری**: اگر اندروید ۱۳+ گزینه را خاکستری کرد: «اطلاعات برنامه» ← منوی ⋮ بالا ← **Allow restricted settings** ← دوباره سرویس را روشن کنید.
5. «نمایش روی سایر برنامه‌ها»، «بدون محدودیت باتری»، «زنگ دقیق» را فعال کنید.
6. شیائومی: Settings ← Apps ← Manage apps ← دستیار ← **Autostart** روشن، و Battery saver روی **No restrictions**؛ برنامه را در Recents قفل کنید.
7. تب تنظیمات: کلید API را وارد کنید.
8. «شروع» را بزنید و بگویید: «کیارش» ... «با مهدی تماس بگیر».

## ساختار کد
| فایل | کار |
|---|---|
| `WakeService.kt` | گوش دادن آفلاین به کلمه بیدارباش (Vosk) |
| `Agent.kt` | مدیریت نشست، تصمیم‌گیری و حلقه ابزارها |
| `AiProvider.kt` | لایه مستقل ارائه‌دهنده هوش مصنوعی؛ در این نسخه آداپتور Anthropic فعال است |
| `Tools.kt` | همه قابلیت‌ها (تماس، پیامک، یادآور، تنظیمات، …) و تأیید کارهای حساس |
| `DastyarAccessibilityService.kt` | خواندن صفحه، کلیک، تایپ، تنظیم دسترسی‌ها از طریق UI |
| `Voice.kt` | صدا: TTS سیستم یا Azure (زن/مرد)، تشخیص گفتار |
| `MainActivity.kt` | رابط، تنظیمات، تم و پس‌زمینه |

افزودن قابلیت جدید: یک `t("name", ...)` در `Tools.definitions` و یک شاخه در `dispatch`.

## امنیت
- کلیدها در EncryptedSharedPreferences؛ ارتباط فقط با HTTPS و میزبان رسمی `api.anthropic.com`؛ تغییر مسیر به میزبان دلخواه فعلاً مسدود است؛ بدون backup.
- خروجی ابزارها به مدل به‌صورت «داده غیرقابل‌اعتماد» داده می‌شود؛ محتوای صفحه و پیامک نباید به‌عنوان دستور اجرا شود.
- نشست فرمان‌ها با Mutex سریالی شده تا دو فرمان هم‌زمان تاریخچه مکالمه را خراب نکنند.
- آداپتورهای ارائه‌دهنده از منطق Agent جدا شده‌اند؛ در حال حاضر فقط Anthropic پیاده‌سازی شده و افزودن سرویس‌دهنده‌های دیگر نیازمند آداپتور مستقل و آزمون امنیتی است.
- تأیید صوتی برای ارسال پیامک و کلیک روی دکمه‌های حذف/پرداخت/نصب (حالت ۱)؛ در تنظیمات قابل تغییر.
- برنامه‌های ممنوع (مدیر رمز، Authenticator) در تنظیمات؛ فیلد رمز هرگز خوانده یا پر نمی‌شود.
- گزارش محلی همه‌ی کارها در تب «گزارش».


## صدا و بیدارباش (مرحله چهارم)
- تشخیص کلمه بیدارباش به‌صورت آفلاین با مدل Vosk انجام می‌شود.
- برای جلوگیری از اجرای تکراری، نتایج تکراری تشخیص گفتار debounce می‌شوند.
- پس از خطای تشخیص‌گر، راه‌اندازی مجدد با تأخیر افزایشی انجام می‌شود.
- سرویس شنود باید با اعلان دائمی Foreground فعال باشد؛ این کار برای شفافیت استفاده از میکروفون است و ممکن است باتری مصرف کند.
- برای پایداری روی HyperOS، کاربر ممکن است لازم باشد Autostart و محدودیت باتری برنامه را در تنظیمات گوشی مدیریت کند. این تنظیمات را سیستم‌عامل کنترل می‌کند و برنامه نمی‌تواند آن‌ها را مخفیانه دور بزند.

## Stage 5: private memory foundation

The assistant now supports explicitly requested, user-confirmed local memories. Memories are stored using the app's encrypted preferences, are not automatically extracted from conversations, and can be listed or forgotten by exact text through assistant commands. Basic credential-like strings are rejected. The memory feature is local to this installation; it is not synced to GitHub or a web service.

The AI provider interface is vendor-neutral, but this release still has only the Anthropic adapter configured. Additional providers require separate adapters and integration tests.

## Build and security notes (stage 6–8)

- Runtime permissions are requested independently from the Home screen.
- Package visibility is limited to launcher activities; broad `QUERY_ALL_PACKAGES` access was removed.
- The boot reminder receiver is not exported.
- GitHub Actions builds the debug APK with JDK 17 and Gradle 8.9. Download the APK from the Artifacts section of a successful Actions run.
- Review `SECURITY_REVIEW_STAGE6.md` and `RELEASE_CHECKLIST.md` before installing or distributing a build.
- This source has not yet been verified by a successful Android Gradle build or on-device test.
