<p align="center">
  <img src="docs/images/logo.svg" width="112" alt="ReLink logo">
</p>
<h1 align="center">ReLink</h1>
<p align="center"><em>Two phones. One connection.</em></p>
<p align="center">A personal Android-to-iOS phone bridge for calls, SMS, one-time codes, contacts and call history.</p>
<p align="center">
  <img alt="GitHub Actions" src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white">
  <img alt="Beta 7" src="https://img.shields.io/badge/version-1.3.0--beta.7-70DDC4">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-blue"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white">
  <img alt="iOS 16+" src="https://img.shields.io/badge/iOS-16%2B-101216?logo=apple">
  <img alt="Development beta" src="https://img.shields.io/badge/status-development%20beta-orange">
</p>
<p align="center"><a href="#download">Download</a> · <a href="#setup">Setup</a> · <a href="#development">Development</a> · <a href="#compatibility">Compatibility</a> · <a href="#فارسی">فارسی</a></p>

> **Development preview.** ReLink is a private, non-commercial project derived from [Tether](https://github.com/amirwmr/tether). Beta 7 passed the Android and Xcode builds and automated tests. Play Protect, live pairing and audible two-way SIM calls still need verification on the target phones.

## Overview

Keep your SIM cards in an Android host and use an iPhone as your everyday companion. ReLink relays phone functions over Bluetooth LE or an authenticated connection on a trusted local Wi-Fi network. The Android host manages the SIM and telephony. The iOS companion uses CallKit and SwiftUI.

## Features

The following are implemented or inherited capabilities, not a guarantee of device compatibility:

- Incoming and outgoing calls, CallKit call controls, and device-dependent two-way audio bridging.
- SMS conversations, notifications, and convenient access to one-time codes (OTP).
- Contacts, favourites, call history, and dialling.
- QR and manual pairing (`tether1:` / `tether://` and `relink://`), connection status, and reconnection.
- Independent capture and injection diagnostics, Audio Helper startup reporting, and local audio fallback.
- English and Persian interfaces, including right-to-left layouts and light/dark themes.
- An optional Android companion (`android/client`, "ReLink Client") is available in the source tree for development.

Some inherited network tools and SMS write operations depend on Android permissions and device support. Automatic system permission grants were removed in Beta 4.

## Download

Use the matched packages from ReLink 1.3.0 Beta 7. Access to the private repository is required. The Beta 6 release remains available for reference.

| Asset | Purpose & Status |
| --- | --- |
| `ReLink-Android-1.3.0-Beta7.apk` | Signed Android host APK (`app.relink.android`) for the SIM phone |
| `ReLink-iOS-1.3.0-Beta7.ipa` | iPhone companion (`app.relink.ios`) for owner re-signing via SideStore or iLoader |
| `SHA256SUMS.txt` | SHA-256 checksums for the release files |
| Installation guide and hardware checklist | Setup and device validation steps |

The iOS IPA is built using GitHub Actions on macOS (`xcodebuild` Release `generic/platform=iOS`); a local Mac is not required. Because the IPA is unsigned (`TETHER_COMM_NOTIFICATIONS=NO`, no paid entitlements), **SideStore / iLoader re-signing is required** before installation. Free Apple account signatures generally require renewal every seven days.

The Android APK is signed with the owner's permanent release keystore (certificate SHA-256 `16fa11bc99a96625b4cfff190404cf4d7ebee59db2a437c89ff2f774eeb2bd6d`). Keep Google Play Protect enabled; if it flags an installation warning, stop and investigate rather than bypassing it.

## Setup

1. Install the signed Beta 7 APK on your SIM-holding Android phone. Re-sign and install the Beta 7 IPA using SideStore or iLoader.
2. Open ReLink on both phones, enable Bluetooth, and grant the requested permissions. Set ReLink as the Android default phone app when asked.
3. Pair using a fresh QR code or manual pairing code entry. Grant the iPhone Local Network access to try local Wi-Fi. Choose Automatic, Wi-Fi Preferred or Bluetooth Only in the iPhone connection settings.
4. For call audio bridging (`Android 11+`), connect the Android host to a trusted Wi-Fi network and complete one-time Wireless Debugging pairing for ReLink. (A USB ADB connection from a computer does not establish the app's own local ADB session.)
5. Check the live ADB connection in Setup, start Audio Helper, confirm Ready status, and run capture/injection self-tests.
6. Test incoming and outgoing calls with a consenting participant, verifying audio in both directions. Export sanitized diagnostics if a step fails.

Beta 7 uses the ReLink application identifiers introduced in Beta 6 (`app.relink.android` and `app.relink.ios`). Keep older Tether apps and their data until the new setup has been validated.

## Architecture

```mermaid
flowchart LR
  SIM[Android SIM phone] --> HOST[ReLink Android host]
  HOST <-->|Local TCP / authenticated helper| HELPER[ADB shell Audio Helper]
  HOST <-->|Encrypted Bluetooth LE or local Wi-Fi| IOS[ReLink iOS companion]
  HOST <-->|Shared protocol| CLIENT[Optional Android companion]
```

The active call-audio backend uses local TCP. Binder IPC and separate pipes have been explored with synthetic probes in development; they are not an active real-call transport in this release. The shared wire protocol (`wire 1`) uses authenticated P-256 key agreement and AES-GCM encryption. Android Kotlin (`android/core`) and Swift (`ios/TetherCore`) implementations are verified against shared test vectors and checked for drift in CI.

## Compatibility

| Target | Status |
| --- | --- |
| Samsung Galaxy A71 (`SM-A715F`), Android 13 / One UI 5.1 | Primary host target. Beta 7 device results pending. |
| iPhone 14 Pro Max | Primary companion target. Beta 7 installation and live pairing pending. |
| iOS 16 or later | Build deployment target (`MinimumOSVersion`), not a universal device guarantee |
| Other Android phones / versions (`Android 8+` host, `Android 11+` call audio) | Inherited platform support varies; not independently validated for ReLink |
| iPad / second Android companion | Physical device validation pending |
| Legacy Tether clients | Not a compatibility target; use matched ReLink builds |

Android call capture and injection depend on OS and vendor restrictions. An iPhone cannot serve as the SIM host for its native cellular calls and SMS. MMS, RCS, and video calls are outside current scope.

## Development status

**Beta 7 — `1.3.0-beta.7`** (build `1030007`) · Shared source commit `private archive`.

| Validation | Status / Result |
| --- | --- |
| Android automated tests (`:core`, `:app`, `:helper`, `:client`) | 407 passed, 0 failed, 0 skipped |
| Swift Core tests (`ios/TetherCore`) | 227 passed (192 XCTest + 35 Swift Testing) |
| iOS Simulator tests (`TetherTests`) | 83 passed |
| Android release build and `lintRelease` | Passed. Lint reported 55 warnings and zero errors. |
| Full Xcode release build & `check-ios-artifact.py` | Passed (unsigned SideStore/iLoader IPA) |
| Physical installation & Google Play Protect check | Pending hardware validation |
| SideStore / iLoader re-signing & iPhone install | Pending hardware validation |
| Live BLE pairing / Wireless ADB / Audio Helper `HELLO` | Pending hardware validation |
| Real two-way cellular call audio | Pending hardware validation |

See the Beta 7 build and test run and verify downloads against SHA256SUMS.txt.

## Development

```bash
git clone https://github.com/heat-core/relink.git
cd relink
```

Access to the private repository is required. Android uses JDK 17, Gradle, and the Android SDK (`compileSdk` / `targetSdk` 36). On Windows, from the `android` directory:

```powershell
.\gradlew.bat :app:assembleRelease
```

Release signing uses the owner's existing local PKCS#12 keystore outside Git. Never commit keys, passwords, or local signing files. See [build documentation](docs/BUILD.md) for local configuration. Use the repository's GitHub Actions macOS workflow (`Beta release builds`) for iOS, or build the Xcode project on a Mac. Always build both binaries from the same source commit and verify `tools/check-protocol.py` before publishing.

| Component | Location |
| --- | --- |
| Android host | `android/app` |
| Android companion | `android/client` |
| Android shared core & Audio Helper | `android/core`, `android/helper` |
| iOS project & Swift Core package | `ios/`, `ios/TetherCore` |
| Protocol specification & SDK guides | [protocol/PROTOCOL.md](protocol/PROTOCOL.md), [Swift SDK](docs/SDK-swift.md), [Kotlin SDK](docs/SDK-kotlin.md) |
| Brand assets (`A4` SVG masters) | `docs/images/logo.svg`, `docs/images/brand/` |

Application identifiers: `app.relink.android` and `app.relink.ios`. Internal package/target names (`app.tether.*`, `TetherCore`, `Tether.xcodeproj`) and BLE/wire UUIDs are intentionally kept stable for protocol compatibility.

## Documentation

- [Installation](docs/INSTALL.md) · [Setup](docs/SETUP.md) · [Usage](docs/USAGE.md) · [FAQ](docs/FAQ.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md) · [Architecture](docs/ARCHITECTURE.md) · [Testing](docs/TESTING.md)
- [Android Client](docs/ANDROID-CLIENT.md) · [Swift SDK](docs/SDK-swift.md) · [Kotlin SDK](docs/SDK-kotlin.md) · [Extending](docs/EXTENDING.md)
- [Build](docs/BUILD.md) · [Releasing](docs/RELEASING.md) · [Roadmap](docs/ROADMAP.md) · [Changelog](CHANGELOG.md)
- [Security](SECURITY.md) · [License](LICENSE) · [Third-Party Notices](NOTICE)

See the Beta 7 notes for current ReLink installation and signing details.

## Security and privacy

ReLink encrypts call, message and contact data over authenticated Bluetooth or Wi-Fi links using `P-256` and `AES-GCM`. The Android Audio Helper uses shell access through owner-authorized wireless ADB. ADB TLS trust remains under review. Review problem reports before sharing and test calls with a consenting participant.

## فارسی

<div dir="rtl" lang="fa">

**ReLink** (*دو گوشی. یک اتصال.*) برای انتقال تماس، پیامک، کد یک‌بارمصرف، مخاطبین و سابقهٔ تماس از گوشی اندرویدی سیم‌کارت‌دار به آیفون از طریق بلوتوث یا Wi-Fi محلی رمزنگاری‌شده طراحی شده است. نسخهٔ بتا ۷ برای اندروید و آیفون از یک commit ساخته شده و آزمون‌های نرم‌افزاری را گذرانده است. برای نصب روی آیفون، IPA را با `SideStore` یا `iLoader` دوباره امضا کنید. نصب، جفت‌سازی و صدای تماس روی `Galaxy A71` و `iPhone 14 Pro Max` هنوز به آزمون واقعی نیاز دارند. `Google Play Protect` را روشن نگه دارید.

صفحهٔ انتشار بتا ۷ · [راهنمای فارسی پروژهٔ اصلی](docs/fa/README.md)

</div>

## License and attribution

ReLink is an independent personal derivative of [Tether by amirwmr](https://github.com/amirwmr/tether), not an official upstream release. Original copyright notices and the MIT License remain intact. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for licensing and third-party attribution. ReLink branding does not imply ownership or endorsement of the original project or its dependencies.
