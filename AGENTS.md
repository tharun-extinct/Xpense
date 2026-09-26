# AGENTS.md — Expense Tracker

Expense Tracker is an Android-only, fully offline expense tracker that parses bank/UPI SMS into categorized transactions. Package `dev.expensetracker.app`.

## Documentation routing (Blueprints)

This repo uses the Blueprints system. Do not restate its routing table here — it lives in one place:

1. Read [blueprints/README.md](blueprints/README.md) first.
2. Match your task by intent/synonyms to one row and load its primary blueprint.
3. Load only the `architecture.md` headings that blueprint links to.
4. Inspect the listed implementation and test paths before changing behavior.
5. Load an impact-check blueprint only when its stated trigger condition applies.
6. For any change to a shared contract (i.e., anything that would edit `architecture.md`), read the complete `architecture.md` and every manifest row linking the affected anchor, then review each of those blueprints.
7. After a local responsibility change, update the primary blueprint (status, evidence, gaps) and the manifest row if concepts/dependencies/paths changed. After a shared-contract change, update `architecture.md` first, then every linked blueprint.

## CI-only build law (non-negotiable)

**GitHub Actions is the only compile, test, and build authority. There is no local runtime.**

- Never run Gradle (or an emulator) locally to validate a change. Do not claim code "builds," "passes," "works," or is "ready" based on local execution or on reading the code — only a GitHub Actions run establishes that.
- There is no checked-in Gradle wrapper JAR; CI installs Gradle 9.5.0 directly via `gradle/actions/setup-gradle` and invokes `gradle` (not `./gradlew`). Keep workflow Gradle-version pins and `gradle/libs.versions.toml`'s `agp`/toolchain versions consistent with each other.
- Workflow: commit → push to a branch → trigger/observe the run with `gh run watch` or `gh run list` / `gh run view --log-failed` → cite the run URL/ID when reporting status.
- If a run fails, fix the root cause and push again; do not add local-only workarounds (e.g., suppressing a check) to make a local build "pass" when there is no local build to pass.
- `blueprints/build-and-ci.md` owns the exact workflow files and toolchain versions — read it before touching `.github/workflows/`, `gradle/libs.versions.toml`, or module `build.gradle.kts` files.

## Privacy invariant (non-negotiable)

- The app never declares the `INTERNET` permission and never makes a network call. Do not add a networking library, analytics SDK, crash reporter with network transport, or ad SDK.
- Raw SMS body text is never persisted or logged. Only the derived `content_hash` and parser-extracted fields reach Room. Never log message content, even at debug level.
- Full detail: [architecture.md § Privacy and permission invariant](architecture.md#privacy-and-permission-invariant).

## Core conventions

- Kotlin + Jetpack Compose + Room. No Java sources.
- Money is always `Long` minor units (paise). Never `Double`/`Float` for currency. See [architecture.md § Data representation](architecture.md#data-representation).
- Timestamps are `Long` UTC epoch millis; convert to device-local `ZoneId` only at the point of month/day bucketing.
- The parsing package (`ingestion`... `parsing`) has zero Android/Room/Compose imports so it stays plain-JVM-testable.
- All tests are JVM tests (plain unit tests or Robolectric). No `androidTest`/instrumented tests in the default pipeline — see [architecture.md § Verification boundaries](architecture.md#verification-boundaries).
- Follow the existing blueprint's "Local rules and implications" for the area you're touching instead of inventing new patterns.

## Branch and PR flow

- Work on a feature branch per responsibility (roughly one per blueprint or a coherent slice of one), not directly on `main`.
- Keep commits scoped to one blueprint's concern where practical; update that blueprint's status/evidence in the same change.
- Open a PR only after a green CI run on the branch; reference the run in the PR description.
- Do not merge with a red or missing CI run.


## Do not use the word 'NIKE' anywhere in the codebase or during file creation

## If you can't able to access the Github CLI, end the session and I will give you the logs of the CI (Actions)

## Don not poll and wait for the logs, end the session. I will provide you the logs.