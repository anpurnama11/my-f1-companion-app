# 0016 — Standings source moved from f1api.dev to Jolpica

**Status:** accepted

## Context

Driver and constructor championship standings were fetched from f1api.dev
(`/current/drivers-championship` and `/current/constructors-championship`).
Live comparison at round 11 of the 2026 season showed Jolpica data was
10–25 points ahead of f1api.dev for the same round, confirming f1api.dev
lagged significantly. The standings source needed to move to Jolpica for
fresher data.

## Decision

Replace f1api.dev championship endpoints with Jolpica Ergast-compatible
endpoints for both driver and constructor standings. The swap touches
three layers: DTOs (new MRData-envelope types), API extensions (new Ktor
client functions), and mappers (string-to-int coercion, Constructors[]
array handling). Detail use cases (`GetDriverDetailUseCase`,
`GetTeamDetailUseCase`) must move in the same commit because they join
standings data with catalog data.

## Field mapping

| Domain field | f1api.dev source | Jolpica source | Conversion |
|---|---|---|---|
| `driverId` | `entry.driverId: String` | `entry.Driver.driverId: String?` | `.orEmpty()` |
| `teamId` | `entry.teamId: String` | `entry.Constructors[0].constructorId: String?` | `.firstOrNull()?.constructorId.orEmpty()` |
| `position: Int` | `Int` | `String` | `.toIntOrNull() ?: 0` |
| `points: Int` | `Int` | `String` | `.toDoubleOrNull()?.toInt() ?: 0` |
| `wins: Int` | `Int` | `String` | `.toIntOrNull() ?: 0` |
| `driverName` | `driver.name + surname` | `Driver.givenName + familyName` | same join pattern |
| `driverShortName` | `driver.shortName` | `Driver.code` | direct |
| `driverNumber: Int?` | `driver.number: Int?` | `Driver.permanentNumber: String?` | `.toIntOrNull()` |
| `teamName` | `team.teamName` | `Constructors[0].name` | direct |
| `country` | `team.country ("Germany")` | `Constructor.nationality ("German")` | passed through — no current consumer formats it |

## Endpoints

| Endpoint | URL |
|---|---|
| Driver standings | `GET {JOLPICA_BASE}/current/driverStandings.json` |
| Constructor standings | `GET {JOLPICA_BASE}/current/constructorStandings.json` |

Where `JOLPICA_BASE = "https://api.jolpi.ca/ergast/f1"` (unchanged).

## Edge cases handled

- **Off-season empty list:** Jolpica returns empty `StandingsLists[]` →
  mapper yields empty list (same behavior as f1api.dev).
- **Multi-team drivers:** `.firstOrNull()` on `Constructors[]` — no worse
  than f1api.dev's single `teamId`.
- **Decimal points (historical):** `.toDoubleOrNull()?.toInt()` floors to
  Int; modern F1 points are always integers.
- **Excluded entries (`"E"` points):** defaults to 0.
- **Missing `givenName`:** falls back to `code` (same pattern as the old
  `shortName` fallback).

## Consequences

- **Domain models unchanged:** `DriverStanding` and `ConstructorStanding`
  keep the same fields and types.
- **ViewModels and screens unchanged:** Use-case lambda signatures
  (`suspend (Boolean) -> Outcome<List<DriverStanding>>`) are identical.
- **Wiring unchanged:** Constructor signatures are identical.
- **Cloudinary headshot derivation unchanged:** `givenName`/`familyName`
  split (Jolpica) produces the same slug as `name`/`surname` (f1api.dev).
- **Driver and team catalog endpoints stay on f1api.dev** (`getCurrentDrivers`,
  `getCurrentTeams`) — only standings moved.

## Reference

- [ADR 0005](0005-session-results-use-two-apis.md) — previous source
  assignment; f1api.dev kept "schedule + catalogs" but this ADR removes
  "championships" from the f1api.dev column.
- [../leaderboard/summary.md](../leaderboard/summary.md) — updated data
  flow diagram.
- [../core/network.md](../core/network.md) — updated extension list.
