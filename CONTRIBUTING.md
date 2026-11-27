# Contributing (Archived Reference Repository)

> [!IMPORTANT]
> **ReLink is permanently discontinued and archived.** Pull requests, issues, and feature requests are no longer actively reviewed or maintained. If you build upon this codebase, consider forking under the [MIT License](LICENSE) while respecting [`NOTICE`](NOTICE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

For contributors exploring, auditing, or running offline verification locally on an archival fork, preserve these repository invariants:

## Repository Layout

| Path | What |
| --- | --- |
| `android/app` | Android SIM host app (`app.relink.android`: link, Telecom, data sync, audio bridge, Compose Material 3 UI) |
| `android/core` | Pure Kotlin/JVM shared core: crypto, records, messages, G.711 and Concentus Opus codecs |
| `android/core-android` | Android platform `MediaCodec` Opus wrapper |
| `android/client` | Second-Android BLE client app (`app.tether.client`) |
| `android/helper` | Headless `uid 2000` shell helper (call audio interception/injection, network switches) |
| `ios/Tether` | iPhone / iPad SwiftUI app (`app.relink.ios`), CallKit, `AVAudioEngine` |
| `ios/TetherCore` | Portable Swift package + vendored `COpus` (`libopus 1.5.2`) |
| `protocol/` | [`PROTOCOL.md`](protocol/PROTOCOL.md), `compatibility.json`, test vectors and message fixtures |
| `docs/` | Guides, architecture specs, and [`docs/research/`](docs/research/README.md) |
| `harness/` | Portable schema-validated multi-agent orchestration harness |
| `tools/` | Offline contract, architecture boundary, doc-link, guardrail, and privacy verification scripts |

## Local Verification Commands

Run these checks before archiving or validating any local derivative commit:

1. `python3 tools/check-protocol.py`
2. `python3 tools/check-architecture.py`
3. `python3 tools/check-docs-links.py`
4. `python3 tools/check-guardrails.py`
5. `python3 tools/altstore-source.py selftest`
6. `bash tools/check-private.sh --all`
7. `python3 -m unittest discover -s harness/tests`

## Core Design Invariants (Reference)

- **Protocol & Test Parity:** Changes to `protocol/PROTOCOL.md` or message types require matching Kotlin (`android/core`) and Swift (`ios/TetherCore`) updates plus shared `protocol/fixtures/` and `vectors.json`.
- **Bluetooth & Debug Boundaries:** Keep BLE free of OS pairing bonding (`LinkSecurityTest`), restrict debug hooks strictly to synthetic numbers `+10000000001..+10000000009`, and keep debug components excluded from release APK manifests.
- **Zero Personal Data or Keys:** Never commit private phone numbers, device serials, macOS/Windows user paths, signing keystores, certificates, or unredacted device logs. See [`SECURITY.md`](SECURITY.md) and [`BUILDING.md`](BUILDING.md).
- **Brand & Licensing:** Retain [`LICENSE`](LICENSE), [`NOTICE`](NOTICE), [`BRAND_USAGE.md`](BRAND_USAGE.md), and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). UI colors follow [`tools/check-guardrails.py`](tools/check-guardrails.py).
