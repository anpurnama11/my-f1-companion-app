> **Historical research — archived.** Current decisions live in
> [`decisions/`](../../decisions/) and build specs live in [`plans/`](../../plans/).

# Driver & Team detail — data sources for the new detail pages (GAP-F)

Research output for the DriverDetail/TeamDetail redesign (currently a
join-only surface per [`lode/leaderboard/summary.md`](../leaderboard/summary.md)).
The screenshots show two tabs per page (current-season + all-time) plus
new fields: first entry / first win, world championships, chassis, power
unit, base (country), and an "About" biography. This file is
the **what and why**; the per-source probes, payload shapes, and live
counts live in
[driver-team-detail-api-wrangling.md](driver-team-detail-api-wrangling.md).

## TL;DR

**Every field on the new detail pages can be served by free sources.**
No new paid APIs, no scraping. The split is:

| Need | Source | Cost |
|---|---|---|
| 2026 season tab — pos/points/wins | f1api.dev `/current/{drivers,constructors}-championship` (already wired) | 0 |
| 2026 season tab — podiums / poles / DNFs / top10s | **F1DB build-time** (per-season totals in `seasons-{drivers,constructors}.json`) | 0 |
| All-time tab — GPs / points / wins / podiums / poles / fastest-laps | **F1DB build-time** (career totals in `{drivers,constructors}.json`) | 0 |
| All-time tab — DNFs / top10s | **F1DB build-time aggregation** from `races-race-results.json` (DNF = `reasonRetired != null`; top10 = `positionNumber <= 10`) | 0 |
| First entry / first win (driver) | **F1DB build-time** — first row of `races-race-results.json` sorted by (year, round); first win = first row where `positionText == "1"` | 0 |
| First entry / first win (team) | **F1DB build-time** — same shape on `races-race-results.json` joined by `constructorId` | 0 |
| World Championships (driver) | **F1DB build-time** — `totalChampionshipWins` on the driver row, OR count `positionNumber == 1` in `seasons-driver-standings.json` | 0 |
| World Championships (team) | **F1DB build-time** — `totalChampionshipWins` on the constructor row, OR count `positionNumber == 1` in `seasons-constructors.json` (note: this field is named `positionText`/`positionNumber`; the `championshipWon` boolean in `races-*-standings.json` is the per-round flag) | 0 |
| Date of birth / country | f1api.dev `/current/{drivers,teams}` (already wired) | 0 |
| Driver code (3-letter) / car number | f1api.dev `shortName` + `number` (already wired) | 0 |
| Current team / current drivers | f1api.dev `/current/drivers` + `/current/teams` (already wired) | 0 |
| First appearance year / constructors' titles / drivers' titles (team) | f1api.dev `/current/teams` (already wired) | 0 |
| "About" / biography | **Wikipedia REST API** `GET /api/rest_v1/page/summary/{title}` — URL extracted from f1api.dev `url` or Jolpica `url` (both link to the same Wikipedia article). CC BY-SA 4.0 — attribution required | **1 call per detail open** (HttpCache covers re-opens) |
| Headshots / car render | formula1.com Cloudinary (already wired per [cloudinary-headshot-paths.md](cloudinary-headshot-paths.md) + [team-imagery.md](team-imagery.md)) | 0 |
| Team accent | `TeamColors.forId()` hardcoded map (already wired per [team-accent.md](team-accent.md)) | 0 |
| **Current chassis (F1 W17)** | **F1DB YAML** `seasons/{year}/entrants.yml` → `chassisId` → `chassis.yml` `name` field (e.g. `mercedes-f1-w17` → `"F1 W17"`). All 11 2026 teams covered, updated weekly by F1DB | 0 (build-time) |
| **Power unit** | **F1DB YAML** `seasons/{year}/entrants.yml` → `engineManufacturerId` → `engine-manufacturers.yml` `name` field (e.g. `mercedes`). All 11 2026 teams covered | 0 (build-time) |
| **Base country** | **F1DB YAML** `seasons/{year}/entrants.yml` → `countryId` (e.g. `germany`, `italy`, `united-kingdom`). All 11 2026 teams covered | 0 (build-time) |

