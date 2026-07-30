# F1app summary

`com.anpurnama.f1_app` — dark-first Jetpack Compose F1 app. Greenfield scaffold
(`F1appTheme`, `Circuits` palette, Navigation 3 multi-backstack `NavShell`)
plus Homepage foundation with three sections (§1 upcoming-session countdown,
§2 season aggregates, §3 combined favorites + nearest-GP circuit cards) and
the Schedule tab (Material 3 `SecondaryTabRow` plus
`HorizontalPager` switching between **Upcoming** and **Past**; Upcoming rows
mirror the §3 card shape — round/GP name/date/city/circuit image; Past rows add
a P1/P2/P3 podium cell with per-row retry).
Round detail page (`Route.RoundDetail`) switches between upcoming mode
(race-weekend schedule + circuit stats) and past mode (per-session result rows);
tapping **Results** pushes `Route.SessionResult` for a full session result list.
The circuit block links to `CircuitDetail` (shipped in ticket 06 as
two independently-failing sections: f1api.dev metadata + jolpica
most-wins aggregation). Four bottom tabs
(Homepage, Schedule, Leaderboard, My Team); Leaderboard has Drivers and
Constructors sub-tabs and drills into Driver and Constructor detail pages, while My Team manages two favorite drivers and one favorite constructor through a standings-backed bottom-sheet picker. **v1 destination is 3 tabs** — My Team is being folded into Homepage §3 per GitHub issues #54 and #18, and the news tab takes the freed slot when RSS news un-parks (GitHub issue #55).
Driver IDs are unique across the two
slots, enforced atomically in `FavoritesCache`; the shared cache updates
Homepage §3 reactively.

The **Countdown widget** (shipped in ticket 07) is a Jetpack Glance
`GlanceAppWidget` reading `NextRaceCache` and rendering a dark-only Surface
with a circuit-accent strip + countdown / LIVE NOW / RACE COMPLETE / Season
over / No race data states. A periodic `CountdownWorker` (15-min floor,
network-constrained, exponential backoff) refreshes the cache; the worker
uses an adaptive gate (every tick inside a 3d-pre-race / 3h-post-race window,
60-min cache-age gate otherwise). Tapping the widget fires a
`f1app://round/{year}/{round}` deep link; `MainActivity` parses it and
pushes `Route.RoundDetail` onto the Homepage backstack.

Data: f1api.dev for schedule + catalogs (season, next race, standings,
driver/team detail joins, circuit metadata, the season driver catalog that
bridges the alpha id namespace); Jolpica standard for Race and Qualifying
results and pit-stop enrichment; Jolpica alpha for Sprint, Sprint Qualifying,
and Free Practice results, translated to Ergast canonical ids at the data
seam via the car-number bridge (ADR 0005 / architecture/id-namespaces.md).
Circuit artwork
is bundled from a pinned F1DB revision; 2026+ driver headshots and team/car
renders load from formula1.com Cloudinary paths derived locally from f1api.dev
name/team fields.
Manual `Wiring` DI, MVVM
init-less, sealed `Outcome<T>`/`SectionUiState<T>` with shared `OutcomeContent`
renderer (ADR 0002). Single `:app` module; KMP `:shared` extraction deferred.
Session start labels are converted from API UTC to the device’s local timezone;
elapsed lap and race result times remain unconverted durations.

Current-season structured cache is partially shipped: schedule, Homepage next
race/session, driver/constructor standings, and current driver/team catalogs are
durable DataStore snapshots. Homepage, Leaderboard, and My Team render cached
content through `SectionUiState.Content(data, sync)` so stale or failed refreshes
do not blank the last good payload. Those current-season refreshes distinguish
persisted writes, fresh skips, deferred work, retryable failures, and permanent
failures; session and non-season resources retain temporary legacy outcomes for
issues #68/#69. The Countdown widget cache remains separate.

**Multi-backstack (revision 2):** each tab has its own persistent
`NavBackStack` — switching tabs no longer destroys ViewModels.
`rememberSaveableStateHolderNavEntryDecorator` + `rememberViewModelStoreNavEntryDecorator`
scope state per-entry. Exit-through-home: Homepage is the start route.

GitHub Issues is the canonical work tracker. The Lode contains current durable
knowledge only; local implementation-ticket and Wayfinder tracker trees are not
used.

> See [lode-map.md](lode-map.md) for the full index.
