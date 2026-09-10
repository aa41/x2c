---
name: x2c-android-integration
description: Audit, integrate, migrate, or verify X2C Android libraries and hosts in resource-free plugin DEX-JAR mode or normal AAR mode. Use for X2C Gradle setup, component annotations, ClassLoader/Application wiring, resource/CDN policy, build validation, or integration failures.
---

# X2C Android Integration

Help developers understand and safely integrate this repository's X2C resource compiler and
Android component runtime. Treat the checked-out X2C version and its generated reports as the
source of truth; do not invent Maven coordinates, versions, supported Android APIs, or resource
semantics.

## Choose the operation

- For explanation or architecture review, stay read-only and read
  [references/capabilities.md](references/capabilities.md).
- For dynamic code delivered after the host APK, use plugin mode and read
  [references/plugin-mode.md](references/plugin-mode.md).
- For an ordinary Android library merged into an application, use normal mode and read
  [references/normal-mode.md](references/normal-mode.md).
- For signing, updates, hardening SDKs, custom Application/ClassLoader behavior, or production
  rollout, also read [references/security-and-operations.md](references/security-and-operations.md).
- For build/device checks or a failed integration, read
  [references/verification.md](references/verification.md) and then the relevant section of
  [references/troubleshooting.md](references/troubleshooting.md).

Read [references/integration-contract.md](references/integration-contract.md) before changing any
project. It defines the modules, artifacts, DSL, ownership, and runtime API shared by both modes.

## Required workflow

1. Establish scope. Identify the target repository, Android library module, host application
   module, variant, desired mode, X2C source/release version, and whether Android components or
   plugin bitmaps are required. Do not equate `pluginMode` with `x2cEnable`.
2. Run the read-only preflight before editing:

   ```bash
   python3 <skill-dir>/scripts/preflight.py \
     --project <project-root> --module :feature \
     --mode plugin --host-module :app
   ```

   Use `--format json` when structured output is useful. Report blockers with their evidence. A
   missing setting in a project not yet integrated is an implementation item, not permission to
   make unrelated changes.
3. Inspect existing Gradle conventions, version catalogs, source sets, Manifest ownership,
   Application setup, shrinker rules, signing/update code, and dirty worktree before proposing
   edits. Reuse the project's style. Never edit generated sources or copy fixture test keys.
4. Present the mode decision and material boundaries before implementation. In particular, state
   the artifact type, resource behavior, dependency ownership, component creation model, image
   path, and unsupported inputs that will fail the build.
5. Make the smallest coherent integration. Keep producer, host, and optional shared business-base
   responsibilities separate. Business code uses `X2C`/`X2cResources`; it must not import generated
   `R2`, `X2cModule`, `X2cLayouts`, or provider implementation classes.
6. Build through `x2cBuild<Variant>` with an explicit mode. Do not treat Gradle exit code alone as
   proof. Run the post-check against the actual artifact and reports:

   ```bash
   python3 <skill-dir>/scripts/postcheck.py \
     --project <project-root> --module :feature \
     --mode plugin --variant release
   ```

7. If a host exists, build the host and perform focused runtime checks. Plugin mode must cover
   signature rejection, cold-process install, class ownership, module registration, component
   routing, host fallback resources, and process-restart upgrade behavior. Normal mode must cover
   Manifest/resource merge, configuration/theme behavior, and native component creation.
8. Re-run preflight after changes. Summarize what changed, commands and observable results,
   remaining warnings, production gates, and exact rollback files. Do not commit, publish, upload
   images, install APKs, or modify a remote service unless the user requested that action.

## Fail-closed rules

- `pluginMode=true` requires `x2cEnable=true`, unique `pluginId` and `generatedPackage`, a
  class-only/DEX-only payload, and host-owned runtime modules. Never make plugin mode work by
  packaging `res/`, an AAR, X2C runtime classes, or a business-component Manifest into the payload.
- Plugin components are marked on plugin entry classes with `@X2cPluginActivity`,
  `@X2cPluginService`, `@X2cPluginReceiver`, or `@X2cPluginProvider`. Do not annotate host
  containers. Intermediate same-payload base classes need no entry annotation.
- A shared native `BaseActivity extends Activity` from a `compileOnly` business project must use
  `@X2cPluginBase` and is copied/transformed as a plugin-private class. An unmarked host-only native
  Activity base cannot be used as a delegate root.
- The host owns exactly one copy of `x2c-runtime`, `x2c-plugin-api`, `x2c-plugin-base`,
  `x2c-plugin-loader`, and `x2c-plugin-runtime`. Plugin producers compile against relevant runtime
  modules with `compileOnly`.
- The real host `Application` calls `X2C.init(...)` once after any hardening/proxy loader is stable
  and before plugin ClassLoader creation or X2C access. Generated modules, components, and resource
  calls must not repeat initialization.
- Plugin bitmap upload is an external CI step. The build only consumes an immutable HTTPS/SHA-256
  asset lock. Never read upload credentials or perform network uploads as part of Gradle codegen.
- Synthetic `R2.id` values are View IDs, not `resources.arsc` entries. They support View identity,
  keyed tags, and layout relations, but must not be passed to host `Resources` value APIs.
- Unsupported resources, unsafe bytecode, missing host dependencies, duplicate identities,
  capacity overflow, signature mismatch, downgrade, or same-version content replacement must
  remain explicit failures. Do not add reflection/XML fallback to hide them.
- The supported AGP range is 3.5.x through 8.x. Treat AGP 9+ as unsupported until the repository's
  compatibility suite says otherwise.

## Evidence standard

Separate these statements in the final result:

- configured: source/Gradle/Manifest state was inspected;
- built: a named task completed;
- structurally verified: report/archive invariants passed;
- device verified: a named path ran on a stated device/API;
- not verified: anything requiring unavailable credentials, server policy, device coverage, or a
  production hardening environment.

Never claim full Android compatibility from one emulator or one successful build.
