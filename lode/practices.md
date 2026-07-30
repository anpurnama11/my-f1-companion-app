# F1app practices

Project conventions in force for F1app. Most match DogBreedViewer's
[practices.md](../../DogBreedViewer/lode/practices.md) — the standing
reference corpus — re-judged here for the Ktor/f1api.dev/Jolpica surface,
never copied blindly.

## Dependency injection (no framework)

- All dependencies wired manually in `Wiring(context: Context)`. The custom
  `Application` (`F1App`) creates one `Wiring` during `onCreate()`, but the
  dependencies inside `Wiring` are `by lazy` so each screen/worker/widget pays
  only for the slice it first touches. No Hilt, Koin, CompositionLocal.
- Infrastructure (Ktor `HttpClient` CIO, `HttpCache` ~10MB file storage,
  `DataStore`) is private to `Wiring`.
- Use cases, cache repositories, `SnapshotStore`, and `FavoritesCache` stay
  private lazy properties on `Wiring`; `httpClient` remains an `internal` lazy
  property because Coil shares it, and `nextRaceCache` remains an `internal`
  lazy property because the Glance widget reads it.
- ViewModels are constructed via `viewModelFactory { initializer { ... } }`
  inside per-feature `*ViewModelFactory` functions. `Wiring` exposes one
  feature-level factory method per screen route, such as
  `wiring.homepageViewModelFactory()`, and those methods delegate to the
  per-feature factories with the private use cases/cache repositories.
- Screens may fetch `(LocalContext.current.applicationContext as F1App).wiring`,
  but only to request the feature-level ViewModel factory. They must not assemble
  ViewModels by selecting individual `Wiring` fields.
  Do **not** read `APPLICATION_KEY` — Navigation 3's entry-scoped
  `ViewModelStoreOwner` (from `rememberViewModelStoreNavEntryDecorator()`)
  does not propagate the Activity's `APPLICATION_KEY` into the entry's
  `CreationExtras`, so `null as F1App` throws NPE.
- Never inject `Wiring` into composables or ViewModels. Always go through
  `Screen → wiring.featureViewModelFactory() → ViewModel → use case`.

## Navigation (Navigation 3 multi-backstack)

- Jetpack Navigation 3 (`androidx.navigation3:navigation3-runtime` +
  `navigation3-ui` + `lifecycle-viewmodel-navigation3`).
- Four top-level `NavKey` tabs — `Homepage`, `Schedule`, `Leaderboard`,
  `MyTeam` — each its own `NavBackStack`. Detail routes (`CircuitDetail`,
  `RoundDetail`, `DriverDetail`, `TeamDetail`, `SessionResult`) are child
  routes stackable on any tab. Routes are `@Serializable` so Navigation 3
  can serialize the back stack across process death.
- `NavigationState` is the hand-rolled state holder.
- `Navigator(state)` is the thin convenience wrapper with `navigate(key)`,
  `goBack()`, `popToRoot()`. Re-tapping the active bottom-bar tab calls
  `popToRoot()`.
- `EntryProviders.allEntries(navigator)` maps each route to its composable.
- Deep link: `f1app://round/{year}/{round}` (`Route.RoundDetail`); parsed in
  `MainActivity` and injected into `NavigationState` on launch.

## Use case pattern

- Single class with a single `invoke()` method.
- Returns `Outcome<T>` — sealed `Success(data)`, `Failure(errorMessage)`,
  `Loading`.
- Follows the suspend pattern:
  `try { emit(Loading); ...; Success(data) } catch { Failure(msg) }`.
- Never throws to the ViewModel. All non-cancellation exceptions are
  caught and wrapped in `Outcome.Failure`. Re-throws `CancellationException`
  to allow structured-concurrency cancellation.
- **Stateless.** No mutable fields, no cache, no in-progress tracking. Each
  `invoke()` call is independent. Lazy-wired infra lives in `Wiring`;
  observation lives in the cache layer; presentation state lives in the
  ViewModel.
- Coordinate between data sources directly — no repository layer sits
  between them and the API extensions (avoids the anemic-repository
  anti-pattern). A repository layer is legitimate **only** for offline-first
  coordination (cache as SSOT), and even then it must be a cohesive stateless
  coordinator, not a forwarding wrapper.
- **No default base URL on the `HttpClient`.** Each Ktor extension builds its
  full URL from per-source `*_BASE` constants (`F1API_BASE`, `JOLPICA_BASE`)
  in `f1/data/F1Api.kt`. One client, per-request URLs.

## ViewModel pattern