The two new third-party data sources are: **F1DB build-time** (already
used for circuit artwork; one new script to import driver/constructor
JSONs + the YAML-based team facts) and **Wikipedia REST API** (one
new `HttpClient` extension).

The Base field in the new screen shows **country only** (e.g.
"Germany", "United Kingdom", "Italy") — city-level data was
considered but dropped per user decision. The Team Principal field
was also dropped. Both have no free JSON source and would have
required either a hardcoded map or scraping HTML wikis.

## Field inventory

### DriverDetail — page 1 (2026 season tab)

| Field | Source today | Source recommended | Status |
|---|---|---|---|
| Name + surname | f1api.dev | f1api.dev | Wired |
| "2026 season" subtitle | `currentSeasonYear()` constant | `currentSeasonYear()` | Wired |
| Position | f1api.dev `/current/drivers-championship` | f1api.dev | Wired |
| Points | f1api.dev `/current/drivers-championship` | f1api.dev | Wired |
| Wins | f1api.dev `/current/drivers-championship` | f1api.dev | Wired |
| **Podiums (8)** | f1api.dev doesn't expose | **F1DB** `seasons-drivers.json` → `totalPodiums` | New — F1DB import |
| **Poles (6)** | f1api.dev doesn't expose | **F1DB** `seasons-drivers.json` → `totalPolePositions` | New — F1DB import |
| **DNFs (1)** | f1api.dev doesn't expose | **F1DB aggregation** from `races-race-results.json` (count `reasonRetired != null`, season-filtered) | New — F1DB import |
| Driver code (ANT) | f1api.dev `shortName` | f1api.dev | Wired |
| Team name (Mercedes-AMG Petronas F1 Team) | f1api.dev | f1api.dev | Wired |
| **First Entry (2025 Australian GP)** | f1api.dev doesn't expose | **F1DB aggregation** — first row of `races-race-results.json` for this `driverId`, sorted by `(year, round)` | New — F1DB import |
| **First Win (2026 Chinese GP)** | f1api.dev doesn't expose | **F1DB aggregation** — first row where `positionText == "1"` | New — F1DB import |
| **World Championships (0)** | f1api.dev doesn't expose | **F1DB** `drivers.json` → `totalChampionshipWins` | New — F1DB import |
| Date of birth | f1api.dev `birthday` | f1api.dev | Wired |
| Country (Italy) | f1api.dev `nationality` + Jolpica 2-letter code lookup | f1api.dev + Jolpica | Wired (flag TBD) |
| **About text** | No source | **Wikipedia REST** `/page/summary/{title}` | New — Wikipedia import |

### DriverDetail — page 2 (Since Debut / all-time tab)

| Field | Source recommended | Computed from |
|---|---|---|
| "Since Debut 2025 - 2026" subtitle | F1DB | First/last `year` for this driver in `seasons-drivers.json` |
| GPs (34) | F1DB | `drivers.json` → `totalRaceEntries` (note: F1DB counts race+qualifying entries, not sprint; see "Caveats") |
| Points (354) | F1DB | `drivers.json` → `totalPoints` |
| Wins (6) | F1DB | `drivers.json` → `totalRaceWins` |
| Podiums (11) | F1DB | `drivers.json` → `totalPodiums` |
| Poles (6) | F1DB | `drivers.json` → `totalPolePositions` |
| **Top 10s (6)** | f1api.dev doesn't expose | **F1DB aggregation** from `races-race-results.json` (count `positionNumber <= 10`) |

### TeamDetail — page 1 (2026 season tab)

