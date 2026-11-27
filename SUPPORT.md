# Getting Support

| What you need | Where to go |
| --- | --- |
| Build or install from source | [ReLink Install Guide](docs/RELINK_INSTALL.md) · [Build Guide](docs/BUILD.md) · [راهنمای فارسی](docs/fa/RELINK.md) |
| Check what works on your devices | [Compatibility](docs/COMPATIBILITY.md) · [Troubleshooting](docs/TROUBLESHOOTING.md) · [FAQ](docs/FAQ.md) ([فارسی](docs/fa/faq.md)) |
| Report Wireless ADB, Helper, call audio, AirPods or transport bugs | [Audio, ADB or Transport issue form](https://github.com/heat-core/relink/issues/new?template=audio_adb_connectivity.yml) |
| Report a general bug | [Bug report form](https://github.com/heat-core/relink/issues/new?template=bug_report.yml) |
| Report device hardware results | [Device compatibility form](https://github.com/heat-core/relink/issues/new?template=device_report.yml) |
| Report build, signing or Play Protect issues | [Install / build form](https://github.com/heat-core/relink/issues/new?template=install_build.yml) |
| Ask a usage or setup question | [Question form](https://github.com/heat-core/relink/issues/new?template=question.yml) |
| Report a security vulnerability | **Privately only:** see [SECURITY.md](SECURITY.md) |

## Before Opening an Issue

1. Check [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md), [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) and open issues first.
2. Specify the exact ReLink version or commit hash on **both** the Android host and the iOS / Android client.
3. State your hardware, OS version and chipset/vendor UI (for example `Galaxy A71, SM-A715F, Snapdragon 730, Android 13 / One UI 5.1` and `iPhone 14 Pro Max, iOS 18.x`).
4. Distinguish clearly between:
   - What happened on a **real cellular SIM call** vs the self-managed test line or offline unit tests.
   - Downlink (`remote caller -> client`) vs uplink (`client mic -> remote caller`), plus Bluetooth headset / AirPods routing.
   - Active transport mode (`Automatic`, `Wi-Fi Preferred` or `Bluetooth Only`).
5. **Scrub all private data** before posting: never paste phone numbers, contact names, SMS/OTP messages, IP/MAC addresses, serials, pairing codes or signing keys. Replace sensitive values with `<redacted>` or synthetic test numbers (`+10000000001`..`+10000000009`).
