---
id: 03
title: Data layer & widget refresh strategy
type: grilling
status: closed
closed_by: single-source Ktor + HttpCache + one periodic WorkManager job; DataStore + HttpCache, no repo class
blocked_by: [01, 04]
owner: ""
---

## Decision

Single-source f1api.dev data layer. One `HttpClient` in `Wiring`, Ktor endpoint
extensions in `f1/data/F1Api.kt`, use cases that compose + map DTO→model. No
`F1Repository` class. HttpCache + pull-to-refresh for caching. One periodic WorkManager
job polling `/current/next` for the Countdown widget, writing typed DataStore keys.
DataStore + HttpCache; WorkManager reserved for widget refresh only.

### Screens the data layer serves

Three top-level navs: **Homepage**, **Schedule**, **Leaderboard**. Driver/Team detail
pages reached from Leaderboard rows and Homepage favorite cards. Navigation 3 routes:
`Homepage` (start), `Schedule`, `Leaderboard`, `DriverDetail(id)`, `TeamDetail(id)`,
`RoundDetail(year, round)`.

### Repository shape — no repo class

- `f1/data/F1Api.kt` = transport: `suspend fun HttpClient.getNextRace(): NextRaceResponseDto`
  etc. One extension per endpoint, pure Kotlin, satisfies the domain-purity invariant.
- Use cases own composition + DTO→model mapping. No pass-through repo class — every use
  case composes its own model; there is no shared transform a repo would centralize.
- Matches PokeDV precedent and the "lean toward no separate repo" tee-up from ticket 04.

### Use cases (screen-driven — no use case without a caller)

| Use case | Endpoints | Callers |
|---|---|---|
| `GetNextRaceUseCase` | `/current/next` | Homepage (fav-next-GP + section 3), Countdown worker |
| `GetSeasonUseCase` | `/current` | Homepage section 2 (aggregates), Schedule both tabs, Schedule-detail-upcoming |
| `GetDriversStandingsUseCase` | `/current/drivers-championship` | Leaderboard, Homepage fav-driver, Driver detail |
| `GetConstructorsStandingsUseCase` | `/current/constructors-championship` | Leaderboard, Homepage fav-team, Team detail |
| `GetDriverDetailUseCase(id)` | `/current/drivers` + `/current/drivers-championship` | Driver detail |
| `GetTeamDetailUseCase(id)` | `/current/teams` + `/current/constructors-championship` | Team detail |
| `GetRoundResultsUseCase(year, round)` | `/{y}/{r}/race` | Past round drilldown |
| `GetRoundQualifyingUseCase(year, round)` | `/{y}/{r}/qualy` | Past round drilldown |

`F1Api.kt` also gets a `getCircuit(id)` extension (cheap, one line) — no use case until
a standalone screen needs `/circuits/{id}`; the circuit block is already inlined in
every race object.

### ViewModel composition

Homepage ViewModel **combines 4 use cases** (`GetNextRace` + `GetSeason` +
`GetDriversStandings` + `GetConstructorsStandings`) via the init-less
`Flow.onStart{load()} + stateIn(WhileSubscribed(5_000))` pattern from ticket 01. Each
section fails independently; no composite `GetHomepageDataUseCase`. Leaderboard,
Schedule, Driver/Team/Round detail each use their own use case(s).

### Caching — HttpCache + NO_CACHE pull-to-refresh

- `HttpCache` plugin, file cache ~10MB in app cache dir.
- Default: respect server `Cache-Control`; configured `max-stale` tolerance for offline
  cold launch (try-then-fallback, no separate stale store).
- Pull-to-refresh: `CacheControl.NO_CACHE` per request — one `HttpClient`, two cache
  policies by request flag.
- `ponytail:` fallback flagged in `HttpClientFactory` — if f1api.dev sends no cache
  headers at all (likely — hobby API), confirm at build time and add a default response
  lifetime via plugin config or a thin in-memory TTL. Strategy locked; exact config
  deferred to implementation.
- DataStore + HttpCache; WorkManager reserved for widget refresh only (multi-source is
  additive endpoints on the same `HttpClient`; caching strategy unchanged from ticket 04).

### Widget refresh — one periodic WorkManager job

- `CountdownWorker` (CoroutinesWorker) → `GetNextRaceUseCase` → extracts next race start
  instant + label + full session schedule → writes `NextRaceCache`.
- 15-min WorkManager floor cadence (fine — next-race timestamp moves slowly except on
  raceday transitions).
- `Constraints.Builder().setRequiredNetworkType(CONNECTED)`, exponential backoff.
- On fetch failure: **leave the cached value**, don't clear. DataStore retains last good.
- The 1s countdown tick + "what to show once the timestamp passes (LIVE / finished)"
  is ticket 07, not 03. 03's job ends at "timestamp + label correctly cached and current."

### DataStore — `NextRaceCache`, typed keys

One `DataStore<Preferences>` held by `Wiring`, wrapped as `NextRaceCache` in
`widget/countdown/data/`. Typed keys in one atomic `edit` block (no serialized JSON
blob — typed keys are simpler and equally atomic within one `edit`):
`NEXT_RACE_START_MILLIS: Long`, `NEXT_RACE_NAME: String`, `NEXT_RACE_CIRCUIT: String`,
`NEXT_RACE_ROUND: Int`, `NEXT_RACE_SEASON: Int`, plus the full session schedule for
"closest event" countdown (FP1/FP2/FP3/qualy/race timestamps). Worker writes, widget
reads, same instance via `Wiring`.