| Field | Source today | Source recommended | Status |
|---|---|---|---|
| Wordmark (Mercedes) | f1api.dev `teamName` | f1api.dev | Wired |
| Chassis label (F1 W17) | No source | **F1DB YAML** `seasons/2026/entrants.yml` → `chassisId` → `chassis.yml` `name` field | New — F1DB import |
| "2026 season" subtitle | `currentSeasonYear()` | `currentSeasonYear()` | Wired |
| Position | f1api.dev | f1api.dev | Wired |
| Points | f1api.dev | f1api.dev | Wired |
| Wins | f1api.dev | f1api.dev | Wired |
| **Podiums (13)** | f1api.dev doesn't expose | **F1DB** `seasons-constructors.json` → `totalPodiums` | New — F1DB import |
| **Poles (10)** | f1api.dev doesn't expose | **F1DB** `seasons-constructors.json` → `totalPolePositions` | New — F1DB import |
| **DNFs (0)** | f1api.dev doesn't expose | **F1DB aggregation** from `races-race-results.json` (count `reasonRetired != null` for `constructorId` rows) | New — F1DB import |
| **Chassis (F1 W17)** | No source | **F1DB YAML** `seasons/{year}/entrants.yml` → `chassisId` | New — F1DB import |
| **Power Unit (Mercedes)** | No source | **F1DB YAML** `seasons/{year}/entrants.yml` → `engineManufacturerId` → engine-manufacturer `name` | New — F1DB import |
| **First Entry (1954 French GP)** | f1api.dev has `firstAppeareance: 1954` (year only) | **F1DB** — first row of `races-race-results.json` for this `constructorId`, joined with `grands-prix.json` for the race name | New — F1DB import |
| Constructors' titles (8) | f1api.dev `constructorsChampionships` | f1api.dev | Wired |
| Drivers' titles (9) | f1api.dev `driversChampionships` | f1api.dev | Wired |
| **Base country (Germany)** | No source | **F1DB YAML** `countryId` (country only; city and team principal dropped per user decision) | New — F1DB import |
| Current drivers (Russell, Antonelli) | f1api.dev `/current/drivers` (already joined in the detail use case) | f1api.dev | Wired |
| Driver 3-letter codes (RUS, ANT) + car numbers (63, 12) | f1api.dev `shortName` + `number` | f1api.dev | Wired |
| **About text** | No source | **Wikipedia REST** | New — Wikipedia import |

### TeamDetail — page 2 (Since Debut / all-time tab)

| Field | Source recommended | Computed from |
|---|---|---|
| "Since Debut 1954 - 2026" subtitle | F1DB | First/last `year` for this constructor in `seasons-constructors.json` |
| GPs (339) | F1DB | `constructors.json` → `totalRaceEntries` |
| Points (8517.5) | F1DB | `constructors.json` → `totalPoints` |
| Wins (130) | F1DB | `constructors.json` → `totalRaceWins` |
| Podiums (211) | F1DB | `constructors.json` → `totalPodiums` |
| Poles (146) | F1DB | `constructors.json` → `totalPolePositions` |
| **Fastest Laps (112)** | f1api.dev doesn't expose | **F1DB** `constructors.json` → `totalFastestLaps` |

## What this rules in / out

### Rules IN

- **F1DB is the cleanest source for the new all-time + per-season
  stats** (podiums, poles, fastest laps, world championships). No
  free runtime API serves these as pre-aggregated fields; computing
  from Jolpica is possible but is strictly more network calls for
  the same answer. F1DB is build-time data (per the existing
  `lode/wayfinder/f1app/f1db-data.md` precedent for circuit artwork).
- **F1DB's YAML source (`seasons/{year}/entrants.yml`) covers all
  three remaining team-facts fields (chassis, power unit, base
  country) for all 11 2026 teams** — the `chassisId` field
  resolves to the human-readable name (e.g. `mercedes-f1-w17` →
  `"F1 W17"`) via the `chassis.yml` files. Updated by the F1DB
  community, structured YAML, all 11 teams covered.
- **Wikipedia REST `/page/summary/{title}` is the "About" source.**
  The `url` field already on f1api.dev driver and team rows is the
  Wikipedia article URL — extract the slug, call the REST API once
  per detail open, cache via the existing Ktor `HttpCache` plugin
  (10 MB file cache, `max-stale` for offline cold launch). CC BY-SA
  4.0 with attribution (Wikipedia URL link in the "About" section,
  same as the existing Wikipedia-sourced `lapRecord` citation on
  Circuit Detail). The summary text is the same shape as the
  screenshots — clean editorial paragraph, not raw wikitext.

