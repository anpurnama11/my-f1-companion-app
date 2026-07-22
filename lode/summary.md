# F1app summary

`com.anpurnama.f1_app` — dark-first Jetpack Compose F1 app. Greenfield scaffold
(`F1appTheme`, `Circuits`/`Tyres` palettes, Navigation 3 `NavShell`) plus Homepage
foundation with three sections (§1 upcoming-session countdown + favorites pager,
§2 season aggregates, §3 nearest-GP circuit card with top-speed line) and the
Schedule tab (Material 3 `TabRow` switching between **Upcoming** and **Past**;
Upcoming rows mirror the §3 card shape — round/GP name/date/city/circuit image;
Past rows add a P1/P2/P3 podium cell with per-row retry). Round detail page (race results +
qualifying + circuit block linked to `CircuitDetail`) is wired behind
`Route.RoundDetail`. Four bottom tabs (Homepage, Schedule, Leaderboard, My Team);
the latter two are placeholders. Data from f1api.dev primary, OpenF1 for top
speed. Manual `Wiring` DI, MVVM init-less, sealed `Outcome<T>`/`SectionUiState<T>`
with shared `OutcomeContent` renderer (ADR 0002). Single `:app` module; KMP
`:shared` extraction deferred.

> See [lode-map.md](lode-map.md) for the full index.
