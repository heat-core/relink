# Engineering Research Archive (`docs/research/`)

ReLink's active development concluded at `1.3.0-beta.10`, leaving substantial cross-platform reverse-engineering notes, failure-mode analyses, protocol audits, and architectural tradeoff evaluations. This sanitized research archive preserves those engineering lessons for the open-source community.

---

## Classification Legend Used Throughout These Notes

- **`CONFIRMED`:** Verified directly in reviewed source code, deterministic unit/contract tests, compiler toolchains, or documented OS SDK APIs.
- **`STRONG EVIDENCE`:** Supported consistently by issue histories, independent community forks under permissive licenses, or observed device runs whose exact internal HAL trace was not isolated step-by-step.
- **`UNVERIFIED HYPOTHESIS`:** Engineering explanation, proposed remediation, or unmeasured performance idea not yet proven on physical target devices (`Galaxy A71` + `iPhone 14 Pro Max` + `AirPods`).

---

## Topic Index (12 Tracked Areas)

| Document | Covered Topics |
| --- | --- |
| [**Android Audio, Samsung HAL, Helper & ADB (`ANDROID_AUDIO_AND_HELPER.md`)**](ANDROID_AUDIO_AND_HELPER.md) | 1. Android call-audio interception & injection<br>2. Samsung / Qualcomm HAL routing constraints<br>3. Shell Helper (`uid 2000`) IPC & lifecycle<br>4. Wireless ADB TLS bootstrap limitations |
| [**iOS CallKit, AirPods HFP, Audio Latency & Hybrid Transport (`IOS_AUDIO_AND_HYBRID_TRANSPORT.md`)**](IOS_AUDIO_AND_HYBRID_TRANSPORT.md) | 5. Hybrid BLE + Local Wi-Fi transport & wake invariants<br>6. iOS CallKit integration & recovery boundaries<br>7. AirPods HFP & `VoiceProcessingIO` double-AEC conflicts<br>8. Latency, buffering, PLC & jitter tradeoffs |
| [**OTP Extraction, Rejected Designs, Compatibility & Open Hypotheses (`OTP_ARCHITECTURE_AND_COMPATIBILITY.md`)**](OTP_ARCHITECTURE_AND_COMPATIBILITY.md) | 9. English & Persian OTP extraction precision tradeoffs<br>10. Rejected architectural alternatives & ADRs (`ADR-01..05`)<br>11. Physical compatibility boundaries & Beta 9/10 summary<br>12. Unverified hypotheses & open engineering questions |

> [!NOTE]
> All private device logs, local environment paths, personal device identifiers, credential artifacts, and non-public exploitation walkthroughs were excluded from this public archive.
