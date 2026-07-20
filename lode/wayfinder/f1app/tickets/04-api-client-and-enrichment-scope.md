---
id: 04
title: API client & enrichment scope
type: grilling
status: closed
closed_by: multi-source Ktor contract — f1api.dev + OpenF1 + jolpica on one HttpClient, per-request base URLs, 11 use cases, HttpCache strategy locked against live probes
blocked_by: []
owner: ""
---

## Decision (closed)

**Multi-source from the initial build.** One `HttpClient` in `Wiring` with
per-request base URLs — no default base URL on the client. Three sources:

- **f1api.dev** — primary schedule / standings / results / circuit metadata.
- **OpenF1** — top speed (`/v1/laps` `st_speed`), 2023+.
- **jolpica** — all-time most-wins-at-circuit (`/circuits/{id}/results/1.json`).

No `openf1/` or `jolpica/` package. All F1 concepts live in `f1/`; second and
third sources are Ktor extensions in `f1/data/F1Api.kt` on the same client.
Domain-purity invariant holds — `f1/` is zero-`android.*`.

## Base URL constants (`f1/data/F1Api.kt`)

```kotlin
const val F1API_BASE   = "https://f1api.dev/api"
const val JOLPICA_BASE  = "https://api.jolpi.ca/ergast/f1"
const val OPENF1_BASE   = "https://api.openf1.org/v1"
```

> **Implementation note (2026-07-20):** the foundation slice (ticket 01) ships
> only `F1API_BASE`. `JOLPICA_BASE` and `OPENF1_BASE` land here alongside their
> use cases / endpoint extensions in tickets 08/09 — they're not pre-declared in
> the foundation slice, so the f1api.dev-only state is not scope creep.

## Ktor extension surface (transport tier)

All `suspend fun HttpClient.*` in `f1/data/F1Api.kt`, pure Kotlin.

| Source | Extension | Endpoint |
|---|---|---|
| f1api.dev | the 8 from ticket 03 + `getCircuit(id)` | unchanged |
| OpenF1 | `getOpenF1Sessions(year, countryName, sessionName): List<OpenF1SessionDto>` | `/v1/sessions` |
| OpenF1 | `getOpenF1Laps(sessionKey): List<OpenF1LapDto>` | `/v1/laps?session_key=…` |
| jolpica | `getCircuitWinners(f1apiCircuitId): CircuitWinnersResponseDto` | `/circuits/{id}/results/1.json` |

**No `speed_unit` param on `/v1/laps`.** It 404s. `st_speed` is natively kph.
**Podium needs no new extension** — `GetRoundPodiumUseCase` reuses
`getRoundResults` and slices `results[0..2]` (position-ordered array).

## circuitId translation map

Private `val` in the jolpica extension; f1api.dev's `circuitId` is the public
ID everywhere else in the app.

```kotlin
private val F1API_TO_JOLPICA_CIRCUIT = mapOf(
    "austin" to "americas",
    "gilles_villeneuve" to "villeneuve",
    "hermanos_rodriguez" to "rodriguez",
    "lusail" to "losail",
    "montmelo" to "catalunya",
)
```

The OpenF1 join uses `country_name` + `year` + `race-date match` (ticket 11) —
`country_name` alone is insufficient because US (3 circuits), Spain (2
circuits 2026+), and Italy (2 circuits 2023–2025) return N sessions per year.
The race-date match is the unique disambiguator (f1api.dev's
`race.schedule.race.date` matches OpenF1's `date_start` date portion for
every circuit). A **1-entry country fallback map** is needed because
f1api.dev's `Great Britain` ≠ OpenF1's `United Kingdom` (Silverstone) — the
only string divergence in the current 24-circuit schedule:

```kotlin
private val F1API_TO_OPENF1_COUNTRY = mapOf(
    "Great Britain" to "United Kingdom",
)
```

Applied only when the literal `country_name` returns 0 results. Lives in
`F1Api.kt` next to the OpenF1 extensions, same pattern as
`F1API_TO_JOLPICA_CIRCUIT` above. `circuitId` is not portable across APIs.

## HttpCache strategy (one shared plugin, three hosts)

