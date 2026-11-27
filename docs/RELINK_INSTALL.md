# Install or Build ReLink (`1.3.0-beta.8`)

Public prebuilt APK and IPA binaries are **not** currently hosted on `heat-core/relink`. Build both apps from the same Git commit so Android (`app.relink.android`) and iOS (`app.relink.ios`) share matching protocol and version metadata (`VERSION`).

For upstream tool details, see [BUILD.md](BUILD.md).

This Beta 8 development build pauses new in-app Audio Helper startup and Share VPN pending their security gates. An already authenticated compatible Helper can reconnect. Use Android-local call audio for normal calls during this phase.

## 1. Android Host (`app.relink.android`)

1. Set `JAVA_HOME` to **JDK 17** and configure Android SDK platform `36` with Build Tools `36.0.0`.
2. Run offline/local verification and build the release APK:
   ```bash
   cd android
   ./gradlew --console=plain :core:test :app:testDebugUnitTest :helper:testDebugUnitTest :client:testDebugUnitTest :app:lintRelease :app:assembleRelease
   ```
3. Sign `android/app/build/outputs/apk/release/app-release-unsigned.apk` locally with your **permanent release keystore** (keep the keystore and passphrase offline on your trusted machine — never commit them or place them in CI secrets):
   ```bash
   apksigner sign --ks /path/to/relink-release.p12 --ks-key-alias <alias> \
     --out ReLink-android-signed.apk android/app/build/outputs/apk/release/app-release-unsigned.apk
   apksigner verify --verbose --print-certs ReLink-android-signed.apk
   ```
4. **Play Protect & safety:** Keep **Google Play Protect** enabled when installing. Stop immediately if Android or Play Protect blocks an APK. Use the same `applicationId` (`app.relink.android`) and signing certificate across updates so app data and pairings survive.

## 2. iPhone Companion (`app.relink.ios`)

1. Building requires **macOS** and **Xcode 16+** (CI checks use Xcode 26.6) plus `XcodeGen`.
2. Generate the project and build unsigned for owner re-signing (or open `ios/Tether.xcodeproj` with your local `ios/Config/Local.xcconfig`):
   ```bash
   cd ios && xcodegen generate
   xcodebuild -project Tether.xcodeproj -scheme Tether -onlyUsePackageVersionsFromResolvedFile \
     -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
   ```
3. Re-sign the built app using your own Apple account in Xcode or SideStore / AltStore / Sideloadly. Keep iOS **Developer Mode** enabled (`Settings › Privacy & Security › Developer Mode`). Remember free Apple accounts expire every 7 days and preserve app data on refresh.

## 3. Initial Pairing, Local Wi-Fi & Audio Helper

1. **Pair over Bluetooth:** Turn on Bluetooth on both phones, open ReLink on Android (`Pair iPhone`) and scan the QR code (or paste the manual code) from the iPhone **Link** tab.
2. **Choose transport mode:** **Automatic** is default (uses Bluetooth LE immediately and upgrades to authenticated local Wi-Fi/hotspot TCP when both phones share a trusted local network or 5 GHz hotspot). Keep **Bluetooth Only** available whenever traveling on untrusted networks; grant iOS **Local Network** access only if you intend to use Wi-Fi/hotspot transport.
3. **Initialize Audio Helper (Android 11+):** Follow Setup to pair **Wireless debugging** once (requires Wi-Fi during the initial Helper launch). Once running, the detached Helper process (`uid 2000` on `127.0.0.1`) survives turning off Wi-Fi or enabling hotspot until Android reboots. On Android 8–10 or when Wi-Fi is unavailable after a reboot, optional `adb tcpip` loopback instructions are in [SETUP.md](SETUP.md#tcp-debugging-android-8-to-10-or-no-wi-fi).

Consult [COMPATIBILITY.md](COMPATIBILITY.md) and [SECURITY.md](../SECURITY.md) before testing on daily SIM calls.
