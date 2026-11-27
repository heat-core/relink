# Architecture Overview

> [!NOTE]
> ReLink is archived at `1.3.0-beta.10`. For the detailed subsystem guide, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md); for the 19-module boundary rules verified by `tools/check-architecture.py`, see [`docs/MODULE_ARCHITECTURE.md`](docs/MODULE_ARCHITECTURE.md); for open failure analyses, see [`docs/research/README.md`](docs/research/README.md).

## System Topology

```mermaid
flowchart LR
    subgraph IOS["iPhone Companion (app.relink.ios)"]
        IUI["SwiftUI + CallKit + AVAudioEngine"]
        ICore["TetherCore (Crypto / Playout / TetherLink)"]
    end

    subgraph TRANS["Hybrid Transports (P-256 ECDH + AES-256-GCM)"]
        BLE["Bluetooth LE (L2CAP CoC / GATT Fallback + WAKE_CHAR)"]
        LAN["Authenticated Local Wi-Fi / Hotspot TCP (Trusted Network Only)"]
    end

    subgraph AND["Android SIM Host (app.relink.android)"]
        AApp["LinkService + Hub + Telecom InCallService + Repositories"]
        ADB["Wireless ADB / Supervised USB Bootstrap"]
        Help["Detached Shell Helper (uid 2000, 127.0.0.1 Mutual HMAC)"]
    end

    ICore <--> BLE <--> AApp
    ICore <-.->|"Optional on trusted LAN/Hotspot"| LAN <-.-> AApp
    AApp -->|"One-shot stdin secret bootstrap"| ADB --> Help
    AApp <-->|"127.0.0.1 control + 16 kHz PCM"| Help
```

## Key Modules

| Module / Directory | Language / Target | Responsibility |
| --- | --- | --- |
| `android/core` | Kotlin / JVM 17 | Pure-JVM protocol contract (`Proto`, `Crypto`, `Records`, `T`, `OpusCodec`), free of Android SDK imports |
| `android/app` | Kotlin / Android 8+ | BLE peripheral, local Wi-Fi link, foreground service, Telecom dialer, SMS/contacts/call-log repos, Material 3 host UI |
| `android/helper` | Kotlin / Android `uid 2000` | Headless shell process (`app_process`) for call-audio interception/injection and privileged network switches |
| `android/client` | Kotlin / Android 10+ | Standalone second-Android BLE companion client sharing wire code only via `:core` |
| `ios/TetherCore` | Swift 5.9 + C (`libopus 1.5.2`) | Portable iOS/macOS/Linux core library + `COpus` wrapper; UI- and CallKit-free |
| `ios/Tether` | SwiftUI / iOS 16+ | Companion UI, CallKit controller, `AVAudioEngine` call audio graph, CoreBluetooth central link |
| `protocol/` | Spec + JSON vectors/fixtures | Wire v1 contract ([`protocol/PROTOCOL.md`](protocol/PROTOCOL.md)), `compatibility.json`, and deterministic fixtures |
| `tools/` | Python / Shell / PowerShell | Protocol, architecture, docs-link, UI guardrail, and privacy verification scripts |