Probed live 2026-07-16. One `HttpCache` plugin, ~10MB file cache; hosts don't
collide (different origins).

| Source | Server cache headers | HttpCache behavior | Pull-to-refresh |
|---|---|---|---|
| f1api.dev | `cache-control: public, max-age=600` | respects 10-min TTL | `NO_CACHE` |
| jolpica | `cache-control: max-age=3600` + `Expires` | respects 1-hour TTL | `NO_CACHE` |
| OpenF1 | **none** (nginx, no CDN, no `Cache-Control`/`Expires`/`ETag`/`Age`) | **uncached** — HttpCache skips it | always fresh (no cache to bust) |

OpenF1 is fast (~0.3s/call, nginx) so the 2 calls per Homepage §3 cold open
cost ~0.6s. Accept uncached — `ponytail:` add a default TTL / in-memory layer
only if latency becomes a measured complaint.

`ponytail:` fallback in `HttpClientFactory` (from ticket 03 / practices.md):
f1api.dev was the original worry, but it sends `max-age=600` — so the fallback
is not load-bearing for f1api.dev. It's also not needed for jolpica
(`max-age=3600`). The only source with no cache story is OpenF1, and it's
accepted uncached. Leave the `ponytail:` comment in `HttpClientFactory` as a
note that OpenF1 is the cacheless source.

Offline cold launch: `max-stale` tolerance for f1api.dev + jolpica; OpenF1
has no stale layer (uncached, fails to network error on offline).

## Use-case reconciliation (8 → 11)

Ticket 03's "OpenF1 follow-up adds `OPENF1_BASE` later" line is superseded.
The use-case table extends to 11:

| Use case | Source | Callers |
|---|---|---|
| `GetNextRaceUseCase` | f1api.dev `/current/next` | Homepage §1+§3, Countdown worker |
| `GetSeasonUseCase` | f1api.dev `/current` | Homepage §2, Schedule, Schedule-detail-upcoming |
| `GetDriversStandingsUseCase` | f1api.dev `/current/drivers-championship` | Leaderboard, Homepage fav-driver, Driver detail |
| `GetConstructorsStandingsUseCase` | f1api.dev `/current/constructors-championship` | Leaderboard, Homepage fav-team, Team detail |
| `GetDriverDetailUseCase(id)` | f1api.dev `/current/drivers` + `/current/drivers-championship` | Driver detail |
| `GetTeamDetailUseCase(id)` | f1api.dev `/current/teams` + `/current/constructors-championship` | Team detail |
| `GetRoundResultsUseCase(year, round)` | f1api.dev `/{y}/{r}/race` | Round detail, Past-list podium (shared cache) |
| `GetRoundQualifyingUseCase(year, round)` | f1api.dev `/{y}/{r}/qualy` | Round detail |
| **`GetCircuitTopSpeedUseCase(circuitId, year?)`** | OpenF1 `/v1/sessions` + `/v1/laps` (`max(st_speed)`) | Homepage §3, Round detail |
| **`GetCircuitMostWinsUseCase(f1apiCircuitId)`** | jolpica `/circuits/{id}/results/1.json` (client-aggregate P1) | Round detail |
| **`GetRoundPodiumUseCase(year, round)`** | f1api.dev (reuses `getRoundResults`, slices `[0..2]`) | Schedule>Past list |

Homepage ViewModel now combines **5** use cases (4 + top speed); Round detail
combines 4–5; sections fail independently, no composite use case.

## OpenF1 schema note (for the DTO)

