#!/usr/bin/env bash
set -euo pipefail

task_root=$(cd "$(dirname "$0")/.." && pwd)
task_java_home=${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}
task_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
task_aapt2=$(find "$task_sdk/build-tools" -maxdepth 2 -type f -name aapt2 | sort | tail -1)
task_failure_log=$(mktemp "${TMPDIR:-/tmp}/x2c-mode-failure.XXXXXX")
trap 'rm -f "$task_failure_log"' EXIT

export JAVA_HOME="$task_java_home"
export ANDROID_HOME="$task_sdk"

"$task_root/scripts/build-x2c-artifact.sh" \
    --module :fixtures:producer \
    --mode plugin \
    --variant release \
    -- \
    --no-configuration-cache \
    "-Pandroid.aapt2FromMavenOverride=$task_aapt2"

task_plugin_report="$task_root/fixtures/producer/build/reports/x2c/release/report.json"
task_dex="$task_root/fixtures/producer/build/outputs/x2c/release/codegen-dex.jar"

rg -q '"mode": "PLUGIN_SYNTHETIC_IDS"' "$task_plugin_report"
[[ $(jar tf "$task_dex") == "classes.dex" ]]

"$task_root/scripts/build-x2c-artifact.sh" \
    --module :fixtures:producer \
    --mode normal \
    --variant release \
    -- \
    --no-configuration-cache \
    "-Pandroid.aapt2FromMavenOverride=$task_aapt2"

task_normal_report="$task_root/fixtures/producer/build/reports/x2c/release/report.json"
task_aar="$task_root/fixtures/producer/build/outputs/aar/producer-release.aar"
rg -q '"mode": "HOST_RESOURCE_IDS"' "$task_normal_report"
unzip -Z1 "$task_aar" | rg '^res/layout/content\.xml$' >/dev/null
unzip -Z1 "$task_aar" | rg '^res/drawable/hero\.jpg$' >/dev/null
unzip -p "$task_aar" AndroidManifest.xml \
    | rg 'dev\.x2c\.fixture\.producer\.DemoActivity' >/dev/null

if "$task_root/gradlew" -p "$task_root" \
        :fixtures:producer:x2cReleaseDexJar \
        --no-configuration-cache \
        -Px2c.pluginMode=false \
        "-Pandroid.aapt2FromMavenOverride=$task_aapt2" \
        >"$task_failure_log" 2>&1; then
    echo "normal integration unexpectedly produced a DEX JAR" >&2
    exit 1
fi
rg -q '(Android component transformation requires x2c.pluginMode=true|Normal integration mode must publish and consume the AAR)' "$task_failure_log"

if "$task_root/gradlew" -p "$task_root" \
        :fixtures:normal-library:help \
        --no-configuration-cache \
        -Px2c.pluginMode=tru \
        "-Pandroid.aapt2FromMavenOverride=$task_aapt2" \
        >"$task_failure_log" 2>&1; then
    echo "invalid pluginMode value was not rejected" >&2
    exit 1
fi
rg -q 'x2c.pluginMode must be exactly true or false' "$task_failure_log"

"$task_root/scripts/build-x2c-artifact.sh" \
    --module :fixtures:producer --mode normal --x2c-enable false --variant release \
    -- --offline --no-configuration-cache
rg -q '"mode": "SYSTEM_RESOURCES"' "$task_normal_report"
[[ -z $(find "$task_root/fixtures/producer/build/generated/java/x2cGenerateRelease" -name '*.java' -print -quit) ]]
task_classes=$(mktemp "${TMPDIR:-/tmp}/x2c-system-classes.XXXXXX")
unzip -p "$task_aar" classes.jar > "$task_classes"
if unzip -Z1 "$task_classes" | rg '/(X2cModule|X2cLayouts|R2)\.class$'; then
    rm -f "$task_classes"
    echo "Disabled AAR contains stale generated classes" >&2
    exit 1
fi
rm -f "$task_classes"

if "$task_root/gradlew" -p "$task_root" :fixtures:producer:x2cGenerateRelease \
    -Px2c.enable=false -Px2c.pluginMode=true --offline --no-configuration-cache \
    >"$task_failure_log" 2>&1; then
    echo "Disabled codegen unexpectedly accepted plugin mode" >&2
    exit 1
fi
rg -q 'x2cEnable=false requires pluginMode=false' "$task_failure_log"

# Switch back without clean: both source generation and compilation must recover.
"$task_root/scripts/build-x2c-artifact.sh" \
    --module :fixtures:producer --mode plugin --variant release \
    -- --offline --no-configuration-cache
rg -q '"mode": "PLUGIN_SYNTHETIC_IDS"' "$task_plugin_report"
echo "X2C pluginMode and x2cEnable switching verification passed"
