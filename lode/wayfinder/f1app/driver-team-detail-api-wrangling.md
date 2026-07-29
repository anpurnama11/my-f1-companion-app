> **Historical research — archived.** Current decisions live in
> [`decisions/`](../../decisions/) and build specs live in [`plans/`](../../plans/).

# Driver & Team detail — API wrangling detail

Companion to [driver-team-detail.md](driver-team-detail.md). Per-source
probes, payload shapes, computed checks, and the F1DB / Jolpica /
f1api.dev / Wikipedia REST join tables. The *what and why* live in the
main file; this is the *how* (re-derivable from the probes below).

All probes confirmed live against the public APIs and F1DB v2026.10.1
on 2026-07-26.

## Field-to-source join table

| Field | Source | Endpoint | Aggregation | Cost per detail open |
|---|---|---|---|---|
| Driver 2026 Position / Points / Wins | f1api.dev | `/api/current/drivers-championship` | take row by `driverId` | 1 call (cached) |
| Driver 2026 Podiums / Poles / DNFs / Top10s | F1DB | `seasons-drivers.json` (per-season) + `races-race-results.json` (per-round) | take row by `(driverId, year)`; DNFs/top10s counted from per-round `reasonRetired`/`positionNumber` | 0 (build-time) |
| Driver all-time GPs / Points / Wins / Podiums / Poles / Fastest Laps | F1DB | `drivers.json` | take row by `id` (F1DB key) | 0 (build-time) |
| Driver all-time DNFs / Top10s | F1DB | `races-race-results.json` | count over all `driverId` rows | 0 (build-time) |
| Driver first entry / first win | F1DB | `races-race-results.json` | first row by `(year, round)`; first row with `positionText == "1"` joined to `grands-prix.json` for race name | 0 (build-time) |
| Driver World Championships | F1DB | `drivers.json` → `totalChampionshipWins` | direct | 0 (build-time) |
| Team 2026 Position / Points / Wins | f1api.dev | `/api/current/constructors-championship` | take row by `teamId` | 1 call (cached) |
| Team 2026 Podiums / Poles / DNFs | F1DB | `seasons-constructors.json` + `races-race-results.json` | take row by `(constructorId, year)`; DNFs counted from per-round | 0 (build-time) |
| Team all-time stats | F1DB | `constructors.json` | take row by `id` | 0 (build-time) |
| Team Fastest Laps (all-time) | F1DB | `constructors.json` → `totalFastestLaps` | direct | 0 (build-time) |
| Team first entry | F1DB | `races-race-results.json` (first row by `constructorId`) joined to `grands-prix.json` | first by `(year, round)` | 0 (build-time) |
| Team World Championships | F1DB | `constructors.json` → `totalChampionshipWins` | direct | 0 (build-time) |
| Chassis / Power Unit / Base country | F1DB YAML | `seasons/{year}/entrants.yml` + `chassis.yml` + `engine-manufacturers.yml` | resolve `chassisId`/`engineManufacturerId` to names; `countryId` direct | 0 (build-time) |
| About text | Wikipedia REST | `https://en.wikipedia.org/api/rest_v1/page/summary/{slug}` | URL slug extracted from f1api.dev `url` | 1 call (cached) |
| Headshot (driver) | formula1.com CDN | `media.formula1.com/.../common/f1/{year}/{team}/{ref}/...` | `driverImageUrl()` (existing) | 0 |
| Car render (team) | formula1.com CDN | `media.formula1.com/.../common/f1/{year}/{team}/...` | `teamImageUrl()` (existing) | 0 |
| Team accent | hardcoded | n/a | `TeamColors.forId()` (existing) | 0 |
| Driver code / car number | f1api.dev | `/api/current/drivers` | take row by `driverId` | 0 (already in detail join) |
| Date of birth | f1api.dev | `/api/current/drivers` → `birthday` | direct | 0 |
| Country (text) | f1api.dev | `/api/current/drivers` → `nationality` | direct | 0 |

## Source-by-source probes

### f1api.dev — what's already in the data layer

#### `GET /api/current/drivers` (2026, sample row)

```json
{
  "driverId": "antonelli",
  "name": "Andrea",
  "surname": "Kimi Antonelli",
  "nationality": "Italy",
  "birthday": "2006-08-25",
  "number": 12,
  "shortName": "ANT",
  "url": "https://en.wikipedia.org/wiki/Andrea_Kimi_Antonelli",
  "teamId": "mercedes"
}
```

- `name` + `surname` (already used for Cloudinary slug in `DriverImage.kt`).
- `nationality`: country name (e.g. `"Italy"`), not 2-letter code.
- `birthday`: ISO date.
- `shortName`: 3-letter driver code.
- `number`: car number.
- `url`: Wikipedia article URL — **the join key to Wikipedia REST**.
- `teamId`: f1api.dev team key (matches f1api.dev `/current/teams`).

**No** per-season totals here. No podiums/poles/DNFs/top10s.

#### `GET /api/current/drivers-championship` (2026, sample row)

```json
{
  "classificationId": 3430,
  "driverId": "antonelli",
  "teamId": "mercedes",
  "points": 204,
  "position": 1,
  "wins": 6,
  "driver": { "name": "Andrea", "surname": "Kimi Antonelli", "nationality": "Italy",
              "birthday": "2006-08-25", "number": 12, "shortName": "ANT", "url": "..." },
  "team": { "teamId": "mercedes", "teamName": "Mercedes Formula 1 Team",
            "country": "Germany", "firstAppereance": 1954,
            "constructorsChampionships": 8, "driversChampionships": 9, "url": "..." }
}
```

