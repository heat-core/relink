# Contributing to ReLink

ReLink (`https://github.com/heat-core/relink`) is an independent derivative of [Tether](https://github.com/amirwmr/tether). Both projects are licensed under the MIT License (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`).

When proposing upstream-compatible improvements, keep fixes isolated so they remain easy to review and share with upstream. Preserve all existing copyright, MIT and third-party license notices (`CONTRIBUTE` / `NOTICE` / `COPYING`).

## How You Can Help

- **Compatibility & call-audio reports:** Include Android model, SoC, Android version/vendor UI, iOS/client version, transport mode, and clearly separate downlink (`remote -> client`), uplink (`client mic -> remote`), and Bluetooth headset/AirPods routing ([device compatibility form](https://github.com/heat-core/relink/issues/new?template=device_report.yml)).
- **Bug & connectivity reports:** Use the [audio/ADB/transport form](https://github.com/heat-core/relink/issues/new?template=audio_adb_connectivity.yml) or [bug report form](https://github.com/heat-core/relink/issues/new?template=bug_report.yml) with scrubbed problem reports (`docs/TROUBLESHOOTING.md#what-to-include-in-a-report`).
- **Pull requests:** Focused bug fixes, tests, documentation, Persian translations and SDK improvements.
- **Security reports:** Always report privately through [SECURITY.md](SECURITY.md) — never in public issues or PRs.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `android/app` | Android SIM host app (`app.relink.android`): BLE peripheral, local Wi-Fi link, foreground service, Telecom dialer, sync repositories, Wireless ADB client and Audio Helper bridge |
| `android/core` | Shared Kotlin/JVM SDK (`core`): handshake, AES-256-GCM records, framing, message types, G.711 and Concentus Opus codecs |
| `android/core-android` | Android platform Opus wrapper (`MediaCodecOpus`) |
| `android/helper` | Privileged shell helper (`uid 2000` over `app_process`): call-audio capture/injection, redirect guard, network state switches |
| `android/client` | Second-Android client app (`app.relink.client`): BLE central, CallTracker, playout buffer |
| `ios/Tether` | iPhone / iPad app (`app.relink.ios`): SwiftUI UI, CallKit integration, `AVAudioEngine` + VoiceProcessing + AirPods/route policy |
| `ios/TetherCore` | Swift SDK package (`TetherCore` + vendored `COpus` / libopus 1.5.2): framing, crypto, BLE `TetherLink`, playout buffer, OTP & number formatting |
| `protocol/` | Wire specification ([PROTOCOL.md](protocol/PROTOCOL.md)), compatibility constants and shared test vectors/fixtures |
| `tools/` | Verification scripts (`check-protocol.py`, `check-guardrails.py`, `check-docs-links.py`, `check-private.sh`, `test-all.sh`, `altstore-source.py`) |
| `docs/` | English documentation and Persian guides (`docs/fa/`) |

## Local Setup and Checks

Follow [docs/BUILD.md](docs/BUILD.md) and [docs/TESTING.md](docs/TESTING.md). Before committing or opening a pull request:

1. **Contract, docs & privacy checks (cross-platform):**
   ```bash
   python tools/check-protocol.py
   python tools/check-docs-links.py
   python tools/check-guardrails.py
   bash tools/check-private.sh --all
   python tools/altstore-source.py selftest
   ```
2. **Android offline / CI unit tests & lint (`JAVA_HOME` JDK 17 + Android SDK):**
   ```bash
   cd android
   ./gradlew --console=plain :core:test :app:testDebugUnitTest :helper:testDebugUnitTest :client:testDebugUnitTest :app:lintRelease
   ```
3. **Swift & Apple checks (Linux for `TetherCore`; macOS + Xcode for iOS app & simulator tests):**
   ```bash
   swift test --package-path ios/TetherCore
   ```
4. **Full local runner (macOS or Linux with `--no-ios`):**
   ```bash
   tools/test-all.sh --no-ios
   ```

## Engineering Rules

- **Separate evidence types:** Never present offline unit tests, simulator runs, loopback test-line checks, or upstream device reports as proof of live cellular audio or hardware compatibility on an untested phone. Report downlink, uplink, AirPods/headset routing, ADB/Helper status and transport failover independently.
- **Keep fallback paths intact:** Bluetooth (L2CAP/GATT) must remain independently usable without Wi-Fi or hotspot. Call control and local Android audio fallback must keep working when audio bridging is unavailable or blocked.
- **Wire protocol compatibility:** Updating message schemas requires updating [protocol/PROTOCOL.md](protocol/PROTOCOL.md), both Kotlin and Swift implementations, backward-compatible `old` variants in `protocol/fixtures/`, and shared vectors (`swift run tether-vectors` in `ios/TetherCore`) in the same commit.
- **Never commit personal data or secrets:** No phone numbers (synthetic `+10000000001`..`+10000000009` only), names, device serials, IP/MAC addresses, Apple IDs, team IDs, provisioning profiles, keystores, passphrases or unredacted device logs. Git-ignored config paths are listed in [docs/BUILD.md](docs/BUILD.md).
- **UI & localization parity:** Use semantic color resources in `colors.xml` / `AccentColor` (dark-first `#101216` surface/background, `#70DDC4` dark accent / `#0D7A63` light accent, `#F4F5F7` light foreground). `tools/check-guardrails.py` rejects raw hex colors outside resources and blocks PR edits to protected identity/signing/workflow files without maintainer review. Update both English and Persian strings (`values`/`values-fa`, `en.lproj`/`fa.lproj`) and keep Persian screens cleanly RTL.
- **Workflow & dependency security:** Never use `pull_request_target` with PR code checkout or secrets. Keep third-party GitHub Actions pinned to full commit SHAs and never add keystores, signing certs or Apple credentials to workflows.

## Pull Request & Commit Guidelines

1. **One topic per commit / PR** with clear imperative commit messages and no private identifiers or automated trailers.
2. **Bug fixes require regression tests** covering the failing path without weakening existing assertions.
3. **Preserve ReLink and upstream attributions** (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `PROVENANCE.md`, `BRAND_USAGE.md`).
4. Everyone participating follows the [Code of Conduct](CODE_OF_CONDUCT.md).
