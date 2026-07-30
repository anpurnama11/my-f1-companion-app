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

The cached `startMillis` is the selected widget session start, not always the
race start. The worker prefers the current/next session from the round schedule
(`Free Practice 1/2/3`, `Sprint Qualifying`, `Sprint`, `Qualifying`, `Race`),
falling back to the race session when the full season schedule is unavailable.

**Visual** (dark `Surface` body + full-bleed ~6dp `Circuits.forId(circuitId)` accent strip):
- body `Surface` (#0d0d0d) with `Spacing.normal`-style 16dp padding
- 6dp accent strip at the top in the circuit's brand color (or neutral padding when no circuit)
- race name (bold) + circuit + country + state-specific label (countdown / LIVE NOW / RACE COMPLETE / off-season / no-data) + session name plus device-local start date/time

```mermaid
flowchart TB
    cache[NextRaceCache snapshot] --> reducer[reduceCountdownState]
    reducer --> glance[CountdownWidget Glance content]
    glance --> launcher[Launcher RemoteViews widget]
    launcher --> deeplink[f1app://round/{year}/{round}]
```

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
typed keys populated with the real next race — Hungarian Grand
Prix 2026, matching what Homepage §1 renders). JobScheduler shows
exactly one active job for the worker, next-fire ≈ 13.7 min after
enqueue (15-min floor, consistent). Deep link `f1app://round/2026/7`
delivered via `adb am start`: foreground hit fired `onNewIntent`
(singleTop), parsed to `Route.RoundDetail(2026, 7)`, pushed onto the
Homepage backstack; RoundScreen rendered "Round 7" with its Round
header and a loading ProgressBar; back returned to the Homepage.

**Launcher render:** the widget renders on Pixel Launcher with content description
`F1 Countdown`, a dark rounded card, circuit accent strip, race/circuit copy,
countdown, and session + local start label. Existing placed widgets keep their current
launcher cell span until the user resizes or re-adds them; the provider info
controls the default and allowed bounds for new placements/resizes.

**Not smoke-verified on device:** the `RetryAction` trampoline path. It relies on
Glance 1.1.1 standard plumbing (`GlanceAppWidgetReceiver` + the auto-merged
`ActionTrampolineActivity` + `actionRunCallback<RetryAction>()` +
`WorkManager.enqueueOneTime`). The trampolines and `ActionCallbackBroadcastReceiver`
are present in the release APK manifest. Acceptable v1 risk; revisit if a real
launcher surfaces a failure.

**Sizing** (per `res/xml/countdown_widget_info.xml`):
`minWidth 180dp`, `minHeight 80dp`, `minResizeWidth 56dp`, `minResizeHeight 56dp`,
`resizeMode horizontal|vertical`, no max resize width/height. The default placement
targets a wider, short card; omitting max bounds lets Pixel Launcher accept wider
resize drags instead of clamping at the previous 130dp maximum width. The lower height
bound lets already-placed widgets shrink down after resizing. `updatePeriodMillis 0`
(worker `updateAll` is the re-render driver), `widgetCategory home_screen`,
`initialLayout @layout/countdown_widget_initial` (trivial dark placeholder; replaced by
`provideGlance` on first bind).

**Widget picker preview:** provider info supplies both `previewImage` and
`previewLayout` so launchers do not fall back to the app icon. `previewImage` is a
static rounded dark card drawable with the green accent strip for broad launcher
compatibility. `previewLayout` is a small RemoteViews-compatible XML mock with sample
copy (`Bahrain Grand Prix`, `Qualifying`, `Sat 7 Mar · 16:00`) for Android 12+
launchers that inflate layout previews. The accent strip is a zero-text `TextView`, not
a plain `View`, because Pixel Launcher failed to load the preview when the layout
contained an unsupported plain `View`. These previews are picker-only; runtime rendering
remains the Glance composition.

## CountdownWorker

Periodic `CoroutineWorker` in `app/.../widget/countdown/CountdownWorker.kt` (15-min
WorkManager floor, `NETWORK_TYPE_CONNECTED` constraint, exponential backoff). Polls
`GetNextRaceUseCase(forceRefresh = true)` for the canonical round, then `GetSeasonUseCase`
for the current/next session label and start. Enqueued by `F1App.onCreate()` via
`CountdownWorker.enqueuePeriodic(context)` using `ExistingPeriodicWorkPolicy.UPDATE` so a
re-launch with a tuned schedule takes effect without first canceling.

**Adaptive gate** ([`shouldFetch`][1] — pure fn, unit-tested in `CountdownWorkerGateTest`):

- `snapshot == null` → fetch (first cold launch).
- `now - lastSyncedMillis >= 60min` → fetch (cache stale).
- `now >= startMillis + 3h` → fetch (the selected session expired; advance promptly).
- `startMillis > 0L && nowMillis in [startMillis - 3d, startMillis + 3h)` → fetch (in race window).
- Otherwise → skip; the next 15-min periodic tick decides again.

**Session selection.** The worker selects the first scheduled session whose start is not
more than the 3-hour live window in the past. During FP1 it still shows Free Practice 1;
after that window it advances to the next session. If the season schedule fails or lacks
the round, the worker writes the race session label/start as a safe fallback.

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
- `next_race_session_name: String` (display label, defaults to `Race` for old caches)
- `next_race_round: Int`
- `next_race_season: Int`
- `next_race_last_synced_millis: Long` (worker's adaptive-gate input)

One atomic `edit` per write. Same `Wiring` instance held by the application, the worker,
and the Glance widget — one DataStore, one source of truth, file under
`filesDir/datastore/next_race.preferences_pb`.

**Corruption recovery.** The DataStore is constructed by
`createPreferencesDataStore(file)` in `core/cache/PreferencesCacheFactory.kt` —
the same internal helper `Wiring` uses, with a
`ReplaceFileCorruptionHandler { emptyPreferences() }`. A parser-detected
corruption read returns a `null` snapshot, which the widget reducer maps to
`CountdownState.NoRaceData`; the widget remains placeable and the worker's next
successful `write` / `writeOffSeason` repopulates it. Ordinary `IOException`s
(permission denied, full disk, etc.) are not `CorruptionException`s and
propagate — the corruption policy does not erase arbitrary I/O failures. Same
contract as `FavoritesCache` (see [../my-team/summary.md](../my-team/summary.md));
the spec is [../specs/cache-correctness-hardening.md](../specs/cache-correctness-hardening.md).

[1]: ../../app/src/main/java/com/anpurnama/f1_app/widget/countdown/CountdownWorker.kt

## Cross-references

- [../architecture/architecture.md](../architecture/architecture.md) — DI (`Wiring`), Ktor `HttpClient`.
- [https://github.com/anpurnama11/my-f1-companion-app/issues/37](https://github.com/anpurnama11/my-f1-companion-app/issues/37) — adaptive cadence, sizing, states.
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
- `app/src/main/res/layout/countdown_widget_preview.xml`
- `app/src/main/res/drawable/countdown_widget_preview.xml`
- `app/src/test/java/com/anpurnama/f1_app/widget/countdown/CountdownStateTest.kt`
- `app/src/test/java/com/anpurnama/f1_app/widget/countdown/CountdownWorkerGateTest.kt`
