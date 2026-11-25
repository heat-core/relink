# Releasing Tether Enhanced

The root VERSION file supplies the release identifier, such as 1.3.0-beta.5. The Android host and iPhone app use 1.3.0 as their marketing version and 1030005 as their build number for Beta 5. Their diagnostics identify the release, Git commit, wire version and connection status. [Protocol compatibility](../protocol/COMPATIBILITY.md)

## Build and verify a release

1. Add changes under Unreleased in CHANGELOG.md. On a clean main branch, run tools/release.sh with the next version. This synchronizes VERSION, the Xcode project and XcodeGen spec, and creates a local version tag. Run python3 tools/check-protocol.py before pushing.
2. Push the branch and tag after the secret audit. The Beta release builds workflow uses the same commit for both platforms. Standard macOS runs are reserved for version tags or manual dispatch. Verify the account's included usage or budget before dispatching or retrying a build.
3. Wait for both platform jobs in the same workflow run. Download the two artifacts and their verification reports. Confirm the metadata files record the same release and commit and verify their SHA-256 files.
4. Sign the Android artifact with the owner's release key. On a trusted workstation with Build Tools 36.0.0, use the commands below. Verify the signing certificate against the owner's pinned fingerprint and check package identity and permissions.
5. Stage the signed APK, the unsigned IPA, SHA256SUMS.txt, RELEASE_NOTES.md, TEST_REPORT.md, INSTALLATION_GUIDE.md and KNOWN_ISSUES.md. Prepare a GitHub draft pre-release on the same tag. Mark the IPA as requiring owner re-signing and report device validation separately.

## Android signing and recovery

The private PKCS#12 store stays on the owner workstation outside Git in an owner-restricted directory. Use the masked local password prompt and store the passphrase separately in the owner's password manager. Keep an encrypted backup on separate owner-controlled storage. Check recovery by opening the backup with keytool and comparing its public certificate SHA-256 with the previous release.

~~~bash
apksigner sign --ks /path/to/tether-enhanced-release.p12 --ks-key-alias tether-enhanced \
  --out Tether-Enhanced-Android-1.3.0-Beta5.apk tether-android-unsigned.apk
apksigner verify --verbose --print-certs Tether-Enhanced-Android-1.3.0-Beta5.apk
aapt dump badging Tether-Enhanced-Android-1.3.0-Beta5.apk
aapt dump permissions Tether-Enhanced-Android-1.3.0-Beta5.apk
zipalign -c -P 16 4 Tether-Enhanced-Android-1.3.0-Beta5.apk
~~~

Keep a copy of the public fingerprint with each release. Android updates require the same package ID and a compatible signing certificate. The previously installed Android app uses a distinct package ID. Keep it and its data until the owner approves a device change.

## iPhone re-signing

The Xcode build produces an arm64 app and packages it without an owner signing identity or provisioning profile. Download the private release artifact with an authorized GitHub account and import the IPA into SideStore. The owner signs it with their own Apple account. Keep the old iPhone app available until the owner has checked the new build. Simulator results establish compilation and app test behavior. SideStore installation, the background Bluetooth and audio paths, and actual call behavior require a physical iPhone.
