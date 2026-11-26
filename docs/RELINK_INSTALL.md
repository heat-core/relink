# Install a local development build

Start with the source instructions for [Android and iPhone](BUILD.md). Build both apps from the same commit and check `VERSION`. A supported JDK and Android SDK are required for Android. iOS requires macOS and Xcode. An unsigned IPA requires signing with your own Apple credentials and provisioning. Keep Android and Apple signing keys on your own trusted machine.

Keep Google Play Protect enabled. Stop if it blocks the APK. Use a matching application ID and signing identity for Android updates, and preserve your app data and pairings when moving between test builds.

On first launch, pair over Bluetooth using the Android QR code or manual entry. Local Wi-Fi can be tried afterward on a trusted network. Call audio depends on Android shell access and vendor audio routing. Consult the [compatibility table](COMPATIBILITY.md) before using it for real calls.
