# F1app Critique — Homepage + Schedule (primary tabs)

**Target:** Homepage (`app/src/main/java/com/anpurnama/f1_app/feature/homepage/HomepageScreen.kt`) + Schedule (`app/src/main/java/com/anpurnama/f1_app/feature/schedule/ScheduleScreen.kt`).
**Surface mode:** Operate (app UI, design serves the product).
**Resolved target for persistence:** `app/src/main/java/com/anpurnama/f1_app/feature/homepage/HomepageScreen.kt` (representative; both surfaces covered in this report).
**Date:** 2027-01-15.

---

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3/4 | §1 LIVE / countdown / RACE COMPLETE trichotomy is clean; pull-to-refresh's `isRefreshing = anyLoading` (`HomepageScreen.kt:67-72`) couples the spinner to all 7 sections. |
| 2 | Match System / Real World | 3/4 | F1 jargon correct (FP1, SQuali, RACE). `formatRaceDate` now renders `Sat 2 Mar` (resolved 2026-07-23) — the raw-ISO knock is gone. Still: no circuit name on Schedule rows; "Top speed" on §3 has no qualifier. |
| 3 | User Control and Freedom | 3/4 | Pull-to-refresh, per-row Retry, system Back respects multi-backstack. But no way to clear/change favorites from §1 (My Team is still a placeholder). |
| 4 | Consistency and Standards | 3/4 | Both surfaces share `OutcomeContent`, Card chrome, Spacing scale. But two date systems in one app (Homepage `MON 14:00` vs Schedule ISO). `DriverCard` and `TeamCard` are 28 lines of line-for-line duplicates. |
| 5 | Error Prevention | 3/4 | Per-row retry is the right shape; `seedIfEmpty` is partial-fill safe. But `SectionUiState.Error` doesn't distinguish "no data" from "transient network." |
| 6 | Recognition Rather Than Recall | 2/4 | F1 abbreviations shown as-is (FP1, SQuali). §1's Driver/Team cards differ only by a one-word caption — casual fans don't know what a "Constructor" is. §3 "Top speed" has no context. |
| 7 | Flexibility and Efficiency | n/a | Product principles target a casual fan; no filter/sort/search is in scope. The surface is small enough that efficiency gestures would be premature. |
| 8 | Aesthetic and Minimalist Design | 3/4 | Dark surface + 23 circuit brand accents carry real identity. §2 is now a single `surfaceContainer` card with a `F1Primary` circular gauge on the left and a 3-row stat column on the right (gauge + "complete" caption + inline label/value rows); the old 4-stacked-cards pattern is gone. |
| 9 | Error Recovery | 3/4 | Pull-to-refresh, per-row retry, section errors render Retry. But the empty card for §1's initial load (`HomepageScreen.kt:175-184`) has no retry beyond swipe-down. |
| 10 | Help and Documentation | n/a | No in-app help in scope for a casual dark-first app; empty/error states carry the documentation. |
| **Total** | | **23/32** | **Acceptable — at the low end. H6 is the drag. H2 (date format) and H8 (circular gauge) resolved.** |

**Rating band:** 23/32 = 71.9% → **Acceptable**, with significant improvements needed before v1 ships. H6 (recognition for a DtS audience) remains the drag.

---

## Design Specificity Verdict

**LLM assessment (60% authored, 40% category-template):** The two surfaces carry enough F1 fingerprints to feel authored for this app — `Constructor`, `FP1`/`Quali`/`RACE` chips, P1/P2/P3, top speed in km/h, round numbers, the 6dp `Circuits.forId(...)` strip on §3, the brand-tinted track-layout image on §1. A casual Drive-to-Survive fan would recognize "this is for F1" within two seconds. But everything else is straight M3 Card-on-`surfaceContainer` with the same vertical padding, the same `labelMedium` grey caption, the same `Column { Text + Text + Text }` rhythm. No F1-specific glyph, no checkered-flag motif, no paddock-pulse animation, no typographic distinction (default Roboto), no silhouette of a car/driver, and §1's Driver/Team cards are visually interchangeable apart from a one-word caption. The user is *near* the sport, not *inside* it.

**Deterministic scan:** The bundled `detect.mjs` detector is web/HTML-scoped and returned 0 findings with exit code 0 on both `.kt` files (no error, no signal). It is not informative for Kotlin/Compose. No detector-vs-LLM agreement or disagreement to report.

