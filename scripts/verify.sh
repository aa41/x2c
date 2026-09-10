#!/usr/bin/env bash
set -euo pipefail
task_root=$(cd "$(dirname "$0")/.." && pwd)
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
"$task_root/gradlew" -p "$task_root/x2c-gradle-plugin" check --offline
"$task_root/gradlew" -p "$task_root" :x2c-runtime:check :x2c-plugin-api:check :x2c-plugin-base:check :x2c-plugin-loader:check :x2c-plugin-runtime:check --offline
bash "$task_root/scripts/verify-demo-suite.sh"
task_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
"$JAVA_HOME/bin/java" --class-path "$task_root/x2c-runtime/build/libs/x2c-runtime-0.1.0-SNAPSHOT.jar:$task_sdk/platforms/android-36/android.jar" "$task_root/scripts/RuntimeRegistrySmoke.java"
echo "X2C foundation and three-demo suite verification passed"
