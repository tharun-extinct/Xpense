# Merchant Categorization

## Outcome or responsibility

Assign a spend category to every parsed transaction using merchant-text matching and user-taught rules, decide whether a transaction is trustworthy enough to auto-confirm or must enter the needs-review queue, and learn from user corrections so the same merchant is categorized correctly next time without repeated manual work.

## Current verified status

**Status:** Partial

`MerchantCategorizer`, `MerchantTextNormalizer`, the seed-rule asset, and `ExpenseRepository.learnMerchantRule` exist with unit tests for rule precedence and the confidence-gated state decision. The needs-review queue is now reachable in the UI (the Home banner routes into a review screen), and correcting a transaction's category teaches a `USER` rule.

Gap: the pipeline did not run the `test` task while the app was being brought up (see [build-and-ci](build-and-ci.md)), and seed coverage is still 27 merchants, so most real transactions land in `UNKNOWN`.

## Architecture dependencies

- [Identity and ownership](../architecture.md#identity-and-ownership) — merchant rules are keyed by normalized merchant text (owned here) and are user-owned/locally mutable; a category rename applies forward only, never rewrites historical transaction rows, which this blueprint's update logic must respect.
- [State lifecycle](../architecture.md#state-lifecycle) — this blueprint is the decision point for `CONFIRMED` vs `NEEDS_REVIEW`: a transaction is only eligible for `CONFIRMED` if the parser's confidence floor is met *and* a known merchant rule (seeded or user-taught) resolves a category; otherwise it lands in `NEEDS_REVIEW` regardless of parser confidence.

## Local rules and implications

- **Seed rules:** a small built-in table of common merchant-text substrings to categories (e.g., "AMAZON" → Shopping, "SWIGGY"/"ZOMATO" → Food & Drinks, "NETFLIX" → Bills) ships as a JSON asset (`app/src/main/assets/merchant-rules/seed-rules.json`), matched case-insensitively against normalized merchant text after basic cleanup (strip transaction-reference suffixes, collapse whitespace).
- **User-taught rules:** stored in Room `merchant_rules` (normalized merchant key → category, `source` = `SEED` or `USER`), upserted whenever a user edits a transaction's category in the UI. `USER` rules always take precedence over `SEED` rules on lookup.
- **Needs-review queue:** exactly the Room query `SELECT * FROM transactions WHERE state = 'NEEDS_REVIEW'`, per [State lifecycle](../architecture.md#state-lifecycle) — this blueprint does not introduce a separate table for it, only the query and the UI affordance to resolve entries from it.
- **Learning is local and immediate:** a single user correction updates `merchant_rules` synchronously in the same transaction as the edit; there is no batching, no confirmation step, and no server round-trip (there is no server).
- **Category set:** a rule's category is a `categories` row id per [Data representation](../architecture.md#data-representation); this blueprint does not define categories, only maps merchants onto ids that already exist. A user-created category is therefore usable as a rule target the moment it is created, with no code change.
- **`UNKNOWN` is the visible default, not a silent one:** because a rule miss yields `UNKNOWN`, a fresh install concentrates most spend in that one id. The category breakdown surfaces it as an explicit "uncategorized" call to action routing into the review queue rather than presenting it as just another slice, so the state is legible and correctable instead of looking like a broken chart.
- **Only the category is ever taught:** the detail screen also lets the user correct a transaction's direction, and that correction is deliberately not generalized into a rule of any kind. A merchant key predicts a category; it does not predict which way money moved, since the same sender issues both a charge and its refund.
- **Teaching a rule is opt-in per correction:** the transaction detail screen applies a category change to that one transaction by default and upserts a `USER` merchant rule only when the user asks to apply it to the merchant. This keeps the forward-only guarantee in [Identity and ownership](../architecture.md#identity-and-ownership) from being surprising — a one-off correction never silently reclassifies future spend.

## Related blueprints

### Required

- [transaction-parsing](transaction-parsing.md) — categorization operates on the `merchant` text field and consumes the parser's confidence score as one input to the `CONFIRMED`/`NEEDS_REVIEW` decision; a change to how merchant text is extracted directly changes match rates here.

### Impact checks

- [local-persistence](local-persistence.md) — check this whenever the `merchant_rules` table schema, its unique key, or the `transactions.state`/`is_user_edited` columns change.

## Relevant implementation and tests

- `app/src/main/java/dev/expensetracker/app/categorization/MerchantCategorizer.kt` — planned: seed + user-rule lookup, `CONFIRMED`/`NEEDS_REVIEW` decision.
- `app/src/main/java/dev/expensetracker/app/categorization/MerchantTextNormalizer.kt` — planned: merchant-text cleanup used both for rule matching and rule-key storage.
- `app/src/main/assets/merchant-rules/seed-rules.json` — planned: built-in merchant-to-category table.
- `app/src/test/java/dev/expensetracker/app/categorization/MerchantCategorizerTest.kt` — planned: seed-rule matching, user-rule precedence, confidence-gated state decision.

## Acceptance or verification criteria

- [ ] A merchant matching a seed rule with sufficient parser confidence is auto-`CONFIRMED` with the correct category.
- [ ] A merchant with no matching rule, regardless of parser confidence, lands in `NEEDS_REVIEW` with category `Unknown`.
- [ ] Editing a `NEEDS_REVIEW` transaction's category confirms it and upserts a `USER` merchant rule that a subsequent identical-merchant transaction picks up automatically.
- [ ] A `USER` rule always wins over a `SEED` rule for the same normalized merchant key.
- [ ] Editing a category never mutates any other historical transaction's stored category.

## Remaining gaps and unknowns

- The seed-rule list is a small starting set; broad merchant coverage for the Indian market is `Unknown` until grown against real transaction samples.
- Fuzzy/substring matching may produce false positives (e.g., a merchant name that contains another known brand's substring); a real collision policy (longest-match-wins vs. explicit precedence list) is not yet decided.
- Category-merge/rename UX (what happens to existing `merchant_rules` pointing at a deleted custom category) is undesigned.
