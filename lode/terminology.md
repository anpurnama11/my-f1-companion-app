# F1app terminology

Short term → meaning lines. Domain + project language.

> **Most terms below describe the *planned* system, not built code.** Only the
> theme-related terms (`F1appTheme`, `F1ColorScheme`, `F1Shapes`, `Spacing`,
> `Circuits`, `Tyres`) and `F1app`/`PokeDV`/`Lode`/`Wayfinder map` correspond to code
> in the repo today. Everything else (`Wiring`, `Outcome<T>`, the use cases,
> `F1Api`, `NextRaceCache`, `CountdownWorker`, the deep link, favorites, multi-source
> wiring) is a **locked design contract** the build works toward; their present-tense
> phrasing is the spec, not a claim the code exists.

- **F1app** — this app, package `com.anpurnama.f1_app`, Android Compose, dark-first. `[BUILT]` greenfield scaffold + dark-only theme + foundation slice (ticket 01) + Homepage §1+§3 (ticket 02).
- **FavoritesCache** — `[BUILT]` `DataStore<Preferences>` wrapper (mirrors `NextRaceCache` in shape, even though not called that way) with typed keys `FAV_DRIVER_1: String`, `FAV_DRIVER_2: String`, `FAV_TEAM: String`, one atomic `edit` block. Written from My Team's picker (later) and from `HomepageViewModel`'s first-launch seed; read by `HomepageViewModel` §1. The `seedIfEmpty(topTeamId, topDriverIds)` is partial-fill safe — it only writes into slots the user hasn't filled yet. `circuitId` translation map (f1api.dev → OpenF1 short name) is **NOT** used (see deviation note in `lode/wayfinder/f1app/tickets/11-...md` — we use the date match on `qualyDate` instead, no translation map needed).
- **PokeDV** — `PokemonDataViewer`, the developer's prior project; architecture
  reference for F1app (single module, manual Wiring DI, sealed Outcome, MVVM init-less,
  Navigation 3).
- **Wiring** — manual service-locator class held by the `Application`; exposes use cases
  and the Ktor `HttpClient` to ViewModels and the widget through one instance.
- **Outcome\<T\>** — sealed result type: `Success(data)`, `Failure(errorMessage)`,
  `Loading`. Lives at `core/Outcome.kt`.
- **UiState** — sealed per-screen state exposed by each `ViewModel` as a `StateFlow`.
- **UseCase** — function-reference seam between a `ViewModel` and data; e.g.
  `GetNextRaceUseCase`. ViewModels take them as `useCase::invoke`.
- **Init-less ViewModel** — first load fires from `Flow.onStart { load() }`, not from a
  `init {}` block. Re-fires on `ON_START` under `WhileSubscribed(5_000)`.
- **Domain-purity invariant** — `f1/` (domain + DTOs + repository interface + Ktor API
  extensions) must contain zero `android.*` imports. Enables a future KMP `:shared`
  module to be a move, not a rewrite.
- **Countdown widget** — home-screen widget ticking down to the next race; the only
  widget in scope. Built with **Jetpack Glance** (`androidx.glance:appwidget`) — a
  `GlanceAppWidget` subclass whose `provideGlance` reads `NextRaceCache` and renders
  `@Composable` content (compiles to `RemoteViews` under the hood). **No live
  chronometer** (ticket 07): the displayed countdown is recomputed from
  `NEXT_RACE_START_MILLIS` at each render; states computed render-time from `now` vs
  the cached race window → countdown / LIVE NOW (circuit accent) / RACE COMPLETE /
  Season over (off-season, `START_MILLIS == 0L`) / No race data (no cache + sync fail).
  GP date/time shown device-local below the countdown. Dark `Surface` body + full-bleed
  ~6dp `Circuits.forId(circuitId)` accent strip. Tapping fires an `ACTION_VIEW`
  `PendingIntent` to `f1app://round/{year}/{round}` via Glance
  `clickable(actionStartActivity(intent))` — suppressed in off-season / no-cache.
