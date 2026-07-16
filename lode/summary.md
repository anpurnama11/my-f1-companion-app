# F1app summary

A dark-first Jetpack Compose Android app for F1 data — 4 in-app screens (Dashboard,
Driver details, Team details, Round details) plus 1 home-screen widget (Countdown to
the next race). Built on the existing `com.anpurnama.f1_app` single-module scaffold.
Data from free F1 APIs (f1api.dev primary; OpenF1 only where a feature needs headshots
or weather — scope TBD by ticket 04).

Architecture mirrors the developer's prior `PokemonDataViewer` project: single `:app`
module, manual `Wiring` service locator on a custom `Application`, sealed `Outcome<T>`
result type, MVVM with init-less `onStart` loading + `combine` + `WhileSubscribed(5_000)`,
UseCase seam between ViewModels and data. Network layer uses **Ktor Client (CIO
engine)** rather than Retrofit — the one deliberate divergence from PokeDV, chosen so
the `f1/` domain package ports to a future Kotlin Multiplatform `:shared` module without
rewrites. Navigation 3 for the Android UI layer. Room is *not* an architectural tenet;
storage choice (Room vs HTTP cache) is deferred to ticket 03.

UI layer runs on a **dark-only Compose Material3 theme** transcribed from the
boxbox-club design (F1-specific `Circuits` / `Tyres` palettes in `ui/theme/Color.kt`,
M3 default typography, custom `F1Shapes` + `Spacing`).

See [architecture/architecture.md](architecture/architecture.md) for the full module,
DI, layering, and tech decisions, and [design-system/theme.md](design-system/theme.md)
for the theme contract.
