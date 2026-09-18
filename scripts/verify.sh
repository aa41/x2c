#!/usr/bin/env bash
set -euo pipefail
task_root=$(cd "$(dirname "$0")/.." && pwd)
task_version=$(tr -d '[:space:]' < "$task_root/VERSION")
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
task_compiler_abi=$(sed -n 's/.*CURRENT_RUNTIME_ABI = \([0-9][0-9]*\);/\1/p' \
  "$task_root/x2c-gradle-plugin/activity-compiler/src/main/java/dev/x2c/plugin/compiler/ActivityJarTransformer.java")
task_host_abi=$(sed -n 's/.*CURRENT_RUNTIME_ABI = \([0-9][0-9]*\);/\1/p' \
  "$task_root/x2c-plugin-api/src/main/java/dev/x2c/plugin/api/PluginActivityRegistry.java")
if [[ -z "$task_compiler_abi" || "$task_compiler_abi" != "$task_host_abi" ]]; then
  echo "Component runtime ABI mismatch: compiler=$task_compiler_abi host=$task_host_abi" >&2
  exit 1
fi
"$task_root/gradlew" -p "$task_root/x2c-gradle-plugin" check --offline
"$task_root/gradlew" -p "$task_root" :x2c-runtime:check :x2c-plugin-api:check :x2c-plugin-base:check :x2c-plugin-loader:check :x2c-plugin-runtime:check :x2c-plugin-runtime:assembleRelease --offline
task_runtime_aar="$task_root/x2c-plugin-runtime/build/outputs/aar/x2c-plugin-runtime-release.aar"
task_runtime_rules=$(unzip -p "$task_runtime_aar" proguard.txt)
for task_keep_rule in \
  '-keep class dev.x2c.plugin.runtime.** { *; }' \
  '-keep class dev.x2c.plugin.api.** { *; }' \
  '-keep class dev.x2c.plugin.base.** { *; }' \
  '-keep class dev.x2c.runtime.** { *; }'; do
  if [[ "$task_runtime_rules" != *"$task_keep_rule"* ]]; then
    echo "Runtime AAR is missing consumer rule: $task_keep_rule" >&2
    exit 1
  fi
done
bash "$task_root/scripts/verify-demo-suite.sh"
task_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
"$JAVA_HOME/bin/java" --class-path "$task_root/x2c-runtime/build/libs/x2c-runtime-$task_version.jar:$task_sdk/platforms/android-36/android.jar" "$task_root/scripts/RuntimeRegistrySmoke.java"
echo "X2C foundation and three-demo suite verification passed"