- **My Team** — 4th top-level NavKey tab (rightmost); the favorites management
  surface. Three slots: 2 favorite drivers + 1 favorite constructor team.
  Tap a filled slot → selection screen/dialog to pick or replace (driver↔team
  decoupled — drivers need not be from the favorited constructor). Backed by
  `FavoritesCache` (DataStore, typed keys, mirrors `NextRaceCache`); first-launch
  default seeds #1 constructor + its two drivers. Homepage §1 reads the same
  cache as a compact pager. Added by ticket 12; amends ticket 05's nav from 3→4.
- **FavoritesCache** — `DataStore<Preferences>` wrapper (mirrors `NextRaceCache`)
  with typed keys `FAV_DRIVER_1: String`, `FAV_DRIVER_2: String`, `FAV_TEAM: String`,
  one atomic `edit` block. Written from My Team's picker, read by
  HomepageViewModel §1 + MyTeamViewModel. No `FAV_*_TS` timestamps unless a
  most-recent heuristic is later needed. 3rd-pin = explicit user replace, not
  auto-evict-oldest.
- **Tour/race/round** — an F1 race weekend. "Round" = a numbered race in a season
  (`RoundDetail(year, round)` route). "Next race" = `/current/next` endpoint from f1api.dev.
- **CircuitDetail** — NavKey route `CircuitDetail(circuitId: String)`, opened from
  RoundDetail's circuit block + Homepage §3's nearest-GP card. The screen home for the
  two circuit-scoped research stats: top-speed (ticket 08, OpenF1 `st_speed`) and
  most-wins-at-circuit (ticket 09, jolpica). Backed by the `getCircuit(id)` extension
  on `F1Api.kt` specified in ticket 03.
- **Deep link (custom scheme)** — `f1app://round/{year}/{round}` is the only deep link
  in scope. Countdown widget builds a `PendingIntent` over `Intent.ACTION_VIEW` with
  that data (args from `NextRaceCache`); `MainActivity` parses the URI into a `RoundDetail`
  nav key and pushes it on Homepage as backstack root. Custom scheme only — no App Links /
  `autoVerify` (single-app, no public web domain to verify against).
- **f1api.dev** — primary free F1 API (schedule, standings, results, circuit metadata,
  pre-joined driver+team). Zero auth.
- **OpenF1** — free secondary API; `[BUILT ticket 02]` for top speed via
  `/v1/laps` `st_speed` (natively kph), 2023+. Join key is
  `country_name + year + qualyDate match` (ticket 11 + ticket 02 deviation) —
  the date match is on f1api.dev's `schedule.qualy.date` (Qualifying day), not
  `schedule.race.date` (race day). Ticket 11 research claimed the date
  matched race day; live probes during ticket 02 build showed OpenF1's
  `date_start` is Qualifying day, which is 1 day before the race (or 2 days
  for sprint weekends). `country_name` alone is insufficient for US (3
  circuits), Spain (2 circuits 2026+), and Italy (2 circuits 2023–2025);
  the date match is the unique disambiguator. One country string diverges
  (`Great Britain` vs `United Kingdom` for Silverstone) → 1-entry
  `F1API_TO_OPENF1_COUNTRY` fallback map, applied only when literal returns 0.
  Sends **no cache headers** (nginx, no CDN) → HttpCache skips it; accepted
  uncached (~0.3s/call). Driver headshots / weather / race-control flags remain
  parked additive enrichments.
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
- **F1Api** — `f1/data/F1Api.kt`; holds the `F1API_BASE` + `OPENF1_BASE`
  consts and the Ktor extensions:
  `suspend fun HttpClient.getCurrent(forceRefresh)`,
  `getNextRace(forceRefresh)`,
  `getDriversChampionship(forceRefresh)`,
  `getConstructorsChampionship(forceRefresh)`,
  `getOpenF1Sessions(year, countryName, sessionName)`,
  `getOpenF1Laps(sessionKey)`. The f1api.dev extensions take a `forceRefresh`
  flag and add `Cache-Control: no-cache` when true; the OpenF1 extensions
  don't (no cache to bust). Also holds `F1API_TO_OPENF1_COUNTRY` (1-entry
  Silverstone fix). `JOLPICA_BASE` and the jolpica extensions land in slice 04
  (most-wins-at-circuit). Pure Kotlin (satisfies the domain-purity invariant).
