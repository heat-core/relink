# Project Status — ReLink Final Archive

**Status:** **Archived / Permanently Discontinued (`1.3.0-beta.10`)**  
**Decision:** Active development of ReLink has permanently ended. This repository preserves sanitized engineering source, comparison milestones (`1.3.0-beta.9` and `1.3.0-beta.10`), technical research, and reusable tooling for open-source reference.

---

## 1. Release Readiness Verdict

- ReLink's final development versions (**`1.3.0-beta.9`** and **`1.3.0-beta.10`**) are **experimental and NOT production-ready**.
- ReLink **must not** be used for dependable voice telephony, emergency calls, or sensitive production environments.
- Prebuilt binaries (`.apk` / `.ipa`) are **not published** on this public repository.

---

## 2. Recorded Milestone Outcomes

### `1.3.0-beta.9` (Build `1030009`, archived source reference `84d3d95aa47685dac2798844649366fe42525064`)

- **Offline / CI Checks (Verified):**
  - Android host/helper/client unit suites and release build assembled cleanly.
  - macOS workflow verified `ios/TetherCore` Swift tests and `TetherTests` Simulator XCTests (`88` tests).
- **Physical Device Observations (Reported):**
  - Installation and pairing succeeded.
  - SMS and contacts synchronization succeeded.
  - Outgoing and incoming cellular call control functioned.
  - Audible call audio and AirPods audio were observed during some calls.
  - **Unresolved Failures & Ambiguities:**
    - Exclusive iPhone microphone ownership was **not** conclusively demonstrated; the Android host microphone and speaker remained active on bridged calls.
    - Severe intermittent latency, background noise, and choppy audio occurred.
    - Connection stability across real calls was insufficient.
    - Call termination experienced noticeable lag.
    - Independent Helper startup and background persistence remained unclear.

### `1.3.0-beta.10` (Build `1030010`, archived source reference `95944c3c50b10351f7ddb1d45e7a8776f0bea898`, validation alignment `84164d5d4e43038c626498b9a1f7a3cb8107f8e7`)

- **Changes Included:**
  - Explicit per-session call media negotiation and per-route acknowledgement (`AudioOwner` / `Hub` / `TetherLink`).
  - Bounded iPhone outbound audio queueing, monotonic receive-timer monitoring, and hangup-acknowledgement warning.
  - Contextual English and Persian OTP verification extraction across iOS (`Util.swift`) and Android (`VerificationCode.kt`).
- **Offline / CI Checks (Verified on `84164d5d` / `95944c3c`):**
  - Android release unit suite: `444` tests passed (`51` `:core`, `244` `:app`, `21` `:helper`, `128` `:client`), `0` lint errors.
  - Apple macOS runner: Swift Core `196` XCTests + `35` Swift Testing tests passed; Simulator `TetherTests` `89` tests passed.
  - Contract, architecture boundary, documentation links, theme guardrails, and secret hygiene checks passed.
- **Physical Device Observations (Reported Serious Regression):**
  - Android and iOS builds installed, and calls could still be initiated from the iPhone companion.
  - **Severe audio regression:** Neither direction of voice audio worked on tested calls, and ringback audio was absent.
  - A **harmful-app security warning** was reported during testing; the exact platform/component responsible for that warning was **not independently established** prior to sunset.

---

## 3. Core Engineering Conclusion

> **Automated passing tests (`444` Android unit tests, `196+35` Swift Core tests, `89` Simulator tests) proved protocol/logic consistency under test doubles only — they did NOT guarantee working physical cellular call audio, OEM HAL compatibility, Play Protect acceptance, or RF coexistence stability.**

For concrete root-cause findings and technical constraints encountered across Android HALs, Shell Helper bootstrap, CallKit, AirPods HFP, and transport coupling, consult [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) and [`docs/research/README.md`](docs/research/README.md).
