---
id: 24
title: "Favorites management lives on Homepage §3 (no separate My Team tab)"
type: grilling
status: closed
blocked_by: []
owner: "pi"
---

## Question

Where does favorites management live if the My Team tab goes away?

## Answer

Variant A wins. §3 is the existing compact preview card (one card,
three rows); each row is tappable and opens the picker for that slot
directly. The `Route.MyTeam` `data object` and the 4th
`NavigationBarItem` are removed; the app has 3 top-level tabs.

The picker (the existing `ModalBottomSheet` listing current standings
with the "Already selected" disabled state) moves into the Homepage
feature module and writes to `FavoritesCache` via the same
`setDriver1` / `setDriver2` / `setTeam` methods. The empty state is
three placeholder rows inside one card, each tappable to pick that
slot — no `Button` CTA.

## Why

- A keeps §3 in the *results* family with §1 (countdown) and §2
  (season aggregates); the page-center-of-gravity stays on the §1
  hero.
- The "Change" label on each row is the affordance; no mode
  toggle to teach.
- C (always editable, three separate cards) would promote §3 to
  the *settings* family, fighting ADR 0008's bleed-to-top
  aesthetic and §1's magazine-cover hero treatment.
- B (edit-mode toggle) is a hybrid that costs a mode-switch for
  a surface the user touches a handful of times per season. B is
  the natural fallback if A's discoverability fails in field
  testing, but A is the right starting point.

## Considered alternatives

- **A — Inline tap-to-pick** (chosen). Compact preview rows; tap
  to pick; "Change" label as affordance.
- **B — Edit-mode toggle** (rejected). Mode-switch cost for a
  low-use surface. Natural fallback if A's discoverability fails.
- **C — Always editable** (rejected). Three separate cards
  promote §3 to the settings family; fights §1 hero treatment.
  Only viable if the whole homepage composition is rethought.
- **Keep the 4-tab shape** (rejected). Status quo. The My Team
  tab doesn't earn its slot for a casual fan who configures
  favorites once.

## Cross-references

- ADR 0010: `lode/decisions/0010-my-team-content-into-homepage-§3.md`
- Research: `lode/wayfinder/f1app/my-team-on-homepage.md`
- Build: `lode/plans/f1app-build/tickets/11-favorites-on-homepage.md`
- Related: ADR 0008 (bleed-to-top), ADR 0004 (multi-backstack),
  ticket 12 (favorites picker UX + storage), ticket 18 (§3
  favorites shape)
