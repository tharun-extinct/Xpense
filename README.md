# Expense Tracker

An Android-only, fully offline expense tracker that turns bank/UPI SMS into categorized
transactions. No account, no backend, no `INTERNET` permission — everything happens on-device.

## Start here

- [AGENTS.md](AGENTS.md) — how to work in this repo (documentation routing, the CI-only build law, privacy invariants, conventions).
- [architecture.md](architecture.md) — shared system contracts.
- [blueprints/README.md](blueprints/README.md) — the manifest that routes any task to the right blueprint.

## Building

**There is no local runtime.** GitHub Actions (`.github/workflows/ci.yml`) is the sole compile,
test, and build authority for this project — do not run `./gradlew` locally and treat the
result as authoritative. Push a branch and check the Actions run.

- `ci.yml` — manifest privacy check and `assembleDebug` on every push/PR.
- `release.yml` — builds a release APK on a version tag (`v*`) and attaches it to a GitHub Release. It is signed with the debug key so it can be sideloaded; there is no upload keystore yet.

## Why SMS, why offline

The app reads `READ_SMS`/`RECEIVE_SMS` to detect bank and UPI transactions automatically. Because
of that, it cannot be distributed via the Google Play Store and is intended for direct/sideload
installation from a release build. In exchange, it never requests network access and never sends
your data anywhere — see [architecture.md § Privacy and permission invariant](architecture.md#privacy-and-permission-invariant).

## UI reference

The `assets/` folder contains reference screenshots from an existing expense tracker used purely
as an aesthetic and UX reference (dark theme, spend ring, category breakdown). Expense Tracker's own
design system is documented in [blueprints/design-system-and-navigation.md](blueprints/design-system-and-navigation.md)
and is not a clone of the reference.
