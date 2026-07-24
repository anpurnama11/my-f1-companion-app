---
id: 09
title: Testing cut (pure mappings + VM transitions + widget reducer)
type: task
status: partial
blocked_by: [02, 07]
owner: ""
---

# 09 — Testing cut (pure mappings + VM transitions + widget reducer)

**What to build:** the consolidated JVM unit testing cut per the spec's Testing Decisions. High leverage, lowest cost first: (1) pure-mapping logic — DTO→model mappers and `Season` aggregates on fixture DTOs including the `circuitLength` digit-strip edge case, plus `Outcome<T>` sealed-type transitions; (2) ViewModels — init-less `Flow.onStart { load() } + stateIn(SharingStarted.Lazily)` load→success/failure transitions, Homepage section-level independence, and schedule/round lifecycle regression coverage, using Ktor `MockEngine` stubs on the `F1Api.kt` extensions for 200 / 4xx / 5xx / empty-body; (3) the `CountdownWidget` render-time state reducer as a pure function (test the reducer, not Glance rendering). The mapping, API, Homepage, Schedule, Round, FavoritesCache, and theme-token unit coverage is now present. The widget reducer remains blocked because the widget implementation is not yet in the repository; the full Compose smoke matrix is also pending. Libraries: `kotlin.test` + JUnit4 (Android default, no new catalog entry) + `ktor-client-mock` (transitively present via CIO). No Mockito/MockK — hand-written fakes over the `Wiring` + function-reference use case seam. Tests live in `:app` `src/test/`; tests for `f1/` are JVM-only and obey the domain-purity invariant (no `android.*` imports) so they port to a future KMP `:shared`.

**Blocked by:** 02 — Homepage section independence + mappings to test; 07 — the widget state reducer to test (the `07` slice leaves a runnable check on the reducer; this slice consolidates and broadens coverage). This slice is the natural late item; it does not gate any feature.

**Status:** partial — unit coverage for the shipped data/ViewModel slices is
present; widget reducer and the broader instrumentation cut remain pending.

- [x] Pure-mapping tests: `Season` aggregates incl. `circuitLength: "7004km"` digit-strip; DTO→model mappers in the use cases; `Outcome<T>` sealed-type transitions
- [x] ViewModel tests: init-less `onStart`→success/failure transitions; Homepage section-level independence; Ktor `MockEngine` stubs on `F1Api.kt` extensions for 200/4xx/5xx/empty-body
- [x] Lifecycle regression tests prove `SharingStarted.Lazily` does not re-fire loads after a long detail read / tab return
- [x] FavoritesCache, `TeamColors`, and Homepage §3 combined-card coverage added
- [ ] CountdownWidget state-reducer tests (pure fn): countdown / LIVE NOW / RACE COMPLETE / Season over / No race data / stale transitions
- [x] `kotlin.test` + JUnit4 + `ktor-client-mock`; hand-written fakes (no Mockito/MockK); `:app` `src/test/` placement; `f1/` tests JVM-only + zero `android.*` imports (domain-purity invariant)
- [x] Homepage favorites Compose test added under `src/androidTest/`
- [ ] Full Compose UI smoke matrix + screenshot tests; instrumentation execution remains pending because no device/emulator was available

Spec cross-ref: `lode/specs/f1app.md` (Testing Decisions), `lode/testing/scope.md`.
