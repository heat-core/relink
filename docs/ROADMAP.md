# Roadmap (Historical — Project Archived)

> [!NOTE]
> Active development of ReLink has permanently ended at `1.3.0-beta.10`. The items below remain solely as reference notes for downstream forks or researchers.

## Unfinished Pre-Sunset Priorities

1. **Physical Cellular Audio & HAL Isolation:** Isolate and resolve the Beta 10 bidirectional/ringback audio regression and Samsung Qualcomm microphone/speaker leakage (`KNOWN_LIMITATIONS.md`).
2. **`libadb-android` Replacement:** Replace `TrustAllX509TrustManager` and `LGPL-3.0` `spake2-java:android` with a native TLS 1.3 ADB client implementing SPKI Trust-On-First-Use (TOFU) pinning.
3. **Unified Logical `SessionCoordinator`:** Decouple physical link switches (`BLE` <-> `Wi-Fi LAN`) from session/outbox teardown while keeping CoreBluetooth `WAKE_CHAR` continuously alive.
4. **Harmful-App Warning Triage:** Isolate which permission, signing, TLS heuristic, or native-binary attribute triggered final physical security warnings.

See [`docs/research/README.md`](research/README.md) for concrete architectural hypotheses and [`https://github.com/amirwmr/tether`](https://github.com/amirwmr/tether) for upstream Tether's own roadmap.
