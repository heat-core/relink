# Third-Party Notices & Dependency Compliance

Retain [`NOTICE`](NOTICE) and upstream copyright statements in full. ReLink itself is licensed under [MIT](LICENSE), while build-time and vendored dependencies remain under their respective licenses:

| Component | Version | License | Scope & Compliance Notes | Source / Pin |
| --- | --- | --- | --- | --- |
| **AndroidX, Jetpack Compose, Kotlin & Coroutines** | BOM `2025.10.01`, Activity `1.11.0` | `Apache-2.0` | Permissive; notices retained | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) · [Kotlin](https://github.com/Kotlin) |
| **`libadb-android`** | `3.1.1` | Dual `GPL-3.0-or-later OR Apache-2.0` | Used under **`Apache-2.0`** option per [`NOTICE`](NOTICE) | [libadb-android](https://github.com/MuntashirAkon/libadb-android) |
| **`spake2-java` (`spake2-android`)** | `2.2.1` | **`LGPL-3.0`** (with associated BSD notices) | **Linked native library (`libspake2.so`) via `libadb-android`.** Any compiled APK redistribution triggers LGPL-3.0 §4 obligations (preserve LGPL text/notices, supply corresponding source, and permit user relinking via source rebuild instructions). This archive distributes source only. | [spake2-java](https://github.com/MuntashirAkon/spake2-java) |
| **`org.json:json`** *(JVM SDK only)* | `20260814` | **JSON License** | Exported transitively by `android/core` (`build.gradle.kts`) for desktop/JVM consumers; **excluded from Android APKs** (which use platform `org.json`). Note: JSON License includes the non-OSI *"Good, not Evil"* clause. | [JSON-java](https://github.com/stleary/JSON-java) |
| **Conscrypt** | `2.9.0` | `Apache-2.0` | Android TLS provider bundled for Wireless ADB | [Conscrypt](https://github.com/google/conscrypt) |
| **ZXing Core** | `3.5.4` | `Apache-2.0` | QR generation/scanning | [ZXing](https://github.com/zxing/zxing) |
| **Bouncy Castle (`bcprov` / `bcpkix`)** | `1.87` | Bouncy Castle License (MIT-style) | X.509 / key handling for ADB | [Bouncy Castle](https://www.bouncycastle.org/licence.html) |
| **Concentus & Vendored `libopus`** | `1.0.2` / `1.5.2` | `BSD-3-Clause` | Pure-Java Opus (`android/core`) and vendored C `COpus` (`ios/TetherCore`) | [Concentus](https://github.com/lostromb/concentus) · [COpus COPYING](ios/TetherCore/Sources/COpus/COPYING) |
| **SwiftPM Packages (`swift-perception`, `swift-syntax`, `xctest-dynamic-overlay`, `swift-crypto`)** | Pinned in `Package.resolved` | `MIT` / `Apache-2.0` | Follow resolved pins and runtime exception notices | [`Package.resolved`](ios/Tether.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved) · [`NOTICE`](NOTICE) |
