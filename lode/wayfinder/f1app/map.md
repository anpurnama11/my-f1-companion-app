# F1 app build spec — wayfinder map

## Destination

F1app v1 spec + v1 polish + GAP-F detail-page redesign. Tickets 01–15 are the v1
spec (closed); tickets 16–24 are the v1 polish pass (closed); ticket 25 is parked
(News RSS — v2). Tickets 26–29 are the GAP-F detail-page redesign — all four
closed (research 26, planning for F1DB catalog 27, planning for Wikipedia REST 28,
planning for the UI rewrite 29). The map is done when (a) every critique P0/P1 has
a locked decision, (b) every minor-observation item has a graduated execution
ticket, (c) the GAP-F detail-page work is fully ticketed in both wayfinder and
plans, and (d) no design decision in the codebase is unrecorded.

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
  DataStore keys in `NextRaceCache`. DataStore + HttpCache; WorkManager reserved for
  widget refresh only.
- [API client & enrichment scope](tickets/04-api-client-and-enrichment-scope.md) —
  multi-source on one `HttpClient` with per-request base URLs: f1api.dev primary,
  OpenF1 for top speed, jolpica for most-wins; no per-source package. Headshot/weather/
  flags enrichment parked to ticket 13.
- [Navigation & deep links](tickets/05-navigation-and-deep-links.md) — 7 flat `NavKey`
  routes (Homepage/Schedule/Leaderboard start + Driver/Team/Round/Circuit detail);
  widget deep-links `f1app://round/{year}/{round}` via single-app custom scheme.
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
- [Team-accent source](tickets/16-team-accent-source.md) — `TeamColors.forId` hardcoded
  map for v1 (11 constructors, Compose `Color(0xFF...)` from Jolpica alpha hex values);
  OpenF1 join rejected (30-day live window breaks the off-season favorites surface);
  Jolpica alpha `/alpha/core/teams/?year={year}` `primary_color` is the future source
  when the alpha tree stabilizes (issue #304). Detail: [team-accent.md](team-accent.md).
- [Q1/Q4 homepage layout](tickets/17-q1-q4-homepage-layout.md) — one §1 hero card with
  the countdown on top and the 5-row weekend schedule below (FP1 / FP2 / Quali / Sprint /
  Race, local times, reuses `SessionChip` + `formatStart`); not a separate section, not
  a pager card. §2 season aggregates unchanged. §3 favorites shape decided in ticket 18.
- [§3 favorites shape + empty-state behavior](tickets/18-section-3-favorites-shape.md) —
  one combined card with Driver 1, Driver 2, and Constructor rows; each row has a
  constructor-color leading bar. No favorites renders one “Pick favorites” CTA card.
- [Q2 podium shape lock](tickets/19-q2-podium-shape-locked.md) — `red = current/active
  (LIVE only)`. Past-row `P1` chip stays text-only with no fill, no background, no
  special color. P1 dominance is implicit (left-to-right scan order). The shape decision
  (chip *shape* was the broken element, not the colors) is final after the 2027-01-15
  three-iteration pass. ADR 0007.
- [Q3 `Constructor` caption](tickets/20-q3-constructor-caption.md) — keep `Constructor`
  on the §1 Team card. F1-orthodox, matches `ConstructorStanding` and Jolpica `Constructor`.
  "My team" rejected (misleading when no favorites picked). "Team" rejected (loses F1
  meaning). Glossary entry added to `lode/terminology.md` with rejected synonyms.
- [Default predictive back behavior for v1](tickets/23-default-predictive-back.md) — use
  Android and Navigation 3 defaults; no custom `PredictiveBackHandler` or screen-specific
  predictive-back animation in v1.
- [Remaining minor observations batch](tickets/22-remaining-minor-observations.md) — completed
  the v1 polish pass: combined favorites card, honest loading/error states, live/completion
  indicators, tappable semantics, and the completed/upcoming schedule fixture.
- [GAP-F research — DriverDetail/TeamDetail redesign data sources](tickets/26-research-gap-f-detail-redesign.md) —
  F1DB build-time for stats + team facts (chassis/PU/base country); Wikipedia REST for
  "About" (CC BY-SA); bar chart + base city + team principal dropped; all-time = race-only
  (sprint rounds filtered). Detail: [driver-team-detail.md](driver-team-detail.md) +
  [api-wrangling](driver-team-detail-api-wrangling.md).
- [F1DB driver + constructor + team-facts import](tickets/27-f1db-driver-constructor-import.md) —
  planned as [build ticket 12](../plans/f1app-build/tickets/12-f1db-driver-constructor-catalog-import.md):
  sister Python script (not extension) + separate F1DB pin (`tools/f1db/catalog-revision.txt` at
  v2026.10.1) + three `object` catalogs at `app/src/main/java/com/anpurnama/f1_app/f1/data/`.
  Race-only counts via `races-race-results.json` (NOT the `total*` fields on
  `f1db-drivers.json`, which include sprint). DNF = `reasonRetired != null AND
  positionNumber == null` — the strict rule is required to hit the "Antonelli 2026: 1 DNF"
  acceptance (R9 Spin is classified at position 14, NOT a DNF). Acceptance gate: script
  prints and asserts the Antonelli 2026 + Mercedes 2026 lines before writing any file.
- [DriverDetail / TeamDetail UI rewrite](tickets/29-driver-team-detail-ui-rewrite.md) —
  four sub-decisions locked: Compare card (DriverDetail only) = vs teammate per
  [ADR 0013](../decisions/0013-compare-card-vs-teammate.md); tab interaction =
  `SecondaryTabRow` + `HorizontalPager` (Leaderboard precedent); "About" section
  = bottom-placed inline; loading/error = `OutcomeContent` pattern. Implementation
  contract: [build ticket 14](../plans/f1app-build/tickets/14-driver-team-detail-ui-rewrite.md).
  Data layer (F1DB catalog from build 12 + Wikipedia REST from build 13) is already shipped.

## Not yet specified

<!-- see "Fog of war": in-scope fog you can't ticket yet; graduates as the frontier advances -->

None. The GAP-F detail-page work is fully ticketed (26 + 27 + 28 + 29 all closed;
build 12 + 13 shipped; build 14 ready). The map is done pending build 14.

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
