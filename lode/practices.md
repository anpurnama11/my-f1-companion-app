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
the remaining use cases (driver/team standings, round results, qualifying, top-speed,
most-wins-at-circuit, podium), the §1 favorite pager and §3 nearest-GP sections on
Homepage, the placeholder tabs (Schedule/Leaderboard/MyTeam), and ticket 04's
multi-source `HttpClient` extensions (jolpica + OpenF1). Detail routes
(`DriverDetail`/`TeamDetail`/`RoundDetail`/`CircuitDetail`) land with the screens that
open them, per ticket 05.

## Build floor `[BUILT]` `[from ticket 02]`

- `compileSdk = release(37)`, `targetSdk = 37` (was 36 / 36.1). Bumped because
  AndroidX deps pulled in by the Compose BOM 2026.06.01 (Kotlin 2.4.10) (`core:1.19.0`,
  `core-ktx:1.19.0`, `lifecycle-runtime-compose-android:2.11.0`) hard-require SDK 37.
  When a dep bumps the floor again, bump it again; do not pin or downgrade.
- `minSdk = 24` (untouched). Don't raise without a user-driven reason.
- `androidTestImplementation(platform(libs.androidx.compose.bom))` is **intentionally
  absent** — the `implementation(platform(...))` constraint already propagates to the
  androidTest configuration via AGP inheritance, so re-declaring it is a duplicate
  (Android Studio flags it). Test deps resolve their versions off the BOM regardless.
  Do not restore the line.

## Release build, signing & R8 `[BUILT]` `[from ticket 15]`

- **Output:** release buildType produces a sideload-able APK (~812K). No AAB / Play
  Console — personal-device sideload is the only target.
- **Signing:** `signingConfigs.register("release")` reads credentials from a
  git-ignored `keystore.properties` at the repo root; keystore lives at
  `~/.android/f1app-release.jks` (PKCS12, RSA-2048). Never commit keystore,
  properties file, or password.
- **R8:** AGP 9.x `optimization { enable = true }` DSL (one flag = R8 code
  shrinking + optimized resource shrinking + bundled default keep rules).
  Requires `android.r8.gradual.support=true` in `gradle.properties`.
- **Keep rules:** none. `f1/` is pure Kotlin (no reflection); Compose +
  kotlinx.serialization ship consumer rules. Add `src/<variant>/keepRules/*.keep`
  only if a release build strips something — not preemptively.
- **Versioning:** `versionCode 1` / `versionName "1.0.0"`, manual bumps, no
  auto-versioning plugin.
- Detail: [release/build-and-signing.md](release/build-and-signing.md).

## Architecture shape (target — from ticket 01; foundation slice `[BUILT]`)

Mirrors `PokemonDataViewer`. Single `:app` module.

**`[BUILT]`** — DI seam: `HomepageViewModel` takes the use case as a function
reference (`getSeason::invoke`) and is built via `homepageViewModelFactory(getSeason)`.
The manual `Wiring(context)` service locator (holds `HttpClient` + `GetSeasonUseCase`)
is held by `F1App` (`(application as F1App).wiring`); `MainActivity` reaches it via
`viewModelFactory { initializer { ... } }` in the screen composable. The widget
shares the same instance when it lands (ticket 07) — one composition root,
cross-entry-point.

**`[BUILT]`** — MVVM init-less contract: `HomepageViewModel` uses
`_uiState.onStart { load() }.stateIn(viewModelScope, WhileSubscribed(5_000), Loading)`;
`load` is `private suspend`. No `init {}`. The re-subscription test locks config-change
survival without re-firing the use case. The public `refresh()` method re-fires
`load(forceRefresh = true)` via `viewModelScope.launch`, wired to the
`PullToRefreshBox` in `HomepageScreen`.

**`[BUILT]`** — `core/Outcome.kt` sealed `Success`/`Failure`/`Loading` with `map`/
`fold`/`dataOrNull`; `Failure`/`Loading` are `Outcome<Nothing>` for variance.

**`[BUILT]`** — Domain seam: `GetSeasonUseCase` wraps `HttpClient.getCurrent()` in
`Outcome`, catching `ClientRequestException` (→ "Request failed (NNN)") /
`ServerResponseException` (→ "Server error (NNN)") / generic `Exception`
(→ `e.message ?: "Network error"`). `internal fun SeasonResponseDto.toSeason()` is
package-private; private `toRace()` helper.

**`[BUILT]`** — Network transport: `core/network/HttpClientFactory.kt` builds the
single Ktor `HttpClient` once at `F1App` startup. `f1/data/F1Api.kt` holds the
`F1API_BASE` const + `suspend fun HttpClient.getCurrent(forceRefresh)` (adds
`Cache-Control: no-cache` when `forceRefresh`). `JOLPICA_BASE` and `OPENF1_BASE` are
deferred to ticket 04 with the multi-source use cases — keeping them out of this
slice is the agreed scope.