### Rules OUT

- **No paid F1 API.** Every field above has a free source. The
  pre-existing project rule ("if not covered by a free API, it's not
  built") is preserved.
- **No Wikipedia HTML scraping.** The REST summary endpoint is the
  contract; parsing wikitext is fragile and unnecessary.
- **No F1 Fandom Wiki / Liquipedia scraping.** Both wikis have
  team-level data (city, principal) but only as HTML — Fandom is
  Cloudflare-protected, Liquipedia has no JSON API. Not worth
  scraping for 11 fields, and the design dropped both.
- **No base city, no team principal on the new screen.** Both
  dropped per user decision — only base country (from F1DB YAML)
  remains.
- **No OpenF1 join** (re-confirming ADR 0009). F1DB build-time
  covers what we need; no live-window dependency.
- **No new Ktor base URL for Jolpica alpha** for `primary_color` /
  biographical fields — the existing Jolpica standard `/drivers/{id}/`
  + `/constructors/{id}/` already returns the Wikipedia URL,
  nationality, and date of birth. Jolpica alpha would add a new base
  URL for strictly less data; the `primary_color` field is parked per
  the [team-accent.md](team-accent.md) plan.
- **No TheSportsDB runtime call** — it has 10/11 teams (no 2026
  Cadillac) and no chassis/engine/principal fields. Adds a new API
  dependency for one base city field that the design dropped.

## Caveats — read these before implementing

### 1. F1DB YAML `seasons/{year}/entrants.yml` is the source of all three team-facts fields

The runtime API alternatives (f1api.dev, Jolpica, TheSportsDB) do not
expose chassis, power unit, or base. F1DB's structured YAML
source does, and is the right shape for a build-time import:

```yaml
# src/data/seasons/2026/entrants.yml — first entry, Mercedes
- entrantId: mercedes-amg-petronas-f1-team
  countryId: germany
  constructorId: mercedes
  engineManufacturerId: mercedes
  chassisId: mercedes-f1-w17
  engineId: mercedes-amg-f1-m17-16-v6-t-h
  tyreManufacturerId: pirelli
  drivers:
    - driverId: kimi-antonelli
      rounds: 1-11
    - driverId: george-russell
      rounds: 1-11
```

The `chassisId` resolves to a human-readable name via the chassis YAML
files (`mercedes-f1-w17.yml` → `name: "F1 W17"`). The
`engineManufacturerId` resolves to a human-readable name via the
engine-manufacturer YAML files (`mercedes.yml` → `name: "Mercedes"`).
The `countryId` gives the base country directly (e.g. `germany`,
`united-kingdom`, `italy`).

**City and team principal are not in F1DB and are not on the new
screen.** Both dropped per user decision.

### 2. F1DB `totalRaceEntries` counts include sprint races

F1DB Mercedes `totalRaceEntries: 351` vs the screenshot all-time `339
GPs`. The 12-round difference is sprint rounds (2021–2024 weekends
with sprint races add one more "race" entry to the constructor's
career totals). F1DB treats sprint races as races for the purpose of
career-entry counting.

The right framing for the user: **"Races entered"** (351) vs **"Grands
Prix contested"** (339, race-only). The screenshot uses the latter
definition.

If we adopt the F1DB number (351), the cell label should be
**"Races entered"** (or just "Entries"). If we want to match the
screenshot exactly, the F1DB aggregation needs to filter out sprint
rows from `races-race-results.json` before counting. Same applies to
`totalRaceWins` (F1DB: 139 wins — the screenshot 130 implies sprint
wins are also excluded).

**Pick one definition and label it clearly.** Recommend "Grands
Prix" for race-only (matches the screenshot, matches the casual F1
fan mental model) and aggregate from `races-race-results.json`
filtering `year-round-iteration-key` to race rows only.

### 3. Wikipedia REST is CC BY-SA 4.0 — attribution required

