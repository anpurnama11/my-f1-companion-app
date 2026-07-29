# F1app testing + out of scope

Testing decisions, out-of-scope features, and further notes.

## Testing Decisions

One guiding principle: test external behavior, not implementation details.
Reuse existing seams; the fewer, the better. Three seams in the initial
cut, two deferred until the UI stabilizes.

**Initial cut (JVM unit, `:app` `src/test/`):**

1. **Pure-mapping logic** — highest leverage, lowest cost. `Season` aggregates
   (`completedGp` / `totalKm` / `totalLaps` / `progressPercent`) on fixture
   DTOs, including the `circuitLength` digit-strip edge case. DTO→model
   mappers in the use cases. `Outcome<T>` sealed-type transitions.
2. **ViewModels** — init-less `Flow.onStart { load() } +
   stateIn(SharingStarted.Lazily)` loading → success/failure transitions;
   section-level independence on Homepage; Ktor `MockEngine` stubs on the
   `F1Api.kt` extensions for 200 / 4xx / 5xx / empty-body.
3. **Widget state reducer** — the `CountdownWidget` render-time state
   computation (countdown / LIVE NOW / RACE COMPLETE / off-season / no-cache)
   as a pure function. Test the reducer, not Glance rendering.

**Deferred until Homepage + theme stabilize** — `src/androidTest/` is
reserved for them:

4. Compose UI screen smokes (`Homepage`, `Schedule`, `Leaderboard`, `My
   Team`, `DriverDetail`, `TeamDetail`, `RoundDetail`, `CircuitDetail`) via
   `androidx.compose.ui:ui-test-junit4`.
5. Theme token screenshot tests (`Circuits.forId`).

**Libraries:** `kotlin.test` + JUnit4 (Android default, no new catalog entry)
+ `ktor-client-mock` (transitively present via CIO). No Mockito/MockK — the
`Wiring` + function-reference use case seam makes hand-written fakes cheaper.

**Module & invariants:** Tests live in `:app` — no `:testing` module (no
second module to share with yet). Tests for `f1/` are JVM-only — the
domain-purity invariant applies to tests too (no `android.*` imports), so
they port to a future KMP `:shared`.

## Out of Scope

- **Feeders (F2 / F3 / F1 Academy)** — no free API.
- **News + collaborator content screens** (BoxBoxClub, F1StatsGuru,
  FormulaAddict, FormulaAerodynamics, FormulaDataAnalysis, FormulaNeon,
  FormulaPlanet, TrackLimits) — served via Firebase Remote Config, no free
  API. **RSS news** (free, public feeds) is parked to v2 per GitHub
  ticket 25; the news tab replaces the My Team tab when un-parked.
- **Firebase Remote Config** as a data source — only the dropped screens /
  feeders used it.
- **7 secondary widget types** (Schedule, Drivers Standings, Team Standings,
  Champion, Season Progress, Favourite Driver, Favourite Team) — user chose
  Countdown widget only.
- **Driver Comparison screen** — dropped.
- **Driver Timeline Graph widget** — dropped (most call-heavy; per-round
  result fan-out).
- **Telemetry (RPM / speed / DRS), per-lap times, full pit-stop history** —
  OpenF1 / jolpica-only data not needed by any in-scope screen or widget.
  (Only the single fastest pitstop is surfaced on Race `SessionResult`.)
- **Weather + race-control flags** enrichments — out of scope for v1
  (live-window only; graduate as a fresh ticket).
- **App Links / `autoVerify`** — no public web domain; custom scheme only.
- **AAB / Play Console upload / store listing** — sideload APK only.
- **CI/CD pipeline** — separate from the first local signed build.
- **Cloud sync of favorites** — local DataStore only.
- **Star / pin on Driver / Team detail** — rejected; My Team is the pick
  surface.
- **End-to-end tests hitting live APIs** — manual smoke only; no free-API
  rate budget for CI.
- **Historical weather / past race-control** — live window only on first cut.
- **All-time OpenF1 top-speed scan** — parked; "Top speed" label ships as
  latest Qualifying peak only.
- **OpenF1 cache layer** — accepted uncached; revisit only if latency
  becomes a measured complaint.

## Further Notes

### One open question (fog, deliberately not fake-locked)

These are in-scope decisions that historical planning flagged as **not yet
specifiable** — sharpening either prematurely into the spec would manufacture
a decision that has no basis yet. Both affect build sequencing.

1. **v1 / MVP slice.** This spec describes the full app, but there's no
   decision yet on what lands in a first release candidate vs. what stays a
   follow-up. It interacts with the favorites picker (now closed — favorites
   are in v1) and the release pipeline (closed — signed APK is the output).
   The build implementer should treat this as the first cut-line question
   when picking what to build first; a follow-up GitHub issue is the right
   place to lock it once concrete screens exist to slice against.
### Resolved cross-cutting choice

The error / empty / loading UX pattern is resolved: `SectionUiState` is the
VM-to-UI transport and the shared `OutcomeContent` family renders loading,
error, content, and optional retry states. Homepage and Schedule use it with
independent section/row failures; later screens must reuse the same family
instead of introducing ad-hoc loading/error renderers. See ADR 0002.

### Design reference

boxbox-club (dark-first F1 design tokens) is the design reference, not a
parity target — the app leans on it for tokens and shape, not a faithful
re-skin of every surface. The full design tokens, per-screen data mapping,
and widget dimensioning live outside the repo in the developer's Downloads,
but their conclusions have been folded into this spec and the referenced
Lode entries; the spec does not depend on the Downloads copies persisting.

### Ponytail / BSSN standing preference

Simplest system that works, no speculative abstraction. Look before writing
— reuse a sibling helper before reimplementing. Items marked `ponytail:`
throughout this spec are deliberate simplifications with a named ceiling and
upgrade path; revisit only when the ceiling materializes.

### Standing "free API or not built" rule

"If not covered by a free API, it's not built." This is the user's rule about
*features* (feeders, news, collaborator screens — all out). Enrichments
(headshots, weather) are a separate question, decided above: headshots + team
imagery in; weather + flags out for v1.

### Current build status and sequence signal

Foundation + Homepage §2 (ticket 01), Homepage §1 countdown + §3 favorites /
nearest-GP polish (ticket 02), Schedule with per-row podium retry, Leaderboard
with Driver/Team detail navigation, and ticket 03's full Round/SessionResult
slice are shipped. Circuit detail is still a placeholder; enrichments and the
widget remain planned. My Team is built with
three cache-backed slots and standings-backed bottom-sheet pickers.

Build proceeds down the dependency chain: Architecture → Data layer → API
client → Navigation → Homepage slices → Schedule / Round detail →
Leaderboard / Driver-Team detail → widget →
research-backed stats → Favorites picker → Enrichments → Testing. Local
verification for the shipped slices passes JVM unit tests, debug android-test
Kotlin compilation and instrumentation on the Pixel AVD, debug/release
assembly, and `git diff --check`.
