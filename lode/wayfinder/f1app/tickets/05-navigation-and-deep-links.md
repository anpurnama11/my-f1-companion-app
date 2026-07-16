---
id: 05
title: Navigation & deep links
type: grilling
status: open
blocked_by: [01]
owner: ""
---

## Question

How do the 4 screens wire together, and does the Countdown widget deep-link into the app?

- **Navigation lib:** Jetpack Navigation-Compose (stable, `androidx.navigation:navigation-compose`)
  vs Navigation 3 (newer, the `navigation-3` skill covers it, scenes + multi-backstack).
  For 4 screens with no nested graphs, Navigation-Compose is the boring correct choice;
  Navigation 3 only earns its keep if the user wants its scene/dialog pattern.
- **Routes:** Dashboard (start) → Driver details (`driver/{id}`) → Team details
  (`team/{id}`) → Round details (`round/{year}/{round}`). Dashboard surfaces entry points
  (next-race card, top-3 standings, etc.).
- **Widget deep link:** tapping the Countdown widget — opens the app to Dashboard, or to a
  Round-details screen for the upcoming race? The reference's Favourite Driver/Team widgets
  used config activities; Countdown has no config activity in the reference, but deep-link
  behavior from it isn't specified there either.

Blocked on 01 (module structure decides whether nav lives in `:app` or a `:feature`
module).
