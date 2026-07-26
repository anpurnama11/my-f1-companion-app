---
id: 14
title: DriverDetail / TeamDetail UI rewrite
type: task
status: ready
blocked_by: [29]
owner: ""
---

# 14 — DriverDetail / TeamDetail UI rewrite

**What to build:** per the locked decision in wayfinder ticket 29,
rewrite `feature/driver/DriverScreen.kt` and
`feature/team/TeamScreen.kt` to match the 5 reference screenshots
(`~/Downloads/Photos-1-001/Screenshot_20260726_130432.jpg` and
four friends — Antonelli DriverDetail + Mercedes TeamDetail, both
tabs). The current screens are joined-out minimal surfaces; the
rewrite adds two tabs (current-season / all-time) via
`SecondaryTabRow` + `HorizontalPager` (Leaderboard precedent), the
new field rows from F1DB catalog (build 12), the "About" section
from Wikipedia REST (build 13), and the "Compare" card on
DriverDetail (vs teammate per ADR 0013).

**Concretely:**

- Rewrite `app/src/main/java/com/anpurnama/f1_app/feature/driver/DriverScreen.kt`
  with the two-tab layout and the full field inventory per
  ticket 29 §"DriverDetail".
- Rewrite `app/src/main/java/com/anpurnama/f1_app/feature/team/TeamScreen.kt`
  with the two-tab layout, the full field inventory per ticket 29
  §"TeamDetail", and the chassis / power unit / base country rows
  from `TeamSeasonalFacts.kt`.
- Add the "About" section to both screens (Wikipedia summary +
  CC BY-SA 4.0 attribution line).
- Add the "Compare" card to `DriverScreen` only (vs teammate per
  ADR 0013). `TeamScreen` does not get a Compare card.
- Extend `DriverDetail` and `TeamDetail` model classes in
  `app/src/main/java/com/anpurnama/f1_app/f1/model/NextRace.kt`
  with the new fields per the field inventory below.
- Extend `GetDriverDetailUseCase` and `GetTeamDetailUseCase` in
  `app/src/main/java/com/anpurnama/f1_app/f1/` to:
  - Inject `DriverCatalog` / `ConstructorCatalog` / `TeamSeasonalFacts`
    via the `Wiring` graph (object references, no factory).
  - Inject the `HttpClient` for the Wikipedia call (the same
    client the use case already uses).
  - Resolve the teammate row in `GetDriverDetailUseCase` from the
    joined `/current/drivers-championship` payload (filter to
    same `teamId`, exclude current `driverId`).
  - Resolve the current-drivers list in `GetTeamDetailUseCase`
    from the joined `/current/constructors-championship` entry
    (the `team.drivers` sublist, mapped to the same
    `DriverSummary` shape used elsewhere — name, shortName,
    number, nationality).
  - Call `getWikipediaSummary(titleSlug)` where `titleSlug` is the
    Wikipedia article title segment extracted from the source URL.
    For any full Wikipedia URL, strip everything before `/wiki/`,
    drop query / fragment parts, URL-decode once, and pass only the
    article title segment (for example,
    `https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One` →
    `Mercedes-Benz_in_Formula_One`). Never pass the full URL to
    `getWikipediaSummary`; the extension encodes its argument as one
    path segment for `/page/summary/{title}`.
- Add a small `CountryNames` static map in
  `f1/data/Countries.kt` for the `TeamSeasonalFacts.baseCountryId`
  → user-facing name resolution (F1DB stores the id; the screen
  renders the name). Map shape:
  `"germany" to "Germany"`, `"united-kingdom" to "United Kingdom"`,
  `"italy" to "Italy"`, `"france" to "France"`, etc. One
  per current 2026 constructor.
- Add JVM unit tests for the new use case joins:
  - `GetDriverDetailUseCaseTest` (extends the existing
    `DriverViewModelTest`'s use case tests, OR a new
    `GetDriverDetailUseCaseTest` file if the existing test
    file is view-model-only).
  - `GetTeamDetailUseCaseTest` (same shape).
  - New test: "teammate resolution from joined championship
    payload" (Antonelli → Russell; Hamilton → Leclerc; one-car
    team edge case → no teammate).
  - New test: "Wikipedia summary join" (MockEngine returns a
    `WikipediaSummary`; assert the use case surfaces the
    `extract` and `contentUrl` on `DriverDetail.wikipediaSummary`).
