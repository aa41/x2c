#!/usr/bin/env python3
"""Validate X2C reports and selected build artifacts after Gradle completes."""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Sequence

from preflight import Report, build_file, print_report, resolve_module


HOST_PREFIXES = (
    "dev/x2c/runtime/",
    "dev/x2c/plugin/api/",
    "dev/x2c/plugin/base/",
    "dev/x2c/plugin/loader/",
    "dev/x2c/plugin/runtime/",
)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify an X2C artifact and its generated reports")
    parser.add_argument("--project", default=".")
    parser.add_argument("--module", required=True)
    parser.add_argument("--module-dir")
    parser.add_argument("--mode", choices=("plugin", "normal"), required=True)
    parser.add_argument("--variant", default="release")
    parser.add_argument("--x2c-enable", choices=("auto", "true", "false"), default="auto")
    parser.add_argument("--artifact", help="Override final DEX JAR or AAR path")
    parser.add_argument("--format", choices=("text", "json"), default="text")
    return parser.parse_args(argv)


def read_json(path: Path, report: Report, code: str) -> Optional[Dict[str, object]]:
    if not path.is_file():
        report.add("BLOCKER", code, "Required JSON report is missing", str(path))
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        report.add("BLOCKER", code, f"JSON report is invalid: {error}", str(path))
        return None
    if not isinstance(value, dict):
        report.add("BLOCKER", code, "JSON report root must be an object", str(path))
        return None
    report.add("PASS", code, "JSON report is readable", str(path))
    return value


def archive_entries(path: Path, report: Report, code: str) -> Optional[List[str]]:
    if not path.is_file():
        report.add("BLOCKER", code, "Required artifact is missing", str(path))
        return None
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            bad = archive.testzip()
    except (OSError, zipfile.BadZipFile) as error:
        report.add("BLOCKER", code, f"Artifact is not a valid ZIP archive: {error}", str(path))
        return None
    if bad:
        report.add("BLOCKER", code, f"Artifact contains a corrupt entry: {bad}", str(path))
        return None
    report.add("PASS", code, f"Archive is readable with {len(names)} entries", str(path))
    return names


def find_normal_aar(module_dir: Path, variant: str) -> Optional[Path]:
    directory = module_dir / "build/outputs/aar"
    candidates = sorted(directory.glob(f"*{variant}*.aar"), key=lambda path: path.stat().st_mtime, reverse=True) if directory.is_dir() else []
    return candidates[0] if candidates else None


def check_report_mode(report: Report, data: Optional[Dict[str, object]], mode: str, enabled: str, path: Path) -> None:
    if data is None:
        return
    actual = data.get("mode")
    if mode == "plugin":
        expected = {"PLUGIN_SYNTHETIC_IDS"}
    elif enabled == "true":
        expected = {"HOST_RESOURCE_IDS"}
    elif enabled == "false":
        expected = {"SYSTEM_RESOURCES"}
    else:
        expected = {"HOST_RESOURCE_IDS", "SYSTEM_RESOURCES"}
    if actual in expected:
        report.add("PASS", "report-mode", f"Compilation report mode is {actual}", str(path))
    else:
        report.add("BLOCKER", "report-mode", f"Compilation report mode {actual!r} does not match {sorted(expected)}", str(path))


def check_class_jar(report: Report, path: Path, code: str) -> Optional[List[str]]:
    names = archive_entries(path, report, code)
    if names is None:
        return None
    forbidden: List[str] = []
    for name in names:
        normalized = name.lstrip("/")
        if (
            normalized.startswith(("res/", "assets/", "lib/"))
            or normalized == "AndroidManifest.xml"
            or normalized.endswith(".so")
            or normalized == "classes.dex"
            or any(normalized.startswith(prefix) for prefix in HOST_PREFIXES)
            or re.search(r"(?:^|/)R(?:\$[^2][^/]*)?\.class$", normalized)
        ):
            forbidden.append(name)
    if forbidden:
        report.add("BLOCKER", f"{code}-contents", f"Class-only JAR contains forbidden payload entry {forbidden[0]}", str(path))
    else:
        report.add("PASS", f"{code}-contents", "Class-only JAR contains no resource, DEX, Android R, native, or host-runtime entries", str(path))
    return names


