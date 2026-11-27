# Security Policy

## Reporting a Vulnerability

Report suspected security vulnerabilities **privately** through [GitHub private vulnerability reporting](https://github.com/heat-core/relink/security/advisories/new).

Please include:

- The affected ReLink version (`VERSION`) or Git commit hash.
- The affected component (Android app, shell helper, iOS app, Android client, core protocol/crypto, ADB bootstrap, or build/CI).
- Minimal reproduction steps or proof-of-concept details using synthetic values only.

**Do not disclose** real phone numbers, contacts, SMS or OTP contents, IP/MAC addresses, device serials, keystore files, private keys, helper tokens or raw unredacted logs in public issues, discussions or pull requests. If private vulnerability reporting is unavailable, open an issue without sensitive technical details asking maintainers for a private contact route. Reports are triaged on a best-effort maintainer schedule; do not assume a fixed SLA unless explicitly stated by the maintainer.

## Supported Scope

- Active development: ReLink `1.3.0-beta.x` on the current development line (`app.relink.android` and `app.relink.ios`), derived from upstream [Tether](https://github.com/amirwmr/tether).
- Earlier private beta builds (`<= 1.3.0-beta.7`) remain in private archive only; public development builds should be built and verified from source at the current revision.

## Security Model and Architecture Boundaries

### Device-to-Device Session Security

- **End-to-end encryption:** Paired devices communicate using P-256 ECDH key agreement and AES-256-GCM authenticated records (`protocol/PROTOCOL.md` §§2–4) with strict monotonic sequence counters.
- **No Bluetooth link-layer bonding:** BLE GATT characteristics and L2CAP CoC intentionally avoid Android Bluetooth link bonding to prevent BR/EDR key-size downgrade bugs during Bluetooth tethering. Confidentiality and peer authentication come exclusively from the application-layer cryptographic session, trading off OS-level bonded RPA tracking.
- **Adaptive Hybrid local Wi-Fi / hotspot transport:** Local TCP endpoints advertise only over the already authenticated encrypted session and are intended **only for trusted local networks or the Android device's own hotspot**. Do not use Adaptive Wi-Fi transport on untrusted public networks; Bluetooth Only mode remains independent and selectable at any time.

### Android ADB and Shell Helper Privilege Boundary

- Bridging call audio and toggling mobile data/hotspot/airplane state on unrooted Android 11+ requires permissions held by the Android `shell` user (`uid 2000`). ReLink obtains these through the OS-sanctioned Wireless Debugging flow (or user-enabled legacy TCP `127.0.0.1:5555` fallback after a USB `adb tcpip` command).
- **Local-only host validation & IPC:** Wireless ADB pairing and TLS connections restrict discovered targets to local non-routable IP addresses (`AdbAddressPolicy`). The detached helper binds only `127.0.0.1`, authenticates mutual HMAC-SHA256 challenge-response frames (`HelperAuth`), and receives its per-launch 128-bit secret via `stdin` into a `umask 077` temporary file immediately deleted on read (`HelperLock.readAndDeleteToken`) rather than in process arguments.
- **Open inherited LibADB TLS limitation:** ReLink inherits `libadb-android` (`3.1.1`) for Wireless Debugging discovery and TLS. Because upstream `libadb-android`'s `SslUtils` uses an all-accepting `TrustManager` (flagged by Android Lint as `TrustAllX509TrustManager`), TLS server-certificate pinning against `adbd` is not enforced by the current client. An on-device privileged adversary or same-subnet spoofing risk if Wireless Debugging were somehow exposed remains a tracked P0 hardening item (`LIBADB_TLS_SERVER_IDENTITY_REVIEW`).
- **Legacy TCP `adb tcpip 5555` warning:** Android's `adbd` TCP mode listens across active network interfaces until disabled (`adb usb`) or rebooted. Keep TCP mode off unless needed without Wi-Fi, and turn it off when no longer required.

### Installation, Backup, Play Protect and Signing Hygiene

- **Keep Google Play Protect enabled.** Verify release APK hashes and signing certificates before installing (`apksigner verify --verbose --print-certs`). Stop immediately if Play Protect or system security blocks a build.
- **Signing keys stay offline.** The permanent Android release keystore never enters Git, CI secrets or contributor environments. Public iOS builds are unsigned (`CODE_SIGNING_ALLOWED=NO`) for owner re-signing with their own Apple account.
- **Backup & extraction disabled:** The Android host manifest specifies `android:allowBackup="false"` together with explicit `dataExtractionRules` (`android >= 12`) and `fullBackupContent` (`android < 12`) exclusions so pairing keys, ADB keys and helper secrets are never exported to cloud backup or device transfers.
- **Public test hygiene:** Test fixtures and scripts use synthetic numbers (`+10000000001`..`+10000000009`) only and must pass `bash tools/check-private.sh`.

[Wire protocol](protocol/PROTOCOL.md) · [Compatibility](docs/COMPATIBILITY.md) · [Release process](docs/RELEASING.md)
