# ReLink Compatibility & Final Device Observations

> [!IMPORTANT]
> ReLink is archived and no longer actively maintained. Physical test entries below reflect historical observations on pre-sunset experimental builds (`Beta 7`, `Beta 9`, and `Beta 10`); they do **not** certify stable daily-driver usability.

| Target | Historical Observation / Evidence |
| --- | --- |
| **Galaxy A71 (`Android 13`, `One UI 5.1`) + iPhone 14 Pro Max** | **Beta 9:** Install, pairing, SMS/contacts sync, and outgoing/incoming call control succeeded; audible speech and AirPods were observed in some calls, **but** exclusive iPhone mic ownership was not demonstrated (host mic/speaker stayed active), latency/noise/choppy audio was severe, stability was insufficient, call end lagged, and Helper persistence stayed unclear.<br>**Beta 10:** Calls could be placed from iPhone, **but** a serious audio regression blocked bidirectional audio and ringback, and a harmful-app warning was reported (platform/cause not isolated). |
| **Android 8+ & iOS 16+** | Build deployment targets (`android/app`, `ios/Tether`). `android/client` targets Android 10+. Real cellular call audio behavior depends heavily on vendor Audio HAL (`AudioReach` / `PAL` / Exynos) and rootless shell privileges. |
| **Upstream `amirwmr/tether` Targets** | Upstream observations (e.g., Galaxy A54 or Galaxy A73 reports) apply only to upstream builds and their tested OS/firmware revisions. |

Consult [`PROJECT_STATUS.md`](../PROJECT_STATUS.md), [`KNOWN_LIMITATIONS.md`](../KNOWN_LIMITATIONS.md), and [`docs/research/OTP_ARCHITECTURE_AND_COMPATIBILITY.md`](research/OTP_ARCHITECTURE_AND_COMPATIBILITY.md) for detailed subsystem boundaries.
