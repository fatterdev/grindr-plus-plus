#!/usr/bin/env python3
"""Validate install-availability.json against the mapping and PairIP packs.

The JSON is the server-side availability matrix used by GrindMod's Install
screen. It must only advertise versions that actually have a patch, and it
must not claim a working status for a version that has no pack.

Usage:
    python scripts/validate_install_availability.py
    python scripts/validate_install_availability.py --sync
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "install-availability.json"
BUNDLED = ROOT / "app" / "src" / "main" / "assets" / "install-availability.json"
MAPPING_INDEX = ROOT / "mapping-packs" / "index.json"
MAPPING_DIR = ROOT / "mapping-packs"
PAIRIP_DIR = ROOT / "pairip-decrypt"

STATUSES = {"working", "not_working", "untested"}
DOWNLOADS = {"aurora", "url", "unavailable"}
PATCHES = {"mapping", "mapping+pairip", "none"}


def fail(message: str, errors: list[str]) -> None:
    errors.append(message)


def load_json(path: Path, errors: list[str]) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        fail(f"missing file: {path.relative_to(ROOT)}", errors)
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path.relative_to(ROOT)}: {exc}", errors)
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--sync", action="store_true", help="copy the root matrix to the bundled asset"
    )
    args = parser.parse_args()

    errors: list[str] = []
    data = load_json(SOURCE, errors)
    if data is None:
        return 1

    if data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1", errors)
    if not str(data.get("moduleBuild", "")).strip():
        fail("moduleBuild must be set", errors)
    if not str(data.get("updatedAt", "")).strip():
        fail("updatedAt must be set", errors)

    mapping_index = load_json(MAPPING_INDEX, errors) or {}
    indexed_codes = {
        int(pack.get("versionCode", 0))
        for pack in mapping_index.get("packs", [])
        if int(pack.get("versionCode", 0)) > 0
    }

    versions = data.get("versions")
    if not isinstance(versions, list) or not versions:
        fail("versions must be a non-empty list", errors)
        versions = []

    seen: set[int] = set()
    for index, entry in enumerate(versions):
        where = f"versions[{index}]"
        code = int(entry.get("versionCode", 0))
        if code <= 0:
            fail(f"{where}: invalid versionCode", errors)
            continue
        if code in seen:
            fail(f"{where}: duplicate versionCode {code}", errors)
        seen.add(code)

        if not str(entry.get("versionName", "")).strip():
            fail(f"{where}: versionName must be set", errors)

        download = entry.get("download")
        if download not in DOWNLOADS:
            fail(f"{where}: download must be one of {sorted(DOWNLOADS)}", errors)
        if download == "url" and not str(entry.get("apkUrl", "")).strip():
            fail(f"{where}: download=url requires apkUrl", errors)

        patch = entry.get("patch")
        if patch not in PATCHES:
            fail(f"{where}: patch must be one of {sorted(PATCHES)}", errors)

        for delivery in ("morphe", "alloy"):
            status = entry.get(delivery)
            if status not in STATUSES:
                fail(f"{where}: {delivery} must be one of {sorted(STATUSES)}", errors)

        if not isinstance(entry.get("pairip"), bool):
            fail(f"{where}: pairip must be a boolean", errors)

        if code not in indexed_codes:
            fail(
                f"{where}: versionCode {code} is not in mapping-packs/index.json",
                errors,
            )
        if (
            patch in {"mapping", "mapping+pairip"}
            and not (MAPPING_DIR / f"{code}.json").is_file()
        ):
            fail(f"{where}: mapping pack missing: mapping-packs/{code}.json", errors)
        if entry.get("pairip") and not (PAIRIP_DIR / str(code) / "pack.json").is_file():
            fail(
                f"{where}: pairip=true but pairip-decrypt/{code}/pack.json is missing",
                errors,
            )
        if patch == "mapping+pairip" and not entry.get("pairip"):
            fail(f"{where}: patch=mapping+pairip but pairip is false", errors)
        if patch == "mapping" and entry.get("pairip"):
            fail(f"{where}: pairip=true requires patch=mapping+pairip", errors)

    if errors:
        print("install-availability.json: FAILED", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    if args.sync:
        shutil.copyfile(SOURCE, BUNDLED)
        print(f"synced {SOURCE.relative_to(ROOT)} -> {BUNDLED.relative_to(ROOT)}")
    elif not BUNDLED.is_file() or BUNDLED.read_bytes() != SOURCE.read_bytes():
        print(
            "warning: bundled asset differs from the root matrix; run with --sync",
            file=sys.stderr,
        )

    print(
        f"install-availability.json: OK ({len(versions)} versions, "
        f"moduleBuild={data['moduleBuild']}, updatedAt={data['updatedAt']})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
