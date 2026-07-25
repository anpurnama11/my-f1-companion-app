# F1app terminology

Short term → meaning lines. Domain + project language.

> **Most terms below describe the current shipped system.** Historical ticket
> terms remain where they explain a deliberate removal or boundary.

- **F1app** — this app, package `com.anpurnama.f1_app`, Android Compose, dark-first. `[BUILT]` greenfield scaffold + dark-only theme + Homepage, Schedule, Leaderboard, My Team, Round detail, and Session Result surfaces.
- **FavoritesCache** — `[BUILT]` `DataStore<Preferences>` wrapper with typed driver/team keys and one atomic `edit` block. Written by My Team's picker and first-launch seeding; read by Homepage and My Team.
- **PokeDV** — `PokemonDataViewer`, the developer's prior project; architecture
  reference for F1app (single module, manual Wiring DI, sealed Outcome, MVVM init-less,
  Navigation 3).
- **Wiring** — manual service-locator class held by the `Application`; exposes use cases
  and the Ktor `HttpClient` to ViewModels and the widget through one instance.
- **Outcome\<T\>** — sealed **data-layer** result type returned by use cases: `Success(data)`,
  `Failure(errorMessage)`, `Loading`. Lives at `core/Outcome.kt`. Stops at the VM: the VM
  maps each `Outcome` to a [SectionUiState] via `Outcome.toSection()` at the assignment site.
  Never imported by composables (ADR 0002).
- **UiState** — sealed per-screen state exposed by each `ViewModel` as a `StateFlow`.
- **UseCase** — function-reference seam between a `ViewModel` and data; e.g.
  `GetNextRaceUseCase`. ViewModels take them as `useCase::invoke`.
- **Init-less ViewModel** — first load fires from `Flow.onStart { load() }`, not from a
  `init {}` block. The cold stream runs under `SharingStarted.Lazily` so the first load
  fires once when the first subscriber appears, and subsequent subscribers read the
  existing `StateFlow` value without re-firing. Re-fire is via `viewModel.refresh()`
  (pull-to-refresh) only. Safe because the data layer is server-cached (10-min f1api.dev,
  1-hr jolpica — see
  `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md` §Caching). Was previously
  `WhileSubscribed(5_000)`; flipped to `Lazily` to fix the "back from Round detail re-fires
  Schedule" regression (a >5s Round read tripped the grace window and re-triggered
  `warmUp` on re-subscribe). Cross-ref: [practices.md](practices.md) §"SharingStarted
  policy".
- **Domain-purity invariant** — `f1/` (domain + DTOs + repository interface + Ktor API
  extensions) must contain zero `android.*` imports. Enables a future KMP `:shared`
  module to be a move, not a rewrite.
- **Countdown widget** — home-screen Glance widget showing countdown to next race.
  Detail: [widget/countdown.md](widget/countdown.md).
- **My Team** — `[BUILT ticket 05]` 4th top-level NavKey tab (rightmost); the favorites management
  surface. Three slots: 2 favorite drivers + 1 favorite constructor team.
  Tap a filled slot → selection screen/dialog to pick or replace (driver↔team
  decoupled — drivers need not be from the favorited constructor). Backed by
  `FavoritesCache` (DataStore, typed keys, mirrors `NextRaceCache`); first-launch
  default seeds #1 constructor + its two drivers. Homepage §1 reads the same
  cache as a compact pager. Added by ticket 12; amends ticket 05's nav from 3→4.
