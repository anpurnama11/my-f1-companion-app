# F1app build spec

Design system, package layout, release, signing, and build floor.

### Design system (already built — ticket 02)

- **`F1appTheme`** — single dark-only `@Composable`, one `content` param.
  No light scheme, no dynamic color, no `isSystemInDarkTheme`.
- `darkColorScheme()` built from named `Color` vals. `F1Shapes`
  (`small 2 / medium 8 / large 14 / extraLarge 16` dp) + 8-rung `Spacing`
  (4–32dp: xs/sm/md/normal/semiLg/lg/xl/xxl). M3 default typography.
- `Circuits` `object` — 21 per-circuit brand colours (backgrounds only on
  dark, never text).

### Schema noise locked into DTOs (no decisions, recorded for implementation)

- `position` is a `String` (`"1"` or `"NC"`) — model as String throughout.
- Race result `time` is messy (`"1:21:06.758"`, `"+1 lap"`, `"DNF (1)"`) —
  store as String, don't parse.
- `birthday` is dirty (ISO `2006-08-25` *and* `15/02/1998` mixed across
  drivers) — store as String, don't parse.
- `circuitLength: "7004km"` in `/current*` vs `7004` `Int` in
  `/circuits/{id}` — the `"<N>km"` form is **meters**, not km (Bahrain
  `"5412km"` = 5.412 km). Strip non-digits and **divide by 1000** to
  get real km; `Season.totalKm` is `Double` for precision.
- Envelope shape differs by endpoint: `/current/next` → `race: [...]` array;
  `/current` → `races: [...]` array; Jolpica standard
  `/{y}/{r}/results.json`|`/qualifying.json` → `MRData.RaceTable.Races[]`
  (Ergast envelope). Different envelope DTOs per source.
- `/current` `RaceSchedule` now includes `sprintQualy` and `sprintRace` fields
  (nullable) in addition to `fp1`/`fp2`/`fp3`/`qualy`/`race`; the model and UI
  must pick the five active sessions for the weekend.
- Jolpica alpha FP/SQ/SR results use opaque `round_id` + opaque driver/team
  ids, distinct from Ergast canonical; `loadAlpha` builds a `CarNumberTranslator`
  from the season-matched `getDrivers(year)` catalog and translates `car_number`
  → `(driverId, teamId)` at the data seam, with opaque-id fallback when the
  catalog misses. See architecture/id-namespaces.md (ADR 0005).
- Jolpica alpha FP results expose per-driver `best lap time` (string); position
  is implicit from ordering and the mapper assigns it.
- Three spellings of "firstAppearance" across endpoints
  (`firstAppareance`, `firstAppearance`, `firstParticipationYear`) —
  `@SerialName` per DTO.
- Jolpica standard race results expose `status` (`Finished`, `Lapped`,
  `Retired`, `Did not start`) and a numeric `grid`; `grid: "0"` means a
  pit-lane start.
- OpenF1 returns lowercase-no-underscore fields
  (`sessionkey`, `countryname`, `circuitshortname`) — OpenF1 DTOs get their
  own `@SerialName` mapping, distinct from f1api.dev's snake_case.

### Package layout

```
com.anpurnama.f1_app/
  F1App.kt                       # Application — holds `wiring: Wiring`
  MainActivity.kt
  core/
    di/Wiring.kt
    navigation/{Routes,Navigator,NavigationState,EntryProviders}.kt
    network/HttpClientFactory.kt
    Outcome.kt
    exception/ExceptionExtension.kt
  f1/                             # DOMAIN — pure Kotlin, zero android.*
    data/{F1Api, Dtos, ...}.kt
    model/                        # NextRace, Season (+aggregates), Race, Circuit,
                                  # Driver, Team, DriverStanding,
                                  # ConstructorStanding, RaceResult,
                                  # QualifyingResult, SessionType,
                                  # SessionResult, FastestPitstop
    {GetNextRaceUseCase, GetSeasonUseCase,
     GetDriversStandingsUseCase, GetConstructorsStandingsUseCase,
     GetDriverDetailUseCase, GetTeamDetailUseCase,
     GetRoundResultsUseCase, GetRoundQualifyingUseCase,
     GetPracticeResultUseCase,
     GetSprintResultUseCase, GetSprintQualifyingResultUseCase,
     GetSessionResultUseCase, GetFastestPitstopUseCase,
     GetCircuitTopSpeedUseCase, GetCircuitMostWinsUseCase,
     GetRoundPodiumUseCase}.kt
  ui/theme/{Color,Theme,Type}.kt   # [BUILT] dark-only M3 theme
  feature/
    homepage/{HomepageScreen,HomepageViewModel,HomepageViewModelFactory}.kt
    schedule/{ScheduleScreen,ScheduleViewModel,...}.kt
    leaderboard/{LeaderboardScreen,LeaderboardViewModel,...}.kt
    driver/...
    team/...
    round/{RoundScreen,RoundViewModel,...}.kt
    sessionresult/{SessionResultScreen,SessionResultViewModel,...}.kt
    circuit/...
  widget/countdown/
    CountdownWidget.kt
    CountdownWorker.kt
    data/NextRaceCache.kt
```

### Release, signing & R8 (`[BUILT]` — ticket 15)

- **Output:** release buildType produces a sideload-able APK. No AAB / Play
  Console.
- **Signing:** `signingConfigs.register("release")` reads credentials from a
  git-ignored `keystore.properties` at the repo root; keystore at
  `~/.android/f1app-release.jks` (PKCS12, RSA-2048).
- **R8:** `optimization { enable = true }` (AGP 9.x DSL — one flag = R8 code
  shrinking + optimized resource shrinking + bundled default keep rules) +
  `android.r8.gradual.support=true` in `gradle.properties`. No app-level
  keep rules (no reflection; Compose + kotlinx.serialization ship consumer
  rules). Add `src/<variant>/keepRules/*.keep` only if a release build strips
  something.
- **Versioning:** `versionCode 1` / `versionName "1.0.0"`, manual per-release
  bumps.

### Build floor (`[BUILT]`)

- `compileSdk = release(37)`, `targetSdk = 37` (bumped for AndroidX deps pulled
  by the Compose BOM 2026.06.01 / Kotlin 2.4.10). `minSdk = 24`.
- Don't re-declare `androidTestImplementation(platform(compose-bom))` — the
  `implementation(platform(...))` constraint propagates to androidTest via AGP
  inheritance.
