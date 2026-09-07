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
    :x2c-runtime:check \
    :x2c-plugin-api:check \
    :x2c-plugin-base:check \
    :x2c-plugin-loader:check \
    :x2c-plugin-runtime:check \
    :fixtures:business-base:assembleRelease \
    :fixtures:producer:x2cReleaseJar \
    :fixtures:producer:x2cBuildRelease \
    :fixtures:producer-secondary:x2cReleaseJar \
    :fixtures:producer-secondary:x2cReleaseDexJar \
    :fixtures:consumer:assembleRelease \
    :fixtures:normal-library:assembleRelease \
    :fixtures:normal-app:assembleRelease \
    --offline \
    --no-configuration-cache \
    "-Pandroid.aapt2FromMavenOverride=$task_aapt2"

task_class_jar="$task_root/fixtures/producer/build/outputs/x2c/release/codegen.jar"
task_activity_jar="$task_root/fixtures/producer/build/outputs/x2c/release/activity-plugin.jar"
task_dex_jar="$task_root/fixtures/producer/build/outputs/x2c/release/codegen-dex.jar"
task_secondary_class_jar="$task_root/fixtures/producer-secondary/build/outputs/x2c/release/codegen.jar"
task_secondary_activity_jar="$task_root/fixtures/producer-secondary/build/outputs/x2c/release/activity-plugin.jar"
task_secondary_dex_jar="$task_root/fixtures/producer-secondary/build/outputs/x2c/release/codegen-dex.jar"
task_activity_report="$task_root/fixtures/producer/build/reports/x2c/release/activities.json"
task_secondary_activity_report="$task_root/fixtures/producer-secondary/build/reports/x2c/release/activities.json"
task_business_base_aar="$task_root/fixtures/business-base/build/outputs/aar/business-base-release.aar"
task_verify_tmp=$(mktemp -d)
trap 'rm -rf "$task_verify_tmp"' EXIT
task_business_base_classes="$task_verify_tmp/business-base-classes.jar"
unzip -p "$task_business_base_aar" classes.jar > "$task_business_base_classes"
task_apk="$task_root/fixtures/consumer/build/outputs/apk/release/consumer-release-unsigned.apk"
task_normal_aar="$task_root/fixtures/normal-library/build/outputs/aar/normal-library-release.aar"
task_normal_apk="$task_root/fixtures/normal-app/build/outputs/apk/release/normal-app-release-unsigned.apk"
task_normal_r2="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/R2.java"
task_normal_module="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/X2cModule.java"
task_normal_provider="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated/X2cResourceProviderImpl.java"
task_normal_bootstrap="$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/x2c/X2cModuleBootstrap.java"
task_normal_report="$task_root/fixtures/normal-library/build/reports/x2c/release/report.json"
task_layout_images="$task_root/fixtures/producer/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/generated/X2cImages.java"
task_component_images="$task_root/fixtures/producer-secondary/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/secondary/generated/X2cImages.java"
task_test_image_hash="567cdec892c6f4a140706350f057fe98fc3871bca7fa1217f8be88c7f5b34b5b"

task_init_calls=$(rg -n '^[[:space:]]*X2C\.init\(' \
    "$task_root/fixtures" \
    "$task_root/x2c-plugin-api" \
    "$task_root/x2c-plugin-loader" \
    "$task_root/x2c-plugin-runtime" \
    "$task_root/x2c-runtime" \
    --glob '*.java' --glob '*.kt' || true)
if [[ $(printf '%s\n' "$task_init_calls" | sed '/^$/d' | wc -l | tr -d '[:space:]') != 2 ]] \
    || [[ "$task_init_calls" != *'/HostApplication.java:'* ]] \
    || [[ "$task_init_calls" != *'/NormalApplication.java:'* ]]; then
    echo "X2C.init(context) must only be called once by each demo Application" >&2
    printf '%s\n' "$task_init_calls" >&2
    exit 1
fi

