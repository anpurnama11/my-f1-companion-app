---
id: 22
title: "Remaining minor observations batch"
type: task
status: open
blocked_by: [17, 18]
owner: ""
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
8. **`HomepageScreen.kt:240-307` `DriverCard` and `TeamCard` are 28 lines of line-for-line duplication** — collapse into one `FavoriteEntry` composable (depends on ticket 18 shape decision).

### Logic / data
9. **`failureState` in §1 empty-card branch only surfaces the first error** — not the most relevant one. If `favorites` is `Error` because the user has never picked, the §1 card is mis-attributed.
10. **`ScheduleViewModel.retryPodium(round)` early-returns when `year == 0` during initial `Loading`** — row stays in `Loading` until season resolves, no user signal. A snackbar ("Schedule still loading") would be honest.
11. **`winnerId`-based Upcoming/Past split is a single point of failure** — DTO mapping `null` would misclassify a completed race as Upcoming. Add an integration test asserting the filter on a known-past f1api.dev fixture.
12. **`ScheduleScreen.kt:243` `race.name.ifEmpty { race.name }` is dead code** — both branches identical. Likely meant `race.raceId`. Fix the dead branch.

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
