# معماری فنی دستیار

## جریان اجرای فرمان

1. رابط کاربری یا سرویس صوتی فرمان را به `Agent.handleText` می‌دهد.
2. `Agent` نشست را با `Mutex` سریالی می‌کند و تاریخچه محدود مکالمه را مدیریت می‌کند.
3. درخواست مدل از طریق قرارداد `AiProvider` ارسال می‌شود؛ `Agent` به جزئیات HTTP سرویس‌دهنده وابسته نیست.
4. پاسخ ابزارها از طریق `Tools.execute` اجرا می‌شوند.
5. خروجی ابزارها به‌عنوان داده غیرقابل‌اعتماد به مدل بازگردانده می‌شوند.
6. عملیات حساس از دروازه تأیید `Tools.allowed` عبور می‌کنند.

## ارائه‌دهندگان هوش مصنوعی

- قرارداد: `AiProvider`
- انتخاب ارائه‌دهنده: `AiProviders.resolve`
- پیاده‌سازی فعلی: `AnthropicMessagesProvider`
- میزبان مجاز فعلی: `api.anthropic.com` روی HTTPS و پورت استاندارد 443
- تغییر میزبان دلخواه و دنبال‌کردن redirect غیرفعال است تا کلید API به میزبان ناشناس ارسال نشود.

برای افزودن ارائه‌دهنده جدید باید یک پیاده‌سازی مستقل، اعتبارسنجی endpoint، مدیریت خطا و آزمون‌های اختصاصی اضافه شود. صرف افزودن نام ارائه‌دهنده کافی نیست.

## امنیت

- کلید API در `EncryptedSharedPreferences` نگهداری می‌شود.
- خروجی ابزارها در پیام ابزار با برچسب `untrusted_data` برگردانده می‌شود.
- تاریخچه Agent در حافظه و با سقف تعداد پیام نگهداری می‌شود.
- تأیید عملیات همچنان در لایه ابزار اعمال می‌شود؛ هر ابزار جدید باید طبقه‌بندی خواندنی/تغییردهنده و حساسیت آن را مشخص کند.

## محدودیت‌های فعلی

- تنها آداپتور Anthropic فعال است.
- تست کامپایل و تست دستگاه در این محیط انجام نشده است؛ وجود Android SDK و Gradle لازم است.
- سازگاری ابزارهای Accessibility و سرویس میکروفون باید روی دستگاه واقعی بررسی شود.

## Stage 3: phone-action safety

Phone actions remain implemented in `Tools`, but sensitive operations now consistently pass through the confirmation gate in balanced mode. Calls and quick-settings changes are treated as sensitive; Accessibility text entry also requires confirmation. Contact resolution refuses to choose silently when a single name maps to multiple distinct phone numbers. SMS input is bounded and the complete body is spoken for approval before dispatch. The return value deliberately distinguishes submission to Android's SMS service from confirmed delivery.

Audit logging omits personal identifiers and content for contact, call, SMS, screen-reading, and text-entry tools. The blocked-package preference is checked before launching an app by package name. URL intents accept only valid HTTP(S) URLs without embedded credentials.
