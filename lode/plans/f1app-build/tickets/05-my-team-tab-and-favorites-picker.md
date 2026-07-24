---
id: 05
title: My Team tab + Favorites picker
type: task
status: ready-for-agent
blocked_by: [02]
owner: ""
---

# 05 — My Team tab + Favorites picker

**What to build:** the 4th top-level tab becomes the favorites management surface. Three slots — two favorite drivers + one favorite constructor team — tappable to open a `ModalBottomSheet` picker that lists drivers/teams from `GetDriversStandings`/`GetConstructorsStandings` and lets the user pick or replace. Replaces the first-launch seed from 02 with the user's explicit choice. Driver↔team decoupled (favorite drivers need not be from the favorite constructor). 3rd-pin is explicit user replace, never auto-evict-oldest; a driver `id` occupies at most one driver slot; a team `id` is unique in the team slot. Written directly to `FavoritesCache`; Homepage §3 picks up the change (same cache instance). No separate onboarding route, no star/pin on Driver/Team detail.

**Blocked by:** 02 — Homepage §3 (which introduces `FavoritesCache` + first-launch seed + reads the cache; the picker writes to the same cache and Homepage §3 reads from it).

**Status:** ready-for-agent

## Done when

- [ ] `MyTeam` tab renders 3 slots (2 drivers + 1 team) from `FavoritesCache`
- [ ] Tap filled slot → `ModalBottomSheet` picker listing drivers/teams (variant A); pick or replace
- [ ] Driver↔team decoupled; driver `id` unique across the two driver slots; team `id` unique in team slot; 3rd-pin = explicit replace (no auto-evict-oldest)
- [ ] Empty state seeds via the 02 default until the user picks (write overwrites the seed)
- [ ] `MyTeamViewModel` writes one atomic `edit` to `FavoritesCache`; Homepage §3 reflects the change (same `Wiring` instance)
- [ ] No separate onboarding route; no star/pin controls on Driver/Team detail surfaces

Spec cross-ref: `lode/specs/f1app.md` (Favorites section), `lode/wayfinder/f1app/tickets/12-design-favorites-picker-ux-storage.md`.
