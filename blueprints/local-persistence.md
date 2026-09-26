# Local Persistence

## Outcome or responsibility

Own the on-device Room database that is the single source of truth for transactions, accounts, merchant rules, budgets, and the SMS dedupe index, including its schema, DAOs, and migration policy, since there is no backend to fall back on.

## Current verified status

**Status:** Partial

The v1 entities, DAOs, `AppDatabase`, and `ExpenseRepository` exist and are exercised by Robolectric DAO tests (`TransactionDaoTest`, `SmsHashDaoTest`, `TransactionDirectionTest`). The `categories` table and `Migration1To2` are added here.

Gap: `ci.yml` did not run the `test` task while the app was being brought up, so DAO and migration tests were written but not executed by the pipeline. Restoring that step is tracked in [build-and-ci](build-and-ci.md) and is a prerequisite for moving this blueprint to `Implemented`.

## Architecture dependencies

- [Identity and ownership](../architecture.md#identity-and-ownership) — this blueprint implements the `content_hash` unique index (`sms_hashes`), the `(issuer, accountTail)` account identity tuple, and the forward-only category-edit rule at the storage layer (an edit is an `UPDATE` on the single row, never a bulk rewrite).
- [State lifecycle](../architecture.md#state-lifecycle) — this blueprint implements the `transactions.state` enum column (`CONFIRMED`/`NEEDS_REVIEW`/`IGNORED`), the `is_user_edited` flag, and the `budgets` table scoped to `(month, category)`.
- [Data representation](../architecture.md#data-representation) — this blueprint stores amounts as `INTEGER` (`Long`, minor units), timestamps as `INTEGER` (`Long`, UTC epoch millis), and direction/category/state as `TEXT` enum-backed columns with Kotlin enum converters.
- [Error and recovery](../architecture.md#error-and-recovery) — this blueprint owns the `Migration` objects; `fallbackToDestructiveMigration` is forbidden from the first shipped version onward, and any pre-ship schema churn using it must be recorded here.

## Local rules and implications

- **Schema (v1 tables):** `transactions` (id, content_hash FK, amount_minor, direction, merchant_raw, merchant_key, category, account_issuer, account_tail, ref_no, balance_minor nullable, occurred_at_utc_millis, state, is_user_edited, created_at); `sms_hashes` (content_hash unique, processed_at); `accounts` (issuer, account_tail, last_seen_balance_minor nullable, composite key `(issuer, account_tail)`); `merchant_rules` (merchant_key unique, category, source); `budgets` (month TEXT `YYYY-MM`, category, limit_minor, composite key `(month, category)`).
- **Schema (v2 addition):** `categories` (`id` TEXT primary key, `name`, `colorArgb` INTEGER, `iconKey`, `isBuiltIn`, `sortOrder`, `isArchived`), implementing [Data representation](../architecture.md#data-representation)'s rule that the category set is data rather than a Kotlin enum. The fourteen built-in rows are seeded with ids equal to the former `SpendCategory` constant names, which is what makes `Migration1To2` purely additive: `transactions.category`, `budgets.category`, and `merchant_rules.category` were already `TEXT` holding those exact strings, so **no existing row is rewritten**. `transactions.category` also gains an index, since the category-breakdown aggregate groups on it.
- **Schema (v3 addition):** `transactions` gains `note` TEXT, `locationLat` REAL, and `locationLng` REAL, all nullable. `Migration2To3` is three `ALTER TABLE ... ADD COLUMN` statements and nothing else — the shape a migration should have when the database is the user's only copy of their history. The two coordinate columns are written and cleared as a pair by `ExpenseRepository.setTransactionLocation`; SQLite cannot express "both or neither", so that invariant lives in the repository, in the same way and for the same reason as the missing category foreign key below.
- **No SQL foreign key on `category` columns:** a declared FK forces a cascade or restrict policy at the SQLite level, and cascade-delete would silently erase transactions when a category is removed. Integrity is enforced in `ExpenseRepository.deleteCategory`, which refuses to delete while any transaction, budget, or merchant rule still references the id, matching the archive-not-delete policy in [Data representation](../architecture.md#data-representation).
- **Migrations:** every schema change after the first CI-verified `assembleDebug` ships a `Migration(from, to)` with its own Robolectric test that runs the migration against a pre-populated old-schema database and asserts data survives; no destructive fallback is used post-ship, per the architecture error-and-recovery contract.
- **Migration tests build the old schema by hand:** `room.schemaLocation` is configured but no exported schema JSON is committed, so `MigrationTestHelper` has nothing to open an old database from. `Migration1To2Test` and `Migration2To3Test` therefore create the old tables with raw SQL through Room's `SupportSQLiteOpenHelper`, insert rows, run the migration, and reopen through Room to assert survival. Committing the schema JSON later would allow the standard helper instead. Each such test must register **every** migration up to the current version, not only the one under test: opening the current `AppDatabase` on an old file runs the whole chain, and a missing link fails the open rather than the assertion, which reads as an unrelated error.
- **DAO shape:** all read queries return Kotlin `Flow` so Compose screens observe changes reactively (no manual refresh calls); all writes are `suspend` functions run off the main thread via the repository layer.
- **Testing substrate:** DAO and migration tests run under Robolectric's in-memory Room builder (`Room.inMemoryDatabaseBuilder`), which is a JVM test, per [Verification boundaries](../architecture.md#verification-boundaries) — no emulator required.
- **No export/import in v1:** per the agreed plan scope (fully offline, no account, no network, no explicit export chosen), this blueprint does not implement backup/export; it is recorded as a gap below rather than silently designed in.

## Related blueprints

### Required

None.

### Impact checks

- [spend-analytics-and-budgets](spend-analytics-and-budgets.md) — check this whenever a table column used in an aggregation query (amount, direction, category, occurred_at) changes shape or meaning.
- [merchant-categorization](merchant-categorization.md) — check this whenever `merchant_rules` or `transactions.state`/`category`/`is_user_edited` columns change.

## Relevant implementation and tests

- `app/src/main/java/dev/expensetracker/app/data/AppDatabase.kt` — Room database class, version 2, migrations list.
- `app/src/main/java/dev/expensetracker/app/data/entity/` — `TransactionEntity`, `SmsHashEntity`, `AccountEntity`, `MerchantRuleEntity`, `BudgetEntity`, `CategoryEntity`.
- `app/src/main/java/dev/expensetracker/app/data/entity/BuiltInCategories.kt` — the fourteen seed rows, shared by the migration and the first-run seeder.
- `app/src/main/java/dev/expensetracker/app/data/migration/Migration1To2.kt` — creates and seeds `categories`, indexes `transactions.category`.
- `app/src/main/java/dev/expensetracker/app/data/dao/` — one DAO per entity, `Flow`-returning queries.
- `app/src/test/java/dev/xpensetracker/app/data/` — Robolectric DAO tests (`TransactionDaoTest`, `SmsHashDaoTest`) plus `CategoryRepositoryTest`, which covers seed idempotency and the delete guard that stands in for the missing foreign key.
- `app/src/test/java/dev/xpensetracker/app/data/migration/Migration1To2Test.kt` — builds the v1 schema with raw SQL (no exported schema JSON is committed, so `MigrationTestHelper` is unavailable), populates it, and asserts every row survives the upgrade and resolves against a seeded category.

## Acceptance or verification criteria

- [ ] Inserting the same `content_hash` twice results in exactly one `sms_hashes` row and no duplicate `transactions` row.
- [ ] A category edit updates exactly one `transactions` row and never touches sibling rows sharing the same merchant.
- [ ] `Migration1To2` preserves every pre-existing `transactions`, `budgets`, and `merchant_rules` row, and every stored category string resolves to a seeded `categories` row afterwards.
- [ ] Deleting a category that is still referenced by a transaction, budget, or merchant rule is refused; archiving it succeeds and leaves historical rows resolvable.
- [ ] Every schema version bump ships a tested `Migration`; `fallbackToDestructiveMigration` does not appear in the shipped database builder configuration.
- [ ] All DAO read methods return `Flow` and are exercised by a Robolectric test using an in-memory database.
- [ ] CI (`build-and-ci`) green run executes all DAO and migration tests on every push.

## Remaining gaps and unknowns

- Local export/import (CSV/JSON) is out of scope for v1 by explicit decision; if the user's only device is lost, transaction history is unrecoverable. This is a known, accepted trade-off of the "fully offline, no backend" scope, not an oversight, but should be revisited before broad use.
- Multi-account collision when two different physical accounts share `(issuer, accountTail)` is accepted as a known limitation, not resolved.
- Indexing/performance tuning for large transaction histories (multi-year SMS backfills) has not been evaluated.
