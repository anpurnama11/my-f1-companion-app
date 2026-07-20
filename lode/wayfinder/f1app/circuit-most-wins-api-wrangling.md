# Most-wins-at-circuit — API wrangling detail

Companion to [circuit-most-wins.md](circuit-most-wins.md). Per-source
probes, payload sizes, ID translation table, full aggregation shape,
the `JOLPICA_BASE` join path. This is the *how*; the *what* lives in
the main file.

All probes confirmed live against the public APIs on 2026-07-16.

## Sources at a glance

| Source | Call | Bytes (typical) | Bytes (worst) | Verdict |
|---|---|---|---|---|
| **jolpica** `/circuits/{id}/results/1.json` | **1** | ~23KB (Bahrain 22) | ~25KB (Monza 75) | **Ship this.** |
| Pit Lane F1 `/circuits/{slug}` | 1 | ~3KB (Bahrain) | ~8KB (Monza 75) | Documented fallback. |
| OpenF1 per-circuit tally | 1 sessions + N results | varies | varies | 2023+ only; out. |

## jolpica — the chosen path

### Endpoint: `GET /circuits/{circuitId}/results/1.json`

The `1` in the path filters to **P1 only** — each race at the circuit
contributes a single result row, not the full 20-driver grid. The
"pagination" parameter (default `limit=30`) refers to **races**, not
result-rows, so even the busiest circuits (Monza 75 races, Monaco 72,
Silverstone 61, Spa 58) come back on one page.

#### Live payload (Bahrain, abbreviated)

```json
{
  "MRData": {
    "total": "22", "limit": "30", "offset": "0",
    "RaceTable": {
      "Races": [
        {
          "season": "2024", "round": "1", "raceName": "Bahrain Grand Prix",
          "Circuit": { "circuitId": "bahrain", "circuitName": "Bahrain International Circuit", "Location": { ... } },
          "date": "2024-03-02",
          "Results": [
            {
              "number": "1", "position": "1", "positionText": "1", "points": "26",
              "Driver":     { "driverId": "max_verstappen", "permanentNumber": "3", "code": "VER", "givenName": "Max", "familyName": "Verstappen", ... },
              "Constructor": { "constructorId": "red_bull", "name": "Red Bull", "nationality": "Austrian", ... },
              "grid": "1", "laps": "57", "status": "Finished", "Time": { ... }, "FastestLap": { ... }
            }
          ]
        },
        ... 21 more races (2004..2025) ...
      ]
    }
  }
}
```

#### Cost (per Round-detail open, cold cache)

| Metric | Value |
|---|---|
| Calls | **1** |
| Payload | **22.9 KB** (Bahrain 22 races) up to **~25 KB** (Monza 75 races) |
| Latency | ~0.3 s |
| Server TTL | `cache-control: max-age=3600` (1 hour, captured by HttpCache) |
| Rate limit | 4 req/s, 500/hr; one call per open = headroom forever |

#### Cross-check (jolpica vs Pit Lane F1, Bahrain)

Both APIs agree exactly on the top-3 driver list (verified live):

| Rank | jolpica | Pit Lane F1 |
|---|---|---|
| 1 | Lewis Hamilton — 5 wins | Lewis Hamilton — 5 wins |
| 2 | Sebastian Vettel — 4 wins | Sebastian Vettel — 4 wins |
| 3 | Fernando Alonso — 3 wins | Fernando Alonso — 3 wins |

Team aggregation is **not** pre-aggregated by either source, so both
need the same client-side walk over `winners_timeline` / P1 rows. The
only divergence is the ID representation.

### ID namespace — f1api.dev ↔ jolpica (mostly clean)

f1api.dev's inlined-race `circuitId` (from `/current`) and jolpica's
`circuitId` (the path segment for `/circuits/{id}/...`) match for
**19 of 24** inlined IDs on the current calendar. The 5 mismatches:

| f1api.dev (inlined on `/current`) | jolpica | Circuit | P1 results on jolpica |
|---|---|---|---|
| `austin` | `americas` | Circuit of the Americas | 13 |
| `gilles_villeneuve` | `villeneuve` | Circuit Gilles Villeneuve | 45 |
| `hermanos_rodriguez` | `rodriguez` | Autódromo Hermanos Rodríguez | 25 |
| `lusail` | `losail` | Losail International Circuit | 4 |
| `montmelo` | `catalunya` | Circuit de Barcelona-Catalunya | 36 |

