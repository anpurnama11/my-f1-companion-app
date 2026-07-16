# F1app practices

Patterns, conventions, and invariants. Current state, not history.

## Architecture shape (from ticket 01)

Mirrors `PokemonDataViewer`. Single `:app` module.

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
  + custom `Navigator`/`NavigationState`. Routes: `Dashboard` (start), `DriverDetail(id)`,
  `TeamDetail(id)`, `RoundDetail(year, round)`.
  Navigation is Android-UI-layer; rewrites to SwiftUI nav if a KMP port happens.
- **Network:** Ktor Client, **CIO engine** (KMP-safe). `ContentNegotiation` with
  `kotlinx.serialization` JSON. `HttpCache` plugin for HTTP response caching. One
  `HttpClient` instance held by `Wiring`.
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

## Package layout

```
com.anpurnama.f1_app/
  F1App.kt                       # Application — holds `wiring: Wiring`
  MainActivity.kt
  core/
    di/Wiring.kt                  # manual service locator; HttpClient + use cases
    navigation/{Routes,Navigator,NavigationState,EntryProviders}.kt
    network/HttpClientFactory.kt  # Ktor HttpClient (CIO) + plugins
    Outcome.kt                    # sealed Success/Failure/Loading
    ExceptionExtension.kt
  f1/                             # DOMAIN — pure Kotlin, zero android.* imports
    data/{F1Api, Dtos, ...}.kt     # Ktor endpoint extensions + DTOs + repo interface
    {GetNextRaceUseCase, GetStandingsUseCase, GetRoundResultsUseCase, ...}.kt
  ui/theme/{Color,Theme,Type}.kt    # dark-only M3 theme — ticket 02. No Tokens.kt: F1 palettes (Circuits, Tyres, result accents) are grouped objects in Color.kt, not a separate file.
  feature/
    dashboard/{DashboardScreen,DashboardViewModel,DashboardViewModelFactory}.kt
    driver/...
    team/...
    round/...
  widget/
    countdown/{CountdownWidget, CountdownWorker, ...}.kt
```

## Build floor (added ticket 02)

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

## Standing preferences

- **Ponytail / BSSN:** simplest system that works; no speculative abstraction.
  Look before you write — reuse a sibling helper before reimplementing.
- **Design reference:** boxbox-club (dark-first F1 design tokens). Lean new app that
  *uses* it as a reference, not a faithful re-skin of every surface.
- **"If not covered by a free API, it's not built"** — user rule about *features*
  (feeders, news, collaborator screens all out). Enrichments (headshots, weather)
  are a separate question, decided in ticket 04.
- **Scope:** 4 screens + 1 widget. 7 secondary widget types and Driver Comparison
  are out, not deferred.
