# F1app terminology

Domain + project language. One term, one tight definition, rejected synonyms.

**F1app** — this app, package `com.anpurnama.f1_app`, Android Compose, dark-first. Avoid: "the app" (ambiguous in multi-project contexts).

**PokeDV** — `PokemonDataViewer`, the developer's prior project; architecture reference for F1app (single module, manual Wiring DI, sealed Outcome, MVVM init-less, Navigation 3).

**Wiring** — manual service-locator class held by the `Application`; lazily owns private use cases/caches and exposes feature-level ViewModel factories plus narrow app/widget seams through one instance. Avoid: DI container, injector.

**Outcome\<T\>** — sealed data-layer result type returned by use cases: `Success(data)`, `Failure(errorMessage)`, `Loading`. Lives at `core/Outcome.kt`. Stops at the VM boundary; composables never import it (ADR 0002). See: [practices.md](practices.md) §Outcome boundary.

**RefreshResult** — cache-refresh report that distinguishes a persisted `Refreshed` payload, neutral `SkippedFresh`/`Deferred` work, and `RetryableFailure`/`PermanentFailure`; temporary legacy `Success`/`Failure` variants remain only for session and non-season resources pending issues #68/#69. Avoid: refresh status (ambiguous with `ContentSyncStatus`), network result (skips are not network attempts).

**SectionUiState\<T\>** — VM→UI transport for a screen section: `Loading`, `Error(message)`, `Content(data, sync)`. Cache-aware `Content` carries `ContentSyncStatus` so stale/refreshing content stays visible (ADR 0018). See: [practices.md](practices.md) §Outcome boundary.

**ContentSyncStatus** — non-destructive cache/refresh marker on `SectionUiState.Content`: `Fresh`, `Stale`, `Refreshing`, `RefreshFailed(message)`. Avoid: treating stale refresh as `Error`, which would blank valid cached content.

**UiState** — sealed per-screen state exposed by each `ViewModel` as a `StateFlow`. Avoid: screen state (too broad), VM state (ambiguous with internal VM state).

**UseCase** — function-reference seam between a `ViewModel` and data; e.g. `GetNextRaceUseCase`. ViewModels take them as `useCase::invoke`. Avoid: repository (different layer), interactor.

**Init-less ViewModel** — ViewModel whose first load fires from `Flow.onStart { load() }` under `SharingStarted.Lazily`, not from an `init {}` block. Avoid: eager initialization, `WhileSubscribed` grace-window. See: [practices.md](practices.md) §SharingStarted policy.

**Domain-purity invariant** — `f1/` (domain + DTOs + repository interface + Ktor API extensions) must contain zero `android.*` imports. Enables future KMP `:shared` extraction as a move, not a rewrite. See: [architecture/architecture.md](architecture/architecture.md) §Domain-purity invariant.

**Countdown widget** — home-screen Glance widget showing countdown to next race. See: [widget/countdown.md](widget/countdown.md).

**CountdownWorker** — periodic WorkManager worker that polls next race and updates `NextRaceCache`. See: [widget/countdown.md](widget/countdown.md).

**NextRaceCache** — `DataStore<Preferences>` cache for next-race data, read by the Countdown widget and written by `CountdownWorker`. See: [widget/countdown.md](widget/countdown.md).

**FavoritesCache** — `DataStore<Preferences>` wrapper with typed driver/team keys and one atomic `edit` block. Written by favorites picker and first-launch seeding; read by Homepage §3. See: [my-team/summary.md](my-team/summary.md).

**My Team** — the favorites management surface. Three slots: 2 favorite drivers + 1 favorite constructor. Superseded: the separate My Team tab is removed per ADR 0010; management now lives in Homepage §3.

**Constructor** — the F1 championship entity that scores constructors' championship points. On the §1 Team card the caption is "Constructor", not "Team" or "My team". Avoid: "Team" (loses F1 meaning), "My team" (misleading when no favorites picked), "Constructor team" (redundant).

**Catalog** — a season-scoped reference list used for lookups and joins, such as current drivers or constructors/teams; it is not a ranked table. Avoid: standings, results, archive.

**Round** — a numbered race in a season (`RoundDetail(year, round)` route). Avoid: race (ambiguous with the race session), GP (informal).

**CircuitDetail** — `Route.CircuitDetail(circuitId: String)`; circuit detail screen with two independently-failing sections (metadata + most-wins). See: [specs/screens.md](specs/screens.md) §Round detail + Session result.

**RoundDetail** — `Route.RoundDetail(year, round)`; the round detail screen. Two modes: upcoming (circuit stats + weekend schedule) and past (circuit stats + results tab + per-session rows). See: [specs/screens.md](specs/screens.md) §Round detail + Session result.

