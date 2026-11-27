#!/usr/bin/env python3
"""Isolated unit and integration tests for the ReLink multi-agent harness.
Uses temporary Git repositories and directories only; never mutates live worktrees."""

from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

HARNESS_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = HARNESS_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import harness_ctl  # noqa: E402


def run_git(repo: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(repo), *args], text=True).strip()


class HarnessTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="relink-harness-test-"))
        self.ws = self.tmp / "ws"
        self.hdir = self.ws / "worktrees" / "project" / "harness"
        self.sdir = self.ws / "worktrees" / "source"
        self.idir = self.ws / "worktrees" / "infra"
        self.pdir = self.ws / "worktrees" / "project"
        self.chk_dir = self.ws / ".local" / "recovery" / "checkpoints"

        for d in (self.hdir / "schemas", self.sdir, self.idir, self.pdir, self.chk_dir):
            d.mkdir(parents=True, exist_ok=True)
        shutil.copytree(HARNESS_ROOT / "schemas", self.hdir / "schemas", dirs_exist_ok=True)
        (self.ws / "AGENTS.md").write_text("# root\n", encoding="utf-8")
        (self.pdir / "AGENTS.md").write_text("# proj\n", encoding="utf-8")

        # Initialize isolated temporary git repos (never touching real workspace repos).
        for repo, branch, fname in (
            (self.sdir, "stabilization/beta8-candidate", "app.kt"),
            (self.idir, "chore/engineering-infrastructure", "infra.md"),
            (self.pdir, "main", "README.md"),
        ):
            run_git(repo, "init", "-b", branch)
            run_git(repo, "config", "user.name", "Test User")
            run_git(repo, "config", "user.email", "test@users.noreply.github.com")
            (repo / fname).write_text("initial\n", encoding="utf-8")
            run_git(repo, "add", ".")
            run_git(repo, "commit", "-m", "Initial")

        self.src_sha = run_git(self.sdir, "rev-parse", "HEAD")
        self.infra_sha = run_git(self.idir, "rev-parse", "HEAD")
        self.proj_sha = run_git(self.pdir, "rev-parse", "HEAD")

        self.art = self.ws / "signed.apk"
        self.art.write_bytes(b"signed-beta9-fixture")
        self.art_sha = hashlib.sha256(b"signed-beta9-fixture").hexdigest()
        self.unsigned_sha = hashlib.sha256(b"unsigned-beta9-fixture").hexdigest()
        self.unsigned_art = self.ws / "unsigned.apk"
        self.unsigned_art.write_bytes(b"unsigned-beta9-fixture")

        now_iso = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
        self.project_state = {
            "schema_version": 1,
            "project_version": "1.3.0-beta.9",
            "build_number": 1030009,
            "wire_protocol": 1,
            "frozen_release_baseline": {
                "version": "1.3.0-beta.9",
                "build": 1030009,
                "branch": "stabilization/beta8-candidate",
                "source_commit": self.src_sha,
                "docs_commit": self.proj_sha,
                "status": "frozen",
            },
            "repositories": {
                "public_fork": {"slug": "owner/project", "visibility": "public", "exists": True, "actions_enabled": False, "push_allowed_without_approval": False},
                "private_archive": {"slug": "owner/project-archive", "visibility": "private", "exists": True, "actions_enabled": False, "push_allowed_without_approval": False},
                "private_dev": {"slug": "owner/project-dev", "visibility": "private", "exists": False, "actions_enabled": None, "push_allowed_without_approval": False},
                "upstream": {"slug": "upstream/project", "visibility": "public", "exists": True, "actions_enabled": None, "push_allowed_without_approval": False},
            },
            "active_worktrees": [
                {"name": "source", "path": str(self.sdir), "expected_branch": "stabilization/beta8-candidate", "expected_commit": self.src_sha, "role": "frozen-beta9-candidate", "allow_dirty_during_task": False},
                {"name": "infra", "path": str(self.idir), "expected_branch": "chore/engineering-infrastructure", "expected_commit": self.infra_sha, "role": "infrastructure-governance-chore", "allow_dirty_during_task": True},
                {"name": "project", "path": str(self.pdir), "expected_branch": "main", "expected_commit": self.proj_sha, "role": "private-docs-harness-state", "allow_dirty_during_task": True},
            ],
            "artifacts": {
                "beta9_signed_android": {"path": str(self.art), "sha256": self.art_sha, "bytes": len(b"signed-beta9-fixture"), "status": "signed-verified", "source_commit": self.src_sha},
                "beta9_unsigned_android": {"path": str(self.unsigned_art), "sha256": self.unsigned_sha, "bytes": len(b"unsigned-beta9-fixture"), "status": "verified-unsigned", "source_commit": self.src_sha},
            },
            "open_blockers": [{"id": "BLK-1", "summary": "Test blocker", "classification": "BLOCKED"}],
            "open_decisions": [{"id": "DEC-1", "summary": "Test decision", "status": "pending"}],
            "owner_approvals": [
                {"id": "APR-SIGN", "operation": "sign_android_release", "repository": "local", "commit": self.src_sha, "scope": "sign", "status": "consumed"},
                {"id": "APR-DEV", "operation": "remote_create_and_push", "repository": "owner/project-dev", "commit": self.src_sha, "scope": "create+push", "status": "pending"},
            ],
            "prohibited_actions": ["push_public"],
            "last_verified_checkpoint": {
                "checkpoint_id": "chk-init",
                "timestamp": now_iso,
                "agent": "agent-a",
                "source_commit": self.src_sha,
                "infra_commit": self.infra_sha,
                "docs_commit": self.proj_sha,
                "notes": "init",
            },
        }
        self.active_task = {
            "schema_version": 1,
            "task_id": "TASK-1",
            "title": "Harness test",
            "status": "in_progress",
            "agent_responsible": "agent-a",
            "worktree_path": str(self.idir),
            "branch": "chore/engineering-infrastructure",
            "base_commit": self.infra_sha,
            "scope": "Test scope",
            "permitted_paths": ["infra.md"],
            "forbidden_paths": ["worktrees/source/**"],
            "completed_steps": ["Step 1"],
            "pending_steps": ["Step 2"],
            "test_commands": ["python test"],
            "test_results": [{"command": "python test", "status": "PASS", "summary": "ok", "commit": self.infra_sha}],
            "lease": {"lease_id": "l1", "agent": "agent-a", "worktree": str(self.idir), "acquired_at": now_iso, "ttl_seconds": 3600, "status": "active"},
            "next_action": "Run checkpoint",
            "updated_at": now_iso,
        }
        mod_template = json.loads((HARNESS_ROOT / "module-map.json").read_text(encoding="utf-8"))
        for m in mod_template["modules"]:
            m["source_paths"] = ["infra.md"]
        perms_template = json.loads((HARNESS_ROOT / "permissions.example.json").read_text(encoding="utf-8"))
        perms_template["release_artifact_guards"]["beta9_signed_android_sha256"] = self.art_sha
        perms_template["release_artifact_guards"]["beta9_unsigned_android_sha256"] = self.unsigned_sha

        harness_ctl.save_json(self.hdir / "project-state.json", self.project_state)
        harness_ctl.save_json(self.hdir / "active-task.json", self.active_task)
        harness_ctl.save_json(self.hdir / "module-map.json", mod_template)
        harness_ctl.save_json(self.hdir / "permissions.json", perms_template)

    def tearDown(self) -> None:
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_01_schema_correctness_and_rejection(self) -> None:
        rc, rep = harness_ctl.cmd_validate(self.hdir, self.idir, strict_hashes=True)
        self.assertEqual(rc, 0, rep)
        bad = dict(self.project_state)
        bad["frozen_release_baseline"] = dict(bad["frozen_release_baseline"], source_commit="not-a-sha")
        harness_ctl.save_json(self.hdir / "project-state.json", bad)
        rc2, rep2 = harness_ctl.cmd_validate(self.hdir, self.idir, strict_hashes=True)
        self.assertNotEqual(rc2, 0)
        self.assertTrue(rep2["schema_errors"])

    def test_02_dirty_frozen_worktree_rejected(self) -> None:
        (self.sdir / "app.kt").write_text("unapproved edit\n", encoding="utf-8")
        rc, rep = harness_ctl.cmd_validate(self.hdir, self.idir, strict_hashes=True)
        self.assertNotEqual(rc, 0)
        self.assertTrue(any("Dirty files not permitted in source" in e for e in rep["worktree_errors"]))

    def test_03_stale_state_detection_on_head_drift(self) -> None:
        (self.idir / "infra.md").write_text("next\n", encoding="utf-8")
        run_git(self.idir, "add", ".")
        run_git(self.idir, "commit", "-m", "Move HEAD")
        rc, rep = harness_ctl.cmd_validate(self.hdir, self.idir, strict_hashes=True)
        self.assertEqual(rc, 0)
        self.assertTrue(any("Stale recorded commit in infra" in w for w in rep["stale_warnings"]))

    def test_04_concurrent_lease_conflict_and_stale_lease_detection(self) -> None:
        rc, rep = harness_ctl.cmd_resume(self.hdir, self.ws, agent="agent-b", acquire_lease=True, force_stale=False)
        self.assertEqual(rc, 2)
        self.assertTrue(any("Concurrent active lease held by 'agent-a'" in r for r in rep["blocking_reasons"]))

        old = (datetime.now(timezone.utc).astimezone() - timedelta(hours=3)).isoformat(timespec="seconds")
        at = harness_ctl.load_json(self.hdir / "active-task.json")
        at["lease"]["acquired_at"] = old
        harness_ctl.save_json(self.hdir / "active-task.json", at)
        rc2, rep2 = harness_ctl.cmd_resume(self.hdir, self.ws, agent="agent-b", acquire_lease=True, force_stale=False)
        self.assertEqual(rc2, 2)
        self.assertTrue(any("Stale lease detected" in r for r in rep2["blocking_reasons"]))

        rc3, rep3 = harness_ctl.cmd_resume(self.hdir, self.ws, agent="agent-b", acquire_lease=True, force_stale=True)
        self.assertEqual(rc3, 0, rep3)
        self.assertEqual(rep3["lease"]["owner"], "agent-b")

    def test_05_protected_branch_and_unauthorized_op_rejected(self) -> None:
        rc1, out1 = harness_ctl.cmd_check_op(
            self.hdir, "direct_commit_to_protected", "owner/project-dev", self.src_sha, branch="main", target_path=None, dry_run=True
        )
        self.assertEqual(rc1, 3)
        self.assertFalse(out1["allowed"])

        # Signing approval (even if consumed) must NEVER authorize remote_create_and_push or device_adb_operation.
        rc2, out2 = harness_ctl.cmd_check_op(
            self.hdir, "remote_create_and_push", "owner/project-dev", self.src_sha, branch="main", target_path=None, dry_run=True
        )
        self.assertEqual(rc2, 3)
        self.assertFalse(out2["allowed"])

        rc3, out3 = harness_ctl.cmd_check_op(
            self.hdir, "remote_push_public", "owner/project", self.src_sha, branch="main", target_path=".local/signing/key.p12", dry_run=True
        )
        self.assertEqual(rc3, 3)
        self.assertFalse(out3["allowed"])

    def test_06_artifact_provenance_tamper_rejected(self) -> None:
        self.art.write_bytes(b"tampered-binary")
        rc, rep = harness_ctl.cmd_validate(self.hdir, self.idir, strict_hashes=True)
        self.assertNotEqual(rc, 0)
        self.assertTrue(any("SHA-256 mismatch" in e for e in rep["artifact_errors"]))

    def test_07_checkpoint_handoff_and_interrupted_recovery(self) -> None:
        # Simulate an interrupted session with dirty tracked edits in infra worktree.
        (self.idir / "infra.md").write_text("partial work before token limit\n", encoding="utf-8")
        rc, chk = harness_ctl.cmd_checkpoint(self.hdir, self.chk_dir, agent="agent-a", note="Pre-interrupt snapshot", write_handoff=True)
        self.assertEqual(rc, 0, chk)
        patch = Path(chk["checkpoint_path"]) / "infra-unstaged.patch"
        self.assertTrue(patch.exists())
        self.assertIn("partial work before token limit", patch.read_text(encoding="utf-8"))

        handoff_file = self.pdir / "AGENT_HANDOFF.md"
        self.assertTrue(handoff_file.exists())
        text = handoff_file.read_text(encoding="utf-8")
        self.assertIn("Resume ReLink from the latest verified checkpoint using the shared harness and active task.", text)
        self.assertIn(chk["checkpoint_id"], text)

        rc_res, res = harness_ctl.cmd_resume(self.hdir, self.ws, agent="agent-a", acquire_lease=False, force_stale=False)
        self.assertEqual(rc_res, 0, res)
        self.assertTrue(res["interrupted_work_detected"])

    def test_08_signing_guard_checks_the_approved_candidate_artifact(self) -> None:
        candidate = self.hdir / "unsigned-new.apk"
        candidate.write_bytes(b"unsigned-new-fixture")
        commit = "e" * 40
        state_path = self.hdir / "project-state.json"
        state = harness_ctl.load_json(state_path)
        state["artifacts"]["beta10_unsigned_android"] = {
            "path": str(candidate), "sha256": harness_ctl.sha256_file(candidate),
            "bytes": candidate.stat().st_size, "status": "verified-unsigned", "source_commit": commit,
        }
        state["owner_approvals"].append({
            "id": "APR-SIGN-10", "operation": "sign_android_release", "repository": "local",
            "commit": commit, "scope": "sign the exact candidate", "status": "approved",
        })
        harness_ctl.save_json(state_path, state)

        def permitted(path: Path) -> bool:
            return harness_ctl.cmd_check_op(
                self.hdir, "sign_android_release", "local", commit,
                branch=None, target_path=str(path), dry_run=True,
            )[1]["allowed"]

        self.assertTrue(permitted(candidate))
        self.assertFalse(permitted(self.unsigned_art))
        candidate.write_bytes(b"tampered")
        self.assertFalse(permitted(candidate))


if __name__ == "__main__":
    unittest.main()
