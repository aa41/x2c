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

echo "X2C pluginMode true/false verification passed"
