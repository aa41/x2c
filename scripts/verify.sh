#!/usr/bin/env bash
set -euo pipefail

task_root=$(cd "$(dirname "$0")/.." && pwd)
task_java_home=${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}
task_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
task_aapt2=$(find "$task_sdk/build-tools" -maxdepth 2 -type f -name aapt2 | sort | tail -1)

if [[ ! -x "$task_java_home/bin/java" ]]; then
    echo "JDK not found: $task_java_home" >&2
    exit 1
fi
if [[ ! -x "$task_aapt2" ]]; then
    echo "aapt2 not found below: $task_sdk/build-tools" >&2
    exit 1
fi

export JAVA_HOME="$task_java_home"
export ANDROID_HOME="$task_sdk"
export LC_ALL=C
export LANG=C

"$task_root/gradlew" -p "$task_root/x2c-gradle-plugin" clean check --offline --warning-mode all
"$task_root/gradlew" -p "$task_root" \
    :fixtures:producer:x2cReleaseJar \
    :fixtures:producer:x2cReleaseDexJar \
    :fixtures:producer-secondary:x2cReleaseJar \
    :fixtures:producer-secondary:x2cReleaseDexJar \
    :fixtures:consumer:assembleRelease \
    :fixtures:normal-library:assembleRelease \
    :fixtures:normal-app:assembleRelease \
    --offline \
    --no-configuration-cache \
    "-Pandroid.aapt2FromMavenOverride=$task_aapt2"

task_class_jar="$task_root/fixtures/producer/build/outputs/x2c/release/codegen.jar"
task_dex_jar="$task_root/fixtures/producer/build/outputs/x2c/release/codegen-dex.jar"
task_secondary_class_jar="$task_root/fixtures/producer-secondary/build/outputs/x2c/release/codegen.jar"
task_secondary_dex_jar="$task_root/fixtures/producer-secondary/build/outputs/x2c/release/codegen-dex.jar"
task_apk="$task_root/fixtures/consumer/build/outputs/apk/release/consumer-release-unsigned.apk"
task_normal_aar="$task_root/fixtures/normal-library/build/outputs/aar/normal-library-release.aar"
task_normal_apk="$task_root/fixtures/normal-app/build/outputs/apk/release/normal-app-release-unsigned.apk"
task_normal_r2="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/R2.java"
task_normal_provider="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/X2cResourceProviderImpl.java"
task_normal_bootstrap="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/x2c/X2cModuleBootstrap.java"
task_normal_report="$task_root/fixtures/normal-library/build/reports/x2c/release/report.json"
task_secondary_images="$task_root/fixtures/producer-secondary/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/secondary/generated/X2cImages.java"
task_test_image_hash="567cdec892c6f4a140706350f057fe98fc3871bca7fa1217f8be88c7f5b34b5b"

if jar tf "$task_class_jar" | awk '!/\.class$/ { found=1 } END { exit found ? 0 : 1 }'; then
    echo "codegen.jar contains a non-class entry" >&2
    exit 1
fi
if jar tf "$task_class_jar" | awk '/(^|\/)R(\$.*)?\.class$/ { found=1 } END { exit found ? 0 : 1 }'; then
    echo "codegen.jar contains an Android R class" >&2
    exit 1
fi
if ! jar tf "$task_class_jar" | rg -q '^dev/x2c/fixture/generated/X2cModule\.class$'; then
    echo "codegen.jar does not contain the generated X2C module registry" >&2
    exit 1
fi
if ! jar tf "$task_class_jar" | rg -q '^dev/x2c/fixture/generated/X2cResourceProviderImpl\.class$'; then
    echo "codegen.jar does not contain the runtime resource provider" >&2
    exit 1
fi
if ! jar tf "$task_class_jar" | rg -q '^dev/x2c/fixture/producer/x2c/X2cModuleBootstrap\.class$' \
    || ! jar tf "$task_secondary_class_jar" | rg -q \
        '^dev/x2c/fixture/secondary/x2c/X2cModuleBootstrap\.class$'; then
    echo "codegen JAR is missing an automatic module bootstrap" >&2
    exit 1
