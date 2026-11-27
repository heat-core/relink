# Branching Strategy, Rulesets & Release Procedure

ReLink follows a lightweight **GitHub Flow** adapted for a solo repository maintainer collaborating with isolated topic branches and automated coding agents across separated public, private development, and historical archive repositories.

## 1. Repository Topology

| Repository | Visibility | Purpose | Public Promotion Policy |
| --- | --- | --- | --- |
| `heat-core/relink` | Public (fork of `amirwmr/tether`) | Official approved open-source tree and public issue tracker | Receives only maintainer-approved, privacy-scanned, license-cleared commits |
| Private development repo | Private | Ongoing feature/fix development, integration (`develop`), and private candidate validation | Primary active development queue; private tasks are never duplicated publicly |
| Historical archive repo | Private (frozen) | Immutable archive of earlier private pre-releases and tags | Historical archive only; not an active development repo |

## 2. Branch Naming & Lifecycle

- `main`: Accepted baseline. Direct pushes and history rewrites blocked by repository rulesets; updated only via reviewed pull requests.
- `develop`: Integration branch in private development where scoped topic branches converge before release stabilization.
- Short-lived topic branches (deleted automatically on merge):
  - `feature/*`: Independent capabilities scoped to specific modules.
  - `fix/*`: Defect fixes paired with regression tests.
  - `hotfix/*`: Urgent minimal fixes targeting `main`.
  - `refactor/*`: Structural improvements preserving external behavior.
  - `security/*`: Private security hardening developed cleanly before coordinated disclosure.
  - `release/*`: Release stabilization candidates awaiting maintainer physical acceptance.
  - `experiment/*`: Isolated experiments not promoted without explicit review.
  - `chore/*`: Build tooling, CI, harness, or documentation maintenance.

Do **not** create empty placeholder branches merely to demonstrate naming conventions.

## 3. Pull Requests, Merge Policy & Controlled Promotion

- **Parallel work isolation:** Each active task uses its own isolated branch and dedicated Git worktree so simultaneous edits never collide.
- **No destructive history edits:** Force pushes and history rewrites are prohibited on shared branches.
- **Merge methods:** Allow `merge commit` and clean `squash` merges while disabling `rebase merge` (to preserve review/upstream lineage), enabling `delete_branch_on_merge`, and keeping `allow_auto_merge` disabled (`false`).
- **Sole-maintainer review rules:** Automated AI reviews are strictly advisory. Public rulesets use PR enforcement without requiring a second human reviewer (`required_approving_review_count: 0`) so the maintainer is never locked out of reviewed merges. Required status checks are added only after matching CI workflows have executed cleanly on GitHub.
- **Controlled public promotion:** Unverified stabilization candidates and private operational state are never merged into public `main`. Public promotion transfers only audited, privacy-clean (`check-private.sh`, `gitleaks`), license-cleared commits reviewed by the maintainer.

## 4. Reproducible Versioned Releases

- Root `VERSION` sets both Android `versionCode` and iOS `CURRENT_PROJECT_VERSION` via `tools/check-protocol.py`.
- CI `.github/workflows/release.yml` runs solely via manual `workflow_dispatch`, generates immutable `candidate-<shortsha>` unsigned APKs and unsigned IPAs alongside `SHA256SUMS.txt` and `ANDROID_BUILD_INFO.txt` / `IOS_BUILD_INFO.txt`, and never accesses signing secrets.
- Permanent release keys remain strictly offline on maintainer-owned hardware. Public binary distribution requires all security, physical acceptance, and third-party license gates (including LGPL-3.0 `spake2` compliance) to be satisfied first.
