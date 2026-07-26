# ID namespaces & the alpha car-number translator

The app talks to two free F1 APIs that use **two** distinct identity
namespaces for drivers and teams. Every consumer — favorites keys,
`Route.DriverDetail(driverId)` / `Route.TeamDetail(teamId)`, the
championship standings, the driver/team detail pages, Jolpica standard
race/qualifying results, and Jolpica pit-stops — keys on the **Ergast
canonical** namespace. Only Jolpica alpha FP/SQ/SR results carry a
different, opaque namespace, which is translated back to canonical at
the data seam.

## The two namespaces

| Namespace | ids | source | consumed by |
|---|---|---|---|
| **Ergast canonical** | `max_verstappen`, `red_bull` | f1api.dev `/current/drivers` AND `/{year}/drivers` (current + historical catalogs share it); `/current/drivers-championship`; Jolpica standard `/results.json` & `/qualifying.json`; Jolpica `/pitstops.json` | favorites (`FavoritesCache` keys), `Route.DriverDetail`/`Route.TeamDetail`, `GetDriverDetailUseCase`/`GetTeamDetailUseCase` lookups, race+quali result rows (steps 2–3), the pit-stop join |
| **Jolpica alpha opaque** | `driver_cAPSXDn9`, `team_LjEBz7Xq` | Jolpica alpha `/f1/alpha/results/{round_id}/{FP1|FP2|FP3|SR|SQ}/` | only the alpha result mappers (`loadAlpha` in `GetSprintResultUseCases.kt`) — never escapes to favorites or routes |

### Correction of an earlier premise

An earlier design note assumed three namespaces — f1api `maxverstappen`
(no underscore) vs Ergast `max_verstappen` vs alpha opaque. Live probes
(`/current/drivers`, `/{year}/drivers`, `/current/drivers-championship`)
disproved that: f1api.dev uses Ergast ids (`max_verstappen`, `red_bull`)
for both current and historical catalogs. There is no separate
`maxverstappen` namespace. So translating alpha → Ergast aligns alpha
rows with **everything** in one move.

## The car-number bridge

Alpha's `AlphaResultDto` exposes a `car_number` that is stable and unique
within a season and matches `CurrentDriverDto.number` in the
season-matched f1api catalog. `CarNumberTranslator` is built once per
`loadAlpha` call from `getDrivers(year)` and maps `carNumber → (driverId,
teamId)`:

```kotlin
internal data class CarNumberTranslator(private val byCarNumber: Map<Int, TranslatedDriver>) {
    internal fun translate(carNumber: Int?): TranslatedDriver? = carNumber?.let { byCarNumber[it] }
    internal companion object {
        internal fun from(catalog: CurrentDriversResponseDto): CarNumberTranslator = ...
        internal val EMPTY = CarNumberTranslator(emptyMap())
    }
}
internal data class TranslatedDriver(val driverId: String, val teamId: String)
```

The alpha row mappers (`toRoundResult`, `toQualifyingResult`,
`toPracticeResult`) translate `driverId`/`teamId` with an **opaque-id
fallback** when the catalog misses (catalog outage, or a car number absent
from that season's catalog). The screen still renders; only a future deep
link from such a row would be unresolved.

### Team-id accuracy: a known limitation

The catalog is a **season** driver list, not a round/session roster. The
`teamId` resolved for a row is the driver's season team — correct for the
overwhelming majority of rows but **wrong for reserve/substitute drives or
mid-season team changes** within that season. Today this is inert: no UI row
links to `Route.TeamDetail`, so a mis-resolved `teamId` renders nothing wrong.
When team deep links land on FP/SQ/SR rows, either document this as a
known limitation or add a team-name → canonical-team translator (alpha exposes
the team name per row, which is a stronger team bridge than the driver
catalog's team). This limitation is recorded in ADR 0005's Consequences and
stays open until (if) a future team-name → canonical-team translator lands.

### Why season-matched, not `getCurrentDrivers`

`getDrivers(year)` (not `getCurrentDrivers`) fetches the catalog for the
**same year** as the alpha result, so a past round's car numbers resolve
against that year's drivers. A current-only catalog would mis-link on
car-number reuse across seasons (e.g. #14 was Alonso in 2024, someone
else in 2026). The catalog is HttpCache-shared, so the cost is one cached
call on top of the alpha round-id + results fetches.

### No pit-stop join concern for alpha

The pit-stop join (`driverForPitstop` on `SessionResult`) only renders
for `SessionType.Race`, whose results come from Jolpica standard
(Ergast ids) — aligned in step 2. Sprint/sprint-quali pit-stops are not
fetched, so the alpha translator has no pit-stop alignment work.

## Scope today: data-layer preparation, no UI effect yet

Every `SessionResult` row — race, quali, FP, sprint, sprint-quali — is a
plain `Card` with **no onClick, no favorites highlight** (verified by
grep on `feature/sessionresult/SessionResultScreen.kt`). The translator
therefore changes no visible behavior today; it makes the FP/SQ/SR rows'
`driverId`/`teamId` honest in the Ergast namespace so that when deep
links / favorites highlight land on those rows, they route correctly.

## Related

- [decisions/0005-session-results-use-two-apis.md](../decisions/0005-session-results-use-two-apis.md) — accepted (amended; supersedes 0006). R+Q on Jolpica standard; FP+SR+SQ on Jolpica alpha via this file's car-number bridge; f1api.dev for schedule + catalogs only.
- [decisions/0006-race-results-hybrid-source.md](../decisions/0006-race-results-hybrid-source.md) — superseded by 0005; the hybrid f1api.dev+Jolpica merge is retired.
- [core/network.md](../core/network.md) — `HttpClient` + the `F1Api` extensions, including `getDrivers(year)`.
- [terminology.md](../terminology.md) — jolpica `driverId`/`constructorId` match f1api.dev's namespace (already recorded); the alpha opaque namespace + translator are this file's concern.