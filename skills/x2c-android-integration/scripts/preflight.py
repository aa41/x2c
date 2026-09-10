#!/usr/bin/env python3
"""Read-only X2C Android integration preflight.

The checks are intentionally conservative and evidence-based. They identify required changes before
integration; the X2C compiler and Android build remain the authoritative validators.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple


SEVERITY_ORDER = {"PASS": 0, "WARN": 1, "BLOCKER": 2}
COMPONENTS = ("activity", "activity-alias", "service", "receiver", "provider")
PLUGIN_HOST_ARTIFACTS = (
    "x2c-runtime",
    "x2c-plugin-api",
    "x2c-plugin-base",
    "x2c-plugin-loader",
    "x2c-plugin-runtime",
)
PLUGIN_PRODUCER_ARTIFACTS = (
    "x2c-runtime",
    "x2c-plugin-base",
    "x2c-plugin-runtime",
)


class Report:
    def __init__(self) -> None:
        self.items: List[Dict[str, str]] = []

    def add(self, severity: str, code: str, message: str, evidence: str = "") -> None:
        self.items.append(
            {"severity": severity, "code": code, "message": message, "evidence": evidence}
        )

    def counts(self) -> Dict[str, int]:
        return {
            severity: sum(item["severity"] == severity for item in self.items)
            for severity in SEVERITY_ORDER
        }

    def has_blockers(self) -> bool:
        return any(item["severity"] == "BLOCKER" for item in self.items)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Audit an Android module before X2C integration")
    parser.add_argument("--project", default=".", help="Gradle project root")
    parser.add_argument("--module", required=True, help="Gradle module path, for example :feature")
    parser.add_argument("--module-dir", help="Explicit producer/library directory")
    parser.add_argument("--host-module", help="Optional host application module path")
    parser.add_argument("--host-dir", help="Explicit host application directory")
    parser.add_argument("--mode", choices=("plugin", "normal"), required=True)
    parser.add_argument("--format", choices=("text", "json"), default="text")
    return parser.parse_args(argv)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def build_file(directory: Path) -> Optional[Path]:
    for name in ("build.gradle.kts", "build.gradle"):
        candidate = directory / name
        if candidate.is_file():
            return candidate
    return None


def settings_files(root: Path) -> List[Path]:
    return [path for path in (root / "settings.gradle.kts", root / "settings.gradle") if path.is_file()]


def resolve_module(root: Path, module: Optional[str], explicit: Optional[str]) -> Optional[Path]:
    if explicit:
        path = Path(explicit)
        return (path if path.is_absolute() else root / path).resolve()
    if not module or not re.fullmatch(r"(?::[A-Za-z0-9_.-]+)+", module):
        return None
    relative = Path(*module.lstrip(":").split(":"))
    direct = (root / relative).resolve()
    if build_file(direct):
        return direct
    escaped = re.escape(module)
    patterns = (
        rf"project\(\s*[\"']{escaped}[\"']\s*\)\.projectDir\s*=\s*file\(\s*[\"']([^\"']+)",
        rf"project\(\s*[\"']{escaped}[\"']\s*\)\.projectDir\s*=\s*new\s+File\([^,]+,\s*[\"']([^\"']+)",
    )
    for settings in settings_files(root):
        text = read_text(settings)
        for pattern in patterns:
            match = re.search(pattern, text)
            if match:
                return (root / match.group(1)).resolve()
    return direct


def source_files(directory: Path, suffixes: Tuple[str, ...]) -> Iterable[Path]:
    source = directory / "src"
    if not source.is_dir():
        return []
    result: List[Path] = []
    for path in source.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in suffixes or "build" in path.parts:
            continue
        relative = path.relative_to(source)
        if relative.parts and relative.parts[0].lower() in ("test", "androidtest"):
            continue
        result.append(path)
    return result


def gradle_files(root: Path) -> Iterable[Path]:
    ignored = {"build", ".gradle", ".git", ".idea", "node_modules"}
    for path in root.rglob("*"):
        if any(part in ignored for part in path.parts):
            continue
        if path.name in ("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"):
            yield path


def dependency_scopes(text: str, artifact: str) -> List[str]:
    scopes: List[str] = []
    for line in text.splitlines():
        if artifact not in line:
            continue
        match = re.search(r"\b(compileOnly|implementation|api|runtimeOnly)\s*\(?", line)
        if match:
            scopes.append(match.group(1))
    return sorted(set(scopes))


def detect_agp_version(root: Path, module_build: Path) -> Optional[str]:
    candidates = [module_build, root / "build.gradle.kts", root / "build.gradle"] + settings_files(root)
    patterns = (
        r"com\.android\.tools\.build:gradle:([0-9]+(?:\.[0-9]+){1,3})",
        r"id\s*\(\s*[\"']com\.android\.(?:application|library)[\"']\s*\)\s*version\s*[\"']([0-9]+(?:\.[0-9]+){1,3})",
        r"id\s+[\"']com\.android\.(?:application|library)[\"']\s+version\s+[\"']([0-9]+(?:\.[0-9]+){1,3})",
    )
    for path in candidates:
        if not path.is_file():
            continue
        text = read_text(path)
        for pattern in patterns:
            match = re.search(pattern, text)
            if match:
                return match.group(1)
    return None


def java_major() -> Tuple[Optional[int], str]:
    java = "java"
    configured_home = os.environ.get("JAVA_HOME")
    if configured_home:
        candidate = Path(configured_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if candidate.is_file():
            java = str(candidate)
    try:
        completed = subprocess.run(
            [java, "-version"], capture_output=True, text=True, timeout=5, check=False
        )
    except (OSError, subprocess.SubprocessError) as error:
        return None, str(error)
    output = (completed.stderr or completed.stdout).strip().splitlines()
    first = output[0] if output else "unknown java output"
    match = re.search(r'version\s+"([0-9]+)(?:\.([0-9]+))?', first)
    if not match:
        return None, first
    major = int(match.group(1))
    if major == 1 and match.group(2):
        major = int(match.group(2))
    return major, first


def sdk_path(root: Path) -> Optional[Path]:
    raw = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if raw:
        return Path(raw).expanduser()
    local = read_text(root / "local.properties")
    match = re.search(r"(?m)^sdk\.dir=(.+)$", local)
    if match:
        return Path(match.group(1).replace("\\:", ":").replace("\\\\", "\\")).expanduser()
    return None


def find_hardcoded_bool(text: str, property_name: str) -> Optional[bool]:
    match = re.search(rf"\b{re.escape(property_name)}\s*\.\s*set\s*\(\s*(true|false)\s*\)", text)
    return None if not match else match.group(1) == "true"


def check_environment(report: Report, root: Path, module_build: Path) -> None:
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if wrapper.is_file():
        report.add("PASS", "gradle-wrapper", "Gradle wrapper is present", str(wrapper))
    else:
        report.add("BLOCKER", "gradle-wrapper", "Gradle wrapper is missing", str(wrapper))

    wrapper_properties = root / "gradle/wrapper/gradle-wrapper.properties"
    distribution = read_text(wrapper_properties)
    gradle_match = re.search(r"gradle-([0-9]+(?:\.[0-9]+){1,2})-(?:bin|all)\.zip", distribution)
    if gradle_match:
        report.add("PASS", "gradle-version", f"Gradle {gradle_match.group(1)} detected", str(wrapper_properties))
    else:
        report.add("WARN", "gradle-version", "Could not determine Gradle wrapper version", str(wrapper_properties))

    agp = detect_agp_version(root, module_build)
    if agp:
        parts = tuple(int(part) for part in agp.split("."))
        supported = (parts[0] == 3 and len(parts) > 1 and parts[1] >= 5) or 4 <= parts[0] <= 8
        report.add(
            "PASS" if supported else "BLOCKER",
            "agp-version",
            f"AGP {agp} is within X2C's 3.5.x–8.x range" if supported else f"AGP {agp} is outside X2C's 3.5.x–8.x range",
            str(module_build),
        )
    else:
        report.add("WARN", "agp-version", "Could not resolve AGP version (possibly a version-catalog alias)", str(module_build))

    major, java_output = java_major()
    if major is None:
        report.add("BLOCKER", "java", "Java is unavailable or unreadable", java_output)
    else:
        severity = "PASS"
        message = f"Java {major} detected"
        if agp:
            agp_major = int(agp.split(".")[0])
            minimum = 17 if agp_major >= 8 else 11 if agp_major >= 7 else 8
            if major < minimum:
                severity = "BLOCKER"
                message += f"; detected AGP requires at least Java {minimum} for this preflight"
        report.add(severity, "java", message, java_output)

    sdk = sdk_path(root)
    if sdk and sdk.is_dir():
        report.add("PASS", "android-sdk", "Android SDK directory is available", str(sdk))
    else:
        report.add("BLOCKER", "android-sdk", "Android SDK directory is missing", str(sdk or "ANDROID_SDK_ROOT/ANDROID_HOME/local.properties"))


def inspect_components(module_dir: Path) -> Dict[str, object]:
    combined = "\n".join(read_text(path) for path in source_files(module_dir, (".java", ".kt")))
    result: Dict[str, object] = {}
    activity_annotations = re.findall(r"@X2cPluginActivity(?:\s*\((.*?)\))?", combined, re.DOTALL)
    modes: Dict[str, int] = {name: 0 for name in ("STANDARD", "SINGLE_TOP", "SINGLE_TASK", "SINGLE_INSTANCE")}
    for body in activity_annotations:
        match = re.search(r"PluginLaunchMode\.([A-Z_]+)", body)
        modes[match.group(1) if match and match.group(1) in modes else "STANDARD"] += 1
    result["activity_modes"] = modes
    result["activities"] = len(activity_annotations)
    result["services"] = len(re.findall(r"@X2cPluginService\b", combined))
    result["receivers"] = len(re.findall(r"@X2cPluginReceiver\b", combined))
    result["providers"] = len(re.findall(r"@X2cPluginProvider\b", combined))
    return result


def inspect_resources(report: Report, module_dir: Path, mode: str) -> None:
    if mode != "plugin":
        return
    for res in (module_dir / "src").glob("*/res") if (module_dir / "src").is_dir() else []:
        if res.parent.name.lower() in ("normal", "test", "androidtest"):
            continue
        for directory in (path for path in res.iterdir() if path.is_dir()):
            if "-" in directory.name:
                report.add("BLOCKER", "resource-qualifier", "Plugin XML-to-Java mode rejects qualified resource directories", str(directory))
        for path in res.rglob("*"):
            if not path.is_file():
                continue
            lower = path.name.lower()
            if lower.endswith(".9.png"):
                report.add("BLOCKER", "nine-patch", "Nine-patch is unsupported in plugin mode", str(path))
            if path.suffix.lower() == ".xml":
                text = read_text(path)
                if re.search(r"<(?:style|declare-styleable|vector|animated-vector|ripple|include|merge)\b", text):
                    report.add("BLOCKER", "unsupported-resource", "Unsupported plugin XML construct detected", str(path))

    bitmaps = [
        path
        for path in source_files(module_dir, (".png", ".jpg", ".jpeg", ".webp", ".gif", ".avif"))
        if path.relative_to(module_dir / "src").parts[0].lower() != "normal"
        and any(part.startswith("drawable") or part.startswith("mipmap") for part in path.parts)
    ]
    if bitmaps:
        lock = module_dir / "x2c-assets.lock.json"
        if lock.is_file():
            report.add("PASS", "asset-lock", f"Asset lock exists for {len(bitmaps)} plugin bitmap(s)", str(lock))
        else:
            report.add("BLOCKER", "asset-lock", f"{len(bitmaps)} plugin bitmap(s) require an immutable asset lock", str(bitmaps[0]))


def check_source_contracts(report: Report, module_dir: Path, mode: str) -> None:
    bad_generated: List[Path] = []
    r_references: List[Path] = []
    init_calls: List[Path] = []
    for path in source_files(module_dir, (".java", ".kt")):
        text = read_text(path)
        if re.search(r"(?:import\s+[^;\n]*\.generated\.|\bX2c(?:Module|Layouts|ResourceProviderImpl)\b)", text):
            bad_generated.append(path)
        if mode == "plugin" and re.search(r"(?<!android\.)\bR\.[A-Za-z_]", text):
            r_references.append(path)
        if "X2C.init(" in text:
            init_calls.append(path)
    if bad_generated:
        report.add("BLOCKER", "generated-api", "Business source references generated implementation", str(bad_generated[0]))
    else:
        report.add("PASS", "generated-api", "Business source does not reference generated implementation")
    if r_references:
        report.add("BLOCKER", "android-r", "Plugin source appears to reference a non-framework R class", str(r_references[0]))
    if init_calls:
        report.add("BLOCKER", "producer-init", "Library/plugin source must not initialize the process-wide X2C runtime", str(init_calls[0]))


def check_plugin_module(report: Report, root: Path, module_dir: Path, text: str) -> None:
    if "dev.x2c.codegen" in text:
        report.add("PASS", "codegen-plugin", "X2C codegen plugin is applied")
    else:
        report.add("BLOCKER", "codegen-plugin", "Apply dev.x2c.codegen to the producer module")

    hard_mode = find_hardcoded_bool(text, "pluginMode")
    hard_enable = find_hardcoded_bool(text, "x2cEnable")
    if hard_mode is False:
        report.add("BLOCKER", "plugin-mode", "Module hardcodes pluginMode=false", "pluginMode.set(false)")
    elif "pluginMode" in text:
        report.add("PASS", "plugin-mode", "Plugin mode configuration is present")
    else:
        report.add("WARN", "plugin-mode", "pluginMode is not explicit; current X2C convention defaults configured modules to plugin mode")
    if hard_enable is False:
        report.add("BLOCKER", "x2c-enable", "Plugin mode cannot disable XML-to-Java generation", "x2cEnable.set(false)")

    plugin_id = re.search(r"pluginId\s*\.\s*set\s*\(\s*[\"']([^\"']+)", text)
    generated = re.search(r"generatedPackage\s*\.\s*set\s*\(\s*[\"']([^\"']+)", text)
    report.add("PASS" if plugin_id else "BLOCKER", "plugin-id", f"pluginId={plugin_id.group(1)}" if plugin_id else "Configure a stable unique x2c.pluginId", str(module_dir))
    report.add("PASS" if generated else "BLOCKER", "generated-package", f"generatedPackage={generated.group(1)}" if generated else "Configure a unique x2c.generatedPackage", str(module_dir))

    components = inspect_components(module_dir)
    total = sum(int(components[key]) for key in ("activities", "services", "receivers", "providers"))
    if total and "dev.x2c.activity-plugin" not in text:
        report.add("BLOCKER", "component-plugin", f"{total} annotated component(s) require dev.x2c.activity-plugin")
    elif total:
        report.add("PASS", "component-plugin", f"Component transform configured for {total} annotated entry/entries")
    for launch_mode, count in dict(components["activity_modes"]).items():
        if count > 8:
            report.add("BLOCKER", "activity-capacity", f"{launch_mode} declares {count} targets; process capacity is 8")
    if int(components["services"]) > 8:
        report.add("BLOCKER", "service-capacity", f"Plugin declares {components['services']} Services; capacity is 8")

    for artifact in PLUGIN_PRODUCER_ARTIFACTS:
        scopes = dependency_scopes(text, artifact)
        if "compileOnly" in scopes and not any(scope in scopes for scope in ("api", "implementation")):
            report.add("PASS", f"producer-{artifact}", f"{artifact} is compileOnly")
        elif "compileOnly" in scopes and any(scope in scopes for scope in ("api", "implementation")):
            report.add(
                "WARN",
                f"producer-{artifact}",
                f"{artifact} has conditional compileOnly and packaged scopes; verify the mode provider selects compileOnly for plugin builds",
                ",".join(scopes),
            )
        elif scopes:
            report.add("BLOCKER", f"producer-{artifact}", f"{artifact} must be compileOnly in plugin producers", ",".join(scopes))
        else:
            report.add("BLOCKER", f"producer-{artifact}", f"Plugin producer is missing compileOnly {artifact}")

    manifest_components: List[Tuple[Path, str]] = []
    for manifest in source_files(module_dir, (".xml",)):
        if manifest.name != "AndroidManifest.xml":
            continue
        try:
            relative_parts = manifest.relative_to(module_dir / "src").parts
        except ValueError:
            relative_parts = manifest.parts
        if any(part.lower() == "normal" for part in relative_parts[:-1]):
            continue
        content = read_text(manifest)
        for component in COMPONENTS:
            if re.search(rf"<{re.escape(component)}\b", content):
                manifest_components.append((manifest, component))
    if manifest_components:
        path, component = manifest_components[0]
        report.add("BLOCKER", "plugin-manifest", f"Plugin Manifest declares <{component}>; business components must use annotations only", str(path))
    else:
        report.add("PASS", "plugin-manifest", "No plugin business components found in source Manifests")

    if plugin_id:
        occurrences: List[Path] = []
        needle = plugin_id.group(1)
        for path in gradle_files(root):
            candidate_text = read_text(path)
            if "dev.x2c.codegen" in candidate_text and re.search(
                rf"pluginId\s*\.\s*set\s*\(\s*[\"']{re.escape(needle)}[\"']",
                candidate_text,
            ):
                occurrences.append(path)
        if len(occurrences) > 1:
            report.add("BLOCKER", "duplicate-plugin-id", f"pluginId {needle} appears in multiple Gradle modules", ", ".join(str(path) for path in occurrences))


def check_normal_module(report: Report, module_dir: Path, text: str) -> None:
    hard_mode = find_hardcoded_bool(text, "pluginMode")
    if hard_mode is True:
        report.add("BLOCKER", "normal-mode", "Module hardcodes pluginMode=true", "pluginMode.set(true)")
    elif hard_mode is False:
        report.add("PASS", "normal-mode", "Module explicitly selects pluginMode=false")
    elif "x2c" in text:
        report.add("WARN", "normal-mode", "Normal mode is not a literal; confirm the property provider resolves false")
    else:
        report.add("PASS", "normal-system-mode", "No X2C configuration detected; native system resources are expected")

    scopes = dependency_scopes(text, "x2c-runtime")
    if any(scope in scopes for scope in ("api", "implementation")):
        report.add("PASS", "normal-runtime", "Normal library exposes/uses x2c-runtime through api or implementation", ",".join(scopes))
    elif scopes == ["compileOnly"]:
        report.add("BLOCKER", "normal-runtime", "Normal AAR cannot rely on compileOnly x2c-runtime alone")
    else:
        report.add("WARN", "normal-runtime", "No x2c-runtime dependency detected; acceptable only if business code does not call X2C")

    if "x2cEnable" not in text and "dev.x2c.codegen" not in text:
        report.add("PASS", "normal-codegen", "Native system-resource mode does not require generated Java")
    elif "dev.x2c.codegen" in text:
        report.add("PASS", "normal-codegen", "X2C codegen plugin is available for configured normal mode")
    else:
        report.add("BLOCKER", "normal-codegen", "x2cEnable is configured but dev.x2c.codegen is not applied")


def check_host(report: Report, host_dir: Optional[Path], mode: str) -> None:
    if host_dir is None:
        if mode == "plugin":
            report.add("WARN", "host-scope", "No host module was supplied; runtime ownership and initialization were not checked")
        return
    host_build = build_file(host_dir)
    if not host_build:
        report.add("BLOCKER", "host-module", "Host module build file is missing", str(host_dir))
        return
    text = read_text(host_build)
    if mode == "plugin":
        host_has_plugin_base = bool(dependency_scopes(text, "x2c-plugin-base"))
        host_has_plugin_runtime = bool(dependency_scopes(text, "x2c-plugin-runtime"))
        for artifact in PLUGIN_HOST_ARTIFACTS:
            scopes = dependency_scopes(text, artifact)
            if any(scope in scopes for scope in ("api", "implementation")):
                report.add("PASS", f"host-{artifact}", f"Host owns {artifact}", ",".join(scopes))
            elif artifact == "x2c-plugin-api" and host_has_plugin_base and host_has_plugin_runtime:
                report.add(
                    "WARN",
                    f"host-{artifact}",
                    "Host does not declare x2c-plugin-api directly; verify the resolved graph contains exactly one transitive copy",
                    "x2c-plugin-base -> x2c-plugin-runtime -> x2c-plugin-api",
                )
            else:
                report.add("BLOCKER", f"host-{artifact}", f"Host must implementation-depend on {artifact}", ",".join(scopes) or "missing")
    init_paths = [path for path in source_files(host_dir, (".java", ".kt")) if "X2C.init(" in read_text(path)]
    if len(init_paths) == 1:
        report.add("PASS", "host-init", "Exactly one host X2C.init call was found", str(init_paths[0]))
    elif not init_paths:
        report.add("BLOCKER", "host-init", "Host must initialize X2C once before resource/plugin access", str(host_dir / "src"))
    else:
        report.add("BLOCKER", "host-init", f"Host contains {len(init_paths)} X2C.init call sites; keep one process-wide initialization", ", ".join(str(path) for path in init_paths))


def print_report(report: Report, payload: Dict[str, object], output_format: str) -> None:
    if output_format == "json":
        print(json.dumps(payload, indent=2, ensure_ascii=False))
        return
    print(f"X2C preflight: mode={payload['mode']} module={payload['module']}")
    for item in report.items:
        evidence = f" [{item['evidence']}]" if item["evidence"] else ""
        print(f"{item['severity']:7} {item['code']}: {item['message']}{evidence}")
    counts = report.counts()
    print(f"Summary: {counts['PASS']} pass, {counts['WARN']} warning, {counts['BLOCKER']} blocker")


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    root = Path(args.project).expanduser().resolve()
    report = Report()
    if not root.is_dir():
        report.add("BLOCKER", "project", "Project root does not exist", str(root))
        payload = {"schema": 1, "mode": args.mode, "project": str(root), "module": args.module, "results": report.items, "counts": report.counts()}
        print_report(report, payload, args.format)
        return 2

    module_dir = resolve_module(root, args.module, args.module_dir)
    module_build = build_file(module_dir) if module_dir else None
    if not module_dir or not module_build:
        report.add("BLOCKER", "module", "Android library module build file was not found", str(module_dir or args.module))
        payload = {"schema": 1, "mode": args.mode, "project": str(root), "module": args.module, "results": report.items, "counts": report.counts()}
        print_report(report, payload, args.format)
        return 2

    text = read_text(module_build)
    if "com.android.library" in text or re.search(r"alias\s*\([^\n]*android[^\n]*library", text, re.IGNORECASE):
        report.add("PASS", "android-library", "Android library plugin is available", str(module_build))
    else:
        report.add("BLOCKER", "android-library", "X2C producer/normal module must be an Android library", str(module_build))

    check_environment(report, root, module_build)
    if args.mode == "plugin":
        check_plugin_module(report, root, module_dir, text)
    else:
        check_normal_module(report, module_dir, text)
    inspect_resources(report, module_dir, args.mode)
    check_source_contracts(report, module_dir, args.mode)

    host_dir = resolve_module(root, args.host_module, args.host_dir) if (args.host_module or args.host_dir) else None
    check_host(report, host_dir, args.mode)

    payload = {
        "schema": 1,
        "mode": args.mode,
        "project": str(root),
        "module": args.module,
        "moduleDirectory": str(module_dir),
        "hostModule": args.host_module,
        "hostDirectory": str(host_dir) if host_dir else None,
        "results": report.items,
        "counts": report.counts(),
        "ready": not report.has_blockers(),
    }
    print_report(report, payload, args.format)
    return 2 if report.has_blockers() else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
