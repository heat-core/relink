---
title: FAQ
---

# Frequently asked questions

[راهنمای فارسی ReLink](fa/RELINK.md) · [پرسش‌های رایج](fa/faq.md) · [ReLink Install & Build Guide](RELINK_INSTALL.md)

### How do I install ReLink?

Public prebuilt releases are not currently hosted on [heat-core/relink](https://github.com/heat-core/relink/releases). Build the Android host (`app.relink.android`) and iPhone companion (`app.relink.ios`) from the same revision: see [RELINK_INSTALL.md](RELINK_INSTALL.md) and [BUILD.md](BUILD.md). Legacy upstream Tether downloads and AltStore metadata conventions are documented in [INSTALL.md](INSTALL.md) for reference.

### How do I sideload the iPhone app? Do I need a Mac?

Compiling `app.relink.ios` from source requires **macOS and Xcode**. Once built (or when using an unsigned IPA), you can re-sign and install with your own Apple ID using Xcode, SideStore, AltStore or Sideloadly (or TrollStore on supported iOS versions). If an AltStore source feed (`altstore.json`) is published with a future release at `https://github.com/heat-core/relink/releases/latest/download/altstore.json`, SideStore/AltStore can use it for updates. See [RELINK_INSTALL.md](RELINK_INSTALL.md) and [INSTALL.md](INSTALL.md#4-install-the-iphone-app).

### Why does the iPhone app stop opening after a week?

A free Apple ID signs apps for 7 days. Refresh it in your sideloading tool before then; your data and pairing are
kept. A paid developer account (building it yourself, [BUILD.md](BUILD.md)) signs for a year; TrollStore does not
expire.

### Does it work Android to Android?

Yes. Install `Tether-Client-….apk` on the second Android phone (Android 10+) and pair it with the SIM phone's QR
code. It has calls with audio, texts, contacts, recents and the Link switches
([ANDROID-CLIENT.md](ANDROID-CLIENT.md)). It is new and so far tested against a simulated phone, not yet on two
real Android phones: reports are welcome.

### Does it work iPhone to iPhone?

No. The phone with the SIM cards must be Android: iOS does not let any app answer or place the iPhone's own calls,
or read and send its texts. The phone in your hand can be an iPhone, an iPad or an Android phone.

### Which Android versions work? Does Android 10 support calls?

| Android | Texts, contacts, call history | Answer / place / end calls from the iPhone | Call audio on the iPhone |
| --- | --- | --- | --- |
| 8 and 9 | yes | yes | no (talk on the Android phone) |
| 10 | yes | yes | no (talk on the Android phone) |
| 11 and 12 | yes | yes | best effort, depends on the phone |
| 13 and later | yes | yes | yes, best support |

Call audio also needs the phone's Android build to let the shell user capture call audio; the self-test in the app
tells you. More: [features by Android version](devices/android-versions.md).

### Can it fully integrate with the iPhone's Phone and Messages apps?

Partly, within what iOS allows any app:

- **Calls** use the iPhone's own call screen (CallKit): lock screen ringing, answering from AirPods or the car,
  Bluetooth headsets. Optionally (Link tab › Numbers in other apps › **Show calls in the Phone app**) Tether calls
  appear in the Phone app's **Recents** and you can call back from there. Tether's own Recents tab always has the
  full history of the Android phone.
- **Messages**: no. iOS has no way for an app to put texts into the Messages app or send SMS through it. Texts are
  in Tether's own Messages tab, with notifications and quick reply.
- **Contacts** are shown in Tether's own Contacts tab; they are not written into the iPhone's Contacts app.
- **Tapping a phone number** elsewhere can open Tether through Shortcuts / Siri ("Call with"), and on iOS 18.2+ with
  a paid developer account Tether can be the default calling app. Details: [USAGE.md](USAGE.md#calling-numbers-from-other-apps).

### Is there a server or an account? Is it safe?

No server and no account. Paired phones talk directly over Bluetooth LE (or optionally over trusted local Wi-Fi / hotspot TCP in Adaptive Hybrid mode), end to end encrypted (P-256 ECDH key agreement, AES-256-GCM), with pairing secrets exchanged by QR/manual code. Your Apple ID is used only by Xcode or your own sideloading tool, never by ReLink. See [SECURITY.md](https://github.com/heat-core/relink/blob/main/SECURITY.md) for current ADB/TLS boundaries and security practices.

### Why does the Android app need Wireless debugging?

Bridging call audio and switching mobile data, hotspot, tethering or airplane mode need permissions only Android's
shell user (`uid 2000`) holds. ReLink pairs with the phone's own Wireless debugging once and starts a detached helper listening only on `127.0.0.1`; nothing leaves the phone. Once started, the helper survives turning off Wi-Fi or switching to hotspot until the Android device restarts. Texts, contacts and call control work without it.

### The other side cannot hear me. What do I do?

Check the iPhone's microphone permission for ReLink (`Settings › Privacy & Security › Microphone`; the call
screen and Link tab warn when refused or silent) and verify the call is not muted. Check [COMPATIBILITY.md](COMPATIBILITY.md) for device-specific audio status (note bidirectional human-speech SIM audio and AirPods HFP are still pending validation on `Galaxy A71 + iPhone 14 Pro Max` on `1.3.0-beta.8`), try **This phone's microphone during bridged calls**, and run the self-test during a call. Full list: [TROUBLESHOOTING.md](TROUBLESHOOTING.md#the-other-side-cannot-hear-me).

### How far apart can the phones be?

In **Bluetooth Only** mode: Bluetooth range (a few metres, through a bag or pocket). In **Automatic** / **Wi-Fi Preferred** modes: within range of your trusted local Wi-Fi network or the Android phone's hotspot.

### How do I send logs with a bug report?

Send a problem report from each app right after the problem (for audio, right after a test call): on the Android
phone, ReLink › **Help** › **Send a problem report** › Share; on the iPhone and Android Client, **Link › Send a
problem report**. Scrubbed `.txt` files omit phone numbers, names, message text, e-mail, addresses and keys; review them anyway before attaching to an issue at
[github.com/heat-core/relink/issues](https://github.com/heat-core/relink/issues/new/choose)
([what is in it](TROUBLESHOOTING.md#what-to-include-in-a-report)). Older versions: **Call audio › Link log › Share** and iPhone **Link › Link log**.
