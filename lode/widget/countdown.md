# Countdown widget

The home-screen countdown widget and its supporting worker/cache. Built with Jetpack Glance.

## CountdownWidget

`GlanceAppWidget` subclass. `provideGlance` reads `NextRaceCache` via `Wiring` and
renders `@Composable` content (compiles to `RemoteViews` under the hood).

**No live chronometer**: the displayed countdown is recomputed from
`NEXT_RACE_START_MILLIS` at each render.

States computed render-time from `now` vs the cached race window:
- countdown (`Nd Nh Nm`)
- "LIVE NOW" (circuit accent)
- "RACE COMPLETE" (transient)
- "Season over" (off-season, `START_MILLIS == 0L`)
- "No race data" (no cache + sync fail)
- stale cached value (cache set + sync fail, never blanks)

Visual: dark `Surface` body + full-bleed ~6dp `Circuits.forId(circuitId)` accent strip.
GP date/time shown device-local below the countdown.

Deep link: tapping fires `ACTION_VIEW` `PendingIntent` to `f1app://round/{year}/{round}`
via Glance `clickable(actionStartActivity(intent))` — suppressed in off-season / no-cache.

## CountdownWorker

Periodic `CoroutineWorker` (15-min WorkManager floor, `NETWORK_TYPE_CONNECTED`
constraint, exponential backoff). Polls `/current/next` via `GetNextRaceUseCase`.

**Adaptive cadence** (ticket 07): inside the cached race window
`[FP1_start, race_start + 3h]` it fetches every tick; outside, a gate in `doWork`
fetches only when cache age ≥ 60 min (effectively hourly between weekends).
One `PeriodicWorkRequest`, no second spec.

Calls `CountdownWidget().updateAll(context)` after a successful cache write.
Failure leaves the cached value.

## NextRaceCache

`DataStore<Preferences>` wrapper with typed keys:
- `NEXT_RACE_START_MILLIS: Long`
- `NEXT_RACE_NAME: String`
- `NEXT_RACE_CIRCUIT: String`
- `NEXT_RACE_ROUND: Int`
- `NEXT_RACE_SEASON: Int`
- full session schedule for "closest event" countdown

One atomic `edit` block — no serialized JSON blob. Written by `CountdownWorker`,
read by `CountdownWidget`, same instance via `Wiring`.

## Cross-references

- [../architecture/architecture.md](../architecture/architecture.md) — DI (`Wiring`), Ktor `HttpClient`.
- [../wayfinder/f1app/tickets/07-countdown-widget-specifics.md](../wayfinder/f1app/tickets/07-countdown-widget-specifics.md) — adaptive cadence, sizing, states.
- [../practices.md](../practices.md) — init-less ViewModel (same `onStart` pattern), test assertions.
