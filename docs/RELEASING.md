# Releasing ReLink

The repository root `VERSION` file sets the shared release version (for example `1.3.0-beta.8`). Marketing version (`1.3.0`) and build number (`1030008` for Beta 8, `99` for final releases) are kept synchronized across `android/app/build.gradle.kts`, `ios/project.yml`, `ios/Tether.xcodeproj/project.pbxproj`, and verified by `python tools/check-protocol.py`.

[Protocol compatibility](../protocol/COMPATIBILITY.md) · [Security policy](../SECURITY.md)

## Build and Verification Gate

1. Record user-visible changes under `## Unreleased` in [CHANGELOG.md](../CHANGELOG.md) and confirm `VERSION`, `ios/project.yml` and `project.pbxproj` match via `python tools/check-protocol.py`.
2. Run all offline checks before tagging or preparing any artifact:
   - `python tools/check-protocol.py`
   - `python tools/check-docs-links.py`
   - `python tools/check-guardrails.py`
   - `bash tools/check-private.sh --all`
   - `python tools/altstore-source.py selftest`
   - `cd android && ./gradlew --console=plain :core:test :app:testDebugUnitTest :helper:testDebugUnitTest :client:testDebugUnitTest :app:lintRelease :app:assembleRelease`
   - macOS Xcode validation (`swift test --package-path ios/TetherCore`, unsigned `Release` `xcodebuild`, simulator `TetherTests`, and `tools/check-ios-artifact.py`).
3. Keep release workflows (`.github/workflows/release.yml`) **manual only** (`workflow_dispatch`) and completely free of signing secrets. Runners build unsigned Android APKs and unsigned iOS IPAs only.

## Owner Offline Android Signing and Verification

Keep your permanent PKCS#12 keystore (`.p12` / `.jks`) strictly offline on an owner-controlled machine outside Git and GitHub secrets.

```bash
apksigner sign --ks /path/to/relink-release.p12 --ks-key-alias <alias> \
  --out ReLink-<version>-android-signed.apk app-release-unsigned.apk
apksigner verify --verbose --print-certs ReLink-<version>-android-signed.apk
aapt2 dump badging ReLink-<version>-android-signed.apk
aapt2 dump permissions ReLink-<version>-android-signed.apk
zipalign -c -P 16 4 ReLink-<version>-android-signed.apk
```

- Verify that the signing certificate SHA-256 fingerprint matches the owner's previous release (`app.relink.android` updates fail if the signer changes).
- Verify that the release manifest excludes `WRITE_SECURE_SETTINGS`, `DebugReceiver`, `TestConnectionService`, `PipeProbeProvider`, and includes `allowBackup="false"`, `dataExtractionRules` and `fullBackupContent`.
- Keep Google Play Protect enabled on target phones and stop any release candidate if Play Protect raises an issue.

## Owner iOS Re-Signing

Xcode builds produce an unsigned `arm64` `.app` / `.ipa` (`CODE_SIGNING_ALLOWED=NO`). Re-sign locally with your Apple account via Xcode or sideloading tools (`SideStore`, `AltStore`, `Sideloadly`). Keep offline/automated simulator test results clearly separate from physical hardware verification before publishing any release notes.