- **Constructor** — the F1 championship concept for a team (the entity that
  scores constructors' championship points). On the §1 Team card the caption
  is "Constructor", not "Team" or "My team". Avoid: "Team" (loses the F1
  championship meaning), "My team" (misleading when no favorites are picked
  yet — the §1 pager shows the championship leader by default, not the
  user's team), "Constructor team" (redundant). Source-of-truth decision:
  ticket 20 (`lode/wayfinder/f1app/tickets/20-q3-constructor-caption.md`).
  Domain shape: `ConstructorStanding` data class; Jolpica `Constructor` object.

- **Tour/race/round** — an F1 race weekend. "Round" = a numbered race in a season
  (`RoundDetail(year, round)` route). "Next race" = `/current/next` endpoint from f1api.dev.
- **CircuitDetail** — `[BUILT ticket 06]` `Route.CircuitDetail(circuitId: String)`,
  the circuit detail screen (`feature/circuit/CircuitScreen.kt`). Two independently
  failing sections per ADR 0002: **metadata** (f1api.dev `/circuits/{circuitId}`:
  length, corners, first-GP year, all-time lap record with attribution) and
  **most wins** (jolpica `/circuits/{id}/results/1.json` aggregated to top driver
  + top team, with the 5-entry `F1API_TO_JOLPICA_CIRCUIT` translation map applied
  at the network seam). The screen renders lap record + most-wins leader per row;
  each section falls back to "—" via the shared UX family when its source is
  unavailable. Opened from the RoundDetail circuit block. Top speed is not a v1
  feature (cut by ticket 10 / ADR 0009).
- **Deep link (custom scheme)** — `f1app://round/{year}/{round}` is the only deep link
  in scope. Countdown widget builds a `PendingIntent` over `Intent.ACTION_VIEW` with
  that data (args from `NextRaceCache`); `MainActivity` parses the URI into a `RoundDetail`
  nav key and pushes it on Homepage as backstack root. Single-app custom scheme.
- **f1api.dev** — primary free F1 API (schedule, standings, results, circuit metadata,
  pre-joined driver+team). Zero auth.
- **OpenF1** — retired runtime API dependency. Ticket 10 deliberately removes
  its production imports, URLs, DTOs, use cases, joins, and artwork fallback.
  Do not reintroduce it as a fallback without a new decision record.
- **jolpica** — free Ergast-successor API; **design-locked (ticket 04, not yet built)** for all-time
  most-wins-at-circuit via `/circuits/{id}/results/1.json` (1 call, ~25KB,
  client-aggregated top driver + top team). `driverId`/`constructorId` match
  f1api.dev's namespace; only `circuitId` needs a 5-entry translation map.
- **Wayfinder map** — `lode/wayfinder/f1app/map.md`; the destination spec + scope
  decisions. Tickets live under `lode/wayfinder/f1app/tickets/` (01–12: 01 arch, 02
  theme, 03 data layer, 04 API scope, 05 nav/deep links, 06 widget tech, 07 countdown
  specifics, 08–10 research into data gaps, 11 follow-up on OpenF1 join + all-time
  semantics, 12 favorites picker + storage).
- **F1appTheme** — the single dark-only `@Composable` in `ui/theme/Theme.kt`; one
  param (`content`). No light scheme, no dynamic color, no `isSystemInDarkTheme`.
- **F1ColorScheme** — the `darkColorScheme()` built in `Theme.kt` from the named
  `Color` vals in `Color.kt` (F1Primary, F1Secondary, F1Tertiary, FLError, Surface*,
  OnSurface*, Outline*). Private to `Theme.kt`.
- **F1Shapes** — the `Shapes(small=2, medium=8, large=14, extraLarge=16)` dp set in
  `Theme.kt`. Design's `full: 28` is not a M3 role; pills use `CircleShape` directly.
- **Spacing** — `object` in `Theme.kt` exposing the 8-step 4–32dp scale
  (xs / sm / md / normal / semiLg / lg / xl / xxl). Use for paddings/gaps per the
  design's "consistent scale" rule.
- **Circuits** — `object` in `Color.kt`; 23 per-circuit brand colors
  (Circuits.AbuDhabi..Circuits.UsaMiami). Accent backgrounds on dark only, never
  text on dark.
- **Tyres** — `object` in `Color.kt`; six Pirelli compounds as text+background pairs
  (Tyres.Soft + Tyres.SoftBg ... Tyres.Wet + Tyres.WetBg, plus Unknown/UnknownBg).
  Always pair the two halves.
- **F1Api** — Ktor endpoint extensions and base URL constants for f1api.dev and
  Jolpica. Detail: [core/network.md](core/network.md).
- **HttpClientFactory** — builds the shared Ktor `HttpClient` used by all use cases
  and the widget. Detail: [core/network.md](core/network.md).
- **Route** — sealed `NavKey` hierarchy in `core/navigation/Routes.kt`. The 4
  top-level tabs are `data object`s (`Homepage`, `Schedule`, `Leaderboard`, `MyTeam`).
  `[BUILT ticket 02]` `data class CircuitDetail(circuitId: String)` — the
  entry exists so §3's tap-target pushes a valid route; the page itself is a
  placeholder until slice 06 lands. `[BUILT ticket 03]` `data class RoundDetail(year: Int, round: Int)` —
  pushed from the Schedule tab row tap; opens the Round detail screen
  (circuit stats + weekend schedule / result session rows). `[BUILT ticket 04]`
  `data class DriverDetail(driverId: String)` and
  `data class TeamDetail(teamId: String)` open the current detail joins from
  Leaderboard rows. `data class SessionResult(year: Int, round: Int, session: SessionType)` —
  full result list for one session (Race, Qualifying, Sprint, SQuali, FP1/2/3);
  pushed from a RoundDetail session row. Race status/grid is normalized from
  the hybrid f1api.dev + Jolpica source; sprint sessions use Jolpica alpha.
- **SessionType** — enum of the possible session types across all GPs:
  `FP1`, `FP2`, `FP3`, `SprintQuali`, `Sprint`, `Quali`, `Race`. A single GP
  always uses exactly **five** of these: Sprint weekends use FP1 → SprintQuali
  → Sprint → Quali → Race; non-sprint weekends use FP1 → FP2 → FP3 → Quali →
  Race. Used by `SessionResult` to pick the correct endpoint and to render the
  correct label/short label.
  Endpoint mapping: Race/Quali/FP1/FP2/FP3 → f1api.dev
  (`/{year}/{round}/race`, `/qualy`, `/fp1`, `/fp2`, `/fp3`);
  Sprint/SprintQuali → Jolpica alpha
  (`/f1/alpha/results/{round_id}/SR/` and `/SQ/`). The session list in
  `RoundDetail` is built from the f1api.dev schedule (which includes
  `sprintQualy` and `sprintRace` fields, null when no sprint).
- **NavShell** — `core/navigation/NavShell.kt`; the 4-tab `Scaffold` +
  `NavigationBar` + `NavDisplay` host. Uses Navigation 3 multi-backstack
  (revision 2): each tab owns a persistent `NavBackStack`; switching tabs
  does not destroy ViewModels — no data re-fetch.
  `NavigationState` holds the per-tab stacks + current tab;
  `Navigator` dispatches tab switches vs within-stack pushes.
  Entry decorators (`rememberSaveableStateHolderNavEntryDecorator` +
  `rememberViewModelStoreNavEntryDecorator`) scope state per-entry.
  Exit-through-home: Homepage is the start route, always rendered.
- **SectionUiState\<T\>** — the **VM→UI transport** for a screen section: `Loading`,
  `Error(message)`, `Content(data)`. Lives at `core/ui/SectionUiState.kt`. Named for screen
  vocabulary (Content/Error), not operation vocabulary (Success/Failure). Each independently-failing
  section atom on `HomepageViewModel.UiState.Sections` is a `SectionUiState<T>`. Rendered by the
  shared `OutcomeContent` family. Outcome maps to this at the VM seam via `Outcome.toSection()`;
  composables never import `Outcome` (ADR 0002).
- **OutcomeContent** — `core/ui/OutcomeContent.kt`; the shared `SectionUiState<T>` →
  composable family (Loading spinner / Error-with-retry / Content). Pinned for open
  question #2 — every later screen reuses this shape, no per-screen ad-hoc
  loading/error rendering. The retry button is suppressed when `onRetry == null`
  (read-only surfaces).
- **NextRaceCache** — `DataStore<Preferences>` cache for next-race data, read by the
  Countdown widget and written by CountdownWorker. Detail: [widget/countdown.md](widget/countdown.md).
- **CountdownWorker** — periodic WorkManager worker that polls next race and updates
  NextRaceCache. Detail: [widget/countdown.md](widget/countdown.md).
- **Season aggregates** — computed client-side in `GetSeasonUseCase` from
  `/current` (full schedule): completedGp (count `winner != null`), totalKm
  (sum of `circuit.circuitLength` digits **divided by 1000** — the wire
  format `"<N>km"` is meters, e.g. Bahrain `"5412km"` = 5.412 km), totalLaps
  (sum `laps`), progressPercent. Exposed on the `Season` model as
  `totalKm: Double` (precision across a season sum) so ViewModels don't
  recompute.
- **SessionTime** — `[BUILT]` `f1/model/RaceWeekend.kt`; a single session of a
  race weekend. A GP always has exactly **five sessions**. Sprint weekends:
  FP1 → Sprint Quali → Sprint → Quali → Race. Non-sprint weekends:
  FP1 → FP2 → FP3 → Quali → Race. Aliases: **Quali** = **Qualifying** =
  **Race Qualification**; **Sprint Quali** = **Sprint Qualifying** = **SQuali**.
  Fields: `label` (long form "Practice 1"), `shortLabel` (chip form "FP1"),
  `start: kotlinx.datetime.Instant` (UTC). Converted from f1api.dev schedule
  slots. Drives the Homepage §1 countdown card.
- **WeekendSchedule** — `[BUILT]` `f1/model/RaceWeekend.kt`; the full list of
  weekend sessions, sorted ascending by `start`. Exposes `nextUpcoming(now:
  Instant): SessionTime?` — the earliest session whose start is still in
  the future (or `null` once the whole weekend has started). Drives the
  §1 countdown card; `null` schedule renders the empty state.
- **RoundDetail** — `[BUILT ticket 03]` `Route.RoundDetail(year, round)`,
  the Round detail screen (`feature/round/RoundScreen.kt`). One route with
  two modes driven by the Race session start time: **upcoming** shows circuit
  stats (length, laps, turns) + the five-session race weekend
  schedule; **past** shows circuit stats + a Results tab + per-session rows
  (Race, Qualifying, Sprint, SQuali, FP1/2/3 as scheduled). The circuit stats are always
  visible regardless of mode. The Highlights tab and Driver of the Day are out
  of scope for v1. Each past session row has a **Results** action that pushes a
  full `SessionResult` screen.
- **SessionResult** — `[BUILT ticket 03]` `Route.SessionResult(year, round, session)` full result
  list for one session. Race results include a podium chip header (top 3),
  Fastest Lap, and Fastest Pitstop standout cards. Fastest Lap is derived from
  f1api.dev `fastLap` fields. Fastest Pitstop comes from Jolpica
  (`/pitstops.json`, duration). If no pit-stop data exists for the
  round (e.g., pre-2024 US GP), the card is hidden. Other sessions show their
  session-specific result table (Quali/SprintQuali = Q1/Q2/Q3; Sprint = same as
  Race; FP = time-ordered fastest lap).
- **RaceSchedule** — `[BUILT ticket 03]` `f1/model/Season.kt`; the
  per-session date+time block carried on `Race` from f1api.dev `/current`
  (`RaceScheduleDto`). Fields: `fp1`/`fp2`/`fp3`/`sprintQualy`/`sprintRace`/
  `qualy`/`race`, each a nullable `SessionSlot(date, time)`. Empty session
  DTO objects (`{ date: null, time: null }`) normalize to null at
  `GetSeasonUseCase.toSchedule()`, matching truly absent fields; this keeps
  non-sprint weekends from advertising Sprint Quali/Sprint rows. Its
  `activeSessions()` helper selects the five non-sprint or sprint slots. The
  screen converts slot UTC values to device-local date/time labels. The whole `RaceSchedule` is
  `null` when the DTO's schedule block is empty (older seasons, partial
  data). Revision 1 of the Schedule tab renders only the `race` slot
  (date + time as a one-liner); the 5-session breakdown was dropped to
  match the Homepage §3 card shape. Distinct from `WeekendSchedule`
  (which is the Instant-based, `nextUpcoming`-aware model
  driving the Homepage §1 countdown).
- **SessionSlot** — `[BUILT ticket 03]` `f1/model/Season.kt`; a
  `date: String?, time: String?` pair from f1api.dev's
  `SessionDto`. Kept as raw strings (e.g. `"2024-03-02"`,
  `"15:00:00Z"`) — the screen formats them. Distinct from
  `SessionTime` (typed Instant-based).
- **RoundPodium** — `[BUILT ticket 03]` `f1/GetRoundPodiumUseCase.kt`;
  the Schedule > Past list's per-row podium. `data class
  RoundPodium(val topThree: List<RoundResult>)` with
  `companion object { const val PODIUM_SIZE = 3 }`. The use case
  composes `GetRoundResultsUseCase` and slices `[0..2]` (per the
  ticket 10 research; no extra network call). On the Schedule screen
  the podium cell renders the 3 `RoundResult`s as P1/P2/P3 chips
  (driver short name + team); on failure the cell degrades to an
  inline error + Retry button that calls
  `ScheduleViewModel.retryPodium(round)` (re-fires a single row with
  `forceRefresh = true`). The whole schedule never blanks from a
  single-row failure (shared UX family from ticket 01).