The summary text is reused under CC BY-SA 4.0. The "About" section
**must** show a small attribution line ("From Wikipedia, the free
encyclopedia, under CC BY-SA 4.0" + link to the article). The
existing `lapRecord` citation on Circuit Detail is the same pattern.

The User-Agent policy is the only real "API etiquette" ask: identify
the app in the User-Agent header (`F1app/1.0 (contact-url)` — same
shape as the existing `User-Agent` we send). The REST API does not
require an API key, no rate-limit ceiling documented for
`/page/summary` beyond the standard 200 req/s Wikimedia edge limit.

### 4. Chassis / power unit / base for FUTURE seasons update via F1DB
YAML import

For the **current** season, F1DB YAML is the source. For **future**
seasons, the F1DB import script (built alongside the existing
circuit-artwork import) re-reads `seasons/{year}/entrants.yml` at
build time and generates a fresh `TeamSeasonalFacts.kt` catalog
file. No code change needed at season launch — just rerun the
import.

### 5. f1api.dev `/current/drivers` nationality is the country name
(e.g. "Italy"), Jolpica adds a 2-letter ISO code

For a country flag, Jolpica ergast `/drivers/{id}/` adds nothing
useful, but the open question is whether to add a flag image
component. Out of scope for this research — a future design
decision. The text country name works on its own.

## Implementation outline (where the data joins go)

The current `GetDriverDetailUseCase` / `GetTeamDetailUseCase` return
an `Outcome<DriverDetail>` / `Outcome<TeamDetail>` from two f1api.dev
calls (driver/team list + championship join). The new field set
needs a third data source — the F1DB catalog — exposed the same way
the existing circuit artwork is exposed: as a `Map<DriverId,
DriverSeasonalStats>` and `Map<TeamId, TeamSeasonalStats>` in `f1/data/`,
populated at build time, zero runtime cost.

```kotlin
// f1/data/DriverCatalog.kt — generated from F1DB at build time
data class DriverAllTimeStats(
    val totalRaceEntries: Int,
    val totalRaceWins: Int,
    val totalPodiums: Int,
    val totalPolePositions: Int,
    val totalPoints: Double,
    val totalFastestLaps: Int,
    val totalTop10s: Int,           // aggregated from race-results
    val totalDnfs: Int,             // aggregated from race-results
    val totalChampionshipWins: Int,
    val firstEntryRaceId: String?,  // from race-results
    val firstWinRaceId: String?,    // from race-results
    val firstSeasonYear: Int,
    val lastSeasonYear: Int,
    val perSeason: Map<Int, SeasonDriverStats>,
)

data class SeasonDriverStats(
    val position: Int?,
    val wins: Int,
    val podiums: Int,
    val poles: Int,
    val points: Double,
    val fastestLaps: Int,
    val dnfs: Int,
    val top10s: Int,
)

// f1/data/ConstructorCatalog.kt — same shape for teams, generated
// from F1DB YAML entrants.yml + chassis.yml + engine-manufacturers.yml
data class TeamSeasonalFacts(
    val chassis: String,         // "F1 W17"
    val powerUnit: String,       // "Mercedes"
    val baseCountry: String,     // "Germany"
)
// No hardcoded map — all three fields come from F1DB YAML build-time.
```

The use case joins the catalog to the f1api.dev detail:

```kotlin
// f1/GetDriverDetailUseCase.kt (revised)
class GetDriverDetailUseCase(
    private val client: HttpClient,
    private val catalog: DriverCatalog,           // F1DB
    private val teamCatalog: TeamSeasonalFactsTable, // F1DB YAML (chassis, power unit, base country)
    private val wikipediaClient: WikipediaClient, // new
) {
    suspend operator fun invoke(driverId: String, forceRefresh: Boolean = false): Outcome<DriverDetail> = ...
}
```

The Wikipedia client is a single `HttpClient` extension that takes
the Wikipedia URL string and returns a `WikipediaSummary` (title +
extract + content_urls):

```kotlin
// f1/data/WikipediaClient.kt
@Serializable
data class WikipediaSummaryDto(
    val title: String = "",
    val description: String? = null,
    val extract: String = "",
    @SerialName("content_urls") val contentUrls: ContentUrlsDto = ContentUrlsDto(),
)

suspend fun HttpClient.getWikipediaSummary(url: String): Outcome<WikipediaSummary>
```

The URL → slug conversion is one regex
(`/wiki/(.+)$` → group 1, URL-decode). For drivers, f1api.dev returns
`https://en.wikipedia.org/wiki/Andrea_Kimi_Antonelli` and the REST
endpoint auto-redirects to the canonical `Kimi_Antonelli` (verified
live). The same shape for teams.

## Follow-up ticket breakdown

If the user wants to ship the new detail pages, the work decomposes
into three tickets (none started):

1. **F1DB import — driver + constructor + race-results + entrants.**
   New build-time script alongside the existing
   `tools/f1db/import-circuit-artwork.py`. Generates three Kotlin
   files: `DriverCatalog.kt`, `ConstructorCatalog.kt`,
   `TeamSeasonalFacts.kt` (from F1DB YAML entrants.yml + chassis.yml
   + engine-manufacturers.yml), with all aggregates pre-computed at
   build time. ~300 lines of Python + ~150 lines of generated
   Kotlin. License: F1DB is CC BY 4.0 (same as Wikipedia); the
   generated files should carry the attribution header in the KDoc.
2. **Wikipedia REST extension** (`HttpClient.getWikipediaSummary`).
   One DTO, one extension, one line in the use case join. CC BY-SA
   attribution surfaced on the "About" section.
3. **DriverDetail / TeamDetail UI rewrite.** The existing
   `feature/driver/DriverScreen.kt` and `feature/team/TeamScreen.kt`
   are joined-out minimal surfaces. The new screens add the two
   tabs (current-season / all-time), the new field rows, the
   "Compare" card, the "About" section. Out of scope for this
   research — design-driven.

The use case seam changes (DriverDetail/TeamDetail models gain
~12 new fields each), but the route + ViewModel + screen
architecture is unchanged.

## What this does NOT cover

- **Compare card** ("Select a driver to compare..."). That's a separate
  feature, not a data source question.
- **Country flags** for the "Country" cell. Jolpica adds a 2-letter
  ISO code; flag imagery is a separate ticket (Cloudinary or
  hardcoded SVG set).
- **Driver headshot on DriverDetail** — already shipped per
  [cloudinary-headshot-paths.md](cloudinary-headshot-paths.md); the
  new detail UI just needs to keep the existing `driverImageUrl()`.
- **Team car render on TeamDetail** — already shipped per
  [team-imagery.md](team-imagery.md); the new detail UI keeps
  `teamImageUrl()`.
- **Per-round bar chart of points.** Out of v1 scope per user
  decision.
- **Detailed "season entry" history** (which teams a driver raced
  for, year by year). F1DB has it (`f1db-seasons-drivers.json` has
  `constructorId` per year); out of v1 scope.

## Cross-references

- [driver-team-detail-api-wrangling.md](driver-team-detail-api-wrangling.md) —
  per-source probes, payload shapes, computed Antonelli/Mercedes checks.
- [lode/leaderboard/summary.md](../leaderboard/summary.md) — the
  current shipped detail-page surface (join-only).
- [lode/wayfinder/f1app/f1db-data.md](f1db-data.md) — F1DB coverage
  precedent (Driver of the Day, fastest laps); the new import extends
  this.
- [lode/wayfinder/f1app/cloudinary-headshot-paths.md](cloudinary-headshot-paths.md) —
  driver headshot path (already shipped; the new detail UI reuses).
- [lode/wayfinder/f1app/team-imagery.md](team-imagery.md) — team car
  imagery (already shipped; precedent for the per-season hardcoded
  `LEGACY_TEAM_SLUGS` map shape).
- [lode/wayfinder/f1app/team-accent.md](team-accent.md) — `TeamColors.forId()`
  hardcoded map (precedent for the import-script catalog shape).
- [lode/wayfinder/f1app/circuit-most-wins.md](circuit-most-wins.md) —
  precedent for "no free source → hardcoded map" decision shape.
- [lode/wayfinder/f1app/top-speed.md](top-speed.md) — sibling research
  file (different stat, same multi-source methodology).
- [lode/terminology.md](../terminology.md) — `F1Api` extension shape,
  `Wiring`, `HttpCache`, `SectionUiState` for the new screen sections.

## Invariants captured

- **Every new field on the new detail pages has a free source** —
  no paid APIs, no scraping. F1DB (build-time) + Wikipedia REST
  (runtime) cover the gap entirely.
- **F1DB YAML `seasons/{year}/entrants.yml` is the source of record
  for all three team-facts fields** (chassis, power unit, base
  country) for any team/season combination. The runtime APIs
  (f1api.dev, Jolpica, TheSportsDB) do not carry these fields. F1DB
  updates ~weekly; the import script reruns at build time.
- **f1api.dev and Jolpica ergast are not the right sources for
  podiums / poles / DNFs / top10s / fastest-laps** — neither exposes
  the totals. F1DB JSON has them pre-aggregated. Computing from
  Jolpica `/results/` is possible but more network for the same
  answer.
- **No hardcoded team-facts map is needed.** Chassis, power unit,
  and base country all come from F1DB YAML build-time. Base city
  and team principal were dropped from the screen per user
  decision.
- **The "About" text is the Wikipedia REST summary**, sourced via
  the `url` field already on f1api.dev driver/team rows. CC BY-SA
  4.0 — attribution line required in the "About" section.
- **The F1DB `totalRaceEntries` count includes sprint rounds** —
  the screen should use the race-only count to match the "Grands
  Prix" mental model and the screenshot. Same for wins, podiums,
  poles. The F1DB value is the "career entries" upper bound; the
  per-round aggregation with sprint rows filtered is the
  "Grands Prix" count.
- **F1DB v2026.10.1 (release tag 2026-07-26)** is the data source.
  Updates ~weekly. The import script runs at build time; the catalog
  file is a generated artifact, not hand-edited.
- **Wikipedia User-Agent policy:** identify the app in the
  `User-Agent` header (`F1app/1.0` + contact URL). Standard 200 req/s
  Wikimedia edge limit; well under our needs.
- **The `url` field on f1api.dev `/current/drivers` (Antonelli:
  `https://en.wikipedia.org/wiki/Andrea_Kimi_Antonelli`) auto-redirects
  to the canonical Wikipedia article** (`Kimi Antonelli`) when used
  as the REST summary slug. The f1api.dev URL is a stable join key
  even when the canonical title differs.

## Lessons learned

- **"Already wired" is a lode phrase that needs re-checking per
  field.** The current detail pages are a thin join surface. "Same
  use case" can mean "12 new fields, three new sources, four
  different aggregation rules." Per-field source decisions matter.
- **F1DB is the runtime-free answer for all-time stats AND for
  per-season team facts (chassis, engine, base country).** Same
  build-time import shape as the existing circuit artwork. No new
  runtime network, no new HttpClient config, no live-window risk.
- **The F1DB YAML source is structured, current, and covers all 11
  2026 teams.** The runtime JSON release (`splitted/`) is missing
  the per-season chassis + engine mapping (it only has a global
  chassis list). The YAML source is where this lives.
- **Wikipedia REST is the "About" answer.** No third-party
  summarizer, no LLM, no AI-bio generator — the user-visible text
  is the Wikipedia editorial summary, with proper attribution.
  License-compliant, free, stable.
- **Sometimes the right answer is to drop the field.** Base city
  and team principal had no free JSON source; rather than introduce
  a hardcoded map or scrape HTML, the design dropped both. The
  remaining team-facts fields (chassis, power unit, base country)
  all come from F1DB YAML.
- **"All time" needs a definition.** F1DB's totals include sprint
  rounds; the screenshot's "all time" excludes them. The label
  ("Grands Prix" vs "Races entered") is the disambiguator; the
  aggregation is the implementation detail.
- **The bar chart is gone per user decision.** It would have
  needed a definition (race points vs championship points) and an
  extra F1DB aggregation; dropping it removes both the data
  definition and the implementation work.
