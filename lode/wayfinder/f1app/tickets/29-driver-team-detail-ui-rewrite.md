---
id: 29
title: "DriverDetail / TeamDetail UI rewrite"
type: task
status: closed
blocked_by: [27, 28]
owner: agent
closed_by: "Four sub-decisions locked. Compare card (DriverDetail only) = vs teammate per ADR 0013. Tab interaction = `SecondaryTabRow` + `HorizontalPager` (Leaderboard precedent). 'About' section = bottom-placed inline per screenshots. Loading/error states = `OutcomeContent` pattern. Data layer is downstream in build tickets 12 (F1DB catalog) + 13 (Wikipedia REST). Implementation contract: build ticket 14. No new fog: the GAP-F detail-page work is fully ticketed."
---

## Resolution

Closed. The four open sub-decisions are locked:

### 1. Compare card = "vs teammate" (DriverDetail only) — ADR 0013

The Compare card on `DriverScreen` shows the current driver
compared against their teammate — the other driver on the same
constructor (e.g. Antonelli's page shows Russell). The teammate
resolves from the joined `/current/drivers-championship` payload
(filter to same `teamId`, exclude current `driverId`) in
`GetDriverDetailUseCase`. No new screens, no new data sources,
no new use case joins. **DriverDetail only**: `TeamScreen` does
not get a Compare card per the ticket scope. Edge cases (one-car
team, reserve driver, missing standing, no constructor match) are
handled in ADR 0013 — the card hides or shows a dash, never
fails the page.

**Rejected: "vs another driver"** — would require a driver
picker/selection flow, which is the dropped Driver Comparison
screen (per the map's Out of scope section). Resurrecting the
picker via a card on the detail page would re-introduce the same
UX under a different entry point.

### 2. Tab interaction = `SecondaryTabRow` + `HorizontalPager`

Mirrors the existing `LeaderboardScreen` pattern. Two tabs
per screen (current-season / all-time). Tab taps animate the
same pager; swipe gestures also work. The `OutcomeContent`
renderer inside each tab page handles loading / error / content
for that tab's section.

**Rejected: inline tab switcher** — would be inconsistent with
the only existing tab pattern in the codebase, and would lose
swipe-to-switch on small screens.

### 3. "About" section = bottom-placed inline

Confirmed. Per the 5 reference screenshots. The Wikipedia
extract is rendered as a `Card` at the bottom of the
`verticalScroll` Column, with the CC BY-SA 4.0 attribution
line below the extract (clickable link to
`summary.contentUrl`). No "More" affordance, no separate screen.
The attribution is a license requirement per ADR 0012.

### 4. Empty / loading / error states = `OutcomeContent` pattern

Confirmed. No new design code. Each tab page wraps its content
in `OutcomeContent(state = sections.detail, onRetry = refresh)`
the same way `LeaderboardScreen` wraps each standings section.
Loading → centered `CircularProgressIndicator`. Error → message
+ optional retry button. Empty content (e.g. no About for an
obscure driver) → renders nothing in that section; the rest of
the screen is unaffected.

### Out of scope (confirmed by this resolution)

- **TeamDetail Compare card** — team vs team is a different
  product decision (compare against championship neighbor?
  historical rival? no teammate equivalent). Tracked as a
  follow-up if desired; not in this ticket.
- **Force-refresh on the Wikipedia call** — the ~24h cache TTL
  is the user's refresh window per build ticket 13. Pull-to-
  refresh re-runs the use case; the cache hit covers the common
  case (re-open within a day).
- **Per-season standings vs per-season aggregate mismatch** —
  the hero shows the live championship position (f1api.dev);
  the per-season tab shows F1DB per-year totals. Both are
  correct for their column. Documented in build ticket 14 §"Risks".
- **Country flags on DriverDetail** — parked per ticket 13
  (out of scope for the GAP-F detail-page redesign).

### Build ticket

The implementation contract lands in
[`lode/plans/f1app-build/tickets/14-driver-team-detail-ui-rewrite.md`](../../../plans/f1app-build/tickets/14-driver-team-detail-ui-rewrite.md)
("14 — DriverDetail / TeamDetail UI rewrite", `ready`,
`blocked_by: [29]`). The build ticket lists every field row
with its source, the full use case seam change, the
teammate-resolution edge cases, the acceptance numbers (Antonelli
2026, Mercedes 2026, Mercedes all-time), the new `CountryNames`
static map for `baseCountryId` → user-facing name, and the test
plan.

### Map status after this resolution

The GAP-F detail-page work is **fully ticketed and ready for
build**:

- 26 (research) — closed
- 27 (F1DB catalog planning) — closed; produced build 12
- 28 (Wikipedia extension planning) — closed; produced build 13
- 29 (UI rewrite planning) — **closed here**; produces build 14

No new fog. No out-of-scope items shifted into scope. The map's
"Not yet specified" section is empty after this resolution.

## Cross-references (resolution)

- [build ticket 14](../../../plans/f1app-build/tickets/14-driver-team-detail-ui-rewrite.md) —
  implementation contract.
- [ADR 0013](../../../decisions/0013-compare-card-vs-teammate.md) —
  Compare card = vs teammate decision.
- [26 — GAP-F research](../tickets/26-research-gap-f-detail-redesign.md) —
  parent research; closed.
- [27 — F1DB import](../tickets/27-f1db-driver-constructor-import.md) —
  closed; produced build 12.
- [28 — Wikipedia extension](../tickets/28-wikipedia-rest-extension.md) —
  closed; produced build 13.
- [lode/leaderboard/summary.md](../../leaderboard/summary.md) —
  running-code description; "Planned" section is updated when
  build 14 ships.

## Question

Rewrite `feature/driver/DriverScreen.kt` and
`feature/team/TeamScreen.kt` to match the 5 reference screenshots.
The current screens are joined-out minimal surfaces; the rewrite
adds two tabs (current-season / all-time), the new field rows
sourced from F1DB (ticket 27) and Wikipedia (ticket 28), the
"Compare" card, and the "About" section.

## Scope

### DriverDetail
- Tab 1 — 2026 season: position, points, wins, podiums, poles,
  DNFs, top10s, fastest laps, current team.
- Tab 2 — Since debut: all-time GPs (race-only), points, wins,
  podiums, poles, DNFs, top10s, fastest laps, first entry
  (year + team), first win (year + team), world championships.
- "About" section: Wikipedia summary text + CC BY-SA
  attribution line ("Text from Wikipedia, licensed under CC
  BY-SA 4.0").
- "Compare" card: row layout. Open design sub-decision; resolve
  during the task.

### TeamDetail
- Tab 1 — 2026 season: position, points, wins, podiums, poles,
  DNFs, top10s, fastest laps, current drivers.
- Tab 2 — Since debut: all-time GPs (race-only), points, wins,
  podiums, poles, DNFs, top10s, fastest laps, first entry
  (year), first win (year), constructors' titles, drivers'
  titles.
- Chassis, power unit, base country rows (from
  `TeamSeasonalFacts.kt`).
- "About" section: Wikipedia summary text + CC BY-SA
  attribution line.

### Shared
- Headshot / car render via the existing Cloudinary pipeline
  (already wired per cloudinary-headshot-paths.md and
  team-imagery.md).
- Team accent via the existing `TeamColors.forId()` (already
  wired per team-accent.md).
- The 5 reference screenshots are in
  `~/Downloads/Photos-1-001/Screenshot_20260726_130432.jpg`
  and friends — the design spec for this rewrite.
- The catalogs from ticket 27 + the Wikipedia extension from
  ticket 28 are the data sources.
- All-time counts are race-only (sprint rounds filtered) per
  ticket 26.

## Open design sub-decisions

These are small enough to resolve during the task, but listed
here so they don't get lost:

- **Compare card** — what does it compare? Head-to-head vs
  teammate? The screenshots show it; the implementation
  detail is open.
- **Tab interaction** — `SecondaryTabRow` + `HorizontalPager`
  (Leaderboard precedent) vs. inline tab switcher. Pick one
  during the task.
- **"About" section placement** — bottom of the screen, or
  behind a "More" affordance? Screenshots show bottom-placed
  inline. Locked to that.
- **Empty / loading / error states** — follow the existing
  `OutcomeContent` pattern (Leaderboard precedent). No new
  design needed.

## Acceptance

- DriverDetail shows both tabs with the field rows matching
  the Antonelli screenshot.
- TeamDetail shows both tabs with the field rows matching the
  Mercedes screenshot.
- "About" section shows the Wikipedia summary + attribution
  line on both screens.
- Chassis, power unit, base country rows on TeamDetail show
  the F1DB-sourced values (e.g. Mercedes → F1 W17 / Mercedes
  / Germany).
- All-time counts are race-only (sprint rounds filtered) per
  ticket 26.
- Loading and error states render via `OutcomeContent`.

## Out of scope

- The data layer (tickets 27, 28). This ticket consumes their
  output; it does not generate it.
- Driver Comparison screen (per the existing out-of-scope
  rule, dropped by the user).
- Driver Timeline Graph widget (dropped by the user).

## Cross-references

- 5 reference screenshots in
  `~/Downloads/Photos-1-001/Screenshot_20260726_130432.jpg`
  and friends.
- [26 — GAP-F research](../tickets/26-research-gap-f-detail-redesign.md) —
  field inventory and rules.
- [27 — F1DB driver + constructor + team-facts import](../tickets/27-f1db-driver-constructor-import.md) —
  data source for the new fields.
- [28 — Wikipedia REST extension](../tickets/28-wikipedia-rest-extension.md) —
  data source for "About".
- [leaderboard/summary.md](../../leaderboard/summary.md) —
  current DriverDetail/TeamDetail join surface (will be
  superseded by this ticket's use case changes).
- [cloudinary-headshot-paths.md](../../cloudinary-headshot-paths.md)
  + [team-imagery.md](../../team-imagery.md) — already-wired
  imagery.
- [team-accent.md](../../team-accent.md) — already-wired
  `TeamColors.forId`.
- [ADR 0009](../../decisions/0009-remove-openf1-runtime-dependency.md) —
  F1DB is build-time; no new runtime OpenF1 dependency.
