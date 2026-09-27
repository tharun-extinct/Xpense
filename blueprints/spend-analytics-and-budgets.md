# Spend Analytics and Budgets

## Outcome or responsibility

Aggregate confirmed transactions into the month-scoped numbers and visuals the UI shows: total spends vs. income, the category-donut breakdown, per-category budget progress, and merchant/trend views, all computed reactively from Room.

## Current verified status

**Status:** Partial

`MonthRange`, `SpendSummaryRepository`, `CategoryBreakdownRepository`, `MerchantBreakdownRepository`, `TrendRepository`, and `BudgetProgressCalculator` exist, with unit tests for month-boundary math and budget progress. The month being aggregated is now user-selectable rather than pinned to the current month.

Gap: aggregation correctness against seeded Room data is not yet covered end to end, and the pipeline did not run the `test` task while the app was being brought up (see [build-and-ci](build-and-ci.md)).

## Architecture dependencies

- [Data representation](../architecture.md#data-representation) — aggregation sums `Long` minor units (never floating point) and buckets by device-local calendar month converted from stored UTC epoch millis; this is the one place month-boundary conversion happens, reused by every screen that shows "this month."
- [State lifecycle](../architecture.md#state-lifecycle) — aggregation reads every transaction the user has not marked `IGNORED`, so both `CONFIRMED` and `NEEDS_REVIEW` amounts count toward spends and income; budgets are read from the `(month, category)` scoped table.
  - This reverses the original `CONFIRMED`-only rule. That rule assumed most transactions would auto-confirm, but confirmation requires a merchant rule to already exist, so on a fresh install essentially everything is `NEEDS_REVIEW` and every total rendered as zero — indistinguishable from the app having imported nothing. Counting unverified amounts risks inflating totals when a low-confidence fallback rule misfires; the needs-review banner is what makes that visible and correctable, and a zero total was judged the worse failure.

## Local rules and implications

- **Spend ring:** total `DEBIT` sum for the selected month vs. total `CREDIT` sum for income comparison (mirroring the reference "Spends" / "Income (Aug)" pairing), computed via a single Room `Flow`-returning aggregate query, re-emitting whenever the underlying table changes.
- **Category donut:** per-category `DEBIT` sum for the selected month, percentage-of-total computed at the presentation layer from minor-unit longs (integer division avoided by computing percentages as `Double` only for display, never fed back into stored data).
- **Breakdown carries display metadata:** because a category's name and color are row data per [Data representation](../architecture.md#data-representation), `CategoryBreakdownRepository` joins the aggregate against `categories` and emits name, `colorArgb`, and `iconKey` alongside each sum. The UI never derives a label from an id. A sum whose id no longer resolves (possible only via manual database edits) degrades to the id string rather than being dropped, so money never silently disappears from a total.
- **Top-N rollup is presentation, not aggregation:** the repository emits every category. Collapsing the long tail into a synthetic "Other" slice so the ring stays readable happens in the donut composable, so the category list below it can still show the full breakdown and the two always sum to the same total.
- **Direction is an input these queries trust, and the user owns it:** every aggregate here splits on `DEBIT` vs `CREDIT`, so a row the parser misread lands in the wrong total with no way for aggregation to notice. The correction lives on the transaction detail screen ([design-system-and-navigation](design-system-and-navigation.md)) and writes the column these queries already read, so no aggregate needs a special case — a reclassified row simply leaves one total and joins the other on the next emission.
- **Selected month is an input, not a constant:** every aggregate takes the `MonthRange` the user picked. The one place the boundary is computed remains `MonthRange`, so a month chosen on Home and a month chosen on Spends resolve identically.
- **Budgets:** a `budgets` row's `limit_minor` compared against the same-month same-category spend sum; progress is a derived, non-persisted value (limit vs. actual), recomputed on read, never stored, so it's always consistent with the latest transactions.
- **Trends:** month-over-month comparison is a query across multiple month buckets; v1 scope is limited to what the reference UI implies (current vs. prior month) rather than an open-ended date-range picker.
- **Merchants view:** a group-by-merchant-key aggregate over the selected month's `DEBIT` transactions, sorted by total spend descending, to power the "Merchants" tab.

## Related blueprints

### Required

- [local-persistence](local-persistence.md) — every aggregation here is a Room query; a schema change to `transactions` or `budgets` columns changes what this blueprint can compute.

### Impact checks

- [design-system-and-navigation](design-system-and-navigation.md) — check this whenever a new chart type or visual affordance (e.g., the spend ring, the donut legend) is introduced, since rendering owns the visual contract while this blueprint owns only the numbers feeding it.

## Relevant implementation and tests

- `app/src/main/java/dev/xpensetracker/app/analytics/SpendSummaryRepository.kt` — month-scoped spend/income aggregate queries exposed as `Flow`.
- `app/src/main/java/dev/xpensetracker/app/analytics/CategoryBreakdownRepository.kt` — per-category sums, percentages, and joined display metadata for the donut.
- `app/src/main/java/dev/xpensetracker/app/analytics/BudgetProgressCalculator.kt` — derived limit-vs-actual computation, pure function over inputs from Room.
- `app/src/main/java/dev/xpensetracker/app/analytics/MonthRange.kt` — the single month-boundary conversion, plus `previousMonth`.
- `app/src/test/java/dev/xpensetracker/app/analytics/` — unit tests for month-boundary math, budget progress, and (in `CategoryBreakdownRepositoryTest`) category-breakdown percentages, slice metadata, the uncategorized bucket, and the ring rollup, against seeded in-memory Room data.

## Acceptance or verification criteria

- [ ] Spend and income totals for a month include every non-`IGNORED` transaction dated within that device-local calendar month.
- [ ] Category percentages sum to 100% (within floating-point display rounding) for a non-empty month.
- [ ] Each breakdown slice carries the display name and color of its category row, and a slice whose id does not resolve still contributes its amount to the total.
- [ ] Budget progress is always recomputed from current data, never stale/cached incorrectly after a transaction edit.
- [ ] A transaction timestamped at a month boundary (e.g., 23:59:59.999 local time on the last day) is bucketed into the correct month.
- [ ] CI (`build-and-ci`) green run executes all aggregation unit tests on every push.

## Remaining gaps and unknowns

- Trend analytics beyond current-vs-prior-month (e.g., multi-month charts) are not designed; v1 scope is intentionally narrow.
- Handling of `CREDIT` transactions that are refunds/reversals rather than true income is not modeled; all `CREDIT` counts as income for now, which may misrepresent true income in edge cases. The per-transaction direction override lets a user correct an individual row but does not give refunds a category of their own.
- Performance of aggregate queries against a multi-year transaction history has not been evaluated or indexed for.
