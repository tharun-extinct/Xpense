# Permissions and Onboarding

## Outcome or responsibility

Explain to a first-run user why the app needs `READ_SMS`/`RECEIVE_SMS`, that it works fully offline with no account and no network access, request the permission, and route the user into an initial (optional) inbox backfill before landing on the Home shell.

## Current verified status

**Status:** Planned

No onboarding UI or permission-request code exists yet. This blueprint records the intended flow ahead of implementation.

## Architecture dependencies

- [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant) — this blueprint is the user-facing surface for that contract: onboarding copy must state plainly that the app cannot be distributed via Google Play for this use case, that no data leaves the device, and that no `INTERNET` permission is requested. Any change to that invariant (e.g., a future sync feature) requires rewriting this flow's copy, not just the manifest.

## Local rules and implications

- **Flow order:** welcome/explanation screen (why SMS access, offline guarantee, Play-Store-distribution caveat) → runtime `READ_SMS`/`RECEIVE_SMS` permission request → on grant, an optional "scan existing messages now" prompt that triggers `SmsBackfillWorker` (see `sms-ingestion`) with a visible progress state → land on Home.
- **Denial handling:** if the user denies the permission, the app does not crash or dead-end; it shows a minimal Home state explaining that transactions can't be tracked without SMS access and offers a way to re-request from Settings, since there is no manual-entry-only fallback designed for v1 (manual transaction entry is out of the agreed v1 scope; this is a named gap, not a silent omission).
- **No dark patterns:** the permission rationale is shown once, clearly, before the system dialog (per Android best practice for dangerous permissions), and is never repeated as a nag beyond what the OS itself does.
- **Location is not an onboarding permission:** `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION` are declared but never requested during first run, because nothing on the path to Home needs them. The request happens inline, the first time a user taps "Tag location" on a transaction, so the rationale is the screen they are already looking at. A denial disables that one control and is stated as such; it never blocks a route or repeats itself. Onboarding copy must not promise "no other permissions" — it promises no network, which remains true.
- **Backfill is opt-in and visible:** per [sms-ingestion](sms-ingestion.md), the backfill worker never runs silently on first launch without this explicit user-facing trigger, so the user always knows when a bulk scan of their inbox is happening.
- **Settings is the durable backfill trigger:** the onboarding prompt appears once, so the Settings "Scan inbox now" button is the only rescan path a returning user has. Its callback must be threaded from `MainActivity` through `ExpenseNavHost` to `SettingsScreen`; the default no-op parameter makes an unwired call site compile silently, so this wiring is worth checking whenever the nav graph changes. Tapping it when the permission was previously denied re-requests the permission instead of starting a worker that cannot read the inbox.

## Related blueprints

### Required

- [sms-ingestion](sms-ingestion.md) — onboarding is the only UI trigger for `SmsBackfillWorker` and the permission-grant checkpoint before the live `SmsReceiver` can do anything.

### Impact checks

None.

## Relevant implementation and tests

- `app/src/main/java/dev/expensetracker/app/ui/onboarding/OnboardingScreen.kt` — planned: welcome/explanation composable.
- `app/src/main/java/dev/expensetracker/app/ui/onboarding/PermissionRequestFlow.kt` — planned: runtime permission request handling.
- `app/src/main/java/dev/expensetracker/app/ui/onboarding/BackfillPromptScreen.kt` — planned: opt-in backfill trigger with progress.
- `app/src/test/java/dev/expensetracker/app/ui/onboarding/` — planned: Robolectric tests for permission-granted and permission-denied navigation paths.

## Acceptance or verification criteria

- [ ] A first-run user sees the explanation screen before any system permission dialog appears.
- [ ] On permission grant, the user can choose to run backfill or skip it, and either choice reaches Home.
- [ ] On permission denial, the app reaches a non-crashing, explanatory Home state rather than dead-ending.
- [ ] Onboarding copy explicitly states the no-network, offline-only guarantee and the Play Store distribution caveat.
- [ ] No location dialog appears anywhere on the first-run path, and denying location leaves every other feature working.
- [ ] CI (`build-and-ci`) green run executes both the grant and denial navigation-path tests.

## Remaining gaps and unknowns

- Manual transaction entry (for users who deny SMS access, or for cash transactions) is out of v1 scope per the agreed plan; this is the most significant usability gap for a denied-permission user and is intentionally deferred.
- Re-requesting a permanently-denied permission requires directing the user to system Settings; the exact UX for that hand-off is not yet designed.
- Whether backfill should have a time-range picker (e.g., "last 3 months" vs. "all") is undecided.