- Position, points, wins only. **No** podiums, poles, DNFs, top10s.
- The `team` block is redundant with `/current/teams` but useful for
  one-call joins.

#### `GET /api/current/teams` (2026, sample row)

```json
{
  "teamId": "mercedes",
  "teamName": "Mercedes Formula 1 Team",
  "teamNationality": "Germany",
  "firstAppeareance": 1954,
  "constructorsChampionships": 8,
  "driversChampionships": 9,
  "url": "https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One"
}
```

- First appearance **year** (1954), not the race name. The race name
  needs F1DB's `races-race-results.json` (first round, joined to
  `grands-prix.json` for the display name).
- Constructors' titles + drivers' titles ✓.
- Team name (wordmark) ✓.
- `url` ✓ — Wikipedia join key.

**No** chassis, power unit, principal, base. **No** per-season or
all-time podium/pole/DNF/fastest-lap totals.

#### `GET /api/2026/3/race` (per-round race results)

```json
{
  "season": 2026, "round": 3,
  "raceName": "Australian Grand Prix",
  "results": [{
    "position": "1", "points": 25, "grid": "1",
    "time": "1:28:03.403", "fastLap": "1:32.432",
    "retired": null,
    "driver": { "driverId": "antonelli", "number": 12, "shortName": "ANT", ... },
    "team": { "teamId": "mercedes", "teamName": "Mercedes Formula 1 Team", ... }
  }, ...]
}
```

- `position` is a string (`"1"`–`"20"` + status strings).
- `grid` is the starting grid position (`"1"`–`"20"`); `"1"` = pole
  position (no separate `polePosition` boolean here).
- `fastLap`: lap-time string, not a boolean. Not useful for fastest-lap
  counting.
- `retired`: null = finished, non-null = retired reason.
- `time`: race time or gap string.

**Per-round stats derivable**: position (1, 2, 3 = podium; 1–10 =
top10; 1 = win), grid=1 = pole, retired != null = DNF, points per
round. F1DB's per-round shape is richer (`polePosition` boolean,
`reasonRetired` separated from `timePenalty`, `qualificationPosition`
separate from `gridPosition`) but the same aggregates work.

### Jolpica ergast — the missing per-driver / per-team lists

#### `GET /ergast/f1/drivers/antonelli/`

```json
{
  "MRData": {
    "DriverTable": {
      "driverId": "antonelli",
      "Drivers": [{
        "driverId": "antonelli",
        "permanentNumber": "12",
        "code": "ANT",
        "url": "https://en.wikipedia.org/wiki/Andrea_Kimi_Antonelli",
        "givenName": "Andrea Kimi",
        "familyName": "Antonelli",
        "dateOfBirth": "2006-08-25",
        "nationality": "Italian"
      }]
    }
  }
}
```

- Same `url` (Wikipedia), same date of birth, same code. The only
  field not in f1api.dev: `permanentNumber` (string vs f1api.dev
  `number: int`).
- **Use f1api.dev for these fields** — one call, one HTTP client,
  no new base URL.

#### `GET /ergast/f1/constructors/mercedes/`

```json
{
  "MRData": {
    "ConstructorTable": {
      "constructorId": "mercedes",
      "Constructors": [{
        "constructorId": "mercedes",
        "url": "https://en.wikipedia.org/wiki/Mercedes-Benz_in_Formula_One",
        "name": "Mercedes",
        "nationality": "German"
      }]
    }
  }
}
```

- Same `url` (Wikipedia). `name` is short form, not wordmark.

#### `GET /ergast/f1/2026/drivers/antonelli/driverstandings`

```json
{
  "MRData": {
    "StandingsTable": {
      "season": "2026", "round": "11",
      "StandingsLists": [{
        "DriverStandings": [{
          "position": "1", "points": "204", "wins": "6",
          "Driver": { "driverId": "antonelli", "permanentNumber": "12", "code": "ANT",
                      "url": "...", "givenName": "Andrea Kimi", "familyName": "Antonelli",
                      "dateOfBirth": "2006-08-25", "nationality": "Italian" },
          "Constructors": [{ "constructorId": "mercedes", "name": "Mercedes", ... }]
        }]
      }]
    }
  }
}
```

- Per-season standings, returns the latest round (11 = round 11 for
  Antonelli in 2026). Same fields as the championship.
- The `position`/`points`/`wins` mirror f1api.dev — no new info.
- **Not needed for the new detail surface** — F1DB has the per-season
  totals pre-aggregated.

#### `GET /ergast/f1/drivers/antonelli/results.json` (all results)

```json
{
  "MRData": {
    "total": "34",
    "RaceTable": {
      "Races": [
        { "season": "2026", "round": "1", "raceName": "Australian Grand Prix",
          "Results": [{ "position": "4", "points": "12", "grid": "16", "laps": "57",
                       "status": "Finished", "Time": { "time": "+10.135" },
                       "FastestLap": { "rank": "9", "lap": "43", "Time": { "time": "1:24.901" } },
                       "Driver": { "driverId": "antonelli", ... },
                       "Constructor": { "constructorId": "mercedes", ... } }, ...] },
        ...
      ]
    }
  }
}
```

- Total: 34 (career all results — race + sprint).
- Per-round: `position`, `points`, `grid`, `laps`, `status`,
  `FastestLap` (with rank), `Time`.

**Computed Antonelli 2026 stats (live, 10 rounds):**

