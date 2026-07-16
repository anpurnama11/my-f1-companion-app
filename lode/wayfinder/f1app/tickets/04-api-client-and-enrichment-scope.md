---
id: 04
title: API client & enrichment scope
type: grilling
status: open
blocked_by: []
owner: ""
---

## Question

Which free F1 APIs does this app actually wire in, and what does each in-scope
screen/widget draw from?

The api-mapping recommends f1api.dev as primary (schedule, standings, race results,
circuit metadata, pre-joined driver+team, zero auth, generous rate limit).
**OpenF1** is the only free source for: driver headshots, per-session weather, race
control (flags/incidents), telemetry. **jolpica** overlaps f1api.dev on most things and
adds pit stops.

The decision:

- **Minimal (f1api.dev only):** Dashboard, Driver details (minus headshot), Team details,
  Round details (no weather/flags), Countdown. Smallest surface, one HTTP client config,
  zero auth concerns. Headshots and weather simply absent.
- **f1api.dev + OpenF1 enrichments:** adds driver headshots to Driver details, weather +
  race-control flags to Round details. More complete reference parity, second API to
  configure (free tier, lower rate limit, nuance around `session_key` joins).

The user's rule — "if not covered by API, no need to create" — was about *features*, not
enrichments. So this is a genuine call: headshots and weather are covered (by OpenF1), but
only there. Resolving this unblocks ticket 03 (data layer) and clears the headshots/weather
fog on the map.