Two more IDs **only** exist on f1api.dev's `/circuits` listing in
French spelling (`bahrein` for Bahrain) — but those are never the form
inlined on races, so we don't see them in the join path. The inlined
form `bahrain` is what we get from `/current` and it matches jolpica
directly.

Driver and constructor IDs match f1api.dev's namespace cleanly on both
sides (e.g. `max_verstappen`, `hamilton`, `red_bull`, `ferrari`,
`mclaren`, `mercedes`). Verified live across 2024 results.

### Translation map (5 entries, constant)

```kotlin
private val F1API_TO_JOLPICA_CIRCUIT = mapOf(
    "austin" to "americas",
    "gilles_villeneuve" to "villeneuve",
    "hermanos_rodriguez" to "rodriguez",
    "lusail" to "losail",
    "montmelo" to "catalunya",
)
```

Lives as a `private val` in the jolpica adapter (the `HttpClient.getCircuitWinners`
extension in `f1/data/F1Api.kt`). Not exported into the public domain —
f1api.dev's `circuitId` is the public ID everywhere else.

## Pit Lane F1 — fallback shape

`GET https://pitlanef1.net/api/v1/public/circuits/{slug}` returns:

- `data.records.most_wins[]` — top driver wins, pre-aggregated
- `data.records.winners_timeline[]` — full chronological list with
  driver `name` + constructor `name` + year (so team aggregation is
  also possible client-side by walking this list)
- `data.records.total_races`
- `data.license = "CC-BY-4.0"`, `data.attribution_url = "https://pitlanef1.net"`

Server cache: `cache-control: public, s-maxage=300, stale-while-revalidate=86400`
(5 min fresh, 1 day stale-while-revalidate). Smaller payload
(~3-7KB), same single-call shape.

**Why fallback, not parallel:** the data is identical (verified) and
Pit Lane F1 lacks canonical IDs. Linking top winners to their
DriverDetail / TeamDetail requires a name-fuzzy-match against current
standings, which **fails for retired drivers** (Senna at Monaco,
Fangio at Monza, Schumacher at Spa). jolpica is the only source that
makes the design's "link to detail" ask trivially satisfiable for
all-time winners. Keep Pit Lane F1's URL in a comment as the
"if jolpica goes dark" recovery path — same shape, 1 call, no extra
wiring. `ponytail:` ceiling = no auto-failover; manual swap if
jolpica is unreachable for >24h.

### Cross-check (Pit Lane F1, top circuits)

| Circuit | total_races | Top 3 (driver, wins) |
|---|---|---|
| Bahrain | 22 | Hamilton 5, Vettel 4, Alonso 3 |
| Monaco | 71 | Senna 6, Hill 5, Schumacher 5 |
| Monza | 75 | Schumacher 5, Hamilton 5, Fangio 3 |
| Silverstone | 60 | Hamilton 9, Prost 5, Clark 3 |
| Spa | 58 | Schumacher 6, Senna 5, Hamilton 5 |

These exact numbers are the "no detail link for retired drivers" pain
point — the design's "link to detail" can't reach Senna, Hill, Clark,
Fangio on the current app without a separate ID-derivation step.

## OpenF1 — not a candidate

- No aggregation endpoint (404 on `/circuits`, `/most_wins`, `/winners`,
  `/standings`).
- Per-circuit tally would mean `GET /v1/sessions?country_name=&session_name=Race`
  → list of session_keys → `GET /v1/sessions/{key}/results?position=1`
  per session.
- OpenF1 data range is **2023+** only (per ticket 08). For Bahrain,
  that's 4 race sessions — misses 2004-2022 entirely. For Monza
  (75 races), it misses 73 of 75. Useless for an "all-time most wins"
  stat that the design's "wins the most" phrasing implies.
- P1 row gives driver name, not `driverId`, so same name-matching
  problem as Pit Lane F1.

Skip.

## f1api.dev — not used for this stat

f1api.dev has the same per-round `/{year}/{round}/race` shape jolpica
does (and ticket 10 already uses it for Past list podiums). Tallying
all-time at a circuit would mean 1 races-list call + N per-round
calls (N ≤ 76). For 1 call + 25KB on jolpica, that's strictly more
work for the same answer. Plus f1api.dev's `Driver.driverId` /
`Team.teamId` would also need to be canonical-checked against our
internal driver/team cache — jolpica's already-verified.

## Aggregation shape (the use-case body)

