---
id: 07
title: Countdown widget (Glance + worker + deep link)
type: task
status: ready-for-agent
blocked_by: [02, 03]
owner: ""
---

# 07 — Countdown widget (Glance + worker + deep link)

**What to build:** the home-screen Countdown widget — a Jetpack Glance `GlanceAppWidget` whose `provideGlance` reads `NextRaceCache` and renders `@Composable` content (compiles to `RemoteViews`; interop is escape hatch only). A `CountdownWorker` periodic `CoroutineWorker` (15-min WorkManager floor, `NETWORK_TYPE_CONNECTED` constraint, exponential backoff) polls `GetNextRaceUseCase` and writes `NextRaceCache` (DataStore typed keys: `NEXT_RACE_START_MILLIS`/`NAME`/`CIRCUIT`/`ROUND`/`SEASON` + full session schedule for the race window), with an adaptive gate in `doWork` — inside the cached race window `[FP1_start, race_start + 3h]` fetch every tick, outside fetch only when cache age ≥ 60 min. On fetch failure leave the cached value (never clear), then call `CountdownWidget().updateAll(context)` after a successful write. The render-time state is a pure reducer computed from `now` vs cached race window (no live chronometer): countdown (`Nd Nh Nm`) / "LIVE NOW" (circuit-accent colour) / "RACE COMPLETE" transient / "Season over" (off-season, `START_MILLIS == 0L`) / "No race data — tap to retry" (no cache + sync fail, taps enqueue one-shot expedited `OneTimeWork`) / stale cached value (cache set + sync fail, never blanks). Visual contract: dark-only `Surface` body (#0d0d0d, `Spacing.normal` padding) + full-bleed ~6dp `Circuits.forId(circuitId)` accent strip; race name (bold), circuit + country, large countdown, GP date/time device-local (`Sun 23 Mar · 15:00`). Tapping fires `Intent.ACTION_VIEW` `PendingIntent` to `f1app://round/{year}/{round}` via Glance `clickable(actionStartActivity(intent))`, args from `NextRaceCache`; `MainActivity` parses the URI, pushes `RoundDetail` onto Homepage as backstack root (`[Homepage, RoundDetail]`; back lands on Homepage). Deep link suppressed in off-season / no-cache. Sizing per the spec's AppWidgetProviderInfo; Glance preview composables for `previewLayout`.

**Blocked by:** 02 — `GetNextRaceUseCase` + `NextRace` model already exist; 03 — `RoundDetail` route already exists (deep-link target).

**Status:** ready-for-agent

## Done when

- [ ] `NextRaceCache` DataStore (typed keys incl. full session schedule for the race window); worker writes, widget reads, same `Wiring` instance
- [ ] `CountdownWorker`: `PeriodicWorkRequest`, 15-min floor, `NETWORK_TYPE_CONNECTED`, exponential backoff; adaptive `FP1_start → race_start+3h` fetch-every-tick vs ≥60-min cache-age gate
- [ ] Fetch failure leaves cached value; success → `CountdownWidget().updateAll(context)`
- [ ] `CountdownWidget` GlanceAppWidget: `provideGlance` reads cache; render-time state reducer (pure fn) → countdown / LIVE NOW / RACE COMPLETE / Season over / No race data / stale
- [ ] Dark `Surface` + full-bleed `Circuits.forId(circuitId)` accent strip; race name / circuit+country / large countdown / device-local GP date-time; no live chronometer
- [ ] Deep link `f1app://round/{year}/{round}` `PendingIntent` via `clickable(actionStartActivity(intent))`; `MainActivity` parses URI, pushes `RoundDetail` on Homepage backstack root; suppressed in off-season / no-cache
- [ ] AppWidgetProviderInfo sizing (minWidth 115dp, minHeight 256dp, maxResize 130×624, minResize 56×120, resizeMode h|v, no configure, `updatePeriodMillis 0`); Glance preview composables
- [ ] One runnable check on the state reducer (countdown/LIVE/COMPLETE/season-over/no-cache/stale transitions) — pure fn, no Glance harness

Spec cross-ref: `lode/specs/f1app.md` (Countdown widget, Deep link, Glance), `lode/wayfinder/f1app/tickets/07-countdown-widget-specifics.md`.