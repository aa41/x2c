# Troubleshooting

Use evidence from `report.json`, `activities.json`, archive listings, generated-source task inputs,
merged Manifest, ClassLoader identities, and a focused logcat. Do not work around failures by
copying runtime classes or resources into the plugin payload.

## Build and IDE

### Generated source exists but JavaCompile cannot see it

Confirm the matching `x2cGenerate<Variant>` task ran, the canonical directory is
`build/generated/java/x2cGenerate<Variant>/`, and JavaCompile depends on/includes it. Re-sync the IDE
after a successful model build. Do not import generated classes from business source as a fix.

### `x2cGenerate` did not run

Check whether the module has a meaningful `x2c {}` configuration and `x2cEnable=true`. An absent or
empty block intentionally selects system resources in normal integration. Plugin mode requires
generation and must fail if disabled.

### Configuration-cache reports `Task.project` at execution time

Move project-derived paths/providers into annotated task properties during configuration. Task
actions must use those properties or injected filesystem services, not `getProject()`.

### Unsupported XML/styleable/qualifier/vector error

This is a product boundary, not a parser fallback request. Redesign into the supported generated
subset, move that screen/resource to normal AAR mode, or implement and test the missing compiler
feature. Never silently retain XML in a resource-free plugin.

### Component transform cannot close an Activity base chain

The entry needs `@X2cPluginActivity`. Same-payload base classes need no annotation. A base from a
compile-only business project must have `@X2cPluginBase`; the host must also contain that project.
Unmarked host-only `BaseActivity extends Activity` and current AndroidX/AppCompat bases cannot be
used as the plugin delegate root.

## Runtime and resources

### `IllegalArgumentException: The key must be an application-specific resource id`

Old plugin payloads generated `0x00xxxxxx` View IDs and cannot be used as keyed tags. Rebuild with a
version that emits `0x70xxxxxx`, increment the signed payload version, and activate it in a fresh
process. Do not substitute a host `R.id` because that breaks plugin isolation.

### `AbstractMethodError` on `ImageLoadListener`

Host and independently desugared plugin DEX may disagree about interface default methods. Rebuild
against the current listener ABI, implement every callback, or extend host-owned `ImageLoadAdapter`.
Increment payload version and test from a cold process.

### Resource returns host value when plugin declares the same name

Confirm business code obtained `X2cResources` from a plugin anchor class, the bootstrap came from
the same PluginClassLoader, and the generated provider declares that name. `X2C.resources(context)`
without an anchor is not evidence of plugin ownership.

### Host-only resource is not found

Only supported value/drawable types use plugin-to-host fallback, and lookup uses the host package.
Check the exact name/type, host APK resource table, shrinker removal, and `tools:keep`. Plugin XML
cannot reference an undeclared host-only name; fallback is an explicit business API path.

### `ClassNotFoundException`, `NoClassDefFoundError`, or `NoSuchMethodError`

Inspect whether the missing class should be plugin-private or host fallback. Plugin-private classes
must be in the producer/runtime closure (or explicit `@X2cPluginBase.include`). Host fallback classes
must exist in the installed host version, remain public/kept, and preserve method descriptors.
Never solve this by fat-jar packaging all host dependencies.

### Duplicate plugin/module identity

Every simultaneously installed plugin needs a unique stable `pluginId` and generated package. A
loaded ID cannot be hot-replaced. Increment the signed version for changed bytes and restart the
process. Duplicate generated module registration should remain a hard failure.

### Plugin Activity starts but behaves unlike a real Activity

The business object is a delegate attached to a real host container. Check the documented bridged
API, lifecycle `super` chain, constructor/field initializer access to Context, and any SDK that
requires exact Activity identity. Move identity-sensitive integration to a host adapter. Do not cast
the delegate/context into an unrelated host Activity.

### Process death loses navigation/state

Android may recreate a Manifest container after the process and plugin registries are gone. The host
must install the selected verified payload and component registry before restoring plugin routes,
persist only versioned/validated route state, and fall back safely when that payload is unavailable.
Treat this as a host startup protocol, not an Activity-only callback fix.

## Signed install

### Signature/digest/ABI/version rejection

Do not bypass it. Compare the canonical descriptor bytes, pinned public key, payload SHA-256,
component runtime ABI, dependency-closure digest, plugin ID, and monotonic version policy. Test keys
and fixture descriptors are not production material.

### Upgrade accepted on disk but old code still runs

Expected: ClassLoader/class identity cannot be safely replaced in-process. Mark the new version for
next process, finish or restart through a controlled host flow, then verify the newly selected
descriptor before loading.