OpenF1 returns lowercase-no-underscore fields (`sessionkey`, `countryname`,
`circuitshortname`). OpenF1 DTOs get their own `@SerialName` mapping —
distinct from f1api.dev's snake_case DTOs. Same `Json { ignoreUnknownKeys =
true; coerceInputValues = true }` absorbs schema noise on both.

## NOT wired (parked, unchanged)

Driver headshot / weather / race-control flags — OpenF1 additive UI
enrichments. The `session_key` plumbing is now paid (top-speed wires it), so
landing these is cheaper, but shipping is a separate prioritization. **Not
widened by this close.** Lands in `f1/` as repository methods on the same
`HttpClient` when a follow-up ticket scopes them.

## What ticket 11 unblocks to

Closing 04 unblocks ticket 11 (research, GAP-A.2). 04 wires `OPENF1_BASE` +
the 2 OpenF1 extensions so 11 researches session_key join + all-time-vs-
latest against a **fixed surface**. 04 does **not** resolve all-time-vs-
latest — that's 11's scope. The homepage.md "record" semantics question is
11's to answer.

## Cross-references

- [tickets/03-data-layer-and-refresh.md](03-data-layer-and-refresh.md) — 8
  original use cases, HttpCache + NO_CACHE, `NextRaceCache`. Superseded line:
  "OpenF1 follow-up adds `OPENF1_BASE` later" → now wired.
- [../top-speed.md](../top-speed.md) + [../top-speed-api-wrangling.md](../top-speed-api-wrangling.md)
  — OpenF1 top-speed research (ticket 08). `st_speed` natively kph.
- [../circuit-most-wins.md](../circuit-most-wins.md) + [../circuit-most-wins-api-wrangling.md](../circuit-most-wins-api-wrangling.md)
  — jolpica most-wins research (ticket 09). 5-entry translation map.
- [../past-list.md](../past-list.md) — Past-list podium (ticket 10). Lazy
  per-row `GetRoundPodiumUseCase` reuses `getRoundResults`.
- [../homepage.md](../homepage.md) — Homepage §3 top-speed cell drives the
  OpenF1 wiring.
- [../../architecture/architecture.md](../../architecture/architecture.md) —
  module/DI/layer decisions; HttpClient config.
- [../../terminology.md](../../terminology.md) — `F1Api`, `Wiring`,
  `OpenF1`, `jolpica`.
- [../../practices.md](../../practices.md) — domain-purity invariant,
  HttpCache config.

## Invariants captured

- **One `HttpClient`** in `Wiring`, three base URL consts, no default base
  URL on the client. Second/third sources are extensions in `f1/data/F1Api.kt`,
  not separate packages.
- **`st_speed` is natively kph** — no `speed_unit` param on `/v1/laps`. It
  404s if passed.
- **`country_name` is the OpenF1 join key**, not `circuitId`, but the join
  is `country_name + year + race-date match` (ticket 11), not `country_name`
  alone. 1-entry `F1API_TO_OPENF1_COUNTRY` fallback map
  (`"Great Britain" → "United Kingdom"`) for the only country string
  divergence (Silverstone).
- **jolpica `driverId`/`constructorId` match f1api.dev's namespace** — the
  5-entry circuitId translation map is the only ID bridge needed; driver/team
  IDs route to `DriverDetail(id)` / `TeamDetail(id)` directly.
- **OpenF1 is the cacheless source.** f1api.dev (10-min) and jolpica (1-hour)
  send server cache headers; OpenF1 sends none. HttpCache skips OpenF1.
- **`lapRecord` (f1api.dev, lap time) is not a speed substitute** — it ships
  as a separate "Fastest lap" cell, never relabeled as "Top speed."

## Lessons learned

- **Probe cache headers before locking the HttpCache contract.** Three
  sources, three stories: f1api.dev (10-min), jolpica (1-hour), OpenF1
  (none). The "add a default TTL" ponytail fallback was written for f1api.dev
  preemptively; f1api.dev sends headers, OpenF1 doesn't. The worry was
  aimed at the wrong source.
- **`speed_unit=kph` looks right and 404s.** OpenF1's `st_speed` is natively
  kph; the param is invalid. Test the live endpoint before copying a param
  from a research doc's shorthand into an extension.
- **The session_key join tax is paid once for all OpenF1 stats.** Wiring
  top-speed (which needs `session_key`) makes headshot/weather/flags cheaper
  later — but cheaper ≠ in-scope. Keep the enrichment follow-up bounded.
- **`/v1/laps` is the workhorse, not `/v1/car_data`.** 2 calls + ~200KB vs
  21 calls + ~60MB; the speed-trap reading answers the question the design
  asks. See [top-speed-api-wrangling.md](../top-speed-api-wrangling.md).