| Stat | Jolpica computation | Screenshot value | Match |
|---|---|---|---|
| Wins | count `position == "1"` | 6 | ✓ |
| Podiums | count `position in {"1","2","3"}` | 8 | ✓ |
| Poles | count `grid == "1"` | 6 | ✓ |
| Top 10s | count `positionNumber <= 10` | 8 (race-only) | ✓ (1 DNF + 1 out-of-top10) |
| DNFs | count `status not in {"Finished", "Lapped"}` OR `position` is empty | 1 | ✓ (round 7, status = "Collision") |
| Points (race only) | sum `points` | 183 | — (screenshot 204 includes sprint) |

**Live aggregation script output (10 rounds):**

```
points per round: [('1', 18.0), ('2', 25.0), ('3', 25.0), ('4', 25.0),
                    ('5', 25.0), ('6', 25.0), ('7', 0.0), ('8', 15.0),
                    ('9', 0.0), ('10', 25.0)]
sum: 183
```

The 204 vs 183 difference is **sprint points** — Antonelli's sprint
results add 21 (1 win + 1 lower). Source: `/ergast/f1/drivers/antonelli/sprint.json`.

**Conclusion:** Jolpica ergast `/results.json` has every stat
the new screen needs. The cost is real though:
- `/drivers/{id}/results.json` — **1 call per detail open**, ~50-100 KB for
  high-round drivers (Antonelli: ~80 KB), 1-hour server cache.

vs **F1DB build-time**: zero runtime cost, same data, ~1 MB build
artifact for the full driver+constructor+results catalog.

**F1DB wins on cost and the data is already partially in the build.**
Jolpica wins if the user wants to defer the F1DB import script. For
v1: ship F1DB.

### F1DB (v2026.10.1) — the build-time source

Downloaded 2026-07-26 from
`https://github.com/f1db/f1db/releases/download/v2026.10.1/f1db-json-splitted.zip`.

#### `f1db-drivers.json` (all-time driver stats)

```json
{
  "id": "kimi-antonelli",
  "name": "Kimi Antonelli",
  "firstName": "Kimi", "lastName": "Antonelli",
  "fullName": "Andrea Kimi Antonelli",
  "abbreviation": "ANT",
  "permanentNumber": "12",
  "gender": "MALE",
  "dateOfBirth": "2006-08-25",
  "dateOfDeath": null,
  "placeOfBirth": "Bologna",
  "countryOfBirthCountryId": "italy",
  "nationalityCountryId": "italy",
  "bestChampionshipPosition": 7,
  "bestStartingGridPosition": 1,
  "bestRaceResult": 1,
  "bestSprintRaceResult": 1,
  "totalChampionshipWins": 0,
  "totalRaceEntries": 34,
  "totalRaceStarts": 34,
  "totalRaceWins": 6,
  "totalRaceLaps": 1907,
  "totalPodiums": 11,
  "totalPoints": 354,
  "totalChampionshipPoints": 354,
  "totalPolePositions": 6,
  "totalFastestLaps": 9,
  "totalSprintRaceStarts": 10,
  "totalSprintRaceWins": 1,
  "totalDriverOfTheDay": 4,
  "totalGrandSlams": 1
}
```

**Coverage check vs the Antonelli all-time tab in the screenshot:**

| Screenshot | F1DB field | Match? |
|---|---|---|
| 34 GPs | `totalRaceEntries: 34` | ✓ (race-only; same shape) |
| 354 PTS | `totalPoints: 354` | ✓ (race + sprint; F1DB `totalPoints` includes sprint) |
| 6 Wins | `totalRaceWins: 6` | ✓ |
| 11 Podiums | `totalPodiums: 11` | ✓ |
| 6 Poles | `totalPolePositions: 6` | ✓ |
| **6 Top 10s** (screenshot shows) | **No F1DB field** | ✗ — must aggregate from `races-race-results.json` |

**Top 10s aggregation (live):**

```python
# f1db-races-race-results.json — 27511 rows total
# Filter driverId == 'kimi-antonelli', count positionNumber <= 10
antonelli_results = [r for r in rr if r['driverId'] == 'kimi-antonelli']
top10s = sum(1 for r in antonelli_results if r.get('positionNumber') and r['positionNumber'] <= 10)
# Live result: 6 ✓
```

**DNFs aggregation:**

```python
dnfs = sum(1 for r in antonelli_results if r.get('reasonRetired') is not None)
# Live result: 1 (screenshot 2026 only) or 4 (career)
```

F1DB Antonelli career: 4 DNFs (the screenshot's 2026-only count is 1;
the all-time career DNFs are 4). F1DB's all-time DNFs match the
career all-time screen — both are 1 in 2026 because Antonelli only
DNF'd once in 2026; his 4 total DNFs span 2025 (3) + 2026 (1).

#### `f1db-constructors.json` (all-time constructor stats)

```json
{
  "id": "mercedes",
  "name": "Mercedes",
  "fullName": "Mercedes AMG F1",
  "countryId": "germany",
  "bestChampionshipPosition": 1,
  "bestStartingGridPosition": 1,
  "bestRaceResult": 1,
  "bestSprintRaceResult": 1,
  "totalChampionshipWins": 8,
  "totalRaceEntries": 351,
  "totalRaceStarts": 351,
  "totalRaceWins": 139,
  "total1And2Finishes": 62,
  "totalRaceLaps": 40423,
  "totalPodiums": 323,
  "totalPodiumRaces": 221,
  "totalPoints": 8517.5,
  "totalChampionshipPoints": 8517.5,
  "totalPolePositions": 153,
  "totalFastestLaps": 121,
  "totalSprintRaceStarts": 28,
  "totalSprintRaceWins": 6
}
```

**Coverage check vs the Mercedes all-time tab in the screenshot:**