def check_compilation_details(
    report: Report,
    data: Optional[Dict[str, object]],
    mode: str,
    enabled: str,
    module_dir: Path,
    path: Path,
) -> None:
    if data is None:
        return
    if data.get("schema") != 1:
        report.add("BLOCKER", "report-schema", f"Unsupported compilation report schema {data.get('schema')!r}", str(path))
    else:
        report.add("PASS", "report-schema", "Compilation report schema is 1", str(path))
    if mode == "plugin":
        generated = data.get("generatedPackage")
        if isinstance(generated, str) and re.fullmatch(
            r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+", generated
        ):
            report.add("PASS", "generated-package", f"Compilation report generatedPackage={generated}", str(path))
        else:
            report.add("BLOCKER", "generated-package", f"Compilation report has invalid generatedPackage {generated!r}", str(path))
        invariants = data.get("invariants")
        if isinstance(invariants, list) and "resource-ids=synthetic" in invariants:
            report.add("PASS", "synthetic-ids", "Compilation report confirms synthetic resource IDs", str(path))
        else:
            report.add("BLOCKER", "synthetic-ids", "Compilation report does not confirm synthetic resource IDs", str(path))
        counts = data.get("counts")
        cdn_images = counts.get("cdnImages", 0) if isinstance(counts, dict) else 0
        if isinstance(cdn_images, int) and cdn_images > 0:
            lock = module_dir / "x2c-assets.lock.json"
            candidates = path.parent / "assets-candidates.json"
            if lock.is_file() and candidates.is_file():
                report.add("PASS", "bitmap-metadata", f"{cdn_images} CDN image(s) have lock and candidate reports", f"{lock}, {candidates}")
            else:
                report.add("BLOCKER", "bitmap-metadata", "Plugin CDN images require both asset lock and candidate report", f"{lock}, {candidates}")
    elif enabled == "false" and data.get("generatedSources") != 0:
        report.add("BLOCKER", "generated-sources", "SYSTEM_RESOURCES report must declare generatedSources=0", str(path))
    elif enabled == "false":
        report.add("PASS", "generated-sources", "SYSTEM_RESOURCES report confirms zero generated sources", str(path))


def check_dex_jar(report: Report, path: Path) -> None:
    names = archive_entries(path, report, "dex-jar")
    if names is None:
        return
    if names != ["classes.dex"]:
        report.add(
            "BLOCKER",
            "dex-contents",
            "DEX JAR must contain exactly classes.dex",
            f"{path}: {names[:8]}",
        )
        return
    try:
        with zipfile.ZipFile(path) as archive:
            magic = archive.read("classes.dex")[:8]
    except (OSError, zipfile.BadZipFile, KeyError) as error:
        report.add("BLOCKER", "dex-magic", f"Cannot read classes.dex: {error}", str(path))
        return
    if magic.startswith(b"dex\n") and magic[7:8] == b"\x00":
        report.add("PASS", "dex-magic", f"Valid DEX header {magic!r}", str(path))
    else:
        report.add("BLOCKER", "dex-magic", f"Invalid DEX header {magic!r}", str(path))


