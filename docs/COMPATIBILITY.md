# ReLink Compatibility

ReLink targets Android 8.0+ (API 26+) on the SIM host and iOS 16+ on the companion iPhone, with Android 11+ (Android 13+ recommended) required for call-audio interception.

> [!IMPORTANT]
> Only observed physical device runs on the target pair count as verified hardware evidence. Automated unit tests, release lint, simulator builds and upstream device reports (`Galaxy A54`, `Galaxy A73`, `Redmi Note 8`) do not substitute for physical verification on your specific hardware and firmware.

## Target Device Pair (`Galaxy A71 SM-A715F` + `iPhone 14 Pro Max` + `Apple AirPods`)

| Area | Latest Observed Status | Pending Retest on `1.3.0-beta.8` |
| --- | --- | --- |
| **APK installation & Play Protect** | **Observed (Beta 6):** Play Protect scanned, cleared and installed `app.relink.android` (`1030006`). | Beta 7/8 build Play Protect review & installation approval (`BLOCKED BY SECURITY REVIEW` until reviewed). |
| **iPhone sideloading & A4 UI** | **Observed (Beta 6):** Sideloading, branding and iOS visual design approved. | Fresh macOS Xcode build (`BLOCKED BY XCODE` in offline Windows checkout). |
| **QR / manual pairing & SMS sync** | **Observed (Beta 6 & Beta 7):** Pairing and SMS synchronization succeeded. | Contacts/recents/SMS stress verification across transport handovers. |
| **Outgoing call initiation** | **Observed (Beta 6):** Outgoing carrier dial initiated via Telecom. | Incoming & outgoing call continuity after CallKit upstream `1.2.5` patch. |
| **Wireless ADB & Audio Helper** | **Degraded UX on Beta 6**; read-only ADB confirmed Helper running on `127.0.0.1` with `capture=intercept`, `inject=intercept` even with Wi-Fi off. | Live Verification of Beta 8 `Verify ADB` shell probe, detached `stdin` token bootstrap and persistent Helper status. |
| **Cellular downlink audio (`caller -> iPhone`)** | **Partial (Beta 6 telemetry):** Android captured 451 frames (`0` dropped) in `bdad1a86`, human listening check unconfirmed. | Live human listening check on incoming & outgoing carrier SIM calls. |
| **Cellular uplink audio (`iPhone -> caller`)** | **Degraded (Beta 6):** High BLE 2.4 GHz jitter (`737 ms` gaps) and AirPods route race produced `uplinkSpeechFrames=0`. | Verification with 5 GHz hotspot + Adaptive Hybrid + upstream `1.2.5` Qualcomm `interceptInjectDead`/mic-mute safeguards. |
| **AirPods / Bluetooth headset HFP** | **Failed (Beta 6):** Call audio routing malfunctioned with AirPods connected. | Verification of Beta 7 route/format audio-graph rebuild + HFP stability. |
| **BLE vs Local Wi-Fi failover** | **Failed (Beta 6 BLE only):** Repeated BLE drops every `7–223 s` on 2.4 GHz hotspot (`HCI error 59 / 19`). | `Automatic`, `Wi-Fi Preferred` and `Bluetooth Only` failover on 5 GHz hotspot & standalone cellular data. |

## General OS Capabilities & Upstream Reference Notes

- **Android version boundaries:** Call-audio interception needs Android 11+ (`SDK >= 30`), with redirect/interception features focused on Android 13+ (`SDK >= 33`). See [Android versions](devices/android-versions.md).
- **Samsung Qualcomm notes (`Galaxy A71 SM-A715F` — Snapdragon 730):** Inherits upstream Tether `1.2.4` / `1.2.5` safeguards that avoid Samsung Text Call vendor keys and Telecom mic mute on Qualcomm HALs where muting could silence uplink audio.
- **Upstream Tether hardware reports:** Notes in [galaxy-a54.md](devices/galaxy-a54.md) (`Galaxy A54 Exynos 1380`) and upstream `#13` (`Galaxy A73`) reflect upstream `Tether` builds, **not** proof of ReLink behavior on `Galaxy A71`.
