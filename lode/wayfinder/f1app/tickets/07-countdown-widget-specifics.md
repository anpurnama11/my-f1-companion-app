---
id: 07
title: Countdown widget specifics
type: task
status: open
blocked_by: [02, 03, 06]
owner: ""
---

## Question

Pin the Countdown widget's concrete behavior, sizing, and refresh contract. From
boxbox-club-DESIGN.md it's 115×256dp min, 130×624dp max resize, 56×120dp min-resize,
standard horizontal|vertical resize mode, no config activity. What this ticket
*decides*:

- **Refresh cadence:** WorkManager periodic (every 15 min, the floor) re-fetching
  `/current/next` and updating the cached next-race row; the visible countdown ticking
  client-side from the cached timestamp. Is 15 min enough, or do we need a tighter tick
  near race start (e.g. switch to a faster alarm/Handler window near the green flag)?
- **Live-race handling:** what does the widget show once the race window opens —
  "Live now", switch to Round-result mode, or just hold the countdown at zero?
- **Empty/error states:** no upcoming race in the season (off-season), network failure on
  last sync — what's shown?
- **Deep-link target:** (rides on ticket 05) — Dashboard vs Round details.
- **Visual:** dark surface + circuit accent color for the upcoming track (from the
  circuit-color palette), driver-of-day-red not used here. This is where ticket 02's
  tokens get applied.

Blocked on 02 (theme/tokens), 03 (refresh mechanism), 06 (Glance vs RemoteViews).
Resolving this ticket likely also clears the "Countdown widget refresh cadence & accuracy"
fog patch on the map.
