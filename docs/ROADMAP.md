# Roadmap

## ReLink Priorities

### P0 — Security, ADB/Helper Reliability & Core Call Audio

1. **Google Play Protect verification:** Submit/review the target release APK (`app.relink.android`, exact SHA-256 and permanent signer fingerprint) through official review before broader distribution.
2. **LibADB TLS server identity:** Evaluate and design a supported server-identity / trust verification strategy for Wireless ADB TLS (`libadb-android` currently uses `TrustAllX509TrustManager`) without breaking OS pairing compatibility.
3. **Physical A71 + iPhone 14 Pro Max validation (`1.3.0-beta.8`):**
   - Verify live Wireless ADB shell verification, optional `TCP 5555` isolated checks, and detached `stdin`-token Helper startup/reattachment on physical Android 13.
   - Confirm audible **downlink and uplink** speech on real incoming and outgoing carrier SIM calls (including Qualcomm `interceptInjectDead` and mic-mute safeguards integrated from upstream Tether `1.2.5`).
   - Validate CallKit provider rebuild/recovery under disconnect/reconnect and AirPods HFP audio routing.

### P1 — Transport Stability & User Experience

1. Validate **Adaptive Hybrid v2** transitions (`Automatic`, `Wi-Fi Preferred`, `Bluetooth Only`) on 5 GHz Android hotspot vs standalone cellular data without dropping active sessions or corrupting SMS/contact sync.
2. Maintain and polish the Material 3 Android interface and English/Persian RTL consistency across `MainActivity`, setup steps and call-audio diagnostics.

### P2 — Companion App Enhancements (Backlog)

1. **OTP extraction precision:** Expand synthetic Persian & English test suites and reject false positives inside URLs, phone numbers, monetary amounts, promo codes and shortcodes.
2. **iOS Keypad ergonomics:** Adjust keypad button sizing and spacing on larger displays (`iPhone 14 Pro Max`) for comfortable one-handed reach.

---

## Inherited Upstream Directions

ReLink tracks upstream [Tether](https://github.com/amirwmr/tether) fixes selectively where relevant (`docs/COMPATIBILITY.md`). Open ReLink feature ideas at [heat-core/relink issues](https://github.com/heat-core/relink/issues/new?template=feature_request.yml).

- Broader Android vendor compatibility reports (non-Samsung Qualcomm/MediaTek/Tensor chipsets).
- Android-to-Android client hardware validation (`android/client`).
- Continued playout jitter-buffer tuning (e.g. 10 ms framing if measurements justify it).

## Explicitly Out of Scope

MMS, RCS, group messaging, video calls and visual voicemail remain out of scope (`wire v1`).
