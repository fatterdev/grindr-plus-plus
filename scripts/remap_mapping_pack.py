#!/usr/bin/env python3
"""First-pass remap of a mapping pack onto a new JADX source tree.

This is a lab/publish helper, not a runtime component. It intentionally
soft-skips anything it cannot prove: a wrong R8 name is worse than an empty
name (see ADR 0001).

Usage:
  python scripts/remap_mapping_pack.py \
    --source mapping-packs/194910.json \
    --sources tmp-grid-lab/gplay-195444/jadx/sources \
    --version-code 195444 --version-name 26.24.0 \
    --out mapping-packs/195444.json
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import zipfile
from pathlib import Path
from typing import Any

CLASS_KINDS = {"class"}
MEMBER_KINDS = {"method", "field"}

# Member keys sometimes use a different prefix than their owning class symbol.
# Keep the aliases explicit so an unresolved member is soft-skipped rather than
# accepted on an unverified owner.
OWNER_ALIASES = {
    "http.userSession": "core.userSession",
    "http.deviceInfo": "core.deviceInfo",
    "http.userAgent": "core.userAgent",
    "EnableUnlimited.userSession": "core.userSession",
}


def iter_java(sources: Path):
    for path in sources.rglob("*.java"):
        yield path


def class_path(sources: Path, name: str) -> Path | None:
    if not name:
        return None
    if "." in name:
        rel = name.replace(".", "/") + ".java"
    else:
        rel = "defpackage/" + name + ".java"
    p = sources / rel
    return p if p.is_file() else None


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, offset
        shift += 7


def _dex_string(data: bytes, offset: int) -> str:
    length, offset = _uleb128(data, offset)
    return data[offset : offset + length].decode("utf-8", errors="replace")


def _dex_descriptors(data: bytes) -> set[str]:
    """Return descriptors from DEX ``class_defs`` only.

    Reading the string pool would also accept classes that are merely
    referenced (method signatures, type instructions) but not defined in this
    APK. ``class_defs`` is the authoritative set for the remap.
    """
    if len(data) < 0x70 or data[:4] != b"dex\n":
        return set()
    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 0x38)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 0x40)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 0x60)
    if not (string_ids_size and type_ids_size and class_defs_size):
        return set()

    strings: list[str] = []
    for index in range(string_ids_size):
        off = struct.unpack_from("<I", data, string_ids_off + 4 * index)[0]
        strings.append(_dex_string(data, off))

    types: list[str] = []
    for index in range(type_ids_size):
        string_index = struct.unpack_from("<I", data, type_ids_off + 4 * index)[0]
        if string_index >= len(strings):
            return set()
        types.append(strings[string_index])

    descriptors: set[str] = set()
    for index in range(class_defs_size):
        class_index = struct.unpack_from("<I", data, class_defs_off + 32 * index)[0]
        if class_index >= len(types):
            continue
        descriptor = types[class_index]
        if descriptor.startswith("L") and descriptor.endswith(";"):
            descriptors.add(descriptor)
    return descriptors


def dex_class_descriptors(apk: Path | None, dex_dir: Path | None) -> set[str]:
    """Collect defined class descriptors from root DEX files.

    JADX can fail on individual classes; the DEX ``class_defs`` entry is the
    ground truth for "this class still exists".
    """
    descriptors: set[str] = set()
    if apk and apk.is_file():
        with zipfile.ZipFile(apk) as zf:
            for name in zf.namelist():
                if name.endswith(".dex") and "/" not in name:
                    descriptors |= _dex_descriptors(zf.read(name))
    if dex_dir and dex_dir.is_dir():
        for path in dex_dir.glob("*.dex"):
            descriptors |= _dex_descriptors(path.read_bytes())
    return descriptors


def descriptor_for(name: str) -> str:
    return "L" + name.replace(".", "/") + ";"


def file_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def simple_name(path: Path) -> str:
    return path.stem


def class_name_from_path(path: Path) -> str:
    """Return the R8/FQCN-style name used by the pack."""
    parts = list(path.with_suffix("").parts)
    if "sources" in parts:
        parts = parts[parts.index("sources") + 1 :]
    # JADX keeps inner-class $ in the file name; keep it.
    return ".".join(parts)


def fingerprint_regex(fingerprint: str) -> re.Pattern[str] | None:
    fp = fingerprint.strip()
    if not fp or fp.startswith("struct:"):
        return None
    if fp.startswith("field "):
        needle = fp[len("field ") :].strip()
        # e.g. "field AdvertisingIdClient.Info" -> the field type appears
        # in a declaration. Keep the search broad; callers score candidates.
        return re.compile(r"\b" + re.escape(needle.split()[0]) + r"\b")
    if fp.startswith("implements "):
        needle = fp[len("implements ") :].strip().split()[0]
        return re.compile(
            r"\b(?:implements|extends)\s+[^\n{]*\b" + re.escape(needle) + r"\b"
        )
    return re.compile(re.escape(fp))


def find_by_fingerprint(
    sources: Path, fingerprint: str, prefer_short: bool
) -> list[Path]:
    """Return candidate class files for a fingerprint.

    Supports the fingerprint forms used by the packs:
      - plain string (search exact substring)
      - ``field Type`` (search a field declaration/type)
      - ``implements Type`` (search class declaration)
    ``struct:`` fingerprints are not resolved here.
    """
    pattern = fingerprint_regex(fingerprint)
    if pattern is None:
        return []
    candidates: list[Path] = []
    for path in iter_java(sources):
        text = file_text(path)
        if pattern.search(text):
            candidates.append(path)
    if prefer_short:
        candidates.sort(key=lambda p: (p.parent.name != "defpackage", str(p)))
    return candidates


def member_exists(path: Path, name: str, kind: str) -> bool:
    if not name:
        return False
    text = file_text(path)
    if kind == "method":
        return re.search(r"\b" + re.escape(name) + r"\s*\(", text) is not None
    return re.search(r"\b" + re.escape(name) + r"\b\s*[;=]", text) is not None


def owner_key(key: str, symbols: dict[str, Any]) -> str | None:
    for suffix in ("_METHOD", "_FIELD"):
        if key.endswith(suffix):
            base = key[: -len(suffix)]
            if base in symbols:
                return base
    # Common sibling pattern: retrofit.successValue -> retrofit.success
    for suffix in ("Value", "Field", "Method", "Class"):
        if key.endswith(suffix):
            base = key[: -len(suffix)]
            if base in symbols:
                return base
    return None


def remap(
    source: dict[str, Any],
    sources: Path,
    version_code: int,
    version_name: str,
    descriptors: set[str],
) -> tuple[dict[str, Any], dict[str, Any]]:
    src_symbols: dict[str, Any] = source.get("symbols", {})
    out_symbols: dict[str, Any] = {}
    resolved: dict[str, str] = {}
    report: dict[str, Any] = {
        "class_total": 0,
        "class_exact": 0,
        "class_fingerprint": 0,
        "class_missing": [],
        "fingerprint_mismatch": [],
        "dex_only": [],
        "member_total": 0,
        "member_exact": 0,
        "member_missing": [],
    }

    # Pass 1: class symbols.
    for key, sym in src_symbols.items():
        kind = sym.get("kind", "class")
        if kind not in CLASS_KINDS:
            continue
        report["class_total"] += 1
        name = str(sym.get("name", "") or "")
        out = dict(sym)
        path = class_path(sources, name) if name else None
        if name and (path is not None or descriptor_for(name) in descriptors):
            report["class_exact"] += 1
            resolved[key] = name
            out_symbols[key] = out
            continue

        fp = str(sym.get("fingerprint", "") or "")
        candidates = find_by_fingerprint(sources, fp, prefer_short=True) if fp else []
        # Prefer a defpackage candidate for short R8 names; otherwise the
        # first candidate. Multiple candidates are ambiguous: soft-skip.
        if len(candidates) == 1:
            cand = candidates[0]
            new_name = (
                simple_name(cand)
                if cand.parent.name == "defpackage"
                else class_name_from_path(cand)
            )
            out["name"] = new_name
            out["note"] = (out.get("note", "") + " | remapped by fingerprint").strip(
                " |"
            )
            report["class_fingerprint"] += 1
            resolved[key] = new_name
            out_symbols[key] = out
            continue

        # Exact name absent and no unique fingerprint: explicit soft-skip.
        out["name"] = ""
        reason = "no exact name on 26.24.0"
        if fp:
            reason += f"; fingerprint candidates={len(candidates)}"
        out["note"] = (out.get("note", "") + " | " + reason).strip(" |")
        report["class_missing"].append(
            {"key": key, "old": name, "fingerprint": fp, "candidates": len(candidates)}
        )
        out_symbols[key] = out

    # Pass 2: member symbols.
    for key, sym in src_symbols.items():
        kind = sym.get("kind", "class")
        if kind not in MEMBER_KINDS:
            continue
        report["member_total"] += 1
        out = dict(sym)
        name = str(sym.get("name", "") or "")
        owner = sym.get("owner") or sym.get("class")
        if not owner:
            owner = owner_key(key, src_symbols)
        if not owner and "." in key:
            prefix = key.rsplit(".", 1)[0]
            owner = OWNER_ALIASES.get(prefix, prefix)

        ok = False
        if name and owner:
            owner_name = resolved.get(owner, str(owner))
            p = class_path(sources, owner_name)
            ok = p is not None and member_exists(p, name, kind)
        if ok and name:
            report["member_exact"] += 1
            out_symbols[key] = out
            continue

        # Unknown owner or absent member: explicit soft-skip, never keep a
        # stale member name on faith.
        out["name"] = ""
        reason = "member not proven on 26.24.0"
        if not owner:
            reason = "member owner unresolved on 26.24.0"
        out["note"] = (out.get("note", "") + " | " + reason).strip(" |")
        report["member_missing"].append(
            {"key": key, "old": name, "kind": kind, "owner": owner}
        )
        out_symbols[key] = out

    # Hooks: never claim runtime success from a static remap.
    hooks = source.get("hooks")
    out_hooks: dict[str, Any] = {}
    if isinstance(hooks, dict):
        for hook, meta in hooks.items():
            m = dict(meta) if isinstance(meta, dict) else {}
            if m.get("status") == "skipped":
                m["status"] = "skipped"
                m["reason"] = (m.get("reason", "") + " | 26.24.0 static remap").strip(
                    " |"
                )
            else:
                m["status"] = "unverified"
                m["reason"] = "26.24.0 static remap; runtime E2E pending"
            out_hooks[hook] = m

    out = {
        "schemaVersion": 2,
        "versionName": version_name,
        "versionCode": version_code,
        "confidence": "static-jadx-remap",
        "generatedFrom": (
            f"JADX decompile of Play {version_name}/{version_code} (px_9a, minSdk 32) "
            f"+ name/fingerprint remap from {source.get('versionName')}/{source.get('versionCode')} "
            "(2026-11-12). Unresolved sites are explicit soft-skips (empty name), not reused "
            "old R8 names. Hooks are unverified until runtime E2E."
        ),
        "symbols": out_symbols,
    }
    if out_hooks:
        out["hooks"] = out_hooks
    return out, report


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True)
    ap.add_argument("--sources", required=True)
    ap.add_argument("--apk", help="APK whose root classes*.dex are used as class truth")
    ap.add_argument("--dex-dir", help="directory of extracted classes*.dex")
    ap.add_argument("--version-code", type=int, required=True)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--report")
    args = ap.parse_args()

    source = json.loads(Path(args.source).read_text(encoding="utf-8"))
    sources = Path(args.sources)
    if not sources.is_dir():
        raise SystemExit(f"missing JADX sources: {sources}")
    descriptors = dex_class_descriptors(
        Path(args.apk) if args.apk else None,
        Path(args.dex_dir) if args.dex_dir else None,
    )

    out, report = remap(
        source, sources, args.version_code, args.version_name, descriptors
    )
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    report_path = (
        Path(args.report) if args.report else out_path.with_suffix(".report.json")
    )
    report_path.write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    print(
        f"classes: exact={report['class_exact']} fingerprint={report['class_fingerprint']} "
        f"missing={len(report['class_missing'])}/{report['class_total']}"
    )
    print(
        f"members: exact={report['member_exact']} missing={len(report['member_missing'])}/"
        f"{report['member_total']}"
    )
    print(f"wrote {out_path} and {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
