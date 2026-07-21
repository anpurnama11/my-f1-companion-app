# Homepage — three-section composition

The Homepage (`Homepage` route, the app start destination) is three sections.
Each section fails independently under the ticket-03 ViewModel composition
(7 use cases via `Flow.onStart{load()} + stateIn(WhileSubscribed(5_000))`,
no composite `GetHomepageDataUseCase`).

```mermaid
flowchart LR
  A[HomepageViewModel] --> S1["§1 Favorite pager"]
  A --> S2["§2 Season progress"]
  A --> S3["§3 Nearest GP info"]
  S1 -.-> UC1[GetDriversStandings + GetConstructorsStandings + GetNextRace + GetRaceWeekendSchedule + GetCircuitImage]
  S2 -.-> UC2[GetSeason]
  S3 -.-> UC3[GetNextRace + GetCircuitTopSpeed]
```

## Section 1 — Favorite pager (sliding)

A swipeable pager of up to four cards: **two favorite drivers, one favorite
team, and one countdown card** for the next race weekend. The pager shows
whatever subset is available; missing favorites just means fewer cards.

- Data: `GetDriversStandingsUseCase` + `GetConstructorsStandingsUseCase`
  (pick the favorited driver/team rows) + `GetNextRaceUseCase` (circuit
  name + accent) + `GetRaceWeekendScheduleUseCase` (the OpenF1 weekend
  schedule — FP1/FP2/FP3/Quali/Sprint/Race with absolute start times) +
  `GetCircuitImageUseCase` (decorative track-layout image from OpenF1
  `/v1/meetings`).
- Implies a **favorites store** — the user picks 2 drivers + 1 team to pin.
  Storage + picker resolved by ticket 12: `FavoritesCache` DataStore (typed
  keys `FAV_DRIVER_1`/`FAV_DRIVER_2`/`FAV_TEAM`); pick/replace surface is the
  **My Team** top-level tab (tap a slot → `ModalBottomSheet`). First-launch
  default seeds #1 constructor + its two drivers. See
  [tickets/12-design-favorites-picker-ux-storage.md](tickets/12-design-favorites-picker-ux-storage.md).
- **Countdown card** (last card in the pager): a decorative circuit
  track-layout image (`AsyncImage` via Coil, loaded from OpenF1's
  `meetings.circuit_image`) on the right side, tinted with the circuit
  brand color (`ColorFilter.tint(..., BlendMode.SrcIn)`), plus the
  circuit name + a big live countdown to the **next upcoming session**
  in the weekend (e.g. FP1 Friday → FP2 Friday → Quali Saturday → Race
  Sunday). The countdown is the most prominent text. Below the countdown
  sits the session label + local time + the GP name. The circuit image is
  best-effort: if OpenF1 has no image for the country, the card renders
  without it. The card ticks every 30s via a `LaunchedEffect` so the value
  is fresh while the user lingers.
  When the entire weekend has started, the card flips to `LIVE` + the
  race session, but only for a 3-hour race window. Once the window has
  passed the card shows `RACE COMPLETE` until `GetNextRaceUseCase` moves
  on to the next GP; when the schedule is still loading or empty, the
  card shows `…` / `—`. OpenF1's `date_start` is parsed to
  `kotlinx.datetime.Instant`; the day name is the enum's `name.take(3)`
  in the device's local timezone (`ponytail:` — minSdk 24 rules out
  `java.time.format.DateTimeFormatter` patterns without
  `coreLibraryDesugaring`; the manual `take(3) + %02d` is one screen's
  worth of strings).
- The Glance Countdown widget (ticket 07) is **independent** of this
  in-app card — the widget renders to the OS home screen with no
  client-side chronometer; the in-app card is a Compose live tick.
  Both pull from the same `GetNextRaceUseCase` / OpenF1 sources.

## Section 2 — Season progress (two vertical blocks)

Left: a **circular progress** showing season-completion percentage. Right:
three cumulative stats — **GP completed, km covered, laps completed**.

- Data: `GetSeasonUseCase` → `Season` model with pre-computed aggregates
  (`completedGp` / `totalKm` / `totalLaps` / `progressPercent`, per ticket 03).
- `totalKm` and `totalLaps` are **client-side sums** over completed rounds
  (`circuitLength` × laps per completed race). Not a free API field.
  `circuitLength` arrives as `"7004km"` on `/current*` — strip non-digits,
  per ticket-03 schema noise. The pre-computation lives in the `Season`
  model mapping, not in a new use case.
- `progressPercent` = `completedGp / scheduledGp`. Circular gauge UI from M3
  or a custom `Canvas` arc — pick at implementation.

