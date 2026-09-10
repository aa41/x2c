#!/usr/bin/env python3
"""Install the canonical X2C Agent Skill into supported coding-agent directories.

Existing differing installations are never overwritten implicitly. --replace moves the old skill
to a timestamped backup before installing the new copy.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import sys
import time
import uuid
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple


SKILL_NAME = "x2c-android-integration"
TOOLS = ("codex", "claude", "cursor", "gemini", "copilot", "opencode")
PROJECT_DIRECTORIES: Dict[str, Path] = {
    "codex": Path(".agents/skills"),
    "claude": Path(".claude/skills"),
    "cursor": Path(".cursor/skills"),
    "gemini": Path(".gemini/skills"),
    "copilot": Path(".github/skills"),
    "opencode": Path(".opencode/skills"),
}


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Install the X2C integration skill")
    parser.add_argument(
        "--tool",
        action="append",
        choices=TOOLS + ("all",),
        required=True,
        help="Target coding agent; repeat for multiple tools or use all",
    )
    parser.add_argument("--scope", choices=("project", "user"), default="project")
    parser.add_argument("--target", default=".", help="Project root for project-scope installation")
    parser.add_argument("--replace", action="store_true", help="Back up and replace a differing installation")
    parser.add_argument("--check", action="store_true", help="Check installation without writing")
    parser.add_argument("--dry-run", action="store_true", help="Print planned destinations without writing")
    return parser.parse_args(argv)


def source_skill() -> Path:
    root = Path(__file__).resolve().parent.parent
    source = root / "skills" / SKILL_NAME
    if not (source / "SKILL.md").is_file():
        raise RuntimeError(f"Canonical skill is missing: {source}")
    return source


def user_base(tool: str) -> Path:
    home = Path.home()
    if tool == "codex":
        return Path(os.environ.get("CODEX_HOME", home / ".codex")) / "skills"
    if tool == "claude":
        return home / ".claude/skills"
    if tool == "cursor":
        return home / ".cursor/skills"
    if tool == "gemini":
        return home / ".gemini/skills"
    if tool == "copilot":
        return home / ".copilot/skills"
    if tool == "opencode":
        config = Path(os.environ.get("XDG_CONFIG_HOME", home / ".config"))
        return config / "opencode/skills"
    raise ValueError(tool)


def selected_tools(values: Sequence[str]) -> List[str]:
    if "all" in values:
        return list(TOOLS)
    return list(dict.fromkeys(values))


def iter_files(root: Path) -> Iterable[Path]:
    ignored = {"__pycache__", ".DS_Store"}
    for path in sorted(root.rglob("*")):
        if path.is_file() and not any(part in ignored for part in path.parts):
            yield path


def digest(root: Path) -> str:
    hasher = hashlib.sha256()
    for path in iter_files(root):
        relative = path.relative_to(root).as_posix().encode("utf-8")
        hasher.update(len(relative).to_bytes(4, "big"))
        hasher.update(relative)
        data = path.read_bytes()
        hasher.update(len(data).to_bytes(8, "big"))
        hasher.update(data)
    return hasher.hexdigest()


def copy_skill(source: Path, destination: Path, replace: bool) -> Tuple[str, str]:
    source_digest = digest(source)
    if destination.exists() or destination.is_symlink():
        if destination.is_dir() and not destination.is_symlink() and digest(destination) == source_digest:
            return "UP-TO-DATE", source_digest
        if not replace:
            return "CONFLICT", source_digest

    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.parent / f".{SKILL_NAME}.tmp-{uuid.uuid4().hex}"
    backup = destination.parent / f"{SKILL_NAME}.backup-{time.strftime('%Y%m%d-%H%M%S')}"
    try:
        shutil.copytree(
            source,
            temporary,
            ignore=shutil.ignore_patterns("__pycache__", ".DS_Store"),
        )
        if destination.exists() or destination.is_symlink():
            counter = 1
            while backup.exists() or backup.is_symlink():
                backup = destination.parent / f"{SKILL_NAME}.backup-{time.strftime('%Y%m%d-%H%M%S')}-{counter}"
                counter += 1
            destination.rename(backup)
        temporary.rename(destination)
    except Exception:
        if temporary.exists():
            shutil.rmtree(temporary)
        if backup.exists() and not destination.exists():
            backup.rename(destination)
        raise
    return (f"INSTALLED (backup={backup})" if backup.exists() else "INSTALLED"), source_digest


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    source = source_skill()
    source_digest = digest(source)
    project_root = Path(args.target).expanduser().resolve()
    if args.scope == "project" and not project_root.is_dir():
        print(f"Project target does not exist or is not a directory: {project_root}", file=sys.stderr)
        return 2
    failures = 0
    for tool in selected_tools(args.tool):
        base = project_root / PROJECT_DIRECTORIES[tool] if args.scope == "project" else user_base(tool)
        destination = base / SKILL_NAME
        if args.dry_run:
            print(f"DRY-RUN {tool}: {destination}")
            continue
        if args.check:
            if destination.is_dir() and not destination.is_symlink() and digest(destination) == source_digest:
                print(f"OK {tool}: {destination} sha256={source_digest}")
            elif destination.exists() or destination.is_symlink():
                print(f"MISMATCH {tool}: {destination}", file=sys.stderr)
                failures += 1
            else:
                print(f"MISSING {tool}: {destination}", file=sys.stderr)
                failures += 1
            continue
        status, installed_digest = copy_skill(source, destination, args.replace)
        if status == "CONFLICT":
            print(
                f"CONFLICT {tool}: {destination}; rerun with --replace to preserve it as a backup",
                file=sys.stderr,
            )
            failures += 1
        else:
            print(f"{status} {tool}: {destination} sha256={installed_digest}")
    return 2 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
