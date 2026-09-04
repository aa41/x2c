#!/usr/bin/env bash
set -euo pipefail

task_root=$(cd "$(dirname "$0")/.." && pwd)
task_gradle_cache=${GRADLE_USER_HOME:-$HOME/.gradle}
task_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
task_java8=${X2C_JAVA8_HOME:-}
task_java11=${X2C_JAVA11_HOME:-}
task_java17=${X2C_JAVA17_HOME:-}
task_offline_args=()

jdk_specification_version() {
    local task_candidate=$1
    "$task_candidate/bin/java" -XshowSettings:properties -version 2>&1 |
        awk -F= '/java.specification.version/ {
            gsub(/[[:space:]]/, "", $2)
            print $2
            exit
        }'
}

find_jdk_home() {
    local task_version=$1
    local task_preferred=${2:-}
    local task_candidate=
    local task_java_home_candidate=

    if [[ -x /usr/libexec/java_home ]]; then
        task_java_home_candidate=$(/usr/libexec/java_home -v "$task_version" 2>/dev/null || true)
    fi
    for task_candidate in \
        "$task_preferred" \
        "$task_java_home_candidate" \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        /Library/Java/JavaVirtualMachines/*/Contents/Home \
        /opt/homebrew/Cellar/openjdk@*/*/libexec/openjdk.jdk/Contents/Home; do
        if [[ -x "$task_candidate/bin/java" && -x "$task_candidate/bin/javac" ]] \
            && [[ $(jdk_specification_version "$task_candidate") == "$task_version" ]]; then
            printf '%s\n' "$task_candidate"
            return 0
        fi
    done
    return 1
}

if [[ -z "$task_java8" ]]; then
    task_java8=$(find_jdk_home 1.8 || true)
fi
if [[ -z "$task_java11" ]]; then
    task_java11=$(find_jdk_home 11 || true)
fi
if [[ -z "$task_java17" ]]; then
    task_java17=$(find_jdk_home 17 "${JAVA_HOME:-}" || true)
fi
if [[ ${X2C_OFFLINE:-false} == true ]]; then
    task_offline_args+=(--offline)
fi

for task_java in "$task_java8" "$task_java11" "$task_java17"; do
    if [[ ! -x "$task_java/bin/java" || ! -x "$task_java/bin/javac" ]]; then
        echo "Missing compatibility JDK: $task_java" >&2
        echo "Set X2C_JAVA8_HOME, X2C_JAVA11_HOME, and X2C_JAVA17_HOME." >&2
        exit 1
    fi
done

find_gradle() {
    local task_version=$1
    find "$task_gradle_cache/wrapper/dists/gradle-$task_version" \
        -type f -path '*/bin/gradle' -print 2>/dev/null | sort | head -1
}

task_gradle54=$(find_gradle '5.4.1-all')
task_gradle65=$(find_gradle '6.5-all')
task_gradle75=$(find_gradle '7.5-all')
task_gradle821=$(find_gradle '8.2.1-bin')
task_gradle84=$(find_gradle '8.4-bin')
task_gradle87=$(find_gradle '8.7-bin')
task_gradle89=$(find_gradle '8.9-bin')
task_gradle812=$(find_gradle '8.12-all')
task_gradle813=$(find_gradle '8.13-bin')
if [[ ! -x "$task_gradle54" || ! -x "$task_gradle65" || ! -x "$task_gradle75" \
    || ! -x "$task_gradle821" || ! -x "$task_gradle84" || ! -x "$task_gradle87" \
    || ! -x "$task_gradle89" || ! -x "$task_gradle812" || ! -x "$task_gradle813" ]]; then
    echo "Gradle 5.4.1, 6.5, 7.5, 8.2.1, 8.4, 8.7, 8.9, 8.12, and 8.13 distributions must exist in the wrapper cache." >&2
    exit 1
fi

