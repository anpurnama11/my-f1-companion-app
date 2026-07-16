# F1 app build spec — wayfinder map

## Destination

An implementation-ready spec for building a Jetpack Compose Android app on the existing
`com.anpurnama.f1_app` scaffold. Dark-first F1 design system transcribed from
boxbox-club-DESIGN.md. **4 in-app screens**: Dashboard, Driver details, Team details,
Round details. **1 home-screen widget**: Countdown (upcoming race). Data from free F1
APIs (f1api.dev primary, OpenF1/jolpica only where a feature needs them). The map is done
when every decision a build session would need is locked.

Scope settled with the user:
- New app, not a parity clone of boxbox club.
- "If not covered by a free API, it's not built" → feeders (F2/F3/F1 Academy), news,
  collaborator content screens are out, not deferred.
- Widgets: **Countdown only** for now. The other 7 API-servicable widget types sit out
  of scope (option A).
- Screens: Driver Comparison dropped. Final = 4.

## Notes

- **References:** `~/Downloads/boxbox-club-DESIGN.md` (design tokens, widget dims,
  component spec) and `~/Downloads/boxbox-club-api-mapping.md` (per-screen/widget data
  sources, recommended architecture). Both live outside the repo; decisions recorded in
  tickets should fold their conclusions *into* the repo (Lode) rather than depend on the
  Downloads copies persisting.
- **Project today:** greenfield Compose scaffold. `:app` single module, `minSdk 24`,
  `targetSdk 36`, `compileSdk 36.1`, package `com.anpurnama.f1_app`. No data layer, no
  widgets, no DI. Anything-not-yet = a ticket decision, not an assumption.
- **Skills every session should consult:**
  - `jetpack-compose` and the `compose-*` family for any UI work.
  - `android-modularization` + `android-gradle-convention-plugins` if module structure is
    on the table (ticket 01).
  - `android-offline-first` for the data + widget-refresh layer (ticket 03).
  - `android-permissions` only if something needs it (likely none — widgets don't need
    runtime perms; network is declared, not requested).
- **Standing preference:** ponytail / BSSN — simplest system that works, no speculative
  abstraction. The reference is boxbox club; the goal is a lean new app that *uses* it as
  a design reference, not a faithful re-skin of every surface.
- **Lode:** none yet — `lode/terminology.md` and `lode/practices.md` get seeded as
  tickets resolve.

## Decisions so far

- **Ticket 01 (architecture) — CLOSED.** Single `:app` module, manual `Wiring` DI on a
  custom `Application` (no Hilt), MVVM with sealed `UiState` + init-less `onStart` +
  `combine` + `WhileSubscribed(5_000)`, UseCase seam, sealed `Outcome<T>`, Navigation 3.
  Network = Ktor Client (CIO) + ContentNegotiation + HttpCache — diverges from PokeDV's
  Retrofit+OkHash so the domain network layer ports to a future KMP `:shared` module.
  Domain-purity invariant: `f1/` is zero-`android.*`. Room is not an architectural tenet
  here (storage choice → ticket 03; lean = no Room + HttpCache).
  Lode: `lode/architecture/architecture.md`, `lode/practices.md`, `lode/terminology.md`.

- **Ticket 02 (theme) — CLOSED.** Dark-only `F1appTheme` (no light scheme, no
  dynamic color). Core semantic colors → `darkColorScheme()` from named `Color`
  vals in `Color.kt`. F1-specific palettes as `object`s in `Color.kt`
  (`Circuits` × 23, `Tyres` × 6 compounds, plus 4 result-highlight accents);
  no `Tokens.kt` (folded into the three theme files). `Typography` = M3
  defaults; `F1Shapes` (2/8/14/16dp); `object Spacing` (4–32dp). Side-effect
  from verification: `compileSdk`/`targetSdk` bumped 36 → 37 to satisfy the
  Compose BOM 2026.06.01 (Kotlin 2.4.10) transitive deps.
  Lode: `lode/design-system/theme.md` (new), `lode/practices.md` (build floor).

## Not yet specified

- **Headshots & weather (OpenF1 enrichments).** Driver details and Round details *could*
  pull driver headshot URLs and per-session weather/race-control from OpenF1 (the only
  free source). Whether to include these enrichments or ship Round/Driver detail without
  them isn't decided — it rides on ticket 04 (API client & scope). If 04 lands "f1api.dev
  only, no OpenF1", this fog clears for free.
- **Countdown widget refresh cadence & accuracy.** How often WorkManager polls, whether
  to compute countdown client-side from a cached next-race timestamp vs re-fetch on
  every update, and handling of the moment a race goes live. Sharpens after ticket 03
  (data layer) lands a sync strategy and ticket 06 (widget tech) picks Glance vs
  RemoteViews.
- **Testing scope.** No test strategy decided yet — unit (repos/viewmodels), Compose UI
  tests, screenshot tests for the design-system tokens. Likely a small frontier ticket
  once architecture (01) and theme (02) close.
- **Release / signing / R8.** The scaffold has `release { optimization.enable = false }`.
  Whether to enable R8 minification, set up a signing config, or stay in debug for the
  initial build. Unknown until the user signals a release target; stays fog until then.

## Out of scope

- **Feeders (F2 / F3 / F1 Academy)** — no free API. User ruled out: "if it's not covered
  by API then no need to create."
- **News + collaborator content screens** (BoxBoxClub, F1StatsGuru, FormulaAddict,
  FormulaAerodynamics, FormulaDataAnalysis, FormulaNeon, FormulaPlanet, TrackLimits) —
  served via Firebase Remote Config, no free API. Ruled out.
- **Firebase Remote Config** as a data source — only the dropped screens/feeders used it;
  removing them removes the need for any Firebase backend.
- **7 secondary widget types** (Schedule, Drivers Standings, Team Standings, Champion,
  Season Progress, Favourite Driver, Favourite Team) — user chose "option A": Countdown
  widget only for now. Re-scoping to add them later is a fresh effort, not a resumption.
- **Driver Comparison screen** — dropped by the user.
- **Driver Timeline Graph widget** — dropped by the user (most call-heavy; per-round
  result fan-out).
- **Telemetry (RPM/speed/DRS), per-lap times, pit stops** — OpenF1/jolpica-only data not
  needed by any in-scope screen or widget.
