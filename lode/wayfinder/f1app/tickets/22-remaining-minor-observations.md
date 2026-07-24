---
id: 22
title: "Remaining minor observations batch"
type: task
status: closed
blocked_by: [17, 18]
owner: dev
---

## Question

The critique (`lode/tmp/f1app-critique-2027-01-15.md`) lists several minor observations beyond the P0s (all resolved), the P1 weekend-card (closed in ticket 17), and the edge-to-edge insets bug (separate ticket 21). This ticket batches the remaining polish items.

## Items in scope

Each is a small, scoped fix. Can be split into execution tickets when work begins.

### A11y / TalkBack
1. **No TalkBack content description on §3's `CircuitCard`** or the Schedule row. TalkBack reads the visible text in order without indicating it's a single tappable element.
2. **No page-count semantics on the §1 `HorizontalPager`.**
3. **`LIVE` chip on §1 has no live indicator** — static red pill, no pulse. A 4dp `Box` with `rememberInfiniteTransition` + `animateFloat` on alpha (1.0 → 0.3 → 1.0, 1.5s) would convey "this is happening right now" within the locked tokens.

### Visual polish
4. **`PodiumCell` loading state uses 20dp / 2dp spinner** — below M3's 24dp / 2.5dp minimum; invisible against `surfaceContainer`. Bump to 24dp / 2.5dp.
5. **`"RACE COMPLETE"` chip on §1 uses `surfaceContainerHighest` background** — barely distinguishable from the card's `surfaceContainer`. Use `outline` background or a higher-contrast surface.
6. **`CountdownCard` wraps a `Row` in a `Box` for no reason** — collapse to a single `Row`. May be redundant after ticket 17 extends the §1 card.
7. **Page-indicator dots on §1 are visual-only** — not tappable to jump. Add `Modifier.clickable` per dot.
8. **Replace the §1 favorites pager with ticket 18's locked §3 shape** — one combined card with Driver 1, Driver 2, and Constructor rows, each rendered by one shared `FavoriteEntry` composable with a per-row constructor-color bar. Render one “Pick favorites” CTA card when empty. This both implements ticket 18 and removes the duplicated `DriverCard` / `TeamCard` layouts.

### Logic / data
9. **`failureState` in §1 empty-card branch only surfaces the first error** — not the most relevant one. If `favorites` is `Error` because the user has never picked, the §1 card is mis-attributed.
10. **`ScheduleViewModel.retryPodium(round)` early-returns when `year == 0` during initial `Loading`** — row stays in `Loading` until season resolves, no user signal. A snackbar ("Schedule still loading") would be honest.
11. **`winnerId`-based Upcoming/Past split is a single point of failure** — DTO mapping `null` would misclassify a completed race as Upcoming. Add an integration test asserting the filter on a known-past f1api.dev fixture.
12. **`ScheduleScreen.kt:243` `race.name.ifEmpty { race.name }` is dead code** — both branches identical. Likely meant `race.raceId`. Fix the dead branch.

## Resolution

The remaining polish batch is implemented. `HomepageScreen` now renders one combined §3 three-row favorites card with per-row `TeamColors` accents, an empty-state CTA wired to `Route.MyTeam`, a pulsing LIVE marker, stronger RACE COMPLETE contrast, a 20dp top-speed spinner, and single-element circuit semantics. The old pager, dots, duplicate driver/team cards, and redundant countdown `Box` are gone. Favorite lookup failures identify the affected data, while persisted-but-unavailable selections show `Unavailable` rather than an add prompt.

`ScheduleScreen` now exposes circuit/round action semantics, uses the circuit name when a race name is blank, shows 24dp/2.5dp podium loading, and reports initial retry attempts through a snackbar. `ScheduleViewModel.retryPodium` rejects requests while the season or row is already loading. `GetSeasonUseCaseTest` covers mixed completed/upcoming fixture mapping. `TeamColors` includes the API's `redbull` id and returns `Color.Unspecified` for unknown teams.

Verification: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:compileDebugAndroidTestKotlin`, and `./gradlew :app:assembleDebug` passed. Instrumentation execution remains unavailable without a connected device.

## Out of scope

- The 4 P0s from the critique — all resolved (see critique document "Resolved" notes).
- The P1 weekend card — closed in ticket 17.
- The edge-to-edge insets bug — separate ticket 21.
- New features not in the critique (e.g. "add weather widget").

## Cross-references

- `lode/tmp/f1app-critique-2027-01-15.md` — the source document; full context per item.
- Ticket 17: Q1/Q4 homepage layout — affects items 6, 7, 9.
- Ticket 18: §3 favorites shape — affects item 8 (the duplication fix).
- Ticket 21: edge-to-edge insets bug — adjacent.
