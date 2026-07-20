---
id: 09
title: Testing cut (pure mappings + VM transitions + widget reducer)
type: task
status: ready-for-agent
blocked_by: [02, 07]
owner: ""
---

# 09 — Testing cut (pure mappings + VM transitions + widget reducer)

**What to build:** the consolidated JVM unit testing cut per the spec's Testing Decisions. High leverage, lowest cost first: (1) pure-mapping logic — DTO→model mappers and `Season` aggregates on fixture DTOs including the `circuitLength` digit-strip edge case, plus `Outcome<T>` sealed-type transitions; (2) ViewModels — init-less `Flow.onStart { load() } + stateIn(WhileSubscribed(5_000))` load→success/failure transitions, Homepage section-level independence, using Ktor `MockEngine` stubs on the `F1Api.kt` extensions for 200 / 4xx / 5xx / empty-body; (3) the `CountdownWidget` render-time state reducer as a pure function (test the reducer, not Glance rendering). Libraries: `kotlin.test` + JUnit4 (Android default, no new catalog entry) + `ktor-client-mock` (transitively present via CIO). No Mockito/MockK — hand-written fakes over the `Wiring` + function-reference use case seam. Tests live in `:app` `src/test/`; tests for `f1/` are JVM-only and obey the domain-purity invariant (no `android.*` imports) so they port to a future KMP `:shared`. Compose UI screen smokes + theme-token screenshot tests remain deferred — `src/androidTest/` is reserved for them once Homepage + theme stabilize.

**Blocked by:** 02 — Homepage section independence + mappings to test; 07 — the widget state reducer to test (the `07` slice leaves a runnable check on the reducer; this slice consolidates and broadens coverage). This slice is the natural late item; it does not gate any feature.

**Status:** ready-for-agent

- [ ] Pure-mapping tests: `Season` aggregates incl. `circuitLength: "7004km"` digit-strip; DTO→model mappers in the use cases; `Outcome<T>` sealed-type transitions
- [ ] ViewModel tests: init-less `onStart`→success/failure transitions; Homepage section-level independence; Ktor `MockEngine` stubs on `F1Api.kt` extensions for 200/4xx/5xx/empty-body
- [ ] CountdownWidget state-reducer tests (pure fn): countdown / LIVE NOW / RACE COMPLETE / Season over / No race data / stale transitions
- [ ] `kotlin.test` + JUnit4 + `ktor-client-mock`; hand-written fakes (no Mockito/MockK); `:app` `src/test/` placement; `f1/` tests JVM-only + zero `android.*` imports (domain-purity invariant)
- [ ] Compose UI smokes + screenshot tests remain deferred — `src/androidTest/` reserved

Spec cross-ref: `lode/specs/f1app.md` (Testing Decisions), `lode/testing/scope.md`.