The convention: **no `init {}` in ViewModels.** First load fires from
`Flow.onStart { warmUp() }` under `SharingStarted.Lazily`, not from an
`init {}` block. The cold stream runs under `Lazily` so the first load fires
once when the first subscriber appears, and subsequent subscribers read the
existing `StateFlow` value without re-firing. Re-fire is via
`viewModel.refresh()` (pull-to-refresh) only.

- ViewModel exposes a single `val uiState: StateFlow<UiState>`.
- `UiState` is a sealed class: `Loading`, `Error(message)`, `Content(data)`.
- Section-level independence: screens with multiple independently-failing
  sections use a `Sections(...)` envelope combining per-section
  `MutableStateFlow<SectionUiState<T>>` atoms. `combine(..., ::Sections)`
  + `onStart { warmUp() }` + `stateIn(Lazily)` is the canonical shape.
- Each `SectionUiState<T>` carries `ContentSyncStatus`
  (`Fresh` / `Stale` / `Refreshing` / `RefreshFailed`) when backed by the
  offline cache (ADR 0018). Stale content stays visible; never degrades to
  full-section `Error`.
- Cache-aware ViewModels observe cached snapshots in `warmUp()` and seed
  `SectionUiState.Content(...)` from the snapshot before the network call
  resolves. Network success refreshes; network failure preserves cached
  content and updates sync status.

### `SharingStarted` policy

- Prefer `SharingStarted.Lazily` for screen VMs whose data is server-cached.
  `Lazily` starts the cold upstream on the first subscriber and never stops
  it for the holder's lifetime (`viewModelScope`). Subsequent subscribers
  read the existing `StateFlow` value; no re-fire.
- Reserve `WhileSubscribed` for genuinely expensive or user-scoped streams.
- Never `Eagerly` for screen VMs — it bypasses the first-subscriber gate
  and can fire on background-tab construction.

## Outcome → SectionUiState boundary

`Outcome<T>` (data-layer result) stops at the VM boundary; composables never
import it (ADR 0002). The VM maps `Outcome` to `SectionUiState` at the seam.
The shared `OutcomeContent` composable family renders `SectionUiState<T>`:
`Loading`, `Error(message, onRetry)`, `Content(data)`. Every screen reuses this
family — no ad-hoc loading/error renderers.

## Domain-purity invariant

`f1/` — domain models, DTOs, repository interface, use cases, and the Ktor
`HttpClient` API extensions — **must contain zero `android.*` imports**.
Platform concerns (`Context`, `android.util.Log`, dispatchers) get injected
as interfaces from `core/`.

This is the cost-free hedge for a future Kotlin Multiplatform port: `f1/`
becomes the `:shared` module's `commonMain` with zero edits. Violations
must move behind an injected interface.

See: [architecture/architecture.md](architecture/architecture.md) §Domain-purity invariant.

## Error handling

- API errors caught in use cases and wrapped in `Outcome.Failure(message)`.
- `ExceptionExtension.kt` maps Ktor/IO exceptions to stable user-readable
  strings.
- ViewModels never expose raw exceptions to UI.
- UI error states include a retry action that calls `viewModel.refresh()`.

## Testing

- `testImplementation` deps: JUnit4, `kotlinx-coroutines-test`, Turbine.
  `androidTestImplementation`: AndroidX JUnit, Espresso, Compose UI test.
- **JUnit4 over `kotlin.test`.** `kotlin.test` is not on the test classpath
  (the Android default pulls only JUnit4). Use `org.junit.Assert.*` +
  `@org.junit.Test`. Don't add `kotlin.test` as a dep.
- **Hand-rolled fakes, not mocks.** A VM test fake use case is a plain
  `suspend (Boolean) -> Outcome<Season>` lambda. Capture args in the lambda
  for assertion (e.g. `receivedForceRefresh`). No `Mockito`/`MockK`.
- **Internal mappers cross packages within `:app`.** `internal fun` on
  `SeasonResponseDto.toSeason()` is reachable from `app/src/test/.../f1/...`
  because the test is the same module. Follow the pattern: pure mapping
  helpers go `internal`, not `public`.
- **VM test assertions are on `SectionUiState`, not `Outcome`.** Use cases
  return `Outcome`; the VM maps to `SectionUiState` at the seam. State
  assertions read `sections.season is SectionUiState.Content` /
  `SectionUiState.Error`.
- **Init-less VM tested via `Flow.take(2).toList()`.** The first 2
  emissions are `initialValue` (Loading) + first post-load emission
  (Success/Failure). `Lazily` keeps the flow alive for the entire
  `viewModelScope` lifetime, so a second `first()` after the first
  completes returns the cached Success without re-firing the use case.
