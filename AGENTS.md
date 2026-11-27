# ReLink Engineering Instructions

These shared repository instructions apply to human contributors and automated coding agents working in `heat-core/relink`.

- Preserve MIT and third-party license notices (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, `PROVENANCE.md`).
- Keep `android` and `ios` aligned on the root `VERSION` and wire contract (`protocol/PROTOCOL.md`, `protocol/compatibility.json`). Preserve Bluetooth LE (`L2CAP CoC` and `GATT` fallback), end-to-end authenticated records (`P-256 ECDH` + `AES-256-GCM`), and local handset call fallback.
- Respect architectural module boundaries verified by [`tools/check-architecture.py`](tools/check-architecture.py) and documented in [`docs/MODULE_ARCHITECTURE.md`](docs/MODULE_ARCHITECTURE.md).
- Follow branching, pull request, and release gates in [`docs/BRANCHING_AND_RELEASES.md`](docs/BRANCHING_AND_RELEASES.md) and security CI practices in [`docs/GOVERNANCE_AND_SECURITY_CI.md`](docs/GOVERNANCE_AND_SECURITY_CI.md).
- Never commit phone numbers (use synthetic `+10000000001..+10000000009`), device serials, IP/MAC addresses, keys, keystores, passwords, raw device logs, or private filesystem paths.
- Never store Android keystores, Apple signing certificates, or passphrases in Git or GitHub Actions Secrets; CI release workflows emit unsigned artifacts for local owner signing only.
- Automated code reviews are advisory; final approval authority rests with the repository maintainer.
- Before committing, run: `python3 tools/check-protocol.py`, `python3 tools/check-architecture.py`, `python3 tools/check-docs-links.py`, `python3 tools/check-guardrails.py`, `bash tools/check-private.sh --all`, and relevant native unit tests.