```kotlin
// in GetCircuitMostWinsUseCase
val races = dto.mrData.raceTable.races
val p1Rows = races.map { it.results[0] }

val topDriver = p1Rows
    .groupingBy { it.driver.driverId }
    .eachCount()
    .maxBy { it.value }
val topTeam = p1Rows
    .groupingBy { it.constructor.constructorId }
    .eachCount()
    .maxBy { it.value }
```

`p1Rows` size = races-at-circuit (n ≤ 76). Two `groupingBy` walks,
O(n) each, ~negligible. Output is `(id, count)` for the leader.

For the display cell we also need the driver/team **name**. The
`name` is in the same P1 row that gave us the `id` — we walk the
list once to find the row matching the leader's id and pull
`driver.givenName + " " + driver.familyName` (or
`constructor.name`). Don't separate "aggregate IDs" from "fetch
names" into two passes; one pass is enough.

## Where it lives in the data layer

- `const val JOLPICA_BASE = "https://api.jolpi.ca/ergast/f1"` in
  `f1/data/F1Api.kt`, alongside `F1API_BASE` (per ticket 04 reopen:
  multi-source, per-request base URLs, one `HttpClient` in `Wiring`).
- `suspend fun HttpClient.getCircuitWinners(f1apiCircuitId: String): CircuitWinnersResponseDto`
  — Ktor extension; performs the ID translation inline using a
  `private val` 5-entry map. The translation is a `const` in the
  same file (5 entries, stable, not data-driven).
- `GetCircuitMostWinsUseCase(f1apiCircuitId: String): Outcome<CircuitMostWins>`
  — calls the extension, performs the aggregation, returns the model.
- Caller: `GetRoundDetailUseCase` (or whichever use case the Round
  detail screen uses) — invokes `getCircuitMostWins` alongside the
  existing `getRoundResults` f1api.dev call. The Round detail screen
  already fetches `circuit` metadata from `/current`, so the
  `circuitId` is on hand.

## Caching

- **Server-side:** `cache-control: max-age=3600` on jolpica. 1 hour TTL.
- **Client-side:** Ktor `HttpCache` plugin (already wired,
  ~10MB file cache, `max-stale` tolerance for offline cold launch).
  Re-opens of the same circuit within 1 hour cost zero network.
  Cross-circuit, 24 current calendars × ~25KB = ~600KB worst-case
  cache footprint — well under the 10MB ceiling.
- **Pull-to-refresh:** `CacheControl.NO_CACHE` per request (the
  standard `practices.md` pattern) — user-triggered refresh bypasses
  the 1-hour cache. Round detail has a pull-to-refresh already.
- **Offline cold launch:** stale response is served; no fallback
  needed in the design.

## Coverage and limits

- **All-time coverage:** 1950+ on jolpica. F1's first championship.
  Every circuit that's been on the calendar has full history.
- **Multi-race-at-same-circuit quirks:** the 2020 season held two
  races at Bahrain (Bahrain GP + Sakhir GP). Both are counted under
  `circuits/bahrain` in jolpica, which is the right behavior —
  "wins at the circuit" not "wins at the grand prix."
- **f1api.dev quirks:** `/circuits` listing uses French spelling for
  some IDs (`bahrein` for Bahrain) but `/current` inlines the
  English form (`bahrain`). The inlined form is what we use; the
  French form is irrelevant to this stat.
- **The 1-hour server cache + Ktor HttpCache** mean a Round detail
  re-open within an hour is free. A `ponytail:` ceiling = no
  in-memory cache layer above HttpCache; if user demand ever
  warrants a "session lifetime" override, add it then.

## Rate-limit feel (live, 2026-07-16)

| API | 5 quick calls | Verdict |
|---|---|---|
| jolpica | 200 in ~0.3 s each | Comfortable. 4 req/s, 500/hr. One call per open: headroom forever. |
| Pit Lane F1 | 200 in ~0.6 s each | Comfortable. `x-ratelimit-limit: 60` per minute; one call per open is well under. |
| f1api.dev | 200 in ~4.5 s each (cold cache) | Slower but cached. Acceptable for stat reads. |

## Cross-references

- Main research file: [circuit-most-wins.md](circuit-most-wins.md) —
  decision, recommendation, invariants, lessons.
- `lode/practices.md` — HttpCache + `NO_CACHE` pull-to-refresh
  pattern. The jolpica call rides the same `HttpClient`.
- `lode/terminology.md` — `Wiring`, `F1Api` extension shape,
  `HttpCache`, `Outcome<T>`.
- Ticket 04 (reopened to multi-source): the `JOLPICA_BASE` const
  joins `F1API_BASE` per this ticket's contract.