- **HttpClientFactory** — `core/network/HttpClientFactory.kt`; builds the single
  Ktor `HttpClient` at `F1App` startup with CIO engine, `ContentNegotiation`
  (`ignoreUnknownKeys = true; coerceInputValues = true`), `HttpCache` with a 10 MB
  `FileStorage` under `cacheDir/http_cache`, `HttpTimeout` 15s/10s, and
  `expectSuccess = true` (so 4xx/5xx throw before body deserialization — the use
  case's 4xx/5xx catch branches depend on it). Held by `Wiring`; one client per
  process, shared by the widget when it lands.
- **Route** — sealed `NavKey` hierarchy in `core/navigation/Routes.kt`. The 4
  top-level tabs are `data object`s (`Homepage`, `Schedule`, `Leaderboard`, `MyTeam`).
  `[BUILT ticket 02]` `data class CircuitDetail(circuitId: String)` — the
  entry exists so §3's tap-target pushes a valid route; the page itself is a
  placeholder until slice 06 lands. `DriverDetail`/`TeamDetail`/`RoundDetail`
  land with the screens that open them, per ticket 05.
- **NavShell** — `core/navigation/NavShell.kt`; the 4-tab `Scaffold` +
  `NavigationBar` + `NavDisplay` host. Uses Navigation 3 1.1.4's `NavBackStack` +
  `rememberNavBackStack` (Android-only reflection serializer) and `NavDisplay(backStack,
  onBack, entryProvider = entryProvider { entry<T> { ... } })`. Tapping a different
  tab clears the back stack and pushes the new top-level route; the system back
  gesture pops one level. Only `Homepage` renders real content this slice; the
  other three are placeholder `Text("… coming soon")` composables.
- **OutcomeContent** — `core/ui/OutcomeContent.kt`; the shared `Outcome<T>` →
  composable family (Loading / Failure-with-retry / Success). Pinned for open
  question #2 — every later screen reuses this shape, no per-screen ad-hoc
  loading/error rendering. The retry button is suppressed when `onRetry == null`
  (read-only surfaces).
- **NextRaceCache** — `widget/countdown/data/NextRaceCache.kt`; wraps a
  `DataStore<Preferences>` with typed keys (`NEXT_RACE_START_MILLIS: Long`,
  `NEXT_RACE_NAME: String`, `NEXT_RACE_CIRCUIT: String`, `NEXT_RACE_ROUND: Int`,
  `NEXT_RACE_SEASON: Int`, plus the full session schedule for "closest event"
  countdown). `CountdownWorker` will write, `CountdownWidget` will read, same instance via
  `Wiring`. One atomic `edit` block — no serialized JSON blob.
- **CountdownWorker** — periodic `CoroutineWorker` **(planned, not yet created)**; (15-min WorkManager floor,
  `NETWORK_TYPE_CONNECTED` constraint, exponential backoff, failure leaves cached
  value) polling `/current/next` via `GetNextRaceUseCase`. **Adaptive cadence**
  (ticket 07): inside the cached race window `[FP1_start, race_start + 3h]` it
  fetches every tick; outside, a gate in `doWork` fetches only when cache age
  ≥ 60 min (effectively hourly between weekends). One `PeriodicWorkRequest`, no
  second spec. Calls `CountdownWidget().updateAll(context)` after a successful
  cache write.
- **Season aggregates** — will be computed client-side in `GetSeasonUseCase` from
  `/current` (full schedule): completedGp (count `winner != null`), totalKm (sum
  `circuit.circuitLength` digits), totalLaps (sum `laps`), progressPercent. Exposed
  on the `Season` model so ViewModels don't recompute.
