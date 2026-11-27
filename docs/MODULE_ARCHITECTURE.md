# Modular Engineering Architecture

ReLink preserves a unified Android + iOS monorepo while enforcing strict dependency boundaries so changes in one subsystem can be reviewed and tested locally without cross-layer breakage. Cross-references: [Architecture overview](ARCHITECTURE.md) and [Protocol specification](../protocol/PROTOCOL.md).

## Architectural Guardrails

Automated checks in [`tools/check-architecture.py`](../tools/check-architecture.py) verify on every build:

- `android/core` (`:core`) remains pure Kotlin/JVM (zero Android framework, Telecom, UI, or Helper dependencies).
- `android/helper` (`:helper`) runs headless under `uid 2000` with zero dependency on `androidx.*`, Android UI, `android.telecom`, or `app.tether.link`.
- `android/client` (`:client`) communicates strictly over shared wire types in `:core` and never imports `:app` or `:helper` internals.
- `android/app/src/main` never references `src/debug/` receivers or providers (`DebugReceiver`, `PipeProbeProvider`).
- `ios/TetherCore` remains portable across macOS and Linux Swift toolchains with zero `SwiftUI`, `UIKit`, `CallKit`, or `AVFAudio` imports.

## Authoritative 19-Module Map

| # | Module ID | Responsibility | Primary Source Paths | Dependencies | Public Interfaces | Key Tests | Security Sensitivity | Regression Impact & Boundary |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `android-host` | Host FGS, permissions wiring, app lifecycle | `android/app/build.gradle.kts`, `TetherApp.kt` | `shared-core`, `core-android`, host modules | `app.relink.android`, `TetherApp` | `android/app/src/test/**` | High | Service lifecycle & manifest boundary |
| 2 | `android-client` | Second-Android BLE client app | `android/client/src/main/**` | `shared-core`, `zxing` | `ClientCore`, `CentralSession`, `CallTracker` | `android/client/src/test/**` | Medium | Decoupled from host/helper internals |
| 3 | `shared-core` | Wire protocol, P-256 ECDH, AES-GCM, G.711, Opus | `android/core/**`, `android/core-android/**` | `concentus`, `org.json` | `Proto`, `Crypto`, `Records`, `T`, `OpusCodec` | `ContractTest`, `JvmSessionTest` | Critical | Cross-OS parity with `TetherCore` |
| 4 | `telecom-integration` | Default dialer InCallService, blocking, test line | `android/app/src/main/.../telecom/` | `shared-core`, `logical-session` | `Calls`, `Blocking`, `TestConnectionService` | `CallStateTest.kt` | Medium | Preserves native handset fallback |
| 5 | `sms-otp` | SMS poll/sync, OTP parser | `SmsRepo.kt`, `VerificationCode.kt`, `MessageText.swift` | `shared-core`, `contacts` | `SmsRepo`, `VerificationCode`, `MessageText` | `VerificationCodeTest`, `OTPRealWorldTests` | High | Zero PII logging in SMS/OTP |
| 6 | `contacts` | Contact sync, phone formatting, fuzzy search | `ContactsRepo.kt`, `PhoneLabels.kt`, `PhoneFormat.swift`, `NumberShape.swift` | `shared-core` | `ContactsRepo`, `PhoneFormat`, `NumberShape` | `ListsTest`, `PhoneToolsTest`, `SearchTests` | High | Host owns source of truth |
| 7 | `cellular-audio-engine` | Strategy selection, mute/mic check, diagnostics | `AudioStrategies.kt`, `helper/CallAudio.kt`, `MicPath.kt`, `AudioBackend*` | `helper-ipc`, `shared-core` | `CallAudio`, `AudioStrategies`, `CallAudioReport` | `AdaptiveAudioTest`, `MicPathTest`, `RedirectGuardTest` | High | Requires live SIM proof for audibility |
| 8 | `helper-ipc` | Headless `uid 2000` daemon, HMAC auth, lock, Net switches | `android/helper/src/main/**` | None (`android.media` / Binder) | `HelperMain`, `HelperAuth`, `HelperLock` | `HelperAuthTest`, `HelperLockAndTokenTest` | Critical | Loopback `127.0.0.1` only |
| 9 | `adb-bootstrap` | ADB discovery policy & Helper bridge | `adb/*`, `HelperBridge.kt`, `HelperHealth.kt`, `tools/start-helper.sh` | `helper-ipc`, `libadb-android` | `HelperBridge`, `AdbAddressPolicy`, `HelperHealth` | `AdbConnectionStatusTest`, `HelperBridgeCommandTest` | Critical | Keeps ADB secret boundary constrained |
| 10 | `ble-transport` | BLE L2CAP CoC stream, GATT fallback, wake characteristic | `BlePeripheral.kt`, `GattCompat.kt`, `BleCentral.kt`, `TetherLink.swift` | `shared-core` | `BlePeripheral`, `BleCentral`, `TetherLink`, `TetherUUID` | `GattChunkTest`, `GattStreamTests` | High | Works independently of Wi-Fi |
| 11 | `lan-hybrid-transport` | Local Wi-Fi TCP link & hybrid failover | `LanLink.kt`, `Connection.kt`, `LinkService.kt`, `LanLink.swift`, `LinkPolicy.swift` | `ble-transport`, `shared-core` | `LanLink`, `Transport`, `LinkPolicy` | `LanLinkTest`, `LinkPolicyTests`, `AudioTimingTest` | High | Untrusted LAN stays off; BLE recovers |
| 12 | `logical-session` | Call ownership, session multiplexing, `Hub` | `Hub.kt`, `AudioOwner.kt`, `AppModel.swift`, `Stores.swift` | `shared-core`, transports, `telecom-integration` | `Hub`, `AudioOwner`, `AppModel` | `AudioOwnerTest`, `AppModelReceivedTests` | High | Single owner of call & route state |
| 13 | `security-crypto` | Keys, ECDH/GCM session, report scrubber | `Keys.kt`, `LinkSecurity.kt`, `report/*`, `Handshake.swift`, `SecureSession.swift`, `ProblemReport.swift` | `shared-core` | `Keys`, `LinkSecurity`, `SecureSession`, `ProblemReport` | `LinkSecurityTest`, `HandshakeSecurityTests`, `ProblemReportTests` | Critical | Cross-checked via `tether-vectors` |
| 14 | `ios-core-client` | Portable Swift SDK + vendored `COpus` `1.5.2` | `ios/TetherCore/**` | `COpus` (`BSD-3-Clause`) | `TetherCore`, `DialLink`, `LibOpus`, `Playout` | `swift test --package-path ios/TetherCore` | Critical | No UI/CallKit imports |
| 15 | `ios-audio-callkit` | CallKit provider, `AVAudioEngine`, VPIO, AirPods HFP | `ios/Tether/Audio/CallAudio.swift`, `Calls/CallController.swift`, `AudioRoute.swift` | `ios-core-client`, `CallKit`, `AVFAudio` | `CallController`, `CallAudio`, `AudioRoute` | `AudioRouteTests`, `AudioRouteMatrixTests`, `CallControllerTests` | Medium | Requires Xcode + real iPhone/AirPods |
| 16 | `ui` | Compose/Material 3 & SwiftUI screens + EN/FA copy | `android/*/ui/`, `ios/Tether/Views/`, `ios/Tether/Resources/` | `logical-session`, `ios-audio-callkit` | `MainActivity`, `RootView`, `LinkViews` | `PairingQrTest`, `DisplayTests`, `check-guardrails.py` | Medium | Theme & ID edits guarded in PRs |
| 17 | `build-tooling` | Verification scripts, Gradle/Xcode config, CI workflows | `tools/*`, `.github/workflows/`, `ios/project.yml`, `android/settings.gradle.kts` | Python 3, JDK 17, Gradle, XcodeGen | `check-protocol.py`, `check-architecture.py`, `release.sh` | `altstore-source.py selftest` | High | Signing keys stay offline |
| 18 | `testing` | Contract vectors, unit tests, debug synthetic hooks | `protocol/vectors.json`, `*/src/test/`, `*/src/debug/`, `ios/TetherTests/` | `shared-core`, `ios-core-client` | `ContractTest`, `tether-vectors` | All unit & contract suites | Medium | Debug hooks excluded from release APKs |
| 19 | `documentation` | Wire spec, EN/FA guides, templates, governance policies | `README.md`, `docs/**`, `protocol/*.md`, `.github/ISSUE_TEMPLATE/`, `SECURITY.md` | None | `PROTOCOL.md`, `RELEASING.md` | `check-docs-links.py` | Low | Zero private state in public docs |
