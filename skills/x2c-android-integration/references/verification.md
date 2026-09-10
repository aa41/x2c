# Verification and recovery

## Build sequence

Use the target repository's wrapper and an explicit mode:

```bash
./gradlew :feature:x2cBuildRelease \
  -Px2c.pluginMode=true -Px2c.enable=true \
  --configuration-cache
```

For normal codegen or system resources:

```bash
./gradlew :feature:x2cBuildRelease -Px2c.pluginMode=false -Px2c.enable=true
./gradlew :feature:x2cBuildRelease -Px2c.pluginMode=false -Px2c.enable=false
```

Do not run `clean` by default. Mode and enablement are task inputs and switching should clear stale
generated source itself. Use `clean` once only when diagnosing a proven IDE/third-party cache issue.

Run the skill post-check after every selected artifact:

```bash
python3 <skill-dir>/scripts/postcheck.py \
  --project . --module :feature --mode plugin --variant release
```

The script checks the report mode, archive type, DEX magic, forbidden resource/runtime entries, and
component report when present. It does not replace runtime tests.

## Repository-level checks

When working inside the X2C source repository, use its maintained suites rather than recreating
them:

```bash
./scripts/verify.sh
./scripts/verify-plugin-modes.sh
./gradlew verifyX2c --offline --configuration-cache
X2C_OFFLINE=true ./scripts/verify-agp-compatibility.sh
```

The compatibility matrix is expensive; use it for plugin releases or compatibility-sensitive
changes, not every consumer edit.

## Plugin host checks

At minimum verify on a cold process:

- invalid signature, payload digest, ABI, downgrade, and same-version replacement are rejected
  before ClassLoader creation;
- runtime classes resolve from the host loader while plugin entries resolve from their own loader;
- `X2C.hostApplication()` is the real Application and initialization ran once;
- two plugin IDs/generated packages can install sequentially without module/resource collision;
- plugin-declared resources win, host-only supported resources fall back, synthetic IDs work with
  `findViewById` and keyed tags, and CDN images pass integrity checks;
- every annotated Activity launch mode, result/new-intent path, started/bound Service, ordered/
  asynchronous Receiver, and supported Provider path works when used;
- plugin-to-host, same-plugin, and cross-plugin navigation follows the explicit routing contract;
- a pending upgrade activates only after process restart and failed activation returns to a
  last-known-good payload.

Capture device serial/model/API, build variant, payload digest, plugin/version/ABI, exact navigation
path, and relevant crash/log excerpts. Avoid sending input to an unspecified connected device.

## Normal host checks

- inspect the AAR and merged host Manifest/resources;
- exercise native inflate with Activity theme, configuration changes, locale/night/density variants,
  local drawable loading, and any `<merge>` layout when generation is disabled;
- verify resource shrinking preserves dynamically named layouts/values/drawables;
- verify process recreation and framework component state through ordinary Android mechanisms.

## Recovery while integrating

Before edits, record the original files and repository status. Keep changes scoped so rollback is a
normal VCS revert, not a destructive checkout. If a generated source fails, fix the source XML/DSL/
contract and regenerate; never patch `build/generated`.

Stop and report rather than guessing when any of these are unknown:

- X2C artifact/plugin version or repository source location;
- owner of the real Application initialization point;
- production signing/public-key rotation policy;
- stable plugin IDs/version source/server compatibility rules;
- whether an unsupported resource/component behavior may be redesigned;
- whether a compile-only host dependency is actually present and kept in every supported host.
