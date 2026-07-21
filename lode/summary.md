# F1app summary

`com.anpurnama.f1_app` — dark-first Jetpack Compose F1 app. Greenfield scaffold
(`F1appTheme`, `Circuits`/`Tyres` palettes, Navigation 3 `NavShell`) plus Homepage
foundation with three sections: §1 upcoming-session countdown + favorites pager,
§2 season aggregates + round list (upcoming/past), §3 nearest-GP circuit card with
top-speed line. Four bottom tabs (Homepage, Schedule, Leaderboard, My Team); the
latter three are placeholders. Data from f1api.dev primary, OpenF1 for top speed.
Manual `Wiring` DI, MVVM init-less, sealed `Outcome<T>`/`SectionUiState<T>` with
shared `OutcomeContent` renderer (ADR 0002). Single `:app` module; KMP `:shared`
extraction deferred.

> See [lode-map.md](lode-map.md) for the full index.
