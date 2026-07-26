---
id: 11
title: Countdown widget cache stores only raceStartMillis; pre-race window is a fixed 3d buffer
status: superseded by 0014
date: 2026-07-25
ticket: 07
---

## Context

The Countdown widget's worker gate decides whether to fetch on a 15-min tick. The
wayfinder 07 spec says the gate uses the cached race window `[FP1_start, race_start + 3h]`:
fetch every tick inside the window, fall through to a 60-min cache-age gate outside it.

The literal interpretation requires `FP1_start` in the cache. The full session schedule
would have to come from `GetSeasonUseCase` (the only endpoint that returns per-session
times for the full season), but that use case hits a larger endpoint and depends on
season data being already cached.

## Decision

Cache only `raceStartMillis` (and the 7 display fields + `lastSyncedMillis` for the gate).
Approximate the pre-race side of the window with a fixed **3-day** buffer: "in the race
window" = `[startMillis - 3d, startMillis + 3h)`.

This is slightly wider than the literal spec (FP1 lands Friday afternoon; the race is
Sunday afternoon, so the literal pre-race buffer is ~2 days). 3 days is a single
constant, cheap to document, and wide enough to cover the standard weekend. The cost of
being 1 day early is one extra 15-min fetch per day in the pre-race window — negligible
against the f1api.dev 10-min HttpCache.

The off-season case (`startMillis == 0L`) is unaffected by this change: the gate falls
through to the 60-min cache-age check, which fires once an hour and eventually picks up
the new season's first race when `/current/next` returns a real value again.

## Why

- The narrow cache keeps the worker's coupling to the data layer minimal. The widget
  only needs to know "when is the next race"; how the data layer learns that is the
  worker's problem, not the cache's.
- Adding `GetSeasonUseCase` to the worker would have widened the blast radius: season
  fetch failures, season cache invalidation, and a 24-race season payload on every
  widget tick. The 3-day buffer is the same behavior with none of that.
- The 3-day window also matches the v1 user expectation: a glanceable widget 3 days
  before lights out is "near the race"; anything further is "the future" and a single
  hourly fetch is enough.

## Considered options

- **Add `GetSeasonUseCase` to the worker; cache the full session schedule.** Honors
  the literal spec; widens the worker's data dependency. Rejected: the data isn't
  needed for any in-app screen, and the season endpoint is heavier.
- **Use only `now < startMillis + 3h` as "in the window".** Rejected: a week before
  the race would be "in the window" — we don't want to fetch every 15 min for an entire
  pre-season, the spec's point of the 60-min gate is to prevent that.
- **Pre-race buffer is "the race is this week" (7 days).** Rejected: 3 days matches the
  Friday-Sunday weekend shape; 7 days would over-fetch.

## Reference

- `lode/plans/f1app-build/tickets/07-countdown-widget.md` — the ticket
- `lode/wayfinder/f1app/tickets/07-countdown-widget-specifics.md` — the spec
- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownWorker.kt` — `shouldFetch` + `PRE_RACE_WINDOW_MS`
- `app/src/test/java/com/anpurnama/f1_app/widget/countdown/CountdownWorkerGateTest.kt` — pins the gate behavior
