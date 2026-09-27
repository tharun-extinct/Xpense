# Blueprint Manifest

## Loading protocol

1. Match the task by intent and synonyms against the router table below.
2. Load the one primary blueprint for that task.
3. Load only the architecture sections it links to, not the whole of `architecture.md`.
4. Inspect the listed implementation and test paths before changing behavior.
5. Load an impact-check blueprint only when the change actually touches the condition named for it.
6. For a shared-contract change (anything that would edit `architecture.md`), load the complete architecture file and every manifest row that links the affected anchor, then review each of those blueprints.
7. Do not load a blueprint that no row routes you to.

## Router

| Concepts and synonyms | Responsibility type | Primary blueprint | Required architecture contracts | Required blueprints | Impact checks | Principal implementation and tests |
|---|---|---|---|---|---|---|
| SMS receiver, inbox backfill, sender allowlist, dedupe, content hash, SMS_RECEIVED | Ingestion | [sms-ingestion](sms-ingestion.md) | [Identity and ownership](../architecture.md#identity-and-ownership), [Error and recovery](../architecture.md#error-and-recovery), [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant) | None | [transaction-parsing](transaction-parsing.md) when the normalized-body contract changes | app/src/main/java/dev/xpensetracker/app/ingestion/; app/src/test/java/dev/xpensetracker/app/ingestion/ |
| parser, regex rules, amount extraction, merchant text extraction, confidence score, fixture corpus | Domain logic | [transaction-parsing](transaction-parsing.md) | [Data representation](../architecture.md#data-representation), [Error and recovery](../architecture.md#error-and-recovery) | None | [merchant-categorization](merchant-categorization.md) when parser output fields change | app/src/main/java/dev/xpensetracker/app/parsing/; app/src/main/assets/parser-rules/; app/src/test/java/dev/xpensetracker/app/parsing/ |
| categorize, merchant rule, user override, needs-review queue, auto-category | Domain logic | [merchant-categorization](merchant-categorization.md) | [Identity and ownership](../architecture.md#identity-and-ownership), [State lifecycle](../architecture.md#state-lifecycle) | [transaction-parsing](transaction-parsing.md) | [local-persistence](local-persistence.md) when the merchant_rules schema changes | app/src/main/java/dev/xpensetracker/app/categorization/; app/src/test/java/dev/xpensetracker/app/categorization/ |
| Room, database, schema, migration, DAO, transactions table, accounts table, budgets table, categories table, custom category storage | Persistence | [local-persistence](local-persistence.md) | [Identity and ownership](../architecture.md#identity-and-ownership), [State lifecycle](../architecture.md#state-lifecycle), [Data representation](../architecture.md#data-representation), [Error and recovery](../architecture.md#error-and-recovery) | None | [spend-analytics-and-budgets](spend-analytics-and-budgets.md) when a query contract changes | app/src/main/java/dev/xpensetracker/app/data/; app/src/test/java/dev/xpensetracker/app/data/ |
| spend ring, category donut, trends, budgets, monthly aggregation, income vs spends | Domain logic | [spend-analytics-and-budgets](spend-analytics-and-budgets.md) | [Data representation](../architecture.md#data-representation), [State lifecycle](../architecture.md#state-lifecycle) | [local-persistence](local-persistence.md) | [design-system-and-navigation](design-system-and-navigation.md) when a chart or visual contract changes | app/src/main/java/dev/xpensetracker/app/analytics/; app/src/test/java/dev/xpensetracker/app/analytics/ |
| theme, dark mode, lime accent, typography, shape and spacing tokens, bottom navigation, Home/Spends/Accounts/Settings screens, month filter, month picker, transaction detail, needs-review screen, category management UI, category picker | UI shell | [design-system-and-navigation](design-system-and-navigation.md) | [Data representation](../architecture.md#data-representation) | None | [spend-analytics-and-budgets](spend-analytics-and-budgets.md), [permissions-and-onboarding](permissions-and-onboarding.md) when navigation entry points change | app/src/main/java/dev/xpensetracker/app/ui/theme/; app/src/main/java/dev/xpensetracker/app/ui/navigation/; app/src/main/java/dev/xpensetracker/app/ui/components/; app/src/test/java/dev/xpensetracker/app/ui/ |
| permission request, onboarding, first run, SMS access rationale, no-network explanation | UI flow | [permissions-and-onboarding](permissions-and-onboarding.md) | [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant) | [sms-ingestion](sms-ingestion.md) | None | app/src/main/java/dev/xpensetracker/app/ui/onboarding/; app/src/test/java/dev/xpensetracker/app/ui/onboarding/ |
| Gradle, GitHub Actions, CI workflow, lint, assembleDebug, toolchain versions, release | Build and tooling | [build-and-ci](build-and-ci.md) | [Verification boundaries](../architecture.md#verification-boundaries), [Privacy and permission invariant](../architecture.md#privacy-and-permission-invariant) | None | Every blueprint, when the JVM or Robolectric test boundary changes | .github/workflows/; gradle/libs.versions.toml; build.gradle.kts; app/build.gradle.kts |

## Status vocabulary

- `Implemented`: current behavior is verified by identified code or tests, backed by a green GitHub Actions run.
- `Partial`: some acceptance criteria are verified; gaps are named explicitly.
- `Planned`: intended behavior is not yet verified in code.
- `Unknown`: evidence is insufficient or conflicting.
- `Deprecated`: retained only for compatibility or migration.
- `Superseded`: replaced by a named successor blueprint.
