# Integration contract

## Ownership and artifacts

| Module/artifact | Owner | Purpose |
|---|---|---|
| `dev.x2c.codegen` | build | XML/resources to generated Java and deterministic JAR tasks |
| `dev.x2c.activity-plugin` | build | component superclass/callsite transform and direct registry |
| `x2c-runtime` | host | `X2C`, resources registry, plugin-first provider, image SPI, ClassLoader |
| `x2c-plugin-api` | host; producer compile-only | annotations, launch modes, registry contract |
| `x2c-plugin-base` | host; producer compile-only | stable host-owned component delegate roots |
| `x2c-plugin-loader` | host | signed descriptor verification, anti-downgrade, private payload install |
| `x2c-plugin-runtime` | host | Activity/Service/Receiver/Provider containers and routing |
| `codegen.jar` | build output | ordinary JVM classes; static audit/D8 input, not directly executable by ART |
| `activity-plugin.jar` | build output | transformed component classes plus generated registry |
| `codegen-dex.jar` | plugin delivery | Android-loadable archive containing `classes.dex` |
| AAR | normal delivery | standard Manifest/resources/classes library artifact |

Do not guess a published version. Prefer the target repository's version catalog or an explicitly
provided release coordinate. When integrating this source checkout, make the Gradle plugins
available through `pluginManagement { includeBuild("<x2c>/x2c-gradle-plugin") }` and depend on the
runtime projects through the project's chosen composite-build/module arrangement.

## Shared DSL

The component and resource settings are intentionally one block; there is no `x2cActivity {}`:

```kotlin
x2c {
    x2cEnable.set(true)
    pluginMode.set(true)
    pluginId.set("com.example.feature")
    generatedPackage.set("com.example.feature.generated")
    assetLockFile.set(layout.projectDirectory.file("x2c-assets.lock.json"))
    customViewsFile.set(layout.projectDirectory.file("x2c-custom-views.json"))
    minApi.set(21)
}
```

Important distinctions:

- `pluginMode`: selects plugin synthetic/resource-free semantics versus normal host-resource/AAR
  semantics.
- `x2cEnable`: selects XML-to-Java generation. Plugin mode cannot disable it. Normal mode may set it
  to false and keep native `Resources`/`LayoutInflater` behavior.
- `pluginId`: stable server/runtime identity, not an Activity name or generated module name.
- `generatedPackage`: private generated Java namespace. It must differ across simultaneously loaded
  plugins.
- `minApi`: code-generation API gate, not a replacement for Android `defaultConfig.minSdk`.
- `report.json.inferredHostResources`: exact host string/color/bool/integer/dimen/drawable names
  inferred from unresolved plugin layout references. Module initialization validates the complete
  set before publishing generated layouts. Normal mode never infers host dependencies.

Use a strict Gradle property provider for CLI/CI switching. An explicit `pluginMode.set(...)` value
overrides the plugin convention, so do not promise command-line switching while hardcoding another
value in the module.

Mode switching does not make plugin component APIs available to normal source automatically. Keep
business/UI logic in a mode-neutral module or `src/main`, and bind a small navigation/component
adapter from mutually exclusive plugin and normal source sets. Direct `PluginActivityManager` (or
other component-manager) imports belong only to the plugin adapter.

## Business API

Initialize once in the real host `Application`. Plugin hosts must also publish their immutable
Activity theme contract:

```java
@Override public void onCreate() {
    super.onCreate();
    X2C.init(
            this,
            PluginHostConfig.builder()
                    .defaultActivityTheme(R.style.HostPluginDefault)
                    .activityTheme("commerce.detail.light.v1", R.style.HostPluginCommerceDetail)
                    .build());
}
```

If a hardening SDK installs a stable host ClassLoader later, use the explicit overload only after
that transition is complete. Normal AAR-only hosts use `X2C.init(...)` instead. Initialization must
precede plugin loader creation and all resource access.

Activity theme ownership is deliberately different by mode:

| Mode | Theme source | Ignored source |
|---|---|---|
| normal AAR | merged Manifest Activity `android:theme`; otherwise application theme | `@X2cPluginActivity.themeKey` |
| dynamic plugin | annotation `themeKey` resolved through host `PluginHostConfig` | plugin Manifest Activity/theme declarations |

This remains true when one host APK contains both kinds of feature. Such an Application may need
the `PluginHostConfig` overload for its dynamic plugins, but native normal Activities still receive
their themes exclusively from Android's merged Manifest.

Business code identifies its module by an anchor class and layout/resource name:

```java
X2cResources resources = X2C.resources(MyActivity.class);
resources.setContentView(this, "activity_account");
TextView title = resources.requireView(
        getWindow().getDecorView(), "title", TextView.class);
String message = resources.string("welcome", userName);
Drawable icon = resources.getDrawable(this, "host_only_icon");
```

`X2C.setContentView(activity, "layout_name")` is also supported. Generated packages are internal
implementation. There is no `R2.init()` and business code should not name an X2C module manually.

## Build entrypoints

Use the unified task so mode selection and artifact selection stay aligned:

```bash
./gradlew :feature:x2cBuildRelease -Px2c.pluginMode=true -Px2c.enable=true
./gradlew :feature:x2cBuildRelease -Px2c.pluginMode=false -Px2c.enable=true
```

Expected reports and artifacts:

```text
build/reports/x2c/<variant>/report.json
build/reports/x2c/<variant>/assets-candidates.json
build/reports/x2c/<variant>/activities.json        # component plugin
build/outputs/x2c/<variant>/codegen.jar             # plugin build stages
build/outputs/x2c/<variant>/activity-plugin.jar
build/outputs/x2c/<variant>/codegen-dex.jar
build/outputs/aar/<module>-<variant>.aar             # normal mode
```

The source checkout also provides `scripts/build-x2c-artifact.sh`, which validates mode, task,
report, and archive together. In another repository, use it only when that repository deliberately
vendors or wraps the script; do not assume it exists.
