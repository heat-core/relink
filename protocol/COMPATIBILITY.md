# Android and iOS protocol compatibility

Beta 5 pairs the Enhanced Android host with the Enhanced iPhone client built from the same commit. Both use wire version 1. The prior Cellular 1.1.0 client used a separate BLE service. The release uses the Enhanced service and authenticated handshake with both new apps.

| Concern | Contract and verification |
| --- | --- |
| Discovery and transport | The service, PSM, RX, TX and wake UUIDs are pinned in [compatibility.json](compatibility.json). The iPhone scans for the service and selects L2CAP when the host supplies a PSM, with GATT fallback per protocol section 1. |
| Pairing, QR and authentication | The in-app scanner accepts the raw tether1: form. Both sides also parse the pairing link. Protocol section 3 defines token validation, P-256 key agreement, HKDF-SHA256 and encrypted FIN. The Android host controls pairing expiry. |
| Session and errors | Sections 2–4 define record kinds, strict counters, AES-256-GCM, control/audio/FIN channels and handshake reject codes. Resume authenticates the stored identities. Reconnect re-establishes a fresh session. |
| Calls and audio | Sections 4–5 define codec IDs, sequence numbers, negotiated audio parameters, 20 ms mono Opus or PCMU frames, and call state/control messages. The Bluetooth link carries call audio in the sealed audio channel. The Android helper handoff has its own local protocol in section 6. |
| Data and capabilities | Section 5 defines shared call, SMS and contact envelopes, synchronization and status. OTP detection presents values from received SMS on the client. Section 7 defines extension capability intersection, bounds and error codes. |

[Wire specification](PROTOCOL.md) · [Shared behavioral fixtures](vectors.json)

Run python3 tools/check-protocol.py before a change to the wire contract. CI checks the constants in both implementations against the reference and checks both implementations against the shared fixtures in their native test suites. A wire change requires updating the specification, fixtures and both implementations. Qualify Bluetooth discovery, reconnection, application-specific Wireless ADB and both call audio directions on physical devices.
