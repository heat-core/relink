# Governance, Security Scanning & CI/CD Architecture

ReLink separates fast Linux checks from cost-sensitive Apple (`macos-26`) jobs and pairs repository governance with minimal least-privilege permissions. Cross-references: [Releasing guide](RELEASING.md) and [Security policy](../SECURITY.md).

## 1. Workflow Separation & Path Controls

| Workflow File | Trigger Policy | Runner(s) | Purpose |
| --- | --- | --- | --- |
| `.github/workflows/ci.yml` | `push` / `pull_request` (`main`, `develop`), `workflow_dispatch` | `ubuntu-24.04`, manual-only `macos-26` | Runs `source` (always, stable required check name) + `git diff`-filtered `kotlin`, `android`, `swift` Linux jobs + manual `apple` job |
| `.github/workflows/security.yml` | `pull_request` (`main`, `develop`), `push` (`main`), `workflow_dispatch` | `ubuntu-24.04` | Dependency review (PR), Gradle wrapper SHA-256 verification, `gitleaks`, `shellcheck`, `actionlint`, architecture boundaries, `check-private.sh` |
| `.github/workflows/codeql.yml` | `push` (`main` public only), `workflow_dispatch` | `ubuntu-24.04` + manual-only `macos-26` | Advanced CodeQL setup (`java-kotlin`, `python`, `actions`, manual `swift`) without duplicating Default Setup |
| `.github/workflows/release.yml` | Strictly `workflow_dispatch` | `ubuntu-24.04` + `macos-26` (`30m` max) | Produces immutable `candidate-<shortsha>` unsigned APK + IPA and `SHA256SUMS.txt` |

- **Path filtering without stalled checks:** The top-level `source` job executes on every PR without path filters so required branch-protection checks never remain indefinitely `Pending`; heavier `android` and `swift` jobs run only when matching paths change.
- **Supply-chain hardening:** Every workflow sets top-level `permissions: {}`, `persist-credentials: false`, 40-char SHA-pinned Actions, verified official SHA-256 digests for `actionlint` and MIT `gitleaks`, and zero signing secrets.

## 2. CodeQL & Private vs. Public Capabilities

- **Public repository (`heat-core/relink`):** Code Scanning / CodeQL is supported free. `.github/workflows/codeql.yml` configures Advanced Setup (`java-kotlin` compiled with Gradle, `python`, `actions` on Linux, and manual-only `swift` on `macos-26`) while keeping Default Setup disabled (`not-configured`) to avoid duplicate scans.
- **Private repositories on free tier:** Advanced security features (`code-scanning`, branch rulesets, secret scanning) gated behind paid Pro/Advanced Security plans remain disabled in favor of local checks and standard Linux CI.

## 3. Swift Macro Validation Policy

- `ios/Tether.xcodeproj` pins `swift-perception` `2.0.12` (`597afd46249f4a71885b95d26b26cc5b893daf32`) in `Package.resolved`.
- Global `-skipMacroValidation` is **not** used in CI workflows. Instead, workflows assert exact `Package.resolved` pins and pre-authorize `PerceptionMacros` strictly at its audited commit fingerprint in `~/Library/org.swift.swiftpm/security/macros.json`.

## 4. Dependabot Policy

- `.github/dependabot.yml` scans Gradle, SwiftPM, and GitHub Actions weekly with PR caps (`6`/`4`/`4`) and isolated `adb-tls-crypto` review grouping.
- Dependency PRs are never auto-merged; `adb-tls-crypto` updates always require manual security review, and `spake2-java:android` (`LGPL-3.0`) redistribution compliance stays gated before public binary releases.