if jar tf "$task_class_jar" | awk '!/\.class$/ { found=1 } END { exit found ? 0 : 1 }'; then
    echo "codegen.jar contains a non-class entry" >&2
    exit 1
fi
if jar tf "$task_class_jar" | awk '/(^|\/)R(\$.*)?\.class$/ { found=1 } END { exit found ? 0 : 1 }'; then
    echo "codegen.jar contains an Android R class" >&2
    exit 1
fi
if ! jar tf "$task_class_jar" | rg '^dev/x2c/fixture/generated/X2cModule\.class$' >/dev/null; then
    echo "codegen.jar does not contain the generated X2C module registry" >&2
    exit 1
fi
if ! jar tf "$task_class_jar" | rg '^dev/x2c/fixture/generated/X2cResourceProviderImpl\.class$' >/dev/null; then
    echo "codegen.jar does not contain the runtime resource provider" >&2
    exit 1
fi
if ! jar tf "$task_class_jar" \
        | rg '^dev/x2c/fixture/producer/x2c/X2cModuleBootstrap\.class$' >/dev/null \
    || ! jar tf "$task_secondary_class_jar" \
        | rg '^dev/x2c/fixture/secondary/x2c/X2cModuleBootstrap\.class$' >/dev/null; then
    echo "codegen JAR is missing an automatic module bootstrap" >&2
    exit 1
fi
if jar tf "$task_class_jar" | rg '^dev/x2c/runtime/' >/dev/null; then
    echo "codegen.jar must resolve x2c-runtime from the host ClassLoader" >&2
    exit 1
fi
if jar tf "$task_class_jar" | rg '(^|/)X2cIds(\$.*)?\.class$' >/dev/null; then
    echo "codegen.jar contains obsolete duplicate X2cIds classes" >&2
    exit 1
fi
if jar tf "$task_class_jar" | rg '^dev/x2c/fixture/generated/X2C\.class$' >/dev/null \
    || jar tf "$task_secondary_class_jar" \
        | rg '^dev/x2c/fixture/secondary/generated/X2C\.class$' >/dev/null; then
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
if ! javap -classpath "$task_activity_jar" dev.x2c.fixture.producer.DemoActivity \
    | rg 'extends dev\.x2c\.fixture\.businessbase\.BusinessBaseActivity' >/dev/null \
    || ! javap -classpath "$task_activity_jar" \
        dev.x2c.fixture.businessbase.BusinessBaseActivity \
        | rg 'extends dev\.x2c\.plugin\.runtime\.PluginActivity' >/dev/null \
    || ! javap -classpath "$task_secondary_activity_jar" \
        dev.x2c.fixture.secondary.ComponentShowcaseActivity \
        | rg 'extends dev\.x2c\.fixture\.businessbase\.BusinessBaseActivity' >/dev/null \
    || ! javap -classpath "$task_secondary_activity_jar" \
        dev.x2c.fixture.secondary.LaunchModeActivityBase \
        | rg 'extends dev\.x2c\.fixture\.businessbase\.BusinessBaseActivity' >/dev/null \
    || ! javap -classpath "$task_secondary_activity_jar" \
        dev.x2c.fixture.businessbase.BusinessBaseActivity \
        | rg 'extends dev\.x2c\.plugin\.runtime\.PluginActivity' >/dev/null \
    || ! javap -classpath "$task_secondary_activity_jar" \
        dev.x2c.fixture.secondary.ComponentProbeService \
        | rg 'extends dev\.x2c\.plugin\.base\.BasePluginService' >/dev/null \
    || ! javap -classpath "$task_secondary_activity_jar" \
        dev.x2c.fixture.secondary.ComponentProbeReceiver \
        | rg 'extends dev\.x2c\.plugin\.base\.BasePluginReceiver' >/dev/null \
    || ! javap -classpath "$task_secondary_activity_jar" \
        dev.x2c.fixture.secondary.ComponentProbeProvider \
        | rg 'extends dev\.x2c\.plugin\.base\.BasePluginProvider' >/dev/null; then
    echo "shared-source BusinessBase or host-owned component base boundary is missing" >&2
    exit 1
