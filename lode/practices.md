# F1app practices

Patterns, conventions, and invariants for the *target* system. **The first foundation
slice has landed end-to-end** (ticket 01): the dark-only theme (ticket 02), the release
pipeline (ticket 15), `core/Outcome.kt`, the pure-Kotlin `f1/` domain package
(`F1Api.kt` + DTOs + `Season` model + `GetSeasonUseCase` + `toSeason()` mapper),
`feature/homepage/HomepageViewModel.kt` (init-less seam + factory + public `refresh()`),
the composition root (`F1App` + `Wiring` + `HttpClientFactory`), the Navigation 3
4-tab shell (`Routes` + `NavShell` + `NavigationBar`), the shared `OutcomeContent`
composable family (pinned for open #2), and the `HomepageScreen` rendering §2 inside a
`PullToRefreshBox` are now built, with 24 JVM unit tests green and both debug + release
APKs assembling clean. Sections marked `[BUILT]` describe code in the repo; everything
else is the agreed contract the build works toward, written present-tense as the spec
to implement against. Flip a section to `[BUILT]` as it lands.

**Still pending:** the widget (`CountdownWidget` + `CountdownWorker` + `NextRaceCache`),
the remaining use cases (driver/team standings, top-speed, most-wins-at-circuit),
the placeholder tabs (Leaderboard/MyTeam), and ticket 04's
multi-source `HttpClient` extensions (jolpica + OpenF1). Round detail
(`Route.RoundDetail` + `RoundViewModel`/`RoundScreen`, fed by
`GetRoundResultsUseCase` + `GetRoundQualifyingUseCase`) and the Schedule tab
(`ScheduleViewModel`/`ScheduleScreen` reusing `GetSeasonUseCase` for the list and
`GetRoundPodiumUseCase` for per-row past podiums) are now `[BUILT]`. Detail
routes (`DriverDetail`/`TeamDetail`) land with the screens that open them, per
ticket 05; `RoundDetail` is wired but its destination page is the
ticket-03-built `RoundScreen`, and `CircuitDetail` remains the slice-06
placeholder.

## Build floor `[BUILT]` `[from ticket 02]`

- `compileSdk = release(37)`, `targetSdk = 37` (was 36 / 36.1). Bumped because
  AndroidX deps pulled in by the Compose BOM 2026.06.01 (Kotlin 2.4.10) (`core:1.19.0`,
  `core-ktx:1.19.0`, `lifecycle-runtime-compose-android:2.11.0`) hard-require SDK 37.
  When a dep bumps the floor again, bump it again; do not pin or downgrade.
- `minSdk = 24` (untouched). Don't raise without a user-driven reason.
  **Date/time rule:** because minSdk 24 rules out `java.time.*` (API 26+) and the
  project does NOT enable `coreLibraryDesugaring`, all date/time parsing + formatting
  uses `kotlinx.datetime` (added 0.6.1 with the §1 countdown). Manual formatting
  (e.g. `dayOfWeek.name.take(3)` for short day name) is acceptable for one-screen
  strings; do not pull in a desugar switch for that. If a future screen needs
  `DateTimeFormatter` patterns, enable desugaring explicitly.
- `androidTestImplementation(platform(libs.androidx.compose.bom))` is **intentionally
  absent** — the `implementation(platform(...))` constraint already propagates to the
  androidTest configuration via AGP inheritance, so re-declaring it is a duplicate
  (Android Studio flags it). Test deps resolve their versions off the BOM regardless.
  Do not restore the line.

## Release build, signing & R8 `[BUILT]` `[from ticket 15]`

> Detail: [release/build-and-signing.md](release/build-and-signing.md).

## Architecture shape (target — from ticket 01; foundation slice `[BUILT]`)

> Detail: [architecture/architecture.md](architecture/architecture.md).

## Domain-purity invariant (hard)

`f1/` — domain models, DTOs, repository interface, use cases, and the Ktor `HttpClient`
API extensions — **must contain zero `android.*` imports**. Platform concerns
(`Context`, `android.util.Log`, dispatchers) get injected as interfaces from `core/`.

This is the hedge for a future Kotlin Multiplatform port. When KMP is greenlit, `f1/`
moves into a new `:shared` KMP module's `commonMain` and becomes the shared code for a
SwiftUI iOS target. Without the invariant, that's a refactor; with it, it's a `git mv`
+ a module declaration.

Ponytail: don't pre-split into `:shared` now. The invariant handles the port; the
module extraction is deferred until KMP is actually greenlit (planned, not started).

## Package layout (foundation slice created; composition root still pending)

**Created so far:** `core/Outcome.kt`, `f1/data/{F1Api,Dtos}.kt`, `f1/model/Season.kt`,
`f1/GetSeasonUseCase.kt`, `feature/homepage/HomepageViewModel.kt`, `ui/theme/`,
`MainActivity.kt`. **Still to create:** `F1App.kt`, `core/di/Wiring.kt`,
`core/network/HttpClientFactory.kt`, `core/navigation/`, the rest of `f1/model/` + the
other 10 use cases, `feature/*/*Screen.kt`, `widget/`.

```
com.anpurnama.f1_app/
  F1App.kt                       # Application — holds `wiring: Wiring`
  MainActivity.kt
  core/
    di/Wiring.kt                  # manual service locator; HttpClient + NextRaceCache + use cases
    navigation/{Routes,Navigator,NavigationState,EntryProviders}.kt
    network/HttpClientFactory.kt  # Ktor HttpClient (CIO) + plugins
    Outcome.kt                    # sealed Success/Failure/Loading (data-layer only — ADR 0002)
    ui/                            # SectionUiState.kt (VM→UI transport) + OutcomeContent.kt (shared renderer)
    exception/ExceptionExtension.kt
  f1/                             # DOMAIN — pure Kotlin, zero android.* imports
    data/{F1Api, Dtos, ...}.kt     # Ktor endpoint extensions + @Serializable DTOs (per-endpoint envelopes)
    model/                        # NextRace, Season (+ aggregates), Race, Circuit, Driver, Team,
                                  # DriverStanding, ConstructorStanding, RaceResult, QualifyingResult
    {GetNextRaceUseCase, GetSeasonUseCase, GetDriversStandingsUseCase,
     GetConstructorsStandingsUseCase, GetDriverDetailUseCase, GetTeamDetailUseCase,
     GetRoundResultsUseCase, GetRoundQualifyingUseCase,
     GetCircuitTopSpeedUseCase, GetCircuitMostWinsUseCase,
     GetRoundPodiumUseCase}.kt
  ui/theme/{Color,Theme,Type}.kt    # dark-only M3 theme — ticket 02. No Tokens.kt: F1 palettes (Circuits, Tyres, result accents) are grouped objects in Color.kt, not a separate file.
  feature/
    homepage/{HomepageScreen,HomepageViewModel,HomepageViewModelFactory}.kt  # combines 5 use cases
    schedule/{ScheduleScreen,ScheduleViewModel,...}.kt
    leaderboard/{LeaderboardScreen,LeaderboardViewModel,...}.kt
    driver/...
    team/...
    round/...
  widget/
    countdown/
      CountdownWidget.kt           # GlanceAppWidget — provideGlance reads NextRaceCache, renders @Composable, deep-link clickable
      CountdownWorker.kt           # periodic (15-min floor), network constraint, polls /current/next → updateAll(widget)
      data/NextRaceCache.kt        # DataStore<Preferences>, typed keys (no JSON blob)
```

## Standing preferences

- **Ponytail / BSSN:** simplest system that works; no speculative abstraction.
  Look before you write — reuse a sibling helper before reimplementing.
- **Design reference:** boxbox-club (dark-first F1 design tokens). Lean new app that
  *uses* it as a reference, not a faithful re-skin of every surface.
- **"If not covered by a free API, it's not built"** — user rule about *features*
  (feeders, news, collaborator screens all out). Enrichments (headshots, weather)
  are a separate question, decided in ticket 04.
- **Scope:** 3 top-level navs (Homepage, Schedule, Leaderboard) + Driver/Team/Round
  detail pages + 1 Countdown widget. 7 secondary widget types and Driver Comparison
  are out, not deferred. Three data gaps not served by f1api.dev (top speed → OpenF1,
  most wins at circuit → jolpica, podium on Past list → f1api.dev full-podium) were
  researched in tickets 08/09/10 and are **design-locked** under ticket 04's
  multi-source contract (11 use cases total) — none built yet.

## Test assertions (JVM unit) `[BUILT]` `[from ticket 01]`

- **JUnit4 over `kotlin.test`.** `kotlin.test` is not on the test classpath (the
  Android default pulls only JUnit4 via `androidx.test`). Use `org.junit.Assert.*`
  + `@org.junit.Test`. Don't add `kotlin.test` back as a dep — it would pull
  `kotlin.test.junit` which JUnit4 then has to bridge, and the test still
  reports as JUnit. The native JUnit import is the path of least friction.
- **Internal mappers cross packages within `:app`.** `internal fun` on
  `SeasonResponseDto.toSeason()` is reachable from `app/src/test/.../f1/...`
  because the test is the same module. The visibility was picked deliberately
  to keep the mapper package-private to production code (no public API surface)
  while still letting the test reach it. Follow the pattern: pure mapping
  helpers go `internal`, not `public`.
- **`MockEngine` body via the String overload of `respond`.** Wrapping the
  body in `ByteReadChannel(...)` up front confuses the deserializer — the
  engine re-wraps the channel and `ContentNegotiation` sees a
  `SourceByteReadChannel` it can't match, throwing
  `NoTransformationFoundException`. Pass the raw `String`; Ktor wraps it
  itself. Combine with `expectSuccess = true` on the test client so 4xx/5xx
  throw before body deserialization.
- **VM test fake use case = `suspend (Boolean) -> Outcome<Season>`.** Per
  the function-ref seam in [terminology.md](terminology.md): the VM takes
  the use case as a function reference, so a test fake is a plain
  `suspend` lambda. No `Mockito`/`MockK`, no extra dep. Capture args in
  the lambda for assertion (e.g. `receivedForceRefresh`).
- **VM test assertions are on `SectionUiState`, not `Outcome`.** Use cases
  return `Outcome`; the VM maps to `SectionUiState` at the seam (ADR 0002).
  Fakes are still `suspend (Boolean) -> Outcome<Season>` lambdas (the VM's
  function-ref parameter type), but state assertions read
  `sections.season is SectionUiState.Content` / `SectionUiState.Error`.
  Composables never import `Outcome`; neither do VM tests' state-side
  assertions.
- **Init-less VM tested via `Flow.take(2).toList()`.** The first 2
  emissions are `initialValue` (Loading) + first post-load emission
  (Success/Failure). `Lazily` keeps the flow alive for the entire
  `viewModelScope` lifetime, so a second `first()` after the first
  completes returns the cached Success without re-firing the use case —
  that one-line assertion is the config-change survival test.
  **Back-pop regression test:** subscribe → unsubscribe → advance
  time by 60s (well past the previous `WhileSubscribed(5_000)` grace) →
  resubscribe → assert call counts unchanged. Pins the `Lazily`
  contract. See `RoundViewModelResubscribeAfterTimeoutTest` (Round
  detail) and `ScheduleViewModelBackFromDetailTest` (Schedule, the
  user-reported bug).
- **Private suspend `load()`.** The VM's `load` is `private suspend` so
  the screen can trigger it via `onStart { load() }` (a suspend block),
  and the test exercises it through the public `uiState` subscription
  rather than calling it directly. No `viewModelScope.launch` timing
  dance in tests.
- **`SharingStarted` policy — prefer `Lazily` for screen VMs whose data
  is server-cached.** `Lazily` starts the cold upstream on the first
  subscriber and never stops it for the holder's lifetime
  (`viewModelScope`). Subsequent subscribers read the existing
  `StateFlow` value; no re-fire. Safe when the data layer is cheap on
  hot cache (10-min f1api.dev, 1-hr jolpica, OpenF1 uncached at
  ~0.3s/call — see [terminology.md §"Init-less ViewModel"](terminology.md)
  + [wayfinder/f1app/tickets/03-data-layer-and-refresh.md](wayfinder/f1app/tickets/03-data-layer-and-refresh.md)
  §Caching). Reserve `WhileSubscribed` for genuinely expensive or
  user-scoped streams. Never `Eagerly` for screen VMs — it bypasses
  the first-subscriber gate and can fire on background-tab construction.
  Was previously `WhileSubscribed(5_000)`; flipped to `Lazily` to fix
  the "back from Round detail re-fires Schedule" regression (a >5s
  Round read tripped the grace window and re-triggered `warmUp` on
  re-subscribe).