fi
if jar tf "$task_class_jar" | rg -q '^dev/x2c/runtime/'; then
    echo "codegen.jar must resolve x2c-runtime from the host ClassLoader" >&2
    exit 1
fi
if jar tf "$task_class_jar" | rg -q '(^|/)X2cIds(\$.*)?\.class$'; then
    echo "codegen.jar contains obsolete duplicate X2cIds classes" >&2
    exit 1
fi
if jar tf "$task_class_jar" | rg -q '^dev/x2c/fixture/generated/X2C\.class$' \
    || jar tf "$task_secondary_class_jar" | rg -q '^dev/x2c/fixture/secondary/generated/X2C\.class$'; then
    echo "codegen JAR contains the obsolete generated X2C facade" >&2
    exit 1
fi
if rg -n 'import dev\.x2c\.fixture\.generated\.|\bR2\.|\bX2cModule\b|\bX2cValues\b' \
    "$task_root/fixtures/producer/src/main/java" --glob '*.java'; then
    echo "producer business source directly references generated implementation classes" >&2
    exit 1
fi
if rg -n 'MODULE_CLASS|SECONDARY_MODULE_CLASS|private static final String MODULE|getModuleName\(' \
    "$task_root/fixtures/producer/src/main/java" \
    "$task_root/fixtures/producer-secondary/src/main/java" \
    "$task_root/fixtures/normal-library/src/main/java" \
    "$task_root/fixtures/consumer/src/main/java" --glob '*.java'; then
    echo "business source declares or manually consumes an X2C module name" >&2
    exit 1
fi
if [[ $(jar tf "$task_dex_jar") != "classes.dex" ]]; then
    echo "codegen-dex.jar must contain only classes.dex" >&2
    exit 1
fi
if unzip -Z1 "$task_apk" | awk '
    /^(res|assets)\// &&
    $0 != "assets/x2c-demo/codegen-dex.jar" &&
    $0 != "assets/x2c-demo/codegen-dex.sha256" &&
    $0 != "assets/x2c-demo-secondary/codegen-dex.jar" &&
    $0 != "assets/x2c-demo-secondary/codegen-dex.sha256" { found=1 }
    END { exit found ? 0 : 1 }
'; then
    echo "consumer APK contains an unexpected res/ or assets/ entry" >&2
    exit 1
fi
task_dex_hash_embedded=$(unzip -p "$task_apk" assets/x2c-demo/codegen-dex.jar | shasum -a 256 | awk '{ print $1 }')
task_dex_hash_declared=$(unzip -p "$task_apk" assets/x2c-demo/codegen-dex.sha256 | tr -d '[:space:]')
task_dex_hash_actual=$(shasum -a 256 "$task_dex_jar" | awk '{ print $1 }')
if [[ "$task_dex_hash_embedded" != "$task_dex_hash_actual" || "$task_dex_hash_declared" != "$task_dex_hash_actual" ]]; then
    echo "consumer APK dynamic DEX payload does not match codegen-dex.jar" >&2
    exit 1
fi
task_secondary_hash_embedded=$(unzip -p "$task_apk" assets/x2c-demo-secondary/codegen-dex.jar | shasum -a 256 | awk '{ print $1 }')
task_secondary_hash_declared=$(unzip -p "$task_apk" assets/x2c-demo-secondary/codegen-dex.sha256 | tr -d '[:space:]')
task_secondary_hash_actual=$(shasum -a 256 "$task_secondary_dex_jar" | awk '{ print $1 }')
if [[ "$task_secondary_hash_embedded" != "$task_secondary_hash_actual" \
    || "$task_secondary_hash_declared" != "$task_secondary_hash_actual" ]]; then
    echo "consumer APK secondary DEX payload does not match codegen-dex.jar" >&2
    exit 1
fi
if unzip -p "$task_apk" classes.dex | strings | rg \
    'dev/x2c/fixture/(producer/|generated/|secondary/)' >/dev/null; then
    echo "consumer classes.dex statically contains producer/generated classes" >&2
    exit 1
