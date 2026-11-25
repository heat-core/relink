#!/usr/bin/env python3
"""Check a Release app before packaging it for owner re-signing."""

import os
import plistlib
import re
import subprocess
from pathlib import Path

app = Path(os.environ["APP_PATH"])
info = plistlib.loads((app / "Info.plist").read_bytes())
release = Path("VERSION").read_text(encoding="utf-8").strip()
build = re.search(r'^    CURRENT_PROJECT_VERSION: "(\d+)"$', Path("ios/project.yml").read_text(encoding="utf-8"), re.M)[1]
expected = {
    "CFBundleIdentifier": "app.tether.ios.enhanced",
    "CFBundleShortVersionString": release.split("-")[0],
    "CFBundleVersion": build,
    "TetherReleaseIdentifier": release,
    "TetherGitCommit": os.environ["GITHUB_SHA"],
    "TetherCommunicationNotifications": "NO",
    "DTPlatformName": "iphoneos",
}
for key, value in expected.items():
    if info.get(key) != value:
        raise SystemExit(f"{key}: got {info.get(key)!r}, expected {value!r}")

modes = set(info.get("UIBackgroundModes", []))
if modes != {"bluetooth-central", "audio", "voip"}:
    raise SystemExit(f"Unexpected iPhone background modes: {sorted(modes)}")
for key in ("NSBluetoothAlwaysUsageDescription", "NSMicrophoneUsageDescription", "NSCameraUsageDescription"):
    if not info.get(key):
        raise SystemExit(f"Missing {key}")
if (app / "embedded.mobileprovision").exists() or (app / "_CodeSignature/CodeResources").exists():
    raise SystemExit("Expected an app bundle for owner re-signing")
if list(app.rglob("*.appex")):
    raise SystemExit("IPA includes app extensions that need a separate re-signing review")

executable = app / info["CFBundleExecutable"]
architectures = subprocess.check_output(["lipo", "-archs", executable], text=True).split()
if "arm64" not in architectures:
    raise SystemExit(f"Missing iPhone arm64 executable: {architectures}")
binary = subprocess.check_output(["otool", "-L", executable], text=True)
for framework in ("CoreBluetooth", "CallKit"):
    if f"/{framework}.framework/" not in binary:
        raise SystemExit(f"Missing linked framework: {framework}")
if not any(f"/{framework}.framework/" in binary for framework in ("AVFoundation", "AVFAudio")):
    raise SystemExit("Missing linked audio framework")
signature = subprocess.run(["codesign", "-d", "--entitlements", "-", executable], capture_output=True)
if signature.stdout and plistlib.loads(signature.stdout):
    raise SystemExit("Unexpected code-signing entitlements in owner re-signing build")
print(f"{expected['CFBundleIdentifier']} {release} ({build}), arm64, unsigned for owner re-signing")