**`[BUILT]`** — Navigation 3 shell (1.1.4 surface, see [core/navigation.md](core/navigation.md)):
`core/navigation/Routes.kt` declares `sealed interface Route : NavKey` with the 4
top-level tab data objects (`Homepage`/`Schedule`/`Leaderboard`/`MyTeam`).
`core/navigation/NavShell.kt` is the `Scaffold` + `NavigationBar` + `NavDisplay` host;
tapping a different tab clears the back stack and pushes the new top-level route.
Detail routes (`DriverDetail`/`TeamDetail`/`RoundDetail`/`CircuitDetail` per ticket 05)
land with the screens that open them.

**`[BUILT]`** — `core/ui/OutcomeContent.kt` (pinned for open #2): the shared
`Outcome<T>` → `Loading` (centered `CircularProgressIndicator`) / `Failure` (error
message + optional `Button(onClick = onRetry)`) / `Success` (caller lambda) family.
Every later screen reuses this shape; no per-screen ad-hoc loading/error rendering.
The retry button is suppressed when `onRetry == null` (read-only surfaces).

- **DI:** manual `Wiring(context)` held by a custom `Application` subclass; exposed as
  `app.wiring`. ViewModels reach in via `viewModelFactory { initializer { ... } }`
  factories. No Hilt. The widget shares the same `app.wiring` instance — one service
  locator, one pattern, cross-entry-point.
- **MVVM:** `ViewModel` + sealed `UiState` + `StateFlow`. State derived via
  `combine` of small `MutableStateFlow` atoms + `stateIn(WhileSubscribed(5_000))`.
  Loading is **init-less**: `Flow.onStart { load() }`, not `init {}`.
- **Result type:** sealed `Outcome<T>` (`Success`/`Failure`/`Loading`) at `core/`.
- **Domain seam:** UseCase classes; ViewModels take them as function references
  (`useCase::invoke`). No direct repository access from ViewModels.
- **Navigation:** Jetpack Navigation 3 — `NavKey` + `@Serializable` route objects
  + custom `Navigator`/`NavigationState`. Three top-level nav routes: `Homepage` (start),
  `Schedule`, `Leaderboard`; detail routes `DriverDetail(id)`, `TeamDetail(id)`,
  `RoundDetail(year, round)`, `CircuitDetail(circuitId)` (opened from RoundDetail's
  circuit block + Homepage §3 — home for the top-speed + most-wins stats).
  Countdown widget deep-links to `RoundDetail` via custom scheme
  `f1app://round/{year}/{round}` (`PendingIntent` over `Intent.ACTION_VIEW`, args
  from `NextRaceCache`); `MainActivity` parses the URI, pushes `RoundDetail` onto
  Homepage backstack root. Custom scheme only — no App Links.
  Navigation is Android-UI-layer; rewrites to SwiftUI nav if a KMP port happens.
- **Network:** Ktor Client, **CIO engine** (KMP-safe). `ContentNegotiation` with
  `kotlinx.serialization` JSON. `HttpCache` plugin for HTTP response caching (~10MB
  file cache, `max-stale` tolerance for offline cold launch, `CacheControl.NO_CACHE`
  per request for pull-to-refresh). One `HttpClient` instance held by `Wiring`;
  three base URL consts in `f1/data/F1Api.kt` (`F1API_BASE`, `JOLPICA_BASE`,
  `OPENF1_BASE`), full URLs per request (no default base URL — second/third sources
  are extensions on the same client, no separate package). Server cache headers
  probed live 2026-07-16: f1api.dev `max-age=600`, jolpica `max-age=3600`, OpenF1
  **none** (nginx, no CDN) → HttpCache skips OpenF1; accepted uncached (~0.3s/call,
  2 calls per Homepage §3 cold open). `ponytail:` add a default TTL / in-memory
  layer for OpenF1 only if latency becomes a measured complaint.
  Replaces PokeDV's Retrofit+OkHttp deliberately — Retrofit's `@GET`-style API
  interfaces are JVM-tied; Ktor endpoints are plain `suspend fun`s in pure Kotlin,
  so the API definition ports to a future KMP `:shared` module unchanged.

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
    Outcome.kt                    # sealed Success/Failure/Loading
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
- **Init-less VM tested via `Flow.take(2).toList()`.** The first 2
  emissions are `initialValue` (Loading) + first post-load emission
  (Success/Failure). `WhileSubscribed(5_000)` keeps the flow alive between
  subscribers, so a second `first()` after the first completes returns
  the cached Success without re-firing the use case — that one-line
  assertion is the config-change survival test.
- **Private suspend `load()`.** The VM's `load` is `private suspend` so
  the screen can trigger it via `onStart { load() }` (a suspend block),
  and the test exercises it through the public `uiState` subscription
  rather than calling it directly. No `viewModelScope.launch` timing
  dance in tests.
