#!/usr/bin/env python3
"""Check the Android and iOS wire constants and release metadata."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = "android/core/src/main/java/app/tether/"
SWIFT = "ios/TetherCore/Sources/TetherCore/"


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def equal(label, actual, expected):
    if actual != expected:
        raise SystemExit(f"{label}: got {actual!r}, expected {expected!r}")


def section(text, name):
    match = re.search(rf"\b(?:enum|object|struct|class) (?:class )?{name}\b[^{{]*\{{(.*?)^\}}", text, re.M | re.S)
    if not match:
        raise SystemExit(f"Missing declaration: {name}")
    return match[1]


def constant(text, name):
    value = re.search(
        rf"\b{re.escape(name)}(?:\s*:\s*[\w.]+)?\s*=\s*"
        r'(?:Data\(|CBUUID\(string:\s*|UUID\.fromString\()?(\"[^\"\n]+\"|0x[0-9a-fA-F]+|[0-9_]+)',
        text,
    )
    if not value:
        raise SystemExit(f"Missing constant: {name}")
    raw = value[1]
    return json.loads(raw) if raw.startswith('"') else int(raw, 16 if raw.startswith("0x") else 10)


def kotlin_name(name):
    return re.sub(r"(?<!^)([A-Z])", r"_\1", name).upper()


def check(label, spec, kotlin, swift, aliases=None):
    for name, expected in spec.items():
        equal(f"{label} Android {name}", constant(kotlin, (aliases or {}).get(name, kotlin_name(name))), expected)
        equal(f"{label} iOS {name}", constant(swift, name), expected)


def main():
    spec = json.loads(read("protocol/compatibility.json"))
    kt_crypto = read(KOTLIN + "core/Crypto.kt")
    sw_crypto = read(SWIFT + "Handshake.swift")
    kt_records = read(KOTLIN + "core/Records.kt")
    kt_messages = read(KOTLIN + "core/Messages.kt")
    sw_messages = read(SWIFT + "Messages.swift")
    kt_proto = section(kt_crypto, "Proto")
    sw_proto = section(sw_crypto, "Proto")
    check("BLE", spec["ble"], read("android/app/src/main/java/app/tether/link/BlePeripheral.kt"),
          section(read(SWIFT + "TetherLink.swift"), "TetherUUID"),
          {"psm": "PSM_CHAR", "rx": "RX_CHAR", "tx": "TX_CHAR", "wake": "WAKE_CHAR"})
    check("Wire version", {"version": spec["version"]}, kt_proto, sw_proto)
    check("Handshake", spec["handshake"], kt_proto, sw_proto + section(sw_crypto, "HandshakeMode"),
          {"resume": "MODE_RESUME", "pair": "MODE_PAIR", "helloLength": "HELLO_LEN", "helloAckLength": "HELLO_ACK_LEN"})
    check("Channel", spec["channels"], kt_proto, sw_proto,
          {"channelControl": "CH_CONTROL", "channelAudio": "CH_AUDIO", "channelFinish": "CH_FINISH"})
    check("Record", spec["records"], section(kt_records, "Kind"), section(read(SWIFT + "Records.swift"), "RecordKind"))
    check("Reject", spec["reject"], section(kt_records, "Reject"), section(sw_crypto, "RejectReason"))
    check("Pairing", spec["pairing"], section(kt_crypto, "PairingCode"), section(read(SWIFT + "Util.swift"), "PairingCode"))
    check("Codec", spec["codecs"], section(kt_messages, "AudioFrame"), section(sw_messages, "AudioFrame"))
    kt_codecs = read(KOTLIN + "audio/Codecs.kt")
    sw_audio = read("ios/Tether/Audio/CallAudio.swift")
    for name, variable in (("opusRate", "rate"), ("opusBitrate", "bitrate")):
        equal(f"{name} Android", constant(kt_codecs.split("class OpusCodec", 1)[1], variable), spec["codecDefaults"][name])
        equal(f"{name} iOS", constant(sw_audio.split("class OpusCoder", 1)[1], variable), spec["codecDefaults"][name])
    for label, text in (("Android", kt_codecs), ("iOS", sw_audio)):
        equal(f"PCMU rate {label}", constant(text, "rate"), spec["codecDefaults"]["pcmuRate"])
    check("Extensions", spec["extensions"], section(kt_messages, "Ext"), section(read(SWIFT + "Extensions.swift"), "Ext"),
          {"ratePerSecond": "RATE_PER_S"})
    kotlin_types = re.findall(r'=\s*"([^"]+)"', section(kt_messages, "T"))
    swift_types = re.findall(r'=\s*"([^"]+)"', section(sw_messages, "MsgType"))
    equal("Android/iOS control message types", sorted(kotlin_types), sorted(swift_types))
    vectors = json.loads(read("protocol/vectors.json"))
    equal("Swift protocol fixture copy", json.loads(read("ios/TetherCore/Tests/TetherCoreTests/vectors.json")), vectors)
    release = read("VERSION").strip()
    version = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z]+\.?(\d+)?)?", release)
    if not version:
        raise SystemExit(f"Invalid VERSION: {release}")
    major, minor, patch = (int(n) for n in version.groups()[:3])
    build = major * 1_000_000 + minor * 10_000 + patch * 100 + (
        min(int(version[4] or 0), 98) if "-" in release else 99
    )
    for key, expected in {
        "MARKETING_VERSION": release.split("-")[0], "CURRENT_PROJECT_VERSION": str(build), "TETHER_RELEASE_ID": release,
    }.items():
        equal(f"XcodeGen {key}", re.findall(rf'^\s*{key}: "([^"]+)"$', read("ios/project.yml"), re.M), [expected])
        equal(f"Xcode {key}", re.findall(rf'\b{key} = "?([^";]+)"?;', read("ios/Tether.xcodeproj/project.pbxproj")), [expected] * 2)
    print(f"{release}, build {build}, wire {spec['version']}: protocol and version checks passed")


if __name__ == "__main__":
    main()
