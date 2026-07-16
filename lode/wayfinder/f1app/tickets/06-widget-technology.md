---
id: 06
title: Widget technology — Glance vs RemoteViews
type: grilling
status: open
blocked_by: [01]
owner: ""
---

## Question

Jetpack **Glance** (Compose-like DSL for app widgets, `androidx.glance`) or classic
**RemoteViews** XML for the Countdown widget?

- **Glance:** Compose-native authoring, shared `@Composable` mental model with the app,
  actively pushed by Google. Still maturing; some visual edge cases. `minSdk 24` is fine
  (Glance needs 23+).
- **RemoteViews:** the stable, boring choice. For a single Countdown widget (timestamp +
  circuit name + maybe a small accent), XML RemoteViews is genuinely simple and has zero
  alpha-tooling risk.

Ponytail lens: Countdown is a glanceable number-on-dark-surface widget. RemoteViews may be
the *shorter* path. But if the user plans to add the other 7 widget types later (out of
scope now, but a known future effort), Glance's composability pays off.

Blocked on 01 (a `:widget` module, if chosen, changes the wiring; if everything's in
`:app`, either tech plugs in directly).
