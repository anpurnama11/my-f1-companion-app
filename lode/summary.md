# F1app summary

`com.anpurnama.f1_app` — dark-first Jetpack Compose F1 app. Greenfield scaffold
(`F1appTheme`, `Circuits`/`Tyres` palettes, Navigation 3 multi-backstack `NavShell`)
plus Homepage foundation with three sections (§1 upcoming-session countdown,
§2 season aggregates, §3 combined favorites + nearest-GP circuit cards with
top-speed line) and the Schedule tab (Material 3 `SecondaryTabRow` plus
`HorizontalPager` switching between **Upcoming** and **Past**; Upcoming rows
mirror the §3 card shape — round/GP name/date/city/circuit image; Past rows add
a P1/P2/P3 podium cell with per-row retry).
Round detail page (`Route.RoundDetail`) switches between upcoming mode
(race-weekend schedule + circuit stats) and past mode (per-session result rows);
tapping **Results** pushes `Route.SessionResult` for a full session result list.
The circuit block links to `CircuitDetail`. Four bottom tabs
(Homepage, Schedule, Leaderboard, My Team); Leaderboard has Drivers and
Constructors sub-tabs and drills into Driver and Constructor detail pages,
while My Team manages two favorite drivers and one favorite constructor through
a standings-backed bottom-sheet picker. Driver IDs are unique across the two
slots, enforced atomically in `FavoritesCache`; the shared cache updates
Homepage §3 reactively.
Data from f1api.dev primary, OpenF1 for top speed, Jolpica standard for race status/grid and most-wins. Manual `Wiring` DI, MVVM
init-less, sealed `Outcome<T>`/`SectionUiState<T>` with shared `OutcomeContent`
renderer (ADR 0002). Single `:app` module; KMP `:shared` extraction deferred.

**Multi-backstack (revision 2):** each tab has its own persistent
`NavBackStack` — switching tabs no longer destroys ViewModels.
`rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`
scope state per-entry. Exit-through-home: Homepage is the start route.

> See [lode-map.md](lode-map.md) for the full index.
