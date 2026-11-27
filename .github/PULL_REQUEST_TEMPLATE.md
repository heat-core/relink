## Summary & Linked Task

- **Linked Issue / Task ID:** <!-- e.g., #12 -->
- **Problem Statement:** <!-- What specific problem, regression, or security gap does this change address? -->
- **Scope:** <!-- What is in scope and explicitly out of scope? -->
- **Affected Modules (`docs/MODULE_ARCHITECTURE.md`):** <!-- e.g., adb-bootstrap, helper-ipc, cellular-audio-engine, build-tooling -->

## Engineering Rationale & Impact

- **Rationale:** <!-- Why this approach was selected over alternatives -->
- **Security Impact:** <!-- Privilege boundaries, keys, tokens, ADB/Helper auth, backup hygiene, TLS trust -->
- **Compatibility Impact:** <!-- Wire protocol v1, legacy peers, Android SDK levels, iOS versions, upgrade path -->
- **Rollback Considerations:** <!-- How to revert cleanly without leaving stale state or broken pairings -->

## Verification Evidence & Source Provenance

- **Environment Used:** <!-- e.g., Linux CI / macOS Xcode / Physical device pair -->
- **Source Provenance / Base Commit:** <!-- Exact branch & base SHA tested -->
- **Automated Checks & Results (verbatim counts):**
  - [ ] `python3 tools/check-protocol.py`
  - [ ] `python3 tools/check-architecture.py`
  - [ ] `python3 tools/check-docs-links.py`
  - [ ] `python3 tools/check-guardrails.py`
  - [ ] `bash tools/check-private.sh --all`
  - [ ] Gradle / Swift tests run (attach exact counts, failures, skipped)
- **Physical Device Validation Status:** <!-- PASS / FAIL / PENDING / BLOCKED — never infer SIM audio from offline tests -->
- **Call-Audio Directions Tested (if applicable):** Downlink `[ ]`, Uplink `[ ]`, Bluetooth / AirPods `[ ]`

## Review & Governance Checklist

- [ ] Changes keep one focused topic with MIT/upstream provenance preserved (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`)
- [ ] Bluetooth-only fallback (`BLE L2CAP/GATT`) and local call fallback remain functional
- [ ] UI/localization updates include both English and Persian (`values` / `values-fa`, `en.lproj` / `fa.lproj`, RTL clean)
- [ ] Zero private numbers (synthetic `+10000000001..9` only), secrets, device serials, keystores, or raw device logs
- [ ] Unreleased entry in `CHANGELOG.md` updated for behavioral changes
- [ ] Workflow / build changes are isolated, SHA-pinned, least-privilege, and contain zero signing secrets
- [ ] Maintainer approval (`@heat-core`) obtained prior to merge
