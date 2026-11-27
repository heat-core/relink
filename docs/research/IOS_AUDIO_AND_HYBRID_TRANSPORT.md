# Research Note: Hybrid BLE/Wi-Fi Transport, iOS CallKit, AirPods HFP & Audio Latency

Topics covered: **(5) Hybrid BLE + LAN transport**, **(6) iOS CallKit boundaries**, **(7) AirPods HFP conflicts**, and **(8) Latency, buffering & jitter**.

---

## 5. Adaptive Hybrid Transport & CoreBluetooth Wake Invariant (`CONFIRMED`)

Because ReLink avoids cloud relays and PushKit/APNs servers, **Bluetooth LE (`WAKE_CHAR` GATT notification + CoreBluetooth `bluetooth-central` state restoration)** is the only non-jailbreak iOS mechanism able to wake a suspended companion app reliably when an incoming cellular call arrives.

- **Verified architectural flaw (`Beta 7–10`):** When local Wi-Fi/hotspot TCP (`LanLink`) was active, clearing BLE state or resetting the session (`TetherLink` / `LanLink` sibling pipes) severed the persistent BLE wake channel and risked flushing queued outbound control state.
- **Target converged model (Unimplemented prior to sunset):** Separate one logical `PeerSession` (keyed by `peerStaticKey`, owning numbered control queue, sync lock, and `CallClaims`) from replaceable physical links:
  - Keep BLE (`L2CAP CoC` -> `GATT` fallback + `WAKE_CHAR`) permanently armed as the low-duty control/wake anchor whenever Bluetooth is on.
  - Offload high-throughput audio frames (`CH_AUDIO`) and bulk SMS/contact sync pages to trusted local IP (Android 5 GHz hotspot or trusted LAN) with rapid ping/stall fallback (`~2s` probe / `6s` audio fallback to BLE) without tearing down CallKit or clearing outbound queues.

---

## 6. iOS CallKit Integration & Lifecycle Boundaries (`CONFIRMED`)

- **Fast disconnect vulnerability:** Tearing down `CXProvider` immediately when RF drops (`linkLost()`) kills calls that would otherwise survive a 1–2 second BLE/Wi-Fi transition. ReLink explored CallKit provider recovery (`1.2.5` merge) and explicit hangup-acknowledgement timeouts (`Beta 10`), though a full multi-second CallKit reconnect grace window was not proven stable end-to-end on physical SIM calls.
- **Private / restricted iOS APIs excluded:** Public iOS third-party apps cannot emulate Bluetooth Classic HFP Hands-Free endpoints, open Classic RFCOMM/MAP/PBAP channels without MFi coprocessor hardware, use deprecated `Multipeer Connectivity` in the background, or assume `com.apple.developer.wifi-aware` entitlements on personal/sideloaded builds.

---

## 7. AirPods HFP & `VoiceProcessingIO` Conflicts (`CONFIRMED` Code + `STRONG EVIDENCE` HFP)

- **Mechanism:** When `AVAudioEngine` attaches `VoiceProcessingIO` while CallKit holds `.voiceChat` category and iOS routes SCO audio to AirPods HFP (16 kHz wideband), hardware DSP in the headset/Bluetooth daemon and software AEC in `VoiceProcessingIO` can collide, producing either robotic distortion or silent zero-energy uplink tap buffers **without throwing an exception from `AVAudioEngine.start()`**.
- **ReLink adjustments (`Beta 9–10`):** Improved `EngineDecision.retryDelay` recovery telemetry, HFP settle timing, and explicit media readiness states (`AudioOwner` / `TetherLink`). Still, dynamic automatic bypass on repeated zero-energy HFP capture frames (and an optional raw `AudioUnit` backend free of `AVAudioEngine` graph rebuild races) remained unvalidated before sunset.

---

## 8. Real-Time Audio Latency, Buffering & Jitter Tradeoffs (`CONFIRMED` Analysis)

| Budget Component | Constraint / Behavior |
| --- | --- |
| **Mouth-to-ear budget** | Total conversational latency should stay below ~`150–180 ms` (of which baseband carrier + OS audio HALs + Helper IPC already consume ~`60–100 ms`), leaving ~`30–80 ms` for transport, framing, and playout buffering. |
| **Codec asymmetry (`Beta 9–10`)** | Android encodes/decodes via pure-Java `Concentus 1.0.2` (higher GC/CPU jitter than C), whereas `ios/TetherCore` bundles native C `libopus 1.5.2` (`COpus`) for decode/PLC and Apple `AudioToolbox` for encode. Unified C `libopus 1.5.2` across Android JNI + iOS was left as an unimplemented optimization. |
| **Drop-late rule vs. TCP HoL** | Dropping audio packets delayed past `60 ms` (`PROTOCOL.md`) limits queue runaway (`Beta 10` bounded queues), **but** running voice over reliable TCP streams (`LanLink`) still incurs Head-of-Line stalls under 2.4 GHz BLE + AirPods Classic + hotspot RF contention. An authenticated unreliable UDP audio datagram sub-lane over local Wi-Fi (`UNVERIFIED HYPOTHESIS`) is physically better suited to real-time speech than TCP. |