fi
for task_transformed_jar in "$task_activity_jar" "$task_secondary_activity_jar"; do
    if ! jar tf "$task_transformed_jar" \
            | rg '^dev/x2c/fixture/businessbase/BusinessBaseActivity\.class$' >/dev/null; then
        echo "plugin-private BusinessBaseActivity transform unit is missing" >&2
        exit 1
    fi
    if jar tf "$task_transformed_jar" \
            | rg '^dev/x2c/fixture/businessbase/analytics/' >/dev/null; then
        echo "host-owned analytics leaked into a plugin payload" >&2
        exit 1
    fi
done
if ! javap -classpath "$task_business_base_classes" \
        dev.x2c.fixture.businessbase.BusinessBaseActivity \
        | rg 'extends android\.app\.Activity' >/dev/null; then
    echo "native BusinessBaseActivity must retain the framework Activity root" >&2
    exit 1
fi
for task_transformed_jar in "$task_activity_jar" "$task_secondary_activity_jar"; do
    if ! jar tf "$task_transformed_jar" \
        | rg '^dev/x2c/generated/plugin/ComponentRegistry\.class$' >/dev/null; then
        echo "transformed plugin JAR is missing its generated component registry" >&2
        exit 1
    fi
    if jar tf "$task_transformed_jar" \
        | rg '^dev/x2c/plugin/(api|base|runtime|loader)/' >/dev/null; then
        echo "transformed plugin JAR embeds host-owned plugin API/base/runtime classes" >&2
        exit 1
    fi
done
if ! rg -q '"pluginId": "dev\.x2c\.fixture\.layout-showcase"' "$task_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.producer\.DemoActivity"' \
        "$task_activity_report" \
    || ! rg -q '"launchMode": "STANDARD"' "$task_activity_report" \
    || ! rg -q '"pluginId": "dev\.x2c\.fixture\.component-showcase"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.ComponentShowcaseActivity"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.StandardActivity"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.SingleTopActivity"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.SingleTaskActivity"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.SingleInstanceActivity"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"launchMode": "STANDARD"' "$task_secondary_activity_report" \
    || ! rg -q '"launchMode": "SINGLE_TOP"' "$task_secondary_activity_report" \
    || ! rg -q '"launchMode": "SINGLE_TASK"' "$task_secondary_activity_report" \
    || ! rg -q '"launchMode": "SINGLE_INSTANCE"' "$task_secondary_activity_report"; then
    echo "generated Activity reports do not match the two plugin contracts" >&2
    exit 1
fi
if ! rg -q '"artifact": "business-base/classes\.jar"' "$task_activity_report" \
    || ! rg -q '"type": "compileOnly-jar"' "$task_activity_report" \
    || ! rg -q '"classes": 1' "$task_activity_report" \
    || ! rg -q '"runtimeAbiVersion": 1' "$task_activity_report" \
    || ! rg -q '"dependencyClasses": "explicit-plugin-private"' "$task_activity_report" \
    || ! rg -q '"compileOnlyReferences": "host-fallback"' "$task_activity_report" \
    || ! rg -q '"androidxAndResourceAars": "rejected-in-phase-1"' "$task_activity_report" \
    || ! rg -q '"artifact": "business-base/classes\.jar"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"type": "compileOnly-jar"' "$task_secondary_activity_report" \
    || ! rg -q '"classes": 1' "$task_secondary_activity_report" \
    || ! rg -q '"compileOnlyReferences": "host-fallback"' "$task_secondary_activity_report"; then
    echo "generated component reports do not describe the dependency ownership closure" >&2
    exit 1
fi
if ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.ComponentProbeService"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.ComponentProbeReceiver"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"className": "dev\.x2c\.fixture\.secondary\.ComponentProbeProvider"' \
        "$task_secondary_activity_report" \
    || ! rg -q '"authority": "dev\.x2c\.fixture\.component\.provider"' \
        "$task_secondary_activity_report"; then
    echo "generated component report is incomplete" >&2
    exit 1
