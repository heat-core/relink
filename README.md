<p align="center"><img src="docs/images/logo.svg" width="112" alt="ReLink A4 logo"></p>

<h1 align="center">ReLink</h1>

<p align="center"><strong>Two phones. One connection.</strong><br>
An independent MIT derivative of <a href="https://github.com/amirwmr/tether">Tether</a> exploring offline, end-to-end encrypted SIM call, SMS and contact bridging between Android and iPhone.<br>
Final archived development revision: <code>1.3.0-beta.10</code> (build <code>1030010</code>) · Wire protocol: <code>v1</code> · IDs: <code>app.relink.android</code> / <code>app.relink.ios</code></p>

> [!IMPORTANT]
> ReLink is an experimental open-source project that is no longer actively maintained. This repository is preserved for research, reference, and community reuse. Its final development versions are not considered stable or production-ready.
>
> Do not rely on ReLink for regular personal or business calling, time-critical messages, or emergency/safety communications. No prebuilt release APKs or IPAs are published from this archive; inspect and build strictly from source at your own risk.

<p align="center">
  <a href="PROJECT_STATUS.md">Project Status</a> ·
  <a href="KNOWN_LIMITATIONS.md">Known Limitations</a> ·
  <a href="ARCHITECTURE.md">Architecture</a> ·
  <a href="BUILDING.md">Building from Source</a> ·
  <a href="docs/research/README.md">Research Archive</a> ·
  <a href="harness/README.md">Agent Harness</a> ·
  <a href="docs/fa/RELINK.md">راهنمای فارسی</a>
</p>

---

## Overview

ReLink extends upstream [Tether](https://github.com/amirwmr/tether) (`1.2.4` and `1.2.5`) with ReLink A4 identity and Material 3 host UI, dual BLE L2CAP/GATT plus local Wi-Fi/hotspot links, detached Shell Helper diagnostics, and cross-platform protocol/contract verification.

Active development has permanently concluded at `1.3.0-beta.10`. This public repository preserves:

1. **Sanitized comparison history** on top of public `main`:
   - `1.3.0-beta.9` comparison baseline (archived private source reference `84d3d95aa47685dac2798844649366fe42525064`, build `1030009`).
   - `1.3.0-beta.10` final experimental code + OTP test alignment (archived private source/validation references `95944c3c50b10351f7ddb1d45e7a8776f0bea898` and `84164d5d4e43038c626498b9a1f7a3cb8107f8e7`, build `1030010`).
2. **Complete sunset & engineering documentation:** [`PROJECT_STATUS.md`](PROJECT_STATUS.md), [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md), [`PROVENANCE.md`](PROVENANCE.md), [`SECURITY.md`](SECURITY.md), [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), [`ARCHITECTURE.md`](ARCHITECTURE.md), [`BUILDING.md`](BUILDING.md), and [`CHANGELOG.md`](CHANGELOG.md).
3. **Sanitized technical research archive:** [`docs/research/README.md`](docs/research/README.md) documenting verified platform boundaries, failure modes, and unverified hypotheses across Android cellular audio, Shell Helper / ADB privileges, BLE/LAN transport, CallKit, AirPods HFP, OTP extraction, and rejected alternatives.
4. **Sanitized multi-agent workflow harness:** [`harness/README.md`](harness/README.md) providing schema-validated state tracking, lease discipline, checkpoint/handoff scripts, and isolated unit tests using portable example templates.

---

## Final Engineering Summary (`Beta 9` vs. `Beta 10`)

> [!WARNING]
> **Automated tests did not guarantee real-world calling reliability.** While offline unit, protocol, and iOS Simulator suites passed on `1.3.0-beta.10`, physical device validation exposed serious audio, stability, and security regressions. See [`PROJECT_STATUS.md`](PROJECT_STATUS.md) for full details.

| Revision | Automated Offline Verification | Observed Physical Device Outcome (Summary) |
| --- | --- | --- |
| **`1.3.0-beta.9`** (`1030009`) | Swift Core + Simulator (`88` tests), contract/architecture/privacy checks, Android unit tests passed | Install, pairing, SMS and contacts sync worked; outgoing/incoming carrier calls possible; audible call audio and AirPods audio were observed on some calls, **but** Android mic & speaker remained active, severe intermittent latency/noise/choppy audio occurred, connection stability was insufficient, call end lagged, and Helper persistence stayed unclear |
| **`1.3.0-beta.10`** (`1030010`) | Android release unit tests (`444` across `:core`, `:app`, `:helper`, `:client` after test alignment), Swift Core (`196` XCTest + `35` Swift Testing), Simulator (`89` tests) passed | Calls could be initiated from iPhone, **but a serious audio regression prevented bidirectional audio**, ringback audio was absent, and a harmful-app installation warning was reported (without independent establishment of platform/cause) |

---

## Repository Layout

| Directory | Contents |
| --- | --- |
| [`android/`](android/settings.gradle.kts) | `:core` (pure JVM protocol SDK), `:core-android`, `:app` (host `app.relink.android`), `:helper` (`uid 2000`), `:client` (second-Android client) |
| [`ios/`](ios/project.yml) | SwiftUI iPhone companion (`app.relink.ios`), CallKit/AVAudioEngine stack, portable `TetherCore` package + vendored `COpus` (`libopus 1.5.2`) |
| [`protocol/`](protocol/PROTOCOL.md) | Wire contract v1 specification, `compatibility.json`, shared crypto/framing vectors, and JSON fixtures |
| [`docs/`](docs/index.md) | User guides, architecture, compatibility notes, Persian guide ([`docs/fa/RELINK.md`](docs/fa/RELINK.md)), and [`docs/research/`](docs/research/README.md) |
| [`harness/`](harness/README.md) | Sanitized reusable multi-agent state, lease, permission-gate, and checkpoint harness |
| [`tools/`](tools/check-protocol.py) | Protocol parity, architecture boundary, doc-link, UI guardrail, privacy scan, and AltStore metadata checks |

---

## License & Provenance

ReLink is preserved under the **[MIT License](LICENSE)**, retaining upstream [Tether](https://github.com/amirwmr/tether) attribution ([`NOTICE`](NOTICE) and [`PROVENANCE.md`](PROVENANCE.md)). Third-party dependencies carry their own terms documented in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — notably `spake2-java:android` under **`LGPL-3.0`** (reason prebuilt APKs are not published from this archive) and `org.json:json` (JSON License in the JVM-only SDK).