- **Back-pop regression test:** subscribe → unsubscribe → advance time
  by 60s → resubscribe → assert call counts unchanged. Pins the `Lazily`
  contract.
- **`MockEngine` body via the `String` overload of `respond`.** Wrapping the
  body in `ByteReadChannel(...)` up front confuses the deserializer — the
  engine re-wraps the channel and `ContentNegotiation` sees a
  `SourceByteReadChannel` it can't match, throwing
  `NoTransformationFoundException`. Pass the raw `String`; Ktor wraps it
  itself. Combine with `expectSuccess = true` on the test client so 4xx/5xx
  throw before body deserialization.

## Build floor

- `compileSdk = release(37)`, `targetSdk = 37`. Bumped because AndroidX
  Compose BOM 2026.06.01 hard-requires SDK 37. When a dep bumps the floor
  again, bump it again; do not pin or downgrade.
- `minSdk = 24`. Don't raise without a user-driven reason.
- **Date/time rule:** minSdk 24 rules out `java.time.*` (API 26+) and the
  project does NOT enable `coreLibraryDesugaring`. All date/time parsing +
  formatting uses `kotlinx.datetime`. Manual formatting
  (e.g. `dayOfWeek.name.take(3)`) is acceptable for one-screen strings;
  do not pull in a desugar switch for that.
- `androidTestImplementation(platform(libs.androidx.compose.bom))` is
  **intentionally absent** — the `implementation(platform(...))` constraint
  already propagates to androidTest via AGP inheritance, so re-declaring it
  is a duplicate. Do not restore the line.

## Release build, signing & R8

> Detail: [release/build-and-signing.md](release/build-and-signing.md).

## Package layout

```
com.anpurnama.f1_app/
  F1App.kt                       # Application — holds `wiring: Wiring`
  MainActivity.kt
  core/
    di/Wiring.kt                  # manual service locator; HttpClient + caches + use cases
    navigation/{Routes,Navigator,NavigationState,EntryProviders}.kt
    network/HttpClientFactory.kt  # Ktor HttpClient (CIO) + plugins
    Outcome.kt                    # sealed Success/Failure/Loading (data-layer only)
    ui/                           # SectionUiState.kt (VM→UI transport) + OutcomeContent.kt
    exception/ExceptionExtension.kt
  f1/                             # DOMAIN — pure Kotlin, zero android.* imports
    data/{F1Api, Dtos, ...}.kt    # Ktor endpoint extensions + @Serializable DTOs
    model/                        # domain models (Season, Race, Circuit, Driver, ...)
    {GetSeasonUseCase, GetNextRaceUseCase, ...}.kt
  ui/theme/{Color,Theme,Type}.kt  # dark-only M3 theme; Circuits palette in Color.kt
  feature/
    homepage/{HomepageScreen,HomepageViewModel,HomepageViewModelFactory}.kt
    schedule/{ScheduleScreen,ScheduleViewModel,...}.kt
    leaderboard/{LeaderboardScreen,LeaderboardViewModel,...}.kt
    round/{RoundScreen,RoundViewModel,...}.kt
    circuit/{CircuitScreen,CircuitViewModel,...}.kt
    myteam/{MyTeamScreen,MyTeamViewModel,...}.kt
    favorites/                      # picker + bottom-sheet
  widget/
    countdown/
      CountdownWidget.kt            # GlanceAppWidget
      CountdownWorker.kt            # periodic WorkManager worker
      data/NextRaceCache.kt         # DataStore<Preferences>
```

## Standing preferences

- **Ponytail / BSSN:** simplest system that works; no speculative abstraction.
  Reuse a sibling helper before reimplementing.
- **Every construct earns its place:** add code, parameters, configuration,
  abstractions, conversions, comments, and tests only when they change
  behavior, explain a hidden constraint, prevent a real failure, or make
  future change simpler. Delete speculative and duplicate constructs.
- **Comments explain hidden constraints, not code:** reserve them for
  compatibility, domain rules, external behavior, performance constraints,
  and trade-offs. Do not narrate obvious implementation.
- **Tests protect behavior, not shape:** add a focused test for a meaningful
  guarantee, tricky logic, external-system assumption, or likely regression;
  do not test plumbing, delegation, or incidental implementation details.
  See [testing/scope.md](testing/scope.md).
- **Abstractions stay local until proven:** extract only when meaningful
  duplication, domain clarity, reduced error risk, or a better future tool
  outweighs the added indirection.
- **"If not covered by a free API, it's not built"** — user rule about
  features. Enrichments (headshots, weather) are a separate question,
  decided per ticket.