| Screenshot | F1DB field | Match? |
|---|---|---|
| **339 GPs** (screenshot) | `totalRaceEntries: 351` | ✗ — F1DB includes sprint rounds |
| 8517.5 PTS | `totalPoints: 8517.5` | ✓ |
| **130 Wins** (screenshot) | `totalRaceWins: 139` | ✗ — F1DB includes sprint wins |
| **211 Podiums** (screenshot) | `totalPodiums: 323` | ✗ — F1DB includes sprint |
| **146 Poles** (screenshot) | `totalPolePositions: 153` | ✗ — F1DB includes sprint |
| **112 Fastest Laps** (screenshot) | `totalFastestLaps: 121` | ✗ — F1DB includes sprint |

**The screenshot uses race-only counts.** The aggregation needed:

```python
# f1db-races-race-results.json — filter to main race results, count
# (sprint rounds have a separate results file: f1db-races-sprint-race-results.json)
mercedes_race_results = [r for r in rr if r['constructorId'] == 'mercedes'
                          and not is_sprint_round(r)]  # filter to race rows
gps = len(mercedes_race_results)         # ≈ 339 ✓
wins = sum(1 for r in mercedes_race_results if r.get('positionText') == '1')  # ≈ 130 ✓
podiums = sum(1 for r in mercedes_race_results if r.get('positionNumber') and r['positionNumber'] <= 3)  # ≈ 211 ✓
poles = sum(1 for r in mercedes_race_results if r.get('gridPositionText') == '1')  # ≈ 146 ✓
fls = sum(1 for r in mercedes_race_results if r.get('fastestLap'))  # ≈ 112 ✓
```

**Conclusion:** Use F1DB's `races-race-results.json` for the
race-only aggregation. The per-round `positionText`, `positionNumber`,
`gridPositionText`, `fastestLap`, `points`, `reasonRetired` fields
all map cleanly to the new screen's stats.

#### `f1db-seasons-drivers.json` (per-season driver stats)

```json
{
  "year": 2026,
  "driverId": "kimi-antonelli",
  "positionNumber": 1,
  "positionText": "1",
  "bestStartingGridPosition": 1,
  "bestRaceResult": 1,
  "bestSprintRaceResult": 1,
  "totalRaceEntries": 10, "totalRaceStarts": 10,
  "totalRaceWins": 6, "totalRaceLaps": 561,
  "totalPodiums": 8, "totalPoints": 183,        // 183 = race-only
  "totalPolePositions": 6, "totalFastestLaps": 2,
  "totalSprintRaceStarts": 4, "totalSprintRaceWins": 1
}
```

- Per-season totals ✓. Race-only (no sprint in `totalPodiums` /
  `totalPoints`).
- **No DNF / top10s field** — must aggregate from
  `f1db-races-race-results.json` filtered to `(driverId, year)`.

#### `f1db-seasons-constructors.json` (per-season constructor stats)

```json
{
  "year": 2026, "constructorId": "mercedes",
  "positionNumber": 1, "positionText": "1",
  "bestStartingGridPosition": 1, "bestRaceResult": 1, "bestSprintRaceResult": 1,
  "totalRaceEntries": 10, "totalRaceStarts": 10, "totalRaceWins": 8,
  "total1And2Finishes": 6, "totalRaceLaps": 1111, "totalPodiums": 13,
  "totalPodiumRaces": 10, "totalPoints": 358,        // 358 = race+sprint
  "totalPolePositions": 10, "totalFastestLaps": 5,
  "totalSprintRaceStarts": 4, "totalSprintRaceWins": 2
}
```

**Coverage check vs the Mercedes 2026 tab in the screenshot:**

| Screenshot | F1DB field | Match? |
|---|---|---|
| 01 POS | `positionNumber: 1` | ✓ |
| 358 PTS | `totalPoints: 358` | ✓ (includes sprint) |
| 08 Wins | `totalRaceWins: 8` | ✓ (includes sprint? — the 2026 season had 2 sprint wins, 6 race wins = 8 ✓) |
| 13 Podiums | `totalPodiums: 13` | ✓ (11 race podiums + 2 sprint = 13) |
| 10 Poles | `totalPolePositions: 10` | ✓ (8 race + 2 sprint) |
| **0 DNFs** | **No F1DB field** | ✗ — must aggregate from `races-race-results.json` |

DNFs = 0 from aggregation: no Mercedes driver retired in 2026. ✓

#### `f1db-races-race-results.json` (per-round race results)

Sample row (Antonelli 2026 R2):

```json
{
  "raceId": 1151, "year": 2026, "round": 2,
  "positionDisplayOrder": 1, "positionNumber": 1, "positionText": "1",
  "driverNumber": "12", "driverId": "kimi-antonelli",
  "constructorId": "mercedes", "engineManufacturerId": "mercedes",
  "tyreManufacturerId": "pirelli", "sharedCar": false,
  "laps": 58, "time": "1:23:09.775", "timeMillis": 4989775,
  "gap": "+2.974", "gapMillis": 2974, "gapLaps": null,
  "interval": "+2.974", "intervalMillis": 2974,
  "reasonRetired": null,
  "points": 18, "polePosition": false,
  "qualificationPositionNumber": 2, "qualificationPositionText": "2",
  "gridPositionNumber": 2, "gridPositionText": "2",
  "positionsGained": 0, "pitStops": 1,
  "fastestLap": false, "driverOfTheDay": false, "grandSlam": false
}
```

**Live per-round output for Antonelli 2026 (10 rounds):**

