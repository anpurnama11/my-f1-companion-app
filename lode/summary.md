# F1app summary

**Current code state:** a greenfield Jetpack Compose app (`com.anpurnama.f1_app`, single
`:app` module) with the dark-only Material3 theme (ticket 02) and a **release build
pipeline** (ticket 15) shipped, the **first foundation slice** (ticket 01), and the
**second slice** (ticket 02 — Homepage §1 favorites pager + §3 top speed) landed
end-to-end via TDD. The §2-only `HomepageViewModel` grew into a 5-use-case combine
(`GetSeason` + `GetNextRace` + `GetDriversStandings` + `GetConstructorsStandings` +
`GetCircuitTopSpeed`) plus a `FavoritesCache` DataStore read; every section fails
independently, no composite "get homepage data" use case. New domain pieces: `f1/data`
adds `OPENF1_BASE` + `getNextRace` / `getDriversChampionship` /
`getConstructorsChampionship` / `getOpenF1Sessions` / `getOpenF1Laps` (no
`openf1/` package — per ticket 04's multi-source contract) and the 1-entry
`F1API_TO_OPENF1_COUNTRY` fallback map (Silverstone "Great Britain" →
"United Kingdom"); new use cases wrap them in `Outcome` with the same
4xx/5xx/general catch shape as `GetSeasonUseCase`; new DTO envelopes
(`NextRaceResponseDto`/`NextRaceInnerDto`, `DriversChampionshipResponseDto`,
`ConstructorsChampionshipResponseDto`, `OpenF1SessionDto`, `OpenF1LapDto`) with
`internal fun XxxDto.toXxx()` mappers; new domain models in `f1/model/NextRace.kt`
(`NextRace` carrying **both** `raceDate` and `qualyDate` — the OpenF1 join key
is `qualyDate`, not `raceDate`; ticket 11 research was wrong about the date match,
verified live 2027-01-15 — see Practices §"deviation notes"); `DriverStanding`,
`ConstructorStanding`, `TopSpeed`. `feature/favorites/FavoritesCache.kt` wraps
`DataStore<Preferences>` with `FAV_DRIVER_1`/`FAV_DRIVER_2`/`FAV_TEAM` typed
keys + a `seedIfEmpty(topTeamId, topDriverIds)` partial-fill-safe seed. New
`core/navigation/Routes.kt` `data class CircuitDetail(circuitId: String)` route
+ `NavShell` `entry<CircuitDetail>` placeholder (real page in slice 06); the §3
circuit card's `onClick` pushes the route on the back stack. `ui/theme/Color.kt`
adds `Circuits.forId(circuitId: String): Color` (kebab→CamelCase mapping for the
24-circuit palette, neutral fallback for unknowns). `HomepageScreen` now renders
all three sections: §1 `HorizontalPager` of driver cards + team card + next-race
card with page-indicator dots; §2 the original aggregates inside its existing
`OutcomeContent`; §3 a circuit card with `Circuits.forId(circuitId)` brand-accent
strip, the race details, and the top speed cell (empty for pre-2023, never a
fake "—"). `MainActivity` is still 14 lines. **63 JVM unit tests, 0 failures**
(`Outcome` 6, `SeasonAggregates` 6, `F1Api` 6, `GetSeasonUseCase` 3,
`NextRaceMapper` 2, `GetNextRaceUseCase` 6, `DriverStandingsMapper` 3,
`GetDriversStandingsUseCase` 3, `ConstructorStandingsMapper` 2,
`GetConstructorsStandingsUseCase` 3, `GetCircuitTopSpeedUseCase` 7,
`FavoritesCache` 6, `CircuitsForId` 3, `HomepageViewModel` 3,
`HomepageViewModelSectionIndependence` 4); debug + release APKs both green
(release = R8 minified + lint-vital clean).

**Design state:** the full app is *designed* — every architectural, data-source,
navigation, widget, and enrichment decision is locked in the wayfinder map + tickets
01–15. Those are **design-locked contracts**, not built code. The lode files under
`architecture/`, `practices.md`, `wayfinder/`, and `testing/` describe the *target*
system the build is working toward; their present-tense phrasing is the contract, not a
claim that the code exists. Treat them as the spec to build against, and update them to
"built" as code lands.

**Destination** (see [wayfinder/f1app/map.md](wayfinder/f1app/map.md)): a dark-first
Jetpack Compose Android app for F1 data — 4 top-level navs (Homepage, Schedule,
Leaderboard, My Team) + Driver/Team/Round/Circuit detail pages, and 1 home-screen
Countdown widget. Single `:app` module, manual `Wiring` service locator, MVVM with
init-less `onStart` loading + `combine` + `WhileSubscribed(5_000)`, sealed `Outcome<T>`,
UseCase seam, Navigation 3. Data is multi-source from the start (f1api.dev primary;
OpenF1 for top speed; jolpica for historical aggregations) over one Ktor `HttpClient`
(CIO engine, chosen so `f1/` ports to a future KMP `:shared` module). One
`CountdownWorker` (15-min WorkManager floor, adaptive hourly gate outside the race
window) feeds `NextRaceCache` (DataStore); the widget renders a render-time countdown
(no live chronometer). Architecture + design-system theme + testing scope match the
developer's prior `PokemonDataViewer` project shape.

**Build sequence:** ticket 02 (theme), ticket 15 (release pipeline), and the full
ticket-01 foundation slice (Outcome + f1/ + GetSeasonUseCase + HomepageViewModel +
`F1App`/`Wiring`/`HttpClientFactory` + Navigation 3 shell + `OutcomeContent` +
`HomepageScreen` + pull-to-refresh + tests) are shipped. Tickets 02→03→…→14 are the
remaining build order (each design-locked; build proceeds down the dependency chain).

See [architecture/architecture.md](architecture/architecture.md) for the *target*
module/DI/layering/tech decisions, [design-system/theme.md](design-system/theme.md)
for the built theme contract, [release/build-and-signing.md](release/build-and-signing.md)
for the built release pipeline, and [practices.md](practices.md) for the target
conventions.