- Add Compose UI tests for both screens (per ticket 14
  testing scope): the Antonelli 2026 numbers, the Mercedes 2026
  facts, the teammate card text, the About attribution line.
  These may live in the existing `DriverViewModelTest` /
  `TeamViewModelTest` files (view-model test path) OR in new
  `DriverScreenTest` / `TeamScreenTest` files under
  `app/src/androidTest/` if the project has settled on
  instrumented Compose tests for the detail screens. Match the
  existing pattern; whichever the rest of the codebase does.
- No new runtime network calls beyond the one Wikipedia fetch
  per detail open (HttpCache covers re-opens). No new top-level
  Ktor base URLs. No new dependencies in `app/build.gradle.kts`.
- The two screens preserve the existing `pullToRefresh` shape
  (re-runs the use case; same `forceRefresh: Boolean = false`
  flag the use case already accepts). The Wikipedia call does
  NOT honor force-refresh (the ~24h cache TTL is the user's
  refresh window per build ticket 13).

**Blocked by:** 29 (wayfinder planning ticket — closed in the
resolution that produced this build ticket).

**Status:** ready

## Field inventory

### DriverDetail — Tab 1 (2026 season)

| Field | Source | Notes |
|---|---|---|
| Headshot (hero) | `driverImageUrl(name, surname, teamId, currentSeasonYear())` | Already wired (build 08) |
| Name (large) | `DriverDetail.name` | Already wired |
| Short name (3-letter) | `DriverDetail.shortName` | Already wired |
| Car number | `DriverDetail.number` | Already wired |
| Current team (tappable → TeamDetail) | `DriverDetail.teamName` + `teamId` | Already wired (existing clickable) |
| "2026 season" subtitle | `currentSeasonYear()` constant | Already wired |
| Position (P2) | `DriverDetail.standing.position` | Already wired |
| Points (198) | `DriverDetail.standing.points` | Already wired |
| Wins (6) | `DriverDetail.standing.wins` | Already wired |
| **Podiums (8)** | `DriverCatalog.forId(driverId).bySeason[2026].podiums` | NEW — F1DB catalog (build 12) |
| **Poles (6)** | `DriverCatalog.forId(driverId).bySeason[2026].poles` | NEW — F1DB catalog (build 12) |
| **DNFs (1)** | `DriverCatalog.forId(driverId).bySeason[2026].dnfs` | NEW — F1DB catalog (build 12) |
| **Top 10s** | `DriverCatalog.forId(driverId).bySeason[2026].top10s` | NEW — F1DB catalog (build 12) |
| **Fastest laps** | `DriverCatalog.forId(driverId).bySeason[2026].fastestLaps` | NEW — F1DB catalog (build 12) |
| Date of birth | `DriverDetail.birthday` | Already wired (small text) |
| Country (Italy) | `DriverDetail.nationality` | Already wired |

### DriverDetail — Tab 2 (Since Debut)

| Field | Source | Notes |
|---|---|---|
| "Since Debut {firstYear} – {lastYear}" subtitle | `DriverCatalog.forId(driverId).allTime.firstSeasonYear` / `lastSeasonYear` | NEW — F1DB catalog |
| **Grands Prix (34, race-only)** | `DriverCatalog.forId(driverId).allTime.raceEntries` | NEW — F1DB catalog; race-only (sprint filtered per ADR 0012) |
| Points (354) | `DriverCatalog.forId(driverId).allTime.points` | NEW — F1DB catalog |
| Wins (6) | `DriverCatalog.forId(driverId).allTime.raceWins` | NEW — F1DB catalog |
| Podiums (11) | `DriverCatalog.forId(driverId).allTime.podiums` | NEW — F1DB catalog |
| Poles (6) | `DriverCatalog.forId(driverId).allTime.polePositions` | NEW — F1DB catalog |
| DNFs | `DriverCatalog.forId(driverId).allTime.dnfs` | NEW — F1DB catalog |
| Top 10s | `DriverCatalog.forId(driverId).allTime.top10s` | NEW — F1DB catalog |
| Fastest laps | `DriverCatalog.forId(driverId).allTime.fastestLaps` | NEW — F1DB catalog |
| **First Entry** ({year} {team} {gpName}) | `DriverCatalog.forId(driverId).allTime.firstEntryRaceName` + `firstSeasonYear` + `teamName` | NEW — F1DB catalog |
| **First Win** ({year} {team} {gpName}) | `DriverCatalog.forId(driverId).allTime.firstWinRaceName` + `firstSeasonYear` + `teamName` | NEW — F1DB catalog |
| **World Championships (0)** | `DriverCatalog.forId(driverId).allTime.championshipWins` | NEW — F1DB catalog |

