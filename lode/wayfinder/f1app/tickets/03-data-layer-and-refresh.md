---
id: 03
title: Data layer & widget refresh strategy
type: grilling
status: open
blocked_by: [01, 04]
owner: ""
---

## Question

How does the app fetch, cache, and refresh F1 data — and critically, how does the
Countdown **widget** stay fresh?

The reference (api-mapping) recommends f1api.dev as the source for schedule + standings +
race results. Open questions:

- **Offline-first with Room + WorkManager** (see the `android-offline-first` skill: Room
  as single source of truth, WorkManager sync, NetworkMonitor) vs **lightweight** remote
  client (Retrofit/Ktor) + in-memory/most-recent cache. The widget needs periodic
  background refresh either way.
- **Widget refresh mechanism:** `WorkManager` periodic (min 15 min) polling
  `/current/next`, updating the widget from a cached next-race row, vs
  `AppWidgetManager` update broadcast. The countdown tick (1s) is a *client-side* UI
  concern — compute it from the cached timestamp, don't re-fetch per tick.
- **Network client choice:** Retrofit + OkHttp + kotlinx.serialization is the Android
  default; Ktor-client is the alternpasive. Pick one.

Blocked on 01 (where does the data module live) and 04 (which APIs are wired — f1api.dev
only, or f1api.dev + OpenF1 for headshots/weather).