## Section 3 — Nearest-date GP info

A card for the next race: **round number, GP name, lap count, corner count,
and the circuit's top speed record**.

- Data: `GetNextRaceUseCase` (round, name, circuit) + a top-speed fetch.
- **Lap count + corner count** — inlined in `GetNextRace`'s race/circuit
  block (`laps: Int`, `circuit.corners: Int`). No extra call.
- **Top speed record** — the live one. f1api.dev has no speed field; the real
  km/h comes from OpenF1 `/v1/laps` `st_speed` (ticket 04 reopened to
  multi-source; ticket 08 closed on this). Requires a `session_key` join.
- New use case: `GetCircuitTopSpeedUseCase(circuitId, year?)` — fetches the
  OpenF1 session for the circuit, then `/v1/laps`, returns `max(st_speed)`.
  Cache strategy: HttpCache (10-min server TTL on OpenF1). The circuit's
  all-time record (vs the latest session's peak) is an open question — see
  [top-speed.md](top-speed.md) for the `st_speed` vs `car_data` trade.

## Use-case surface after this

Ticket 03 named 8 use cases; §3 adds one, §1 adds one:

- `GetCircuitTopSpeedUseCase(circuitId, year?)` — OpenF1 `/v1/sessions` +
  `/v1/laps`, returns peak `st_speed` (km/h). Callers: Homepage §3,
  Round-detail top-speed cell.
- `GetRaceWeekendScheduleUseCase(year, country)` — OpenF1
  `/v1/sessions?year=…&country_name=…` (no `session_name` filter),
  maps + sorts + returns `WeekendSchedule?` (or `null` when the
  weekend is not on the OpenF1 calendar). Reuses the
  `F1API_TO_OPENF1_COUNTRY` fallback for Silverstone. Callers:
  Homepage §1 countdown card. `kotlinx.datetime.Instant` for the
  parsed start time.

§1's favorites store is a DataStore concern, not a use case, until a
picker screen exists.

- `GetCircuitImageUseCase(year, country)` — OpenF1
  `/v1/meetings?year=…&country_name=…`, returns the first
  non-null `circuit_image` URL (or `null` when OpenF1 has no image).
  Reuses the `F1API_TO_OPENF1_COUNTRY` fallback for Silverstone.
  Caller: Homepage §1 countdown card. Loaded via Coil `AsyncImage`.

## Data sources cross-reference

| Section | Source | Endpoints |
|---|---|---|
| §1 driver cards | f1api.dev | `/current/drivers-championship` |
| §1 team card | f1api.dev | `/current/constructors-championship` |
| §1 next-GP circuit + accent | f1api.dev | `/current/next` |
| §1 countdown schedule | OpenF1 | `/v1/sessions` (no `session_name`) |
| §1 countdown circuit image | OpenF1 | `/v1/meetings` (`circuit_image`) |
| §2 aggregates | f1api.dev | `/current` (client-side sum) |
| §3 round/name/laps/corners | f1api.dev | `/current/next` |
| §3 top speed | OpenF1 | `/v1/sessions` + `/v1/laps` (`st_speed`) |

## ViewModel derived-section loading

`topSpeed`, `weekendSchedule`, and `circuitImage` are derived from the
`nextRace` atom. `HomepageViewModel.warmUp()` and `refresh()` load
`nextRace` first, then immediately call `loadRaceDerivedSections()`.
There is no reactive observer on the atom because `nextRace` is a private
`MutableStateFlow` with only one writer — `loadNextRace()`. Every race
change already goes through the same path that reloads the derived
sections, so an observer would be dead code.

## Open questions (not blockers)

- **Top speed = latest session peak vs all-time circuit record.** The
  design says "record." OpenF1 `/v1/laps` cannot serve all-time across
  seasons without an N-year scan. Latest-session peak ships first; the
  all-time label is a follow-up or a stat-relabel. See [top-speed.md](top-speed.md).
- **Circular gauge implementation** — M3 component vs custom `Canvas`. Pick
  at implementation, not a spec decision.

## Cross-references

- Ticket 03: `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md` —
  use-case table, ViewModel composition, `Season` aggregates.
- Ticket 04: `lode/wayfinder/f1app/tickets/04-api-client-and-enrichment-scope.md`
  — multi-source wiring; one `HttpClient`, per-request base URL.
- Ticket 08: `lode/wayfinder/f1app/top-speed.md` — top-speed API wrangling.
- Architecture: `lode/architecture/architecture.md` — `Homepage` route,
  ViewModel pattern.
