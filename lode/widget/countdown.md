# Countdown widget `[BUILT ticket 07]`

The home-screen countdown widget and its supporting worker/cache. Built with Jetpack Glance.

## CountdownWidget

`GlanceAppWidget` subclass in `app/.../widget/countdown/CountdownWidget.kt`. `provideGlance` reads
`NextRaceCache` via `Wiring` and renders `@Composable` content (compiles to `RemoteViews` under the hood).

**No live chronometer**: the displayed countdown is recomputed from
`NEXT_RACE_START_MILLIS` at each render.

States computed render-time by `reduceCountdownState(nowMillis, snapshot)` (pure fn in `CountdownState.kt`):

| State | Trigger | Display |
|---|---|---|
| `NoRaceData` | `snapshot == null` | "No race data — tap to retry" (tap enqueues `OneTimeWork`) |
| `SeasonOver` | `snapshot.startMillis == 0L` | "Season over" in `OnSurfaceVariant`, deep link suppressed |
| `Countdown` | `now < start` | `Nd Nh Nm` countdown + device-local GP date/time |
| `LiveNow` | `start <= now < start + 3h` | "LIVE NOW" in circuit accent + device-local GP date/time |
| `RaceComplete` | `now >= start + 3h` | "RACE COMPLETE" transient + device-local GP date/time |

