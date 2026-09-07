#!/usr/bin/env bash
set -euo pipefail

task_root=$(cd "$(dirname "$0")/.." && pwd)
task_mode=${X2C_BUILD_MODE:-plugin}
task_module=${X2C_MODULE:-:fixtures:producer}
task_variant=${X2C_VARIANT:-release}
task_extra_gradle_args=()

usage() {
    echo "Usage: $0 [--mode plugin|normal] [--module :path] [--variant name] [-- GRADLE_ARGS...]"
    echo
    echo "Environment alternatives: X2C_BUILD_MODE, X2C_MODULE, X2C_VARIANT"
    echo "plugin -> Android-loadable codegen-dex.jar; normal -> standard Android AAR"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --mode)
            [[ $# -ge 2 ]] || { echo "--mode requires a value" >&2; exit 2; }
            task_mode=$2
            shift 2
            ;;
        --module)
            [[ $# -ge 2 ]] || { echo "--module requires a value" >&2; exit 2; }
            task_module=$2
            shift 2
            ;;
        --variant)
            [[ $# -ge 2 ]] || { echo "--variant requires a value" >&2; exit 2; }
            task_variant=$2
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        --)
            shift
            task_extra_gradle_args=("$@")
            break
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

case "$task_mode" in
    plugin|true) task_mode=plugin; task_plugin_mode=true ;;
    normal|false) task_mode=normal; task_plugin_mode=false ;;
    *)
        echo "Invalid mode '$task_mode'; expected plugin or normal" >&2
        exit 2
        ;;
esac

if [[ ! "$task_module" =~ ^(:[A-Za-z0-9_.-]+)+$ ]]; then
    echo "Invalid Gradle module path: $task_module" >&2
    exit 2
fi
if [[ ! "$task_variant" =~ ^[A-Za-z][A-Za-z0-9]*$ ]]; then
    echo "Invalid Android variant: $task_variant" >&2
    exit 2
fi
for task_arg in "${task_extra_gradle_args[@]}"; do
    if [[ "$task_arg" == -Px2c.pluginMode=* ]]; then
        echo "Do not pass x2c.pluginMode twice; use --mode plugin|normal" >&2
        exit 2
    fi
done

task_module_relative=${task_module#:}
task_module_relative=${task_module_relative//:/\/}
task_module_directory="$task_root/$task_module_relative"
if [[ ! -f "$task_module_directory/build.gradle" \
        && ! -f "$task_module_directory/build.gradle.kts" ]]; then
    echo "Gradle module directory was not found: $task_module_directory" >&2
    exit 2
fi

task_variant_capitalized=$(printf '%s' "$task_variant" \
    | awk '{ print toupper(substr($0, 1, 1)) substr($0, 2) }')
task_gradle_task="${task_module}:x2cBuild${task_variant_capitalized}"

if [[ -z ${JAVA_HOME:-} \
        && -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

echo "X2C build: mode=$task_mode module=$task_module variant=$task_variant"
"$task_root/gradlew" -p "$task_root" \
    "$task_gradle_task" \
    "-Px2c.pluginMode=$task_plugin_mode" \
    "${task_extra_gradle_args[@]}"

task_report="$task_module_directory/build/reports/x2c/$task_variant/report.json"
if [[ ! -f "$task_report" ]]; then
    echo "X2C compilation report was not produced: $task_report" >&2
    exit 1
fi

if [[ "$task_mode" == plugin ]]; then
    task_artifact="$task_module_directory/build/outputs/x2c/$task_variant/codegen-dex.jar"
    if [[ ! -f "$task_artifact" ]]; then
        echo "Plugin DEX JAR was not produced: $task_artifact" >&2
        exit 1
    fi
    task_entries=$(unzip -Z1 "$task_artifact")
    if [[ "$task_entries" != "classes.dex" ]]; then
        echo "Unsafe plugin artifact: codegen-dex.jar must contain only classes.dex" >&2
        exit 1
    fi
    if ! grep -q '"mode": "PLUGIN_SYNTHETIC_IDS"' "$task_report"; then
        echo "Plugin report does not declare PLUGIN_SYNTHETIC_IDS" >&2
        exit 1
    fi
else
    task_project_name=${task_module##*:}
    task_variant_lower=$(printf '%s' "$task_variant" | tr '[:upper:]' '[:lower:]')
    task_artifact="$task_module_directory/build/outputs/aar/${task_project_name}-${task_variant_lower}.aar"
    if [[ ! -f "$task_artifact" ]]; then
        task_aar_directory="$task_module_directory/build/outputs/aar"
        task_aar_candidates=()
        if [[ -d "$task_aar_directory" ]]; then
            while IFS= read -r task_candidate; do
                task_aar_candidates+=("$task_candidate")
            done < <(find "$task_aar_directory" -maxdepth 1 -type f -name '*.aar' | sort)
        fi
        if [[ ${#task_aar_candidates[@]} -ne 1 ]]; then
            echo "Cannot determine the normal AAR below: $task_aar_directory" >&2
            exit 1
        fi
        task_artifact=${task_aar_candidates[0]}
    fi
    task_aar_entries=$(unzip -Z1 "$task_artifact")
    if ! grep -qx 'AndroidManifest.xml' <<<"$task_aar_entries" \
            || ! grep -qx 'classes.jar' <<<"$task_aar_entries"; then
        echo "Invalid normal Android library artifact: $task_artifact" >&2
        exit 1
    fi
    if ! grep -q '"mode": "HOST_RESOURCE_IDS"' "$task_report"; then
        echo "Normal report does not declare HOST_RESOURCE_IDS" >&2
        exit 1
    fi
fi

task_artifact_directory=$(cd "$(dirname "$task_artifact")" && pwd)
task_artifact="$task_artifact_directory/$(basename "$task_artifact")"
echo "X2C_ARTIFACT=$task_artifact"
echo "X2C_REPORT=$task_report"
