#!/usr/bin/env python3
"""Prepare and validate immutable Watchio public-release assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

REPOSITORY = "IAMSKORPZ/Watchio-IPTV"
APK_NAME = "Watchio-IPTV.apk"
SEMVER = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
SHA = re.compile(r"^[0-9a-fA-F]{40}$")
CHECKSUM = re.compile(r"^[0-9a-fA-F]{64}$")


class ReleaseValidationError(ValueError):
    pass


def semantic_version(value: str) -> tuple[int, int, int]:
    match = SEMVER.fullmatch(value)
    if not match:
        raise ReleaseValidationError("versionName must use canonical X.Y.Z syntax")
    return tuple(int(part) for part in match.groups())


def load_manifest(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ReleaseValidationError(f"Unable to read stable manifest: {error}") from error
    validate_manifest_shape(value)
    return value


def validate_manifest_shape(manifest: dict) -> None:
    required = {
        "schemaVersion", "channel", "versionCode", "versionName",
        "minimumSupportedVersionCode", "mandatory", "publishedAt",
        "releaseNotes", "githubRelease", "apk",
    }
    if set(manifest) != required:
        raise ReleaseValidationError("Stable manifest fields do not match schema version 1")
    if manifest["schemaVersion"] != 1 or manifest["channel"] != "stable":
        raise ReleaseValidationError("Stable manifest channel or schema is invalid")
    if not isinstance(manifest["versionCode"], int) or manifest["versionCode"] <= 0:
        raise ReleaseValidationError("Stable versionCode must be positive")
    semantic_version(manifest["versionName"])
    if not isinstance(manifest["minimumSupportedVersionCode"], int) or manifest["minimumSupportedVersionCode"] <= 0:
        raise ReleaseValidationError("minimumSupportedVersionCode must be positive")
    if not isinstance(manifest["mandatory"], bool):
        raise ReleaseValidationError("mandatory must be boolean")
    if not isinstance(manifest["releaseNotes"], list) or not manifest["releaseNotes"] or not all(
        isinstance(note, str) and note.strip() for note in manifest["releaseNotes"]
    ):
        raise ReleaseValidationError("releaseNotes must contain non-empty strings")
    apk = manifest["apk"]
    if set(apk) != {"fileName", "downloadUrl", "sha256"}:
        raise ReleaseValidationError("APK metadata fields are invalid")
    if apk["fileName"] != APK_NAME or not CHECKSUM.fullmatch(apk["sha256"]):
        raise ReleaseValidationError("APK filename or checksum is invalid")
    validate_github_url(manifest["githubRelease"], f"/{REPOSITORY}/releases/tag/")
    validate_github_url(apk["downloadUrl"], f"/{REPOSITORY}/releases/download/")


def validate_github_url(value: str, prefix: str) -> None:
    parsed = urlparse(value)
    if parsed.scheme != "https" or parsed.hostname != "github.com" or parsed.port is not None:
        raise ReleaseValidationError("Release URL must use trusted GitHub HTTPS origin")
    if parsed.username or parsed.password or parsed.query or parsed.fragment or not parsed.path.startswith(prefix):
        raise ReleaseValidationError("Release URL path is invalid")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def prepare(
    base_manifest_path: Path,
    source_apk: Path,
    output_dir: Path,
    version_name: str,
    version_code: int,
    source_sha: str,
    release_notes: list[str],
    published_at: str,
) -> dict:
    base = load_manifest(base_manifest_path)
    requested = semantic_version(version_name)
    if requested <= semantic_version(base["versionName"]):
        raise ReleaseValidationError("versionName must be newer than current stable version")
    if version_code <= base["versionCode"]:
        raise ReleaseValidationError("versionCode must be greater than current stable versionCode")
    if not SHA.fullmatch(source_sha):
        raise ReleaseValidationError("source SHA must contain exactly 40 hexadecimal characters")
    if not source_apk.is_file() or source_apk.stat().st_size <= 0:
        raise ReleaseValidationError("Release APK is missing or empty")
    notes = [note.strip() for note in release_notes if note.strip()]
    if not notes:
        raise ReleaseValidationError("At least one release note is required")
    try:
        datetime.fromisoformat(published_at.replace("Z", "+00:00"))
    except ValueError as error:
        raise ReleaseValidationError("publishedAt must be ISO-8601") from error

    output_dir.mkdir(parents=True, exist_ok=True)
    apk_path = output_dir / APK_NAME
    shutil.copyfile(source_apk, apk_path)
    checksum = sha256(apk_path)
    tag = f"v{version_name}"
    release_url = f"https://github.com/{REPOSITORY}/releases/tag/{tag}"
    download_url = f"https://github.com/{REPOSITORY}/releases/download/{tag}/{APK_NAME}"
    manifest = {
        "schemaVersion": 1,
        "channel": "stable",
        "versionCode": version_code,
        "versionName": version_name,
        "minimumSupportedVersionCode": base["minimumSupportedVersionCode"],
        "mandatory": False,
        "publishedAt": published_at,
        "releaseNotes": notes,
        "githubRelease": release_url,
        "apk": {"fileName": APK_NAME, "downloadUrl": download_url, "sha256": checksum},
    }
    validate_manifest_shape(manifest)
    (output_dir / "stable.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output_dir / "SHA256SUMS.txt").write_text(f"{checksum.lower()}  {APK_NAME}\n", encoding="ascii")
    (output_dir / "SOURCE_SHA.txt").write_text(source_sha.lower() + "\n", encoding="ascii")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-manifest", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--release-notes-file", type=Path, required=True)
    parser.add_argument("--published-at", default=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"))
    args = parser.parse_args()
    notes = args.release_notes_file.read_text(encoding="utf-8").splitlines()
    prepare(
        args.base_manifest,
        args.apk,
        args.output_dir,
        args.version_name,
        args.version_code,
        args.source_sha,
        notes,
        args.published_at,
    )


if __name__ == "__main__":
    main()