**Visual** (dark `Surface` body + full-bleed ~6dp `Circuits.forId(circuitId)` accent strip):
- body `Surface` (#0d0d0d) with `Spacing.normal`-style 16dp padding
- 6dp accent strip at the top in the circuit's brand color (or neutral padding when no circuit)
- race name (bold) + circuit + country + state-specific label (countdown / LIVE NOW / RACE COMPLETE / off-season / no-data) + device-local GP date/time

**Deep link** (tap on the body): `Intent.ACTION_VIEW` to `f1app://round/{year}/{round}` via Glance
`clickable(actionStartActivity(intent))`. Args come from the cached snapshot. `MainActivity`
parses the URI (manifest `<intent-filter>` + `f1app` scheme + `round` host), pushes
`Route.RoundDetail` onto the Homepage backstack (`[Homepage, RoundDetail(y, r)]`). Suppressed
in off-season / no-cache (no valid round to open). `launchMode="singleTop"` ensures a
foreground widget tap reuses the existing activity and fires `onNewIntent`.

The "tap to retry" affordance in the no-cache state uses `actionRunCallback<RetryAction>()`,
which enqueues a one-shot `CountdownWorker` (no expedited; not quota-bound on a user-driven
retry).

**Smoke status (2026-07-25).** End-to-end on a Pixel_Tablet AVD
(Android 15) with auto-mobile: app installs; `F1App.onCreate` enqueues
one `PeriodicWorkRequest`; the first worker tick runs and writes the
DataStore cache (`files/datastore/next_race.preferences_pb`, 300 bytes,
all 8 typed keys populated with the real next race — Hungarian Grand
Prix 2026, matching what Homepage §1 renders). JobScheduler shows
exactly one active job for the worker, next-fire ≈ 13.7 min after
enqueue (15-min floor, consistent). Deep link `f1app://round/2026/7`
delivered via `adb am start`: foreground hit fired `onNewIntent`
(singleTop), parsed to `Route.RoundDetail(2026, 7)`, pushed onto the
Homepage backstack; RoundScreen rendered "Round 7" with its Round
header and a loading ProgressBar; back returned to the Homepage.

**Not smoke-verified on device:** the actual launcher widget render
(no widget on the home screen) and the `RetryAction` trampoline path
(no way to tap a non-existent widget). Both rely on Glance 1.1.1
standard plumbing (`GlanceAppWidgetReceiver` + the auto-merged
`ActionTrampolineActivity` + `actionRunCallback<RetryAction>()` +
`WorkManager.enqueueOneTime`). The trampolines and
`ActionCallbackBroadcastReceiver` are present in the release APK
manifest. Acceptable v1 risk; revisit if a real launcher surfaces a
failure.

**Sizing** (per `res/xml/countdown_widget_info.xml`):
`minWidth 115dp`, `minHeight 256dp`, `maxResizeWidth 130dp`, `maxResizeHeight 624dp`,
`minResizeWidth 56dp`, `minResizeHeight 120dp`, `resizeMode horizontal|vertical`,
`updatePeriodMillis 0` (worker `updateAll` is the re-render driver), `widgetCategory home_screen`,
`initialLayout @layout/countdown_widget_initial` (trivial dark placeholder; replaced by
`provideGlance` on first bind). `previewLayout` omitted — the launcher's widget picker
falls back to a generated preview.

## CountdownWorker

Periodic `CoroutineWorker` in `app/.../widget/countdown/CountdownWorker.kt` (15-min
WorkManager floor, `NETWORK_TYPE_CONNECTED` constraint, exponential backoff). Polls
`GetNextRaceUseCase(forceRefresh = true)`. Enqueued by `F1App.onCreate()` via
`CountdownWorker.enqueuePeriodic(context)` using `ExistingPeriodicWorkPolicy.UPDATE` so a
re-launch with a tuned schedule takes effect without first canceling.

**Adaptive gate** ([`shouldFetch`][1] — pure fn, unit-tested in `CountdownWorkerGateTest`):

- `snapshot == null` → fetch (first cold launch).
- `now - lastSyncedMillis >= 60min` → fetch (cache stale).
- `startMillis > 0L && nowMillis in [startMillis - 3d, startMillis + 3h)` → fetch (in race window).
- Otherwise → skip; the next 15-min periodic tick decides again.

**V1 simplification** (tickets 07 spec / wayfinder 07 lock). The literal spec window is
`[FP1_start, race_start + 3h]`. The widget cache only stores `raceStartMillis` to keep the
worker narrow and the cache small, so the v1 approximation uses a fixed 3-day pre-race
buffer in place of `FP1_start`: 3 days before through 3 hours after the race. Slightly
wider than the literal spec by ~1 day; cheaper than caching the full session schedule.
Documented compromise; revisit if pre-FP1 freshness becomes a real user complaint.

**Fetch failure policy.** Per data-layer invariant: the cache is **never cleared** on a
network failure. The next successful tick writes the new data; a failed tick returns
`Result.success()` (the 15-min periodic tick is the retry path — double-scheduling via
`Result.retry()` would be redundant and could stall the next periodic run).

**On success.** Writes the new snapshot, then calls `CountdownWidget().updateAll(context)`
to repaint every active instance.

**One-time retry.** `CountdownWorker.enqueueOneTime(context)` enqueues a one-shot work
with `ExistingWorkPolicy.REPLACE` for the "tap to retry" affordance. The widget's
`RetryAction` calls this from its `ActionCallback.onAction`.

## NextRaceCache

`DataStore<Preferences>` cache in `app/.../widget/countdown/data/NextRaceCache.kt` with
typed keys:

- `next_race_start_millis: Long` (0L sentinel = off-season)
- `next_race_name: String`
- `next_race_circuit: String`
- `next_race_circuit_country: String` (empty when absent)
- `next_race_circuit_id: String` (for the accent strip)
- `next_race_round: Int`
- `next_race_season: Int`
- `next_race_last_synced_millis: Long` (worker's adaptive-gate input)

One atomic `edit` per write. Same `Wiring` instance held by the application, the worker,
and the Glance widget — one DataStore, one source of truth, file under
`filesDir/datastore/next_race.preferences_pb`.

[1]: ../../app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownWorker.kt

## Cross-references

- [../architecture/architecture.md](../architecture/architecture.md) — DI (`Wiring`), Ktor `HttpClient`.
- [../wayfinder/f1app/tickets/07-countdown-widget-specifics.md](../wayfinder/f1app/tickets/07-countdown-widget-specifics.md) — adaptive cadence, sizing, states.
- [../practices.md](../practices.md) — init-less ViewModel (same `onStart` pattern), test assertions.

## Files

- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownWidget.kt`
- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownWidgetReceiver.kt`
- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownWorker.kt`
- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownState.kt`
- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/NextRaceSnapshot.kt`
- `app/src/main/java/com/anpurnama/f1_app/widget/countdown/data/NextRaceCache.kt`
- `app/src/main/res/xml/countdown_widget_info.xml`
- `app/src/main/res/layout/countdown_widget_initial.xml`
- `app/src/test/java/com/anpurnama/f1_app/widget/countdown/CountdownStateTest.kt`
- `app/src/test/java/com/anpurnama/f1_app/widget/countdown/CountdownWorkerGateTest.kt`
