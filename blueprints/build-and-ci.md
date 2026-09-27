# Build and CI

## Outcome or responsibility

Own the Gradle toolchain configuration and the GitHub Actions workflows that are the sole compile, test, and build authority for this repository — nothing is considered working, mergeable, or "done" without a green run, since there is no local runtime to fall back on.

## Current verified status

**Status:** Planned

No Gradle project files or workflow files exist yet at the time of writing. This blueprint records the intended toolchain and pipeline ahead of implementation; status will move to `Partial` after the first successful `assembleDebug` run and to `Implemented` once lint, unit tests, and the manifest-permission check are all green in the same workflow.

## Architecture dependencies

- [Verification boundaries](../architecture.md#verification-boundaries) — this blueprint implements exactly the layered verification described there: `./gradlew test` (JVM unit + Robolectric), `./gradlew lint`, and `./gradlew assembleDebug`, with no instrumented/emulator step in the default pipeline.
- [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant) — this blueprint owns the automated check that `AndroidManifest.xml` never declares `INTERNET`, so the privacy guarantee is enforced by CI, not just by convention.

## Local rules and implications

- **Toolchain pins** (`gradle/libs.versions.toml`): AGP 9.1.0, Kotlin 2.4.10, Gradle wrapper 9.5.0, JDK 17 (Temurin), Compose BOM 2026.08.00, Material 3 1.4.0, KSP 2.3.11, Room 2.8.5, `compileSdk 37`, `targetSdk 36`, `minSdk 26`. These are the versions to scaffold with; if a CI run reveals an incompatibility, the fix is a version-catalog edit plus a note here, not a silent local workaround, since there is no local build to "just try it" against.
- **Built-in Kotlin (AGP 9.0+):** the module build files do **not** apply `org.jetbrains.kotlin.android` — AGP 9.0+ rejects it in favor of built-in Kotlin support, discovered via a real CI failure ("The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0"). The `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` compiler-subplugin ids are still applied directly since they are independent of the removed `kotlin-android` plugin. `kotlinOptions { jvmTarget = "17" }` was likewise removed from `app/build.gradle.kts`; `kotlin.compilerOptions.jvmTarget` defaults to `android.compileOptions.targetCompatibility`, which is already `VERSION_17`.
- **Room 2.8.5 is a KSP2 floor, not a preference:** KSP 2.3.11 is KSP2-only, and `room-compiler` before 2.7.0 crashes under it with `IllegalStateException: unexpected jvm signature V` — KSP2 reports the JVM `void` descriptor for a `Unit`-returning `suspend` DAO method, which the old processor cannot parse ([google/ksp#2177](https://github.com/google/ksp/issues/2177)). The DAOs in [Local persistence](local-persistence.md) have several such methods (`AccountDao.upsert`, `BudgetDao.upsert`, `MerchantRuleDao.upsert`, `MerchantRuleDao.insertSeedRules`, `TransactionDao.update`), so this was a hard failure of `:app:kspDebugKotlin`, discovered via a real CI run. Downgrading to KSP1 is not an option on this KSP line, so the whole version-locked Room set (runtime, compiler, testing) moves together. `room-ktx` is dropped from the catalog and from `app/build.gradle.kts` because Room 2.7.0 merged its APIs into `room-runtime` and left the artifact blank.
- **Room schema export uses the Room Gradle plugin:** `androidx.room` owns `schemaDirectory("$projectDir/schemas")` instead of passing `room.schemaLocation` directly through the KSP DSL. The raw argument left the directory invisible to Gradle's task input/output tracking, so debug and release KSP work could race while rewriting the same schema after a database-package refactor; the observed failure was an empty JSON document read by `kspDebugKotlin` while `kspReleaseKotlin` continued. The plugin makes schema generation reproducible, cacheable, and variant-aware.
- **No checked-in wrapper:** there is no `gradlew`/`gradle-wrapper.jar` in this repository (a binary JAR cannot be authored as text). CI installs Gradle 9.5.0 directly via `gradle/actions/setup-gradle`'s `gradle-version` input and invokes `gradle` rather than `./gradlew`. This is fully valid under the CI-only build law since GitHub Actions remains the sole authority either way; a future contributor could add a real wrapper JAR without changing this contract.
- **`ci.yml` (push and pull_request):** a fast `privacy-check` job asserts the source manifest never contains `uses-permission android:name="android.permission.INTERNET"`; a parallel `build` job sets up JDK 17 (Temurin) and Gradle 9.5.0, runs `gradle --no-daemon assembleDebug assembleRelease`, and uploads the debug APK and the release mapping as workflow artifacts regardless of pass/fail; a `test` job runs `gradle --no-daemon testDebugUnitTest` and uploads the HTML reports. `lint` is still not wired up; that remains under Remaining gaps.
- **Robolectric must keep up with `targetSdk`, and it drags the test JVM with it:** Robolectric refuses to run a package whose `targetSdkVersion` exceeds the newest Android it ships, failing every test with `Package targetSdkVersion=36 > maxSdkVersion=35` before a single assertion runs. 4.14.1 capped at SDK 35 against this app's `targetSdk 36`, so the pin moved to 4.17 — which then requires Java 21 to build its SDK 36 sandbox (`Android SDK 36 requires Java 21`). The `test` job therefore sets up **JDK 21** while `build` stays on JDK 17; the emitted bytecode target is unchanged either way because it comes from `android.compileOptions`. On that JVM Robolectric additionally needs `--add-opens` for several `java.base` packages (it reaches into JDK internals to emulate Android's native layer, and otherwise fails with "Failed to interact with raw FileDescriptor internals"), so `app/build.gradle.kts` sets them under `testOptions.unitTests.all`. Raising `targetSdk` means checking the Robolectric version, the test JVM, and these opens in the same change.
- **`test` is a blocking gate, not a convenience:** it was restored alongside the `categories` schema change because `assembleDebug` cannot detect a broken Room migration — a migration compiles perfectly and still destroys the user's only copy of their financial history, with no backend to restore from ([Error and recovery](../architecture.md#error-and-recovery)). While this job was absent, every blueprint citing "CI green run executes tests" was citing something that did not happen, which is why several of them were moved to `Partial` rather than `Implemented`.
- **Release optimization is mostly a list of things not configured.** `release` sets `isMinifyEnabled = true` and `isShrinkResources = true`; that is the entire opt-in. R8 full mode (AGP 8.0+), `-allowaccessmodification` (AGP 8.2+ with `proguard-android-optimize.txt`), class repackaging into the unnamed package (AGP 9.1+), and optimized resource shrinking (AGP 9.0+, automatic whenever the resource shrinker is on) are all defaults at AGP 9.1.0. Restating them in `gradle.properties` is not harmless: `android.r8.optimizedResourceShrinking` is deprecated in AGP 9 and several sibling flags were removed outright, so the property that looks like it turns an optimization on is the one that generates a deprecation warning. The default ProGuard file must stay the `-optimize` variant — AGP 9 dropped support for `proguard-android.txt` because it carries `-dontoptimize`.
- **Every keep rule in `app/proguard-rules.pro` exists because something is looked up by name at runtime,** and the three that exist are each a data-loss or silent-failure risk rather than a crash: enum constants (`Converters.kt` persists `TransactionDirection`, `TransactionState` and `RuleSource` via `valueOf`/`name`, so a renamed constant makes rows written by a previous install unreadable), WorkManager's worker class names (stored in its own database at enqueue time and resolved after an app update, i.e. after a different R8 run), and Room's `"<database>_Impl"` string concatenation. Rules for kotlinx.serialization are deliberately absent and the file says why — both call sites pass an explicit `X.serializer()`, which R8 can trace statically.
- **`assembleRelease` runs on every push, not only on tags:** release is the only variant R8 touches, so a keep rule that strips something the app needs is invisible to `assembleDebug`, to the tests, and to lint. Building it in the `build` job means a broken shrinker configuration fails the commit that introduced it. `.github/scripts/optimization-report.sh` then parses R8's own `mapping.txt` and `usage.txt` into a job-summary table of shrinking, optimization, and obfuscation percentages, so the effect of a keep rule is visible one push after it is written instead of after a Play Console upload.
- **`mapping.txt` is an artifact of record, not a build leftover:** it is uploaded by `ci.yml` and attached to the GitHub Release by `release.yml` under a tag-stamped name. Every build regenerates it differently, so a stack trace from a published APK is permanently undecipherable without the exact mapping of the build that produced it.
- **`release.yml` (on tag push or manual dispatch):** `gradle --no-daemon assembleRelease` producing a release APK attached to a GitHub Release; no Play Store publishing step, consistent with the sideload-only distribution decision in [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant).
- **Release builds are debug-signed:** `release` sets `signingConfig = signingConfigs.getByName("debug")`. Android refuses to install an unsigned APK ("App not installed"), and sideloading is the only distribution channel, so an unsigned artifact is useless. The debug keystore is generated by AGP on the runner, so the signing identity is not stable across machines — an app signed by one run cannot be upgraded in place by another. Replacing this with a real keystore held in GitHub secrets is still open.
- **Docs check job:** a lightweight job (link and heading validation over `architecture.md` and `blueprints/`) runs alongside the build job so documentation drift is caught the same way code drift is.
- **No local build claims:** per the repository's `AGENTS.md`, no change is described as "working," "passing," or "built" without citing a specific GitHub Actions run URL/ID; this blueprint's acceptance criteria are only satisfiable by a real run, not by local `./gradlew` output.

## Related blueprints

### Required

None.

### Impact checks

- Every other blueprint — a change to the JVM/Robolectric test boundary, the toolchain versions, or the manifest-permission check affects what "green" means for all of them.

## Relevant implementation and tests

- `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` — root and app module Gradle configuration.
- `gradle/libs.versions.toml` — pinned version catalog.
- `.github/workflows/ci.yml` — `privacy-check`, `build` (assembleDebug and assembleRelease, with APK and mapping upload), `test`, and `docs-check` jobs.
- `.github/workflows/release.yml` — tag-triggered release build, signature-verified with `apksigner` before publishing, with the mapping attached to the release.
- `app/proguard-rules.pro` — the three runtime-name-lookup keep rules, and the reasoning for the rules that are deliberately not there.
- `.github/scripts/optimization-report.sh` — turns R8's `mapping.txt`/`usage.txt` into the shrinking, optimization and obfuscation figures in the job summary.
- `scripts/check-docs.mjs` — architecture/blueprint link, heading, and manifest-routing checks run by the `docs-check` job.

## Acceptance or verification criteria

- [ ] A pushed branch produces a green `ci.yml` run executing the privacy check, `assembleDebug`, `assembleRelease`, and `testDebugUnitTest`, with artifacts attached.
- [ ] The optimization report shows a non-zero shrinking percentage and an obfuscation percentage near 100%; a sudden drop in either means a keep rule is matching more than intended.
- [ ] A release APK installs, ingests an SMS, and reads back transactions written by the previous version — the three keep rules are for failures that only appear on a minified build after an upgrade.
- [ ] The manifest-permission check fails the run if `INTERNET` is ever added to the manifest.
- [ ] A pushed tag produces a `release.yml` run that attaches a release APK to a GitHub Release, and `apksigner verify` confirms it is signed.
- [ ] The docs-check job fails on a broken internal link or a missing required blueprint heading.
- [ ] No repository instruction or blueprint claims "Implemented" status without citing a specific green run.

## Remaining gaps and unknowns

- There is no checked-in Gradle wrapper JAR (see Local rules above); CI pins the Gradle version via the `setup-gradle` action input instead. A real wrapper could be added later for local-dev convenience without weakening the CI-only contract.
- Release builds are signed with the runner-generated debug key as a stopgap; a real keystore stored as a GitHub secret is needed before any distribution that requires stable, upgradable signing.
- `lint` is still not run by `ci.yml`. `test` has been restored as a blocking job; `lint` should follow once the codebase is known to be clean against it.
- No Room schema JSON is committed under `app/schemas/` even though the Room Gradle plugin configures schema export, so migration tests cannot use `MigrationTestHelper` and must build old schemas by hand. Committing the exported schemas would remove that constraint.
- **The keep rules are unverifiable by the current pipeline.** `assembleRelease` proves R8 completes and nothing fails to resolve at compile time; it cannot prove that a `valueOf` on an obfuscated enum still matches a string written to SQLite by an earlier install, because every test runs against an unminified debug build. Closing this properly needs an instrumented test on a minified variant, which the repository has deliberately never had. Until then the rules rest on reasoning, and the upgrade path in the acceptance criteria is a manual check.
- Whether to add a nightly scheduled run against the latest AGP/Compose BOM to catch upstream breakage early is undecided.
- Build cache/remote cache strategy for CI speed as the project grows has not been evaluated.