**Visual overlays:** Skipped — native Android target, no live URL, `live.mjs` flow is web-only. Fallback signal: no browser-based overlay available for this surface.

---

## Overall Impression

The bones are right. Brand-accent discipline (`Circuits` on dark, never as text), section independence under failure, the LIVE/countdown/complete trichotomy, and per-row retry are real design moves that most F1 apps miss. But three issues block the v1 bar set by `PRODUCT.md`:

1. **A data-correctness bug on a "real data only" surface** (`totalKm` label-vs-value unit mismatch) — ✅ resolved 2026-07-23.
2. **A spec gap on §2** (no circular progress gauge, four stacked text cards instead) — ✅ resolved 2026-07-23.
3. **Two design choices that under-serve the "never miss a session" principle** — Schedule dates ship as raw ISO (✅ resolved 2026-07-23), and the 5-session weekend schedule is already loaded but never rendered (the §1 countdown uses only `nextUpcoming(now)`).

The single biggest remaining opportunity: render the loaded-but-hidden weekend schedule as a 4th homepage section. Pure UI move on data that's already there. That gets the screen to the "Good" band (28+).

---

## What's Working

1. **Brand-accent discipline end-to-end.** `Circuits.forId(race.circuit.id)` is used in three places (the 6dp §3 strip at `HomepageScreen.kt:381-386`, the §1 track-layout image tint at `HomepageScreen.kt:327-333`, the page-indicator dot's selected color) and nowhere as text. The `Color.kt` contract — accent backgrounds on dark, never text — is respected everywhere. That is a real identity move and most F1 apps miss it.
2. **Section independence under failure.** Both VMs keep every section in its own `SectionUiState`; the `combine` fold is a per-atom map, not a composite. A `/race` 4xx blanks exactly one row of Past, not the whole list. A `nextRace` 5xx blanks exactly §3, not §1 or §2. The architecture is right and the screens honor it.
3. **LIVE / countdown / RACE COMPLETE trichotomy in §1.** `HomepageScreen.kt:300-310` (the `live: SessionTime?` and `raceComplete` derivations) correctly covers the three temporal states. The `LIVE` chip uses `primary` (F1Primary red) and the others use `surfaceContainerHighest` — real visual differentiation, not a single label that changes text.

---

## Priority Issues

**[P0] `totalKm` label-vs-value unit mismatch on a "real data only" surface.** ✅ **RESOLVED 2026-07-23** (clarify + harden pass).
- **Why it matters:** §2's "Total km" `StatCard` (`HomepageScreen.kt:491-498`) reads `s.totalKm.toString()`. The lode note (`homepage.md`) says `circuitLength` arrives as `"7004km"` and gets `strip non-digits` → `7004`. If the API actually returns meters (the F1 standard, e.g. `7004` meaning 7.004 km), the field name `totalKm` is wrong and the displayed value is roughly 1000× too large. If the API literally returns `"7004km"`, the value 7004 is the correct km and the per-race sum would be ~168 km (plausible for 24 races × 7 km). Either way, label and value may not agree, and a stats-aware user (Maya) catches it in seconds — eroding the "real data only" principle.
- **Fix:** Verify the API response shape first with a single curl call against f1api.dev `/current`. If meters, the `Season` mapping must `value / 1000` and the field should be `totalKm: Double` so partial-km values are not silently truncated. If already km, the field is correct. Add a unit test asserting the unit. Render with one decimal (`"168.4 km"`) once verified.
- **Resolution:** Live `curl https://f1api.dev/api/current` confirmed the API returns **meters** (e.g. Albert Park `"5278km"` = 5.278 km). Fix shipped: `Season.totalKm` is now `Double`, the mapper does `digits / 1000.0`, the label is "Total km covered", the render is `%.1f`, and `SeasonAggregatesTest.totalKm converts meters on the wire to km in the domain` pins the unit (Bahrain 5.412 + Spa 7.004). Lode updated in 5 places (terminology, specs, homepage.md, ticket 03, ticket 01). 20/20 unit tests pass.
- **Suggested command:** `/impeccable harden` (data correctness) and `/impeccable clarify` (label vs. value).

**[P0] §2 has no circular progress gauge.** ✅ **RESOLVED 2026-07-23** (layout pass).
- **Why it matters:** The spec calls for a circular progress on the left of §2 with three stat tiles on the right (`homepage.md` §2). The code instead stacks four `Card`s vertically, each a label + a value, with no gauge. `ProgressCard` was just a label and a `31%` number on a card. A Drive-to-Survive fan who checks in mid-season wants to *see* "we're a third of the way through" at a glance. The current text-only rendering makes the season feel like a spreadsheet. Pure UI, no data or contract dependency — ship it first to build momentum.
- **Fix:** Add a `CircularProgressGauge(percent: Int)` in `HomepageScreen.kt` using `Canvas` (the spec leaves M3 vs Canvas open). Render a `Row { CircularGauge; Column { StatTile x 3 } }` and delete the four stacked cards. The stat tiles should be one composite card with three internal columns, not four cards. Render the progress as a fraction too: `31% · 7 of 24 rounds`.
- **Resolution:** §2 is now one `surfaceContainer` card. New `CircularProgressGauge(percent: Int)` (`HomepageScreen.kt`) draws a 144dp `F1Primary` arc on a faint `outlineVariant` track, starting at 12 o'clock and sweeping clockwise with a round stroke cap. Center holds the integer percent (`headlineLarge` bold) + a `labelSmall` "complete" caption. The right column is a `Column(weight = 1f)` of three `SeasonStatRow`s (GPs completed, Total km covered, Total laps) — inline label+value rows, no card chrome. The four stacked cards and the old `ProgressCard` / `StatCard` composables are deleted. Animation: `animateFloatAsState(tween(900ms, FastOutSlowInEasing))` for the arc. `compileDebugKotlin` clean; layout detector clean. Lode updated in `homepage.md` — the open `M3 vs Canvas` question is decided (Canvas) with rationale recorded.
- **Not adopted:** the suggested `"31% · 7 of 24 rounds"` fraction. The gauge carries `%` + `"complete"` only. `scheduledGp` is on the `Season` model but isn't surfaced — the §1 "next event" card already names the round, so the fraction would be redundant context. If a future Maya-style persona needs the count, `scheduledGp` is one render away.
- **Suggested command:** `/impeccable layout` then `/impeccable shape` (to confirm the gauge choice).

**[P0] Schedule row date is raw ISO `2025-03-23`, contradicting its own docstring.** ✅ **RESOLVED 2026-07-23** (clarify pass).
- **Why it matters:** `formatRaceDate` (`ScheduleScreen.kt:368-385`) declared `"Sun 23 Mar · 15:00"` in its KDoc but the body returned `slot.date` verbatim. The time portion was dropped. The function was a redundant `when` that reduced to `return slot.date`. This was the first thing a casual user reads on every Schedule row. ISO dates also fought Homepage's `MON 14:00` format — two date systems in one app.
- **Fix:** Replace `formatRaceDate` to actually format per the docstring, or remove the time if intentionally not surfaced. Mirror the `formatStart` helper at `HomepageScreen.kt:503-509` for consistency. Apply the same formatter to §3's nearest-GP card if `raceDate` is rendered there (currently it isn't — only `laps` and `corners` show).
- **Resolution:** `formatRaceDate` now parses the raw `YYYY-MM-DD` from `SessionSlot.date` via `kotlinx.datetime.LocalDate(year, monthNumber, dayOfMonth)` and renders `"Sat 2 Mar"` — e.g. `2024-03-02` → `Sat 2 Mar`. Mirrors Homepage's manual `dayOfWeek.name.take(3)` + month-name titlecase style. Date only, by design: the race time is on the Homepage countdown card (which answers "is it starting now?"), and the Schedule row answers "when is/was that race?" — two different questions, two different fields. The redundant `when` and the time-port-of-the-KDoc are gone. The `if (raceDate != null)` check at the call site handles the new `runCatching { }.getOrNull()` on missing or malformed input the same way it handled the old `null` return. `compileDebugKotlin` clean.
- **Not adopted:** the suggested `"EEE d MMM · HH:mm"` formatter (with time). User explicitly asked for date-only in Schedule: "for upcoming i only need date. time i can see in homepage instead of schedule." The two date systems in the app are now an intentional split (Homepage: weekday + time of day; Schedule: weekday + day + month), not a duplication.
- **Suggested command:** `/impeccable clarify` and `/impeccable typeset`.

