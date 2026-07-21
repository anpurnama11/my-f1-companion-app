# 0003 — Derived homepage sections load immediately after `nextRace`

## Context

`HomepageViewModel` exposes independent `SectionUiState` atoms for favorites,
season, next race, drivers, constructors, top speed, weekend schedule, and
circuit image. The latter three are derived from the `nextRace` atom: they need
the race's year and country to call their respective use cases.

A review of finding #1 considered adding a reactive Flow observer on
`nextRace` so that weekend schedule / circuit image / top speed would reload
automatically whenever `nextRace` advanced to the next GP.

## Decision

Do **not** add a reactive observer. Instead, call a single
`loadRaceDerivedSections()` helper immediately after every `loadNextRace()`
call in `warmUp()` and `refresh()`.

## Why

`nextRace` is a private `MutableStateFlow` and `loadNextRace()` is its only
writer. Because every race change routes through the same call site that
already reloads the derived sections, an observer would be dead code. The
imperative approach is fewer lines, no mutex, no started flag, and no extra
imports.

If a future feature introduces another writer of `nextRace`, that is the
right time to introduce an observer.

## Consequences

- `warmUp()` and `refresh()` are the only two places that trigger derived
  section loads.
- Pull-to-refresh continues to re-fetch schedule/image/top speed even when
  the race ID has not changed, because `refresh()` calls
  `loadRaceDerivedSections(forceRefresh = true)`.
- Off-season (`nextRace` content is `null`) clears the derived atoms to
  `SectionUiState.Content(null)` instead of leaving stale data.
