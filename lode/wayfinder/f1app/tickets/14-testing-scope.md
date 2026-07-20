---
id: 14
title: "Testing scope & strategy"
type: grilling
status: closed
blocked_by: []
owner: ""
---

## Question

What gets tested, how, and where does it live? No test strategy was
decided while architecture (01) and theme (02) were open; both closed.
The scaffold currently ships zero tests. This ticket picks the scope and
the libraries so implementation tickets know where their tests go.

## Candidate surfaces (ranked by leverage)

1. **Pure-mapping logic** (highest leverage, lowest cost):
   - `Season` aggregates (`completedGp` / `totalKm` / `totalLaps` /
     `progressPercent` from `/current`) — pure functions, asserts
     on fixture DTOs. `circuitLength` digit-strip edge case lives here.
   - DTO→model mappers in the use cases.
   - `Outcome<T>` sealed type transitions.
2. **ViewModels** (Outcome + StateFlow composition):
   - init-less `Flow.onStart{load()} + stateIn(WhileSubscribed(5_000))`
     behavior — loading → success/failure transitions, section-level
     independence on Homepage.
   - Repository/`F1Api.kt` HttpClient extension stubs (Ktor
     `MockEngine` for 200/4xx/5xx/empty-body).
3. **Compose UI**:
   - Screen-level smoke tests for `Homepage`, `Schedule`, `Leaderboard`,
     `My Team`, `DriverDetail`, `TeamDetail`, `RoundDetail`,
     `CircuitDetail`.
   - Theme token assertions (`Circuits.forId`, `Tyres.Soft` + `Tyres.SoftBg`
     pairing) via screenshot tests — the ticket-02 contract.
   - The favorites `ModalBottomSheet` picker (ticket 12).
4. **Widget** (Glance): `CountdownWidget` render-time state computation
   (countdown / LIVE NOW / RACE COMPLETE / off-season / no-cache) as a
   pure function — test the state reducer, not Glance rendering.

## Decision needed

- **Which surfaces ship in the initial cut?** Ponytail lean: (1) pure
  mappings + (2) ViewModel `Outcome` transitions + Ktor `MockEngine`
  stubs. Compose UI tests + screenshot tests are high-cost / high-flake
  on a fresh project; add when the theme and one screen stabilize.
- **Libraries** (match existing scaffold if any are present — check
  `libs.versions.toml`):
  - Unit: `kotlin.test` + JUnit4 (Android default) — no new dependency.
  - Ktor stubs: `ktor-client-mock` (already transitively available via
    the CIO engine choice in ticket 01).
  - Compose UI: `androidx.compose.ui:ui-test-junit4` + a
    `androidx.compose.ui.tooling.preview` screenshot harness — only if
    UI tests are in scope.
  - No Mockito/MockK (the project's `Wiring` + function-reference use
    cases make hand-written fakes cheaper; per the `android-test-doubles`
    skill preference).
- **Module placement**: tests live in `:app` `src/test/` (unit) and
  `src/androidTest/` (instrumented) — matches the single-module
  architecture from ticket 01. No `:core:testing` module (no second
  module to share with yet, per ticket 01's one-module decision).
- **Domain-purity invariant**: tests for `f1/` (domain + DTOs + Ktor
  API extensions) must be JVM-only — no `android.*`. The KMP-port
  invariant from ticket 01 applies to tests too.

## Out of scope

- End-to-end tests hitting live APIs — manual smoke only; no free-API
  rate budget for CI.
- Screenshot golden updates as a CI gate — local-only until a release
  flow exists (ticket 15).
- A `:testing` module — premature under the one-module architecture.
- Performance / macrobenchmark tests — deferred; no baseline profile
  requirement yet.

## Default resolution if not decided

Scope = pure mappings + ViewModel `Outcome` transitions + Ktor
`MockEngine` on `F1Api.kt` extensions. Libraries = `kotlin.test`/JUnit4
+ `ktor-client-mock` (both transitively present, no new catalog entry).
Placement = `:app` `src/test/` for JVM unit, `src/androidTest/` reserved
for later Compose UI tests. Compose UI + screenshot tests deferred to a
follow-up under this ticket once Homepage + theme stabilize.

## Cross-references

- Ticket 01: `lode/architecture/architecture.md` — single-module,
  init-less ViewModel + `Outcome` + `Wiring`, domain-purity invariant.
- Ticket 02: `lode/design-system/theme.md` — `Circuits` / `Tyres` /
  `F1Shapes` / `Spacing` contracts screenshot tests would assert.
- Ticket 03: `lode/wayfinder/f1app/tickets/03-data-layer-and-refresh.md`
  — use-case + `F1Api.kt` extension surface to stub.
- `android-test-doubles` skill — hand-written fakes over MockK.