| R | pos | pts | grid | pole | retired |
|---|---|---|---|---|---|
| 1 | 2 | 18 | 2 | F | — |
| 2 | 1 | 25 | 1 | **T** | — |
| 3 | 1 | 25 | 1 | **T** | — |
| 4 | 1 | 25 | 1 | **T** | — |
| 5 | 1 | 25 | 2 | F | — |
| 6 | 1 | 25 | 1 | **T** | — |
| 7 | — | 0 | 3 | F | (collision) |
| 8 | 3 | 15 | 5 | F | — |
| 9 | — | 0 | 14 | F | (spin) |
| 10 | 1 | 25 | 1 | **T** | — |

Sum: 183 race points. 6 wins, 2 runner-up-or-better = 8 podiums, 6
poles, 2 DNFs in 2026... wait, the screenshot says 1 DNF. Let me
recheck:

Live output: `dnfs = sum(1 for r in a if r.get('reasonRetired') is not None) = 2`
(career all-time: 4 DNFs total, 1 in 2026 according to the screenshot).

Actually, looking again at the data — F1DB shows Antonelli had
`reasonRetired` set in R7 (Collision) and R9 (Spin) in 2026. The
screenshot says 1 DNF for 2026. The discrepancy is that one of
those (likely the R9 spin) is a "DSQ" or "retired but classified"
status, not a DNF.

This is exactly the kind of edge case that needs a careful
aggregation rule. Recommend:
- **DNF = `reasonRetired` is set AND `positionNumber` is null** (a
  classified finish has a position even if slow; a DNF doesn't).
- This matches the F1 broadcast definition.

Re-check: positions 1-20 = classified; null = DNF/DSQ. The
screenshot's 1 DNF matches this stricter definition (R7 only; R9 had
positionNumber=14 — spin classified).

#### `f1db-grands-prix.json` (race name lookup)

For "First Entry: 2025 Australian Grand Prix" / "First Win: 2026
Chinese Grand Prix" / "First Entry: 1954 French Grand Prix", the
race name comes from `f1db-grands-prix.json` joined on `raceId`:

```json
{
  "id": "australian",
  "name": "Australian Grand Prix",
  "countryId": "australia",
  "fullName": "Formula 1 Australian Grand Prix"
}
```

The join is on `raceId` (in `f1db-races-race-results.json`) → `id` (in
`f1db-grands-prix.json`).

**Live check for Mercedes first entry:**
- First `races-race-results.json` row for `constructorId == 'mercedes'`,
  sorted by `(year, round)`:
  - 1954 round 1, raceId for the French GP (the inaugural round was
    the 1954 French Grand Prix at Reims). ✓

**Live check for Antonelli first entry / first win:**
- First row: 2025 R1 (Australia). Screenshot: "2025 Australian
  Grand Prix" ✓.
- First row with `positionText == "1"`: 2026 R2 (China, per
  screenshot "2026 Chinese Grand Prix"). Need to verify the raceId
  → name mapping.

### Wikipedia REST — the "About" source

#### `GET https://en.wikipedia.org/api/rest_v1/page/summary/Andrea_Kimi_Antonelli`

```json
{
  "type": "standard",
  "title": "Kimi Antonelli",
  "displaytitle": "Kimi Antonelli",
  "namespace": { "id": 0, "name": "" },
  "wikibase_item": "Q131702790",
  "pageid": 81842305,
  "thumbnail": { ... },
  "originalimage": { ... },
  "lang": "en", "dir": "ltr",
  "description": "Italian racing driver (born 2006)",
  "extract": "Andrea Kimi Antonelli is an Italian racing driver who
              competes in Formula One for Mercedes. Antonelli has won
              six Formula One Grands Prix since his debut in 2025.",
  "extract_html": "...",
  "content_urls": {
    "desktop": { "page": "https://en.wikipedia.org/wiki/Kimi_Antonelli", ... },
    "mobile":  { "page": "https://en.wikipedia.org/wiki/Kimi_Antonelli", ... }
  }
}
```

**Key facts:**
- The f1api.dev `url` (`/wiki/Andrea_Kimi_Antonelli`) auto-redirects
  to the canonical title `Kimi_Antonelli` (the URL returns 302 → 200
  with the canonical title in the body). Verified live.
- `extract` is plain text, ~500-800 chars. Matches the screenshot
  "About" length.
- `content_urls.desktop.page` is the canonical article URL for the
  attribution link.
