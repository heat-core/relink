# Research Note: OTP Extraction, Rejected Designs, Compatibility & Open Hypotheses

Topics covered: **(9) OTP extraction logic**, **(10) Rejected approaches & ADRs**, **(11) Device compatibility summary**, and **(12) Unverified hypotheses**.

---

## 9. Bilingual English & Persian OTP Extraction (`CONFIRMED`)

- **Precision-first behavior (`Beta 10`, `84164d5d`):** Both `VerificationCode.kt` (`android/client`) and `OTPExtractor` in `Util.swift` (`ios/TetherCore`) normalize Persian (`۰-۹`) and Arabic-Indic (`٠-٩`) numerals and enforce strict **contextual verification keywords** alongside negative exclusions (`tracking`/`پیگیری`/`رهگیری`, `discount`/`تخفیف`, `promo`, `CVV`, URL paths, shortcodes, bank card patterns).
- **Tradeoff:** Prevents false one-time-code copy banners on postal tracking numbers, transaction IDs, and promotional vouchers; intentionally does **not** treat isolated numbers (`"Code 482913"` only if ambiguous or bare digits without an OTP keyword) as verified codes when surrounding context is missing or excluded.

---

## 10. Rejected Approaches & Architecture Decision Records (`ADR-01..05`)

### Rejected Alternatives (`CONFIRMED` Platform / Protocol Constraints)

| Alternative | Reason Rejected |
| --- | --- |
| Bluetooth Classic HFP / MAP / PBAP emulation | Closed to third-party iOS apps (requires MFi coprocessor) and `@hide` on AOSP |
| Bluetooth LE Audio Custom Phone-to-Phone CIS | System-internal on iOS/Android; no public app API for arbitrary peer bridging |
| Apple `Multipeer Connectivity` / AWDL / Sideloaded Wi-Fi Aware | Deprecated (`TN3213`) or non-cross-platform; Wi-Fi Aware requires restricted Apple entitlement |
| Full `libwebrtc` Stack | Adds `+10–20 MB`/ABI binary bloat; ICE assumes IP availability unavailable in BLE-only mode |
| ARQ Stream Transports for Voice (`TCP`/`KCP`/`SRT`) | Retransmission past 60 ms bursts stall conversational speech worse than Opus PLC |
| Rewriting Controls from JSON to Protobuf/Cap'n Proto | Control messages are `< 2 KB`; breaks protocol v1 fixture parity for negligible benefit |

### Recorded Engineering Decisions (`ADR-01..05`)

- **`ADR-01` (ADB Trust Boundary):** Local subnet filters and echo checks do not authenticate `adbd`; keep automated ADB bootstrap disabled until native TLS SPKI pinning is built.
- **`ADR-02` (IPC & Proxy Containment):** Keep Helper bound strictly to `127.0.0.1` with mutual HMAC and avoid expanding external proxy listeners until SSRF/auth hardening completes.
- **`ADR-03` (Targeted Correctness Fixes):** Adopted bounded fork fixes (`SmsParts`, `wake()` before `send()`, per-`cid` claims) while preserving wire v1 compatibility.
- **`ADR-04` (Wire v1 Preservation):** Retained deterministic P-256 ECDH + AES-256-GCM wire contract and `vectors.json` verification rather than breaking compatibility mid-stabilization.
- **`ADR-05` (Deferred Heavy Rewrites):** Kept `AVAudioEngine` and safe Qualcomm guards as baseline while deferring native Android JNI `libopus`, UDP audio, raw `AudioUnit`, and Binder FD pipe promotion to isolated future experiments.

---

## 11. Hardware & Software Compatibility Boundaries

| Component / Target | Observed State | Notes |
| --- | --- | --- |
| **Offline Protocol & Unit Suites (`1.3.0-beta.10`)** | Passing (`444` Android release unit tests, `196+35` Swift Core, `89` Simulator XCTests) | Tested with synthetic numbers (`+10000000001..9`) and simulated hardware doubles |
| **Real Device Pair (Android 13 One UI 5.1 + iOS + AirPods)** | **Beta 9:** Calls, sync & intermittent audible speech/AirPods observed, **but** host mic/speaker remained active, severe noise/latency/instability occurred, hangup lagged, Helper unclear.<br>**Beta 10:** Serious bidirectional & ringback audio regression + reported harmful-app warning. | Automated pass did not imply real-world voice quality or hardware compatibility |

---

## 12. Unverified Hypotheses (Preserve Clearly Separated from Confirmed Facts)

None of the following proposals were experimentally validated on physical hardware prior to project sunset:

1. Whether switching `HelperMain` <-> app audio transport from `127.0.0.1` TCP to `PipeProbe`-style Binder `ParcelFileDescriptor` / `SharedMemory` materially reduces voice tail latency or avoids Play Protect heuristics in release builds.
2. Whether compiling native C `libopus 1.5.2` (JNI) with Opus DRED on Android plus encrypted UDP datagram audio (`LanLink` UDP sub-lane) eliminates 2.4 GHz choppy speech.
3. Whether direct iOS `kAudioUnitSubType_VoiceProcessingIO` `AudioUnit` prevents AirPods HFP double-AEC silent buffers more reliably than `AVAudioEngine` graph rebuilds.
4. Which exact change between `Beta 9` and `Beta 10` caused the physical bidirectional/ringback audio regression (`Beta 10` media negotiation / route-ACK gating vs device Helper/HAL state), and which specific APK attribute triggered the final harmful-app warning.
