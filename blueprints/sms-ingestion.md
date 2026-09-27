# SMS Ingestion

## Outcome or responsibility

Capture every transaction-bearing SMS the device receives (live, via broadcast) or already holds (historical, via a one-time/on-demand backfill), filter out non-bank senders, and hand each message to the parser exactly once, even if the same message is seen by both the live receiver and a backfill pass.

## Current verified status

**Status:** Planned

No code exists yet under `app/src/main/java/dev/xpensetracker/app/ingestion/`. This blueprint records the intended design ahead of implementation.

## Architecture dependencies

- [Identity and ownership](../architecture.md#identity-and-ownership) — this blueprint owns computing `contentHash` (sender + normalized body + timestamp truncated to the minute) and enforcing the uniqueness constraint that makes dedupe correct.
- [Error and recovery](../architecture.md#error-and-recovery) — this blueprint owns the requirement that `SmsReceiver` and `SmsBackfillWorker` never throw across their Android entry points and never drop a plausibly-financial message silently.
- [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant) — this blueprint is the sole place that touches `READ_SMS`/`RECEIVE_SMS` content; it must not persist raw SMS bodies and must not introduce any networking.

## Local rules and implications

- **Sender allowlist:** a message is only considered for parsing if its sender matches a known bank/UPI/wallet sender pattern (e.g., alphanumeric sender IDs like `VM-HDFCBK`, `AX-SBIINB`, short-code patterns). The allowlist is a maintained list (see `transaction-parsing` for where issuer patterns live) and is intentionally conservative: unmatched senders are never parsed, never stored, and never logged with content.
- **Sender shape is up to three DLT segments:** headers carry an optional telco prefix and an optional trailing category letter, so `AD-HDFCBK-S` is as common as `VM-HDFCBK`. An earlier two-segment-only pattern rejected most real traffic before parsing. Pure-digit senders are always rejected, which is what keeps personal messages out.
- **The allowlist stays narrow on purpose:** widening it to any shortcode would admit merchant receipts (food delivery, cabs) that restate a spend the bank also reported, double-counting the same purchase. Recall is improved by adding issuer tokens, not by removing the check.
- **Live path:** `SmsReceiver` extends `BroadcastReceiver`, registered for `android.provider.Telephony.SMS_RECEIVED`, declared in the manifest (broadcast receivers for SMS must be manifest-declared, not only runtime-registered, to reliably fire when the app is not foregrounded). It must complete quickly (Android broadcast receiver time limits) and delegate heavy work (parsing, DB writes) to a coroutine-backed work item or `WorkManager` one-off job rather than blocking `onReceive`.
- **Backfill path:** `SmsBackfillWorker` (a `CoroutineWorker`) reads the device's SMS content provider (`content://sms/inbox`) on user-triggered demand from Settings (see `permissions-and-onboarding`) — never automatically on every app start, to respect the user's expectation of when a bulk scan happens. It applies the same allowlist and normalizer as the live path.
- **Dedupe boundary:** both paths route through the same `SmsNormalizer.normalize()` function before hashing, so `contentHash` is computed identically regardless of entry point. The Room insert for `sms_hashes` uses `OnConflictStrategy.IGNORE` keyed on the unique `content_hash` index; an existing hash means "already imported" and short-circuits before the parser runs, per [Identity and ownership](../architecture.md#identity-and-ownership).
- **Claim the hash after parsing, not before:** a hash is only written once the message is known to be a transaction. Writing it first marked messages the rules could not yet handle as permanently processed, so improving the rules could never recover them — the dedupe table silently became a blocklist of everything the parser had failed at. Messages that parse as `IGNORE` or `UNRECOGNIZED` are deliberately left unclaimed so a later rescan reconsiders them; the cost is re-parsing them on each scan, which is cheap and in-memory.
- **Backfill clears orphan hashes first:** `SmsBackfillWorker` deletes `sms_hashes` rows with no matching transaction before scanning, so installs carrying hashes from the old claim-before-parse ordering heal themselves on the next user-triggered scan rather than needing a reinstall.
- **No raw body persistence:** the normalized body string exists only in memory for the duration of one parse call; only the hash and the parser's extracted fields are ever written to Room, per the privacy invariant.

## Related blueprints

### Required

None.

### Impact checks

- [transaction-parsing](transaction-parsing.md) — check this whenever the shape of the normalized body string, the sender-allowlist matching, or the `contentHash` inputs change, since the parser consumes exactly what this blueprint hands it.

## Relevant implementation and tests

- `app/src/main/java/dev/xpensetracker/app/ingestion/SmsReceiver.kt` — planned: manifest-declared broadcast receiver for live SMS.
- `app/src/main/java/dev/xpensetracker/app/ingestion/SmsBackfillWorker.kt` — planned: `CoroutineWorker` for on-demand historical scan.
- `app/src/main/java/dev/xpensetracker/app/ingestion/SmsNormalizer.kt` — planned: sender allowlist check, body normalization, `contentHash` computation.
- `app/src/test/java/dev/xpensetracker/app/ingestion/SmsNormalizerTest.kt` — planned: hash stability and dedupe-key tests.
- `app/src/test/java/dev/xpensetracker/app/ingestion/SmsBackfillWorkerTest.kt` — planned: Robolectric `WorkManager` test verifying dedupe against a pre-seeded `sms_hashes` table.

## Acceptance or verification criteria

- [ ] A message from an allowlisted sender is normalized, hashed, and forwarded to the parser exactly once whether it arrives live or is found by backfill.
- [ ] A message from a non-allowlisted sender is never normalized, hashed, stored, or forwarded to the parser.
- [ ] Re-running backfill after live receipt of the same message produces zero duplicate `sms_hashes`/`transactions` rows.
- [ ] `SmsReceiver.onReceive` never blocks longer than the Android broadcast time limit and never throws uncaught.
- [ ] No test or code path logs SMS body content.
- [ ] CI (`build-and-ci`) green run covers all of the above via JVM/Robolectric tests.

## Remaining gaps and unknowns

- The exact sender-allowlist pattern set (which issuer sender ID formats to match) is not yet enumerated; it will start small and grow with the fixture corpus in `transaction-parsing`.
- Whether backfill should page through very large inboxes (memory/time bound) is unspecified; needs a concrete row-count/time budget once real device inbox sizes are known.
- User-facing progress/cancel UX for a long backfill is owned by `permissions-and-onboarding` / `design-system-and-navigation` and is not yet designed.
