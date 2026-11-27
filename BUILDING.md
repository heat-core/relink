# Building from Source

> [!NOTE]
> ReLink is archived at `1.3.0-beta.10` as a **source-only reference repository**. No prebuilt release binaries are published here. See [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) before installing experimental builds on personal devices.

For full step-by-step installation, sideloading, and local config instructions, read [`docs/BUILD.md`](docs/BUILD.md) and [`docs/RELINK_INSTALL.md`](docs/RELINK_INSTALL.md).

## Prerequisites

- **Android / Kotlin Core:** JDK 17, Android SDK Platform 36, Android Build Tools `36.0.0`.
- **iOS Companion / Swift Core:** macOS with Xcode 16+ (macOS CI used `macos-26`), `xcodegen` (`2.40+`) for regenerating `ios/Tether.xcodeproj`. (`ios/TetherCore` tests also run on Linux Swift toolchains.)
- **Verification Scripts:** Python 3.11+, `git`, `bash`/`zsh`, optional `gitleaks`.

## Offline Contract & Guardrail Checks

```bash
python3 tools/check-protocol.py
python3 tools/check-architecture.py
python3 tools/check-docs-links.py
python3 tools/check-guardrails.py
python3 tools/altstore-source.py selftest
bash tools/check-private.sh --all
python3 -m unittest discover -s harness/tests
```

## Android Unit Tests & Unsigned Builds

```bash
cd android
./gradlew --console=plain :core:test :app:testDebugUnitTest :helper:testDebugUnitTest :client:testDebugUnitTest
./gradlew --console=plain :app:assembleRelease :client:assembleRelease
```

Keep Android release signing keystores offline on your own trusted workstation; never commit keystores or passwords (`android/local.properties` is git-ignored).

## iOS Unsigned Build & Tests (macOS Only)

```bash
swift test --package-path ios/TetherCore
cd ios && xcodegen generate
xcodebuild -project Tether.xcodeproj -scheme Tether -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGNING_IDENTITY= build
```

For local sideloading, copy `ios/Config/Local.xcconfig.example` to `ios/Config/Local.xcconfig` with your own Apple Team ID and bundle identifier (`ios/Config/Local.xcconfig` stays git-ignored).