fi
if unzip -Z1 "$task_apk" | awk '
    /^(res|assets)\// &&
    $0 != "assets/x2c-layout-showcase/codegen-dex.jar" &&
    $0 != "assets/x2c-layout-showcase/codegen-dex.descriptor" &&
    $0 != "assets/x2c-layout-showcase/codegen-dex.sig" &&
    $0 != "assets/x2c-component-showcase/codegen-dex.jar" &&
    $0 != "assets/x2c-component-showcase/codegen-dex.descriptor" &&
    $0 != "assets/x2c-component-showcase/codegen-dex.sig" { found=1 }
    END { exit found ? 0 : 1 }
'; then
    echo "consumer APK contains an unexpected res/ or assets/ entry" >&2
    exit 1
fi
task_dex_hash_embedded=$(unzip -p "$task_apk" assets/x2c-layout-showcase/codegen-dex.jar | shasum -a 256 | awk '{ print $1 }')
task_dex_hash_declared=$(unzip -p "$task_apk" assets/x2c-layout-showcase/codegen-dex.descriptor | tail -n 1)
task_dex_hash_actual=$(shasum -a 256 "$task_dex_jar" | awk '{ print $1 }')
if [[ "$task_dex_hash_embedded" != "$task_dex_hash_actual" || "$task_dex_hash_declared" != "$task_dex_hash_actual" ]]; then
    echo "consumer APK dynamic DEX payload does not match codegen-dex.jar" >&2
    exit 1
fi
task_secondary_hash_embedded=$(unzip -p "$task_apk" assets/x2c-component-showcase/codegen-dex.jar | shasum -a 256 | awk '{ print $1 }')
task_secondary_hash_declared=$(unzip -p "$task_apk" assets/x2c-component-showcase/codegen-dex.descriptor | tail -n 1)
task_secondary_hash_actual=$(shasum -a 256 "$task_secondary_dex_jar" | awk '{ print $1 }')
if [[ "$task_secondary_hash_embedded" != "$task_secondary_hash_actual" \
    || "$task_secondary_hash_declared" != "$task_secondary_hash_actual" ]]; then
    echo "consumer APK secondary DEX payload does not match codegen-dex.jar" >&2
    exit 1
fi
if [[ $(unzip -p "$task_apk" assets/x2c-layout-showcase/codegen-dex.sig | wc -c | tr -d '[:space:]') != 256 ]] \
    || [[ $(unzip -p "$task_apk" assets/x2c-component-showcase/codegen-dex.sig | wc -c | tr -d '[:space:]') != 256 ]]; then
    echo "consumer APK does not contain two RSA-2048 plugin signatures" >&2
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
if ! unzip -p "$task_dex_jar" classes.dex | strings \
    | rg 'dev/x2c/fixture/producer/DemoActivity' >/dev/null \
    || ! unzip -p "$task_dex_jar" classes.dex | strings \
        | rg 'dev/x2c/fixture/businessbase/BusinessBaseActivity' >/dev/null \
    || ! unzip -p "$task_dex_jar" classes.dex | strings \
        | rg 'dev/x2c/generated/plugin/ComponentRegistry' >/dev/null; then
    echo "layout codegen-dex.jar does not contain its Activity and registry" >&2
    exit 1
fi
for task_secondary_symbol in ComponentShowcaseActivity StandardActivity SingleTopActivity SingleTaskActivity SingleInstanceActivity ComponentProbeService ComponentProbeReceiver ComponentProbeProvider ComponentShowcaseLibrary generated/X2cModule; do
    if ! unzip -p "$task_secondary_dex_jar" classes.dex | strings \
        | rg "dev/x2c/fixture/secondary/$task_secondary_symbol" >/dev/null; then
        echo "secondary codegen-dex.jar is missing $task_secondary_symbol" >&2
        exit 1
    fi
