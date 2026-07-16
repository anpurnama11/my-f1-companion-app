# F1app terminology

Short term → meaning lines. Domain + project language.

- **F1app** — this app, package `com.anpurnama.f1_app`, Android Compose, dark-first.
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
  widget in scope.
- **Tour/race/round** — an F1 race weekend. "Round" = a numbered race in a season
  (`round/{year}/{round}` route). "Next race" = `/current/next` endpoint from f1api.dev.
- **f1api.dev** — primary free F1 API (schedule, standings, results, circuit metadata,
  pre-joined driver+team). Zero auth.
- **OpenF1** — free secondary API; only free source for driver headshots, per-session
  weather, race-control flags. Inclusion TBD (ticket 04).
- **jolpica** — overlaps f1api.dev; adds pit stops. Not anticipated in scope.
- **Wayfinder map** — `lode/wayfinder/f1app/map.md`; the destination spec + scope
  decisions. Tickets live under `lode/wayfinder/f1app/tickets/`.
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
