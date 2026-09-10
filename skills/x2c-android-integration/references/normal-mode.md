# Normal-mode integration

Normal mode is a standard Android library path. The artifact is an AAR whose Manifest and resources
merge into the host; Android creates its components natively.

## Choose generation behavior

| Configuration | Layout/value path |
|---|---|
| `pluginMode=false`, `x2cEnable=true` | XML is compiled to generated Java; IDs resolve against the merged host resource table |
| `pluginMode=false`, `x2cEnable=false` | no generated X2C source; `X2C` uses native `Resources`/`LayoutInflater` by name |
| no meaningful `x2c {}` configuration | same system-resources behavior as disabled generation |

An empty block does not enable generation. If default codegen is desired, set `x2cEnable=true`
explicitly.

## Library module

1. Apply `com.android.library` and `dev.x2c.codegen` when X2C configuration/codegen is required.
   A dual-mode module may retain `dev.x2c.activity-plugin`; normal build tasks do not run component
   transformation.
2. Set `pluginMode=false`. Use `api` or `implementation` for `x2c-runtime`; do not make the host
   depend on a runtime available only as `compileOnly`.
3. Keep ordinary resources, local bitmaps, qualifiers, and the library Manifest. Declare Activity,
   Service, Receiver, and Provider normally. Android framework/AGP performs Manifest and resource
   merging.
4. Do not build or publish `x2c<Variant>Jar`/`x2c<Variant>DexJar`; normal resources cannot be detached
   from the final application resource table. Use `x2cBuild<Variant>` or `assemble<Variant>` and
   publish the AAR.
5. Business code may use the same stable facade:

   ```java
   X2C.setContentView(this, "activity_account");
   X2cResources resources = X2C.resources(this, AccountActivity.class);
   TextView title = resources.requireView(root, "title", TextView.class);
   ```

   With generation disabled, the Activity Context is retained for theme/configuration-aware native
   inflation, including normal `<merge>` behavior.

## R2 and resources

- Generated normal `R2` is a stateless compatibility facade such as `R2.id.title(context)`. It
  resolves and caches the final host ID; there is no `R2.init()`.
- Prefer `X2cResources.id/layout/requireView` over importing generated code.
- Runtime IDs are not Java compile-time constants and cannot be used in `switch case` labels or
  annotation parameters.
- Local bitmap drawables stay in the AAR and load synchronously through host `Resources`; they do
  not need a CDN asset lock or image-loader SPI.
- When generation is disabled, dynamic name lookup can be affected by resource shrinking. Keep
  names through static references or `tools:keep`.
- Android plural APIs select locale quantity from an integer. X2C's generated syntactic quantity
  enum is not a substitute when system mode is selected; call native `Resources` for that case.

## Dual distribution warning

A boolean does not make one artifact safely interchangeable between plugin and normal delivery.
The dependency scopes, Manifest, image policy, component creation model, and artifact type also
change. Prefer two publishing modules sharing business source. If one module must switch, use the
same strict Gradle Provider to control mode, dependency scopes, Manifest source, and image/resource
inputs together, then verify both outputs independently.

## Required build result

- `report.json` mode is `HOST_RESOURCE_IDS` when codegen is enabled or `SYSTEM_RESOURCES` when it is
  disabled.
- The AAR contains `AndroidManifest.xml`, `classes.jar`, and all expected resource entries.
- The merged host manifest contains the intended components.
- Theme/configuration/locales/local drawables behave through normal Android APIs.
- No dynamic plugin installer or component registry is needed for this library.
