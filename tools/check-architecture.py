#!/usr/bin/env python3
"""Verify architectural module boundaries without altering application code."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CORE_FORBIDDEN = re.compile(
    r"^\s*import\s+(?:android\.|androidx\.|app\.tether\.(?:helper|link|adb|telecom|ui|data)\.|app\.tether\.audio\.Helper)"
)
HELPER_FORBIDDEN = re.compile(
    r"^\s*import\s+(?:androidx\.|android\.telecom\.|app\.tether\.(?:ui|link|adb|telecom|data)\.)"
)
CLIENT_FORBIDDEN = re.compile(
    r"^\s*import\s+app\.tether\.(?:helper|adb|link|telecom|data|ui)\."
)
APP_MAIN_FORBIDDEN = re.compile(
    r"^\s*(?:import\s+.*\b(?:DebugReceiver|PipeProbeProvider)\b|.*\b(?:DebugReceiver|PipeProbeProvider)\b\s*[(.:])"
)
TETHERCORE_FORBIDDEN = re.compile(
    r"^\s*import\s+(?:SwiftUI|UIKit|CallKit|AVFoundation|AVFAudio)\b"
)


def scan_dir(rel_dir: str, suffixes: tuple[str, ...], pattern: re.Pattern[str], rule: str) -> list[str]:
    base = ROOT / rel_dir
    if not base.exists():
        return [f"Missing expected source directory: {rel_dir}"]
    errors: list[str] = []
    for file in sorted(base.rglob("*")):
        if not file.is_file() or file.suffix not in suffixes:
            continue
        text = file.read_text(encoding="utf-8", errors="replace")
        for idx, line in enumerate(text.splitlines(), 1):
            stripped = line.lstrip()
            if stripped.startswith(("//", "/*", "*")):
                continue
            if pattern.search(line):
                rel = file.relative_to(ROOT).as_posix()
                errors.append(f"{rel}:{idx}: {rule}: {stripped}")
    return errors


def main() -> int:
    errors: list[str] = []
    errors.extend(scan_dir("android/core/src/main", (".kt", ".java"), CORE_FORBIDDEN, ":core must stay pure JVM"))
    errors.extend(scan_dir("android/helper/src/main", (".kt", ".java"), HELPER_FORBIDDEN, ":helper must stay isolated from app/UI/Telecom"))
    errors.extend(scan_dir("android/client/src/main", (".kt", ".java"), CLIENT_FORBIDDEN, ":client must depend only on :core, never :app/:helper"))
    errors.extend(scan_dir("android/app/src/main", (".kt", ".java"), APP_MAIN_FORBIDDEN, "release main sources must not reference debug-only receivers/providers"))
    errors.extend(scan_dir("ios/TetherCore/Sources/TetherCore", (".swift",), TETHERCORE_FORBIDDEN, "TetherCore must stay UI/CallKit/AVFAudio-free"))

    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"\n{len(errors)} architecture boundary violation(s).", file=sys.stderr)
        return 1
    print("Architecture boundaries ok (:core pure JVM, :helper isolated, :client decoupled, main free of debug hooks, TetherCore headless).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
