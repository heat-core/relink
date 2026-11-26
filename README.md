<p align="center"><img src="docs/images/logo.svg" width="112" alt="ReLink logo"></p>

# ReLink

ReLink lets an iPhone use the calls, messages and contacts of a nearby Android phone that holds the SIM. It is an independent derivative of [Tether](https://github.com/amirwmr/tether). The development version is 1.3.0-beta.8.

| Area | Status |
| --- | --- |
| Bluetooth pairing, calls and sync | QR and manual pairing, call control and data sync are implemented. Beta 7 synchronized SMS on the development pair. |
| Local Wi-Fi | Experimental authenticated transport for already paired devices. Bluetooth remains available for initial pairing and as fallback. Live failover is pending. |
| Cellular audio | Helper, capture and injection exist. Audible two-way SIM audio and AirPods recovery are pending on the target pair. |
| Distribution | The earlier development packages are kept in the private archive. Public development builds require local Android tooling and macOS with Xcode for iPhone. |

[Installation and build](docs/RELINK_INSTALL.md) · [Compatibility](docs/COMPATIBILITY.md) · [Roadmap](docs/ROADMAP.md) · [راهنمای فارسی](docs/fa/RELINK.md)

## Setup

1. Build the Android host and iPhone companion from the same revision. Their identifiers are `app.relink.android` and `app.relink.ios`.
2. Enable Bluetooth and pair the devices with a QR or manual code from the Android host. Grant iPhone Local Network access only if you choose to try local Wi-Fi.
3. For an authorized call-audio test, use a trusted network and follow the Android Wireless Debugging prompts. Keep Play Protect enabled. Stop if Android blocks installation.

## Development

Run `python tools/check-protocol.py` and the Android unit tests before proposing a change. [Build instructions](docs/BUILD.md) · [Contributing](CONTRIBUTING.md) · [Security reporting](SECURITY.md) · [Changelog](CHANGELOG.md)

ReLink retains the Tether copyright and the MIT grant. Third-party components retain their own terms. [License](LICENSE) · [Notices](THIRD_PARTY_NOTICES.md) · [Brand usage](BRAND_USAGE.md)