def check_components(report: Report, path: Path, activity_jar: Path) -> None:
    if not path.exists() and not activity_jar.exists():
        report.add("PASS", "component-report", "No component transform output is present; resource-only plugin assumed")
        return
    data = read_json(path, report, "component-report")
    if data is None:
        return
    registry = data.get("registryClass")
    if registry == "dev.x2c.generated.plugin.ComponentRegistry":
        report.add("PASS", "component-registry", "Generated direct component registry is declared", str(path))
    else:
        report.add("BLOCKER", "component-registry", f"Unexpected component registry {registry!r}", str(path))
    if not isinstance(data.get("runtimeAbiVersion"), int):
        report.add("BLOCKER", "component-abi", "Component report is missing integer runtimeAbiVersion", str(path))
    else:
        report.add("PASS", "component-abi", f"Component runtime ABI is {data['runtimeAbiVersion']}", str(path))
    plugin_id = data.get("pluginId")
    if isinstance(plugin_id, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", plugin_id):
        report.add("PASS", "component-plugin-id", f"Component registry pluginId={plugin_id}", str(path))
    else:
        report.add("BLOCKER", "component-plugin-id", f"Invalid component pluginId {plugin_id!r}", str(path))
    closure_digest = data.get("dependencyClosureSha256")
    if isinstance(closure_digest, str) and re.fullmatch(r"[0-9a-f]{64}", closure_digest):
        report.add("PASS", "component-closure", "Dependency-closure digest is present", str(path))
    else:
        report.add("BLOCKER", "component-closure", "Dependency-closure digest is missing or invalid", str(path))
    for key in ("activities", "services", "receivers", "providers"):
        if not isinstance(data.get(key), list):
            report.add("BLOCKER", "component-list", f"Component report field {key} is missing or invalid", str(path))
    activities = data.get("activities")
    if isinstance(activities, list):
        per_mode: Dict[str, int] = {}
        for item in activities:
            if isinstance(item, dict):
                launch_mode = str(item.get("launchMode"))
                per_mode[launch_mode] = per_mode.get(launch_mode, 0) + 1
        for launch_mode, count in per_mode.items():
            if count > 8:
                report.add("BLOCKER", "activity-capacity", f"{launch_mode} has {count} distinct entries; process capacity is 8", str(path))
    services = data.get("services")
    if isinstance(services, list) and len(services) > 8:
        report.add("BLOCKER", "service-capacity", f"Component report contains {len(services)} Services; capacity is 8", str(path))
    activity_names = check_class_jar(report, activity_jar, "activity-plugin-jar")
    registry_entry = "dev/x2c/generated/plugin/ComponentRegistry.class"
    if activity_names is not None:
        if registry_entry in activity_names:
            report.add("PASS", "component-registry-class", "Activity plugin JAR contains the generated registry class", str(activity_jar))
        else:
            report.add("BLOCKER", "component-registry-class", "Activity plugin JAR is missing the generated registry class", str(activity_jar))


def check_plugin(report: Report, module_dir: Path, variant: str, artifact: Optional[str]) -> Path:
    output = module_dir / "build/outputs/x2c" / variant
    codegen = output / "codegen.jar"
    activity_jar = output / "activity-plugin.jar"
    dex = Path(artifact).expanduser().resolve() if artifact else output / "codegen-dex.jar"
    codegen_names = check_class_jar(report, codegen, "codegen-jar")
    if codegen_names is not None:
        bootstraps = [name for name in codegen_names if name.endswith("/x2c/X2cModuleBootstrap.class")]
        if bootstraps:
            report.add("PASS", "module-bootstrap", f"Codegen JAR contains {len(bootstraps)} namespace bootstrap(s)", str(codegen))
        else:
            report.add("BLOCKER", "module-bootstrap", "Codegen JAR is missing the namespace module bootstrap", str(codegen))
    check_dex_jar(report, dex)
    check_components(report, module_dir / "build/reports/x2c" / variant / "activities.json", activity_jar)
    return dex


def check_normal(report: Report, module_dir: Path, variant: str, enabled: str, artifact: Optional[str]) -> Optional[Path]:
    aar = Path(artifact).expanduser().resolve() if artifact else find_normal_aar(module_dir, variant)
    if aar is None:
        report.add("BLOCKER", "aar", "No matching normal-mode AAR was found", str(module_dir / "build/outputs/aar"))
        return None
    names = archive_entries(aar, report, "aar")
    if names is None:
        return aar
    for required in ("AndroidManifest.xml", "classes.jar"):
        if required in names:
            report.add("PASS", f"aar-{required.lower()}", f"AAR contains {required}", str(aar))
        else:
            report.add("BLOCKER", f"aar-{required.lower()}", f"AAR is missing {required}", str(aar))
    source_has_res = any(path.is_file() for path in (module_dir / "src").glob("*/res/**/*")) if (module_dir / "src").is_dir() else False
    packaged_res = any(name == "res/" or name.startswith("res/") for name in names)
    if source_has_res and not packaged_res:
        report.add("BLOCKER", "aar-resources", "Source resources exist but the AAR contains no res entries", str(aar))
    elif source_has_res:
        report.add("PASS", "aar-resources", "AAR retains Android resources", str(aar))
    if enabled == "false":
        try:
            with zipfile.ZipFile(aar) as outer:
                classes_bytes = outer.read("classes.jar")
            import io
            with zipfile.ZipFile(io.BytesIO(classes_bytes)) as classes:
                generated = [name for name in classes.namelist() if name.endswith("/x2c/X2cModuleBootstrap.class") or "/generated/X2cModule.class" in name]
        except (OSError, KeyError, zipfile.BadZipFile) as error:
            report.add("BLOCKER", "system-classes", f"Cannot inspect AAR classes.jar: {error}", str(aar))
        else:
            if generated:
                report.add("BLOCKER", "system-classes", f"x2cEnable=false AAR contains generated module class {generated[0]}", str(aar))
            else:
                report.add("PASS", "system-classes", "x2cEnable=false AAR contains no generated module/bootstrap classes", str(aar))
    return aar


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    root = Path(args.project).expanduser().resolve()
    module_dir = resolve_module(root, args.module, args.module_dir)
    report = Report()
    if module_dir is None or build_file(module_dir) is None:
        report.add("BLOCKER", "module", "Gradle module was not found", str(module_dir or args.module))
        final_artifact: Optional[Path] = None
    else:
        report_path = module_dir / "build/reports/x2c" / args.variant / "report.json"
        data = read_json(report_path, report, "compilation-report")
        check_report_mode(report, data, args.mode, args.x2c_enable, report_path)
        check_compilation_details(
            report,
            data,
            args.mode,
            args.x2c_enable,
            module_dir,
            report_path,
        )
        final_artifact = check_plugin(report, module_dir, args.variant, args.artifact) if args.mode == "plugin" else check_normal(report, module_dir, args.variant, args.x2c_enable, args.artifact)

    payload = {
        "schema": 1,
        "mode": args.mode,
        "project": str(root),
        "module": args.module,
        "variant": args.variant,
        "artifact": str(final_artifact) if final_artifact else None,
        "results": report.items,
        "counts": report.counts(),
        "verified": not report.has_blockers(),
    }
    if args.format == "json":
        print(json.dumps(payload, indent=2, ensure_ascii=False))
    else:
        print(f"X2C post-check: mode={args.mode} module={args.module} variant={args.variant}")
        for item in report.items:
            evidence = f" [{item['evidence']}]" if item["evidence"] else ""
            print(f"{item['severity']:7} {item['code']}: {item['message']}{evidence}")
        counts = report.counts()
        print(f"Summary: {counts['PASS']} pass, {counts['WARN']} warning, {counts['BLOCKER']} blocker")
    return 2 if report.has_blockers() else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