### DriverDetail — Compare card (driver-only per ADR 0013)

| Field | Source | Notes |
|---|---|---|
| Card title ("Compare") | Static | |
| Teammate name (e.g. "George Russell") | `DriverDetail.teammate.name` (resolved from joined `/current/drivers-championship`) | NEW |
| Teammate standing (e.g. "P2 · 198 pts") | `DriverDetail.teammate.position` + `points` | NEW; dash if `null` |

The card is a single row below the main content (between the
standings block and the "About" section). When the teammate is
absent (one-car team, mid-season swap, missing standing), the
card hides — the screen's content is unaffected (per ADR 0013
edge cases).

### DriverDetail — "About" section (bottom-placed inline)

| Field | Source | Notes |
|---|---|---|
| Section title ("About") | Static | |
| Wikipedia extract | `DriverDetail.wikipediaSummary.extract` | NEW — Wikipedia REST (build 13) |
| Attribution line | `"From Wikipedia, the free encyclopedia, under CC BY-SA 4.0"` + clickable `summary.contentUrl` | NEW — license requirement per ADR 0012 |

### TeamDetail — Tab 1 (2026 season)

| Field | Source | Notes |
|---|---|---|
| Car render (hero) | `teamImageUrl(teamId, currentSeasonYear())` | Already wired (build 08) |
| Wordmark (Mercedes) | `TeamDetail.wordmark` | Already wired |
| Country (Germany) | `TeamDetail.country` (small text below wordmark) | Already wired |
| "2026 season" subtitle | `currentSeasonYear()` constant | Already wired |
| **Chassis (F1 W17)** | `TeamSeasonalFacts.forId(teamId).chassis` | NEW — F1DB YAML (build 12) |
| **Power Unit (Mercedes)** | `TeamSeasonalFacts.forId(teamId).powerUnit` | NEW — F1DB YAML (build 12) |
| **Base country (Germany)** | `CountryNames[TeamSeasonalFacts.forId(teamId).baseCountryId]` | NEW — F1DB YAML + small static map |
| Position (P1) | `TeamDetail.standing.position` | Already wired |
| Points (354) | `TeamDetail.standing.points` | Already wired |
| Wins (6) | `TeamDetail.standing.wins` | Already wired |
| **Podiums (13)** | `ConstructorCatalog.forId(constructorId).bySeason[2026].podiums` | NEW — F1DB catalog (build 12) |
| **Poles (10)** | `ConstructorCatalog.forId(constructorId).bySeason[2026].poles` | NEW — F1DB catalog (build 12) |
| **DNFs (0)** | `ConstructorCatalog.forId(constructorId).bySeason[2026].dnfs` | NEW — F1DB catalog (build 12) |
| **Top 10s** | `ConstructorCatalog.forId(constructorId).bySeason[2026].top10s` | NEW — F1DB catalog (build 12) |
| **Fastest laps** | `ConstructorCatalog.forId(constructorId).bySeason[2026].fastestLaps` | NEW — F1DB catalog (build 12) |
| **Current drivers (Russell · #63, Antonelli · #12)** | `TeamDetail.currentDrivers` (resolved from joined `/current/constructors-championship` `team.drivers` sublist) | NEW |
| Constructors' titles (8) | `TeamDetail.constructorsChampionships` | Already wired |
| Drivers' titles (9) | `TeamDetail.driversChampionships` | Already wired |

### TeamDetail — Tab 2 (Since Debut)

| Field | Source | Notes |
|---|---|---|
| "Since Debut {firstYear} – {lastYear}" subtitle | `ConstructorCatalog.forId(constructorId).allTime.firstSeasonYear` / `lastSeasonYear` | NEW — F1DB catalog |
| **Grands Prix (339, race-only)** | `ConstructorCatalog.forId(constructorId).allTime.raceEntries` | NEW — F1DB catalog; race-only |
| Points (8517.5) | `ConstructorCatalog.forId(constructorId).allTime.points` | NEW — F1DB catalog |
| Wins (130) | `ConstructorCatalog.forId(constructorId).allTime.raceWins` | NEW — F1DB catalog |
| Podiums (211) | `ConstructorCatalog.forId(constructorId).allTime.podiums` | NEW — F1DB catalog |
| Poles (146) | `ConstructorCatalog.forId(constructorId).allTime.polePositions` | NEW — F1DB catalog |
| DNFs | `ConstructorCatalog.forId(constructorId).allTime.dnfs` | NEW — F1DB catalog |
| Top 10s | `ConstructorCatalog.forId(constructorId).allTime.top10s` | NEW — F1DB catalog |
| Fastest laps (112) | `ConstructorCatalog.forId(constructorId).allTime.fastestLaps` | NEW — F1DB catalog |
| **First Entry (1954 French GP)** | `ConstructorCatalog.forId(constructorId).allTime.firstEntryRaceName` + `firstSeasonYear` | NEW — F1DB catalog |
| **First Win (1954 French GP)** | `ConstructorCatalog.forId(constructorId).allTime.firstWinRaceName` + `firstSeasonYear` | NEW — F1DB catalog |
| Constructors' titles (8) | `TeamDetail.constructorsChampionships` | Already wired |
| Drivers' titles (9) | `TeamDetail.driversChampionships` | Already wired |

### TeamDetail — "About" section (bottom-placed inline)

Same shape as DriverDetail: Wikipedia extract + CC BY-SA
attribution line. TeamDetail does NOT get a Compare card (per
ADR 0013 §"Considered options").

## Use case seam change

`DriverDetail` and `TeamDetail` model classes gain the new
fields. The use case constructor signatures gain the catalogs
and the `HttpClient` (already injected for the f1api.dev
calls). The wiring graph is:

```kotlin
// core/di/Wiring.kt — additions for build ticket 14
val getDriverDetail: GetDriverDetailUseCase = GetDriverDetailUseCase(
    client = httpClient,
    driverCatalog = DriverCatalog,
    teamFacts = TeamSeasonalFacts,
)

val getTeamDetail: GetTeamDetailUseCase = GetTeamDetailUseCase(
    client = httpClient,
    constructorCatalog = ConstructorCatalog,
    teamFacts = TeamSeasonalFacts,
)
```

The `DriverCatalog`, `ConstructorCatalog`, and `TeamSeasonalFacts`
references are the `object` declarations themselves (singletons
by definition), not factory calls — same shape as the
`CircuitArtwork` and `TeamColors` objects already on the
`Wiring` graph.

The new model fields on `DriverDetail` / `TeamDetail` are
non-nullable where the source always has a value (e.g.
`championshipWins: Int` is always 0+ for every F1 driver, never
`null`); nullable only where the source is genuinely
optional (e.g. `firstEntryRaceName: String?` is `null` if the
driver has no race-results rows, which would only happen for a
brand-new F1 driver with no races yet). The acceptance tests
pin which fields are nullable.

## Acceptance

- [ ] `DriverScreen` shows both tabs with the field rows matching
      the Antonelli DriverDetail screenshot:
      - Tab 1: position, points, wins, podiums (8), poles (6),
        DNFs (1), top10s, fastest laps, current team
      - Tab 2: race-only GPs, points, wins, podiums, poles, DNFs,
        top10s, fastest laps, first entry (2025 Australian GP),
        first win (2026 Chinese GP), world championships (0)
- [ ] `TeamScreen` shows both tabs with the field rows matching
      the Mercedes TeamDetail screenshot:
      - Tab 1: chassis (F1 W17), power unit (Mercedes), base
        country (Germany), position, points, wins, podiums (13),
        poles (10), DNFs (0), top10s, fastest laps, current
        drivers (Russell, Antonelli)
      - Tab 2: race-only GPs (339), points (8517.5), wins (130),
        podiums (211), poles (146), DNFs, top10s, fastest laps
        (112), first entry (1954 French GP), first win
        (1954 French GP), constructors' titles (8), drivers'
        titles (9)
- [ ] "About" section on both screens shows the Wikipedia extract
      + the CC BY-SA 4.0 attribution line with a clickable link
      to `summary.contentUrl`
- [ ] Compare card on `DriverScreen` shows the teammate name +
      standing (e.g. "George Russell — P2 · 198 pts" on
      Antonelli's page). Hides when no teammate resolves (per
      ADR 0013 edge cases)
- [ ] All-time counts are race-only (sprint rounds filtered) per
      ADR 0012 — the Antonelli / Mercedes acceptance numbers are
      the gate
- [ ] Loading and error states render via the existing
      `OutcomeContent` pattern — no new design code
- [ ] `pullToRefresh` re-runs the use case (same `forceRefresh`
      flag the use case already accepts)
- [ ] Tab interaction uses `SecondaryTabRow` + `HorizontalPager`
      (Leaderboard precedent)
- [ ] No new top-level F1DB / Wikipedia / Jolpica runtime calls
      beyond what's already on the use case + the one Wikipedia
      fetch per detail open
- [ ] No new dependencies in `app/build.gradle.kts`
- [ ] No `android.*` imports in any new file in `f1/` (domain-
      purity invariant per practices.md)
- [ ] `lode/leaderboard/summary.md` "Planned: GAP-F detail-page
      redesign" section is updated to mark this ticket shipped
- [ ] `lode/leaderboard/summary.md` "Planned" mermaid diagram is
      updated: the DriverDetail → DriverCatalog + TeamSeasonalFacts
      + Wikipedia edges become live (currently labelled
      upstream-only)
- [ ] `./gradlew :app:compileDebugKotlin` and
      `./gradlew :app:testDebugUnitTest` both green

## Done when (summary checklist)

The full checklist; the acceptance section above is the gate.

- [ ] `DriverScreen` rewritten to match the field inventory above
- [ ] `TeamScreen` rewritten to match the field inventory above
- [ ] `DriverDetail` model gains the new fields (per the
      inventory)
- [ ] `TeamDetail` model gains the new fields (per the
      inventory)
- [ ] `GetDriverDetailUseCase` joins: `DriverCatalog` per-season
      + all-time, `TeamSeasonalFacts` (for the constructor), the
      Wikipedia summary, the teammate row
- [ ] `GetTeamDetailUseCase` joins: `ConstructorCatalog`
      per-season + all-time, `TeamSeasonalFacts` (chassis / power
      unit / base country), the current-drivers list, the
      Wikipedia summary
- [ ] `CountryNames` static map at
      `app/src/main/java/com/anpurnama/f1_app/f1/data/Countries.kt`
      — F1DB `baseCountryId` → user-facing name (10–15 entries
      for the 2026 constructors; the map is small enough to
      hand-write; one entry per constructor, not per F1DB
      country id globally)
- [ ] JVM unit tests for the new use case joins: teammate
      resolution, Wikipedia summary join, missing-teammate edge
      case, missing-Wikipedia-summary edge case (treats as
      no-About-section rather than an error)
- [ ] Compose UI tests for the Antonelli 2026 / Mercedes 2026
      acceptance numbers (per the existing testing scope)
- [ ] `Wiring` exposes the three catalog objects as `val`
      properties (already done in build 12, but verify the use
      case actually consumes them now)
- [ ] `THIRD_PARTY_NOTICES.md` retained — F1DB CC BY 4.0 and
      Wikipedia CC BY-SA 4.0 attributions are already on the
      catalog files + the Wikipedia API; no new entries needed
- [ ] `lode/leaderboard/summary.md` "Planned" section is
      updated to mark this ticket shipped (the new edges go
      live; the upstream-only labels drop)
- [ ] `./gradlew :app:compileDebugKotlin` and
      `./gradlew :app:testDebugUnitTest` both green

## Risks

- **Field row layout** is the largest design decision this
  ticket makes beyond the locked sub-decisions. The screenshots
  show the row order (position, points, wins, podiums, poles,
  DNFs, top10s, fastest laps — the order on the existing
  Leaderboard rows). The ticket is explicit: match the
  screenshot. If a future pass wants a different order, that's
  a UI tweak, not a planning change.
- **Per-season standings vs per-season aggregate mismatch**:
  `DriverDetail.standing` (from f1api.dev) shows the live
  championship position; `DriverCatalog.bySeason[2026].points`
  (from F1DB) shows the per-season points total. These can
  differ mid-season (championship is cumulative; per-season is
  per-year). The screen renders the per-season tab using F1DB
  numbers throughout; the standing row in the hero is f1api.dev.
  Both are correct for their column.
- **Wikipedia "About" section can be empty for obscure
  drivers**: the section renders nothing (no card, no "No
  description available" placeholder) when
  `wikipediaSummary` is `null`. This is the natural failure
  mode; the acceptance test pins it.
- **Mid-season teammate swap**: the "teammate" is whoever the
  championship payload lists as the second row on the same
  `teamId`. If a driver swap happens mid-season, the new
  teammate replaces the old one in the cache. The card reflects
  the joined payload, not the year-to-date season entry. This
  matches the f1api.dev / Wikipedia single-source-of-truth
  pattern used elsewhere.

## Invariants

- All-time counts are race-only (sprint filtered) per ADR 0012.
  The script's race-only filter is the source of the catalog
  values; the screen does not re-filter.
- F1DB is build-time, not runtime. No new F1DB runtime calls.
  Wikipedia is the only new runtime source (build 13).
- The Compare card is a driver-only feature. TeamDetail has no
  Compare card. ADR 0013.
- The "About" section is bottom-placed inline on both screens.
  No "More" affordance; no separate screen.
- Loading and error states use the shared `OutcomeContent`
  pattern; no per-screen ad-hoc rendering.
- Tab interaction uses `SecondaryTabRow` + `HorizontalPager`
  (Leaderboard precedent). The `OutcomeContent` inside each
  tab page renders loading / error / content for that tab's
  section.
- The use case contracts are unchanged in shape
  (`Outcome<DriverDetail>` / `Outcome<TeamDetail>`); only the
  model and the constructor signature grow.

## Out of scope for this ticket

- The data layer (F1DB catalog from build 12, Wikipedia
  extension from build 13). Both are shipped; this ticket
  consumes them. No new data sources.
- Driver Comparison screen (dropped by the user; map's Out of
  scope). The Compare card is the static "vs teammate" row,
  not a new comparison surface.
- Driver Timeline Graph widget (dropped by the user; map's
  Out of scope).
- Bar chart (per ADR 0012 — dropped; no free source).
- Base city, team principal (per ADR 0012 — dropped; no free
  source). The screen shows base country only.
- TeamDetail Compare card (per ADR 0013 — out of scope for
  this ADR; team vs team is a different product decision).
- Country flags (parked per
  [`lode/wayfinder/f1app/tickets/13-additive-ui-enrichments.md`](../../../wayfinder/f1app/tickets/13-additive-ui-enrichments.md)
  — out of scope).
- Detailed season-entry history (which teams a driver raced
  for, year by year — F1DB has it; parked per ticket 26).
- Multi-language Wikipedia (English only per build 13).
- Wikipedia force-refresh flag (per build 13: the ~24h cache
  TTL is the user's refresh window).

## Cross-references

- Wayfinder 29:
  [`lode/wayfinder/f1app/tickets/29-driver-team-detail-ui-rewrite.md`](../../../wayfinder/f1app/tickets/29-driver-team-detail-ui-rewrite.md) —
  planning ticket; closed in the resolution that produced this
  build ticket. Locks the four sub-decisions.
- Wayfinder 26:
  [`lode/wayfinder/f1app/tickets/26-research-gap-f-detail-redesign.md`](../../../wayfinder/f1app/tickets/26-research-gap-f-detail-redesign.md) —
  parent research; closed.
- Wayfinder 27:
  [`lode/wayfinder/f1app/tickets/27-f1db-driver-constructor-import.md`](../../../wayfinder/f1app/tickets/27-f1db-driver-constructor-import.md) —
  planning ticket for the F1DB catalog; closed; produced build
  ticket 12.
- Wayfinder 28:
  [`lode/wayfinder/f1app/tickets/28-wikipedia-rest-extension.md`](../../../wayfinder/f1app/tickets/28-wikipedia-rest-extension.md) —
  planning ticket for the Wikipedia extension; closed; produced
  build ticket 13.
- Research:
  [`lode/wayfinder/f1app/driver-team-detail.md`](../../../wayfinder/f1app/driver-team-detail.md) +
  [`lode/wayfinder/f1app/driver-team-detail-api-wrangling.md`](../../../wayfinder/f1app/driver-team-detail-api-wrangling.md) —
  field inventory + per-source payload shapes + computed
  Antonelli/Mercedes checks.
- ADR 0012:
  [`lode/decisions/0012-gap-f-detail-page-data-sources.md`](../../../decisions/0012-gap-f-detail-page-data-sources.md) —
  the data source split (F1DB build-time + Wikipedia REST +
  f1api.dev runtime). This ticket consumes all three.
- ADR 0013:
  [`lode/decisions/0013-compare-card-vs-teammate.md`](../../../decisions/0013-compare-card-vs-teammate.md) —
  Compare card = vs teammate. This ticket implements the
  decision.
- ADR 0009:
  [`lode/decisions/0009-remove-openf1-runtime-dependency.md`](../../../decisions/0009-remove-openf1-runtime-dependency.md) —
  F1DB is build-time. This ticket preserves the invariant.
- ADR 0002:
  [`lode/decisions/0002-sectionuistate-is-vm-to-ui-transport.md`](../../../decisions/0002-sectionuistate-is-vm-to-ui-transport.md) —
  `SectionUiState` is VM→UI transport; `OutcomeContent` is the
  shared renderer. The new tabs use the same pattern.
- Build 12:
  [`12-f1db-driver-constructor-catalog-import.md`](12-f1db-driver-constructor-catalog-import.md) —
  F1DB catalog import (shipped). Consumes the three
  generated `object` catalogs.
- Build 13:
  [`13-wikipedia-rest-extension.md`](13-wikipedia-rest-extension.md) —
  Wikipedia REST extension (shipped). Consumes the
  `getWikipediaSummary` extension.
- Build 11:
  [`11-favorites-on-homepage.md`](11-favorites-on-homepage.md) —
  reference for the ticket shape (`blocked_by` frontmatter,
  "per the locked decision in wayfinder ticket N" opener,
  build-as-code structure).
- Imagery:
  [`lode/wayfinder/f1app/cloudinary-headshot-paths.md`](../../../wayfinder/f1app/cloudinary-headshot-paths.md) +
  [`lode/wayfinder/f1app/team-imagery.md`](../../../wayfinder/f1app/team-imagery.md) —
  already-wired headshot + car-render paths.
- Team accent:
  [`lode/wayfinder/f1app/team-accent.md`](../../../wayfinder/f1app/team-accent.md) —
  already-wired `TeamColors.forId()` map.
- Current `DriverScreen`:
  [`app/src/main/java/com/anpurnama/f1_app/feature/driver/DriverScreen.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/feature/driver/DriverScreen.kt) —
  the file this ticket rewrites.
- Current `TeamScreen`:
  [`app/src/main/java/com/anpurnama/f1_app/feature/team/TeamScreen.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/feature/team/TeamScreen.kt) —
  the file this ticket rewrites.
- Leaderboard `SecondaryTabRow` + `HorizontalPager` precedent:
  [`app/src/main/java/com/anpurnama/f1_app/feature/leaderboard/LeaderboardScreen.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/feature/leaderboard/LeaderboardScreen.kt) —
  the pattern to mirror for the two new tab interactions.
- Pattern reference for `OutcomeContent`:
  [`app/src/main/java/com/anpurnama/f1_app/core/ui/OutcomeContent.kt`](../../../../app/src/main/java/com/anpurnama/f1_app/core/ui/OutcomeContent.kt) —
  shared loading / error / content renderer; no per-screen
  ad-hoc rendering.
- 5 reference screenshots at
  `~/Downloads/Photos-1-001/Screenshot_20260726_130432.jpg`
  and four friends (Antonelli DriverDetail both tabs + Mercedes
  TeamDetail both tabs + 1 context shot).
