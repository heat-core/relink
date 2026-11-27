#!/usr/bin/env python3
"""ReLink multi-agent harness controller: schema validation, Git drift/lease detection,
checkpoints, deterministic handoffs, and scoped permission checks."""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def parse_iso(ts: str) -> datetime:
    return datetime.fromisoformat(ts)


def git_run(repo: Path, *args: str) -> tuple[int, str, str]:
    proc = subprocess.run(
        ["git", "-C", str(repo), *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return proc.returncode, proc.stdout.strip(), proc.stderr.strip()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path: Path, data: Any) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _resolve_ref(ref: str, root_schema: dict[str, Any]) -> dict[str, Any]:
    if not ref.startswith("#/$defs/"):
        raise ValueError(f"Unsupported $ref: {ref}")
    key = ref.split("/", 2)[2]
    return root_schema["$defs"][key]


def _check_type(val: Any, expected: str) -> bool:
    if expected == "object":
        return isinstance(val, dict)
    if expected == "array":
        return isinstance(val, list)
    if expected == "string":
        return isinstance(val, str)
    if expected == "integer":
        return isinstance(val, int) and not isinstance(val, bool)
    if expected == "number":
        return isinstance(val, (int, float)) and not isinstance(val, bool)
    if expected == "boolean":
        return isinstance(val, bool)
    if expected == "null":
        return val is None
    return False


def validate_schema(val: Any, schema: dict[str, Any], root: dict[str, Any], path: str = "$") -> list[str]:
    if "$ref" in schema:
        schema = _resolve_ref(schema["$ref"], root)
    errors: list[str] = []
    if "type" in schema:
        allowed = schema["type"] if isinstance(schema["type"], list) else [schema["type"]]
        if not any(_check_type(val, t) for t in allowed):
            return [f"{path}: expected type {allowed}, got {type(val).__name__}"]
    if "enum" in schema and val not in schema["enum"]:
        errors.append(f"{path}: value {val!r} not in enum {schema['enum']}")
    if isinstance(val, str):
        if "minLength" in schema and len(val) < schema["minLength"]:
            errors.append(f"{path}: string shorter than minLength {schema['minLength']}")
        if "pattern" in schema and not re.match(schema["pattern"], val):
            errors.append(f"{path}: string {val!r} does not match pattern {schema['pattern']}")
    if isinstance(val, int) and not isinstance(val, bool):
        if "minimum" in schema and val < schema["minimum"]:
            errors.append(f"{path}: integer {val} < minimum {schema['minimum']}")
    if isinstance(val, list):
        if "minItems" in schema and len(val) < schema["minItems"]:
            errors.append(f"{path}: array length {len(val)} < minItems {schema['minItems']}")
        if "items" in schema:
            for idx, item in enumerate(val):
                errors.extend(validate_schema(item, schema["items"], root, f"{path}[{idx}]"))
    if isinstance(val, dict):
        props = schema.get("properties", {})
        for req in schema.get("required", []):
            if req not in val:
                errors.append(f"{path}: missing required key '{req}'")
        for k, v in val.items():
            if k in props:
                errors.extend(validate_schema(v, props[k], root, f"{path}.{k}"))
            else:
                addl = schema.get("additionalProperties", True)
                if addl is False:
                    errors.append(f"{path}: unexpected additional property '{k}'")
                elif isinstance(addl, dict):
                    errors.extend(validate_schema(v, addl, root, f"{path}.{k}"))
    return errors


def inspect_worktree(wt: dict[str, Any]) -> dict[str, Any]:
    path = Path(wt["path"])
    if not path.exists():
        return {"name": wt["name"], "path": str(path), "exists": False, "errors": [f"Missing worktree: {path}"]}
    rc_br, branch, err_br = git_run(path, "rev-parse", "--abbrev-ref", "HEAD")
    rc_hd, head, err_hd = git_run(path, "rev-parse", "HEAD")
    rc_st, status, err_st = git_run(path, "status", "--porcelain")
    dirty_files = [line for line in status.splitlines() if line.strip()] if status else []
    errors: list[str] = []
    if rc_br != 0 or rc_hd != 0 or rc_st != 0:
        errors.append(f"Git error in {path}: {err_br} {err_hd} {err_st}".strip())
    if branch != wt["expected_branch"]:
        errors.append(f"Branch drift in {wt['name']}: actual={branch}, expected={wt['expected_branch']}")
    if wt["role"] == "frozen-beta9-candidate" and head != wt["expected_commit"]:
        errors.append(f"Frozen commit drift in {wt['name']}: actual={head}, expected={wt['expected_commit']}")
    elif not wt.get("allow_dirty_during_task", False) and dirty_files:
        errors.append(f"Dirty files not permitted in {wt['name']}: {dirty_files[:5]}")
    stale_head = head != wt["expected_commit"]
    return {
        "name": wt["name"],
        "path": str(path),
        "exists": True,
        "branch": branch,
        "head": head,
        "expected_commit": wt["expected_commit"],
        "stale_state": stale_head,
        "dirty": bool(dirty_files),
        "dirty_count": len(dirty_files),
        "dirty_files": dirty_files,
        "errors": errors,
    }


def evaluate_lease(lease: dict[str, Any] | None, current_agent: str | None = None, now: datetime | None = None) -> dict[str, Any]:
    if not lease or lease.get("status") == "released":
        return {"present": False, "state": "none", "conflict": False, "stale": False}
    now_dt = now or datetime.now(timezone.utc).astimezone()
    acquired = parse_iso(lease["acquired_at"])
    age = (now_dt - acquired).total_seconds()
    is_stale = age > lease["ttl_seconds"] or lease.get("status") == "stale"
    owner = lease.get("agent")
    conflict = (not is_stale) and (current_agent is not None) and (owner != current_agent) and lease.get("status") == "active"
    return {
        "present": True,
        "lease_id": lease.get("lease_id"),
        "owner": owner,
        "age_seconds": round(age, 1),
        "ttl_seconds": lease.get("ttl_seconds"),
        "stale": is_stale,
        "conflict": conflict,
        "state": "stale" if is_stale else ("conflict" if conflict else lease.get("status", "active")),
    }


def cmd_validate(harness_dir: Path, source_root: Path | None, strict_hashes: bool = True) -> tuple[int, dict[str, Any]]:
    schemas_dir = harness_dir / "schemas"
    pairs = [
        ("project-state.json", "project-state.schema.json"),
        ("active-task.json", "active-task.schema.json"),
        ("module-map.json", "module-map.schema.json"),
        ("permissions.json", "permissions.schema.json"),
    ]
    schema_errors: list[str] = []
    loaded: dict[str, Any] = {}
    for data_name, schema_name in pairs:
        data_path = harness_dir / data_name
        schema_path = schemas_dir / schema_name
        if not data_path.exists() or not schema_path.exists():
            schema_errors.append(f"Missing {data_path} or {schema_path}")
            continue
        data = load_json(data_path)
        schema = load_json(schema_path)
        loaded[data_name] = data
        schema_errors.extend(validate_schema(data, schema, schema, data_name))

    worktree_reports: list[dict[str, Any]] = []
    wt_errors: list[str] = []
    stale_warnings: list[str] = []
    if "project-state.json" in loaded:
        for wt in loaded["project-state.json"]["active_worktrees"]:
            rep = inspect_worktree(wt)
            worktree_reports.append(rep)
            wt_errors.extend(rep.get("errors", []))
            if rep.get("stale_state"):
                stale_warnings.append(f"Stale recorded commit in {wt['name']}: recorded={rep['expected_commit'][:7]}, actual={rep.get('head','')[:7]}")

    module_errors: list[str] = []
    if "module-map.json" in loaded and source_root is not None:
        for mod in loaded["module-map.json"]["modules"]:
            for rel in mod["source_paths"]:
                p = source_root / rel
                if not p.exists():
                    module_errors.append(f"Module '{mod['id']}' missing path in {source_root}: {rel}")

    artifact_errors: list[str] = []
    if strict_hashes and "project-state.json" in loaded:
        for name, art in loaded["project-state.json"]["artifacts"].items():
            if art["sha256"] == "PENDING":
                continue
            p = Path(art["path"])
            if not p.exists():
                artifact_errors.append(f"Artifact '{name}' missing on disk: {p}")
                continue
            actual_sha = sha256_file(p)
            if actual_sha.lower() != art["sha256"].lower():
                artifact_errors.append(f"Artifact '{name}' SHA-256 mismatch: got {actual_sha}, expected {art['sha256']}")

    lease_status = evaluate_lease(loaded.get("active-task.json", {}).get("lease"))
    all_errors = schema_errors + wt_errors + module_errors + artifact_errors
    result = {
        "ok": len(all_errors) == 0,
        "schema_errors": schema_errors,
        "worktree_errors": wt_errors,
        "stale_warnings": stale_warnings,
        "module_path_errors": module_errors,
        "artifact_errors": artifact_errors,
        "lease": lease_status,
        "worktrees": worktree_reports,
    }
    return (0 if result["ok"] else 1), result


def cmd_resume(harness_dir: Path, workspace_root: Path, agent: str, acquire_lease: bool, force_stale: bool) -> tuple[int, dict[str, Any]]:
    ps = load_json(harness_dir / "project-state.json")
    at = load_json(harness_dir / "active-task.json")
    perms = load_json(harness_dir / "permissions.json")
    mm = load_json(harness_dir / "module-map.json")

    agents_files = [
        workspace_root / "AGENTS.md",
        harness_dir.parent / "AGENTS.md",
    ]
    agents_found = [str(p) for p in agents_files if p.exists()]
    worktrees = [inspect_worktree(wt) for wt in ps["active_worktrees"]]
    lease = evaluate_lease(at.get("lease"), current_agent=agent)

    interrupted = any(w.get("dirty") for w in worktrees if w["name"] != "project") or at.get("status") in {"paused", "in_progress"}
    rc_val, val_report = cmd_validate(harness_dir, None, strict_hashes=True)

    blocking_reasons: list[str] = []
    if rc_val != 0:
        blocking_reasons.extend(val_report["schema_errors"] + val_report["artifact_errors"] + val_report["worktree_errors"])
    if lease["conflict"]:
        blocking_reasons.append(f"Concurrent active lease held by '{lease['owner']}' (lease_id={lease['lease_id']}). Do not write simultaneously.")
    if lease["stale"] and acquire_lease and not force_stale:
        blocking_reasons.append(f"Stale lease detected for '{lease['owner']}' (age={lease['age_seconds']}s > ttl={lease['ttl_seconds']}s). Re-run with --force-stale after inspecting interrupted work.")

    if acquire_lease and not blocking_reasons:
        at["lease"] = {
            "lease_id": f"lease-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}-{agent}",
            "agent": agent,
            "worktree": at.get("worktree_path", ""),
            "acquired_at": utc_now_iso(),
            "ttl_seconds": 3600,
            "status": "active",
        }
        at["agent_responsible"] = agent
        at["updated_at"] = utc_now_iso()
        save_json(harness_dir / "active-task.json", at)
        lease = evaluate_lease(at["lease"], current_agent=agent)

    report = {
        "ok": len(blocking_reasons) == 0,
        "protocol_steps_checked": 10,
        "agents_files": agents_found,
        "task_id": at["task_id"],
        "task_status": at["status"],
        "interrupted_work_detected": interrupted,
        "lease": lease,
        "frozen_baseline": ps["frozen_release_baseline"],
        "worktrees": worktrees,
        "stale_warnings": val_report["stale_warnings"],
        "protected_branches": perms["protected_branches"],
        "pending_steps": at["pending_steps"],
        "next_action": at["next_action"],
        "module_count": len(mm["modules"]),
        "blocking_reasons": blocking_reasons,
    }
    return (0 if report["ok"] else 2), report


def cmd_checkpoint(harness_dir: Path, checkpoint_dir: Path, agent: str, note: str, write_handoff: bool) -> tuple[int, dict[str, Any]]:
    ps = load_json(harness_dir / "project-state.json")
    at = load_json(harness_dir / "active-task.json")
    ts = datetime.now(timezone.utc).astimezone()
    chk_id = f"chk-{ts.strftime('%Y%m%d-%H%M%S')}-{agent}"
    chk_out = checkpoint_dir / chk_id
    chk_out.mkdir(parents=True, exist_ok=True)

    wt_snapshots: list[dict[str, Any]] = []
    source_commit = ps["frozen_release_baseline"]["source_commit"]
    infra_commit = ""
    docs_commit = ps["frozen_release_baseline"]["docs_commit"]

    for wt in ps["active_worktrees"]:
        info = inspect_worktree(wt)
        p = Path(wt["path"])
        if info["exists"] and info["dirty"]:
            _, diff_unstaged, _ = git_run(p, "diff")
            _, diff_staged, _ = git_run(p, "diff", "--cached")
            if diff_unstaged:
                (chk_out / f"{wt['name']}-unstaged.patch").write_text(diff_unstaged + "\n", encoding="utf-8")
            if diff_staged:
                (chk_out / f"{wt['name']}-staged.patch").write_text(diff_staged + "\n", encoding="utf-8")
        if info.get("exists"):
            if wt["name"] == "source":
                source_commit = info["head"]
            elif wt["name"] == "infra":
                infra_commit = info["head"]
            elif wt["name"] == "project":
                docs_commit = info["head"]
        wt_snapshots.append(info)

    ps["last_verified_checkpoint"] = {
        "checkpoint_id": chk_id,
        "timestamp": ts.isoformat(timespec="seconds"),
        "agent": agent,
        "source_commit": source_commit,
        "infra_commit": infra_commit,
        "docs_commit": docs_commit,
        "notes": note,
    }
    for wt in ps["active_worktrees"]:
        if wt["name"] == "infra" and infra_commit:
            wt["expected_commit"] = infra_commit
        elif wt["name"] == "project" and docs_commit:
            wt["expected_commit"] = docs_commit
    save_json(harness_dir / "project-state.json", ps)

    at["updated_at"] = ts.isoformat(timespec="seconds")
    save_json(harness_dir / "active-task.json", at)
    save_json(chk_out / "checkpoint-manifest.json", {
        "checkpoint_id": chk_id,
        "timestamp": ts.isoformat(timespec="seconds"),
        "agent": agent,
        "note": note,
        "worktrees": wt_snapshots,
        "active_task": at,
    })

    if write_handoff:
        cmd_handoff(harness_dir, harness_dir.parent / "AGENT_HANDOFF.md")

    return 0, {"ok": True, "checkpoint_id": chk_id, "checkpoint_path": str(chk_out), "worktrees": wt_snapshots}


def _render_handoff_markdown(ps: dict[str, Any], at: dict[str, Any], worktrees: list[dict[str, Any]]) -> str:
    chk = ps["last_verified_checkpoint"]
    frozen = ps["frozen_release_baseline"]
    lines = [
        "# Agent Handoff (Generated from Verified Harness State)",
        "",
        "Begin with [current state](CURRENT_PROJECT_STATE.md) and harness directory `harness/`.",
        "",
        "## Quick Resume Prompt (Agent Handoff)",
        "",
        "```text",
        "Resume ReLink from the latest verified checkpoint using the shared harness and active task.",
        "```",
        "",
        f"- **Active Task ID:** `{at['task_id']}` — {at['title']}",
        f"- **Task Status:** `{at['status']}` | **Agent Responsible:** `{at['agent_responsible']}`",
        f"- **Last Verified Checkpoint:** `{chk['checkpoint_id']}` at `{chk['timestamp']}`",
        f"- **Current project version:** `{ps['project_version']}` / build `{ps['build_number']}`",
        f"- **Frozen Source Baseline:** `{frozen['branch']}` @ `{frozen['source_commit']}` (`{frozen['version']}` / build `{frozen['build']}`)",
        "",
        "## Live Worktree State",
        "",
        "| Worktree | Branch | HEAD Commit | Dirty Files |",
        "| --- | --- | --- | --- |",
    ]
    for wt in worktrees:
        head_short = wt.get("head", "MISSING")[:12]
        dirty = f"{wt.get('dirty_count', 0)} modified/untracked"
        lines.append(f"| `{wt['name']}` | `{wt.get('branch', 'N/A')}` | `{head_short}` | {dirty} |")

    lines.extend(["", "## Completed Steps", ""])
    for step in at["completed_steps"]:
        lines.append(f"- [x] {step}")

    lines.extend(["", "## Pending Steps & Exact Next Action", ""])
    for step in at["pending_steps"]:
        lines.append(f"- [ ] {step}")
    lines.append(f"- **Next Action:** {at['next_action']}")

    lines.extend(["", "## Test Results & Blockers", ""])
    for tr in at["test_results"]:
        lines.append(f"- **{tr['status']}** `{tr['command']}` (`{tr['commit'][:7]}`): {tr['summary']}")
    for blk in ps["open_blockers"]:
        lines.append(f"- **{blk['classification']}** (`{blk['id']}`): {blk['summary']}")

    lines.extend(["", "## Owner Approvals & Recovery", ""])
    for apr in ps["owner_approvals"]:
        lines.append(f"- `{apr['id']}` (`{apr['operation']}` on `{apr['repository']}` @ `{apr['commit'][:12]}`): **{apr['status']}** — {apr['scope']}")
    lines.append("")
    lines.append("- **Recovery / Interruption Script:** `powershell -ExecutionPolicy Bypass -File .\\harness\\scripts\\resume.ps1` followed by `.\\harness\\scripts\\validate.ps1`.")
    lines.append("")
    return "\n".join(lines)


def cmd_handoff(harness_dir: Path, handoff_out: Path | None) -> tuple[int, dict[str, Any]]:
    ps = load_json(harness_dir / "project-state.json")
    at = load_json(harness_dir / "active-task.json")
    worktrees = [inspect_worktree(wt) for wt in ps["active_worktrees"]]
    md = _render_handoff_markdown(ps, at, worktrees)
    summary_path = harness_dir / "RESUME_SUMMARY.md"
    summary_path.write_text(md, encoding="utf-8")
    written = [str(summary_path)]
    if handoff_out is not None:
        handoff_out.write_text(md, encoding="utf-8")
        written.append(str(handoff_out))
    return 0, {"ok": True, "files": written}


def cmd_check_op(
    harness_dir: Path,
    operation: str,
    repo: str,
    commit: str,
    branch: str | None,
    target_path: str | None,
    dry_run: bool,
) -> tuple[int, dict[str, Any]]:
    ps = load_json(harness_dir / "project-state.json")
    perms = load_json(harness_dir / "permissions.json")
    reasons: list[str] = []

    if branch:
        for pat in perms["protected_branches"]:
            if fnmatch.fnmatch(branch, pat) and operation in {"direct_commit_to_protected", "force_push", "delete_branch"}:
                reasons.append(f"Protected branch '{branch}' (matches '{pat}') blocks '{operation}'.")

    if target_path:
        norm = target_path.replace("\\", "/")
        for pat in perms["sensitive_path_exclusions"]:
            if fnmatch.fnmatch(norm, pat) or norm.startswith(pat.rstrip("*")):
                reasons.append(f"Target path '{norm}' matches sensitive exclusion '{pat}'.")

    if operation.startswith("remote_") and repo not in perms["allowed_remote_destinations"] and repo != "heat-core/relink":
        reasons.append(f"Remote repository '{repo}' is outside allowed destinations without explicit owner review.")

    gated = {g["operation"]: g for g in perms["gated_operations"]}
    if operation in gated and gated[operation]["requires_explicit_owner_approval"]:
        matched = [
            apr for apr in ps["owner_approvals"]
            if apr["status"] == "approved"
            and apr["operation"] == operation
            and apr["repository"] == repo
            and apr["commit"] == commit
        ]
        if not matched:
            reasons.append(
                f"Operation '{operation}' on '{repo}' commit '{commit}' requires an active status='approved' entry in owner_approvals (prior/consumed/other-op approvals cannot cross-authorize)."
            )

    if operation == "sign_android_release":
        candidates = [
            (name, art) for name, art in ps["artifacts"].items()
            if name.endswith("_unsigned_android") and art.get("source_commit") == commit
        ]
        if len(candidates) != 1:
            reasons.append("Artifact guard failure: expected one registered unsigned Android host for this commit.")
        else:
            name, art = candidates[0]
            path = Path(art["path"])
            expected = perms["release_artifact_guards"].get(f"{name}_sha256", art["sha256"])
            if target_path and Path(target_path).resolve() != path.resolve():
                reasons.append("Artifact guard failure: signing target differs from the registered unsigned host.")
            if not path.is_file() or sha256_file(path).lower() != expected.lower():
                reasons.append("Artifact guard failure: the registered unsigned host is missing or its SHA-256 differs.")

    return (0 if not reasons else 3), {
        "allowed": len(reasons) == 0,
        "dry_run": dry_run,
        "operation": operation,
        "repository": repo,
        "commit": commit,
        "reasons": reasons,
    }


def _detect_workspace_root(harness_dir: Path) -> Path:
    resolved = harness_dir.resolve()
    for candidate in (resolved, *resolved.parents):
        if (candidate / "worktrees" / "source").exists() or (candidate / "worktrees" / "infra").exists():
            return candidate
    return resolved.parents[2] if len(resolved.parents) >= 3 else resolved.parent


def main(argv: list[str] | None = None) -> int:
    default_hdir = Path(__file__).resolve().parents[1]
    default_wroot = _detect_workspace_root(default_hdir)
    parser = argparse.ArgumentParser(description="ReLink Multi-Agent Harness CLI")
    parser.add_argument("--harness-dir", default=str(default_hdir))
    parser.add_argument("--workspace-root", default=str(default_wroot))
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_val = sub.add_parser("validate")
    p_val.add_argument("--source-root", default="")
    p_val.add_argument("--skip-hashes", action="store_true")

    p_res = sub.add_parser("resume")
    p_res.add_argument("--agent", default="agent-a")
    p_res.add_argument("--acquire-lease", action="store_true")
    p_res.add_argument("--force-stale", action="store_true")

    p_chk = sub.add_parser("checkpoint")
    p_chk.add_argument("--agent", default="agent-a")
    p_chk.add_argument("--note", default="Milestone checkpoint")
    p_chk.add_argument("--checkpoint-dir", default="")
    p_chk.add_argument("--write-handoff", action="store_true")

    p_hnd = sub.add_parser("handoff")
    p_hnd.add_argument("--out", default="")

    p_op = sub.add_parser("check-op")
    p_op.add_argument("--operation", required=True)
    p_op.add_argument("--repo", required=True)
    p_op.add_argument("--commit", required=True)
    p_op.add_argument("--branch", default="")
    p_op.add_argument("--target-path", default="")
    p_op.add_argument("--dry-run", action="store_true")

    args = parser.parse_args(argv)
    hdir = Path(args.harness_dir).resolve()
    wroot = Path(args.workspace_root).resolve()

    if args.cmd == "validate":
        sroot = Path(args.source_root).resolve() if args.source_root else (wroot / "worktrees" / "infra" if (wroot / "worktrees" / "infra").exists() else wroot / "worktrees" / "source")
        rc, out = cmd_validate(hdir, sroot, strict_hashes=not args.skip_hashes)
    elif args.cmd == "resume":
        rc, out = cmd_resume(hdir, wroot, args.agent, args.acquire_lease, args.force_stale)
    elif args.cmd == "checkpoint":
        cdir = Path(args.checkpoint_dir).resolve() if args.checkpoint_dir else (wroot / ".local" / "recovery" / "checkpoints")
        rc, out = cmd_checkpoint(hdir, cdir, args.agent, args.note, args.write_handoff)
    elif args.cmd == "handoff":
        hout = Path(args.out).resolve() if args.out else (hdir.parent / "AGENT_HANDOFF.md")
        rc, out = cmd_handoff(hdir, hout)
    elif args.cmd == "check-op":
        rc, out = cmd_check_op(hdir, args.operation, args.repo, args.commit, args.branch or None, args.target_path or None, args.dry_run)
    else:
        return 2

    print(json.dumps(out, indent=2, ensure_ascii=False))
    return rc


if __name__ == "__main__":
    sys.exit(main())