done
if ! rg -Fq 'https://img2.baidu.com/it/u=2604349716,1033098650&fm=253&fmt=auto&app=120&f=JPEG?w=500&h=750' \
    "$task_layout_images" \
    || ! rg -q "$task_test_image_hash" "$task_layout_images" \
    || ! rg -q "$task_test_image_hash" "$task_component_images"; then
    echo "showcase CDN metadata does not match the locked test image" >&2
    exit 1
fi
if rg -q 'private static final String MODULE' "$task_layout_images" \
    || rg -q 'private static final String MODULE' "$task_component_images"; then
    echo "generated image bridge duplicated the canonical module name" >&2
    exit 1
fi
task_manifest=$($task_aapt2 dump xmltree "$task_apk" --file AndroidManifest.xml)
if rg -q 'dev\.x2c\.fixture\.(producer\.DemoActivity|secondary\.ComponentShowcaseActivity)' \
    <<<"$task_manifest"; then
    echo "consumer manifest statically declares a plugin Activity" >&2
    exit 1
fi
if rg -q 'DynamicAppComponentFactory' <<<"$task_manifest"; then
    echo "obsolete AppComponentFactory hook remains in the consumer manifest" >&2
    exit 1
fi
task_container_count=$(rg -o 'dev\.x2c\.plugin\.runtime\.PluginContainerActivities\$[A-Za-z]+[0-7]' \
    <<<"$task_manifest" | sort -u | wc -l | tr -d '[:space:]')
if [[ "$task_container_count" != 32 ]]; then
    echo "consumer manifest must contain exactly 4 launch modes x 8 containers" >&2
    exit 1
fi
task_service_container_count=$(rg -o \
    'dev\.x2c\.plugin\.runtime\.PluginContainerServices\$Slot[0-7]' \
    <<<"$task_manifest" | sort -u | wc -l | tr -d '[:space:]')
if [[ "$task_service_container_count" != 8 ]]; then
    echo "consumer manifest must contain exactly 8 non-exported Service containers" >&2
    exit 1
fi
if ! rg -q 'dev\.x2c\.plugin\.runtime\.PluginContainerReceiver' <<<"$task_manifest" \
    || ! rg -q 'dev\.x2c\.plugin\.runtime\.PluginContainerProvider' <<<"$task_manifest" \
    || ! rg -q 'dev\.x2c\.fixture\.consumer\.x2c\.plugin\.provider' \
        <<<"$task_manifest"; then
    echo "consumer manifest is missing Receiver/Provider plugin containers" >&2
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
if ! unzip -p "$task_normal_aar" AndroidManifest.xml | strings \
    | rg 'dev\.x2c\.fixture\.normal\.NormalDemoActivity' >/dev/null; then
    echo "normal-library AAR manifest does not declare NormalDemoActivity" >&2
    exit 1
fi
if ! rg -q 'public static int normal_content\(Context context\)' "$task_normal_r2" \
    || ! rg -q 'return X2cModule\.identifier\(context, type, name\)' "$task_normal_r2" \
    || rg -q 'R2\.init|X2cModule\.init\(context\)|public static int normal_content;|getResources\(\)\.getIdentifier' "$task_normal_r2"; then
    echo "normal integration R2 is not a stateless provider facade" >&2
    exit 1
fi
if ! rg -q 'context\.getResources\(\)\.getIdentifier\(name, type, context\.getPackageName\(\)\)' \
    "$task_normal_provider" \
    || ! rg -q 'public int getIdentifier\(String type, String name\)' "$task_normal_provider" \
    || ! rg -q 'synchronized \(identifiers\)' "$task_normal_provider" \
    || ! rg -q 'identifiers\.put\(key, Integer\.valueOf\(identifier\)\)' "$task_normal_provider" \
    || rg -q 'return R2\.layout\.normal_content' "$task_normal_provider"; then
    echo "normal integration provider is not the cached host-ID owner" >&2
    exit 1
