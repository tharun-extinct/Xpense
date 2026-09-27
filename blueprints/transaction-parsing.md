# Transaction Parsing

## Outcome or responsibility

Turn a normalized SMS body into a structured, partially-or-fully-populated transaction record — amount, direction (debit/credit), merchant text, account tail, reference number, running balance where present — with a confidence score that determines whether the result is trustworthy enough to auto-confirm.

## Current verified status

**Status:** Planned

No parser code or rule assets exist yet. This blueprint records the intended design ahead of implementation.

## Architecture dependencies

- [Data representation](../architecture.md#data-representation) — the parser must emit amounts as `Long` minor units (never `Double`) and direction as the `DEBIT`/`CREDIT` enum; this blueprint owns the amount/currency-string-to-minor-units conversion logic.
- [Error and recovery](../architecture.md#error-and-recovery) — the parser must never throw on malformed input; a message that doesn't match any rule template produces a low/zero-confidence result rather than an exception, and the caller (ingestion) decides `NEEDS_REVIEW` vs `IGNORED` based on that result.

## Local rules and implications

- **Rule assets:** parsing rules live as a JSON asset (`app/src/main/assets/parser-rules/*.json`), one file per issuer pattern family (e.g., generic UPI debit, generic UPI credit, card transaction, EMI/bill debit), each entry containing a regex template with named capture groups (`amount`, `merchant`, `accountTail`, `refNo`, `balance`) and a `messageType` (`DEBIT`/`CREDIT`/`IGNORE`, e.g. OTP/promo patterns are explicitly `IGNORE` rules so they're recognized, not just unmatched). Shipping rules as data (not hardcoded Kotlin) lets the corpus grow without touching parser engine code.
- **Confidence scoring:** confidence is a simple deterministic function of which required fields matched: amount + direction present is the floor for `NEEDS_REVIEW`; amount + direction + merchant + (accountTail or refNo) is the floor for auto-`CONFIRMED` eligibility (the final `CONFIRMED` decision also depends on `merchant-categorization` finding a known rule — see that blueprint). No machine learning; the scoring function must be simple enough to unit test exhaustively against the fixture corpus.
- **Fixture-driven corpus:** `app/src/test/resources/sms-fixtures/*.json` holds hand-written `(sender, body, expectedFields)` samples covering major Indian bank/UPI formats (HDFC, SBI, ICICI, generic UPI apps). Every new rule template added to the JSON assets must ship with at least one fixture; the test suite iterates the fixture corpus and asserts extracted fields exactly, so parsing accuracy claims are always tied to a concrete, growable fixture count rather than an unverified percentage.
- **Engine is pure Kotlin:** the parser package has no Android SDK dependency beyond loading the JSON asset bytes (handled by the caller passing in raw asset text), no Room dependency, and no Compose dependency — this is what makes it plain-JVM-unit-testable per [Verification boundaries](../architecture.md#verification-boundaries).
- **Currency assumption:** v1 assumes INR only (₹ symbol / "Rs" / "INR" tokens); multi-currency is out of scope and would be a local rule-set extension, not an architecture change, since amounts are already minor-unit integers.
- **Rule order is load order:** `TransactionParser` returns on the first directional rule that matches, and rules are compiled in the order `RuleAssets.PARSER_RULE_ASSET_PATHS` lists the files. Merchant-bearing rules must therefore be loaded before `fallback.json`, whose amount-only rules would otherwise shadow them and strip the merchant from every result.
- **Amount-only fallback rules:** `fallback.json` matches a currency-prefixed amount next to a direction verb (`debited`, `spent`, `credited`, …) with no merchant group. This resolves the "wholly novel format" gap below and satisfies [Error and recovery](../architecture.md#error-and-recovery)'s requirement that a plausibly-financial SMS is never dropped: the result carries `LOW` confidence, so it always lands in `NEEDS_REVIEW` for the user to label.
- **Ignore rules must not swallow transactions:** the promo and balance-enquiry `IGNORE` patterns are guarded by a negative lookahead for direction verbs, because real debit messages often append text like "Available balance is Rs 5000" and were being discarded whole.

## Related blueprints

### Required

None.

### Impact checks

- [merchant-categorization](merchant-categorization.md) — check this whenever the set of output fields (especially the raw `merchant` text contract) or the confidence-score thresholds change, since categorization keys off the merchant text and the `CONFIRMED`-eligibility decision is joint between the two.

## Relevant implementation and tests

- `app/src/main/java/dev/xpensetracker/app/parsing/TransactionParser.kt` — planned: loads rule templates, applies regex, computes confidence.
- `app/src/main/java/dev/xpensetracker/app/parsing/ParsedTransaction.kt` — planned: the plain data class output (amount minor units, direction, merchant text, accountTail, refNo, balance, confidence).
- `app/src/main/assets/parser-rules/` — planned: JSON rule templates by pattern family.
- `app/src/test/resources/sms-fixtures/` — planned: fixture corpus.
- `app/src/test/java/dev/xpensetracker/app/parsing/TransactionParserTest.kt` — planned: fixture-driven parameterized test asserting exact field extraction and confidence tier per fixture.

## Acceptance or verification criteria

- [ ] Every fixture in the corpus produces the exact expected fields and expected confidence tier.
- [ ] A message matching no rule template returns a defined zero/low-confidence result, never an exception.
- [ ] All extracted amounts are `Long` minor units; no `Double`/`Float` appears anywhere in the parsing package.
- [ ] The parser package compiles and is tested with zero Android framework imports (verified by a JVM-only test source set, not Robolectric).
- [ ] CI (`build-and-ci`) green run executes the full fixture-driven test suite on every push.

## Remaining gaps and unknowns

- The fixture corpus starts small (a handful of hand-written samples); real-world parsing accuracy across the long tail of Indian bank SMS formats is `Unknown` until the corpus grows from real (anonymized) samples.
- The rule set was written against documented issuer message shapes (HDFC "Sent Rs.X From A/C … To …", SBI "debited by X … trf to …", ICICI "debited for Rs X … ; … credited", card "… for Rs X at …") but has **not** been validated against a real device inbox; per-issuer coverage is `Unknown` until fixtures from real anonymized samples exist.
- Multi-currency and non-INR formats are explicitly deferred, not designed.
