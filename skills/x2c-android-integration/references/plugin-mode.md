# Plugin-mode integration

Use this mode only for Android-loadable code delivered outside the host APK. The delivery artifact
is a DEX JAR without Android resources, assets, JNI, or Manifest components.

## Producer module

1. Apply `com.android.library`, `dev.x2c.codegen`, and—when any Android component is included—
   `dev.x2c.activity-plugin`.
2. Configure `pluginMode=true`, `x2cEnable=true`, a globally unique stable `pluginId`, a unique
   `generatedPackage`, and `minApi`. Configure `assetLockFile` only when bitmaps exist and
   `customViewsFile` only when custom View contracts exist.
3. Use `compileOnly` for `x2c-runtime`, `x2c-plugin-runtime`, `x2c-plugin-base`, and the annotation
   API visible to source. The payload must not contain those host-owned classes.
4. Keep the plugin Manifest free of business Activity, Service, Receiver, and Provider declarations.
   The host runtime AAR contributes fixed non-exported containers.
5. Mark only concrete plugin component entries:

   ```java
   @X2cPluginActivity(launchMode = PluginLaunchMode.SINGLE_TOP)
   public final class DetailActivity extends Activity { }

   @X2cPluginService
   public final class SyncService extends Service { }

   @X2cPluginReceiver
   public final class RefreshReceiver extends BroadcastReceiver { }

   @X2cPluginProvider(authority = "com.example.feature.data")
   public final class FeatureProvider extends ContentProvider { }
   ```

   Entries must be public, concrete, and have a public no-argument constructor. Intermediate base
   classes in the same payload are transformed through the inheritance closure without an entry
   annotation.
6. A normal shared business base can live in a larger project:

   ```java
   @X2cPluginBase(include = {PluginPrivateHelper.class})
   public abstract class BusinessBaseActivity extends Activity { }
   ```

   The host uses `implementation(project(":business-base"))`; the producer uses `compileOnly`.
   Only the selected base chain, nest/inner classes, and explicit `include` closure are copied into
   the payload. Other compile-only references resolve from the host ClassLoader and therefore need
   stable public ABI plus host shrinker keep rules.
7. Replace Android resource access in plugin business code with `X2cResources`. Do not import
   Android `R`, generated `R2`, `X2cLayouts`, or `X2cModule`. The compiler rejects resource-table,
   styleable, and dynamic resource APIs in plugin bytecode.

## Resources and images

- Plugin-declared supported values/drawables are plugin-first. A supported value/drawable name not
  declared by the plugin may fall back to the host through explicit `X2cResources` lookup.
- `id` and `layout` remain plugin synthetic identities and never fall back to host IDs.
- Plugin XML/generated drawable references must resolve inside the plugin model. Host fallback is
  not an excuse for undeclared XML references.
- Plugin bitmaps become HTTPS metadata. Upload outside Gradle, content-address by SHA-256, and commit
  the immutable `x2c-assets.lock.json`. The host installs one verified image loader. A host-only
  local drawable may be requested by name, but a plugin bitmap of the same name wins.
- Dynamically named host fallback resources need a static host reference or Android `tools:keep`
  when resource shrinking is enabled.

## Host application

1. Ensure the resolved application graph contains exactly one copy of all five host modules:
   runtime, plugin API, plugin base, loader, and plugin runtime. Direct `implementation`
   declarations are easiest to audit; plugin API may also arrive transitively through plugin
   base/runtime. The host may implement shared business APIs used through ClassLoader fallback.
2. Initialize `X2C` once in the real `Application`, after hardening/proxy ClassLoader setup is
   stable. Install the image loader before plugin UI requests images.
3. Verify the signed descriptor and payload with `SignedPluginInstaller` before creating the
   `PluginClassLoader`. Production private keys belong in an external publisher/CI signer; the APK
   pins public keys only.
4. Use one independent `PluginClassLoader` per physical payload. Load a known plugin entry, install
   its generated component registry with `PluginComponentManager.loadAndInstall`, then register
   resources using `X2C.loadModule(applicationContext, entryClass)`.
5. Launch through `PluginActivityManager`. Same-plugin explicit component Intents are routed by the
   delegate; cross-plugin launches must name the target `pluginId` explicitly.

The minimal verified installation order is:

```java
PluginDescriptor descriptor = PluginDescriptor.parse(descriptorInput);
PluginPackage installed = SignedPluginInstaller.install(
        application, descriptor, dexJarInput, signatureBytes, pinnedPublisherPublicKey);
PluginClassLoader loader = SignedPluginInstaller.createClassLoader(application, installed);

Class<?> entryClass = loader.loadClass("com.example.feature.PluginEntry");
PluginComponentRegistry registry =
        PluginComponentManager.loadAndInstall(application, loader);
if (!descriptor.pluginId.equals(registry.pluginId())) {
    throw new SecurityException("Descriptor/registry pluginId mismatch");
}
X2cResources resources = X2C.loadModule(application, entryClass);

PluginActivityManager.startActivity(
        application,
        descriptor.pluginId,
        "com.example.feature.HomeActivity");
```

Own and close the three delivery streams at the host boundary. The fixed entry class name and
expected component set belong to the signed server catalog/host compatibility contract; loading an
arbitrary class name from untrusted metadata is not a safe discovery mechanism.

## Capacity and lifecycle boundaries

- Activity containers: 8 slots for each of four launch modes, process-wide. `standard` can create
  multiple instances per assigned slot; distinct targets still consume assignment capacity.
- Service containers: 8 process-wide.
- Receivers are explicit/non-exported routes. Ordered state and `goAsync()` are bridged.
- Providers use virtual authorities and a supported ContentResolver subset. Unsupported caller
  identity and ProviderClient APIs fail the build.
- Hot unload/replacement is not supported. Download and verify an upgrade, mark it pending, and
  activate it in a new application process.
- AndroidX/AppCompat/FragmentActivity/Material inheritance is currently outside the phase-one
  component transform and must fail closed.

## Required build result

- `report.json` mode is `PLUGIN_SYNTHETIC_IDS`.
- `activities.json` contains the expected registry, ABI, dependencies, and every annotated
  component.
- `codegen.jar` and `activity-plugin.jar` contain classes only.
- `codegen-dex.jar` contains a valid `classes.dex` and no runtime or resource payload.
- A cold host process verifies, installs, loads, registers, and launches the plugin. Upgrade and
  rejection tests run in controlled fresh processes.
