# Security and operations

Read this reference for production delivery, signing, hardening SDKs, shared host APIs, shrinkers,
or updates. The fixture's embedded private key is test-only and must never be copied.

## Trust boundary

A dynamically delivered DEX executes with the host application's UID and permissions. Encryption
protects confidentiality only while the file is stored or transported; decryption necessarily
exposes executable bytes to the app process. It does not establish trust by itself.

Before any ClassLoader sees the payload:

1. download over an authenticated transport into app-private staging storage;
2. parse a canonical descriptor with strict size/format limits;
3. verify the pinned publisher signature;
4. verify plugin ID, version code/name, runtime ABI, dependency-closure digest, and payload SHA-256;
5. reject downgrade and same-version content replacement;
6. atomically place the verified file in a private location and make it read-only;
7. create the ClassLoader only after every check succeeds.

Keep production private keys in an external signer/HSM or tightly scoped CI secret. The APK contains
only current/next public keys and an explicit rotation policy. Server catalog controls are defense
in depth, not a replacement for local cryptographic verification.

## ClassLoader ownership

`PluginClassLoader` is plugin-first for plugin/business/third-party classes and falls back to the
host loader on misses. Android/Java and all X2C runtime/API/base/loader packages are forced
parent-first. This preserves one runtime registry and one set of component protocol types.

Consequences:

- a plugin can call public host classes visible to the parent; there is no mandatory host API
  allowlist in the ClassLoader;
- the host still needs a deliberately stable facade for long-lived server-delivered plugins;
- if the same business FQCN is packaged in the plugin, the plugin copy wins and has separate class
  identity/static state;
- public method descriptors crossing loaders must use types owned consistently by the parent or a
  documented bridge;
- missing/changed host dependencies surface as normal linkage errors and should be prevented by
  catalog compatibility gates and ABI tests.

Host shrinking cannot see future plugin references. Keep public host facades, reflected generated
bootstrap/registry names, runtime containers, and any compile-only host dependencies required by
the plugin. Prefer narrow stable facades even if the host is temporarily kept broadly during early
development.

## Application and hardening SDKs

Plugin Contexts intentionally return the real host `Application` from `getApplicationContext()`.
That is appropriate for process-wide SDKs and singleton ownership, but it does not turn a delegate
into the original framework Activity object.

For shell/proxy Application or a hardening SDK:

- wait until the real Application and stable host definition loader exist;
- call `X2C.init(realApplication, stableHostLoader)` once;
- initialize plugin infrastructure from an explicit host hook or a carefully ordered provider only
  if the provider runs after the loader transition;
- never infer plugin identity solely from `Class.getClassLoader() != application.getClassLoader()`;
  use the runtime's registered ownership/anchor APIs;
- test cold start, multidex, backup/restore, process restart, and the exact protected release build.

Third-party SDKs that cast `getApplicationContext()`, require a real Activity identity, install
`ActivityLifecycleCallbacks`, inspect the Manifest/package resources, use native code, or perform
class-name checks may need a host-side adapter. Initialization timing alone does not solve those
identity differences.

## Updates and rollback

The current component runtime deliberately rejects hot unload and in-process replacement. Framework
tokens, bound services, pending broadcasts, and provider calls make partial teardown unprovable.

Use an A/B payload store:

- verify into an inactive slot;
- record the last-known-good descriptor and activation state;
- activate only on a controlled fresh process;
- mark startup/health success after the plugin reaches a meaningful checkpoint;
- on crash-loop or health timeout, start another fresh process with the last-known-good slot;
- retain enough signed metadata for forensic and downgrade-policy decisions.

Do not delete the only known-good artifact during installation. Never “fix” a failed upgrade by
lowering the local version check or accepting the same version code with new bytes.

## Store and policy considerations

Server-delivered executable code can conflict with Google Play policy, review expectations, and
security-product behavior. Before production, obtain policy/legal review for the distribution
channel and purpose, document the allowed plugin capability surface, and maintain emergency server
disable plus host-version/API/ABI targeting. A technically valid ClassLoader design is not store
approval.
