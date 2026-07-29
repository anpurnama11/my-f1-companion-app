# F1app spec

Problem, solution, and user stories.

---
id: f1app
topic: F1app — dark-first Jetpack Compose F1 data app
status: design-locked / ticket 03 shipped; later slices remain
lode-cross-refs:
  - ../summary.md
  - ../terminology.md
  - ../practices.md
  - ../architecture/architecture.md
  - ../design-system/theme.md
  - ../release/build-and-signing.md
  - ../testing/scope.md
  - https://github.com/anpurnama11/my-f1-companion-app/issues/6
---

## Problem Statement

An F1 fan wants live and historical F1 data on their personal Android phone:
the next race countdown, the current season's schedule with full podiums,
driver and constructor standings, circuit stats, and quick access to their
favourite drivers and team — surfaced through a dark-first, glanceable
interface *and* a home-screen Countdown widget. No free offering bundles these
into one lean native app; the fan currently jumps between web sources to get
the same picture.

## Solution

A single-module Jetpack Compose Android app (`com.anpurnama.f1_app`) with three
top-level tabs — Homepage, Schedule, Leaderboard — plus Driver / Team
/ Round / Circuit detail pages, and one Jetpack Glance home-screen Countdown
widget. All F1 data comes from free, zero-auth APIs
(f1api.dev for schedule + driver/team/circuit catalogs; Jolpica standard for
Race + Qualifying results and pit-stops; Jolpica alpha for Sprint, Sprint
Qualifying, and Free Practice results, translated to Ergast ids at the data
seam; jolpica for all-time most-wins at a circuit),
fetched over one Ktor `HttpClient`, cached via HttpCache +
DataStore for offline-first reads. Favorites (2 drivers + 1 team) persist
locally and drive the Homepage §3 combined favorites card and My Team
management view. The
widget periodically refreshes a cached next-race snapshot and renders a
render-time countdown + LIVE / COMPLETE / off-season / no-cache states, with
a custom-scheme deep link into the round detail.

## User Stories

1. As an F1 fan, I want a dark-first app so that staring at race data in the
   evening doesn't blast me with white.
2. As a fan, I want four clear top-level tabs so I can move between
   overview, schedule, standings, and my favorites without hunting.
3. As a fan, I want the next race countdown on a home-screen widget so I
   never have to open the app to know how long until lights out.
4. As a fan, I want the widget to show "LIVE NOW" in a circuit brand colour
   when a race session is in its window so I notice and open the app.
5. As a fan, I want the widget to show the GP date and time in my local
   timezone so I know exactly when to tune in.
6. As a fan, I want to tap the widget and land on that race's Round detail
   so I can read the result without navigating.
7. As a fan, I want the widget to keep showing the last good countdown when
   the network fails so it never blanks mid-season.
8. As a fan, I want to see "Season over" on the widget during the off-season
   rather than a stale or missing countdown.
9. As a fan, I want the Homepage to surface, in one scroll: the next-session
   countdown (§1), season progress aggregates (§2), and my favourite drivers
   and constructor plus the nearest GP's circuit stats including top speed
   (§3), so I get the whole weekend picture at a glance.
10. As a fan, I want the Schedule tab to show upcoming rounds with session
    times and past rounds with full podiums (P1/P2/P3), so I can see what's
    next and what just happened.
11. As a fan, I want the Leaderboard tab to show current driver and
    constructor standings with wins and points, with rows that drill into
    driver or team detail.
12. As a fan, I want my two favourite drivers and one favourite
    constructor to be pinnable from Homepage §3, with an easy replace
    interaction, so the Homepage reflects who I care about.
13. As a fan, I want the favorite-driving picks to be decoupled from my
    favorite constructor (drivers need not be from that team), so I can
    follow whoever I actually root for.
14. As a fan, I want first launch to seed sensible defaults (the current
    championship-leading constructor plus its two drivers) so §3 on the
    Homepage is not empty before I pick anything.
15. As a fan, I want a Round detail page showing the race-weekend schedule
    (upcoming) or per-session result rows (past), plus a circuit block that
    links to Circuit detail, and a per-session result page, so I can dig into
    a specific weekend.
16. As a fan, I want a Circuit detail page showing the top speed recorded
    there and the all-time most-winning driver and team at that circuit, so
    a track has identity beyond one race.
17. As a fan, I want Driver detail to show a headshot, team, number, and
    standings snapshot; Team detail to show a car render, wordmark, and
    standings snapshot, so a driver or team I follow has a rich surface.
18. As a fan, I want the app to keep working offline (last good cached data)
    rather than throwing connection errors when I'm on a patchy network.
19. As a fan, I want pull-to-refresh on list screens to force-fetch fresh
    data ignoring the cache, so I'm never looking at a stale table right
    after a session.
20. As a fan, I want per-section failure independence on the Homepage so a
    single source failing doesn't blank the whole screen.
21. As a fan, I want a release-signed APK I can sideload on my personal
    Android device, without needing the Play Store.