fi
if ! unzip -p "$task_apk" classes.dex | strings | rg 'dev/x2c/runtime/(X2C|PluginClassLoader)' >/dev/null; then
    echo "consumer classes.dex does not contain the host-owned x2c-runtime" >&2
    exit 1
fi
if ! unzip -p "$task_dex_jar" classes.dex | strings | rg 'dev/x2c/fixture/producer/DemoActivity' >/dev/null; then
    echo "codegen-dex.jar does not contain the dynamic DemoActivity" >&2
    exit 1
fi
for task_secondary_symbol in SecondaryActivity SecondaryLibrary generated/X2cModule; do
    if ! unzip -p "$task_secondary_dex_jar" classes.dex | strings | rg -q \
        "dev/x2c/fixture/secondary/$task_secondary_symbol"; then
        echo "secondary codegen-dex.jar is missing $task_secondary_symbol" >&2
        exit 1
    fi
done
if ! rg -Fq 'https://img2.baidu.com/it/u=2604349716,1033098650&fm=253&fmt=auto&app=120&f=JPEG?w=500&h=750' \
    "$task_secondary_images" \
    || ! rg -q "$task_test_image_hash" "$task_secondary_images"; then
    echo "secondary CDN metadata does not match the locked test image" >&2
    exit 1
fi
if rg -q 'private static final String MODULE' "$task_secondary_images"; then
    echo "generated image bridge duplicated the canonical module name" >&2
    exit 1
fi
task_manifest=$($task_aapt2 dump xmltree "$task_apk" --file AndroidManifest.xml)
if ! rg -q 'dev\.x2c\.fixture\.producer\.DemoActivity' <<<"$task_manifest"; then
    echo "consumer manifest does not declare the dynamic DemoActivity" >&2
    exit 1
fi
if ! rg -q 'dev\.x2c\.fixture\.secondary\.SecondaryActivity' <<<"$task_manifest"; then
    echo "consumer manifest does not declare the secondary dynamic Activity" >&2
    exit 1
fi
if ! rg -q 'DynamicAppComponentFactory' <<<"$task_manifest"; then
    echo "consumer manifest does not install DynamicAppComponentFactory" >&2
    exit 1
fi

if ! unzip -Z1 "$task_normal_aar" | awk '
    $0 == "res/layout/normal_content.xml" { layout=1 }
    $0 == "res/drawable/normal_button.xml" { drawable=1 }
    $0 == "res/drawable/local_product.jpg" { bitmap=1 }
    END { exit layout && drawable && bitmap ? 0 : 1 }
'; then
    echo "normal-library AAR did not retain its Android resources and local bitmap" >&2
    exit 1
fi
task_normal_image_hash=$(unzip -p "$task_normal_aar" res/drawable/local_product.jpg \
    | shasum -a 256 | awk '{ print $1 }')
if [[ "$task_normal_image_hash" != "$task_test_image_hash" ]]; then
    echo "normal-library AAR local bitmap differs from the supplied test image" >&2
    exit 1
fi
if ! unzip -p "$task_normal_aar" AndroidManifest.xml | strings | rg -q \
    'dev\.x2c\.fixture\.normal\.NormalDemoActivity'; then
    echo "normal-library AAR manifest does not declare NormalDemoActivity" >&2
    exit 1
fi
if ! rg -q 'context\.getResources\(\)\.getIdentifier' "$task_normal_r2" \
    || rg -q 'public static final int normal_content' "$task_normal_r2"; then
    echo "normal integration R2 is not host-resource-backed" >&2
    exit 1
fi
if ! rg -q 'context\.getResources\(\)\.getIdentifier\(name, type, context\.getPackageName\(\)\)' \
    "$task_normal_provider" \
    || rg -q 'return R2\.layout\.normal_content' "$task_normal_provider"; then
    echo "normal integration provider is not directly host-resource-backed" >&2
    exit 1
