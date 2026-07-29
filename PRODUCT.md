# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The developer, building F1app for personal use: a casual F1 fan who came in via
Netflix's *Drive to Survive* and follows the season loosely — not a stats-first
or live-timing user. Primary jobs when opening the app: see when the next
session is so they don't miss it, and see results (race or standings) when
they did miss it.

## Product Purpose

A dark-first Android F1 companion that puts "when is the next session" and
"what happened" within one tap, anchored by a home-screen countdown widget
the official F1 app does not ship. Success = the user never misses a session
they intended to watch, and can always catch up on the ones they missed.

## Positioning

The home-screen Glance widget. The official F1 app and the major broadcast
apps have not shipped one. F1app's bet: a single, well-built glance surface
beats feature parity with the official app for a casual fan who only needs
"when" and "who won."

## Operating Context

Used on a phone (`minSdk 24`, `targetSdk 37`) plus the phone's home screen via
the Glance widget. Three bottom tabs: Homepage, Schedule, Leaderboard.
Race weekends (Fri–Sun, plus Saturday for sprint weekends)
are the peak usage window; off-season is dead air. The user checks the widget
for "is it time?" and opens the app for details only when needed.

## Capabilities and Constraints

Built: next-session countdown (Homepage §1); Glance countdown widget;
Schedule (Upcoming + Past with per-row podium retry); Round detail (upcoming +
past modes, 5-session weekend, Results push); Session results (Race / Quali /
Sprint / SQuali / FP); Homepage §2 season aggregates; combined favorites card
(Homepage §3); Circuit Detail (ticket 06); 2026+ Cloudinary driver headshots
and team/car imagery (ticket 08); deep link `f1app://round/{year}/{round}` from widget.

Queued for v1: favorites management folded into Homepage §3 (ticket 11,
ready). Leaderboard is built.

Confirmed technical constraints (not product): single `:app` module; manual
Wiring DI; MVVM init-less; sealed `Outcome<T>` → `SectionUiState<T>`;
f1api.dev primary, Jolpica for result companions and pit-stop duration, local
F1DB artwork; top speed is absent from v1;
`f1/` has zero `android.*` imports (future KMP `:shared` extraction stays a
move, not a rewrite).

Explicitly out of v1 scope (per `https://github.com/anpurnama11/my-f1-companion-app/issues/6`): Highlights
tab, Driver of the Day, Firebase-backed content, collaborator colors,
pit-wall status set, mclaren/nina/formula2 palettes.

## Brand Commitments

Package `com.anpurnama.f1_app`, version 1.0.0. Dark-only Material 3 theme —
no light scheme, no dynamic color, no `isSystemInDarkTheme` branch (see
`lode/design-system/theme.md`).

Locked palette: `F1Primary #ff3301`, `F1Secondary #125df0`,
`F1Tertiary #583ff2`, `FLError #fa1a24`, `Surface #0d0d0d`,
`SurfaceContainer #111111`, `OnSurface #ffffff`, `Outline #404040`.
Accent objects: `Circuits` (23 per-track colors, background accents only) and
`Tyres` (6 Pirelli compounds as text+background pairs). Shapes 2/8/14/16 dp;
pills use `CircleShape` directly. Spacing 4–32 dp in 8 rungs. Typography:
M3 defaults.

## Evidence on Hand

F1 data is fetched live from f1api.dev and jolpica. Circuit artwork is bundled
from a pinned F1DB revision; driver/team imagery loads from the pinned
formula1.com Cloudinary tree for 2026+ seasons. No synthetic
fixtures; no fabricated driver/team stats; no invented testimonials or
benchmarks. Design source for the theme: `~/Downloads/boxbox-club-DESIGN.md`
(lives outside the repo), transcribed into `lode/design-system/theme.md`.
Architecture reference: **PokeDV** (the developer's prior project). No user
data, no telemetry, no accounts.

## Product Principles

1. **Widget-first** — the home-screen Glance widget is the product. The app
   exists to back it. If something works in the app but not on the widget,
   the widget catches up, not the other way around.
2. **Never miss a session** — countdown + schedule is the primary entry
   point. A user must be able to tell when the next session starts in under
   3 seconds, from the home screen.
3. **Catch up fully, not partially** — past sessions and the Leaderboard
   show the same data live and after the fact, in the same place. No "live
   only" gating.
4. **Real data only, no engagement theater** — no streaks, badges, fake
   stats, or fabricated benchmarks. F1app's edge is one well-built thing, not
   feature parity with the official F1 app.
5. **Ship the queued tickets before adding new surface area** — v1 closes
   11. No new surfaces until that is done.

## Accessibility & Inclusion

No product-specific accessibility or localization requirement has been
established. Android platform defaults apply (TalkBack on `minSdk 24`, system
font scale, system locale). Future work that introduces a binding requirement
should record it here.
