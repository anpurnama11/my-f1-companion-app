---
id: 01
title: Foundation + Homepage §2 (season aggregates)
type: task
status: shipped
blocked_by: []
owner: ""
shipped_at: 2026-07-20
---

# 01 — Foundation + Homepage §2 (season aggregates)

**What to build:** the app launches into the dark `F1appTheme` and shows a Homepage where §2 ("season progress") renders the current season's aggregates — completed GPs, total km, total laps, progress percent — from the live `f1api.dev /current` endpoint. This slice stands up every shared foundation the rest of the build routes through: the `F1App` Application holding a `Wiring` service locator, the Ktor `HttpClient` (CIO engine + `ContentNegotiation`/`kotlinx.serialization` + `HttpCache` ~10MB), the sealed `Outcome<T>`, the `f1/` pure-Kotlin domain package (`F1Api.kt` `/current` extensions + DTOs + mappers + `Season` model + `GetSeasonUseCase`), and the Navigation 3 shell whose bottom bar shows all 4 top-level tabs (Homepage start, Schedule, Leaderboard, My Team) — only Homepage is wired this slice, the other three are placeholder routes. The Homepage ViewModel follows the init-less `Flow.onStart { load() } + stateIn(WhileSubscribed(5_000))` contract and holds just this one use case (§1/§3 land in slice 02).

**This slice also pins the spec's open question #2** — the error/empty/loading UX family. Pick a shared `Outcome`-driven composable shape here (one set across loading/empty/failure/retry) that every later screen reuses; do not defer it per-screen and discover duplication later. Whatever shape wins here becomes the contract the other slices follow.

**Blocked by:** None — can start immediately.

**Status:** shipped (2026-07-20)

- [x] `F1App` Application subclass holds `wiring: Wiring`; `Wiring(context)` exposes the `HttpClient` and use cases; `MainActivity` reaches it via `viewModelFactory { initializer { ... } }`
- [x] `HttpClient` built with CIO engine, `ContentNegotiation` (`ignoreUnknownKeys = true; coerceInputValues = true`), `HttpCache` ~10MB file cache; `max-stale` tolerance for offline cold launch
- [x] `core/Outcome.kt` sealed `Success`/`Failure`/`Loading`
- [x] `f1/data/F1Api.kt` defines `F1API_BASE` const + `suspend fun HttpClient.getCurrent()` extension; DTOs (`/current` envelope `races: [...]`, per-endpoint `@SerialName` for the three spellings of "firstAppearance"); mappers to domain `Season` model with `completedGp`/`totalKm`/`totalLaps`/`progressPercent` pre-computed in the use case (`circuitLength: "7004km"` is **meters**, not km — strip non-digits then divide by 1000; `totalKm` is `Double`)
- [x] `f1/model/Season.kt`, `f1/GetSeasonUseCase.kt` — zero `android.*` imports (domain-purity invariant enforced)
- [x] Navigation 3 shell: `NavKey` + `@Serializable` route objects (`Homepage`, `Schedule`, `Leaderboard`, `MyTeam`, + detail routes reserved), 4-tab bottom bar; only `Homepage` renders real content this slice
- [x] `HomepageViewModel` (init-less) exposes `StateFlow<UiState>`; §2 section only; `HomepageScreen` renders aggregates against the fade of/theme tokens
- [x] Shared `Outcome`-driven error/empty/loading/retry composable family introduced here and reused by later slices — pinned as the pattern (open #2 resolved)
- [x] Pull-to-refresh on §2 uses `CacheControl.NO_CACHE` per request on the same `HttpClient`
- [x] One JVM unit check: `Season` aggregates on a fixture DTO incl. the `circuitLength` digit-strip edge case (6 tests in `SeasonAggregatesTest`)

Spec cross-ref: `lode/specs/f1app.md` (Implementation Decisions), `lode/architecture/architecture.md`, `lode/design-system/theme.md`.