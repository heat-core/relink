---
title: راهنمای جامع فارسی ReLink
lang: fa
dir: rtl
---

<div dir="rtl" lang="fa" markdown="1">

# راهنمای جامع فارسی ReLink

در این نسخهٔ توسعه، راه‌اندازی جدید دستیار صدا از داخل برنامه و قابلیت Share VPN تا تأیید بررسی امنیتی متوقف‌اند. برای تماس عادی می‌توانید از صدای خود گوشی اندرویدی استفاده کنید.

<p align="center"><img src="../images/logo.svg" width="96" alt="لوگوی A4 برنامه ReLink"></p>

**دو گوشی. یک اتصال.** — <span dir="ltr">`Two phones. One connection.`</span>

**ReLink** یک انشعاب مستقل با پروانهٔ **MIT** بر پایهٔ پروژهٔ [Tether](https://github.com/amirwmr/tether) است که گوشی اندرویدی دارای سیم‌کارت شما را به یک «پایگاه ارتباطی امن» برای آیفون تبدیل می‌کند تا بدون نیاز به سرور یا حساب کاربری، تماس‌های شبکهٔ موبایل، پیامک‌ها، کدهای یک‌بارمصرف، مخاطبین، تاریخچهٔ تماس و ابزارهای کنترل شبکه را روی آیفون خود در اختیار داشته باشید.

- نسخهٔ توسعهٔ فعلی: <span dir="ltr">`1.3.0-beta.8`</span> (نسخهٔ پروتکل سیمی: <span dir="ltr">`1`</span>)
- شناسه‌های رسمی برنامه: اندروید <span dir="ltr">`app.relink.android`</span> و آیفون <span dir="ltr">`app.relink.ios`</span>
- پیوندهای کمکی: [راهنمای نصب و ساخت](https://github.com/heat-core/relink/blob/main/docs/RELINK_INSTALL.md) · [ماتریس سازگاری](../COMPATIBILITY.md) · [پرسش‌های رایج](faq.md) · [سیاست امنیت](../../SECURITY.md)

---

## ۱. وضعیت واقعی و صادقانهٔ قابلیت‌ها (`1.3.0-beta.8`)

در مستندات ReLink، نتایج آزمایش آفلاین هرگز جایگزین آزمون زندهٔ سخت‌افزاری معرفی نمی‌شوند. وضعیت فعلی روی جفت سخت‌افزاری مرجع (<span dir="ltr">`Galaxy A71 SM-A715F · Android 13 / One UI 5.1`</span> + <span dir="ltr">`iPhone 14 Pro Max`</span> + <span dir="ltr">`AirPods`</span>):

| بخش | وضعیت تأییدشده در سخت‌افزار | وضعیت مهندسی و موارد در انتظار آزمون مجدد |
| --- | --- | --- |
| **نصب اندروید و Play Protect** | در نسخهٔ <span dir="ltr">`Beta 6`</span> اسکن Play Protect انجام و نصب مجاز شد. | نسخهٔ `Beta 7/8` نیازمند بررسی Play Protect و تأیید کاربر پیش از نصب است. |
| **نصب آیفون و هویت بصری A4** | نصب <span dir="ltr">`Beta 6`</span>، لوگو و طراحی آیفون تأیید شدند. | ساخت جدید آیفون روی مک و Xcode لازم است (در محیط ویندوز انجام نمی‌شود). |
| **جفت‌سازی QR، پیامک و برقراری تماس خروجی** | جفت‌سازی امن، همگام‌سازی پیامک و شروع تماس خروجی در تست میدانی موفق بوده‌اند. | آزمون هم‌زمان جابه‌جایی شبکه و پایداری در تماس‌های پیاپی در دست بررسی است. |
| **Wireless ADB و دستیار صدا (`Audio Helper`)** | در `Beta 6` بازخورد UI گمراه‌کننده بود، هرچند بررسی فقط-خواندنی زنده بودن <span dir="ltr">`Helper`</span> را حتی پس از خاموش‌شدن Wi-Fi تأیید کرد. | تفکیک وضعیت زندهٔ Helper از ADB، ارسال امن توکن با `stdin` و محدودسازی میزبان محلی پیاده‌سازی شده و در انتظار آزمون روی <span dir="ltr">`A71`</span> است. |
| **صدای دوطرفهٔ تماس و `AirPods`** | در `Beta 6` تداخل باند `2.4 GHz` با Hotspot و مسابقه در مسیر `AirPods` باعث اختلال Uplink شد (`uplinkSpeechFrames = 0`). | اصلاحات `Beta 7/8`، تنظیم Hotspot روی `5 GHz` و محافظ‌های Qualcomm / CallKit از <span dir="ltr">`Tether 1.2.5`</span> پیاده شده‌اند و باید در تماس سیم‌کارتی واقعی هر دو جهت (رفت و برگشت) دوباره آزموده شوند. |

---

## ۲. معماری و حالت‌های انتقال (`Adaptive Hybrid v2`)

ReLink نشست رمزنگاری‌شدهٔ سرتاسری (<span dir="ltr">`P-256 ECDH`</span> + <span dir="ltr">`AES-256-GCM`</span>) را از لایهٔ فیزیکی جدا می‌کند و سه حالت در اختیار کاربر می‌گذارد:

1. **خودکار (<span dir="ltr">`Automatic`</span> — پیش‌فرض):** ابتدا با **بلوتوث کم‌مصرف (<span dir="ltr">`BLE L2CAP`</span> / <span dir="ltr">`GATT`</span>)** وصل می‌شود و در صورت اتصال هر دو گوشی به Hotspot گوشی یا شبکهٔ محلی مطمئن، ترافیک پرحجم همگام‌سازی و صوت را روی سوکت محلی رمزنگاری‌شده جابه‌جا می‌کند.
2. **ترجیح Wi-Fi (<span dir="ltr">`Wi-Fi Preferred`</span>):** استفاده از شبکهٔ محلی یا Hotspot در صورت دسترسی، با بازگشت خودکار به بلوتوث.
3. **فقط بلوتوث (<span dir="ltr">`Bluetooth Only`</span>):** کارکرد کاملاً مستقل و بدون نیاز به Wi-Fi یا Hotspot.

> [!CAUTION]
> حالت شبکهٔ محلی و Wi-Fi فقط برای **Hotspot گوشی اندرویدی خودتان یا شبکهٔ محلی کاملاً مطمئن** طراحی شده است. در شبکه‌های عمومی و ناامن از حالت **فقط بلوتوث** استفاده کنید.

---

## ۳. نکات امنیتی، ADB و نگهداری کلیدها

- **روشن‌ماندن <span dir="ltr">`Google Play Protect`</span>:** هنگام نصب هر بیلد، <span dir="ltr">`Play Protect`</span> را فعال نگه دارید. اگر سیستم نصب را مسدود کرد، بلافاصله نصب را متوقف کنید.
- **کلید دائمی امضا:** فایل <span dir="ltr">`keystore`</span> اندروید فقط به‌صورت آفلاین روی سیستم امن مالک نگهداری می‌شود و هرگز وارد گیت یا مخزن عمومی نمی‌شود. بیلد آیفون به‌شکل امضانشده تولید می‌شود تا با حساب اپل خودتان امضا شود.
- **پشتیبان‌گیری ابری اندروید:** فایل مانیفست اندروید علاوه بر `allowBackup="false"`، با قواعد صریح `dataExtractionRules` و `fullBackupContent` از خروج کلیدهای جفت‌سازی، ADB و توکن دستیار جلوگیری می‌کند.
- **محدودیت باز در <span dir="ltr">`LibADB TLS`</span>:** لایهٔ <span dir="ltr">`libadb-android 3.1.1`</span> به‌طور موروثی گواهی سرور TLS را پین نمی‌کند (`TrustAllX509TrustManager`). ReLink میزبان‌های کشف‌شده و متصل را به آدرس‌های غیرعمومی محلی محدود کرده است، اما پین‌کردن گواهی سرور همچنان یک مسئلهٔ امنیتی باز <span dir="ltr">`P0`</span> است ([SECURITY.md](../../SECURITY.md)).
- **پایداری `Audio Helper` بدون Wi-Fi:** در اندروید ۱۱ به بالا، <span dir="ltr">`Wireless debugging`</span> فقط هنگام راه‌اندازی اولیهٔ Helper به اتصال Wi-Fi نیاز دارد. پس از اجرا، فرایند مستقل <span dir="ltr">`Helper`</span> (تحت کاربر <span dir="ltr">`shell`</span> روی پورت محلی <span dir="ltr">`127.0.0.1`</span>) حتی با خاموش‌شدن Wi-Fi یا روشن‌شدن Hotspot ۵ گیگاهرتز **زنده می‌ماند** و تنها پس از راه‌اندازی مجدد گوشی دوباره نیاز به شروع دارد.

---

## ۴. ساخت از سورس (در نبود فایل‌های آمادهٔ عمومی)

در حال حاضر روی مخزن عمومی `heat-core/relink` باینری آماده منتشر نشده است. برای ساخت هماهنگ هر دو برنامه از یک کامیت واحد (<span dir="ltr">`VERSION`</span> مشترک):

- **اندروید (JDK 17 + Android SDK 36):** از دستورهای زیر در پوشهٔ `android` برای اجرای آزمون‌ها و ساخت APK امضانشده استفاده کنید و سپس با کلید دائمی خودتان امضا و بررسی کنید ([BUILD.md](../BUILD.md)):
  - `./gradlew --console=plain :core:test :app:testDebugUnitTest :helper:testDebugUnitTest :client:testDebugUnitTest :app:lintRelease :app:assembleRelease`
  - برای امضای محلی: `apksigner verify --verbose --print-certs <signed.apk>`
- **آیفون (macOS + Xcode 16+ و `XcodeGen`):** پروژه را با `xcodegen generate` بسازید و نسخهٔ `Release` امضانشده را با `CODE_SIGNING_ALLOWED=NO` تولید کنید تا در ابزار Sideload یا Xcode با حساب اپل خودتان امضا شود ([RELINK_INSTALL.md](../RELINK_INSTALL.md)).

---

## ۵. ارسال گزارش و حریم خصوصی

پیش از بازکردن Issue در [heat-core/relink/issues](https://github.com/heat-core/relink/issues):

1. از هر دو برنامه گزارش مشکل پالایش‌شده بسازید (**Help › Send a problem report** در اندروید و **Link › Send a problem report** در آیفون) و پیش از انتشار دستی نیز آن را بررسی کنید.
2. **هرگز** شماره تلفن واقعی، نام مخاطب، متن پیامک، کد یک‌بارمصرف، آدرس IP یا MAC کامل، سریال دستگاه یا کلید و توکن را منتشر نکنید. از شماره‌های آزمایشی `+10000000001…9` یا `<redacted>` استفاده کنید.
3. مسائل امنیتی را فقط به‌صورت خصوصی از مسیر [گزارش خصوصی امنیتی](https://github.com/heat-core/relink/security/advisories/new) ارسال کنید.

---

## ۶. پروانه و یادکرد بالادست

ReLink تحت پروانهٔ [MIT License](../../LICENSE) منتشر می‌شود و حقوق و یادکرد نویسندگان اصلی پروژهٔ [Tether](https://github.com/amirwmr/tether) و اجزای متن‌باز شخص ثالث ([NOTICE](../../NOTICE) · [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) · [PROVENANCE.md](../../PROVENANCE.md)) را به‌طور کامل حفظ می‌کند.

</div>
