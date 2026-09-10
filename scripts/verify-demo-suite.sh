#!/usr/bin/env bash
set -euo pipefail
task_root=$(cd "$(dirname "$0")/.." && pwd)
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
"$task_root/gradlew" -p "$task_root" :fixtures:consumer:assembleDebug --offline --configuration-cache
task_apk="$task_root/fixtures/consumer/build/outputs/apk/debug/consumer-debug.apk"
task_tmp=$(mktemp -d)
trap 'rm -rf "$task_tmp"' EXIT
for task_payload in x2c-layout-showcase x2c-component-showcase; do
    unzip -p "$task_apk" "assets/$task_payload/codegen-dex.jar" > "$task_tmp/$task_payload.jar"
    [[ $(unzip -Z1 "$task_tmp/$task_payload.jar") == classes.dex ]]
    unzip -p "$task_tmp/$task_payload.jar" classes.dex | strings > "$task_tmp/$task_payload.classes"
    unzip -p "$task_apk" "assets/$task_payload/codegen-dex.descriptor" > "$task_tmp/$task_payload.descriptor"
    [[ $(sed -n '5p' "$task_tmp/$task_payload.descriptor") == 2 ]]
done
rg -q 'Ldev/x2c/fixture/producer/DemoActivity;' "$task_tmp/x2c-layout-showcase.classes"
for task_type in VideoHomeActivity VideoDetailActivity ComponentShowcaseActivity ComponentProbeService ComponentProbeReceiver ComponentProbeProvider; do
    rg -q "Ldev/x2c/fixture/secondary/$task_type;" "$task_tmp/x2c-component-showcase.classes"
done
task_classjar="$task_root/fixtures/producer/build/outputs/x2c/release/codegen.jar"
if unzip -Z1 "$task_classjar" | rg '^dev/x2c/runtime/'; then
    echo "Plugin contains runtime duplicates" >&2; exit 1
fi
task_report="$task_root/fixtures/normal-library/build/reports/x2c/debug/report.json"
rg -q '"mode": "SYSTEM_RESOURCES"' "$task_report"
[[ -z $(find "$task_root/fixtures/normal-library/build/generated/java/x2cGenerateDebug" -name '*.java' -print -quit) ]]
unzip -Z1 "$task_apk" | rg '^res/raw/x2c_sample.mp4$' >/dev/null
task_reference_count=$(unzip -Z1 "$task_apk" | rg -c '^res/drawable[^/]*/bili_ref_.*\.png$')
[[ "$task_reference_count" == 17 ]]
task_manifest="$task_root/x2c-gradle-plugin/src/main/java/dev/x2c/compiler/resource/FrameworkViewRegistry.java"
task_expected=$(sed -n '/FRAMEWORK_VIEW_TAGS = set(/,/);/p' "$task_manifest" | tr ',' '\n' | sed -nE 's/.*"([^"]+)".*/\1/p' | wc -l | tr -d ' ')
task_actual=$(find "$task_root/fixtures/producer/src/main/res/layout" -name 'probe_*.xml' | wc -l | tr -d ' ')
[[ "$task_expected" == "$task_actual" ]]
task_child_probes=$(find "$task_root/fixtures/producer/src/main/res/layout" -name 'probe_*.xml' -print0 \
    | xargs -0 rg -l 'id="@\+id/probe_child"' | wc -l | tr -d ' ')
[[ "$task_child_probes" == 20 ]]
rg -q 'android:textColorHint="@color/success"' \
    "$task_root/fixtures/producer/src/main/res/layout/probe_edittext.xml"
rg -q 'android:scrollbars="vertical"' \
    "$task_root/fixtures/producer/src/main/res/layout/probe_scrollview.xml"
for task_custom_id in custom_context custom_attrs custom_input custom_pill custom_flow; do
    rg -Fq "@+id/$task_custom_id" "$task_root/fixtures/producer/src/main/res/layout/custom_views.xml"
done
while IFS= read -r task_tag; do
    task_name=$(printf '%s' "${task_tag##*.}" | tr '[:upper:]' '[:lower:]')
    [[ -f "$task_root/fixtures/producer/src/main/res/layout/probe_$task_name.xml" ]]
done < <(sed -n '/FRAMEWORK_VIEW_TAGS = set(/,/);/p' "$task_manifest" | tr ',' '\n' | sed -nE 's/.*"([^"]+)".*/\1/p')
echo "PASS: $task_actual framework XML cases ($task_child_probes real child trees); custom Views; two resource-free DEX payloads; normal system resources; host video"
echo "APK=$task_apk"