**SessionResult** — `Route.SessionResult(year, round, session)`; full result list for one session. Race results include podium header, Fastest Lap, and Fastest Pitstop standout cards. See: [specs/screens.md](specs/screens.md) §Round detail + Session result.

**SessionType** — enum of session types across GPs: `FP1`, `FP2`, `FP3`, `SprintQuali`, `Sprint`, `Quali`, `Race`. A single GP uses exactly five sessions. Aliases: Quali = Qualifying = Race Qualification; SprintQuali = Sprint Qualifying = SQuali. See: [specs/data-layer.md](specs/data-layer.md).

**SessionTime** — a single session of a race weekend with `label`, `shortLabel`, and `start: Instant` (UTC). Drives the Homepage §1 countdown card. See: [specs/data-layer.md](specs/data-layer.md).

**WeekendSchedule** — the full list of weekend sessions, sorted ascending by `start`. Exposes `nextUpcoming(now): SessionTime?`. See: [specs/data-layer.md](specs/data-layer.md).

**RaceSchedule** — per-session date+time block carried on `Race` from f1api.dev `/current`. Distinct from `WeekendSchedule` (Instant-based model). See: [specs/data-layer.md](specs/data-layer.md).

**SessionSlot** — raw `date: String?, time: String?` pair from f1api.dev's `SessionDto`. Distinct from `SessionTime` (typed Instant-based). See: [specs/data-layer.md](specs/data-layer.md).

**RoundPodium** — Schedule > Past list's per-row podium. Composes `GetRoundResultsUseCase` and slices `[0..2]`; no extra network call. See: [specs/screens.md](specs/screens.md).

**Season aggregates** — computed client-side in `GetSeasonUseCase`: `completedGp`, `totalKm` (circuitLength digits divided by 1000), `totalLaps`, `progressPercent`. See: [specs/data-layer.md](specs/data-layer.md).

**Deep link** — `f1app://round/{year}/{round}`; the only deep link in scope. Countdown widget builds a `PendingIntent` over `Intent.ACTION_VIEW`. `launchMode="singleTop"` reuses the foreground activity. See: [specs/screens.md](specs/screens.md).

**NavShell** — `core/navigation/NavShell.kt`; the 4-tab `Scaffold` + `NavigationBar` + `NavDisplay` host. Navigation 3 multi-backstack: each tab owns a persistent `NavBackStack`; switching tabs does not destroy ViewModels. See: [core/navigation.md](core/navigation.md).

**Route** — sealed `NavKey` hierarchy in `core/navigation/Routes.kt`. Four top-level tabs are `data object`s: `Homepage`, `Schedule`, `Leaderboard`, `MyTeam`. Detail routes: `CircuitDetail`, `RoundDetail`, `DriverDetail`, `TeamDetail`, `SessionResult`. See: [core/navigation.md](core/navigation.md).

**f1api.dev** — primary free F1 API (schedule, standings, results, circuit metadata, pre-joined driver+team). Zero auth.

**jolpica** — free Ergast-successor API; used for Race/Qualifying results (standard), Sprint/SprintQuali/FP results (alpha), pit-stops, and all-time most-wins-at-circuit.

**OpenF1** — retired runtime API dependency. Removed by ticket 10 / ADR 0009. Do not reintroduce without a new decision record.

**F1appTheme** — the single dark-only `@Composable` in `ui/theme/Theme.kt`; one param (`content`). No light scheme, no dynamic color, no `isSystemInDarkTheme`.

**F1ColorScheme** — the `darkColorScheme()` built in `Theme.kt` from named `Color` vals in `Color.kt` (F1Primary, F1Secondary, F1Tertiary, FLError, Surface*, OnSurface*, Outline*). Private to `Theme.kt`.

**F1Shapes** — the `Shapes(small=2, medium=8, large=14, extraLarge=16)` dp set in `Theme.kt`. Design's `full: 28` is not a M3 role; pills use `CircleShape` directly.

**Spacing** — `object` in `Theme.kt` exposing the 8-step 4–32dp scale (xs / sm / md / normal / semiLg / lg / xl / xxl). Use for paddings/gaps.

**Circuits** — `object` in `Color.kt`; 21 per-circuit brand colors. Accent backgrounds on dark only, never text on dark.

**F1Api** — Ktor endpoint extensions and base URL constants for f1api.dev and Jolpica. See: [core/network.md](core/network.md).

**HttpClientFactory** — builds the shared Ktor `HttpClient` used by all use cases and the widget. See: [core/network.md](core/network.md).

**OutcomeContent** — `core/ui/OutcomeContent.kt`; the shared `SectionUiState<T>` → composable family (Loading spinner / Error-with-retry / Content). Pinned for open question #2 — every later screen reuses this shape. See: [practices.md](practices.md) §Outcome boundary.
