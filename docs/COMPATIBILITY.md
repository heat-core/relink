# ReLink compatibility

| Target | Evidence |
| --- | --- |
| Galaxy A71, Android 13 and One UI 5.1 | Primary Android host target. Beta 7 SMS sync was observed. Wireless ADB, Helper startup and audible call audio remain pending. |
| iPhone 14 Pro Max | Primary companion target. Controlled SIM calls, route changes, AirPods HFP and hybrid failover remain pending. |
| Android 8+ and iOS 16+ | Build deployment targets. Call audio support varies with Android version and vendor routing. |
| Other Android or iOS phones | Follow the same staged validation. Upstream Tether reports on other devices cover their own build and firmware. |

Automated results cover the tested software paths. A real incoming and outgoing SIM call is required to check whether each participant hears speech in both directions. Keep Bluetooth available while validating local-network fallback.
