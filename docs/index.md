---
title: ReLink documentation
---

<img src="images/logo.svg" width="96" alt="ReLink A4 logo">

ReLink brings an Android phone's calls, SMS messages, contacts and call history to an iPhone, iPad or second Android phone over an end-to-end encrypted link (`app.relink.android` and `app.relink.ios`), with Bluetooth LE always available and optional trusted local Wi-Fi acceleration. It is an independent MIT derivative of [Tether](https://github.com/amirwmr/tether). [Source on GitHub](https://github.com/heat-core/relink).

**[راهنمای فارسی ReLink](fa/RELINK.md)** · [پرسش‌های رایج](fa/faq.md)

## Start Here

- [ReLink install and build notes](RELINK_INSTALL.md): local source build workflow, owner APK signing, Play Protect hygiene and iOS re-signing
- [Compatibility](COMPATIBILITY.md): Galaxy A71 (`SM-A715F`) + iPhone 14 Pro Max evidence vs pending Beta 8 checks
- [Build](BUILD.md): Android SDK / JDK 17 and macOS Xcode build commands
- [Set up](SETUP.md): permissions, Wireless debugging, TCP fallback, pairing and Adaptive Hybrid modes
- [Usage](USAGE.md): calls, messages, contacts, internet sharing, Share VPN and notifications
- [Troubleshooting](TROUBLESHOOTING.md): connection drops, ADB/Helper recovery, audio directions and scrubbed problem reports
- [FAQ](FAQ.md): common questions across Android, iPhone and sideloading
- [Android client](ANDROID-CLIENT.md): using a second Android phone as the client
- Devices: [Android version feature matrix](devices/android-versions.md) · [Historical upstream Galaxy A54 reference](devices/galaxy-a54.md)

## SDK and Protocol

- [Swift SDK (`TetherCore`)](SDK-swift.md) and generated DocC target
- [Kotlin SDK (`core`)](SDK-kotlin.md) and Dokka target
- [Extending the protocol](EXTENDING.md)
- [Wire protocol (`PROTOCOL.md`)](https://github.com/heat-core/relink/blob/main/protocol/PROTOCOL.md)
- [Architecture](ARCHITECTURE.md)

## Engineering & Governance

- [Testing](TESTING.md)
- [Releasing](RELEASING.md)
- [Roadmap](ROADMAP.md)
- [Contributing](https://github.com/heat-core/relink/blob/main/CONTRIBUTING.md) ·
  [Security](https://github.com/heat-core/relink/blob/main/SECURITY.md) ·
  [Changelog](https://github.com/heat-core/relink/blob/main/CHANGELOG.md)