### DTO + model structure

- DTOs in `f1/data/Dtos.kt`: `@Serializable`, `@SerialName` (snake_case→camelCase),
  nullable where the API nulls, envelope-wrapped per endpoint
  (`NextRaceResponseDto`, `SeasonResponseDto`, `DriversStandingsResponseDto`, etc.).
- Domain models in `f1/model/`: `NextRace`, `Season` (with pre-computed aggregates:
  completedGp / totalKm / totalLaps / progressPercent), `Race`, `Circuit`, `Driver`,
  `Team`, `DriverStanding`, `ConstructorStanding`, `RaceResult`, `QualifyingResult`.
- Use cases map DTO→model.
- `Json { ignoreUnknownKeys = true; coerceInputValues = true }` absorbs schema noise
  (envelope `api`/`url` fields, extra fields, dirty birthday strings).

### Schema noise locked into DTOs (no decision needed, recorded for implementation)

- **Position is a String** (`"1"` or `"NC"`) — model as String throughout.
- **Race result `time`** is messy (`"1:21:06.758"`, `"+1 lap"`, `"DNF (1)"`) — store as
  String, don't parse.
- **`birthday`** is dirty (ISO `2006-08-25` *and* `15/02/1998` mixed across drivers in
  the same response) — store as String, don't parse.
- **`circuitLength: "7004km"` in `/current*` vs `7004` Int in `/circuits/{id}`** — parse
  to Int by stripping non-digits when computing km aggregates.
- **Envelope shape differs by endpoint:** `/current/next` → `race: [...]` array;
  `/current` → `races: [...]` array; `/{y}/{r}/race` → `races: {...}` object with
  `results: [...]`. Three different envelope DTOs.
- **`circuit` is an object** in `/current*` but **a one-element array** in
  `/{y}/{r}/race`. Different DTO per endpoint.
- **Three spellings of "firstAppearance"** across endpoints (`firstAppareance`,
  `firstAppearance`, `firstParticipationYear`) — `@SerialName` per DTO.

### Base URL

Per ticket 04: one `HttpClient` with **no default base URL**; `F1Api.kt` defines
`const val F1API_BASE = "https://f1api.dev/api"` and each extension builds its full URL.
Ticket 04 (closed, multi-source) adds `JOLPICA_BASE` and `OPENF1_BASE` to the same
file — the "OpenF1 follow-up adds `OPENF1_BASE` later" line is superseded; all
three consts ship in the initial build.

### Wiring surface after 03

`Wiring` exposes: `HttpClient`, `NextRaceCache`, and the eight use cases above. The
worker reaches `Wiring` via the `Application` (one-instance, cross-entry-point
service-locator from ticket 01).

## Data gaps deferred to research tickets

The Schedule-detail circuit-stats block described three fields f1api.dev doesn't
serve. tickets 08–10 research each; ticket 04 reopened to multi-source so the stats
that need a second API ship in the initial build:

- **GAP-A — Top speed (ticket 08, closed).** f1api.dev has no speed field. Ship the
  real top speed (km/h) from OpenF1 `/v1/laps` `st_speed` in the initial build
  (ticket 04 reopened). `circuit.lapRecord` (lap time) may also ship as a separate
  "Fastest lap" cell on Round detail, not as a speed substitute. Serves Homepage
  section 3 and Round detail.
- **GAP-B — Most wins (team + driver) at circuit (ticket 09, open).** No clean free
  source. Not f1api.dev (verified). OpenF1 would need every historical session +
  client-side tally. jolpica is the likely source — now wiring-allowed under the
  ticket-04 reopen. Default if unresolved: stat stays **dropped** from circuit-stats.
- **GAP-C — Full podium on Past list (ticket 10, closed).** `/current` inlines only
  the winner per completed race; full podium needs one `/race` fetch per past round.
  Past list shows **full podium (P1/P2/P3)** via lazy per-row `GetRoundPodiumUseCase`,
  cached by HttpCache. See `lode/wayfinder/f1app/past-list.md`.

## Sources

f1api.dev wired paths (all zero auth, base `https://f1api.dev/api`):
- `GET /current` — full-season schedule + sessions (races array; circuit inlined; winner inlined on completed races)
- `GET /current/next` — next race (race array, take [0]; full `schedule` of fp1/fp2/fp3/qualy/race + inlined circuit)
- `GET /current/drivers` — driver list (name, number, team, nationality; birthday dirty)
- `GET /current/teams` — team list
- `GET /current/drivers-championship` — standings, driver+team pre-joined; `wins`/`points`/`position` per row
- `GET /current/constructors-championship` — constructor standings; `wins`/`points`/`position` per row
- `GET /{year}/{round}/race` — race results (grid, position, fastLap, time, driver+team; `circuit` as one-element array)
- `GET /{year}/{round}/qualy` — qualifying results
- `GET /circuits/{circuitId}` — circuit metadata (same fields as inlined circuit block; `circuitLength` as Int here)

`/current/last` (seen in the `/current/next` envelope `url` field) is not wired —
`/current/next` is the widget + Homepage source.