fi
if rg -q 'R2\.init\(context\)' "$task_normal_module" \
    || ! rg -q 'private static volatile boolean initialized' "$task_normal_module" \
    || ! rg -q 'if \(!initialized\) init\(context\)' "$task_normal_module" \
    || ! rg -q 'created\.getIdentifier\("layout", "normal_content"\)' "$task_normal_module"; then
    echo "normal integration module still depends on R2 initialization" >&2
    exit 1
fi
task_normal_identifier_owners=$(rg -l 'getResources\(\)\.getIdentifier' \
    "$task_root/fixtures/normal-library/build/generated/java/x2cGenerateRelease/dev/x2c/fixture/normal/generated" \
    --glob '*.java' | wc -l | tr -d '[:space:]')
if [[ "$task_normal_identifier_owners" != "1" ]]; then
    echo "normal generated code has more than one Resources#getIdentifier owner" >&2
    exit 1
fi
if ! rg -q 'dev\.x2c\.fixture\.normal\.generated\.X2cModule\.init\(context\)' \
    "$task_normal_bootstrap"; then
    echo "normal integration automatic module bootstrap was not generated" >&2
    exit 1
fi
if ! rg -q 'getDrawable\(X2cModule\.identifier\("drawable", "local_product"\), context\.getTheme\(\)\)' \
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
if ! unzip -p "$task_normal_apk" classes.dex | strings \
    | rg 'Ldev/x2c/fixture/normal/generated/X2cModule;' >/dev/null; then
    echo "normal app does not statically contain the generated module" >&2
    exit 1
fi
if ! unzip -p "$task_normal_apk" classes.dex | strings \
    | rg 'Ldev/x2c/fixture/normal/x2c/X2cModuleBootstrap;' >/dev/null; then
    echo "normal app does not contain the automatic module bootstrap" >&2
    exit 1
fi
if unzip -Z1 "$task_normal_apk" \
    | rg '^assets/x2c-(layout|component)-showcase/' >/dev/null; then
    echo "normal app unexpectedly contains the dynamic plugin payload" >&2
    exit 1
fi

"$task_java_home/bin/java" -Dfile.encoding=UTF-8 \
    "$task_root/scripts/ClassJarSmoke.java" "$task_class_jar"
"$task_java_home/bin/java" -Dfile.encoding=UTF-8 \
    --class-path "$task_root/x2c-runtime/build/libs/x2c-runtime-0.1.0-SNAPSHOT.jar:$task_sdk/platforms/android-36/android.jar" \
    "$task_root/scripts/RuntimeRegistrySmoke.java"

task_class_hash_before=$(shasum -a 256 "$task_class_jar" | awk '{ print $1 }')
task_activity_hash_before=$(shasum -a 256 "$task_activity_jar" | awk '{ print $1 }')
task_dex_hash_before=$(shasum -a 256 "$task_dex_jar" | awk '{ print $1 }')
"$task_root/gradlew" -p "$task_root" \
    :fixtures:producer:x2cReleaseJar \
    :fixtures:producer:x2cReleaseDexJar \
    --rerun-tasks \
    --offline \
    --no-configuration-cache
task_class_hash_after=$(shasum -a 256 "$task_class_jar" | awk '{ print $1 }')
task_activity_hash_after=$(shasum -a 256 "$task_activity_jar" | awk '{ print $1 }')
task_dex_hash_after=$(shasum -a 256 "$task_dex_jar" | awk '{ print $1 }')
if [[ "$task_class_hash_before" != "$task_class_hash_after" \
    || "$task_activity_hash_before" != "$task_activity_hash_after" \
    || "$task_dex_hash_before" != "$task_dex_hash_after" ]]; then
    echo "JAR output is not deterministic across rebuilds" >&2
    exit 1
fi

"$task_aapt2" dump resources "$task_apk"
shasum -a 256 "$task_class_jar" "$task_activity_jar" "$task_dex_jar" \
    "$task_secondary_class_jar" "$task_secondary_activity_jar" \
    "$task_secondary_dex_jar" "$task_apk"
echo "X2C verification passed"
