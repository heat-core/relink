# Security Policy (Archived Project)

> [!IMPORTANT]
> ReLink is **permanently discontinued and unmaintained**. This repository is no longer monitored for security advisories, patches, or incident response. Do not deploy archived ReLink builds in untrusted environments or rely on them for sensitive communications.

## Open Security Limitations in the Final Codebase

Before reusing or forking this codebase, review all known security gaps documented in [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md):

1. **`libadb-android` (`3.1.1`) `TrustAllX509TrustManager`:** The inherited Wireless ADB client accepts any TLS certificate (`SslUtils`). Automated ADB bootstrap remains disabled (`ADB_HELPER_BOOTSTRAP_ALLOWED = false`) in `1.3.0-beta.9` / `1.3.0-beta.10` until replaced with native TLS + `adbd` SPKI TOFU pinning.
2. **Helper Loopback & Key Storage Debt:** Loopback pre-auth socket throttling/hardening and hardware-backed encryption at rest (`AndroidKeyStore` wrapping for local keys/tokens) remain incomplete.
3. **Local Wi-Fi & Proxy Boundaries:** Local Wi-Fi transport and HTTP/SOCKS5 ShareProxy must be used **only** on isolated trusted networks; ShareProxy lacks per-client auth and private-LAN SSRF filtering (`isSiteLocalAddress`).
4. **Google Play Protect:** Keep Play Protect enabled and do not bypass security warnings. Final physical testing reported a harmful-app warning whose exact cause was not isolated.

## Security Hygiene Rules Preserved in Source

- **End-to-end session crypto:** `P-256 ECDH` key agreement + `HKDF-SHA256` + `AES-256-GCM` with strict monotonic sequence counters (`protocol/PROTOCOL.md` §§2–4).
- **Android backup exclusions:** `android:allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml`.
- **Zero credentials in repository:** All signing keystores, Apple provisioning profiles, personal phone numbers, device serials, and raw logs remain excluded and verified by `tools/check-private.sh --all` and `gitleaks`.

For earlier architectural security details, see [`docs/GOVERNANCE_AND_SECURITY_CI.md`](docs/GOVERNANCE_AND_SECURITY_CI.md) and [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md).