if [[ ${X2C_OFFLINE:-false} == true ]]; then
    task_module_cache="$task_gradle_cache/caches/modules-2/files-2.1"
    task_compat_repo="$task_root/build/compat-maven-cache"
    if [[ ! -f "$task_compat_repo/com/android/tools/build/gradle/3.5.4/gradle-3.5.4.pom" ]]; then
        mkdir -p "$task_compat_repo"
        find "$task_module_cache" -mindepth 5 -maxdepth 5 -type f \
            \( -name '*.pom' -o -name '*.jar' -o -name '*.module' \) |
            while IFS= read -r task_file; do
                task_relative=${task_file#"$task_module_cache"/}
                task_group=${task_relative%%/*}
                task_rest=${task_relative#*/}
                task_module=${task_rest%%/*}
                task_rest=${task_rest#*/}
                task_version=${task_rest%%/*}
                task_name=${task_file##*/}
                task_group_path=$(printf '%s' "$task_group" | tr '.' '/')
                task_destination="$task_compat_repo/$task_group_path/$task_module/$task_version"
                mkdir -p "$task_destination"
                ln -sf "$task_file" "$task_destination/$task_name"
            done
    fi
fi

JAVA_HOME="$task_java17" "$task_root/gradlew" -p "$task_root/x2c-gradle-plugin" \
    publishToMavenLocal \
    :activity-compiler:publishToMavenLocal \
    :activity-gradle-plugin:publishToMavenLocal \
    ${task_offline_args[@]+"${task_offline_args[@]}"} \
    --no-configuration-cache --console=plain
JAVA_HOME="$task_java17" ANDROID_HOME="$task_sdk" "$task_root/gradlew" -p "$task_root" \
    :x2c-runtime:publishToMavenLocal \
    :x2c-plugin-api:publishToMavenLocal \
    ${task_offline_args[@]+"${task_offline_args[@]}"} \
    --no-configuration-cache --console=plain

run_fixture() {
    local task_gradle=$1
    local task_java_home=$2
    local task_agp=$3
    JAVA_HOME="$task_java_home" ANDROID_HOME="$task_sdk" "$task_gradle" \
        -p "$task_root/compatibility/fixture" clean verifyGeneratedSourceModel \
        "-PagpVersion=$task_agp" ${task_offline_args[@]+"${task_offline_args[@]}"} \
        --no-daemon --no-build-cache --console=plain
}

run_fixture "$task_gradle54" "$task_java8" 3.5.4
run_fixture "$task_gradle65" "$task_java8" 4.1.3
run_fixture "$task_gradle75" "$task_java11" 7.3.1
run_fixture "$task_gradle75" "$task_java11" 7.4.2
run_fixture "$task_gradle821" "$task_java17" 8.0.1
run_fixture "$task_gradle821" "$task_java17" 8.1.0
run_fixture "$task_gradle84" "$task_java17" 8.3.1
run_fixture "$task_gradle87" "$task_java17" 8.5.2
run_fixture "$task_gradle89" "$task_java17" 8.7.3
run_fixture "$task_gradle812" "$task_java17" 8.9.1
run_fixture "$task_gradle813" "$task_java17" 8.11.1
JAVA_HOME="$task_java17" ANDROID_HOME="$task_sdk" "$task_gradle813" \
    -p "$task_root/compatibility/fixture" clean verifyGeneratedSourceModel assembleRelease \
    -PagpVersion=8.11.1 -Px2cPluginMode=false \
    ${task_offline_args[@]+"${task_offline_args[@]}"} \
    --no-daemon --no-build-cache --console=plain
task_normal_aar="$task_root/compatibility/fixture/build/outputs/aar/x2c-agp-compatibility-fixture-release.aar"
if [[ ! -f "$task_normal_aar" ]] \
    || ! unzip -Z1 "$task_normal_aar" | awk '$0 == "res/layout/compatibility_screen.xml" { found=1 } END { exit found ? 0 : 1 }' \
    || ! unzip -Z1 "$task_normal_aar" | awk '$0 == "res/values/values.xml" { found=1 } END { exit found ? 0 : 1 }'; then
    echo "Normal integration mode did not retain resources in its AAR for host merging." >&2
    exit 1
fi
run_fixture "$task_gradle813" "$task_java17" 8.12.3
run_fixture "$task_gradle813" "$task_java17" 8.13.1

task_gradle813_home=$(cd "$(dirname "$task_gradle813")/.." && pwd)
task_builder_model=$(find "$task_gradle_cache/caches/modules-2/files-2.1/com.android.tools.build/builder-model/8.11.1" \
    -type f -name 'builder-model-8.11.1.jar' -print | sort | head -1)
if [[ ! -f "$task_builder_model" ]]; then
    echo "Missing AGP 8.11.1 builder-model JAR for Android Studio model verification." >&2
    exit 1
fi
task_model_classes="$task_root/build/compat-model-probe"
mkdir -p "$task_model_classes"
task_model_classpath="$task_builder_model"
for task_jar in "$task_gradle813_home"/lib/*.jar "$task_gradle813_home"/lib/plugins/*.jar; do
    task_model_classpath="$task_model_classpath:$task_jar"
done
"$task_java17/bin/javac" -proc:none -cp "$task_model_classpath" -d "$task_model_classes" \
    "$task_root/scripts/AndroidGeneratedSourceModelSmoke.java"
"$task_java17/bin/java" -cp "$task_model_classes:$task_model_classpath" AndroidGeneratedSourceModelSmoke \
    "$task_root/compatibility/fixture" 8.11.1 "$task_java17"

while IFS= read -r task_class; do
    task_major=$("$task_java17/bin/javap" -verbose "$task_class" | awk '/major version:/ { print $3; exit }')
    if [[ "$task_major" != 52 ]]; then
        echo "Plugin class is not Java 8 bytecode: $task_class (major=$task_major)" >&2
        exit 1
    fi
done < <(find "$task_root/x2c-gradle-plugin/build/classes/java/main" -type f -name '*.class' | sort)

echo "X2C AGP 3.5.x-8.x compatibility verification passed"
