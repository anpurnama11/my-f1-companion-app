# F1app summary

**Current code state:** a greenfield Jetpack Compose app (`com.anpurnama.f1_app`, single
`:app` module) with the dark-only Material3 theme (ticket 02) and a **release build
pipeline** (ticket 15) shipped, plus the **first foundation slice** (ticket 01) landed
end-to-end via TDD: `core/Outcome.kt` (sealed `Success`/`Failure`/`Loading`); the
pure-Kotlin `f1/` domain package (`f1/data/F1Api.kt` Ktor `/current` extension + the
`F1API_BASE` const — `JOLPICA_BASE`/`OPENF1_BASE` are deferred to ticket 04 with the
multi-source use cases; `f1/data/Dtos.kt` `@Serializable` envelopes; `f1/model/Season.kt`
domain models; `f1/GetSeasonUseCase.kt` with `internal SeasonResponseDto.toSeason()`
pre-computing `completedGp`/`totalKm`/`totalLaps`/`progressPercent`); the
**composition root** (`F1App` `Application` subclass + `core/di/Wiring` service locator
+ `core/network/HttpClientFactory` building the single Ktor `HttpClient` with the CIO
engine, `ContentNegotiation` (`ignoreUnknownKeys`/`coerceInputValues`), `HttpCache`
with a 10 MB `FileStorage` under `cacheDir/http_cache`, `HttpTimeout` 15s/10s, and
`expectSuccess = true`); a **4-tab Navigation 3 shell** (`core/navigation/Routes.kt`
with the `Homepage`/`Schedule`/`Leaderboard`/`MyTeam` `NavKey` data objects +
`NavShell.kt` with `NavigationBar` + `NavDisplay` using the `NavBackStack` /
`rememberNavBackStack` 1.1.4 surface); the **shared `OutcomeContent` composable
family** (`core/ui/OutcomeContent.kt` — loading / failure-with-retry / success —
pinned for open #2, every later screen reuses this shape); and the **Homepage screen**
(`feature/homepage/HomepageScreen.kt` rendering §2 aggregates inside a
`PullToRefreshBox` that calls `viewModel.refresh()` with `forceRefresh = true` so the
request bypasses HttpCache). `MainActivity` is now 14 lines: `setContent {
F1appTheme { NavShell() } }`. **24 JVM unit tests, 0 failures** (`Outcome` 6,
`SeasonAggregates` 6, `F1Api` 6, `GetSeasonUseCase` 3, `HomepageViewModel` 3); debug
+ release APKs both green (release = R8 minified + lint-vital clean).

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
