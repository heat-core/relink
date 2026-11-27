# Source Provenance & Archival History

ReLink began as an independent MIT-licensed derivative of [Tether](https://github.com/amirwmr/tether) (`1.2.4` at `0afaece67c63e7c39c7333d703d5d3e9005aa6`, later incorporating upstream `1.2.5` Qualcomm mixer/mic-mute and CallKit recovery fixes `78fc682` and `98e0864`, crediting Arash Farahani and Amir Mohammad Rezaei).

## Public History Isolation & Sanitized Snapshots

During active private stabilization (`Beta 9` and `Beta 10`), intermediate commits, private backups, device identifiers, and owner operational files existed only in private repositories/local storage. To preserve open-source engineering value without leaking private commit ancestry, logs, credentials, or operational metadata, the final public sunset branch (`sunset/public-publication`) is constructed cleanly atop live public `main` (`227445c3b302f62bdc359441ac451fe344a4994b`) in two sanitized commits:

1. **Sanitized `1.3.0-beta.9` Comparison Baseline (Build `1030009`):**
   - Mirrors the sanitized public-safe application, protocol, and test source tree corresponding to private frozen reference `84d3d95aa47685dac2798844649366fe42525064`.
   - Retains the public governance, CodeQL, guardrail, and privacy-check infrastructure from `227445c3`.
2. **Sanitized `1.3.0-beta.10` Final Archive, Sunset Docs, Research & Generic Harness (Build `1030010`):**
   - Mirrors the sanitized public-safe source and aligned OTP test suite corresponding to private frozen application commit `95944c3c50b10351f7ddb1d45e7a8776f0bea898` and test-only OTP alignment commit `84164d5d4e43038c626498b9a1f7a3cb8107f8e7` (with macOS dual-provenance workflow reference `f4815af81f5c69e92fac6920358d4e22a43e9278`).
   - Adds sunset status and limitation documentation (`PROJECT_STATUS.md`, `KNOWN_LIMITATIONS.md`, `ARCHITECTURE.md`, `BUILDING.md`), sanitized engineering research under `docs/research/`, and a portable generic multi-agent workflow harness under `harness/`.

## Authorship & Attribution Notes

- Public sunset commits are recorded under `heat-core <260391741+heat-core@users.noreply.github.com>`.
- Original upstream copyright, MIT license terms ([`LICENSE`](LICENSE)), [`NOTICE`](NOTICE), and third-party dependency attributions ([`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)) remain preserved intact.
- Community ecosystem insights analyzed in [`docs/research/`](docs/research/README.md) preserve reference attribution to upstream `amirwmr/tether` and its MIT-licensed fork authors.
