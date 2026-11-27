# Research Note: Android Cellular Audio, Samsung/Qualcomm HALs, Helper Lifecycle & ADB Limits

Topics covered: **(1) Android call-audio capture & injection**, **(2) Samsung/Qualcomm vendor behavior**, **(3) Shell Helper lifecycle & IPC**, and **(4) Wireless ADB bootstrap limits**.

---

## 1. Android Cellular Call-Audio Interception (`CONFIRMED` Platform Ceiling)

On unrooted stock Android (API 29–36), regular untrusted application UIDs (`untrusted_app`) cannot read cellular downlink or inject uplink speech because modem voice streams bypass `AudioFlinger` directly between baseband DSP and Audio HAL (`AudioReach` / `PAL_STREAM_VOICE_CALL` on Qualcomm; `abox`/SLSI on Exynos).

Permissions capable of crossing this boundary (`CALL_AUDIO_INTERCEPTION`, `CAPTURE_AUDIO_OUTPUT`, `MODIFY_PHONE_STATE`, `MODIFY_AUDIO_ROUTING`) are restricted by AOSP to **`signature|privileged|role`** components — which on non-rooted user devices leaves only `com.android.shell` (`uid 2000`). Verified non-viable alternatives:

- **`DeviceOwner` (MDM):** Does not grant call-audio capture/injection privileges (`CONFIRMED`).
- **`CompanionDeviceManager`:** Grants UX association conveniences only, never telephony audio permissions (`CONFIRMED`).

ReLink (`android/helper/src/main/java/app/tether/helper/`) therefore launches `HelperMain` via `app_process` under `uid 2000` and probes a ranked strategy ladder:

- **Capture ladder:** `intercept` (Android 13+ `AudioPlaybackCapture` / `CALL_AUDIO_INTERCEPTION` semantics) -> `voice_downlink` -> `telephony_rx` (gated strictly in `Beta 9` by `AudioDeviceInfo.TYPE_TELEPHONY` to prevent accidentally routing `BUILTIN_MIC` as downlink) -> `voice_call` (mixed Tx+Rx fallback).
- **Injection ladder:** `intercept` -> `telephony_tx` -> `policy_mix`.

---

## 2. Samsung / Qualcomm Routing Constraints (`CONFIRMED` Code + `STRONG EVIDENCE` OEM)

- **Qualcomm `/dev/snd/pcmC0D15c` wedge guard:** Upstream `1.2.4` / `1.2.5` (retained in ReLink) documented that writing Samsung call mixer keys or forcing redirect modes on newer Samsung Qualcomm firmwares (e.g., Galaxy A73 / Android 16) could wedge the voice PCM node silently until reboot (`CallAudio.kt`). To avoid wedging, ReLink skips automatic Samsung redirect modes, vendor mixer key mutation unless previously written by Helper, and Qualcomm host mic mute when unsafe.
- **Direct UX consequence (`CONFIRMED` in `Beta 9`):** Because those isolation guards remain active, the Android host microphone and speaker stayed live during Beta 9 bridged calls, preventing exclusive iPhone audio isolation and creating acoustic echo / privacy leaks in a user's pocket.
- **Screen-lock audio policy kill (`STRONG EVIDENCE`):** Samsung One UI aggressively alters audio focus and background execution on screen lock, coinciding with reported drops or speaker bleed during bridged calls.
- **Permanent 6-second VoLTE block hazard (Fixed in `Beta 9`, `CONFIRMED`):** Early silence on VoLTE call connect could trigger permanent helper audio lockouts across same-version beta builds (`versionCode` key); moving from single-call lockouts to per-call fallback and `lastUpdateTime` keying addressed that lock-in mechanism, yet **Beta 10** subsequently suffered an unresolved full bidirectional/ringback physical regression despite passing offline unit tests.

---

## 3. Shell Helper Lifecycle & Inter-Process Communication (`CONFIRMED`)

- **Privilege split & secret handoff:** `HelperBridge` passes a per-session secret over `stdin` into a `umask 077` temp file immediately unlinked on read (`HelperLock.readAndDeleteToken`) so secrets never appear in `/proc/<pid>/cmdline`. Control and 16 kHz PCM frames traverse loopback `127.0.0.1` protected by mutual HMAC-SHA256 challenge-response (`HelperAuth`).
- **Remaining IPC & security gaps (`KNOWN_LIMITATIONS.md`):**
  1. `HelperMain`'s accept loop still spawns session threads / touches waiting queues prior to completing HMAC verification (bounded local DoS vector).
  2. Loopback TCP carries context-switch and jitter overhead (`UplinkBuffer` + pure-Java `Concentus` 20 ms framing), and debug-only `PipeProbeProvider` / `ShellPipeProbe` already demonstrated that `uid 2000` can exchange `ParcelFileDescriptor` pipes (`capture`/`inject`) and `IBinder.linkToDeath` across SELinux via a `DUMP`-protected `ContentProvider` — an unpromoted optimization candidate for future forks.

---

## 4. Wireless ADB Bootstrap Limits (`CONFIRMED`)

1. **Android toggles Wireless Debugging off when Wi-Fi disconnects (`adb_wifi_enabled = 0`):** Even when the detached `uid 2000` Helper remains healthy serving `127.0.0.1`, diagnostic UIs must treat ADB discovery (`_adb-tls-connect._tcp`) and live Helper HMAC health (`127.0.0.1`) as separate state machines (`HelperHealth`).
2. **Legacy `libadb-android 3.1.1` TLS debt:** ReLink's Android manifest excludes automatic ADB Helper bootstrap (`ADB_HELPER_BOOTSTRAP_ALLOWED = false`) because `libadb-android`'s `SslUtils` uses `TrustAllX509TrustManager` with no server certificate verification against `adbd`. Supervised USB startup (`tools/start-helper.ps1` / `start-helper.sh`) remained a manual diagnostic workaround only.
3. **Host address boundary:** `AdbAddressPolicy` validates local host ownership before attempting TLS connection so malformed or remote subnet mDNS advertisements are rejected.
4. **Pending replacement architecture (`UNVERIFIED` long-term path):** Replacing `libadb-android` (`SslUtils` + `LGPL-3.0` `spake2-java`) with a small Kotlin TLS 1.3 ADB client (`API 29+`) performing SPKI Trust-On-First-Use (TOFU) pinning during pairing and wrapping `adb_key.pk8` in `AndroidKeyStore`.
