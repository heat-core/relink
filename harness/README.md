# Portable Multi-Agent Engineering Harness

This directory preserves a **sanitized, reusable version** of the coordination harness used across multi-agent development sessions on ReLink. All local workstation paths, private repository references, operational credentials, and model-specific names have been stripped.

## Core Guarantees

1. **Schema-Validated Project State (`schemas/`):** Validates `project-state`, `active-task`, `permissions`, and `module-map` JSON contracts without external Python dependencies (Python 3.11+ stdlib only).
2. **Single-Writer Lease Protocol (`resume`):** Prevents concurrent agents from modifying overlapping worktrees simultaneously, detects stale/expired leases, and flags dirty interrupted workspaces before writing.
3. **Scoped Explicit Permission Gates (`check-op`):** Enforces explicit `status = "approved"` records bound to `(operation, repository, commit, scope)` before protected remote pushes, CI dispatches, device actions, or artifact signing; prior consumed approvals never carry over automatically.
4. **Artifact Digest Integrity (`validate`):** Verifies SHA-256 digests of registered release artifacts and rejects tampered or swapped binaries.
5. **Deterministic Checkpoints & Handoffs (`checkpoint`, `handoff`):** Captures unstaged/staged Git patches from dirty worktrees and renders deterministic Markdown handoff summaries (`AGENT_HANDOFF.md` / `RESUME_SUMMARY.md`).

## Quick Start (Isolated Self-Test)

Run the standalone test suite (uses temporary Git repositories only; makes zero modifications to the working tree):

```bash
python3 -m unittest discover -s harness/tests
```

To adopt this harness in another project, copy `*.example.json` to `project-state.json`, `active-task.json`, and `permissions.json` outside public source control (or in a dedicated private ops repo) and run:

```bash
python3 harness/scripts/harness_ctl.py validate
python3 harness/scripts/harness_ctl.py resume --agent agent-a --acquire-lease
```
