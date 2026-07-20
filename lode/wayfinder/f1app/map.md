# F1 app build spec — wayfinder map

## Destination

An implementation-ready spec for building a Jetpack Compose Android app on the existing
`com.anpurnama.f1_app` scaffold. Dark-first F1 design system transcribed from
boxbox-club. **4 top-level navs** (Homepage, Schedule, Leaderboard, My Team) +
Driver/Team/Round/Circuit detail pages, and **1 home-screen widget** (Countdown). Data
from free F1 APIs (f1api.dev primary, OpenF1/jolpica only where a feature needs them).
The map is done when every decision a build session needs is locked.

> **Build vs decide.** Wayfinder plans; building is downstream of the map. A `closed`
> ticket here means the *planning decision* is locked — it says nothing about whether the
> code is built. Only ticket 02 (theme) is shipped as code; that fact lives in the Lode
> (`lode/summary.md`), **not** in ticket status.

## Notes

- **References:** `~/Downloads/boxbox-club-DESIGN.md` (design tokens, widget dims,
  component spec) and `~/Downloads/boxbox-club-api-mapping.md` (per-screen/widget data
  sources). Both live outside the repo; decisions recorded in tickets should fold their
  conclusions *into* the Lode rather than depend on the Downloads copies persisting.
- **Project today:** greenfield Compose scaffold. `:app` single module, `minSdk 24`,
  `compileSdk/targetSdk 37` (bumped by ticket 02), package `com.anpurnama.f1_app`. No
  data layer, no widgets, no DI yet — anything-not-yet-built is a ticket decision, not an
  assumption; build state tracks in `lode/summary.md`.
- **Skills every session should consult:** `jetpack-compose` + the `compose-*` family for
  UI; `android-modularization` + `android-gradle-convention-plugins` if module structure
  is on the table (ticket 01); `android-offline-first` for the data + widget-refresh
  layer (ticket 03); `android-permissions` only if something needs it (likely none).
- **Standing preference:** ponytail / BSSN — simplest system that works, no speculative
  abstraction. boxbox club is a design reference, not a parity target.
- **Lode:** `lode/summary.md` / `terminology.md` / `practices.md` describe the *code
  state* (mostly unbuilt); the map describes *planning decisions*. Two different
  artifacts — don't conflate.

## Decisions so far

- [Architecture & module structure](tickets/01-architecture-and-modules.md) — single
  `:app`, manual `Wiring` DI, MVVM init-less + sealed `Outcome<T>` + UseCase seam,
  Navigation 3; Ktor/CIO (not Retrofit) so `f1/` ports to a future KMP `:shared`.
- [Design system → Compose Material3 theme](tickets/02-design-system-theme.md) —
  dark-only `F1appTheme`; `Circuits`/`Tyres` palettes as `object`s in `Color.kt`; M3
  default typography; `F1Shapes` (2/8/14/16dp) + 8-rung `Spacing`. (Also the only ticket
  shipped as code so far.)
- [Data layer & widget refresh](tickets/03-data-layer-and-refresh.md) — no `F1Repository`
  class; `f1/data/F1Api.kt` = Ktor endpoint extensions; 8 screen-driven use cases;
  HttpCache + `NO_CACHE` pull-to-refresh; 1 periodic `CountdownWorker` → typed
  DataStore keys in `NextRaceCache`. No Room, no WorkManager-for-sync.
- [API client & enrichment scope](tickets/04-api-client-and-enrichment-scope.md) —
  multi-source on one `HttpClient` with per-request base URLs: f1api.dev primary,
  OpenF1 for top speed, jolpica for most-wins; no per-source package. Headshot/weather/
  flags enrichment parked to ticket 13.
- [Navigation & deep links](tickets/05-navigation-and-deep-links.md) — 7 flat `NavKey`
  routes (Homepage/Schedule/Leaderboard start + Driver/Team/Round/Circuit detail);
  widget deep-links `f1app://round/{year}/{round}` via custom scheme only (no App Links).
- [Widget technology](tickets/06-widget-technology.md) — Jetpack Glance;
  `CountdownWidget` reads `NextRaceCache` via `Wiring`; RemoteViews interop reserved as
  escape hatch only.
- [Countdown widget specifics](tickets/07-countdown-widget-specifics.md) — adaptive
  15-min/hourly cadence gated by the race window `[FP1_start, race_start + 3h]`;
  render-time LIVE/COMPLETE countdown (no live chronometer); off-season/no-cache/stale
  states; sizing 115×256dp min.
- [Research: top speed per circuit](tickets/08-research-top-speed.md) — OpenF1
  `/v1/laps` `st_speed` is the only free speed source; ships as latest-Qualifying peak
  labeled "Top speed". Detail: [top-speed.md](top-speed.md).
- [Research: most wins at circuit](tickets/09-research-most-wins-at-circuit.md) — jolpica
  `/circuits/{id}/results/1.json`, 1 call ~25KB, 5-entry circuitId translation map.
  Detail: [circuit-most-wins.md](circuit-most-wins.md).
- [Research: full podium on Past list](tickets/10-research-past-list-podium.md) —
  per-row `/race` P1/P2/P3 (no bulk podium endpoint); lazy per-row + HttpCache.
  Detail: [past-list.md](past-list.md).
- [Research: OpenF1 session_key join + all-time](tickets/11-research-openf1-join-all-time-top-speed.md)
  — join = `country_name + year + race-date match`; latest-only ("all-time" misleading,
  OpenF1 starts 2023); 1-entry `Great Britain → United Kingdom` fallback.
- [Additive UI enrichments (headshots, team/car imagery)](tickets/13-additive-ui-enrichments.md)
  — Tier 1 only: headshots (OpenF1, all driver surfaces) + team/car imagery
  (formula1.com Cloudinary 2026+, all team surfaces). Fallback chain:
  OpenF1 → Cloudinary portrait → team_colour swatch. Weather + flags out of scope.
- [Testing scope & strategy](tickets/14-testing-scope.md) — JVM unit (pure mappings,
  VM transitions, Ktor `MockEngine`) + Compose instrumented; no `:testing` module;
  `f1/` tests stay `android.*`-free to port with KMP; macrobenchmark deferred to ticket 15.
- [Release, signing & R8](tickets/15-release-signing-r8.md) — sideload-APK target;
  fresh PKCS12 keystore in `~/.android/`, `signingConfigs` from git-ignored
  `keystore.properties`; `optimization { enable = true }` (AGP 9 DSL) +
  `android.r8.gradual.support=true`; `versionCode 1` / `versionName "1.0.0"`;
  no app keeps needed. Macrobenchmark rung folded in (blocked only on real screens).

## Not yet specified

- **v1 / MVP slice.** The destination describes the full app, but there's no decision
  yet on what lands in a first release candidate vs. what stays a follow-up. The
  favorites picker/UX (open ticket 12) and release/signing (ticket 15) feed that cut.
  Ticket 13 enrichments are locked — headshots + team imagery in, weather + flags out.
- **Error / empty / loading UX pattern.** Per-screen ad-hoc, or a shared
  `Outcome`-driven composable family? Surfaced implicitly by every screen, never grilled
  as a cross-cutting decision.

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
  widget only. Re-scoping to add them later is a fresh effort, not a resumption.
- **Driver Comparison screen** — dropped by the user.
- **Driver Timeline Graph widget** — dropped by the user (most call-heavy; per-round
  result fan-out).
- **Telemetry (RPM/speed/DRS), per-lap times, pit stops** — OpenF1/jolpica-only data not
  needed by any in-scope screen or widget.