**[P0] Past-row podium has no P1 emphasis and no team color.**
- **Why it matters:** `PodiumChip` (`ScheduleScreen.kt:335-358`) renders three identical `Column`s of `Text` (P1/P2/P3 caption + driver name) on a `RoundedCornerShape(8.dp)` clip but with **no background fill, no P1 emphasis, and no team color**. P1 looks exactly like P3. The chip captures `team: String?` into `teamLabel` but never renders it. A trailing `Spacer(Modifier.width(Spacing.xs))` (`ScheduleScreen.kt:357`) appears after every chip including the last, creating asymmetric spacing. The whole ticket-10 spec exists to surface the podium — that's the only reason a casual fan opens the Past tab. Rendering it as three equivalent text columns undercuts the ticket.
- **Fix:** Make P1 visually dominant (larger type, or a `F1Primary` background mirroring the LIVE chip — a single visual rule across the app: red = current/active, red background = the winner). Render team short name (`teamLabel`) under the driver name in `labelSmall`. Drop the trailing `Spacer`.
- **Suggested command:** `/impeccable bolder` then `/impeccable shape` to confirm P1 dominance choice.

**[P1] The 5-session weekend card is in the spec, the data is already loaded, but the UI is absent.**
- **Why it matters:** The spec says (lode `homepage.md` §1, "Below the fold"): a "next race" weekend card. The current §3 `CircuitCard` (`HomepageScreen.kt:374-432`) is the "nearest-GP info" card (round/GP name/laps/corners/top speed) — not the "weekend" card (5-session schedule, dates, times). The `weekendSchedule` atom in the VM (`HomepageViewModel.kt:73-78`) is already loaded but §1 uses only `nextUpcoming(now)`. The product's headline job is "never miss a session." A 5-row weekend card closes the core use case more directly than the podium or the gauge — the user sees "FP1 Fri 13:30, Quali Sat 16:00, Race Sun 15:00" without leaving the Homepage.
- **Fix:** Add a `WeekendScheduleCard(schedule: SectionUiState<WeekendSchedule?>)` that renders a compact list of 5 session rows (FP1 / FP2 / Quali / Sprint / Race with local times) when the schedule is `Content(non-null)`. Reuse the `SessionChip` and `formatStart` helpers. Place between §1 and §2 so the "when" hero sits above the "how the season's going" stats.
- **Suggested command:** `/impeccable shape` (does this belong as §1b or replace §3's laps/corners?) then `/impeccable layout`.

---

## Persona Red Flags

**Jordan (Confused First-Timer):**
- Opens the app. Homepage shows a 180dp empty box for ~1–2s while standings load. Jordan doesn't know the screen is loading — it just looks broken.
- §1's pager settles on the first card: `"Driver"` / `"George Russell"` / `"RUS · #63"` / `"P1 · 169 pts"`. The word `Constructor` in the next swipe is not defined anywhere. Jordan has to swipe to discover there's a second kind of card.
- The countdown card shows `"FP1"` chip and `"2d 5h"` big text. Jordan doesn't know FP1 is "Practice 1" — F1's first free-practice session. No tooltip, no expanded label, no glossary.
- Schedule > Past row: `"P1"` / `"VER"` / `"P2"` / `"NOR"` / `"P3"` / `"PIA"`. Jordan has to know that VER is Verstappen, NOR is Norris, PIA is Piastri. Three-letter codes are F1-orthodox but obscure to a Drive-to-Survive fan who's watched 2 seasons.
- The "—" used for "no top speed" on §3 (`HomepageScreen.kt:415-421`) reads as "we have no information" rather than "this round is pre-2023, OpenF1 has no data." Jordan doesn't know whether to interpret it as a bug or a known limitation.

**Casey (Distracted Mobile User):**
- Casey opens the app between meetings. The §1 countdown is the only thing that should matter for the "don't miss" job. Casey has to scroll past §1's pager indicator dots, then §2's four stat cards, then §3's circuit card, then the bottom nav to find the *single number that matters*. Three scrolls before the payoff.
- ~~The Schedule Past row's `2025-03-23` date is constant small friction. Casey has to mentally translate ISO to "that was last weekend."~~ Resolved 2026-07-23 (clarify pass): the row now shows `Sat 2 Mar`, which Casey reads in one beat.
- The §1 pager's 30s tick (`HomepageScreen.kt:294-302`) means the countdown changes underneath Casey's eyes — Casey looks back after a meeting and the number has moved. That's a feature, but the screen has no haptic, no animation, no "tick" — the change is silent and might be missed.
- The pull-to-refresh at the top of the Homepage is Casey's main tool, but its `isRefreshing` is bound to `anyLoading` of 7 sections (`HomepageScreen.kt:67-72`) — meaning if Casey pulls during a partial load, the spinner stays until *all seven* sections resolve, even the ones Casey didn't ask to refresh.
- Casey wants to tap a driver/team card to see more. §1's Driver/Team cards are not tappable — there's no `onClick` (`HomepageScreen.kt:240-307`). The only tappable card on Homepage is §3. Casey has no clear path to "tell me more about Russell."

**Maya (Stats Nerd — third axis, useful for data correctness):**
- Opens the app and goes to §2. Sees four cards: `Progress 31%`, `GPs completed 7`, `Total km 168000`, `Total laps 1430`. Immediately notices `Total km` is three orders of magnitude off (real value would be ~168) and loses trust in the screen.
- The §2 card now has the circular gauge Maya was missing (`F1Primary` arc on `outlineVariant` track, 144dp, "31%" + "complete" caption). The "X of Y rounds" fraction she asked for is still not surfaced — see the P0 §2 "Not adopted" note.
- §3's "Top speed 325 km/h" — Maya wants the year, the session, the driver. None are surfaced. The open question in `homepage.md` is still open in the code.
- Opens Schedule > Past and sees the podium chips. The team names are captured (`teamLabel = team ?: ""`) but never rendered (`ScheduleScreen.kt:337, 358`). The team-color or team-flag isn't used. Maya, who tracks constructors religiously, gets the driver but not the constructor.

---

## Minor Observations

- `race.name.ifEmpty { race.name }` at `ScheduleScreen.kt:243` is dead code: both branches return the same value. The intended fallback was likely `race.raceId`.
- ~~`formatRaceDate`'s `when` (`ScheduleScreen.kt:336-339`) is a no-op rewrite of `return slot.date`. The function reduces to a one-liner.~~ Resolved 2026-07-23 (clarify pass): the `when` is gone; the function now parses via `LocalDate` and renders the friendly form.
- `ScheduleScreen.kt` and `ScheduleViewModel.kt` had 7 stale KDoc references to `circuitImages` / `getCircuitImage` after the production circuit-image code was removed in commit `ab8a8f0 refactor(schedule): remove circuit image loading from schedule views` — the docs still said "per-race circuit image", "every past circuit image", `loadCircuitImage`, `circuitImagesState`, etc. All 7 cleaned in the 2026-07-23 follow-up; production was already circuit-image-free, so this was doc-vs-code drift, not a feature gap.
- `DriverCard` and `TeamCard` (`HomepageScreen.kt:240-307`) duplicate 28 lines of layout for what differs by 2 strings. A single `FavoritesCard` composable would close the duplication and the future-drift risk. Defer to the team-accent-strip pass.
- ~~`ProgressCard` renders a `Progress` label + a `31%` text. The label "Progress" is generic — "Season complete" or "Season progress" is more specific and tells the user what 31% is of. Add the fraction (`"31% · 7 of 24 rounds"`) for context.~~ Resolved with the P0 gauge pass: the generic "Progress" label is gone; the gauge now uses a specific "complete" caption. The "X of Y rounds" fraction suggestion is still open and was reviewed — see the P0 §2 "Not adopted" note.
- The `Spacer(Modifier.width(Spacing.xs))` at the end of `PodiumChip` (`ScheduleScreen.kt:357`) renders a trailing gap after the last chip in the row, making the visual spacing asymmetric.
- `ScheduleScreen.kt:301-312` `PodiumCell`'s loading state uses `CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)` — a 20dp spinner is below the 24dp M3 minimum recommended size for spinners and is hard to see against `surfaceContainer`. Recommend 24dp / 2.5dp.
- The page-indicator dots (`HomepageScreen.kt:201-221`) are visual only — not tappable to jump to a specific page. Adding tap-to-jump is a one-line change with a `Modifier.clickable` per dot, and matches user expectation.
- The "RACE COMPLETE" chip (`HomepageScreen.kt:319-323`) uses `surfaceContainerHighest` background, which is barely distinguishable from the card's `surfaceContainer`. The chip's container effectively disappears; only the text reads. Use `outline` background or a higher-contrast surface.
- §1's `Box` wrapping the `Row` in `CountdownCard` (`HomepageScreen.kt:280-336`) wraps a `Row` in a `Box` for no apparent reason — the `Row` already has `fillMaxSize` and the `Box` adds nothing. Can collapse to a single `Row`.
- `HomepageScreen.kt:140-145`: the `failureState` `when` in §1's empty-card branch only surfaces the *first* error, not the most relevant one. If `favorites` is `Error` because the user has never picked, the §1 card is mis-attributed. Consider logging the surface-actual error rather than cascading it.
- `ScheduleViewModel.kt:115-118` initializes `yearState = 0` then `podiumsState` keyed by `round`. The `retryPodium(round)` early-returns if `year == 0`. If a user pulls to refresh during the initial `Loading` state, the row-level retry silently no-ops; the row stays in `Loading` until the season resolves. A defensive "show a transient snackbar: 'Schedule still loading'" would be honest.
- `ScheduleViewModelTest.kt` carried 2 stale tests and a `getCircuitImage` parameter on `fakeVm(...)` that exercised a circuit-image code path removed in `ab8a8f0`. The failing `circuit images are fetched for all country-bearing races on warmUp` test and its sibling `per-race circuit image failure degrades to Error, not screen blank` were both removed, along with the unused `imageCalls` instrumentation in the `season failure blanks...` test. Schedule tests now 8/8 green.
- ~~The §2 `ProgressCard` lacks the `progressPercent` as a fraction (`7/24`) — only the percentage. Showing both (`31% · 7 of 24 rounds`) gives the user a concrete frame.~~ Superseded by the P0 §2 resolution. The X/Y fraction was deliberately not adopted on the gauge (rationale + alternative in the P0 block); `scheduledGp` is on the `Season` model and the render is one line away if Maya needs it.
- Edge-to-edge insets: `MainActivity` calls `enableEdgeToEdge()` (per `lode/core/navigation.md`), but `HomepageScreen.kt` and `ScheduleScreen.kt` apply no `WindowInsets.systemBars` or `safeDrawingPadding` on their root `Column`. On a Pixel 7 with gesture nav, the last Past-list row may sit under the system gesture pill; a tap on the last card can land on the bottom nav instead of opening `RoundDetail`.
- No TalkBack-friendly combined content description on §3's `CircuitCard` or the Schedule row. TalkBack reads the visible text in order without indicating it's a single tappable element. The `HorizontalPager` has no semantics for the page count.
- `LIVE` chip on §1 has no live indicator — static red pill, no pulse. A 4dp `Box` with `rememberInfiniteTransition` + `animateFloat` on alpha (1.0 → 0.3 → 1.0, 1.5s) would convey "this is happening right now" within the locked tokens.
- `winnerId`-based Upcoming/Past split (`ScheduleScreen.kt:194, 218`) is a single point of failure. If a completed race has `winner: { id: "x" }` but the DTO maps it to `null` (or vice versa), past rounds silently land in Upcoming and the whole tab is wrong. Add an integration test asserting the filter on a known-past f1api.dev fixture.

---

## Questions to Consider

1. **Should the §1 pager also surface the 5-session weekend schedule as a 4th or 5th card, instead of hiding the data in `weekendSchedule`?** The spec calls for a "next race weekend card" below the fold; the data is already fetched (`HomepageViewModel.kt:73-78`). A 5-row weekend card would close the "when's Quali?" question for the casual fan and use the `SessionTime` model that's currently under-utilized.
2. **Should the P1 chip on the Past podium use `F1Primary` (red) as a background, mirroring the `LIVE` chip on §1?** That would create a single visual rule across the app: red = current/active, red background = the winner. Within the locked tokens and would unify the two screens' identity language.
3. **Is `Constructor` the right caption on §1's Team card for a Drive-to-Survive audience?** The data is `ConstructorStanding` and F1 orthodoxy calls it that, but the term is opaque. Alternative: "My team" (matches the My Team tab) or "Team" (shorter, less precise, more accessible). A glossary call — `terminology.md` could absorb it.
4. **Should the Homepage's primary action be a single "When's the next session?" card at the top, with the pager, season aggregates, and circuit card as scroll-fodder below?** Right now three sections compete; making §1 (or a stripped countdown card) the unambiguous hero would answer the "never miss" principle in one viewport. The data is already there; it's a layout decision, not a data decision.
