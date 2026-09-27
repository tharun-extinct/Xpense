# Design System and Navigation

## Outcome or responsibility

Provide the shared Compose theme (dark surface, lime accent, typography, category color palette) and the four-destination bottom-navigation shell (Home, Spends, Accounts, Settings) that every screen blueprint builds on, so the app has one consistent, intuitive visual language inspired by — but not a copy of — the reference screenshots.

## Current verified status

**Status:** Partial

The theme, type scale, shape and spacing tokens, the four-destination shell, and the shared composables all exist, alongside three detail routes (transaction detail, needs-review queue, category management) and a month filter shared by Home and Spends.

Gap: the pipeline did not run the `test` task while the app was being brought up (see [build-and-ci](build-and-ci.md)), so the Compose smoke tests are written but not yet executed by CI. Visual polish has not been validated on a real device.

## Architecture dependencies

- [Data representation](../architecture.md#data-representation) — category name and color are row data, so no screen may hardcode a category label or hue; both come from the `categories` row. This is the one shared contract this otherwise presentation-only blueprint must obey.

## Local rules and implications

- **Palette:** base background `#0D0D0D`, elevated card surface `#1A1A1A`, on-dark text `#FFFFFF`/`#B3B3B3` secondary, destructive rose `#F43F5E`, income green `#4ADE80`. The accent is named `AccentPrimary` for its role, not its hue, because the previous hue-based name had to be edited in seventeen files to change one colour.
- **The accent is a user preference; everything else in the palette is not.** Four accents are selectable (`AccentTheme`: Ember `#FF8A3D` default, Lime `#A8E84A`, Azure `#22D3EE`, Orchid `#E879F9`), each shipping its own darkened `muted` variant for Material's `primaryContainer` so adding a theme is one decision rather than two that can drift. Surfaces, text, the income green and the destructive rose stay fixed, because those carry meaning and a display preference must never make a destructive action look ordinary. The destructive colour is rose rather than the former orange-leaning `#E85A4A` specifically so it stays distinguishable from the warm default accent.
- **`AccentPrimary` is read through a composition local, not a constant.** It survives as a property with its original name so the call sites already written as `color = AccentPrimary` did not have to change when it became dynamic; `LocalAccentTheme` is `static` because the value changes only on an explicit user action, when rebuilding the whole tree is what you want anyway. The consequence to remember: the accent can only be read *in composition*, so anything reading it inside a `DrawScope` (`SpendRing`'s `Canvas`) must hoist it into a `val` first.
- **Swatch rule, weakened deliberately:** no category swatch may be the *exact* value of any selectable accent. Full hue separation was achievable with one fixed accent and is not with four — sixteen swatches cannot dodge four hues without becoming a thin palette — so the guarantee that remains is that the accent is never the only signal that something is interactive; shape, placement, and labels carry it too. Category hues are **not** in this file; they live on the `categories` row as `colorArgb` so a color always means the same category app-wide and a user-created category gets a real color like any other. `Color.kt` retains only the palette defaults used to seed new categories.
- **Typography:** a single type scale (display for the big spend-ring number, title for section headers, body for transaction rows, label for chips/badges) defined once in `Type.kt` and referenced everywhere — no screen defines ad hoc font sizes. The scale must cover every style any screen references; `bodySmall` was used by Settings while undefined, silently falling back to the Material default.
- **Shape and spacing tokens:** `Shape.kt` is passed into `MaterialTheme` so `MaterialTheme.shapes` is the corner-radius source, and `Spacing.kt` defines the 4/8/12/16/20/24/32 step. Screens reference tokens rather than inline `dp` literals, which is what keeps rhythm consistent across four independently-edited screens.
- **Navigation shell:** bottom navigation bar with exactly four destinations — Home, Spends, Accounts, Settings — using Compose Navigation (`NavHost`), each destination a top-level route. Detail surfaces are **full-screen routes layered on the same host**, not bottom sheets or split panes: `transaction/{id}`, `review`, and `categories`. The needs-review banner on Home links into `review` rather than becoming a fifth nav destination.
- **Month filter is hoisted, not per-screen:** the selected `YearMonth` lives in the shell as `rememberSaveable` state shared by Home and Spends, so the two can never display different months and the selection survives tab switches and process death. Its bounds come from data — no forward paging past the current month, no backward paging past the earliest recorded transaction — so the user cannot navigate into guaranteed-empty months.
- **Componentization:** shared composables (`SpendRing`, `CategoryDonut`, `TransactionRow`, `AccountCard`, `SectionHeader`, `MonthSelector`, `CategoryPickerGrid`, `SegmentedTabs`, `StatCard`, `EmptyState`) live in `ui/components/` and are reused across screens rather than duplicated, since several surfaces show overlapping data (recent transactions, spend totals, category pickers).
- **A screen that states a derived fact must let the user correct it:** the transaction detail route shows the parser's reading of direction and, directly below the detail panel, a control to overrule it. Copy that depends on direction is written from the transaction's current classification — the ignore affordance says "xpense" or "income" accordingly — because fixed wording made the app look like it had misread a credit even when it had not.
- **Everything on the detail screen is a draft until Save:** category, direction, note, and location tag are all held in screen state and written in one go by the Save/Confirm button, so leaving without saving discards all of them and no field can be half-applied. Note and location are held as `rememberSaveable`, since a process death mid-edit must not silently drop typing or a tag the user believes is attached.
- **The location tag says what it actually is:** an SMS can arrive long after the purchase, so the control records where the user is *now* and the caption says exactly that. Tapping a tag hands it to the device's default map app through a `geo:` intent rather than rendering a map in-app, which would require the network access the privacy invariant forbids; when no map app accepts the intent the screen says so instead of appearing inert.
- **A chart must never render as a blank shape:** every data visual draws a neutral track before its data, renders explicit copy when it has nothing to show, and labels itself with a center total, so an empty or single-category month is legible as data rather than mistakable for a broken component.
- **Typeface constraint:** a custom font would require committing a binary `.ttf`, which cannot be authored as text in this repository. The type scale therefore uses the system family and derives character from weight, size, and letter-spacing. Adding a font file later is a `Type.kt`-local change.
- **Differentiation from the reference:** no promotional/ad banners (e.g., no "Amazon Pay" style card slot), a visible needs-review affordance the reference doesn't have, and budget progress rendered directly on/near the spend ring rather than as a separate deposits section.
- **Accessibility baseline:** minimum touch target sizes and content descriptions on icon-only buttons (e.g., the add-transaction FAB, chart tap targets) are required from the first screen, not retrofitted later.

## Related blueprints

### Required

None.

### Impact checks

- [spend-analytics-and-budgets](spend-analytics-and-budgets.md) — check this whenever a new visual (chart type, ring style) is introduced, to keep the numbers-vs-rendering boundary clean.
- [permissions-and-onboarding](permissions-and-onboarding.md) — check this whenever a nav entry point or first-run route changes, since onboarding decides what the user sees before the shell is reachable.

## Relevant implementation and tests

- `app/src/main/java/dev/xpensetracker/app/ui/theme/Color.kt` — fixed palette values, the accent accessors backed by `LocalAccentTheme`, and the swatch set offered when creating a category.
- `app/src/main/java/dev/xpensetracker/app/ui/theme/AccentTheme.kt` — the four selectable accents and their persisted ids.
- `app/src/main/java/dev/xpensetracker/app/data/ThemePreferences.kt` — DataStore-backed accent preference, kept out of Room so a display setting can never appear in a migration that must not lose money.
- `app/src/test/java/dev/xpensetracker/app/data/ThemePreferencesTest.kt` — round trip and the stale-id fallback.
- `app/src/main/java/dev/xpensetracker/app/ui/theme/Type.kt` — type scale.
- `app/src/main/java/dev/xpensetracker/app/ui/theme/Shape.kt` — corner-radius tokens wired into `MaterialTheme`.
- `app/src/main/java/dev/xpensetracker/app/ui/theme/Spacing.kt` — spacing step.
- `app/src/main/java/dev/xpensetracker/app/ui/theme/CategoryIcons.kt` — `iconKey` to `ImageVector` resolution for the fixed icon set.
- `app/src/main/java/dev/xpensetracker/app/ui/navigation/xpenseNavHost.kt` — `NavHost` with the four top-level routes, the three detail routes, and the hoisted month filter.
- `app/src/main/java/dev/xpensetracker/app/ui/components/` — shared composables listed above.
- `app/src/main/java/dev/xpensetracker/app/ui/transaction/`, `ui/review/`, `ui/categories/` — the detail routes.
- `app/src/main/java/dev/xpensetracker/app/location/` — `DeviceLocationSource` (one-shot platform fix, no Play Services) and the `geo:` intent hand-off used by the detail route's location tag.
- `app/src/test/java/dev/xpensetracker/app/ui/` — Robolectric Compose UI tests for nav destinations, donut states, and the month selector's bounds.

## Acceptance or verification criteria

- [ ] All four bottom-nav destinations are reachable and each renders without crashing on an empty database (zero transactions).
- [ ] Category colors are stored in exactly one place (the `categories` row) and referenced, never redefined, by the donut, the category list, and transaction avatars.
- [ ] No screen hardcodes a color or font size outside the theme/type-scale files.
- [ ] No category swatch equals a selectable accent's exact value, and the destructive colour is distinguishable from every accent.
- [ ] Choosing an accent in Settings repaints the whole shell, survives a restart, and changes neither the income green nor the destructive rose.
- [ ] A note typed and a location tagged on the detail screen are both discarded by backing out, and both persist when saved.
- [ ] The category donut renders legible copy on an empty month and a labelled ring on a single-category month, never an unexplained circle.
- [ ] The month filter cannot advance past the current month or before the earliest recorded transaction, and Home and Spends always show the same month.
- [ ] `transaction/{id}`, `review`, and `categories` are reachable full-screen routes with a working back affordance.
- [ ] Toggling a transaction between income and xpense on the detail screen moves its amount between the spend and income totals, and no screen describes a credit as an xpense.
- [ ] CI (`build-and-ci`) green run executes Compose UI smoke tests for navigation on every push.

## Remaining gaps and unknowns

- Light theme is out of scope for v1; the reference and this app are both dark-only by design, but this hasn't been explicitly confirmed as a permanent constraint versus a v1 shortcut.
- Tablet/large-screen layout adaptation is not designed; v1 targets phone form factors only.
- Motion/animation spec (e.g., donut chart entrance animation) is not yet detailed beyond "should feel intuitive and modern."