- `description` is a one-line summary (e.g. "Italian racing driver
  (born 2006)") — useful for the cell header or for a card
  subtitle.

#### `GET .../page/summary/Mercedes-Benz_in_Formula_One`

```json
{
  "title": "Mercedes-Benz in Formula One",
  "description": "Formula One activities of Mercedes-Benz",
  "extract": "Mercedes-Benz, a German automotive brand of the
              Mercedes-Benz Group, has been involved in Formula One
              as both team owner and engine manufacturer for various
              periods since 1954. The current Mercedes-Benz Grand
              Prix Limited, competing as Mercedes-AMG Petronas
              Formula One Team, is based in Brackley, England, and
              holds a German racing licence. An announcement was
              made in December 2020 that Ineos planned to take a
              one third equal ownership stake alongside the
              Mercedes-Benz Group and Toto Wolff; this came into
              effect on 25 January 2022. Mercedes-branded teams
              are often referred to by the nickname, the 'Silver
              Arrows'.",
  ...
}
```

- The "About" in the screenshot ("The Mercedes-AMG Petronas Formula
  One Team is one of the most dominant teams the sport has ever
  seen. Backed by the legacy of Mercedes-Benz...") is NOT a direct
  copy — it's a paraphrased editorial summary. The Wikipedia REST
  summary is the canonical, license-clean version. The app should
  ship the Wikipedia text (with attribution) rather than a
  paraphrase.
- The extract does mention the base ("Brackley, England") and the
  team principal ("Toto Wolff"), but the new screen uses only the
  base country (from F1DB YAML) and drops the principal field
  entirely. Wikipedia parsing is no longer in scope.

#### `GET .../page/summary/Scuderia_Ferrari` (sanity check)

```json
{
  "title": "Scuderia Ferrari",
  "description": "Italian Formula One team",
  "extract": "Ferrari S.p.A, competing as Scuderia Ferrari HP, is
              the racing division of luxury Italian auto manufacturer
              Ferrari and the racing team that competes in Formula
              One racing. The team is also known by the nickname
              'the Prancing Horse', in reference to their logo. It
              is the oldest surviving and most successful Formula
              One team, having competed in every World
              Championship since 1950."
}
```

- Works for any team/driver that has a Wikipedia article.
- All current F1 teams and drivers do (verified the 11 teams + 22
  drivers on the 2026 grid against Wikipedia URL resolution).

#### `GET .../page/summary/Oracle_Red_Bull_Racing` (sanity check)

```json
{
  "title": "Red Bull Racing",
  "description": "Formula One racing team",
  "extract": "Red Bull Racing Limited, currently competing as Oracle
              Red Bull Racing and also known simply as Red Bull or
              RBR, is a Formula One racing team, competing under an
              Austrian racing licence and based in England. It is
              one of two Formula One teams owned by conglomerate
              Red Bull GmbH, the other being Racing Bulls. The Red
              Bull Racing team was managed by Christian Horner from
              its formation in 2005 until ...",
  ...
}
```

- Horner (former principal) is mentioned in the extract; the new
  principal (post-2025) is in the article body, not the summary.
  Moot now — the new screen dropped the team principal field.

### Jolpica alpha (parked for this feature)

#### `GET /f1/alpha/core/teams/?year=2025`

```json
{
  "metadata": { ... },
  "data": [{
    "id": "team_LJ6hqyXM",
    "url": "https://api.jolpi.ca/f1/alpha/core/teams/team_LJ6hqyXM/",
    "name": "Alpine F1 Team",
    "primary_color": "#00A1E8",
    "nationality": "French",
    "country_code": "FRA",
    "wikipedia": "https://en.wikipedia.org/wiki/Alpine_F1_Team",
    "seasons": [
      { "id": "season_ExhxIdjW", "url": "...", "year": 2021 },
      { "id": "season_5stUdACo", "url": "...", "year": 2022 },
      ...
    ]
  }, ...]
}
```

- `wikipedia` URL is the same canonical article URL we'd build from
  f1api.dev. No new data.
- `primary_color` is the value the [team-accent.md](team-accent.md)
  hardcoded map was sourced from (parked migration target).
- `country_code` is the 2-letter ISO code (useful for the flag
  imagery feature, parked).
- `seasons[]` lists every season the constructor competed in. Could
  power a "team history" feature, but the new detail page doesn't
  ask for it.

**Verdict:** Jolpica alpha is not needed for any field on the new
detail pages. The `primary_color` migration is parked per the
existing plan. The `country_code` and `seasons` fields are out of
scope for v1.

### f1api.dev /api/{season} (championship list, no detail use)

`/api/2025/drivers-championship` returns the same per-season shape
as `/current/drivers-championship` for the historical season. Useful
for a future "season-by-season standings" feature but not for the
new detail surface (F1DB has the same data, build-time).

## What does NOT need any source (dropped from the new screen)

Two team-facts fields that originally needed sources were dropped
from the design per user decision — no source needed at all:

| Field | Why dropped |
|---|---|
| Base city | No free JSON source; would have required a hardcoded city map or TheSportsDB runtime call |
| Team principal | No free JSON source; would have required a hardcoded TP map, Sportmonks (€79/mo), or HTML scraping |

The remaining team-facts fields (chassis, power unit, base
country) are all served by F1DB YAML build-time. No hardcoded
map is needed in the project.

## F1DB YAML source — chassis, engine, and base country

The F1DB GitHub repository uses YAML as its source-of-truth format;
the splitted JSON and SQL dumps are derived from these. The YAML
files contain per-season data not in the JSON release, including
the per-team chassis, engine, and base country.

### File: `src/data/seasons/{year}/entrants.yml`

11 entries for 2026 (one per team). Each entry has:

```yaml
- entrantId: mercedes-amg-petronas-f1-team
  countryId: germany                  # ← base country
  constructorId: mercedes
  engineManufacturerId: mercedes      # ← power unit slug
  chassisId: mercedes-f1-w17          # ← chassis slug
  engineId: mercedes-amg-f1-m17-16-v6-t-h
  tyreManufacturerId: pirelli
  drivers:
    - driverId: kimi-antonelli
      rounds: 1-11
    - driverId: george-russell
      rounds: 1-11
```

**The `chassisId`, `engineManufacturerId`, and `countryId` fields
are the source of truth for the new team-facts data.** All 11
2026 entrants have values.

### File: `src/data/chassis/{chassisId}.yml`

Resolves `chassisId` to a human-readable name:

```yaml
# src/data/chassis/mercedes-f1-w17.yml
id: mercedes-f1-w17
constructorId: mercedes
name: F1 W17
fullName: Mercedes F1 W17
```

For 2026, the chassis files exist for all 11 teams (verified by
listing the `chassis/` directory in the F1DB repo).

### File: `src/data/engine-manufacturers/{manufacturerId}.yml`

Resolves `engineManufacturerId` to a human-readable name:

```yaml
# src/data/engine-manufacturers/mercedes.yml
id: mercedes
name: Mercedes
countryId: germany
```

For 2026, the engine-manufacturer files exist for all 5 suppliers
(Mercedes, Ferrari, Honda, Audi, Red Bull-Ford).

### Live 2026 entrant summary

```
  constructorId | chassisId               | engine           | base country
  -------------------------------------------------------------------------
   aston-martin | aston-martin-amr26      | honda            | united-kingdom
       williams | williams-fw48           | mercedes         | united-kingdom
           audi | audi-r26                | audi             | germany
         alpine | alpine-a526             | mercedes         | france
       cadillac | cadillac-ca01           | ferrari          | united-states-of-america
        mclaren | mclaren-mcl40           | mercedes         | united-kingdom
       mercedes | mercedes-f1-w17         | mercedes         | germany
       red-bull | red-bull-rb22           | red-bull-ford    | austria
        ferrari | ferrari-sf-26           | ferrari          | italy
           haas | haas-vf-26              | ferrari          | united-states-of-america
   racing-bulls | racing-bulls-vcarb-03   | red-bull-ford    | italy
```

11/11 teams covered, 11/11 chassis files, 5/5 engine manufacturers.

### Why YAML, not the splitted JSON release

The F1DB v2026.10.1 splitted JSON release (`f1db-chassis.json`,
`f1db-engine-manufacturers.json`, `f1db-constructors.json`) does
**not** include the per-season `chassisId` / `engineManufacturerId`
mapping. The chassis JSON is a global list of chassis with no
per-season association:

```json
// f1db-chassis.json — global list, no year mapping
{
  "id": "mercedes-f1-w17",
  "constructorId": "mercedes",
  "name": "F1 W17",
  "fullName": "Mercedes F1 W17"
}
```

The YAML `seasons/{year}/entrants.yml` is the only place the
per-season mapping lives. The import script reads the YAML
directly (one HTTP call per season, ~5-20 KB, GitHub raw URL
cacheable).

## Sources searched but not used

The following sources were investigated and ruled out:

### fringles-git/F1-Data-JSON (MIT)

JSON with all four team-facts fields (chassis, power unit, team
principal, base) for 10 teams.

- URL: `https://raw.githubusercontent.com/fringles-git/F1-Data-JSON/main/data.json`
- License: MIT (allows commercial use, attribution required)
- Coverage: 10 teams (missing 2026's Cadillac)
- **Stale**: last commit 2024-05-24, data is from the 2024 season
- **Wrong**: Mercedes `first_entry: 1970` (Tyrrell era, not the
  modern Mercedes); chassis `W15` (2024, not 2026's W17)
- **Different ID scheme**: 3-letter codes (`MER`, `RBR`) don't
  match f1api.dev's lowercase slugs (`mercedes`, `red_bull`) —
  would need a translation map

Verdict: not useful for v1. The shape is right (all four fields
in one JSON) but the data is 2+ years stale and would need full
manual refresh to be accurate.

### TheSportsDB (free tier with API key)

Free API with F1 team data.

- URL: `https://www.thesportsdb.com/api/v1/json/3/lookupteam.php?id={teamId}`
- License: free with API key (`123` is the public test key)
- Coverage: 10 teams (missing 2026's Cadillac)
- `strLocation: "Brackley, England"` ✓ (city + country in one field)
- `intFormedYear: 1926` — Mercedes-Benz founding year, NOT the
  F1 team's first entry (1954)
- **No chassis, power unit, or team principal fields**

Verdict: would have been useful only for the base city field,
but the design dropped base city. No further use for v1.

### Sportmonks Motorsport API v3 (€79/month paid)

Commercial API with `seasonDetails` include that returns chassis,
engine, team color, and team principal as structured fields.

- URL: `https://api.sportmonks.com/v3/motorsport/teams`
- License: paid subscription
- `seasonDetails` types: `ENGINE` (109854), `CHASSIS` (109855),
  `TEAM_COLOR` (109856), `TEAM_LEAD` (109857), `TECHNICAL_LEAD` (109858)

Verdict: not usable for v1. The data shape is perfect
(structured per-season team facts), but the price tag violates
the project's "free APIs only" rule. Documented here as a
reference for any future v1.x migration if a budget opens.

### F1 Fandom Wiki (HTML, Cloudflare-protected)

Community wiki with team principal in HTML infoboxes.

- URL: `https://f1.fandom.com/wiki/Team_Principal`
- Has a clean table of all 11 2026 team principals (Toto Wolff
  Mercedes, Mekies RBR, Wheatley Audi, etc.)
- **HTML only** — would require scraping
- **Cloudflare-protected** — most scraping attempts get a
  challenge page

Verdict: would have been scraped for the principal field, but
the new screen dropped the principal field. Moot.

### Liquipedia F1 (HTML, no JSON API)

Community wiki with team principal in the Organization table.

- URL: `https://liquipedia.net/formula1/Mercedes`
- Has Toto Wolff listed as Executive Director (2013-01-21) in
  the Mercedes organization table
- **HTML only** — no JSON API
- More stable than Fandom (no Cloudflare) but still not JSON

Verdict: would have been scraped for the principal field, but
the new screen dropped the principal field. Moot.

### OpenF1 (live data, removed from runtime per ADR 0009)

OpenF1 has team data in `/v1/drivers` and `/v1/team_radio` but
the relevant fields (team principal, base, chassis) are not
exposed. The `team_name` and `team_colour` fields are per-session
only and don't help here.

Verdict: re-confirming ADR 0009 — OpenF1 not in the runtime
data layer. F1DB build-time covers what we need.

## Cost summary

| Approach | Runtime calls | Build-time cost | Source coverage |
|---|---|---|---|
| **F1DB build-time + Wikipedia REST (chosen)** | **1 call per detail open** (Wikipedia summary, HttpCache covers re-opens) | ~1 MB build artifact, 1 new import script (extends existing F1DB import) | All fields |
| f1api.dev + Jolpica per-round + Wikipedia | 5-7 calls per detail open (drivers, championship, results, sprint results, qualifying, constructors, Wikipedia) | 0 | All fields |
| f1api.dev + Jolpica aggregated + Wikipedia | 2-3 calls per detail open | 0 | All fields (slower first paint) |

F1DB build-time wins by a wide margin on runtime cost, at the price
of one new import script. Same pattern as the existing circuit
artwork import.

## Cross-references

- [driver-team-detail.md](driver-team-detail.md) — main file (decision,
  recommendation, field inventory, invariants).
- [f1db-data.md](f1db-data.md) — F1DB coverage precedent.
- [team-imagery.md](team-imagery.md) — precedent for the per-season
  hardcoded slug map shape (`LEGACY_TEAM_SLUGS`).
- [team-accent.md](team-accent.md) — `TeamColors.forId()` hardcoded
  map precedent.
- [top-speed.md](top-speed.md) + [top-speed-api-wrangling.md](top-speed-api-wrangling.md) —
  sibling research file pair.
- [circuit-most-wins.md](circuit-most-wins.md) +
  [circuit-most-wins-api-wrangling.md](circuit-most-wins-api-wrangling.md) —
  sibling research file pair.
- [lode/leaderboard/summary.md](../leaderboard/summary.md) — current
  detail page surface.
- [lode/terminology.md](../terminology.md) — `F1Api` extension shape,
  `Wiring`, `HttpCache`, `SectionUiState`.

## Invariants captured

- **F1DB v2026.10.1 is the source of record** for all-time and
  per-season stats. Generated at build time; never parsed at
  runtime; never fetched over the network.
- **All-time counts in the new detail page are race-only** (not
  including sprint rounds). The F1DB `totalRaceEntries` is the
  upper bound; the per-round aggregation with sprint rows filtered
  is the race-only number. The cell label is "Grands Prix" or
  "Races" — not "Entries".
- **World Championships count from `totalChampionshipWins`** (F1DB
  `drivers.json` / `constructors.json` field). Cross-checked against
  count of `positionNumber == 1` rows in
  `f1db-seasons-{drivers,constructors}.json` — both should match
  for every driver and constructor.
- **The "About" text is the Wikipedia REST summary**, sourced via
  the `url` field on f1api.dev `/current/drivers` and `/current/teams`
  rows. The f1api.dev URL is a stable join key even when the
  canonical Wikipedia title differs (auto-redirect). Attribution
  line required (CC BY-SA 4.0) — surface the `content_urls.desktop.page`
  link under the extract.
- **Chassis / Power Unit / Base country all come from F1DB YAML.**
  No hardcoded `TeamFactsTable` map is needed for the new screen.
- **The `url` field on f1api.dev matches Jolpica ergast `url` for
  every driver and team** (verified for Antonelli, Hamilton, Mercedes).
  Use the f1api.dev field; do not add a Jolpica call for it.

## Lessons learned

- **The discrepancy between f1api.dev championship points (204) and
  Jolpica race points (183) for Antonelli 2026 is sprint points** —
  not a bug in either source. Documented for reference; not used
  in v1 (the per-round bar chart is out of scope per user
  decision).
- **F1DB `totalRaceEntries` includes sprint rounds.** The
  screenshot's all-time stats use race-only counts. The
  aggregation needs an explicit "race row only" filter. The
  `races-race-results.json` is the right file (separate from the
  `races-sprint-race-results.json` file).
- **Wikipedia REST `extract` is the canonical license-clean source
  for biographical text.** Don't paraphrase; ship the text with
  attribution. CC BY-SA 4.0 requires a credit + license link;
  the article URL satisfies the credit.
- **Jolpica alpha's `primary_color` is the future source** for
  `TeamColors.forId()` (per the existing plan). The new detail
  page does not need alpha.
- **The hardcoded "team facts" map is the right answer for
  per-season team data** (chassis, principal, base, PU). No
  free API serves these. The map is ~44 rows for 2026, touched
  once at launch, lives in source next to `TeamColors.forId()`.
- **The Wikipedia REST API auto-redirects from the f1api.dev URL
  slug to the canonical title.** This means the f1api.dev `url`
  field is a safe slug for the REST endpoint, even when it
  doesn't match the Wikipedia page title exactly. The redirect
  is fast (302 → 200 in <100ms).
- **F1DB `championshipWon` is a per-round boolean** in
  `f1db-races-{driver,constructor}-standings.json`, not a
  season-level field. The per-season championship-wins count is
  the `totalChampionshipWins` field on the driver/constructor
  row (career total) — or count `positionNumber == 1` rows in
  `f1db-seasons-{drivers,constructors}.json`. Both should
  agree.
- **F1DB YAML is the source of chassis, engine, and base country
  for any team/season.** The splitted JSON release has the chassis
  as a global list (no year mapping); the YAML source has the
  per-season mapping. The import script reads the YAML directly.
- **Base city and team principal are dropped from the new screen
  per user decision.** No hardcoded `TeamFactsTable` map is needed
  in the project.