fi
if ! rg -q 'dev\.x2c\.fixture\.normal\.generated\.X2cModule\.init\(context\)' \
    "$task_normal_bootstrap"; then
    echo "normal integration automatic module bootstrap was not generated" >&2
    exit 1
fi
if ! rg -q 'getDrawable\(R2\.drawable\.local_product, context\.getTheme\(\)\)' \
    "$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/X2cLayouts.java" \
    || [[ -e "$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/X2cImages.java" ]]; then
    echo "normal AAR bitmap was incorrectly converted into CDN metadata" >&2
    exit 1
fi
if ! rg -q '"mode": "HOST_RESOURCE_IDS"' "$task_normal_report"; then
    echo "normal integration report has the wrong mode" >&2
    exit 1
fi
if ! rg -q '"hostBitmapDrawables": 1' "$task_normal_report" \
    || ! rg -q '"cdnImages": 0' "$task_normal_report" \
    || [[ -e "$(dirname "$task_normal_report")/assets-candidates.json" ]]; then
    echo "normal integration report does not describe a host-backed bitmap" >&2
    exit 1
fi
if rg -n 'dev\.x2c\.fixture\.normal\.generated\.(R2|X2cModule|X2cValues)' \
    "$task_root/fixtures/normal-library/src/main/java" --glob '*.java'; then
    echo "normal-library business source directly references generated implementation classes" >&2
    exit 1
fi
task_normal_manifest=$($task_aapt2 dump xmltree "$task_normal_apk" --file AndroidManifest.xml)
if ! rg -q 'dev\.x2c\.fixture\.normal\.NormalDemoActivity' <<<"$task_normal_manifest"; then
    echo "normal app did not merge the library Activity declaration" >&2
    exit 1
fi
if ! unzip -p "$task_normal_apk" classes.dex | strings | rg -q \
    'Ldev/x2c/fixture/normal/generated/X2cModule;'; then
    echo "normal app does not statically contain the generated module" >&2
    exit 1
fi
if ! unzip -p "$task_normal_apk" classes.dex | strings | rg -q \
    'Ldev/x2c/fixture/normal/x2c/X2cModuleBootstrap;'; then
    echo "normal app does not contain the automatic module bootstrap" >&2
    exit 1
fi
if unzip -Z1 "$task_normal_apk" | rg -q '^assets/x2c-demo/'; then
    echo "normal app unexpectedly contains the dynamic plugin payload" >&2
    exit 1
fi

"$task_java_home/bin/java" -Dfile.encoding=UTF-8 \
    "$task_root/scripts/ClassJarSmoke.java" "$task_class_jar"
"$task_java_home/bin/java" -Dfile.encoding=UTF-8 \
    --class-path "$task_root/x2c-runtime/build/libs/x2c-runtime-0.1.0-SNAPSHOT.jar:$task_sdk/platforms/android-36/android.jar" \
    "$task_root/scripts/RuntimeRegistrySmoke.java"

task_class_hash_before=$(shasum -a 256 "$task_class_jar" | awk '{ print $1 }')
task_dex_hash_before=$(shasum -a 256 "$task_dex_jar" | awk '{ print $1 }')
"$task_root/gradlew" -p "$task_root" \
    :fixtures:producer:x2cReleaseJar \
    :fixtures:producer:x2cReleaseDexJar \
    --rerun-tasks \
    --offline \
    --no-configuration-cache
task_class_hash_after=$(shasum -a 256 "$task_class_jar" | awk '{ print $1 }')
task_dex_hash_after=$(shasum -a 256 "$task_dex_jar" | awk '{ print $1 }')
if [[ "$task_class_hash_before" != "$task_class_hash_after" || "$task_dex_hash_before" != "$task_dex_hash_after" ]]; then
    echo "JAR output is not deterministic across rebuilds" >&2
    exit 1
fi

"$task_aapt2" dump resources "$task_apk"
shasum -a 256 "$task_class_jar" "$task_dex_jar" \
    "$task_secondary_class_jar" "$task_secondary_dex_jar" "$task_apk"
echo "X2C verification passed"
