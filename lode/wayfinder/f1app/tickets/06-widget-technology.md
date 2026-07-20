---
id: 06
title: Widget technology — Glance vs RemoteViews
type: grilling
status: closed
closed_by: Jetpack Glance — Compose-native widget DSL, single widget only; RemoteViews interop reserved as escape hatch
blocked_by: [01]
owner: ""
---

## Decision

Countdown widget uses **Jetpack Glance** (`androidx.glance:appwidget`).
Compose-native authoring matches the app's single mental model — the widget is
authored as `@Composable` content in `provideGlance`, the same idiom as every
screen. Glance compiles to `RemoteViews` under the hood, so it is bound by the
same RemoteViews primitive set, but that set is enough for this widget.

RemoteViews is **not** the choice, but remains the escape hatch via
`AndroidRemoteViews` interop if a feature the design needs is missing from the
Glance API surface at implementation time.

### Tech surface

- `GlanceAppWidget` subclass in `widget/countdown/CountdownWidget.kt`; override
  `provideGlance` → `provideContent { ... }`.
- `GlanceAppWidgetReceiver` registered in `AndroidManifest.xml` as the
  `<receiver>` with the widget `<meta-data>` pointing at
  `CountdownWidgetReceiver`.
- `AppWidgetProviderInfo` (XML in `res/xml/`) holds the sizing from ticket 07
  (min 115×256dp, max 130×624dp, min-resize 56×120dp, no config activity).
- `updateAll` is driven by the `CountdownWorker` after a successful cache write
  (ticket 03) so the widget reflects the freshest `NextRaceCache` without a
  second fetch.

### Data binding

`provideGlance` reads `NextRaceCache` (DataStore<Preferences>) via `Wiring`:

```kotlin
class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cache = (context.applicationContext as F1App).wiring.nextRaceCache
        val state = cache.snapshot().first()   // typed keys → CountdownWidgetUiState
        provideContent { CountdownContent(state) }
    }
}
```

- One cold read of the cache per `provideGlance` run; no network.
- `CountdownWorker` calls `CountdownWidget().updateAll(context)` at the end of
  a successful `doWork()` so the host re-renders from the updated cache.
- The 1s tick is client-side (ticket 07) — outside Glance's update model; the
  visible countdown uses a `Text` recomputed from the cached start millis when
  the widget re-renders, not a live chronometer that Glance would have to drive.

### Deep-link PendingIntent (ticket 05 contract)

```kotlin
class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wiring = (context.applicationContext as F1App).wiring
        val s = wiring.nextRaceCache.snapshot().first()
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(Spacing.normal)
                    .clickable(actionStartActivity(
                        Intent(Intent.ACTION_VIEW,
                            "f1app://round/${s.season}/${s.round}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ))
            ) {
                Text(s.raceName, style = TextStyle(color = OnSurface,
                    fontWeight = FontWeight.Bold))
                Text(formatRemaining(s.startMillis), style = TextStyle(
                    color = Circuits.forId(s.circuitId)))
            }
        }
    }
}
```

Glance's `clickable(actionStartActivity(intent))` is the Compose-native way to
attach the `PendingIntent` over the custom-scheme `ACTION_VIEW` that ticket 05
specified. `MainActivity` parses the URI → `RoundDetail` nav key, Homepage is
backstack root. No config activity, matches the reference.

### Theme binding

- Glance colors are `androidx.glance.ColorProvider` / `ColorFilter`; for a
  dark-only widget the simplest path is `ColorProvider(Color.fromRGBO(...))`
  built from the same hex vals in `ui/theme/Color.kt` (`Surface`, `OnSurface`,
  `OnSurfaceVariant`).
- Circuit accent (`Circuits.forId(id)`) is read by the widget code at render
  time; Glance does not consume Compose `MaterialTheme`, so the values are
  imported directly from `Color.kt`, not themed through `F1appTheme`.
- `Spacing` values (4–32dp) are plain dp and reuse directly in Glance.
- No light variant — the widget is dark-first, matching the app invariant.

```mermaid
flowchart LR
    Worker["CountdownWorker\n(15-min periodic)"] -->|write typed keys| Cache["NextRaceCache\n(DataStore<Preferences>)"]
    Worker -->|updateAll| Widget
    Widget["CountdownWidget\n(GlanceAppWidget)"] -->|snapshot().first()| Cache
    Widget -->|clickable actionStartActivity| Intent["Intent.ACTION_VIEW\nf1app://round/{y}/{r}"]
    Intent --> MainActivity["MainActivity parses URI\n→ RoundDetail nav key"]
    MainActivity --> Nav3["Navigation 3 backstack\n[Homepage, RoundDetail]"]
```

## Rationale

- Compose-native authoring is the project's single mental model; Glance keeps
  the widget in that model instead of introducing XML RemoteViews as a second
  UI paradigm. The whole app is Compose; the widget ships in the same DSL.
- Glance is actively pushed by Google and compiles to RemoteViews, so it does
  not escape the RemoteViews primitive ceiling — but that ceiling (TextView,
  ImageView, Column/Box/Row analogues) is sufficient for a glanceable
  number-on-dark-surface widget. No RemoteViews-only feature is needed here.
- The "Glance is still maturing" concern is absorbed by the
  `AndroidRemoteViews` interop escape hatch: if a feature the design needs is
  missing from the Glance API at build time, a single `AndroidRemoteViews`
  composable bridges to classic RemoteViews without rewriting the widget.
- The deep-link `PendingIntent` contract from ticket 05 maps cleanly to Glance's
  `clickable(actionStartActivity(intent))`; no special handling.
- Single widget in scope (the other 7 are out, not deferred). Glance's
  composability payoff would only materialize if those widgets returned —
  the choice stands on authoring-model fit and API stability, not on speculative
  reuse.

## Out of scope

- **The 1s countdown tick + live/finished handling + sizing specifics** —
  ticket 07. 06 ends at "Glance is the tech; the widget reads NextRaceCache,
  renders dark, taps to RoundDetail."
- **The 7 secondary widget types** — ruled out at the map level. If they
  return, Glance is already the tech; they'd be new `GlanceAppWidget` subclasses.
- **Glance tile/wear support** — no Wear target; Glance appwidgets only.

## Reference

- [../../architecture.md](../../../architecture/architecture.md) — widget
  tech decision + package layout for `widget/countdown/`.
- [../map.md](../map.md) — Countdown-only widget scope; other 7 out.
- [03-data-layer-and-refresh.md](03-data-layer-and-refresh.md) —
  `NextRaceCache` typed keys, `CountdownWorker` 15-min cadence.
- [05-navigation-and-deep-links.md](05-navigation-and-deep-links.md) —
  `f1app://round/{year}/{round}` deep-link contract the widget builds.
- [07-countdown-widget-specifics.md](07-countdown-widget-specifics.md) —
  blocked-by 06; now unblocked on the widget-tech axis.
