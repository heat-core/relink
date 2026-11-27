# Known Limitations — ReLink (`1.3.0-beta.10` Final Archive)

This document catalogs verified architectural, functional, security, and licensing limitations present in the archived ReLink codebase.

---

## 1. Cellular Audio & Hardware Routing Limitations

- **Beta 10 Audio Regression:** In physical testing after automated suite passes, calls could be placed from iPhone, but bidirectional voice audio and ringback failed completely. Root causes on physical carrier HALs were not resolved before discontinuation.
- **Host Microphone & Earpiece Isolation (Samsung Qualcomm & OEM HALs):** To avoid wedging `/dev/snd/pcmC0D15c` on newer Samsung Qualcomm firmwares (inherited `1.2.4` / `1.2.5` safeguards), ReLink disables vendor call keys, redirect modes, and host mic muting on affected Samsung devices. Consequently, even when downlink/uplink bridging works (`Beta 9`), the Android phone's own microphone and earpiece can remain active simultaneously.
- **One UI Screen-Lock Audio Drops:** Upstream issues and testing indicate Samsung One UI power/audio policy changes during screen lock can silence `CALL_AUDIO_INTERCEPTION` streams or return audio to the handset speaker.
- **Latency & RF Noise:** Loopback TCP IPC (`HelperMain` ↔ app) plus 20 ms pure-Java `Concentus` software encoding over 2.4 GHz Bluetooth coexistence (AirPods Classic HFP alongside BLE) causes intermittent bursts, jitter, and choppy audio.
- **No Cloud / No VoIP PushKit:** Because ReLink avoids cloud servers, suspended iOS wakeups depend strictly on CoreBluetooth GATT `WAKE_CHAR` notifications and state restoration. Any RF gap or transport teardown can stall incoming call delivery.

---

## 2. iOS CallKit & AirPods HFP Limitations

- **Double-AEC Silent Uplink on AirPods HFP:** Combining iOS CallKit `.voiceChat` audio mode, `AVAudioEngine` `VoiceProcessingIO`, and external AirPods Bluetooth HFP hardware DSP can silently yield all-zero uplink frames without throwing engine startup errors.
- **Delayed Hangup / State Lag:** Carrier Telecom teardown and ACK propagation can lag behind UI interactions, leaving delayed call termination in some failure paths.

---

## 3. Wireless ADB & Shell Helper Security / UX Debt

- **Fail-Closed Automated ADB Bootstrap:** Because no production-grade `adbd` TLS server-identity pinning exists yet, automated Wireless ADB Helper startup (`ADB_HELPER_BOOTSTRAP_ALLOWED = false`) remains disabled in `Beta 9`/`Beta 10`; supervised USB Helper startup (`tools/start-helper.ps1` / `start-helper.sh`) is manual/diagnostic only.
- **Legacy `TrustAllX509TrustManager` in `libadb-android`:** Upstream `libadb-android 3.1.1` (`SslUtils`) accepts any TLS certificate (`TrustAllX509TrustManager`). A native TLS client with SPKI Trust-On-First-Use (TOFU) pinning on pairing is required before shipping unattended Wireless ADB authentication.
- **Helper IPC & Key Storage:** Helper loopback socket thread spawning before HMAC completion, plaintext local preference/key files (`allowBackup=false` mitigated backups only), and `ShareProxy` internal network (`isSiteLocalAddress`) SSRF hardening gaps remain unaddressed for production deployment.
- **Play Protect / Harmful-App Warning:** A harmful-app warning was reported during final testing (cause not independently isolated; candidates include self-signed/sideloaded APK trust signals, Wireless ADB/`TrustAllX509TrustManager` patterns, dynamic shell execution patterns, or local permission combinations).
- **Wi-Fi Toggle Resets Android Wireless Debugging:** Toggling Wi-Fi off disables `adb_wifi_enabled` in Android Settings even while a detached `uid 2000` Helper remains healthy on `127.0.0.1`.

---

## 4. Hybrid Transport Coupling

- **Control/Media Carrier Coupling (`Beta 7–10`):** `LanLink` and `TetherLink` operate as sibling transport pipes rather than a unified logical `PeerSession` coordinator. Switching paths or resetting sessions risks tearing down BLE `WAKE_CHAR` background wakeups or flushing queued frames if interrupted mid-flight.

---

## 5. OTP Extraction Tradeoff

- **Contextual Precision over Bare-Number Recall:** Following `Beta 10` (`84164d5d`), both iOS (`Util.swift`) and Android (`VerificationCode.kt`) require explicit verification keywords and reject tracking/promo/CVV/URL false positives. Messages containing bare 4–8 digit codes without recognized English or Persian OTP keywords are intentionally ignored.

---

## 6. Licensing & Distribution Constraints

- **`LGPL-3.0` Transitive Dependency (`spake2-java:android`):** Bundled via `libadb-android 3.1.1`, which links native `libspake2.so` under `LGPL-3.0`. Any third party distributing compiled APKs must obey `LGPL-3.0` §4 (notice preservation, corresponding source, relinking capability). This archive publishes **source only**.
- **`org.json:json` (JSON License):** Used only in pure JVM `android/core` builds (excluded from Android APKs, which use Android's platform `org.json`). Downstream JVM consumers requiring strict OSI/FSF-only licenses must account for the JSON License (`"Good, not Evil"` clause).
